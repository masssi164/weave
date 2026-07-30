#!/usr/bin/env bash
# shellcheck shell=bash
# shellcheck disable=SC2016

set -euo pipefail

REPOSITORY_ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../../.." && pwd)"
readonly REPOSITORY_ROOT
readonly LIFECYCLE="${REPOSITORY_ROOT}/gradle/tasks/test-app.sh"
readonly CONTEXT_HELPER="${REPOSITORY_ROOT}/gradle/scripts/prepare_test_app_context.py"
readonly RUNTIME_CLEANUP="${REPOSITORY_ROOT}/gradle/scripts/cleanup_test_app_runtime.py"
readonly SECRET_INITIALIZER="${REPOSITORY_ROOT}/infra/weave-workspace/scripts/init_secrets.py"
readonly GRADLE_TASKS="${REPOSITORY_ROOT}/gradle/tasks/architecture-lifecycle.gradle"
readonly MODULE_BUILD="${REPOSITORY_ROOT}/weave-product-e2e/build.gradle"
readonly MODULE_TASKS="${REPOSITORY_ROOT}/weave-product-e2e/gradle/tasks/product-flow.gradle"
readonly FLOW="${REPOSITORY_ROOT}/weave-product-e2e/src/main/java/com/massimotter/weave/e2e/FreshProductFlow.java"
readonly BROWSER_FLOW="${REPOSITORY_ROOT}/weave-product-e2e/src/main/java/com/massimotter/weave/e2e/OidcBrowserJourney.java"
readonly ACTIVATION_INBOX="${REPOSITORY_ROOT}/weave-product-e2e/src/main/java/com/massimotter/weave/e2e/MailpitActivationInbox.java"
readonly MCP_FLOW="${REPOSITORY_ROOT}/weave-product-e2e/src/main/java/com/massimotter/weave/e2e/WorkloadMcpJourney.java"
readonly CANDIDATE_WORKFLOW="${REPOSITORY_ROOT}/.github/workflows/candidate-images.yml"

fail() { printf 'testApp product-flow contract failed: %s\n' "$*" >&2; exit 1; }
contains() {
  local file="$1" text="$2"
  grep -Fq -- "${text}" "${file}" || fail "${file} omitted ${text}"
}
absent() {
  local file="$1" text="$2"
  ! grep -Fq -- "${text}" "${file}" || fail "${file} contains forbidden ${text}"
}

bash -n "${LIFECYCLE}"
python3 -m py_compile "${CONTEXT_HELPER}" "${RUNTIME_CLEANUP}"

contains "${GRADLE_TASKS}" "tasks.register('testApp', Exec)"
contains "${GRADLE_TASKS}" "commandLine 'bash', 'gradle/tasks/test-app.sh'"
contains "${LIFECYCLE}" 'prepare_test_app_context.py'
contains "${LIFECYCLE}" 'cleanup_test_app_runtime.py'
contains "${LIFECYCLE}" 'CONTEXT_PREPARED=true'
contains "${LIFECYCLE}" '--output-root "${OUTPUT_ROOT}"'
contains "${LIFECYCLE}" 'compose.sh'
contains "${LIFECYCLE}" 'WEAVE_E2E_STACK_SCOPE=isolated'
contains "${LIFECYCLE}" ':weave-product-e2e:productFlow'
contains "${LIFECYCLE}" 'WEAVE_TEST_APP_SERVER_IMAGE must be digest-pinned'
contains "${LIFECYCLE}" 'WEAVE_TEST_APP_MCP_IMAGE must be digest-pinned'
contains "${LIFECYCLE}" 'testApp requires a clean worktree'
contains "${LIFECYCLE}" 'live-stack-failure-diagnostics.sh'
contains "${LIFECYCLE}" 'WEAVE_RESOURCE_PREFIX="${WEAVE_E2E_RUN_NAMESPACE}"'
contains "${LIFECYCLE}" '/failure-diagnostics'
contains "${LIFECYCLE}" 'status --porcelain=v1 --untracked-files=all'
contains "${LIFECYCLE}" 'weave.e2e.candidate-commit'
contains "${LIFECYCLE}" 'weave.e2e.specification-commit'
contains "${LIFECYCLE}" 'weave.e2e.compose-project'
contains "${LIFECYCLE}" 'validate_runtime_image'
contains "${LIFECYCLE}" 'org.opencontainers.image.revision'
contains "${LIFECYCLE}" 'com.massimotter.weave.spec-digest'
contains "${CONTEXT_HELPER}" 'teardown-evidence.json'
contains "${SECRET_INITIALIZER}" 'identity-bootstrap-owner-token'
contains "${CONTEXT_HELPER}" 'weave-test-app-evidence.json'
contains "${RUNTIME_CLEANUP}" 'invalid isolated namespace'
contains "${RUNTIME_CLEANUP}" 'for generated_input in ("test.env", "hosts")'
absent "${LIFECYCLE}" 'reset-password'
absent "${LIFECYCLE}" 'isolated-e2e-identities.sh'
absent "${LIFECYCLE}" 'test-users.json'
absent "${LIFECYCLE}" 'WEAVE_TEST_USERS_FILE'
absent "${LIFECYCLE}" 'WEAVE_E2E_AUTHOR_PASSWORD'
absent "${LIFECYCLE}" 'WEAVE_E2E_COLLABORATOR_PASSWORD'
absent "${LIFECYCLE}" 'WEAVE_E2E_OUTSIDER_PASSWORD'

