#!/usr/bin/env python3
"""Contract tests for the non-adopting Fresh Start backup rehearsal."""

from __future__ import annotations

import json
import os
import stat
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path
from types import SimpleNamespace
from unittest import mock

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))

import adoption_rehearsal  # noqa: E402
from compose_env import ContractError  # noqa: E402


class FreshStartBackupRehearsalContractTest(unittest.TestCase):
    CANDIDATE = "a" * 40

    def setUp(self) -> None:
        self.temporary = tempfile.TemporaryDirectory()
        base = Path(self.temporary.name)
        self.generated = base / "generated"
        self.generated.mkdir()
        self.backup_dir = base / "private-backup"
        self.backup_dir.mkdir()
        env = {
            "WEAVE_COMPOSE_PROJECT": "weave-test",
            "WEAVE_DOCKER_NETWORK": "weave_network",
            "WEAVE_DB_ADMIN_USERNAME": "weave_admin",
            "WEAVE_BACKEND_DB_NAME": "weave_backend",
            "WEAVE_KEYCLOAK_DB_NAME": "weave_keycloak",
            "WEAVE_MAS_DB_NAME": "weave_mas",
            "WEAVE_SYNAPSE_DB_NAME": "weave_synapse",
            "WEAVE_NEXTCLOUD_DB_NAME": "weave_nextcloud",
            "WEAVE_POSTGRES_IMAGE": "postgres@sha256:" + "b" * 64,
        }
        for variable, _archive, _kind in adoption_rehearsal.VOLUME_ARTIFACTS:
            env[variable] = variable.lower().replace("weave_", "weave-")
        env["WEAVE_DB_DATA_VOLUME"] = "weave-db-data"
        env["WEAVE_MAILPIT_DATA_VOLUME"] = "weave-mailpit-data"
        self.context = SimpleNamespace(
            profile="test",
            generated_root=self.generated,
            env=env,
        )

    def tearDown(self) -> None:
        self.temporary.cleanup()

    def test_fresh_start_execution_never_prepares_legacy_secret_continuity(
        self,
    ) -> None:
        receipt = {
            "schemaVersion": "weave.fresh-start-private-backup-rehearsal.v1",
            "supportSafe": True,
            "containsSecretValues": False,
            "legacyStateMigrated": False,
            "adoptionAuthorized": False,
            "cleanupVerified": True,
        }
        with (
            mock.patch.object(
                adoption_rehearsal, "_prepare_legacy_secret_continuity"
            ) as migrate,
            mock.patch.object(
                adoption_rehearsal, "backup", return_value=self.backup_dir
            ),
            mock.patch.object(
                adoption_rehearsal, "rehearse", return_value=receipt
            ) as rehearse,
        ):
            output = adoption_rehearsal.execute(
                self.context, "fresh-start"
            )

        migrate.assert_not_called()
        rehearse.assert_called_once_with(
            self.context, self.backup_dir, "fresh-start"
        )
        self.assertEqual(
            output, self.backup_dir / "FreshStartBackupRehearsal.json"
        )
        self.assertEqual(
            stat.S_IMODE(output.stat().st_mode),
            0o600,
        )
        self.assertEqual(
            json.loads(output.read_text(encoding="utf-8")),
            receipt,
        )

    def test_fresh_start_rehearsal_restores_and_cleans_without_adoption(
        self,
    ) -> None:
        manifest = {
            "schemaVersion": "weave.compose-private-backup.v2",
            "candidateCommit": self.CANDIDATE,
            "profile": "test",
            "composeProject": "weave-test",
            "databaseFingerprint": "sha256:" + "c" * 64,
            "artifacts": [],
        }
        (self.backup_dir / "BackupManifest.json").write_text(
            json.dumps(manifest), encoding="utf-8"
        )
        (self.backup_dir / "postgres.sql").write_text(
            'CREATE ROLE weave_admin;\n',
            encoding="utf-8",
        )
        inventory = {
            "entryCount": 1,
            "regularFileCount": 1,
            "regularFileBytes": 1,
            "inventoryDigest": "sha256:" + "d" * 64,
        }
        databases = sorted(
            {
                self.context.env["WEAVE_BACKEND_DB_NAME"],
                self.context.env["WEAVE_KEYCLOAK_DB_NAME"],
                self.context.env["WEAVE_MAS_DB_NAME"],
                self.context.env["WEAVE_SYNAPSE_DB_NAME"],
                self.context.env["WEAVE_NEXTCLOUD_DB_NAME"],
            }
        )

        def run_result(
            *arguments: str, input_file: Path | None = None
        ) -> subprocess.CompletedProcess[bytes]:
            del input_file
            joined = " ".join(arguments)
            if "SELECT datname" in joined:
                return subprocess.CompletedProcess(
                    arguments, 0, ("\n".join(databases) + "\n").encode(), b""
                )
            if "SELECT count(*) FROM realm" in joined:
                return subprocess.CompletedProcess(arguments, 0, b"1\n", b"")
            return subprocess.CompletedProcess(arguments, 0, b"", b"")

        def docker_result(
            arguments: list[str], **_kwargs: object
        ) -> subprocess.CompletedProcess[bytes]:
            if arguments[1:3] == ["exec", mock.ANY]:
                return subprocess.CompletedProcess(arguments, 0, b"", b"")
            if len(arguments) >= 3 and arguments[2] == "inspect":
                return subprocess.CompletedProcess(arguments, 1, b"", b"")
            return subprocess.CompletedProcess(arguments, 0, b"", b"")

        with (
            mock.patch.dict(
                os.environ,
                {"WEAVE_CANDIDATE_COMMIT": self.CANDIDATE},
                clear=False,
            ),
            mock.patch.object(
                adoption_rehearsal, "_archive_inventory", return_value=inventory
            ),
            mock.patch.object(adoption_rehearsal, "_restore_volume"),
            mock.patch.object(
                adoption_rehearsal, "_volume_inventory", return_value=inventory
            ),
            mock.patch.object(
                adoption_rehearsal, "_sanitize_admin_creation"
            ) as sanitize,
            mock.patch.object(
                adoption_rehearsal, "_prepare_legacy_secret_continuity"
            ) as migrate,
            mock.patch.object(
                adoption_rehearsal, "_run", side_effect=run_result
            ),
            mock.patch.object(
                adoption_rehearsal.subprocess,
                "run",
                side_effect=docker_result,
            ),
        ):
            receipt = adoption_rehearsal.rehearse(
                self.context, self.backup_dir, "fresh-start"
            )

        migrate.assert_not_called()
        sanitize.assert_called_once()
        self.assertEqual(
            receipt["schemaVersion"],
            "weave.fresh-start-private-backup-rehearsal.v1",
        )
        self.assertTrue(receipt["backupVerified"])
        self.assertTrue(receipt["isolatedRestoreVerified"])
        self.assertTrue(receipt["cleanupVerified"])
        self.assertFalse(receipt["legacyStateMigrated"])
        self.assertFalse(receipt["adoptionAuthorized"])
        self.assertEqual(
            receipt["recoveryBoundary"],
            "private-backup-only-no-adoption",
        )
        self.assertTrue(receipt["supportSafe"])
        self.assertFalse(receipt["containsSecretValues"])

    def test_cleanup_fails_closed_when_an_owned_resource_remains(self) -> None:
        def docker_result(
            arguments: list[str], **_kwargs: object
        ) -> subprocess.CompletedProcess[bytes]:
            returncode = (
                0
                if len(arguments) >= 3
                and arguments[1:3] == ["container", "inspect"]
                else 1
                if len(arguments) >= 3
                and arguments[2] == "inspect"
                else 0
            )
            return subprocess.CompletedProcess(
                arguments, returncode, b"", b""
            )

        with mock.patch.object(
            adoption_rehearsal.subprocess,
            "run",
            side_effect=docker_result,
        ):
            with self.assertRaisesRegex(
                ContractError, "cleanup left exact owned resources"
            ):
                adoption_rehearsal._cleanup(
                    "weave-restore-fixture",
                    "weave-restore-fixture-postgres",
                    "weave-restore-fixture-network",
                    ["weave-restore-fixture-db-data"],
                )


if __name__ == "__main__":
    unittest.main()
