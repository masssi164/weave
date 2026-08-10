#!/usr/bin/env python3
"""Unit tests for persistent dogfood resource continuity evidence."""

from __future__ import annotations

import importlib.util
import tempfile
import unittest
from argparse import Namespace
from pathlib import Path
from unittest.mock import patch


ROOT = Path(__file__).resolve().parents[1]
SPEC = importlib.util.spec_from_file_location("dogfood_resource_continuity", ROOT / "tools/dogfood_resource_continuity.py")
assert SPEC and SPEC.loader
module = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(module)


class DogfoodResourceContinuityTest(unittest.TestCase):
    def arguments(self, root: Path) -> Namespace:
        certificate = root / "cert.pem"
        certificate.write_text("-----BEGIN CERTIFICATE-----\nfixture\n", encoding="ascii")
        values = {role.replace("-", "_"): "weave_dogfood_" + role.replace("-", "_") for role in module.VOLUME_ROLES}
        return Namespace(
            mode="capture",
            compose_project="weave-dogfood",
            generation="fresh-0123456789ab",
            ca_file=certificate,
            gateway_certificate=certificate,
            mailpit_certificate=certificate,
            baseline=root / "baseline.json",
            output=root / "comparison.json",
            **values,
        )

    def test_snapshot_contains_only_hashes_and_safe_booleans(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            args = self.arguments(Path(directory))
            projection = {
                "nameSha256": "sha256:" + "1" * 64,
                "driver": "local",
                "labelsSha256": "sha256:" + "2" * 64,
            }
            with patch.object(module, "volume_projection", return_value=projection), patch.object(module, "human_writer_absent", return_value=True):
                value = module.snapshot(args)
            self.assertTrue(value["humanWriterAbsent"])
            self.assertNotIn("weave_dogfood_database", str(value))
            self.assertFalse(value["containsSecretValues"])

    def test_runtime_boundary_rejects_bootstrap_mount_or_admin_environment(self) -> None:
        listed = type("Completed", (), {"stdout": "one\n"})()
        with patch.object(module.subprocess, "run", return_value=listed), patch.object(
            module,
            "docker_json",
            return_value=[
                {
                    "Config": {
                        "Labels": {
                            "com.docker.compose.service": "backend",
                            "com.massimotter.weave.managed": "true",
                            "com.massimotter.weave.environment": "dogfood",
                            "com.massimotter.weave.scope": "persistent",
                            "com.massimotter.weave.stack": "weave",
                            "com.massimotter.weave.generation": "fresh-0123456789ab",
                        },
                        "Env": ["WEAVE_IDENTITY_BOOTSTRAP_OWNER_ENABLED=true"],
                    },
                    "Mounts": [],
                }
            ],
        ):
            self.assertFalse(module.human_writer_absent("weave-dogfood", "fresh-0123456789ab"))

    def test_volume_projection_requires_exact_role_and_generation_labels(self) -> None:
        labels = {
            "com.massimotter.weave.managed": "true",
            "com.massimotter.weave.environment": "dogfood",
            "com.massimotter.weave.scope": "persistent",
            "com.massimotter.weave.stack": "weave",
            "com.massimotter.weave.generation": "fresh-0123456789ab",
            "com.massimotter.weave.component": "identity",
            "com.massimotter.weave.data-class": "identity-sensitive",
        }
        with patch.object(module, "docker_json", return_value=[{"Labels": labels, "Driver": "local"}]):
            projection = module.volume_projection(
                "keycloak-runtime", "weave_dogfood_keycloak", "fresh-0123456789ab"
            )
            self.assertEqual(projection["driver"], "local")
            with self.assertRaises(module.ContinuityError):
                module.volume_projection("database", "weave_dogfood_keycloak", "fresh-0123456789ab")

    def test_exact_snapshot_comparison_emits_v3_contract(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            args = self.arguments(root)
            value = {
                "schemaVersion": "weave.persistent-dogfood-resource-snapshot.v1",
                "volumes": {},
                "certificates": {},
                "generation": "fresh-0123456789ab",
                "humanWriterAbsent": True,
                "supportSafe": True,
                "containsSecretValues": False,
            }
            result = module.comparison(value, value)
            self.assertEqual(result["schemaVersion"], "weave.persistent-dogfood-comparison.v3")
            self.assertTrue(result["identityStoreVolumePreserved"])
            changed = {**value, "humanWriterAbsent": False}
            with self.assertRaises(module.ContinuityError):
                module.comparison(value, changed)


if __name__ == "__main__":
    unittest.main(verbosity=2)
