#!/usr/bin/env python3
"""Static and focused unit evidence for ephemeral dogfood owner bootstrap."""

from __future__ import annotations

import json
import os
import sys
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))

import compose_runtime  # noqa: E402
from compose_env import ContractError  # noqa: E402


class OwnerBootstrapLifecycleContractTest(unittest.TestCase):
    def test_private_request_is_exact_and_not_world_readable(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            path = Path(temporary) / "owner.json"
            path.write_text(
                json.dumps(
                    {
                        "displayName": "Owner",
                        "email": "OWNER@example.test",
                        "idempotencyKey": "owner-bootstrap-run-0001",
                    }
                ),
                encoding="utf-8",
            )
            os.chmod(path, 0o600)
            request = compose_runtime._owner_bootstrap_request(path)
            self.assertEqual(request["email"], "owner@example.test")
            os.chmod(path, 0o640)
            with self.assertRaisesRegex(ContractError, "group or other"):
                compose_runtime._owner_bootstrap_request(path)

    def test_boundary_detects_any_bootstrap_environment_or_mount(self) -> None:
        canonical = {"Config": {"Env": ["SPRING_PROFILES_ACTIVE=prod"]}, "Mounts": []}
        self.assertFalse(compose_runtime._bootstrap_boundary_present(canonical))
        self.assertTrue(
            compose_runtime._bootstrap_boundary_present(
                {
                    "Config": {"Env": ["WEAVE_IDENTITY_BOOTSTRAP_OWNER_ENABLED=true"]},
                    "Mounts": [],
                }
            )
        )
        self.assertTrue(
            compose_runtime._bootstrap_boundary_present(
                {
                    "Config": {"Env": []},
                    "Mounts": [
                        {"Destination": "/run/secrets/weave/bootstrap-owner"}
                    ],
                }
            )
        )

    def test_mailpit_matching_reads_recipient_projection_only(self) -> None:
        self.assertEqual(
            compose_runtime._mailpit_addresses(
                {"Address": "Owner@Example.Test", "Name": "Owner"}
            ),
            ["owner@example.test"],
        )
        self.assertEqual(
            compose_runtime._mailpit_addresses("activation body is not inspected"), []
        )

    def test_source_has_ephemeral_directory_and_unconditional_canonicalization(self) -> None:
        source = (ROOT / "scripts/compose_runtime.py").read_text(encoding="utf-8")
        lifecycle = source.split("def owner_bootstrap(", 1)[1].split("\ndef execute(", 1)[0]
        self.assertIn('context.environment != "dogfood"', lifecycle)
        self.assertIn('"--no-deps"', lifecycle)
        self.assertIn('"--force-recreate"', lifecycle)
        self.assertIn('"read_only": False', lifecycle)
        self.assertIn('"/run/secrets/weave/bootstrap-owner/token"', lifecycle)
        self.assertIn("finally:", lifecycle)
        self.assertIn("_canonical_backend(context)", lifecycle)
        self.assertIn("_bootstrap_disabled(context)", lifecycle)
        self.assertLess(
            lifecycle.index("_private_json(\n                request_anchor_path"),
            lifecycle.index("_bootstrap_override_command("),
        )
        self.assertIn('"requestAnchorPresent": request_anchor_path.is_file()', lifecycle)
        self.assertIn("shutil.rmtree(operation_root)", lifecycle)
        self.assertNotIn('compose(context, "down"', lifecycle)
        self.assertNotIn("providerInvitationId", lifecycle)


if __name__ == "__main__":
    unittest.main(verbosity=2)
