#!/usr/bin/env python3
"""Exercise private-file transport with a local shell, without adb or an account."""

import contextlib
import importlib.util
import io
import json
import os
from pathlib import Path
import shlex
import signal
import stat
import subprocess
import tempfile
from types import SimpleNamespace
import unittest
from unittest import mock


SPEC = importlib.util.spec_from_file_location(
    "maestro_transport_runner", Path(__file__).with_name("run-tests.py"),
)
runner = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(runner)

SYNTHETIC_SECRET = "synthetic-세션 'quoted' \"double\"\n$HOME $(printf literal) \\끝"
CREDENTIALS = {
    "authState": {"accessToken": SYNTHETIC_SECRET},
    "memberId": "synthetic-회원-200",
    "fixtureStartDate": "2026-01-02",
}
SESSION_DATA = {"authState": CREDENTIALS["authState"], "memberId": CREDENTIALS["memberId"]}


def publish_file(root, name, payload):
    path = root / name
    path.parent.mkdir(exist_ok=True)
    temporary = path.with_name(path.name + ".new")
    temporary.write_bytes(payload)
    temporary.chmod(0o600)
    temporary.replace(path)


class AdbTransport:
    """Model only adb's command boundary and the setup activity's file exchange."""

    def __init__(self, root):
        self.root = root
        root.mkdir(mode=0o700)
        self.bin = root / "bin"
        self.bin.mkdir(mode=0o700)
        self.result = b'{"status":"ready"}'
        self.session = None
        self.pending_sleeps = 1
        self.started = False
        self.allow_run_as = True
        self.request_bytes = None
        self.request_mode = None
        self.calls = []
        self.private_results = []
        self.before_private = None
        self.after_private = None
        self.on_publish = self.publish

    def publish(self):
        if self.session is not None:
            publish_file(self.root, runner.SESSION, self.session)
        publish_file(self.root, runner.RESULT, self.result)

    def tick(self):
        if not self.started or self.pending_sleeps is None:
            return
        if self.pending_sleeps:
            self.pending_sleeps -= 1
        if self.pending_sleeps == 0:
            self.on_publish()
            self.started = False

    def command(self, argv, env, timeout=60, payload=None):
        if argv[:3] != ["test-adb", "-s", "transport-test"]:
            raise AssertionError("Unexpected test device")
        self.calls.append((tuple(argv), dict(env)))
        args = argv[3:]
        if args == ["get-state"]:
            return 0, b"device\n", b""
        if args[0] == "shell":
            remote = args[1:]
            if remote[:1] == ["-T"]:
                remote = remote[1:]
            if remote == ["am", "force-stop", runner.APP_ID]:
                return 0, b"", b""
            if remote == ["cmd", "package", "resolve-activity", "--brief", "-n", runner.COMPONENT]:
                return 0, runner.COMPONENT.encode() + b"\n", b""
            if remote == ["am", "start", "-W", "-n", runner.COMPONENT]:
                request = self.root / runner.REQUEST
                if request.is_file():
                    self.request_bytes = request.read_bytes()
                    self.request_mode = stat.S_IMODE(request.stat().st_mode)
                    request.unlink()
                self.started = True
                if self.pending_sleeps == 0:
                    self.tick()
                return 0, b"", b""
            # adb shell은 인자를 따로 이스케이프하지 않고 원격 셸에 연결합니다.
            text = " ".join(remote)
            stdin = payload if payload is not None else b""
            stderr = subprocess.PIPE
            forward_exit = True
        elif args[0] == "exec-out":
            remote = args[1:]
            # 기존 경로는 stdin을 보내지 않고 인자를 다시 감싸며 원격 종료 코드를 버립니다.
            text = remote[0] + " " + " ".join(shlex.quote(arg) for arg in remote[1:])
            stdin = b""
            stderr = subprocess.STDOUT
            forward_exit = False
        else:
            raise AssertionError("Unexpected test transport")
        if remote[:2] != ["run-as", runner.APP_ID]:
            raise AssertionError("Only app-private commands may execute locally")
        if self.before_private is not None:
            self.before_private(remote, payload)
        if self.allow_run_as:
            shim = '#!/bin/sh\nshift\nexec "$@"\n'
        else:
            shim = "#!/bin/sh\nprintf '%s\\n' 'synthetic-private-diagnostic' >&2\nexit 1\n"
        # POSIX 셸의 함수명 제약을 피하려고 임시 디렉터리에 run-as 실행 파일을 둡니다.
        run_as = self.bin / "run-as"
        run_as.write_text(shim, encoding="utf-8")
        run_as.chmod(0o700)
        completed = subprocess.run(
            ["/bin/sh", "-c", text], cwd=self.root,
            env={
                "PATH": str(self.bin) + os.pathsep + os.defpath,
                "HOME": str(self.root), "TMPDIR": str(self.root),
            },
            input=stdin, stdout=subprocess.PIPE, stderr=stderr, timeout=timeout,
        )
        outcome = (
            completed.returncode if forward_exit else 0,
            completed.stdout, completed.stderr or b"",
        )
        self.private_results.append(outcome)
        if self.after_private is not None:
            self.after_private(remote, outcome)
        return outcome


