#!/usr/bin/env python3
"""Validate one Compose private backup without exposing private content."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import sys
import tarfile
from pathlib import Path, PurePosixPath
from typing import Any


EXPECTED_ARTIFACT_KINDS = {
    "postgres.sql": "postgres-consistency-dump",
    "nextcloud-data.tgz": "files-calendar-provider-data",
    "synapse-data.tgz": "matrix-media-and-local-state",
    "caddy-data.tgz": "gateway-runtime-state",
    "caddy-config.tgz": "gateway-config-state",
    "keycloak-data.tgz": "keycloak-runtime-state",
    "matrix-appservice.tgz": "matrix-appservice-runtime",
    "private-config-secrets.tgz": "private-config-secretrefs",
}
REQUIRED_ARTIFACTS = frozenset(EXPECTED_ARTIFACT_KINDS)
COMMIT_RE = re.compile(r"^[0-9a-f]{40}$")
BACKUP_ID_RE = re.compile(r"^weave-(test|prod)-\d{8}T\d{6}Z-[0-9a-f]{12}$")
PROJECT_RE = re.compile(r"^[a-z0-9][a-z0-9_-]{1,62}$")
DIGEST_RE = re.compile(r"^[0-9a-f]{64}$")


class IntegrityError(ValueError):
    """Raised when a private backup fails its closed integrity contract."""


def digest(path: Path) -> tuple[str, int]:
    checksum = hashlib.sha256()
    size = 0
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            checksum.update(chunk)
            size += len(chunk)
    return checksum.hexdigest(), size


def _validate_archive(path: Path) -> None:
    entries = 0
    try:
        with tarfile.open(path, "r:gz") as archive:
            for member in archive:
                entries += 1
                candidate = PurePosixPath(member.name)
                if candidate.is_absolute() or ".." in candidate.parts:
                    raise IntegrityError("private backup archive contains an unsafe member path")
                if member.ischr() or member.isblk() or member.isfifo() or member.isdev():
                    raise IntegrityError("private backup archive contains a special device member")
                if member.issym() or member.islnk():
                    target = PurePosixPath(member.linkname)
                    if target.is_absolute() or ".." in target.parts:
                        raise IntegrityError("private backup archive contains an unsafe link target")
    except (OSError, tarfile.TarError) as error:
        raise IntegrityError("private backup archive is unreadable") from error
    if entries == 0:
        raise IntegrityError("private backup archive is empty")


def _manifest(path: Path) -> dict[str, Any]:
    if path.is_symlink() or not path.is_file():
        raise IntegrityError("private BackupManifest.json is missing or unsafe")
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as error:
        raise IntegrityError("private BackupManifest.json is unreadable") from error
    if not isinstance(value, dict):
        raise IntegrityError("private BackupManifest.json must contain an object")
    return value


def validate_backup(backup_dir: Path) -> dict[str, Any]:
    if backup_dir.is_symlink() or not backup_dir.is_dir():
        raise IntegrityError("private backup directory is missing or unsafe")
    manifest = _manifest(backup_dir / "BackupManifest.json")
    if manifest.get("schemaVersion") != "weave.compose-private-backup.v2":
        raise IntegrityError("backup manifest schema is unsupported")
    if manifest.get("supportSafe") is not False or manifest.get("containsSecretsOrMemberData") is not True:
        raise IntegrityError("backup manifest must declare its private data boundary")
    candidate = manifest.get("candidateCommit")
    profile = manifest.get("profile")
    backup_id = manifest.get("backupId")
    if not isinstance(candidate, str) or not COMMIT_RE.fullmatch(candidate):
        raise IntegrityError("backup manifest candidate commit is invalid")
    if profile not in {"test", "prod"}:
        raise IntegrityError("backup manifest profile is invalid")
    if (
        not isinstance(backup_id, str)
        or not BACKUP_ID_RE.fullmatch(backup_id)
        or not backup_id.startswith(f"weave-{profile}-")
        or not backup_id.endswith(candidate[:12])
    ):
        raise IntegrityError("backup manifest identifier is not candidate-bound")
    compose_project = manifest.get("composeProject")
    if not isinstance(compose_project, str) or not PROJECT_RE.fullmatch(compose_project):
        raise IntegrityError("backup manifest Compose project is invalid")
    database_fingerprint = manifest.get("databaseFingerprint")
    if not isinstance(database_fingerprint, str) or not re.fullmatch(r"sha256:[0-9a-f]{64}", database_fingerprint):
        raise IntegrityError("backup manifest database fingerprint is invalid")
    if not isinstance(manifest.get("quiescedServices"), list) or not isinstance(manifest.get("runtimeInventory"), list):
        raise IntegrityError("backup manifest runtime consistency boundary is missing")

    artifacts = manifest.get("artifacts")
    if not isinstance(artifacts, list):
        raise IntegrityError("backup artifact inventory is missing")
    inventory: dict[str, dict[str, Any]] = {}
    for item in artifacts:
        if not isinstance(item, dict) or set(item) != {"path", "kind", "sha256", "bytes"}:
            raise IntegrityError("backup artifact inventory contains an invalid entry")
        name = item.get("path")
        if not isinstance(name, str) or Path(name).name != name or name not in REQUIRED_ARTIFACTS:
            raise IntegrityError("backup artifact path is unsafe or unsupported")
        if name in inventory:
            raise IntegrityError("backup artifact inventory contains a duplicate path")
        if item.get("kind") != EXPECTED_ARTIFACT_KINDS[name]:
            raise IntegrityError("backup artifact kind does not match its canonical path")
        expected_hash = item.get("sha256")
        expected_size = item.get("bytes")
        if not isinstance(expected_hash, str) or not DIGEST_RE.fullmatch(expected_hash):
            raise IntegrityError("backup artifact checksum is invalid")
        if not isinstance(expected_size, int) or isinstance(expected_size, bool) or expected_size <= 0:
            raise IntegrityError("backup artifact byte count is invalid")
        inventory[name] = item
    if set(inventory) != REQUIRED_ARTIFACTS:
        raise IntegrityError("backup artifact inventory does not match the canonical restore set")

    for name in sorted(REQUIRED_ARTIFACTS):
        path = backup_dir / name
        if path.is_symlink() or not path.is_file():
            raise IntegrityError("a required private backup artifact is missing or unsafe")
        actual_hash, actual_size = digest(path)
        if actual_hash != inventory[name]["sha256"] or actual_size != inventory[name]["bytes"]:
            raise IntegrityError("a required private backup artifact failed checksum validation")
        if name.endswith(".tgz"):
            _validate_archive(path)

    return {
        "schemaVersion": "weave.compose-private-backup-integrity.v2",
        "status": "passed",
        "backupIdSha256": hashlib.sha256(backup_id.encode("utf-8")).hexdigest(),
        "candidateCommit": candidate,
        "profile": profile,
        "composeProject": compose_project,
        "artifactCount": len(REQUIRED_ARTIFACTS),
        "allRequiredArtifactsVerified": True,
        "privateArtifactContentIncluded": False,
        "supportSafe": True,
    }


def write_result(path: Path, result: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_name(f"{path.name}.tmp.{os.getpid()}")
    descriptor = os.open(temporary, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    try:
        with os.fdopen(descriptor, "w", encoding="utf-8") as stream:
            json.dump(result, stream, indent=2, sort_keys=True)
            stream.write("\n")
            stream.flush()
            os.fsync(stream.fileno())
        os.replace(temporary, path)
        os.chmod(path, 0o600)
    finally:
        if temporary.exists():
            temporary.unlink()


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--backup-dir", required=True, type=Path)
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    try:
        result = validate_backup(args.backup_dir)
        if args.output:
            write_result(args.output, result)
        print(
            "PRIVATE_BACKUP_INTEGRITY status=passed "
            f"artifactCount={result['artifactCount']} candidateCommit={result['candidateCommit']} "
            "supportSafe=true"
        )
        return 0
    except IntegrityError as error:
        print(f"private-backup-integrity: invalid: {error}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
