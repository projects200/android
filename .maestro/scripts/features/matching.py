"""Use real matching fixture values with the shared authenticated lifecycle."""

import json
import math
import os
import re


FLOWS = frozenset((
    "matching-place-crud", "matching-place-validation", "matching-member-to-chat",
))


def before_flow(core, device, flow, variables, credentials, run_id):
    if flow not in FLOWS or not re.fullmatch(r"[a-f0-9]{8,32}", run_id):
        raise core.RunnerFailure("matching-invalid-flow-or-run-id")
    try:
        latitude = float(variables["MATCHING_LATITUDE"])
        longitude = float(variables["MATCHING_LONGITUDE"])
        names = {
            "MATCHING_CATALOG_NAME_PATTERN": variables["MATCHING_CATALOG_NAME"],
            "MATCHING_CATALOG_ADDRESS_PATTERN": variables["MATCHING_CATALOG_ADDRESS"],
            "MATCHING_PEER_NICKNAME_PATTERN": variables["MATCHING_PEER_NICKNAME"],
            "MATCHING_CATALOG_ROW_PATTERN": "matching_search_" + variables["MATCHING_CATALOG_NAME"] + "_" + variables["MATCHING_CATALOG_ADDRESS"],
            "MATCHING_PEER_ROW_PATTERN": "matching_member_" + variables["MATCHING_PEER_ID"] + "_" + variables["MATCHING_PEER_LOCATION_ID"],
        }
    except (KeyError, TypeError, ValueError):
        raise core.RunnerFailure("matching-fixture-variables-invalid") from None
    if not math.isfinite(latitude) or not math.isfinite(longitude) or not (-90 <= latitude <= 90 and -180 <= longitude <= 180):
        raise core.RunnerFailure("matching-fixture-coordinates-invalid")
    if any(not isinstance(value, str) or not value for value in names.values()):
        raise core.RunnerFailure("matching-fixture-variables-invalid")
    if device.require("shell", "getprop", "ro.kernel.qemu").strip() != b"1":
        raise core.RunnerFailure("matching-requires-configured-emulator")
    # 실제 peer 장소 좌표를 첫 앱 실행 전에 공급합니다. 고정 서울 좌표를 peer로 가정하지 않습니다.
    device.require("emu", "geo", "fix", str(longitude), str(latitude))
    patterns = {key: "^" + re.escape(value) + "$" for key, value in names.items()}
    patterns["MATCHING_PEER_MARKER_PATTERN"] = ".*" + re.escape(
        "matching_marker_" + variables["MATCHING_PEER_ID"] + "_" + variables["MATCHING_PEER_LOCATION_ID"]
    ) + ".*"
    return patterns


def after_flow(core, device, flow, variables, credentials, run_id):
    verification_failed = False
    restoration_failed = False
    try:
        # prepare의 verify 단계는 기존 스냅샷을 덮어쓰거나 새 데이터를 만들지 않습니다.
        device.bootstrap(
            "prepare", credentials, feature="matching", flow=flow, run_id=run_id,
            parameters={"phase": "verify"},
        )
    except Exception:
        verification_failed = True
    for permission in ("android.permission.ACCESS_FINE_LOCATION", "android.permission.ACCESS_COARSE_LOCATION"):
        try:
            device.require("shell", "pm", "grant", core.APP_ID, permission)
        except Exception:
            restoration_failed = True
    if verification_failed and restoration_failed:
        raise core.RunnerFailure("matching-verification-and-permission-restore-failed")
    if verification_failed:
        raise core.RunnerFailure("matching-server-verification-failed")
    if restoration_failed:
        raise core.RunnerFailure("matching-permission-restore-failed")


DIAGNOSTICS_FILE = "matching-map-diagnostics.json"
MAX_EVIDENCE_BYTES = 1024 * 1024
MAX_EVENTS = 1000
SDK_CATEGORIES = {
    -1: "unknown", -2: "network", -3: "network", -4: "network",
    400: "request", 401: "auth", 403: "auth", 429: "quota", 499: "network",
    1001: "initialization", 1002: "rendering", 1003: "auth",
}


def _diagnostics(status, events=None, label_clicks=None, place_loads=None):
    events = events or []
    return {
        "schemaVersion": 1,
        "status": status,
        "finalState": events[-1]["state"] if events else ("not_observed" if status == "ok" else "unknown"),
        "events": events,
        "labelClicks": label_clicks or [],
        "placeLoads": place_loads or [],
    }


