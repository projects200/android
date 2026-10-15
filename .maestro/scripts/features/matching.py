"""Use real matching fixture values with the shared authenticated lifecycle."""

import math
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
