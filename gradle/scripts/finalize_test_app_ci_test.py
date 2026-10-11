"""Prove cleanup rejects stale ownership and redirected output before mutation."""

import hashlib
import importlib.util
import io
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest
from contextlib import redirect_stdout
from unittest.mock import patch


class FinalizerSafetyTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name).resolve()
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

    def module(self):
        spec = importlib.util.spec_from_file_location("finalizer_probe", self.script)
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        return module

    def test_preproof_cleanup_checks_namespace_without_compose_teardown(self):
        self.run.mkdir(parents=True)
        module = self.module()
        output = io.StringIO()
        with patch.dict(os.environ, self.env), patch.object(module, "docker", return_value="") as docker, \
                patch.object(module, "cleanup_inputs") as cleanup, redirect_stdout(output):
            module.main()
        self.assertIn("status=no-stack", output.getvalue())
        cleanup.assert_called_once_with(self.root, self.root / "build/test-app", self.namespace)
        self.assertEqual(docker.call_count, 4)
        for call in docker.call_args_list[:3]:
            self.assertIn(f"label=com.massimotter.weave.namespace={self.namespace}", call.args)
        self.assertEqual(docker.call_args_list[2].args[-1], "{{.Name}}")

    def test_preproof_unexpected_resource_preserves_inputs(self):
        self.run.mkdir(parents=True)
        module = self.module()
        with patch.dict(os.environ, self.env), patch.object(module, "docker", return_value="unexpected"), \
                patch.object(module, "cleanup_inputs") as cleanup:
            with self.assertRaisesRegex(ValueError, "unexpected namespace resources"):
                module.main()
        cleanup.assert_not_called()

    def test_images_require_exact_provenance_and_no_container_before_removal(self):
        module = self.module()
        for title, name in (("Weave Server", "weave-backend"),
                            ("Weave MCP Server", "weave-mcp-server")):
            tag = f"{name}:test-app-{self.namespace}"
            labels = {
                "org.opencontainers.image.revision": self.sha,
                "org.opencontainers.image.source": "https://github.com/masssi164/weave",
                "org.opencontainers.image.title": title,
                "com.massimotter.weave.provenance-reference": "local-test-app-not-published",
            }
            for key in labels:
                with self.subTest(name=name, foreign=key):
                    foreign = dict(labels, **{key: "unowned"})
                    with patch.object(module, "docker", side_effect=[tag, json.dumps([
                            {"Config": {"Labels": foreign}}])]) as docker:
                        with self.assertRaisesRegex(ValueError, "unowned"):
                            module.cleanup_images(self.namespace, self.sha)
                    self.assertEqual(docker.call_count, 2)
            with self.subTest(name=name, in_use=True):
                with patch.object(module, "docker", side_effect=[tag, json.dumps([
                        {"Config": {"Labels": labels}}]), "container"]) as docker:
                    with self.assertRaisesRegex(ValueError, "in-use"):
                        module.cleanup_images(self.namespace, self.sha)
                self.assertEqual(docker.call_count, 3)
            with self.subTest(name=name, owned=True):
                with patch.object(module, "docker", side_effect=[tag, json.dumps([
                        {"Config": {"Labels": labels}}]), "", ""]) as docker:
                    module.cleanup_images(self.namespace, self.sha)
                self.assertEqual(docker.call_args_list[-1].args, ("image", "rm", tag))

    def test_absent_run_still_checks_resources_and_images(self):
        module = self.module()
        output = io.StringIO()
        with patch.dict(os.environ, self.env), patch.object(module, "docker", return_value="") as docker, \
                patch.object(module, "cleanup_inputs") as cleanup, redirect_stdout(output):
            module.main()
        self.assertIn("status=not-created", output.getvalue())
        self.assertEqual(docker.call_count, 4)
        cleanup.assert_not_called()

    def test_docker_timeout_propagates_without_input_cleanup(self):
        self.run.mkdir(parents=True)
        module = self.module()
        with patch.dict(os.environ, self.env), patch.object(module.subprocess, "run", \
                side_effect=subprocess.TimeoutExpired("docker", 30)) as command, \
                patch.object(module, "cleanup_inputs") as cleanup:
            with self.assertRaises(subprocess.TimeoutExpired):
                module.main()
        self.assertEqual(command.call_args.kwargs["timeout"], 30)
        cleanup.assert_not_called()

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
