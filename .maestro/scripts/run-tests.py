#!/usr/bin/env python3
"""Run isolated Maestro flows. This module uses only the Python standard library."""

import argparse
import datetime
import importlib.util
import json
import math
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
RECOVERY = "files/maestro-feature-recovery.json"
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
FEATURE_NAME = re.compile(r"[a-z][a-z0-9-]*")
FLOW_NAME = re.compile(r"[a-z][a-z0-9-]*")
VARIABLE_NAME = re.compile(r"[A-Z][A-Z0-9_]*")
REQUIRED_ENVIRONMENT = frozenset({
    "MAESTRO_PEER_AUTH_STATE_JSON", "MAESTRO_PEER_MEMBER_ID",
    "MAESTRO_PEER_FIXTURE_START_DATE", "MAESTRO_CHAT_ROOM_ID",
    "MAESTRO_MATCHING_LEFT_TOP_LATITUDE", "MAESTRO_MATCHING_LEFT_TOP_LONGITUDE",
    "MAESTRO_MATCHING_RIGHT_BOTTOM_LATITUDE", "MAESTRO_MATCHING_RIGHT_BOTTOM_LONGITUDE",
    "MAESTRO_MATCHING_SEARCH_QUERY", "MAESTRO_MATCHING_CATALOG_PLACE_NAME",
    "MAESTRO_MATCHING_MARKER_POINT", "MAESTRO_MATCHING_ROOM_ID",
})


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


def read_credentials(prefix="MAESTRO_"):
    if prefix not in ("MAESTRO_", "MAESTRO_PEER_"):
        raise RunnerFailure("invalid-credentials-prefix")
    keys = tuple(prefix + name for name in ("AUTH_STATE_JSON", "MEMBER_ID", "FIXTURE_START_DATE"))
    missing = [key for key in keys if not os.environ.get(key, "").strip()]
    if missing:
        raise RunnerFailure("missing-required-session-secrets: " + ", ".join(missing))
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


def discover_suites():
    directory = ROOT / ".maestro" / "suites"
    descriptors = {}
    if not directory.exists():
        return descriptors
    if directory.is_symlink() or not directory.is_dir():
        raise RunnerFailure("invalid-suite-directory")
    owned_flows = set(flow for flows in FLOWS.values() for flow in flows)
    for path in sorted(directory.iterdir()):
        if path.suffix != ".json":
            raise RunnerFailure("invalid-suite-descriptor-file")
        name = path.stem
        if (not FEATURE_NAME.fullmatch(name) or name in {*FLOWS, "ci"}
                or path.is_symlink() or not path.is_file()):
            raise RunnerFailure("invalid-suite-descriptor")
        try:
            descriptor = json.loads(path.read_text(encoding="utf-8"))
        except (OSError, ValueError):
            raise RunnerFailure("invalid-suite-descriptor") from None
        if not isinstance(descriptor, dict) or set(descriptor) != {
            "flows", "apiLevel", "fixture", "requiredEnvironment", "driver",
        }:
            raise RunnerFailure("invalid-suite-descriptor")
        flows = descriptor["flows"]
        environment = descriptor["requiredEnvironment"]
        driver = descriptor["driver"]
        if (not isinstance(flows, list) or not flows
                or any(not isinstance(flow, str) or not FLOW_NAME.fullmatch(flow) for flow in flows)
                or len(set(flows)) != len(flows) or owned_flows.intersection(flows)
                or type(descriptor["apiLevel"]) is not int or not 30 <= descriptor["apiLevel"] <= 99
                or type(descriptor["fixture"]) is not bool
                or not isinstance(environment, list)
                or any(not isinstance(key, str) or key not in REQUIRED_ENVIRONMENT for key in environment)
                or len(set(environment)) != len(environment)
                or (driver is not None and driver != name)):
            raise RunnerFailure("invalid-suite-descriptor")
        for flow in flows:
            flow_path = ROOT / ".maestro" / "flows" / (flow + ".yaml")
            if flow_path.is_symlink() or not flow_path.is_file():
                raise RunnerFailure("selected-flow-file-missing")
        module = ROOT / ".maestro" / "scripts" / "features" / (name + ".py")
        if module.is_symlink() or (driver is not None and not module.is_file()):
            raise RunnerFailure("invalid-feature-module")
        owned_flows.update(flows)
        descriptors[name] = {"name": name, **descriptor}
    return descriptors


