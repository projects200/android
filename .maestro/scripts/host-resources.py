#!/usr/bin/env python3
"""Collect bounded, numeric host evidence without accessing an Android device."""

import json
import math
import os
from pathlib import Path
import stat
import sys
from threading import Event, Thread
import time


PROC_ROOT = Path("/proc")
INTERVAL_SECONDS = 1.0
MAX_SAMPLES = 5400
MAX_SOURCE_BYTES = 262144
MAX_RECORD_BYTES = 4096
MAX_LOG_BYTES = (MAX_SAMPLES + 2) * MAX_RECORD_BYTES
STOP_TIMEOUT_SECONDS = 0.25
MAX_COUNTER = (1 << 64) - 1


def _counter(text):
    if not text.isascii() or not text.isdecimal() or len(text) > 20:
        raise ValueError
    value = int(text)
    if value > MAX_COUNTER:
        raise ValueError
    return value


def _number(text):
    if len(text) > 64:
        raise ValueError
    value = float(text)
    if not math.isfinite(value) or value < 0:
        raise ValueError
    return value


def _parse_cpu(text):
    counters = None
    capacity = 0
    for line in text.splitlines():
        fields = line.split()
        if not fields:
            continue
        if fields[0] == "cpu":
            if counters is not None or len(fields) < 9:
                raise ValueError
            # guest/guest_nice는 user/nice에 이미 포함되므로 다시 더하지 않습니다.
            counters = tuple(_counter(value) for value in fields[1:9])
        elif fields[0].startswith("cpu") and fields[0][3:].isascii() and fields[0][3:].isdecimal():
            capacity += 1
    if counters is None:
        raise ValueError
    return counters, capacity or None


def _parse_memory(text):
    names = {
        "MemTotal:": "total_bytes", "MemAvailable:": "available_bytes",
        "MemFree:": "free_bytes", "SwapTotal:": "swap_total_bytes",
        "SwapFree:": "swap_free_bytes",
    }
    values = {}
    for line in text.splitlines():
        fields = line.split()
        if not fields or fields[0] not in names:
            continue
        key = names[fields[0]]
        if key in values or len(fields) != 3 or fields[2] != "kB":
            raise ValueError
        values[key] = _counter(fields[1]) * 1024
    if len(values) != len(names):
        raise ValueError
    return values


def _parse_load(text):
    fields = text.split()
    if len(fields) < 3:
        raise ValueError
    # 뒤의 runnable/total 및 마지막 PID는 보관하지 않습니다.
    return dict(zip(("avg1", "avg5", "avg15"), (_number(value) for value in fields[:3])))


def _parse_pressure(text):
    values = {}
    names = {"avg10", "avg60", "avg300", "total"}
    for line in text.splitlines():
        fields = line.split()
        if not fields or fields[0] not in ("some", "full"):
            continue
        if fields[0] in values:
            raise ValueError
        metrics = {}
        for field in fields[1:]:
            name, separator, value = field.partition("=")
            if name not in names:
                continue
            if not separator or name in metrics:
                raise ValueError
            metrics[name] = _counter(value) if name == "total" else _number(value)
            if name != "total" and metrics[name] > 100:
                raise ValueError
        if metrics.keys() != names:
            raise ValueError
        metrics["total_us"] = metrics.pop("total")
        values[fields[0]] = metrics
    if "some" not in values:
        raise ValueError
    return values


def _clock(function):
    try:
        value = function()
        return value if math.isfinite(value) and value >= 0 else None
    except (OSError, ValueError, OverflowError):
        return None


def _difference(current, previous):
    if current is None or previous is None or current < previous:
        return None
    return current - previous


