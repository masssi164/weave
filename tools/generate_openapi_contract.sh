#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
if [[ "${WEAVE_OPENAPI_SKIP_EXPORT:-false}" != "true" ]]; then
  "$ROOT_DIR/gradlew" -q :server:app:openApiContractExport -Pdomains=all
fi

EXPORT_DIR="${WEAVE_OPENAPI_EXPORT_DIR:-$ROOT_DIR/build/openapi/$(python3 "$ROOT_DIR/tools/domain_selection_resolver.py" --root "$ROOT_DIR" --domains all | python3 -c 'import json,sys; print("-".join(json.load(sys.stdin)["domains"]))')}"
python3 "$ROOT_DIR/tools/normalize_openapi_contracts.py" --export-dir "$EXPORT_DIR" --root "$ROOT_DIR"
