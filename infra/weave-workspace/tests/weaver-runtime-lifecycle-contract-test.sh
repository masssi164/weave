#!/usr/bin/env bash
# shellcheck shell=bash

set -euo pipefail

ROOT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
REPO_DIR="$(cd -- "${ROOT_DIR}/../.." && pwd)"
CONTRACT="${ROOT_DIR}/weaver-runtime-lifecycle.contract.json"
DOC="${ROOT_DIR}/../docs/weaver-runtime-lifecycle.md"
ARCH_DOC="${REPO_DIR}/docs/architecture/weaver-openclaw-profile.md"
TRACE="${REPO_DIR}/specs/0007-governed-weaver-runtime/traceability.yaml"
SPEC_INVENTORY="${REPO_DIR}/specs/spec-inventory.yaml"

fail() {
  printf '%s\n' "$*" >&2
  exit 1
}

assert_contains() {
  local file="$1"
  local needle="$2"
  grep -Fq -- "${needle}" "${file}" || fail "Expected ${file} to contain: ${needle}"
}

[[ -f "${CONTRACT}" ]] || fail "Missing Weaver runtime lifecycle contract: ${CONTRACT}"
[[ -f "${DOC}" ]] || fail "Missing Weaver runtime lifecycle doc: ${DOC}"

jq -e '
  .schema == "weave-weaver-runtime-lifecycle-contract-v2"
  and .status == "guarded-control-plane"
  and .runtimeProfileInput.source == "signed-RuntimeProfile-v2"
  and (.runtimeProfileInput.requiredVerification | index("runtimeProfileHash"))
  and (.runtimeProfileInput.forbiddenMaterial | index("rawProviderSecrets"))
  and .identity.authority == "keycloak"
  and .identity.clientPerCell == true
  and .identity.subjectBinding == "immutable-keycloak-service-account-subject"
  and .identity.authorizedPartyEqualsClientId == true
  and .identity.soleRealmRole == "weaver-runtime"
  and .identity.clientRolesAllowed == false
  and .identity.profileFetch.soleScope == "agent-runtime.profile.read"
  and .identity.mcp.requiredBaseScope == "mcp.tools"
  and .identity.mcp.domainScopesFromCurrentRuntimeProfile == true
  and .identity.mcp.accessTokenType == "at+jwt"
  and .identity.mcp.clientCredentialsExtension == "io.modelcontextprotocol/oauth-client-credentials"
  and .identity.mcp.backendTokenExchange == "standard-token-exchange-v2-downscoped"
  and .identity.mcp.incomingBearerRelayAllowed == false
  and .identity.humanOrGenericServiceAccess == "deny"
  and .containerBinding.activeContainerPerTrustBoundary == 1
  and .containerBinding.disallowHumanOrProviderIdentifiersInRuntimeRefs == true
  and .filesystem.zeroDurableCellLocalBytes == true
  and .filesystem.readOnlyRootFilesystem == true
  and .filesystem.implicitHostMounts == false
  and .filesystem.durableCellVolumesAllowed == false
  and (.filesystem.forbiddenMounts | index("durableStateDir"))
  and .filesystem.portableWorkspace.authority == "webdav"
  and .externalRuntimeState.authority == "RuntimeStateStore"
  and .externalRuntimeState.dogfoodAdapter == "s3-compatible-minio-client-encrypted-generations"
  and .externalRuntimeState.generationCipher == "AES-256-GCM"
  and .externalRuntimeState.dataKeyWrapping == "AES-KWP-mounted-file-key"
  and .externalRuntimeState.commit == "compare-and-swap"
  and .externalRuntimeState.fileKeyClaimMaturity == "Guarded"
  and .resources.memoryLimitRequired == true
  and .network.defaultEgress == "deny"
  and (.network.allowedInternalTargets | index("weave-api-internal"))
  and (.network.allowedInternalTargets | index("weave-mcp-gateway-internal"))
  and (.network.allowedInternalTargets | index("allowed-channel-proxy"))
  and (.network.forbiddenTargets | index("provider-api-direct"))
  and (.lifecycle.states | index("MATERIALIZING"))
  and (.lifecycle.states | index("REVOKING"))
  and (.lifecycle.states | index("DELETED"))
  and .lifecycle.cellDeletionDeletesWebDav == false
  and .lifecycle.confirmedRuntimeStateDeletionDeletesExternalRuntimeState == true
  and .lifecycle.runtimeStateDeletionConfirmation == "DELETE_RUNTIME_STATE_ONLY"
  and .restore.normalStartupMayGenerateMissingKeys == false
  and .restore.readinessBlockedUntilReconciled == true
  and (.restore.consistencySet | index("keycloak-data"))
  and (.restore.consistencySet | index("runtime-state-wrapping-secret-root"))
  and (.supportBundle.redactOrOmit | index("SecretRefValues"))
  and (.supportBundle.redactOrOmit | index("rawProviderPayloads"))
' "${CONTRACT}" >/dev/null || fail "Weaver runtime lifecycle contract is missing required fail-closed controls"

assert_contains "${DOC}" "Each cell gets a dedicated confidential Keycloak client"
assert_contains "${DOC}" 'Human clients and generic service accounts have no MCP path.'
assert_contains "${DOC}" "Zero durable cell-local bytes"
assert_contains "${DOC}" 'no durable `stateDir`, `workspaceDir`, `agentDir`'
assert_contains "${DOC}" "RuntimeStateStore"
assert_contains "${DOC}" "AES-256-GCM"
assert_contains "${DOC}" "never deletes WebDAV/Files content"
assert_contains "${DOC}" "Default egress is deny"
assert_contains "${DOC}" "one private restore consistency set"
assert_contains "${DOC}" 'remains `Guarded`'
assert_contains "${ARCH_DOC}" "One active user/trust boundary maps to one active runtime context/container"
assert_contains "${ARCH_DOC}" "zero durable cell-local bytes"
assert_contains "${TRACE}" "domains/agent-runtime-control/spec.md"
assert_contains "${TRACE}" "infra/weave-workspace/weaver-runtime-lifecycle.contract.json"
assert_contains "${SPEC_INVENTORY}" "Canonical runtime-control and approval authority is owned by domains/agent-runtime-control/spec.md"

printf '%s\n' 'weaver runtime lifecycle contract tests passed'
