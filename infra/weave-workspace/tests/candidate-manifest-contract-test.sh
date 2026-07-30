#!/usr/bin/env bash
# shellcheck shell=bash

set -euo pipefail

REPOSITORY_ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../../.." && pwd)"
readonly REPOSITORY_ROOT
TEMP_ROOT="$(mktemp -d "${TMPDIR:-/tmp}/weave-candidate-manifest-test.XXXXXX")"
readonly TEMP_ROOT
trap 'rm -rf -- "${TEMP_ROOT}"' EXIT

readonly DIGEST="sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
readonly SBOM="sha256:bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
readonly PROVENANCE="sha256:cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc"
readonly COMMIT="dddddddddddddddddddddddddddddddddddddddd"
readonly MANIFEST="${TEMP_ROOT}/candidate-manifest.json"

python3 "${REPOSITORY_ROOT}/gradle/tasks/candidate-manifest-create.py" \
  --commit "${COMMIT}" \
  --spec-digest "${DIGEST}" \
  --build-evidence-ref "https://github.com/masssi164/weave/actions/runs/1/attempts/1" \
  --image server "ghcr.io/masssi164/weave-server@${DIGEST}" "${SBOM}" "${PROVENANCE}" \
  --image mcp-server "ghcr.io/masssi164/weave-mcp-server@${DIGEST}" "${SBOM}" "${PROVENANCE}" \
  --image identity-ops "ghcr.io/masssi164/weave-identity-ops@${DIGEST}" "${SBOM}" "${PROVENANCE}" \
  --image keycloak-runtime "ghcr.io/masssi164/weave-keycloak-runtime@${DIGEST}" "${SBOM}" "${PROVENANCE}" \
  --output "${MANIFEST}"

jq -e '
  [.images[].component] | sort ==
  ["identity-ops", "keycloak-runtime", "mcp-server", "server"]
' "${MANIFEST}" >/dev/null

python3 - "${MANIFEST}" <<'PY'
import hashlib
import json
import sys
from pathlib import Path

manifest = Path(sys.argv[1])
payload = json.loads(manifest.read_bytes())
payload["images"] = [
    image for image in payload["images"]
    if image["component"] != "keycloak-runtime"
]
raw = json.dumps(
    payload, ensure_ascii=False, separators=(",", ":"), sort_keys=True
).encode("utf-8")
manifest.write_bytes(raw)
manifest.with_suffix(".json.sha256").write_text(
    f"{hashlib.sha256(raw).hexdigest()}  {manifest.name}\n",
    encoding="ascii",
)
PY

if python3 "${REPOSITORY_ROOT}/gradle/tasks/candidate-manifest-check.py" \
  --manifest "${MANIFEST}" >"${TEMP_ROOT}/invalid.out" 2>&1; then
  echo "candidate manifest accepted a missing Keycloak Runtime" >&2
  exit 1
fi
grep -Fq 'identity-ops, keycloak-runtime, mcp-server, server' "${TEMP_ROOT}/invalid.out"

printf 'candidate manifest contract tests passed\n'
