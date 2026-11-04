#!/usr/bin/env python3
"""Run the full authenticated ci suite on one verified public emulator pair."""

import argparse
import gzip
import importlib.util
import json
import mmap
import os
from pathlib import Path, PurePosixPath
import posixpath
import shutil
import signal
import subprocess
import struct
import sys
import tarfile
import tempfile
import time
from types import SimpleNamespace


ROOT = Path(__file__).resolve().parents[2]
IMAGE = "system-images;android-34;google_apis;x86_64"
MAX_ARCHIVE_BYTES = 4 * 1024 ** 3
MAX_EXPANDED_BYTES = 12 * 1024 ** 3
MAX_MEMBER_BYTES = 2 * 1024 ** 3
MAX_MEMBERS = 50000
MAX_METADATA_BYTES = 1024 * 1024


def load_module(name, filename):
    spec = importlib.util.spec_from_file_location(name, Path(__file__).with_name(filename))
    module = importlib.util.module_from_spec(spec)
    sys.modules[name] = module
    spec.loader.exec_module(module)
    return module


core = load_module("color_buffer_maestro_core", "run-tests.py")
trace = load_module("color_buffer_trace", "color-buffer-trace.py")


class DiagnosticFailure(Exception):
    """Only fixed codes, not tool output, leave this module."""


class ProducerStatus:
    """Own a private anonymous channel for native completion validation."""

    def __init__(self):
        self.fd = None
        self.mapping = None
        try:
            if sys.platform != "linux":
                raise OSError()
            import fcntl
            self.fd = os.memfd_create("gfxstream-color-buffer-status", os.MFD_ALLOW_SEALING | os.MFD_CLOEXEC)
            os.ftruncate(self.fd, 4)
            fcntl.fcntl(self.fd, fcntl.F_ADD_SEALS,
                        fcntl.F_SEAL_SHRINK | fcntl.F_SEAL_GROW | fcntl.F_SEAL_SEAL)
            self.mapping = mmap.mmap(self.fd, 4, flags=mmap.MAP_SHARED, prot=mmap.PROT_READ | mmap.PROT_WRITE)
            self.mapping[:] = b"\0" * 4
        except (AttributeError, ImportError, OSError, TypeError, ValueError):
            try:
                self.close()
            except DiagnosticFailure:
                pass
            raise DiagnosticFailure("producer-status-api-unavailable") from None

    def read_after_exit(self, process):
        if process is None or process.poll() is None:
            raise DiagnosticFailure("producer-status-process-not-exited")
        try:
            return struct.unpack("=I", self.mapping[:])[0]
        except (OSError, ValueError, TypeError, struct.error):
            raise DiagnosticFailure("producer-status-read-failed") from None

    def close(self):
        failed = False
        if self.mapping is not None:
            try:
                self.mapping.close()
            except (OSError, ValueError):
                failed = True
            self.mapping = None
        if self.fd is not None:
            fd, self.fd = self.fd, None
            try:
                os.close(fd)
            except OSError:
                failed = True
        if failed:
            raise DiagnosticFailure("producer-status-close-failed")



class BoundedTarReader:
    """Bound gzip expansion and tar metadata reads before tarfile allocates them."""

    def __init__(self, source, limit):
        self.source = source
        self.remaining = limit
        self.position = 0

    def read(self, size=-1):
        if size < 0 or size > MAX_METADATA_BYTES:
            raise DiagnosticFailure("archive-metadata-limit")
        data = self.source.read(min(size, self.remaining + 1))
        self.remaining -= len(data)
        self.position += len(data)
        if self.remaining < 0:
            raise DiagnosticFailure("archive-expansion-limit")
        return data

    def tell(self):
        return self.position

    def seek(self, offset, whence=0):
        target = offset if whence == 0 else self.position + offset if whence == 1 else -1
        if target < self.position:
            raise DiagnosticFailure("archive-backward-seek")
        while self.position < target:
            if not self.read(min(MAX_METADATA_BYTES, target - self.position)):
                raise DiagnosticFailure("archive-truncated")
        return self.position


