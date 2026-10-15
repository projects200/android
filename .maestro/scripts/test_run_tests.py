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

    def test_feature_metadata_uses_private_stdin_and_returns_safe_variables(self):
        self.transport.result = b'{"status":"ready","variables":{"E2E_MARKER":"run-owned"}}'
        parameters = {"MAESTRO_PEER_MEMBER_ID": "synthetic-peer"}
        result = self.device.bootstrap(
            "prepare", CREDENTIALS, feature="matching", flow="matching-map",
            run_id="0123456789abcdef", parameters=parameters,
        )
        request = json.loads(self.transport.request_bytes)
        self.assertEqual(request["parameters"], parameters)
        self.assertEqual(request["runId"], "0123456789abcdef")
        self.assertEqual(result, {"E2E_MARKER": "run-owned"})
        self.assert_no_credential_arguments()
        for argv, env in self.transport.calls:
            self.assertFalse(any("synthetic-peer" in value for value in (*argv, *env.values())))

    def test_bootstrap_refuses_sensitive_return_variables(self):
        self.transport.result = b'{"status":"ready","variables":{"FCM_TOKEN":"private"}}'
        self.assert_failure(
            "invalid-feature-variables", lambda: self.device.bootstrap("prepare", CREDENTIALS),
        )

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


if __name__ == "__main__":
    unittest.main()
