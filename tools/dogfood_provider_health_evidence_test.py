#!/usr/bin/env python3
"""Tests for fresh manifest-bound dogfood provider-health evidence."""

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
SCRIPT = ROOT / "tools" / "dogfood_provider_health_evidence.py"
LANE = "1" * 40
SOURCE = "2" * 40
SPEC = "3" * 40
MANIFEST = "sha256:" + "4" * 64
DEPLOYMENT_RUN = "22"
HEALTH_RUN = "33"


class DogfoodProviderHealthEvidenceTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)

    def tearDown(self) -> None:
        self.temp.cleanup()

    def documents(self) -> dict[str, dict]:
        ids = {
            "backend": "sha256:" + "5" * 64,
            "mcp": "sha256:" + "6" * 64,
            "identity-ops": "sha256:" + "7" * 64,
            "keycloak": "sha256:" + "8" * 64,
        }
        references = {
            "backend": "ghcr.io/example/server@" + ids["backend"],
            "mcp": "ghcr.io/example/mcp@" + ids["mcp"],
            "identity-ops": "ghcr.io/example/identity@" + ids["identity-ops"],
            "keycloak": "ghcr.io/example/keycloak@" + ids["keycloak"],
        }
        manifest_images = {
            name: {
                "reference": references[name],
                "expectedImageId": ids[name],
                "observedImageId": ids[name],
                "sourceCommit": SOURCE,
                "matches": True,
            }
            for name in ids
        }
        return {
            "deployment": {
                "schemaVersion": 2,
                "supportSafe": True,
                "candidateCommit": LANE,
                "sourceCandidateCommit": SOURCE,
                "candidateManifestDigest": MANIFEST,
                "candidateImages": ids,
                "runUrl": f"https://github.com/example/weave/actions/runs/{DEPLOYMENT_RUN}",
            },
            "manifest": {
                "schemaVersion": "weave.test-stack-manifest.v2",
                "supportSafe": True,
                "containsSecretValues": False,
                "laneCandidateCommit": LANE,
                "sourceCandidateCommit": SOURCE,
                "specificationCommit": SPEC,
                "candidateManifestDigest": MANIFEST,
                "images": manifest_images,
                "runtime": {
                    "environment": "persistent-dogfood",
                    "scope": "persistent",
                    "composeProject": "weave-dogfood",
                    "generation": "fresh-222222222222",
                },
                "evidence": {
                    "deploymentRunUrl": f"https://github.com/example/weave/actions/runs/{DEPLOYMENT_RUN}"
                },
            },
            "runtime": {
                "schemaVersion": "weave.runtime-image-observation.v1",
                "sourceCandidateCommit": SOURCE,
                "images": copy.deepcopy(manifest_images),
                "supportSafe": True,
                "containsSecretValues": False,
            },
            "health": {
                "schemaVersion": "weave.provider-health-metrics-summary.v1",
                "supportSafe": True,
                "source": "loopback-actuator-cached-metrics",
                "providerProbeTriggered": False,
                "rawMetricPayloadIncluded": False,
                "overall": "available",
                "observedAtUtc": (datetime.now(timezone.utc) - timedelta(seconds=5)).isoformat().replace(
                    "+00:00", "Z"
                ),
                "cachedResultAgeSeconds": 5,
                "capabilities": {"chat": "available", "files": "available", "calendar": "available"},
            },
        }

    def run_tool(self, documents: dict[str, dict]) -> subprocess.CompletedProcess[str]:
        paths = {}
        for name, document in documents.items():
            path = self.root / f"{name}.json"
            path.write_text(json.dumps(document), encoding="utf-8")
            paths[name] = path
        return subprocess.run(
            [
                sys.executable,
                str(SCRIPT),
                "--candidate-commit",
                LANE,
                "--deployment-run-id",
                DEPLOYMENT_RUN,
                "--health-run-id",
                HEALTH_RUN,
                "--health-run-url",
                f"https://github.com/example/weave/actions/runs/{HEALTH_RUN}",
                "--deployment-evidence",
                str(paths["deployment"]),
                "--test-stack-manifest",
                str(paths["manifest"]),
                "--runtime-image-observation",
                str(paths["runtime"]),
                "--provider-health",
                str(paths["health"]),
                "--output",
                str(self.root / "result.json"),
            ],
            cwd=ROOT,
            text=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            check=False,
        )

    def test_available_current_health_is_bound_to_exact_candidate(self) -> None:
        completed = self.run_tool(self.documents())
        self.assertEqual(completed.returncode, 0, completed.stdout + completed.stderr)
        result = json.loads((self.root / "result.json").read_text(encoding="utf-8"))
        self.assertEqual(result["candidateManifestDigest"], MANIFEST)
        self.assertEqual(result["providerHealth"]["overall"], "available")
        self.assertEqual(
            set(result["providerHealth"]),
            {"overall", "observedAtUtc", "cachedResultAgeSeconds", "capabilities"},
        )
        self.assertEqual(set(result["images"]), {"server", "mcp-server", "identity-ops", "keycloak-runtime"})

    def test_stale_health_is_rejected_even_when_claimed_age_is_small(self) -> None:
        documents = self.documents()
        documents["health"]["observedAtUtc"] = (
            datetime.now(timezone.utc) - timedelta(minutes=10)
        ).isoformat().replace("+00:00", "Z")
        documents["health"]["cachedResultAgeSeconds"] = 1
        completed = self.run_tool(documents)
        self.assertEqual(completed.returncode, 2)
        self.assertIn("stale", completed.stderr)

    def test_manifest_image_mismatch_is_rejected(self) -> None:
        documents = self.documents()
        documents["manifest"]["images"]["backend"]["observedImageId"] = "sha256:" + "f" * 64
        completed = self.run_tool(documents)
        self.assertEqual(completed.returncode, 2)
        self.assertIn("backend image", completed.stderr)

    def test_deployment_or_health_run_mismatch_is_rejected(self) -> None:
        documents = self.documents()
        documents["deployment"]["runUrl"] = "https://github.com/example/weave/actions/runs/99"
        completed = self.run_tool(documents)
        self.assertEqual(completed.returncode, 2)
        self.assertIn("exact GitHub Actions run URL", completed.stderr)

        documents = copy.deepcopy(self.documents())
        documents["health"]["overall"] = "degraded"
        completed = self.run_tool(documents)
        self.assertEqual(completed.returncode, 2)
        self.assertIn("available", completed.stderr)


if __name__ == "__main__":
    unittest.main(verbosity=2)
