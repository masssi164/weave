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
  and .status == "guarded-dark-awaiting-arc-binding"
  and .placement.contractArea == "infra/weave-workspace"
  and .placement.runtimeModule == "weave-mcp-server"
  and (.placement.implementation | test("denied before protocol dispatch"))
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
  and (.canonicalDomains | length) == 8
  and ([.canonicalDomains[].key] | index("calendar"))
  and ([.canonicalDomains[].key] | index("files_documents"))
  and ([.canonicalDomains[].key] | index("boards_tasks"))
  and ([.canonicalDomains[].key] | index("chat_comms"))
  and ([.canonicalDomains[].key] | index("people_identity_org"))
  and ([.canonicalDomains[].key] | index("admin_setup_providers"))
  and ([.canonicalDomains[].key] | index("audit_policy"))
  and ([.canonicalDomains[].key] | index("weaver_runtime_governance"))
  and ([.canonicalDomains[] | select(.key == "admin_setup_providers") | .forbiddenOutputs[]] | index("SecretRefValues"))
  and ([.canonicalDomains[] | select(.key == "weaver_runtime_governance") | .forbiddenOutputs[]] | index("openclaw.json"))
  and ([.canonicalDomains[] | select(.key == "chat_comms") | .forbiddenOutputs[]] | index("mxcUris"))
  and ([.canonicalDomains[].writeToolsRequireApproval | length] | all(. > 0))
  and .sprint16ProofSlice.implementAllAdapters == false
  and .sprint16ProofSlice.allowedProofAdaptersMax <= 2
  and .activeRuntimeEvidence.transport == "stateful-streamable-http-installed"
  and .activeRuntimeEvidence.enabled == false
  and .activeRuntimeEvidence.security == "deny-all-until-arc-binding"
  and .activeRuntimeEvidence.oidcGatekeeper == "spring-security-oauth2-resource-server"
  and .activeRuntimeEvidence.tools == []
  and .activeRuntimeEvidence.resources == []
  and .activeRuntimeEvidence.prompts == []
  and .activeRuntimeEvidence.pythonFastMcpRemoved == true
  and .activeRuntimeEvidence.handwrittenJsonRpcRemoved == true
' "${CONTRACT}" >/dev/null || fail "Weave MCP tool contract is missing required support-safe/fail-closed controls"

assert_contains "${DOC}" "Status: **Guarded / dark**"
assert_contains "${DOC}" 'Each enabled Weaver cell receives its own confidential Keycloak workload client, `weaver-cell-{cellId}`'
assert_contains "${DOC}" "Human access tokens"
assert_contains "${DOC}" "Tool, resource, and prompt capabilities are disabled."
assert_contains "${PRODUCT_PLAN}" "Weave is planned product-first, not agent-first."
assert_contains "${PRODUCT_PLAN}" "OpenClaw configuration remains an implementation target, not the product model."

printf '%s\n' 'weave MCP tool contract tests passed'