class Clock:
    def __init__(self, transport):
        self.now = 0
        self.sleeps = []
        self.transport = transport

    def monotonic(self):
        return self.now

    def sleep(self, seconds):
        self.now += seconds
        self.sleeps.append(seconds)
        self.transport.tick()


class SessionTransportTests(unittest.TestCase):
    def setUp(self):
        # TMPDIR가 레포 안을 가리켜도 export 경로는 레포 밖의 실제 경로로 격리합니다.
        temporary = tempfile.TemporaryDirectory(
            prefix="maestro-transport-test-", dir=Path("/tmp").resolve(),
        )
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name).resolve()
        self.root.chmod(0o700)
        self.host = self.root / "host"
        self.host.mkdir(mode=0o700)
        self.transport = AdbTransport(self.root / "app")
        self.clock = Clock(self.transport)
        patches = (
            mock.patch.object(runner, "command", self.transport.command),
            mock.patch.object(runner, "shutil", SimpleNamespace(which=lambda _name: "test-adb")),
            mock.patch.object(runner, "time", self.clock),
            # export의 신호 처리도 모듈 안에서만 바꿔 다른 테스트의 핸들러를 건드리지 않습니다.
            mock.patch.object(runner, "signal", SimpleNamespace(
                SIGINT=signal.SIGINT, SIGTERM=signal.SIGTERM, SIG_IGN=signal.SIG_IGN,
                signal=mock.Mock(return_value=signal.SIG_DFL),
            )),
        )
        for patch in patches:
            patch.start()
            self.addCleanup(patch.stop)
        self.device = runner.Device("transport-test")

    def assert_failure(self, expected, action):
        stdout, stderr = io.StringIO(), io.StringIO()
        with contextlib.redirect_stdout(stdout), contextlib.redirect_stderr(stderr):
            with self.assertRaises(runner.RunnerFailure) as caught:
                action()
        self.assertEqual(str(caught.exception), expected)
        self.assertEqual(stdout.getvalue() + stderr.getvalue(), "")
        self.assertNotIn(SYNTHETIC_SECRET, str(caught.exception))
        self.assertNotIn("synthetic-private-diagnostic", str(caught.exception))

    def assert_no_credential_arguments(self):
        values = (
            SYNTHETIC_SECRET, json.dumps(SYNTHETIC_SECRET, ensure_ascii=False)[1:-1],
            CREDENTIALS["memberId"], CREDENTIALS["fixtureStartDate"],
        )
        for argv, env in self.transport.calls:
            for value in values:
                self.assertFalse(any(value in arg for arg in argv))
                self.assertFalse(any(value in item for item in env.values()))

    def export_path(self):
        return runner.secure_export_path(str(self.host / "session.json"))

    def configure_export(self):
        self.transport.result = b'{"status":"exported"}'
        self.transport.session = json.dumps(SESSION_DATA, ensure_ascii=False).encode("utf-8")

    def test_prepare_transfers_exact_request_then_waits_for_ready(self):
        self.device.bootstrap("prepare", CREDENTIALS)
        expected = json.dumps(
            {"operation": "prepare", **CREDENTIALS}, ensure_ascii=False, separators=(",", ":"),
        ).encode("utf-8")
        self.assertEqual(self.transport.request_bytes, expected)
        self.assertEqual(json.loads(self.transport.request_bytes)["authState"], CREDENTIALS["authState"])
        self.assertEqual(self.transport.request_mode, 0o600)
        self.assertEqual(self.clock.sleeps, [0.5])
        self.assertTrue(any(code == 3 and output == b"" for code, output, _ in self.transport.private_results))
        self.assertFalse((self.transport.root / runner.REQUEST).exists())
        self.assertFalse((self.transport.root / runner.RESULT).exists())
        self.assert_no_credential_arguments()

    def test_cleanup_waits_for_cleaned_status(self):
        self.transport.result = b'{"status":"cleaned"}'
        self.device.bootstrap("cleanup", CREDENTIALS)
        self.assertEqual(json.loads(self.transport.request_bytes)["operation"], "cleanup")
        self.assertEqual(self.clock.sleeps, [0.5])
        self.assertFalse((self.transport.root / runner.RESULT).exists())
        self.assert_no_credential_arguments()

    def test_offline_observation_keeps_running_process_and_sends_no_auth_state(self):
        self.transport.result = b'{"status":"observed"}'
        request = {
            "memberId": CREDENTIALS["memberId"], "runId": "0123456789abcdef0123456789abcdef",
            "startedAt": "2026-10-15T09:00:00", "endedAt": "2026-10-15T10:00:00",
            "stage": "timers-synced",
        }
        self.device.bootstrap("offline-observe", request)
        self.assertEqual(json.loads(self.transport.request_bytes), {"operation": "offline-observe", **request})
        self.assertFalse(any("force-stop" in argv for argv, _env in self.transport.calls))
        self.assertNotIn("authState", json.loads(self.transport.request_bytes))
        self.assertEqual(self.transport.request_mode, 0o600)
        self.assert_no_credential_arguments()

    def test_offline_prerequisite_failures_use_only_allowlisted_messages(self):
        self.transport.pending_sleeps = 0
        for reason, expected in (
            ("offline_cache_flag_disabled", "offline-suite-requires-enabled-cache-flag"),
            ("offline_upload_not_observed", "offline-suite-requires-real-server-upload"),
            (SYNTHETIC_SECRET, "bootstrap-offline-observe-failed"),
        ):
            with self.subTest(reason=reason):
                self.transport.result = json.dumps({"status": "failed", "reason": reason}).encode()
                self.assert_failure(expected, lambda: self.device.bootstrap("offline-observe", {"stage": "timers-synced"}))

    def test_empty_and_malformed_results_fail_without_polling_again(self):
        self.transport.pending_sleeps = 0
        for payload in (b"", b" \n\t", b'{"status":', b"not-json"):
            with self.subTest(payload=payload):
                self.transport.result = payload
                self.assert_failure("invalid-bootstrap-result", lambda: self.device.bootstrap("prepare", CREDENTIALS))
                self.assertEqual((self.transport.root / runner.RESULT).read_bytes(), payload)
                self.assertEqual(self.clock.sleeps, [])

    def test_wrong_status_and_non_object_results_fail_closed(self):
        self.transport.pending_sleeps = 0
        cases = (
            ("prepare", b'{"status":"exported"}'),
            ("cleanup", b'{"status":"ready"}'),
            ("export", b'{"status":"cleaned"}'),
            ("prepare", b"{}"),
            ("prepare", b"null"),
            ("prepare", b"[]"),
            ("prepare", json.dumps({"status": "failed", "reason": SYNTHETIC_SECRET}).encode("utf-8")),
        )
        for operation, payload in cases:
            with self.subTest(operation=operation, payload=payload):
                self.transport.result = payload
                self.assert_failure(
                    "bootstrap-" + operation + "-failed",
                    lambda: self.device.bootstrap(operation, CREDENTIALS if operation != "export" else None),
                )
                self.assertEqual(self.clock.sleeps, [])
        self.assert_no_credential_arguments()

    def test_verify_setup_rejects_run_as_failure_without_diagnostics(self):
        self.transport.allow_run_as = False
        self.assert_failure("adb-operation-failed", self.device.verify_setup)
        code, output, stderr = self.transport.private_results[-1]
        self.assertNotEqual(code, 0)
        self.assertEqual(output, b"")
        self.assertIn(b"synthetic-private-diagnostic", stderr)

    def test_request_write_failure_is_not_accepted(self):
        def block_request(_remote, payload):
            if payload is not None:
                request = self.transport.root / runner.REQUEST
                request.parent.mkdir(exist_ok=True)
                request.mkdir()

        self.transport.before_private = block_request
        self.assert_failure("adb-operation-failed", lambda: self.device.bootstrap("prepare", CREDENTIALS))
        self.assertTrue((self.transport.root / runner.REQUEST).is_dir())
        self.assertIsNone(self.transport.request_bytes)
        self.assertEqual(self.clock.sleeps, [])
        self.assertNotEqual(self.transport.private_results[-1][0], 0)
        self.assert_no_credential_arguments()

    def test_existing_unreadable_result_is_not_pending(self):
        self.transport.pending_sleeps = 0
        self.transport.on_publish = lambda: (self.transport.root / runner.RESULT).mkdir()
        self.assert_failure("adb-operation-failed", lambda: self.device.bootstrap("prepare", CREDENTIALS))
        code, output, stderr = self.transport.private_results[-1]
        self.assertNotIn(code, (0, 3))
        self.assertEqual(output, b"")
        self.assertTrue(stderr)
        self.assertEqual(self.clock.sleeps, [])
        self.assert_no_credential_arguments()

    def test_pending_result_times_out_deterministically(self):
        self.transport.pending_sleeps = None
        self.assert_failure(
            "bootstrap-prepare-timeout", lambda: self.device.bootstrap("prepare", CREDENTIALS, timeout=1),
        )
        self.assertEqual(self.clock.sleeps, [0.5, 0.5])
        self.assertEqual(self.clock.now, 1)
        pending = [code for code, _, _ in self.transport.private_results if code == 3]
        self.assertEqual(pending, [3, 3])
        self.assertFalse((self.transport.root / runner.RESULT).exists())
        self.assert_no_credential_arguments()

    def test_export_waits_for_exported_and_writes_private_json(self):
        self.configure_export()
        output = self.export_path()
        runner.export_session(self.device, output)
        self.assertEqual(json.loads(output.read_bytes()), SESSION_DATA)
        self.assertEqual(stat.S_IMODE(output.stat().st_mode), 0o600)
        self.assertEqual(stat.S_IMODE(output.parent.stat().st_mode), 0o700)
        self.assertNotIn(runner.ROOT, output.parents)
        self.assertEqual(json.loads(self.transport.request_bytes), {"operation": "export"})
        self.assertEqual(self.clock.sleeps, [0.5])
        self.assertTrue(any(code == 3 for code, _, _ in self.transport.private_results))
        self.assertEqual(list((self.transport.root / "files").iterdir()), [])
        self.assert_no_credential_arguments()

    def test_session_read_failure_does_not_create_host_export(self):
        self.configure_export()
        self.transport.pending_sleeps = 0
        session = self.transport.root / runner.SESSION

        def publish_unreadable_session():
            session.parent.mkdir(exist_ok=True)
            session.mkdir()
            publish_file(self.transport.root, runner.RESULT, self.transport.result)

        read_failures = []

        def release_obstruction(remote, outcome):
            if remote[-2:] == ["cat", runner.SESSION]:
                read_failures.append(outcome)
                session.rmdir()

        self.transport.on_publish = publish_unreadable_session
        self.transport.after_private = release_obstruction
        output = self.export_path()
        self.assert_failure("adb-operation-failed", lambda: runner.export_session(self.device, output))
        self.assertFalse(output.exists())
        self.assertEqual(len(read_failures), 1)
        self.assertNotEqual(read_failures[0][0], 0)
        self.assertTrue(read_failures[0][2])
        self.assertEqual(list((self.transport.root / "files").iterdir()), [])
        self.assert_no_credential_arguments()

    def test_cleanup_failure_removes_newly_written_host_export(self):
        self.configure_export()
        output = self.export_path()
        written = []

        def block_final_cleanup(remote, _payload):
            if remote[2:4] == ["rm", "-f"] and output.exists():
                written.append((output.read_bytes(), stat.S_IMODE(output.stat().st_mode)))
                (self.transport.root / (runner.SESSION + ".bak")).mkdir()

        self.transport.before_private = block_final_cleanup
        self.assert_failure("export-private-cleanup-failed", lambda: runner.export_session(self.device, output))
        self.assertEqual(len(written), 1)
        self.assertEqual(json.loads(written[0][0]), SESSION_DATA)
        self.assertEqual(written[0][1], 0o600)
        self.assertFalse(output.exists())
        self.assertNotEqual(self.transport.private_results[-1][0], 0)
        self.assert_no_credential_arguments()

    def test_remove_private_preserves_session_until_explicit_cleanup(self):
        groups = {}
        for name in (runner.REQUEST, runner.RESULT, runner.SESSION):
            groups[name] = [self.transport.root / (name + suffix) for suffix in ("", ".new", ".bak")]
            for path in groups[name]:
                path.parent.mkdir(exist_ok=True)
                path.write_bytes(b"synthetic-private-file")
        unrelated = self.transport.root / "files" / "unrelated.json"
        unrelated.write_bytes(b"keep")
        self.device.remove_private()
        for name in (runner.REQUEST, runner.RESULT):
            self.assertTrue(all(not path.exists() for path in groups[name]))
        self.assertTrue(all(path.exists() for path in groups[runner.SESSION]))
        self.device.remove_private(include_session=True)
        self.assertTrue(all(not path.exists() for path in groups[runner.SESSION]))
        self.assertEqual(unrelated.read_bytes(), b"keep")


