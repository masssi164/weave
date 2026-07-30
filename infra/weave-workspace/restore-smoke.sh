#!/usr/bin/env bash
# shellcheck shell=bash

set -euo pipefail

ROOT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd -- "${ROOT_DIR}/../.." && pwd)"
readonly ROOT_DIR REPO_ROOT
readonly INTEGRITY_TOOL="${REPO_ROOT}/tools/private_backup_integrity.py"
REPROVISION_MATRIX="${WEAVE_RESTORE_SMOKE_REPROVISION_MATRIX:-false}"
ARTIFACTS_ONLY="${WEAVE_RESTORE_SMOKE_ARTIFACTS_ONLY:-false}"
RESTORE_RECEIPT_OUTPUT="${WEAVE_RESTORE_RECEIPT_OUTPUT:-}"

log() {
  printf '%s\n' "$*"
}

fail() {
  printf '%s\n' "$*" >&2
  exit 1
}

usage() {
  cat <<USAGE
Usage: bash weave-workspace/restore-smoke.sh [backup-dir]

Verifies a stack after an operator restore or clean reprovisioning rehearsal.
This script does not restore data and never deletes volumes. It checks the same
minimum recovery contract that issue #36 requires: backend readiness, Keycloak
discovery, Matrix client versions and MAS discovery, default Matrix room aliases,
raw Nextcloud readiness, and the Agent Runtime Control restore/reconciliation boundary.

Arguments:
  backup-dir  Optional directory created by backup.sh. When provided, restore-smoke
              checks that the expected backup artifacts are present before probing
              the running stack.

Environment:
  WEAVE_RESTORE_SMOKE_REPROVISION_MATRIX=true  Re-run the idempotent Matrix default
                                               workspace provisioner before checks.
  WEAVE_RESTORE_SMOKE_ARTIFACTS_ONLY=true      Only validate backup artifact
                                               presence, then exit. This is a
                                               preflight/lint mode and does not
                                               prove restored service readiness.
USAGE
}

