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
from typing import Any, Collection

sys.path.insert(
    0,
    str(
        Path(__file__).resolve().parents[1]
        / "infra"
        / "weave-workspace"
        / "scripts"
    ),
)
from recovery_receipt import database_inventory_digest  # noqa: E402


EXPECTED_ARTIFACT_KINDS = {
    "postgres.sql": "postgres-consistency-dump",
    "nextcloud-data.tgz": "files-calendar-provider-data",
    "synapse-data.tgz": "matrix-media-and-local-state",
    "caddy-data.tgz": "gateway-runtime-state",
    "caddy-config.tgz": "gateway-config-state",
    "keycloak-data.tgz": "keycloak-runtime-state",
    "matrix-appservice.tgz": "matrix-appservice-runtime",
    "native-files-data.tgz": "native-files-payload-data",
    "runtime-state-data.tgz": "runtime-state-sensitive",
    "private-config-secrets.tgz": "private-config-secretrefs",
}
ALLOWED_ARTIFACTS = frozenset(EXPECTED_ARTIFACT_KINDS)
REQUIRED_ARTIFACTS = frozenset(
    (
        "postgres.sql",
        "caddy-data.tgz",
        "caddy-config.tgz",
        "keycloak-data.tgz",
        "native-files-data.tgz",
        "private-config-secrets.tgz",
    )
)
COMMIT_RE = re.compile(r"^[0-9a-f]{40}$")
BACKUP_ID_RE = re.compile(r"^weave-(dogfood|prod)-\d{8}T\d{6}Z-[0-9a-f]{12}$")
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


def _validate_archive(path: Path, *, require_root: bool) -> None:
    entries = 0
    roots = 0
    normalized_paths: set[str] = set()
    symlink_paths: set[str] = set()
    try:
        with tarfile.open(path, "r:gz") as archive:
            for member in archive:
                entries += 1
                candidate = PurePosixPath(member.name)
                if candidate.is_absolute() or ".." in candidate.parts:
                    raise IntegrityError("private backup archive contains an unsafe member path")
                normalized = str(candidate).removeprefix("./") or "."
                if normalized in normalized_paths:
                    raise IntegrityError(
                        "private backup archive contains a duplicate member path"
                    )
                normalized_paths.add(normalized)
                if member.ischr() or member.isblk() or member.isfifo() or member.isdev():
                    raise IntegrityError("private backup archive contains a special device member")
                if not (
                    member.isfile()
                    or member.isdir()
                    or member.issym()
                    or member.islnk()
                ):
                    raise IntegrityError(
                        "private backup archive contains an unsupported member type"
                    )
                if (
                    not isinstance(member.uid, int)
                    or isinstance(member.uid, bool)
                    or member.uid < 0
                    or member.uid > 2_147_483_647
                    or not isinstance(member.gid, int)
                    or isinstance(member.gid, bool)
                    or member.gid < 0
                    or member.gid > 2_147_483_647
                ):
                    raise IntegrityError(
                        "private backup archive contains invalid numeric ownership"
                    )
                if member.isfile() and member.mode & 0o6000:
                    raise IntegrityError(
                        "private backup archive contains a privileged regular-file mode"
                    )
                if normalized == ".":
                    roots += 1
                    if not member.isdir():
                        raise IntegrityError(
                            "private backup archive root is not a directory"
                        )
                if member.issym() or member.islnk():
                    target = PurePosixPath(member.linkname)
                    if target.is_absolute() or ".." in target.parts:
                        raise IntegrityError("private backup archive contains an unsafe link target")
                if member.issym():
                    symlink_paths.add(normalized)
        for normalized in normalized_paths:
            candidate = PurePosixPath(normalized)
            if any(str(parent) in symlink_paths for parent in candidate.parents):
                raise IntegrityError(
                    "private backup archive traverses a parent symlink"
                )
    except (OSError, tarfile.TarError) as error:
        raise IntegrityError("private backup archive is unreadable") from error
    if entries == 0:
        raise IntegrityError("private backup archive is empty")
    if require_root and roots != 1:
        raise IntegrityError(
            "provider-volume backup archive must contain exactly one root directory"
        )


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


