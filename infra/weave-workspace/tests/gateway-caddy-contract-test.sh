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

import render_config  # noqa: E402

renderer = (root / "scripts/render_config.py").read_text(encoding="utf-8")

assert "handle @synapse {{ reverse_proxy synapse:8008 }}" not in renderer
assert "handle {{ reverse_proxy mas:8080 }}" not in renderer
assert render_config._gateway_site("https://api.weave.test:44443") == "https://api.weave.test"
assert """  handle @synapse {{
    reverse_proxy synapse:8008
  }}
  handle {{
    reverse_proxy mas:8080
  }}""" in renderer
PY

printf '%s\n' "Caddy gateway configuration contract test passed."
