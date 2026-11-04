"""Check package trust, process cleanup and fail-closed sequencing without tools."""

import gzip
import importlib.util
import io
import json
from pathlib import Path
import subprocess
import tarfile
import tempfile
import unittest
from unittest import mock


SPEC = importlib.util.spec_from_file_location("tested_color_buffer_diagnostics", Path(__file__).with_name("run-color-buffer-diagnostics.py"))
runner = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(runner)
trace = runner.trace
CANARY = "synthetic-private-command-token"


def member(name, payload=b"binary", kind=tarfile.REGTYPE, link="", mode=0o755):
    item = tarfile.TarInfo(name)
    item.type = kind
    item.linkname = link
    item.mode = mode
    item.size = len(payload) if kind == tarfile.REGTYPE else 0
    return item, payload


def passed(variant="baseline"):
    return {"variant": variant, "test_returncode": 0, "failure": None, "shutdown": "console-shutdown",
            "emulator_returncode": 0, "errors": {"status": "captured"},
            "trace": {"status": "complete", "complete": True}}


class PackageTests(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name)
        self.package = self.root / "package.tar.gz"
        self.destination = self.root / "unpacked"

    def archive(self, entries):
        with tarfile.open(self.package, "w:gz") as archive:
            for item, payload in entries:
                archive.addfile(item, io.BytesIO(payload) if item.isfile() else None)
        return self.package

    def test_real_file_tree_and_internal_symlink_extract_without_owner_modes(self):
        self.archive([member("emulator", kind=tarfile.DIRTYPE), member("emulator/emulator"),
                      member("emulator/lib64", kind=tarfile.DIRTYPE), member("emulator/lib64/backend.so"),
                      member("emulator/lib64/backend-current.so", kind=tarfile.SYMTYPE, link="backend.so")])
        executable = runner.extract_package(self.package, self.destination)
        self.assertEqual(executable.read_bytes(), b"binary")
        self.assertTrue(executable.stat().st_mode & 0o111)
        self.assertEqual((self.destination / "emulator/lib64/backend-current.so").read_bytes(), b"binary")

    def test_escaping_paths_and_special_members_never_extract(self):
        unsafe = [member("../outside"), member("/tmp/outside"), member("emulator/../../outside"),
                  member("emulator/a/../outside"), member("emulator/./outside"), member("other/file"),
                  member("emulator\\outside"), member("emulator/private\nname"),
                  member("emulator/fifo", kind=tarfile.FIFOTYPE), member("emulator/device", kind=tarfile.CHRTYPE),
                  member("emulator/hardlink", kind=tarfile.LNKTYPE, link="emulator/emulator"),
                  member("emulator/setuid", mode=0o4755)]
        for entry in unsafe:
            with self.subTest(entry=entry[0].name):
                self.archive([member("emulator/emulator"), entry])
                with self.assertRaises(runner.DiagnosticFailure):
                    runner.extract_package(self.package, self.destination)
                self.assertFalse(self.destination.exists())

    def test_outside_link_cycles_and_symlink_parent_are_rejected(self):
        unsafe = [[member("emulator/link", kind=tarfile.SYMTYPE, link="../../outside")],
                  [member("emulator/link", kind=tarfile.SYMTYPE, link="/tmp/outside")],
                  [member("emulator/link", kind=tarfile.SYMTYPE, link="loop"),
                   member("emulator/loop", kind=tarfile.SYMTYPE, link="link")],
                  [member("emulator/link", kind=tarfile.SYMTYPE, link="emulator"), member("emulator/link/file")],
                  [member("emulator/link", kind=tarfile.SYMTYPE, link="missing")]]
        for entries in unsafe:
            with self.subTest(entries=[item.name for item, _ in entries]):
                self.archive([member("emulator/emulator"), *entries])
                with self.assertRaises(runner.DiagnosticFailure):
                    runner.extract_package(self.package, self.destination)
                self.assertFalse(self.destination.exists())

    def test_symlink_dotdot_is_resolved_after_intermediate_links(self):
        # A lexical normpath would hide a traversal through a shorter symlink target.
        entries = [member("emulator", kind=tarfile.DIRTYPE), member("emulator/emulator"),
                   member("emulator/dir", kind=tarfile.DIRTYPE),
                   member("emulator/dir/up", kind=tarfile.SYMTYPE, link=".."),
                   member("emulator/dir/bad", kind=tarfile.SYMTYPE, link="up/../../outside")]
        self.archive(entries)
        with self.assertRaises(runner.DiagnosticFailure):
            runner.extract_package(self.package, self.destination)

    def test_duplicate_names_size_limits_and_missing_launcher_are_rejected(self):
        for entries in ([member("emulator/emulator"), member("emulator/emulator")],
                        [member("emulator/lib.so")], [member("emulator/emulator", mode=0o644)]):
            self.archive(entries)
            with self.assertRaises(runner.DiagnosticFailure):
                runner.extract_package(self.package, self.destination)
        self.archive([member("emulator/emulator", payload=b"x" * 32)])
        for key, limit in (("MAX_MEMBER_BYTES", 16), ("MAX_EXPANDED_BYTES", 16), ("MAX_MEMBERS", 0), ("MAX_ARCHIVE_BYTES", 1)):
            with self.subTest(key=key), mock.patch.object(runner, key, limit):
                with self.assertRaises(runner.DiagnosticFailure):
                    runner.extract_package(self.package, self.destination)
        self.assertFalse(self.destination.exists())

    def test_compressed_and_extended_metadata_bombs_are_bounded(self):
        with gzip.open(self.package, "wb") as archive:
            archive.write(b"x" * 8192)
        with mock.patch.object(runner, "MAX_EXPANDED_BYTES", 1024), mock.patch.object(runner, "MAX_MEMBERS", 0):
            with self.assertRaises(runner.DiagnosticFailure):
                runner.extract_package(self.package, self.destination)
        item, payload = member("emulator/emulator")
        item.pax_headers = {"comment": "x" * 2048}
        self.archive([(item, payload)])
        with mock.patch.object(runner, "MAX_METADATA_BYTES", 1024):
            with self.assertRaises(runner.DiagnosticFailure):
                runner.extract_package(self.package, self.destination)
        self.assertFalse(self.destination.exists())

    def write_provenance(self):
        for variant in ("baseline", "instrumented"):
            (self.root / (variant + ".tar.gz")).write_bytes(b"public-package-" + variant.encode())
        manifest = self.root / "resolved-manifest.xml"
        manifest.write_text('<manifest><project name="qemu" revision="' + trace.REVISIONS["qemu_revision"] + '"/></manifest>')
        value = {"schema": 1, "scope": "public-source-not-sdk-16740729", **trace.REVISIONS,
                 "resolved_manifest_sha256": trace.digest(manifest), "patch_sha256": "a" * 64,
                 "archives": {variant: {"filename": variant + ".tar.gz", "sha256": trace.digest(self.root / (variant + ".tar.gz"))}
                              for variant in ("baseline", "instrumented")}}
        (self.root / "provenance.json").write_text(json.dumps(value))
        return value

    def test_provenance_requires_exact_scope_sources_hashes_and_pinned_manifest(self):
        value = self.write_provenance()
        self.assertEqual(trace.validate_provenance(self.root), value)
        for mutate in (lambda item: item.update(scope="stock-sdk-16740729"),
                       lambda item: item.update(qemu_revision="b" * 40),
                       lambda item: item["archives"]["baseline"].update(filename="../../outside"),
                       lambda item: item["archives"]["baseline"].update(sha256="0" * 64),
                       lambda item: item.update(patch_sha256=CANARY)):
            with self.subTest(mutation=mutate):
                value = self.write_provenance()
                mutate(value)
                (self.root / "provenance.json").write_text(json.dumps(value))
                with self.assertRaises(trace.TraceFailure):
                    trace.validate_provenance(self.root)
        value = self.write_provenance()
        (self.root / "resolved-manifest.xml").write_text('<manifest><project name="qemu" revision="main"/></manifest>')
        value["resolved_manifest_sha256"] = trace.digest(self.root / "resolved-manifest.xml")
        (self.root / "provenance.json").write_text(json.dumps(value))
        with self.assertRaises(trace.TraceFailure):
            trace.validate_provenance(self.root)

    def test_unknown_provenance_build_text_is_not_republished(self):
        value = self.write_provenance()
        value["arbitrary_build_note"] = CANARY
        (self.root / "provenance.json").write_text(json.dumps(value))
        safe = trace.validate_provenance(self.root)
        self.assertNotIn(CANARY, json.dumps(safe))