def builtin_suite(flows):
    return {"name": None, "flows": list(flows), "apiLevel": 30, "fixture": False,
            "requiredEnvironment": [], "driver": None}


def select_suites(name):
    descriptors = discover_suites()
    if name in ("ci", "all"):
        suites = [builtin_suite(("smoke-tabs",))]
        if name == "all":
            suites.append(builtin_suite(FLOWS["regression"]))
        suites.extend(descriptors.values())
        suites.append(builtin_suite(("login-screen",)))
    elif name in FLOWS:
        suites = [builtin_suite(FLOWS[name])]
    elif name in descriptors:
        suites = [descriptors[name]]
    else:
        raise RunnerFailure("unknown-selected-suite")
    for flow in selected_flows(suites):
        path = ROOT / ".maestro" / "flows" / (flow + ".yaml")
        if path.is_symlink() or not path.is_file():
            raise RunnerFailure("selected-flow-file-missing")
    return suites


def selected_flows(suites):
    return [flow for suite in suites for flow in suite["flows"]]


def required_api(suites):
    return max(suite["apiLevel"] for suite in suites)


def read_required_environment(suites):
    required = sorted({key for suite in suites for key in suite["requiredEnvironment"]})
    missing = [key for key in required if not os.environ.get(key, "").strip()]
    if missing:
        raise RunnerFailure("missing-required-environment: " + ", ".join(missing))
    values = {key: os.environ[key] for key in required}
    if "MAESTRO_PEER_AUTH_STATE_JSON" in values:
        read_credentials("MAESTRO_PEER_")
    return values


def safe_variables(variables):
    if not isinstance(variables, dict):
        raise RunnerFailure("invalid-feature-variables")
    for key, value in variables.items():
        if (not isinstance(key, str) or not VARIABLE_NAME.fullmatch(key)
                or re.search(r"AUTH|TOKEN|FCM|SECRET|PASSWORD|SESSION", key)
                or not isinstance(value, str) or len(value) > 4096
                or any(ord(character) < 32 for character in value)
                or re.search(r"eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.", value)):
            raise RunnerFailure("invalid-feature-variables")
    return dict(variables)


