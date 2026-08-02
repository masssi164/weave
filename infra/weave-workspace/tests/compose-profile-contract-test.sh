#!/usr/bin/env bash
# shellcheck shell=bash

set -euo pipefail

ROOT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
python3 "${ROOT_DIR}/tests/compose_profile_contract_test.py"

DEV_DOWN="${ROOT_DIR}/../../gradle/tasks/dev-down.sh"
DEV_UP="${ROOT_DIR}/../../gradle/tasks/dev-up.sh"
PREPARE_DEV="${ROOT_DIR}/scripts/prepare_dev_dependencies.py"

grep -Fq 'python3 "${WORKSPACE}/scripts/prepare_dev_dependencies.py" --root "${WORKSPACE}"' "${DEV_UP}"
if grep -Eq 'install\.sh|stop_host_replaced_container|container stop' "${DEV_UP}"; then
  printf '%s\n' "compose profile contract failed: devUp must use the closed provider preparation path" >&2
  exit 1
fi
grep -Fq '[str(root / "install.sh"), "dev"]' "${PREPARE_DEV}"

grep -Fq 'bash "${WORKSPACE}/compose.sh" dev down --remove-orphans' "${DEV_DOWN}"
if grep -Eq 'teardown\.sh|WEAVE_REMOVE_VOLUMES|--volumes|(^|[[:space:]])-v([[:space:]]|$)' "${DEV_DOWN}"; then
  printf '%s\n' "compose profile contract failed: devDown must preserve persistent volumes" >&2
  exit 1
fi
