#!/usr/bin/env bash
# shellcheck shell=bash

set -euo pipefail

ROOT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
readonly ROOT_DIR
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
and raw Nextcloud readiness.

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

require_artifact() {
  local backup_dir="$1"
  local name="$2"
  [[ -s "${backup_dir}/${name}" ]] || fail "Backup artifact is missing or empty: ${backup_dir}/${name}"
}


write_restore_receipt() {
  local backup_dir="$1"
  local validation_mode="$2"
  local status="$3"
  local destroy_performed="$4"
  local domain_data_status="$5"
  local release_eligible="$6"
  local chat_appservice_status="$7"
  local output="${RESTORE_RECEIPT_OUTPUT}"
  if [[ -z "${output}" ]]; then
    if [[ -n "${backup_dir}" ]]; then
      output="${backup_dir}/RestoreReceipt.json"
    else
      output="${ROOT_DIR}/.generated/RestoreReceipt.json"
    fi
  fi
  mkdir -p "$(dirname -- "${output}")"
  RESTORE_CREATED_AT="$(date -u +%Y%m%dT%H%M%SZ)"     RESTORE_BACKUP_DIR="${backup_dir}"     RESTORE_VALIDATION_MODE="${validation_mode}"     RESTORE_STATUS="${status}"     RESTORE_DESTROY_PERFORMED="${destroy_performed}"     RESTORE_DOMAIN_DATA_STATUS="${domain_data_status}"     RESTORE_RELEASE_ELIGIBLE="${release_eligible}"     RESTORE_CHAT_APPSERVICE_STATUS="${chat_appservice_status}"     RESTORE_RECEIPT_OUTPUT_PATH="${output}"     python3 - <<'PYJSON'
import json
import os
from pathlib import Path

backup_dir = os.environ.get("RESTORE_BACKUP_DIR") or ""
destroy_performed = os.environ["RESTORE_DESTROY_PERFORMED"] == "true"
domain_status = os.environ["RESTORE_DOMAIN_DATA_STATUS"]
release_eligible = os.environ["RESTORE_RELEASE_ELIGIBLE"] == "true"
manifest_path = Path(backup_dir) / "BackupManifest.json" if backup_dir else None
manifest_ref = str(manifest_path) if manifest_path and manifest_path.exists() else "not-provided"
receipt = {
    "artifactKind": "weave-restore-receipt-v1",
    "issue": 639,
    "supportSafe": True,
    "createdAt": os.environ["RESTORE_CREATED_AT"],
    "backupManifestRef": manifest_ref,
    "restoreRunId": f"restore-smoke-{os.environ['RESTORE_CREATED_AT']}",
    "validationMode": os.environ["RESTORE_VALIDATION_MODE"],
    "status": os.environ["RESTORE_STATUS"],
    "destroyStep": {
        "performed": destroy_performed,
        "reason": "operator-declared approved disposable restore rehearsal" if destroy_performed else "not performed by restore-smoke.sh",
    },
    "checks": [
        {"name": "backup_artifacts_present", "status": "passed" if backup_dir else "not_run"},
        {"name": "backup_manifest_present", "status": "passed" if manifest_ref != "not-provided" else "not_provided"},
        {"name": "post_restore_operator_check", "status": "passed" if os.environ["RESTORE_VALIDATION_MODE"] != "artifacts_only" else "not_run"},
        {"name": "matrix_chat_appservice_registration_and_secret_mounts", "status": os.environ["RESTORE_CHAT_APPSERVICE_STATUS"]},
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
  require_artifact "${backup_dir}" MANIFEST.txt
  require_artifact "${backup_dir}" postgres.sql
  require_artifact "${backup_dir}" nextcloud-data.tgz
  require_artifact "${backup_dir}" matrix-synapse-data.tgz
  require_artifact "${backup_dir}" caddy-data.tgz
  require_artifact "${backup_dir}" caddy-config.tgz
  require_artifact "${backup_dir}" keycloak-data.tgz
  require_artifact "${backup_dir}" generated-config-secrets.tgz
  if [[ -s "${backup_dir}/BackupManifest.json" ]]; then
    log "Backup manifest presence check passed for ${backup_dir}/BackupManifest.json"
  else
    log "Backup manifest not provided for ${backup_dir}; RestoreReceipt will record backupManifestRef=not-provided."
  fi

  log "Backup artifact presence check passed for ${backup_dir}"
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
    write_restore_receipt "${backup_dir}" "artifacts_only" "artifact_preflight_passed_not_restore_proof" "false" "not_proven" "false" "archived_not_runtime_verified"
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

  write_restore_receipt "${backup_dir}" "post_restore_live" "passed" "${WEAVE_RESTORE_SMOKE_DESTROY_PERFORMED:-false}" "${WEAVE_RESTORE_SMOKE_DOMAIN_DATA_RECOVERED:-not_proven}" "${WEAVE_RESTORE_SMOKE_RELEASE_ELIGIBLE:-false}" "passed"
  log "Restore smoke passed: backend, Keycloak, Matrix/MAS, default Matrix rooms, and raw Nextcloud checks are healthy."
}

main "$@"
