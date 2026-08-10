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
readonly SPECIFICATION_COMMIT="eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee"
readonly SEMANTIC_DIGEST="sha256:1111111111111111111111111111111111111111111111111111111111111111"
readonly MIGRATION_DIGEST="sha256:2222222222222222222222222222222222222222222222222222222222222222"
readonly MANIFEST="${TEMP_ROOT}/candidate-manifest.json"
readonly VALID_MANIFEST="${TEMP_ROOT}/candidate-manifest.valid.json"

python3 "${REPOSITORY_ROOT}/gradle/tasks/candidate-manifest-create.py" \
  --commit "${COMMIT}" \
  --specification-commit "${SPECIFICATION_COMMIT}" \
  --spec-digest "${DIGEST}" \
  --build-evidence-ref "https://github.com/masssi164/weave/actions/runs/1/attempts/1" \
  --keycloak-build-evidence-digest "${DIGEST}" \
  --semantic-realm-source-digest "${SEMANTIC_DIGEST}" \
  --realm-migration-definition-digest "${MIGRATION_DIGEST}" \
  --image server "ghcr.io/masssi164/weave-server@${DIGEST}" "${SBOM}" "${PROVENANCE}" \
  --image mcp-server "ghcr.io/masssi164/weave-mcp-server@${DIGEST}" "${SBOM}" "${PROVENANCE}" \
  --image keycloak-runtime "ghcr.io/masssi164/weave-keycloak-runtime@${DIGEST}" "${SBOM}" "${PROVENANCE}" \
  --output "${MANIFEST}"

jq -e '
  .schemaVersion == "weave.release.candidate-manifest.v4" and
  .specificationCommit == $specification_commit and
  (.images[] | select(.component == "keycloak-runtime") |
    .buildEvidenceDigest) == $digest and
  ([.images[].component] | sort) ==
  ["keycloak-runtime", "mcp-server", "server"] and
  .realmDefinition.containsSecrets == false and
  .realmDefinition.semanticRealmSourceDigest == $semantic_digest and
  .realmDefinition.migrationDefinitionDigest == $migration_digest
' \
  --arg digest "${DIGEST}" \
  --arg specification_commit "${SPECIFICATION_COMMIT}" \
  --arg semantic_digest "${SEMANTIC_DIGEST}" \
  --arg migration_digest "${MIGRATION_DIGEST}" \
  "${MANIFEST}" >/dev/null

cp "${MANIFEST}" "${VALID_MANIFEST}"
cp "${MANIFEST}.sha256" "${VALID_MANIFEST}.sha256"

mutate_manifest() {
  local mutation="$1"
  cp "${VALID_MANIFEST}" "${MANIFEST}"
  python3 - "${MANIFEST}" "${mutation}" <<'PY'
import hashlib
import json
import sys
from pathlib import Path

manifest = Path(sys.argv[1])
mutation = sys.argv[2]
payload = json.loads(manifest.read_bytes())
keycloak = next(image for image in payload["images"] if image["component"] == "keycloak-runtime")
server = next(image for image in payload["images"] if image["component"] == "server")
if mutation == "missing-keycloak":
    payload["images"].remove(keycloak)
elif mutation == "missing-build-evidence":
    keycloak.pop("buildEvidenceDigest")
elif mutation == "malformed-build-evidence":
    keycloak["buildEvidenceDigest"] = "sha256:not-a-digest"
elif mutation == "non-keycloak-build-evidence":
    server["buildEvidenceDigest"] = "sha256:" + "e" * 64
elif mutation == "malformed-semantic-digest":
    payload["realmDefinition"]["semanticRealmSourceDigest"] = "sha256:not-a-digest"
elif mutation == "malformed-migration-digest":
    payload["realmDefinition"]["migrationDefinitionDigest"] = "sha256:not-a-digest"
else:
    raise SystemExit(f"unknown mutation: {mutation}")
raw = json.dumps(payload, ensure_ascii=False, separators=(",", ":"), sort_keys=True).encode("utf-8")
manifest.write_bytes(raw)
manifest.with_suffix(".json.sha256").write_text(
    f"{hashlib.sha256(raw).hexdigest()}  {manifest.name}\n",
    encoding="ascii",
)
PY
}

mutate_manifest "missing-keycloak"
if python3 "${REPOSITORY_ROOT}/gradle/tasks/candidate-manifest-check.py" --manifest "${MANIFEST}" >"${TEMP_ROOT}/invalid.out" 2>&1; then
  echo "candidate manifest accepted a missing Keycloak Runtime" >&2
  exit 1
fi
grep -Fq 'keycloak-runtime, mcp-server, server' "${TEMP_ROOT}/invalid.out"

for mutation in \
  malformed-semantic-digest \
  malformed-migration-digest \
  missing-build-evidence \
  malformed-build-evidence \
  non-keycloak-build-evidence; do
  mutate_manifest "${mutation}"
  if python3 "${REPOSITORY_ROOT}/gradle/tasks/candidate-manifest-check.py" --manifest "${MANIFEST}" >"${TEMP_ROOT}/${mutation}.out" 2>&1; then
    echo "candidate manifest accepted invalid evidence: ${mutation}" >&2
    exit 1
  fi
done

cp "${VALID_MANIFEST}" "${MANIFEST}"
cp "${VALID_MANIFEST}.sha256" "${MANIFEST}.sha256"
python3 - "${MANIFEST}" <<'PY'
import sys
from pathlib import Path
manifest = Path(sys.argv[1])
manifest.write_bytes(manifest.read_bytes() + b"\n")
PY
if python3 "${REPOSITORY_ROOT}/gradle/tasks/candidate-manifest-check.py" --manifest "${MANIFEST}" >"${TEMP_ROOT}/tampered.out" 2>&1; then
  echo "candidate manifest accepted bytes not bound by its adjacent digest" >&2
  exit 1
fi
grep -Fq 'adjacent manifest digest does not match exact bytes' "${TEMP_ROOT}/tampered.out"

printf 'candidate manifest contract tests passed\n'
