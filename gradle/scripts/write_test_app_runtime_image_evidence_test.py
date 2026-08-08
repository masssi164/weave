#!/usr/bin/env python3
"""Tests for manifest-bound runtime image and rendered realm evidence."""

from __future__ import annotations

import argparse
import hashlib
import importlib.util
import json
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch


ROOT = Path(__file__).resolve().parents[2]
SPEC = importlib.util.spec_from_file_location(
    "write_test_app_runtime_image_evidence",
    ROOT / "gradle/scripts/write_test_app_runtime_image_evidence.py",
)
assert SPEC and SPEC.loader
module = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(module)


class RuntimeImageEvidenceTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temporary = tempfile.TemporaryDirectory()
        self.root = Path(self.temporary.name)
        self.candidate = "1" * 40
        self.source = "2" * 40
        self.specification = "3" * 40
        self.spec_digest = "sha256:" + "4" * 64
        self.build_evidence = "sha256:" + "5" * 64
        self.references = {
            "server": "ghcr.io/example/server@sha256:" + "6" * 64,
            "mcp-server": "ghcr.io/example/mcp@sha256:" + "7" * 64,
            "keycloak-runtime": "ghcr.io/example/keycloak@sha256:" + "8" * 64,
        }
        self.image_ids = {
            "server": "sha256:" + "9" * 64,
            "mcp-server": "sha256:" + "a" * 64,
            "keycloak-runtime": "sha256:" + "b" * 64,
        }
        self.baseline = self.root / "weave-realm.json"
        self.migrations = self.root / "manifest.json"
        self.baseline.write_text('{"realm":"weave"}\n', encoding="utf-8")
        self.migrations.write_text(
            '{"migrations":[],"schemaVersion":"weave.realm-migrations.v1"}\n',
            encoding="utf-8",
        )
        realm_artifacts = {
            "baselineDigest": self.digest(self.baseline),
            "migrationBundleDigest": self.digest(self.migrations),
            "containsSecrets": False,
        }
        manifest = {
            "schemaVersion": "weave.release.candidate-manifest.v3",
            "supportSafe": True,
            "commit": self.source,
            "specificationCommit": self.specification,
            "specDigest": self.spec_digest,
            "realmArtifacts": realm_artifacts,
            "images": [
                {
                    "component": component,
                    "reference": reference,
                    **(
                        {"buildEvidenceDigest": self.build_evidence}
                        if component == "keycloak-runtime"
                        else {}
                    ),
                }
                for component, reference in sorted(self.references.items())
            ],
        }
        self.manifest = self.root / "candidate-manifest.json"
        self.manifest.write_bytes(
            json.dumps(
                manifest,
                ensure_ascii=False,
                separators=(",", ":"),
                sort_keys=True,
            ).encode("utf-8")
        )
        self.output = self.root / "runtime-image-evidence.json"

    def tearDown(self) -> None:
        self.temporary.cleanup()

    @staticmethod
    def digest(path: Path) -> str:
        return "sha256:" + hashlib.sha256(path.read_bytes()).hexdigest()

    def arguments(self) -> argparse.Namespace:
        return argparse.Namespace(
            candidate_commit=self.candidate,
            source_candidate_commit=self.source,
            specification_commit=self.specification,
            spec_digest=self.spec_digest,
            candidate_manifest_digest=self.digest(self.manifest),
            compose_project="weave-e2e-0123456789abcdef",
            output=self.output,
            manifest=self.manifest,
            realm_baseline_artifact=self.baseline,
            realm_migration_bundle_artifact=self.migrations,
            image=[
                [component, self.references[component], self.image_ids[component]]
                for component in sorted(self.references)
            ],
        )

    def run_writer(self) -> int:
        service_ids = {
            "backend": self.image_ids["server"],
            "mcp": self.image_ids["mcp-server"],
            "keycloak": self.image_ids["keycloak-runtime"],
        }
        with (
            patch.object(module, "arguments", return_value=self.arguments()),
            patch.object(
                module,
                "container_image_id",
                side_effect=lambda _project, service: service_ids[service],
            ),
            patch.object(module, "image_label", return_value=self.build_evidence),
        ):
            return module.main()

    def test_hashes_exact_rendered_artifacts_into_verified_evidence(self) -> None:
        self.assertEqual(self.run_writer(), 0)
        evidence = json.loads(self.output.read_text(encoding="utf-8"))
        self.assertTrue(evidence["manifestBound"])
        self.assertTrue(evidence["realmArtifactsVerified"])
        self.assertEqual(
            evidence["realmArtifacts"]["baselineDigest"],
            self.digest(self.baseline),
        )
        self.assertEqual(
            {item["component"] for item in evidence["images"]},
            set(module.COMPONENTS),
        )

    def test_rejects_rendered_artifact_drift(self) -> None:
        self.baseline.write_text('{"realm":"other"}\n', encoding="utf-8")
        with self.assertRaisesRegex(
            SystemExit, "rendered realm baseline differs from the candidate manifest"
        ):
            self.run_writer()


if __name__ == "__main__":
    unittest.main(verbosity=2)
