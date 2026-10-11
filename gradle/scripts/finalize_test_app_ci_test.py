"""Prove cleanup rejects stale ownership and redirected output before mutation."""

import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest


class FinalizerSafetyTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.script = self.root / "gradle/scripts/finalize_test_app_ci.py"
        self.script.parent.mkdir(parents=True)
        shutil.copyfile(Path(__file__).with_name(self.script.name), self.script)
        self.run_id = "finalizer-security-probe"
        self.sha = "a" * 40
        self.namespace = "weave-e2e-" + hashlib.sha256(self.run_id.encode()).hexdigest()[:16]
        self.run = self.root / "build/test-app" / self.namespace
        self.env = dict(os.environ, WEAVE_TEST_APP_RUN_ID=self.run_id,
                        WEAVE_CANDIDATE_COMMIT=self.sha)

    def prepare(self):
        self.run.mkdir(parents=True)
        self.proof = {
            "schemaVersion": "weave.e2e-empty-namespace-proof/v1",
            "runId": self.run_id, "namespace": self.namespace,
            "composeProject": self.namespace, "candidateCommit": self.sha,
            "deploymentContext": "disposable", "environment": "e2e",
            "verifiedBeforeResourceCreation": True,
            "candidateManifestDigest": "sha256:" + "b" * 64,
        }
        self.write_proof()
        (self.run / "e2e.env").write_text("disposable input must remain\n")

    def write_proof(self):
        (self.run / "empty-namespace-proof.json").write_text(json.dumps(self.proof))

    def invoke(self):
        return subprocess.run([sys.executable, str(self.script)], env=self.env,
                              capture_output=True, text=True)

    def test_not_created_needs_no_cleanup(self):
        result = self.invoke()
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("status=not-created", result.stdout)

    def test_wrong_source_and_namespace_preserve_inputs(self):
        self.prepare()
        for key, wrong in (("candidateCommit", "c" * 40),
                           ("namespace", "weave-e2e-" + "0" * 16)):
            with self.subTest(key=key):
                original = self.proof[key]
                self.proof[key] = wrong
                self.write_proof()
                result = self.invoke()
                self.assertNotEqual(result.returncode, 0)
                self.assertIn("does not match the exact disposable run", result.stderr)
                self.assertTrue((self.run / "e2e.env").exists())
                self.proof[key] = original

    def test_symlinked_input_preserves_external_file(self):
        self.prepare()
        external = self.root / "unrelated.env"
        external.write_text("unrelated state\n")
        (self.run / "e2e.env").unlink()
        (self.run / "e2e.env").symlink_to(external)
        result = self.invoke()
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("symlinked run input", result.stderr)
        self.assertEqual(external.read_text(), "unrelated state\n")

    def test_remaining_resource_cannot_be_accepted(self):
        self.prepare()
        (self.run / "e2e.env").unlink()
        evidence = dict(self.proof, remainingOwnedResources=1,
                        remainingContainerCount=1, remainingNetworkCount=0,
                        remainingVolumeCount=0)
        (self.run / "teardown-evidence.json").write_text(json.dumps(evidence))
        result = self.invoke()
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("did not prove zero remaining resources", result.stderr)

    def test_dry_run_foreign_profile_and_boolean_counts_are_rejected(self):
        self.prepare()
        (self.run / "e2e.env").unlink()
        evidence = dict(self.proof, schemaVersion="weave.compose-isolated-teardown.v1",
                        profile="e2e", dryRun=False, ownershipLabelsVerified=True,
                        remainingOwnedResources=0, remainingContainerCount=0,
                        remainingNetworkCount=0, remainingVolumeCount=0)
        for key, wrong in (("dryRun", True), ("profile", "dogfood"),
                           ("composeProject", "weave-dogfood"),
                           ("remainingContainerCount", False)):
            with self.subTest(key=key):
                bad = dict(evidence, **{key: wrong})
                (self.run / "teardown-evidence.json").write_text(json.dumps(bad))
                result = self.invoke()
                self.assertNotEqual(result.returncode, 0)
                self.assertIn("did not prove zero remaining resources", result.stderr)
