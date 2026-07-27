#!/usr/bin/env bash
# shellcheck shell=bash

set -euo pipefail

ROOT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
python3 "${ROOT_DIR}/tests/compose_profile_contract_test.py"

DEV_DOWN="${ROOT_DIR}/../../gradle/tasks/dev-down.sh"
grep -Fq 'bash "${WORKSPACE}/compose.sh" dev down --remove-orphans' "${DEV_DOWN}"
if grep -Eq 'teardown\.sh|WEAVE_REMOVE_VOLUMES|--volumes|(^|[[:space:]])-v([[:space:]]|$)' "${DEV_DOWN}"; then
  printf '%s\n' "compose profile contract failed: devDown must preserve persistent volumes" >&2
  exit 1
fi
