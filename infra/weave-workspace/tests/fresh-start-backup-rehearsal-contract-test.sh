#!/usr/bin/env bash
# shellcheck shell=bash

set -euo pipefail

ROOT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
SCRIPT="${ROOT_DIR}/fresh-start-backup-rehearsal.sh"
IMPLEMENTATION="${ROOT_DIR}/scripts/adoption_rehearsal.py"

bash -n "${SCRIPT}"
shellcheck "${SCRIPT}"
grep -Fq -- '--purpose fresh-start' "${SCRIPT}"
grep -Fq 'private-backup-only-no-adoption' "${IMPLEMENTATION}"
grep -Fq '"legacyStateMigrated": False' "${IMPLEMENTATION}"
grep -Fq '"adoptionAuthorized": False' "${IMPLEMENTATION}"
python3 "${ROOT_DIR}/tests/fresh_start_backup_rehearsal_contract_test.py" -v
python3 "${ROOT_DIR}/tests/recovery_receipt_contract_test.py" -v

printf 'fresh-start backup rehearsal contract tests passed\n'
