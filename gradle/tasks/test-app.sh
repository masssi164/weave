#!/usr/bin/env bash
# shellcheck shell=bash
# shellcheck disable=SC2154

set -euo pipefail

REPOSITORY_ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)"
readonly REPOSITORY_ROOT
readonly WORKSPACE_ROOT="${REPOSITORY_ROOT}/infra/weave-workspace"
readonly IDENTITY_LIFECYCLE="${WORKSPACE_ROOT}/isolated-e2e-identities.sh"
readonly INSTALL_SCRIPT="${WORKSPACE_ROOT}/install.sh"
readonly TEARDOWN_SCRIPT="${WORKSPACE_ROOT}/teardown.sh"

RUN_ID="${WEAVE_TEST_APP_RUN_ID:-}"
OUTPUT_ROOT="${WEAVE_TEST_APP_OUTPUT_ROOT:-${REPOSITORY_ROOT}/build/test-app}"
SERVER_IMAGE="${WEAVE_TEST_APP_SERVER_IMAGE:-}"
MCP_IMAGE="${WEAVE_TEST_APP_MCP_IMAGE:-}"
STACK_PREPARED=false

log() { printf '%s\n' "$*"; }
fail() { printf 'WEAVE_TEST_APP_LIFECYCLE_ERROR %s\n' "$*" >&2; exit 1; }

require_command() {
  command -v "$1" >/dev/null 2>&1 || fail "missing required command: $1"
}

file_mode() {
  local path="$1"
  if stat -c '%a' "${path}" >/dev/null 2>&1; then
    stat -c '%a' "${path}"
  else
    stat -f '%Lp' "${path}"
  fi
}

public_origin() {
  local host="$1"
  local port="${TF_VAR_proxy_host_port}"
  if [[ "${port}" == "443" ]]; then
    printf 'https://%s' "${host}"
  else
    printf 'https://%s:%s' "${host}" "${port}"
  fi
}

image_label() {
  local image="$1"
  local label="$2"
  docker image inspect --format "{{ index .Config.Labels \"${label}\" }}" "${image}"
}