class NetworkClock:
    def __init__(self):
        self.now = 0

    def monotonic(self):
        return self.now

    def sleep(self, seconds):
        self.now += seconds


class RadioDevice:
    def __init__(self):
        self.values = {"wifi_on": b"0", "mobile_data": b"1", "airplane_mode_on": b"0"}
        self.calls = []
        self.external_network = False
        self.failed_radio = None
        self.report = None

    def require(self, *args):
        self.calls.append(args)
        if args[:4] == ("shell", "settings", "get", "global"):
            return self.values[args[4]] + b"\n"
        if args == ("shell", "dumpsys", "connectivity"):
            if self.report is not None:
                return self.report
            connected = self.external_network or any(self.values[key] == b"1" for key in ("wifi_on", "mobile_data"))
            active = b"100" if connected else b"none"
            return b"Active default network: " + active + b"\nHistory: VALIDATED network 99\n"
        if args[:2] == ("shell", "svc"):
            if args[2] == self.failed_radio:
                raise runner.RunnerFailure("adb-operation-failed")
            key = {"wifi": "wifi_on", "data": "mobile_data"}[args[2]]
            self.values[key] = b"1" if args[3] == "enable" else b"0"
            return b""
        raise AssertionError("Unexpected radio command")


class OfflineNetworkTests(unittest.TestCase):
    def setUp(self):
        self.device = RadioDevice()
        self.clock = NetworkClock()
        patch = mock.patch.object(runner, "time", self.clock)
        patch.start()
        self.addCleanup(patch.stop)

    def test_disconnect_checks_current_network_and_restores_original_radios(self):
        original = dict(self.device.values)
        network = runner.NetworkState(self.device)
        network.disconnect()
        self.assertFalse(network.connected())
        self.assertEqual(self.device.values["mobile_data"], b"0")
        network.restore()
        self.assertEqual(self.device.values, original)
        self.assertTrue(network.connected())
        self.assertNotIn(("shell", "svc", "wifi", "enable"), self.device.calls)

    def test_ethernet_or_other_active_network_prevents_false_offline_success(self):
        network = runner.NetworkState(self.device)
        self.device.external_network = True
        with self.assertRaisesRegex(runner.RunnerFailure, "^device-network-transition-not-observed$"):
            network.disconnect()
        self.assertEqual(self.clock.now, 30)
        network.restore()
        self.assertEqual(self.device.values["mobile_data"], b"1")

    def test_restore_attempts_both_radios_when_one_command_fails(self):
        network = runner.NetworkState(self.device)
        network.disconnect()
        self.device.failed_radio = "wifi"
        with self.assertRaisesRegex(runner.RunnerFailure, "^device-network-restore-failed$"):
            network.restore()
        self.assertEqual(self.device.values["mobile_data"], b"1")
        self.assertIn(("shell", "svc", "data", "enable"), self.device.calls)

    def test_unknown_settings_and_reports_fail_before_radio_changes(self):
        for report in (b"", b"History: Active default network: 100\n", b"Active default network: none\nActive default network: 100\n"):
            with self.subTest(report=report):
                self.device.report = report
                with self.assertRaisesRegex(runner.RunnerFailure, "^unsupported-device-connectivity-report$"):
                    runner.NetworkState(self.device)
        self.device.values["mobile_data"] = b"null"
        with self.assertRaisesRegex(runner.RunnerFailure, "^unsupported-device-network-settings$"):
            runner.NetworkState(self.device)
        self.assertFalse(any(args[:2] == ("shell", "svc") for args in self.device.calls))

    def test_initially_offline_device_is_not_silently_reconfigured(self):
        self.device.values["mobile_data"] = b"0"
        with self.assertRaisesRegex(runner.RunnerFailure, "^offline-suite-requires-initial-online-network$"):
            runner.NetworkState(self.device)
        self.assertFalse(any(args[:2] == ("shell", "svc") for args in self.device.calls))


