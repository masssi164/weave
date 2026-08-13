#!/usr/bin/env python3
"""Tests for support-safe live plus iPhone Simulator evidence composition."""

from __future__ import annotations

import copy
import hashlib
import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SCRIPT = ROOT / "tools" / "human_testing_automated_evidence.py"
LANE = "1" * 40
SOURCE = "2" * 40
SPEC = "3" * 40
IMAGE_DIGESTS = {
    "server": "4" * 64,
    "mcp-server": "5" * 64,
    "keycloak-runtime": "7" * 64,
}
REALM_DEFINITION = {
    "semanticRealmSourceDigest": "sha256:" + "6" * 64,
    "migrationDefinitionDigest": "sha256:" + "7" * 64,
    "containsSecrets": False,
}
REALM_EVIDENCE = {
    **REALM_DEFINITION,
    "overlayDigest": "sha256:" + "8" * 64,
    "renderedRealmDigest": "sha256:" + "9" * 64,
    "semanticReadbackDigest": "sha256:" + "a" * 64,
    "candidateRealmDefinitionMatched": True,
    "environmentRealmRenderStable": True,
    "semanticReadbackVerified": True,
}
HASHES = {
    "author": "sha256:" + "a" * 64,
    "collaborator": "sha256:" + "b" * 64,
    "outsider": "sha256:" + "c" * 64,
}


def pass_proof(index: int) -> dict[str, object]:
    return {
        "pass": index,
        "freshAuthorizationCodePkce": True,
        "chatPassed": True,
        "filesPassed": True,
        "calendarPassed": True,
        "homePassed": True,
        "profilePassed": True,
        "outsiderDenied": True,
        "canonicalJpaVerified": True,
        "nativePersistenceVerified": True,
        "idempotencyVerified": True,
        "southboundProviderDependencyObserved": False,
        "restartContinuityVerified": index == 2,
        "cleanupComplete": True,
        "nativeRevisionHash": "sha256:" + str(index) * 64,
    }


class HumanTestingAutomatedEvidenceTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        self.manifest = {
            "schemaVersion": "weave.release.candidate-manifest.v4",
            "supportSafe": True,
            "commit": SOURCE,
            "specificationCommit": SPEC,
            "specDigest": "sha256:" + "8" * 64,
            "buildEvidenceRef": "https://github.com/example/weave/actions/runs/9",
            "realmDefinition": copy.deepcopy(REALM_DEFINITION),
            "images": [
                {
                    "component": component,
                    "reference": f"ghcr.io/example/weave-{component}@sha256:{digest}",
                    "sbomDigest": "sha256:" + "9" * 64,
                    "provenanceDigest": "sha256:" + "a" * 64,
                }
                for component, digest in sorted(IMAGE_DIGESTS.items())
            ],
        }
        manifest_digest = "sha256:" + hashlib.sha256(
            json.dumps(
                self.manifest, ensure_ascii=False, separators=(",", ":"), sort_keys=True
            ).encode("utf-8")
        ).hexdigest()
        self.product = {
            "schemaVersion": "weave.test-app-product-flow/v2",
            "supportSafe": True,
            "candidateCommit": LANE,
            "sourceCandidateCommit": SOURCE,
            "specificationCommit": SPEC,
            "candidateManifestDigest": manifest_digest,
            "composeProject": "weave-e2e-0123456789abcdef",
            "activation": "keycloak-required-actions-real-chromium",
            "humanOAuth": "authorization_code_pkce_s256",
            "workloadOAuth": "client_credentials_private_key_jwt",
            "postgresRestartObserved": True,
            "runtimeStateRestartObserved": True,
            "runtimeStateFixtureRestored": True,
            "sameJpaCellAfterRestart": True,
            "sameMcpCellAfterRestart": True,
            "revocationDenied": True,
            "regrantRestored": True,
            "sameHumanSubjectAfterRegrant": True,
            "samePersonRefAfterRegrant": True,
            "credentialsIncluded": False,
            "actionLinksIncluded": False,
            "collaboration": {
                "repeatCount": 2,
                "selectedProviders": {"chat": "weave-native", "files": "weave-native", "calendar": "weave-native"},
                "northboundFacades": {"matrix": True, "webdav": True, "caldav": True},
                "southboundProviderDependencyObserved": False,
                "identityRefHashes": HASHES,
                "passes": [pass_proof(1), pass_proof(2)],
            },
        }
        self.runtime = {
            "schemaVersion": "weave.test-app-runtime-images/v2",
            "supportSafe": True,
            "candidateCommit": LANE,
            "sourceCandidateCommit": SOURCE,
            "specificationCommit": SPEC,
            "specDigest": self.manifest["specDigest"],
            "candidateManifestDigest": manifest_digest,
            "composeProject": "weave-e2e-0123456789abcdef",
            "manifestBound": True,
            "realmEvidence": copy.deepcopy(REALM_EVIDENCE),
            "realmEvidenceVerified": True,
            "images": [
                {
                    "component": component,
                    "immutableReference": (
                        f"ghcr.io/example/weave-{component}@sha256:{digest}"
                    ),
                    "localImageId": "sha256:" + digest,
                    "observedImageId": "sha256:" + digest,
                    "lifecycle": "running-container",
                    "matchesCandidate": True,
                }
                for component, digest in sorted(IMAGE_DIGESTS.items())
            ],
            "credentialsIncluded": False,
            "containsSecretValues": False,
        }
        self.teardown = {
            "schemaVersion": "weave.compose-isolated-teardown.v1",
            "supportSafe": True,
            "candidateCommit": LANE,
            "candidateManifestDigest": manifest_digest,
            "namespace": "weave-e2e-0123456789abcdef",
            "composeProject": "weave-e2e-0123456789abcdef",
            "dryRun": False,
            "ownershipLabelsVerified": True,
            "containsSecretValues": False,
            "removedVolumeNames": [
                f"weave_e2e_0123456789abcdef_{suffix}"
                for suffix in sorted(
                    (
                        "caddy_data",
                        "caddy_config",
                        "db_data",
                        "keycloak_data",
                        "mailpit_data",
                        "native_files_data",
                        "runtime_state",
                    )
                )
            ],
            "networkRemoved": True,
            "removedNetworkName": "weave-e2e-0123456789abcdef_network",
            "composeDownStatus": "passed",
            "fallbackAttempted": False,
            "observedContainerCount": 18,
            "fallbackObservedContainerCount": 0,
            "removedContainerCount": 0,
            "remainingContainerCount": 0,
            "remainingVolumeCount": 0,
            "remainingNetworkCount": 0,
            "remainingOwnedResources": 0,
        }

    def tearDown(self) -> None:
        self.temp.cleanup()

    def write(self, name: str, value: dict) -> Path:
        path = self.root / name
        path.write_text(json.dumps(value), encoding="utf-8")
        return path

    def live(
        self,
        product: dict | None = None,
        runtime: dict | None = None,
    ) -> subprocess.CompletedProcess[str]:
        return subprocess.run(
            [
                sys.executable,
                str(SCRIPT),
                "live",
                "--product-evidence",
                str(self.write("product.json", product or self.product)),
                "--teardown-evidence",
                str(self.write("teardown.json", self.teardown)),
                "--candidate-manifest",
                str(self.write("candidate-manifest.json", self.manifest)),
                "--runtime-image-evidence",
                str(self.write("runtime-image-evidence.json", runtime or self.runtime)),
                "--run-url",
                "https://github.com/example/weave/actions/runs/10",
                "--output",
                str(self.root / "live.json"),
            ],
            cwd=ROOT,
            text=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            check=False,
        )

    def simulator(self) -> dict[str, object]:
        return {
            "schemaVersion": "weave.ios-simulator-current-surfaces.v1",
            "supportSafe": True,
            "candidateCommit": LANE,
            "sourceCandidateCommit": SOURCE,
            "specCorpusCommit": SPEC,
            "evidenceMode": "fixture-ui",
            "freshSimulator": True,
            "cleanupStatus": "passed",
            "remainingOwnedSimulators": 0,
            "surfaces": {name: "passed" for name in ("home", "chat", "files", "calendar", "settings", "profile")},
            "evidenceRef": "artifact:ios-simulator-current-surfaces",
        }

    def test_combines_real_live_claims_with_explicit_fixture_ui_claims(self) -> None:
        live = self.live()
        self.assertEqual(live.returncode, 0, live.stdout + live.stderr)
        combined = subprocess.run(
            [
                sys.executable,
                str(SCRIPT),
                "combine",
                "--live-evidence",
                str(self.root / "live.json"),
                "--simulator-evidence",
                str(self.write("simulator.json", self.simulator())),
                "--output",
                str(self.root / "combined.json"),
            ],
            cwd=ROOT,
            text=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            check=False,
        )
        self.assertEqual(combined.returncode, 0, combined.stdout + combined.stderr)
        result = json.loads((self.root / "combined.json").read_text(encoding="utf-8"))
        self.assertEqual(result["surfaces"]["settings"]["status"], "passed")
        self.assertEqual(result["surfaces"]["settings"]["proofKinds"], ["fixture-ui"])
        self.assertEqual(
            result["surfaces"]["chat"]["proofKinds"],
            ["live-provider-backed", "fixture-ui"],
        )
        self.assertEqual(result["evidenceModes"], ["live-provider-backed", "fixture-ui"])
        self.assertEqual(result["collaboration"]["repeatCount"], 2)
        self.assertEqual(result["collaboration"]["scenarioResults"]["settingsProfile"], "passed")
        self.assertEqual(result["candidateManifestDigest"], self.product["candidateManifestDigest"])
        self.assertEqual(set(result["images"]), set(IMAGE_DIGESTS))
        self.assertEqual(result["realmEvidence"], REALM_EVIDENCE)

    def test_rejects_a_failed_live_provider_fact(self) -> None:
        product = copy.deepcopy(self.product)
        product["collaboration"]["passes"][1]["nativePersistenceVerified"] = False
        completed = self.live(product)
        self.assertEqual(completed.returncode, 2)
        self.assertIn("nativePersistenceVerified", completed.stderr)

    def test_rejects_unverified_or_mismatched_runtime_realm_evidence(self) -> None:
        runtime = copy.deepcopy(self.runtime)
        runtime["realmEvidenceVerified"] = False
        completed = self.live(runtime=runtime)
        self.assertEqual(completed.returncode, 2)
        self.assertIn("realm evidence", completed.stderr)

        runtime = copy.deepcopy(self.runtime)
        runtime["composeProject"] = "weave-e2e-fedcfedcfedcfedc"
        completed = self.live(runtime=runtime)
        self.assertEqual(completed.returncode, 2)
        self.assertIn("exact candidate", completed.stderr)

        runtime = copy.deepcopy(self.runtime)
        runtime["realmEvidence"]["semanticRealmSourceDigest"] = "sha256:" + "f" * 64
        completed = self.live(runtime=runtime)
        self.assertEqual(completed.returncode, 2)
        self.assertIn("realm evidence", completed.stderr)

    def test_rejects_fixture_ui_as_live_or_cross_candidate_evidence(self) -> None:
        completed = self.live()
        self.assertEqual(completed.returncode, 0, completed.stdout + completed.stderr)
        simulator = self.simulator()
        simulator["candidateCommit"] = "4" * 40
        combined = subprocess.run(
            [
                sys.executable,
                str(SCRIPT),
                "combine",
                "--live-evidence",
                str(self.root / "live.json"),
                "--simulator-evidence",
                str(self.write("simulator.json", simulator)),
                "--output",
                str(self.root / "combined.json"),
            ],
            cwd=ROOT,
            text=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            check=False,
        )
        self.assertEqual(combined.returncode, 2)
        self.assertIn("disagree on candidateCommit", combined.stderr)

    def test_rejects_secret_bearing_evidence_keys(self) -> None:
        product = copy.deepcopy(self.product)
        product["accessToken"] = "redacted-but-forbidden"
        completed = self.live(product)
        self.assertEqual(completed.returncode, 2)
        self.assertIn("forbidden evidence key", completed.stderr)

    def test_rejects_manifest_mismatch_and_unbacked_outage_claim(self) -> None:
        product = copy.deepcopy(self.product)
        product["candidateManifestDigest"] = "sha256:" + "f" * 64
        completed = self.live(product)
        self.assertEqual(completed.returncode, 2)
        self.assertIn("candidate manifest digest", completed.stderr)

        product = copy.deepcopy(self.product)
        product["collaboration"]["passes"][0]["southboundProviderDependencyObserved"] = True
        completed = self.live(product)
        self.assertEqual(completed.returncode, 2)
        self.assertIn("southbound provider dependency", completed.stderr)

    def test_rejects_incomplete_or_unbounded_teardown_evidence(self) -> None:
        self.teardown["remainingContainerCount"] = 1
        self.teardown["remainingOwnedResources"] = 1
        completed = self.live()
        self.assertEqual(completed.returncode, 2)
        self.assertIn("left an isolated owned resource", completed.stderr)

        self.teardown["remainingContainerCount"] = 0
        self.teardown["remainingOwnedResources"] = 0
        self.teardown["composeDownStatus"] = "timed-out"
        self.teardown["fallbackAttempted"] = False
        completed = self.live()
        self.assertEqual(completed.returncode, 2)
        self.assertIn("did not use the owned-resource fallback", completed.stderr)

    def test_rejects_cross_manifest_or_cross_namespace_teardown_evidence(self) -> None:
        self.teardown["candidateManifestDigest"] = "sha256:" + "f" * 64
        completed = self.live()
        self.assertEqual(completed.returncode, 2)
        self.assertIn("another candidate manifest", completed.stderr)

        self.teardown["candidateManifestDigest"] = self.product["candidateManifestDigest"]
        self.teardown["removedVolumeNames"][0] = "weave_e2e_feedfeedfeedfeed_caddy_config"
        completed = self.live()
        self.assertEqual(completed.returncode, 2)
        self.assertIn("exact volume and network set", completed.stderr)

        self.teardown["removedVolumeNames"][0] = "weave_e2e_0123456789abcdef_caddy_config"
        self.teardown["removedNetworkName"] = "weave-e2e-feedfeedfeedfeed_network"
        completed = self.live()
        self.assertEqual(completed.returncode, 2)
        self.assertIn("exact volume and network set", completed.stderr)

    def test_rejects_non_string_teardown_volume_names_with_controlled_error(self) -> None:
        self.teardown["removedVolumeNames"][0] = {"forged": "volume"}
        completed = self.live()
        self.assertEqual(completed.returncode, 2)
        self.assertIn("exact volume and network set", completed.stderr)
        self.assertNotIn("TypeError", completed.stderr)
        self.assertNotIn("Traceback", completed.stderr)

    def test_rejects_secret_like_values_and_credentialed_urls(self) -> None:
        product = copy.deepcopy(self.product)
        product["diagnostic"] = "Bearer abc.def.ghi"
        completed = self.live(product)
        self.assertEqual(completed.returncode, 2)
        self.assertIn("secret-like", completed.stderr)

        manifest = copy.deepcopy(self.manifest)
        manifest["buildEvidenceRef"] = "https://operator:password@example.invalid/run"
        original = self.manifest
        try:
            self.manifest = manifest
            completed = self.live()
        finally:
            self.manifest = original
        self.assertEqual(completed.returncode, 2)
        self.assertRegex(completed.stderr, r"credential-bearing URL|secret-like")

    def test_rejects_malformed_or_secret_bearing_realm_definition(self) -> None:
        for mutation in (
            lambda definition: definition.__setitem__("containsSecrets", True),
            lambda definition: definition.__setitem__(
                "semanticRealmSourceDigest", "sha256:not-a-digest"
            ),
            lambda definition: definition.__setitem__(
                "unreviewedDigest", "sha256:" + "f" * 64
            ),
        ):
            with self.subTest(mutation=mutation):
                manifest = copy.deepcopy(self.manifest)
                mutation(manifest["realmDefinition"])
                original = self.manifest
                try:
                    self.manifest = manifest
                    completed = self.live()
                finally:
                    self.manifest = original
                self.assertEqual(completed.returncode, 2)
                self.assertIn("candidate manifest", completed.stderr)


if __name__ == "__main__":
    unittest.main(verbosity=2)
