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
    MANIFEST = "sha256:" + "d" * 64
    CONTAINER_ID = "e" * 64

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
            profile="test",
            isolated_namespace="weave-e2e-run-123456",
            env=env,
            root=ROOT,
            generated_root=ROOT / ".generated/isolated/weave-e2e-run-123456",
            compose_base_command=("docker", "compose", "--project-name", "weave-e2e-run-123456"),
        )
        self.binding = teardown_compose.OwnershipBinding(
            self.CANDIDATE,
            self.MANIFEST,
            self.context.env["WEAVE_COMPOSE_PROJECT"],
        )

    def evidence_environment(self) -> dict[str, str]:
        return {
            "WEAVE_CANDIDATE_COMMIT": self.CANDIDATE,
            "WEAVE_CANDIDATE_MANIFEST_DIGEST": self.MANIFEST,
        }

    def test_dry_run_verifies_ownership_without_mutation(self) -> None:
        with (
            mock.patch.dict(os.environ, self.evidence_environment(), clear=False),
            mock.patch.object(teardown_compose, "_assert_owned", return_value=True) as owned,
            mock.patch.object(teardown_compose, "_owned_containers", return_value=[]),
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
        self.assertEqual(evidence["candidateManifestDigest"], self.MANIFEST)
        self.assertEqual(evidence["removedNetworkName"], "")
        self.assertEqual(evidence["composeDownStatus"], "not-run")
        self.assertFalse(evidence["fallbackAttempted"])
        self.assertEqual(evidence["observedContainerCount"], 0)
        self.assertEqual(evidence["remainingOwnedResources"], len(teardown_compose.VOLUME_KEYS) + 1)

    def test_execute_removes_only_verified_project_volumes_and_network(self) -> None:
        with (
            mock.patch.dict(os.environ, self.evidence_environment(), clear=False),
            mock.patch.object(teardown_compose, "_assert_owned", return_value=True),
            mock.patch.object(teardown_compose, "_owned_containers", return_value=[]),
            mock.patch.object(
                teardown_compose,
                "_remaining_owned_resource_counts",
                return_value=(0, 0, 0),
            ),
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
        self.assertEqual(
            evidence["removedNetworkName"],
            self.context.env["WEAVE_DOCKER_NETWORK"],
        )
        self.assertEqual(evidence["composeDownStatus"], "passed")
        self.assertFalse(evidence["fallbackAttempted"])
        self.assertEqual(evidence["remainingOwnedResources"], 0)

    def test_failed_compose_down_uses_only_exact_owned_container_fallback(self) -> None:
        results = iter(
            [subprocess.CompletedProcess([], 1)]
            + [subprocess.CompletedProcess([], 0)] * (len(teardown_compose.VOLUME_KEYS) + 1)
        )
        with (
            mock.patch.dict(os.environ, self.evidence_environment(), clear=False),
            mock.patch.object(teardown_compose, "_assert_owned", return_value=True),
            mock.patch.object(teardown_compose, "_owned_containers", return_value=[]),
            mock.patch.object(
                teardown_compose,
                "_remaining_owned_resource_counts",
                return_value=(0, 0, 0),
            ),
            mock.patch.object(teardown_compose, "compose_environment", return_value={}),
            mock.patch.object(
                teardown_compose,
                "_remove_remaining_owned_containers",
                return_value=(2, 1),
            ) as fallback,
            mock.patch.object(
                teardown_compose.subprocess,
                "run",
                side_effect=lambda *_args, **_kwargs: next(results),
            ),
        ):
            evidence = teardown_compose.teardown(self.context, dry_run=False)
        fallback.assert_called_once()
        self.assertEqual(evidence["composeDownStatus"], "failed")
        self.assertTrue(evidence["fallbackAttempted"])
        self.assertEqual(evidence["fallbackObservedContainerCount"], 2)
        self.assertEqual(evidence["removedContainerCount"], 1)
        self.assertEqual(evidence["remainingOwnedResources"], 0)

    def test_successful_compose_down_with_a_remaining_container_uses_fallback(self) -> None:
        remaining = [(self.CONTAINER_ID, "weave-e2e-run-123456-mcp-server")]
        with (
            mock.patch.dict(os.environ, self.evidence_environment(), clear=False),
            mock.patch.object(teardown_compose, "_assert_owned", return_value=False),
            mock.patch.object(
                teardown_compose,
                "_owned_containers",
                side_effect=[remaining, remaining],
            ),
            mock.patch.object(
                teardown_compose,
                "_remove_remaining_owned_containers",
                return_value=(1, 1),
            ) as fallback,
            mock.patch.object(
                teardown_compose,
                "_remaining_owned_resource_counts",
                return_value=(0, 0, 0),
            ),
            mock.patch.object(teardown_compose, "compose_environment", return_value={}),
            mock.patch.object(
                teardown_compose.subprocess,
                "run",
                return_value=subprocess.CompletedProcess([], 0),
            ),
        ):
            evidence = teardown_compose.teardown(self.context, dry_run=False)
        fallback.assert_called_once()
        self.assertEqual(evidence["composeDownStatus"], "passed")
        self.assertTrue(evidence["fallbackAttempted"])
        self.assertEqual(evidence["observedContainerCount"], 1)
        self.assertEqual(evidence["remainingContainerCount"], 0)

    def test_container_fallback_retries_a_transient_zombie_until_none_remain(self) -> None:
        with (
            mock.patch.object(
                teardown_compose,
                "_owned_containers",
                side_effect=[
                    [(self.CONTAINER_ID, "weave-e2e-run-123456-mcp-server")],
                    [],
                ],
            ),
            mock.patch.object(
                teardown_compose.subprocess,
                "run",
                return_value=subprocess.CompletedProcess([], 1),
            ) as run,
            mock.patch.object(teardown_compose.time, "sleep") as sleep,
        ):
            observed, removed = teardown_compose._remove_remaining_owned_containers(
                self.context,
                self.binding,
                deadline=teardown_compose.time.monotonic() + 100,
            )
        self.assertEqual(observed, 1)
        self.assertEqual(removed, 0)
        self.assertEqual(
            run.call_args.args[0],
            ["docker", "container", "rm", "--force", self.CONTAINER_ID],
        )
        sleep.assert_called_once_with(teardown_compose.CONTAINER_REMOVAL_WAIT_SECONDS)

    def test_permanently_stuck_container_exhausts_the_bounded_fallback(self) -> None:
        remaining = [(self.CONTAINER_ID, "weave-e2e-run-123456-mcp-server")]
        with (
            mock.patch.object(teardown_compose, "CONTAINER_REMOVAL_ATTEMPTS", 2),
            mock.patch.object(teardown_compose, "_owned_containers", return_value=remaining),
            mock.patch.object(
                teardown_compose,
                "_run_docker",
                return_value=subprocess.CompletedProcess([], 1),
            ),
            mock.patch.object(teardown_compose.time, "sleep"),
        ):
            with self.assertRaisesRegex(ContractError, "left exact owned containers"):
                teardown_compose._remove_remaining_owned_containers(
                    self.context,
                    self.binding,
                    deadline=teardown_compose.time.monotonic() + 100,
                )

    def test_docker_inspect_timeout_is_support_safe_and_deadline_is_enforced(self) -> None:
        with mock.patch.object(
            teardown_compose.subprocess,
            "run",
            side_effect=subprocess.TimeoutExpired(["docker", "volume", "inspect"], 1),
        ):
            with self.assertRaisesRegex(ContractError, "Docker volume-inspect timed out"):
                teardown_compose._labels(
                    "volume",
                    "weave_e2e_run_123456_db_data",
                    deadline=teardown_compose.time.monotonic() + 10,
                )
        with self.assertRaisesRegex(ContractError, "deadline exhausted"):
            teardown_compose._remaining_timeout(teardown_compose.time.monotonic() - 1)

    def test_existing_resource_inspect_failure_is_not_treated_as_absent(self) -> None:
        with (
            mock.patch.object(teardown_compose, "INSPECT_CONSISTENCY_ATTEMPTS", 2),
            mock.patch.object(teardown_compose.time, "sleep") as sleep,
            mock.patch.object(
                teardown_compose.subprocess,
                "run",
                side_effect=(
                    subprocess.CompletedProcess([], 1, stdout=""),
                    subprocess.CompletedProcess(
                        [],
                        0,
                        stdout=self.context.env["WEAVE_DB_DATA_VOLUME"] + "\n",
                    ),
                    subprocess.CompletedProcess([], 1, stdout=""),
                    subprocess.CompletedProcess(
                        [],
                        0,
                        stdout=self.context.env["WEAVE_DB_DATA_VOLUME"] + "\n",
                    ),
                ),
            ),
        ):
            with self.assertRaisesRegex(
                ContractError, "could not inspect existing Docker volume"
            ):
                teardown_compose._labels(
                    "volume",
                    self.context.env["WEAVE_DB_DATA_VOLUME"],
                    deadline=teardown_compose.time.monotonic() + 10,
                )
        sleep.assert_called_once_with(teardown_compose.INSPECT_CONSISTENCY_WAIT_SECONDS)

    def test_container_disappearing_during_inspect_retry_is_absent(self) -> None:
        with (
            mock.patch.object(teardown_compose, "INSPECT_CONSISTENCY_ATTEMPTS", 2),
            mock.patch.object(teardown_compose.time, "sleep") as sleep,
            mock.patch.object(
                teardown_compose.subprocess,
                "run",
                side_effect=(
                    subprocess.CompletedProcess([], 1, stdout=""),
                    subprocess.CompletedProcess([], 0, stdout=self.CONTAINER_ID + "\n"),
                    subprocess.CompletedProcess([], 1, stdout=""),
                    subprocess.CompletedProcess([], 0, stdout=""),
                ),
            ),
        ):
            observed = teardown_compose._labels(
                "container",
                self.CONTAINER_ID,
                deadline=teardown_compose.time.monotonic() + 10,
            )
        self.assertIsNone(observed)
        sleep.assert_called_once_with(teardown_compose.INSPECT_CONSISTENCY_WAIT_SECONDS)

    def test_container_list_and_remove_calls_are_individually_bounded(self) -> None:
        deadline = teardown_compose.time.monotonic() + 10
        with mock.patch.object(
            teardown_compose.subprocess,
            "run",
            side_effect=subprocess.TimeoutExpired(["docker", "container", "ls"], 1),
        ):
            with self.assertRaisesRegex(ContractError, "Docker container-list timed out"):
                teardown_compose._owned_containers(
                    self.context,
                    self.binding,
                    deadline=deadline,
                )

        with mock.patch.object(
            teardown_compose.subprocess,
            "run",
            side_effect=subprocess.TimeoutExpired(["docker", "container", "rm"], 1),
        ):
            result = teardown_compose._run_docker(
                ["docker", "container", "rm", "--force", self.CONTAINER_ID],
                deadline=teardown_compose.time.monotonic() + 10,
                operation="container-remove",
                tolerate_timeout=True,
            )
        self.assertEqual(result.returncode, 124)


    def test_empty_evidence_environment_uses_generated_default(self) -> None:
        with mock.patch.dict(
            os.environ,
            {"WEAVE_TEARDOWN_EVIDENCE_FILE": ""},
            clear=False,
        ):
            output = teardown_compose._evidence_output_path(self.context, None)
        self.assertEqual(
            output,
            (self.context.generated_root / "teardown/evidence.json").resolve(),
        )
        self.assertNotEqual(output, self.context.root.resolve())

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
            mock.patch.dict(
                os.environ,
                {
                    "WEAVE_CANDIDATE_COMMIT": "branch-name",
                    "WEAVE_CANDIDATE_MANIFEST_DIGEST": self.MANIFEST,
                },
                clear=False,
            ),
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
            "com.massimotter.weave.candidate-commit": self.CANDIDATE,
            "com.massimotter.weave.candidate-manifest-digest": self.MANIFEST,
        }
        completed = subprocess.CompletedProcess([], 0, stdout=json.dumps(observed))
        with mock.patch.object(teardown_compose.subprocess, "run", return_value=completed):
            with self.assertRaisesRegex(ContractError, "refusing to remove unowned Docker volume"):
                teardown_compose._assert_owned(
                    self.context,
                    self.binding,
                    "volume",
                    "weave_db_data",
                    deadline=teardown_compose.time.monotonic() + 10,
                )

    def test_candidate_manifest_and_compose_project_mismatches_are_refused(self) -> None:
        correct = {
            "com.massimotter.weave.managed": "true",
            "com.massimotter.weave.environment": "test",
            "com.massimotter.weave.namespace": self.context.env["WEAVE_RESOURCE_PREFIX"],
            "com.massimotter.weave.scope": "isolated",
            "com.massimotter.weave.candidate-commit": self.CANDIDATE,
            "com.massimotter.weave.candidate-manifest-digest": self.MANIFEST,
            "com.docker.compose.project": self.context.env["WEAVE_COMPOSE_PROJECT"],
        }
        for key, value in (
            ("com.massimotter.weave.candidate-commit", "f" * 40),
            ("com.massimotter.weave.candidate-manifest-digest", "sha256:" + "f" * 64),
            ("com.docker.compose.project", "another-project"),
        ):
            with self.subTest(key=key):
                completed = subprocess.CompletedProcess(
                    [], 0, stdout=json.dumps({**correct, key: value})
                )
                with mock.patch.object(
                    teardown_compose.subprocess, "run", return_value=completed
                ):
                    with self.assertRaisesRegex(
                        ContractError, "refusing to remove unowned Docker container"
                    ):
                        teardown_compose._assert_owned(
                            self.context,
                            self.binding,
                            "container",
                            self.CONTAINER_ID,
                            deadline=teardown_compose.time.monotonic() + 10,
                        )


if __name__ == "__main__":
    unittest.main()
