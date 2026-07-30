#!/usr/bin/env bash
# shellcheck shell=bash
# shellcheck disable=SC2016

set -euo pipefail

REPOSITORY_ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../../.." && pwd)"
readonly REPOSITORY_ROOT
readonly SERVER_IMAGE="${REPOSITORY_ROOT}/server/Dockerfile"
readonly MCP_IMAGE="${REPOSITORY_ROOT}/weave-mcp-server/Dockerfile"
readonly IDENTITY_OPS_IMAGE="${REPOSITORY_ROOT}/infra/weave-workspace/keycloak/Dockerfile.identity-ops"
readonly KEYCLOAK_RUNTIME_IMAGE="${REPOSITORY_ROOT}/infra/weave-workspace/keycloak/Dockerfile.runtime"
readonly WORKFLOW="${REPOSITORY_ROOT}/.github/workflows/candidate-images.yml"
readonly DOCTOR_TASK="${REPOSITORY_ROOT}/gradle/tasks/ci-lifecycle.gradle"

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

for image in "${SERVER_IMAGE}" "${MCP_IMAGE}" "${IDENTITY_OPS_IMAGE}" "${KEYCLOAK_RUNTIME_IMAGE}"; do
  for label in \
    org.opencontainers.image.title \
    org.opencontainers.image.source \
    org.opencontainers.image.revision \
    org.opencontainers.image.version \
    org.opencontainers.image.created \
    org.opencontainers.image.licenses \
    org.opencontainers.image.vendor \
    com.massimotter.weave.spec-digest \
    com.massimotter.weave.module \
    com.massimotter.weave.runtime-user \
    com.massimotter.weave.dependency-platform \
    com.massimotter.weave.sbom-reference \
    com.massimotter.weave.provenance-reference; do
    contains "${image}" "${label}"
  done
done

contains "${SERVER_IMAGE}" 'USER 10001:10001'
contains "${SERVER_IMAGE}" 'com.massimotter.weave.module="server"'
contains "${MCP_IMAGE}" 'USER 10001:10001'
contains "${MCP_IMAGE}" 'com.massimotter.weave.module="weave-mcp-server"'
contains "${IDENTITY_OPS_IMAGE}" 'USER 1000:1000'
contains "${IDENTITY_OPS_IMAGE}" 'com.massimotter.weave.module="identity-ops"'
contains "${KEYCLOAK_RUNTIME_IMAGE}" 'USER 1000:0'
contains "${KEYCLOAK_RUNTIME_IMAGE}" 'com.massimotter.weave.module="keycloak-runtime"'
contains "${KEYCLOAK_RUNTIME_IMAGE}" 'com.massimotter.weave.provider-id="weave-workload-client-registration-enforcer"'

contains "${WORKFLOW}" 'tag_sha=sha-$GITHUB_SHA'
contains "${WORKFLOW}" 'tag_run=candidate-$GITHUB_RUN_ID-$GITHUB_RUN_ATTEMPT'
contains "${WORKFLOW}" 'provenance: mode=max'
contains "${WORKFLOW}" 'sbom: true'
contains "${WORKFLOW}" 'Verify published OCI runtime metadata'
contains "${WORKFLOW}" 'validate_published_image'
contains "${WORKFLOW}" 'docker image inspect'
contains "${WORKFLOW}" 'com.massimotter.weave.runtime-user'
contains "${WORKFLOW}" 'WEAVE_TEST_APP_KEYCLOAK_IMAGE: ghcr.io/${{ github.repository_owner }}/weave-keycloak-runtime@${{ needs.build-candidate.outputs.keycloak_runtime_digest }}'
contains "${WORKFLOW}" 'context: infra/weave-workspace/keycloak'
contains "${WORKFLOW}" 'file: infra/weave-workspace/keycloak/Dockerfile.identity-ops'
[[ "$(grep -Fc 'ssh-key: ${{ secrets.WEAVE_SPECS_DEPLOY_KEY }}' "${WORKFLOW}")" -eq 2 ]] ||
  fail "${WORKFLOW} must authenticate both specification-corpus checkouts through the deploy key"
reject "${WORKFLOW}" 'opentofu/setup-opentofu'
reject "${WORKFLOW}" 'tofu_version'
reject "${DOCTOR_TASK}" "checkCommand('tofu'"
if grep -Fq ':latest' "${WORKFLOW}"; then
  fail "${WORKFLOW} contains a mutable latest tag"
fi

printf 'candidate image contract tests passed\n'
