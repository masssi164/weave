#!/usr/bin/env python3
"""Validate a private Weave backup without exposing private artifact content."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import sys
from pathlib import Path
from typing import Any


REQUIRED_ARTIFACTS = {
    "MANIFEST.txt",
    "postgres.sql",
    "nextcloud-data.tgz",
    "matrix-synapse-data.tgz",
    "caddy-data.tgz",
    "caddy-config.tgz",
    "keycloak-data.tgz",
    "generated-config-secrets.tgz",
}
LEGACY_TEXT_MARKER = b"- BackupManifest.json:"
LEGACY_NOTES_MARKER = b"\nNotes:\n"


class IntegrityError(ValueError):
    """Raised when a private backup fails its support-safe integrity contract."""


def digest(path: Path) -> tuple[str, int]:
    checksum = hashlib.sha256()
    size = 0
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            checksum.update(chunk)
            size += len(chunk)
    return checksum.hexdigest(), size


def _legacy_manifest_prefix_matches(path: Path, expected_hash: str, expected_size: int) -> bool:
    content = path.read_bytes()
    marker_index = content.find(LEGACY_TEXT_MARKER)
    if marker_index < 0 or LEGACY_NOTES_MARKER not in content[marker_index:]:
        return False
    prefix = content[:marker_index]
    return len(prefix) == expected_size and hashlib.sha256(prefix).hexdigest() == expected_hash


def validate_backup(
    backup_dir: Path,
    *,
    allow_legacy_text_manifest_finalization_bug: bool = False,
) -> dict[str, Any]:
    manifest_path = backup_dir / "BackupManifest.json"
    if not manifest_path.is_file():
        raise IntegrityError("private BackupManifest.json is missing")
    try:
        manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as error:
        raise IntegrityError("private BackupManifest.json is unreadable") from error

    if manifest.get("artifactKind") != "weave-backup-manifest-v1":
        raise IntegrityError("backup manifest kind is unsupported")
    if manifest.get("supportSafe") is not False:
        raise IntegrityError("private backup manifest must declare supportSafe=false")
    backup_id = manifest.get("backupId")
    if not isinstance(backup_id, str) or not backup_id:
        raise IntegrityError("backup manifest has no stable backup identifier")
    artifacts = manifest.get("artifacts")
    if not isinstance(artifacts, list):
        raise IntegrityError("backup artifact inventory is missing")

    inventory: dict[str, dict[str, Any]] = {}
    for item in artifacts:
        if not isinstance(item, dict):
            raise IntegrityError("backup artifact inventory contains a non-object entry")
        name = item.get("path")
        if not isinstance(name, str) or not name or Path(name).name != name:
            raise IntegrityError("backup artifact path is unsafe")
        if name in inventory:
            raise IntegrityError("backup artifact inventory contains a duplicate path")
        if item.get("requiredForRestore") is not True:
            raise IntegrityError("backup artifact is not marked required for restore")
        expected_hash = item.get("sha256")
        expected_size = item.get("bytes")
        if (
            not isinstance(expected_hash, str)
            or re.fullmatch(r"[0-9a-f]{64}", expected_hash) is None
        ):
            raise IntegrityError("backup artifact checksum is invalid")
        if not isinstance(expected_size, int) or expected_size <= 0:
            raise IntegrityError("backup artifact byte count is invalid")
        inventory[name] = item

    if set(inventory) != REQUIRED_ARTIFACTS:
        raise IntegrityError("backup artifact inventory does not match the required restore set")

    reconciled_legacy_manifest = False
    for name in sorted(REQUIRED_ARTIFACTS):
        path = backup_dir / name
        if not path.is_file():
            raise IntegrityError("a required private backup artifact is missing")
        actual_hash, actual_size = digest(path)
        expected_hash = inventory[name]["sha256"]
        expected_size = inventory[name]["bytes"]
        if actual_hash == expected_hash and actual_size == expected_size:
            continue
        if (
            name == "MANIFEST.txt"
            and allow_legacy_text_manifest_finalization_bug
            and _legacy_manifest_prefix_matches(path, expected_hash, expected_size)
        ):
            reconciled_legacy_manifest = True
            continue
        raise IntegrityError("a required private backup artifact failed checksum validation")

    return {
        "schemaVersion": "weave.private-backup-integrity.v1",
        "status": "passed",
        "backupIdSha256": hashlib.sha256(backup_id.encode("utf-8")).hexdigest(),
        "artifactCount": len(REQUIRED_ARTIFACTS),
        "dataArtifactCount": len(REQUIRED_ARTIFACTS - {"MANIFEST.txt"}),
        "allRequiredArtifactsVerified": True,
        "legacyTextManifestFinalizationBugReconciled": reconciled_legacy_manifest,
        "privateArtifactContentIncluded": False,
        "supportSafe": True,
    }


def write_result(path: Path, result: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_name(f"{path.name}.tmp.{os.getpid()}")
    temporary.write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
    temporary.chmod(0o600)
    temporary.replace(path)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--backup-dir", required=True, type=Path)
    parser.add_argument("--output", type=Path)
    parser.add_argument("--allow-legacy-text-manifest-finalization-bug", action="store_true")
    args = parser.parse_args()
    try:
        result = validate_backup(
            args.backup_dir,
            allow_legacy_text_manifest_finalization_bug=args.allow_legacy_text_manifest_finalization_bug,
        )
        if args.output:
            write_result(args.output, result)
        print(
            "PRIVATE_BACKUP_INTEGRITY status=passed "
            f"artifactCount={result['artifactCount']} "
            "supportSafe=true"
        )
        return 0
    except IntegrityError as error:
        print(f"private-backup-integrity: invalid: {error}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
