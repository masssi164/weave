#!/usr/bin/env bash
# shellcheck shell=bash

set -euo pipefail

ROOT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
readonly ROOT_DIR
PYTHON_BIN="$(command -v python3)"
readonly PYTHON_BIN
TEMP_ROOT="$(mktemp -d "${TMPDIR:-/tmp}/weave-keycloak-runtime-test.XXXXXX")"
readonly TEMP_ROOT
trap 'rm -rf -- "$TEMP_ROOT"' EXIT

readonly CANDIDATE='1111111111111111111111111111111111111111'
readonly SPEC_DIGEST='sha256:bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb'
readonly IMAGE_ID='sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa'
REPOSITORY="${TEMP_ROOT}/repository"
MOCK_BIN="${TEMP_ROOT}/bin"
EVIDENCE_FILE="${TEMP_ROOT}/keycloak-runtime-image.json"
mkdir -p \
  "${MOCK_BIN}" \
  "${REPOSITORY}/infra/weave-workspace/keycloak" \
  "${REPOSITORY}/keycloak-workload-registration-provider/build/libs" \
  "${REPOSITORY}/specs"

printf '{}\n' >"${REPOSITORY}/specs/weave-specs.lock.json"
printf '%s\n' \
  'ARG WEAVE_KEYCLOAK_BASE=quay.io/keycloak/keycloak@sha256:0f198be292568439d700cdbfb893e69a6009bb43a94a06a945b1d3d506c76b13' \
  >"${REPOSITORY}/infra/weave-workspace/keycloak/Dockerfile.runtime"

cat >"${REPOSITORY}/gradlew" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
repository="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
mkdir -p "${repository}/keycloak-workload-registration-provider/build/libs"
printf 'deterministic-provider-fixture' \
  >"${repository}/keycloak-workload-registration-provider/build/libs/weave-workload-client-registration-provider-1.0.0.jar"
EOF
chmod 700 "${REPOSITORY}/gradlew"

cat >"${MOCK_BIN}/git" <<EOF
#!/usr/bin/env bash
set -euo pipefail
case " \$* " in
  *" rev-parse HEAD "*) printf '%s\\n' '${CANDIDATE}' ;;
  *" status "*) ;;
  *) printf 'unexpected git invocation: %s\\n' "\$*" >&2; exit 2 ;;
esac
EOF
chmod 700 "${MOCK_BIN}/git"

cat >"${MOCK_BIN}/docker" <<EOF
#!/usr/bin/env bash
set -euo pipefail
case "\${1:-} \${2:-}" in
  'build --file') ;;
  'image inspect')
    printf '%s\\n' '{"Id":"${IMAGE_ID}","Config":{"Labels":{"org.opencontainers.image.title":"Weave Keycloak Runtime","org.opencontainers.image.source":"https://github.com/masssi164/weave","org.opencontainers.image.revision":"${CANDIDATE}","org.opencontainers.image.licenses":"Apache-2.0","org.opencontainers.image.vendor":"Weave","com.massimotter.weave.spec-digest":"${SPEC_DIGEST}","com.massimotter.weave.module":"keycloak-runtime","com.massimotter.weave.runtime-user":"1000:1000","com.massimotter.weave.dependency-platform":"keycloak-26.7-client-policy-spi","com.massimotter.weave.provider-id":"weave-workload-client-registration-enforcer"}}}'
    ;;
  'run --rm') ;;
  *) printf 'unexpected docker invocation: %s\\n' "\$*" >&2; exit 2 ;;
esac
EOF
chmod 700 "${MOCK_BIN}/docker"

actual="$(
  PATH="${MOCK_BIN}:${PATH}" "${PYTHON_BIN}" \
    "${ROOT_DIR}/scripts/build_keycloak_image.py" \
    --root "${REPOSITORY}" \
    --candidate-commit "${CANDIDATE}" \
    --spec-digest "${SPEC_DIGEST}" \
    --output "${EVIDENCE_FILE}"
)"

[[ "${actual}" == "${IMAGE_ID}" ]] || {
  printf 'builder stdout was not exactly one immutable image ID: %q\n' "${actual}" >&2
  exit 1
}

jq -e \
  --arg imageId "${IMAGE_ID}" \
  --arg candidate "${CANDIDATE}" \
  --arg specDigest "${SPEC_DIGEST}" \
  '.schemaVersion == "weave.keycloak-runtime-image.v3" and
   .imageId == $imageId and
   .candidateCommit == $candidate and
   .specDigest == $specDigest and
   .providerId == "weave-workload-client-registration-enforcer" and
   .labelsVerified == true and
   .providerArtifactVerified == true and
   .supportSafe == true and
   .containsSecretValues == false' \
  "${EVIDENCE_FILE}" >/dev/null

printf 'build-keycloak-image-helper-test: ok\n'
