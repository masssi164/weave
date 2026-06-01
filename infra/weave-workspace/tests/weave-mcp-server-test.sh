#!/usr/bin/env bash
# shellcheck shell=bash

set -euo pipefail

REPO_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../../.." && pwd)"
MCP_DIR="${REPO_DIR}/infra/weave-mcp"

[[ -f "${MCP_DIR}/pyproject.toml" ]] || {
  printf '%s\n' "Missing infra/weave-mcp/pyproject.toml" >&2
  exit 1
}

python3 - <<'PY' "${MCP_DIR}/pyproject.toml"
import pathlib
import sys
text = pathlib.Path(sys.argv[1]).read_text()
required = [
    'primary_transport = "streamable-http"',
    'backend_authority = "weave-backend"',
    'weave-mcp = "weave_mcp.app:main"',
    'weave-mcp-fastmcp = "weave_mcp.fastmcp_app:main"',
]
missing = [needle for needle in required if needle not in text]
if missing:
    raise SystemExit("missing MCP pyproject contract entries: " + ", ".join(missing))
PY

PYTHONPATH="${MCP_DIR}/src" python3 -m unittest discover -s "${MCP_DIR}/tests" -p 'test_*.py'

printf '%s\n' 'weave MCP streamable-http server tests passed'