def validate_backup(
    backup_dir: Path, *, expected_artifacts: Collection[str] | None = None
) -> dict[str, Any]:
    if backup_dir.is_symlink() or not backup_dir.is_dir():
        raise IntegrityError("private backup directory is missing or unsafe")
    manifest = _manifest(backup_dir / "BackupManifest.json")
    if manifest.get("schemaVersion") != "weave.compose-private-backup.v3":
        raise IntegrityError("backup manifest schema is unsupported")
    if manifest.get("supportSafe") is not False or manifest.get("containsSecretsOrMemberData") is not True:
        raise IntegrityError("backup manifest must declare its private data boundary")
    candidate = manifest.get("candidateCommit")
    candidate_manifest_digest = manifest.get("candidateManifestDigest")
    profile = manifest.get("profile")
    backup_id = manifest.get("backupId")
    if not isinstance(candidate, str) or not COMMIT_RE.fullmatch(candidate):
        raise IntegrityError("backup manifest candidate commit is invalid")
    if not isinstance(candidate_manifest_digest, str) or not re.fullmatch(
        r"sha256:[0-9a-f]{64}", candidate_manifest_digest
    ):
        raise IntegrityError("backup manifest candidate manifest digest is invalid")
    if profile not in {"dogfood", "prod"}:
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
    postgres_dump_client_image = manifest.get("postgresDumpClientImage")
    if not isinstance(postgres_dump_client_image, str) or not re.fullmatch(
        r"postgres@sha256:[0-9a-f]{64}", postgres_dump_client_image
    ):
        raise IntegrityError(
            "backup manifest PostgreSQL dump client image is invalid"
        )
    postgres_databases = manifest.get("postgresDatabases")
    postgres_database_inventory_digest = manifest.get(
        "postgresDatabaseInventoryDigest"
    )
    if (
        not isinstance(postgres_databases, list)
        or "postgres" not in postgres_databases
        or postgres_databases != sorted(set(postgres_databases))
        or any(
            not isinstance(name, str)
            or not re.fullmatch(r"[A-Za-z_][A-Za-z0-9_-]{0,62}", name)
            for name in postgres_databases
        )
    ):
        raise IntegrityError("backup manifest PostgreSQL inventory is invalid")
    if (
        not isinstance(postgres_database_inventory_digest, str)
        or postgres_database_inventory_digest
        != database_inventory_digest(postgres_databases)
    ):
        raise IntegrityError(
            "backup manifest PostgreSQL inventory digest is invalid"
        )
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
        if not isinstance(name, str) or Path(name).name != name or name not in ALLOWED_ARTIFACTS:
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
    if not REQUIRED_ARTIFACTS.issubset(inventory):
        raise IntegrityError("backup artifact inventory is missing the canonical core restore set")
    present_artifacts = {
        path.name
        for path in backup_dir.iterdir()
        if path.name in ALLOWED_ARTIFACTS
    }
    if present_artifacts != set(inventory):
        raise IntegrityError(
            "backup artifact inventory does not exactly match the private artifact files"
        )
    if expected_artifacts is not None:
        expected = set(expected_artifacts)
        if not expected.issubset(ALLOWED_ARTIFACTS):
            raise IntegrityError("expected backup artifact set contains an unsupported path")
        if set(inventory) != expected:
            raise IntegrityError(
                "backup artifact inventory does not match the selected deployment profile"
            )

    for name in sorted(inventory):
        path = backup_dir / name
        if path.is_symlink() or not path.is_file():
            raise IntegrityError("a required private backup artifact is missing or unsafe")
        actual_hash, actual_size = digest(path)
        if actual_hash != inventory[name]["sha256"] or actual_size != inventory[name]["bytes"]:
            raise IntegrityError("a required private backup artifact failed checksum validation")
        if name.endswith(".tgz"):
            _validate_archive(
                path, require_root=name != "private-config-secrets.tgz"
            )

    return {
        "schemaVersion": "weave.compose-private-backup-integrity.v3",
        "status": "passed",
        "backupIdSha256": hashlib.sha256(backup_id.encode("utf-8")).hexdigest(),
        "candidateCommit": candidate,
        "candidateManifestDigest": candidate_manifest_digest,
        "profile": profile,
        "composeProject": compose_project,
        "postgresDumpClientImage": postgres_dump_client_image,
        "postgresDatabaseInventoryDigest": postgres_database_inventory_digest,
        "postgresDatabaseCount": len(postgres_databases),
        "artifactCount": len(inventory),
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
