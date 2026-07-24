#!/usr/bin/env python3
"""Unit tests for the retired-infrastructure-path guard."""

from __future__ import annotations

import importlib.util
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SPEC = importlib.util.spec_from_file_location(
    "no_executable_opentofu_check",
    ROOT / "tools" / "no_executable_opentofu_check.py",
)
assert SPEC and SPEC.loader
module = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(module)


class NoExecutableOpenTofuCheckTest(unittest.TestCase):
    def test_accepts_compose_and_historical_prose_outside_active_surfaces(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "infra").mkdir()
            (root / "infra" / "compose.yaml").write_text("services: {}\n", encoding="utf-8")
            (root / "docs").mkdir()
            (root / "docs" / "history.md").write_text("Former OpenTofu plan.\n", encoding="utf-8")
            self.assertEqual(module.findings(root), [])

    def test_rejects_hcl_state_environment_and_commands(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            scripts = root / "infra" / "scripts"
            scripts.mkdir(parents=True)
            (root / "infra" / "main.tf").write_text("terraform {}\n", encoding="utf-8")
            (scripts / "deploy.sh").write_text(
                "#!/bin/sh\nTF_VAR_realm=weave tofu apply ./saved.tfstate\n",
                encoding="utf-8",
            )
            violations = module.findings(root)
            self.assertTrue(any("retired infrastructure/state file" in item for item in violations))
            self.assertTrue(any("TF_VAR_" in item for item in violations))
            self.assertTrue(any("OpenTofu command" in item for item in violations))
            self.assertTrue(any("retired state dependency" in item for item in violations))

    def test_allows_only_the_closed_historical_secret_map(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            migration = root / "infra" / "weave-workspace" / "migration"
            migration.mkdir(parents=True)
            (migration / "legacy-secret-map.json").write_text(
                '{"literalSecretKeys":["TF_VAR_backend_db_password"]}\n',
                encoding="utf-8",
            )
            self.assertEqual(module.findings(root), [])


if __name__ == "__main__":
    unittest.main()
