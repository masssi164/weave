#!/usr/bin/env python3
"""Unit evidence for the private Compose consistency-set backup."""

from __future__ import annotations

import hashlib
import json
import os
import sys
import tempfile
import unittest
from pathlib import Path
from types import SimpleNamespace
from unittest import mock

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))

import backup_runtime  # noqa: E402
from compose_env import ContractError  # noqa: E402


class BackupRuntimeContractTest(unittest.TestCase):
    CANDIDATE = "a" * 40

    def setUp(self) -> None:
        self.temporary = tempfile.TemporaryDirectory()
        base = Path(self.temporary.name)
        self.repository = base / "checkout"
        self.repository.mkdir()
        self.backup_root = base / "private-backups"
        self.generated = base / "generated"
        self.secrets = base / "secrets"
        self.tls = base / "tls"
        for path in (self.generated, self.secrets, self.tls):
            path.mkdir()
            (path / "fixture").write_text(path.name, encoding="utf-8")
        env = {
            "WEAVE_COMPOSE_PROJECT": "weave-test",
            "WEAVE_RESOURCE_PREFIX": "weave",
            "WEAVE_DB_ADMIN_USERNAME": "weave_admin",
            "WEAVE_POSTGRES_IMAGE": "postgres@sha256:" + "b" * 64,
        }
        for variable, _archive, _kind in backup_runtime.VOLUME_ARTIFACTS:
            env[variable] = variable.lower().replace("weave_", "weave-")
        self.context = SimpleNamespace(
            profile="test",
            repository_root=self.repository,
            root=ROOT,
            generated_root=self.generated,
            secret_root=self.secrets,
            tls_root=self.tls,
            env=env,
        )

    def tearDown(self) -> None:
        self.temporary.cleanup()

    @staticmethod
    def _write_postgres(_context: object, target: Path) -> str:
        target.write_bytes(b"postgres-consistency-dump\n")
        return "sha256:" + hashlib.sha256(b"system-identifier").hexdigest()

    @staticmethod
    def _write_volume(_context: object, volume: str, target: Path) -> None:
        target.write_bytes(("volume:" + volume).encode("utf-8"))

    @staticmethod
    def _write_private(_context: object, target: Path) -> None:
        target.write_bytes(b"private-config-with-secretrefs")

    def _environment(self) -> dict[str, str]:
        return {
            "WEAVE_CANDIDATE_COMMIT": self.CANDIDATE,
            "WEAVE_BACKUP_ROOT": str(self.backup_root),
        }

    def test_manifest_binds_exact_candidate_artifacts_and_quiescence(self) -> None:
        inventory = [
            {"service": service, "authority": "compose", "container": f"weave-{service}"}
            for service in backup_runtime.QUIESCED_SERVICES
        ]
        with (
            mock.patch.dict(os.environ, self._environment(), clear=False),
            mock.patch.object(backup_runtime, "_running_services", return_value=(list(backup_runtime.QUIESCED_SERVICES), inventory)),
            mock.patch.object(backup_runtime, "_stop") as stop,
            mock.patch.object(backup_runtime, "_start") as start,
            mock.patch.object(backup_runtime, "_postgres_dump", side_effect=self._write_postgres),
            mock.patch.object(backup_runtime, "_archive_volume", side_effect=self._write_volume),
            mock.patch.object(backup_runtime, "_archive_private_config", side_effect=self._write_private),
        ):
            destination = backup_runtime.backup(self.context)

        manifest = json.loads((destination / "BackupManifest.json").read_text(encoding="utf-8"))
        self.assertEqual(manifest["schemaVersion"], "weave.compose-private-backup.v2")
        self.assertEqual(manifest["candidateCommit"], self.CANDIDATE)
        self.assertEqual(manifest["composeProject"], "weave-test")
        self.assertEqual(manifest["quiescedServices"], list(backup_runtime.QUIESCED_SERVICES))
        self.assertEqual(manifest["runtimeInventory"], inventory)
        self.assertFalse(manifest["supportSafe"])
        self.assertTrue(manifest["containsSecretsOrMemberData"])
        self.assertEqual(len(manifest["artifacts"]), len(backup_runtime.VOLUME_ARTIFACTS) + 2)
        for artifact in manifest["artifacts"]:
            path = destination / artifact["path"]
            self.assertTrue(path.is_file())
            self.assertEqual(hashlib.sha256(path.read_bytes()).hexdigest(), artifact["sha256"])
            self.assertEqual(path.stat().st_size, artifact["bytes"])
        stop.assert_called_once()
        start.assert_called_once()

    def test_quiesced_services_restart_after_artifact_failure(self) -> None:
        running = list(backup_runtime.QUIESCED_SERVICES)
        inventory = [{"service": service, "authority": "compose", "container": service} for service in running]
        with (
            mock.patch.dict(os.environ, self._environment(), clear=False),
            mock.patch.object(backup_runtime, "_running_services", return_value=(running, inventory)),
            mock.patch.object(backup_runtime, "_stop"),
            mock.patch.object(backup_runtime, "_start") as start,
            mock.patch.object(backup_runtime, "_postgres_dump", side_effect=ContractError("fixture failure")),
        ):
            with self.assertRaisesRegex(ContractError, "fixture failure"):
                backup_runtime.backup(self.context)
        start.assert_called_once_with(self.context, running, inventory)

    def test_volume_archiver_has_only_the_read_capability_required_for_private_provider_data(
        self,
    ) -> None:
        target = self.backup_root / "nextcloud-data.tgz"
        target.parent.mkdir(parents=True)
        with mock.patch.object(
            backup_runtime.subprocess,
            "run",
            side_effect=[
                mock.Mock(returncode=0),
                mock.Mock(returncode=0),
            ],
        ) as run:
            backup_runtime._archive_volume(
                self.context,
                "weave-nextcloud-data",
                target,
            )

        archive_command = run.call_args_list[1].args[0]
        self.assertEqual(archive_command[0:3], ["docker", "run", "--rm"])
        self.assertIn("--read-only", archive_command)
        self.assertEqual(
            archive_command[
                archive_command.index("--user") : archive_command.index("--user") + 2
            ],
            ["--user", "0:0"],
        )
        self.assertEqual(
            archive_command[
                archive_command.index("--cap-drop") : archive_command.index("--cap-drop") + 2
            ],
            ["--cap-drop", "ALL"],
        )
        self.assertEqual(
            archive_command[
                archive_command.index("--cap-add") : archive_command.index("--cap-add") + 2
            ],
            ["--cap-add", "DAC_READ_SEARCH"],
        )
        self.assertNotIn("DAC_OVERRIDE", archive_command)
        self.assertIn("type=volume,src=weave-nextcloud-data,dst=/source,readonly", archive_command)
        self.assertIn("no-new-privileges:true", archive_command)

    def test_dev_profile_and_unbound_candidate_fail_closed(self) -> None:
        self.context.profile = "dev"
        with mock.patch.dict(os.environ, self._environment(), clear=False):
            with self.assertRaisesRegex(ContractError, "test/prod"):
                backup_runtime.backup(self.context)
        self.context.profile = "test"
        environment = self._environment()
        environment["WEAVE_CANDIDATE_COMMIT"] = "branch-name"
        with mock.patch.dict(os.environ, environment, clear=False):
            with self.assertRaisesRegex(ContractError, "exact candidate"):
                backup_runtime.backup(self.context)


if __name__ == "__main__":
    unittest.main()
