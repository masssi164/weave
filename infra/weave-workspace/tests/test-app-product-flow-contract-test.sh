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
readonly DCR_CONTRACT_PROBE="${REPOSITORY_ROOT}/infra/weave-workspace/scripts/verify_keycloak_dcr_contract.py"
readonly DCR_CONTRACT_PROBE_TEST="${REPOSITORY_ROOT}/infra/weave-workspace/tests/verify_keycloak_dcr_contract_test.py"
readonly COMPOSE_RUNTIME="${REPOSITORY_ROOT}/infra/weave-workspace/scripts/compose_runtime.py"
readonly BOUNDED_PROCESS="${REPOSITORY_ROOT}/infra/weave-workspace/scripts/bounded_process.py"
readonly RUNTIME_IMAGE_EVIDENCE="${REPOSITORY_ROOT}/gradle/scripts/write_test_app_runtime_image_evidence.py"
readonly RUNTIME_IMAGE_EVIDENCE_TEST="${REPOSITORY_ROOT}/gradle/scripts/write_test_app_runtime_image_evidence_test.py"
readonly GRADLE_TASKS="${REPOSITORY_ROOT}/gradle/tasks/architecture-lifecycle.gradle"
readonly MODULE_BUILD="${REPOSITORY_ROOT}/weave-product-e2e/build.gradle"
readonly MODULE_TASKS="${REPOSITORY_ROOT}/weave-product-e2e/gradle/tasks/product-flow.gradle"
readonly FLOW="${REPOSITORY_ROOT}/weave-product-e2e/src/main/java/com/massimotter/weave/e2e/FreshProductFlow.java"
readonly BROWSER_FLOW="${REPOSITORY_ROOT}/weave-product-e2e/src/main/java/com/massimotter/weave/e2e/OidcBrowserJourney.java"
readonly ACTIVATION_INBOX="${REPOSITORY_ROOT}/weave-product-e2e/src/main/java/com/massimotter/weave/e2e/MailpitActivationInbox.java"
readonly MCP_FLOW="${REPOSITORY_ROOT}/weave-product-e2e/src/main/java/com/massimotter/weave/e2e/WorkloadMcpJourney.java"
readonly RESTART_FLOW="${REPOSITORY_ROOT}/weave-product-e2e/src/main/java/com/massimotter/weave/e2e/PersistenceRestartJourney.java"
readonly CANDIDATE_WORKFLOW="${REPOSITORY_ROOT}/.github/workflows/candidate-images.yml"
readonly COMPOSE_FILE="${REPOSITORY_ROOT}/infra/weave-workspace/compose.yaml"

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
python3 -m py_compile \
  "${CONTEXT_HELPER}" \
  "${RUNTIME_CLEANUP}" \
  "${DCR_CONTRACT_PROBE}" \
  "${COMPOSE_RUNTIME}" \
  "${BOUNDED_PROCESS}" \
  "${RUNTIME_IMAGE_EVIDENCE}"
python3 -m unittest "${DCR_CONTRACT_PROBE_TEST}"
python3 -m unittest "${RUNTIME_IMAGE_EVIDENCE_TEST}"

CONTEXT_TEST_OUTPUT_ROOT="$(mktemp -d "${TMPDIR:-/tmp}/weave-testapp-context-contract.XXXXXX")"
CONTEXT_TEST_NAMESPACE=""
cleanup_context_contract() {
  local status="$?" cleanup_status=0
  set +e
  if [[ -n "${CONTEXT_TEST_NAMESPACE}" ]]; then
    python3 "${RUNTIME_CLEANUP}" \
      --repository-root "${REPOSITORY_ROOT}" \
      --output-root "${CONTEXT_TEST_OUTPUT_ROOT}" \
      --namespace "${CONTEXT_TEST_NAMESPACE}" >/dev/null || cleanup_status=$?
  fi
  rm -rf -- "${CONTEXT_TEST_OUTPUT_ROOT}" || cleanup_status=$?
  if ((status != 0)); then
    return "${status}"
  fi
  return "${cleanup_status}"
}
trap cleanup_context_contract EXIT
context_assignments="$(
  python3 "${CONTEXT_HELPER}" \
    --repository-root "${REPOSITORY_ROOT}" \
    --output-root "${CONTEXT_TEST_OUTPUT_ROOT}" \
    --run-id contract-tenant-42
)"
eval "${context_assignments}"
CONTEXT_TEST_NAMESPACE="${WEAVE_E2E_RUN_NAMESPACE}"
grep -Fxq -- \
  "WEAVE_MAILPIT_URL=https://mail.weave.test:${WEAVE_PROXY_HTTPS_HOST_PORT}" \
  "${WEAVE_ENV_FILE}" || fail "isolated context omitted the Mailpit gateway URL"
