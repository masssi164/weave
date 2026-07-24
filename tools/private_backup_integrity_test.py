#!/usr/bin/env python3
"""Tests for the closed Compose private-backup v2 integrity contract."""

from __future__ import annotations

import io
import json
import tarfile
import tempfile
import unittest
from pathlib import Path

from private_backup_integrity import (
    EXPECTED_ARTIFACT_KINDS,
    IntegrityError,
    REQUIRED_ARTIFACTS,
    digest,
    validate_backup,
)


CANDIDATE = "a" * 40


class PrivateBackupIntegrityTest(unittest.TestCase):
    def create_backup(self, root: Path) -> Path:
        backup = root / f"weave-dogfood-20260722T120000Z-{CANDIDATE[:12]}"
        backup.mkdir()
        for name in REQUIRED_ARTIFACTS:
            target = backup / name
            if name.endswith(".tgz"):
                with tarfile.open(target, "w:gz") as archive:
                    payload = f"fixture:{name}".encode("utf-8")
                    member = tarfile.TarInfo("fixture/value")
                    member.size = len(payload)
                    archive.addfile(member, io.BytesIO(payload))
            else:
                target.write_text(f"fixture:{name}\n", encoding="utf-8")
        artifacts = []
        for name in sorted(REQUIRED_ARTIFACTS):
            checksum, size = digest(backup / name)
            artifacts.append(
                {
                    "path": name,
                    "kind": EXPECTED_ARTIFACT_KINDS[name],
                    "sha256": checksum,
                    "bytes": size,
                }
            )
        manifest = {
            "schemaVersion": "weave.compose-private-backup.v2",
            "backupId": backup.name,
            "createdAt": "2026-07-22T12:00:00Z",
            "candidateCommit": CANDIDATE,
            "profile": "dogfood",
            "composeProject": "weave-dogfood",
            "databaseFingerprint": "sha256:" + "b" * 64,
            "quiescedServices": ["backend", "keycloak"],
            "runtimeInventory": [{"service": "backend", "authority": "compose"}],
            "artifacts": artifacts,
            "supportSafe": False,
            "containsSecretsOrMemberData": True,
        }
        (backup / "BackupManifest.json").write_text(
            json.dumps(manifest, indent=2) + "\n", encoding="utf-8"
        )
        return backup

    def test_candidate_bound_v2_backup_passes(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            result = validate_backup(self.create_backup(Path(temporary)))
            self.assertEqual("weave.compose-private-backup-integrity.v2", result["schemaVersion"])
            self.assertEqual(CANDIDATE, result["candidateCommit"])
            self.assertTrue(result["allRequiredArtifactsVerified"])

    def test_corrupted_artifact_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            backup = self.create_backup(Path(temporary))
            (backup / "postgres.sql").write_text("changed\n", encoding="utf-8")
            with self.assertRaisesRegex(IntegrityError, "checksum validation"):
                validate_backup(backup)

    def test_retired_v1_manifest_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            backup = self.create_backup(Path(temporary))
            manifest = json.loads((backup / "BackupManifest.json").read_text(encoding="utf-8"))
            manifest["schemaVersion"] = "weave-backup-manifest-v1"
            (backup / "BackupManifest.json").write_text(json.dumps(manifest), encoding="utf-8")
            with self.assertRaisesRegex(IntegrityError, "schema is unsupported"):
                validate_backup(backup)

    def test_archive_traversal_is_rejected_even_when_hash_matches(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            backup = self.create_backup(Path(temporary))
            archive_path = backup / "nextcloud-data.tgz"
            with tarfile.open(archive_path, "w:gz") as archive:
                payload = b"unsafe"
                member = tarfile.TarInfo("../outside")
                member.size = len(payload)
                archive.addfile(member, io.BytesIO(payload))
            manifest = json.loads((backup / "BackupManifest.json").read_text(encoding="utf-8"))
            checksum, size = digest(archive_path)
            for item in manifest["artifacts"]:
                if item["path"] == archive_path.name:
                    item.update({"sha256": checksum, "bytes": size})
            (backup / "BackupManifest.json").write_text(json.dumps(manifest), encoding="utf-8")
            with self.assertRaisesRegex(IntegrityError, "unsafe member path"):
                validate_backup(backup)


if __name__ == "__main__":
    unittest.main()
