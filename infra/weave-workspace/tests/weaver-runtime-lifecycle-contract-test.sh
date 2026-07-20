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
  .schema == "weave-weaver-runtime-lifecycle-contract-v1"
  and .status == "disabled-preflight"
  and .runtimeProfileInput.source == "signed-WeaverRuntimeProfile"
  and (.runtimeProfileInput.requiredVerification | index("runtimeProfileHash"))
  and (.runtimeProfileInput.forbiddenMaterial | index("rawProviderSecrets"))
  and .containerBinding.activeContainerPerTrustBoundary == 1
  and .containerBinding.disallowUsernamesInRuntimeRefs == true
  and .filesystem.readOnlyRootFilesystem == true
  and .filesystem.implicitHostMounts == false
  and (.filesystem.volumes | has("stateDir") and has("workspaceDir") and has("agentDir"))
  and .resources.memoryLimitRequired == true
  and .network.defaultEgress == "deny"
  and (.network.allowedInternalTargets | index("weave-api-internal"))
  and (.network.allowedInternalTargets | index("weave-mcp-gateway-internal"))
  and (.network.allowedInternalTargets | index("allowed-channel-proxy"))
  and (.network.allowedInternalTargets | index("allowed-mcp-proxy"))
  and (.network.forbiddenTargets | index("provider-api-direct"))
  and .credentials.directSecretAccess == false
  and .credentials.credentialBrokerRequired == true
  and .credentials.runtimeTokenMaxTtlSeconds <= 900
  and (.lifecycle.states | index("reload_requested"))
  and (.lifecycle.states | index("restart_required"))
  and (.lifecycle.states | index("rollback_pending"))
  and (.lifecycle.states | index("revoked"))
  and (.lifecycle.restartRequiredFor | index("networkBoundaryChange"))
  and (.lifecycle.restartRequiredFor | index("memoryBoundaryChange"))
  and (.lifecycle.rollbackGate | index("previousProfileNotRevoked"))
  and (.supportBundle.redactOrOmit | index("SecretRefValues"))
  and (.supportBundle.redactOrOmit | index("rawProviderPayloads"))
' "${CONTRACT}" >/dev/null || fail "Weaver runtime lifecycle contract is missing required fail-closed controls"

assert_contains "${DOC}" "One active user/trust boundary maps to one active Weaver runtime context"
assert_contains "${DOC}" 'The only runtime input is a signed `WeaverRuntimeProfile`'
assert_contains "${DOC}" 'separate writable volumes for `stateDir`, `workspaceDir`, and `agentDir`'
assert_contains "${DOC}" "Default egress is deny"
assert_contains "${DOC}" "Weave API through the internal service route"
assert_contains "${DOC}" "Weave MCP Gateway"
assert_contains "${DOC}" "short-lived runtime token only"
assert_contains "${DOC}" "rollback to the previous signed profile"
assert_contains "${DOC}" "Dogfood-production honesty"
assert_contains "${ARCH_DOC}" "One active user/trust boundary maps to one active runtime context/container"
assert_contains "${ARCH_DOC}" "Profile reload, restart, rollback, and revocation are lifecycle operations"
assert_contains "${TRACE}" "domains/agent-runtime-control/spec.md"
assert_contains "${TRACE}" "infra/weave-workspace/weaver-runtime-lifecycle.contract.json"
assert_contains "${SPEC_INVENTORY}" "Canonical runtime-control and approval authority is owned by domains/agent-runtime-control/spec.md"

printf '%s\n' 'weaver runtime lifecycle contract tests passed'
