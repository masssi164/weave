#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TARGET="$ROOT_DIR/contracts/openapi/weave-openapi.json"
BEFORE="$ROOT_DIR/build/openapi/weave-openapi.before.json"

if [[ ! -f "$TARGET" ]]; then
  echo "Missing $TARGET. Run ./gradlew generateOpenApiContract." >&2
  exit 1
fi

mkdir -p "$(dirname "$BEFORE")"
cp "$TARGET" "$BEFORE"
"$ROOT_DIR/tools/generate_openapi_contract.sh" >/dev/null
if ! cmp -s "$BEFORE" "$TARGET"; then
  echo "OpenAPI contract artifact is stale. Run ./gradlew generateOpenApiContract and commit the result." >&2
  git --no-pager diff -- "$TARGET" >&2 || true
  exit 1
fi
