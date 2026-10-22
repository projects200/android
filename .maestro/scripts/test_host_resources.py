"""Check persisted host diagnostics with synthetic proc data, never adb."""

import importlib.util
import json
import math
from pathlib import Path
import tempfile
import threading
from types import SimpleNamespace
import unittest
from unittest import mock


SPEC = importlib.util.spec_from_file_location(
    "maestro_host_resources", Path(__file__).with_name("host-resources.py"),
)
resources = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(resources)

CANARY = "synthetic-private-token-command-url-identifier"
CPU_FIRST = "cpu 100 20 30 400 50 10 5 5 900 800\ncpu0 0\ncpu1 0\n"
CPU_SECOND = "cpu 120 20 40 450 60 15 8 7 9900 9800\ncpu0 0\ncpu1 0\n"
MEMORY = "MemTotal: 1000 kB\nMemAvailable: 600 kB\nMemFree: 200 kB\nSwapTotal: 300 kB\nSwapFree: 250 kB\n"
PRESSURE = "some avg10=1.25 avg60=2.5 avg300=3.75 total=400\nfull avg10=0.25 avg60=0.5 avg300=0.75 total=50\n"


class Clock:
    def __init__(self):
        self.now = 0.0

    def monotonic(self):
        return 100.0 + self.now

    def time(self):
        return 1700000000.0 + self.now


class SampleGate:
    """Replace the worker's timed wait with explicit, synchronized sample steps."""

    def __init__(self):
        self.condition = threading.Condition()
        self.waits = 0
        self.tokens = 0
        self.stopped = False

    def is_set(self):
        with self.condition:
            return self.stopped

    def set(self):
        with self.condition:
            self.stopped = True
            self.condition.notify_all()

    def wait(self, timeout):
        with self.condition:
            self.waits += 1
            self.condition.notify_all()
            ready = self.condition.wait_for(lambda: self.stopped or self.tokens, timeout=5)
            if not ready:
                raise RuntimeError("sample-gate-timeout")
            if self.stopped:
                return True
            self.tokens -= 1
            return False

    def advance(self):
        with self.condition:
            self.tokens += 1
            self.condition.notify_all()

    def wait_for_samples(self, count):
        with self.condition:
            return self.condition.wait_for(lambda: self.waits >= count, timeout=5)