def archive_name(member):
    name = member.name.rstrip("/") if member.isdir() else member.name
    parts = PurePosixPath(name).parts
    if (not name or len(name) > 512 or "\\" in name or "\x00" in name
            or any(ord(char) < 32 or ord(char) > 126 for char in name)
            or name.startswith("/") or not parts or parts[0] != "emulator"
            or any(part in ("", ".", "..") for part in name.split("/"))):
        raise DiagnosticFailure("archive-unsafe-path")
    return name


def validate_members(archive):
    members = {}
    total = 0
    for member in archive:
        name = archive_name(member)
        if name in members or len(members) >= MAX_MEMBERS:
            raise DiagnosticFailure("archive-member-limit-or-duplicate")
        if not (member.isdir() or member.isfile() or member.issym()):
            raise DiagnosticFailure("archive-unsupported-member")
        if member.size < 0 or member.size > MAX_MEMBER_BYTES:
            raise DiagnosticFailure("archive-member-size-limit")
        if not member.isfile() and member.size:
            raise DiagnosticFailure("archive-invalid-member-size")
        total += member.size
        if total > MAX_EXPANDED_BYTES:
            raise DiagnosticFailure("archive-expansion-limit")
        if member.mode & 0o7000:
            raise DiagnosticFailure("archive-unsafe-mode")
        if member.issym():
            target = member.linkname
            if (not target or len(target) > 512 or target.startswith("/")
                    or "\\" in target or any(ord(char) < 32 or ord(char) > 126 for char in target)):
                raise DiagnosticFailure("archive-unsafe-link")
            resolved = posixpath.normpath(posixpath.join(posixpath.dirname(name), target))
            if resolved != "emulator" and not resolved.startswith("emulator/"):
                raise DiagnosticFailure("archive-unsafe-link")
        members[name] = member
    for name, member in members.items():
        for parent in PurePosixPath(name).parents:
            if str(parent) == ".":
                break
            entry = members.get(str(parent))
            if entry is not None and not entry.isdir():
                raise DiagnosticFailure("archive-nondirectory-parent")
        if member.issym():
            pending = list(PurePosixPath(name).parent.parts) + member.linkname.split("/")
            resolved = []
            followed = 0
            while pending:
                part = pending.pop(0)
                if part in ("", "."):
                    continue
                if part == "..":
                    if len(resolved) <= 1:
                        raise DiagnosticFailure("archive-unsafe-link")
                    resolved.pop()
                    continue
                resolved.append(part)
                entry = members.get("/".join(resolved))
                if entry is not None and entry.issym():
                    followed += 1
                    if followed > 40:
                        raise DiagnosticFailure("archive-link-cycle")
                    resolved.pop()
                    pending = entry.linkname.split("/") + pending
            if "/".join(resolved) not in members:
                raise DiagnosticFailure("archive-dangling-link")
    executable = members.get("emulator/emulator")
    if executable is None or not executable.isfile() or not executable.mode & 0o111:
        raise DiagnosticFailure("archive-emulator-missing")
    return members


def extract_package(package, destination):
    if package.is_symlink() or not package.is_file() or package.stat().st_size > MAX_ARCHIVE_BYTES:
        raise DiagnosticFailure("archive-input-invalid")
    if destination.exists():
        raise DiagnosticFailure("archive-destination-exists")
    # Stream validation limits compressed bombs and extended headers before any extraction.
    try:
        with gzip.open(package, "rb") as source:
            bounded = BoundedTarReader(source, MAX_EXPANDED_BYTES + MAX_MEMBERS * 4096)
            with tarfile.open(fileobj=bounded, mode="r:") as archive:
                members = validate_members(archive)
        destination.mkdir(mode=0o700, parents=True)
        with tarfile.open(package, "r:gz") as archive:
            for name, member in members.items():
                target = destination / name
                target.parent.mkdir(mode=0o755, parents=True, exist_ok=True)
                if member.isdir():
                    target.mkdir(mode=0o755, exist_ok=True)
                elif member.isfile():
                    source = archive.extractfile(member)
                    if source is None:
                        raise DiagnosticFailure("archive-file-unavailable")
                    with source, target.open("xb") as output:
                        shutil.copyfileobj(source, output, length=1024 * 1024)
                    target.chmod(0o755 if member.mode & 0o111 else 0o644)
            for name, member in members.items():
                if member.issym():
                    (destination / name).symlink_to(member.linkname)
    except (tarfile.TarError, EOFError, OSError, ValueError, RecursionError):
        raise DiagnosticFailure("archive-invalid") from None
    return destination / "emulator" / "emulator"


