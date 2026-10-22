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

    def test_profile_prepare_discloses_only_fixed_reviewed_failures(self):
        self.transport.pending_sleeps = 0
        cases = (
            ("profile_baseline_nickname_unsupported",
             "bootstrap-prepare-failed-profile-baseline-nickname-unsupported"),
            ("profile_baseline_gender_unsupported",
             "bootstrap-prepare-failed-profile-baseline-gender-unsupported"),
            ("profile_baseline_bio_null", "bootstrap-prepare-failed-profile-baseline-bio-null"),
            ("profile_baseline_bio_too_long", "bootstrap-prepare-failed-profile-baseline-bio-too-long"),
            ("profile_baseline_bio_control_characters",
             "bootstrap-prepare-failed-profile-baseline-bio-control-characters"),
            ("profile_fcm_token_timeout", "bootstrap-prepare-failed-profile-fcm-token-timeout"),
        )
        for reason, expected in cases:
            with self.subTest(reason=reason):
                self.transport.result = json.dumps({
                    "status": "failed", "reason": reason, "private": SYNTHETIC_SECRET,
                }).encode("utf-8")
                self.assert_failure(
                    expected,
                    lambda: self.device.bootstrap(
                        "prepare", CREDENTIALS, feature="profile", flow="profile-edit",
                        run_id="0123456789abcdef", parameters={},
                    ),
                )
                self.assertTrue((self.transport.root / runner.RESULT).exists())
                self.assertEqual(self.clock.sleeps, [])
        self.assert_no_credential_arguments()

    def test_profile_prepare_unknown_and_non_string_reasons_remain_generic(self):
        self.transport.pending_sleeps = 0
        reasons = (
            "profile_unknown_failure",
            "profile_baseline_text_unsupported",
            "profile_baseline_nickname_unsupported-" + SYNTHETIC_SECRET,
            SYNTHETIC_SECRET,
            {"code": "profile_baseline_bio_null", "private": SYNTHETIC_SECRET},
            ["profile_baseline_bio_control_characters", SYNTHETIC_SECRET],
            None,
            7,
            True,
        )
        for index, reason in enumerate(reasons):
            with self.subTest(case=index):
                self.transport.result = json.dumps({"status": "failed", "reason": reason}).encode("utf-8")
                self.assert_failure(
                    "bootstrap-prepare-failed",
                    lambda: self.device.bootstrap(
                        "prepare", CREDENTIALS, feature="profile", flow="profile-edit",
                        run_id="0123456789abcdef", parameters={},
                    ),
                )
                self.assertTrue((self.transport.root / runner.RESULT).exists())
                self.assertEqual(self.clock.sleeps, [])
        self.assert_no_credential_arguments()

    def test_reviewed_profile_reason_outside_failed_profile_prepare_remains_generic(self):
        self.transport.pending_sleeps = 0
        cases = (
            ("prepare", "profile", "exported", "bootstrap-prepare-failed"),
            ("prepare", "profile", None, "bootstrap-prepare-failed"),
            ("prepare", "matching", "failed", "bootstrap-prepare-failed"),
            ("cleanup", "profile", "failed", "bootstrap-cleanup-failed"),
            ("prepare", None, "failed", "bootstrap-prepare-failed"),
        )
        for reason in ("profile_baseline_bio_null", "profile_fcm_token_timeout"):
            for operation, feature, status, expected in cases:
                with self.subTest(reason=reason, operation=operation, feature=feature, status=status):
                    payload = json.dumps({
                        "status": status, "reason": reason, "private": SYNTHETIC_SECRET,
                    }).encode("utf-8")
                    self.transport.result = payload
                    metadata = {}
                    if feature is not None:
                        metadata = {
                            "feature": feature,
                            "flow": "profile-preferred-exercise" if feature == "profile" else "matching-place-crud",
                            "run_id": "0123456789abcdef", "parameters": {},
                        }
                    self.assert_failure(
                        expected, lambda: self.device.bootstrap(operation, CREDENTIALS, **metadata),
                    )
                    self.assertEqual((self.transport.root / runner.RESULT).read_bytes(), payload)
                    self.assertEqual(self.clock.sleeps, [])
        self.assert_no_credential_arguments()

    def test_common_prepare_failure_reasons_use_fixed_codes(self):
        self.transport.pending_sleeps = 0
        cases = (
            ("discovery_failed", "bootstrap-prepare-failed-discovery"),
            ("refresh_failed", "bootstrap-prepare-failed-refresh"),
            ("registration_failed", "bootstrap-prepare-failed-registration"),
            ("fixture_read_failed", "bootstrap-prepare-failed-fixture-read"),
            ("fixture_delete_failed", "bootstrap-prepare-failed-fixture-delete"),
            ("setup_failed", "bootstrap-prepare-failed-setup"),
        )
        for reason, expected in cases:
            with self.subTest(reason=reason):
                self.transport.result = json.dumps({"status": "failed", "reason": reason}).encode("utf-8")
                self.assert_failure(
                    expected,
                    lambda: self.device.bootstrap(
                        "prepare",
                        CREDENTIALS,
                    ),
                )
        self.assert_no_credential_arguments()

    def test_matching_prepare_failure_reason_is_exposed_as_fixed_code(self):
        self.transport.pending_sleeps = 0
        cases = (
            ("matching_peer_unavailable", "bootstrap-prepare-failed-matching-peer-unavailable"),
            ("matching_existing_peer_room_required", "bootstrap-prepare-failed-matching-existing-peer-room-required"),
            ("matching_api_read_failed", "bootstrap-prepare-failed-matching-api-read-failed"),
        )
        for reason, expected in cases:
            with self.subTest(reason=reason):
                payload = json.dumps({
                    "status": "failed", "reason": reason, "message": SYNTHETIC_SECRET,
                    "variables": {"MATCHING_PEER_ID": SYNTHETIC_SECRET},
                    "details": {"response": SYNTHETIC_SECRET},
                }, ensure_ascii=False).encode("utf-8")
                self.transport.result = payload
                self.assert_failure(
                    expected,
                    lambda: self.device.bootstrap(
                        "prepare",
                        CREDENTIALS,
                        feature="matching",
                        flow="matching-place-crud",
                        run_id="0123456789abcdef",
                        parameters={},
                    ),
                )
                self.assertEqual((self.transport.root / runner.RESULT).read_bytes(), payload)
                self.assertEqual(self.clock.sleeps, [])
        self.assert_no_credential_arguments()

    def test_matching_failure_reason_is_not_exposed_outside_prepare(self):
        self.transport.pending_sleeps = 0
        metadata = {"flow": "matching-place-crud", "run_id": "0123456789abcdef", "parameters": {}}
        matching = {"feature": "matching", **metadata}
        profile = {"feature": "profile", **metadata, "flow": "profile-preferred-exercise"}
        cases = (
            ("matching-cleanup", "cleanup", matching, "failed"),
            ("other-feature", "prepare", {"feature": "exercise", **metadata}, "failed"),
            ("profile-prepare", "prepare", profile, "failed"),
            ("absent-feature", "prepare", {}, "failed"),
            ("wrong-status", "prepare", matching, "exported"),
            ("absent-status", "prepare", matching, None),
            ("export", "export", {}, "failed"),
        )
        for name, operation, arguments, status in cases:
            with self.subTest(case=name):
                result = {"reason": "matching_peer_unavailable", "details": SYNTHETIC_SECRET}
                if status is not None:
                    result["status"] = status
                payload = json.dumps(result, ensure_ascii=False).encode("utf-8")
                self.transport.result = payload
                self.assert_failure(
                    "bootstrap-" + operation + "-failed",
                    lambda operation=operation: self.device.bootstrap(
                        operation, CREDENTIALS if operation != "export" else None, **arguments,
                    ),
                )
                self.assertEqual((self.transport.root / runner.RESULT).read_bytes(), payload)
                self.assertEqual(self.clock.sleeps, [])
        self.assert_no_credential_arguments()

    def test_matching_prepare_rejects_unreviewed_or_malformed_reasons(self):
        self.transport.pending_sleeps = 0
        known = "matching_peer_unavailable"
        reasons = (
            "matching_unknown_failure",
            "setup_failed",
            known + SYNTHETIC_SECRET,
            SYNTHETIC_SECRET + known,
            SYNTHETIC_SECRET,
            {"reason": known, "secret": SYNTHETIC_SECRET},
            [known, SYNTHETIC_SECRET],
            None,
            1,
            True,
        )
        for index, reason in enumerate(reasons):
            with self.subTest(reason_index=index):
                payload = json.dumps({
                    "status": "failed", "reason": reason, "details": SYNTHETIC_SECRET,
                }, ensure_ascii=False).encode("utf-8")
                self.transport.result = payload
                self.assert_failure(
                    "bootstrap-prepare-failed",
                    lambda: self.device.bootstrap(
                        "prepare", CREDENTIALS, feature="matching", flow="matching-place-crud",
                        run_id="0123456789abcdef", parameters={},
                    ),
                )
                self.assertEqual((self.transport.root / runner.RESULT).read_bytes(), payload)
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


