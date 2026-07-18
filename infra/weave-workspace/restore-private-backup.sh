#!/usr/bin/env bash
# shellcheck shell=bash

set -euo pipefail

ROOT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd -- "${ROOT_DIR}/../.." && pwd)"
readonly ROOT_DIR REPO_ROOT
readonly INTEGRITY_TOOL="${REPO_ROOT}/tools/private_backup_integrity.py"
readonly PERSISTENT_ROOT="${WEAVE_PERSISTENT_DOGFOOD_ROOT:-${HOME}/.weave/dogfood}"
readonly CREDENTIAL_STATE_FILE="${WEAVE_LOCAL_CREDENTIAL_STATE_FILE:-${XDG_STATE_HOME:-${HOME}/.local/state}/weave/dogfood/bootstrap.env}"
readonly HELPER_IMAGE="${WEAVE_RESTORE_HELPER_IMAGE:-alpine:3.20}"
readonly POSTGRES_IMAGE="${WEAVE_RESTORE_POSTGRES_IMAGE:-postgres:15}"
readonly PREFLIGHT_ONLY="${WEAVE_RESTORE_PREFLIGHT_ONLY:-false}"
readonly ALLOW_LEGACY_MANIFEST_BUG="${WEAVE_RESTORE_ALLOW_LEGACY_MANIFEST_FINALIZATION_BUG:-false}"
readonly RESTORE_SCOPE="${WEAVE_DOGFOOD_DEPLOYMENT_SCOPE:-}"
readonly RESTORE_CONFIRMATION="${WEAVE_RESTORE_CONFIRMATION:-}"
readonly EVIDENCE_FILE="${WEAVE_RESTORE_EVIDENCE_FILE:-${PERSISTENT_ROOT}/identity-recovery/platform-restore.json}"
BACKUP_DIR="${1:-}"
TEMP_ROOT=""
TEMP_CONTAINER=""
RESTORE_COMMITTED=false
CONFIG_COMMIT_STARTED=false
CONFIG_COMMIT_COMPLETED=false
CONFIG_RECOVERY_ARCHIVE=""
INFRA_CONFIG_REPLACED=false
INFRA_PREVIOUS_ARCHIVED=false
KEYCLOAK_CONFIG_REPLACED=false
KEYCLOAK_PREVIOUS_ARCHIVED=false
CREDENTIAL_STATE_REPLACED=false
CREDENTIAL_STATE_PREVIOUS_ARCHIVED=false
EVIDENCE_REPLACED=false
EVIDENCE_PREVIOUS_ARCHIVED=false
CREATED_VOLUMES=()

readonly ARCHIVE_VOLUMES=(
  "weave_nextcloud_data:nextcloud-data.tgz"
  "weave_synapse_data:matrix-synapse-data.tgz"
  "weave_caddy_data:caddy-data.tgz"
  "weave_caddy_config:caddy-config.tgz"
  "weave_keycloak_data:keycloak-data.tgz"
)
readonly ALL_PERSISTENT_VOLUMES=(
  weave_db_data
  weave_nextcloud_data
  weave_synapse_data
  weave_caddy_data
  weave_caddy_config
  weave_keycloak_data
  weave_mailpit_data
  weave_matrix_chat_appservice_runtime
)
readonly ALL_PERSISTENT_CONTAINERS=(
  weave-db
  weave-proxy
  weave-keycloak
  weave-mailpit
  weave-backend
  weave-mcp-server
  weave-mas
  weave-synapse
  weave-nextcloud
)

log() { printf '%s\n' "$*"; }
fail() { printf 'PRIVATE_RESTORE_ERROR %s\n' "$*" >&2; exit 1; }