grep -Fq -- "mail.weave.test" "${WEAVE_TEST_APP_HOSTS_FILE}" ||
  fail "isolated context omitted the Mailpit gateway host mapping"
[[ "${WEAVE_TEST_APP_MAILPIT_ORIGIN}" == "https://mail.weave.test:${WEAVE_PROXY_HTTPS_HOST_PORT}" ]] ||
  fail "isolated context omitted the exact Mailpit gateway origin"
python3 - "${WEAVE_TEST_APP_CONTEXT_MEMBERSHIPS}" "${WEAVE_TEST_APP_TENANT_ID}" <<'PY'
import json
import sys
from pathlib import Path

seed = json.loads(Path(sys.argv[1]).read_text(encoding="utf-8"))
tenant = sys.argv[2]
memberships = seed.get("memberships")
if not isinstance(memberships, list) or not memberships:
    raise SystemExit("testApp context contract omitted memberships")
if {membership.get("tenantId") for membership in memberships} != {tenant}:
    raise SystemExit("testApp tenant does not match every context membership")
PY

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
contains "${LIFECYCLE}" 'require_free_disk_space'
contains "${LIFECYCLE}" 'local minimum_kib=8388608'
contains "${LIFECYCLE}" 'before image build and resource creation'
contains "${LIFECYCLE}" 'live-stack-failure-diagnostics.sh'
contains "${LIFECYCLE}" 'WEAVE_LIVE_STACK_DIAGNOSTICS_TIMEOUT_SECONDS=30'
diagnostics_line="$(grep -nF 'bash "${FAILURE_DIAGNOSTICS}"' "${LIFECYCLE}" | cut -d: -f1)"
teardown_line="$(grep -nF 'bash "${TEARDOWN}" e2e' "${LIFECYCLE}" | cut -d: -f1)"
[[ "${diagnostics_line}" =~ ^[0-9]+$ && "${teardown_line}" =~ ^[0-9]+$ &&
   ${diagnostics_line} -lt ${teardown_line} ]] ||
  fail "bounded diagnostics must remain immediately before exact teardown"
