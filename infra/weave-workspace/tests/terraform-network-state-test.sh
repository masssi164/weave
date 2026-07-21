#!/usr/bin/env bash
# shellcheck shell=bash

set -euo pipefail

TEST_WORKSPACE_ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
readonly TEST_WORKSPACE_ROOT
INSTALL_SCRIPT="${TEST_WORKSPACE_ROOT}/install.sh"
readonly INSTALL_SCRIPT

# shellcheck source=infra/weave-workspace/install.sh
source "${INSTALL_SCRIPT}"

fail() {
  printf '%s\n' "$*" >&2
  exit 1
}

assert_contains() {
  local file="$1"
  local expected="$2"
  grep -Fq -- "${expected}" "${file}" || fail "Expected ${file} to contain: ${expected}"
}

assert_absent() {
  local file="$1"
  local unexpected="$2"
  ! grep -Fq -- "${unexpected}" "${file}" || fail "Did not expect ${file} to contain: ${unexpected}"
}

weave_iac_init() {
  printf 'init %s\n' "$*" >>"${MOCK_COMMAND_LOG}"
}

weave_iac() {
  printf 'iac %s\n' "$*" >>"${MOCK_COMMAND_LOG}"
  if [[ "${2:-}" == state && "${3:-}" == show ]]; then
    [[ -n "${MOCK_STATE_NETWORK_ID:-}" ]] || return 1
    printf 'id = "%s"\n' "${MOCK_STATE_NETWORK_ID}"
  fi
}

docker() {
  printf 'docker %s\n' "$*" >>"${MOCK_COMMAND_LOG}"
  [[ "${1:-}" == network && "${2:-}" == inspect ]] || return 1
  [[ -n "${MOCK_LIVE_NETWORK_ID:-}" ]] || return 1
  if [[ "${3:-}" == --format ]]; then
    printf '%s\n' "${MOCK_LIVE_NETWORK_ID}"
  fi
}

run_case() {
  local state_id="$1"
  local live_id="$2"
  local expected_mode="$3"
  local test_dir
  test_dir="$(mktemp -d)"
  export MOCK_COMMAND_LOG="${test_dir}/commands.log"
  export MOCK_STATE_NETWORK_ID="${state_id}"
  export MOCK_LIVE_NETWORK_ID="${live_id}"
  export TF_VAR_docker_network_name=weave_network
  : >"${MOCK_COMMAND_LOG}"

  ensure_terraform_network_state

  case "${expected_mode}" in
    unchanged)
      assert_absent "${MOCK_COMMAND_LOG}" 'state rm docker_network.weave_network'
      assert_absent "${MOCK_COMMAND_LOG}" 'import -input=false docker_network.weave_network'
      ;;
    replace)
      assert_contains "${MOCK_COMMAND_LOG}" 'state rm docker_network.weave_network'
      assert_contains "${MOCK_COMMAND_LOG}" "import -input=false docker_network.weave_network ${live_id}"
      ;;
    import)
      assert_absent "${MOCK_COMMAND_LOG}" 'state rm docker_network.weave_network'
      assert_contains "${MOCK_COMMAND_LOG}" "import -input=false docker_network.weave_network ${live_id}"
      ;;
    forget)
      assert_contains "${MOCK_COMMAND_LOG}" 'state rm docker_network.weave_network'
      assert_absent "${MOCK_COMMAND_LOG}" 'import -input=false docker_network.weave_network'
      ;;
    *)
      fail "Unknown expected mode: ${expected_mode}"
      ;;
  esac
}

run_case live-a live-a unchanged
run_case stale-a live-b replace
run_case '' live-c import
run_case stale-d '' forget

printf '%s\n' 'Terraform network state tests passed'
