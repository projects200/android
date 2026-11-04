#!/usr/bin/env python3
"""Validate metadata-only ColorBuffer traces. Never publish native renderer output."""

import hashlib
import json
import re
import stat
import threading
import time
import xml.etree.ElementTree as ET


KEYS = frozenset({"schema", "seq", "mono_ns", "pid", "tid", "event", "handle", "reason", "refcount", "found"})
REASONS = {
    "session_start": {"enabled"},
    "session_end": {"clean"},
    "loss": {"limit", "late_event"},
    "register": {"create_refcount_pipe", "create_legacy", "create_guest", "snapshot_load"},
    "open": {"guest", "refcount_pipe", "window", "post"},
    "close": {"guest", "refcount_pipe", "window", "process_cleanup", "surface_destroy"},
    "refcount": {"guest", "forced_close", "post_release", "final_ref", "window", "process_cleanup", "surface_destroy"},
    "delay": {"guest", "post_release", "window", "process_cleanup", "surface_destroy"},
    "erase": {"forced_close", "delayed_expiry", "delayed_forced", "final_ref", "window", "process_cleanup", "surface_destroy", "shutdown", "snapshot_reset", "snapshot_stale", "snapshot_load_failed"},
    "destroy_request": {"final_ref", "queue_full", "deferred_sweep"},
    "lookup": {"generic"},
    "bind": {"texture", "texture2", "renderbuffer"},
}
MAX_TRACE_BYTES = 256 * 1024 * 1024
MAX_RECORD_BYTES = 1024
MAX_RECORDS = 1000010
MAX_HANDLES = 200000
MAX_FINDINGS = 10000
MAX_ERROR_RECORDS = 10000
SHA256 = re.compile(r"[0-9a-f]{64}")
REVISIONS = {
    "manifest_revision": "2692acc620f6563b21995540656674faeb536cdc",
    "qemu_revision": "cf0f40a0aad1aa3fca68f2f89372525c8d28ca68",
    "gfxstream_revision": "49ee51f297118cd2e98c5f4867df556e8966562a",
}


class TraceFailure(Exception):
    """The input failed validation; exception messages are fixed codes only."""


def integer(value, minimum=0, maximum=(1 << 64) - 1):
    return type(value) is int and minimum <= value <= maximum


