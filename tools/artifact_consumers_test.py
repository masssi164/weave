#!/usr/bin/env python3
"""Runtime consumers must use the exact qualified bytes without compilation."""
from __future__ import annotations

import copy
import json
import os
from pathlib import Path
import plistlib
from types import SimpleNamespace
import tempfile
import unittest
from unittest.mock import patch

import artifact_consumers as consumers
from build_artifacts import ArtifactError


class ArtifactConsumers(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name).resolve()
        self.manifest = {"component": "ios-simulator", "sourceCommit": "a" * 40, "sourceTree": "b" * 40,
            "inputDigest": "sha256:" + "c" * 64, "target": "aarch64-apple-ios-sim", "buildMode": "debug",
            "buildNumber": "77", "producer": {"runId": 123, "runAttempt": 1},
            "payload": {"sha256": "sha256:" + "d" * 64}}
        entry = {key: self.manifest[key] for key in ("sourceCommit", "sourceTree", "inputDigest", "target", "buildMode", "buildNumber")}
        entry.update(payloadSha256=self.manifest["payload"]["sha256"], producerRunId=123, producerRunAttempt=1)
        self.composition = {"components": {"ios-simulator": entry}}
        self.addCleanup(patch.stopall)
        patch.dict(os.environ, {"WEAVE_BUILD_ARTIFACT_ROOT": str(self.root)}).start()

    def test_component_matches_original_producer_inside_current_composition(self):
        with patch.object(consumers, "verify_component", return_value=self.manifest), \
                patch.object(consumers, "candidate_composition", return_value=self.composition):
            manifest, path = consumers.component("ios-simulator", "f" * 40)
            self.assertIs(manifest, self.manifest)
            self.assertEqual(path, self.root / "ios-simulator/payload")

    def test_same_input_hash_does_not_allow_substituting_another_qualified_payload(self):
        for field, value in (("payloadSha256", "sha256:" + "e" * 64), ("buildNumber", "78"),
                             ("producerRunId", 124), ("producerRunAttempt", 2), ("sourceCommit", "e" * 40)):
            composition = copy.deepcopy(self.composition)
            composition["components"]["ios-simulator"][field] = value
            with self.subTest(field=field), patch.object(consumers, "verify_component", return_value=self.manifest), \
                    patch.object(consumers, "candidate_composition", return_value=composition), \
                    self.assertRaisesRegex(ArtifactError, "differs-from-qualified-composition"):
                consumers.component("ios-simulator", "f" * 40)

    def test_symlinked_consumer_root_is_rejected_before_readback(self):
        alias = self.root / "alias"
        alias.symlink_to(self.root, target_is_directory=True)
        with patch.dict(os.environ, {"WEAVE_BUILD_ARTIFACT_ROOT": str(alias)}), \
                patch.object(consumers, "verify_component") as verifier, self.assertRaises(ArtifactError):
            consumers.component("ios-simulator")
        verifier.assert_not_called()

    def test_native_bundle_requires_real_bundle_and_manifest_build_number(self):
        payload = self.root / "payload"
        app = payload / "ordinary/Runner.app"
        app.mkdir(parents=True)
        products = payload / "driver/Products"
        products.mkdir(parents=True)
        (products / "test.xctestrun").write_bytes(plistlib.dumps({}))
        info = {"CFBundleIdentifier": "com.massimotter.weave", "CFBundleVersion": "77"}
        (app / "Info.plist").write_bytes(plistlib.dumps(info))
        with patch.object(consumers, "component", return_value=(self.manifest, payload)):
            self.assertEqual(consumers.native_bundle()[1:], (app, products))
            info["CFBundleVersion"] = "78"
            (app / "Info.plist").write_bytes(plistlib.dumps(info))
            with self.assertRaisesRegex(ArtifactError, "build-identity-mismatch"):
                consumers.native_bundle()

    def test_portable_driver_plan_is_copied_without_modifying_shared_payload(self):
        products = self.root / "Products"
        products.mkdir()
        plan = {"BrowserDriver": {"TestBundlePath": "__TESTROOT__/Debug/BrowserDriver.xctest",
                                  "TestHostPath": "__TESTROOT__/Debug/BrowserDriver-Runner.app/BrowserDriver-Runner"}}
        source = products / "driver.xctestrun"
        source.write_bytes(plistlib.dumps(plan))
        original = source.read_bytes()
        destination = self.root / "private-products"
        self.assertEqual(consumers.copy_driver_products(products, destination), destination)
        (destination / "driver.xctestrun").write_bytes(plistlib.dumps({"private": "actor fixture"}))
        self.assertEqual(source.read_bytes(), original)
        plan["BrowserDriver"]["TestHostPath"] = "/Users/runner/work/old/Products/Runner"
        source.write_bytes(plistlib.dumps(plan))
        with self.assertRaisesRegex(ArtifactError, "nonportable-native-driver-plan"):
            consumers.copy_driver_products(products, self.root / "rejected")

    def runtime(self):
        manifest = {**self.manifest, "component": "runtime-linux-amd64", "target": "linux/amd64", "buildMode": "release"}
        definition = {"schemaVersion": "weave.runtime-images/v1", "sourceCommit": manifest["sourceCommit"],
                      "specDigest": "sha256:" + "e" * 64, "platform": "linux/amd64",
                      "images": {key: "sha256:" + str(index) * 64 for index, key in enumerate(("backend", "mcp", "keycloak", "runtimeState"), 1)}}
        return manifest, definition, self.root

    def image(self, reference, manifest, definition):
        return {"Id": reference, "Os": "linux", "Architecture": "amd64", "Config": {"Labels": {
            "org.opencontainers.image.revision": manifest["sourceCommit"],
            "com.massimotter.weave.spec-digest": definition["specDigest"], "com.massimotter.weave.module": "server"}}}

    def test_runtime_loads_verified_images_and_never_builds(self):
        manifest, definition, payload = self.runtime()
        def inspect(command, **_):
            return json.dumps([self.image(command[-1], manifest, definition)]).encode()
        with patch.object(consumers, "runtime_definition", return_value=(manifest, definition, payload)), \
                patch.object(consumers.subprocess, "run", return_value=SimpleNamespace(returncode=1)) as command, \
                patch.object(consumers.subprocess, "check_output", side_effect=inspect), \
                patch.object(consumers, "record_verified_runtime") as storage:
            environment = consumers.load_runtime_images()
        storage.assert_called_once_with(manifest, definition["images"])
        self.assertEqual(environment["WEAVE_TEST_APP_SERVER_IMAGE"], definition["images"]["backend"])
        self.assertEqual(environment["WEAVE_IMAGE_SOURCE_COMMIT"], manifest["sourceCommit"])
        invocations = [call.args[0] for call in command.call_args_list]
        self.assertIn(["docker", "load", "--input", str(payload / "images.tar")], invocations)
        self.assertFalse(any("build" in invocation for invocation in invocations))

    def test_running_server_must_match_image_id_and_original_producer_labels(self):
        manifest, definition, payload = self.runtime()
        reference = definition["images"]["backend"]
        value = self.image(reference, manifest, definition)
        with patch.object(consumers, "runtime_definition", return_value=(manifest, definition, payload)), \
                patch.object(consumers.subprocess, "check_output", return_value=json.dumps([value]).encode()):
            self.assertEqual(consumers.verify_runtime_container({"Image": reference})["sourceCommit"], manifest["sourceCommit"])
            with self.assertRaises(ArtifactError):
                consumers.verify_runtime_container({"Image": "sha256:" + "f" * 64})
        value["Config"]["Labels"]["org.opencontainers.image.revision"] = "f" * 40
        with patch.object(consumers, "runtime_definition", return_value=(manifest, definition, payload)), \
                patch.object(consumers.subprocess, "check_output", return_value=json.dumps([value]).encode()), \
                self.assertRaisesRegex(ArtifactError, "running-server-artifact-mismatch"):
            consumers.verify_runtime_container({"Image": reference})

    def test_retry_consumes_original_successful_build_composition(self):
        identity = {"candidateCommit": "a" * 40, "sourceTree": "b" * 40, "specificationCommit": "c" * 40}
        run = {"head_sha": identity["candidateCommit"], "head_branch": "dogfood", "event": "push",
               "path": ".github/workflows/live-stack-e2e.yml", "run_attempt": 2}
        prefix = f"candidate-artifacts-{identity['candidateCommit']}-123-attempt-"
        records = [{"name": prefix + "1"}, {"name": prefix + "3"}, {"name": "unrelated"}]
        output = self.root / "composition.json"
        with patch.dict(os.environ, {"GITHUB_TOKEN": "fixture-token"}), \
                patch.object(consumers, "candidate_identity", return_value=identity), \
                patch("verify_main_promotion_evidence.api", return_value=run), \
                patch("verify_main_promotion_evidence.paged", return_value=records), \
                patch("verify_main_promotion_evidence.download_artifact", return_value=b"fixture") as download, \
                patch("verify_main_promotion_evidence.artifact_json", return_value=self.composition), \
                patch("build_artifacts.validate_composition") as validate:
            consumers.fetch_composition(identity["candidateCommit"], 123, 2, output)
        self.assertEqual(download.call_args.args[2], prefix + "1")
        validate.assert_called_once_with(consumers.ROOT, self.composition, identity["candidateCommit"])
        self.assertEqual(json.loads(output.read_text()), self.composition)

    def test_expired_latest_composition_never_falls_back_to_older_artifact(self):
        identity = {"candidateCommit": "a" * 40, "sourceTree": "b" * 40, "specificationCommit": "c" * 40}
        run = {"head_sha": identity["candidateCommit"], "head_branch": "dogfood", "event": "push",
               "path": ".github/workflows/live-stack-e2e.yml", "run_attempt": 2}
        prefix = f"candidate-artifacts-{identity['candidateCommit']}-123-attempt-"
        output = self.root / "composition.json"
        with patch.dict(os.environ, {"GITHUB_TOKEN": "fixture-token"}), \
                patch.object(consumers, "candidate_identity", return_value=identity), \
                patch("verify_main_promotion_evidence.api", return_value=run), \
                patch("verify_main_promotion_evidence.paged", return_value=[{"name": prefix + "1"}, {"name": prefix + "2", "expired": True}]), \
                patch("verify_main_promotion_evidence.download_artifact", side_effect=ValueError("expired")) as download, \
                self.assertRaisesRegex(ValueError, "expired"):
            consumers.fetch_composition(identity["candidateCommit"], 123, 2, output)
        self.assertEqual(download.call_count, 1)
        self.assertEqual(download.call_args.args[2], prefix + "2")
        self.assertFalse(output.exists())

    def test_deployment_uses_composition_and_attempt_actually_qualified(self):
        identity = {"candidateCommit": "a" * 40, "sourceTree": "b" * 40, "specificationCommit": "c" * 40}
        run = {"head_sha": identity["candidateCommit"], "head_branch": "dogfood", "event": "push",
               "path": ".github/workflows/live-stack-e2e.yml", "run_attempt": 2}
        output, environment = self.root / "composition.json", self.root / "github-env"
        with patch.dict(os.environ, {"GITHUB_TOKEN": "fixture-token", "GITHUB_ENV": str(environment)}), \
                patch.object(consumers, "candidate_identity", return_value=identity), \
                patch("verify_main_promotion_evidence.api", return_value=run), \
                patch("verify_main_promotion_evidence.paged") as records, \
                patch("verify_main_promotion_evidence.download_artifact") as download, \
                patch("verify_main_promotion_evidence.qualification_evidence", return_value=(1, self.composition)) as qualification, \
                patch("build_artifacts.validate_composition"):
            consumers.fetch_composition(identity["candidateCommit"], 123, 2, output, qualified=True)
        qualification.assert_called_once()
        records.assert_not_called()
        download.assert_not_called()
        self.assertEqual(json.loads(output.read_text()), self.composition)
        self.assertEqual(environment.read_text(), "WEAVE_QUALIFICATION_RUN_ATTEMPT=1\n")


if __name__ == "__main__":
    unittest.main()
