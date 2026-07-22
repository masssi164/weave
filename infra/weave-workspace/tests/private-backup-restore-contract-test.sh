#!/usr/bin/env bash
# shellcheck shell=bash

set -euo pipefail

ROOT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
REPO_ROOT="$(cd -- "${ROOT_DIR}/../.." && pwd)"
SCRIPT="${ROOT_DIR}/restore-private-backup.sh"
TOOL="${REPO_ROOT}/tools/private_backup_integrity.py"

fail() { printf '%s\n' "$*" >&2; exit 1; }
require_script() { grep -Fq -- "$1" "${SCRIPT}" || fail "Private restore helper is missing: $1"; }

bash -n "${SCRIPT}"
python3 "${REPO_ROOT}/tools/private_backup_integrity_test.py"

require_script 'WEAVE_RESTORE_PREFLIGHT_ONLY'
require_script 'WEAVE_DOGFOOD_DEPLOYMENT_SCOPE=persistent-dogfood'
require_script 'restore-private-dogfood-backup'
require_script 'WEAVE_RESTORE_ALLOW_LEGACY_MANIFEST_FINALIZATION_BUG'
require_script 'assert_persistent_runtime_absent'
require_script 'CREATED_VOLUMES'
require_script 'RESTORE_COMMITTED'
require_script 'weave_db_data'
require_script 'weave_mailpit_data'
require_script 'weave_matrix_chat_appservice_runtime'
require_script 'validated dump does not contain exactly one persistent administrator creation'
require_script 'temporaryAdministratorCreated:false'
require_script 'CONFIG_COMMIT_COMPLETED'
require_script 'install_restore_evidence'
require_script 'identityRestorableForRecordedMember'
require_script 'soleRestoredDisposableBootstrapIdentity'
require_script 'profileSigningRootRestored:true'
require_script 'runtimeStateWrappingRootRestored:true'
require_script 'workloadCredentialSecretRefsRestored:true'
require_script 'liveReconciliationStatus:"pending"'
require_script 'mailpitHistoryRestored=false'
require_script 'privateArtifactContentIncluded:false'
require_script 'supportSafe:true'
grep -Fq -- '-hczf "${target}"' "${ROOT_DIR}/backup.sh" ||
  fail "Private generated-state backup must archive symlink targets rather than checkout-local links"

if grep -Eq 'docker (system|volume) prune|teardown\.sh|WEAVE_REMOVE_VOLUMES' "${SCRIPT}"; then
  fail "Private restore helper contains an unbounded destructive operation"
fi
if grep -Fq 'POSTGRES_USER=restore_admin' "${SCRIPT}"; then
  fail "Private restore helper must not create an undeletable temporary initdb authority"
fi
if ! grep -Fq 'LEGACY_TEXT_MARKER' "${TOOL}" || ! grep -Fq 'a required private backup artifact failed checksum validation' "${TOOL}"; then
  fail "Private backup integrity validation is not narrowly fail-closed"
fi

printf 'private backup restore contract tests passed\n'