class HostResourceTests(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory(
            prefix="maestro-host-resources-", dir=Path("/tmp").resolve(),
        )
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name)
        self.proc = self.root / "proc"
        self.proc.mkdir()
        (self.proc / "pressure").mkdir()
        self.write("stat", CPU_FIRST)
        self.write("meminfo", MEMORY)
        self.write("loadavg", "0.25 0.5 0.75 2/300 12345\n")
        for name in ("cpu", "memory", "io"):
            self.write("pressure/" + name, PRESSURE)
        self.clock = Clock()
        self.gate = SampleGate()
        for patch in (
            mock.patch.object(resources, "PROC_ROOT", self.proc),
            mock.patch.object(resources, "sys", SimpleNamespace(platform="linux")),
            mock.patch.object(resources, "time", self.clock),
            mock.patch.object(resources, "Event", return_value=self.gate),
        ):
            patch.start()
            self.addCleanup(patch.stop)
        self.destination = self.root / "diagnostics" / "host-resources.log"
        self.sampler = resources.HostResourceSampler(self.destination)
        self.addCleanup(self.sampler.stop)

    def write(self, name, text):
        (self.proc / name).write_text(text, encoding="ascii")

    def records(self):
        return [json.loads(line) for line in self.destination.read_text(encoding="ascii").splitlines()]

    def samples(self):
        return [record for record in self.records() if record["kind"] == "sample"]

    def start_sample(self):
        self.sampler.start()
        self.assertTrue(self.gate.wait_for_samples(1), "worker did not persist its first sample")
        return self.samples()[0]

    def next_sample(self, count, gap=1.0):
        self.clock.now += gap
        self.gate.advance()
        self.assertTrue(self.gate.wait_for_samples(count), "worker did not persist the next sample")
        return self.samples()[-1]

    def join_worker(self):
        self.sampler._thread.join(timeout=5)
        self.assertFalse(self.sampler._thread.is_alive(), "worker did not terminate")

    def assert_safe_values(self, value):
        if isinstance(value, dict):
            for item in value.values():
                self.assert_safe_values(item)
        elif isinstance(value, list):
            for item in value:
                self.assert_safe_values(item)
        elif isinstance(value, (int, float)):
            self.assertTrue(math.isfinite(value))
            self.assertGreaterEqual(value, 0)
        elif value is not None:
            self.assertIn(value, {
                "start", "sample", "summary", "started", "stopped", "host-only",
                "periodic-not-continuous", "ok", "partial", "unsupported", "unavailable",
                "malformed", "first-sample", "counter-reset", "no-progress", "sample-limit",
                "output-limit", "write-unavailable", "sampling-unavailable",
            })

    def test_persisted_cpu_deltas_exclude_duplicate_guest_time(self):
        first = self.start_sample()
        self.assertEqual(first["cpu"]["logical_cpus"], 2)
        self.assertEqual(first["cpu"]["interval_status"], "first-sample")
        self.assertIsNone(first["cpu"]["busy_percent"])
        self.assertIsNone(first["cpu"]["iowait_percent"])
        self.write("stat", CPU_SECOND)
        second = self.next_sample(2)
        self.assertEqual(second["status"], "ok")
        self.assertEqual(second["cpu"]["interval_status"], "ok")
        self.assertAlmostEqual(second["cpu"]["busy_percent"], 40.0)
        self.assertAlmostEqual(second["cpu"]["iowait_percent"], 10.0)
        self.assertEqual(second["host_epoch"], 1700000001.0)
        self.assertEqual(second["elapsed_seconds"], 1.0)
        self.assertEqual(second["sample_gap_seconds"], 1.0)
        self.assertEqual(second["clock_status"], "ok")

    def test_memory_load_and_pressure_keep_only_numeric_allowed_fields(self):
        self.write("stat", CPU_FIRST + "intr " + CANARY + "\n")
        self.write("meminfo", MEMORY + "PrivateIdentifier: " + CANARY + "\n")
        self.write("loadavg", "0.25 0.5 0.75 " + CANARY + " " + CANARY + "\n")
        self.write("pressure/cpu", PRESSURE.replace("total=400", "total=400 secret=" + CANARY))
        sample = self.start_sample()
        self.assertEqual(sample["memory"], {
            "status": "ok", "total_bytes": 1024000, "available_bytes": 614400,
            "free_bytes": 204800, "swap_total_bytes": 307200, "swap_free_bytes": 256000,
        })
        self.assertEqual(sample["load"], {"status": "ok", "avg1": 0.25, "avg5": 0.5, "avg15": 0.75})
        self.assertEqual(sample["pressure_cpu"]["some"], {
            "avg10": 1.25, "avg60": 2.5, "avg300": 3.75, "total_us": 400,
        })
        self.assertEqual(sample["pressure_io"]["full"]["total_us"], 50)
        self.sampler.stop()
        self.assertNotIn(CANARY, self.destination.read_text(encoding="ascii"))
        self.assert_safe_values(self.records())

    def test_any_counter_reset_is_unknown_even_when_total_increases(self):
        self.start_sample()
        self.write("stat", "cpu 1 20 900 450 60 15 8 7 0 0\ncpu0 0\n")
        reset = self.next_sample(2)
        self.assertEqual(reset["cpu"]["interval_status"], "counter-reset")
        self.assertIsNone(reset["cpu"]["busy_percent"])
        self.assertIsNone(reset["cpu"]["iowait_percent"])
        self.write("stat", "cpu 11 20 905 480 65 15 8 7 0 0\ncpu0 0\n")
        recovered = self.next_sample(3)
        self.assertAlmostEqual(recovered["cpu"]["busy_percent"], 30.0)
        self.assertAlmostEqual(recovered["cpu"]["iowait_percent"], 10.0)

    def test_unchanged_counters_do_not_fabricate_idle_load(self):
        self.start_sample()
        sample = self.next_sample(2)
        self.assertEqual(sample["cpu"]["interval_status"], "no-progress")
        self.assertIsNone(sample["cpu"]["busy_percent"])
        self.assertIsNone(sample["cpu"]["iowait_percent"])

    def test_partial_source_failure_preserves_other_metrics_and_breaks_cpu_continuity(self):
        self.start_sample()
        self.write("stat", "cpu " + CANARY + "\n")
        failed = self.next_sample(2, gap=5.0)
        self.assertEqual(failed["status"], "partial")
        self.assertEqual(failed["cpu"]["status"], "malformed")
        self.assertIsNone(failed["cpu"]["busy_percent"])
        self.assertEqual(failed["memory"]["available_bytes"], 614400)
        self.assertEqual(failed["sample_gap_seconds"], 5.0)
        self.write("stat", CPU_SECOND)
        recovered = self.next_sample(3)
        self.assertEqual(recovered["cpu"]["interval_status"], "first-sample")
        self.assertIsNone(recovered["cpu"]["busy_percent"])
        self.sampler.stop()
        self.assertEqual(self.records()[-1]["elapsed_seconds"], 6.0)
        self.assertNotIn(CANARY, self.destination.read_text(encoding="ascii"))

    def test_unavailable_unsupported_and_malformed_sources_are_distinct(self):
        (self.proc / "stat").unlink()
        (self.proc / "pressure/cpu").unlink()
        self.write("pressure/memory", "some avg10=" + CANARY + "\n")
        sample = self.start_sample()
        self.assertEqual(sample["cpu"]["status"], "unavailable")
        self.assertIsNone(sample["cpu"]["logical_cpus"])
        self.assertEqual(sample["pressure_cpu"], {"status": "unsupported"})
        self.assertEqual(sample["pressure_memory"], {"status": "malformed"})
        self.assertEqual(sample["status"], "partial")
        self.assertEqual(sample["load"]["avg1"], 0.25)
        self.assertNotIn(CANARY, self.destination.read_text(encoding="ascii"))

    def test_non_linux_sources_are_unsupported_not_zero_measurements(self):
        with mock.patch.object(resources, "sys", SimpleNamespace(platform="darwin")):
            sample = self.start_sample()
            self.sampler.stop()
        self.assertEqual(sample["status"], "unsupported")
        self.assertEqual(sample["cpu"]["status"], "unsupported")
        self.assertIsNone(sample["cpu"]["busy_percent"])
        for name in ("memory", "load", "pressure_cpu", "pressure_memory", "pressure_io"):
            self.assertEqual(sample[name], {"status": "unsupported"})

    def test_nonfinite_negative_or_oversized_numbers_never_reach_json(self):
        self.start_sample()
        for count, bad in enumerate(("NaN", "inf", "-1", "1e999", "9" * 100), start=2):
            with self.subTest(value=bad):
                self.write("loadavg", bad + " 0 0 1/1 1\n")
                self.write("pressure/cpu", "some avg10=" + bad + " avg60=0 avg300=0 total=1\n")
                self.write("meminfo", MEMORY.replace("1000 kB", bad + " kB"))
                sample = self.next_sample(count)
                self.assertEqual(sample["load"], {"status": "malformed"})
                self.assertEqual(sample["pressure_cpu"], {"status": "malformed"})
                self.assertEqual(sample["memory"], {"status": "malformed"})
        self.sampler.stop()
        self.assert_safe_values(self.records())

    def test_missing_fields_and_oversized_sources_are_not_success_shaped(self):
        self.write("meminfo", "MemTotal: 1000 kB\n")
        self.write("loadavg", CANARY * (resources.MAX_SOURCE_BYTES // len(CANARY) + 1))
        self.write("pressure/io", "some avg10=0 avg60=0 total=0\n")
        sample = self.start_sample()
        self.assertEqual(sample["memory"], {"status": "malformed"})
        self.assertEqual(sample["load"], {"status": "malformed"})
        self.assertEqual(sample["pressure_io"], {"status": "malformed"})
        self.assertNotIn(CANARY, self.destination.read_text(encoding="ascii"))

    def test_pressure_full_is_optional_without_fabricating_zero(self):
        self.write("pressure/cpu", "some avg10=0 avg60=0 avg300=0 total=0\n")
        sample = self.start_sample()
        self.assertEqual(sample["pressure_cpu"]["status"], "ok")
        self.assertEqual(sample["pressure_cpu"]["some"]["total_us"], 0)
        self.assertNotIn("full", sample["pressure_cpu"])

    def test_invalid_clocks_are_null_without_nonfinite_json(self):
        clock = SimpleNamespace(monotonic=lambda: float("nan"), time=lambda: float("inf"))
        with mock.patch.object(resources, "time", clock):
            sample = self.start_sample()
            self.sampler.stop()
        self.assertIsNone(sample["host_epoch"])
        self.assertIsNone(sample["elapsed_seconds"])
        self.assertIsNone(sample["sample_gap_seconds"])
        self.assertEqual(sample["clock_status"], "unavailable")
        self.assert_safe_values(self.records())

    def test_sample_limit_records_truncation_and_closes_worker(self):
        with mock.patch.object(resources, "MAX_SAMPLES", 2):
            self.start_sample()
            self.clock.now = 1.0
            self.gate.advance()
            self.join_worker()
        records = self.records()
        self.assertEqual(len(self.samples()), 2)
        self.assertEqual(records[0]["sample_limit"], 2)
        self.assertEqual(records[-1]["status"], "sample-limit")
        self.assertEqual(records[-1]["samples"], 2)
        self.assertEqual(records[-1]["coverage"], "periodic-not-continuous")
        self.assertLessEqual(self.destination.stat().st_size, resources.MAX_LOG_BYTES)
        self.assertTrue(all(len(line) <= resources.MAX_RECORD_BYTES for line in self.destination.read_bytes().splitlines(keepends=True)))

    def test_byte_limit_reserves_a_terminal_truncation_record(self):
        limit = resources.MAX_RECORD_BYTES + 512
        with mock.patch.object(resources, "MAX_LOG_BYTES", limit):
            self.sampler.start()
            self.join_worker()
        records = self.records()
        self.assertEqual(self.samples(), [])
        self.assertEqual(records[-1]["status"], "output-limit")
        self.assertEqual(records[-1]["samples"], 0)
        self.assertLessEqual(self.destination.stat().st_size, limit)

    def test_start_and_stop_are_idempotent_and_durable(self):
        self.start_sample()
        self.sampler.start()
        self.sampler.stop()
        self.join_worker()
        before = self.destination.read_bytes()
        self.sampler.stop()
        self.sampler.start()
        self.assertEqual(self.destination.read_bytes(), before)
        self.assertEqual(len(self.samples()), 1)
        self.assertEqual(self.records()[-1]["status"], "stopped")
        self.assertEqual(self.records()[-1]["samples"], 1)
        self.destination.unlink()
        self.assertFalse(self.destination.exists())

    def test_stop_before_start_does_not_create_a_collector(self):
        self.sampler.stop()
        self.sampler.start()
        self.sampler.stop()
        self.assertFalse(self.destination.exists())

    def test_unavailable_threads_do_not_replace_caller_failure(self):
        with mock.patch.object(resources, "Thread", side_effect=RuntimeError(CANARY)):
            with self.assertRaisesRegex(ValueError, "original-ui-failure"):
                try:
                    self.sampler.start()
                    raise ValueError("original-ui-failure")
                finally:
                    self.sampler.stop()
        self.assertFalse(self.destination.exists())

    def test_unavailable_output_does_not_replace_caller_failure(self):
        self.destination.mkdir(parents=True)
        with self.assertRaisesRegex(ValueError, "original-ui-failure"):
            try:
                self.sampler.start()
                self.join_worker()
                raise ValueError("original-ui-failure")
            finally:
                self.sampler.stop()
        self.assertEqual(list(self.destination.iterdir()), [])

    def test_caller_interruption_preserves_samples_and_terminates_worker(self):
        self.start_sample()
        with self.assertRaises(KeyboardInterrupt):
            try:
                raise KeyboardInterrupt
            finally:
                self.sampler.stop()
        self.join_worker()
        self.assertEqual(len(self.samples()), 1)
        self.assertEqual(self.records()[-1]["status"], "stopped")

    def test_stop_is_bounded_when_a_source_is_stalled(self):
        entered = threading.Event()
        release = threading.Event()
        stopped = threading.Event()
        original = self.sampler._source

        def blocked_source(relative, parser, optional=False):
            if relative == "stat":
                entered.set()
                release.wait(timeout=5)
            return original(relative, parser, optional)

        def stop_collector():
            try:
                self.sampler.stop()
            finally:
                stopped.set()

        with mock.patch.object(self.sampler, "_source", blocked_source):
            self.sampler.start()
            stopper = threading.Thread(target=stop_collector, daemon=True)
            try:
                self.assertTrue(entered.wait(timeout=5))
                stopper.start()
                self.assertTrue(stopped.wait(timeout=2), "stop waited for stalled source IO")
                self.assertTrue(self.sampler._thread.daemon)
                self.assertEqual(self.samples(), [])
            finally:
                release.set()
                self.join_worker()
                if stopper.ident is not None:
                    stopper.join(timeout=5)
        self.assertEqual(self.samples(), [])
        self.assertEqual(self.records()[-1]["status"], "stopped")

    def test_partial_write_failure_keeps_previous_complete_records(self):
        self.start_sample()
        original_write = resources.os.write
        failed = False

        def fail_one_write(descriptor, payload):
            nonlocal failed
            if not failed:
                failed = True
                original_write(descriptor, payload[:10])
                raise OSError(CANARY)
            return original_write(descriptor, payload)

        with mock.patch.object(resources.os, "write", fail_one_write):
            self.clock.now = 1.0
            self.gate.advance()
            self.join_worker()
        self.assertEqual(len(self.samples()), 1)
        self.assertEqual(self.records()[-1]["status"], "write-unavailable")
        self.assertEqual(self.records()[-1]["samples"], 1)
        self.assertNotIn(CANARY, self.destination.read_text(encoding="ascii"))


if __name__ == "__main__":
    unittest.main()
