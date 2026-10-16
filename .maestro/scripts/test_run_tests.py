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

    def test_matching_prepare_failure_reason_is_exposed_as_fixed_code(self):
        self.transport.result = b'{"status":"failed","reason":"matching_peer_unavailable"}'
        self.assert_failure(
            "bootstrap-prepare-failed-matching-peer-unavailable",
            lambda: self.device.bootstrap(
                "prepare",
                CREDENTIALS,
                feature="matching",
                flow="matching-place-crud",
                run_id="0123456789abcdef",
                parameters={},
            ),
        )
        self.assert_no_credential_arguments()

    def test_matching_failure_reason_is_not_exposed_outside_prepare(self):
        cases = (
            ("cleanup", "matching_peer_unavailable", "bootstrap-cleanup-failed"),
            ("prepare", "matching_unknown_failure", "bootstrap-prepare-failed"),
            ("prepare", "matching_peer_unavailable", "bootstrap-prepare-failed"),
        )
        for operation, reason, expected in cases:
            with self.subTest(operation=operation, reason=reason):
                self.transport.result = json.dumps(
                    {"status": "failed", "reason": reason},
                ).encode("utf-8")
                self.assert_failure(
                    expected,
                    lambda operation=operation: self.device.bootstrap(
                        operation,
                        CREDENTIALS,
                        feature="exercise" if operation == "prepare" and reason == "matching_peer_unavailable" else "matching",
                        flow="matching-place-crud",
                        run_id="0123456789abcdef",
                        parameters={},
                    ),
                )
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