contains "${LIFECYCLE}" 'verify_keycloak_dcr_contract.py'
contains "${LIFECYCLE}" 'keycloak-dcr-live-proof.json'
contains "${LIFECYCLE}" 'WEAVE_TEST_APP_RESTART_EVIDENCE_PATH'
contains "${LIFECYCLE}" 'WEAVE_TEST_APP_RUNTIME_IMAGE_EVIDENCE_PATH'
contains "${LIFECYCLE}" 'WEAVE_TEST_APP_CANDIDATE_MANIFEST'
contains "${LIFECYCLE}" 'candidate-manifest-check.py'
contains "${LIFECYCLE}" '.postgresRestartObserved == true'
contains "${LIFECYCLE}" '.runtimeStateRestartObserved == true'
contains "${LIFECYCLE}" '.postUpdateFinalStateVerified == true'
contains "${LIFECYCLE}" '.directAdminRestCreationRejected == true'
contains "${LIFECYCLE}" '.failedCreateRollbackVerified == true'
contains "${LIFECYCLE}" '.failedUpdateRollbackVerified == true'
contains "${LIFECYCLE}" '.crossCellHandoffRejected == true'
contains "${LIFECYCLE}" '.handoffRecoveryAndFinalize == true'
contains "${LIFECYCLE}" '.handoffResponsesNonCacheable == true'
contains "${LIFECYCLE}" '.internalSpiWarningAbsent == true'
contains "${LIFECYCLE}" 'require_no_pending_registration_operations'
contains "${LIFECYCLE}" 'a pending registration authority operation blocks isolated proof'
contains "${LIFECYCLE}" 'label=com.docker.compose.service=keycloak'
contains "${LIFECYCLE}" 'WEAVE_RESOURCE_PREFIX="${WEAVE_E2E_RUN_NAMESPACE}"'
contains "${LIFECYCLE}" '/failure-diagnostics'
contains "${LIFECYCLE}" 'status --porcelain=v1 --untracked-files=all'
contains "${LIFECYCLE}" 'weave.e2e.candidate-commit'
contains "${LIFECYCLE}" 'weave.e2e.specification-commit'
contains "${LIFECYCLE}" 'weave.e2e.compose-project'
contains "${LIFECYCLE}" 'weave.e2e.tenant-id'
contains "${CONTEXT_HELPER}" 'WEAVE_TEST_APP_TENANT_ID'
contains "${LIFECYCLE}" 'validate_runtime_image'
contains "${LIFECYCLE}" 'org.opencontainers.image.revision'
contains "${LIFECYCLE}" 'com.massimotter.weave.spec-digest'
contains "${CONTEXT_HELPER}" 'teardown-evidence.json'
contains "${SECRET_INITIALIZER}" 'identity-bootstrap-owner-token'
contains "${SECRET_INITIALIZER}" 'chat-e2e-proof-token'
contains "${CONTEXT_HELPER}" 'weave.context-authorization-seed/v1'
contains "${CONTEXT_HELPER}" 'weave-test-app-evidence.json'
contains "${CONTEXT_HELPER}" 'persistence-restart-evidence.json'
contains "${CONTEXT_HELPER}" 'runtime-image-evidence.json'
contains "${COMPOSE_FILE}" 'native-files-data:'
contains "${COMPOSE_FILE}" 'name: ${WEAVE_NATIVE_FILES_DATA_VOLUME:-weave_native_files_data}'
contains "${COMPOSE_FILE}" '      - DAC_OVERRIDE'
contains "${COMPOSE_FILE}" '      - FOWNER'
contains "${COMPOSE_FILE}" 'runtime-state-data:'
contains "${COMPOSE_FILE}" 'name: ${WEAVE_RUNTIME_STATE_VOLUME:-weave_runtime_state_data}'
absent "${COMPOSE_FILE}" 'WEAVE_RUNTIME_STATE_DATA_VOLUME'
contains "${RUNTIME_CLEANUP}" 'invalid isolated namespace'
contains "${RUNTIME_CLEANUP}" 'for generated_input in ("e2e.env", "hosts")'
absent "${LIFECYCLE}" 'reset-password'
absent "${LIFECYCLE}" 'isolated-e2e-identities.sh'
absent "${LIFECYCLE}" 'test-users.json'
absent "${LIFECYCLE}" 'WEAVE_TEST_USERS_FILE'
absent "${LIFECYCLE}" 'WEAVE_E2E_AUTHOR_PASSWORD'
absent "${LIFECYCLE}" 'WEAVE_E2E_COLLABORATOR_PASSWORD'
absent "${LIFECYCLE}" 'WEAVE_E2E_OUTSIDER_PASSWORD'
contains "${DCR_CONTRACT_PROBE}" '"invalid-namespace"'
contains "${DCR_CONTRACT_PROBE}" '"out-of-namespace"'
contains "${DCR_CONTRACT_PROBE}" '"wrong-auth-method"'
contains "${DCR_CONTRACT_PROBE}" '"human-login-flow"'
contains "${DCR_CONTRACT_PROBE}" '"web-origin"'
contains "${DCR_CONTRACT_PROBE}" '"unapproved-scope"'
contains "${DCR_CONTRACT_PROBE}" '"protocol-mapper"'
contains "${DCR_CONTRACT_PROBE}" '"custom-attribute"'
contains "${DCR_CONTRACT_PROBE}" '"direct-admin-rest-bypass"'
contains "${DCR_CONTRACT_PROBE}" '"cross-cell-update"'
contains "${DCR_CONTRACT_PROBE}" '"failed-update-rollback"'
contains "${DCR_CONTRACT_PROBE}" '"cross-Cell RAT read was not rejected"'
contains "${DCR_CONTRACT_PROBE}" '"stale RAT remained valid after rotation"'
contains "${DCR_CONTRACT_PROBE}" '"KC-SERVICES0047"'
contains "${DCR_CONTRACT_PROBE}" '"credentialsIncluded": False'

