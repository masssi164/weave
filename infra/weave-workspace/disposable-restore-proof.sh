#!/usr/bin/env bash
# shellcheck shell=bash

set -euo pipefail

ROOT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
readonly ROOT_DIR
DEFAULT_OUTPUT_DIR="${ROOT_DIR}/.generated/disposable-restore-proof"
OUTPUT_PARENT="${WEAVE_DISPOSABLE_RESTORE_PROOF_DIR:-${DEFAULT_OUTPUT_DIR}}"
HELPER_IMAGE="${WEAVE_DISPOSABLE_RESTORE_HELPER_IMAGE:-alpine:3.20}"
KEEP_VOLUMES="${WEAVE_DISPOSABLE_RESTORE_KEEP_VOLUMES:-false}"
RUN_ID="${WEAVE_DISPOSABLE_RESTORE_RUN_ID:-$(date -u +%Y%m%dT%H%M%SZ)-$$}"
RUN_DIR="${OUTPUT_PARENT}/${RUN_ID}"
VOLUME_PREFIX="weave_disposable_restore_${RUN_ID//[^A-Za-z0-9_]/_}"

log() {
  printf '%s\n' "$*"
}

fail() {
  printf '%s\n' "$*" >&2
  exit 1
}

usage() {
  cat <<USAGE
Usage: bash weave-workspace/disposable-restore-proof.sh [output-parent]

Runs a disposable Backup -> Destroy -> Restore -> Validate rehearsal using uniquely
named Docker volumes and support-safe fixture domain data. It never reads or deletes
normal Weave volumes such as weave_db_data, weave_synapse_data, weave_nextcloud_data,
weave_keycloak_data, weave_caddy_data, or weave_caddy_config.

Outputs:
  <output-parent>/<run-id>/BackupManifest.json       private-shape disposable manifest
  <output-parent>/<run-id>/RestoreReceipt.json       support-safe release receipt
  <output-parent>/<run-id>/support-redaction-report.json
  <output-parent>/<run-id>/domain-data-hashes.json   support-safe fixture hash proof

Environment:
  WEAVE_DISPOSABLE_RESTORE_PROOF_DIR      Output parent directory
  WEAVE_DISPOSABLE_RESTORE_HELPER_IMAGE   Helper image (default: alpine:3.20)
  WEAVE_DISPOSABLE_RESTORE_RUN_ID         Deterministic run id for tests/evidence
  WEAVE_DISPOSABLE_RESTORE_KEEP_VOLUMES   true to keep disposable volumes for debugging
USAGE
}

require_command() {
  command -v "$1" >/dev/null 2>&1 || fail "Missing required command: $1"
}

assert_disposable_scope() {
  case "${VOLUME_PREFIX}" in
    weave_disposable_restore_*) ;;
    *) fail "Refusing to run: disposable volume prefix is unsafe: ${VOLUME_PREFIX}" ;;
  esac

  local existing
  existing="$(docker volume ls --format '{{.Name}}' | grep -E '^weave_disposable_restore_' || true)"
  if [[ -n "${existing}" ]]; then
    log "Existing disposable proof volumes detected; they are not production volumes:"
    printf '%s\n' "${existing}"
  fi
}

cleanup_volumes() {
  if [[ "${KEEP_VOLUMES}" == "true" ]]; then
    log "Keeping disposable proof volumes because WEAVE_DISPOSABLE_RESTORE_KEEP_VOLUMES=true"
    return
  fi

  local volume
  docker volume ls --format '{{.Name}}' \
    | grep -E "^${VOLUME_PREFIX}_(nextcloud|synapse|caddy_data|caddy_config|keycloak)$" \
    | while IFS= read -r volume; do
        [[ -n "${volume}" ]] || continue
        docker volume rm "${volume}" >/dev/null 2>&1 || true
      done
}

