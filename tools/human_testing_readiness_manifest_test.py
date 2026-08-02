#!/usr/bin/env python3
"""Fixture tests for the human-testing readiness manifest guard."""

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
SCRIPT = ROOT / "tools" / "human_testing_readiness_manifest.py"
FIXTURE = ROOT / "tools" / "fixtures" / "human_testing_readiness" / "green.json"


class HumanTestingReadinessManifestTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temp = tempfile.TemporaryDirectory()
        self.manifest = Path(self.temp.name) / "manifest.json"
        self.data = json.loads(FIXTURE.read_text(encoding="utf-8"))
        self.data["providerHealth"]["observedAtUtc"] = (
            datetime.now(timezone.utc) - timedelta(seconds=60)
        ).isoformat().replace("+00:00", "Z")

    def tearDown(self) -> None:
        self.temp.cleanup()

    def run_validate(
        self,
        data: dict,
        *,
        require_ready: bool = False,
        provider_age_reference: str = "now",
    ) -> subprocess.CompletedProcess[str]:
        self.manifest.write_text(json.dumps(data), encoding="utf-8")
        command = [
            sys.executable,
            str(SCRIPT),
            "validate",
            "--manifest",
            str(self.manifest),
            "--provider-age-reference",
            provider_age_reference,
        ]
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
        self.assertIn("physicalAcceptance.protocol.build", completed.stderr)

    def test_builds_must_bind_source_not_lane_candidate(self) -> None:
        data = copy.deepcopy(self.data)
        data["builds"]["backend"]["commit"] = data["candidateCommit"]
        data["state"] = "failed"
        data["humanTestingReady"] = False
        completed = self.run_validate(data)
        self.assertEqual(completed.returncode, 0)
        self.assertIn("source candidate", completed.stdout)

    def test_manifest_images_and_proof_origins_are_mandatory(self) -> None:
        data = copy.deepcopy(self.data)
        data["images"]["server"] = "ghcr.io/example/weave-server:mutable"
        completed = self.run_validate(data)
        self.assertEqual(completed.returncode, 2)
        self.assertIn("canonical schema validation failed", completed.stderr)

        data = copy.deepcopy(self.data)
        data["surfaces"]["settings"]["proofKinds"] = []
        completed = self.run_validate(data)
        self.assertEqual(completed.returncode, 2)
        self.assertIn("proofKinds", completed.stderr)

    def test_physical_protocol_must_bind_candidate_and_all_steps(self) -> None:
        data = copy.deepcopy(self.data)
        data["physicalAcceptance"]["protocol"]["candidateManifestDigest"] = "sha256:" + "f" * 64
        completed = self.run_validate(data)
        self.assertEqual(completed.returncode, 2)
        self.assertIn("candidateManifestDigest", completed.stderr)

        data = copy.deepcopy(self.data)
        data["physicalAcceptance"]["protocol"]["steps"].pop("callsUi")
        completed = self.run_validate(data)
        self.assertEqual(completed.returncode, 2)
        self.assertIn("canonical schema validation failed", completed.stderr)

    def test_provider_health_age_is_calculated_from_observation_time(self) -> None:
        data = copy.deepcopy(self.data)
        data["providerHealth"]["observedAtUtc"] = "2026-07-12T11:59:00Z"
        data["providerHealth"]["cachedResultAgeSeconds"] = 60
        data["state"] = "blocked"
        data["humanTestingReady"] = False
        completed = self.run_validate(data, require_ready=True)
        self.assertEqual(completed.returncode, 1, completed.stdout + completed.stderr)
        self.assertIn("provider health cache is stale", completed.stdout)

    def test_immutable_artifact_uses_generation_time_for_historical_freshness(self) -> None:
        data = copy.deepcopy(self.data)
        data["generatedAtUtc"] = "2026-07-12T12:00:00Z"
        data["providerHealth"]["observedAtUtc"] = "2026-07-12T11:59:00Z"
        data["providerHealth"]["cachedResultAgeSeconds"] = 60
        completed = self.run_validate(
            data,
            require_ready=True,
            provider_age_reference="generated-at",
        )
        self.assertEqual(completed.returncode, 0, completed.stdout + completed.stderr)

    def test_historical_artifact_rejects_health_stale_at_generation(self) -> None:
        data = copy.deepcopy(self.data)
        data["generatedAtUtc"] = "2026-07-12T12:04:00Z"
        data["providerHealth"]["observedAtUtc"] = "2026-07-12T11:59:00Z"
        data["providerHealth"]["cachedResultAgeSeconds"] = 60
        data["state"] = "blocked"
        data["humanTestingReady"] = False
        completed = self.run_validate(
            data,
            require_ready=True,
            provider_age_reference="generated-at",
        )
        self.assertEqual(completed.returncode, 1, completed.stdout + completed.stderr)
        self.assertIn("provider health cache is stale", completed.stdout)

    def test_declared_provider_cache_age_cannot_hide_staleness(self) -> None:
        data = copy.deepcopy(self.data)
        data["providerHealth"]["cachedResultAgeSeconds"] = 181
        data["state"] = "blocked"
        data["humanTestingReady"] = False
        completed = self.run_validate(data, require_ready=True)
        self.assertEqual(completed.returncode, 1, completed.stdout + completed.stderr)
        self.assertIn("declared cache age is stale", completed.stdout)

    def test_canonical_schema_rejects_unknown_fields_at_every_closed_boundary(self) -> None:
        mutations = (
            lambda data: data.__setitem__("unreviewedExtension", True),
            lambda data: data["collaboration"].__setitem__("unreviewedExtension", True),
            lambda data: data["physicalAcceptance"]["protocol"].__setitem__(
                "unreviewedExtension", True
            ),
            lambda data: data["physicalAcceptance"]["protocol"]["steps"][
                "invitationMail"
            ].__setitem__("unreviewedExtension", True),
        )
        for mutate in mutations:
            with self.subTest(mutation=mutate):
                data = copy.deepcopy(self.data)
                mutate(data)
                completed = self.run_validate(data)
                self.assertEqual(completed.returncode, 2, completed.stdout + completed.stderr)
                self.assertIn("canonical schema validation failed", completed.stderr)


if __name__ == "__main__":
    unittest.main(verbosity=2)