contains "${MODULE_BUILD}" 'apply from: "${projectDir}/gradle/tasks/product-flow.gradle"'
contains "${MODULE_TASKS}" "args 'install', '--with-deps', 'chromium'"
contains "${FLOW}" '/api/bootstrap/owner-invitation'
contains "${FLOW}" '/api/v1/identity/session/reconcile'
contains "${FLOW}" '"access_updated"'
contains "${FLOW}" 'organizationGroups(claims)'
contains "${FLOW}" '/api/admin/providers/selections'
contains "${FLOW}" 'new ProviderSelection("chat", "weave-native")'
contains "${FLOW}" 'new ProviderSelection("files", "weave-native")'
contains "${FLOW}" 'new ProviderSelection("calendar", "weave-native")'
contains "${FLOW}" 'weave.test-app-product-flow/v2'
contains "${FLOW}" 'southboundProviderDependencyObserved'
contains "${FLOW}" 'nativePersistenceVerified'
contains "${FLOW}" '"recommended_self_hosted_default"'
contains "${FLOW}" '/api/chat/readiness'
contains "${FLOW}" '"available".equals(observedState)'
absent "${FLOW}" 'claims.path("groups")'
contains "${FLOW}" 'authorization_code_pkce_s256'
contains "${FLOW}" 'client_credentials_private_key_jwt'
contains "${FLOW}" 'awaitEmailVerificationLink'
contains "${FLOW}" 'credentialsIncluded'
contains "${FLOW}" 'actionLinksIncluded'
contains "${FLOW}" 'candidateCommit'
contains "${FLOW}" 'specificationCommit'
contains "${FLOW}" 'composeProject'
contains "${FLOW}" 'candidateManifestDigest'
contains "${FLOW}" 'sameJpaCellAfterRestart'
contains "${FLOW}" 'sameMcpCellAfterRestart'
contains "${RESTART_FLOW}" 'persistence-restart-proof'
contains "${RESTART_FLOW}" 'weave.test-app-persistence-restart/v1'
contains "${COMPOSE_RUNTIME}" 'compose(context, "restart", "--no-deps"'
contains "${COMPOSE_RUNTIME}" '"dependentKeycloakRestartObserved": True'
contains "${COMPOSE_RUNTIME}" '"fixtureRestoredExactly": True'
contains "${COMPOSE_RUNTIME}" '"live-integration-fixture"'
contains "${RUNTIME_IMAGE_EVIDENCE}" '"weave.test-app-runtime-images/v2"'
contains "${RUNTIME_IMAGE_EVIDENCE}" 'manifest_references.get(component) != reference'
contains "${RUNTIME_IMAGE_EVIDENCE}" 'KEYCLOAK_BUILD_EVIDENCE_LABEL'
contains "${RUNTIME_IMAGE_EVIDENCE}" 'buildEvidenceDigest'
contains "${RUNTIME_IMAGE_EVIDENCE}" '--realm-evidence'
contains "${RUNTIME_IMAGE_EVIDENCE}" 'candidateRealmDefinitionMatched'
contains "${RUNTIME_IMAGE_EVIDENCE}" 'realmEvidenceVerified'
contains "${LIFECYCLE}" 'keycloak_realm_evidence.py'
contains "${LIFECYCLE}" '--realm-evidence "${realm_evidence_path}"'
contains "${LIFECYCLE}" 'keycloak/realm-render-evidence.json'
contains "${LIFECYCLE}" 'fgap-v2-primary-organization-post-import.receipt.json'
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

python3 - "${FLOW}" <<'PY'
import sys
from pathlib import Path

source = Path(sys.argv[1]).read_text(encoding="utf-8")
selection = source.index("configureRequiredProviders(ownerSession.accessToken())")
readiness = source.index("awaitChatReadiness(ownerSession.accessToken())")
collaboration = source.index("CollaborationJourney collaboration")
if not selection < readiness < collaboration:
    raise SystemExit("Fresh product flow must apply providers and await readiness before collaboration")
PY

contains "${CANDIDATE_WORKFLOW}" 'fresh-product-proof:'
contains "${CANDIDATE_WORKFLOW}" 'needs: [verify-source, build-candidate]'
contains "${CANDIDATE_WORKFLOW}" 'weave-server@${{ needs.build-candidate.outputs.server_digest }}'
contains "${CANDIDATE_WORKFLOW}" 'weave-mcp-server@${{ needs.build-candidate.outputs.mcp_digest }}'
contains "${CANDIDATE_WORKFLOW}" 'weave-keycloak-runtime@${{ needs.build-candidate.outputs.keycloak_runtime_digest }}'
contains "${CANDIDATE_WORKFLOW}" 'WEAVE_CANDIDATE_MANIFEST_DIGEST: ${{ needs.build-candidate.outputs.candidate_manifest_digest }}'
contains "${CANDIDATE_WORKFLOW}" 'actions/download-artifact@3e5f45b2cfb9172054b4087a40e8e0b5a5461e7c # v8.0.1'
contains "${CANDIDATE_WORKFLOW}" 'run: ./gradlew --no-daemon testApp'
contains "${CANDIDATE_WORKFLOW}" 'weave/build/test-app/*/keycloak-dcr-live-proof.json'
contains "${CANDIDATE_WORKFLOW}" 'weave/build/test-app/*/persistence-restart-evidence.json'
contains "${CANDIDATE_WORKFLOW}" 'weave/build/test-app/*/runtime-image-evidence.json'
contains "${CANDIDATE_WORKFLOW}" 'weave/build/test-app/*/failure-diagnostics/**'

printf 'testApp product-flow contract tests passed\n'