class HostResourceSampler:
    def __init__(self, destination: Path):
        self.destination = destination
        self._stop = Event()
        self._thread = None
        self._started = False
        self._stopped = False
        self._initialized = False
        self._bytes_written = 0
        self._samples = 0
        self._previous_cpu = None
        self._origin = None
        self._last_sample = None
        self._write_status = "write-unavailable"

    def start(self):
        if self._started or self._stop.is_set():
            return
        self._started = True
        try:
            self._thread = Thread(target=self._run, daemon=True)
            self._thread.start()
        except (OSError, RuntimeError):
            # 스레드를 만들 수 없으면 파일도 없을 수 있습니다. UI 결과는 유지합니다.
            self._stop.set()

    def stop(self):
        if self._stopped:
            return
        self._stopped = True
        self._stop.set()
        if self._thread is not None:
            try:
                self._thread.join(timeout=STOP_TIMEOUT_SECONDS)
            except (OSError, RuntimeError):
                pass

    def _source(self, relative, parser, optional=False):
        if not sys.platform.startswith("linux"):
            return "unsupported", None
        if self._stop.is_set():
            return "unavailable", None
        descriptor = None
        try:
            flags = os.O_RDONLY | getattr(os, "O_NONBLOCK", 0) | getattr(os, "O_NOFOLLOW", 0)
            descriptor = os.open(PROC_ROOT / relative, flags)
            if not stat.S_ISREG(os.fstat(descriptor).st_mode):
                return "unsupported", None
            with open(descriptor, "r", encoding="ascii", closefd=False) as source:
                text = source.read(MAX_SOURCE_BYTES + 1)
            if len(text) > MAX_SOURCE_BYTES:
                return "malformed", None
            return "ok", parser(text)
        except FileNotFoundError:
            return "unsupported" if optional else "unavailable", None
        except (UnicodeError, ValueError, OverflowError):
            return "malformed", None
        except OSError:
            return "unavailable", None
        finally:
            if descriptor is not None:
                try:
                    os.close(descriptor)
                except OSError:
                    pass

    def _sample(self, now):
        epoch = _clock(time.time)
        elapsed = _difference(now, self._origin)
        cpu_status, parsed_cpu = self._source("stat", _parse_cpu)
        cpu = {
            "status": cpu_status, "logical_cpus": None,
            "capacity_status": "unavailable", "interval_status": "unavailable",
            "busy_percent": None, "iowait_percent": None,
        }
        if parsed_cpu is not None:
            counters, capacity = parsed_cpu
            cpu["logical_cpus"] = capacity
            cpu["capacity_status"] = "ok" if capacity is not None else "unavailable"
            cpu["interval_status"] = "first-sample"
            if self._previous_cpu is not None:
                deltas = tuple(current - previous for current, previous in zip(counters, self._previous_cpu))
                if any(delta < 0 for delta in deltas):
                    cpu["interval_status"] = "counter-reset"
                elif sum(deltas) == 0:
                    cpu["interval_status"] = "no-progress"
                else:
                    total = sum(deltas)
                    cpu["interval_status"] = "ok"
                    cpu["busy_percent"] = 100 * (total - deltas[3] - deltas[4]) / total
                    cpu["iowait_percent"] = 100 * deltas[4] / total
            self._previous_cpu = counters
        else:
            # 관찰하지 못한 구간을 다음 샘플의 정상 CPU 구간으로 합치지 않습니다.
            self._previous_cpu = None
        sources = {"cpu": cpu}
        for name, relative, parser, optional in (
            ("memory", "meminfo", _parse_memory, False),
            ("load", "loadavg", _parse_load, False),
            ("pressure_cpu", "pressure/cpu", _parse_pressure, True),
            ("pressure_memory", "pressure/memory", _parse_pressure, True),
            ("pressure_io", "pressure/io", _parse_pressure, True),
        ):
            status, values = self._source(relative, parser, optional)
            sources[name] = {"status": status, **(values or {})}
        statuses = {source["status"] for source in sources.values()}
        status = "ok" if statuses == {"ok"} else "partial" if "ok" in statuses else "unavailable"
        if statuses == {"unsupported"}:
            status = "unsupported"
        return {
            "kind": "sample", "status": status, "index": self._samples + 1,
            "host_epoch": epoch, "elapsed_seconds": elapsed,
            "clock_status": "ok" if epoch is not None and elapsed is not None else "unavailable",
            "sample_gap_seconds": _difference(now, self._last_sample),
            **sources,
        }

    def _append(self, record, final=False):
        descriptor = None
        self._write_status = "write-unavailable"
        try:
            payload = (json.dumps(record, allow_nan=False, separators=(",", ":")) + "\n").encode("ascii")
            limit = MAX_LOG_BYTES if final else MAX_LOG_BYTES - MAX_RECORD_BYTES
            if len(payload) > MAX_RECORD_BYTES or self._bytes_written + len(payload) > limit:
                self._write_status = "output-limit"
                return False
            flags = os.O_WRONLY | os.O_CREAT | os.O_APPEND
            flags |= getattr(os, "O_NONBLOCK", 0) | getattr(os, "O_NOFOLLOW", 0)
            descriptor = os.open(self.destination, flags, 0o600)
            metadata = os.fstat(descriptor)
            if not stat.S_ISREG(metadata.st_mode):
                return False
            if not self._initialized:
                os.ftruncate(descriptor, 0)
                self._initialized = True
            elif metadata.st_size != self._bytes_written:
                return False
            written = 0
            while written < len(payload):
                count = os.write(descriptor, payload[written:])
                if count <= 0:
                    raise OSError
                written += count
            self._bytes_written += written
            return True
        except (OSError, ValueError, TypeError):
            if descriptor is not None and self._initialized:
                try:
                    os.ftruncate(descriptor, self._bytes_written)
                except OSError:
                    pass
            return False
        finally:
            if descriptor is not None:
                try:
                    os.close(descriptor)
                except OSError:
                    pass

    def _run(self):
        reason = "stopped"
        self._origin = _clock(time.monotonic)
        try:
            self.destination.parent.mkdir(parents=True, exist_ok=True)
            if not self._append({
                "kind": "start", "status": "started", "scope": "host-only",
                "coverage": "periodic-not-continuous", "interval_seconds": INTERVAL_SECONDS,
                "sample_limit": MAX_SAMPLES,
            }):
                reason = self._write_status
                return
            while not self._stop.is_set() and self._samples < MAX_SAMPLES:
                now = _clock(time.monotonic)
                sample = self._sample(now)
                if self._stop.is_set():
                    break
                if not self._append(sample):
                    reason = self._write_status
                    break
                self._samples += 1
                self._last_sample = now
                if self._samples == MAX_SAMPLES:
                    reason = "sample-limit"
                    break
                duration = _difference(_clock(time.monotonic), now)
                delay = INTERVAL_SECONDS if duration is None else max(0, INTERVAL_SECONDS - duration)
                if self._stop.wait(delay):
                    break
        except (OSError, RuntimeError, ValueError, OverflowError):
            reason = "sampling-unavailable"
        finally:
            if self._initialized:
                now = _clock(time.monotonic)
                self._append({
                    "kind": "summary", "status": reason, "samples": self._samples,
                    "coverage": "periodic-not-continuous",
                    "elapsed_seconds": _difference(now, self._origin),
                    "unsampled_tail_seconds": _difference(now, self._last_sample),
                }, final=True)