validate_runtime_image() {
  local image="$1"
  local expected_title="$2"
  local expected_platform="$3"
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
  local primary_status="$?"
  local cleanup_status=0
  trap - EXIT INT TERM
  set +e

  if [[ "${STACK_PREPARED}" == "true" && -f "${WEAVE_E2E_STARTUP_ENV_PATH:-}" ]]; then
    if [[ -f "${WEAVE_E2E_STACK_BOOTSTRAP_ENV:-}" ]]; then
      # shellcheck disable=SC1090
      source "${WEAVE_E2E_STACK_BOOTSTRAP_ENV}"
    fi
    # Reload last so immutable namespace, ownership, candidate, and port bindings
    # cannot be redirected by generated bootstrap state.
    # shellcheck disable=SC1090
    source "${WEAVE_E2E_STARTUP_ENV_PATH}"
    WEAVE_TEARDOWN_EVIDENCE_FILE="${WEAVE_E2E_OUTPUT_ROOT}/${WEAVE_E2E_RUN_NAMESPACE}/test-app-teardown.json" \
      WEAVE_E2E_IDENTITY_MANIFEST_PATH="${WEAVE_E2E_IDENTITY_MANIFEST_PATH}" \
      WEAVE_E2E_STACK_SCOPE=isolated \
      WEAVE_REMOVE_VOLUMES=true \
      WEAVE_CONFIRM_DESTRUCTIVE_RESET="${TF_VAR_tenant_slug}" \
      bash "${TEARDOWN_SCRIPT}" || cleanup_status=$?
  fi

  if [[ -n "${SERVER_IMAGE}" ]]; then
    docker image rm "${SERVER_IMAGE}" >/dev/null 2>&1 || true
  fi
  if [[ -n "${MCP_IMAGE}" ]]; then
    docker image rm "${MCP_IMAGE}" >/dev/null 2>&1 || true
  fi

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

for command in bash docker git java jq openssl python3 shasum tofu; do
  require_command "${command}"
done
docker info >/dev/null 2>&1 || fail "Docker daemon is not reachable"
[[ -x "${REPOSITORY_ROOT}/gradlew" ]] || fail "Gradle wrapper is unavailable"
[[ "${OUTPUT_ROOT}" == /* ]] || fail "WEAVE_TEST_APP_OUTPUT_ROOT must be absolute"
if [[ -z "${SERVER_IMAGE}" && -z "${MCP_IMAGE}" ]]; then
  [[ -z "$(git -C "${REPOSITORY_ROOT}" status --porcelain=v1 --untracked-files=all)" ]] ||
    fail "local testApp image builds require a clean worktree so OCI revision labels identify exact source"
elif [[ -n "${SERVER_IMAGE}" && -n "${MCP_IMAGE}" ]]; then
  [[ "${SERVER_IMAGE}" =~ @sha256:[0-9a-f]{64}$ ]] ||
    fail "WEAVE_TEST_APP_SERVER_IMAGE must be digest-pinned"
  [[ "${MCP_IMAGE}" =~ @sha256:[0-9a-f]{64}$ ]] ||
    fail "WEAVE_TEST_APP_MCP_IMAGE must be digest-pinned"
else
  fail "Server and MCP testApp image overrides must be supplied together"
fi

if [[ -z "${RUN_ID}" ]]; then
  RUN_ID="test-app-$(date -u +%Y%m%dT%H%M%SZ)-$$-$(openssl rand -hex 4)"
fi
[[ "${RUN_ID}" =~ ^[A-Za-z0-9][A-Za-z0-9._:-]{7,159}$ ]] ||
  fail "WEAVE_TEST_APP_RUN_ID is invalid"

candidate_commit="${WEAVE_CANDIDATE_COMMIT:-$(git -C "${REPOSITORY_ROOT}" rev-parse HEAD)}"
[[ "${candidate_commit}" =~ ^[0-9a-f]{40}$ ]] ||
  fail "the candidate commit must be a lowercase 40-character Git object ID"
candidate_evidence_ref="${WEAVE_CANDIDATE_EVIDENCE_REF:-https://github.com/masssi164/weave/commit/${candidate_commit}}"

mkdir -p "${OUTPUT_ROOT}"
chmod 700 "${OUTPUT_ROOT}"
integration_variables="$(
  WEAVE_E2E_STACK_SCOPE=isolated \
    WEAVE_CANDIDATE_COMMIT="${candidate_commit}" \
    WEAVE_CANDIDATE_EVIDENCE_REF="${candidate_evidence_ref}" \
    bash "${IDENTITY_LIFECYCLE}" prepare-product-flow \
      --run-id "${RUN_ID}" \
      --output-root "${OUTPUT_ROOT}"
)"
eval "${integration_variables}"
export WEAVE_E2E_OUTPUT_ROOT
export WEAVE_E2E_RUN_NAMESPACE
export WEAVE_E2E_STARTUP_ENV_PATH
export WEAVE_E2E_IDENTITY_MANIFEST_PATH
export WEAVE_TEST_APP_EVIDENCE_PATH
export WEAVE_E2E_STACK_BOOTSTRAP_ENV
export WEAVE_TEARDOWN_OWNERSHIP_FILE
# shellcheck disable=SC1090
source "${WEAVE_E2E_STARTUP_ENV_PATH}"
STACK_PREPARED=true

run_root="${WEAVE_E2E_OUTPUT_ROOT}/${WEAVE_E2E_RUN_NAMESPACE}"
[[ ! -e "${run_root}/credentials.env" ]] ||
  fail "product-flow context contains a human credential file"
[[ "${TF_VAR_identity_bootstrap_owner_enabled}" == "true" ]] ||
  fail "protected owner bootstrap was not enabled for the empty realm"
[[ "${TF_VAR_isolated_e2e_context_memberships}" == "[]" ]] ||
  fail "product-flow context must not seed static human memberships"

spec_lock="${REPOSITORY_ROOT}/specs/weave-specs.lock.json"
[[ -f "${spec_lock}" ]] || fail "the repository specification lock is unavailable"
spec_digest="sha256:$(shasum -a 256 "${spec_lock}" | awk '{print $1}')"
if [[ -z "${SERVER_IMAGE}" && -z "${MCP_IMAGE}" ]]; then
  SERVER_IMAGE="weave-backend:test-app-${WEAVE_E2E_RUN_NAMESPACE}"
  MCP_IMAGE="weave-mcp-server:test-app-${WEAVE_E2E_RUN_NAMESPACE}"
  log "Building the exact Server and MCP runtime artifacts for the isolated product proof."
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
  log "Pulling the exact digest-pinned Server and MCP candidate images."
  docker pull "${SERVER_IMAGE}"
  docker pull "${MCP_IMAGE}"
else
  fail "Server and MCP testApp image overrides must be supplied together"
fi
readonly SERVER_IMAGE MCP_IMAGE
validate_runtime_image \
  "${SERVER_IMAGE}" \
  "Weave Server" \
  "java21-spring-boot-4.1"
validate_runtime_image \
  "${MCP_IMAGE}" \
  "Weave MCP Server" \
  "java21-spring-boot-4.1-spring-ai-2.0"

export TF_VAR_weave_backend_image="${SERVER_IMAGE}"
export TF_VAR_weave_mcp_server_image="${MCP_IMAGE}"
log "Starting one exact, disposable, ownership-labelled Weave stack."
bash "${INSTALL_SCRIPT}"

[[ -f "${WEAVE_E2E_STACK_BOOTSTRAP_ENV}" && ! -L "${WEAVE_E2E_STACK_BOOTSTRAP_ENV}" ]] ||
  fail "the exact isolated bootstrap environment is unavailable"
# shellcheck disable=SC1090
source "${WEAVE_E2E_STACK_BOOTSTRAP_ENV}"
# shellcheck disable=SC1090
source "${WEAVE_E2E_STARTUP_ENV_PATH}"
export TF_VAR_weave_backend_image="${SERVER_IMAGE}"
export TF_VAR_weave_mcp_server_image="${MCP_IMAGE}"

domain="${TF_VAR_tenant_domain:-weave.test}"
api_host="${TF_VAR_api_subdomain:-api}.${domain}"
auth_host="${TF_VAR_auth_subdomain:-auth}.${domain}"
api_origin="$(public_origin "${api_host}")"
issuer="$(public_origin "${auth_host}")/realms/${TF_VAR_tenant_slug}"
mcp_endpoint="${api_origin}/mcp"
mailpit_api="http://127.0.0.1:${TF_VAR_mailpit_web_host_port}/api/v1"
infra_generated="${WORKSPACE_ROOT}/01-infrastructure/.generated/isolated/${WEAVE_E2E_RUN_NAMESPACE}"
ca_file="${infra_generated}/caddy/certs/weave-local-ca.pem"
leaf_file="${infra_generated}/caddy/certs/weave.test.pem"
credential_root="${infra_generated}/agent-runtime/credentials"
bootstrap_token="${TF_VAR_identity_bootstrap_owner_token_host_path:-}"
hosts_file="${run_root}/hosts"

for required_file in "${ca_file}" "${leaf_file}" "${bootstrap_token}"; do
  [[ -f "${required_file}" && ! -L "${required_file}" ]] ||
    fail "an exact isolated TLS or bootstrap SecretRef input is unavailable"
done
[[ -d "${credential_root}" && ! -L "${credential_root}" ]] ||
  fail "the isolated workload credential root is unavailable"
[[ "$(file_mode "${bootstrap_token}")" == "600" ]] ||
  fail "owner bootstrap SecretRef permissions are not private"

umask 077
printf '127.0.0.1 %s %s\n' "${api_host}" "${auth_host}" >"${hosts_file}"
chmod 600 "${hosts_file}"

log "Running invitation, real Chromium activation, PKCE, ARC, WebDAV, and MCP."
"${REPOSITORY_ROOT}/gradlew" \
  --no-daemon \
  --max-workers=2 \
  "-Dweave.e2e.run-id=${RUN_ID}" \
  "-Dweave.e2e.api-origin=${api_origin}" \
  "-Dweave.e2e.issuer=${issuer}" \
  "-Dweave.e2e.mailpit-api=${mailpit_api}" \
  "-Dweave.e2e.mcp-endpoint=${mcp_endpoint}" \
  "-Dweave.e2e.ca-certificate=${ca_file}" \
  "-Dweave.e2e.tls-leaf-certificate=${leaf_file}" \
  "-Dweave.e2e.hosts-file=${hosts_file}" \
  "-Dweave.e2e.bootstrap-owner-token=${bootstrap_token}" \
  "-Dweave.e2e.workload-credential-root=${credential_root}" \
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
  fail "the product-flow evidence does not satisfy the Fresh acceptance contract"
! grep -Eq 'login-actions/action-token|client_assertion|access_token|refresh_token|password' \
  "${WEAVE_TEST_APP_EVIDENCE_PATH}" ||
  fail "the product-flow evidence contains credential material"

log "WEAVE_TEST_APP_LIFECYCLE_RESULT status=passed isolated=true cleanup=armed supportSafe=true"
