#!/usr/bin/env python3
"""Candidate registry receipts must bind images, source, platform and digest."""
from __future__ import annotations

import base64
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
            if "login" in command:
                self.assertEqual(input_text, "fixture-token")
            if "push" in command:
                return "candidate: digest: sha256:" + "f" * 64 + " size: 1234"
            return ""

        with patch.dict(os.environ, self.context()), patch.object(registry, "_docker", side_effect=fake_docker) as runner:
            receipt = registry.publish(self.images(), self.source, self.spec)
        self.assertEqual(registry.validate_receipt(receipt, self.source, self.spec), self.images())
        self.assertEqual(runner.call_count, 9)
        for key, entry in receipt["images"].items():
            self.assertTrue(entry["reference"].startswith(registry.NAMES[key] + "@sha256:"))
            self.assertNotIn(":candidate-", entry["reference"])

    def test_rejects_mutable_or_cross_repository_receipts(self):
        receipt = {"schemaVersion": "weave.runtime-images/v2", "sourceCommit": self.source,
                   "specDigest": self.spec, "platform": "linux/amd64",
                   "images": {key: {"imageId": image, "reference": registry.NAMES[key] + "@" + image}
                              for key, image in self.images().items()}}
        registry.validate_receipt(receipt, self.source, self.spec)
        with self.assertRaises(ArtifactError):
            registry.validate_receipt(receipt, "c" * 40, self.spec)
        receipt["images"]["mcp"]["reference"] = "ghcr.io/attacker/mcp@sha256:" + "f" * 64
        with self.assertRaises(ArtifactError):
            registry.validate_receipt(receipt, self.source, self.spec)
        receipt["images"]["mcp"]["reference"] = registry.NAMES["mcp"] + ":latest"
        with self.assertRaises(ArtifactError):
            registry.validate_receipt(receipt, self.source, self.spec)

    def test_rejects_untrusted_publication_context_before_login(self):
        context = self.context()
        context["GITHUB_REPOSITORY"] = "fork/weave"
        with patch.dict(os.environ, context), patch.object(registry, "_docker") as docker, \
                self.assertRaisesRegex(ArtifactError, "context-invalid"):
            registry.publish(self.images(), self.source, self.spec)
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