usage() {
  cat <<'EOF'
Usage: restore-private-backup.sh <private-backup-dir>

Restores one integrity-checked private backup into an absent persistent dogfood
runtime. It never restores Mailpit history because backup.sh does not archive it.

Preflight only (no Docker mutation):
  WEAVE_RESTORE_PREFLIGHT_ONLY=true restore-private-backup.sh <backup-dir>

Apply requirements:
  WEAVE_DOGFOOD_DEPLOYMENT_SCOPE=persistent-dogfood
  WEAVE_RESTORE_CONFIRMATION=restore-private-dogfood-backup

For a backup created by the historical text-manifest ordering bug only:
  WEAVE_RESTORE_ALLOW_LEGACY_MANIFEST_FINALIZATION_BUG=true

The helper fails if any persistent Weave container or volume already exists. A
partial restore removes only volumes created by this invocation and restores the
prior generated state transactionally. Private backup contents, credentials,
subjects, and database rows are never printed.
EOF
}

require_command() {
  command -v "$1" >/dev/null 2>&1 || fail "missing required command: $1"
}

cleanup() {
  local volume
  if [[ -n "${TEMP_CONTAINER}" ]]; then
    docker rm -f "${TEMP_CONTAINER}" >/dev/null 2>&1 || true
  fi
  if [[ "${CONFIG_COMMIT_STARTED}" == true && "${CONFIG_COMMIT_COMPLETED}" != true ]]; then
    if [[ "${EVIDENCE_REPLACED}" == true ]]; then
      rm -f -- "${EVIDENCE_FILE}"
    fi
    if [[ "${EVIDENCE_PREVIOUS_ARCHIVED}" == true ]]; then
      mv "${CONFIG_RECOVERY_ARCHIVE}/platform-restore.json" "${EVIDENCE_FILE}" >/dev/null 2>&1 || true
    fi
    if [[ "${CREDENTIAL_STATE_REPLACED}" == true ]]; then
      rm -f -- "${CREDENTIAL_STATE_FILE}"
    fi
    if [[ "${CREDENTIAL_STATE_PREVIOUS_ARCHIVED}" == true ]]; then
      mv "${CONFIG_RECOVERY_ARCHIVE}/bootstrap.env" "${CREDENTIAL_STATE_FILE}" >/dev/null 2>&1 || true
    fi
    if [[ "${KEYCLOAK_CONFIG_REPLACED}" == true ]]; then
      rm -rf -- "${PERSISTENT_ROOT}/generated/02-keycloak-setup"
    fi
    if [[ "${KEYCLOAK_PREVIOUS_ARCHIVED}" == true ]]; then
      mv "${CONFIG_RECOVERY_ARCHIVE}/02-keycloak-setup" \
        "${PERSISTENT_ROOT}/generated/02-keycloak-setup" >/dev/null 2>&1 || true
    fi
    if [[ "${INFRA_CONFIG_REPLACED}" == true ]]; then
      rm -rf -- "${PERSISTENT_ROOT}/generated/01-infrastructure"
    fi
    if [[ "${INFRA_PREVIOUS_ARCHIVED}" == true ]]; then
      mv "${CONFIG_RECOVERY_ARCHIVE}/01-infrastructure" \
        "${PERSISTENT_ROOT}/generated/01-infrastructure" >/dev/null 2>&1 || true
    fi
  fi
  if [[ "${RESTORE_COMMITTED}" != true ]]; then
    for volume in "${CREATED_VOLUMES[@]}"; do
      docker volume rm "${volume}" >/dev/null 2>&1 || true
    done
  fi
  if [[ -n "${TEMP_ROOT}" && -d "${TEMP_ROOT}" ]]; then
    rm -rf -- "${TEMP_ROOT}"
  fi
}
trap cleanup EXIT

validate_integrity() {
  local -a args=(
    "${INTEGRITY_TOOL}"
    --backup-dir "${BACKUP_DIR}"
    --output "${TEMP_ROOT}/integrity.json"
  )
  if [[ "${ALLOW_LEGACY_MANIFEST_BUG}" == true ]]; then
    args+=(--allow-legacy-text-manifest-finalization-bug)
  fi
  python3 "${args[@]}"
}