class OfflineSuiteTests(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory(prefix="maestro-offline-unit-")
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name)
        for phase in runner.OFFLINE_PHASES["offline-custom-timers"]:
            path = self.root / ".maestro" / "offline" / (phase + ".yaml")
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text("synthetic unit-test flow", encoding="utf-8")
        self.events = []
        self.fail_phase = None
        self.fail_observe = False
        self.fail_restore = False
        self.fail_cleanup = False
        self.interrupted = False
        self.device = mock.Mock()
        self.device.clear.side_effect = lambda: self.events.append("clear")
        self.device.bootstrap.side_effect = self.bootstrap
        self.device.exercise_variables.return_value = {
            "EXERCISE_TITLE": "maestro-e2e-0123456789abcdef0123456789abcdef",
            "EDITED_EXERCISE_TITLE": "maestro-e2e-0123456789abcdef0123456789abcdef-edited",
            "EXERCISE_START_DATE": "2026.10.15", "EXERCISE_END_DATE": "2026.10.15",
            "EXERCISE_START_HOUR": "09", "EXERCISE_END_HOUR": "10",
        }
        self.network = mock.Mock()
        self.network.disconnect.side_effect = lambda: self.events.append("disconnect")
        self.network.restore.side_effect = self.restore
        patches = (
            mock.patch.object(runner, "ROOT", self.root),
            mock.patch.dict(runner.FLOWS, {"offline": ("offline-custom-timers",)}),
            mock.patch.object(runner, "Device", return_value=self.device),
            mock.patch.object(runner, "NetworkState", return_value=self.network),
            mock.patch.object(runner, "read_credentials", return_value=CREDENTIALS),
            mock.patch.object(runner, "run_maestro", side_effect=self.phase),
            mock.patch.object(runner, "signal", SimpleNamespace(
                SIGINT=signal.SIGINT, SIGTERM=signal.SIGTERM, SIG_IGN=signal.SIG_IGN,
                signal=mock.Mock(return_value=signal.SIG_DFL),
            )),
        )
        for patch in patches:
            patch.start()
            self.addCleanup(patch.stop)

    def bootstrap(self, operation, request):
        stage = request.get("stage")
        self.events.append(operation + (":" + stage if stage else ""))
        self.assertEqual(request["memberId"], CREDENTIALS["memberId"])
        self.assertEqual(request["runId"], "0123456789abcdef0123456789abcdef")
        if operation == "offline-observe":
            self.assertNotIn("authState", request)
            if self.fail_observe and stage == "timers-synced":
                raise runner.RunnerFailure("offline-suite-requires-real-server-upload")
        else:
            self.assertEqual(request["authState"], CREDENTIALS["authState"])
        if operation == "offline-cleanup" and self.fail_cleanup:
            raise runner.RunnerFailure("bootstrap-offline-cleanup-failed")

    def restore(self):
        self.events.append("restore")
        if self.fail_restore:
            raise runner.RunnerFailure("device-network-restore-failed")

    def phase(self, _device, name, _output, _redactor, _timeout, **kwargs):
        self.events.append(name)
        self.assertEqual(kwargs["variables"]["TIMER_CREATE_TITLE"], "maestro-offline-0123456789abcdef0123456789abcdef-create")
        self.assertNotIn("authState", kwargs["variables"])
        self.assertNotIn("memberId", kwargs["variables"])
        if name == self.fail_phase:
            failure = runner.RunInterrupted if self.interrupted else runner.RunnerFailure
            raise failure("run-interrupted" if self.interrupted else "maestro-flow-failed")

    def run_suite(self):
        args = SimpleNamespace(suite="offline", device="synthetic", configure_emulator=False, apk=None, flow_timeout=42)
        output = io.StringIO()
        with contextlib.redirect_stdout(output), contextlib.redirect_stderr(output):
            result = runner.run_suite(args)
        self.assertNotIn(SYNTHETIC_SECRET, output.getvalue())
        reports = list((self.root / "build" / "maestro").glob("*/report.xml"))
        self.assertEqual(len(reports), 1)
        report = runner.ET.parse(reports[0]).getroot()
        self.assertEqual(report.get("skipped"), "0")
        return result, report

    def test_success_requires_offline_ui_reconnect_outcome_upload_and_preserved_form(self):
        result, report = self.run_suite()
        self.assertEqual(result, 0)
        self.assertEqual(report.get("failures"), "0")
        self.assertEqual(self.events, [
            "clear", "offline-prepare", "offline-timers-online", "offline-observe:timers-online",
            "disconnect", "offline-timers-mutate", "offline-timers-relaunch", "restore",
            "offline-timers-after-reconnect", "offline-observe:timers-synced", "offline-timers-reconnect",
            "restore", "offline-cleanup", "clear",
        ])

    def test_ui_failure_and_interruption_restore_before_scoped_cleanup(self):
        self.fail_phase = "offline-timers-relaunch"
        for interrupted in (False, True):
            with self.subTest(interrupted=interrupted):
                self.interrupted = interrupted
                self.events.clear()
                # 실행마다 별도 디렉터리를 써서 보고서 수가 타임스탬프에 의존하지 않게 합니다.
                with tempfile.TemporaryDirectory(dir=self.root) as directory:
                    build = Path(directory)
                    with mock.patch.object(runner, "ROOT", build), mock.patch.object(Path, "is_file", return_value=True):
                        args = SimpleNamespace(suite="offline", device="synthetic", configure_emulator=False, apk=None, flow_timeout=42)
                        with contextlib.redirect_stdout(io.StringIO()):
                            self.assertEqual(runner.run_suite(args), 1)
                self.assertEqual(self.events[-3:], ["restore", "offline-cleanup", "clear"])
                self.assertNotIn("offline-timers-after-reconnect", self.events)

    def test_missing_real_upload_fails_without_skip_and_still_restores(self):
        self.fail_observe = True
        result, report = self.run_suite()
        self.assertEqual(result, 1)
        self.assertIn("offline-suite-requires-real-server-upload", report.find("testcase/failure").get("message"))
        self.assertNotIn("offline-timers-reconnect", self.events)
        self.assertEqual(self.events[-3:], ["restore", "offline-cleanup", "clear"])

    def test_prepare_failure_restores_without_running_any_ui_phase(self):
        original_bootstrap = self.bootstrap

        def fail_prepare(operation, request):
            if operation == "offline-prepare":
                self.events.append(operation)
                raise runner.RunnerFailure("offline-suite-requires-enabled-cache-flag")
            original_bootstrap(operation, request)

        self.device.bootstrap.side_effect = fail_prepare
        result, report = self.run_suite()
        self.assertEqual(result, 1)
        self.assertIn("offline-suite-requires-enabled-cache-flag", report.find("testcase/failure").get("message"))
        self.assertEqual(self.events, ["clear", "offline-prepare", "restore", "offline-cleanup", "clear"])

    def test_failed_restore_prevents_server_cleanup_and_fails_the_report(self):
        self.fail_phase = "offline-timers-mutate"
        self.fail_restore = True
        result, report = self.run_suite()
        self.assertEqual(result, 1)
        self.assertIn("device-network-restore-failed", report.find("testcase/failure").get("message"))
        self.assertNotIn("offline-cleanup", self.events)
        self.assertEqual(self.events[-1], "clear")

    def test_cleanup_failure_cannot_be_reported_as_a_pass(self):
        self.fail_cleanup = True
        result, report = self.run_suite()
        self.assertEqual(result, 1)
        self.assertIn("fixture-cleanup-failed", report.find("testcase/failure").get("message"))
        self.assertEqual(self.events[-3:], ["restore", "offline-cleanup", "clear"])

    def test_offline_remains_explicit_and_outside_every_default_suite(self):
        for suite in ("smoke", "regression", "all", "login"):
            self.assertFalse(any(flow.startswith("offline-") for flow in runner.FLOWS[suite]))


