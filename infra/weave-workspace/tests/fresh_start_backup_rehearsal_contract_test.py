#!/usr/bin/env python3
"""Contract tests for the non-adopting Fresh Start backup rehearsal."""

from __future__ import annotations

import json
import io
import os
import stat
import subprocess
import sys
import tarfile
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
    CANDIDATE_MANIFEST = "sha256:" + "9" * 64

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
            "WEAVE_NEXTCLOUD_IMAGE": "nextcloud@sha256:" + "e" * 64,
            "WEAVE_CANDIDATE_MANIFEST_DIGEST": self.CANDIDATE_MANIFEST,
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

    def test_read_only_archive_root_is_applied_after_child_extraction(
        self,
    ) -> None:
        archive_path = self.backup_dir / "matrix-appservice.tgz"
        with tarfile.open(archive_path, "w:gz") as archive:
            root = tarfile.TarInfo(".")
            root.type = tarfile.DIRTYPE
            root.mode = 0o555
            root.uid = 991
            root.gid = 991
            archive.addfile(root)
            child = tarfile.TarInfo("./registration.yaml")
            child.mode = 0o444
            child.uid = 991
            child.gid = 991
            child.size = 1
            archive.addfile(child, io.BytesIO(b"x"))

        inventory = adoption_rehearsal._archive_inventory(archive_path)
        self.assertEqual(
            inventory["rootMetadata"],
            {"mode": 0o555, "uid": 991, "gid": 991},
        )

        with mock.patch.object(adoption_rehearsal, "_run") as run:
            adoption_rehearsal._restore_volume(
                "postgres@sha256:" + "b" * 64,
                "weave-restore-fixture",
                "weave-restore-fixture-matrix-appservice",
                self.backup_dir,
                archive_path.name,
                inventory["rootMetadata"],
            )

        self.assertEqual(run.call_count, 2)
        restore = run.call_args_list[1].args
        self.assertIn("--network", restore)
        self.assertIn("none", restore)
        for capability in ("CHOWN", "DAC_OVERRIDE", "FOWNER"):
            self.assertIn(capability, restore)
        command = restore[-1]
        self.assertIn("--strip-components 1", command)
        self.assertIn("--numeric-owner", command)
        self.assertIn("--same-owner", command)
        self.assertIn("--same-permissions", command)
        self.assertIn("--delay-directory-restore", command)
        self.assertTrue(
            command.endswith("chown 991:991 /target && chmod 0555 /target")
        )
        self.assertLess(
            command.index("--strip-components 1"),
            command.index("chmod 0555 /target"),
        )

    def test_private_config_archive_does_not_require_a_dot_root(self) -> None:
        archive_path = self.backup_dir / "private-config-secrets.tgz"
        with tarfile.open(archive_path, "w:gz") as archive:
            generated = tarfile.TarInfo("generated")
            generated.type = tarfile.DIRTYPE
            generated.mode = 0o700
            generated.uid = 0
            generated.gid = 0
            archive.addfile(generated)

        inventory = adoption_rehearsal._archive_inventory(archive_path)

        self.assertEqual(inventory["entryCount"], 1)
        self.assertIsNone(inventory["rootMetadata"])

    def test_busybox_tar_restore_helper_is_rejected(self) -> None:
        result = subprocess.CompletedProcess(
            ("docker", "run"), 0, b"BusyBox v1.37.0 multi-call binary.\n", b""
        )
        with mock.patch.object(
            adoption_rehearsal, "_run", return_value=result
        ):
            with self.assertRaisesRegex(
                ContractError, "requires the pinned GNU tar helper"
            ):
                adoption_rehearsal._verify_restore_helper(
                    "postgres@sha256:" + "b" * 64
                )

    def test_postgres_wait_ignores_transient_init_server(self) -> None:
        calls = []

        def docker_result(
            arguments: list[str], **_kwargs: object
        ) -> subprocess.CompletedProcess[str]:
            calls.append(arguments)
            if arguments[-2] == "-euc" and "/proc/1/cmdline" in arguments[-1]:
                attempt = sum(
                    call[-2] == "-euc"
                    and "/proc/1/cmdline" in call[-1]
                    for call in calls
                )
                return subprocess.CompletedProcess(
                    arguments, 1 if attempt == 1 else 0, "", ""
                )
            if "pg_isready" in arguments:
                return subprocess.CompletedProcess(arguments, 0, "", "")
            self.fail(f"unexpected command: {arguments}")

        with (
            mock.patch.object(
                adoption_rehearsal.subprocess,
                "run",
                side_effect=docker_result,
            ),
            mock.patch.object(adoption_rehearsal.time, "sleep") as sleep,
        ):
            adoption_rehearsal._wait_for_final_postgres(
                "weave-restore-fixture-postgres", "weave_admin", attempts=2
            )

        sleep.assert_called_once_with(1)
        self.assertEqual(
            sum("pg_isready" in call for call in calls),
            1,
        )

    def test_restore_copy_keeps_restrict_and_removes_only_bootstrap_admin(
        self,
    ) -> None:
        source = self.backup_dir / "postgres.sql"
        target = self.backup_dir / "postgres-sanitized.sql"
        source.write_text(
            "\\restrict fixture-key\n"
            "CREATE ROLE weave_admin;\n"
            "ALTER ROLE weave_admin WITH SUPERUSER LOGIN;\n"
            "CREATE ROLE weave_backend;\n"
            "ALTER ROLE weave_backend WITH NOSUPERUSER LOGIN;\n"
            "\\unrestrict fixture-key\n",
            encoding="utf-8",
        )

        adoption_rehearsal._sanitize_admin_role(
            source, target, "weave_admin"
        )

        restored = target.read_text(encoding="utf-8")
        self.assertIn("\\restrict fixture-key", restored)
        self.assertIn("\\unrestrict fixture-key", restored)
        self.assertNotIn("CREATE ROLE weave_admin;", restored)
        self.assertNotIn("ALTER ROLE weave_admin", restored)
        self.assertIn("CREATE ROLE weave_backend;", restored)
        self.assertIn("ALTER ROLE weave_backend", restored)
        self.assertEqual(stat.S_IMODE(target.stat().st_mode), 0o600)

    def test_fresh_start_rehearsal_restores_and_cleans_without_adoption(
        self,
    ) -> None:
        databases = [
            "postgres",
            "weave_backend",
            "weave_keycloak",
            "weave_mas",
            "weave_nextcloud",
            "weave_synapse",
        ]
        artifact_names = [
            "postgres.sql",
            "private-config-secrets.tgz",
            *[
                archive
                for _variable, archive, _kind in adoption_rehearsal.active_volume_artifacts(
                    self.context
                )
            ],
        ]
        artifacts = []
        for name in artifact_names:
            target = self.backup_dir / name
            target.write_bytes(("fixture:" + name).encode("utf-8"))
            artifacts.append(
                {
                    "path": name,
                    "sha256": adoption_rehearsal._digest(target),
                }
            )
        manifest = {
            "schemaVersion": "weave.compose-private-backup.v3",
            "candidateCommit": self.CANDIDATE,
            "candidateManifestDigest": self.CANDIDATE_MANIFEST,
            "profile": "test",
            "composeProject": "weave-test",
            "databaseFingerprint": "sha256:" + "c" * 64,
            "postgresDumpClientImage": "postgres@sha256:" + "f" * 64,
            "postgresDatabases": databases,
            "postgresDatabaseInventoryDigest": (
                adoption_rehearsal.database_inventory_digest(databases)
            ),
            "artifacts": artifacts,
        }
        (self.backup_dir / "BackupManifest.json").write_text(
            json.dumps(manifest), encoding="utf-8"
        )
        inventory = {
            "entryCount": 1,
            "regularFileCount": 1,
            "regularFileBytes": 1,
            "inventoryDigest": "sha256:" + "d" * 64,
            "rootMetadata": {"mode": 0o700, "uid": 0, "gid": 0},
        }
        databases = manifest["postgresDatabases"]

        def run_result(
            *arguments: str, input_file: Path | None = None
        ) -> subprocess.CompletedProcess[bytes]:
            del input_file
            joined = " ".join(arguments)
            if arguments[-1:] == ("--version",):
                return subprocess.CompletedProcess(
                    arguments, 0, b"tar (GNU tar) 1.35\n", b""
                )
            if "SELECT datname" in joined:
                return subprocess.CompletedProcess(
                    arguments, 0, ("\n".join(databases) + "\n").encode(), b""
                )
            if "SELECT count(*) FROM realm" in joined:
                return subprocess.CompletedProcess(arguments, 0, b"1\n", b"")
            return subprocess.CompletedProcess(arguments, 0, b"", b"")

        def docker_result(
            arguments: list[str], **_kwargs: object
        ) -> subprocess.CompletedProcess[object]:
            if arguments[-2] == "-euc" and "/proc/1/cmdline" in arguments[-1]:
                return subprocess.CompletedProcess(
                    arguments, 0, "", ""
                )
            if "pg_isready" in arguments:
                return subprocess.CompletedProcess(arguments, 0, b"", b"")
            if len(arguments) >= 3 and arguments[2] == "inspect":
                return subprocess.CompletedProcess(arguments, 1, b"", b"")
            return subprocess.CompletedProcess(arguments, 0, b"", b"")

        with (
            mock.patch.dict(
                os.environ,
                {
                    "WEAVE_CANDIDATE_COMMIT": self.CANDIDATE,
                    "WEAVE_CANDIDATE_MANIFEST_DIGEST": (
                        self.CANDIDATE_MANIFEST
                    ),
                },
                clear=False,
            ),
            mock.patch.object(
                adoption_rehearsal, "_archive_inventory", return_value=inventory
            ),
            mock.patch.object(
                adoption_rehearsal,
                "_validate_private_backup",
                return_value={"candidateCommit": self.CANDIDATE},
            ),
            mock.patch.object(adoption_rehearsal, "_restore_volume"),
            mock.patch.object(
                adoption_rehearsal, "_volume_inventory", return_value=inventory
            ),
            mock.patch.object(
                adoption_rehearsal, "_sanitize_admin_role"
            ) as sanitize,
            mock.patch.object(
                adoption_rehearsal, "_prepare_legacy_secret_continuity"
            ) as migrate,
            mock.patch.object(
                adoption_rehearsal, "_run", side_effect=run_result
            ) as run,
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
        self.assertNotIn("resources", receipt)
        self.assertEqual(
            receipt["restoredProviderVolumeCount"],
            len(adoption_rehearsal.active_volume_artifacts(self.context)),
        )
        self.assertEqual(receipt["verifiedDatabaseCount"], len(databases))
        self.assertEqual(
            receipt["verifiedServiceDatabaseCount"], len(databases) - 1
        )
        self.assertTrue(receipt["supportSafe"])
        self.assertFalse(receipt["containsSecretValues"])
        self.assertEqual(
            receipt["postgresDumpClientImage"],
            manifest["postgresDumpClientImage"],
        )
        self.assertEqual(
            receipt["candidateManifestDigest"], self.CANDIDATE_MANIFEST
        )
        server_starts = [
            call.args
            for call in run.call_args_list
            if "--detach" in call.args and "--name" in call.args
        ]
        self.assertEqual(len(server_starts), 1)
        restore_server = server_starts[0]
        self.assertEqual(restore_server[-1], manifest["postgresDumpClientImage"])
        self.assertIn("--read-only", restore_server)
        self.assertIn("no-new-privileges:true", restore_server)
        for capability in (
            "CHOWN",
            "DAC_OVERRIDE",
            "FOWNER",
            "SETGID",
            "SETUID",
        ):
            self.assertIn(capability, restore_server)
        restore_clients = [
            call.args
            for call in run.call_args_list
            if "PGPASSFILE=/run/secrets/pgpass" in call.args
        ]
        self.assertEqual(len(restore_clients), 1)
        restore_client = restore_clients[0]
        self.assertEqual(
            restore_client[
                restore_client.index("--entrypoint") :
                restore_client.index("--entrypoint") + 3
            ],
            (
                "--entrypoint",
                "psql",
                manifest["postgresDumpClientImage"],
            ),
        )
        self.assertIn("--host", restore_client)
        self.assertIn("--interactive", restore_client)
        self.assertIn("DAC_READ_SEARCH", restore_client)
        self.assertIn("weave-restore-", restore_client[restore_client.index("--host") + 1])

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
