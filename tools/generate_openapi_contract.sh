#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
EXPORT_TMP="$ROOT_DIR/build/openapi/weave-openapi.raw.json"
EXPORT_TARGET="$ROOT_DIR/contracts/openapi/weave-openapi.json"

if [[ "${WEAVE_OPENAPI_SKIP_EXPORT:-false}" != "true" ]]; then
  "$ROOT_DIR/gradlew" -q :server:openApiContractExport
fi

if [[ ! -s "$EXPORT_TMP" ]]; then
  echo "OpenAPI export is missing: $EXPORT_TMP" >&2
  exit 1
fi

mkdir -p "$(dirname "$EXPORT_TARGET")"
jq -S '
  def sort_arrays: walk(if type == "array" then sort_by(tostring) else . end);
  reduce (.paths | keys[]) as $path (. ;
    reduce (.paths[$path] | keys[]) as $method (. ;
      if .paths[$path][$method].responses? != null then
        reduce (.paths[$path][$method].responses | keys[]) as $status (. ;
          .paths[$path][$method].responses[$status].description = ("HTTP " + $status)
        )
      else . end
    )
  ) | sort_arrays
' "$EXPORT_TMP" > "$EXPORT_TARGET"
