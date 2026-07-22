#!/usr/bin/env bash
# shellcheck shell=bash

set -euo pipefail

ROOT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
readonly ROOT_DIR
BOOTSTRAP_ENV_FILE="${ROOT_DIR}/.generated/bootstrap.env"
DEFAULT_OUTPUT_DIR="${ROOT_DIR}/.generated/backups"
BACKUP_OUTPUT_DIR="${WEAVE_BACKUP_DIR:-${DEFAULT_OUTPUT_DIR}}"
HELPER_IMAGE="${WEAVE_BACKUP_HELPER_IMAGE:-alpine:3.20}"
CREATED_AT="$(date -u +%Y%m%dT%H%M%SZ)"
BACKUP_BASENAME="weave-backup-${CREATED_AT}"
BACKUP_DIR=""

readonly VOLUME_BACKUPS=(
  "weave_nextcloud_data:nextcloud-data.tgz:Nextcloud files/calendar application data"
  "weave_synapse_data:matrix-synapse-data.tgz:Matrix/Synapse media and local data"
  "weave_caddy_data:caddy-data.tgz:Caddy ACME/TLS runtime data"
  "weave_caddy_config:caddy-config.tgz:Caddy runtime config"
  "weave_keycloak_data:keycloak-data.tgz:Keycloak container-side runtime data"
)

log() {
  printf '%s\n' "$*"
}

fail() {
  printf '%s\n' "$*" >&2
  exit 1
}

usage() {
  cat <<USAGE
Usage: bash weave-workspace/backup.sh [output-dir]

Creates a backup artifact set for operator-managed restore rehearsals.
The output contains secrets and production data. It is not a support bundle and must
not be attached to issues or shared with support.

Environment:
  WEAVE_BACKUP_DIR           Output parent directory (default: .generated/backups)
  WEAVE_BACKUP_HELPER_IMAGE  Container image used for read-only volume archives (default: alpine:3.20)
USAGE
}

require_command() {
  command -v "$1" >/dev/null 2>&1 || fail "Missing required command: $1"
}

load_bootstrap_env() {
  if [[ -f "${BOOTSTRAP_ENV_FILE}" ]]; then
    # shellcheck disable=SC1090
    source "${BOOTSTRAP_ENV_FILE}"
  fi
}

require_container_running() {
  local name="$1"
  local state

  state="$(docker inspect --format '{{.State.Status}}' "${name}" 2>/dev/null || true)"
  [[ "${state}" == "running" ]] || fail "Required container is not running: ${name}"
}

require_volume() {
  local name="$1"
  docker volume inspect "${name}" >/dev/null 2>&1 || fail "Required Docker volume not found: ${name}"
}

write_manifest_header() {
  cat >"${BACKUP_DIR}/MANIFEST.txt" <<MSG
Weave backup
Created UTC: ${CREATED_AT}

SECURITY: This directory contains secrets and user/workspace data. Keep it encrypted
or operator-readable only. Do not attach these artifacts to GitHub issues or support
requests. Use support-bundle.sh for redacted diagnostics.

Restore smoke after a restore or reprovisioning rehearsal:
  bash weave-workspace/restore-smoke.sh ${BACKUP_DIR}

Artifacts:
MSG
}

append_manifest() {
  printf -- '- %s: %s\n' "$1" "$2" >>"${BACKUP_DIR}/MANIFEST.txt"
}

