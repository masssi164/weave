#!/usr/bin/env python3
"""Tests for exact-candidate physical iPhone human evidence validation."""

from __future__ import annotations

import copy
import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SCRIPT = ROOT / "tools" / "physical_iphone_human_evidence.py"
GREEN = json.loads(
    (ROOT / "tools/fixtures/human_testing_readiness/green.json").read_text(encoding="utf-8")
)


class PhysicalIPhoneHumanEvidenceTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        self.submission = {
            "schemaVersion": "weave.physical-iphone-human-submission.v1",
            "supportSafe": True,
            "testerRefHash": GREEN["physicalAcceptance"]["testerRefHash"],
            "voiceOver": "passed",
            "sessionUpgrade": "passed",
            "navigation": "passed",
            "protocol": GREEN["physicalAcceptance"]["protocol"],
        }
        self.distribution = {
            "schemaVersion": "weave.ios-dogfood-distribution.v2",
            "supportSafe": True,
            "result": "success",
            "laneCandidateCommit": GREEN["candidateCommit"],
            "sourceCandidateCommit": GREEN["sourceCandidateCommit"],
            "commit": GREEN["sourceCandidateCommit"],
            "candidateManifestDigest": GREEN["candidateManifestDigest"],
            "version": GREEN["builds"]["client"]["version"],
            "buildNumber": GREEN["builds"]["client"]["buildNumber"],
            "bundleId": GREEN["builds"]["client"]["bundleId"],
            "deploymentRunId": "20",
            "githubRunId": "30",
            "deploymentRunUrl": "https://github.com/example/weave/actions/runs/20",
            "liveE2eRunUrl": "https://github.com/example/weave/actions/runs/10",
            "runUrl": "https://github.com/example/weave/actions/runs/30",
        }

    def tearDown(self) -> None:
        self.temp.cleanup()

    def write(self, name: str, value: dict) -> Path:
        path = self.root / name
        path.write_text(json.dumps(value), encoding="utf-8")
        return path

    def run_tool(self, submission: dict | None = None, distribution: dict | None = None) -> subprocess.CompletedProcess[str]:
        return subprocess.run(
            [
                sys.executable,
                str(SCRIPT),
                "--submission",
                str(self.write("submission.json", submission or self.submission)),
                "--distribution-evidence",
                str(self.write("distribution.json", distribution or self.distribution)),
                "--candidate-commit",
                GREEN["candidateCommit"],
                "--source-candidate-commit",
                GREEN["sourceCandidateCommit"],
                "--spec-commit",
                GREEN["specCorpusCommit"],
                "--candidate-manifest-digest",
                GREEN["candidateManifestDigest"],
                "--deployment-run-id",
                "20",
                "--distribution-run-id",
                "30",
                "--run-url",
                "https://github.com/example/weave/actions/runs/40",
                "--output",
                str(self.root / "physical.json"),
                "--require-passed",
            ],
            cwd=ROOT,
            text=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            check=False,
        )

    def test_accepts_exact_tester_confirmed_protocol(self) -> None:
        completed = self.run_tool()
        self.assertEqual(completed.returncode, 0, completed.stdout + completed.stderr)
        result = json.loads((self.root / "physical.json").read_text(encoding="utf-8"))
        self.assertEqual(result["physicalAcceptance"]["status"], "passed")
        self.assertEqual(len(result["physicalAcceptance"]["protocol"]["steps"]), 20)
        self.assertEqual(result["candidateManifestDigest"], GREEN["candidateManifestDigest"])

    def test_rejects_unperformed_step_or_missing_confirmation(self) -> None:
        submission = copy.deepcopy(self.submission)
        submission["protocol"]["steps"]["callsUi"]["status"] = "not_run"
        completed = self.run_tool(submission=submission)
        self.assertEqual(completed.returncode, 2)
        self.assertIn("incomplete", completed.stderr)

        submission = copy.deepcopy(self.submission)
        submission["protocol"]["testerConfirmed"] = False
        completed = self.run_tool(submission=submission)
        self.assertEqual(completed.returncode, 2)
        self.assertIn("incomplete", completed.stderr)

    def test_rejects_cross_candidate_distribution(self) -> None:
        distribution = copy.deepcopy(self.distribution)
        distribution["candidateManifestDigest"] = "sha256:" + "f" * 64
        completed = self.run_tool(distribution=distribution)
        self.assertEqual(completed.returncode, 2)
        self.assertIn("another candidate", completed.stderr)

    def test_rejects_private_or_credential_like_actual_outcomes(self) -> None:
        submission = copy.deepcopy(self.submission)
        submission["protocol"]["steps"]["filesUi"]["actualOutcome"] = "tester@example.invalid"
        completed = self.run_tool(submission=submission)
        self.assertEqual(completed.returncode, 2)
        self.assertIn("private or credential-like", completed.stderr)


if __name__ == "__main__":
    unittest.main(verbosity=2)
