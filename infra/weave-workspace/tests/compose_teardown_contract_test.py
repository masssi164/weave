#!/usr/bin/env python3
"""Unit evidence for exact-ownership isolated Compose teardown."""

from __future__ import annotations

import json
import os
import subprocess
import sys
import unittest
from pathlib import Path
from types import SimpleNamespace
from unittest import mock

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))

import teardown_compose  # noqa: E402
from compose_env import ContractError  # noqa: E402


class ComposeTeardownContractTest(unittest.TestCase):
    CANDIDATE = "c" * 40

    def setUp(self) -> None:
        env = {
            "WEAVE_RESOURCE_PREFIX": "weave-e2e-run-123456",
            "WEAVE_STACK_SCOPE": "isolated",
            "WEAVE_DOCKER_NETWORK": "weave-e2e-run-123456_network",
            "WEAVE_COMPOSE_PROJECT": "weave-e2e-run-123456",
        }
        for key in teardown_compose.VOLUME_KEYS:
            env[key] = "weave_e2e_run_123456_" + key.removeprefix("WEAVE_").removesuffix("_VOLUME").lower()
        self.context = SimpleNamespace(
            profile="dogfood",
            isolated_namespace="weave-e2e-run-123456",
            env=env,
            root=ROOT,
            compose_base_command=("docker", "compose", "--project-name", "weave-e2e-run-123456"),
        )

    def test_dry_run_verifies_ownership_without_mutation(self) -> None:
        with (
            mock.patch.dict(os.environ, {"WEAVE_CANDIDATE_COMMIT": self.CANDIDATE}, clear=False),
            mock.patch.object(teardown_compose, "_assert_owned", return_value=True) as owned,
            mock.patch.object(teardown_compose.subprocess, "run") as run,
        ):
            evidence = teardown_compose.teardown(self.context, dry_run=True)
        self.assertEqual(owned.call_count, len(teardown_compose.VOLUME_KEYS) + 1)
        run.assert_not_called()
        self.assertTrue(evidence["dryRun"])
        self.assertEqual(evidence["removedVolumeNames"], [])
        self.assertFalse(evidence["networkRemoved"])
        self.assertTrue(evidence["ownershipLabelsVerified"])
        self.assertTrue(evidence["supportSafe"])
        self.assertFalse(evidence["containsSecretValues"])

    def test_execute_removes_only_verified_project_volumes_and_network(self) -> None:
        with (
            mock.patch.dict(os.environ, {"WEAVE_CANDIDATE_COMMIT": self.CANDIDATE}, clear=False),
            mock.patch.object(teardown_compose, "_assert_owned", return_value=True),
            mock.patch.object(teardown_compose, "compose_environment", return_value={}),
            mock.patch.object(teardown_compose.subprocess, "run", return_value=subprocess.CompletedProcess([], 0)) as run,
        ):
            evidence = teardown_compose.teardown(self.context, dry_run=False)
        commands = [call.args[0] for call in run.call_args_list]
        self.assertEqual(commands[0][-2:], ["down", "--remove-orphans"])
        self.assertEqual(
            commands[1 : 1 + len(teardown_compose.VOLUME_KEYS)],
            [["docker", "volume", "rm", self.context.env[key]] for key in teardown_compose.VOLUME_KEYS],
        )
        self.assertEqual(commands[-1], ["docker", "network", "rm", self.context.env["WEAVE_DOCKER_NETWORK"]])
        self.assertEqual(evidence["removedVolumeNames"], sorted(self.context.env[key] for key in teardown_compose.VOLUME_KEYS))
        self.assertTrue(evidence["networkRemoved"])

    def test_persistent_or_unbound_invocation_fails_before_docker(self) -> None:
        self.context.isolated_namespace = None
        self.context.env["WEAVE_STACK_SCOPE"] = "persistent"
        with mock.patch.object(teardown_compose.subprocess, "run") as run:
            with self.assertRaisesRegex(ContractError, "run-scoped isolated"):
                teardown_compose.teardown(self.context, dry_run=False)
        run.assert_not_called()

        self.context.isolated_namespace = "weave-e2e-run-123456"
        self.context.env["WEAVE_STACK_SCOPE"] = "isolated"
        with (
            mock.patch.dict(os.environ, {"WEAVE_CANDIDATE_COMMIT": "branch-name"}, clear=False),
            mock.patch.object(teardown_compose.subprocess, "run") as run,
        ):
            with self.assertRaisesRegex(ContractError, "exact WEAVE_CANDIDATE_COMMIT"):
                teardown_compose.teardown(self.context, dry_run=False)
        run.assert_not_called()

    def test_label_mismatch_is_refused(self) -> None:
        observed = {
            "com.massimotter.weave.managed": "true",
            "com.massimotter.weave.environment": "dogfood",
            "com.massimotter.weave.namespace": "weave",
            "com.massimotter.weave.scope": "persistent",
        }
        completed = subprocess.CompletedProcess([], 0, stdout=json.dumps(observed))
        with mock.patch.object(teardown_compose.subprocess, "run", return_value=completed):
            with self.assertRaisesRegex(ContractError, "refusing to remove unowned Docker volume"):
                teardown_compose._assert_owned(self.context, "volume", "weave_db_data")


if __name__ == "__main__":
    unittest.main()