write_restore_receipt() {
  local backup_dir="$1"
  local validation_mode="$2"
  local status="$3"
  local destroy_performed="$4"
  local domain_data_status="$5"
  local chat_appservice_status="$6"
  local agent_runtime_status="$7"
  local output="${RESTORE_RECEIPT_OUTPUT}"
  if [[ -z "${output}" ]]; then
    if [[ -n "${backup_dir}" ]]; then
      output="${backup_dir}/RestoreReceipt.json"
    else
      output="${ROOT_DIR}/.generated/RestoreReceipt.json"
    fi
  fi
  mkdir -p "$(dirname -- "${output}")"
  RESTORE_CREATED_AT="$(date -u +%Y%m%dT%H%M%SZ)" \
    RESTORE_BACKUP_DIR="${backup_dir}" \
    RESTORE_VALIDATION_MODE="${validation_mode}" \
    RESTORE_STATUS="${status}" \
    RESTORE_DESTROY_PERFORMED="${destroy_performed}" \
    RESTORE_DOMAIN_DATA_STATUS="${domain_data_status}" \
    RESTORE_CHAT_APPSERVICE_STATUS="${chat_appservice_status}" \
    RESTORE_AGENT_RUNTIME_STATUS="${agent_runtime_status}" \
    RESTORE_RECEIPT_OUTPUT_PATH="${output}" \
    python3 - <<'PYJSON'
import hashlib
import json
import os
from pathlib import Path

backup_dir = os.environ.get("RESTORE_BACKUP_DIR") or ""
destroy_performed = os.environ["RESTORE_DESTROY_PERFORMED"] == "true"
domain_status = os.environ["RESTORE_DOMAIN_DATA_STATUS"]
validation_mode = os.environ["RESTORE_VALIDATION_MODE"]
status = os.environ["RESTORE_STATUS"]
chat_status = os.environ["RESTORE_CHAT_APPSERVICE_STATUS"]
agent_status = os.environ["RESTORE_AGENT_RUNTIME_STATUS"]
manifest_path = Path(backup_dir) / "BackupManifest.json" if backup_dir else None
manifest = json.loads(manifest_path.read_text(encoding="utf-8")) if manifest_path and manifest_path.exists() else None
manifest_digest = hashlib.sha256(manifest_path.read_bytes()).hexdigest() if manifest_path and manifest_path.exists() else None
backup_binding = None
if manifest is not None:
    backup_binding = {
        "manifestSha256": manifest_digest,
        "backupIdSha256": hashlib.sha256(manifest["backupId"].encode("utf-8")).hexdigest(),
        "candidateCommit": manifest["candidateCommit"],
        "profile": manifest["profile"],
        "composeProject": manifest["composeProject"],
    }
release_eligible = (
    manifest is not None
    and validation_mode == "post_restore_live"
    and status == "passed"
    and destroy_performed
    and domain_status == "passed"
    and chat_status == "passed"
    and agent_status == "passed"
)
receipt = {
    "schemaVersion": "weave.compose-restore-receipt.v2",
    "supportSafe": True,
    "generatedAt": os.environ["RESTORE_CREATED_AT"],
    "backupBinding": backup_binding,
    "restoreRunId": f"restore-smoke-{os.environ['RESTORE_CREATED_AT']}",
    "validationMode": validation_mode,
    "status": status,
    "destroyStep": {
        "performed": destroy_performed,
        "reason": "operator-declared approved disposable restore rehearsal" if destroy_performed else "not performed by restore-smoke.sh",
    },
    "checks": [
        {"name": "backup_artifacts_present", "status": "passed" if backup_dir else "not_run"},
        {"name": "backup_integrity_verified", "status": "passed" if manifest is not None else "not_provided"},
        {"name": "post_restore_operator_check", "status": "passed" if validation_mode != "artifacts_only" else "not_run"},
        {"name": "matrix_chat_appservice_registration_and_secret_mounts", "status": chat_status},
        {"name": "agent_runtime_consistency_set_and_live_reconciliation", "status": agent_status},
        {"name": "domain_data_recovered", "status": domain_status},
    ],
    "provesRestoredDomainData": domain_status == "passed" and destroy_performed,
    "releaseEligible": release_eligible,
    "limitations": [] if release_eligible else [
        "This receipt does not prove release-ready backup/restore unless destroyStep.performed=true and domain_data_recovered passed.",
    ],
}

Path(os.environ["RESTORE_RECEIPT_OUTPUT_PATH"]).write_text(json.dumps(receipt, indent=2) + "\n", encoding="utf-8")
PYJSON
  log "RestoreReceipt written to ${output}"
}