def unique_object(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise TraceFailure("duplicate-key")
        result[key] = value
    return result


def parse_json(data):
    try:
        return json.loads(data, object_pairs_hook=unique_object,
                          parse_constant=lambda _value: (_ for _ in ()).throw(TraceFailure("invalid-number")))
    except (ValueError, UnicodeError, RecursionError):
        raise TraceFailure("invalid-json") from None


def validate_record(record):
    if not isinstance(record, dict) or set(record) != KEYS:
        raise TraceFailure("invalid-fields")
    if (type(record["schema"]) is not int or record["schema"] != 1
            or not integer(record["seq"], 1) or not integer(record["mono_ns"])
            or not integer(record["pid"], 1, (1 << 32) - 1)
            or not integer(record["tid"], 1, (1 << 32) - 1)):
        raise TraceFailure("invalid-numeric-metadata")
    event, reason = record["event"], record["reason"]
    if not isinstance(event, str) or event not in REASONS or not isinstance(reason, str) or reason not in REASONS[event]:
        raise TraceFailure("invalid-enum")
    if event in ("session_start", "session_end", "loss"):
        if any(record[key] is not None for key in ("handle", "refcount", "found")):
            raise TraceFailure("invalid-session-metadata")
    else:
        if not integer(record["handle"], 0, (1 << 32) - 1):
            raise TraceFailure("invalid-handle")
        count, found = record["refcount"], record["found"]
        if count is not None and not integer(count, 0, (1 << 32) - 1):
            raise TraceFailure("invalid-refcount")
        if found is not None and type(found) is not bool:
            raise TraceFailure("invalid-found")
        if event in ("register", "delay", "erase") and (found is not True or count is None):
            raise TraceFailure("invalid-map-observation")
        if event in ("open", "refcount", "lookup", "bind") and (type(found) is not bool or (count is not None) != found):
            raise TraceFailure("invalid-lookup-observation")
        if event in ("close", "destroy_request") and (count is not None or found is not None):
            raise TraceFailure("invalid-request-observation")
    # Construct a new object; never return unknown or original text fields.
    return {key: record[key] for key in sorted(KEYS)}


def digest(path):
    value = hashlib.sha256()
    with path.open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            value.update(chunk)
    return value.hexdigest()


def regular_file(path, limit):
    info = path.lstat()
    if not stat.S_ISREG(info.st_mode) or info.st_size > limit:
        raise TraceFailure("invalid-input-file")


def validate_provenance(directory):
    path = directory / "provenance.json"
    regular_file(path, 65536)
    value = parse_json(path.read_bytes())
    required = {"schema", "scope", "resolved_manifest_sha256", "patch_sha256", "archives", *REVISIONS}
    if not isinstance(value, dict) or not required.issubset(value):
        raise TraceFailure("invalid-provenance")
    if type(value["schema"]) is not int or value["schema"] != 1 or value["scope"] != "public-source-not-sdk-16740729":
        raise TraceFailure("invalid-provenance-scope")
    if any(value[key] != expected for key, expected in REVISIONS.items()):
        raise TraceFailure("unexpected-source-revision")
    for key in ("resolved_manifest_sha256", "patch_sha256"):
        if not isinstance(value[key], str) or not SHA256.fullmatch(value[key]):
            raise TraceFailure("invalid-provenance-hash")
    manifest = directory / "resolved-manifest.xml"
    regular_file(manifest, 4 * 1024 * 1024)
    if digest(manifest) != value["resolved_manifest_sha256"]:
        raise TraceFailure("manifest-hash-mismatch")
    manifest_bytes = manifest.read_bytes()
    if b"<!DOCTYPE" in manifest_bytes or b"<!ENTITY" in manifest_bytes:
        raise TraceFailure("manifest-unsafe-declaration")
    try:
        document = ET.fromstring(manifest_bytes)
    except ET.ParseError:
        raise TraceFailure("manifest-invalid") from None
    projects = document.findall("project")
    if document.tag != "manifest" or not projects or any(not re.fullmatch(r"[0-9a-f]{40}", item.get("revision", "")) for item in projects):
        raise TraceFailure("manifest-not-revision-pinned")
    archives = value["archives"]
    if not isinstance(archives, dict) or set(archives) != {"baseline", "instrumented"}:
        raise TraceFailure("invalid-archive-provenance")
    safe = {key: value[key] for key in required if key != "archives"}
    safe["archives"] = {}
    for variant in ("baseline", "instrumented"):
        item = archives[variant]
        if (not isinstance(item, dict) or set(item) != {"filename", "sha256"}
                or item["filename"] != variant + ".tar.gz"
                or not isinstance(item["sha256"], str) or not SHA256.fullmatch(item["sha256"])):
            raise TraceFailure("invalid-archive-provenance")
        package = directory / item["filename"]
        regular_file(package, 4 * 1024 ** 3)
        if digest(package) != item["sha256"]:
            raise TraceFailure("package-hash-mismatch")
        safe["archives"][variant] = {"filename": item["filename"], "sha256": item["sha256"]}
    return safe


class Lifecycle:
    def __init__(self, producer_status):
        self.processes = {}
        self.handles = {}
        self.findings = []
        self.issues = set()
        self.events = 0
        self.registrations = 0
        self.erases = 0
        self.binds = 0
        names = {0: "uninitialized", 1: "active", 2: "clean", 3: "failed"}
        self.producer_status = (names.get(producer_status, "invalid") if type(producer_status) is int
                                else "unavailable" if producer_status is None else "invalid")
        if self.producer_status != "clean":
            self.issues.add("producer-status-" + self.producer_status)

    def accept(self, record):
        self.events += 1
        pid, seq, mono, event = (record[key] for key in ("pid", "seq", "mono_ns", "event"))
        state = self.processes.get(pid)
        if state is None:
            state = {"last_seq": 0, "last_mono_ns": 0, "started": False, "ended": False}
            self.processes[pid] = state
        if seq != state["last_seq"] + 1:
            self.issues.add("sequence-gap-or-reorder")
        if mono < state["last_mono_ns"]:
            self.issues.add("monotonic-time-reversal")
        if state["ended"]:
            self.issues.add("records-after-footer")
        if event == "session_start":
            if state["started"] or seq != 1:
                self.issues.add("invalid-session-start")
            state["started"] = True
        elif not state["started"]:
            self.issues.add("missing-session-start")
        if event == "session_end":
            state["ended"] = True
        if event == "loss":
            self.issues.add("producer-record-loss")
            if record["reason"] == "limit":
                self.issues.add("producer-record-limit")
        state["last_seq"], state["last_mono_ns"] = seq, mono
        if record["handle"] is None:
            return
        key = (pid, record["handle"])
        if key not in self.handles:
            if len(self.handles) >= MAX_HANDLES:
                self.issues.add("analysis-handle-limit")
                return
            self.handles[key] = {"generation": 0, "registration_seq": None, "erase_seq": None, "live": False}
        handle = self.handles[key]
        if event == "register":
            handle.update(generation=handle["generation"] + 1, registration_seq=seq, erase_seq=None, live=True)
            self.registrations += 1
        elif event == "erase":
            handle.update(erase_seq=seq, live=False)
            self.erases += 1
        elif event == "bind":
            self.binds += 1
            if record["found"] is False:
                if len(self.findings) >= MAX_FINDINGS:
                    self.issues.add("analysis-finding-limit")
                    return
                relationship = ("after-observed-erase" if handle["erase_seq"] is not None
                                else "without-observed-registration" if handle["registration_seq"] is None
                                else "after-observed-registration")
                self.findings.append({"pid": pid, "handle": record["handle"], "bind_seq": seq,
                                      "generation": handle["generation"] or None,
                                      "registration_seq": handle["registration_seq"],
                                      "erase_seq": handle["erase_seq"], "relationship": relationship})

    def summary(self):
        if not self.processes:
            self.issues.add("missing-trace")
        if any(not state["ended"] for state in self.processes.values()):
            self.issues.add("missing-session-footer")
        if not self.registrations or not self.binds:
            self.issues.add("missing-lifecycle-coverage")
        complete = not self.issues
        if not complete:
            for finding in self.findings:
                finding["relationship"] = "inconclusive-partial-trace"
        truncated = bool(self.issues & {"producer-record-limit", "trace-file-limit", "consumer-record-limit",
                                       "truncated-or-oversize-record", "analysis-handle-limit", "analysis-finding-limit"})
        return {"schema": 1, "status": "complete" if complete else "incomplete", "complete": complete,
                "issues": sorted(self.issues), "truncated": truncated,
                "producer_status": self.producer_status,
                "records": self.events, "processes": len(self.processes),
                "registrations": self.registrations, "erases": self.erases, "binds": self.binds,
                "missing_binds": self.findings, "root_cause": "not-established"}


def export_trace(source, destination, summary_path, *, producer_status):
    lifecycle = Lifecycle(producer_status)
    destination.parent.mkdir(mode=0o700, parents=True, exist_ok=True)
    total = 0
    count = 0
    with destination.open("w", encoding="ascii") as output:
        try:
            regular_file(source, (1 << 63) - 1)
            if source.stat().st_size > MAX_TRACE_BYTES:
                lifecycle.issues.add("trace-file-limit")
            with source.open("rb") as stream:
                while True:
                    line = stream.readline(MAX_RECORD_BYTES + 1)
                    if not line:
                        break
                    total += len(line)
                    count += 1
                    if total > MAX_TRACE_BYTES or count > MAX_RECORDS:
                        lifecycle.issues.add("consumer-record-limit")
                        break
                    if len(line) > MAX_RECORD_BYTES or not line.endswith(b"\n"):
                        lifecycle.issues.add("truncated-or-oversize-record")
                        break
                    try:
                        record = validate_record(parse_json(line))
                    except TraceFailure:
                        lifecycle.issues.add("invalid-record")
                        continue
                    lifecycle.accept(record)
                    output.write(json.dumps(record, sort_keys=True, separators=(",", ":")) + "\n")
        except (OSError, TraceFailure):
            lifecycle.issues.add("trace-unavailable-or-invalid-file")
    result = lifecycle.summary()
    summary_path.write_text(json.dumps(result, sort_keys=True, separators=(",", ":")) + "\n", encoding="ascii")
    return {key: result[key] for key in ("status", "complete", "issues", "records", "truncated", "producer_status")}


class ErrorCollector:
    """Drain private stdout continuously; rewrite only two exact error messages."""

    def __init__(self, stream, destination):
        self.stream = stream
        self.destination = destination
        self.thread = threading.Thread(target=self.collect, daemon=True)
        self.records = 0
        self.truncated = False
        self.available = True
        self.missing = 0
        self.invalid_bind = 0

    def start(self):
        self.thread.start()

    def collect(self):
        output = None
        try:
            try:
                self.destination.parent.mkdir(mode=0o700, parents=True, exist_ok=True)
                output = self.destination.open("w", encoding="ascii")
            except OSError:
                self.available = False
            while True:
                line = self.stream.readline(8193)
                if not line:
                    break
                if len(line) > 8192 or not line.endswith(b"\n"):
                    # Keep draining a long native line without retaining its raw bytes.
                    while line and not line.endswith(b"\n"):
                        line = self.stream.readline(8193)
                    self.truncated = True
                    continue
                if not self.available:
                    continue
                record = safe_error(line)
                if record is None:
                    continue
                if self.records >= MAX_ERROR_RECORDS:
                    self.truncated = True
                    continue
                record.update(host_epoch_ns=time.time_ns(), host_mono_ns=time.monotonic_ns())
                try:
                    output.write(json.dumps(record, sort_keys=True, separators=(",", ":")) + "\n")
                    output.flush()
                except (OSError, ValueError):
                    self.available = False
                    continue
                self.records += 1
                if record["event"] == "missing-color-buffer":
                    self.missing += 1
                else:
                    self.invalid_bind += 1
        except (OSError, ValueError):
            self.available = False
        finally:
            if output is not None:
                try:
                    output.close()
                except OSError:
                    self.available = False

    def finish(self):
        self.thread.join(timeout=10)
        if self.thread.is_alive():
            self.available = False
        return {"status": "captured" if self.available and not self.truncated else "partial",
                "records": self.records, "missing_color_buffers": self.missing,
                "invalid_texture_binds": self.invalid_bind, "truncated": self.truncated}


def safe_error(line):
    # A renderer prefix may precede the message; no prefix or arbitrary suffix survives.
    missing = re.search(rb"(?:^|\s)Failed to find ColorBuffer: ([0-9]{1,10})\r?\n$", line)
    if missing:
        handle = int(missing.group(1))
        if handle <= (1 << 32) - 1:
            return {"event": "missing-color-buffer", "handle": handle}
    if re.search(rb"(?:^|\s)Failed to bind to texture: invalid color buffer\.\r?\n$", line):
        return {"event": "invalid-texture-bind", "handle": None}
    return None