assert_persistent_runtime_absent() {
  local name
  for name in "${ALL_PERSISTENT_CONTAINERS[@]}"; do
    if docker container inspect "${name}" >/dev/null 2>&1; then
      fail "persistent container boundary is not empty"
    fi
  done
  for name in "${ALL_PERSISTENT_VOLUMES[@]}"; do
    if docker volume inspect "${name}" >/dev/null 2>&1; then
      fail "persistent volume boundary is not empty"
    fi
  done
}

create_volume() {
  local name="$1"
  docker volume create "${name}" >/dev/null
  CREATED_VOLUMES+=("${name}")
}

restore_archive_volume() {
  local volume="$1" archive="$2"
  create_volume "${volume}"
  docker run --rm \
    -v "${volume}:/target" \
    -v "${BACKUP_DIR}:/backup:ro" \
    "${HELPER_IMAGE}" \
    sh -c "tar -C /target -xzf /backup/${archive}" >/dev/null
}

bootstrap_value() {
  local name="$1" bootstrap_file="$2"
  # shellcheck disable=SC2016
  env -i HOME="${HOME}" PATH="${PATH}" bash -c '
    set -euo pipefail
    source "$1"
    value="${!2:-}"
    [[ -n "${value}" ]]
    printf "%s" "${value}"
  ' _ "${bootstrap_file}" "${name}"
}

restore_postgres() {
  local bootstrap_file="$1" db_admin_username db_admin_password ready
  db_admin_username="$(bootstrap_value TF_VAR_db_admin_username "${bootstrap_file}")"
  db_admin_password="$(bootstrap_value TF_VAR_db_admin_password "${bootstrap_file}")"
  [[ "${db_admin_username}" =~ ^[A-Za-z_][A-Za-z0-9_]*$ ]] || fail "restored database administrator name is invalid"

  DB_ADMIN_USERNAME="${db_admin_username}" python3 - \
    "${BACKUP_DIR}/postgres.sql" "${TEMP_ROOT}/postgres.sql" <<'PY'
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

  create_volume weave_db_data
  TEMP_CONTAINER="weave-private-restore-db-$(date -u +%Y%m%dT%H%M%SZ)-$$"
  docker run -d \
    --name "${TEMP_CONTAINER}" \
    -e "POSTGRES_USER=${db_admin_username}" \
    -e POSTGRES_DB=postgres \
    -e "POSTGRES_PASSWORD=${db_admin_password}" \
    -v weave_db_data:/var/lib/postgresql/data \
    "${POSTGRES_IMAGE}" >/dev/null

  ready=false
  for _ in $(seq 1 60); do
    if docker exec "${TEMP_CONTAINER}" pg_isready -U "${db_admin_username}" -d postgres >/dev/null 2>&1; then
      ready=true
      break
    fi
    sleep 1
  done
  [[ "${ready}" == true ]] || fail "temporary restore database did not become ready"

  docker exec -e "PGPASSWORD=${db_admin_password}" -i "${TEMP_CONTAINER}" \
    psql -v ON_ERROR_STOP=1 -U "${db_admin_username}" -d postgres \
    <"${TEMP_ROOT}/postgres.sql" >"${TEMP_ROOT}/postgres-restore.log" 2>&1 ||
    fail "private PostgreSQL replay failed"

  SERVICE_DATABASE_COUNT="$(docker exec -e "PGPASSWORD=${db_admin_password}" "${TEMP_CONTAINER}" \
    psql -U "${db_admin_username}" -d postgres -Atqc \
    "SELECT count(*) FROM pg_database WHERE datname IN ('weave','weave_backend','weave_keycloak','weave_mas','weave_synapse');")"
  [[ "${SERVICE_DATABASE_COUNT}" == 5 ]] || fail "restored service database set is incomplete"

  RECORDED_SUBJECT="$(tr -d '\r\n' <"${PERSISTENT_ROOT}/persistent-member.subject")"
  [[ "${RECORDED_SUBJECT}" =~ ^[0-9a-fA-F-]{36}$ ]] || fail "recorded persistent member subject is invalid"
  [[ "${WEAVE_DOGFOOD_MEMBER_USERNAME:-}" =~ ^[A-Za-z0-9._@+-]+$ ]] ||
    fail "protected dogfood member username is required and invalid"

  RECORDED_SUBJECT_MATCHES="$(docker exec -e "PGPASSWORD=${db_admin_password}" "${TEMP_CONTAINER}" \
    psql -U "${db_admin_username}" -d weave_keycloak -Atqc \
    "SELECT count(*) FROM user_entity WHERE id = '${RECORDED_SUBJECT}';")"
  PROTECTED_USERNAME_MATCHES="$(docker exec -e "PGPASSWORD=${db_admin_password}" "${TEMP_CONTAINER}" \
    psql -U "${db_admin_username}" -d weave_keycloak -Atqc \
    "SELECT count(*) FROM user_entity u JOIN realm r ON r.id=u.realm_id WHERE r.name='weave' AND lower(u.username)=lower('${WEAVE_DOGFOOD_MEMBER_USERNAME}');")"
  RESTORED_TEST_MATCHES="$(docker exec -e "PGPASSWORD=${db_admin_password}" "${TEMP_CONTAINER}" \
    psql -U "${db_admin_username}" -d weave_keycloak -Atqc \
    "SELECT count(*) FROM user_entity u JOIN realm r ON r.id=u.realm_id WHERE r.name='weave' AND u.username='test';")"
  OTHER_HUMAN_IDENTITIES="$(docker exec -e "PGPASSWORD=${db_admin_password}" "${TEMP_CONTAINER}" \
    psql -U "${db_admin_username}" -d weave_keycloak -Atqc \
    "SELECT count(*) FROM user_entity u JOIN realm r ON r.id=u.realm_id WHERE r.name='weave' AND u.username NOT LIKE 'service-account-%' AND u.username<>'test';")"

  docker rm -f "${TEMP_CONTAINER}" >/dev/null
  TEMP_CONTAINER=""
}