write_backup_manifest_json() {
  BACKUP_CREATED_AT="${CREATED_AT}" BACKUP_ID="${BACKUP_BASENAME}" BACKUP_DIR="${BACKUP_DIR}" python3 - <<'PYJSON'
import hashlib
import json
import os
from pathlib import Path

backup_dir = Path(os.environ["BACKUP_DIR"])


def artifact_digest(path: Path) -> tuple[str, int]:
    digest = hashlib.sha256()
    size = 0
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            size += len(chunk)
            digest.update(chunk)
    return digest.hexdigest(), size
required = [
    ("MANIFEST.txt", "text-manifest"),
    ("postgres.sql", "postgres-dump"),
    ("nextcloud-data.tgz", "docker-volume-archive"),
    ("matrix-synapse-data.tgz", "docker-volume-archive"),
    ("caddy-data.tgz", "docker-volume-archive"),
    ("caddy-config.tgz", "docker-volume-archive"),
    ("keycloak-data.tgz", "docker-volume-archive"),
    ("generated-config-secrets.tgz", "generated-config-secrets"),
]
artifacts = []
for name, kind in required:
    path = backup_dir / name
    sha256, size = artifact_digest(path)
    artifacts.append(
        {
            "path": name,
            "kind": kind,
            "sha256": sha256,
            "bytes": size,
            "requiredForRestore": True,
        }
    )
manifest = {
    "artifactKind": "weave-backup-manifest-v1",
    "issue": 639,
    "supportSafe": False,
    "createdAt": os.environ["BACKUP_CREATED_AT"],
    "backupId": os.environ["BACKUP_ID"],
    "scope": {
        "environment": os.environ.get("WEAVE_BACKUP_ENVIRONMENT", "operator-managed-stack"),
        "domains": ["identity-idm", "agent-runtime-control", "chat", "files", "calendar", "health"],
        "artifactsContainSecretsOrMemberData": True,
        "shareExternally": False,
    },
    "artifacts": artifacts,
    "limitations": [
        "Backup artifacts contain secrets or member/workspace data and must stay private.",
        "BackupManifest is not restore proof; collect RestoreReceipt after an approved restore rehearsal.",
    ],
}
(backup_dir / "BackupManifest.json").write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
PYJSON
}

finalize_text_manifest() {
  append_manifest "BackupManifest.json" "Machine-readable private backup manifest with artifact checksums and restore scope"

  cat >>"${BACKUP_DIR}/MANIFEST.txt" <<'MSG'

Notes:
- This backup intentionally uses pg_dumpall for PostgreSQL-backed service data instead
  of copying the live postgres data volume.
- Matrix room/event state is in postgres.sql; Matrix media/local files are in
  matrix-synapse-data.tgz.
- Nextcloud metadata is in postgres.sql; file/calendar application data is in
  nextcloud-data.tgz.
- Caddy artifacts are included for ACME/TLS continuity when applicable.
- Generated config/secrets are included because restore/reprovisioning may need them;
  this includes the stable Synapse Application Service registration/tokens plus
  the Agent Runtime Control signing root, runtime-state
  wrapping root, policy, and workload-credential SecretRefs. Keycloak, PostgreSQL,
  and those private ARC assets are one restore consistency set. Disposable Chat E2E
  proof credentials are explicitly excluded and cannot be restored. Keep this backup private.
MSG
}


backup_postgres() {
  local db_user="${TF_VAR_db_admin_username:-weave_admin}"
  local db_password="${TF_VAR_db_admin_password:-}"
  local target="${BACKUP_DIR}/postgres.sql"

  [[ -n "${db_password}" ]] || fail "TF_VAR_db_admin_password is required; run install.sh first or provide the generated bootstrap env."
  require_container_running weave-db

  log "Backing up PostgreSQL service databases to postgres.sql"
  docker exec -e "PGPASSWORD=${db_password}" weave-db pg_dumpall -U "${db_user}" >"${target}"
  if [[ "${TF_VAR_agent_runtime_enabled:-false}" == "true" ]]; then
    grep -Fq 'weave_agent_runtime_cells' "${target}" ||
      fail "PostgreSQL backup is missing the Agent Runtime Control store."
    grep -Fq 'weave_agent_runtime_state_generations' "${target}" ||
      fail "PostgreSQL backup is missing encrypted external runtime-state storage."
  fi
  append_manifest "postgres.sql" "PostgreSQL dump for Keycloak, Agent Runtime Control and encrypted runtime state, MAS, Synapse, Nextcloud, and Weave backend service databases"
}

