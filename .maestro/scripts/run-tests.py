#!/usr/bin/env python3
"""Run isolated Maestro flows. This module uses only the Python standard library."""

import argparse
import datetime
import json
import os
from pathlib import Path
import re
import shlex
import shutil
import signal
import stat
import subprocess
import sys
import tempfile
import time
import uuid
import xml.etree.ElementTree as ET
import zipfile


ROOT = Path(__file__).resolve().parents[2]
APP_ID = "com.project200.undabang"
COMPONENT = APP_ID + "/com.project200.undabang.e2e.MaestroSetupActivity"
REQUEST = "files/maestro-request.json"
RESULT = "files/maestro-result.json"
SESSION = "files/maestro-session.json"
MAESTRO_VERSION = "2.11.0"
FLOWS = {
    "smoke": ("smoke-tabs", "login-screen"),
    "regression": ("exercise-form-entry", "exercise-crud"),
    "all": ("smoke-tabs", "exercise-form-entry", "exercise-crud", "login-screen"),
    "login": ("login-screen",),
}
TOOL_ENV_KEYS = (
    "PATH", "HOME", "USER", "LOGNAME", "JAVA_HOME", "ANDROID_HOME",
    "ANDROID_SDK_ROOT", "ANDROID_ADB_SERVER_PORT", "ADB_SERVER_SOCKET",
    "ADB_VENDOR_KEYS", "TMPDIR", "TMP", "TEMP", "SYSTEMROOT",
)


class RunnerFailure(Exception):
    """Only fixed, non-secret messages may cross the command-line boundary."""


class RunInterrupted(RunnerFailure):
    pass


def tool_environment():
    # 셸/CI 비밀과 기존 MAESTRO_* 설정을 도구 프로세스로 상속하지 않습니다.
    env = {key: os.environ[key] for key in TOOL_ENV_KEYS if key in os.environ}
    env.update({"LANG": "C.UTF-8", "LC_ALL": "C.UTF-8", "TZ": "Asia/Seoul"})
    return env


def command(argv, env, timeout=60, payload=None):
    process = None
    try:
        process = subprocess.Popen(
            argv, cwd=ROOT, env=env, stdin=subprocess.PIPE,
            stdout=subprocess.PIPE, stderr=subprocess.PIPE, start_new_session=True,
        )
        stdout, stderr = process.communicate(input=payload, timeout=timeout)
        return process.returncode, stdout, stderr
    except subprocess.TimeoutExpired:
        if process is not None:
            os.killpg(process.pid, signal.SIGKILL)
            process.communicate()
        raise RunnerFailure("tool-timeout") from None
    except (KeyboardInterrupt, RunInterrupted):
        if process is not None:
            os.killpg(process.pid, signal.SIGKILL)
            process.communicate()
        raise RunInterrupted("run-interrupted") from None
    except OSError:
        raise RunnerFailure("tool-unavailable") from None


def read_credentials():
    keys = ("MAESTRO_AUTH_STATE_JSON", "MAESTRO_MEMBER_ID", "MAESTRO_FIXTURE_START_DATE")
    if any(not os.environ.get(key, "").strip() for key in keys):
        raise RunnerFailure("missing-required-session-secrets")
    try:
        auth = json.loads(os.environ[keys[0]])
        start = datetime.date.fromisoformat(os.environ[keys[2]])
    except (ValueError, TypeError):
        raise RunnerFailure("invalid-session-secrets") from None
    if not isinstance(auth, dict) or not auth:
        raise RunnerFailure("invalid-session-secrets")
    if start.isoformat() != os.environ[keys[2]] or start > datetime.datetime.now(
        datetime.timezone(datetime.timedelta(hours=9))
    ).date():
        raise RunnerFailure("invalid-fixture-start-date")
    return {"authState": auth, "memberId": os.environ[keys[1]], "fixtureStartDate": start.isoformat()}


