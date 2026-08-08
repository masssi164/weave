#!/usr/bin/env python3

from __future__ import annotations

import importlib.util
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SPEC = importlib.util.spec_from_file_location("test_stack_manifest", ROOT / "tools/test_stack_manifest.py")
assert SPEC and SPEC.loader
module = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(module)


class TestStackManifestTest(unittest.TestCase):
    lane = "1" * 40
    source = "2" * 40
    spec = "3" * 40

    def documents(self):
        image_ids = {name: "sha256:" + str(index + 4) * 64 for index, name in enumerate(sorted(module.COMPONENTS))}
        candidate_images = []
        for runtime_name, manifest_name in sorted(module.COMPONENTS.items()):
            candidate_images.append({
                "component": manifest_name,
                "reference": f"ghcr.io/example/{manifest_name}@sha256:" + str(len(candidate_images) + 4) * 64,
                "sbomDigest": "sha256:" + "a" * 64,
                "provenanceDigest": "sha256:" + "b" * 64,
                **({"buildEvidenceDigest": "sha256:" + "c" * 64} if manifest_name == "keycloak-runtime" else {}),
            })
        candidate = {
            "schemaVersion": "weave.release.candidate-manifest.v3",
            "supportSafe": True,
            "commit": self.source,
            "specificationCommit": self.spec,
            "specDigest": "sha256:" + "d" * 64,
            "buildEvidenceRef": "https://example.invalid/build/1",
            "realmArtifacts": {
                "baselineDigest": "sha256:" + "8" * 64,
                "migrationBundleDigest": "sha256:" + "9" * 64,
                "containsSecrets": False,
            },
            "images": sorted(candidate_images, key=lambda item: item["component"]),
        }
        import hashlib, json
        digest = "sha256:" + hashlib.sha256(json.dumps(candidate, ensure_ascii=False, separators=(",", ":"), sort_keys=True).encode()).hexdigest()
        by_component = {item["component"]: item for item in candidate_images}
        return (
            {
                "schemaVersion": "weave.candidate-source-mapping.v1", "status": "passed",
                "laneCandidateCommit": self.lane, "sourceCandidateCommit": self.source,
                "sourceTree": "e" * 40, "laneTree": "e" * 40, "images": image_ids,
                "supportSafe": True, "containsSecretValues": False,
            },
            candidate,
            {
                "schemaVersion": "weave.runtime-image-observation.v1", "sourceCandidateCommit": self.source,
                "images": {name: {"reference": by_component[component]["reference"], "expectedImageId": image_ids[name], "observedImageId": image_ids[name], "sourceCommit": self.source, "matches": True} for name, component in module.COMPONENTS.items()},
                "supportSafe": True, "containsSecretValues": False,
            },
            {
                "schemaVersion": "weave.fresh-start-cut-report.v1", "laneCandidateCommit": self.lane,
                "sourceCandidateCommit": self.source, "candidateManifestDigest": digest, "status": "passed",
                "schemaConverged": True, "realmArtifactsVerified": True, "imagesVerified": True,
                "newInvitationPending": True,
                "legacyStateMigrated": False, "adoptionAuthorized": False, "supportSafe": True,
                "containsSecretValues": False,
            },
            {
                "schemaVersion": "weave.persistent-test-idempotence.v3", "runtimeProfile": "dogfood",
                "deploymentContext": "persistent-dogfood",
                "noChanges": True, "composeModelStable": True, "realmArtifactsUnchanged": True,
                "supportSafe": True, "containsSecretValues": False,
            },
            {
                "schemaVersion": "weave.provider-health-metrics-summary.v1", "overall": "available",
                "supportSafe": True, "providerProbeTriggered": False, "rawMetricPayloadIncluded": False,
            },
        )

    def assemble(self, documents):
        mapping, candidate, runtime, cut, idempotence, health = documents
        return module.assemble(mapping=mapping, candidate=candidate, runtime=runtime, cut=cut,
            idempotence=idempotence, health=health, compose_project="weave-dogfood", generation="fresh-v2",
            live_run_url="https://example.invalid/runs/1", deployment_run_url="https://example.invalid/runs/2")

    def test_exact_source_lane_runtime_manifest_is_accepted(self):
        result = self.assemble(self.documents())
        self.assertEqual(result["laneCandidateCommit"], self.lane)
        self.assertEqual(result["sourceCandidateCommit"], self.source)
        self.assertEqual(
            result["realmArtifacts"],
            self.documents()[1]["realmArtifacts"],
        )
        self.assertEqual(result["deployment"]["freshStartStatus"], "passed")

    def test_runtime_image_drift_is_rejected(self):
        documents = self.documents()
        documents[2]["images"]["backend"]["observedImageId"] = "sha256:" + "f" * 64
        with self.assertRaises(module.ManifestError):
            self.assemble(documents)

    def test_adoption_claim_is_rejected(self):
        documents = self.documents()
        documents[3]["adoptionAuthorized"] = True
        with self.assertRaises(module.ManifestError):
            self.assemble(documents)

    def test_unverified_realm_artifacts_are_rejected(self):
        documents = self.documents()
        documents[3]["realmArtifactsVerified"] = False
        with self.assertRaises(module.ManifestError):
            self.assemble(documents)

    def test_secret_bearing_or_malformed_realm_artifacts_are_rejected(self):
        for mutation in (
            lambda artifacts: artifacts.__setitem__("containsSecrets", True),
            lambda artifacts: artifacts.__setitem__(
                "migrationBundleDigest", "sha256:not-a-digest"
            ),
            lambda artifacts: artifacts.__setitem__(
                "unreviewedDigest", "sha256:" + "f" * 64
            ),
        ):
            with self.subTest(mutation=mutation):
                documents = self.documents()
                mutation(documents[1]["realmArtifacts"])
                with self.assertRaises(module.ManifestError):
                    self.assemble(documents)


if __name__ == "__main__":
    unittest.main(verbosity=2)
