#!/usr/bin/env python3
"""Fixture tests for tools/release_readiness_check.py."""

from __future__ import annotations

import json
import shutil
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SCRIPT = ROOT / "tools" / "release_readiness_check.py"
FIXTURE = ROOT / "tools" / "fixtures" / "rc_readiness" / "green"
COMMIT = "1111111111111111111111111111111111111111"
TAG = "v0.1.0-rc.1"
VERSION = "0.1.0-rc.1"


class ReleaseReadinessCheckTest(unittest.TestCase):
    def setUp(self) -> None:
        self.tmp = tempfile.TemporaryDirectory()
        self.root = Path(self.tmp.name) / "fixture"
        shutil.copytree(FIXTURE, self.root)

    def tearDown(self) -> None:
        self.tmp.cleanup()

    def run_check(self, *extra: str) -> subprocess.CompletedProcess[str]:
        args = [
            sys.executable,
            str(SCRIPT),
            "--candidate-version",
            VERSION,
            "--candidate-tag",
            TAG,
            "--candidate-commit",
            COMMIT,
            "--ci-summary",
            str(self.root / "ci-summary.json"),
            "--live-manifest",
            str(self.root / "weave-live-stack-acceptance-evidence" / "release-evidence-manifest.json"),
            "--release-notes",
            str(self.root / "release-notes-unreleased.md"),
            "--blockers-json",
            str(self.root / "release-blockers.json"),
            "--json",
            *extra,
        ]
        return subprocess.run(args, cwd=ROOT, text=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE, check=False)

    def json_result(self, completed: subprocess.CompletedProcess[str]) -> dict:
        return json.loads(completed.stdout)

    def check_by_id(self, result: dict, check_id: str) -> dict:
        for check in result["checks"]:
            if check["id"] == check_id:
                return check
        self.fail(f"missing check {check_id}")

    def test_green_fixture_is_ready(self) -> None:
        completed = self.run_check()
        self.assertEqual(completed.returncode, 0, completed.stdout + completed.stderr)
        result = self.json_result(completed)
        self.assertEqual(result["status"], "ready")
        self.assertEqual(self.check_by_id(result, "live-e2e")["status"], "pass")

    def test_missing_e2e_manifest_blocks_without_waiver(self) -> None:
        manifest = self.root / "weave-live-stack-acceptance-evidence" / "release-evidence-manifest.json"
        manifest.unlink()
        completed = self.run_check()
        self.assertNotEqual(completed.returncode, 0)
        result = self.json_result(completed)
        self.assertEqual(result["status"], "blocked")
        self.assertEqual(self.check_by_id(result, "live-e2e")["status"], "fail")

    def test_wrong_manifest_commit_blocks(self) -> None:
        manifest = self.root / "weave-live-stack-acceptance-evidence" / "release-evidence-manifest.json"
        data = json.loads(manifest.read_text(encoding="utf-8"))
        data["commit"] = "2222222222222222222222222222222222222222"
        manifest.write_text(json.dumps(data), encoding="utf-8")
        completed = self.run_check()
        self.assertNotEqual(completed.returncode, 0)
        self.assertIn("manifest commit does not match candidate", self.check_by_id(self.json_result(completed), "live-e2e")["summary"])

    def test_missing_marker_blocks(self) -> None:
        manifest = self.root / "weave-live-stack-acceptance-evidence" / "release-evidence-manifest.json"
        data = json.loads(manifest.read_text(encoding="utf-8"))
        data["acceptanceContract"]["observedMarkers"].remove("BOARDS_RESULT")
        manifest.write_text(json.dumps(data), encoding="utf-8")
        completed = self.run_check()
        self.assertNotEqual(completed.returncode, 0)
        self.assertIn("BOARDS_RESULT", self.check_by_id(self.json_result(completed), "live-e2e")["summary"])

    def test_open_release_blocker_blocks(self) -> None:
        blockers = {
            "schemaVersion": 1,
            "openBlockers": [
                {"number": 360, "title": "Live Stack E2E is red", "state": "open", "labels": ["release-blocker"]}
            ],
        }
        (self.root / "release-blockers.json").write_text(json.dumps(blockers), encoding="utf-8")
        completed = self.run_check()
        self.assertNotEqual(completed.returncode, 0)
        self.assertIn("#360", self.check_by_id(self.json_result(completed), "release-blockers")["summary"])

    def test_uppercase_open_release_blocker_blocks(self) -> None:
        blockers = {
            "schemaVersion": 1,
            "issues": [
                {"number": 591, "title": "Manual AT signoff", "state": "OPEN", "labels": [{"name": "release-blocker"}]}
            ],
        }
        (self.root / "release-blockers.json").write_text(json.dumps(blockers), encoding="utf-8")
        completed = self.run_check()
        self.assertNotEqual(completed.returncode, 0)
        self.assertIn("#591", self.check_by_id(self.json_result(completed), "release-blockers")["summary"])

    def test_support_safe_policy_requires_all_evidence_exclusions(self) -> None:
        readme = ROOT / "README.md"
        original = readme.read_text(encoding="utf-8")
        try:
            readme.write_text(original.replace("private prompts", "private redacted inputs"), encoding="utf-8")
            completed = self.run_check()
            self.assertNotEqual(completed.returncode, 0)
            self.assertEqual(self.check_by_id(self.json_result(completed), "support-safe-policy")["status"], "fail")
        finally:
            readme.write_text(original, encoding="utf-8")

    def test_release_claim_control_blocks_prohibited_wording_classes(self) -> None:
        notes = self.root / "release-notes-unreleased.md"
        samples = [
            "public release is ready",
            "full accessibility passed",
            "provider interchangeability is available",
            "production restore is ready",
            "Weaver is available",
        ]
        for sample in samples:
            original = notes.read_text(encoding="utf-8")
            notes.write_text(original + f"\n- Unsafe sample says {sample}.\n", encoding="utf-8")
            completed = self.run_check()
            self.assertNotEqual(completed.returncode, 0, sample)
            self.assertEqual(self.check_by_id(self.json_result(completed), "claim-control")["status"], "fail")
            notes.write_text(original, encoding="utf-8")

    def test_missing_e2e_can_be_waived_with_explicit_marker(self) -> None:
        manifest = self.root / "weave-live-stack-acceptance-evidence" / "release-evidence-manifest.json"
        manifest.unlink()
        completed = self.run_check("--waiver", str(self.root / "live-e2e-waiver.json"))
        self.assertEqual(completed.returncode, 0, completed.stdout + completed.stderr)
        result = self.json_result(completed)
        self.assertEqual(result["status"], "ready")
        self.assertEqual(self.check_by_id(result, "live-e2e")["status"], "waived")


if __name__ == "__main__":
    unittest.main(verbosity=2)
