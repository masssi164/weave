#!/usr/bin/env bash
# shellcheck shell=bash

set -euo pipefail

ROOT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
SCRIPT="${ROOT_DIR}/isolated-e2e-mcp-workload.sh"

[[ -f "${SCRIPT}" ]] || { echo "missing MCP workload evidence script" >&2; exit 1; }
bash -n "${SCRIPT}"

assert_contains() {
  grep -Fq "$2" "$1" || { echo "expected '$2' in $1" >&2; exit 1; }
}

assert_contains "${SCRIPT}" 'assert_isolated_runtime'
assert_contains "${SCRIPT}" 'standard.token.exchange.enabled'
assert_contains "${SCRIPT}" 'oauth-protected-resource/mcp'
assert_contains "${SCRIPT}" 'grant-type:token-exchange'
assert_contains "${SCRIPT}" 'audience=weave-backend'
assert_contains "${SCRIPT}" 'scope=weave:mcp-backend'
assert_contains "${SCRIPT}" '/client-secret'
assert_contains "${SCRIPT}" 'oldCredentialRevoked:true'
assert_contains "${SCRIPT}" 'WEAVE_MCP_CLIENT_SECRET='
assert_contains "${SCRIPT}" 'rawTokenIncluded:false'
assert_contains "${SCRIPT}" 'rawSecretIncluded:false'
assert_contains "${SCRIPT}" 'persistentHumanChanged:false'

printf 'isolated MCP workload evidence contract tests passed\n'
