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
  and .status == "design-foundation-disabled"
  and .placement.contractArea == "infra/weave-workspace"
  and .placement.futureServerPackage == "infra/weave-mcp"
  and (.placement.implementationHint | test("FastMCP"))
  and (.placement.architecturePrinciple | test("governed tool projection over Weave APIs"))
  and .authorityBoundary.productAuthority == "weave-backend"
  and .authorityBoundary.canonicalApiRemainsAuthoritative == true
  and .authorityBoundary.runtimeDirectProviderAccessAllowed == false
  and .authorityBoundary.memberMayConfigureProvidersThroughMcp == false
  and .globalControls.defaultExposeTools == false
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
  and ([.canonicalDomains[].key] | index("calendar-meetings"))
  and ([.canonicalDomains[].key] | index("files-docs"))
  and ([.canonicalDomains[].key] | index("boards-tasks"))
  and ([.canonicalDomains[].key] | index("chat_comms"))
  and ([.canonicalDomains[].key] | index("people_identity_org"))
  and ([.canonicalDomains[].key] | index("admin_setup_adapters"))
  and ([.canonicalDomains[].key] | index("audit_policy"))
  and ([.canonicalDomains[].key] | index("weaver_runtime_governance"))
  and ([.canonicalDomains[].key] | index("calendar") | not)
  and ([.canonicalDomains[].key] | index("calendar-events") | not)
  and ([.canonicalDomains[].key] | index("files_documents") | not)
  and ([.canonicalDomains[].key] | index("boards_tasks") | not)
  and ([.canonicalDomains[] | select(.key == "admin_setup_adapters") | .forbiddenOutputs[]] | index("SecretRefValues"))
  and ([.canonicalDomains[] | select(.key == "weaver_runtime_governance") | .forbiddenOutputs[]] | index("openclaw.json"))
  and ([.canonicalDomains[] | select(.key == "chat_comms") | .forbiddenOutputs[]] | index("mxcUris"))
  and ([.canonicalDomains[].writeToolsRequireApproval | length] | all(. > 0))
  and .sprint16ProofSlice.implementAllAdapters == false
  and .sprint16ProofSlice.allowedProofAdaptersMax <= 2
' "${CONTRACT}" >/dev/null || fail "Weave MCP tool contract is missing required support-safe/fail-closed controls"

assert_contains "${DOC}" "MCP exposes governed actions for approved runtimes; it does not replace backend APIs."
assert_contains "${DOC}" 'FastMCP with Python `@tool` remains an implementation candidate only.'
assert_contains "${DOC}" "SecretRef/CredentialRef handling"
assert_contains "${DOC}" "Do not build every provider adapter in Sprint 16."
assert_contains "${PRODUCT_PLAN}" "Weave is planned product-first, not agent-first."
assert_contains "${PRODUCT_PLAN}" "OpenClaw configuration remains an implementation target, not the product model."

if grep -Eq '"key": "(calendar|calendar-events|files_documents|boards_tasks)"' "${CONTRACT}"; then
  fail "MCP contract contains a non-canonical non-chat domain key"
fi
if grep -Eiq 'raw(CalDav|WebDav|Nextcloud|OpenProject)|providerNative' "${CONTRACT}"; then
  fail "MCP contract contains provider-native member/tool vocabulary"
fi

printf '%s\n' 'weave MCP tool contract tests passed'
