#!/usr/bin/env python3
"""Tests for the closed Compose private-backup v3 integrity contract."""

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
    database_inventory_digest,
    digest,
    validate_backup,
)


CANDIDATE = "a" * 40


class PrivateBackupIntegrityTest(unittest.TestCase):
    def create_backup(self, root: Path) -> Path:
        backup = root / f"weave-test-20260722T120000Z-{CANDIDATE[:12]}"
        backup.mkdir()
        for name in REQUIRED_ARTIFACTS:
            target = backup / name
            if name.endswith(".tgz"):
                with tarfile.open(target, "w:gz") as archive:
                    if name != "private-config-secrets.tgz":
                        root = tarfile.TarInfo(".")
                        root.type = tarfile.DIRTYPE
                        root.mode = 0o700
                        root.uid = 0
                        root.gid = 0
                        archive.addfile(root)
                    payload = f"fixture:{name}".encode("utf-8")
                    member = tarfile.TarInfo("./fixture/value")
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
            "schemaVersion": "weave.compose-private-backup.v3",
            "backupId": backup.name,
            "createdAt": "2026-07-22T12:00:00Z",
            "candidateCommit": CANDIDATE,
            "candidateManifestDigest": "sha256:" + "d" * 64,
            "profile": "test",
            "composeProject": "weave-test",
            "databaseFingerprint": "sha256:" + "b" * 64,
            "postgresDumpClientImage": "postgres@sha256:" + "c" * 64,
            "postgresDatabases": [
                "postgres",
                "weave_backend",
                "weave_keycloak",
            ],
            "postgresDatabaseInventoryDigest": database_inventory_digest(
                ["postgres", "weave_backend", "weave_keycloak"]
            ),
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

    @staticmethod
    def refresh_artifact(backup: Path, archive_path: Path) -> None:
        manifest = json.loads(
            (backup / "BackupManifest.json").read_text(encoding="utf-8")
        )
        checksum, size = digest(archive_path)
        for item in manifest["artifacts"]:
            if item["path"] == archive_path.name:
                item.update({"sha256": checksum, "bytes": size})
        (backup / "BackupManifest.json").write_text(
            json.dumps(manifest), encoding="utf-8"
        )

    def test_candidate_bound_v3_backup_passes(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            result = validate_backup(self.create_backup(Path(temporary)))
            self.assertEqual("weave.compose-private-backup-integrity.v3", result["schemaVersion"])
            self.assertEqual(CANDIDATE, result["candidateCommit"])
            self.assertTrue(result["allRequiredArtifactsVerified"])

    def test_corrupted_artifact_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            backup = self.create_backup(Path(temporary))
            (backup / "postgres.sql").write_text("changed\n", encoding="utf-8")
            with self.assertRaisesRegex(IntegrityError, "checksum validation"):
                validate_backup(backup)

    def test_retired_v2_manifest_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            backup = self.create_backup(Path(temporary))
            manifest = json.loads((backup / "BackupManifest.json").read_text(encoding="utf-8"))
            manifest["schemaVersion"] = "weave.compose-private-backup.v2"
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
            self.refresh_artifact(backup, archive_path)
            with self.assertRaisesRegex(IntegrityError, "unsafe member path"):
                validate_backup(backup)

    def test_provider_archive_without_exact_root_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            backup = self.create_backup(Path(temporary))
            archive_path = backup / "nextcloud-data.tgz"
            with tarfile.open(archive_path, "w:gz") as archive:
                member = tarfile.TarInfo("fixture")
                member.size = 1
                archive.addfile(member, io.BytesIO(b"x"))
            self.refresh_artifact(backup, archive_path)
            with self.assertRaisesRegex(
                IntegrityError, "exactly one root directory"
            ):
                validate_backup(backup)

    def test_duplicate_normalized_member_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            backup = self.create_backup(Path(temporary))
            archive_path = backup / "nextcloud-data.tgz"
            with tarfile.open(archive_path, "w:gz") as archive:
                root = tarfile.TarInfo(".")
                root.type = tarfile.DIRTYPE
                archive.addfile(root)
                for name in ("./fixture", "fixture"):
                    member = tarfile.TarInfo(name)
                    member.size = 1
                    archive.addfile(member, io.BytesIO(b"x"))
            self.refresh_artifact(backup, archive_path)
            with self.assertRaisesRegex(IntegrityError, "duplicate member"):
                validate_backup(backup)

    def test_privileged_regular_file_mode_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            backup = self.create_backup(Path(temporary))
            archive_path = backup / "nextcloud-data.tgz"
            with tarfile.open(archive_path, "w:gz") as archive:
                root = tarfile.TarInfo(".")
                root.type = tarfile.DIRTYPE
                archive.addfile(root)
                member = tarfile.TarInfo("./fixture")
                member.mode = 0o4755
                member.size = 1
                archive.addfile(member, io.BytesIO(b"x"))
            self.refresh_artifact(backup, archive_path)
            with self.assertRaisesRegex(
                IntegrityError, "privileged regular-file mode"
            ):
                validate_backup(backup)


if __name__ == "__main__":
    unittest.main()
