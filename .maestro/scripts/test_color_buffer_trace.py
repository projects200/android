"""Exercise untrusted metadata and lifecycle evidence without a renderer or accounts."""

import importlib.util
import io
import json
from pathlib import Path
import tempfile
import unittest
from unittest import mock


SPEC = importlib.util.spec_from_file_location("tested_color_buffer_trace", Path(__file__).with_name("color-buffer-trace.py"))
trace = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(trace)
CANARY = "synthetic-private-data-not-for-artifacts"


def record(seq, event, reason, handle=None, count=None, found=None, pid=42):
    return {"schema": 1, "seq": seq, "mono_ns": seq * 100, "pid": pid, "tid": pid + 1,
            "event": event, "handle": handle, "reason": reason, "refcount": count, "found": found}


def capture():
    return [record(1, "session_start", "enabled"),
            record(2, "register", "create_guest", 7, 1, True),
            record(3, "bind", "texture", 7, 1, True),
            record(4, "session_end", "clean")]


class TraceTests(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name)
        self.source = self.root / "private.jsonl"
        self.destination = self.root / "safe.jsonl"
        self.summary = self.root / "summary.json"

    def export(self, records, suffix=b"", producer_status=2):
        self.source.write_bytes(b"".join(json.dumps(item).encode("ascii") + b"\n" for item in records) + suffix)
        result = trace.export_trace(self.source, self.destination, self.summary, producer_status=producer_status)
        return result, json.loads(self.summary.read_text(encoding="ascii"))

    def test_complete_capture_requires_lifecycle_not_just_footer(self):
        result, summary = self.export(capture())
        self.assertTrue(result["complete"])
        self.assertEqual(summary["root_cause"], "not-established")
        self.assertEqual(len(self.destination.read_text().splitlines()), 4)
        result, summary = self.export([record(1, "session_start", "enabled"), record(2, "session_end", "clean")])
        self.assertFalse(result["complete"])
        self.assertIn("missing-lifecycle-coverage", summary["issues"])

    def test_valid_footer_cannot_override_failed_or_unfinished_producer_status(self):
        records = capture()[:2] + [record(3, "erase", "final_ref", 7, 0, True),
                                   record(4, "bind", "texture", 7, None, False),
                                   record(5, "session_end", "clean")]
        for status, name in ((3, "failed"), (1, "active"), (0, "uninitialized"),
                             (None, "unavailable"), (True, "invalid"), (99, "invalid")):
            with self.subTest(status=status):
                result, summary = self.export(records, producer_status=status)
                self.assertFalse(result["complete"])
                self.assertEqual(result["producer_status"], name)
                self.assertIn("producer-status-" + name, summary["issues"])
                self.assertEqual(summary["missing_binds"][0]["relationship"], "inconclusive-partial-trace")

    def test_clean_producer_status_does_not_override_jsonl_loss(self):
        result, summary = self.export(capture() + [record(5, "loss", "late_event")], producer_status=2)
        self.assertEqual(result["producer_status"], "clean")
        self.assertFalse(result["complete"])
        self.assertIn("producer-record-loss", summary["issues"])

    def test_export_requires_explicit_producer_status(self):
        with self.assertRaises(TypeError):
            trace.export_trace(self.source, self.destination, self.summary)

    def test_bind_after_actual_erase_is_not_a_refcount_root_cause_claim(self):
        records = capture()[:2] + [record(3, "erase", "final_ref", 7, 0, True),
                                   record(4, "bind", "texture", 7, None, False),
                                   record(5, "session_end", "clean")]
        result, summary = self.export(records)
        self.assertTrue(result["complete"])
        finding = summary["missing_binds"][0]
        self.assertEqual(finding["relationship"], "after-observed-erase")
        self.assertEqual((finding["generation"], finding["registration_seq"], finding["erase_seq"]), (1, 2, 3))
        self.assertEqual(summary["root_cause"], "not-established")

    def test_reused_handle_has_a_new_generation_not_old_erase(self):
        records = capture()[:2] + [record(3, "erase", "final_ref", 7, 0, True),
                                   record(4, "register", "create_legacy", 7, 1, True),
                                   record(5, "bind", "texture2", 7, None, False),
                                   record(6, "session_end", "clean")]
        result, summary = self.export(records)
        self.assertTrue(result["complete"])
        finding = summary["missing_binds"][0]
        self.assertEqual(finding["generation"], 2)
        self.assertEqual(finding["registration_seq"], 4)
        self.assertIsNone(finding["erase_seq"])
        self.assertEqual(finding["relationship"], "after-observed-registration")

    def test_absent_registration_is_only_observed_in_complete_capture(self):
        records = capture()[:2] + [record(3, "bind", "texture", 99, None, False), record(4, "session_end", "clean")]
        result, summary = self.export(records)
        self.assertTrue(result["complete"])
        self.assertEqual(summary["missing_binds"][0]["relationship"], "without-observed-registration")
        self.assertIsNone(summary["missing_binds"][0]["generation"])
        result, summary = self.export(records[:-1])
        self.assertFalse(result["complete"])
        self.assertEqual(summary["missing_binds"][0]["relationship"], "inconclusive-partial-trace")

    def test_gap_cannot_establish_that_handle_was_not_registered_again(self):
        records = capture()[:2] + [record(3, "erase", "delayed_expiry", 7, 0, True),
                                   record(5, "bind", "texture", 7, None, False), record(6, "session_end", "clean")]
        result, summary = self.export(records)
        self.assertFalse(result["complete"])
        self.assertIn("sequence-gap-or-reorder", summary["issues"])
        self.assertEqual(summary["missing_binds"][0]["relationship"], "inconclusive-partial-trace")

    def test_per_process_sequences_and_handle_generations_are_independent(self):
        other = [dict(item, pid=90, tid=91) for item in capture()]
        result, summary = self.export([item for pair in zip(capture(), other) for item in pair])
        self.assertTrue(result["complete"])
        self.assertEqual(summary["processes"], 2)
        self.assertEqual(summary["registrations"], 2)

    def test_loss_after_footer_invalidates_capture(self):
        for reason in ("limit", "late_event"):
            with self.subTest(reason=reason):
                result, summary = self.export(capture() + [record(5, "loss", reason)])
                self.assertFalse(result["complete"])
                self.assertIn("producer-record-loss", summary["issues"])

    def test_footer_before_unrecorded_events_and_clock_reversal_are_incomplete(self):
        for extra in (record(5, "lookup", "generic", 7, 1, True),
                      dict(record(5, "lookup", "generic", 7, 1, True), mono_ns=0)):
            result, summary = self.export(capture() + [extra])
            self.assertFalse(result["complete"])
            self.assertIn("records-after-footer", summary["issues"])

    def test_invalid_metadata_is_never_copied(self):
        mutations = [dict(capture()[1], command=CANARY), dict(capture()[1], reason=CANARY),
                     dict(capture()[1], seq=True), dict(capture()[1], mono_ns=-1),
                     dict(capture()[1], pid=0), dict(capture()[1], tid=1 << 32),
                     dict(capture()[1], handle=1 << 32), dict(capture()[1], refcount=1.0),
                     dict(capture()[1], refcount=-1), dict(capture()[1], found=1),
                     dict(capture()[1], event=[]), dict(capture()[1], handle=False),
                     dict(capture()[1], schema=True), dict(capture()[1], refcount=float("nan"))]
        for item in mutations:
            with self.subTest(item=item):
                result, summary = self.export([capture()[0], item, *capture()[2:]])
                self.assertFalse(result["complete"])
                self.assertIn("invalid-record", summary["issues"])
                self.assertNotIn(CANARY, self.destination.read_text() + self.summary.read_text())
                self.assertEqual(len(self.destination.read_text().splitlines()), 3)

    def test_observation_invariants_reject_fake_presence_or_request_counts(self):
        invalid = [record(2, "close", "guest", 7, 1, True),
                   record(2, "delay", "guest", 7, None, False),
                   record(2, "open", "guest", 7, None, None),
                   record(2, "refcount", "guest", 7, None, True),
                   record(2, "bind", "texture", 7, 0, False),
                   record(2, "session_end", "clean", 7)]
        for item in invalid:
            with self.subTest(item=item), self.assertRaises(trace.TraceFailure):
                trace.validate_record(item)
        self.assertEqual(trace.validate_record(record(2, "lookup", "generic", 0, None, False))["handle"], 0)

    def test_malformed_duplicate_unknown_and_truncated_json_are_incomplete(self):
        for suffix in (b'{"schema":1,"schema":1}\n', b'{not-json}\n', b'[]\n', b'{}',
                       b'{"private":"' + CANARY.encode() + b'"}\n', b"x" * 1025 + b"\n"):
            with self.subTest(suffix=suffix[:30]):
                result, _ = self.export(capture(), suffix)
                self.assertFalse(result["complete"])
                self.assertNotIn(CANARY, self.destination.read_text() + self.summary.read_text())

    def test_consumer_caps_and_missing_file_never_pass(self):
        with mock.patch.object(trace, "MAX_RECORDS", 2):
            result, summary = self.export(capture())
        self.assertFalse(result["complete"])
        self.assertIn("consumer-record-limit", summary["issues"])
        self.assertTrue(result["truncated"])
        with mock.patch.object(trace, "MAX_TRACE_BYTES", 300):
            result, summary = self.export(capture())
        self.assertFalse(result["complete"])
        self.assertTrue(result["truncated"])
        self.assertIn("trace-file-limit", summary["issues"])
        self.assertLessEqual(self.destination.stat().st_size, 300)
        self.source.unlink()
        result = trace.export_trace(self.source, self.destination, self.summary, producer_status=2)
        self.assertFalse(result["complete"])
        self.assertEqual(self.destination.read_bytes(), b"")
        self.export([])
        self.assertFalse(json.loads(self.summary.read_text())["complete"])

    def test_only_exact_error_message_and_bounded_handle_survive(self):
        self.assertEqual(trace.safe_error((CANARY + " Failed to find ColorBuffer: 123\n").encode()),
                         {"event": "missing-color-buffer", "handle": 123})
        self.assertEqual(trace.safe_error(b"Failed to bind to texture: invalid color buffer.\n"),
                         {"event": "invalid-texture-bind", "handle": None})
        for line in (b"Failed to find ColorBuffer: 4294967296\n", b"Failed to find ColorBuffer: -1\n",
                     b"Failed to find ColorBuffer: 1 token\n", b"Failed to bind to texture: invalid color buffer. secret\n",
                     b"Failed to bind to texture: invalid color buffer\n",
                     b"Failed to bind to texture: invalid color bufferX\n"):
            self.assertIsNone(trace.safe_error(line))

    def test_private_native_output_is_drained_but_never_published(self):
        stream = io.BytesIO((CANARY + "\nFailed to find ColorBuffer: 12\n").encode() +
                            b"Failed to bind to texture: invalid color buffer.\n")
        collector = trace.ErrorCollector(stream, self.destination)
        collector.start()
        status = collector.finish()
        self.assertEqual(status["status"], "captured")
        self.assertEqual((status["missing_color_buffers"], status["invalid_texture_binds"]), (1, 1))
        safe = self.destination.read_text()
        self.assertNotIn(CANARY, safe)
        for line in safe.splitlines():
            item = json.loads(line)
            self.assertGreater(item["host_epoch_ns"], 0)
            self.assertGreater(item["host_mono_ns"], 0)

    def test_observed_period_terminated_bind_error_is_counted_exactly_once(self):
        stream = io.BytesIO(b"Failed to bind to texture: invalid color buffer.\n"
                            b"Failed to bind to texture: invalid color buffer\n"
                            b"Failed to bind to texture: invalid color bufferX\n")
        collector = trace.ErrorCollector(stream, self.destination)
        collector.start()
        status = collector.finish()
        self.assertEqual(status["status"], "captured")
        self.assertEqual(status["invalid_texture_binds"], 1)
        self.assertEqual(status["missing_color_buffers"], 0)
        self.assertEqual(status["records"], 1)
        self.assertEqual(json.loads(self.destination.read_text())["event"], "invalid-texture-bind")

    def test_error_record_limit_keeps_draining_and_marks_partial(self):
        stream = io.BytesIO(b"Failed to find ColorBuffer: 1\n" * 3)
        collector = trace.ErrorCollector(stream, self.destination)
        with mock.patch.object(trace, "MAX_ERROR_RECORDS", 1):
            collector.start()
            status = collector.finish()
        self.assertEqual(stream.read(), b"")
        self.assertEqual(status["status"], "partial")
        self.assertTrue(status["truncated"])
        self.assertEqual(len(self.destination.read_text().splitlines()), 1)

    def test_output_failure_still_drains_renderer_without_publishing_private_data(self):
        stream = io.BytesIO((CANARY + "\nFailed to find ColorBuffer: 1\n").encode() * 100)
        collector = trace.ErrorCollector(stream, self.destination)
        with mock.patch.object(Path, "open", side_effect=OSError(CANARY)):
            collector.start()
            status = collector.finish()
        self.assertEqual(stream.read(), b"")
        self.assertEqual(status["status"], "partial")
        self.assertFalse(self.destination.exists())

    def test_oversize_native_line_is_discarded_and_subsequent_error_is_rewritten(self):
        stream = io.BytesIO(CANARY.encode() * 1000 + b"\nFailed to find ColorBuffer: 7\n")
        collector = trace.ErrorCollector(stream, self.destination)
        collector.start()
        status = collector.finish()
        self.assertTrue(status["truncated"])
        self.assertEqual(status["records"], 1)
        self.assertNotIn(CANARY, self.destination.read_text())


if __name__ == "__main__":
    unittest.main()
