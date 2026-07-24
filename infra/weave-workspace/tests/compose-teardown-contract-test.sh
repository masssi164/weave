#!/usr/bin/env bash
# shellcheck shell=bash

set -euo pipefail

ROOT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
grep -Fq -- '[[ "${PROFILE}" == test ]]' "${ROOT_DIR}/teardown.sh"
grep -Fq -- 'if not args.isolated:' "${ROOT_DIR}/scripts/teardown_compose.py"
grep -Fq -- 'destructive teardown is restricted to a run-scoped isolated test project' "${ROOT_DIR}/scripts/teardown_compose.py"
exec python3 "${ROOT_DIR}/tests/compose_teardown_contract_test.py" -v
