#!/usr/bin/env bash
# shellcheck shell=bash

set -euo pipefail

ROOT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
SCRIPT="${ROOT_DIR}/audit-private-backup-identity.sh"

fail() { printf '%s\n' "$*" >&2; exit 1; }
require_script() { grep -Fq -- "$1" "${SCRIPT}" || fail "Private identity audit helper is missing: $1"; }

[[ -x "${SCRIPT}" ]] || fail "Private identity audit helper is missing or not executable"
bash -n "${SCRIPT}"
shellcheck "${SCRIPT}"

require_script 'tools/private_backup_integrity.py'
require_script 'validated dump does not contain exactly one persistent administrator creation'
require_script 'weave_identity_audit_'
require_script 'weave.scope=identity-audit'
require_script 'trap cleanup EXIT'
require_script 'identityRestorableForRecordedMember:false'
require_script 'soleDisposableBootstrapIdentity:true'
require_script 'otherHumanIdentityCount:0'
require_script 'persistentRuntimeMutated:false'
require_script 'privateArtifactContentIncluded:false'
require_script 'supportSafe:true'

if grep -Eq 'docker (system|volume) prune|WEAVE_REMOVE_VOLUMES|weave_db_data|teardown\.sh' "${SCRIPT}"; then
  fail "Private identity audit helper contains an unbounded or persistent-runtime mutation"
fi
if grep -Eq 'cat .*postgres|printf .*password|set -x' "${SCRIPT}"; then
  fail "Private identity audit helper may expose private backup or credential material"
fi

printf 'private backup identity audit contract tests passed\n'