verify_agent_runtime_restore_boundary() {
  local destination
  local mount_writable
  local readiness
  for destination in \
    /run/weave-agent-runtime/profile-signing \
    /run/weave-agent-runtime/state-wrapping \
    /run/weave-agent-runtime/credentials; do
    mount_writable="$(docker inspect --format "{{range .Mounts}}{{if eq .Destination \"${destination}\"}}{{.RW}}{{end}}{{end}}" weave-backend 2>/dev/null || true)"
    [[ "${mount_writable}" == "true" ]] ||
      fail "The backend is missing one explicit writable Agent Runtime Control SecretRef mount."
  done

  docker exec weave-backend sh -c '
    test -s /run/weave-agent-runtime/profile-signing/runtime-profile-signing-keys.json &&
    test -s /run/weave-agent-runtime/state-wrapping/runtime-state-wrapping-keys.json &&
    test -s /run/weave-agent-runtime/credentials/weave/agent-runtime/admin/keycloak
  ' >/dev/null || fail "The backend cannot read the complete restored Agent Runtime Control private consistency set."

  if docker volume ls --format '{{.Name}}' |
    grep -Eiq '(^|[_-])weaver([_-].*)?(state|workspace|agent|cell)([_-]|$)|(^|[_-])agent[_-]runtime[_-](state|workspace|agent|cell)([_-]|$)'; then
    fail "A forbidden durable Weaver cell volume exists after restore."
  fi

  readiness="$(docker exec weave-backend sh -c '
    curl -fsS http://127.0.0.1:8080/api/health/ready 2>/dev/null ||
      wget -qO- http://127.0.0.1:8080/api/health/ready 2>/dev/null
  ' || true)"
  printf '%s' "${readiness}" | python3 -c '
import json, sys
data = json.load(sys.stdin)
checks = {item.get("key"): item.get("readiness") for item in data.get("checks", [])}
required = ("agent-runtime-state", "agent-runtime-workload-identities")
raise SystemExit(0 if data.get("status") == "up" and all(checks.get(key) == "ready" for key in required) else 1)
' || fail "Agent Runtime Control state and Keycloak workload reconciliation are not ready after restore."

  log "Verified the restored Agent Runtime Control consistency set, converged workload identities, and zero durable cell volumes."
}

verify_matrix_chat_appservice_runtime() {
  local container_name
  local mount_writable
  for container_name in weave-backend weave-synapse; do
    mount_writable="$(docker inspect --format '{{range .Mounts}}{{if eq .Destination "/run/weave-chat-appservice"}}{{.RW}}{{end}}{{end}}' "${container_name}" 2>/dev/null || true)"
    [[ "${mount_writable}" == "false" ]] ||
      fail "${container_name} does not have the private Matrix Chat Application Service runtime mounted read-only."
  done

  docker exec weave-synapse sh -c '
    test -r /run/weave-chat-appservice/registration.yaml &&
    grep -q "^rate_limited: true$" /run/weave-chat-appservice/registration.yaml &&
    grep -q "exclusive: true" /run/weave-chat-appservice/registration.yaml
  ' >/dev/null || fail "Synapse cannot read the narrow, rate-limited Matrix Chat Application Service registration."

  docker exec weave-backend sh -c '
    as_token="$(cat /run/weave-chat-appservice/as-token)"
    hs_token="$(cat /run/weave-chat-appservice/hs-token)"
    test -n "${as_token}" && test -n "${hs_token}" && test "${as_token}" != "${hs_token}"
  ' >/dev/null || fail "The backend cannot read two distinct Matrix Chat Application Service token files."

  log "Verified restored Matrix Chat Application Service registration and read-only secret mounts."
}

check_backup_dir() {
  local backup_dir="$1"

  [[ -d "${backup_dir}" ]] || fail "Backup directory not found: ${backup_dir}"
  python3 "${INTEGRITY_TOOL}" --backup-dir "${backup_dir}"
  log "Backup artifact integrity check passed for the candidate-bound Compose v3 consistency set."
}

main() {
  if [[ "${1:-}" == "--help" || "${1:-}" == "-h" ]]; then
    usage
    exit 0
  fi

  local backup_dir="${1:-}"
  if [[ -n "${backup_dir}" ]]; then
    check_backup_dir "${backup_dir}"
  fi

  if [[ "${ARTIFACTS_ONLY}" == "true" ]]; then
    [[ -n "${backup_dir}" ]] || fail "WEAVE_RESTORE_SMOKE_ARTIFACTS_ONLY=true requires a backup directory argument."
    write_restore_receipt "${backup_dir}" "artifacts_only" "artifact_preflight_passed_not_restore_proof" "false" "not_proven" "archived_not_runtime_verified" "archived_not_runtime_verified"
    log "Restore smoke artifact preflight passed. Service readiness was not checked in artifacts-only mode."
    exit 0
  fi

  if [[ "${REPROVISION_MATRIX}" == "true" ]]; then
    log "Re-running idempotent Matrix default workspace provisioner before restore smoke..."
    bash "${ROOT_DIR}/provision-matrix-default-workspace.sh"
  fi

  log "Running recovery readiness checks with operator-check.sh..."
  bash "${ROOT_DIR}/operator-check.sh"

  verify_matrix_chat_appservice_runtime
  verify_agent_runtime_restore_boundary

  write_restore_receipt "${backup_dir}" "post_restore_live" "passed" "${WEAVE_RESTORE_SMOKE_DESTROY_PERFORMED:-false}" "${WEAVE_RESTORE_SMOKE_DOMAIN_DATA_RECOVERED:-not_proven}" "passed" "passed"
  log "Restore smoke passed: backend, Keycloak, Agent Runtime Control, Matrix/MAS, default Matrix rooms, and raw Nextcloud checks are healthy."
}

main "$@"
