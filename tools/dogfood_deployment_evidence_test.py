#!/usr/bin/env python3
"""Unit tests for manifest-bound Fresh Start and routine dogfood evidence."""

from __future__ import annotations

import importlib.util
import json
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch


ROOT = Path(__file__).resolve().parents[1]
SPEC = importlib.util.spec_from_file_location("dogfood_deployment_evidence", ROOT / "tools" / "dogfood_deployment_evidence.py")
assert SPEC and SPEC.loader
module = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(module)


class DogfoodDeploymentEvidenceTest(unittest.TestCase):
    lane = "1" * 40
    source = "2" * 40
    manifest = "sha256:" + "3" * 64

    def images(self) -> dict[str, str]:
        return {name: "sha256:" + str(index + 4) * 64 for index, name in enumerate(sorted(module.IMAGE_COMPONENTS))}

    def health(self, overall: str = "available") -> dict[str, object]:
        return {
            "schemaVersion": "weave.provider-health-metrics-summary.v1",
            "supportSafe": True,
            "providerProbeTriggered": False,
            "rawMetricPayloadIncluded": False,
            "overall": overall,
            "observedAtUtc": "2026-08-01T12:00:00Z",
            "cachedResultAgeSeconds": 30,
            "capabilities": {"chat": overall, "files": overall, "calendar": overall},
        }

    def cut(self) -> dict[str, object]:
        return {
            "schemaVersion": "weave.fresh-start-cut-report.v1",
            "laneCandidateCommit": self.lane,
            "sourceCandidateCommit": self.source,
            "candidateManifestDigest": self.manifest,
            "status": "passed",
            "schemaConverged": True,
            "identitySecondPlanEmpty": True,
            "imagesVerified": True,
            "newInvitationPending": True,
            "legacyStateMigrated": False,
            "adoptionAuthorized": False,
            "supportSafe": True,
            "containsSecretValues": False,
        }

    def comparison(self) -> dict[str, object]:
        return {
            "schemaVersion": "weave.persistent-dogfood-comparison.v2",
            "status": "passed",
            "baselineSource": "pre-deploy",
            "preExistingRuntimeObserved": True,
            "twoNonDestructiveInstallsPreservedState": True,
            "supportSafe": True,
        }

    def assemble(self, *, cut=None, comparison=None, health=None, idempotent=True):
        return module.assemble_deployment(
            candidate=self.lane,
            source_candidate=self.source,
            candidate_manifest_digest=self.manifest,
            backend_version="0.1.0",
            backend_build_number="42",
            run_url="https://example.invalid/runs/42",
            provider_health=health or self.health(),
            candidate_images=self.images(),
            idempotency_passed=idempotent,
            fresh_start_cut=cut,
            persistent_comparison=comparison,
        )

    def test_fresh_start_is_green_but_activation_remains_explicit(self):
        evidence = self.assemble(cut=self.cut())
        self.assertEqual(evidence["deployment"]["stackStatus"], "passed")
        self.assertEqual(evidence["deployment"]["baselineSource"], "fresh-start")
        self.assertEqual(evidence["deployment"]["ownerActivationStatus"], "pending")
        self.assertFalse(evidence["deployment"]["persistentHumanUnchanged"])
        self.assertFalse(evidence["deployment"]["legacyStateMigrated"])
        self.assertFalse(evidence["deployment"]["adoptionAuthorized"])
        self.assertEqual(evidence["backendBuild"]["commit"], self.source)
        self.assertEqual(evidence["blockers"][0]["code"], "fresh-owner-activation-pending")

    def test_routine_deployment_proves_new_generation_continuity(self):
        evidence = self.assemble(comparison=self.comparison())
        self.assertEqual(evidence["deployment"]["baselineSource"], "pre-deploy")
        self.assertTrue(evidence["deployment"]["persistentHumanUnchanged"])
        self.assertEqual(evidence["deployment"]["ownerActivationStatus"], "passed")
        self.assertEqual(evidence["blockers"], [])

    def test_adoption_or_legacy_claim_fails_closed(self):
        cut = self.cut()
        cut["adoptionAuthorized"] = True
        with self.assertRaises(module.EvidenceError):
            self.assemble(cut=cut)

    def test_provider_or_idempotence_failure_blocks(self):
        degraded = self.assemble(cut=self.cut(), health=self.health("degraded"))
        self.assertEqual(degraded["deployment"]["stackStatus"], "blocked")
        drifted = self.assemble(cut=self.cut(), idempotent=False)
        self.assertEqual(drifted["deployment"]["idempotencyStatus"], "failed")

    def test_exactly_one_baseline_is_required(self):
        with self.assertRaises(module.EvidenceError):
            self.assemble()
        with self.assertRaises(module.EvidenceError):
            self.assemble(cut=self.cut(), comparison=self.comparison())

    def test_candidate_mapping_requires_tree_identity_and_all_images(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "candidate-source-mapping.json"
            value = {
                "schemaVersion": "weave.candidate-source-mapping.v1",
                "status": "passed",
                "laneCandidateCommit": self.lane,
                "sourceCandidateCommit": self.source,
                "sourceTree": "a" * 40,
                "laneTree": "a" * 40,
                "images": self.images(),
                "supportSafe": True,
                "containsSecretValues": False,
            }
            path.write_text(json.dumps(value), encoding="utf-8")
            self.assertEqual(module.candidate_source_mapping(path, self.lane), (self.source, self.images()))
            value["laneTree"] = "b" * 40
            path.write_text(json.dumps(value), encoding="utf-8")
            with self.assertRaises(module.EvidenceError):
                module.candidate_source_mapping(path, self.lane)

    def test_metrics_endpoint_must_be_loopback_and_uncredentialed(self):
        for value in ("https://127.0.0.1:48084/actuator/metrics", "http://api.weave.test/actuator/metrics", "http://user:secret@127.0.0.1:48084/actuator/metrics"):
            with self.subTest(value=value), self.assertRaises(module.EvidenceError):
                module.validate_loopback_metrics_url(value)

    def test_collector_reads_chat_from_the_same_cached_metrics(self):
        with patch.object(module, "metric_value", return_value=2.0) as metric:
            result = module.collect_provider_health("http://127.0.0.1:48084/actuator/metrics")
        queried = {call.args[2] for call in metric.call_args_list}
        self.assertEqual(queried, {"chat", "files", "calendar"})
        self.assertEqual(result["capabilities"]["chat"], "available")


if __name__ == "__main__":
    unittest.main(verbosity=2)