class ProducerStatusTests(unittest.TestCase):
    @unittest.skipUnless(runner.sys.platform == "linux" and hasattr(runner.os, "memfd_create"), "Linux memfd required")
    def test_channel_is_zeroed_sealed_shared_and_not_read_before_exit(self):
        channel = runner.ProducerStatus()
        self.addCleanup(channel.close)
        fd = channel.fd
        self.assertEqual(runner.os.fstat(fd).st_size, 4)
        self.assertEqual(channel.mapping[:], b"\0" * 4)
        process = mock.Mock()
        process.poll.return_value = None
        channel.mapping[:] = runner.struct.pack("=I", 2)
        with self.assertRaisesRegex(runner.DiagnosticFailure, "producer-status-process-not-exited"):
            channel.read_after_exit(process)
        process.poll.return_value = 0
        self.assertEqual(channel.read_after_exit(process), 2)
        with runner.mmap.mmap(fd, 4, access=runner.mmap.ACCESS_WRITE) as shared:
            shared[:] = runner.struct.pack("=I", 3)
            self.assertEqual(channel.read_after_exit(process), 3)
        with self.assertRaises(OSError):
            runner.os.ftruncate(fd, 0)
        channel.close()
        self.assertIsNone(channel.fd)
        self.assertIsNone(channel.mapping)
        with self.assertRaises(OSError):
            runner.os.fstat(fd)

    @unittest.skipUnless(runner.sys.platform == "linux" and hasattr(runner.os, "memfd_create"), "Linux memfd required")
    def test_closing_parent_copies_does_not_invalidate_an_independent_mapping(self):
        channel = runner.ProducerStatus()
        self.addCleanup(channel.close)
        with runner.mmap.mmap(channel.fd, 4, access=runner.mmap.ACCESS_WRITE) as independent:
            channel.close()
            independent[:] = runner.struct.pack("=I", 3)
            self.assertEqual(runner.struct.unpack("=I", independent[:])[0], 3)

    def test_unavailable_api_reports_fixed_diagnostic_failure(self):
        with mock.patch.object(runner.sys, "platform", "unsupported"):
            with self.assertRaisesRegex(runner.DiagnosticFailure, "^producer-status-api-unavailable$"):
                runner.ProducerStatus()

    @unittest.skipUnless(runner.sys.platform == "linux" and hasattr(runner.os, "memfd_create"), "Linux memfd required")
    def test_mapping_setup_failure_closes_the_allocated_descriptor(self):
        allocated = []
        create = runner.os.memfd_create
        def allocate(*args):
            fd = create(*args)
            allocated.append(fd)
            return fd
        with mock.patch.object(runner.os, "memfd_create", side_effect=allocate), \
                mock.patch.object(runner.mmap, "mmap", side_effect=OSError(CANARY)):
            with self.assertRaisesRegex(runner.DiagnosticFailure, "^producer-status-api-unavailable$"):
                runner.ProducerStatus()
        self.assertEqual(len(allocated), 1)
        with self.assertRaises(OSError):
            runner.os.fstat(allocated[0])