class MaestroFailureArtifactTests(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory(
            prefix="maestro-failure-test-", dir=Path("/tmp").resolve(),
        )
        self.addCleanup(temporary.cleanup)
        self.output = Path(temporary.name) / "chatting" / "a-to-b-open-receiver" / "b"
        self.flow = "chatting-open"
        self.destination = self.output / self.flow
        self.escaped_secret = json.dumps(SYNTHETIC_SECRET, ensure_ascii=False)[1:-1]
        self.console = ("Maestro UI assertion failed: " + SYNTHETIC_SECRET).encode("utf-8")
        self.device = SimpleNamespace(
            serial="failure-test", exercise_variables=mock.Mock(return_value={}), call=mock.Mock(),
        )

        def command(argv, _env, timeout=60, payload=None):
            if argv[1:] == ["--version"]:
                return 0, runner.MAESTRO_VERSION.encode("utf-8"), b""
            if argv[1:2] == ["test"]:
                return 1, self.console, b""
            raise AssertionError("Unexpected Maestro operation")

        patches = (
            mock.patch.object(runner, "command", side_effect=command),
            mock.patch.object(runner, "shutil", SimpleNamespace(which=lambda _name: "test-maestro")),
        )
        for patch in patches:
            patch.start()
            self.addCleanup(patch.stop)

    def test_first_nested_failure_preserves_redacted_diagnostics_and_original_failure(self):
        unrelated = "I/Other: synthetic-unrelated-device-line"
        self.device.call.return_value = (
            0,
            ("E/OkHttp: WebSocket handshake failed: " + self.escaped_secret + "\n"
             + unrelated + "\n").encode("utf-8"),
            b"",
        )
        self.assertFalse(self.output.exists())
        with self.assertRaises(runner.RunnerFailure) as caught:
            runner.run_maestro(
                self.device, self.flow, self.output, runner.Redactor(CREDENTIALS), timeout=60,
            )
        self.assertEqual(str(caught.exception), "maestro-flow-failed")
        console = (self.destination / "console.log").read_text(encoding="utf-8")
        diagnostic = (self.destination / "device-log.log").read_text(encoding="utf-8")
        self.assertEqual(console, "Maestro UI assertion failed: [REDACTED]")
        self.assertEqual(diagnostic, "E/OkHttp: WebSocket handshake failed: [REDACTED]")
        for content in (console, diagnostic):
            self.assertNotIn(SYNTHETIC_SECRET, content)
            self.assertNotIn(self.escaped_secret, content)
            self.assertNotIn(unrelated, content)

    def test_unavailable_logcat_preserves_original_failure_and_console(self):
        self.device.call.return_value = (
            1, ("E/WebSocket: " + self.escaped_secret).encode("utf-8"),
            b"synthetic-private-logcat-error",
        )
        self.assertFalse(self.output.exists())
        with self.assertRaises(runner.RunnerFailure) as caught:
            runner.run_maestro(
                self.device, self.flow, self.output, runner.Redactor(CREDENTIALS), timeout=60,
            )
        self.assertEqual(str(caught.exception), "maestro-flow-failed")
        self.assertEqual(
            (self.destination / "console.log").read_text(encoding="utf-8"),
            "Maestro UI assertion failed: [REDACTED]",
        )
        self.assertFalse((self.destination / "device-log.log").exists())

    def test_logcat_capture_failure_preserves_original_failure_and_console(self):
        self.device.call.side_effect = runner.RunnerFailure("tool-timeout")
        self.assertFalse(self.output.exists())
        published = io.StringIO()
        with contextlib.redirect_stdout(published), contextlib.redirect_stderr(published):
            with self.assertRaises(runner.RunnerFailure) as caught:
                runner.run_maestro(
                    self.device, self.flow, self.output, runner.Redactor(CREDENTIALS), timeout=60,
                )
        self.assertEqual(str(caught.exception), "maestro-flow-failed")
        self.assertEqual(
            (self.destination / "console.log").read_text(encoding="utf-8"),
            "Maestro UI assertion failed: [REDACTED]",
        )
        self.assertEqual(published.getvalue(), "")
        self.assertFalse((self.destination / "device-log.log").exists())

    def test_logcat_write_failure_preserves_original_failure_without_private_error(self):
        self.device.call.return_value = (
            0, ("E/OkHttp: " + self.escaped_secret).encode("utf-8"), b"",
        )
        private_error = "synthetic-private-device-log-error: " + SYNTHETIC_SECRET
        write_text = Path.write_text

        def fail_device_log_write(path, *args, **kwargs):
            if path == self.destination / "device-log.log":
                raise OSError(private_error)
            return write_text(path, *args, **kwargs)

        self.assertFalse(self.output.exists())
        published = io.StringIO()
        with mock.patch.object(Path, "write_text", new=fail_device_log_write):
            with contextlib.redirect_stdout(published), contextlib.redirect_stderr(published):
                with self.assertRaises(runner.RunnerFailure) as caught:
                    runner.run_maestro(
                        self.device, self.flow, self.output, runner.Redactor(CREDENTIALS), timeout=60,
                    )
        self.assertEqual(str(caught.exception), "maestro-flow-failed")
        console = (self.destination / "console.log").read_text(encoding="utf-8")
        self.assertEqual(console, "Maestro UI assertion failed: [REDACTED]")
        self.assertEqual(published.getvalue(), "")
        for private in (private_error, SYNTHETIC_SECRET, self.escaped_secret):
            self.assertNotIn(private, str(caught.exception) + console + published.getvalue())
        self.assertFalse((self.destination / "device-log.log").exists())

    def test_logcat_interruption_propagates_unchanged(self):
        interruption = runner.RunInterrupted("run-interrupted")
        self.device.call.side_effect = interruption
        self.assertFalse(self.output.exists())
        with self.assertRaises(runner.RunInterrupted) as caught:
            runner.run_maestro(
                self.device, self.flow, self.output, runner.Redactor(CREDENTIALS), timeout=60,
            )
        self.assertIs(caught.exception, interruption)
        self.assertEqual(
            (self.destination / "console.log").read_text(encoding="utf-8"),
            "Maestro UI assertion failed: [REDACTED]",
        )
        self.assertFalse((self.destination / "device-log.log").exists())

    def test_system_ui_anr_summary_keeps_only_categories_and_load(self):
        private = "synthetic-private-window-and-intent"
        report = (
            "ACTIVITY MANAGER LAST ANR (dumpsys activity lastanr)\n"
            "ANR in com.android.systemui\nPID: 6175\n"
            "Reason: Broadcast of Intent { " + private + " }\n"
            "Load: 14.67 / 12.04 / 6.64\n"
            "  98.5% TOTAL: 70% user + 28.5% kernel\n"
            "Subject: " + self.escaped_secret + "\n"
        )
        self.device.call.side_effect = [
            (0, b"", b""), (0, report.encode("utf-8"), b""), (0, b"", b""),
        ]
        with self.assertRaises(runner.RunnerFailure) as caught:
            runner.run_maestro(
                self.device, self.flow, self.output, runner.Redactor(CREDENTIALS), timeout=60,
            )
        self.assertEqual(str(caught.exception), "maestro-flow-failed")
        self.assertEqual(
            json.loads((self.destination / "last-anr-summary.json").read_text(encoding="utf-8")),
            {
                "process": "system-ui", "reason": "broadcast",
                "load_average": [14.67, 12.04, 6.64], "total_cpu_percent": 98.5,
            },
        )
        for path in self.destination.iterdir():
            content = path.read_text(encoding="utf-8")
            for value in (
                private, self.escaped_secret, SYNTHETIC_SECRET, "com.android.systemui", "6175",
            ):
                self.assertNotIn(value, content)

    def test_android14_indented_anr_reason_does_not_infer_process_from_activity_records(self):
        private = "synthetic-private-window-and-intent"
        for indentation, reason, category in (
            ("  ", "Input dispatching timed out (" + private + ")", "input-dispatch"),
            ("\t", "Broadcast of Intent { " + private + " }", "broadcast"),
            (" \t", "executing service synthetic.private.service", "service"),
            ("  ", "ContentProvider not responding: synthetic.private.provider", "content-provider"),
            ("  ", private, "unknown"),
            ("  ", "", "unknown"),
            ("  ", None, "unknown"),
        ):
            with self.subTest(indentation=indentation, reason=reason):
                reason_line = indentation + "Reason: " + reason + "\n" if reason is not None else ""
                # Android 14의 들여쓴 Reason만 분류하고 활동·창 기록에서 ANR 프로세스를 추측하지 않습니다.
                report = (
                    "ACTIVITY MANAGER LAST ANR (dumpsys activity lastanr)\n"
                    "  ANR time: Oct 22, 2026, 1:12:34 PM\n"
                    + reason_line
                    + "\nACTIVITY MANAGER STARTER (dumpsys activity starter)\n"
                    "  mLastStartActivityRecord=ActivityRecord{synthetic u0 "
                    + runner.APP_ID + "/.MainActivity t42}\n"
                    "\nACTIVITY MANAGER ACTIVITIES (dumpsys activity activities)\n"
                    "  * Hist #0: ActivityRecord{synthetic u0 "
                    + runner.APP_ID + "/.MainActivity t42}\n"
                    "    intent={act=android.intent.action.MAIN " + private + "}\n"
                    "  mCurrentFocus=Window{synthetic u0 com.android.systemui/StatusBar}\n"
                    "  pid=6175 uid=1000 https://synthetic.invalid/?token=synthetic-token\n"
                    '  authState={"accessToken":"' + self.escaped_secret + '"}\n'
                    "  memberId=" + CREDENTIALS["memberId"] + "\n"
                )
                self.device.call.side_effect = [
                    (0, b"", b""), (0, report.encode("utf-8"), b"synthetic-private-command-stderr"),
                    (0, b"", b""),
                ]
                published = io.StringIO()
                with contextlib.redirect_stdout(published), contextlib.redirect_stderr(published):
                    with self.assertRaises(runner.RunnerFailure) as caught:
                        runner.run_maestro(
                            self.device, self.flow, self.output, runner.Redactor(CREDENTIALS), timeout=60,
                        )
                self.assertEqual(str(caught.exception), "maestro-flow-failed")
                self.assertEqual(
                    json.loads((self.destination / "last-anr-summary.json").read_text(encoding="utf-8")),
                    {
                        "process": "unknown", "reason": category,
                        "load_average": None, "total_cpu_percent": None,
                    },
                )
                self.assertEqual(
                    (self.destination / "console.log").read_text(encoding="utf-8"),
                    "Maestro UI assertion failed: [REDACTED]",
                )
                self.assertEqual(published.getvalue(), "")
                for path in self.destination.iterdir():
                    content = path.read_text(encoding="utf-8")
                    for value in (
                        private, runner.APP_ID, "com.android.systemui", "ActivityRecord", "Window{",
                        "ANR time", "Oct 22", "6175", "1000", "Intent", "https://", "synthetic-token",
                        "authState", "accessToken", CREDENTIALS["memberId"], SYNTHETIC_SECRET,
                        self.escaped_secret, "synthetic-private-command-stderr",
                        "synthetic.private.service", "synthetic.private.provider",
                    ):
                        self.assertNotIn(value, content)

    def test_application_anr_summary_uses_fixed_reason_categories(self):
        for reason, category in (
            ("Input dispatching timed out (synthetic-private-window)", "input-dispatch"),
            ("executing service synthetic.private.service", "service"),
            ("ContentProvider not responding: synthetic.private.provider", "content-provider"),
        ):
            with self.subTest(category=category):
                self.device.call.return_value = (
                    0, ("ANR in " + runner.APP_ID + "\nReason: " + reason + "\n").encode("utf-8"), b"",
                )
                runner.save_failure_anr(self.device, self.destination)
                self.assertEqual(
                    json.loads((self.destination / "last-anr-summary.json").read_text(encoding="utf-8")),
                    {
                        "process": "application", "reason": category,
                        "load_average": None, "total_cpu_percent": None,
                    },
                )

    def test_anr_summary_does_not_publish_other_process_or_reason_text(self):
        self.device.call.return_value = (
            0, b"ANR in synthetic.private.process\nReason: private-user-content\n", b"",
        )
        runner.save_failure_anr(self.device, self.destination)
        self.assertEqual(
            json.loads((self.destination / "last-anr-summary.json").read_text(encoding="utf-8")),
            {"process": "other", "reason": "unknown", "load_average": None, "total_cpu_percent": None},
        )

    def test_missing_anr_report_is_unknown_not_an_application_failure(self):
        self.device.call.return_value = (0, b"<no ANR has occurred>\n", b"")
        runner.save_failure_anr(self.device, self.destination)
        self.assertEqual(
            json.loads((self.destination / "last-anr-summary.json").read_text(encoding="utf-8")),
            {"process": "unknown", "reason": "unknown", "load_average": None, "total_cpu_percent": None},
        )

    def test_unavailable_anr_preserves_original_failure_and_console(self):
        self.device.call.side_effect = [
            (0, b"", b""),
            (1, SYNTHETIC_SECRET.encode("utf-8"), self.escaped_secret.encode("utf-8")),
            (0, b"", b""),
        ]
        published = io.StringIO()
        with contextlib.redirect_stdout(published), contextlib.redirect_stderr(published):
            with self.assertRaises(runner.RunnerFailure) as caught:
                runner.run_maestro(
                    self.device, self.flow, self.output, runner.Redactor(CREDENTIALS), timeout=60,
                )
        self.assertEqual(str(caught.exception), "maestro-flow-failed")
        self.assertEqual(
            (self.destination / "console.log").read_text(encoding="utf-8"),
            "Maestro UI assertion failed: [REDACTED]",
        )
        self.assertEqual(published.getvalue(), "")
        self.assertFalse((self.destination / "last-anr-summary.json").exists())

    def test_anr_capture_failure_preserves_original_failure_and_console(self):
        for failure in (runner.RunnerFailure("tool-timeout"), OSError(SYNTHETIC_SECRET)):
            with self.subTest(failure=type(failure).__name__):
                self.device.call.side_effect = [(0, b"", b""), failure, (0, b"", b"")]
                published = io.StringIO()
                with contextlib.redirect_stdout(published), contextlib.redirect_stderr(published):
                    with self.assertRaises(runner.RunnerFailure) as caught:
                        runner.run_maestro(
                            self.device, self.flow, self.output, runner.Redactor(CREDENTIALS), timeout=60,
                        )
                self.assertEqual(str(caught.exception), "maestro-flow-failed")
                self.assertEqual(
                    (self.destination / "console.log").read_text(encoding="utf-8"),
                    "Maestro UI assertion failed: [REDACTED]",
                )
                self.assertEqual(published.getvalue(), "")
                self.assertFalse((self.destination / "last-anr-summary.json").exists())

    def test_anr_write_failure_preserves_original_failure_without_private_error(self):
        self.device.call.side_effect = [
            (0, b"", b""),
            (0, b"ANR in com.project200.undabang\nReason: Input dispatching timed out\n", b""),
            (0, b"", b""),
        ]
        write_text = Path.write_text

        def fail_anr_write(path, *args, **kwargs):
            if path == self.destination / "last-anr-summary.json":
                raise OSError(SYNTHETIC_SECRET)
            return write_text(path, *args, **kwargs)

        published = io.StringIO()
        with mock.patch.object(Path, "write_text", new=fail_anr_write):
            with contextlib.redirect_stdout(published), contextlib.redirect_stderr(published):
                with self.assertRaises(runner.RunnerFailure) as caught:
                    runner.run_maestro(
                        self.device, self.flow, self.output, runner.Redactor(CREDENTIALS), timeout=60,
                    )
        self.assertEqual(str(caught.exception), "maestro-flow-failed")
        self.assertEqual(
            (self.destination / "console.log").read_text(encoding="utf-8"),
            "Maestro UI assertion failed: [REDACTED]",
        )
        self.assertEqual(published.getvalue(), "")
        self.assertFalse((self.destination / "last-anr-summary.json").exists())

    def test_anr_interruption_propagates_unchanged(self):
        interruption = runner.RunInterrupted("run-interrupted")
        self.device.call.side_effect = [(0, b"", b""), interruption]
        with self.assertRaises(runner.RunInterrupted) as caught:
            runner.run_maestro(
                self.device, self.flow, self.output, runner.Redactor(CREDENTIALS), timeout=60,
            )
        self.assertIs(caught.exception, interruption)
        self.assertEqual(
            (self.destination / "console.log").read_text(encoding="utf-8"),
            "Maestro UI assertion failed: [REDACTED]",
        )
        self.assertFalse((self.destination / "last-anr-summary.json").exists())

    def test_system_ui_anr_events_summary_keeps_only_timestamp_and_categories(self):
        private = "synthetic-private-window-and-intent"
        report = (
            "--------- beginning of events\n"
            "1700000100.250  1000  1001 I am_anr  : [0,6175,com.android.systemui,0,"
            "Broadcast of Intent { " + private + ", extras=[one,two] } "
            "https://synthetic.invalid/?key=synthetic-key&token=synthetic-token "
            'authState={"accessToken":"' + self.escaped_secret + '"}]\n'
        )
        self.device.call.side_effect = [
            (0, b"", b""), (0, b"<no ANR has occurred>\n", b""),
            (0, report.encode("utf-8"), b"synthetic-private-command-stderr"),
        ]
        published = io.StringIO()
        with contextlib.redirect_stdout(published), contextlib.redirect_stderr(published):
            with self.assertRaises(runner.RunnerFailure) as caught:
                runner.run_maestro(
                    self.device, self.flow, self.output, runner.Redactor(CREDENTIALS), timeout=60,
                )
        self.assertEqual(str(caught.exception), "maestro-flow-failed")
        self.assertEqual(
            json.loads((self.destination / "anr-events-summary.json").read_text(encoding="utf-8")),
            {
                "scope": "device-history", "status": "captured",
                "events": [
                    {"timestamp_epoch": 1700000100.25, "process": "system-ui", "reason": "broadcast"},
                ],
            },
        )
        self.assertEqual(
            (self.destination / "console.log").read_text(encoding="utf-8"),
            "Maestro UI assertion failed: [REDACTED]",
        )
        self.assertEqual(published.getvalue(), "")
        for path in self.destination.iterdir():
            content = path.read_text(encoding="utf-8")
            for value in (
                private, "com.android.systemui", "6175", "1000", "1001", "Intent",
                "https://", "synthetic-key", "synthetic-token", "authState", "accessToken",
                SYNTHETIC_SECRET, self.escaped_secret, "synthetic-private-command-stderr",
            ):
                self.assertNotIn(value, content)

    def test_anr_event_process_and_reason_classification_boundaries(self):
        for process, reason, process_category, reason_category in (
            ("com.android.systemui", "Input dispatching timed out (private)", "system-ui", "input-dispatch"),
            (runner.APP_ID, "Broadcast of Intent { private, [extras] }", "application", "broadcast"),
            (runner.APP_ID, "executing service synthetic.private.service", "application", "service"),
            (runner.APP_ID, "ContentProvider not responding: private", "application", "content-provider"),
            ("com.android.systemui:private", "private reason", "other", "unknown"),
            ("com.android.systemui.private", "private reason", "other", "unknown"),
            (runner.APP_ID + ":private", "private reason", "other", "unknown"),
            (runner.APP_ID + ".private", "private reason", "other", "unknown"),
            ("synthetic.private.process", "prefix Input dispatching timed out", "other", "unknown"),
            (runner.APP_ID, "input dispatching timed out", "application", "unknown"),
        ):
            with self.subTest(process=process, reason_category=reason_category):
                self.device.call.return_value = (
                    0,
                    ("1700000100.250 1000 1001 I am_anr: [0,6175," + process + ",0," + reason + "]\n")
                    .encode("utf-8"),
                    b"",
                )
                runner.save_failure_anr_events(self.device, self.destination)
                self.assertEqual(
                    json.loads((self.destination / "anr-events-summary.json").read_text(encoding="utf-8")),
                    {
                        "scope": "device-history", "status": "captured",
                        "events": [{
                            "timestamp_epoch": 1700000100.25,
                            "process": process_category, "reason": reason_category,
                        }],
                    },
                )

    def test_anr_events_distinguish_empty_unrecognized_and_partial_capture(self):
        valid = (
            b"1700000100.250 1000 1001 I am_anr: "
            b"[0,6175,com.android.systemui,0,synthetic-private-reason]\n"
        )
        for report, status, events in (
            (b"", "empty", []),
            (b"\n--------- beginning of events\n--------- switch to events\n", "empty", []),
            (
                valid.replace(b"[0,6175,", b"[-1,6175,").replace(b",0,", b",-2147483648,"),
                "captured",
                [{"timestamp_epoch": 1700000100.25, "process": "system-ui", "reason": "unknown"}],
            ),
            (valid, "captured", [
                {"timestamp_epoch": 1700000100.25, "process": "system-ui", "reason": "unknown"},
            ]),
            (b"synthetic-private-unrecognized-output\n", "unrecognized", []),
            (valid.replace(b"am_anr:", b"other_tag:"), "unrecognized", []),
            (valid.replace(b"com.android.systemui", b"invalid process"), "unrecognized", []),
            (valid.replace(b"[0,6175,", b"[0,"), "unrecognized", []),
            (
                b"--------- beginning of events\n" + valid
                + b"synthetic-private-malformed-row\xff\n",
                "partial",
                [{"timestamp_epoch": 1700000100.25, "process": "system-ui", "reason": "unknown"}],
            ),
        ):
            with self.subTest(status=status, report=report):
                self.device.call.return_value = (0, report, b"")
                runner.save_failure_anr_events(self.device, self.destination)
                self.assertEqual(
                    json.loads((self.destination / "anr-events-summary.json").read_text(encoding="utf-8")),
                    {"scope": "device-history", "status": status, "events": events},
                )

    def test_anr_events_retain_only_latest_eight_finite_nonnegative_timestamps(self):
        timestamps = ["0", "12.5", "2", "11", "1", "10", "3", "9", "4", "8", "5", "7", "6"]
        invalid = ["-1", "nan", "NaN", "inf", "Infinity", "1e3", "9" * 400]
        report = "\n".join(
            timestamp + " 1000 1001 I am_anr: [0,6175,com.android.systemui,0,private]"
            for timestamp in timestamps
        )
        self.device.call.return_value = (0, report.encode("utf-8"), b"")
        runner.save_failure_anr_events(self.device, self.destination)
        summary = json.loads((self.destination / "anr-events-summary.json").read_text(encoding="utf-8"))
        self.assertEqual(set(summary), {"scope", "status", "events"})
        self.assertEqual(summary["scope"], "device-history")
        self.assertEqual(summary["status"], "captured")
        self.assertEqual(
            [event["timestamp_epoch"] for event in summary["events"]],
            [5.0, 6.0, 7.0, 8.0, 9.0, 10.0, 11.0, 12.5],
        )
        for timestamp in invalid:
            with self.subTest(timestamp=timestamp):
                self.device.call.return_value = (
                    0,
                    (report + "\n" + timestamp
                     + " 1000 1001 I am_anr: [0,6175,com.android.systemui,0,private]\n").encode("utf-8"),
                    b"",
                )
                runner.save_failure_anr_events(self.device, self.destination)
                mixed = json.loads((self.destination / "anr-events-summary.json").read_text(encoding="utf-8"))
                self.assertEqual(mixed, {**summary, "status": "partial"})
        self.device.call.return_value = (
            0, b"0.000 1000 1001 I am_anr: [0,6175,com.android.systemui,0,private]\n", b"",
        )
        runner.save_failure_anr_events(self.device, self.destination)
        self.assertEqual(
            json.loads((self.destination / "anr-events-summary.json").read_text(encoding="utf-8")),
            {
                "scope": "device-history", "status": "captured",
                "events": [{"timestamp_epoch": 0.0, "process": "system-ui", "reason": "unknown"}],
            },
        )

    def test_anr_events_unavailable_or_unreadable_preserves_original_failure_and_console(self):
        for failure in (
            (1, SYNTHETIC_SECRET.encode("utf-8"), self.escaped_secret.encode("utf-8")),
            runner.RunnerFailure("tool-timeout"),
            OSError(SYNTHETIC_SECRET),
        ):
            with self.subTest(failure=type(failure).__name__):
                self.device.call.side_effect = [(0, b"", b""), (0, b"", b""), failure]
                published = io.StringIO()
                with contextlib.redirect_stdout(published), contextlib.redirect_stderr(published):
                    with self.assertRaises(runner.RunnerFailure) as caught:
                        runner.run_maestro(
                            self.device, self.flow, self.output, runner.Redactor(CREDENTIALS), timeout=60,
                        )
                self.assertEqual(str(caught.exception), "maestro-flow-failed")
                self.assertEqual(
                    json.loads((self.destination / "anr-events-summary.json").read_text(encoding="utf-8")),
                    {"scope": "device-history", "status": "unavailable", "events": []},
                )
                self.assertEqual(
                    (self.destination / "console.log").read_text(encoding="utf-8"),
                    "Maestro UI assertion failed: [REDACTED]",
                )
                self.assertEqual(published.getvalue(), "")

    def test_anr_events_parse_failure_preserves_original_failure_and_console(self):
        self.device.call.side_effect = [(0, b"", b""), (0, b"", b""), (0, b"", b"")]
        published = io.StringIO()
        with mock.patch.object(runner, "parse_anr_events", side_effect=ValueError(SYNTHETIC_SECRET)):
            with contextlib.redirect_stdout(published), contextlib.redirect_stderr(published):
                with self.assertRaises(runner.RunnerFailure) as caught:
                    runner.run_maestro(
                        self.device, self.flow, self.output, runner.Redactor(CREDENTIALS), timeout=60,
                    )
        self.assertEqual(str(caught.exception), "maestro-flow-failed")
        self.assertEqual(
            json.loads((self.destination / "anr-events-summary.json").read_text(encoding="utf-8")),
            {"scope": "device-history", "status": "unrecognized", "events": []},
        )
        self.assertEqual(
            (self.destination / "console.log").read_text(encoding="utf-8"),
            "Maestro UI assertion failed: [REDACTED]",
        )
        self.assertEqual(published.getvalue(), "")

    def test_anr_events_write_failure_preserves_ui_failure_console_and_fixture_cleanup(self):
        self.device.call.side_effect = [
            (0, b"", b""), (0, b"", b""),
            (0, b"1700000100.250 1000 1001 I am_anr: [0,6175,com.android.systemui,0,private]\n", b""),
        ]
        self.device.clear = mock.Mock()
        self.device.bootstrap = mock.Mock(return_value={})
        write_text = Path.write_text

        def fail_events_write(path, *args, **kwargs):
            if path == self.destination / "anr-events-summary.json":
                raise OSError(SYNTHETIC_SECRET)
            return write_text(path, *args, **kwargs)

        published = io.StringIO()
        with mock.patch.object(Path, "write_text", new=fail_events_write):
            with contextlib.redirect_stdout(published), contextlib.redirect_stderr(published):
                result, safe_to_continue = runner.run_ordinary_flow(
                    SimpleNamespace(flow_timeout=60), self.device,
                    {"name": "chatting", "fixture": True}, self.flow, self.output,
                    runner.Redactor(CREDENTIALS), CREDENTIALS, {}, None,
                )
        self.assertEqual(result[0], self.flow)
        self.assertEqual(result[2], ["maestro-flow-failed"])
        self.assertTrue(safe_to_continue)
        operations = [call.args[0] for call in self.device.bootstrap.call_args_list]
        self.assertEqual(operations, ["prepare", "cleanup"])
        self.assertEqual(self.device.clear.call_count, 2)
        self.assertEqual(
            (self.destination / "console.log").read_text(encoding="utf-8"),
            "Maestro UI assertion failed: [REDACTED]",
        )
        self.assertFalse((self.destination / "anr-events-summary.json").exists())
        self.assertEqual(published.getvalue(), "")

    def test_anr_events_interruption_propagates_unchanged_and_keeps_sanitized_console(self):
        write_text = Path.write_text
        for stage in ("read", "parse", "write"):
            with self.subTest(stage=stage):
                interruption = runner.RunInterrupted("run-interrupted")
                self.device.call.side_effect = [
                    (0, b"", b""), (0, b"", b""),
                    interruption if stage == "read" else (0, b"", b""),
                ]

                def interrupt_events_write(path, *args, **kwargs):
                    if path == self.destination / "anr-events-summary.json":
                        raise interruption
                    return write_text(path, *args, **kwargs)

                with contextlib.ExitStack() as patches:
                    if stage == "parse":
                        patches.enter_context(mock.patch.object(
                            runner, "parse_anr_events", side_effect=interruption,
                        ))
                    elif stage == "write":
                        patches.enter_context(mock.patch.object(
                            Path, "write_text", new=interrupt_events_write,
                        ))
                    with self.assertRaises(runner.RunInterrupted) as caught:
                        runner.run_maestro(
                            self.device, self.flow, self.output, runner.Redactor(CREDENTIALS), timeout=60,
                        )
                self.assertIs(caught.exception, interruption)
                self.assertEqual(
                    (self.destination / "console.log").read_text(encoding="utf-8"),
                    "Maestro UI assertion failed: [REDACTED]",
                )
                self.assertFalse((self.destination / "anr-events-summary.json").exists())


class AnrCheckpointTests(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory(prefix="maestro-checkpoint-test-")
        self.addCleanup(temporary.cleanup)
        self.destination = Path(temporary.name) / "failure"
        self.device = SimpleNamespace(call=mock.Mock(side_effect=[
            (0, b"<no ANR has occurred>\n", b""),
            (0, b"--------- beginning of events\n", b""),
            (0, b"1700000123\n", b""), (0, b"91.25 200.50\n", b""),
        ]))

    def capture(self):
        runner.capture_anr_checkpoint(self.device, self.destination, "failure")
        return json.loads((self.destination / "checkpoint.json").read_text(encoding="utf-8"))

    def test_checkpoint_separates_host_capture_window_device_clocks_and_historical_events(self):
        private = "synthetic-private-checkpoint-window"
        self.device.call.side_effect = [
            (0, ("ANR in com.android.systemui\n  Reason: Broadcast of Intent { " + private + " }\n"
                 "Load: 14.67 / 12.04 / 6.64\n98.5% TOTAL: private\n").encode(), b"private-stderr"),
            (0, ("1700000100.25 6175 6176 I am_anr: [0,6175,com.android.systemui,0,"
                 "Input dispatching timed out (" + private + ")]\n").encode(), b"private-stderr"),
            (0, b"1700000123\n", b""), (0, b"91.25 200.50\n", b""),
        ]
        with mock.patch.object(runner.time, "time", side_effect=[1700000200.0, 1700000201.0]):
            with mock.patch.object(runner.time, "monotonic", side_effect=[100.0, 101.0]):
                metadata = self.capture()
        self.assertEqual(metadata, {
            "phase": "failure", "scope": "device-history",
            "host_capture_started_epoch": 1700000200.0, "host_capture_ended_epoch": 1700000201.0,
            "host_capture_started_monotonic": 100.0, "host_capture_ended_monotonic": 101.0,
            "device_clock": {"status": "captured", "wall_epoch": 1700000123.0, "uptime_seconds": 91.25},
            "last_anr_status": "captured", "anr_events_status": "captured",
        })
        last_anr = json.loads((self.destination / "last-anr-summary.json").read_text())
        self.assertEqual(last_anr, {
            "status": "captured", "scope": "device-history",
            "process": "system-ui", "reason": "broadcast",
            "load_average": [14.67, 12.04, 6.64], "total_cpu_percent": 98.5,
        })
        events = json.loads((self.destination / "anr-events-summary.json").read_text())
        self.assertEqual(events, {
            "status": "captured", "scope": "device-history",
            "events": [{"timestamp_epoch": 1700000100.25, "process": "system-ui", "reason": "input-dispatch"}],
        })
        for path in self.destination.iterdir():
            content = path.read_text()
            for secret in (private, "private-stderr", "com.android.systemui", "6175", "Intent", "timed out"):
                self.assertNotIn(secret, content)

    def test_missing_malformed_and_partial_sources_never_become_clean_anr_evidence(self):
        for last, events, wall, uptime, expected in (
            (b"", b"", b"1700000123", b"91.25 200", ("empty", "empty", "captured")),
            (b"private", b"private", b"nan", b"-1 0", ("unrecognized", "unrecognized", "unrecognized")),
            (b"  Reason: Input dispatching timed out (private)", b"", b"1700000123", b"private",
             ("partial", "empty", "partial")),
            (None, None, None, None, ("unavailable", "unavailable", "unavailable")),
        ):
            with self.subTest(expected=expected):
                self.device.call.side_effect = [
                    (0, value, b"") if value is not None else (1, b"private-output", b"private-stderr")
                    for value in (last, events, wall, uptime)
                ]
                metadata = self.capture()
                self.assertEqual(
                    (metadata["last_anr_status"], metadata["anr_events_status"], metadata["device_clock"]["status"]),
                    expected,
                )
                self.assertEqual(metadata["scope"], "device-history")
                if expected[2] in ("unavailable", "unrecognized"):
                    self.assertIsNone(metadata["device_clock"]["wall_epoch"])
                    self.assertIsNone(metadata["device_clock"]["uptime_seconds"])
                for path in self.destination.iterdir():
                    self.assertNotIn("private", path.read_text())

    def test_failed_reads_keep_other_sources_and_numeric_metadata(self):
        for failure in (runner.RunnerFailure("tool-timeout"), OSError(SYNTHETIC_SECRET)):
            with self.subTest(failure=type(failure).__name__):
                self.device.call.side_effect = [
                    failure, (0, b"", b""), failure, (0, b"91.25 200\n", b""),
                ]
                metadata = self.capture()
                self.assertEqual(metadata["last_anr_status"], "unavailable")
                self.assertEqual(metadata["anr_events_status"], "empty")
                self.assertEqual(metadata["device_clock"], {
                    "status": "partial", "wall_epoch": None, "uptime_seconds": 91.25,
                })
                self.assertNotIn(SYNTHETIC_SECRET, str(metadata))

    def test_parse_failure_does_not_discard_other_checkpoint_sources(self):
        with mock.patch.object(runner, "parse_last_anr", side_effect=ValueError(SYNTHETIC_SECRET)):
            metadata = self.capture()
        self.assertEqual(metadata["last_anr_status"], "unrecognized")
        self.assertEqual(metadata["anr_events_status"], "empty")
        self.assertEqual(metadata["device_clock"]["status"], "captured")
        self.assertNotIn(SYNTHETIC_SECRET, str(metadata))

    def test_nonfinite_numbers_are_unknown_and_never_serialized_as_infinity(self):
        huge = "9" * 400
        self.device.call.side_effect = [
            (0, ("ANR in com.android.systemui\nReason: Broadcast of Intent\n"
                 "Load: " + huge + " / 1 / 2\n" + huge + "% TOTAL:\n").encode(), b""),
            (0, b"", b""), (0, huge.encode(), b""), (0, (huge + " 1\n").encode(), b""),
        ]
        metadata = self.capture()
        self.assertEqual(metadata["device_clock"]["status"], "unrecognized")
        last_anr = json.loads((self.destination / "last-anr-summary.json").read_text())
        self.assertIsNone(last_anr["load_average"])
        self.assertIsNone(last_anr["total_cpu_percent"])
        for path in self.destination.iterdir():
            self.assertNotIn("Infinity", path.read_text())
            self.assertNotIn("NaN", path.read_text())

    def test_single_artifact_write_failure_keeps_other_summary_and_checkpoint(self):
        write_text = Path.write_text

        def write(path, *args, **kwargs):
            if path.name == "last-anr-summary.json":
                raise OSError(SYNTHETIC_SECRET)
            return write_text(path, *args, **kwargs)

        with mock.patch.object(Path, "write_text", new=write):
            metadata = self.capture()
        self.assertEqual(metadata["last_anr_status"], "empty")
        self.assertTrue((self.destination / "anr-events-summary.json").is_file())
        self.assertFalse((self.destination / "last-anr-summary.json").exists())

    def test_interruption_during_checkpoint_read_preserves_cancellation(self):
        interruption = runner.RunInterrupted("run-interrupted")
        self.device.call.side_effect = interruption
        with self.assertRaises(runner.RunInterrupted) as caught:
            self.capture()
        self.assertIs(caught.exception, interruption)
        self.assertFalse(self.destination.exists())


class EmulatorInputTests(unittest.TestCase):
    def input_method(self, subtypes):
        return runner.ET.fromstring(
            '<input-method xmlns:android="http://schemas.android.com/apk/res/android">'
            + subtypes + '</input-method>',
        )

    def installed_subtype_from_dumps(self, resources, metadata, sdk=b"30\n"):
        manifest = '''E: manifest (line=1)
  A: package="example.keyboard"
    E: application (line=2)
        E: service (line=3)
          A: http://schemas.android.com/apk/res/android:name(0x01010003)=".SelectedIme"
            E: meta-data (line=4)
              A: http://schemas.android.com/apk/res/android:name(0x01010003)="android.view.im"
              A: http://schemas.android.com/apk/res/android:resource(0x01010025)=@0x7f010002
'''
        device = object.__new__(runner.Device)
        device.env = {}
        device.require = mock.Mock(side_effect=[
            b"example.keyboard/.SelectedIme\n", b"package:/system/app/Keyboard.apk\n", b"", sdk,
        ])

        def dump(argv, _env):
            if argv[1:3] == ["dump", "resources"]:
                return 0, resources.encode(), b""
            if argv[1:4] == ["dump", "xmltree", "--file"]:
                output = manifest if argv[4] == "AndroidManifest.xml" else metadata[argv[4]]
                return 0, output.encode(), b""
            raise AssertionError("Unexpected metadata operation")

        with mock.patch.object(runner.shutil, "which", return_value="test-aapt2"):
            with mock.patch.object(runner, "command", side_effect=dump) as command:
                result = runner.installed_ascii_subtype(device)
        self.assertEqual(
            device.require.call_args_list.count(mock.call("shell", "getprop", "ro.build.version.sdk")), 1,
        )
        xml_paths = [call.args[0][4] for call in command.call_args_list if call.args[0][2] == "xmltree"]
        return result, xml_paths

    def subtype_dump(self, subtype):
        return '''E: input-method (line=1)
  E: subtype (line=2)
    A: http://schemas.android.com/apk/res/android:imeSubtypeLocale(0x01010399)="en_US"
    A: http://schemas.android.com/apk/res/android:imeSubtypeMode(0x0101039a)="keyboard"
    A: http://schemas.android.com/apk/res/android:isAsciiCapable(0x010103e9)=true
    A: http://schemas.android.com/apk/res/android:subtypeId(0x010103c1)=''' + str(subtype) + "\n"

    def test_missing_metadata_tool_fails_before_keyboard_changes(self):
        device = object.__new__(runner.Device)
        device.env = {}
        device.require = mock.Mock(return_value=b"example.keyboard/.SelectedIme\n")
        with mock.patch.object(runner.shutil, "which", return_value=None):
            with self.assertRaisesRegex(runner.RunnerFailure, "emulator-ascii-input-requires-aapt2"):
                runner.installed_ascii_subtype(device)
        self.assertEqual(device.require.call_count, 1)

    def test_failed_apk_metadata_read_reports_stage_without_tool_output_or_environment(self):
        secret = "synthetic-private-metadata"
        device = object.__new__(runner.Device)
        device.env = {"PRIVATE_SETTING": secret}
        device.require = mock.Mock(side_effect=[
            b"example.keyboard/.SelectedIme\n",
            ("package:/system/app/" + secret + "/Keyboard.apk\n").encode(), b"",
        ])
        stdout, stderr = io.StringIO(), io.StringIO()
        with contextlib.redirect_stdout(stdout), contextlib.redirect_stderr(stderr):
            with mock.patch.object(runner.shutil, "which", return_value="/private/" + secret + "/aapt2"):
                with mock.patch.object(runner, "command", return_value=(7, secret.encode(), secret.encode())):
                    with self.assertRaisesRegex(runner.RunnerFailure, "emulator-ime-metadata-unavailable"):
                        runner.installed_ascii_subtype(device)
        self.assertEqual(stdout.getvalue(), "")
        self.assertNotIn(secret, stderr.getvalue())
        records = self.diagnostic_records(stderr)
        self.assertEqual(records[-1], {
            "stage": "aapt", "operation": "manifest", "command": "xmltree",
            "status": "completed", "exit_code": 7,
        })

    def diagnostic_records(self, stderr):
        return [json.loads(line.removeprefix("IME metadata: ")) for line in stderr.getvalue().splitlines()]

    def test_invalid_default_ime_reports_only_invalid_state(self):
        secret = "synthetic-private-invalid-ime\nextra-private-line"
        device = object.__new__(runner.Device)
        device.require = mock.Mock(return_value=secret.encode())
        stderr = io.StringIO()
        with contextlib.redirect_stderr(stderr):
            with self.assertRaisesRegex(runner.RunnerFailure, "emulator-ime-metadata-unavailable"):
                runner.installed_ascii_subtype(device)
        self.assertNotIn(secret, stderr.getvalue())
        self.assertEqual(self.diagnostic_records(stderr), [
            {"stage": "default-ime", "valid": False, "component": "invalid"},
        ])

    def test_multiple_apk_paths_report_count_without_paths(self):
        secret = "synthetic-private-apk-path"
        device = object.__new__(runner.Device)
        device.require = mock.Mock(side_effect=[
            b"example.keyboard/.SelectedIme\n",
            ("package:/private/" + secret + "/base.apk\n"
             "package:/private/" + secret + "/split.apk\n").encode(),
        ])
        stderr = io.StringIO()
        with contextlib.redirect_stderr(stderr):
            with mock.patch.object(runner.shutil, "which", return_value="test-aapt2"):
                with self.assertRaisesRegex(runner.RunnerFailure, "emulator-ime-metadata-unavailable"):
                    runner.installed_ascii_subtype(device)
        self.assertNotIn(secret, stderr.getvalue())
        self.assertEqual(self.diagnostic_records(stderr)[-1], {
            "stage": "apk-paths", "count": 2, "valid_single_apk": False,
        })

    def test_unrecognized_attribute_namespace_reports_schema_without_loosening_selection(self):
        secret = "synthetic-private-other-service"
        dump = '''E: manifest (line=1)
  A: package="example.keyboard"
    E: application (line=2)
        E: service (line=3)
          A: android:name(0x01010003)="''' + secret + '''"
            E: meta-data (line=4)
              A: android:name(0x01010003)="android.view.im"
              A: android:resource(0x01010025)=@0x7f010001
'''
        stderr = io.StringIO()
        with contextlib.redirect_stderr(stderr):
            manifest = runner.aapt_xml(dump, "manifest")
            with self.assertRaisesRegex(runner.RunnerFailure, "emulator-ime-metadata-unavailable"):
                runner.ime_resource_id(manifest, "example.keyboard/.SelectedIme")
        self.assertNotIn(secret, stderr.getvalue())
        xml, selection = self.diagnostic_records(stderr)
        self.assertEqual(xml["root"], "manifest")
        self.assertEqual(xml["android_attribute_names"], ["android-prefix:name", "android-prefix:resource"])
        self.assertEqual(selection["matching_service_count"], 0)
        self.assertEqual(selection["resource_count"], 0)

    def test_missing_xml_root_reports_document_without_dumping_body(self):
        secret = "synthetic-private-non-xml-output"
        stderr = io.StringIO()
        with contextlib.redirect_stderr(stderr):
            with self.assertRaisesRegex(runner.RunnerFailure, "emulator-ime-metadata-unavailable"):
                runner.aapt_xml(secret, "manifest")
        self.assertNotIn(secret, stderr.getvalue())
        self.assertEqual(self.diagnostic_records(stderr), [
            {"stage": "xml", "document": "manifest", "status": "missing-root"},
        ])

    def test_collects_configured_resource_paths_without_logging_values(self):
        secret = "synthetic-private-resource"
        for config in ("ko", "private/config"):
            with self.subTest(config=config):
                resources = '''resource 0x7f010002 xml/metadata
  () (file) res/''' + secret + '''.xml type=XML
  (''' + config + ''') (file) res/other.xml type=XML
'''
                stderr = io.StringIO()
                with contextlib.redirect_stderr(stderr):
                    self.assertEqual(
                        runner.ime_resource_path(resources, "0x7f010002", 30),
                        ["res/" + secret + ".xml", "res/other.xml"],
                    )
                self.assertNotIn(secret, stderr.getvalue())
                self.assertNotIn("private/config", stderr.getvalue())
                self.assertEqual(self.diagnostic_records(stderr), [{
                    "stage": "resource-path", "selected_resource": "0x7f010002",
                    "matching_resource_count": 1, "path_count": 2,
                    "configs": ["", config if config == "ko" else "invalid"],
                    "sdk_level": 30, "rule": "all-variants-agree",
                }])

    def test_selects_declared_english_ascii_keyboard_not_korean_or_voice(self):
        metadata = self.input_method(
            '<subtype android:imeSubtypeLocale="ko_KR" android:imeSubtypeMode="keyboard"'
            ' android:isAsciiCapable="true" android:subtypeId="101" />'
            '<subtype android:languageTag="en-US" android:imeSubtypeMode="voice"'
            ' android:isAsciiCapable="true" android:subtypeId="102" />'
            '<subtype android:languageTag="en-US" android:imeSubtypeMode="keyboard"'
            ' android:isAsciiCapable="true" android:subtypeId="0xfffffffe" />',
        )
        self.assertEqual(runner.ascii_ime_subtype(metadata), -2)

    def test_selects_android_derived_id_when_subtype_id_is_absent_or_zero(self):
        for declaration in ("", ' android:subtypeId="0"', ' android:subtypeId="0x0"'):
            with self.subTest(declaration=declaration):
                metadata = self.input_method(
                    '<subtype android:imeSubtypeLocale="en_US" android:imeSubtypeMode="keyboard"'
                    ' android:isAsciiCapable="true"' + declaration + ' />',
                )
                self.assertEqual(runner.ascii_ime_subtype(metadata), 38405219)

    def test_derived_id_uses_raw_locale_and_extra_value_not_language_tag(self):
        cases = (
            ('android:languageTag="en-US"', "", 1095049687),
            (
                'android:imeSubtypeLocale="en_US" android:languageTag="en-US"',
                ' android:imeSubtypeExtraValue="\U0001f600"', 1315231776,
            ),
        )
        for locale, extra, expected in cases:
            with self.subTest(locale=locale, extra=extra):
                metadata = self.input_method(
                    '<subtype ' + locale + ' android:imeSubtypeMode="keyboard"'
                    ' android:isAsciiCapable="true"' + extra + ' />',
                )
                self.assertEqual(runner.ascii_ime_subtype(metadata), expected)

    def test_java_string_hash_uses_utf16_units_and_signed_int_overflow(self):
        for value, expected in (
            ("", 0), ("Aa", 2112), ("\U0001f600", 1772899),
            ("\ud800", 55296), ("zzzzzz", -685785664),
        ):
            with self.subTest(value=value):
                self.assertEqual(runner.java_string_hash(value), expected)

    def test_derived_id_matches_framework_defaults_and_ascii_compatibility_hash(self):
        self.assertEqual(runner.android_subtype_id({}), 28668735)
        self.assertEqual(runner.android_subtype_id({"isAsciiCapable": "true"}), 888732016)
        self.assertEqual(
            runner.android_subtype_id({"isAsciiCapable": "true", "imeSubtypeExtraValue": "\uffff"}),
            -1453882095,
        )
        attributes = {"imeSubtypeLocale": "en_US", "imeSubtypeMode": "keyboard"}
        self.assertEqual(runner.android_subtype_id(attributes), 1802354156)

    def test_refuses_missing_ambiguous_automatic_and_unsupported_subtypes(self):
        keyboard = (
            '<subtype android:imeSubtypeLocale="en_US" android:imeSubtypeMode="keyboard"'
            ' android:isAsciiCapable="true" android:subtypeId="{}" />'
        )
        cases = (
            "",
            keyboard.format(-1),
            keyboard.format("@integer/english_id"),
            keyboard.format("0x100000000"),
            keyboard.format(0) + keyboard.format(101),
            keyboard.format(0).replace('/>', 'android:imeSubtypeExtraValue="@0x7f010001" />'),
            keyboard.format(101) + keyboard.format(102),
            keyboard.format(101).replace('isAsciiCapable="true"', 'isAsciiCapable="false"'),
            keyboard.format(101).replace('en_US', 'ko_KR'),
            keyboard.format(101).replace('/>', 'android:isAuxiliary="true" />'),
            keyboard.format(101).replace('/>', 'android:overridesImplicitlyEnabledSubtype="true" />'),
        )
        for subtypes in cases:
            with self.subTest(subtypes=subtypes):
                with self.assertRaisesRegex(runner.RunnerFailure, "emulator-ascii-subtype-unavailable"):
                    runner.ascii_ime_subtype(self.input_method(subtypes))

    def test_reads_public_metadata_for_the_selected_service_only(self):
        dump = '''N: android=http://schemas.android.com/apk/res/android (line=1)
  E: manifest (line=1)
    A: package="example.keyboard"
      E: application (line=2)
          E: service (line=3)
            A: http://schemas.android.com/apk/res/android:name(0x01010003)=".OtherIme"
              E: meta-data (line=4)
                A: http://schemas.android.com/apk/res/android:name(0x01010003)="android.view.im"
                A: http://schemas.android.com/apk/res/android:resource(0x01010025)=@0x7f010001
          E: service (line=5)
            A: http://schemas.android.com/apk/res/android:name(0x01010003)=".SelectedIme"
              E: meta-data (line=6)
                A: http://schemas.android.com/apk/res/android:name(0x01010003)="android.view.im"
                A: http://schemas.android.com/apk/res/android:resource(0x01010025)=@0x7f010002
'''
        manifest = runner.aapt_xml(dump)
        self.assertEqual(
            runner.ime_resource_id(manifest, "example.keyboard/.SelectedIme"), "0x7f010002",
        )
        with self.assertRaisesRegex(runner.RunnerFailure, "emulator-ime-metadata-unavailable"):
            runner.ime_resource_id(manifest, "example.keyboard/.MissingIme")

    def test_resolves_metadata_resource_without_guessing_its_filename(self):
        resources = '''Package name=example.keyboard id=7f
  type xml id=01 entryCount=2
    resource 0x7f010001 xml/unrelated
      () (file) res/xml/a.xml type=XML
    resource 0x7f010002 xml/renamed
      () (file) res/x9.xml type=XML
'''
        self.assertEqual(runner.ime_resource_path(resources, "0x7f010002", 30), ["res/x9.xml"])
        for table in (
            resources.replace("0x7f010002", "0x7f010003"),
            resources + "    resource 0x7f010002 xml/duplicate\n      () (file) res/x9.xml type=XML\n",
            resources.replace("      () (file) res/x9.xml type=XML\n", ""),
            resources + "      () (file) res/other.xml type=XML\n",
            resources + "      () (file) res/x9.xml type=XML\n",
            resources + "      (ko) (file) res/ko.xml type=XML\n      (ko) (file) res/other.xml type=XML\n",
        ):
            with self.subTest(table=table):
                with self.assertRaisesRegex(runner.RunnerFailure, "emulator-ime-metadata-unavailable"):
                    runner.ime_resource_path(table, "0x7f010002", 30)

    def test_selects_highest_supported_version_metadata(self):
        resources = '''resource 0x7f010002 xml/metadata
  () (file) res/default.xml type=XML
  (v28) (file) res/v28.xml type=XML
'''
        metadata = {"res/default.xml": self.subtype_dump(101), "res/v28.xml": self.subtype_dump(102)}
        for sdk, path, subtype in ((30, "res/v28.xml", 102), (27, "res/default.xml", 101)):
            with self.subTest(sdk=sdk):
                self.assertEqual(runner.ime_resource_path(resources, "0x7f010002", sdk), [path])
                result, xml_paths = self.installed_subtype_from_dumps(resources, metadata, str(sdk).encode())
                self.assertEqual(result, ("example.keyboard/.SelectedIme", subtype))
                self.assertEqual(xml_paths, ["AndroidManifest.xml", path])

    def test_refuses_version_metadata_newer_than_device(self):
        resources = '''resource 0x7f010002 xml/metadata
  (v31) (file) res/v31.xml type=XML
'''
        with self.assertRaisesRegex(runner.RunnerFailure, "emulator-ime-metadata-unavailable"):
            runner.ime_resource_path(resources, "0x7f010002", 30)
        with self.assertRaisesRegex(runner.RunnerFailure, "emulator-ime-metadata-unavailable"):
            self.installed_subtype_from_dumps(resources, {})

    def test_accepts_locale_variants_only_when_all_subtype_ids_agree(self):
        resources = '''resource 0x7f010002 xml/metadata
  () (file) res/default.xml type=XML
  (ko) (file) res/ko.xml type=XML
'''
        metadata = {"res/default.xml": self.subtype_dump(101), "res/ko.xml": self.subtype_dump(101)}
        result, xml_paths = self.installed_subtype_from_dumps(resources, metadata)
        self.assertEqual(result, ("example.keyboard/.SelectedIme", 101))
        self.assertEqual(xml_paths, ["AndroidManifest.xml", "res/default.xml", "res/ko.xml"])

        metadata["res/ko.xml"] = self.subtype_dump(102)
        with self.assertRaisesRegex(runner.RunnerFailure, "emulator-ascii-subtype-unavailable"):
            self.installed_subtype_from_dumps(resources, metadata)

    def test_refuses_locale_variants_with_invalid_or_unavailable_subtype_metadata(self):
        resources = '''resource 0x7f010002 xml/metadata
  () (file) res/default.xml type=XML
  (ko) (file) res/ko.xml type=XML
'''
        for output, failure in (
            ("", "emulator-ime-metadata-unavailable"),
            ("E: input-method (line=1)\n", "emulator-ascii-subtype-unavailable"),
            (self.subtype_dump(-1), "emulator-ascii-subtype-unavailable"),
        ):
            with self.subTest(failure=failure, output=output):
                metadata = {"res/default.xml": self.subtype_dump(101), "res/ko.xml": output}
                with self.assertRaisesRegex(runner.RunnerFailure, failure):
                    self.installed_subtype_from_dumps(resources, metadata)

    def test_refuses_invalid_device_sdk_metadata(self):
        for sdk in (b"", b"invalid\n", b"30.0\n"):
            with self.subTest(sdk=sdk):
                with self.assertRaisesRegex(runner.RunnerFailure, "emulator-ime-metadata-unavailable"):
                    self.installed_subtype_from_dumps("", {}, sdk)

    def test_physical_device_refusal_precedes_all_configuration(self):
        device = object.__new__(runner.Device)
        device.require = mock.Mock(return_value=b"0\n")
        with self.assertRaisesRegex(runner.RunnerFailure, "configure-emulator-refuses-physical-devices"):
            device.configure_emulator()
        self.assertEqual(device.require.call_count, 1)

    def test_restricts_only_selected_ime_and_preserves_other_enabled_methods(self):
        selected = "example.keyboard/.SelectedIme"
        device = object.__new__(runner.Device)
        settings = {
            "default_input_method": selected,
            "enabled_input_methods": selected + ";101;102:example.voice/.VoiceIme;201",
            "selected_input_method_subtype": "101",
        }

        def call(*args, **_kwargs):
            # adb shell이 연결한 인자를 원격 셸처럼 해석한 뒤 실제 설정 상태를 바꿉니다.
            parsed = shlex.split(" ".join(args))
            if parsed[:2] != ["shell", "settings"] or parsed[3] != "secure":
                raise AssertionError("Unexpected settings operation")
            if parsed[2] == "get":
                return 0, (settings[parsed[4]] + "\n").encode(), b""
            if parsed[2] == "put":
                settings[parsed[4]] = parsed[5]
                return 0, b"", b""
            raise AssertionError("Unexpected settings operation")

        device.call = call
        with mock.patch.object(runner, "installed_ascii_subtype", return_value=(selected, 103)):
            device.configure_ascii_input()
        self.assertEqual(settings, {
            "default_input_method": selected,
            "enabled_input_methods": selected + ";103:example.voice/.VoiceIme;201",
            "selected_input_method_subtype": "103",
        })

    def test_rejects_readback_when_keyboard_configuration_did_not_apply(self):
        selected = "example.keyboard/.SelectedIme"
        device = object.__new__(runner.Device)
        for outputs in (
            [selected.encode(), (selected + ";103").encode(), b"102"],
            [selected.encode(), (selected + ";103;104").encode(), b"103"],
            [b"other.keyboard/.Ime", (selected + ";103").encode(), b"103"],
        ):
            with self.subTest(outputs=outputs):
                device.require = mock.Mock(side_effect=outputs)
                with self.assertRaisesRegex(runner.RunnerFailure, "emulator-ascii-input-not-configured"):
                    device.verify_ascii_input(selected, 103)


if __name__ == "__main__":
    unittest.main()
