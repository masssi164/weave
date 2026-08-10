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

from rendering.gateway import _site, render_caddy  # noqa: E402

renderer = (root / "scripts/rendering/gateway.py").read_text(encoding="utf-8")

assert _site("https://api.weave.test:44443") == "https://api.weave.test"
assert '@private_network remote_ip private_ranges' in renderer
assert 'reverse_proxy mailpit:8025' in renderer
assert 'context.profile in {"dogfood", "e2e"}' in renderer
assert 'if env["WEAVE_CHAT_PROVIDER"] != "matrix-synapse"' in renderer
assert "reverse_proxy synapse:8008" in renderer
assert "reverse_proxy mas:8080" in renderer
assert 'matrix provider disabled' in renderer
assert 'nextcloud provider disabled' in renderer
assert '@internal path /api/internal/* /actuator/*' in renderer
assert 'header_up X-Forwarded-For {http.request.remote.host}' in renderer
assert 'handle @internal {' in renderer
assert 'respond 404' in renderer
PY

printf '%s\n' "Caddy gateway configuration contract test passed."
