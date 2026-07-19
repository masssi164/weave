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
  .schema == "weave-mcp-tool-contract-v1"
  and .status == "implemented-spring-ai-stateful"
  and .placement.contractArea == "infra/weave-workspace"
  and .placement.runtimeModule == "weave-mcp-server"
  and (.placement.implementation | test("Spring AI 2.0"))
  and (.placement.retiredRuntime | test("Python and FastMCP"))
  and (.placement.architecturePrinciple | test("governed tool projection over Weave APIs"))
  and .authorityBoundary.productAuthority == "weave-backend"
  and .authorityBoundary.canonicalApiRemainsAuthoritative == true
  and .authorityBoundary.runtimeDirectProviderAccessAllowed == false
  and .authorityBoundary.memberMayConfigureProvidersThroughMcp == false
  and .globalControls.defaultExposeTools == false
  and .globalControls.protocolCatalogIsCanonicalCapabilityCeiling == true
  and .globalControls.runtimeProfileGrantRequiredForInvocation == true
  and .globalControls.runtimeApprovedDiscoveryResource == "weave://runtime/approved-tools"
  and .globalControls.approvalReceiptReferenceAloneAuthorizes == false
  and (.globalControls.approvalReceiptBindings | index("runtimeProfileHash"))
  and (.globalControls.approvalReceiptBindings | index("argumentDigest"))
  and (.globalControls.approvalReceiptBindings | index("approvalMode"))
  and (.globalControls.approvalReceiptBindings | index("evidenceRef"))
  and (.globalControls.approvalReceiptBindings | index("toolContractVersion"))
  and (.globalControls.approvalReceiptBindings | index("policyVersion"))
  and .globalControls.denyUnknownTools == true
  and .globalControls.supportSafeOutputsOnly == true
  and .globalControls.secretRefOnly == true
  and .globalControls.credentialRefOnly == true
  and .globalControls.rawProviderInternalsReturned == false
  and .globalControls.rawProviderPayloadsReturned == false
  and .globalControls.credentialBearingUrlsReturned == false
  and .globalControls.auditRequiredForEveryToolCall == true
  and .globalControls.approvalReceiptRequiredForWriteDeleteExternalSendProviderSwitch == true
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
  and .activeRuntimeEvidence.transport == "stateful-streamable-http"
  and .activeRuntimeEvidence.elicitation == "form"
  and .activeRuntimeEvidence.oidcGatekeeper == "spring-security-oauth2-resource-server"
  and (.activeRuntimeEvidence.tools | index("files.search"))
  and (.activeRuntimeEvidence.tools | index("calendar.search_events"))
  and (.activeRuntimeEvidence.tools | index("chat.send_message"))
  and .activeRuntimeEvidence.pythonFastMcpRemoved == true
  and .activeRuntimeEvidence.handwrittenJsonRpcRemoved == true
' "${CONTRACT}" >/dev/null || fail "Weave MCP tool contract is missing required support-safe/fail-closed controls"

assert_contains "${DOC}" "MCP exposes governed actions for approved runtimes; it does not replace backend APIs."
assert_contains "${DOC}" 'The earlier `infra/weave-mcp` Python/FastMCP gateway and handwritten Java JSON-RPC controller are removed'
assert_contains "${DOC}" "SecretRef/CredentialRef handling"
assert_contains "${DOC}" "spring.ai.mcp.server.protocol=STREAMABLE"
assert_contains "${PRODUCT_PLAN}" "Weave is product-first, provider-neutral for collaboration providers"
assert_contains "${PRODUCT_PLAN}" "Generated Weaver/OpenClaw config is implementation output"
assert_contains "${PRODUCT_PLAN}" "from Weave policy, never a second source of authority."

printf '%s\n' 'weave MCP tool contract tests passed'
