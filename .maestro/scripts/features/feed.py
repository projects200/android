"""Validate the real run-owned gallery before the feed photo flow mutates a post."""

import re


def before_flow(core, device, flow, variables, credentials, run_id):
    if flow != "feed-photo":
        return {}
    if not re.fullmatch(r"[0-9a-f]{16}", run_id):
        raise core.RunnerFailure("feed-invalid-run-id")
    if device.require("shell", "getprop", "ro.kernel.qemu").strip() != b"1":
        raise core.RunnerFailure("feed-photo-requires-disposable-emulator")
    try:
        api = int(device.require("shell", "getprop", "ro.build.version.sdk").strip())
    except ValueError:
        raise core.RunnerFailure("feed-photo-invalid-device-api") from None
    if api < 33:
        raise core.RunnerFailure("feed-photo-requires-api-33")
    expected = []
    for label in ("A", "B"):
        name = variables.get("FEED_IMAGE_" + label + "_NAME")
        digest = variables.get("FEED_IMAGE_" + label + "_SHA256", "")
        taken = variables.get("FEED_IMAGE_" + label + "_TAKEN", "")
        if name != "maestro-feed-" + run_id + "-" + label + ".png" or not re.fullmatch(r"[0-9a-f]{64}", digest):
            raise core.RunnerFailure("feed-photo-invalid-media-metadata")
        if not re.fullmatch(r"[0-9]+", taken):
            raise core.RunnerFailure("feed-photo-invalid-media-metadata")
        expected.append((name, taken, "Pictures/maestro-feed/" + run_id + "/"))
    if variables["FEED_IMAGE_A_SHA256"] == variables["FEED_IMAGE_B_SHA256"]:
        raise core.RunnerFailure("feed-photo-images-must-differ")
    # shell 권한으로 전체 이미지 목록을 읽습니다. 앱에 보이는 자기 소유 행만으로 빈 갤러리를 추정하지 않습니다.
    raw = device.require(
        "shell", "content", "query", "--uri", "content://media/external/images/media",
        "--projection", "_id:_display_name:datetaken:relative_path",
        "--sort", "'datetaken DESC, _id DESC'",
    ).decode("utf-8", "replace")
    rows = []
    for line in raw.splitlines():
        match = re.fullmatch(
            r"Row: [0-9]+ _id=[0-9]+, _display_name=([^,]+), datetaken=([0-9]+), relative_path=([^,]+)",
            line.strip(),
        )
        if not match:
            raise core.RunnerFailure("feed-photo-unexpected-media-provider-result")
        rows.append(match.groups())
    if rows != expected:
        # 개인 사진을 지우거나 첫 썸네일로 대체하지 않습니다. 정확히 A/B만 있는 새 CI 갤러리가 필요합니다.
        raise core.RunnerFailure("feed-photo-gallery-must-contain-only-run-images")
    return {"FEED_IMAGE_A_INDEX": "0", "FEED_IMAGE_B_INDEX": "1"}
