#!/usr/bin/env bash
# shellcheck shell=bash

set -euo pipefail

ROOT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd -- "${ROOT_DIR}/../.." && pwd)"
readonly ROOT_DIR REPO_ROOT
readonly INTEGRITY_TOOL="${REPO_ROOT}/tools/private_backup_integrity.py"
readonly PREFLIGHT_ONLY="${WEAVE_RESTORE_PREFLIGHT_ONLY:-false}"
BACKUP_DIR="${1:-}"
TEMP_ROOT=""

fail() { printf 'PRIVATE_RESTORE_ERROR %s\n' "$*" >&2; exit 1; }

cleanup() {
  if [[ -n "${TEMP_ROOT}" && -d "${TEMP_ROOT}" ]]; then
    rm -rf -- "${TEMP_ROOT}"
  fi
}
trap cleanup EXIT

if [[ -z "${BACKUP_DIR}" || ! -d "${BACKUP_DIR}" ]]; then
  fail "usage: restore-private-backup.sh <private-backup-dir>"
fi
command -v python3 >/dev/null 2>&1 || fail "missing required command: python3"
TEMP_ROOT="$(mktemp -d "${TMPDIR:-/tmp}/weave-private-restore-preflight.XXXXXX")"

python3 "${INTEGRITY_TOOL}" \
  --backup-dir "${BACKUP_DIR}" \
  --output "${TEMP_ROOT}/integrity.json"

if [[ "${PREFLIGHT_ONLY}" != true ]]; then
  fail "direct private-backup apply is retired after the Compose/JPA cutover; use the reviewed Compose control-store restore runbook and exact-candidate recovery workflow"
fi

printf '%s\n' \
  'private restore preflight: integrity verified; apply remains Guarded pending Compose/control-store restore evidence'
