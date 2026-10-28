#!/usr/bin/env python3
"""Candidate registry receipts must bind images, source, platform and digest."""
from __future__ import annotations

import base64
import copy
import json
import os
from pathlib import Path
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import patch

from build_artifacts import ArtifactError
import runtime_image_registry as registry
import runtime_registry_auth as auth


class ImageRegistryTests(unittest.TestCase):
    source = "a" * 40
    spec = "sha256:" + "b" * 64
    runtime = {"domains": ["calendar", "chat", "files"], "flavor": "jvm"}

    def validate(self, receipt, source=None, platform="linux/arm64"):
        return registry.validate_receipt(receipt, source or self.source, self.spec,
                                         platform=platform, runtime=self.runtime)

    def publish(self, platform="linux/arm64"):
        return registry.publish(self.images(), self.source, self.spec,
                                platform=platform, runtime=self.runtime)

    def receipt(self):
        return {"schemaVersion": "weave.runtime-images/v2", "sourceCommit": self.source,
                "specDigest": self.spec, "platform": "linux/arm64", "runtime": self.runtime,
                "images": {key: {"imageId": image, "reference": registry.NAMES[key] + "@" + image}
                           for key, image in self.images().items()}}

    def images(self) -> dict[str, str]:
        return {key: "sha256:" + str(index) * 64
                for index, key in enumerate(registry.NAMES, 1)}

    def context(self) -> dict[str, str]:
        return {"GITHUB_REPOSITORY": "masssi164/weave", "GITHUB_REF": "refs/heads/dogfood",
                "GITHUB_EVENT_NAME": "push", "GITHUB_SHA": self.source,
                "GITHUB_ACTOR": "weave-bot", "GHCR_TOKEN": "fixture-token",
                "GITHUB_RUN_ID": "1723", "GITHUB_RUN_ATTEMPT": "1"}

    def test_private_push_produces_immutable_digest_receipt(self):
        def fake_docker(command, *, input_text=None):
            if command[:2] == ["image", "inspect"]:
                return json.dumps([{"Id": command[-1], "Os": "linux", "Architecture": "arm64"}])
            if "login" in command:
                self.assertEqual(input_text, "fixture-token")
            if "push" in command:
                return "candidate: digest: sha256:" + "f" * 64 + " size: 1234"
            return ""

        with patch.dict(os.environ, self.context()), patch.object(registry, "_docker", side_effect=fake_docker) as runner:
            receipt = self.publish()
        self.assertEqual(self.validate(receipt), self.images())
        self.assertEqual(runner.call_count, 13)
        for key, entry in receipt["images"].items():
            self.assertTrue(entry["reference"].startswith(registry.NAMES[key] + "@sha256:"))
            self.assertNotIn(":candidate-", entry["reference"])

    def test_rejects_previous_public_candidate_package_receipts(self):
        previous_packages = {
            "backend": "weave-ci-backend", "mcp": "weave-ci-mcp",
            "keycloak": "weave-ci-keycloak", "runtimeState": "weave-ci-runtime-state",
        }
        self.assertEqual(set(registry.NAMES), set(previous_packages))
        for component, package in previous_packages.items():
            with self.subTest(component=component):
                receipt = self.receipt()
                self.validate(receipt)
                receipt["images"][component]["reference"] = (
                    f"ghcr.io/masssi164/{package}@sha256:" + "f" * 64)
                with self.assertRaisesRegex(ArtifactError, "runtime-registry-reference-invalid"):
                    self.validate(receipt)

    def test_rejects_mutable_or_cross_repository_receipts(self):
        receipt = self.receipt()
        self.validate(receipt)
        with self.assertRaises(ArtifactError):
            self.validate(receipt, "c" * 40)
        receipt["images"]["mcp"]["reference"] = "ghcr.io/attacker/mcp@sha256:" + "f" * 64
        with self.assertRaises(ArtifactError):
            self.validate(receipt)
        receipt["images"]["mcp"]["reference"] = registry.NAMES["mcp"] + ":latest"
        with self.assertRaises(ArtifactError):
            self.validate(receipt)

    def test_receipt_binds_platform_domains_and_runtime_flavor(self):
        for field, changed in (("platform", "linux/amd64"),
                               ("runtime", {"domains": ["files"], "flavor": "jvm"}),
                               ("runtime", {"domains": self.runtime["domains"], "flavor": "native"}),
                               ("runtime", None)):
            receipt = copy.deepcopy(self.receipt())
            receipt[field] = changed
            with self.subTest(field=field, changed=changed), self.assertRaisesRegex(ArtifactError, "receipt-invalid"):
                self.validate(receipt)

    def test_mismatched_image_platform_fails_before_registry_mutation(self):
        with patch.dict(os.environ, self.context()), patch.object(registry, "_docker", return_value=json.dumps([
                {"Id": self.images()["backend"], "Os": "linux", "Architecture": "amd64"}])) as docker, \
                self.assertRaisesRegex(ArtifactError, "image-platform-mismatch"):
            self.publish()
        self.assertEqual([call.args[0][:2] for call in docker.call_args_list], [["image", "inspect"]])

    def test_amd64_requires_explicit_manual_producer(self):
        with patch.dict(os.environ, self.context()), patch.object(registry, "_docker") as docker, \
                self.assertRaisesRegex(ArtifactError, "manual-only"):
            self.publish("linux/amd64")
        docker.assert_not_called()
        def fake_docker(command, *, input_text=None):
            if command[:2] == ["image", "inspect"]:
                return json.dumps([{"Id": command[-1], "Os": "linux", "Architecture": "amd64"}])
            if "push" in command:
                return "candidate: digest: sha256:" + "f" * 64 + " size: 1234"
            return ""
        context = {**self.context(), "GITHUB_EVENT_NAME": "workflow_dispatch"}
        with patch.dict(os.environ, context), patch.object(registry, "_docker", side_effect=fake_docker):
            receipt = self.publish("linux/amd64")
        self.assertEqual(self.validate(receipt, platform="linux/amd64"), self.images())
        with self.assertRaises(ArtifactError):
            self.validate(receipt)

    def test_rejects_untrusted_publication_context_before_login(self):
        context = self.context()
        context["GITHUB_REPOSITORY"] = "fork/weave"
        with patch.dict(os.environ, context), patch.object(registry, "_docker") as docker, \
                self.assertRaisesRegex(ArtifactError, "context-invalid"):
            self.publish()
        docker.assert_not_called()

    def test_auth_is_run_scoped_and_cleanup_is_exact(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            output = root / "github-env"
            output.touch()

            with patch.dict(os.environ, {"RUNNER_TEMP": temporary, "GITHUB_ENV": str(output),
                                      "GITHUB_ACTOR": "weave-bot", "GHCR_TOKEN": "fixture-token",
                                      "DOCKER_HOST": "unix:///fixture/docker.sock", "DOCKER_CONTEXT": "",
                                      "DOCKER_TLS": "", "DOCKER_TLS_VERIFY": ""}), \
                    patch.object(auth.subprocess, "run") as commands:
                auth.setup()
                environment = dict(line.split("=", 1) for line in output.read_text().splitlines())
                config = Path(environment["WEAVE_RUNTIME_DOCKER_CONFIG"])
                self.assertTrue(config.is_dir())
                self.assertEqual(config.stat().st_mode & 0o777, 0o700)
                self.assertEqual((config / "config.json").stat().st_mode & 0o777, 0o600)
                credentials = json.loads((config / "config.json").read_text())
                self.assertEqual(credentials, {"auths": {"ghcr.io": {"auth": base64.b64encode(
                    b"weave-bot:fixture-token").decode("ascii")}}})
                self.assertEqual(environment["DOCKER_HOST"], "unix:///fixture/docker.sock")
                self.assertEqual(environment["DOCKER_CONTEXT"], "")
                self.assertNotIn("fixture-token", output.read_text())
                # An installed osxkeychain/pass helper cannot be invoked: no
                # docker login is executed, regardless of host defaults.
                commands.assert_not_called()
                with self.assertRaises(ArtifactError):
                    auth.cleanup(str(root))
                auth.cleanup(str(config))
                self.assertFalse(config.exists())

    def test_auth_resolves_selected_named_local_docker_context(self):
        for context in ("desktop-linux", "colima"):
            with self.subTest(context=context), patch.dict(os.environ, {
                "DOCKER_CONTEXT": context, "DOCKER_HOST": "unix:///not-selected/docker.sock",
                "DOCKER_TLS": "", "DOCKER_TLS_VERIFY": ""}), patch.object(auth.subprocess, "run", return_value=
                    SimpleNamespace(stdout=json.dumps([{"Endpoints": {"docker": {
                        "Host": "unix:///selected/docker.sock"}}}]))) as command:
                self.assertEqual(auth.local_docker_host(), "unix:///selected/docker.sock")
                self.assertEqual(command.call_args.args[0], ["docker", "context", "inspect", "--", context])

    def test_auth_preserves_current_context_without_docker_environment_overrides(self):
        with patch.dict(os.environ, {"DOCKER_CONTEXT": "", "DOCKER_HOST": "", "DOCKER_TLS": "",
                                     "DOCKER_TLS_VERIFY": ""}), patch.object(auth.subprocess, "run", side_effect=[
                SimpleNamespace(stdout="desktop-linux\n"), SimpleNamespace(stdout=json.dumps([
                    {"Endpoints": {"docker": {"Host": "unix:///selected/docker.sock"}}}]))]) as commands:
            self.assertEqual(auth.local_docker_host(), "unix:///selected/docker.sock")
        self.assertEqual([call.args[0] for call in commands.call_args_list], [
            ["docker", "context", "show"], ["docker", "context", "inspect", "--", "desktop-linux"]])

    def test_remote_docker_context_is_not_silently_replaced_with_default_daemon(self):
        for endpoint in ("tcp://remote:2376", "ssh://operator@remote", "unix:///socket\nINJECTED=value"):
            with self.subTest(endpoint=endpoint), patch.dict(os.environ, {
                "DOCKER_CONTEXT": "", "DOCKER_HOST": endpoint, "DOCKER_TLS": "", "DOCKER_TLS_VERIFY": ""}), \
                    self.assertRaisesRegex(ArtifactError, "local-docker-socket-required"):
                auth.local_docker_host()

    def test_cleanup_preserves_unexpected_files_and_symlink_targets(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            config = root / "weave-ghcr-fixture"
            config.mkdir(mode=0o700)
            other = root / "operator-owned"
            other.write_text("fixture")
            (config / "config.json").symlink_to(other)
            with patch.dict(os.environ, {"RUNNER_TEMP": temporary}), \
                    self.assertRaisesRegex(ArtifactError, "cleanup-unexpected-entry"):
                auth.cleanup(str(config))
            self.assertEqual(other.read_text(), "fixture")


if __name__ == "__main__":
    unittest.main()
