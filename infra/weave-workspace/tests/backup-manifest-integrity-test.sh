#!/usr/bin/env bash
# shellcheck shell=bash

set -euo pipefail

ROOT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
# shellcheck source=../backup.sh
source "${ROOT_DIR}/backup.sh"

fixture_dir="$(mktemp -d)"
trap 'rm -rf "${fixture_dir}"' EXIT

BACKUP_DIR="${fixture_dir}/weave-backup-fixture"
mkdir -p "${BACKUP_DIR}"

write_manifest_header

declare -a artifacts=(
  postgres.sql
  nextcloud-data.tgz
  matrix-synapse-data.tgz
  caddy-data.tgz
  caddy-config.tgz
  keycloak-data.tgz
  generated-config-secrets.tgz
)

for artifact in "${artifacts[@]}"; do
  printf 'fixture for %s\n' "${artifact}" >"${BACKUP_DIR}/${artifact}"
  append_manifest "${artifact}" "Integrity fixture"
done

finalize_text_manifest
write_backup_manifest_json

grep -Fq 'BackupManifest.json' "${BACKUP_DIR}/MANIFEST.txt" || {
  echo "Final text manifest does not list BackupManifest.json." >&2
  exit 1
}
grep -Fq 'Notes:' "${BACKUP_DIR}/MANIFEST.txt" || {
  echo "Final text manifest does not contain the restore notes." >&2
  exit 1
}

while IFS=$'\t' read -r expected path; do
  actual="$(shasum -a 256 "${BACKUP_DIR}/${path}" | awk '{print $1}')"
  [[ "${actual}" == "${expected}" ]] || {
    echo "BackupManifest checksum mismatch for ${path}." >&2
    exit 1
  }
done < <(jq -r '.artifacts[] | [.sha256, .path] | @tsv' "${BACKUP_DIR}/BackupManifest.json")

echo "Backup manifest integrity test passed."
