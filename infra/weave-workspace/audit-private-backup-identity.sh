#!/usr/bin/env bash
# shellcheck shell=bash

set -euo pipefail

ROOT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd -- "${ROOT_DIR}/../.." && pwd)"
readonly ROOT_DIR REPO_ROOT
readonly INTEGRITY_TOOL="${REPO_ROOT}/tools/private_backup_integrity.py"
readonly PERSISTENT_ROOT="${WEAVE_PERSISTENT_DOGFOOD_ROOT:-${HOME}/.weave/dogfood}"
readonly SUBJECT_FILE="${WEAVE_DOGFOOD_MEMBER_SUBJECT_FILE:-${PERSISTENT_ROOT}/persistent-member.subject}"
readonly HELPER_IMAGE="${WEAVE_IDENTITY_AUDIT_POSTGRES_IMAGE:-postgres:15}"
readonly EVIDENCE_FILE="${WEAVE_IDENTITY_AUDIT_EVIDENCE_FILE:-}"
BACKUP_DIR="${1:-}"
TEMP_ROOT=""
TEMP_CONTAINER=""
TEMP_VOLUME=""

log() { printf '%s\n' "$*"; }
fail() { printf 'PRIVATE_IDENTITY_AUDIT_ERROR %s\n' "$*" >&2; exit 1; }

cleanup() {
  if [[ -n "${TEMP_CONTAINER}" ]]; then
    docker rm -f "${TEMP_CONTAINER}" >/dev/null 2>&1 || true
  fi
  if [[ -n "${TEMP_VOLUME}" ]]; then
    docker volume rm "${TEMP_VOLUME}" >/dev/null 2>&1 || true
  fi
  if [[ -n "${TEMP_ROOT}" && -d "${TEMP_ROOT}" ]]; then
    rm -rf -- "${TEMP_ROOT}"
  fi
}
trap cleanup EXIT

usage() {
  cat <<'EOF'
Usage: audit-private-backup-identity.sh <private-backup-dir>

Integrity-checks one private backup and replays its PostgreSQL dump into a
uniquely named disposable Docker volume. It proves whether the recorded
persistent subject or configured username is identity-restorable without
changing the running persistent stack. Shared evidence contains hashes and
counts only; credentials, database rows, subjects, and private artifacts are
never printed.

Required environment:
  WEAVE_DOGFOOD_MEMBER_USERNAME
  WEAVE_IDENTITY_AUDIT_EVIDENCE_FILE
EOF
}

require_command() {
  command -v "$1" >/dev/null 2>&1 || fail "missing required command: $1"
}

bootstrap_value() {
  local name="$1" bootstrap_file="$2"
  # shellcheck disable=SC2016
  env -i HOME="${HOME}" PATH="${PATH}" bash -c '
    set -euo pipefail
    source "$1"
    value="${!2:-}"
    [[ -n "$value" ]]
    printf "%s" "$value"
  ' _ "${bootstrap_file}" "${name}"
}

prepare_dump() {
  local source="$1" target="$2" administrator="$3"
  DB_ADMIN_USERNAME="${administrator}" python3 - "${source}" "${target}" <<'PY'
import os
import re
import sys
from pathlib import Path

source = Path(sys.argv[1])
target = Path(sys.argv[2])
administrator = os.environ["DB_ADMIN_USERNAME"]
create_role = re.compile(r'^CREATE ROLE ("(?:[^"]|"")*"|[A-Za-z_][A-Za-z0-9_$]*);$')


def role_name(sql_identifier: str) -> str:
    if sql_identifier.startswith('"'):
        return sql_identifier[1:-1].replace('""', '"')
    return sql_identifier.lower()


removed = 0
with source.open("r", encoding="utf-8") as reader, target.open(
    "w", encoding="utf-8", newline=""
) as writer:
    for line in reader:
        match = create_role.fullmatch(line.rstrip("\r\n"))
        if match and role_name(match.group(1)) == administrator:
            removed += 1
            continue
        writer.write(line)

if removed != 1:
    target.unlink(missing_ok=True)
    raise SystemExit("validated dump does not contain exactly one persistent administrator creation")
target.chmod(0o600)
PY
}

