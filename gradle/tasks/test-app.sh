#!/usr/bin/env bash
# shellcheck shell=bash

set -euo pipefail

REPOSITORY_ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)"
readonly REPOSITORY_ROOT
readonly WORKSPACE_ROOT="${REPOSITORY_ROOT}/infra/weave-workspace"
readonly CONTEXT_HELPER="${REPOSITORY_ROOT}/gradle/scripts/prepare_test_app_context.py"
readonly RUNTIME_CLEANUP="${REPOSITORY_ROOT}/gradle/scripts/cleanup_test_app_runtime.py"
readonly COMPOSE="${WORKSPACE_ROOT}/compose.sh"
readonly TEARDOWN="${WORKSPACE_ROOT}/teardown.sh"
readonly FAILURE_DIAGNOSTICS="${WORKSPACE_ROOT}/live-stack-failure-diagnostics.sh"

RUN_ID="${WEAVE_TEST_APP_RUN_ID:-}"
OUTPUT_ROOT="${WEAVE_TEST_APP_OUTPUT_ROOT:-${REPOSITORY_ROOT}/build/test-app}"
SERVER_IMAGE="${WEAVE_TEST_APP_SERVER_IMAGE:-}"
MCP_IMAGE="${WEAVE_TEST_APP_MCP_IMAGE:-}"
IDENTITY_OPS_IMAGE="${WEAVE_TEST_APP_IDENTITY_OPS_IMAGE:-}"
KEYCLOAK_IMAGE="${WEAVE_TEST_APP_KEYCLOAK_IMAGE:-}"
LOCAL_SERVER_TAG=""
LOCAL_MCP_TAG=""
STACK_PREPARED=false

log() { printf '%s\n' "$*"; }
fail() { printf 'WEAVE_TEST_APP_LIFECYCLE_ERROR %s\n' "$*" >&2; exit 1; }

require_command() {
  command -v "$1" >/dev/null 2>&1 || fail "missing required command: $1"
}

image_label() {
  docker image inspect --format "{{ index .Config.Labels \"$2\" }}" "$1"
}

validate_runtime_image() {
  local image="$1" expected_title="$2" expected_platform="$3"
  [[ "$(image_label "${image}" org.opencontainers.image.title)" == "${expected_title}" ]] ||
    fail "${expected_title} image title label is invalid"
  [[ "$(image_label "${image}" org.opencontainers.image.revision)" == "${candidate_commit}" ]] ||
    fail "${expected_title} image revision is not the exact candidate"
  [[ "$(image_label "${image}" com.massimotter.weave.spec-digest)" == "${spec_digest}" ]] ||
    fail "${expected_title} image specification digest is invalid"
  [[ "$(image_label "${image}" com.massimotter.weave.dependency-platform)" == "${expected_platform}" ]] ||
    fail "${expected_title} image dependency-platform label is invalid"
}

cleanup() {
  local primary_status="$?" cleanup_status=0
  trap - EXIT INT TERM
  set +e
  if [[ "${STACK_PREPARED}" == "true" ]]; then
    if ((primary_status != 0)) && [[ -x "${FAILURE_DIAGNOSTICS}" ]]; then
      WEAVE_PROFILE=test \
        WEAVE_RESOURCE_PREFIX="${WEAVE_E2E_RUN_NAMESPACE}" \
        bash "${FAILURE_DIAGNOSTICS}" \
          "${OUTPUT_ROOT}/${WEAVE_E2E_RUN_NAMESPACE}/failure-diagnostics" ||
        log "WEAVE_TEST_APP_LIFECYCLE_WARNING support-safe failure diagnostics did not complete"
    fi
    WEAVE_TEARDOWN_EVIDENCE_FILE="${WEAVE_TEST_APP_TEARDOWN_EVIDENCE_PATH}" \
      bash "${TEARDOWN}" test \
        --env-file "${WEAVE_ENV_FILE}" \
        --isolated \
        --evidence-file "${WEAVE_TEST_APP_TEARDOWN_EVIDENCE_PATH}" ||
      cleanup_status=$?
    if ((cleanup_status == 0)); then
      python3 "${RUNTIME_CLEANUP}" \
        --repository-root "${REPOSITORY_ROOT}" \
        --namespace "${WEAVE_E2E_RUN_NAMESPACE}" ||
        cleanup_status=$?
    fi
  fi
  [[ -z "${LOCAL_SERVER_TAG}" ]] || docker image rm "${LOCAL_SERVER_TAG}" >/dev/null 2>&1 || true
  [[ -z "${LOCAL_MCP_TAG}" ]] || docker image rm "${LOCAL_MCP_TAG}" >/dev/null 2>&1 || true
  if ((primary_status != 0)); then
    exit "${primary_status}"
  fi
  if ((cleanup_status != 0)); then
    fail "isolated stack teardown failed with status ${cleanup_status}"
  fi
}

trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

for command in bash docker git java jq openssl python3 shasum; do
  require_command "${command}"
done
docker info >/dev/null 2>&1 || fail "Docker daemon is not reachable"
[[ -x "${REPOSITORY_ROOT}/gradlew" ]] || fail "Gradle wrapper is unavailable"
[[ "${OUTPUT_ROOT}" == /* ]] || fail "WEAVE_TEST_APP_OUTPUT_ROOT must be absolute"
[[ -f "${CONTEXT_HELPER}" ]] || fail "Fresh testApp context helper is unavailable"

candidate_commit="${WEAVE_CANDIDATE_COMMIT:-$(git -C "${REPOSITORY_ROOT}" rev-parse HEAD)}"
[[ "${candidate_commit}" =~ ^[0-9a-f]{40}$ ]] ||
  fail "the candidate commit must be an exact lowercase Git object ID"
[[ -z "$(git -C "${REPOSITORY_ROOT}" status --porcelain=v1 --untracked-files=all)" ]] ||
  fail "testApp requires a clean worktree so every runtime artifact identifies exact source"

if [[ -z "${RUN_ID}" ]]; then
  RUN_ID="testapp-$(date -u +%Y%m%d%H%M%S)-$$"
fi
[[ "${RUN_ID}" =~ ^[a-z0-9][a-z0-9-]{5,39}$ ]] ||
  fail "WEAVE_TEST_APP_RUN_ID must match [a-z0-9][a-z0-9-]{5,39}"

mkdir -p "${OUTPUT_ROOT}"
chmod 700 "${OUTPUT_ROOT}"
context="$(
  python3 "${CONTEXT_HELPER}" \
    --repository-root "${REPOSITORY_ROOT}" \
    --output-root "${OUTPUT_ROOT}" \
    --run-id "${RUN_ID}"
)"
eval "${context}"
export WEAVE_E2E_RUN_ID WEAVE_E2E_RUN_NAMESPACE WEAVE_ENV_FILE
export WEAVE_PROXY_HTTP_HOST_PORT WEAVE_PROXY_HTTPS_HOST_PORT
export WEAVE_KEYCLOAK_HOST_PORT WEAVE_KEYCLOAK_MANAGEMENT_HOST_PORT
export WEAVE_MAILPIT_WEB_HOST_PORT WEAVE_MAS_HOST_PORT WEAVE_SYNAPSE_HOST_PORT
export WEAVE_NEXTCLOUD_HOST_PORT WEAVE_BACKEND_HOST_PORT WEAVE_MCP_HOST_PORT
export WEAVE_E2E_STACK_SCOPE=isolated
export WEAVE_CANDIDATE_COMMIT="${candidate_commit}"

spec_lock="${REPOSITORY_ROOT}/specs/weave-specs.lock.json"
[[ -f "${spec_lock}" ]] || fail "the pinned specification lock is unavailable"
spec_digest="sha256:$(shasum -a 256 "${spec_lock}" | awk '{print $1}')"

if [[ -z "${SERVER_IMAGE}" && -z "${MCP_IMAGE}" ]]; then
  LOCAL_SERVER_TAG="weave-backend:test-app-${WEAVE_E2E_RUN_NAMESPACE}"
  LOCAL_MCP_TAG="weave-mcp-server:test-app-${WEAVE_E2E_RUN_NAMESPACE}"
  SERVER_IMAGE="${LOCAL_SERVER_TAG}"
  MCP_IMAGE="${LOCAL_MCP_TAG}"
  log "Building Server and MCP from the exact candidate."
  "${REPOSITORY_ROOT}/gradlew" --no-daemon --max-workers=2 \
    :server:bootJar \
    :weave-mcp-server:bootJar
  image_created="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  image_version="test-app-${candidate_commit:0:12}"
  common_build_args=(
    --build-arg "WEAVE_IMAGE_CREATED=${image_created}"
    --build-arg "WEAVE_IMAGE_REVISION=${candidate_commit}"
    --build-arg "WEAVE_IMAGE_VERSION=${image_version}"
    --build-arg "WEAVE_SPEC_DIGEST=${spec_digest}"
    --build-arg "WEAVE_SBOM_REFERENCE=local-test-app-not-published"
    --build-arg "WEAVE_PROVENANCE_REFERENCE=local-test-app-not-published"
  )
  docker build \
    "${common_build_args[@]}" \
    --tag "${SERVER_IMAGE}" \
    --file "${REPOSITORY_ROOT}/server/Dockerfile" \
    "${REPOSITORY_ROOT}"
  docker build \
    "${common_build_args[@]}" \
    --tag "${MCP_IMAGE}" \
    --file "${REPOSITORY_ROOT}/weave-mcp-server/Dockerfile" \
    "${REPOSITORY_ROOT}"
elif [[ -n "${SERVER_IMAGE}" && -n "${MCP_IMAGE}" ]]; then
  [[ "${SERVER_IMAGE}" =~ @sha256:[0-9a-f]{64}$ ]] ||
    fail "WEAVE_TEST_APP_SERVER_IMAGE must be digest-pinned"
  [[ "${MCP_IMAGE}" =~ @sha256:[0-9a-f]{64}$ ]] ||
    fail "WEAVE_TEST_APP_MCP_IMAGE must be digest-pinned"
  docker pull "${SERVER_IMAGE}"
  docker pull "${MCP_IMAGE}"
else
  fail "Server and MCP image overrides must be supplied together"
fi
validate_runtime_image "${SERVER_IMAGE}" "Weave Server" "java21-spring-boot-4.1"
validate_runtime_image \
  "${MCP_IMAGE}" \
  "Weave MCP Server" \
  "java21-spring-boot-4.1-spring-ai-2.0"

if [[ -z "${IDENTITY_OPS_IMAGE}" ]]; then
  IDENTITY_OPS_IMAGE="$(
    python3 "${WORKSPACE_ROOT}/scripts/build_identity_ops_image.py" \
      --root "${WORKSPACE_ROOT}" \
      --candidate-commit "${candidate_commit}" |
      tail -n 1
  )"
else
  [[ "${IDENTITY_OPS_IMAGE}" =~ @sha256:[0-9a-f]{64}$ ]] ||
    fail "WEAVE_TEST_APP_IDENTITY_OPS_IMAGE must be digest-pinned"
  docker pull "${IDENTITY_OPS_IMAGE}"
fi
if [[ -z "${KEYCLOAK_IMAGE}" ]]; then
  KEYCLOAK_IMAGE="$(
    python3 "${WORKSPACE_ROOT}/scripts/build_keycloak_image.py" \
      --root "${REPOSITORY_ROOT}" \
      --candidate-commit "${candidate_commit}"
  )"
else
  [[ "${KEYCLOAK_IMAGE}" =~ @sha256:[0-9a-f]{64}$ ]] ||
    fail "WEAVE_TEST_APP_KEYCLOAK_IMAGE must be digest-pinned"
  docker pull "${KEYCLOAK_IMAGE}"
fi

export WEAVE_BACKEND_IMAGE
WEAVE_BACKEND_IMAGE="$(docker image inspect "${SERVER_IMAGE}" --format '{{.Id}}')"
export WEAVE_MCP_IMAGE
WEAVE_MCP_IMAGE="$(docker image inspect "${MCP_IMAGE}" --format '{{.Id}}')"
export WEAVE_IDENTITY_OPS_IMAGE
WEAVE_IDENTITY_OPS_IMAGE="$(docker image inspect "${IDENTITY_OPS_IMAGE}" --format '{{.Id}}')"
export WEAVE_KEYCLOAK_IMAGE
WEAVE_KEYCLOAK_IMAGE="$(docker image inspect "${KEYCLOAK_IMAGE}" --format '{{.Id}}')"

log "Starting one exact, disposable Compose test stack."
STACK_PREPARED=true
bash "${COMPOSE}" test up
bash "${WORKSPACE_ROOT}/operator-check.sh" test

for required in \
  "${WEAVE_TEST_APP_TLS_ROOT}/ca.pem" \
  "${WEAVE_TEST_APP_TLS_ROOT}/cert.pem" \
  "${WEAVE_TEST_APP_TLS_ROOT}/mailpit-cert.pem" \
  "${WEAVE_TEST_APP_TLS_ROOT}/mailpit-key.pem" \
  "${WEAVE_TEST_APP_SECRET_ROOT}/identity-reference-hmac-key" \
  "${WEAVE_TEST_APP_SECRET_ROOT}/identity-bootstrap-owner-token"; do
  [[ -f "${required}" && ! -L "${required}" ]] ||
    fail "an exact TLS or bootstrap SecretRef input is unavailable"
done
[[ -d "${WEAVE_TEST_APP_SECRET_ROOT}/agent-runtime/workloads" ]] ||
  fail "the isolated workload SecretRef root is unavailable"

log "Running invitation, real Chromium activation, PKCE, WebDAV, ARC, and MCP."
"${REPOSITORY_ROOT}/gradlew" \
  --no-daemon \
  --max-workers=2 \
  "-Dweave.e2e.run-id=${RUN_ID}" \
  "-Dweave.e2e.api-origin=${WEAVE_TEST_APP_API_ORIGIN}" \
  "-Dweave.e2e.issuer=${WEAVE_TEST_APP_ISSUER}" \
  "-Dweave.e2e.mailpit-api=${WEAVE_TEST_APP_MAILPIT_API}" \
  "-Dweave.e2e.mcp-endpoint=${WEAVE_TEST_APP_MCP_ENDPOINT}" \
  "-Dweave.e2e.ca-certificate=${WEAVE_TEST_APP_TLS_ROOT}/ca.pem" \
  "-Dweave.e2e.tls-leaf-certificate=${WEAVE_TEST_APP_TLS_ROOT}/cert.pem" \
  "-Dweave.e2e.hosts-file=${WEAVE_TEST_APP_HOSTS_FILE}" \
  "-Dweave.e2e.bootstrap-owner-token=${WEAVE_TEST_APP_SECRET_ROOT}/identity-bootstrap-owner-token" \
  "-Dweave.e2e.workload-credential-root=${WEAVE_TEST_APP_SECRET_ROOT}/agent-runtime/workloads" \
  "-Dweave.e2e.evidence-file=${WEAVE_TEST_APP_EVIDENCE_PATH}" \
  "-Dweave.e2e.convergence-timeout=${WEAVE_TEST_APP_CONVERGENCE_TIMEOUT:-PT3M}" \
  :weave-product-e2e:productFlow

jq -e '
  .schemaVersion == "weave.test-app-product-flow/v1" and
  .activation == "keycloak-required-actions-real-chromium" and
  .humanOAuth == "authorization_code_pkce_s256" and
  .workloadOAuth == "client_credentials_private_key_jwt" and
  .mcpTool == "files.search" and
  .serverProjection == "weave-webdav" and
  .canonicalResourceSeen == true and
  .revocationDenied == true and
  .credentialsIncluded == false and
  .actionLinksIncluded == false and
  .supportSafe == true
' "${WEAVE_TEST_APP_EVIDENCE_PATH}" >/dev/null ||
  fail "the Fresh product-flow evidence is incomplete"
! grep -Eqi 'login-actions/action-token|client_assertion|access_token|refresh_token|password' \
  "${WEAVE_TEST_APP_EVIDENCE_PATH}" ||
  fail "the product-flow evidence contains credential material"

log "WEAVE_TEST_APP_LIFECYCLE_RESULT status=passed isolated=true cleanup=armed supportSafe=true"