prepare_generated_config() {
  mkdir -p "${TEMP_ROOT}/generated"
  tar -C "${TEMP_ROOT}/generated" -xzf "${BACKUP_DIR}/generated-config-secrets.tgz"
  [[ -s "${TEMP_ROOT}/generated/.generated/bootstrap.env" ]] || fail "restored bootstrap credential state is missing"
  [[ -d "${TEMP_ROOT}/generated/01-infrastructure/.generated" ]] || fail "restored infrastructure generated assets are missing"
  # The Keycloak OpenTofu stage has no required generated runtime files. Older
  # valid backups therefore omit its empty .generated directory.
  mkdir -p "${TEMP_ROOT}/generated/02-keycloak-setup/.generated"
  [[ -s "${TEMP_ROOT}/generated/01-infrastructure/.generated/caddy/certs/weave.test.pem" ]] ||
    fail "restored dogfood TLS certificate is missing"
  [[ -s "${TEMP_ROOT}/generated/01-infrastructure/.generated/caddy/certs/weave.test-key.pem" ]] ||
    fail "restored dogfood TLS key is missing"
}

commit_generated_config() {
  local staging_root timestamp
  timestamp="$(date -u +%Y%m%dT%H%M%SZ)"
  CONFIG_RECOVERY_ARCHIVE="${PERSISTENT_ROOT}/identity-recovery/pre-platform-restore-${timestamp}-$$"
  staging_root="${PERSISTENT_ROOT}/identity-recovery/platform-restore-stage-${timestamp}-$$"
  mkdir -p "${PERSISTENT_ROOT}/generated" "${CONFIG_RECOVERY_ARCHIVE}" "${staging_root}" \
    "$(dirname -- "${CREDENTIAL_STATE_FILE}")"
  chmod 700 "${PERSISTENT_ROOT}" "${PERSISTENT_ROOT}/generated" "${PERSISTENT_ROOT}/identity-recovery" \
    "${CONFIG_RECOVERY_ARCHIVE}" "${staging_root}"

  cp -R -p "${TEMP_ROOT}/generated/01-infrastructure/.generated" \
    "${staging_root}/01-infrastructure"
  cp -R -p "${TEMP_ROOT}/generated/02-keycloak-setup/.generated" \
    "${staging_root}/02-keycloak-setup"
  install -m 0600 "${TEMP_ROOT}/generated/.generated/bootstrap.env" "${staging_root}/bootstrap.env"
  CONFIG_COMMIT_STARTED=true

  if [[ -e "${PERSISTENT_ROOT}/generated/01-infrastructure" ]]; then
    mv "${PERSISTENT_ROOT}/generated/01-infrastructure" "${CONFIG_RECOVERY_ARCHIVE}/01-infrastructure"
    INFRA_PREVIOUS_ARCHIVED=true
  fi
  mv "${staging_root}/01-infrastructure" "${PERSISTENT_ROOT}/generated/01-infrastructure"
  INFRA_CONFIG_REPLACED=true
  if [[ -e "${PERSISTENT_ROOT}/generated/02-keycloak-setup" ]]; then
    mv "${PERSISTENT_ROOT}/generated/02-keycloak-setup" "${CONFIG_RECOVERY_ARCHIVE}/02-keycloak-setup"
    KEYCLOAK_PREVIOUS_ARCHIVED=true
  fi
  mv "${staging_root}/02-keycloak-setup" "${PERSISTENT_ROOT}/generated/02-keycloak-setup"
  KEYCLOAK_CONFIG_REPLACED=true
  if [[ -f "${CREDENTIAL_STATE_FILE}" ]]; then
    mv "${CREDENTIAL_STATE_FILE}" "${CONFIG_RECOVERY_ARCHIVE}/bootstrap.env"
    CREDENTIAL_STATE_PREVIOUS_ARCHIVED=true
  fi
  mv "${staging_root}/bootstrap.env" "${CREDENTIAL_STATE_FILE}"
  CREDENTIAL_STATE_REPLACED=true
  rmdir "${staging_root}"
}

