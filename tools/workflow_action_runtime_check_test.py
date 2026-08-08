#!/usr/bin/env python3
"""Fixture coverage for the workflow action runtime and pin guard."""

from __future__ import annotations

import json
import tempfile
import unittest
from pathlib import Path

from workflow_action_runtime_check import PolicyError, validate


PIN = {
    "action": "example/supported-action",
    "commit": "a" * 40,
    "release": "v1.2.3",
    "runtime": "node24",
    "source": "https://github.com/example/supported-action",
}


class WorkflowActionRuntimeCheckTest(unittest.TestCase):
    def fixture(
        self,
        uses: str,
        *,
        pin: dict[str, str] | None = None,
    ) -> tuple[Path, Path, tempfile.TemporaryDirectory[str]]:
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        root = Path(temporary.name)
        workflows = root / ".github" / "workflows"
        workflows.mkdir(parents=True)
        (workflows / "ci.yml").write_text(
            f"name: fixture\njobs:\n  verify:\n    steps:\n      - uses: {uses}\n",
            encoding="utf-8",
        )
        manifest = root / "manifest.json"
        manifest.write_text(
            json.dumps(
                {
                    "schemaVersion": 1,
                    "reviewedAt": "2026-07-23",
                    "policy": "fixture",
                    "actions": [pin or PIN],
                }
            ),
            encoding="utf-8",
        )
        return root, manifest, temporary

    def test_accepts_reviewed_node24_commit_and_release_comment(self) -> None:
        root, manifest, _ = self.fixture(
            f"{PIN['action']}@{PIN['commit']} # {PIN['release']}"
        )
        self.assertEqual((1, 1), validate(root, manifest))

    def test_accepts_nested_action_from_canonical_repository(self) -> None:
        nested_pin = {
            **PIN,
            "action": "example/actions/supported-action",
            "source": "https://github.com/example/actions",
        }
        root, manifest, _ = self.fixture(
            f"{nested_pin['action']}@{nested_pin['commit']} # {nested_pin['release']}",
            pin=nested_pin,
        )
        self.assertEqual((1, 1), validate(root, manifest))

    def test_rejects_floating_release_ref(self) -> None:
        root, manifest, _ = self.fixture(f"{PIN['action']}@v1 # {PIN['release']}")
        with self.assertRaisesRegex(PolicyError, "must use reviewed commit"):
            validate(root, manifest)

    def test_rejects_missing_release_pin_comment(self) -> None:
        root, manifest, _ = self.fixture(f"{PIN['action']}@{PIN['commit']}")
        with self.assertRaisesRegex(PolicyError, "pin comment"):
            validate(root, manifest)

    def test_rejects_unreviewed_external_action(self) -> None:
        root, manifest, _ = self.fixture(
            f"example/other-action@{'b' * 40} # v1.0.0"
        )
        with self.assertRaisesRegex(PolicyError, "unreviewed action"):
            validate(root, manifest)

    def test_rejects_node20_manifest_runtime(self) -> None:
        legacy_pin = {**PIN, "runtime": "node20"}
        root, manifest, _ = self.fixture(
            f"{PIN['action']}@{PIN['commit']} # {PIN['release']}",
            pin=legacy_pin,
        )
        with self.assertRaisesRegex(PolicyError, "unsupported runtime"):
            validate(root, manifest)

    def test_local_actions_do_not_require_external_inventory(self) -> None:
        root, manifest, _ = self.fixture(
            f"{PIN['action']}@{PIN['commit']} # {PIN['release']}"
        )
        workflow = root / ".github" / "workflows" / "local.yml"
        workflow.write_text(
            "name: local\njobs:\n  verify:\n    steps:\n      - uses: ./actions/verify\n",
            encoding="utf-8",
        )
        self.assertEqual((1, 1), validate(root, manifest))


if __name__ == "__main__":
    unittest.main(verbosity=2)
