#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
COMPARISON_DIR="$(mktemp -d)"
trap 'rm -rf "$COMPARISON_DIR"' EXIT
if [[ "${WEAVE_OPENAPI_SKIP_EXPORT:-false}" != "true" ]]; then
  "$ROOT_DIR/gradlew" -q :server:app:openApiContractExport
fi
python3 "$ROOT_DIR/tools/normalize_openapi_contracts.py" --root "$ROOT_DIR" --output-dir "$COMPARISON_DIR"
for name in weave-openapi weave-user-openapi weave-admin-openapi; do
  target="$ROOT_DIR/contracts/openapi/$name.json"
  if [[ ! -f "$target" ]]; then
    echo "Missing $target. Run ./gradlew generateOpenApiContract." >&2
    exit 1
  fi
  if ! cmp -s "$target" "$COMPARISON_DIR/$name.json"; then
    echo "OpenAPI contract artifact is stale: $target. Run ./gradlew generateOpenApiContract." >&2
    diff -u "$target" "$COMPARISON_DIR/$name.json" >&2 || true
    exit 1
  fi
done
