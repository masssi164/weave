#!/usr/bin/env bash
# shellcheck shell=bash

set -euo pipefail

ROOT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
readonly ROOT_DIR
SCRIPT="${ROOT_DIR}/restore-smoke.sh"

backup_dir="$(mktemp -d)"
trap 'rm -rf "${backup_dir}"' EXIT

PYTHONPATH="${ROOT_DIR}/../../tools" python3 - "${backup_dir}" <<'PY'
import io
import hashlib
import json
import sys
import tarfile
from pathlib import Path
from private_backup_integrity import EXPECTED_ARTIFACT_KINDS, REQUIRED_ARTIFACTS, digest

backup = Path(sys.argv[1])
candidate = "a" * 40
for name in REQUIRED_ARTIFACTS:
    path = backup / name
    if name.endswith(".tgz"):
        with tarfile.open(path, "w:gz") as archive:
            if name != "private-config-secrets.tgz":
                root = tarfile.TarInfo(".")
                root.type = tarfile.DIRTYPE
                root.mode = 0o700
                archive.addfile(root)
            payload = name.encode("utf-8")
            member = tarfile.TarInfo("./fixture/value")
            member.size = len(payload)
            archive.addfile(member, io.BytesIO(payload))
    else:
        path.write_text(f"fixture for {name}\n", encoding="utf-8")
artifacts = []
for name in sorted(REQUIRED_ARTIFACTS):
    checksum, size = digest(backup / name)
    artifacts.append({"path": name, "kind": EXPECTED_ARTIFACT_KINDS[name], "sha256": checksum, "bytes": size})
manifest = {
    "schemaVersion": "weave.compose-private-backup.v3",
    "backupId": f"weave-dogfood-20260722T120000Z-{candidate[:12]}",
    "createdAt": "2026-07-22T12:00:00Z",
    "candidateCommit": candidate,
    "candidateManifestDigest": "sha256:" + "d" * 64,
    "profile": "dogfood",
    "composeProject": "weave-dogfood",
    "databaseFingerprint": "sha256:" + "b" * 64,
    "postgresDumpClientImage": "postgres@sha256:" + "c" * 64,
    "postgresDatabases": ["postgres", "weave_backend", "weave_keycloak"],
    "postgresDatabaseInventoryDigest": "sha256:" + hashlib.sha256(
        json.dumps(
            ["postgres", "weave_backend", "weave_keycloak"],
            separators=(",", ":"),
            sort_keys=True,
        ).encode("utf-8")
    ).hexdigest(),
    "quiescedServices": ["backend", "keycloak"],
    "runtimeInventory": [{"service": "backend", "authority": "compose"}],
    "artifacts": artifacts,
    "supportSafe": False,
    "containsSecretsOrMemberData": True,
}
(backup / "BackupManifest.json").write_text(json.dumps(manifest), encoding="utf-8")
PY

output="$(WEAVE_RESTORE_SMOKE_ARTIFACTS_ONLY=true WEAVE_RESTORE_SMOKE_RELEASE_ELIGIBLE=true bash "${SCRIPT}" "${backup_dir}")"
[[ "${output}" == *"Backup artifact integrity check passed"* ]] || {
  echo "restore-smoke did not report artifact integrity success" >&2
  echo "${output}" >&2
  exit 1
}
[[ "${output}" == *"Service readiness was not checked in artifacts-only mode"* ]] || {
  echo "restore-smoke did not make artifacts-only limits explicit" >&2
  echo "${output}" >&2
  exit 1
}
[[ -s "${backup_dir}/RestoreReceipt.json" ]] || { echo "restore-smoke did not write RestoreReceipt.json" >&2; exit 1; }
python3 - "${backup_dir}/RestoreReceipt.json" <<'PY'
import json
import sys
receipt = json.load(open(sys.argv[1], encoding='utf-8'))
assert receipt['schemaVersion'] == 'weave.compose-restore-receipt.v2'
assert receipt['validationMode'] == 'artifacts_only'
assert receipt['backupBinding']['candidateCommit'] == 'a' * 40
assert receipt['destroyStep']['performed'] is False
assert receipt['provesRestoredDomainData'] is False
assert receipt['releaseEligible'] is False
assert any(
    check['name'] == 'matrix_chat_appservice_registration_and_secret_mounts'
    and check['status'] == 'archived_not_runtime_verified'
    for check in receipt['checks']
)
assert any(
    check['name'] == 'agent_runtime_consistency_set_and_live_reconciliation'
    and check['status'] == 'archived_not_runtime_verified'
    for check in receipt['checks']
)
PY

rm "${backup_dir}/postgres.sql"
if WEAVE_RESTORE_SMOKE_ARTIFACTS_ONLY=true bash "${SCRIPT}" "${backup_dir}" >/tmp/restore-smoke-missing.out 2>&1; then
  echo "restore-smoke accepted a backup directory with a missing postgres.sql" >&2
  exit 1
fi
grep -Fq "required private backup artifact is missing or unsafe" /tmp/restore-smoke-missing.out || {
  echo "restore-smoke missing-artifact failure was not actionable" >&2
  cat /tmp/restore-smoke-missing.out >&2
  exit 1
}

printf 'restore smoke artifact tests passed\n'