class OfflineExerciseOrderTests(unittest.TestCase):
    def test_cache_hit_and_miss_use_distinct_preconditions_and_relaunch_phases(self):
        variables = {
            "EXERCISE_TITLE": "maestro-e2e-0123456789abcdef0123456789abcdef",
            "EXERCISE_START_DATE": "2026.10.15", "EXERCISE_END_DATE": "2026.10.15",
            "EXERCISE_START_HOUR": "09", "EXERCISE_END_HOUR": "10",
        }
        for scenario, expected in (
            ("offline-exercise-cache", ["offline-exercise-create", "exercise-online", "disconnect", "offline-exercise-cached"]),
            ("offline-exercise-miss", ["disconnect", "offline-exercise-missing"]),
        ):
            with self.subTest(scenario=scenario):
                events = []
                device, network = mock.Mock(), mock.Mock()
                device.bootstrap.side_effect = lambda _operation, request: events.append(request["stage"])
                network.disconnect.side_effect = lambda: events.append("disconnect")
                with mock.patch.object(runner, "run_maestro", side_effect=lambda _device, phase, *_args, **_kwargs: events.append(phase)):
                    runner.run_offline_scenario(
                        device, scenario, network, CREDENTIALS, variables, Path("/synthetic-output"), runner.Redactor(CREDENTIALS), 42,
                    )
                self.assertEqual(events, expected)
                device.clear.assert_not_called()
                network.restore.assert_not_called()


if __name__ == "__main__":
    unittest.main()
