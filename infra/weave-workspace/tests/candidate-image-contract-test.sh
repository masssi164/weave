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
readonly FRESH_START_RECREATE="${REPOSITORY_ROOT}/infra/weave-workspace/fresh-start-recreate.py"
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

for image in "${SERVER_IMAGE}" "${MCP_IMAGE}" "${KEYCLOAK_RUNTIME_IMAGE}"; do
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
contains "${KEYCLOAK_RUNTIME_IMAGE}" 'USER 1000:0'
contains "${KEYCLOAK_RUNTIME_IMAGE}" 'com.massimotter.weave.module="keycloak-runtime"'
contains "${KEYCLOAK_RUNTIME_IMAGE}" 'com.massimotter.weave.provider-id="weave-workload-client-registration-enforcer"'
contains "${KEYCLOAK_RUNTIME_IMAGE}" 'ARG WEAVE_KEYCLOAK_BUILD_EVIDENCE_DIGEST'
contains "${KEYCLOAK_RUNTIME_IMAGE}" 'com.massimotter.weave.keycloak-build-evidence-digest="${WEAVE_KEYCLOAK_BUILD_EVIDENCE_DIGEST}"'

contains "${WORKFLOW}" 'name: Candidate Cut'
contains "${WORKFLOW}" 'run-name: Candidate Cut ${{ inputs.candidate_sha }}'
contains "${WORKFLOW}" 'candidate_sha:'
contains "${WORKFLOW}" 'group: candidate-cut-${{ inputs.candidate_sha }}'
contains "${WORKFLOW}" 'Verify selected candidate belongs to protected dev'
contains "${WORKFLOW}" '[[ "$GITHUB_REF" == "refs/heads/dev" ]]'
contains "${WORKFLOW}" '[[ "$WEAVE_CANDIDATE_COMMIT" =~ ^[0-9a-f]{40}$ ]]'
contains "${WORKFLOW}" 'git merge-base --is-ancestor'
contains "${WORKFLOW}" 'environment: candidate-cut'
contains "${WORKFLOW}" 'candidate_sha: ${{ steps.source.outputs.candidate_sha }}'
contains "${WORKFLOW}" 'WEAVE_CANDIDATE_COMMIT: ${{ needs.verify-source.outputs.candidate_sha }}'
if sed -n '1,12p' "${WORKFLOW}" | grep -Fq -- '  push:'; then
  fail "${WORKFLOW} must not publish candidates from a push trigger"
fi
reject "${WORKFLOW}" 'github.sha'
reject "${WORKFLOW}" 'GITHUB_SHA'
contains "${WORKFLOW}" 'tag_sha=sha-$WEAVE_CANDIDATE_COMMIT'
contains "${WORKFLOW}" 'tag_run=candidate-$GITHUB_RUN_ID-$GITHUB_RUN_ATTEMPT'
contains "${WORKFLOW}" 'provenance: mode=max'
contains "${WORKFLOW}" 'sbom: true'
contains "${WORKFLOW}" 'Verify published OCI runtime metadata'
contains "${WORKFLOW}" 'validate_published_image'
contains "${WORKFLOW}" 'docker image inspect'
contains "${WORKFLOW}" 'com.massimotter.weave.runtime-user'
contains "${WORKFLOW}" 'WEAVE_TEST_APP_KEYCLOAK_IMAGE: ghcr.io/${{ github.repository_owner }}/weave-keycloak-runtime@${{ needs.build-candidate.outputs.keycloak_runtime_digest }}'
contains "${WORKFLOW}" 'build/candidate/keycloak-runtime-build.json'
contains "${WORKFLOW}" 'verify_keycloak_build_evidence.py'
contains "${WORKFLOW}" 'WEAVE_KEYCLOAK_BUILD_EVIDENCE_DIGEST=${{ steps.keycloak_runtime_prepare.outputs.build_evidence_digest }}'
contains "${WORKFLOW}" 'com.massimotter.weave.keycloak-build-evidence-digest'
contains "${WORKFLOW}" 'keycloakRuntimeBuildEvidenceDigest=${{ steps.keycloak_runtime_prepare.outputs.build_evidence_digest }}'
contains "${WORKFLOW}" 'realmBaselineArtifact=$realm_baseline'
contains "${WORKFLOW}" 'realmMigrationBundleArtifact=$realm_migrations'
contains "${WORKFLOW}" 'build/candidate/keycloak/import/weave-realm.json'
contains "${WORKFLOW}" 'build/candidate/keycloak/migrations/manifest.json'
contains "${WORKFLOW}" '[[ -f "$realm_baseline" && ! -L "$realm_baseline"'
reject "${WORKFLOW}" 'Dockerfile.identity-ops'
reject "${WORKFLOW}" 'identity_ops_digest'
reject "${LIVE_STACK_WORKFLOW}" 'WEAVE_TEST_APP_IDENTITY_OPS_IMAGE'
reject "${FRESH_START_RECREATE}" 'candidate, "identity-ops"'
contains "${TEST_STACK_WORKFLOW}" 'actual_baseline="sha256:$(shasum -a 256 "$baseline"'
contains "${TEST_STACK_WORKFLOW}" 'actual_migrations="sha256:$(shasum -a 256 "$migrations"'
[[ "$(grep -Fc 'ssh-key: ${{ secrets.WEAVE_SPECS_DEPLOY_KEY }}' "${WORKFLOW}")" -eq 2 ]] ||
  fail "${WORKFLOW} must authenticate both specification-corpus checkouts through the deploy key"
contains "${WORKFLOW}" "printf '/canonical-weave-specs/\\n' >> .git/info/exclude"

for spec_workflow in \
  "${WORKFLOW}" \
  "${CI_WORKFLOW}" \
  "${HUMAN_TESTING_WORKFLOW}" \
  "${LIVE_STACK_WORKFLOW}" \
  "${MAIN_PROMOTION_WORKFLOW}" \
  "${TEST_STACK_WORKFLOW}"; do
  contains "${spec_workflow}" 'spec_commit="$(' # fail-closed assignment
  contains "${spec_workflow}" '.specCorpus.gitCommit | select(type == "string" and test("^[0-9a-f]{40}$"))'
  contains "${spec_workflow}" "printf 'commit=%s\\n'"
  reject "${spec_workflow}" 'echo "commit=$(jq'
  reject "${spec_workflow}" 'test(\"'
done
[[ "$(grep -Fc 'spec_commit="$(' "${WORKFLOW}")" -eq 2 ]] ||
  fail "${WORKFLOW} must resolve the pinned specification fail-closed in both candidate jobs"
[[ "$(grep -Fc 'spec_commit="$(' "${CI_WORKFLOW}")" -eq 1 ]] ||
  fail "${CI_WORKFLOW} must resolve the pinned specification fail-closed in the canonical Gradle job"

contains "${CI_WORKFLOW}" 'group: ci-${{ github.workflow }}-${{ github.event.pull_request.number || github.ref }}'
contains "${CI_WORKFLOW}" "cancel-in-progress: \${{ github.event_name == 'pull_request' }}"
reject "${CI_WORKFLOW}" "&& 'labels' || 'full'"
reject "${CI_WORKFLOW}" "github.event.action != 'labeled'"

reject "${WORKFLOW}" 'opentofu/setup-opentofu'
reject "${WORKFLOW}" 'tofu_version'
reject "${DOCTOR_TASK}" "checkCommand('tofu'"
if grep -Fq ':latest' "${WORKFLOW}"; then
  fail "${WORKFLOW} contains a mutable latest tag"
fi

printf 'candidate image contract tests passed\n'
