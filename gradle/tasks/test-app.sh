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
readonly DCR_CONTRACT_PROBE="${WORKSPACE_ROOT}/scripts/verify_keycloak_dcr_contract.py"
readonly RUNTIME_IMAGE_EVIDENCE_WRITER="${REPOSITORY_ROOT}/gradle/scripts/write_test_app_runtime_image_evidence.py"
readonly CANDIDATE_MANIFEST_CHECK="${REPOSITORY_ROOT}/gradle/tasks/candidate-manifest-check.py"

RUN_ID="${WEAVE_TEST_APP_RUN_ID:-}"
OUTPUT_ROOT="${WEAVE_TEST_APP_OUTPUT_ROOT:-${REPOSITORY_ROOT}/build/test-app}"
SERVER_IMAGE="${WEAVE_TEST_APP_SERVER_IMAGE:-}"
MCP_IMAGE="${WEAVE_TEST_APP_MCP_IMAGE:-}"
KEYCLOAK_IMAGE="${WEAVE_TEST_APP_KEYCLOAK_IMAGE:-}"
LOCAL_SERVER_TAG=""
LOCAL_MCP_TAG=""
CONTEXT_PREPARED=false
STACK_PREPARED=false

log() { printf '%s\n' "$*"; }
fail() { printf 'WEAVE_TEST_APP_LIFECYCLE_ERROR %s\n' "$*" >&2; exit 1; }

require_command() {
  command -v "$1" >/dev/null 2>&1 || fail "missing required command: $1"
}

require_free_disk_space() {
  local minimum_kib=8388608
  local available_kib
  mkdir -p "${OUTPUT_ROOT}"
  available_kib="$(df -Pk "${OUTPUT_ROOT}" | awk 'NR == 2 {print $4}')"
  [[ "${available_kib}" =~ ^[0-9]+$ ]] ||
    fail "unable to determine free disk space for the isolated proof"
  if ((available_kib < minimum_kib)); then
    fail "isolated proof requires at least ${minimum_kib} KiB free before image build and resource creation"
  fi
}

require_no_pending_registration_operations() {
  local workload_root="${WEAVE_TEST_APP_SECRET_ROOT}/agent-runtime/workloads"
  local operation_root
  [[ -d "${workload_root}" && ! -L "${workload_root}" ]] ||
    fail "the isolated workload SecretRef root is unsafe or unavailable"
  for operation_root in \
    "${workload_root}/weave/agent-runtime/registration-handoffs" \
    "${workload_root}/weave/agent-runtime/registration-deletions"; do
    [[ ! -L "${operation_root}" ]] ||
      fail "a registration authority operation root is unsafe"
    if [[ -e "${operation_root}" ]]; then
      [[ -d "${operation_root}" ]] ||
        fail "a registration authority operation root is unsafe"
      if [[ -n "$(find "${operation_root}" -mindepth 1 -maxdepth 1 -print -quit)" ]]; then
        fail "a pending registration authority operation blocks isolated proof"
      fi
    fi
  done
}

image_label() {
  docker image inspect --format "{{ index .Config.Labels \"$2\" }}" "$1"
}

