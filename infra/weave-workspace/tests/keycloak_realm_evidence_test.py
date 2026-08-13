#!/usr/bin/env python3
from __future__ import annotations

import importlib.util
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[3]
MODULE_PATH = ROOT / "infra/weave-workspace/scripts/keycloak_realm_evidence.py"
SPEC = importlib.util.spec_from_file_location("keycloak_realm_evidence", MODULE_PATH)
assert SPEC is not None and SPEC.loader is not None
module = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(module)


class KeycloakRealmEvidenceTest(unittest.TestCase):
    def setUp(self) -> None:
        self.lane = "a" * 40
        self.source = "b" * 40
        semantic = "sha256:" + "1" * 64
        migration = "sha256:" + "2" * 64
        self.identity = {
            "semanticRealmSourceDigest": semantic,
            "migrationDefinitionDigest": migration,
            "overlayDigest": "sha256:" + "3" * 64,
            "renderedRealmDigest": "sha256:" + "4" * 64,
        }
        self.candidate = {
            "schemaVersion": "weave.release.candidate-manifest.v4",
            "commit": self.source,
            "realmDefinition": {
                "semanticRealmSourceDigest": semantic,
                "migrationDefinitionDigest": migration,
                "containsSecrets": False,
            },
            "supportSafe": True,
        }
        self.render = {
            "schemaVersion": "weave.compose-render.v3",
            "realmIdentity": self.identity,
            "deploymentArtifacts": {
                "renderedRealmPath": "keycloak/import/weave-realm.json",
                "migrationBundlePath": "keycloak/migrations/fresh-start-v1.json",
                "environmentRenderEvidencePath": "keycloak/realm-render-evidence.json",
                "containsSecretValues": False,
            },
            "containsSecretValues": False,
        }
        self.render_evidence = {
            "schemaVersion": "weave.keycloak-environment-render-evidence/v1",
            "candidateCommit": self.lane,
            "realmIdentity": self.identity,
            "semanticReadbackDigest": None,
            "semanticReadbackVerified": False,
            "supportSafe": True,
            "containsSecretValues": False,
        }
        self.receipt = {
            "schemaVersion": module.RECEIPT_SCHEMA,
            "status": "complete",
            "operationId": module.OPERATION_ID,
            "baselineArtifactDigest": self.identity["renderedRealmDigest"],
            "targetBaselineRevision": self.identity["semanticRealmSourceDigest"],
            "firstRunOperations": [],
            "firstRunMutationCount": 0,
            "semanticReadbackVerified": True,
            "secondRunPlanEmpty": True,
            "bootstrapAuthorityDeleted": True,
            "bootstrapAuthorityNegativeReadbackVerified": True,
            "supportSafe": True,
            "containsSecretValues": False,
        }

    def finalize(self, lane: str | None = None) -> dict[str, object]:
        return module.finalize(
            lane or self.lane,
            self.candidate,
            self.render,
            self.render,
            self.render_evidence,
            self.receipt,
        )

    def test_accepts_distinct_lane_and_manifest_source_when_each_is_bound(self) -> None:
        evidence = self.finalize()
        self.assertTrue(evidence["candidateRealmDefinitionMatched"])
        self.assertTrue(evidence["semanticReadbackVerified"])

    def test_rejects_render_evidence_bound_to_manifest_source_instead_of_lane(self) -> None:
        self.render_evidence["candidateCommit"] = self.source
        with self.assertRaisesRegex(module.EvidenceError, "stale or overclaims"):
            self.finalize()

    def test_rejects_unvalidated_lane_candidate(self) -> None:
        with self.assertRaisesRegex(module.EvidenceError, "lane candidate"):
            self.finalize("not-a-commit")


if __name__ == "__main__":
    unittest.main()