class SequencingTests(unittest.TestCase):
    def test_failed_first_suite_preserves_original_status_and_blocks_second_mutation(self):
        first = dict(passed(), test_returncode=17)
        run = mock.Mock(return_value=first)
        results = runner.run_pair(run)
        run.assert_called_once_with("baseline")
        self.assertEqual(runner.pair_returncode(results), 17)

    def test_unsafe_shutdown_blocks_second_run_even_when_suite_passed(self):
        for status in ("shutdown-failed", "killed-after-shutdown-timeout", "terminated-after-console-timeout"):
            with self.subTest(status=status):
                run = mock.Mock(return_value=dict(passed(), shutdown=status))
                self.assertEqual(len(runner.run_pair(run)), 1)
                run.assert_called_once_with("baseline")

    def test_partial_error_capture_or_abnormal_emulator_exit_blocks_second_run(self):
        for first in (dict(passed(), errors={"status": "partial"}),
                      dict(passed(), emulator_returncode=1)):
            with self.subTest(first=first):
                run = mock.Mock(return_value=first)
                self.assertEqual(len(runner.run_pair(run)), 1)
                run.assert_called_once_with("baseline")

    def test_second_run_is_full_and_sequential_and_missing_trace_is_failure(self):
        calls = []
        def run(variant):
            calls.append(variant)
            return passed(variant)
        results = runner.run_pair(run)
        self.assertEqual(calls, ["baseline", "instrumented"])
        self.assertEqual(runner.pair_returncode(results), 0)
        results[-1]["trace"] = {"complete": False}
        self.assertEqual(runner.pair_returncode(results), 1)
        results[-1]["test_returncode"] = 23
        self.assertEqual(runner.pair_returncode(results), 23)

    def test_sdk_environment_does_not_inherit_account_or_ci_secrets(self):
        with mock.patch.dict(runner.os.environ, {"MAESTRO_AUTH_STATE_JSON": CANARY, "GITHUB_TOKEN": CANARY,
                                               "GFXSTREAM_COLOR_BUFFER_TRACE_FILE": CANARY}, clear=False):
            environment = runner.tool_environment(Path("/tmp/private-avd"))
        self.assertNotIn(CANARY, environment.values())
        self.assertEqual(environment["ANDROID_AVD_HOME"], "/tmp/private-avd")
        self.assertEqual(environment["TZ"], "Asia/Seoul")

    def test_graceful_shutdown_precedes_any_signal_and_bounded_kill(self):
        process = mock.Mock(pid=123)
        process.poll.return_value = None
        process.wait.side_effect = [subprocess.TimeoutExpired("private", 30), subprocess.TimeoutExpired("private", 30), 0]
        order = []
        with mock.patch.object(runner, "sdk_command", side_effect=lambda *args, **kwargs: order.append("console")), \
                mock.patch.object(runner.os, "killpg", side_effect=lambda _pid, sig: order.append(sig)):
            status = runner.stop_emulator(process, {})
        self.assertEqual(order, ["console", runner.signal.SIGTERM, runner.signal.SIGKILL])
        self.assertEqual(status, "killed-after-shutdown-timeout")
        process.wait.assert_has_calls([mock.call(timeout=30), mock.call(timeout=30), mock.call(timeout=10)])

    def test_first_original_failure_survives_trace_export_failure_in_finally(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            process = mock.Mock(pid=123)
            process.poll.return_value = 0
            process.stdout = io.BytesIO(b"")
            collector = mock.Mock()
            collector.finish.return_value = {"status": "captured", "records": 0}
            status_channel = mock.Mock(fd=123)
            status_channel.read_after_exit.return_value = 2
            with mock.patch.object(runner, "create_avd"), mock.patch.object(runner, "sdk_command"), \
                    mock.patch.object(runner.subprocess, "Popen", return_value=process), \
                    mock.patch.object(runner.trace, "ErrorCollector", return_value=collector), \
                    mock.patch.object(runner, "ProducerStatus", return_value=status_channel), \
                    mock.patch.object(runner, "boot"), mock.patch.object(runner.core, "run_suite", return_value=17), \
                    mock.patch.object(runner, "stop_emulator", return_value="console-shutdown") as stop, \
                    mock.patch.object(runner.trace, "export_trace", side_effect=OSError(CANARY)), \
                    mock.patch.object(runner.signal, "signal"):
                result = runner.run_variant("instrumented", root / "emulator", root / "apk", root / "private", root / "safe")
            self.assertEqual(result["test_returncode"], 17)
            self.assertFalse(result["trace"]["complete"])
            stop.assert_called_once()
            collector.finish.assert_called_once()
            self.assertNotIn(CANARY, (root / "safe/outcome.json").read_text())

    def test_outcome_write_failure_does_not_replace_original_test_failure(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            process = mock.Mock(pid=123)
            process.poll.return_value = 0
            collector = mock.Mock()
            collector.finish.return_value = {"status": "captured", "records": 0}
            with mock.patch.object(runner, "create_avd"), mock.patch.object(runner, "sdk_command"), \
                    mock.patch.object(runner.subprocess, "Popen", return_value=process), \
                    mock.patch.object(runner.trace, "ErrorCollector", return_value=collector), \
                    mock.patch.object(runner, "boot"), mock.patch.object(runner.core, "run_suite", return_value=17), \
                    mock.patch.object(runner, "stop_emulator", return_value="console-shutdown"), \
                    mock.patch.object(runner, "write_json", side_effect=OSError(CANARY)), \
                    mock.patch.object(runner.signal, "signal"):
                result = runner.run_variant("baseline", root / "emulator", root / "apk", root / "private", root / "safe")
            self.assertEqual(result["test_returncode"], 17)
            self.assertEqual(result["failure"], "outcome-write-failed")
            self.assertEqual(runner.pair_returncode([result]), 17)

    @unittest.skipUnless(runner.sys.platform == "linux" and hasattr(runner.os, "memfd_create"), "Linux memfd required")
    def test_live_child_shutdown_failure_does_not_read_clean_status_or_leak_parent_resources(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            process = mock.Mock(pid=123)
            process.poll.return_value = None
            collector = mock.Mock()
            collector.finish.return_value = {"status": "captured", "records": 0}
            channel = runner.ProducerStatus()
            self.addCleanup(channel.close)
            channel.mapping[:] = runner.struct.pack("=I", 2)
            fd = channel.fd
            with mock.patch.object(runner, "create_avd"), mock.patch.object(runner, "sdk_command"), \
                    mock.patch.object(runner.subprocess, "Popen", return_value=process), \
                    mock.patch.object(runner.trace, "ErrorCollector", return_value=collector), \
                    mock.patch.object(runner, "ProducerStatus", return_value=channel), \
                    mock.patch.object(runner, "boot"), mock.patch.object(runner.core, "run_suite", return_value=17), \
                    mock.patch.object(runner, "stop_emulator", side_effect=OSError(CANARY)), \
                    mock.patch.object(runner.signal, "signal"):
                result = runner.run_variant("instrumented", root / "emulator", root / "apk", root / "private", root / "safe")
            self.assertEqual(result["test_returncode"], 17)
            self.assertEqual(result["shutdown"], "shutdown-failed")
            self.assertEqual(result["failure"], "producer-status-process-not-exited")
            self.assertFalse(result["trace"]["complete"])
            self.assertEqual(result["trace"]["producer_status"], "unavailable")
            self.assertIsNone(channel.fd)
            self.assertIsNone(channel.mapping)
            with self.assertRaises(OSError):
                runner.os.fstat(fd)
            self.assertNotIn(CANARY, (root / "safe/outcome.json").read_text())


if __name__ == "__main__":
    unittest.main()
