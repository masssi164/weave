#!/usr/bin/env python3
"""Fixture tests for the human-testing readiness manifest guard."""

from __future__ import annotations

import copy
import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SCRIPT = ROOT / "tools" / "human_testing_readiness_manifest.py"
FIXTURE = ROOT / "tools" / "fixtures" / "human_testing_readiness" / "green.json"


class HumanTestingReadinessManifestTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temp = tempfile.TemporaryDirectory()
        self.manifest = Path(self.temp.name) / "manifest.json"
        self.data = json.loads(FIXTURE.read_text(encoding="utf-8"))

    def tearDown(self) -> None:
        self.temp.cleanup()

    def run_validate(self, data: dict, *, require_ready: bool = False) -> subprocess.CompletedProcess[str]:
        self.manifest.write_text(json.dumps(data), encoding="utf-8")
        command = [sys.executable, str(SCRIPT), "validate", "--manifest", str(self.manifest)]
        if require_ready:
            command.append("--require-ready")
        return subprocess.run(
            command,
            cwd=ROOT,
            text=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            check=False,
        )

    def test_green_manifest_is_ready(self) -> None:
        completed = self.run_validate(self.data, require_ready=True)
        self.assertEqual(completed.returncode, 0, completed.stdout + completed.stderr)
        self.assertIn("humanTestingReady=true", completed.stdout)

    def test_degraded_current_capability_is_blocked(self) -> None:
        data = copy.deepcopy(self.data)
        data["providerHealth"]["overall"] = "degraded"
        data["providerHealth"]["capabilities"]["calendar"] = "degraded"
        data["state"] = "blocked"
        data["humanTestingReady"] = False
        completed = self.run_validate(data, require_ready=True)
        self.assertEqual(completed.returncode, 1, completed.stdout + completed.stderr)
        self.assertIn("providerHealth.overall=degraded", completed.stdout)

    def test_failed_surface_is_failed_not_blocked(self) -> None:
        data = copy.deepcopy(self.data)
        data["surfaces"]["files"]["status"] = "failed"
        data["state"] = "failed"
        data["humanTestingReady"] = False
        completed = self.run_validate(data)
        self.assertEqual(completed.returncode, 0, completed.stdout + completed.stderr)
        self.assertIn("failed: surfaces.files=failed", completed.stdout)

    def test_waiting_approval_requires_exact_blocker_context(self) -> None:
        data = copy.deepcopy(self.data)
        data["blockers"] = [
            {
                "code": "environment-approval-waiting",
                "summary": "Protected distribution approval is waiting.",
                "candidateCommit": data["candidateCommit"],
            }
        ]
        data["state"] = "blocked"
        data["humanTestingReady"] = False
        completed = self.run_validate(data)
        self.assertEqual(completed.returncode, 2)
        self.assertIn("environment", completed.stderr)

    def test_simulator_cannot_replace_physical_iphone(self) -> None:
        data = copy.deepcopy(self.data)
        data["physicalAcceptance"]["physicalIPhone"] = False
        data["state"] = "blocked"
        data["humanTestingReady"] = False
        completed = self.run_validate(data, require_ready=True)
        self.assertEqual(completed.returncode, 1, completed.stdout + completed.stderr)
        self.assertIn("simulator evidence is insufficient", completed.stdout)

    def test_one_collaboration_pass_is_blocked(self) -> None:
        data = copy.deepcopy(self.data)
        data["collaboration"]["repeatCount"] = 1
        data["state"] = "blocked"
        data["humanTestingReady"] = False
        completed = self.run_validate(data)
        self.assertEqual(completed.returncode, 0, completed.stdout + completed.stderr)
        self.assertIn("has not passed twice", completed.stdout)

    def test_raw_identity_material_is_rejected(self) -> None:
        data = copy.deepcopy(self.data)
        data["collaboration"]["email"] = "author@example.test"
        completed = self.run_validate(data)
        self.assertEqual(completed.returncode, 2)
        self.assertIn("support-safe validation failed", completed.stderr)

    def test_credential_bearing_or_query_evidence_reference_is_rejected(self) -> None:
        for reference in (
            "https://user:secret@example.invalid/run/1",
            "https://example.invalid/run/1?signature=secret",
            "/tmp/private-evidence.json",
        ):
            with self.subTest(reference=reference):
                data = copy.deepcopy(self.data)
                data["evidence"][0] = reference
                completed = self.run_validate(data)
                self.assertEqual(completed.returncode, 2)
                self.assertIn("support-safe", completed.stderr)

    def test_build_commit_mismatch_cannot_be_declared_ready(self) -> None:
        data = copy.deepcopy(self.data)
        data["builds"]["client"]["commit"] = "3" * 40
        completed = self.run_validate(data)
        self.assertEqual(completed.returncode, 2)
        self.assertIn("declared state", completed.stderr)


if __name__ == "__main__":
    unittest.main(verbosity=2)
