#!/usr/bin/env python3
"""Unit evidence for closed Fresh Start recovery-receipt validation."""

from __future__ import annotations

import hashlib
import importlib.util
import json
import os
import sys
import tempfile
import unittest
from datetime import datetime, timezone
from pathlib import Path
from types import SimpleNamespace


ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))

from recovery_receipt import (  # noqa: E402
    ReceiptContractError,
    database_inventory_digest,
    load_fresh_start_recovery,
)

FRESH_START_SPEC = importlib.util.spec_from_file_location(
    "fresh_start", ROOT / "fresh-start.py"
)
assert FRESH_START_SPEC and FRESH_START_SPEC.loader
fresh_start = importlib.util.module_from_spec(FRESH_START_SPEC)
FRESH_START_SPEC.loader.exec_module(fresh_start)


class RecoveryReceiptContractTest(unittest.TestCase):
    CANDIDATE = "a" * 40
    CANDIDATE_MANIFEST = "sha256:" + "b" * 64
    DATABASES = ["postgres", "weave_backend", "weave_keycloak"]

    def write_fixture(self, root: Path) -> tuple[Path, dict[str, object]]:
        manifest = {
            "schemaVersion": "weave.compose-private-backup.v3",
            "candidateCommit": self.CANDIDATE,
            "candidateManifestDigest": self.CANDIDATE_MANIFEST,
            "profile": "dogfood",
            "composeProject": "weave-dogfood",
            "postgresDumpClientImage": "postgres@sha256:" + "c" * 64,
            "postgresDatabases": self.DATABASES,
            "postgresDatabaseInventoryDigest": database_inventory_digest(
                self.DATABASES
            ),
            "supportSafe": False,
            "containsSecretsOrMemberData": True,
        }
        manifest_path = root / "BackupManifest.json"
        manifest_path.write_text(
            json.dumps(manifest, sort_keys=True) + "\n", encoding="utf-8"
        )
        os.chmod(manifest_path, 0o600)
        manifest_digest = hashlib.sha256(manifest_path.read_bytes()).hexdigest()
        inventories = [
            {
                "artifact": artifact,
                "entryCount": 2,
                "regularFileCount": 1,
                "regularFileBytes": 1,
                "inventoryDigest": "sha256:" + str(index + 1) * 64,
                "rootMetadata": {"uid": 0, "gid": 0, "mode": 0o700},
                "verified": True,
            }
            for index, artifact in enumerate(
                (
                    "caddy-config.tgz",
                    "caddy-data.tgz",
                    "keycloak-data.tgz",
                    "matrix-appservice.tgz",
                    "native-files-data.tgz",
                    "nextcloud-data.tgz",
                    "synapse-data.tgz",
                )
            )
        ]
        receipt: dict[str, object] = {
            "schemaVersion": "weave.fresh-start-private-backup-rehearsal.v1",
            "profile": "dogfood",
            "composeProject": "weave-dogfood",
            "candidateCommit": self.CANDIDATE,
            "candidateManifestDigest": self.CANDIDATE_MANIFEST,
            "backupRef": "evidence:private-backup:sha256:" + manifest_digest,
            "databaseFingerprint": "sha256:" + "d" * 64,
            "postgresDumpClientImage": manifest["postgresDumpClientImage"],
            "postgresDatabaseInventoryDigest": manifest[
                "postgresDatabaseInventoryDigest"
            ],
            "postgresDatabaseCount": len(self.DATABASES),
            "backupVerified": True,
            "isolatedRestoreVerified": True,
            "restoreHelperImage": "nextcloud@sha256:" + "e" * 64,
            "restoredVolumeInventories": inventories,
            "isolatedNamespace": "weave-restore-123456789abc",
            "verifiedDatabaseCount": len(self.DATABASES),
            "verifiedServiceDatabaseCount": len(self.DATABASES) - 1,
            "verifiedAt": datetime.now(timezone.utc)
            .isoformat()
            .replace("+00:00", "Z"),
            "cleanupVerified": True,
            "supportSafe": True,
            "containsSecretValues": False,
            "recoveryBoundary": "private-backup-only-no-adoption",
            "legacyStateMigrated": False,
            "adoptionAuthorized": False,
            "privateArtifactCount": len(inventories) + 2,
            "restoredProviderVolumeCount": len(inventories),
        }
        receipt_path = root / "FreshStartBackupRehearsal.json"
        receipt_path.write_text(
            json.dumps(receipt, sort_keys=True) + "\n", encoding="utf-8"
        )
        os.chmod(receipt_path, 0o600)
        return receipt_path, receipt

    def test_exact_candidate_receipt_is_accepted(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            receipt_path, _receipt = self.write_fixture(Path(temporary))
            result = load_fresh_start_recovery(
                receipt_path,
                candidate=self.CANDIDATE,
                candidate_manifest_digest=self.CANDIDATE_MANIFEST,
            )
            self.assertRegex(result, r"^[0-9a-f]{64}$")

    def test_destructive_plan_validation_consumes_the_private_receipt(
        self,
    ) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            receipt_path, _receipt = self.write_fixture(Path(temporary))
            args = SimpleNamespace(
                environment="persistent-dogfood",
                scope="persistent",
                stack="weave",
                namespace="weave",
                retired_generation="fresh-v1",
                target_generation="fresh-v2",
                spec_commit="1" * 40,
                spec_digest="sha256:" + "2" * 64,
                candidate_commit=self.CANDIDATE,
                candidate_manifest_digest=self.CANDIDATE_MANIFEST,
                operation_nonce="fresh-start-op-0001",
                recovery_decision="verified-backup",
                recovery_evidence_ref=(
                    "https://github.com/masssi164/weave/issues/1266"
                ),
                recovery_receipt=receipt_path,
            )
            self.assertRegex(
                fresh_start.validate_plan_arguments(args),
                r"^[0-9a-f]{64}$",
            )

    def test_missing_cleanup_proof_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            receipt_path, receipt = self.write_fixture(Path(temporary))
            receipt["cleanupVerified"] = False
            receipt_path.write_text(json.dumps(receipt), encoding="utf-8")
            os.chmod(receipt_path, 0o600)
            with self.assertRaisesRegex(
                ReceiptContractError, "unsafe, stale, or not candidate-bound"
            ):
                load_fresh_start_recovery(
                    receipt_path,
                    candidate=self.CANDIDATE,
                    candidate_manifest_digest=self.CANDIDATE_MANIFEST,
                )

    def test_database_inventory_binding_mismatch_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            receipt_path, receipt = self.write_fixture(Path(temporary))
            receipt["postgresDatabaseInventoryDigest"] = (
                "sha256:" + "f" * 64
            )
            receipt_path.write_text(json.dumps(receipt), encoding="utf-8")
            os.chmod(receipt_path, 0o600)
            with self.assertRaisesRegex(
                ReceiptContractError, "does not match its private BackupManifest"
            ):
                load_fresh_start_recovery(
                    receipt_path,
                    candidate=self.CANDIDATE,
                    candidate_manifest_digest=self.CANDIDATE_MANIFEST,
                )

    def test_weak_receipt_permissions_are_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            receipt_path, _receipt = self.write_fixture(Path(temporary))
            os.chmod(receipt_path, 0o644)
            with self.assertRaisesRegex(
                ReceiptContractError, "owner-controlled mode-0600"
            ):
                load_fresh_start_recovery(
                    receipt_path,
                    candidate=self.CANDIDATE,
                    candidate_manifest_digest=self.CANDIDATE_MANIFEST,
                )


if __name__ == "__main__":
    unittest.main()
