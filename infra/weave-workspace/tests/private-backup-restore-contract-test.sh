#!/usr/bin/env bash
# shellcheck shell=bash

set -euo pipefail

ROOT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
REPO_ROOT="$(cd -- "${ROOT_DIR}/../.." && pwd)"
SCRIPT="${ROOT_DIR}/restore-private-backup.sh"
TOOL="${REPO_ROOT}/tools/private_backup_integrity.py"

fail() { printf '%s\n' "$*" >&2; exit 1; }
require() { grep -Fq -- "$2" "$1" || fail "Missing guarded restore contract '$2' in $1"; }

bash -n "${SCRIPT}"
python3 "${REPO_ROOT}/tools/private_backup_integrity_test.py"

require "${SCRIPT}" 'WEAVE_RESTORE_PREFLIGHT_ONLY'
require "${SCRIPT}" 'direct private-backup apply is retired after the Compose/JPA cutover'
require "${SCRIPT}" 'integrity verified; apply remains Guarded pending Compose/control-store restore evidence'
require "${TOOL}" 'a required private backup artifact failed checksum validation'
require "${TOOL}" 'weave.compose-private-backup.v2'

if grep -Eq 'weave-backup-manifest-v1|allow-legacy|MANIFEST\.txt|generated-config-secrets' "${SCRIPT}" "${TOOL}"; then
  fail "Restore preflight must not retain the retired backup schema or compatibility reader"
fi

if grep -Eq 'docker|compose|volume (create|rm)|network (create|rm)|psql|pg_restore|tar -x|rm -rf.*BACKUP' "${SCRIPT}"; then
  fail "Restore preflight must not contain a mutating runtime or artifact-extraction path"
fi

printf 'private backup restore contract tests passed\n'