def write_json(path, value):
    path.parent.mkdir(mode=0o700, parents=True, exist_ok=True)
    path.write_text(json.dumps(value, sort_keys=True, separators=(",", ":")) + "\n", encoding="ascii")


def sdk_command(argv, environment, payload=None, timeout=120):
    try:
        result = subprocess.run(argv, input=payload, env=environment, cwd=ROOT,
                                stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=timeout)
    except (OSError, subprocess.TimeoutExpired):
        raise DiagnosticFailure("sdk-command-unavailable") from None
    if result.returncode:
        raise DiagnosticFailure("sdk-command-failed")
    return result.stdout


def tool_environment(avd_home):
    environment = core.tool_environment()
    environment["ANDROID_AVD_HOME"] = str(avd_home)
    return environment


def create_avd(private, environment):
    manager = shutil.which("avdmanager", path=environment.get("PATH", ""))
    if manager is None:
        raise DiagnosticFailure("avdmanager-unavailable")
    sdk_command([str(manager), "create", "avd", "-n", "color_buffer", "-k", IMAGE],
                environment, payload=b"no\n")
    config = private / "avd" / "color_buffer.avd" / "config.ini"
    # Same default hardware profile as ordinary CI, with its exact RAM/heap overrides.
    settings = {"hw.cpu.ncore": "2", "hw.ramSize": "4096M", "hw.heapSize": "512M"}
    lines = [line for line in config.read_text(encoding="utf-8").splitlines()
             if line.partition("=")[0].strip() not in settings]
    config.write_text("\n".join(lines + [key + "=" + value for key, value in settings.items()]) + "\n", encoding="utf-8")
    if shutil.disk_usage(config.parent).free < 8 * 1024 ** 3:
        raise DiagnosticFailure("avd-disk-space-insufficient")


def boot(process, environment):
    deadline = time.monotonic() + 600
    while time.monotonic() < deadline:
        if process.poll() is not None:
            raise DiagnosticFailure("emulator-exited-before-boot")
        try:
            completed = sdk_command(["adb", "-s", "emulator-5554", "shell", "getprop", "sys.boot_completed"],
                                    environment, timeout=10)
            if completed.strip() == b"1":
                break
        except DiagnosticFailure:
            pass
        time.sleep(2)
    else:
        raise DiagnosticFailure("emulator-boot-timeout")
    api = sdk_command(["adb", "-s", "emulator-5554", "shell", "getprop", "ro.build.version.sdk"], environment)
    if api.strip() != b"34":
        raise DiagnosticFailure("emulator-api-mismatch")
    sdk_command(["adb", "-s", "emulator-5554", "shell", "input", "keyevent", "82"], environment)
    for key in ("window_animation_scale", "transition_animation_scale", "animator_duration_scale"):
        sdk_command(["adb", "-s", "emulator-5554", "shell", "settings", "put", "global", key, "0.0"], environment)


def stop_emulator(process, environment):
    if process is None:
        return "not-started"
    if process.poll() is not None:
        return "already-exited"
    try:
        sdk_command(["adb", "-s", "emulator-5554", "emu", "kill"], environment, timeout=10)
    except DiagnosticFailure:
        pass
    try:
        process.wait(timeout=30)
        return "console-shutdown"
    except subprocess.TimeoutExpired:
        os.killpg(process.pid, signal.SIGTERM)
    try:
        process.wait(timeout=30)
        return "terminated-after-console-timeout"
    except subprocess.TimeoutExpired:
        os.killpg(process.pid, signal.SIGKILL)
        process.wait(timeout=10)
        return "killed-after-shutdown-timeout"