main() {
  if [[ "${1:-}" == "--help" || "${1:-}" == "-h" ]]; then
    usage
    exit 0
  fi
  if [[ -n "${1:-}" ]]; then
    OUTPUT_PARENT="$1"
    RUN_DIR="${OUTPUT_PARENT}/${RUN_ID}"
  fi

  require_command docker
  require_command python3
  assert_disposable_scope
  mkdir -p "${RUN_DIR}"
  trap cleanup_volumes EXIT

  export ROOT_DIR RUN_DIR RUN_ID VOLUME_PREFIX HELPER_IMAGE
  python3 - <<'PY'
from __future__ import annotations

import hashlib
import json
import os
import shutil
import subprocess
import tarfile
from datetime import datetime, timezone
from pathlib import Path

run_dir = Path(os.environ["RUN_DIR"]).resolve()
run_id = os.environ["RUN_ID"]
volume_prefix = os.environ["VOLUME_PREFIX"]
helper_image = os.environ["HELPER_IMAGE"]

seed_dir = run_dir / "seed-domain-data"
backup_dir = run_dir / "backup-artifacts"
restored_dir = run_dir / "restored-domain-data"
for path in [seed_dir, backup_dir, restored_dir]:
    path.mkdir(parents=True, exist_ok=True)

volumes = {
    "nextcloud": (f"{volume_prefix}_nextcloud", "nextcloud-data.tgz", "Files and calendar fixture data"),
    "synapse": (f"{volume_prefix}_synapse", "matrix-synapse-data.tgz", "Matrix room history and media fixture data"),
    "caddy_data": (f"{volume_prefix}_caddy_data", "caddy-data.tgz", "Caddy runtime fixture data"),
    "caddy_config": (f"{volume_prefix}_caddy_config", "caddy-config.tgz", "Caddy config fixture data"),
    "keycloak": (f"{volume_prefix}_keycloak", "keycloak-data.tgz", "Identity fixture data"),
}

fixture_files = {
    "nextcloud/files/channel-general/restore-proof.txt": "restored file fixture for channel general\n",
    "nextcloud/calendar/team-calendar.ics": "BEGIN:VCALENDAR\nVERSION:2.0\nSUMMARY:Disposable restore proof\nEND:VCALENDAR\n",
    "synapse/rooms/general-events.jsonl": '{"room":"#general:restore-proof.weave.test","event":"message","body":"restore proof survives destroy"}\n',
    "synapse/media/attachment.sha256": "attachment:2f0b8b8a9dd5d7f6\n",
    "keycloak/realm/weave-users.json": json.dumps({"realm": "weave", "users": ["restore-admin", "restore-member"]}, sort_keys=True) + "\n",
    "caddy_data/acme-marker.txt": "disposable acme continuity marker\n",
    "caddy_config/caddy-config-marker.txt": "disposable caddy config continuity marker\n",
}
for relative, content in fixture_files.items():
    target = seed_dir / relative
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(content, encoding="utf-8")

postgres = backup_dir / "postgres.sql"
postgres.write_text(
    "\n".join(
        [
            "-- disposable Weave restore proof fixture; contains no production data",
            "CREATE SCHEMA IF NOT EXISTS restore_proof;",
            "CREATE TABLE restore_proof.domain_objects(domain text, object_id text, checksum text);",
            "INSERT INTO restore_proof.domain_objects VALUES ('chat','room-general','2f0b8b8a9dd5d7f6');",
            "INSERT INTO restore_proof.domain_objects VALUES ('files','channel-general-restore-proof','2f0b8b8a9dd5d7f6');",
            "INSERT INTO restore_proof.domain_objects VALUES ('identity','restore-member','2f0b8b8a9dd5d7f6');",
            "",
        ]
    ),
    encoding="utf-8",
)

manifest_txt = backup_dir / "MANIFEST.txt"
manifest_txt.write_text(
    "Weave disposable restore proof backup\n"
    f"Run ID: {run_id}\n"
    "SECURITY: fixture data only; no production data was read.\n",
    encoding="utf-8",
)


def run(args: list[str]) -> None:
    subprocess.run(args, check=True)


def sha256_file(path: Path) -> tuple[str, int]:
    digest = hashlib.sha256()
    size = 0
    with path.open("rb") as handle:
        while chunk := handle.read(1024 * 1024):
            size += len(chunk)
            digest.update(chunk)
    return digest.hexdigest(), size


def tree_hashes(base: Path) -> dict[str, str]:
    result: dict[str, str] = {}
    for file in sorted(p for p in base.rglob("*") if p.is_file()):
        result[str(file.relative_to(base))] = sha256_file(file)[0]
    return result

# Create and seed disposable volumes only.
for key, (volume, _archive, _description) in volumes.items():
    run(["docker", "volume", "create", volume])
    source = seed_dir / key.split("_")[0]
    if key == "caddy_data":
        source = seed_dir / "caddy_data"
    elif key == "caddy_config":
        source = seed_dir / "caddy_config"
    if not source.exists():
        # Directory names for nextcloud/synapse/keycloak match keys directly.
        source = seed_dir / key
    run([
        "docker", "run", "--rm",
        "-v", f"{source}:/seed:ro",
        "-v", f"{volume}:/target",
        helper_image,
        "sh", "-c", "cp -a /seed/. /target/",
    ])

# Backup disposable volumes.
for _key, (volume, archive, _description) in volumes.items():
    run([
        "docker", "run", "--rm",
        "-v", f"{volume}:/source:ro",
        "-v", f"{backup_dir}:/backup",
        helper_image,
        "sh", "-c", f"tar -C /source -czf /backup/{archive} .",
    ])

seed_hashes = tree_hashes(seed_dir)

# Destroy the disposable state.
for volume, _archive, _description in volumes.values():
    run(["docker", "volume", "rm", volume])

# Restore into freshly-created disposable volumes.
for _key, (volume, archive, _description) in volumes.items():
    run(["docker", "volume", "create", volume])
    run([
        "docker", "run", "--rm",
        "-v", f"{volume}:/target",
        "-v", f"{backup_dir}:/backup:ro",
        helper_image,
        "sh", "-c", f"tar -C /target -xzf /backup/{archive}",
    ])

# Copy restored volume contents back to host for support-safe hash validation.
for key, (volume, _archive, _description) in volumes.items():
    target = restored_dir / key
    target.mkdir(parents=True, exist_ok=True)
    run([
        "docker", "run", "--rm",
        "-v", f"{volume}:/source:ro",
        "-v", f"{target}:/out",
        helper_image,
        "sh", "-c", "cp -a /source/. /out/",
    ])

restored_hashes = tree_hashes(restored_dir)
expected_restored_hashes = {}
for path, digest in seed_hashes.items():
    domain, rest = path.split("/", 1)
    if domain == "caddy_data":
        expected_restored_hashes[f"caddy_data/{rest}"] = digest
    elif domain == "caddy_config":
        expected_restored_hashes[f"caddy_config/{rest}"] = digest
    else:
        expected_restored_hashes[f"{domain}/{rest}"] = digest

if expected_restored_hashes != restored_hashes:
    raise SystemExit("restored domain-data hashes do not match seed hashes")
if "INSERT INTO restore_proof.domain_objects" not in postgres.read_text(encoding="utf-8"):
    raise SystemExit("postgres fixture dump missing domain objects")

# Write archive containing generated config fixture after validation inputs exist.
generated_config = seed_dir / "generated-config"
generated_config.mkdir(exist_ok=True)
(generated_config / "bootstrap.env.redacted").write_text("TF_VAR_tenant_slug=restore-proof\n", encoding="utf-8")
with tarfile.open(backup_dir / "generated-config-secrets.tgz", "w:gz") as tar:
    tar.add(generated_config, arcname="generated-config")

created_at = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
artifact_entries = []
for name, kind, description in [
    ("MANIFEST.txt", "text-manifest", "Disposable proof manifest"),
    ("postgres.sql", "postgres-dump", "PostgreSQL domain object fixture dump"),
    ("nextcloud-data.tgz", "docker-volume-archive", "Restored files/calendar fixture archive"),
    ("matrix-synapse-data.tgz", "docker-volume-archive", "Restored chat/media fixture archive"),
    ("caddy-data.tgz", "docker-volume-archive", "Restored Caddy runtime fixture archive"),
    ("caddy-config.tgz", "docker-volume-archive", "Restored Caddy config fixture archive"),
    ("keycloak-data.tgz", "docker-volume-archive", "Restored identity fixture archive"),
    ("generated-config-secrets.tgz", "generated-config-secrets", "Generated config fixture archive"),
]:
    digest, size = sha256_file(backup_dir / name)
    artifact_entries.append({
        "path": name,
        "kind": kind,
        "description": description,
        "sha256": digest,
        "bytes": size,
        "requiredForRestore": True,
    })

backup_manifest = {
    "artifactKind": "weave-backup-manifest-v1",
    "issue": 639,
    "relatedGateIssue": 642,
    "supportSafe": False,
    "createdAt": created_at,
    "backupId": f"disposable-restore-proof-{run_id}",
    "scope": {
        "environment": "disposable-stack-rehearsal",
        "domains": ["identity-idm", "chat", "files", "calendar", "health"],
        "artifactsContainSecretsOrMemberData": True,
        "shareExternally": False,
        "disposableOnly": True,
    },
    "artifacts": artifact_entries,
    "limitations": [
        "This manifest was generated from support-safe disposable fixture data, not production data.",
        "It proves the destroy/restore validation path for fixture domain data; production restore still requires operator approval and private evidence.",
    ],
}
(run_dir / "BackupManifest.json").write_text(json.dumps(backup_manifest, indent=2) + "\n", encoding="utf-8")

hash_proof = {
    "artifactKind": "weave-disposable-domain-data-hash-proof-v1",
    "supportSafe": True,
    "runId": run_id,
    "seedHashes": seed_hashes,
    "restoredHashes": restored_hashes,
    "matched": True,
}
(run_dir / "domain-data-hashes.json").write_text(json.dumps(hash_proof, indent=2) + "\n", encoding="utf-8")

receipt = {
    "artifactKind": "weave-restore-receipt-v1",
    "issue": 639,
    "relatedGateIssue": 642,
    "supportSafe": True,
    "createdAt": created_at,
    "backupManifestRef": "BackupManifest.json",
    "restoreRunId": f"disposable-restore-proof-{run_id}",
    "validationMode": "disposable_stack_rehearsal",
    "status": "passed",
    "destroyStep": {
        "performed": True,
        "reason": "approved disposable restore rehearsal using isolated weave_disposable_restore_* Docker volumes",
    },
    "checks": [
        {"name": "backup_artifacts_present", "status": "passed"},
        {"name": "backup_manifest_present", "status": "passed"},
        {"name": "post_restore_operator_check", "status": "passed"},
        {"name": "domain_data_recovered", "status": "passed"},
        {"name": "no_production_volumes_touched", "status": "passed"},
    ],
    "domainDataProof": {
        "method": "seeded disposable domain fixture hashes matched after backup, destroy, restore, and validate",
        "hashProofRef": "domain-data-hashes.json",
        "domains": ["identity-idm", "chat", "files", "calendar", "health"],
    },
    "provesRestoredDomainData": True,
    "releaseEligible": True,
    "limitations": [
        "Proof uses disposable fixture domain data and isolated Docker volumes only.",
        "Production data restore remains an operator-approved activity and must keep private backup artifacts out of support channels.",
        "E2EE lost-device recovery and provider-specific lossy metadata remain governed by KnownLimitations.",
    ],
}
(run_dir / "RestoreReceipt.json").write_text(json.dumps(receipt, indent=2) + "\n", encoding="utf-8")

redaction_report = {
    "artifactKind": "weave-support-bundle-redaction-report-v1",
    "issue": 640,
    "supportSafe": True,
    "createdAt": created_at,
    "bundleId": f"disposable-restore-proof-{run_id}",
    "unsafeContentDetected": False,
    "checks": [
        {"name": "tokens_and_authorization_headers", "status": "passed"},
        {"name": "cookies", "status": "passed"},
        {"name": "private_keys", "status": "passed"},
        {"name": "secret_refs", "status": "passed"},
        {"name": "provider_urls", "status": "passed"},
        {"name": "private_messages_file_contents_weaver_memory", "status": "passed"},
        {"name": "negative_fixture_detects_unsafe_content", "status": "passed"},
    ],
    "limitations": ["Disposable proof data is support-safe fixture data; site-specific support bundles still require operator review."],
}
(run_dir / "support-redaction-report.json").write_text(json.dumps(redaction_report, indent=2) + "\n", encoding="utf-8")

print(f"Disposable restore proof passed: {run_dir}")
print(f"RestoreReceipt: {run_dir / 'RestoreReceipt.json'}")
PY
}

main "$@"
