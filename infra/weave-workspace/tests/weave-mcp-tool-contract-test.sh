#!/usr/bin/env bash
# shellcheck shell=bash

set -euo pipefail

ROOT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
REPO_DIR="$(cd -- "${ROOT_DIR}/../.." && pwd)"
CONTRACT="${ROOT_DIR}/weave-mcp-tool-contract.json"
DOC="${ROOT_DIR}/../docs/weave-mcp-tool-contract.md"
PRODUCT_PLAN="${REPO_DIR}/docs/product-line-and-weaver-plan.md"

fail() {
  printf '%s\n' "$*" >&2
  exit 1
}

assert_contains() {
  local file="$1"
  local needle="$2"
  grep -Fq -- "${needle}" "${file}" || fail "Expected ${file} to contain: ${needle}"
}

[[ -f "${CONTRACT}" ]] || fail "Missing Weave MCP tool contract: ${CONTRACT}"
[[ -f "${DOC}" ]] || fail "Missing Weave MCP tool contract doc: ${DOC}"

jq -e '
  .schema == "weave-mcp-workload-contract-v2"
  and .status == "guarded-workload-boundary-active"
  and .placement.contractArea == "infra/weave-workspace"
  and .placement.runtimeModule == "weave-mcp-server"
  and (.placement.implementation | test("server-owned cell binding"))
  and (.placement.retiredRuntime | test("Python and FastMCP"))
  and (.placement.architecturePrinciple | test("workload-only Weaver boundary"))
  and .authorityBoundary.productAuthority == "weave-backend"
  and .authorityBoundary.canonicalApiRemainsAuthoritative == true
  and .authorityBoundary.runtimeDirectProviderAccessAllowed == false
  and .authorityBoundary.memberMayConfigureProvidersThroughMcp == false
  and .authorityBoundary.humanAccessAllowed == false
  and .authorityBoundary.unboundServiceAccountAccessAllowed == false
  and .globalControls.defaultExposeTools == false
  and .globalControls.protocolCatalogIsCanonicalCapabilityCeiling == true
  and .globalControls.runtimeProfileGrantRequiredForInvocation == true
  and .globalControls.runtimeProfileVersion == "v2"
  and .globalControls.workloadClientConvention == "weaver-cell-{cellId}"
  and .globalControls.serverOwnedBindingRequired == true
  and .globalControls.humanTokensForbidden == true
  and .globalControls.genericServiceAccountsForbidden == true
  and .globalControls.v1ReadersAllowed == false
  and .globalControls.approvalDecisionEvidenceVersion == "v2"
  and .globalControls.actionEvidenceVersion == "v2"
  and .globalControls.denyUnknownTools == true
  and .globalControls.supportSafeOutputsOnly == true
  and .globalControls.secretRefOnly == true
  and .globalControls.credentialRefOnly == true
  and .globalControls.rawProviderInternalsReturned == false
  and .globalControls.rawProviderPayloadsReturned == false
  and .globalControls.credentialBearingUrlsReturned == false
  and .globalControls.auditRequiredForEveryToolCall == true
  and .globalControls.approvalDecisionEvidenceRequiredForWriteDeleteExternalSendProviderSwitch == true
  and (.canonicalDomains | length) == 6
  and ([.canonicalDomains[].key] | index("calendar"))
  and ([.canonicalDomains[].key] | index("files_documents"))
  and ([.canonicalDomains[].key] | index("boards_tasks") | not)
  and ([.canonicalDomains[].key] | index("chat_comms"))
  and ([.canonicalDomains[].key] | index("people_identity_org"))
  and ([.canonicalDomains[].key] | index("admin_setup_providers"))
  and ([.canonicalDomains[].key] | index("audit_policy"))
  and ([.canonicalDomains[] | select(.key == "admin_setup_providers") | .forbiddenOutputs[]] | index("SecretRefValues"))
  and ([.canonicalDomains[] | select(.key == "chat_comms") | .forbiddenOutputs[]] | index("mxcUris"))
  and ([.canonicalDomains[].writeToolsRequireApproval | length] | all(. > 0))
  and (has("sprint16ProofSlice") | not)
  and .activeRuntimeEvidence.transport == "stateful-streamable-http-workload-active"
  and .activeRuntimeEvidence.enabled == true
  and .activeRuntimeEvidence.accessTokenType == "at+jwt"
  and .activeRuntimeEvidence.protectedResourceMetadata == "/.well-known/oauth-protected-resource/mcp"
  and .activeRuntimeEvidence.clientCredentialsExtension == "io.modelcontextprotocol/oauth-client-credentials"
  and .activeRuntimeEvidence.backendContext == "standard-token-exchange-v2-and-current-arc-binding"
  and .activeRuntimeEvidence.security == "bound-cell-only-rfc9068-exact-audience-and-current-arc-context"
  and .activeRuntimeEvidence.oidcGatekeeper == "spring-security-oauth2-resource-server"
  and .activeRuntimeEvidence.tools == ["files.search"]
  and .activeRuntimeEvidence.resources == ["weave://files/{canonicalFileId}"]
  and .activeRuntimeEvidence.prompts == []
  and .activeRuntimeEvidence.filesDataPlane.facade == "/dav/files"
  and .activeRuntimeEvidence.filesDataPlane.canonicalIdProperty == "{urn:weave:files}canonical-id"
  and .activeRuntimeEvidence.filesDataPlane.toolSpecificBackendEndpoint == false
  and .activeRuntimeEvidence.pythonFastMcpRemoved == true
  and .activeRuntimeEvidence.handwrittenJsonRpcRemoved == true
' "${CONTRACT}" >/dev/null || fail "Weave MCP tool contract is missing required support-safe/fail-closed controls"

assert_contains "${DOC}" "Status: **Guarded / first read-only Files slice active**"
assert_contains "${DOC}" "Each enabled Weaver cell receives its own confidential Keycloak workload client,"
assert_contains "${DOC}" '`weaver-cell-{cellId}`, through Agent Runtime Control (ARC).'
assert_contains "${DOC}" "Human access tokens"
assert_contains "${DOC}" '`files.search`'
assert_contains "${DOC}" '`weave://files/{canonicalFileId}`'
assert_contains "${PRODUCT_PLAN}" "Weave is planned product-first, not agent-first."
assert_contains "${PRODUCT_PLAN}" "OpenClaw configuration remains ephemeral implementation output, not the product model."

printf '%s\n' 'weave MCP tool contract tests passed'