def suite_arguments(apk):
    return SimpleNamespace(suite="ci", device="emulator-5554", peer_device=None, apk=str(apk),
                           configure_emulator=True, flow_timeout=900, timer_initial_seconds=None,
                           timer_updated_seconds=None, offline_initial_seconds=None,
                           offline_updated_seconds=None, offline_step_name=None)


def run_variant(variant, executable, apk, private, output):
    result = {"variant": variant, "boot": "not-started", "test_returncode": None,
              "emulator_returncode": None, "shutdown": "not-started", "failure": None,
              "trace": "not-requested", "errors": {"status": "unavailable"}, "reproduction": "unavailable",
              "start_host_epoch_ns": time.time_ns(), "start_host_mono_ns": time.monotonic_ns()}
    process = None
    collector = None
    status_channel = None
    environment = tool_environment(private / "avd")
    native_trace = private / "native-trace.jsonl"
    try:
        if variant == "instrumented":
            status_channel = ProducerStatus()
        (private / "avd").mkdir(parents=True, mode=0o700)
        create_avd(private, environment)
        sdk_command(["adb", "start-server"], environment)
        native_environment = dict(environment)
        if variant == "instrumented":
            native_environment["GFXSTREAM_COLOR_BUFFER_TRACE_FILE"] = str(native_trace)
            native_environment["GFXSTREAM_COLOR_BUFFER_TRACE_STATUS_FD"] = str(status_channel.fd)
        process = subprocess.Popen(
            [str(executable), "-avd", "color_buffer", "-port", "5554", "-no-window", "-read-only",
             "-gpu", "swiftshader", "-noaudio", "-no-boot-anim", "-no-snapshot", "-memory", "4096",
             "-timezone", "Asia/Seoul"],
            env=native_environment, cwd=private, stdin=subprocess.DEVNULL,
            stdout=subprocess.PIPE, stderr=subprocess.STDOUT, start_new_session=True,
            pass_fds=(status_channel.fd,) if status_channel is not None else (),
        )
        collector = trace.ErrorCollector(process.stdout, output / "color-buffer-errors.jsonl")
        collector.start()
        result["boot"] = "started"
        boot(process, environment)
        result["boot"] = "booted-api34"
        # The existing runner owns real authentication, per-flow bootstrap and finally cleanup.
        arguments = suite_arguments(apk)
        result["test_returncode"] = core.run_suite(arguments)
        if hasattr(arguments, "output"):
            result["maestro_report_directory"] = arguments.output.name
    except core.RunInterrupted:
        result["failure"] = "run-interrupted"
    except (DiagnosticFailure, core.RunnerFailure) as failure:
        result["failure"] = str(failure) if isinstance(failure, DiagnosticFailure) else "maestro-setup-failed"
    except Exception:
        result["failure"] = "diagnostic-operation-failed"
    finally:
        handlers = {sig: signal.signal(sig, signal.SIG_IGN) for sig in (signal.SIGINT, signal.SIGTERM)}
        try:
            try:
                result["shutdown"] = stop_emulator(process, environment)
            except Exception:
                result["shutdown"] = "shutdown-failed"
            if process is not None:
                result["emulator_returncode"] = process.poll()
            if collector is not None:
                try:
                    result["errors"] = collector.finish()
                    errors = result["errors"]
                    if errors.get("missing_color_buffers", 0) and errors.get("invalid_texture_binds", 0):
                        result["reproduction"] = "observed-missing-and-invalid-bind"
                    elif errors.get("missing_color_buffers", 0):
                        result["reproduction"] = "observed-missing-only"
                    elif errors.get("invalid_texture_binds", 0):
                        result["reproduction"] = "observed-invalid-bind-only"
                    elif errors["status"] == "captured":
                        result["reproduction"] = "not-observed-in-this-public-build-run"
                except Exception:
                    result["errors"] = {"status": "unavailable"}
            if variant == "instrumented":
                producer_status = None
                if status_channel is not None:
                    try:
                        producer_status = status_channel.read_after_exit(process)
                    except DiagnosticFailure as failure:
                        result["failure"] = result["failure"] or str(failure)
                try:
                    result["trace"] = trace.export_trace(native_trace, output / "color-buffer-trace.jsonl",
                                                          output / "lifecycle-summary.json",
                                                          producer_status=producer_status)
                except Exception:
                    result["trace"] = {"status": "unavailable", "complete": False}
            if status_channel is not None:
                try:
                    status_channel.close()
                except DiagnosticFailure:
                    result["failure"] = result["failure"] or "producer-status-close-failed"
            result["end_host_epoch_ns"] = time.time_ns()
            result["end_host_mono_ns"] = time.monotonic_ns()
            try:
                write_json(output / "outcome.json", result)
            except OSError:
                result["failure"] = result["failure"] or "outcome-write-failed"
        finally:
            for sig, handler in handlers.items():
                signal.signal(sig, handler)
    return result