require_agent_runtime_consistency_set() {
  [[ "${TF_VAR_agent_runtime_enabled:-false}" == "true" ]] || return 0

  local root="${ROOT_DIR}/01-infrastructure/.generated/agent-runtime"
  [[ -s "${root}/profile-signing/runtime-profile-signing-keys.json" ]] ||
    fail "Agent Runtime Control signing trust root is incomplete; refusing a partial backup."
  [[ -n "$(find "${root}/profile-signing" -maxdepth 1 -type f -name 'key-rpk_*.pk8' -size +0c -print -quit 2>/dev/null)" ]] ||
    fail "Agent Runtime Control signing private material is incomplete; refusing a partial backup."
  [[ -s "${root}/state-wrapping/runtime-state-wrapping-keys.json" ]] ||
    fail "Agent Runtime Control state-wrapping trust root is incomplete; refusing a partial backup."
  [[ -n "$(find "${root}/state-wrapping/keys" -maxdepth 1 -type f -name 'rsk_*.key' -size +0c -print -quit 2>/dev/null)" ]] ||
    fail "Agent Runtime Control state-wrapping private material is incomplete; refusing a partial backup."
  [[ -s "${root}/runtime-policy.json" ]] ||
    fail "Agent Runtime Control runtime policy is missing; refusing a partial backup."
  [[ -s "${root}/credentials/weave/agent-runtime/admin/keycloak" ]] ||
    fail "Agent Runtime Control Keycloak administration SecretRef is missing; refusing a partial backup."
  [[ "${TF_VAR_agent_runtime_keycloak_organization_id:-}" =~ ^[0-9a-fA-F-]{36}$ ]] ||
    fail "Agent Runtime Control Keycloak organization authority is unresolved; refusing a partial backup."
}

backup_volume() {
  local volume="$1"
  local archive_name="$2"
  local description="$3"

  require_volume "${volume}"
  log "Archiving Docker volume ${volume} to ${archive_name}"
  docker run --rm \
    -v "${volume}:/source:ro" \
    -v "${BACKUP_DIR}:/backup" \
    "${HELPER_IMAGE}" \
    sh -c "tar -C /source -czf /backup/${archive_name} ."
  append_manifest "${archive_name}" "${description} from Docker volume ${volume}"
}

backup_generated_config() {
  local -a generated_paths=()
  local target="${BACKUP_DIR}/generated-config-secrets.tgz"

  [[ -f "${ROOT_DIR}/.generated/bootstrap.env" ]] && generated_paths+=(".generated/bootstrap.env")
  [[ -f "${ROOT_DIR}/.generated/app-config.env" ]] && generated_paths+=(".generated/app-config.env")
  [[ -d "${ROOT_DIR}/.generated/tls" ]] && generated_paths+=(".generated/tls")
  [[ -d "${ROOT_DIR}/01-infrastructure/.generated" ]] && generated_paths+=("01-infrastructure/.generated")
  [[ -d "${ROOT_DIR}/02-keycloak-setup/.generated" ]] && generated_paths+=("02-keycloak-setup/.generated")

  ((${#generated_paths[@]} > 0)) || fail "No generated config/secrets were found under weave-workspace/.generated or Terraform stage .generated directories."

  log "Archiving generated config/secrets metadata to generated-config-secrets.tgz"
  tar -C "${ROOT_DIR}" -hczf "${target}" \
    --exclude='chat-provider-proof.token' \
    --exclude='*/chat-provider-proof.token' \
    --exclude='.generated/isolated-e2e' \
    "${generated_paths[@]}"
  append_manifest "generated-config-secrets.tgz" "Private generated runtime consistency set required for restore/reprovisioning, including TLS, Matrix Application Service state, and Agent Runtime Control trust roots and workload credentials; disposable Chat E2E proof credentials are excluded"
}

create_backup() {
  local output_dir="$1"
  umask 077
  mkdir -p "${output_dir}"
  BACKUP_DIR="${output_dir}/${BACKUP_BASENAME}"
  mkdir -p "${BACKUP_DIR}"

  write_manifest_header
  backup_postgres

  local entry
  for entry in "${VOLUME_BACKUPS[@]}"; do
    IFS=: read -r volume archive description <<<"${entry}"
    backup_volume "${volume}" "${archive}" "${description}"
  done

  backup_generated_config
  # Finalize MANIFEST.txt before hashing it into BackupManifest.json. Mutating the
  # text manifest after this point would invalidate the recorded restore checksum.
  finalize_text_manifest
  write_backup_manifest_json

  log "Backup written to ${BACKUP_DIR}"
}

main() {
  if [[ "${1:-}" == "--help" || "${1:-}" == "-h" ]]; then
    usage
    exit 0
  fi

  require_command docker
  require_command tar
  load_bootstrap_env
  [[ "${TF_VAR_chat_e2e_proof_enabled:-false}" != "true" ]] ||
    fail "Backups are disabled for disposable Chat E2E proof namespaces; destroy the isolated namespace instead."
  require_agent_runtime_consistency_set

  local output_dir="${1:-${BACKUP_OUTPUT_DIR}}"
  create_backup "${output_dir}"
}

if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then
  main "$@"
fi
