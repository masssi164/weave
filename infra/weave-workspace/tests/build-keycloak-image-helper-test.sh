#!/usr/bin/env bash
# shellcheck shell=bash

set -euo pipefail

ROOT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
readonly ROOT_DIR
PYTHON_BIN="$(command -v python3)"
readonly PYTHON_BIN
TEMP_ROOT="$(mktemp -d "${TMPDIR:-/tmp}/weave-keycloak-resolver-test.XXXXXX")"
readonly TEMP_ROOT
trap 'rm -rf -- "$TEMP_ROOT"' EXIT

MOCK_BIN="${TEMP_ROOT}/bin"
EVIDENCE_FILE="${TEMP_ROOT}/stock-keycloak-image.json"
mkdir -p "${MOCK_BIN}"

cat >"${MOCK_BIN}/docker" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail

readonly STOCK_REFERENCE='quay.io/keycloak/keycloak@sha256:0f198be292568439d700cdbfb893e69a6009bb43a94a06a945b1d3d506c76b13'
readonly IMAGE_ID='sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa'

case "${1:-}" in
  pull)
    [[ "${2:-}" == "${STOCK_REFERENCE}" ]]
    printf 'mock pull progress that must not contaminate resolver stdout\n'
    ;;
  image)
    [[ "${2:-}" == inspect ]]
    [[ "${3:-}" == "${STOCK_REFERENCE}" ]]
    printf '{"Id":"%s","RepoDigests":["%s"]}\n' "${IMAGE_ID}" "${STOCK_REFERENCE}"
    ;;
  *)
    printf 'unexpected docker invocation\n' >&2
    exit 2
    ;;
esac
EOF
chmod 700 "${MOCK_BIN}/docker"

actual="$(
  PATH="${MOCK_BIN}:${PATH}" "${PYTHON_BIN}" \
    "${ROOT_DIR}/scripts/build_keycloak_image.py" \
    --root "${ROOT_DIR}" \
    --candidate-commit 1111111111111111111111111111111111111111 \
    --output "${EVIDENCE_FILE}"
)"

expected='sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa'
[[ "${actual}" == "${expected}" ]] || {
  printf 'resolver stdout was not exactly one immutable image ID: %q\n' "${actual}" >&2
  exit 1
}

jq -e \
  --arg imageId "${expected}" \
  '.schemaVersion == "weave.stock-keycloak-image.v1" and
   .imageId == $imageId and
   .evidenceForCandidateCommit == "1111111111111111111111111111111111111111" and
   .supportSafe == true and
   .containsSecretValues == false' \
  "${EVIDENCE_FILE}" >/dev/null

printf 'build-keycloak-image-helper-test: ok\n'