class Redactor:
    def __init__(self, credentials):
        values = []

        def collect(value):
            if isinstance(value, dict):
                for item in value.values():
                    collect(item)
            elif isinstance(value, list):
                for item in value:
                    collect(item)
            elif isinstance(value, str) and len(value) >= 4:
                values.extend((value, json.dumps(value, ensure_ascii=False)[1:-1]))

        collect(credentials or {})
        self.values = sorted(set(values), key=len, reverse=True)

    def clean(self, text):
        original = text
        for value in self.values:
            text = text.replace(value, "[REDACTED]")
        text = re.sub(r"\beyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\b", "[REDACTED]", text)
        # 갱신된 토큰도 필드명/헤더가 있는 줄 전체를 제거해 원본 토큰 목록에 의존하지 않습니다.
        sensitive = text != original
        lines = []
        for line in text.splitlines():
            if re.search(
                r"authorization|bearer\s|(?:access|refresh|id)[_-]?token|authState|maestro-session|maestro-request",
                line, re.IGNORECASE,
            ):
                lines.append("[REDACTED sensitive line]")
                sensitive = True
            else:
                lines.append(line)
        text = "\n".join(lines)
        text = re.sub(r"\x1b\[[0-?]*[ -/]*[@-~]", "", text)
        return text, sensitive


