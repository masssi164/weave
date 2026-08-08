#!/usr/bin/env python3
"""Create a support-safe proof from a verified private pre-migration backup."""

from __future__ import annotations

import hashlib
import json
import os
import re
from datetime import datetime, timezone
from pathlib import Path

from compose_env import ComposeContext, ContractError
from keycloak_migration import migration_inputs


BACKUP_PROOF_NAME = "fgap-v2-primary-organization-post-import.backup-proof.json"


def _sha256_bytes(value: bytes) -> str:
    return "sha256:" + hashlib.sha256(value).hexdigest()


def _atomic_private(
    path: Path, value: dict[str, object], runtime_owner: tuple[int, int]
) -> None:
    if path.is_symlink():
        raise ContractError("Keycloak migration backup proof target is a symlink")
    payload = (json.dumps(value, indent=2, sort_keys=True) + "\n").encode()
    temporary = path.with_name(f".{path.name}.{os.getpid()}.tmp")
    descriptor = os.open(temporary, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    try:
        with os.fdopen(descriptor, "wb") as stream:
            stream.write(payload)
            stream.flush()
            os.fsync(stream.fileno())
        os.replace(temporary, path)
        os.chmod(path, 0o600)
        uid, gid = runtime_owner
        if path.stat().st_uid != uid or path.stat().st_gid != gid:
            try:
                os.chown(path, uid, gid)
            except PermissionError as error:
                raise ContractError(
                    "Keycloak migration backup proof is not readable by the rootless Server migration uid/gid"
                ) from error
    finally:
        if temporary.exists():
            temporary.unlink()


def create_backup_proof(context: ComposeContext) -> Path:
    # Imported lazily because the existing backup verifier reads the Compose
    # volume inventory from compose_runtime.
    import adoption_rehearsal
    import backup_runtime

    if context.environment not in {"dogfood", "prod"}:
        raise ContractError("persistent Keycloak migration backup proof is dogfood/prod only")
    candidate = os.environ.get("WEAVE_CANDIDATE_COMMIT", "")
    if not re.fullmatch(r"[0-9a-f]{40}", candidate):
        raise ContractError("Keycloak migration backup proof requires an exact candidate commit")
    backup_dir = backup_runtime.backup(context)
    rehearsal = adoption_rehearsal.rehearse(context, backup_dir, "fresh-start")
    if (
        rehearsal.get("backupVerified") is not True
        or rehearsal.get("isolatedRestoreVerified") is not True
        or rehearsal.get("cleanupVerified") is not True
        or rehearsal.get("supportSafe") is not True
        or rehearsal.get("containsSecretValues") is not False
    ):
        raise ContractError("private pre-migration backup rehearsal is incomplete")
    manifest_file = backup_dir / "BackupManifest.json"
    if manifest_file.is_symlink() or not manifest_file.is_file():
        raise ContractError("private pre-migration BackupManifest is unavailable")
    manifest_payload = manifest_file.read_bytes()
    manifest = json.loads(manifest_payload)
    backup_id = manifest.get("backupId")
    if (
        manifest.get("schemaVersion") != "weave.compose-private-backup.v3"
        or manifest.get("candidateCommit") != candidate
        or manifest.get("profile") != context.environment
        or manifest.get("composeProject") != context.env["WEAVE_COMPOSE_PROJECT"]
        or not isinstance(backup_id, str)
        or not backup_id
    ):
        raise ContractError("private pre-migration BackupManifest scope is invalid")
    inputs = migration_inputs(context)
    proof = {
        "schemaVersion": "weave.keycloak-realm-migration-backup-proof/v1",
        "supportSafe": True,
        "status": "verified",
        "createdAt": datetime.now(timezone.utc).isoformat().replace("+00:00", "Z"),
        "environment": context.environment,
        "realm": "weave",
        "sourceBaselineRevision": inputs.target_revision,
        "backupManifestSha256": _sha256_bytes(manifest_payload),
        "backupIdSha256": _sha256_bytes(backup_id.encode("utf-8")),
        "candidateCommit": candidate,
        "composeProject": context.env["WEAVE_COMPOSE_PROJECT"],
    }
    output = context.generated_root / "keycloak/migrations" / BACKUP_PROOF_NAME
    _atomic_private(
        output,
        proof,
        (int(context.env["WEAVE_RUNTIME_UID"]), int(context.env["WEAVE_RUNTIME_GID"])),
    )
    return output
