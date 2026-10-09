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
  and (.placement.implementation | test("server-owned workload/member binding"))
  and (.placement.retiredRuntime | test("Python and FastMCP"))
  and (.placement.architecturePrinciple | test("workload-only Weaver boundary"))
  and .authorityBoundary.productAuthority == "weave-backend"
  and .authorityBoundary.canonicalApiRemainsAuthoritative == true
  and .authorityBoundary.runtimeDirectProviderAccessAllowed == false
  and .authorityBoundary.memberMayConfigureProvidersThroughMcp == false
  and .authorityBoundary.humanAccessAllowed == false
  and .authorityBoundary.unboundServiceAccountAccessAllowed == false
  and .globalControls.defaultExposeTools == false
  and .globalControls.staticCatalogDoesNotAuthorizeInvocation == true
  and .globalControls.serverOwnedBindingRequired == true
  and .globalControls.currentMemberAuthorizationRequired == true
  and .globalControls.currentToolGrantRequired == true
  and .globalControls.incomingBearerRelayAllowed == false
  and .globalControls.exchangedScopeMayExceedAdmittedWorkloadScope == false
  and .globalControls.humanTokensForbidden == true
  and .globalControls.genericServiceAccountsForbidden == true
  and .globalControls.denyUnknownTools == true
  and .globalControls.supportSafeOutputsOnly == true
  and .globalControls.secretRefOnly == true
  and .globalControls.credentialRefOnly == true
  and .globalControls.rawProviderInternalsReturned == false
  and .globalControls.rawProviderPayloadsReturned == false
  and .globalControls.credentialBearingUrlsReturned == false
  and .globalControls.auditRequiredForEveryToolCall == true
  and (has("canonicalDomains") | not)
  and (has("sprint16ProofSlice") | not)
  and .activeRuntimeEvidence.transport == "stateful-streamable-http-workload-active"
  and .activeRuntimeEvidence.enabled == true
  and .activeRuntimeEvidence.accessTokenType == "at+jwt"
  and .activeRuntimeEvidence.protectedResourceMetadata == "/.well-known/oauth-protected-resource/mcp"
  and .activeRuntimeEvidence.clientCredentialsExtension == "io.modelcontextprotocol/oauth-client-credentials"
  and .activeRuntimeEvidence.backendContext == "downscoped-token-exchange-and-current-member-binding"
  and .activeRuntimeEvidence.security == "rfc9068-exact-audience-scope-and-current-member-resource-authorization"
  and .activeRuntimeEvidence.oidcGatekeeper == "spring-security-oauth2-resource-server"
  and .activeRuntimeEvidence.tools == ["files.search", "calendar.agenda"]
  and .activeRuntimeEvidence.resources == ["weave://files/{canonicalFileId}"]
  and .activeRuntimeEvidence.prompts == []
  and .activeRuntimeEvidence.filesDataPlane.facade == "/api/files/items"
  and .activeRuntimeEvidence.filesDataPlane.canonicalIdProperty == "fileId"
  and .activeRuntimeEvidence.filesDataPlane.toolSpecificBackendEndpoint == false
  and .activeRuntimeEvidence.calendarDataPlane.facade == "/api/calendar/calendars"
  and .activeRuntimeEvidence.calendarDataPlane.canonicalIdProperty == "calendarId"
  and .activeRuntimeEvidence.calendarDataPlane.toolSpecificBackendEndpoint == false
  and .activeRuntimeEvidence.calendarDataPlane.previewMaterializationAllowed == false
  and .activeRuntimeEvidence.pythonFastMcpRemoved == true
  and .activeRuntimeEvidence.handwrittenJsonRpcRemoved == true
' "${CONTRACT}" >/dev/null || fail "Weave MCP tool contract is missing required support-safe/fail-closed controls"

assert_contains "${DOC}" "Status: **Guarded / bounded read slices active**"
assert_contains "${DOC}" "The current implementation reuses an existing cell-bound workload client"
assert_contains "${DOC}" "lifecycle are not #1470 release gates."
assert_contains "${DOC}" "Human access tokens"
assert_contains "${DOC}" '`files.search`'
assert_contains "${DOC}" '`calendar.agenda`'
assert_contains "${DOC}" '`weave://files/{canonicalFileId}`'
assert_contains "${PRODUCT_PLAN}" "Weave is planned product-first, not agent-first."
assert_contains "${PRODUCT_PLAN}" "OpenClaw configuration remains ephemeral implementation output, not the product model."

printf '%s\n' 'weave MCP tool contract tests passed'