class Device:
    def __init__(self, serial):
        adb = shutil.which("adb")
        if not adb:
            raise RunnerFailure("adb-unavailable")
        self.env = tool_environment()
        if not serial:
            code, output, _ = command([adb, "devices"], self.env)
            devices = [line.split()[0] for line in output.decode("utf-8", "replace").splitlines()
                       if len(line.split()) == 2 and line.split()[1] == "device"]
            if code != 0 or len(devices) != 1:
                raise RunnerFailure("select-one-connected-device-with-device")
            serial = devices[0]
        self.serial = serial
        self.adb = [adb, "-s", serial]
        code, output, _ = self.call("get-state")
        if code != 0 or output.strip() != b"device":
            raise RunnerFailure("device-unavailable")

    def call(self, *args, timeout=60, payload=None):
        return command(self.adb + list(args), self.env, timeout, payload)

    def require(self, *args, timeout=60, payload=None):
        code, output, _ = self.call(*args, timeout=timeout, payload=payload)
        if code != 0:
            raise RunnerFailure("adb-operation-failed")
        return output

    def configure_emulator(self):
        if self.require("shell", "getprop", "ro.kernel.qemu").strip() != b"1":
            raise RunnerFailure("configure-emulator-refuses-physical-devices")
        self.require("root")
        self.require("wait-for-device")
        self.require("shell", "setprop", "persist.sys.locale", "ko-KR")
        self.require("shell", "setprop", "persist.sys.timezone", "Asia/Seoul")
        self.require("reboot")
        self.require("wait-for-device", timeout=180)
        deadline = time.monotonic() + 180
        while time.monotonic() < deadline:
            if self.require("shell", "getprop", "sys.boot_completed").strip() == b"1":
                return
            time.sleep(1)
        raise RunnerFailure("emulator-boot-timeout")

    def verify_locale(self):
        locale = self.require("shell", "getprop", "persist.sys.locale").strip()
        timezone = self.require("shell", "getprop", "persist.sys.timezone").strip()
        if locale not in (b"ko-KR", b"ko_KR") or timezone != b"Asia/Seoul":
            raise RunnerFailure("device-must-use-ko-KR-and-Asia-Seoul")

    def exercise_variables(self):
        output = self.require("shell", "date", "+%Y-%m-%dT%H:%M:%S%z")
        try:
            now = datetime.datetime.strptime(output.decode().strip(), "%Y-%m-%dT%H:%M:%S%z")
        except ValueError:
            raise RunnerFailure("invalid-device-clock") from None
        if now.utcoffset() != datetime.timedelta(hours=9):
            raise RunnerFailure("device-must-use-Asia-Seoul")
        end = now.replace(minute=0, second=0, microsecond=0)
        start = end - datetime.timedelta(hours=1)
        title = "maestro-e2e-" + uuid.uuid4().hex
        return {
            "EXERCISE_TITLE": title,
            "EDITED_EXERCISE_TITLE": title + "-edited",
            "EXERCISE_START_HOUR": start.strftime("%H"),
            "EXERCISE_END_HOUR": end.strftime("%H"),
            "EXERCISE_START_DATE": start.strftime("%Y.%m.%d"),
            "EXERCISE_END_DATE": end.strftime("%Y.%m.%d"),
        }

    def install(self, apk):
        try:
            with zipfile.ZipFile(apk) as archive:
                abis = {name.split("/")[1] for name in archive.namelist()
                        if name.startswith("lib/") and name.endswith(".so") and len(name.split("/")) == 3}
        except (OSError, zipfile.BadZipFile):
            raise RunnerFailure("invalid-e2e-apk") from None
        arm_abis = abis.intersection({"armeabi-v7a", "arm64-v8a"})
        effective = set(self.require("shell", "getprop", "ro.product.cpu.abilist").decode().strip().split(","))
        if not arm_abis or not effective.intersection(arm_abis):
            raise RunnerFailure("device-abi-does-not-support-apk-arm-libraries")
        # 같은 ID의 release 앱과 서명이 다르면 설치 실패를 유지합니다. 자동 제거하지 않습니다.
        output = self.require("install", "-r", str(apk), timeout=180)
        if b"Success" not in output:
            raise RunnerFailure("e2e-apk-install-failed")

    def verify_setup(self):
        output = self.require("shell", "cmd", "package", "resolve-activity", "--brief", "-n", COMPONENT)
        resolved = {line.strip() for line in output.decode("utf-8", "replace").splitlines()}
        names = {COMPONENT, APP_ID + "/.e2e.MaestroSetupActivity"}
        if not resolved.intersection(names):
            raise RunnerFailure("installed-app-has-no-e2e-setup-component")
        self.require("exec-out", "run-as", APP_ID, "pwd")

    def clear(self):
        self.require("shell", "am", "force-stop", APP_ID)
        output = self.require("shell", "pm", "clear", APP_ID)
        if output.strip() != b"Success":
            raise RunnerFailure("app-data-clear-failed")

    def remove_private(self, include_session=False):
        files = (REQUEST, RESULT, SESSION) if include_session else (REQUEST, RESULT)
        # export 완료 뒤 호스트가 읽을 때까지 세션 본문과 AtomicFile sidecar를 보존합니다.
        paths = [path + suffix for path in files for suffix in ("", ".new", ".bak")]
        self.require("exec-out", "run-as", APP_ID, "rm", "-f", *paths)

    def bootstrap(self, operation, credentials=None, timeout=300):
        self.require("shell", "am", "force-stop", APP_ID)
        self.remove_private(include_session=operation == "export")
        request = {"operation": operation, **(credentials or {})}
        payload = json.dumps(request, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
        # exec-out은 PTY 없이 stdin을 전달합니다. argv에는 고정 파일명/명령만 들어갑니다.
        script = "umask 077; mkdir -p files && cat > " + REQUEST
        self.require("exec-out", "run-as", APP_ID, "sh", "-c", shlex.quote(script), payload=payload)
        self.require("shell", "am", "start", "-W", "-n", COMPONENT)
        expected = {"prepare": "ready", "cleanup": "cleaned", "export": "exported"}[operation]
        deadline = time.monotonic() + timeout
        while time.monotonic() < deadline:
            code, output, _ = self.call("exec-out", "run-as", APP_ID, "cat", RESULT)
            if code == 0 and output:
                try:
                    result = json.loads(output)
                except (ValueError, TypeError):
                    raise RunnerFailure("invalid-bootstrap-result") from None
                if not isinstance(result, dict) or result.get("status") != expected:
                    # 앱의 reason도 외부 입력입니다. 로그/리포트에는 고정 오류만 남깁니다.
                    raise RunnerFailure("bootstrap-" + operation + "-failed")
                self.remove_private()
                return
            time.sleep(0.5)
        raise RunnerFailure("bootstrap-" + operation + "-timeout")


def secure_export_path(value):
    path = Path(value).expanduser()
    if not path.is_absolute():
        raise RunnerFailure("export-path-must-be-absolute")
    if any(item.is_symlink() for item in (path, *path.parents)):
        raise RunnerFailure("export-path-must-not-use-symlinks")
    path = path.resolve()
    if path == ROOT or ROOT in path.parents or path.exists():
        raise RunnerFailure("export-path-must-be-new-and-outside-repository")
    try:
        parent = path.parent.stat()
    except OSError:
        raise RunnerFailure("export-parent-must-exist-with-mode-0700") from None
    if not stat.S_ISDIR(parent.st_mode) or parent.st_uid != os.getuid() or stat.S_IMODE(parent.st_mode) & 0o077:
        raise RunnerFailure("export-parent-must-exist-with-mode-0700")
    return path


def export_session(device, output):
    written = False
    try:
        device.bootstrap("export")
        payload = device.require("exec-out", "run-as", APP_ID, "cat", SESSION)
        try:
            session = json.loads(payload)
        except (ValueError, TypeError):
            raise RunnerFailure("invalid-exported-session") from None
        if not isinstance(session, dict) or not isinstance(session.get("authState"), dict) or not session["authState"]:
            raise RunnerFailure("invalid-exported-session")
        if not isinstance(session.get("memberId"), str) or not session["memberId"]:
            raise RunnerFailure("invalid-exported-session")
        descriptor = os.open(output, os.O_WRONLY | os.O_CREAT | os.O_EXCL | os.O_NOFOLLOW, 0o600)
        written = True
        with os.fdopen(descriptor, "wb") as handle:
            handle.write(json.dumps(session, ensure_ascii=False, separators=(",", ":")).encode("utf-8"))
            handle.flush()
            os.fsync(handle.fileno())
    except BaseException:
        if written:
            output.unlink(missing_ok=True)
        raise
    finally:
        cleanup_failed = False
        handlers = {sig: signal.signal(sig, signal.SIG_IGN) for sig in (signal.SIGINT, signal.SIGTERM)}
        for cleanup in (
            lambda: device.require("shell", "am", "force-stop", APP_ID),
            lambda: device.remove_private(include_session=True),
        ):
            try:
                cleanup()
            except Exception:
                cleanup_failed = True
        for sig, handler in handlers.items():
            signal.signal(sig, handler)
        if cleanup_failed:
            if written:
                output.unlink(missing_ok=True)
            raise RunnerFailure("export-private-cleanup-failed")


def save_debug(raw, destination, redactor, console):
    destination.mkdir(parents=True, exist_ok=True)
    clean, sensitive = redactor.clean(console.decode("utf-8", "replace"))
    (destination / "console.log").write_text(clean, encoding="utf-8")
    images = []
    index = 0
    for path in sorted(raw.rglob("*")):
        if path.is_symlink() or not path.is_file() or "logcat" in path.name.lower():
            continue
        suffix = path.suffix.lower()
        if suffix == ".png":
            images.append(path)
        elif suffix in {".log", ".json"}:
            clean, changed = redactor.clean(path.read_text(encoding="utf-8", errors="replace"))
            sensitive = sensitive or changed
            index += 1
            (destination / ("debug-%03d" % index + suffix)).write_text(clean, encoding="utf-8")
    # 텍스트에 자격 증명이 있으면 이미지의 안전성을 판정할 수 없으므로 이미지도 보관하지 않습니다.
    if not sensitive:
        for index, path in enumerate(images, 1):
            content = path.read_bytes()
            if content.startswith(b"\x89PNG\r\n\x1a\n"):
                (destination / ("screenshot-%03d.png" % index)).write_bytes(content)


def maestro_result(report):
    try:
        root = ET.parse(report).getroot()
    except (OSError, ET.ParseError):
        raise RunnerFailure("missing-or-invalid-maestro-junit") from None
    cases = list(root.iter("testcase"))
    if not cases or any(case.find("skipped") is not None for case in cases):
        raise RunnerFailure("maestro-did-not-execute-selected-flow")
    if any(case.find("failure") is not None or case.find("error") is not None for case in cases):
        raise RunnerFailure("maestro-assertion-failed")


def run_maestro(device, flow, output, redactor, timeout, fixture_start_date=None):
    maestro = shutil.which("maestro")
    if not maestro:
        raise RunnerFailure("maestro-unavailable")
    with tempfile.TemporaryDirectory(prefix="undabang-maestro-") as temporary:
        raw = Path(temporary)
        home = raw / "home"
        home.mkdir()
        env = tool_environment()
        env.update({
            "HOME": str(home), "XDG_CONFIG_HOME": str(home),
            "JAVA_TOOL_OPTIONS": "-Duser.home=" + str(home),
            "MAESTRO_CLI_NO_ANALYTICS": "true",
            "MAESTRO_CLI_ANALYSIS_NOTIFICATION_DISABLED": "true",
            "MAESTRO_DISABLE_UPDATE_CHECK": "true",
        })
        code, stdout, stderr = command([maestro, "--version"], env)
        if code != 0 or MAESTRO_VERSION not in stdout.decode("utf-8", "replace").split():
            raise RunnerFailure("maestro-version-must-be-2.11.0")
        variables = device.exercise_variables()
        if flow == "exercise-crud" and variables["EXERCISE_START_DATE"].replace(".", "-") < fixture_start_date:
            raise RunnerFailure("fixture-start-date-excludes-flow-record-date")
        report = raw / "report.xml"
        argv = [
            maestro, "test", "--device", device.serial, "--no-ansi", "--format", "junit",
            "--output", str(report), "--debug-output", str(raw / "debug"),
            "--test-output-dir", str(raw / "artifacts"), "--flatten-debug-output",
            str(ROOT / ".maestro" / "flows" / (flow + ".yaml")),
        ]
        for key, value in variables.items():
            argv[2:2] = ["-e", key + "=" + value]
        stdout = stderr = b""
        try:
            code, stdout, stderr = command(argv, env, timeout=timeout)
            if code != 0:
                raise RunnerFailure("maestro-flow-failed")
            maestro_result(report)
        finally:
            save_debug(raw, output / flow, redactor, stdout + b"\n" + stderr)


def write_report(output, results):
    suite = ET.Element("testsuite", name="undabang-maestro", tests=str(len(results)),
                       failures=str(sum(bool(errors) for _, _, errors in results)), errors="0", skipped="0")
    for name, duration, errors in results:
        case = ET.SubElement(suite, "testcase", classname="maestro", name=name, time="%.3f" % duration)
        if errors:
            ET.SubElement(case, "failure", message="; ".join(errors)).text = "; ".join(errors)
    ET.ElementTree(suite).write(output / "report.xml", encoding="utf-8", xml_declaration=True)


def interrupt(_signum, _frame):
    raise RunInterrupted("run-interrupted")


def run_suite(args):
    selected = FLOWS[args.suite]
    output = ROOT / "build" / "maestro" / (datetime.datetime.now(datetime.timezone.utc).strftime("%Y%m%dT%H%M%SZ") + "-" + uuid.uuid4().hex[:8])
    output.mkdir(parents=True, mode=0o700)
    results = []
    credentials = None
    device = None
    try:
        if any(flow != "login-screen" for flow in selected):
            credentials = read_credentials()
        for flow in selected:
            if not (ROOT / ".maestro" / "flows" / (flow + ".yaml")).is_file():
                raise RunnerFailure("selected-flow-file-missing")
        device = Device(args.device)
        if args.configure_emulator:
            device.configure_emulator()
        device.verify_locale()
        if args.apk:
            device.install(Path(args.apk).resolve())
        # 일반 debug/release 앱은 이 확인을 통과하지 못하며 pm clear도 하지 않습니다.
        device.verify_setup()
        redactor = Redactor(credentials)
        for flow in selected:
            started = time.monotonic()
            errors = []
            interrupted = False
            try:
                device.clear()
                if flow != "login-screen":
                    device.bootstrap("prepare", credentials)
                run_maestro(device, flow, output, redactor, args.flow_timeout,
                            credentials["fixtureStartDate"] if credentials else None)
            except RunInterrupted:
                errors.append("run-interrupted")
                interrupted = True
            except RunnerFailure as failure:
                errors.append(str(failure))
            except Exception:
                errors.append("runner-operation-failed")
            finally:
                # 중단 신호가 정리 중 다시 들어와도 삭제/로컬 비밀 제거를 중간에 끊지 않습니다.
                handlers = {sig: signal.signal(sig, signal.SIG_IGN) for sig in (signal.SIGINT, signal.SIGTERM)}
                try:
                    if flow != "login-screen":
                        try:
                            device.bootstrap("cleanup", credentials)
                        except Exception:
                            errors.append("fixture-cleanup-failed")
                    try:
                        device.clear()
                    except Exception:
                        errors.append("final-app-data-clear-failed")
                finally:
                    for sig, handler in handlers.items():
                        signal.signal(sig, handler)
                results.append((flow, time.monotonic() - started, errors))
                write_report(output, results)
            print(flow + (": FAILED (" + "; ".join(errors) + ")" if errors else ": PASSED"))
            if interrupted:
                break
    except RunnerFailure as failure:
        results.append(("host-setup", 0, [str(failure)]))
        print("Host setup: FAILED (" + str(failure) + ")", file=sys.stderr)
    except Exception:
        results.append(("host-setup", 0, ["runner-operation-failed"]))
    finally:
        write_report(output, results)
    print("Safe artifacts: " + str(output.relative_to(ROOT)))
    return 1 if not results or any(errors for _, _, errors in results) else 0


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    operation = parser.add_mutually_exclusive_group(required=True)
    operation.add_argument("--suite", choices=tuple(FLOWS))
    operation.add_argument("--export-session", metavar="ABSOLUTE_PRIVATE_PATH")
    parser.add_argument("--device", help="ADB serial; required when multiple devices are connected")
    parser.add_argument("--apk", help="Install this exact e2e APK after checking effective ARM ABI support")
    parser.add_argument("--flow-timeout", type=int, default=900, help="Per-flow Maestro timeout in seconds")
    parser.add_argument("--configure-emulator", action="store_true", help="Set ko-KR/Asia-Seoul and reboot a rootable emulator")
    args = parser.parse_args()
    if args.flow_timeout <= 0:
        parser.error("--flow-timeout must be positive")
    signal.signal(signal.SIGINT, interrupt)
    signal.signal(signal.SIGTERM, interrupt)
    try:
        if args.export_session:
            output = secure_export_path(args.export_session)
            device = Device(args.device)
            if args.configure_emulator:
                device.configure_emulator()
            if args.apk:
                device.install(Path(args.apk).resolve())
            device.verify_setup()
            export_session(device, output)
            print("Session exported to the requested private file. No credentials were printed.")
            return 0
        return run_suite(args)
    except RunnerFailure as failure:
        print("Runner failed: " + str(failure), file=sys.stderr)
        return 1
    except Exception:
        # 파이썬/subprocess 예외 객체에는 stdin 원문이 있을 수 있어 traceback을 출력하지 않습니다.
        print("Runner failed: internal-operation-failed", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
