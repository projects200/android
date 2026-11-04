#!/usr/bin/env bash
# CI-only native builder. No credentials or application environment are needed.
set -euo pipefail
if [[ "$#" != 2 ]]; then
    printf '%s\n' 'Usage: build-public-emulator.sh <source-dir> <output-dir>' >&2
    exit 2
fi
asset_dir="$(CDPATH= cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
exec python3 "$asset_dir/build-public-emulator.py" "$1" "$2"
