#!/usr/bin/env python3
"""Unit tests for support-safe persistent dogfood evidence."""

from __future__ import annotations

import importlib.util
import json
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SPEC = importlib.util.spec_from_file_location(
    "dogfood_deployment_evidence",
    ROOT / "tools" / "dogfood_deployment_evidence.py",
)
assert SPEC and SPEC.loader
module = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(module)


class DogfoodDeploymentEvidenceTest(unittest.TestCase):
    def images(self) -> dict[str, str]:
        return {
            name: "sha256:" + str(index + 1) * 64
            for index, name in enumerate(sorted(module.IMAGE_COMPONENTS))
        }

    def comparison(self, passed: bool = True) -> dict[str, object]:
        return {
            "schemaVersion": "weave.persistent-dogfood-comparison.v2",
            "status": "passed" if passed else "failed",
            "baselineSource": "pre-deploy",
            "preExistingRuntimeObserved": True,
            "twoNonDestructiveInstallsPreservedState": passed,
            "supportSafe": True,
        }

    def health(self, overall: str = "available") -> dict[str, object]:
        state = overall
        return {
            "schemaVersion": "weave.provider-health-metrics-summary.v1",
            "supportSafe": True,
            "providerProbeTriggered": False,
            "rawMetricPayloadIncluded": False,
            "overall": overall,
            "observedAtUtc": "2026-07-12T12:00:00Z",
            "cachedResultAgeSeconds": 30,
            "capabilities": {"chat": state, "files": state, "calendar": state},
        }

    def assemble(self, comparison=None, health=None):
        return module.assemble_deployment(
            candidate="1" * 40,
            backend_version="0.1.0",
            backend_build_number="42",
            run_url="https://example.invalid/runs/42",
            comparison=comparison or self.comparison(),
            provider_health=health or self.health(),
            source_candidate="2" * 40,
            candidate_images=self.images(),
        )

    def test_green_evidence_is_candidate_bound_and_support_safe(self):
        evidence = self.assemble()
        self.assertEqual(evidence["deployment"]["stackStatus"], "passed")
        self.assertTrue(evidence["deployment"]["persistentHumanUnchanged"])
        self.assertEqual(evidence["deployment"]["baselineSource"], "pre-deploy")
        self.assertTrue(evidence["deployment"]["preExistingRuntimeObserved"])
        self.assertEqual(evidence["providerHealth"]["capabilities"]["files"], "available")
        self.assertEqual(evidence["sourceCandidateCommit"], "2" * 40)
        self.assertEqual(evidence["backendBuild"]["commit"], "2" * 40)
        self.assertEqual(evidence["candidateImages"], self.images())
        self.assertEqual(evidence["blockers"], [])
        serialized = json.dumps(evidence).lower()
        self.assertNotIn("password", serialized)
        self.assertNotIn("token", serialized)

    def test_cold_start_baseline_is_green_but_does_not_claim_preexisting_runtime(self):
        comparison = self.comparison()
        comparison["baselineSource"] = "first-install"
        comparison["preExistingRuntimeObserved"] = False
        evidence = self.assemble(comparison=comparison)
        self.assertEqual(evidence["deployment"]["stackStatus"], "passed")
        self.assertEqual(evidence["deployment"]["baselineSource"], "first-install")
        self.assertFalse(evidence["deployment"]["preExistingRuntimeObserved"])

    def test_degraded_provider_is_explicitly_blocked(self):
        evidence = self.assemble(health=self.health("degraded"))
        self.assertEqual(evidence["deployment"]["stackStatus"], "blocked")
        self.assertEqual(evidence["blockers"][0]["code"], "provider-health-not-available")

    def test_changed_persistent_state_is_explicitly_blocked(self):
        evidence = self.assemble(comparison=self.comparison(False))
        self.assertFalse(evidence["deployment"]["persistentHumanUnchanged"])
        self.assertEqual(evidence["blockers"][0]["code"], "persistent-dogfood-state-changed")

    def test_idempotence_reports_no_changes_without_rejecting_safe_failure_evidence(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "deployment-idempotence.json"
            path.write_text('{"supportSafe":true,"noChanges":false}', encoding="utf-8")
            self.assertFalse(module.idempotence_passed(path))

    def test_failed_idempotence_is_explicitly_failed(self):
        evidence = module.assemble_deployment(
            candidate="1" * 40,
            backend_version="0.1.0",
            backend_build_number="42",
            run_url="https://example.invalid/runs/42",
            comparison=self.comparison(),
            provider_health=self.health(),
            source_candidate="2" * 40,
            candidate_images=self.images(),
            idempotency_passed=False,
        )
        self.assertEqual(evidence["deployment"]["idempotencyStatus"], "failed")
        self.assertEqual(evidence["deployment"]["stackStatus"], "blocked")
        self.assertEqual(evidence["blockers"][0]["code"], "persistent-dogfood-not-idempotent")

    def test_adoption_gate_requires_exact_candidate_backup_and_isolated_restore(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "compose-adoption-gate.json"
            path.write_text(json.dumps({
                "schemaVersion": "weave.dogfood.compose-adoption-gate.v1",
                "candidateCommit": "1" * 40,
                "status": "passed",
                "adoptionRequired": True,
                "backupVerified": True,
                "isolatedRestoreVerified": True,
                "receiptRef": "artifact:compose-adoption-receipt.json",
                "supportSafe": True,
                "containsSecretValues": False,
            }), encoding="utf-8")
            self.assertEqual(module.adoption_gate(path, "1" * 40), (True, True))
            value = json.loads(path.read_text(encoding="utf-8"))
            value["isolatedRestoreVerified"] = False
            path.write_text(json.dumps(value), encoding="utf-8")
            with self.assertRaises(module.EvidenceError):
                module.adoption_gate(path, "1" * 40)

    def test_fresh_install_adoption_gate_requires_absent_database_volume(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "compose-adoption-gate.json"
            value = {
                "schemaVersion": "weave.dogfood.compose-adoption-gate.v1",
                "candidateCommit": "1" * 40,
                "status": "passed",
                "adoptionRequired": False,
                "persistentDatabaseVolumePresent": False,
                "supportSafe": True,
                "containsSecretValues": False,
            }
            path.write_text(json.dumps(value), encoding="utf-8")
            self.assertEqual(module.adoption_gate(path, "1" * 40), (True, False))
            value["persistentDatabaseVolumePresent"] = True
            path.write_text(json.dumps(value), encoding="utf-8")
            with self.assertRaises(module.EvidenceError):
                module.adoption_gate(path, "1" * 40)

    def test_candidate_source_mapping_requires_exact_lane_tree_and_closed_images(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "candidate-source-mapping.json"
            value = {
                "schemaVersion": "weave.candidate-source-mapping.v1",
                "status": "passed",
                "laneCandidateCommit": "1" * 40,
                "sourceCandidateCommit": "2" * 40,
                "protectedDevHead": "2" * 40,
                "sourceTree": "3" * 40,
                "laneTree": "3" * 40,
                "images": self.images(),
                "supportSafe": True,
                "containsSecretValues": False,
            }
            path.write_text(json.dumps(value), encoding="utf-8")
            self.assertEqual(
                module.candidate_source_mapping(path, "1" * 40),
                ("2" * 40, self.images()),
            )
            value["laneTree"] = "4" * 40
            path.write_text(json.dumps(value), encoding="utf-8")
            with self.assertRaises(module.EvidenceError):
                module.candidate_source_mapping(path, "1" * 40)
            value["laneTree"] = value["sourceTree"]
            value["images"].pop("mcp")
            path.write_text(json.dumps(value), encoding="utf-8")
            with self.assertRaises(module.EvidenceError):
                module.candidate_source_mapping(path, "1" * 40)

    def test_metrics_collection_rejects_non_loopback_or_credentialed_urls(self):
        for url in (
            "https://127.0.0.1:48084/actuator/metrics",
            "http://api.weave.test/actuator/metrics",
            "http://192.0.2.10:48084/actuator/metrics",
            "http://user:secret@127.0.0.1:48084/actuator/metrics",
            "http://127.0.0.1:48084/actuator/health",
        ):
            with self.subTest(url=url):
                with self.assertRaises(module.EvidenceError):
                    module.validate_loopback_metrics_url(url)
        self.assertEqual(
            module.validate_loopback_metrics_url(
                "http://127.0.0.1:48084/actuator/metrics/"
            ),
            "http://127.0.0.1:48084/actuator/metrics",
        )

    def test_assembly_rejects_unsafe_run_urls_and_uncached_health(self):
        for run_url in (
            "http://example.invalid/runs/42",
            "https://user:secret@example.invalid/runs/42",
            "https://example.invalid/runs/42?token=secret",
        ):
            with self.subTest(run_url=run_url), self.assertRaises(module.EvidenceError):
                module.assemble_deployment(
                    candidate="1" * 40,
                    backend_version="0.1.0",
                    backend_build_number="42",
                    run_url=run_url,
                    comparison=self.comparison(),
                    provider_health=self.health(),
                    source_candidate="2" * 40,
                    candidate_images=self.images(),
                )

        health = self.health()
        health["providerProbeTriggered"] = True
        with self.assertRaises(module.EvidenceError):
            self.assemble(health=health)


if __name__ == "__main__":
    unittest.main()
