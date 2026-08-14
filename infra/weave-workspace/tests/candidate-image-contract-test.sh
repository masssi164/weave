#!/usr/bin/env bash
# shellcheck shell=bash
# shellcheck disable=SC2016

set -euo pipefail

REPOSITORY_ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../../.." && pwd)"
readonly REPOSITORY_ROOT
readonly SERVER_IMAGE="${REPOSITORY_ROOT}/server/Dockerfile"
readonly MCP_IMAGE="${REPOSITORY_ROOT}/weave-mcp-server/Dockerfile"
readonly KEYCLOAK_RUNTIME_IMAGE="${REPOSITORY_ROOT}/infra/weave-workspace/keycloak/Dockerfile.runtime"
readonly WORKFLOW="${REPOSITORY_ROOT}/.github/workflows/candidate-images.yml"
readonly CI_WORKFLOW="${REPOSITORY_ROOT}/.github/workflows/ci.yml"
readonly HUMAN_TESTING_WORKFLOW="${REPOSITORY_ROOT}/.github/workflows/human-testing-readiness.yml"
readonly LIVE_STACK_WORKFLOW="${REPOSITORY_ROOT}/.github/workflows/live-stack-e2e.yml"
readonly MAIN_PROMOTION_WORKFLOW="${REPOSITORY_ROOT}/.github/workflows/main-promotion-gate.yml"
readonly TEST_STACK_WORKFLOW="${REPOSITORY_ROOT}/.github/workflows/test-stack-deploy.yml"
readonly DOCTOR_TASK="${REPOSITORY_ROOT}/gradle/tasks/ci-lifecycle.gradle"
readonly RENDERER="${REPOSITORY_ROOT}/infra/weave-workspace/scripts/render_config.py"
readonly COMPOSE="${REPOSITORY_ROOT}/infra/weave-workspace/compose.yaml"
readonly MIGRATION_VALIDATOR="${REPOSITORY_ROOT}/infra/weave-workspace/scripts/keycloak_migration.py"
readonly REALM_EVIDENCE="${REPOSITORY_ROOT}/infra/weave-workspace/scripts/keycloak_realm_evidence.py"

fail() {
  printf 'candidate image contract failed: %s\n' "$*" >&2
  exit 1
}

contains() {
  local file="$1"
  local text="$2"
  grep -Fq -- "${text}" "${file}" || fail "${file} omitted ${text}"
}

reject() {
  local file="$1"
  local text="$2"
  ! grep -Fq -- "${text}" "${file}" || fail "${file} retains retired prerequisite ${text}"
}

for image in "${SERVER_IMAGE}" "${MCP_IMAGE}" "${KEYCLOAK_RUNTIME_IMAGE}"; do
  for label in org.opencontainers.image.title org.opencontainers.image.source org.opencontainers.image.revision org.opencontainers.image.version org.opencontainers.image.created org.opencontainers.image.licenses org.opencontainers.image.vendor com.massimotter.weave.spec-digest com.massimotter.weave.module com.massimotter.weave.runtime-user com.massimotter.weave.dependency-platform com.massimotter.weave.sbom-reference com.massimotter.weave.provenance-reference; do
    contains "${image}" "${label}"
  done
done

contains "${SERVER_IMAGE}" 'USER 10001:10001'
contains "${SERVER_IMAGE}" 'COPY rust/matrix-protocol/Cargo.toml rust/matrix-protocol/Cargo.toml'
contains "${SERVER_IMAGE}" 'COPY rust/matrix-protocol/src rust/matrix-protocol/src'
contains "${SERVER_IMAGE}" 'COPY rust/matrix-client/Cargo.toml rust/matrix-client/Cargo.toml'
contains "${SERVER_IMAGE}" 'COPY rust/matrix-client/src rust/matrix-client/src'
contains "${SERVER_IMAGE}" 'cargo build --release -p weave-matrix-protocol --features jni'
contains "${SERVER_IMAGE}" 'libweave_matrix_protocol.so'
contains "${SERVER_IMAGE}" 'WEAVE_MATRIX_PROTOCOL_LIBRARY_PATH'
reject "${SERVER_IMAGE}" 'weave-matrix-core'
reject "${SERVER_IMAGE}" 'libweave_matrix_core.so'
contains "${MCP_IMAGE}" 'USER 10001:10001'
contains "${KEYCLOAK_RUNTIME_IMAGE}" 'USER 1000:0'
contains "${KEYCLOAK_RUNTIME_IMAGE}" 'keycloak-services-26.7.1.jar'
reject "${KEYCLOAK_RUNTIME_IMAGE}" 'keycloak-services-26.7.0.jar'

if [[ -f "${WORKFLOW}" ]]; then
  contains "${WORKFLOW}" 'name: Candidate Cut'
  contains "${WORKFLOW}" 'candidate_sha:'
  contains "${WORKFLOW}" 'environment: candidate-cut'
  contains "${WORKFLOW}" 'provenance: mode=max'
  contains "${WORKFLOW}" 'sbom: true'
  contains "${WORKFLOW}" 'name: Stage flat candidate evidence artifact'
  contains "${WORKFLOW}" 'artifact_root=build/candidate/upload'
  contains "${WORKFLOW}" 'install -m 0644 "$source" "$artifact_root/${source##*/}"'
  contains "${WORKFLOW}" 'path: build/candidate/upload'
  contains "${WORKFLOW}" 'WEAVE_TEST_APP_CANDIDATE_MANIFEST: ${{ github.workspace }}/weave/build/candidate-input/candidate-manifest.json'
  contains "${WORKFLOW}" 'infra/weave-workspace/keycloak/migration-definition.json'
  contains "${WORKFLOW}" '.realmDefinition.semanticRealmSourceDigest'
  contains "${WORKFLOW}" '.realmDefinition.migrationDefinitionDigest'
  reject "${WORKFLOW}" 'realmBaselineArtifact='
  reject "${WORKFLOW}" 'realmMigrationBundleArtifact='
  reject "${WORKFLOW}" 'Dockerfile.identity-ops'
