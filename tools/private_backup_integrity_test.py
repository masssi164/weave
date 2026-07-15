#!/usr/bin/env python3

from __future__ import annotations

import hashlib
import json
import tempfile
import unittest
from pathlib import Path

from private_backup_integrity import IntegrityError, REQUIRED_ARTIFACTS, validate_backup


def checksum(content: bytes) -> str:
    return hashlib.sha256(content).hexdigest()


class PrivateBackupIntegrityTest(unittest.TestCase):
    def create_fixture(self, root: Path, *, legacy_manifest: bool = False) -> Path:
        backup = root / "backup"
        backup.mkdir()
        finalized_manifest = (
            b"Weave backup\nArtifacts:\n"
            b"- postgres.sql: fixture\n"
            b"- BackupManifest.json: machine-readable inventory\n"
            b"\nNotes:\n- Keep this backup private.\n"
        )
        legacy_prefix = b"Weave backup\nArtifacts:\n- postgres.sql: fixture\n"
        (backup / "MANIFEST.txt").write_bytes(finalized_manifest)
        for name in REQUIRED_ARTIFACTS - {"MANIFEST.txt"}:
            (backup / name).write_bytes(f"fixture:{name}\n".encode("utf-8"))

        artifacts = []
        for name in sorted(REQUIRED_ARTIFACTS):
            content = (backup / name).read_bytes()
            recorded = legacy_prefix if legacy_manifest and name == "MANIFEST.txt" else content
            artifacts.append(
                {
                    "path": name,
                    "kind": "fixture",
                    "sha256": checksum(recorded),
                    "bytes": len(recorded),
                    "requiredForRestore": True,
                }
            )
        manifest = {
            "artifactKind": "weave-backup-manifest-v1",
            "supportSafe": False,
            "backupId": "private-fixture-id",
            "artifacts": artifacts,
        }
        (backup / "BackupManifest.json").write_text(
            json.dumps(manifest, indent=2) + "\n", encoding="utf-8"
        )
        return backup

    def test_finalized_manifest_and_data_artifacts_pass(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            result = validate_backup(self.create_fixture(Path(directory)))
        self.assertEqual(result["status"], "passed")
        self.assertFalse(result["legacyTextManifestFinalizationBugReconciled"])
        self.assertNotIn("private-fixture-id", json.dumps(result))

    def test_known_legacy_text_manifest_ordering_bug_is_narrowly_reconciled(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            backup = self.create_fixture(Path(directory), legacy_manifest=True)
            with self.assertRaises(IntegrityError):
                validate_backup(backup)
            result = validate_backup(
                backup, allow_legacy_text_manifest_finalization_bug=True
            )
        self.assertTrue(result["legacyTextManifestFinalizationBugReconciled"])

    def test_corrupted_domain_artifact_never_passes_legacy_reconciliation(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            backup = self.create_fixture(Path(directory), legacy_manifest=True)
            (backup / "postgres.sql").write_text("corrupted\n", encoding="utf-8")
            with self.assertRaises(IntegrityError):
                validate_backup(
                    backup, allow_legacy_text_manifest_finalization_bug=True
                )


if __name__ == "__main__":
    unittest.main(verbosity=2)
