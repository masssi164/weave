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
  and .placement.area == "infra/weave-workspace"
  and (.placement.futurePackageCandidates | index("infra/weave-workspace/weave-mcp"))
  and (.placement.implementationHint | test("FastMCP"))
  and .authorityBoundary.productAuthority == "weave-backend"
  and .authorityBoundary.canonicalApiRemainsAuthoritative == true
  and .authorityBoundary.runtimeDirectProviderAccessAllowed == false
  and .authorityBoundary.memberMayConfigureProvidersThroughMcp == false
  and .globalControls.defaultExposeTools == false
  and .globalControls.denyUnknownTools == true
  and .globalControls.supportSafeOutputsOnly == true
  and .globalControls.secretRefOnly == true
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
  and ([.canonicalDomains[] | select(.key == "weaver_runtime_governance") | .forbiddenOutputs[]] | index("openclawJson"))
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

printf '%s\n' 'weave MCP tool contract tests passed'