def _map_diagnostics(raw, run_id):
    if type(raw) is not bytes:
        return _diagnostics("invalid_evidence")
    if len(raw) > MAX_EVIDENCE_BYTES:
        return _diagnostics("size_limit")
    try:
        document = json.loads(raw)
    except (ValueError, UnicodeError, RecursionError):
        return _diagnostics("invalid_evidence")
    if type(document) is not dict or set(document) != {"runId", "events"}:
        return _diagnostics("invalid_evidence")
    if type(document["runId"]) is not str or document["runId"] != run_id:
        return _diagnostics("stale_evidence")
    source = document["events"]
    if type(source) is not list or len(source) > MAX_EVENTS:
        return _diagnostics("invalid_evidence")
    events = []
    label_clicks = []
    place_loads = []
    pending_place_loads = set()
    attempt = None
    previous_state = None
    for sequence, event in enumerate(source, 1):
        if type(event) is not dict or type(event.get("type")) is not str:
            return _diagnostics("invalid_evidence")
        if event["type"] in {"places", "chat", "denied"}:
            continue
        if event["type"] == "place_load":
            state = event.get("state")
            keys = {"type", "sequence", "request", "state", "elapsedMs"}
            if type(state) is not str or state not in {"started", "success", "error", "cancelled", "exception"}:
                return _diagnostics("invalid_evidence")
            if state in {"error", "exception"}:
                keys.add("category")
            if (set(event) != keys
                    or type(event["sequence"]) is not int or event["sequence"] != sequence
                    or type(event["request"]) is not int or not 1 <= event["request"] <= MAX_EVENTS
                    or type(event["elapsedMs"]) is not int or not 0 <= event["elapsedMs"] <= 9223372036854775807):
                return _diagnostics("invalid_evidence")
            request = event["request"]
            if state == "started":
                if request != sequence or event["elapsedMs"] != 0:
                    return _diagnostics("invalid_evidence")
                pending_place_loads.add(request)
            elif request not in pending_place_loads:
                return _diagnostics("invalid_evidence")
            else:
                pending_place_loads.remove(request)
            safe = {"sequence": sequence, "request": request, "state": state, "elapsedMs": event["elapsedMs"]}
            if state in {"error", "exception"}:
                category = event["category"]
                if type(category) is not str or category not in {"network", "mapping", "auth", "server", "unknown"}:
                    return _diagnostics("invalid_evidence")
                safe["category"] = category
            place_loads.append(safe)
            continue
        if event["type"] == "map_label_click":
            if (set(event) != {"type", "sequence", "attempt", "kind"}
                    or type(event["sequence"]) is not int or event["sequence"] != sequence
                    or type(event["attempt"]) is not int or event["attempt"] != attempt
                    or previous_state != "ready" or type(event["kind"]) is not str
                    or event["kind"] not in {"cluster", "place", "unknown"}):
                return _diagnostics("invalid_evidence")
            label_clicks.append({"sequence": sequence, "attempt": attempt, "kind": event["kind"]})
            continue
        if event["type"] != "map" or type(event.get("state")) is not str:
            return _diagnostics("invalid_evidence")
        state = event["state"]
        keys = {"type", "sequence", "attempt", "state"}
        if state == "failed":
            keys.update(("category", "sdkErrorCode"))
        if (state not in {"loading", "ready", "failed"} or set(event) != keys
                or type(event["sequence"]) is not int or event["sequence"] != sequence
                or type(event["attempt"]) is not int or not 1 <= event["attempt"] <= MAX_EVENTS):
            return _diagnostics("invalid_evidence")
        if state == "loading":
            if event["attempt"] != sequence:
                return _diagnostics("invalid_evidence")
            attempt = sequence
        elif attempt != event["attempt"] or previous_state == "failed":
            return _diagnostics("invalid_evidence")
        # 원본 root/event를 복사하지 않고 허용된 값만 새 객체에 기록합니다.
        safe = {"sequence": sequence, "attempt": attempt, "state": state}
        if state == "failed":
            code = event["sdkErrorCode"]
            category = event["category"]
            if type(category) is not str:
                return _diagnostics("invalid_evidence")
            if code is None:
                if category not in {"network", "unknown"}:
                    return _diagnostics("invalid_evidence")
            elif (type(code) is not int or not -2147483648 <= code <= 2147483647
                  or category != SDK_CATEGORIES.get(code, "unknown")):
                return _diagnostics("invalid_evidence")
            safe["category"] = category
            safe["sdkErrorCode"] = code
        events.append(safe)
        previous_state = state
    return _diagnostics("ok", events, label_clicks, place_loads)


def collect_diagnostics(core, device, flow, variables, credentials, run_id, output):
    if flow not in FLOWS or type(run_id) is not str or not re.fullmatch(r"[a-f0-9]{8,32}", run_id):
        document = _diagnostics("invalid_evidence")
    else:
        try:
            # run-as 원문은 메모리에서만 처리하며 읽기도 1 MiB + 초과 감지 1바이트로 제한합니다.
            raw = device.require(
                "shell", "-T", "run-as", core.APP_ID, "head", "-c", str(MAX_EVIDENCE_BYTES + 1),
                "files/maestro-matching-events.json",
            )
        except Exception:
            document = _diagnostics("read_failed")
        else:
            document = _map_diagnostics(raw, run_id)
    path = output / DIAGNOSTICS_FILE
    descriptor = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_TRUNC | os.O_NOFOLLOW, 0o600)
    with os.fdopen(descriptor, "w", encoding="utf-8") as stream:
        os.fchmod(stream.fileno(), 0o600)
        json.dump(document, stream, sort_keys=True, separators=(",", ":"))
        stream.write("\n")
    if document["status"] != "ok":
        raise core.RunnerFailure("matching-map-diagnostics-unavailable")
