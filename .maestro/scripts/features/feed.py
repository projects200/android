"""Validate the real run-owned gallery before the feed photo flow mutates a post."""

import re


def _literal(value):
    return r"\Q" + value.replace(r"\E", r"\E\\E\Q") + r"\E"


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
        capture = variables.get("FEED_IMAGE_" + label + "_CAPTURE_TEXT", "")
        if not capture or any(ord(char) < 32 for char in capture):
            raise core.RunnerFailure("feed-photo-invalid-media-metadata")
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
        "--sort", "'_id ASC'",
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
    if len(rows) != len(set(rows)) or any(rows.count(row) != 1 for row in expected):
        raise core.RunnerFailure("feed-photo-owned-media-mismatch")
    # 같은 초의 외부 사진은 native 설명이 같아질 수 있어 선택 전에 거절합니다
    expected_seconds = {int(row[1]) // 1000 for row in expected}
    if len(expected_seconds) != 2 or any(
            row not in expected and (row[0] in {item[0] for item in expected}
                                     or int(row[1]) // 1000 in expected_seconds)
            for row in rows):
        raise core.RunnerFailure("feed-photo-ambiguous-media-metadata")
    captures = [variables["FEED_IMAGE_" + label + "_CAPTURE_TEXT"] for label in ("A", "B")]
    if len(set(captures)) != 2:
        raise core.RunnerFailure("feed-photo-ambiguous-media-metadata")
    return {
        "FEED_IMAGE_" + label + "_SELECTOR": "(?s).*" + _literal(capture) + ".*"
        for label, capture in zip(("A", "B"), captures)
    }
