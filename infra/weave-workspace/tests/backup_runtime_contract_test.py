#!/usr/bin/env python3
"""Unit evidence for the private Compose consistency-set backup."""

from __future__ import annotations

import hashlib
import json
import os
import subprocess
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
from fresh_start_retired_inventory import load_retired_inventory  # noqa: E402


class BackupRuntimeContractTest(unittest.TestCase):
    CANDIDATE = "a" * 40
    CANDIDATE_MANIFEST = "sha256:" + "d" * 64

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
            "WEAVE_COMPOSE_PROJECT": "weave-dogfood",
            "WEAVE_RESOURCE_PREFIX": "weave",
            "WEAVE_DB_ADMIN_USERNAME": "weave_admin",
            "WEAVE_POSTGRES_IMAGE": "postgres@sha256:" + "b" * 64,
        }
        for variable, _archive, _kind in backup_runtime.VOLUME_ARTIFACTS:
            env[variable] = variable.lower().replace("weave_", "weave-")
        self.context = SimpleNamespace(
            profile="dogfood",
            environment="dogfood",
            active_profiles=("dogfood",),
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
    def _write_postgres(
        _context: object, target: Path, _container: str | None = None
    ) -> tuple[str, str, list[str]]:
        target.write_bytes(b"postgres-consistency-dump\n")
        return (
            "sha256:" + hashlib.sha256(b"system-identifier").hexdigest(),
            "postgres@sha256:" + "c" * 64,
            ["postgres", "weave_backend", "weave_keycloak"],
        )

    @staticmethod
    def _write_volume(_context: object, volume: str, target: Path) -> None:
        target.write_bytes(("volume:" + volume).encode("utf-8"))

    @staticmethod
    def _write_private(_context: object, target: Path) -> None:
        target.write_bytes(b"private-config-with-secretrefs")

    def _environment(self) -> dict[str, str]:
        return {
            "WEAVE_CANDIDATE_COMMIT": self.CANDIDATE,
            "WEAVE_CANDIDATE_MANIFEST_DIGEST": self.CANDIDATE_MANIFEST,
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
        self.assertEqual(manifest["schemaVersion"], "weave.compose-private-backup.v3")
        self.assertEqual(
            manifest["postgresDumpClientImage"],
            "postgres@sha256:" + "c" * 64,
        )
        self.assertEqual(
            manifest["postgresDatabases"],
            ["postgres", "weave_backend", "weave_keycloak"],
        )
        self.assertEqual(manifest["candidateCommit"], self.CANDIDATE)
        self.assertEqual(
            manifest["candidateManifestDigest"], self.CANDIDATE_MANIFEST
        )
        self.assertRegex(
            manifest["postgresDatabaseInventoryDigest"],
            r"^sha256:[0-9a-f]{64}$",
        )
        self.assertEqual(manifest["composeProject"], "weave-dogfood")
        self.assertEqual(manifest["quiescedServices"], list(backup_runtime.QUIESCED_SERVICES))
        self.assertEqual(manifest["runtimeInventory"], inventory)
        self.assertFalse(manifest["supportSafe"])
        self.assertTrue(manifest["containsSecretsOrMemberData"])
        self.assertEqual(
            len(manifest["artifacts"]),
            len(backup_runtime.active_volume_artifacts(self.context)) + 2,
        )
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
        self.assertEqual(
            list(self.backup_root.iterdir()),
            [],
            "a failed private backup must not leave a partial directory",
        )

    def test_retired_backup_binds_inventory_digest_and_exact_artifacts(self) -> None:
        retired = load_retired_inventory(ROOT / "fresh-start-targets.json")
        running = list(backup_runtime.QUIESCED_SERVICES)
        inventory = [
            {
                "service": service,
                "authority": "former-state-adoption",
                "container": retired.containers[service],
            }
            for service in running
        ]
        with (
            mock.patch.dict(os.environ, self._environment(), clear=False),
            mock.patch.object(
                backup_runtime,
                "_running_services",
                return_value=(running, inventory),
            ),
            mock.patch.object(backup_runtime, "_stop"),
            mock.patch.object(backup_runtime, "_start"),
            mock.patch.object(
                backup_runtime,
                "_postgres_dump",
                side_effect=self._write_postgres,
            ),
            mock.patch.object(
                backup_runtime,
                "_archive_volume",
                side_effect=self._write_volume,
            ),
            mock.patch.object(
                backup_runtime,
                "_archive_private_config",
                side_effect=self._write_private,
            ),
        ):
            destination = backup_runtime.backup(self.context, retired)

        manifest = json.loads(
            (destination / "BackupManifest.json").read_text(encoding="utf-8")
        )
        self.assertEqual(manifest["composeProject"], retired.namespace)
        self.assertEqual(manifest["retiredInventoryDigest"], retired.digest)
        self.assertEqual(
            {item["path"] for item in manifest["artifacts"]},
            {
                "postgres.sql",
                "private-config-secrets.tgz",
                *(item.archive for item in retired.backup_volumes),
            },
        )

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
        self.assertEqual(
            archive_command[
                archive_command.index("--network") :
                archive_command.index("--network") + 2
            ],
            ["--network", "none"],
        )
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

    def test_volume_archiver_reports_bounded_support_safe_docker_failure(self) -> None:
        target = self.backup_root / "caddy-config.tgz"
        target.parent.mkdir(parents=True)
        failure = subprocess.CalledProcessError(
            125,
            ["docker", "run", "--secret-value-must-not-be-repeated"],
            stderr=(
                b"docker: failed to mount /Users/example/private/location: "
                b"daemon rejected helper startup\n"
            ),
        )
        with mock.patch.object(
            backup_runtime.subprocess,
            "run",
            side_effect=[mock.Mock(returncode=0), failure],
        ):
            with self.assertRaisesRegex(
                ContractError,
                r"archive helper failed.*exit 125.*<path>.*daemon rejected",
            ) as raised:
                backup_runtime._archive_volume(
                    self.context,
                    "weave-caddy-config",
                    target,
                )

        diagnostic = str(raised.exception)
        self.assertNotIn("/Users/example", diagnostic)
        self.assertNotIn("secret-value", diagnostic)

    def test_volume_archiver_retries_only_the_docker_digest_race(self) -> None:
        target = self.backup_root / "caddy-config.tgz"
        target.parent.mkdir(parents=True)
        digest_race = subprocess.CalledProcessError(
            125,
            ["docker", "run"],
            stderr=b"docker: cannot overwrite digest sha256:" + b"a" * 64,
        )
        completed = subprocess.CompletedProcess(["docker", "run"], 0, b"", b"")
        with (
            mock.patch.object(
                backup_runtime.subprocess,
                "run",
                side_effect=[mock.Mock(returncode=0), digest_race, completed],
            ) as run,
            mock.patch.object(backup_runtime.time, "sleep") as sleep,
        ):
            backup_runtime._archive_volume(
                self.context,
                "weave-caddy-config",
                target,
            )

        self.assertEqual(run.call_count, 3)
        sleep.assert_called_once_with(backup_runtime.BACKUP_HELPER_RETRY_DELAY_SECONDS)

    def test_volume_archiver_stops_after_bounded_digest_races(self) -> None:
        target = self.backup_root / "caddy-config.tgz"
        target.parent.mkdir(parents=True)
        digest_race = subprocess.CalledProcessError(
            125,
            ["docker", "run"],
            stderr=b"docker: cannot overwrite digest sha256:" + b"a" * 64,
        )
        with (
            mock.patch.object(
                backup_runtime.subprocess,
                "run",
                side_effect=[mock.Mock(returncode=0)]
                + [digest_race] * backup_runtime.BACKUP_HELPER_MAX_ATTEMPTS,
            ),
            mock.patch.object(backup_runtime.time, "sleep") as sleep,
        ):
            with self.assertRaisesRegex(
                ContractError,
                r"archive helper failed.*exit 125.*cannot overwrite digest",
            ):
                backup_runtime._archive_volume(
                    self.context,
                    "weave-caddy-config",
                    target,
                )

        self.assertEqual(
            sleep.call_count,
            backup_runtime.BACKUP_HELPER_MAX_ATTEMPTS - 1,
        )

    def test_postgres_dump_client_is_bound_to_the_running_published_digest(
        self,
    ) -> None:
        image_id = "sha256:" + "d" * 64
        published = "postgres@sha256:" + "e" * 64
        with mock.patch.object(
            backup_runtime.subprocess,
            "run",
            side_effect=[
                subprocess.CompletedProcess(
                    ("docker", "container", "inspect"),
                    0,
                    json.dumps([{"Image": image_id}]),
                    "",
                ),
                subprocess.CompletedProcess(
                    ("docker", "image", "inspect"),
                    0,
                    json.dumps([{"RepoDigests": [published]}]),
                    "",
                ),
            ],
        ):
            self.assertEqual(
                backup_runtime._published_postgres_image("weave-db"),
                published,
            )

    def test_postgres_dump_client_rejects_mutable_or_local_only_image(
        self,
    ) -> None:
        image_id = "sha256:" + "d" * 64
        with mock.patch.object(
            backup_runtime.subprocess,
            "run",
            side_effect=[
                subprocess.CompletedProcess(
                    ("docker", "container", "inspect"),
                    0,
                    json.dumps([{"Image": image_id}]),
                    "",
                ),
                subprocess.CompletedProcess(
                    ("docker", "image", "inspect"),
                    0,
                    json.dumps([{"RepoDigests": []}]),
                    "",
                ),
            ],
        ):
            with self.assertRaisesRegex(
                ContractError, "published immutable digest"
            ):
                backup_runtime._published_postgres_image("weave-db")

    def test_dev_profile_and_unbound_candidate_fail_closed(self) -> None:
        self.context.profile = "dev"
        self.context.environment = "dev"
        with mock.patch.dict(os.environ, self._environment(), clear=False):
            with self.assertRaisesRegex(ContractError, "dogfood/prod"):
                backup_runtime.backup(self.context)
        self.context.profile = "dogfood"
        self.context.environment = "dogfood"
        environment = self._environment()
        environment["WEAVE_CANDIDATE_COMMIT"] = "branch-name"
        with mock.patch.dict(os.environ, environment, clear=False):
            with self.assertRaisesRegex(ContractError, "exact candidate"):
                backup_runtime.backup(self.context)

    def test_pending_registration_handoff_blocks_backup_before_runtime_mutation(
        self,
    ) -> None:
        handoffs = (
            self.secrets
            / "agent-runtime/workloads/weave/agent-runtime/registration-handoffs"
        )
        handoffs.mkdir(parents=True)
        (handoffs / "weaver-cell-example").write_text(
            "protected-fixture", encoding="utf-8"
        )

        with (
            mock.patch.dict(os.environ, self._environment(), clear=False),
            mock.patch.object(backup_runtime, "_running_services") as running,
        ):
            with self.assertRaisesRegex(
                ContractError, "pending registration authority operation"
            ):
                backup_runtime.backup(self.context)
        running.assert_not_called()

    def test_unsafe_registration_operation_root_blocks_backup(self) -> None:
        operation_parent = (
            self.secrets / "agent-runtime/workloads/weave/agent-runtime"
        )
        operation_parent.mkdir(parents=True)
        outside = Path(self.temporary.name) / "outside"
        outside.mkdir()
        (operation_parent / "registration-deletions").symlink_to(
            outside, target_is_directory=True
        )

        with mock.patch.dict(os.environ, self._environment(), clear=False):
            with self.assertRaisesRegex(
                ContractError, "registration authority operation root is unsafe"
            ):
                backup_runtime.backup(self.context)


if __name__ == "__main__":
    unittest.main()
