#!/usr/bin/env bash
# shellcheck shell=bash

set -euo pipefail

ROOT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
python3 "${ROOT_DIR}/tests/compose_profile_contract_test.py"

DEV_DOWN="${ROOT_DIR}/../../gradle/tasks/dev-down.sh"
DEV_UP="${ROOT_DIR}/../../gradle/tasks/dev-up.sh"
DEV_RUN="${ROOT_DIR}/../../gradle/tasks/dev-run.sh"
PREPARE_DEV="${ROOT_DIR}/scripts/prepare_dev_dependencies.py"
DOGFOOD_LIFECYCLE="${ROOT_DIR}/scripts/dogfood_lifecycle.py"
LIFECYCLE_TASKS="${ROOT_DIR}/../../gradle/tasks/architecture-lifecycle.gradle"

grep -Fq 'python3 "${WORKSPACE}/scripts/prepare_dev_dependencies.py" --root "${WORKSPACE}"' "${DEV_UP}"
if grep -Eq 'install\.sh|stop_host_replaced_container|container stop' "${DEV_UP}"; then
  printf '%s\n' "compose profile contract failed: devUp must use the closed provider preparation path" >&2
  exit 1
fi
grep -Fq 'execute(context, "up", [])' "${PREPARE_DEV}"
grep -Fq 'compose(context, "up", "--no-start", "--no-deps", "backend", "mcp")' "${PREPARE_DEV}"
! grep -Fq 'FGAP' "${PREPARE_DEV}"
! grep -Fq 'TF_VAR_' "${DEV_UP}"
! grep -Fq 'TF_VAR_' "${DEV_RUN}"
! grep -Fq 'TF_VAR_' "${DEV_DOWN}"

grep -Fq 'bash "${WORKSPACE}/compose.sh" dev down --remove-orphans' "${DEV_DOWN}"
if grep -Eq 'teardown\.sh|WEAVE_REMOVE_VOLUMES|--volumes|(^|[[:space:]])-v([[:space:]]|$)' "${DEV_DOWN}"; then
  printf '%s\n' "compose profile contract failed: devDown must preserve persistent volumes" >&2
  exit 1
fi

for task in dogfoodUp dogfoodDown dogfoodReset; do
  grep -Fq "tasks.register('${task}'" "${LIFECYCLE_TASKS}"
done
grep -Fq 'choices=("up", "down", "reset")' "${DOGFOOD_LIFECYCLE}"
grep -Fq 'execute(context, "down", ["--remove-orphans"])' "${DOGFOOD_LIFECYCLE}"
grep -Fq 'execute(context, args.operation, [])' "${DOGFOOD_LIFECYCLE}"
! grep -Fq 'freshStart' "${LIFECYCLE_TASKS}"