def feature_metadata(operation, feature, flow, run_id, parameters):
    if all(value is None for value in (feature, flow, run_id, parameters)):
        return {}
    if (operation not in ("prepare", "cleanup")
            or not isinstance(feature, str) or not FEATURE_NAME.fullmatch(feature)
            or not isinstance(flow, str) or not FLOW_NAME.fullmatch(flow)
            or not isinstance(run_id, str) or not re.fullmatch(r"[a-f0-9]{16,32}", run_id)
            or not isinstance(parameters, dict)
            or any(not isinstance(key, str) or not isinstance(value, str) for key, value in parameters.items())):
        raise RunnerFailure("invalid-feature-metadata")
    return {"feature": feature, "flow": flow, "runId": run_id, "parameters": parameters}


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
            elif isinstance(value, str) and value:
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
        locale = self.require("shell", "getprop", "persist.sys.locale").strip()
        timezone = self.require("shell", "getprop", "persist.sys.timezone").strip()
        locale_matches = locale in (b"ko-KR", b"ko_KR")
        timezone_matches = timezone == b"Asia/Seoul"
        print(
            f"Locale after-setprop: locale_matches={locale_matches} timezone_matches={timezone_matches}",
            file=sys.stderr,
        )
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
        locale_matches = locale in (b"ko-KR", b"ko_KR")
        timezone_matches = timezone == b"Asia/Seoul"
        print(
            f"Locale verification: locale_matches={locale_matches} timezone_matches={timezone_matches}",
            file=sys.stderr,
        )
        if not locale_matches or not timezone_matches:
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
        self.require("shell", "-T", "run-as", APP_ID, "pwd")

    def clear(self):
        # 이전 실행의 복구 snapshot이 있으면 새 실행도 pm clear로 이를 지우지 않습니다.
        probe = "if [ -e " + RECOVERY + " ] || [ -e " + RECOVERY + ".bak ] || [ -e " + RECOVERY + ".new ]; then exit 4; fi"
        code, _, _ = self.call("shell", "-T", "run-as", APP_ID, "sh", "-c", shlex.quote(probe))
        if code == 4:
            raise RunnerFailure("pending-feature-restoration-refuses-app-data-clear")
        if code != 0:
            raise RunnerFailure("adb-operation-failed")
        self.require("shell", "am", "force-stop", APP_ID)
        output = self.require("shell", "pm", "clear", APP_ID)
        if output.strip() != b"Success":
            raise RunnerFailure("app-data-clear-failed")

    def remove_private(self, include_session=False):
        files = (REQUEST, RESULT, SESSION) if include_session else (REQUEST, RESULT)
        # export 완료 뒤 호스트가 읽을 때까지 세션 본문과 AtomicFile sidecar를 보존합니다.
        paths = [path + suffix for path in files for suffix in ("", ".new", ".bak")]
        self.require("shell", "-T", "run-as", APP_ID, "rm", "-f", *paths)

    def bootstrap(self, operation, credentials=None, timeout=300, feature=None, flow=None,
                  run_id=None, parameters=None):
        if operation not in ("prepare", "cleanup", "export"):
            raise RunnerFailure("invalid-bootstrap-operation")
        metadata = feature_metadata(operation, feature, flow, run_id, parameters)
        request = {"operation": operation, **(credentials or {}), **metadata}
        self.require("shell", "am", "force-stop", APP_ID)
        self.remove_private(include_session=operation == "export")
        payload = json.dumps(request, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
        # shell -T는 PTY 없이 stdin과 원격 종료 상태를 전달합니다. argv에는 고정 명령만 넣습니다.
        script = "umask 077; mkdir -p files && cat > " + REQUEST
        self.require("shell", "-T", "run-as", APP_ID, "sh", "-c", shlex.quote(script), payload=payload)
        self.require("shell", "am", "start", "-W", "-n", COMPONENT)
        expected = {"prepare": "ready", "cleanup": "cleaned", "export": "exported"}[operation]
        # 결과 파일이 아직 없을 때만 기다리고, 실제 읽기 실패의 종료 상태는 보존합니다.
        probe = "if [ ! -e " + RESULT + " ]; then exit 3; fi; cat " + RESULT
        deadline = time.monotonic() + timeout
        while time.monotonic() < deadline:
            code, output, _ = self.call("shell", "-T", "run-as", APP_ID, "sh", "-c", shlex.quote(probe))
            if code == 3:
                time.sleep(0.5)
                continue
            if code != 0:
                raise RunnerFailure("adb-operation-failed")
            try:
                result = json.loads(output)
            except (ValueError, TypeError):
                raise RunnerFailure("invalid-bootstrap-result") from None
            if not isinstance(result, dict) or result.get("status") != expected:
                # 앱의 reason도 외부 입력입니다. 로그/리포트에는 고정 오류만 남깁니다.
                raise RunnerFailure("bootstrap-" + operation + "-failed")
            variables = safe_variables(result.get("variables", {}))
            self.remove_private()
            return variables
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
        payload = device.require("shell", "-T", "run-as", APP_ID, "cat", SESSION)
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


def run_maestro(device, flow, output, redactor, timeout, fixture_start_date=None, variables=None):
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
        explicit = safe_variables(variables or {})
        variables = {**device.exercise_variables(), **explicit}
        if flow == "exercise-crud" and (not fixture_start_date or
                variables["EXERCISE_START_DATE"].replace(".", "-") < fixture_start_date):
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


def load_feature_module(suite):
    if not suite["name"]:
        return None
    path = ROOT / ".maestro" / "scripts" / "features" / (suite["name"] + ".py")
    if not path.is_file():
        return None
    spec = importlib.util.spec_from_file_location("maestro_feature_" + suite["name"], path)
    module = importlib.util.module_from_spec(spec)
    sys.modules[spec.name] = module
    spec.loader.exec_module(module)
    if suite["driver"] and not callable(getattr(module, "run_suite", None)):
        raise RunnerFailure("invalid-feature-driver")
    return module


def core_module():
    # importlib로 로드하는 회귀 테스트도 실행 모듈과 같은 공개 API를 받습니다.
    if __name__ in sys.modules:
        return sys.modules[__name__]
    module = type(sys)("maestro_core", __doc__)
    module.__dict__.update(globals())
    return module


def run_ordinary_flow(args, device, suite, flow, output, redactor, credentials, parameters, hooks):
    started = time.monotonic()
    errors = []
    run_id = uuid.uuid4().hex[:16]
    ownership = output / flow
    ownership.mkdir(parents=True, exist_ok=True)
    (ownership / "ownership.log").write_text("E2E_RUN_ID=" + run_id + "\n", encoding="utf-8")
    variables = {"E2E_RUN_ID": run_id}
    metadata = ({"feature": suite["name"], "flow": flow, "run_id": run_id, "parameters": parameters}
                if suite["fixture"] else {})
    prepared = False
    safe_to_continue = True
    try:
        device.clear()
        if flow != "login-screen":
            # prepare 중 부분 변경이 발생해도 동일 ownership으로 cleanup을 요청합니다.
            prepared = True
            added = safe_variables(device.bootstrap("prepare", credentials, **metadata))
            if "E2E_RUN_ID" in added and added["E2E_RUN_ID"] != run_id:
                raise RunnerFailure("invalid-feature-variables")
            variables.update(added)
        if hooks and callable(getattr(hooks, "before_flow", None)):
            try:
                added = safe_variables(hooks.before_flow(
                    core_module(), device, flow, dict(variables), credentials, run_id,
                ))
                if "E2E_RUN_ID" in added and added["E2E_RUN_ID"] != run_id:
                    raise RunnerFailure("invalid-feature-variables")
                variables.update(added)
            except RunnerFailure:
                raise
            except Exception:
                raise RunnerFailure("feature-hook-failed") from None
        run_maestro(device, flow, output, redactor, args.flow_timeout,
                    credentials["fixtureStartDate"] if credentials else None, variables=variables)
        if hooks and callable(getattr(hooks, "after_flow", None)):
            try:
                hooks.after_flow(core_module(), device, flow, dict(variables), credentials, run_id)
            except RunnerFailure:
                raise
            except Exception:
                raise RunnerFailure("feature-hook-failed") from None
    except RunInterrupted:
        errors.append("run-interrupted")
        safe_to_continue = False
    except RunnerFailure as failure:
        errors.append(str(failure))
    except Exception:
        errors.append("runner-operation-failed")
    finally:
        handlers = {sig: signal.signal(sig, signal.SIG_IGN) for sig in (signal.SIGINT, signal.SIGTERM)}
        try:
            cleanup_failed = False
            if prepared:
                try:
                    device.bootstrap("cleanup", credentials, **metadata)
                except Exception:
                    errors.append("fixture-cleanup-failed")
                    cleanup_failed = True
                    safe_to_continue = False
            if cleanup_failed:
                # pm clear는 복구 snapshot도 지우므로 정리 실패 뒤에는 다음 flow도 실행하지 않습니다.
                errors.append("private-state-preserved-for-recovery")
            else:
                try:
                    device.clear()
                except Exception:
                    errors.append("final-app-data-clear-failed")
                    safe_to_continue = False
        finally:
            for sig, handler in handlers.items():
                signal.signal(sig, handler)
    return (flow, time.monotonic() - started, errors), safe_to_continue


def validate_driver_results(flows, results):
    if not isinstance(results, (list, tuple)):
        return [(flow, 0, ["invalid-driver-results"]) for flow in flows]
    reported = {}
    invalid = False
    for item in results:
        if not isinstance(item, (list, tuple)) or len(item) != 3:
            invalid = True
            continue
        flow, duration, errors = item
        if (not isinstance(flow, str) or flow not in flows or flow in reported
                or type(duration) not in (int, float) or not math.isfinite(duration) or duration < 0
                or not isinstance(errors, list)
                or any(not isinstance(error, str) or not re.fullmatch(r"[a-z][a-z0-9-]*", error) for error in errors)):
            invalid = True
            continue
        reported[flow] = (flow, duration, errors)
    return [(flow, reported[flow][1], [*reported[flow][2], "invalid-driver-results"])
            if invalid and flow in reported else reported.get(flow, (flow, 0, ["driver-flow-not-reported"]))
            for flow in flows]


def run_suite(args):
    output = ROOT / "build" / "maestro" / (datetime.datetime.now(datetime.timezone.utc).strftime("%Y%m%dT%H%M%SZ") + "-" + uuid.uuid4().hex[:8])
    output.mkdir(parents=True, mode=0o700)
    args.output = output
    results = []
    selected = []
    try:
        suites = select_suites(args.suite)
        selected = selected_flows(suites)
        environment = read_required_environment(suites)
        credentials = read_credentials() if any(flow != "login-screen" for flow in selected) else None
        peer = read_credentials("MAESTRO_PEER_") if "MAESTRO_PEER_AUTH_STATE_JSON" in environment else None
        redactor = Redactor({"primary": credentials, "peer": peer, "feature": environment})
        # 모듈의 import/계약 오류도 장치 초기화나 서버 변경 전에 검출합니다.
        modules = {suite["name"]: load_feature_module(suite) for suite in suites}
        device = None
        continue_run = True
        for suite in suites:
            parameters = {key: environment[key] for key in suite["requiredEnvironment"]}
            hooks = modules[suite["name"]]
            if not continue_run:
                results.extend((flow, 0, ["not-executed-after-unsafe-cleanup-or-interruption"])
                               for flow in suite["flows"])
                continue
            if suite["driver"]:
                try:
                    returned = hooks.run_suite(args, core_module())
                    driver_results = validate_driver_results(suite["flows"], returned)
                except RunInterrupted:
                    driver_results = [(flow, 0, ["run-interrupted"]) for flow in suite["flows"]]
                except Exception:
                    driver_results = [(flow, 0, ["feature-driver-failed"]) for flow in suite["flows"]]
                results.extend(driver_results)
                continue_run = not any(errors for _, _, errors in driver_results)
                device = None
            else:
                if device is None:
                    device = Device(args.device)
                    if args.configure_emulator:
                        device.configure_emulator()
                    device.verify_locale()
                    if args.apk:
                        device.install(Path(args.apk).resolve())
                    # 일반 debug/release 앱은 이 확인을 통과하지 못하며 pm clear도 하지 않습니다.
                    device.verify_setup()
                for flow in suite["flows"]:
                    if not continue_run:
                        results.append((flow, 0, ["not-executed-after-unsafe-cleanup-or-interruption"]))
                        continue
                    result, continue_run = run_ordinary_flow(
                        args, device, suite, flow, output, redactor, credentials, parameters, hooks,
                    )
                    results.append(result)
                    write_report(output, results)
            write_report(output, results)
    except RunnerFailure as failure:
        results.append(("host-setup", 0, [str(failure)]))
    except Exception:
        results.append(("host-setup", 0, ["runner-operation-failed"]))
    finally:
        reported = {name for name, _, _ in results}
        results.extend((flow, 0, ["not-executed-host-setup-failed"])
                       for flow in selected if flow not in reported)
        write_report(output, results)
    for flow, _, errors in results:
        print(flow + (": FAILED (" + "; ".join(errors) + ")" if errors else ": PASSED"))
    print("Safe artifacts: " + str(output.relative_to(ROOT)))
    return 1 if not results or any(errors for _, _, errors in results) else 0


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    operation = parser.add_mutually_exclusive_group(required=True)
    operation.add_argument("--suite", help="smoke, regression, login, ci, all, or a discovered feature")
    operation.add_argument("--export-session", metavar="ABSOLUTE_PRIVATE_PATH")
    parser.add_argument("--device", help="ADB serial; required when multiple devices are connected")
    parser.add_argument("--peer-device", help="Second ADB serial for the two-account chat driver")
    parser.add_argument("--preflight", action="store_true", help="Validate selected inputs and emit the required emulator API")
    parser.add_argument("--apk", help="Install this exact e2e APK after checking effective ARM ABI support")
    parser.add_argument("--flow-timeout", type=int, default=900, help="Per-flow Maestro timeout in seconds")
    parser.add_argument("--configure-emulator", action="store_true", help="Set ko-KR/Asia-Seoul and reboot a rootable emulator")
    args = parser.parse_args()
    if args.flow_timeout <= 0:
        parser.error("--flow-timeout must be positive")
    signal.signal(signal.SIGINT, interrupt)
    signal.signal(signal.SIGTERM, interrupt)
    try:
        if args.preflight:
            if not args.suite:
                raise RunnerFailure("preflight-requires-suite")
            suites = select_suites(args.suite)
            selected = selected_flows(suites)
            api = required_api(suites)
            print("Selected flows: " + ", ".join(selected))
            print("Required emulator API: " + str(api))
            try:
                read_required_environment(suites)
                if any(flow != "login-screen" for flow in selected):
                    read_credentials()
            except RunnerFailure as failure:
                output = ROOT / "build" / "maestro" / "preflight"
                output.mkdir(parents=True, exist_ok=True)
                write_report(output, [(flow, 0, [str(failure), "not-executed-input-preflight-failed"])
                                      for flow in selected])
                raise
            if os.environ.get("GITHUB_OUTPUT"):
                with open(os.environ["GITHUB_OUTPUT"], "a", encoding="utf-8") as handle:
                    handle.write("api-level=" + str(api) + "\n")
            return 0
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