sql_count() {
  local database="$1" query="$2" password="$3" administrator="$4"
  docker exec -e "PGPASSWORD=${password}" "${TEMP_CONTAINER}" \
    psql -U "${administrator}" -d "${database}" -Atqc "${query}"
}

main() {
  if [[ "${BACKUP_DIR}" == --help || "${BACKUP_DIR}" == -h ]]; then
    usage
    exit 0
  fi
  [[ -n "${BACKUP_DIR}" && -d "${BACKUP_DIR}" ]] || fail "private backup directory is required"
  : "${WEAVE_DOGFOOD_MEMBER_USERNAME:?WEAVE_DOGFOOD_MEMBER_USERNAME is required}"
  [[ "${WEAVE_DOGFOOD_MEMBER_USERNAME}" =~ ^[A-Za-z0-9._@+-]+$ ]] ||
    fail "protected dogfood member username is invalid"
  [[ -n "${EVIDENCE_FILE}" ]] || fail "WEAVE_IDENTITY_AUDIT_EVIDENCE_FILE is required"
  [[ -s "${SUBJECT_FILE}" ]] || fail "recorded persistent member subject is unavailable"
  require_command docker
  require_command jq
  require_command python3
  require_command shasum
  require_command tar

  TEMP_ROOT="$(mktemp -d "${TMPDIR:-/tmp}/weave-identity-audit.XXXXXX")"
  chmod 700 "${TEMP_ROOT}"
  python3 "${INTEGRITY_TOOL}" --backup-dir "${BACKUP_DIR}" --output "${TEMP_ROOT}/integrity.json"
  tar -C "${TEMP_ROOT}" -xzf "${BACKUP_DIR}/generated-config-secrets.tgz" .generated/bootstrap.env
  local bootstrap_file="${TEMP_ROOT}/.generated/bootstrap.env"
  local administrator password recorded_subject audit_id ready
  administrator="$(bootstrap_value TF_VAR_db_admin_username "${bootstrap_file}")"
  password="$(bootstrap_value TF_VAR_db_admin_password "${bootstrap_file}")"
  [[ "${administrator}" =~ ^[A-Za-z_][A-Za-z0-9_]*$ ]] || fail "restored database administrator name is invalid"
  recorded_subject="$(tr -d '\r\n' <"${SUBJECT_FILE}")"
  [[ "${recorded_subject}" =~ ^[0-9a-fA-F-]{36}$ ]] || fail "recorded persistent member subject is invalid"
  prepare_dump "${BACKUP_DIR}/postgres.sql" "${TEMP_ROOT}/postgres.sql" "${administrator}"

  audit_id="$(date -u +%Y%m%dT%H%M%SZ)-$$"
  TEMP_VOLUME="weave_identity_audit_${audit_id}"
  TEMP_CONTAINER="weave-identity-audit-${audit_id}"
  docker volume create --label weave.scope=identity-audit "${TEMP_VOLUME}" >/dev/null
  docker run -d --name "${TEMP_CONTAINER}" \
    --label weave.scope=identity-audit \
    -e "POSTGRES_USER=${administrator}" \
    -e POSTGRES_DB=postgres \
    -e "POSTGRES_PASSWORD=${password}" \
    -v "${TEMP_VOLUME}:/var/lib/postgresql/data" \
    "${HELPER_IMAGE}" >/dev/null

  ready=false
  for _ in $(seq 1 60); do
    if docker exec "${TEMP_CONTAINER}" pg_isready -U "${administrator}" -d postgres >/dev/null 2>&1; then
      ready=true
      break
    fi
    sleep 1
  done
  [[ "${ready}" == true ]] || fail "temporary identity-audit database did not become ready"
  docker exec -e "PGPASSWORD=${password}" -i "${TEMP_CONTAINER}" \
    psql -v ON_ERROR_STOP=1 -U "${administrator}" -d postgres \
    <"${TEMP_ROOT}/postgres.sql" >"${TEMP_ROOT}/postgres-restore.log" 2>&1 ||
    fail "private PostgreSQL replay failed"

  local service_databases recorded_matches username_matches bootstrap_matches other_humans
  service_databases="$(sql_count postgres \
    "SELECT count(*) FROM pg_database WHERE datname IN ('weave','weave_backend','weave_keycloak','weave_mas','weave_synapse');" \
    "${password}" "${administrator}")"
  [[ "${service_databases}" == 5 ]] || fail "restored service database set is incomplete"
  recorded_matches="$(sql_count weave_keycloak \
    "SELECT count(*) FROM user_entity WHERE id = '${recorded_subject}';" \
    "${password}" "${administrator}")"
  username_matches="$(sql_count weave_keycloak \
    "SELECT count(*) FROM user_entity u JOIN realm r ON r.id=u.realm_id WHERE r.name='weave' AND lower(u.username)=lower('${WEAVE_DOGFOOD_MEMBER_USERNAME}');" \
    "${password}" "${administrator}")"
  bootstrap_matches="$(sql_count weave_keycloak \
    "SELECT count(*) FROM user_entity u JOIN realm r ON r.id=u.realm_id WHERE r.name='weave' AND u.username='test';" \
    "${password}" "${administrator}")"
  other_humans="$(sql_count weave_keycloak \
    "SELECT count(*) FROM user_entity u JOIN realm r ON r.id=u.realm_id WHERE r.name='weave' AND u.username NOT LIKE 'service-account-%' AND u.username<>'test';" \
    "${password}" "${administrator}")"

  [[ "${recorded_matches}" == 0 && "${username_matches}" == 0 ]] ||
    fail "backup is identity-restorable or ambiguous for the protected member"
  [[ "${bootstrap_matches}" == 1 && "${other_humans}" == 0 ]] ||
    fail "backup does not contain the exact sole disposable bootstrap identity boundary"

  mkdir -p "$(dirname -- "${EVIDENCE_FILE}")"
  local temporary="${EVIDENCE_FILE}.tmp.$$"
  jq -n \
    --arg backupIdSha256 "$(jq -r '.backupIdSha256' "${TEMP_ROOT}/integrity.json")" \
    --arg recordedSubjectSha256 "$(printf '%s' "${recorded_subject}" | shasum -a 256 | awk '{print $1}')" \
    --arg protectedUsernameSha256 "$(printf '%s' "${WEAVE_DOGFOOD_MEMBER_USERNAME}" | shasum -a 256 | awk '{print $1}')" \
    --arg auditedAt "$(date -u '+%Y-%m-%dT%H:%M:%SZ')" '
    {
      schemaVersion:"weave.dogfood.private-backup-identity-audit.v1",
      status:"passed",
      auditedAt:$auditedAt,
      backupIdSha256:$backupIdSha256,
      integrity:{allRequiredArtifactsVerified:true},
      isolatedDatabaseReplay:{status:"passed",serviceDatabases:5,persistentRuntimeMutated:false},
      identity:{
        recordedSubjectSha256:$recordedSubjectSha256,
        protectedUsernameSha256:$protectedUsernameSha256,
        identityRestorableForRecordedMember:false,
        soleDisposableBootstrapIdentity:true,
        otherHumanIdentityCount:0
      },
      privateArtifactContentIncluded:false,
      supportSafe:true
    }
  ' >"${temporary}"
  chmod 600 "${temporary}"
  mv "${temporary}" "${EVIDENCE_FILE}"
  log "PRIVATE_IDENTITY_AUDIT status=passed identityRestorableForRecordedMember=false soleDisposableBootstrapIdentity=true persistentRuntimeMutated=false supportSafe=true"
}

main "$@"
