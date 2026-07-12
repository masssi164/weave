#!/usr/bin/env python3
"""Tests for assembling ordered candidate evidence into one manifest."""

from __future__ import annotations

import copy
import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SCRIPT = ROOT / "tools" / "human_testing_readiness_assemble.py"
GREEN = ROOT / "tools" / "fixtures" / "human_testing_readiness" / "green.json"


class HumanTestingReadinessAssembleTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        self.green = json.loads(GREEN.read_text(encoding="utf-8"))
        self.candidate = self.green["candidateCommit"]

    def tearDown(self) -> None:
        self.temp.cleanup()

    def documents(self) -> dict[str, dict]:
        return {
            "automated": {
                "schemaVersion": 1,
                "supportSafe": True,
                "candidateCommit": self.candidate,
                "specCorpusCommit": self.green["specCorpusCommit"],
                "surfaces": self.green["surfaces"],
                "collaboration": self.green["collaboration"],
                "evidenceRefs": ["artifact:live"],
                "blockers": [],
            },
            "deployment": {
                "schemaVersion": 1,
                "supportSafe": True,
                "candidateCommit": self.candidate,
                "backendBuild": self.green["builds"]["backend"],
                "deployment": self.green["deployment"],
                "providerHealth": self.green["providerHealth"],
                "evidenceRefs": ["artifact:deployment"],
                "blockers": [],
            },
            "distribution": {
                "schemaVersion": 2,
                "supportSafe": True,
                "commit": self.candidate,
                "version": self.green["builds"]["client"]["version"],
                "buildNumber": self.green["builds"]["client"]["buildNumber"],
                "bundleId": self.green["builds"]["client"]["bundleId"],
                "channel": "testflight",
                "result": "success",
                "runUrl": "https://github.com/example/weave/actions/runs/3",
                "blockers": [],
            },
            "physical": {
                "schemaVersion": 1,
                "supportSafe": True,
                "candidateCommit": self.candidate,
                "physicalAcceptance": self.green["physicalAcceptance"],
                "physicalEvidenceRef": "issue:physical/3",
                "blockers": [],
            },
        }

    def run_assemble(self, documents: dict[str, dict], *, ready: bool = True) -> subprocess.CompletedProcess[str]:
        paths: dict[str, Path] = {}
        for label, document in documents.items():
            path = self.root / f"{label}.json"
            path.write_text(json.dumps(document), encoding="utf-8")
            paths[label] = path
        command = [
            sys.executable,
            str(SCRIPT),
            "--candidate-commit",
            self.candidate,
            "--automated-evidence",
            str(paths["automated"]),
            "--deployment-evidence",
            str(paths["deployment"]),
            "--distribution-evidence",
            str(paths["distribution"]),
            "--physical-evidence",
            str(paths["physical"]),
            "--output",
            str(self.root / "result.json"),
        ]
        if ready:
            command.append("--require-ready")
        return subprocess.run(
            command,
            cwd=ROOT,
            text=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            check=False,
        )

    def test_green_ordered_evidence_assembles_ready_manifest(self) -> None:
        completed = self.run_assemble(self.documents())
        self.assertEqual(completed.returncode, 0, completed.stdout + completed.stderr)
        result = json.loads((self.root / "result.json").read_text(encoding="utf-8"))
        self.assertTrue(result["humanTestingReady"])
        self.assertEqual(result["state"], "ready")
        self.assertIn("PHYSICAL_IPHONE_VOICEOVER_RESULT", completed.stdout)

    def test_mismatched_distribution_candidate_fails(self) -> None:
        documents = self.documents()
        documents["distribution"]["commit"] = "2" * 40
        completed = self.run_assemble(documents)
        self.assertEqual(completed.returncode, 2)
        self.assertIn("distribution evidence targets", completed.stderr)

    def test_stable_signing_fallback_assembles_ready_manifest(self) -> None:
        documents = self.documents()
        documents["distribution"]["channel"] = "stable-signing-fallback"
        completed = self.run_assemble(documents)
        self.assertEqual(completed.returncode, 0, completed.stdout + completed.stderr)
        result = json.loads((self.root / "result.json").read_text(encoding="utf-8"))
        self.assertTrue(result["humanTestingReady"])
        self.assertEqual(result["distribution"]["channel"], "stable-signing-fallback")

    def test_non_physical_acceptance_stays_blocked(self) -> None:
        documents = self.documents()
        physical = copy.deepcopy(documents["physical"])
        physical["physicalAcceptance"]["physicalIPhone"] = False
        physical["physicalAcceptance"]["status"] = "blocked"
        physical["physicalAcceptance"]["voiceOver"] = "not_run"
        documents["physical"] = physical
        completed = self.run_assemble(documents)
        self.assertEqual(completed.returncode, 1, completed.stdout + completed.stderr)
        result = json.loads((self.root / "result.json").read_text(encoding="utf-8"))
        self.assertEqual(result["state"], "blocked")
        self.assertFalse(result["humanTestingReady"])


if __name__ == "__main__":
    unittest.main(verbosity=2)