validate_runtime_image() {
  local image="$1" expected_title="$2" expected_platform="$3"
  [[ "$(image_label "${image}" org.opencontainers.image.title)" == "${expected_title}" ]] ||
    fail "${expected_title} image title label is invalid"
  [[ "$(image_label "${image}" org.opencontainers.image.revision)" == "${image_source_commit}" ]] ||
    fail "${expected_title} image revision is not the exact source candidate"
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
      WEAVE_PROFILE=e2e \
        WEAVE_RESOURCE_PREFIX="${WEAVE_E2E_RUN_NAMESPACE}" \
        WEAVE_LIVE_STACK_DIAGNOSTICS_TIMEOUT_SECONDS=30 \
        bash "${FAILURE_DIAGNOSTICS}" \
          "${OUTPUT_ROOT}/${WEAVE_E2E_RUN_NAMESPACE}/failure-diagnostics" ||
        log "WEAVE_TEST_APP_LIFECYCLE_WARNING support-safe failure diagnostics did not complete"
    fi
    WEAVE_TEARDOWN_EVIDENCE_FILE="${WEAVE_TEST_APP_TEARDOWN_EVIDENCE_PATH}" \
      bash "${TEARDOWN}" e2e \
        --env-file "${WEAVE_ENV_FILE}" \
        --isolated \
        --evidence-file "${WEAVE_TEST_APP_TEARDOWN_EVIDENCE_PATH}" ||
      cleanup_status=$?
  fi
  if [[ "${CONTEXT_PREPARED}" == "true" ]] && ((cleanup_status == 0)); then
    python3 "${RUNTIME_CLEANUP}" \
      --repository-root "${REPOSITORY_ROOT}" \
      --output-root "${OUTPUT_ROOT}" \
      --namespace "${WEAVE_E2E_RUN_NAMESPACE}" ||
      cleanup_status=$?
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

for command in awk bash df docker find git java jq openssl python3 shasum; do
  require_command "${command}"
done
docker info >/dev/null 2>&1 || fail "Docker daemon is not reachable"
[[ -x "${REPOSITORY_ROOT}/gradlew" ]] || fail "Gradle wrapper is unavailable"
[[ "${OUTPUT_ROOT}" == /* ]] || fail "WEAVE_TEST_APP_OUTPUT_ROOT must be absolute"
[[ -f "${CONTEXT_HELPER}" ]] || fail "Fresh testApp context helper is unavailable"
require_free_disk_space

candidate_commit="${WEAVE_CANDIDATE_COMMIT:-$(git -C "${REPOSITORY_ROOT}" rev-parse HEAD)}"
[[ "${candidate_commit}" =~ ^[0-9a-f]{40}$ ]] ||
  fail "the candidate commit must be an exact lowercase Git object ID"
image_source_commit="${WEAVE_IMAGE_SOURCE_COMMIT:-${candidate_commit}}"
[[ "${image_source_commit}" =~ ^[0-9a-f]{40}$ ]] ||
  fail "the image source commit must be an exact lowercase Git object ID"
[[ -z "$(git -C "${REPOSITORY_ROOT}" status --porcelain=v1 --untracked-files=all)" ]] ||
  fail "testApp requires a clean worktree so every runtime artifact identifies exact source"

if [[ -z "${RUN_ID}" ]]; then
  RUN_ID="testapp-$(date -u +%Y%m%d%H%M%S)-$$"
fi
[[ "${RUN_ID}" =~ ^[a-z0-9][a-z0-9-]{5,39}$ ]] ||
  fail "WEAVE_TEST_APP_RUN_ID must match [a-z0-9][a-z0-9-]{5,39}"

chmod 700 "${OUTPUT_ROOT}"
context="$(
  python3 "${CONTEXT_HELPER}" \
    --repository-root "${REPOSITORY_ROOT}" \
    --output-root "${OUTPUT_ROOT}" \
    --run-id "${RUN_ID}"
)"
eval "${context}"
CONTEXT_PREPARED=true
export WEAVE_E2E_RUN_ID WEAVE_E2E_RUN_NAMESPACE WEAVE_E2E_NAMESPACE WEAVE_ENV_FILE
export WEAVE_TEST_APP_RUN_ROOT WEAVE_TEST_APP_RESTART_EVIDENCE_PATH
export WEAVE_TEST_APP_RUNTIME_IMAGE_EVIDENCE_PATH
export WEAVE_TEST_APP_TENANT_ID
export WEAVE_PROXY_HTTP_HOST_PORT WEAVE_PROXY_HTTPS_HOST_PORT
export WEAVE_KEYCLOAK_HOST_PORT WEAVE_KEYCLOAK_MANAGEMENT_HOST_PORT
export WEAVE_MAILPIT_WEB_HOST_PORT WEAVE_MAS_HOST_PORT WEAVE_SYNAPSE_HOST_PORT
export WEAVE_NEXTCLOUD_HOST_PORT WEAVE_BACKEND_HOST_PORT WEAVE_MCP_HOST_PORT
export WEAVE_E2E_STACK_SCOPE=isolated
export WEAVE_CANDIDATE_COMMIT="${candidate_commit}"
export WEAVE_IMAGE_SOURCE_COMMIT="${image_source_commit}"

spec_lock="${REPOSITORY_ROOT}/specs/weave-specs.lock.json"
[[ -f "${spec_lock}" ]] || fail "the pinned specification lock is unavailable"
specification_commit="$(
  jq -er '.specCorpus.gitCommit | select(test("^[0-9a-f]{40}$"))' "${spec_lock}"
)" || fail "the pinned specification commit is invalid"
spec_digest="sha256:$(shasum -a 256 "${spec_lock}" | awk '{print $1}')"
candidate_manifest_path="${WEAVE_TEST_APP_CANDIDATE_MANIFEST:-}"
if [[ -n "${candidate_manifest_path}" ]]; then
  [[ "${candidate_manifest_path}" == /* ]] ||
    fail "WEAVE_TEST_APP_CANDIDATE_MANIFEST must be absolute"
  [[ -f "${candidate_manifest_path}" && ! -L "${candidate_manifest_path}" ]] ||
    fail "the candidate manifest must be a regular non-symlink file"
  [[ -n "${WEAVE_CANDIDATE_MANIFEST_DIGEST:-}" ]] ||
    fail "a manifest-bound run requires WEAVE_CANDIDATE_MANIFEST_DIGEST"
  python3 "${CANDIDATE_MANIFEST_CHECK}" --manifest "${candidate_manifest_path}"
  candidate_manifest_digest="sha256:$(shasum -a 256 "${candidate_manifest_path}" | awk '{print $1}')"
  [[ "${candidate_manifest_digest}" == "${WEAVE_CANDIDATE_MANIFEST_DIGEST}" ]] ||
    fail "candidate manifest bytes do not match WEAVE_CANDIDATE_MANIFEST_DIGEST"
elif [[ -n "${WEAVE_CANDIDATE_MANIFEST_DIGEST:-}" ]]; then
  fail "WEAVE_CANDIDATE_MANIFEST_DIGEST requires the exact candidate manifest"
else
  candidate_manifest_digest="$(
    python3 - "${candidate_commit}" "${spec_digest}" <<'PY'
import hashlib
import json
import sys

payload = {
    "schemaVersion": "weave.test-app-local-candidate.v1",
    "candidateCommit": sys.argv[1],
    "specDigest": sys.argv[2],
}
serialized = json.dumps(payload, separators=(",", ":"), sort_keys=True).encode("utf-8")
print("sha256:" + hashlib.sha256(serialized).hexdigest())
PY
  )"
fi
export WEAVE_CANDIDATE_MANIFEST_DIGEST="${candidate_manifest_digest}"

if [[ -z "${SERVER_IMAGE}" && -z "${MCP_IMAGE}" ]]; then
  [[ "${image_source_commit}" == "${candidate_commit}" ]] ||
    fail "a lane promotion must consume manifest-bound source images without rebuilding"
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

if [[ -z "${KEYCLOAK_IMAGE}" ]]; then
  KEYCLOAK_IMAGE="$(
    python3 "${WORKSPACE_ROOT}/scripts/build_keycloak_image.py" \
      --root "${REPOSITORY_ROOT}" \
      --candidate-commit "${image_source_commit}"
  )"
else
  [[ "${KEYCLOAK_IMAGE}" =~ @sha256:[0-9a-f]{64}$ ]] ||
    fail "WEAVE_TEST_APP_KEYCLOAK_IMAGE must be digest-pinned"
  docker pull "${KEYCLOAK_IMAGE}"
fi
validate_runtime_image \
  "${KEYCLOAK_IMAGE}" \
  "Weave Keycloak Runtime" \
  "keycloak-26.7.0-downstream-built-in-policy"

if [[ -n "${candidate_manifest_path}" ]]; then
  jq -e \
    --arg source_candidate_commit "${image_source_commit}" \
    --arg specification_commit "${specification_commit}" \
    --arg spec_digest "${spec_digest}" \
    --arg server "${SERVER_IMAGE}" \
    --arg mcp "${MCP_IMAGE}" \
    --arg keycloak "${KEYCLOAK_IMAGE}" '
      .schemaVersion == "weave.release.candidate-manifest.v3" and
      .commit == $source_candidate_commit and
      .specificationCommit == $specification_commit and
      .specDigest == $spec_digest and
      .supportSafe == true and
      ([.images[] | {key: .component, value: .reference}] | from_entries) == {
        "server": $server,
        "mcp-server": $mcp,
        "keycloak-runtime": $keycloak
      } and
      .realmArtifacts.containsSecrets == false and
      (.realmArtifacts.baselineDigest | test("^sha256:[0-9a-f]{64}$")) and
      (.realmArtifacts.migrationBundleDigest | test("^sha256:[0-9a-f]{64}$"))
    ' "${candidate_manifest_path}" >/dev/null ||
    fail "runtime image inputs do not match the exact candidate manifest"
fi

export WEAVE_BACKEND_IMAGE
WEAVE_BACKEND_IMAGE="$(docker image inspect "${SERVER_IMAGE}" --format '{{.Id}}')"
export WEAVE_MCP_IMAGE
WEAVE_MCP_IMAGE="$(docker image inspect "${MCP_IMAGE}" --format '{{.Id}}')"
export WEAVE_KEYCLOAK_IMAGE
WEAVE_KEYCLOAK_IMAGE="$(docker image inspect "${KEYCLOAK_IMAGE}" --format '{{.Id}}')"

log "Starting one exact, disposable Compose test stack."
STACK_PREPARED=true
bash "${COMPOSE}" e2e up
bash "${WORKSPACE_ROOT}/operator-check.sh" e2e

runtime_image_evidence_arguments=(
  --candidate-commit "${candidate_commit}"
  --source-candidate-commit "${image_source_commit}"
  --specification-commit "${specification_commit}"
  --spec-digest "${spec_digest}"
  --candidate-manifest-digest "${candidate_manifest_digest}"
  --compose-project "${WEAVE_E2E_RUN_NAMESPACE}"
  --output "${WEAVE_TEST_APP_RUNTIME_IMAGE_EVIDENCE_PATH}"
  --image server "${SERVER_IMAGE}" "${WEAVE_BACKEND_IMAGE}"
  --image mcp-server "${MCP_IMAGE}" "${WEAVE_MCP_IMAGE}"
  --image keycloak-runtime "${KEYCLOAK_IMAGE}" "${WEAVE_KEYCLOAK_IMAGE}"
)
if [[ -n "${candidate_manifest_path}" ]]; then
  runtime_image_evidence_arguments+=(
    --manifest "${candidate_manifest_path}"
    --realm-baseline-artifact \
      "${WEAVE_TEST_APP_GENERATED_ROOT}/keycloak/import/weave-realm.json"
    --realm-migration-bundle-artifact \
      "${WEAVE_TEST_APP_GENERATED_ROOT}/keycloak/migrations/manifest.json"
  )
fi
python3 "${RUNTIME_IMAGE_EVIDENCE_WRITER}" \
  "${runtime_image_evidence_arguments[@]}"

for required in \
  "${WEAVE_TEST_APP_TLS_ROOT}/ca.pem" \
  "${WEAVE_TEST_APP_TLS_ROOT}/cert.pem" \
  "${WEAVE_TEST_APP_TLS_ROOT}/mailpit-cert.pem" \
  "${WEAVE_TEST_APP_TLS_ROOT}/mailpit-key.pem" \
  "${WEAVE_TEST_APP_SECRET_ROOT}/identity-reference-hmac-key" \
  "${WEAVE_TEST_APP_SECRET_ROOT}/identity-bootstrap-owner-token" \
  "${WEAVE_TEST_APP_SECRET_ROOT}/chat-e2e-proof-token"; do
  [[ -f "${required}" && ! -L "${required}" ]] ||
    fail "an exact TLS or bootstrap SecretRef input is unavailable"
done
require_no_pending_registration_operations

log "Running direct Keycloak DCR policy and Registration Access Token lifecycle proof."
dcr_evidence="${OUTPUT_ROOT}/${WEAVE_E2E_RUN_NAMESPACE}/keycloak-dcr-live-proof.json"
keycloak_container_id="$(
  docker ps \
    --filter "label=com.docker.compose.project=${WEAVE_E2E_RUN_NAMESPACE}" \
    --filter "label=com.docker.compose.service=keycloak" \
    --format '{{.ID}}'
)"
[[ "${keycloak_container_id}" =~ ^[0-9a-f]{12,64}$ ]] ||
  fail "the isolated Keycloak runtime container is ambiguous or unavailable"
python3 "${DCR_CONTRACT_PROBE}" \
  --keycloak-base "http://127.0.0.1:${WEAVE_KEYCLOAK_HOST_PORT}" \
  --issuer "${WEAVE_TEST_APP_ISSUER}" \
  --realm weave \
  --runtime-admin-jwk \
    "${WEAVE_TEST_APP_SECRET_ROOT}/agent-runtime/workloads/weave/keycloak/weave-agent-runtime-admin" \
  --run-id "${RUN_ID}" \
  --candidate-commit "${candidate_commit}" \
  --specification-commit "${specification_commit}" \
  --compose-project "${WEAVE_E2E_RUN_NAMESPACE}" \
  --keycloak-container-id "${keycloak_container_id}" \
  --output "${dcr_evidence}"
jq -e \
  --arg candidate_commit "${candidate_commit}" \
  --arg source_candidate_commit "${image_source_commit}" \
  --arg specification_commit "${specification_commit}" \
  --arg compose_project "${WEAVE_E2E_RUN_NAMESPACE}" '
  .schemaVersion == "weave.keycloak-dcr-live-proof/v1" and
  .candidateCommit == $candidate_commit and
  .specificationCommit == $specification_commit and
  .composeProject == $compose_project and
  .runtimeAdminRoles == ["create-client"] and
  .broadAdminRestRejected == true and
  .directAdminRestCreationRejected == true and
  .validRegistration == true and
  .privateKeyJwt == true and
  .effectiveWorkloadRoles == ["weaver-runtime"] and
  .registrationAccessTokenRotation == true and
  .postUpdateFinalStateVerified == true and
  .staleRegistrationAccessTokenRejected == true and
  .crossCellRegistrationAccessTokenRejected == true and
  .crossCellUpdateRejected == true and
  .crossCellHandoffRejected == true and
  .handoffRecoveryAndFinalize == true and
  .handoffResponsesNonCacheable == true and
  .failedCreateRollbackVerified == true and
  .failedUpdateRollbackVerified == true and
  .internalSpiWarningAbsent == true and
  (.negativeCases | length) == 12 and
  .cleanupComplete == true and
  .credentialsIncluded == false and
  .supportSafe == true
' "${dcr_evidence}" >/dev/null ||
  fail "the live Keycloak DCR evidence is incomplete"
! grep -Eqi 'authorization:|bearer |registration_access_token|access_token|client_assertion|private_key' \
  "${dcr_evidence}" ||
  fail "the live Keycloak DCR evidence contains credential material"

log "Running invitation, real Chromium activation, PKCE, WebDAV, ARC, and MCP."
"${REPOSITORY_ROOT}/gradlew" \
  --no-daemon \
  --max-workers=2 \
  "-Dweave.e2e.run-id=${RUN_ID}" \
  "-Dweave.e2e.candidate-commit=${candidate_commit}" \
  "-Dweave.e2e.source-candidate-commit=${image_source_commit}" \
  "-Dweave.e2e.specification-commit=${specification_commit}" \
  "-Dweave.e2e.compose-project=${WEAVE_E2E_RUN_NAMESPACE}" \
  "-Dweave.e2e.tenant-id=${WEAVE_TEST_APP_TENANT_ID}" \
  "-Dweave.e2e.product-origin=${WEAVE_TEST_APP_PRODUCT_ORIGIN}" \
  "-Dweave.e2e.api-origin=${WEAVE_TEST_APP_API_ORIGIN}" \
  "-Dweave.e2e.issuer=${WEAVE_TEST_APP_ISSUER}" \
  "-Dweave.e2e.mailpit-api=${WEAVE_TEST_APP_MAILPIT_API}" \
  "-Dweave.e2e.mcp-endpoint=${WEAVE_TEST_APP_MCP_ENDPOINT}" \
  "-Dweave.e2e.chat-proof-origin=${WEAVE_TEST_APP_CHAT_PROOF_ORIGIN}" \
  "-Dweave.e2e.ca-certificate=${WEAVE_TEST_APP_TLS_ROOT}/ca.pem" \
  "-Dweave.e2e.tls-leaf-certificate=${WEAVE_TEST_APP_TLS_ROOT}/cert.pem" \
  "-Dweave.e2e.hosts-file=${WEAVE_TEST_APP_HOSTS_FILE}" \
  "-Dweave.e2e.bootstrap-owner-token=${WEAVE_TEST_APP_SECRET_ROOT}/identity-bootstrap-owner-token" \
  "-Dweave.e2e.chat-proof-token=${WEAVE_TEST_APP_SECRET_ROOT}/chat-e2e-proof-token" \
  "-Dweave.e2e.workload-credential-root=${WEAVE_TEST_APP_SECRET_ROOT}/agent-runtime/workloads" \
  "-Dweave.e2e.evidence-file=${WEAVE_TEST_APP_EVIDENCE_PATH}" \
  "-Dweave.e2e.persistence-restart-command=${COMPOSE}" \
  "-Dweave.e2e.persistence-restart-evidence=${WEAVE_TEST_APP_RESTART_EVIDENCE_PATH}" \
  "-Dweave.e2e.candidate-manifest-digest=${candidate_manifest_digest}" \
  "-Dweave.e2e.convergence-timeout=${WEAVE_TEST_APP_CONVERGENCE_TIMEOUT:-PT3M}" \
  :weave-product-e2e:productFlow

jq -e \
  --arg candidate_commit "${candidate_commit}" \
  --arg source_candidate_commit "${image_source_commit}" \
  --arg specification_commit "${specification_commit}" \
  --arg candidate_manifest_digest "${candidate_manifest_digest}" \
  --arg compose_project "${WEAVE_E2E_RUN_NAMESPACE}" '
  .schemaVersion == "weave.test-app-product-flow/v1" and
  .candidateCommit == $candidate_commit and
  .sourceCandidateCommit == $source_candidate_commit and
  .specificationCommit == $specification_commit and
  .candidateManifestDigest == $candidate_manifest_digest and
  .composeProject == $compose_project and
  .activation == "keycloak-required-actions-real-chromium" and
  .humanOAuth == "authorization_code_pkce_s256" and
  .workloadOAuth == "client_credentials_private_key_jwt" and
  .mcpTool == "files.search" and
  .serverProjection == "weave-webdav" and
  .canonicalResourceSeen == true and
  .postgresRestartObserved == true and
  .runtimeStateRestartObserved == true and
  .runtimeStateFixtureRestored == true and
  .sameJpaCellAfterRestart == true and
  .sameMcpCellAfterRestart == true and
  (.persistenceRestartEvidenceSha256 | test("^sha256:[0-9a-f]{64}$")) and
  .revocationDenied == true and
  .regrantRestored == true and
  .sameHumanSubjectAfterRegrant == true and
  .samePersonRefAfterRegrant == true and
  .collaboration.repeatCount == 2 and
  (.collaboration.identityRefHashes.author | test("^sha256:[0-9a-f]{64}$")) and
  (.collaboration.identityRefHashes.collaborator | test("^sha256:[0-9a-f]{64}$")) and
  (.collaboration.identityRefHashes.outsider | test("^sha256:[0-9a-f]{64}$")) and
  ([.collaboration.identityRefHashes[]] | unique | length) == 3 and
  ([.collaboration.passes[].pass] | sort) == [1, 2] and
  ([.collaboration.passes[] |
    .freshAuthorizationCodePkce and
    .chatPassed and
    .filesPassed and
    .calendarPassed and
    .homePassed and
    .profilePassed and
    .outsiderDenied and
    .canonicalJpaVerified and
    .directSynapseVerified and
    .callbackReplayVerified and
    .cleanupComplete and
    (.providerCorrelationHash | test("^sha256:[0-9a-f]{64}$"))] | all) and
  (.collaboration.passes[] | select(.pass == 1) |
    .restartContinuityVerified == false) and
  (.collaboration.passes[] | select(.pass == 2) |
    .restartContinuityVerified == true) and
  .credentialsIncluded == false and
  .actionLinksIncluded == false and
  .supportSafe == true
' "${WEAVE_TEST_APP_EVIDENCE_PATH}" >/dev/null ||
  fail "the Fresh product-flow evidence is incomplete"
! grep -Eqi 'protocol/openid-connect/registrations|client_assertion|access_token|refresh_token|password' \
  "${WEAVE_TEST_APP_EVIDENCE_PATH}" ||
  fail "the product-flow evidence contains credential material"

jq -e \
  --arg candidate_commit "${candidate_commit}" \
  --arg specification_commit "${specification_commit}" \
  --arg candidate_manifest_digest "${candidate_manifest_digest}" \
  --arg compose_project "${WEAVE_E2E_RUN_NAMESPACE}" '
  .schemaVersion == "weave.test-app-persistence-restart/v1" and
  .candidateCommit == $candidate_commit and
  .specificationCommit == $specification_commit and
  .candidateManifestDigest == $candidate_manifest_digest and
  .composeProject == $compose_project and
  .postgres.restartObserved == true and
  .postgres.healthyAfterRestart == true and
  .postgres.dependentKeycloakRestartObserved == true and
  .postgres.keycloakHealthyAfterRestart == true and
  .runtimeState.restartObserved == true and
  .runtimeState.healthyAfterRestart == true and
  .runtimeState.sameVolume == true and
  .runtimeState.fixtureRestoredExactly == true and
  .runtimeState.fixtureRemoved == true and
  .credentialsIncluded == false and
  .containsSecretValues == false and
  .supportSafe == true
' "${WEAVE_TEST_APP_RESTART_EVIDENCE_PATH}" >/dev/null ||
  fail "the persistence-restart evidence is incomplete"

jq -e \
  --arg candidate_commit "${candidate_commit}" \
  --arg source_candidate_commit "${image_source_commit}" \
  --arg specification_commit "${specification_commit}" \
  --arg candidate_manifest_digest "${candidate_manifest_digest}" \
  --arg compose_project "${WEAVE_E2E_RUN_NAMESPACE}" '
  .schemaVersion == "weave.test-app-runtime-images/v1" and
  .candidateCommit == $candidate_commit and
  .sourceCandidateCommit == $source_candidate_commit and
  .specificationCommit == $specification_commit and
  .candidateManifestDigest == $candidate_manifest_digest and
  .composeProject == $compose_project and
  (.images | length) == 3 and
  ([.images[].component] | sort) ==
    ["keycloak-runtime", "mcp-server", "server"] and
  ((.manifestBound == false and .realmArtifacts == null) or
    (.manifestBound == true and
      .realmArtifactsVerified == true and
      .realmArtifacts.containsSecrets == false and
      (.realmArtifacts.baselineDigest | test("^sha256:[0-9a-f]{64}$")) and
      (.realmArtifacts.migrationBundleDigest | test("^sha256:[0-9a-f]{64}$")))) and
  (.images[] | select(.component == "keycloak-runtime") |
    .buildEvidenceDigest | test("^sha256:[0-9a-f]{64}$")) and
  ([.images[].matchesCandidate] | all) and
  .credentialsIncluded == false and
  .containsSecretValues == false and
  .supportSafe == true
' "${WEAVE_TEST_APP_RUNTIME_IMAGE_EVIDENCE_PATH}" >/dev/null ||
  fail "the runtime image evidence is incomplete"
if [[ -n "${candidate_manifest_path}" ]]; then
  jq -e '.manifestBound == true and ([.images[].immutableReference] | all)' \
    "${WEAVE_TEST_APP_RUNTIME_IMAGE_EVIDENCE_PATH}" >/dev/null ||
    fail "the manifest-bound runtime image evidence is incomplete"
fi

for evidence in \
  "${WEAVE_TEST_APP_RESTART_EVIDENCE_PATH}" \
  "${WEAVE_TEST_APP_RUNTIME_IMAGE_EVIDENCE_PATH}"; do
  ! grep -Eqi \
    'authorization:|bearer |registration_access_token|access_token|refresh_token|client_assertion|private[_-]?key|BEGIN [A-Z ]+PRIVATE KEY' \
    "${evidence}" ||
    fail "support-safe evidence contains credential material"
done

require_no_pending_registration_operations

log "WEAVE_TEST_APP_LIFECYCLE_RESULT status=passed isolated=true cleanup=armed supportSafe=true"
