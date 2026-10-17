#!/usr/bin/env python3
"""Load a private exported session into the runner environment without printing it."""

import json
import os
from pathlib import Path
import stat
import sys


ROOT = Path(__file__).resolve().parents[2]


def main():
    if len(sys.argv) < 4 or sys.argv[2] != "--suite":
        print("Usage: python3 .maestro/scripts/run-with-session.py ABSOLUTE_PRIVATE_SESSION --suite SUITE [runner options]", file=sys.stderr)
        return 1
    try:
        path = Path(sys.argv[1]).expanduser()
        if not path.is_absolute() or any(item.is_symlink() for item in (path, *path.parents)):
            raise ValueError()
        path = path.resolve()
        if ROOT in path.parents or not os.environ.get("MAESTRO_FIXTURE_START_DATE"):
            raise ValueError()
        descriptor = os.open(path, os.O_RDONLY | os.O_NOFOLLOW)
        with os.fdopen(descriptor, "rb") as handle:
            info = os.fstat(handle.fileno())
            if not stat.S_ISREG(info.st_mode) or info.st_uid != os.getuid() or stat.S_IMODE(info.st_mode) & 0o077:
                raise ValueError()
            session = json.load(handle)
        if not isinstance(session.get("authState"), dict) or not session["authState"]:
            raise ValueError()
        if not isinstance(session.get("memberId"), str) or not session["memberId"]:
            raise ValueError()
        env = os.environ.copy()
        env["MAESTRO_AUTH_STATE_JSON"] = json.dumps(session["authState"], ensure_ascii=False, separators=(",", ":"))
        env["MAESTRO_MEMBER_ID"] = session["memberId"]
        # 실제 러너만 인증 환경을 받습니다. 러너는 Maestro/ADB 자식 환경에서 비밀을 제거합니다.
        argv = [sys.executable, str(ROOT / ".maestro" / "scripts" / "run-tests.py"), *sys.argv[2:]]
        os.execve(sys.executable, argv, env)
    except Exception:
        print("Session load failed: use a private external session file and set MAESTRO_FIXTURE_START_DATE.", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
