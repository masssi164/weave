#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
if [[ "${WEAVE_OPENAPI_SKIP_EXPORT:-false}" != "true" ]]; then
  "$ROOT_DIR/gradlew" -q :server:openApiContractExport
fi

python3 "$ROOT_DIR/tools/normalize_openapi_contracts.py" --root "$ROOT_DIR"
