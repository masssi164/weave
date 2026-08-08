#!/usr/bin/env python3
"""Tests for assembling ordered candidate evidence into one manifest."""

from __future__ import annotations

import copy
import json
import subprocess
import sys
import tempfile
import unittest
from datetime import datetime, timedelta, timezone
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SCRIPT = ROOT / "tools" / "human_testing_readiness_assemble.py"
GREEN = ROOT / "tools" / "fixtures" / "human_testing_readiness" / "green.json"


class HumanTestingReadinessAssembleTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        self.green = json.loads(GREEN.read_text(encoding="utf-8"))
        self.green["providerHealth"]["observedAtUtc"] = (
            datetime.now(timezone.utc) - timedelta(seconds=60)
        ).isoformat().replace("+00:00", "Z")
        self.candidate = self.green["candidateCommit"]
        self.source_candidate = self.green["sourceCandidateCommit"]

    def tearDown(self) -> None:
        self.temp.cleanup()

    def documents(self) -> dict[str, dict]:
        return {
            "automated": {
                "schemaVersion": 1,
                "supportSafe": True,
                "candidateCommit": self.candidate,
                "sourceCandidateCommit": self.source_candidate,
                "specCorpusCommit": self.green["specCorpusCommit"],
                "candidateManifestDigest": self.green["candidateManifestDigest"],
                "images": copy.deepcopy(self.green["images"]),
                "realmArtifacts": copy.deepcopy(self.green["realmArtifacts"]),
                "evidenceModes": self.green["evidenceModes"],
                "liveE2eRunUrl": "https://github.com/example/weave/actions/runs/1",
                "surfaces": copy.deepcopy(self.green["surfaces"]),
                "collaboration": copy.deepcopy(self.green["collaboration"]),
                "evidenceRefs": ["artifact:live"],
                "blockers": [],
            },
            "deployment": {
                "schemaVersion": 1,
                "supportSafe": True,
                "candidateCommit": self.candidate,
                "sourceCandidateCommit": self.source_candidate,
                "candidateManifestDigest": self.green["candidateManifestDigest"],
                "realmArtifacts": copy.deepcopy(self.green["realmArtifacts"]),
                "candidateImages": {
                    "backend": "sha256:" + "5" * 64,
                    "mcp": "sha256:" + "6" * 64,
                    "keycloak": "sha256:" + "8" * 64,
                },
                "backendBuild": self.green["builds"]["backend"],
                "deployment": self.green["deployment"],
                "providerHealth": copy.deepcopy(self.green["providerHealth"]),
                "runUrl": "https://github.com/example/weave/actions/runs/2",
                "evidenceRefs": ["artifact:deployment"],
                "blockers": [],
            },
            "health": {
                "schemaVersion": "weave.dogfood-provider-health-evidence.v1",
                "supportSafe": True,
                "containsSecretValues": False,
                "candidateCommit": self.candidate,
                "sourceCandidateCommit": self.source_candidate,
                "specCorpusCommit": self.green["specCorpusCommit"],
                "candidateManifestDigest": self.green["candidateManifestDigest"],
                "images": copy.deepcopy(self.green["images"]),
                "deploymentRunUrl": "https://github.com/example/weave/actions/runs/2",
                "runUrl": "https://github.com/example/weave/actions/runs/4",
                "providerHealth": copy.deepcopy(self.green["providerHealth"]),
                "evidenceRefs": ["artifact:provider-health"],
                "blockers": [],
            },
            "distribution": {
                "schemaVersion": 2,
                "supportSafe": True,
                "laneCandidateCommit": self.candidate,
                "sourceCandidateCommit": self.source_candidate,
                "commit": self.source_candidate,
                "candidateManifestDigest": self.green["candidateManifestDigest"],
                "version": self.green["builds"]["client"]["version"],
                "buildNumber": self.green["builds"]["client"]["buildNumber"],
                "bundleId": self.green["builds"]["client"]["bundleId"],
                "channel": "testflight",
                "result": "success",
                "runUrl": "https://github.com/example/weave/actions/runs/3",
                "deploymentRunUrl": "https://github.com/example/weave/actions/runs/2",
                "liveE2eRunUrl": "https://github.com/example/weave/actions/runs/1",
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
            "--provider-health-evidence",
            str(paths["health"]),
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
        documents["distribution"]["laneCandidateCommit"] = "3" * 40
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

    def test_manifest_or_run_graph_mismatch_is_rejected(self) -> None:
        documents = self.documents()
        documents["distribution"]["candidateManifestDigest"] = "sha256:" + "f" * 64
        completed = self.run_assemble(documents)
        self.assertEqual(completed.returncode, 2)
        self.assertIn("candidate manifest", completed.stderr)

        documents = self.documents()
        documents["distribution"]["liveE2eRunUrl"] = "https://github.com/example/weave/actions/runs/99"
        completed = self.run_assemble(documents)
        self.assertEqual(completed.returncode, 2)
        self.assertIn("isolated live run", completed.stderr)

    def test_missing_proof_origin_is_rejected(self) -> None:
        documents = self.documents()
        documents["automated"]["surfaces"]["chat"]["proofKinds"] = ["fixture-ui"]
        completed = self.run_assemble(documents)
        self.assertEqual(completed.returncode, 2)
        self.assertIn("live-provider-backed", completed.stderr)

    def test_provider_health_must_match_the_deployment_graph(self) -> None:
        documents = self.documents()
        documents["health"]["deploymentRunUrl"] = "https://github.com/example/weave/actions/runs/99"
        completed = self.run_assemble(documents)
        self.assertEqual(completed.returncode, 2)
        self.assertIn("provider health", completed.stderr)

        documents = self.documents()
        documents["health"]["images"]["server"] = (
            "ghcr.io/example/other@sha256:" + "9" * 64
        )
        completed = self.run_assemble(documents)
        self.assertEqual(completed.returncode, 2)
        self.assertIn("image evidence", completed.stderr)

    def test_realm_artifacts_must_be_exact_secret_free_candidate_evidence(self) -> None:
        mutations = (
            lambda documents: documents["automated"]["realmArtifacts"].__setitem__(
                "baselineDigest", "sha256:not-a-digest"
            ),
            lambda documents: documents["automated"]["realmArtifacts"].__setitem__(
                "containsSecrets", True
            ),
            lambda documents: documents["deployment"]["realmArtifacts"].__setitem__(
                "migrationBundleDigest", "sha256:" + "f" * 64
            ),
        )
        for mutate in mutations:
            with self.subTest(mutation=mutate):
                documents = self.documents()
                mutate(documents)
                completed = self.run_assemble(documents)
                self.assertEqual(completed.returncode, 2, completed.stdout + completed.stderr)
                self.assertIn("realm artifact evidence", completed.stderr)


if __name__ == "__main__":
    unittest.main(verbosity=2)
