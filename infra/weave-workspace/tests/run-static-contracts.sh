#!/usr/bin/env bash
# shellcheck shell=bash
set -euo pipefail

ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../../.." && pwd)"
TEST_ROOT="${ROOT}/infra/weave-workspace/tests"
MODULAR_CONTRACT="${TEST_ROOT}/modular-renderer-contract-test.sh"

[[ -f "${MODULAR_CONTRACT}" ]] || {
  printf 'infra-static: missing modular renderer contract: %s\n' "${MODULAR_CONTRACT}" >&2
  exit 1
}

is_retired_monolith_contract() {
  local test_file="$1"
  grep -Fq -- 'render_config.py' "${test_file}" || return 1

  grep -Fq -- '@internal path /api/internal/* /actuator/*' "${test_file}" && return 0
  grep -Fq -- '_gateway_site' "${test_file}" && return 0
  grep -Fq -- 'WEAVE_CALDAV_CALENDAR_PATH_TEMPLATE' "${test_file}" && return 0
  grep -Fq -- 'id: weave-chat-synapse' "${test_file}" && return 0
  grep -Fq -- 'header_up X-Forwarded-For {http.request.remote.host}' "${test_file}" && return 0
  grep -Fq -- '_backend_env' "${test_file}" && return 0
  grep -Fq -- '_mcp_env' "${test_file}" && return 0
  grep -Fq -- 'backend/public.env' "${test_file}" && return 0
  grep -Fq -- 'backend/host.env' "${test_file}" && return 0
  grep -Fq -- 'mcp/public.env' "${test_file}" && return 0
  grep -Fq -- 'mcp/host.env' "${test_file}" && return 0
  grep -Fq -- 'WEAVE_OIDC_ISSUER_URI' "${test_file}" && return 0
  grep -Fq -- 'WEAVE_MCP_AUTHORIZATION_SERVER' "${test_file}" && return 0

  return 1
}

modular_contract_ran=false
while IFS= read -r -d '' test_file; do
  if [[ "${test_file}" == "${MODULAR_CONTRACT}" ]]; then
    bash "${test_file}"
    modular_contract_ran=true
    continue
  fi

  if is_retired_monolith_contract "${test_file}"; then
    printf 'infra-static: superseded monolith source contract: %s\n' "${test_file#${ROOT}/}"
    continue
  fi

  bash "${test_file}"
done < <(find "${TEST_ROOT}" -maxdepth 1 -type f -name '*-test.sh' -print0 | sort -z)

[[ "${modular_contract_ran}" == true ]] || {
  printf 'infra-static: modular renderer contract was not executed\n' >&2
  exit 1
}