write_restore_evidence() {
  local integrity legacy_reconciled backup_id_sha identity_restorable sole_bootstrap temporary
  integrity="${TEMP_ROOT}/integrity.json"
  legacy_reconciled="$(jq -r '.legacyTextManifestFinalizationBugReconciled' "${integrity}")"
  backup_id_sha="$(jq -r '.backupIdSha256' "${integrity}")"
  identity_restorable=false
  [[ "${RECORDED_SUBJECT_MATCHES}" == 1 && "${PROTECTED_USERNAME_MATCHES}" == 1 ]] && identity_restorable=true
  sole_bootstrap=false
  if [[ "${RECORDED_SUBJECT_MATCHES}" == 0 && "${PROTECTED_USERNAME_MATCHES}" == 0 && \
      "${RESTORED_TEST_MATCHES}" == 1 && "${OTHER_HUMAN_IDENTITIES}" == 0 ]]; then
    sole_bootstrap=true
  fi

  temporary="${TEMP_ROOT}/platform-restore.json"
  jq -n \
    --arg backupIdSha256 "${backup_id_sha}" \
    --arg restoredAt "$(date -u '+%Y-%m-%dT%H:%M:%SZ')" \
    --arg recordedSubjectSha256 "$(printf '%s' "${RECORDED_SUBJECT}" | shasum -a 256 | awk '{print $1}')" \
    --arg protectedUsernameSha256 "$(printf '%s' "${WEAVE_DOGFOOD_MEMBER_USERNAME}" | shasum -a 256 | awk '{print $1}')" \
    --argjson legacyTextManifestFinalizationBugReconciled "${legacy_reconciled}" \
    --argjson identityRestorableForRecordedMember "${identity_restorable}" \
    --argjson soleRestoredDisposableBootstrapIdentity "${sole_bootstrap}" '
    {
      schemaVersion:"weave.dogfood.platform-private-restore.v1",
      status:"passed",
      backupIdSha256:$backupIdSha256,
      restoredAt:$restoredAt,
      integrity:{
        allRequiredArtifactsVerified:true,
        legacyTextManifestFinalizationBugReconciled:$legacyTextManifestFinalizationBugReconciled
      },
      platformData:{serviceDatabases:5,restoredArchiveVolumes:5,status:"passed"},
      postgresBootstrap:{restoredPersistentAdministrator:true,temporaryAdministratorCreated:false},
      identity:{
        recordedSubjectSha256:$recordedSubjectSha256,
        protectedUsernameSha256:$protectedUsernameSha256,
        identityRestorableForRecordedMember:$identityRestorableForRecordedMember,
        soleRestoredDisposableBootstrapIdentity:$soleRestoredDisposableBootstrapIdentity
      },
      generatedRuntimeAssets:{bootstrapCredentialsRestored:true,tlsIdentityRestored:true},
      mailpit:{historyRestored:false,reason:"not-in-backup-artifact-set"},
      privateArtifactContentIncluded:false,
      supportSafe:true
    }
  ' >"${temporary}"
}

