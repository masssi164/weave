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

failures=()
modular_contract_ran=false
while IFS= read -r -d '' test_file; do
  if [[ "${test_file}" == "${MODULAR_CONTRACT}" ]]; then
    modular_contract_ran=true
  fi

  if ! bash "${test_file}"; then
    failures+=("${test_file#${ROOT}/}")
  fi
done < <(find "${TEST_ROOT}" -maxdepth 1 -type f -name '*-test.sh' -print0 | sort -z)

[[ "${modular_contract_ran}" == true ]] || failures+=("${MODULAR_CONTRACT#${ROOT}/} (not executed)")

if ((${#failures[@]} > 0)); then
  printf 'infra-static: failed contracts (%d):\n' "${#failures[@]}" >&2
  printf ' - %s\n' "${failures[@]}" >&2
  exit 1
fi
