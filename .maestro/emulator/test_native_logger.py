#!/usr/bin/env python3
"""CI-only behavioral proof: logger append failure cannot retain clean producer status."""

import fcntl
import json
import mmap
import os
from pathlib import Path
import shutil
import struct
import subprocess
import tempfile
import unittest


HERE = Path(__file__).resolve().parent


class NativeLoggerStatusTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.build_temp = tempfile.TemporaryDirectory()
        cls.addClassCleanup(cls.build_temp.cleanup)
        compiler = shutil.which("g++")
        if compiler is None:
            raise RuntimeError("Native logger regression tests require the Ubuntu C++ compiler")
        cls.binary = Path(cls.build_temp.name) / "native-logger-test"
        subprocess.run(
            [compiler, "-std=c++17", "-Wall", "-Wextra", "-Werror", "-pthread", "-I", str(HERE),
             str(HERE / "test-color-buffer-trace.cpp"), "-o", str(cls.binary)],
            check=True,
        )

    def capture(self, scenario, *, status_text=None, provide_status=True, status_size=4):
        with tempfile.TemporaryDirectory() as temp:
            trace = Path(temp) / "trace.jsonl"
            descriptor = os.memfd_create("color-buffer-test-status", os.MFD_CLOEXEC | os.MFD_ALLOW_SEALING)
            try:
                os.ftruncate(descriptor, status_size)
                with mmap.mmap(descriptor, status_size, flags=mmap.MAP_SHARED,
                               prot=mmap.PROT_READ | mmap.PROT_WRITE) as status:
                    status[:4] = struct.pack("=I", 0)
                    fcntl.fcntl(descriptor, fcntl.F_ADD_SEALS,
                                fcntl.F_SEAL_SHRINK | fcntl.F_SEAL_GROW | fcntl.F_SEAL_SEAL)
                    env = os.environ.copy()
                    env["GFXSTREAM_COLOR_BUFFER_TRACE_FILE"] = str(trace)
                    env.pop("GFXSTREAM_COLOR_BUFFER_TRACE_STATUS_FD", None)
                    if provide_status:
                        env["GFXSTREAM_COLOR_BUFFER_TRACE_STATUS_FD"] = (
                            str(descriptor) if status_text is None else status_text)
                    subprocess.run([str(self.binary), scenario], env=env, pass_fds=(descriptor,),
                                   check=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
                    # subprocess.run has waited for actual process death, including
                    # all registered exit callbacks, before the shared read.
                    producer_status = struct.unpack("=I", status[:4])[0]
                content = trace.read_bytes() if trace.exists() else None
                return producer_status, content
            finally:
                os.close(descriptor)

    def test_wrong_status_size_cannot_start_capture(self):
        status, content = self.capture("normal", status_size=8)
        self.assertEqual(status, 0)
        self.assertIsNone(content)

    def test_successful_footer_has_clean_status(self):
        status, content = self.capture("normal")
        self.assertEqual(status, 2)
        records = [json.loads(line) for line in content.splitlines()]
        self.assertEqual(records[0]["event"], "session_start")
        self.assertEqual(records[-1]["event"], "session_end")

    def test_zero_byte_late_failures_override_a_still_valid_clean_footer(self):
        for scenario in ("late-write", "late-eintr", "late-clock", "late-lock"):
            with self.subTest(scenario=scenario):
                status, content = self.capture(scenario)
                records = [json.loads(line) for line in content.splitlines()]
                self.assertEqual(records[-1]["event"], "session_end")
                # This is the regression: valid old JSONL is not sufficient.
                # The shared failure status survives the zero-byte append loss
                # and a second finish() cannot reset failure to clean.
                self.assertEqual(status, 3)

    def test_partial_late_write_and_reported_late_event_are_failed(self):
        for scenario in ("late-partial", "late-normal"):
            with self.subTest(scenario=scenario):
                status, unused = self.capture(scenario)
                self.assertEqual(status, 3)

    def test_abrupt_exit_remains_active_not_clean(self):
        status, content = self.capture("abrupt")
        self.assertEqual(status, 1)
        self.assertNotIn("session_end", [json.loads(line)["event"] for line in content.splitlines()])

    def test_initial_logging_failures_are_failed(self):
        for scenario in ("initial-write", "initial-clock", "initial-lock", "initial-atexit"):
            with self.subTest(scenario=scenario):
                status, unused = self.capture(scenario)
                self.assertEqual(status, 3)

    def test_missing_or_invalid_status_descriptor_cannot_start_capture(self):
        status, content = self.capture("normal", provide_status=False)
        self.assertEqual(status, 0)
        self.assertIsNone(content)
        for text in ("", "-1", "+1", " 1", "1x", "2147483648", "999999999999999999999999999999"):
            with self.subTest(text=text):
                status, content = self.capture("normal", status_text=text)
                self.assertEqual(status, 0)
                self.assertIsNone(content)


if __name__ == "__main__":
    unittest.main()
