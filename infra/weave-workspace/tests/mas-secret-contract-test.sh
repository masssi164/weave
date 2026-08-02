#!/usr/bin/env bash
# shellcheck shell=bash

set -euo pipefail

ROOT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
readonly ROOT_DIR
export ROOT_DIR

python3 - <<'PY'
import os
import sys
from pathlib import Path

root = Path(os.environ["ROOT_DIR"])
sys.path.insert(0, str(root / "scripts"))

import init_secrets  # noqa: E402

assert "mas-encryption-secret" in init_secrets.HEX_SECRETS
assert "mas-encryption-secret" not in init_secrets.TEXT_SECRETS

values = {init_secrets._random_hex_secret().decode("ascii").strip() for _ in range(8)}
assert len(values) == 8
assert all(len(value) == 64 for value in values)
assert all(set(value) <= set("0123456789abcdef") for value in values)

renderer = (root / "scripts/render_config.py").read_text(encoding="utf-8")
assert "  encryption: {_read_secret(context, 'mas-encryption-secret')}" in renderer
assert "    - id: 01J0000000WEAVEKEYC10AKMAS" in renderer
PY

printf '%s\n' "MAS secret contract test passed."