contains "${MODULE_BUILD}" 'apply from: "${projectDir}/gradle/tasks/product-flow.gradle"'
contains "${MODULE_TASKS}" "args 'install', '--with-deps', 'chromium'"
contains "${FLOW}" '/api/bootstrap/owner-invitation'
contains "${FLOW}" '/api/v1/identity/session/reconcile'
contains "${FLOW}" '"access_updated"'
contains "${FLOW}" 'organizationGroups(claims)'
absent "${FLOW}" 'claims.path("groups")'
contains "${FLOW}" 'authorization_code_pkce_s256'
contains "${FLOW}" 'client_credentials_private_key_jwt'
contains "${FLOW}" 'awaitEmailVerificationLink'
contains "${FLOW}" 'credentialsIncluded'
contains "${FLOW}" 'actionLinksIncluded'
contains "${FLOW}" 'candidateCommit'
contains "${FLOW}" 'specificationCommit'
contains "${FLOW}" 'composeProject'
contains "${BROWSER_FLOW}" '--ignore-certificate-errors-spki-list='
contains "${BROWSER_FLOW}" '--host-resolver-rules='
contains "${BROWSER_FLOW}" 'isEmailVerificationRequiredAction'
contains "${BROWSER_FLOW}" '"email-verification"'
contains "${BROWSER_FLOW}" '.pf-m-danger'
absent "${BROWSER_FLOW}" "\"[role='alert']"
absent "${BROWSER_FLOW}" '.kc-feedback-text'
contains "${ACTIVATION_INBOX}" '/login-actions/action-token'
contains "${ACTIVATION_INBOX}" '"key"'
absent "${BROWSER_FLOW}" 'setIgnoreHTTPSErrors'
contains "${MCP_FLOW}" 'private_key_jwt'
contains "${MCP_FLOW}" '"files.search"'
contains "${MCP_FLOW}" '"weave://files/"'
contains "${MCP_FLOW}" '"/remote.php/dav"'
absent "${FLOW}" 'org.springframework'
absent "${FLOW}" 'jakarta.persistence'
absent "${MCP_FLOW}" 'org.springframework'
absent "${MCP_FLOW}" 'jakarta.persistence'

contains "${CANDIDATE_WORKFLOW}" 'fresh-product-proof:'
contains "${CANDIDATE_WORKFLOW}" 'needs: build-candidate'
contains "${CANDIDATE_WORKFLOW}" 'weave-server@${{ needs.build-candidate.outputs.server_digest }}'
contains "${CANDIDATE_WORKFLOW}" 'weave-mcp-server@${{ needs.build-candidate.outputs.mcp_digest }}'
contains "${CANDIDATE_WORKFLOW}" 'run: ./gradlew --no-daemon testApp'

printf 'testApp product-flow contract tests passed\n'
