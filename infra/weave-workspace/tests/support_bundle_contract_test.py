#!/usr/bin/env python3
"""Unit evidence for the bounded Compose support bundle."""

from __future__ import annotations

import json
import stat
import sys
import tarfile
import tempfile
import unittest
from pathlib import Path
from types import SimpleNamespace
from unittest import mock

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))

import support_bundle  # noqa: E402
from compose_env import ContractError  # noqa: E402


class SupportBundleContractTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temporary = tempfile.TemporaryDirectory()
        base = Path(self.temporary.name)
        self.generated = base / "generated"
        self.output = base / "output"
        self.generated.mkdir()
        self.context = SimpleNamespace(
            profile="e2e",
            generated_root=self.generated,
            env={
                "WEAVE_COMPOSE_PROJECT": "weave-e2e",
                "WEAVE_ADMIN_CONSOLE_URL": "https://admin.weave.test:44443",
                "WEAVE_PROVIDER_PROFILE": "sovereign-default",
                "WEAVE_SECRET_VALUE": "must-never-appear",
            },
        )
        self.model = {
            "modelDigest": "sha256:" + "a" * 64,
            "services": {"backend": {"image": "sha256:" + "b" * 64}},
            "volumeNames": ["weave_db_data"],
            "networkNames": ["weave_network"],
        }
        self.ps = [{"Service": "backend", "State": "running", "Health": "healthy"}]

    def tearDown(self) -> None:
        self.temporary.cleanup()

    def _evidence(self, relative: str, value: dict[str, object]) -> None:
        path = self.generated / relative
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps(value), encoding="utf-8")

    def test_bundle_includes_only_allowlisted_support_safe_evidence(self) -> None:
        self._evidence(
            "operator/readiness.json",
            {"schemaVersion": "operator.v1", "containsSecretValues": False, "supportSafe": True},
        )
        self._evidence(
            "nextcloud/readiness.json",
            {"schemaVersion": "nextcloud.v1", "containsSecretValues": False, "supportSafe": True},
        )
        self._evidence(
            "keycloak/private-response.json",
            {"authorization": "Bearer eyJfixture.fixture.fixture", "secret": "must-never-appear"},
        )
        (self.generated / "raw.log").write_text("person@example.com must-never-appear", encoding="utf-8")

        with (
            mock.patch.object(support_bundle, "_compose_model", return_value=self.model),
            mock.patch.object(support_bundle, "_ps", return_value=self.ps),
        ):
            archive = support_bundle.create(self.context, self.output)

        self.assertEqual(stat.S_IMODE(archive.stat().st_mode), 0o600)
        with tarfile.open(archive, "r:gz") as bundle:
            members = sorted(member.name.split("/", 1)[1] for member in bundle.getmembers() if "/" in member.name)
            self.assertEqual(
                members,
                [
                    "compose-model-summary.json",
                    "compose-ps.json",
                    "manifest.json",
                    "nextcloud-readiness.json",
                    "operator-readiness.json",
                ],
            )
            manifest_member = next(member for member in bundle.getmembers() if member.name.endswith("/manifest.json"))
            manifest = json.load(bundle.extractfile(manifest_member))
            payload = b"\n".join(
                bundle.extractfile(member).read()
                for member in bundle.getmembers()
                if member.isfile()
            )
        self.assertNotIn(b"must-never-appear", payload)
        self.assertNotIn(b"eyJfixture", payload)
        self.assertNotIn(b"person@example.com", payload)
        self.assertEqual(manifest["schemaVersion"], "weave.compose-support-bundle.v1")
        self.assertEqual(manifest["includedEvidence"], ["nextcloud-readiness.json", "operator-readiness.json"])
        self.assertTrue(manifest["supportSafe"])
        self.assertFalse(manifest["containsSecretValues"])
        self.assertIn(b'"supportSafe": true', payload)
        self.assertIn(b'"containsSecretValues": false', payload)
        self.assertIn(b'"raw logs"', payload)
        self.assertIn(b'"signed receipt payloads"', payload)

    def test_evidence_without_explicit_safe_marker_is_rejected(self) -> None:
        self._evidence(
            "nextcloud/readiness.json",
            {"schemaVersion": "nextcloud.v1", "containsSecretValues": True, "secret": "unsafe"},
        )
        with (
            mock.patch.object(support_bundle, "_compose_model", return_value=self.model),
            mock.patch.object(support_bundle, "_ps", return_value=self.ps),
        ):
            with self.assertRaisesRegex(ContractError, "not explicitly support-safe"):
                support_bundle.create(self.context, self.output)


if __name__ == "__main__":
    unittest.main()