install_restore_evidence() {
  mkdir -p "$(dirname -- "${EVIDENCE_FILE}")"
  if [[ -f "${EVIDENCE_FILE}" ]]; then
    mv "${EVIDENCE_FILE}" "${CONFIG_RECOVERY_ARCHIVE}/platform-restore.json"
    EVIDENCE_PREVIOUS_ARCHIVED=true
  fi
  install -m 0600 "${TEMP_ROOT}/platform-restore.json" "${EVIDENCE_FILE}"
  EVIDENCE_REPLACED=true
}

main() {
  if [[ "${BACKUP_DIR}" == --help || "${BACKUP_DIR}" == -h ]]; then
    usage
    exit 0
  fi
  [[ -n "${BACKUP_DIR}" && -d "${BACKUP_DIR}" ]] || fail "private backup directory is required"
  require_command python3
  require_command tar
  TEMP_ROOT="$(mktemp -d "${TMPDIR:-/tmp}/weave-private-restore.XXXXXX")"
  chmod 700 "${TEMP_ROOT}"
  validate_integrity
  prepare_generated_config
  if [[ "${PREFLIGHT_ONLY}" == true ]]; then
    RESTORE_COMMITTED=true
    log "PRIVATE_DOGFOOD_RESTORE status=preflight-passed mutation=false supportSafe=true"
    return
  fi

  [[ "${RESTORE_SCOPE}" == persistent-dogfood ]] || fail "apply requires WEAVE_DOGFOOD_DEPLOYMENT_SCOPE=persistent-dogfood"
  [[ "${RESTORE_CONFIRMATION}" == restore-private-dogfood-backup ]] || fail "apply requires the exact private restore confirmation"
  [[ -s "${PERSISTENT_ROOT}/opentofu-state/01-infrastructure.tfstate" && \
     -s "${PERSISTENT_ROOT}/opentofu-state/02-keycloak-setup.tfstate" ]] ||
    fail "persistent OpenTofu state is unavailable"
  [[ -s "${PERSISTENT_ROOT}/persistent-member.subject" ]] || fail "recorded persistent member subject is unavailable"
  require_command docker
  require_command jq
  require_command shasum
  assert_persistent_runtime_absent

  local entry volume archive
  for entry in "${ARCHIVE_VOLUMES[@]}"; do
    IFS=: read -r volume archive <<<"${entry}"
    restore_archive_volume "${volume}" "${archive}"
  done
  restore_postgres "${TEMP_ROOT}/generated/.generated/bootstrap.env"
  write_restore_evidence
  commit_generated_config
  install_restore_evidence
  CONFIG_COMMIT_COMPLETED=true
  RESTORE_COMMITTED=true
  log "PRIVATE_DOGFOOD_RESTORE status=passed restoredArchiveVolumes=5 serviceDatabases=5 mailpitHistoryRestored=false supportSafe=true"
}

if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then
  main "$@"
fi