def variant_passed(result):
    return (result["test_returncode"] == 0 and result["failure"] is None
            and result["emulator_returncode"] == 0
            and result["errors"]["status"] == "captured"
            and result["shutdown"] in ("console-shutdown", "already-exited"))


def run_pair(run):
    baseline = run("baseline")
    results = [baseline]
    if variant_passed(baseline):
        results.append(run("instrumented"))
    return results


def pair_returncode(results):
    # Preserve the first original test failure independently of diagnostic failures.
    for result in results:
        if result["test_returncode"] not in (None, 0):
            return result["test_returncode"] if result["test_returncode"] > 0 else 1
        if not variant_passed(result):
            return 1
    if len(results) != 2:
        return 1
    captured = results[-1].get("trace", {})
    return 0 if isinstance(captured, dict) and captured.get("complete") is True else 1


def preflight():
    suites = core.select_suites("ci")
    if core.required_api(suites) != 34:
        raise DiagnosticFailure("ci-suite-api-not-34")
    core.read_required_environment(suites)
    core.read_credentials()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--packages", required=True, type=Path)
    parser.add_argument("--apk", required=True, type=Path)
    args = parser.parse_args()
    output = ROOT / "build" / "color-buffer"
    output.mkdir(parents=True, mode=0o700, exist_ok=True)
    status = {"scope": "public-source-not-sdk-16740729", "packages": "not-verified",
              "preflight": "not-passed", "instrumented_blocked": True, "runs": []}
    returncode = 1
    signal.signal(signal.SIGINT, core.interrupt)
    signal.signal(signal.SIGTERM, core.interrupt)
    try:
        preflight()
        status["preflight"] = "passed"
        packages = args.packages.resolve()
        apk = args.apk.resolve(strict=True)
        provenance = trace.validate_provenance(packages)
        status["packages"] = "hashes-verified"
        status["apk_sha256"] = trace.digest(apk)
        write_json(output / "provenance.json", provenance)
        shutil.copyfile(packages / "resolved-manifest.xml", output / "resolved-manifest.xml")
        with tempfile.TemporaryDirectory(prefix="color-buffer-", dir=os.environ.get("RUNNER_TEMP")) as temporary:
            private = Path(temporary)
            private.chmod(0o700)
            executables = {variant: extract_package(packages / (variant + ".tar.gz"), private / (variant + "-package"))
                           for variant in ("baseline", "instrumented")}
            status["packages"] = "archives-verified"
            # API, AVD hardware, APK and flags stay identical; each run has fresh userdata.
            status["runs"] = run_pair(lambda variant: run_variant(
                variant, executables[variant], apk, private / variant, output / variant,
            ))
        returncode = pair_returncode(status["runs"])
    except core.RunInterrupted:
        status["failure"] = "run-interrupted"
    except (DiagnosticFailure, trace.TraceFailure) as failure:
        status["failure"] = str(failure)
    except core.RunnerFailure:
        status["failure"] = "authenticated-preflight-failed"
    except Exception:
        status["failure"] = "diagnostic-operation-failed"
    finally:
        status["instrumented_blocked"] = len(status["runs"]) != 2
        try:
            write_json(output / "diagnostic-status.json", status)
        except OSError:
            print("Diagnostic status write failed; no complete capture is implied.", file=sys.stderr)
            status["failure"] = status.get("failure", "diagnostic-status-write-failed")
    return (pair_returncode(status["runs"]) or 1) if "failure" in status else returncode


if __name__ == "__main__":
    sys.exit(main())
