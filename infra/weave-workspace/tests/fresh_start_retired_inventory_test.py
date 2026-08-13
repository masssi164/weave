#!/usr/bin/env python3
"""Contract tests for the exact retired-generation transition inventory."""

from __future__ import annotations

import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path
from unittest import mock

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))

from compose_env import ContractError  # noqa: E402
import backup_runtime  # noqa: E402
from fresh_start_retired_inventory import load_retired_inventory  # noqa: E402


class FreshStartRetiredInventoryTest(unittest.TestCase):
    def test_repository_inventory_binds_the_observed_legacy_topology(self) -> None:
        inventory = load_retired_inventory(ROOT / "fresh-start-targets.json")

        self.assertEqual(inventory.generation, "legacy-unlabeled-v1")
        self.assertEqual(inventory.namespace, "weave")
        self.assertEqual(inventory.database_container, "weave-db")
        self.assertEqual(inventory.network, "weave_network")
        self.assertEqual(len(inventory.containers), 9)
        self.assertEqual(len(inventory.volumes), 8)
        self.assertEqual(
            {item.archive for item in inventory.backup_volumes},
            {
                "caddy-config.tgz",
                "caddy-data.tgz",
                "keycloak-data.tgz",
                "matrix-appservice.tgz",
                "nextcloud-data.tgz",
                "synapse-data.tgz",
            },
        )
        self.assertRegex(inventory.digest, r"^sha256:[0-9a-f]{64}$")

    def test_duplicate_exact_identity_fails_closed(self) -> None:
        payload = json.loads(
            (ROOT / "fresh-start-targets.json").read_text(encoding="utf-8")
        )
        payload["targets"].append(dict(payload["targets"][0]))
        with tempfile.TemporaryDirectory() as temporary:
            path = Path(temporary) / "targets.json"
            path.write_text(json.dumps(payload), encoding="utf-8")
            with self.assertRaisesRegex(ContractError, "ambiguous"):
                load_retired_inventory(path)

    def test_missing_backup_volume_fails_closed(self) -> None:
        payload = json.loads(
            (ROOT / "fresh-start-targets.json").read_text(encoding="utf-8")
        )
        payload["targets"] = [
            item
            for item in payload["targets"]
            if item["name"] != "weave_synapse_data"
        ]
        with tempfile.TemporaryDirectory() as temporary:
            path = Path(temporary) / "targets.json"
            path.write_text(json.dumps(payload), encoding="utf-8")
            with self.assertRaisesRegex(ContractError, "backup volume inventory"):
                load_retired_inventory(path)

    def test_runtime_binding_requires_exact_network_and_accounted_mounts(self) -> None:
        inventory = load_retired_inventory(ROOT / "fresh-start-targets.json")

        def inspect(arguments: list[str], **_kwargs: object) -> subprocess.CompletedProcess[str]:
            name = arguments[-1]
            payload = [
                {
                    "Id": "a" * 64,
                    "Name": "/" + name,
                    "State": {"Status": "exited" if name == "weave-mcp-server" else "running"},
                    "NetworkSettings": {"Networks": {inventory.network: {}}},
                    "Mounts": [
                        {"Type": "volume", "Name": volume}
                        for volume in inventory.volumes
                    ],
                }
            ]
            return subprocess.CompletedProcess(arguments, 0, json.dumps(payload), "")

        with mock.patch.object(backup_runtime.subprocess, "run", side_effect=inspect):
            running, observed = backup_runtime._running_retired_services(inventory)

        self.assertEqual(len(running), 8)
        self.assertEqual(len(observed), 9)
        self.assertTrue(
            all(item["authority"] == "former-state-adoption" for item in observed)
        )

    def test_runtime_binding_rejects_an_unaccounted_mount(self) -> None:
        inventory = load_retired_inventory(ROOT / "fresh-start-targets.json")

        def inspect(arguments: list[str], **_kwargs: object) -> subprocess.CompletedProcess[str]:
            name = arguments[-1]
            mounts = list(inventory.volumes)
            if name == "weave-backend":
                mounts.append("foreign-volume")
            payload = [
                {
                    "Id": "b" * 64,
                    "Name": "/" + name,
                    "State": {"Status": "running"},
                    "NetworkSettings": {"Networks": {inventory.network: {}}},
                    "Mounts": [
                        {"Type": "volume", "Name": volume}
                        for volume in mounts
                    ],
                }
            ]
            return subprocess.CompletedProcess(arguments, 0, json.dumps(payload), "")

        with mock.patch.object(backup_runtime.subprocess, "run", side_effect=inspect):
            with self.assertRaisesRegex(ContractError, "unaccounted named volume"):
                backup_runtime._running_retired_services(inventory)


if __name__ == "__main__":
    unittest.main()
