#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BEFORE_DIR="$(mktemp -d)"
trap 'rm -rf "$BEFORE_DIR"' EXIT
for name in weave-openapi weave-user-openapi weave-admin-openapi; do
  target="$ROOT_DIR/contracts/openapi/$name.json"
  if [[ ! -f "$target" ]]; then
    echo "Missing $target. Run ./gradlew generateOpenApiContract." >&2
    exit 1
  fi
  cp "$target" "$BEFORE_DIR/$name.json"
done

"$ROOT_DIR/tools/generate_openapi_contract.sh" >/dev/null
for name in weave-openapi weave-user-openapi weave-admin-openapi; do
  target="$ROOT_DIR/contracts/openapi/$name.json"
  if ! cmp -s "$BEFORE_DIR/$name.json" "$target"; then
    echo "OpenAPI contract artifact is stale: $target. Run ./gradlew generateOpenApiContract." >&2
    git --no-pager diff -- "$target" >&2 || true
    exit 1
  fi
done
