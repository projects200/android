#!/usr/bin/env python3
"""Validate required CI secrets and write private DEV build configuration."""

import base64
import datetime
import json
import os
from pathlib import Path
import re
import sys


ROOT = Path(__file__).resolve().parents[2]
BUILD_KEYS = (
    "COGNITO_USER_POOL_ID_DEV", "COGNITO_APP_CLIENT_ID_DEV", "COGNITO_REGION_DEV",
    "KAKAO_NATIVE_APP_KEY", "KAKAO_REST_API_KEY",
)
SESSION_KEYS = ("MAESTRO_AUTH_STATE_JSON", "MAESTRO_MEMBER_ID", "MAESTRO_FIXTURE_START_DATE")


def private_write(path, content):
    path.parent.mkdir(parents=True, exist_ok=True)
    descriptor = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_TRUNC | os.O_NOFOLLOW, 0o600)
    with os.fdopen(descriptor, "wb") as handle:
        os.fchmod(handle.fileno(), 0o600)
        handle.write(content)


def main():
    required = (*BUILD_KEYS, *SESSION_KEYS, "GOOGLE_SERVICES_JSON_DEV_BASE64")
    missing = [key for key in required if not os.environ.get(key, "").strip()]
    if missing:
        print("Missing required CI secrets: " + ", ".join(missing), file=sys.stderr)
        return 1
    try:
        for key in BUILD_KEYS:
            # Properties에 줄바꿈/구분자/이스케이프가 섞이지 않도록 실제 설정값 형식만 받습니다.
            if not re.fullmatch(r"[A-Za-z0-9_-]+", os.environ[key]):
                raise ValueError()
        auth = json.loads(os.environ["MAESTRO_AUTH_STATE_JSON"])
        if not isinstance(auth, dict) or not auth:
            raise ValueError()
        date = datetime.date.fromisoformat(os.environ["MAESTRO_FIXTURE_START_DATE"])
        today = datetime.datetime.now(datetime.timezone(datetime.timedelta(hours=9))).date()
        if date.isoformat() != os.environ["MAESTRO_FIXTURE_START_DATE"] or date > today:
            raise ValueError()
        google = base64.b64decode(os.environ["GOOGLE_SERVICES_JSON_DEV_BASE64"], validate=True)
        config = json.loads(google)
        if not any(client.get("client_info", {}).get("android_client_info", {}).get("package_name")
                   == "com.project200.undabang" for client in config.get("client", [])):
            raise ValueError()
        properties = "".join(key + "=" + os.environ[key] + "\n" for key in BUILD_KEYS)
        private_write(ROOT / "local.properties", properties.encode("ascii"))
        private_write(ROOT / "app" / "src" / "e2e" / "google-services.json", google)
    except Exception:
        # 디코더/JSON 예외에는 비밀 원문이 포함될 수 있어 값이나 traceback을 출력하지 않습니다.
        print("CI configuration failed: invalid secrets or private configuration write failed.", file=sys.stderr)
        return 1
    print("Private DEV configuration is ready. No secret values were printed.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