fi
reject "${LIVE_STACK_WORKFLOW}" 'WEAVE_TEST_APP_IDENTITY_OPS_IMAGE'

contains "${RENDERER}" '"schemaVersion": "weave.compose-render.v3"'
contains "${RENDERER}" '"deploymentArtifacts": {'
reject "${RENDERER}" '_backend_env'
reject "${RENDERER}" '_mcp_env'
reject "${RENDERER}" 'backend/public.env'
reject "${RENDERER}" 'backend/host.env'
reject "${RENDERER}" 'mcp/public.env'
reject "${RENDERER}" 'mcp/host.env'
reject "${RENDERER}" 'WEAVE_OIDC_ISSUER_URI'
reject "${RENDERER}" 'WEAVE_MCP_AUTHORIZATION_SERVER'
reject "${RENDERER}" '"realmArtifacts": {'
reject "${RENDERER}" '"schemaVersion": "weave.compose-render.v2"'

contains "${COMPOSE}" 'SPRING_PROFILES_ACTIVE: ${WEAVE_ENVIRONMENT:?environment required}'
contains "${COMPOSE}" 'weave.identity.invitations.keycloak.private-key-jwt-audience: ${WEAVE_AUTH_URL:?auth URL required}/realms/weave'
contains "${COMPOSE}" 'weave.identity.invitations.keycloak.organization-id: 8f771be4-f526-5bef-97dc-00c8e2fa383d'
contains "${COMPOSE}" 'weave.agent-runtime.workload-identity.keycloak-organization-id: 8f771be4-f526-5bef-97dc-00c8e2fa383d'
contains "${COMPOSE}" 'weave.agent-runtime.workload-identity.issuer: ${WEAVE_AUTH_URL:?auth URL required}/realms/weave'
contains "${COMPOSE}" 'weave.security.required-audience: ${WEAVE_API_URL:?API URL required}'
contains "${COMPOSE}" 'weave.mcp.resource-uri: ${WEAVE_API_ORIGIN:?API origin required}/mcp'
contains "${COMPOSE}" 'weave.mcp.authorization-server: ${WEAVE_AUTH_URL:?auth URL required}/realms/weave'
reject "${COMPOSE}" '/backend/public.env'
reject "${COMPOSE}" '/mcp/public.env'

for profile in dev dogfood e2e prod; do
  server_profile="${REPOSITORY_ROOT}/server/src/main/resources/application-${profile}.yml"
  mcp_profile="${REPOSITORY_ROOT}/weave-mcp-server/src/main/resources/application-${profile}.yml"
  contains "${server_profile}" "on-profile: ${profile}"
  contains "${mcp_profile}" "on-profile: ${profile}"
  reject "${mcp_profile}" 'datasource:'
  reject "${mcp_profile}" 'jpa:'
done
contains "${REPOSITORY_ROOT}/server/src/main/resources/application-dogfood.yml" 'issuer-uri: https://auth.weave.test/realms/weave'
contains "${REPOSITORY_ROOT}/server/src/main/resources/application-dogfood.yml" 'jwk-set-uri: http://keycloak:8080/realms/weave/protocol/openid-connect/certs'
contains "${REPOSITORY_ROOT}/weave-mcp-server/src/main/resources/application-dogfood.yml" 'authorization-server: https://auth.weave.test/realms/weave'
contains "${REPOSITORY_ROOT}/weave-mcp-server/src/main/resources/application-dogfood.yml" 'token-uri: http://keycloak:8080/realms/weave/protocol/openid-connect/token'

contains "${MIGRATION_VALIDATOR}" 'rendered.get("deploymentArtifacts")'
contains "${MIGRATION_VALIDATOR}" 'rendered.get("schemaVersion") != "weave.compose-render.v3"'
contains "${MIGRATION_VALIDATOR}" '"realmArtifacts" in rendered'
contains "${REALM_EVIDENCE}" 'manifest.get("schemaVersion") != "weave.compose-render.v3"'
contains "${REALM_EVIDENCE}" 'manifest.get("deploymentArtifacts")'
contains "${REALM_EVIDENCE}" '"realmArtifacts" in manifest'

contains "${CI_WORKFLOW}" 'group: ci-${{ github.workflow }}-${{ github.event.pull_request.number || github.ref }}'
contains "${CI_WORKFLOW}" "cancel-in-progress: \${{ github.event_name == 'pull_request' }}"
contains "${CI_WORKFLOW}" 'timeout-minutes: 60'
contains "${CI_WORKFLOW}" "github.event.action != 'labeled'"
contains "${CI_WORKFLOW}" "github.event.action != 'unlabeled'"
reject "${CI_WORKFLOW}" '- ready_for_review'

if [[ -f "${WORKFLOW}" ]]; then
  reject "${WORKFLOW}" 'opentofu/setup-opentofu'
fi
reject "${DOCTOR_TASK}" "checkCommand('tofu'"
if [[ -f "${WORKFLOW}" ]] && grep -Fq ':latest' "${WORKFLOW}"; then
  fail "${WORKFLOW} contains a mutable latest tag"
fi

printf 'candidate image contract tests passed\n'
