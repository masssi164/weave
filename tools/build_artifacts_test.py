#!/usr/bin/env python3
"""Regression tests for relevant-input reuse, producer trust and archive consumption."""
from __future__ import annotations

import copy
import hashlib
import io
import json
import os
from pathlib import Path
import plistlib
import shutil
import struct
import subprocess
import tarfile
import tempfile
import unittest
from unittest.mock import patch
import zipfile

import build_artifacts as artifacts
import build_artifacts_hosted as hosted


class Repository(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name) / "repo"
        self.root.mkdir()
        self.command("init", "-q")
        self.command("config", "user.name", "Artifact test")
        self.command("config", "user.email", "artifact@example.invalid")
        self.write(artifacts.CONFIG, (artifacts.ROOT / artifacts.CONFIG).read_text())
        self.write("specs/weave-specs.lock.json", json.dumps({"specCorpus": {"gitCommit": "a" * 40}}))
        self.write("client/lib/main.dart", "void main() {}\n")
        self.write("server/src/main/java/Server.java", "class Server {}\n")
        self.write("contracts/openapi/weave-user-openapi.json", "{}\n")
        self.write("rust/matrix-client/src/lib.rs", "pub fn run() {}\n")
        self.write(artifacts.SCENARIO, json.dumps({"schemaVersion": "fixture/v1", "members": ["a", "b"]}))
        self.commit()

    def command(self, *arguments):
        return subprocess.check_output(["git", *arguments], cwd=self.root, stderr=subprocess.DEVNULL).decode().strip()

    def write(self, name, value):
        path = self.root / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(value)

    def commit(self):
        self.command("add", ".")
        self.command("commit", "-qm", "fixture")

    def inputs(self):
        return {name: artifacts.input_digest(self.root, name) for name in artifacts.DEFAULT_COMPONENTS}

    def bundle(self):
        payload = Path(self.temporary.name) / "payload"
        (payload / "ordinary/Runner.app/Frameworks/Test.framework/Versions/A").mkdir(parents=True)
        executable = payload / "ordinary/Runner.app/Runner"
        executable.write_bytes(b"actual-application-bytes")
        executable.chmod(0o755)
        (payload / "ordinary/Runner.app/Frameworks/Test.framework/Versions/Current").symlink_to("A")
        (payload / "ordinary/Runner.app/Frameworks/Test.framework/Versions/A/Test").write_bytes(b"framework")
        archive = Path(self.temporary.name) / "payload.tar.gz"
        tree = artifacts.package_payload(payload, archive)
        component = "ios-simulator"
        definition = artifacts.component_configuration(self.root, component)
        manifest = {"schemaVersion": "weave.build-artifact/v1", "component": component,
            "inputDigest": artifacts.input_digest(self.root, component),
            "sourceCommit": self.command("rev-parse", "HEAD"), "sourceTree": self.command("rev-parse", "HEAD^{tree}"),
            "target": definition["target"], "buildMode": definition["buildMode"], "toolchain": definition["toolchain"],
            "buildNumber": "12", "payload": {"filename": "payload.tar.gz", "sha256": artifacts.digest_file(archive),
            "bytes": archive.stat().st_size, "treeDigest": tree},
            "producer": {"repository": "masssi164/weave", "runId": 123, "runAttempt": 1,
                "ref": "refs/heads/dev", "event": "push", "workflowPath": ".github/workflows/build-artifacts.yml",
                "jobName": "Build component (ios-simulator)"}}
        return manifest, archive

    def test_docs_do_not_rebuild_but_spec_lock_rebuilds_runtime_with_embedded_digest(self):
        before = self.inputs()
        self.write("docs/example.md", "Changed explanatory prose\n")
        self.commit()
        self.assertEqual(before, self.inputs())
        self.write("specs/weave-specs.lock.json", json.dumps({"specCorpus": {"gitCommit": "b" * 40}}))
        self.commit()
        after = self.inputs()
        self.assertNotEqual(before["runtime-linux-amd64"], after["runtime-linux-amd64"])
        self.assertEqual(before["ios-simulator"], after["ios-simulator"])
        self.assertEqual(before["ios-device"], after["ios-device"])

    def test_client_change_does_not_rebuild_server(self):
        before = self.inputs()
        self.write("client/lib/main.dart", "void main() { print('changed'); }\n")
        self.commit()
        after = self.inputs()
        self.assertEqual(before["runtime-linux-amd64"], after["runtime-linux-amd64"])
        self.assertNotEqual(before["ios-simulator"], after["ios-simulator"])
        self.assertNotEqual(before["ios-device"], after["ios-device"])

    def test_server_change_does_not_rebuild_ios(self):
        before = self.inputs()
        self.write("server/src/main/java/Server.java", "class Server { int version = 2; }\n")
        self.commit()
        after = self.inputs()
        self.assertNotEqual(before["runtime-linux-amd64"], after["runtime-linux-amd64"])
        self.assertEqual(before["ios-simulator"], after["ios-simulator"])
        self.assertEqual(before["ios-device"], after["ios-device"])

    def test_embedded_admin_console_and_module_dependency_changes_rebuild_runtime(self):
        before = self.inputs()
        for path in ("admin-console/src/main.ts", "weave-mcp-server/gradle/scripts/java-and-dependencies.gradle"):
            self.write(path, "changed build input\n")
            self.commit()
            after = self.inputs()
            self.assertNotEqual(before["runtime-linux-amd64"], after["runtime-linux-amd64"])
            self.assertEqual(before["ios-simulator"], after["ios-simulator"])
            self.assertEqual(before["ios-device"], after["ios-device"])
            before = after

    def test_shared_rust_change_rebuilds_all_consumers(self):
        before = self.inputs()
        self.write("rust/matrix-client/src/lib.rs", "pub fn changed() {}\n")
        self.commit()
        self.assertTrue(all(before[name] != self.inputs()[name] for name in before))

    def test_cocoapods_toolchain_change_invalidates_only_affected_component(self):
        before = self.inputs()
        config = artifacts.configuration(self.root)
        config["components"]["ios-device"]["toolchain"]["cocoapods"] = "different"
        self.write(artifacts.CONFIG, json.dumps(config))
        self.commit()
        after = self.inputs()
        self.assertNotEqual(before["ios-device"], after["ios-device"])
        self.assertEqual(before["ios-simulator"], after["ios-simulator"])
        self.assertEqual(before["runtime-linux-amd64"], after["runtime-linux-amd64"])

    def test_mode_rename_and_deletion_change_input_identity(self):
        values = [artifacts.input_digest(self.root, "ios-device")]
        (self.root / "client/lib/main.dart").chmod(0o755)
        self.commit()
        values.append(artifacts.input_digest(self.root, "ios-device"))
        (self.root / "client/lib/main.dart").rename(self.root / "client/lib/renamed.dart")
        self.commit()
        values.append(artifacts.input_digest(self.root, "ios-device"))
        (self.root / "client/lib/renamed.dart").unlink()
        self.commit()
        values.append(artifacts.input_digest(self.root, "ios-device"))
        self.assertEqual(len(set(values)), 4)

    def test_bundle_roundtrip_retains_executable_and_internal_framework_link(self):
        manifest, archive = self.bundle()
        destination = Path(self.temporary.name) / "extracted"
        destination.mkdir()
        artifacts.extract_payload(archive, destination / "payload")
        (destination / "manifest.json").write_text(json.dumps(manifest))
        self.assertEqual(artifacts.verify_component(self.root, destination), manifest)
        self.assertTrue((destination / "payload/ordinary/Runner.app/Runner").stat().st_mode & 0o111)
        self.assertTrue((destination / "payload/ordinary/Runner.app/Frameworks/Test.framework/Versions/Current").is_symlink())
        (destination / "payload/ordinary/Runner.app/Runner").write_bytes(b"modified")
        with self.assertRaisesRegex(artifacts.ArtifactError, "payload-digest-mismatch"):
            artifacts.verify_component(self.root, destination)

    def test_private_operator_umask_preserves_the_producer_tree_digest(self):
        manifest, archive = self.bundle()
        destination = Path(self.temporary.name) / "private-operator"
        previous = os.umask(0o077)
        try:
            artifacts.extract_payload(archive, destination)
        finally:
            os.umask(previous)
        self.assertEqual(artifacts.tree_digest(destination), manifest["payload"]["treeDigest"])

    def test_wrong_architecture_toolchain_or_source_inputs_cannot_be_reused(self):
        manifest, _ = self.bundle()
        for field, value in (("target", "x86_64-apple-ios"), ("buildMode", "release"), ("inputDigest", "sha256:" + "0" * 64)):
            invalid = {**manifest, field: value}
            with self.subTest(field=field), self.assertRaises(artifacts.ArtifactError):
                artifacts.validate_manifest(self.root, invalid, "ios-simulator")
        changed = copy.deepcopy(manifest)
        changed["toolchain"]["xcodeBuild"] = "different"
        with self.assertRaises(artifacts.ArtifactError):
            artifacts.validate_manifest(self.root, changed, "ios-simulator")

    def test_unchanged_inputs_allow_original_producer_identity_after_new_commit(self):
        manifest, _ = self.bundle()
        old = manifest["sourceCommit"]
        self.write("docs/change.md", "New candidate, same binary inputs\n")
        self.commit()
        self.assertNotEqual(old, self.command("rev-parse", "HEAD"))
        self.assertEqual(artifacts.validate_manifest(self.root, manifest, "ios-simulator")["sourceCommit"], old)

    def test_current_attempt_failure_does_not_relabel_previous_successful_build(self):
        manifest, _ = self.bundle()
        artifact = {"workflow_run": {"id": 123, "head_sha": manifest["sourceCommit"]}}
        github = artifacts.GitHub("masssi164/weave", "test-token")
        run = {"repository": {"full_name": "masssi164/weave"}, "head_repository": {"full_name": "masssi164/weave"},
               "head_sha": manifest["sourceCommit"], "event": "push", "head_branch": "dev",
               "path": ".github/workflows/build-artifacts.yml", "conclusion": "failure", "run_attempt": 2}
        job = {"name": "Artifacts / Build component (ios-simulator)", "conclusion": "success", "labels": ["macos-26"]}
        with patch.object(github, "json", return_value=run), patch.object(github, "pages", return_value=iter([job])) as pages:
            github.trusted_producer(artifact, manifest, "macos-26")
            self.assertIn("/attempts/1/jobs", pages.call_args.args[0])

    def test_completed_reusable_build_is_valid_while_qualification_is_still_running(self):
        manifest, _ = self.bundle()
        manifest["producer"]["workflowPath"] = ".github/workflows/live-stack-e2e.yml"
        manifest["producer"]["event"] = "workflow_dispatch"
        artifact = {"workflow_run": {"id": 123, "head_sha": manifest["sourceCommit"]}}
        github = artifacts.GitHub("masssi164/weave", "test-token")
        run = {"repository": {"full_name": "masssi164/weave"}, "head_repository": {"full_name": "masssi164/weave"},
               "head_sha": manifest["sourceCommit"], "event": "workflow_dispatch", "head_branch": "dev",
               "path": ".github/workflows/live-stack-e2e.yml", "status": "in_progress", "conclusion": None}
        job = {"name": "Hosted build artifacts / Build component (ios-simulator)",
               "conclusion": "success", "labels": ["macos-26"]}
        with patch.object(github, "json", return_value=run), patch.object(github, "pages", return_value=iter([job])):
            github.trusted_producer(artifact, manifest, "macos-26")
        for name in ("Build component (ios-simulator) extra", "Build component (ios-device)"):
            with patch.object(github, "json", return_value=run), \
                    patch.object(github, "pages", return_value=iter([{**job, "name": name}])), \
                    self.assertRaises(artifacts.ArtifactError):
                github.trusted_producer(artifact, manifest, "macos-26")

    def test_pr_fork_unknown_workflow_or_self_hosted_cache_is_rejected(self):
        manifest, _ = self.bundle()
        artifact = {"workflow_run": {"id": 123, "head_sha": manifest["sourceCommit"]}}
        github = artifacts.GitHub("masssi164/weave", "test-token")
        run = {"repository": {"full_name": "masssi164/weave"}, "head_repository": {"full_name": "masssi164/weave"},
               "head_sha": manifest["sourceCommit"], "event": "push", "head_branch": "dev",
               "path": ".github/workflows/build-artifacts.yml"}
        job = {"name": "Build component (ios-simulator)", "conclusion": "success", "labels": ["macos-26"]}
        variants = [({**run, "event": "pull_request"}, job),
                    ({**run, "head_repository": {"full_name": "other/fork"}}, job),
                    ({**run, "path": ".github/workflows/untrusted.yml"}, job),
                    (run, {**job, "labels": ["self-hosted", "macos-26"]}),
                    (run, {**job, "conclusion": "failure"})]
        for bad_run, bad_job in variants:
            with self.subTest(variant=bad_run, job=bad_job), patch.object(github, "json", return_value=bad_run), \
                    patch.object(github, "pages", return_value=iter([bad_job])), self.assertRaises(artifacts.ArtifactError):
                github.trusted_producer(artifact, manifest, "macos-26")

    def test_local_and_pull_request_builds_cannot_publish_trusted_manifest(self):
        with patch.dict(os.environ, {}, clear=True), self.assertRaisesRegex(artifacts.ArtifactError, "hosted-producer-required"):
            artifacts.publish_manifest(self.root, "ios-simulator", self.root, self.root / "unused")
        with patch.dict(os.environ, {"GITHUB_ACTIONS": "true", "RUNNER_ENVIRONMENT": "github-hosted",
                                    "GITHUB_REF": "refs/pull/2/merge", "GITHUB_EVENT_NAME": "pull_request"}, clear=True), \
                self.assertRaisesRegex(artifacts.ArtifactError, "protected-producer-context-required"):
            artifacts.publish_manifest(self.root, "ios-simulator", self.root, self.root / "unused")

    def test_source_guard_reports_escaped_paths_without_source_contents(self):
        filename = "client/lib/generated\n::error::unsafe.dart"
        self.write(filename, "original source\n")
        self.commit()
        self.write(filename, "sensitive build output must not be logged\n")
        self.command("add", filename)
        with self.assertRaises(artifacts.ArtifactError) as caught:
            artifacts.require_clean_source(self.root, "apple-ios-configuration")
        message = str(caught.exception)
        self.assertNotIn("\n", message)
        self.assertNotIn("sensitive build output", message)
        details = json.loads(message.removeprefix("producer-tracked-source-modified "))
        self.assertEqual(details, {"stage": "apple-ios-configuration", "paths": [filename], "totalChangedPaths": 1})

    def test_dirty_apple_configuration_stops_before_native_compilation(self):
        project = "client/ios/Runner.xcodeproj/project.pbxproj"
        self.write(project, "tracked project\n")
        self.commit()
        pins = artifacts.component_configuration(self.root, "ios-device")["toolchain"]
        commands = []

        def run(root, arguments, **kwargs):
            commands.append(arguments)
            if arguments == ["xcodebuild", "-version"]:
                return "Xcode " + pins["xcode"] + "\nBuild version " + pins["xcodeBuild"]
            if arguments[:2] == ["xcrun", "--sdk"]:
                return pins["sdk"]
            if arguments == ["pod", "--version"]:
                return pins["cocoapods"]
            if arguments[:2] == ["flutter", "--version"]:
                return json.dumps({"frameworkVersion": pins["flutter"], "channel": "stable"})
            if "--config-only" in arguments:
                self.write(project, "automatically migrated project\n")
            return ""

        with patch.object(hosted.platform, "system", return_value="Darwin"), \
                patch.object(hosted.platform, "machine", return_value="arm64"), \
                patch.object(hosted, "run", side_effect=run), \
                patch.dict(os.environ, {"GITHUB_RUN_NUMBER": "12"}), \
                self.assertRaisesRegex(artifacts.ArtifactError, "apple-ios-configuration"):
            hosted.apple(self.root, "ios-device", self.root / "payload", self.command("rev-parse", "HEAD"))
        builds = [command for command in commands if command[:3] == ["flutter", "build", "ios"]]
        self.assertEqual(len(builds), 1)
        self.assertIn("--config-only", builds[0])
        self.assertIn("--profile", builds[0])
        self.assertIn("--no-codesign", builds[0])
        self.assertFalse(any("build-for-testing" in command for command in commands))

    def test_publication_still_rejects_changed_tracked_source(self):
        source = self.command("rev-parse", "HEAD")
        self.write("client/lib/main.dart", "changed after native compilation\n")
        output = self.root / "unused"
        environment = {"GITHUB_ACTIONS": "true", "RUNNER_ENVIRONMENT": "github-hosted",
                       "GITHUB_REF": "refs/heads/dev", "GITHUB_EVENT_NAME": "push",
                       "GITHUB_REPOSITORY": "masssi164/weave", "GITHUB_SHA": source,
                       "GITHUB_WORKFLOW_REF": "masssi164/weave/.github/workflows/build-artifacts.yml@refs/heads/dev"}
        with patch.dict(os.environ, environment, clear=True), \
                self.assertRaisesRegex(artifacts.ArtifactError, "producer-tracked-source-modified.*publication"):
            artifacts.publish_manifest(self.root, "ios-device", self.root, output)
        self.assertFalse(output.exists())

    def test_configuration_only_cannot_compile_pack_or_publish(self):
        source = self.command("rev-parse", "HEAD")
        pins = artifacts.component_configuration(self.root, "ios-device")["toolchain"]
        commands = []

        def run(root, arguments, **kwargs):
            commands.append(arguments)
            if arguments == ["xcodebuild", "-version"]:
                return "Xcode " + pins["xcode"] + "\nBuild version " + pins["xcodeBuild"]
            if arguments[:2] == ["xcrun", "--sdk"]:
                return pins["sdk"]
            if arguments == ["pod", "--version"]:
                return pins["cocoapods"]
            if arguments[:2] == ["flutter", "--version"]:
                return json.dumps({"frameworkVersion": pins["flutter"], "channel": "stable"})
            return ""

        environment = {"GITHUB_ACTIONS": "true", "RUNNER_ENVIRONMENT": "github-hosted",
                       "GITHUB_SHA": source, "GITHUB_RUN_NUMBER": "12",
                       "GITHUB_REF": "refs/pull/42/merge", "GITHUB_EVENT_NAME": "pull_request"}
        output = self.root / "no-artifacts"
        arguments = ["build_artifacts_hosted.py", "--root", str(self.root), "--component", "ios-device",
                     "--output", str(output), "--configuration-only"]
        with patch("sys.argv", arguments), patch.dict(os.environ, environment, clear=True), \
                patch.object(hosted.platform, "system", return_value="Darwin"), \
                patch.object(hosted.platform, "machine", return_value="arm64"), \
                patch.object(hosted, "run", side_effect=run), \
                patch.object(hosted, "publish_manifest") as publish, \
                patch.object(artifacts, "package_payload") as package, \
                patch("sys.stdout", new_callable=io.StringIO) as stdout:
            self.assertEqual(hosted.main(), 0)
        builds = [command for command in commands if command[:3] == ["flutter", "build", "ios"]]
        self.assertEqual(len(builds), 1)
        self.assertIn("--config-only", builds[0])
        self.assertFalse(any("build-for-testing" in command for command in commands))
        publish.assert_not_called()
        package.assert_not_called()
        self.assertFalse(output.exists())
        self.assertIn("WEAVE_HOSTED_CONFIGURATION_RESULT component=ios-device status=passed", stdout.getvalue())
        self.assertNotIn("WEAVE_HOSTED_BUILD_RESULT", stdout.getvalue())

    def test_configuration_only_rejects_runtime_component(self):
        arguments = ["build_artifacts_hosted.py", "--root", str(self.root), "--component", "runtime-linux-amd64",
                     "--output", str(self.root / "unused"), "--configuration-only"]
        with patch("sys.argv", arguments), \
                patch.dict(os.environ, {"GITHUB_ACTIONS": "true", "RUNNER_ENVIRONMENT": "github-hosted"}, clear=True), \
                patch.object(hosted, "runtime") as runtime, \
                self.assertRaisesRegex(artifacts.ArtifactError, "configuration-only-apple-component-required"):
            hosted.main()
        runtime.assert_not_called()

    def test_unpinned_cocoapods_is_rejected_before_dependency_preparation(self):
        for component in ("ios-device", "ios-simulator", "macos-desktop"):
            with self.subTest(component=component):
                pins = artifacts.component_configuration(self.root, component)["toolchain"]
                commands = []

                def run(root, arguments, **kwargs):
                    commands.append(arguments)
                    if arguments == ["xcodebuild", "-version"]:
                        return "Xcode " + pins["xcode"] + "\nBuild version " + pins["xcodeBuild"]
                    if arguments[:2] == ["xcrun", "--sdk"]:
                        return pins["sdk"]
                    if arguments == ["pod", "--version"]:
                        return "1.16.2"
                    self.fail("Unexpected preparation command before CocoaPods validation")

                with patch.object(hosted.platform, "system", return_value="Darwin"), \
                        patch.object(hosted.platform, "machine", return_value="arm64"), \
                        patch.object(hosted, "run", side_effect=run), \
                        self.assertRaisesRegex(artifacts.ArtifactError, "pinned-cocoapods-unavailable"):
                    hosted.apple(self.root, component, self.root / "unused", self.command("rev-parse", "HEAD"),
                                 configuration_only=True)
                self.assertEqual(commands[-1], ["pod", "--version"])
                self.assertFalse(any(command[0] == "flutter" for command in commands))

    def downloaded_fixture(self):
        manifest, archive = self.bundle()
        zipped = Path(self.temporary.name) / "github.zip"
        with zipfile.ZipFile(zipped, "w") as output:
            output.writestr("manifest.json", json.dumps(manifest))
            output.write(archive, "payload.tar.gz")
        metadata = {"id": 987, "name": artifacts.artifact_name("ios-simulator", manifest["inputDigest"]),
                    "expired": False, "size_in_bytes": zipped.stat().st_size, "digest": artifacts.digest_file(zipped)}
        github = artifacts.GitHub("masssi164/weave", "test-token")
        return manifest, zipped, metadata, github

    def test_authenticated_download_checks_both_digests_and_materializes_once(self):
        manifest, zipped, metadata, github = self.downloaded_fixture()
        destination = Path(self.temporary.name) / "consumed"
        with patch.object(github, "download", side_effect=lambda identifier, output: shutil.copyfile(zipped, output)), \
                patch.object(github, "trusted_producer") as trust:
            result = artifacts.read_download(self.root, github, metadata, "ios-simulator", destination)
            self.assertEqual(result, manifest)
            trust.assert_called_once()
            # A repeated consumer verifies the existing payload instead of overwriting it.
            artifacts.read_download(self.root, github, metadata, "ios-simulator", destination)
            broken = {**metadata, "digest": "sha256:" + "f" * 64}
            with self.assertRaisesRegex(artifacts.ArtifactError, "github-artifact-digest-mismatch"):
                artifacts.read_download(self.root, github, broken, "ios-simulator")

    def test_untrusted_producer_never_extracts_downloaded_payload(self):
        _, zipped, metadata, github = self.downloaded_fixture()
        destination = Path(self.temporary.name) / "untrusted"
        with patch.object(github, "download", side_effect=lambda identifier, output: shutil.copyfile(zipped, output)), \
                patch.object(github, "trusted_producer", side_effect=artifacts.ArtifactError("untrusted")), \
                self.assertRaisesRegex(artifacts.ArtifactError, "untrusted"):
            artifacts.read_download(self.root, github, metadata, "ios-simulator", destination)
        self.assertFalse(destination.exists())

    def test_missing_or_expired_artifact_is_a_build_miss(self):
        _, _, metadata, github = self.downloaded_fixture()
        with patch.object(github, "pages", return_value=iter([{**metadata, "expired": True}])), \
                patch.object(github, "download") as download:
            self.assertIsNone(artifacts.find_artifact(self.root, github, "ios-simulator"))
            download.assert_not_called()

    def test_composition_cannot_claim_a_different_candidate(self):
        identity = artifacts.candidate_identity(self.root)
        composition = {"schemaVersion": "weave.candidate-artifacts/v1", **identity,
                       "candidateCommit": "0" * 40, "components": {"ios-simulator": {"artifactId": 1}}}
        github = artifacts.GitHub("masssi164/weave", "test-token")
        with patch.object(github, "json") as metadata, self.assertRaisesRegex(artifacts.ArtifactError, "composition-mismatch"):
            artifacts.consume(self.root, github, composition, self.root / "unused")
        metadata.assert_not_called()

    def test_selective_consumption_verifies_full_composition_but_downloads_only_requested_component(self):
        manifest, zipped, metadata, github = self.downloaded_fixture()
        entry = artifacts.composition_entry(metadata, manifest)
        device = copy.deepcopy(entry)
        definition = artifacts.component_configuration(self.root, "ios-device")
        device.update({"artifactId": 988, "inputDigest": artifacts.input_digest(self.root, "ios-device"),
                       "target": definition["target"], "buildMode": definition["buildMode"]})
        device["artifactName"] = artifacts.artifact_name("ios-device", device["inputDigest"])
        value = {"schemaVersion": "weave.candidate-artifacts/v1", **artifacts.candidate_identity(self.root),
                 "scenarioDigest": None, "components": {"ios-simulator": entry, "ios-device": device}}
        value["compositionDigest"] = artifacts.composition_digest(value)
        self.assertIs(artifacts.validate_composition(self.root, value), value)
        with patch.object(github, "json", return_value=metadata) as query, \
                patch.object(github, "download", side_effect=lambda identifier, output: shutil.copyfile(zipped, output)), \
                patch.object(github, "trusted_producer"):
            destination = Path(self.temporary.name) / "selected"
            artifacts.consume(self.root, github, value, destination, components=["ios-simulator"])
            query.assert_called_once_with(github.prefix + "/actions/artifacts/987")
            self.assertFalse((destination / "ios-device").exists())
            value["components"]["ios-device"]["inputDigest"] = "sha256:" + "0" * 64
            value["compositionDigest"] = artifacts.composition_digest(value)
            with self.assertRaisesRegex(artifacts.ArtifactError, "component-metadata-mismatch"):
                artifacts.consume(self.root, github, value, destination, components=["ios-simulator"])

    def test_composition_digest_is_order_independent_and_detects_changed_scenario(self):
        manifest, _, metadata, _ = self.downloaded_fixture()
        value = {"schemaVersion": "weave.candidate-artifacts/v1", **artifacts.candidate_identity(self.root),
                 "scenarioDigest": artifacts.scenario_digest(self.root),
                 "components": {"ios-simulator": artifacts.composition_entry(metadata, manifest)}}
        value["compositionDigest"] = artifacts.composition_digest(value)
        self.assertEqual(artifacts.composition_digest(dict(reversed(list(value.items())))), value["compositionDigest"])
        value["scenarioDigest"] = "sha256:" + "b" * 64
        with self.assertRaisesRegex(artifacts.ArtifactError, "composition-mismatch"):
            artifacts.validate_composition(self.root, value)

    def test_scenario_change_requires_fresh_composition_without_rebuilding_binary(self):
        before = self.inputs()
        scenario = artifacts.scenario_digest(self.root)
        self.write(artifacts.SCENARIO, json.dumps({"schemaVersion": "fixture/v1", "members": ["a", "b", "c"]}))
        self.commit()
        self.assertEqual(self.inputs(), before)
        self.assertNotEqual(artifacts.scenario_digest(self.root), scenario)

    def test_scenario_digest_preserves_unicode_and_ignores_json_formatting(self):
        scenario = {"schemaVersion": "fixture/v1", "members": ["Émile", "Béa"], "message": "Grüße"}
        canonical = json.dumps(scenario, sort_keys=True, separators=(",", ":"), ensure_ascii=False).encode("utf-8")
        self.write(artifacts.SCENARIO, json.dumps(scenario, indent=2, ensure_ascii=True))
        self.commit()
        self.assertEqual(artifacts.scenario_digest(self.root), artifacts.digest_bytes(canonical))
        self.write(artifacts.SCENARIO, json.dumps(dict(reversed(list(scenario.items()))), ensure_ascii=False))
        self.commit()
        self.assertEqual(artifacts.scenario_digest(self.root), artifacts.digest_bytes(canonical))


def simulator_executable(entitlements: bytes | None, der: bytes | None = None, *, cpu: int = 0x0100000C) -> bytes:
    sections = [(name, value) for name, value in ((b"__entitlements", entitlements), (b"__ents_der", der)) if value is not None]
    command_size = 72 + 80 * len(sections)
    offset = 32 + command_size
    command = struct.pack("<II16sQQQQiiII", 0x19, command_size, b"__TEXT", 0, 0, 0, 0, 7, 5, len(sections), 0)
    contents = b""
    for name, value in sections:
        command += struct.pack("<16s16sQQIIIIIIII", name, b"__TEXT", 0, len(value), offset, 0, 0, 0, 0, 0, 0, 0)
        contents += value
        offset += len(value)
    return struct.pack("<8I", 0xFEEDFACF, cpu, 0, 2, 1, command_size, 0, 0) + command + contents


class SimulatorSigning(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.client = self.root / "client"
        self.app = self.client / "build/ios/iphonesimulator/Runner.app"
        self.app.mkdir(parents=True)
        (self.root / "tools").mkdir()
        (self.root / artifacts.CONFIG).write_text((artifacts.ROOT / artifacts.CONFIG).read_text())
        self.definitions = json.loads((self.root / artifacts.CONFIG).read_text())["components"]
        self.entitlements_directory = self.client / "ios/Runner"
        self.entitlements_directory.mkdir(parents=True)
        self.requested = {"keychain-access-groups": ["$(AppIdentifierPrefix)com.massimotter.weave"]}
        for filename in ("Runner.entitlements", "RunnerDevelopment.entitlements"):
            (self.entitlements_directory / filename).write_bytes(plistlib.dumps(self.requested))
        self.info = {"CFBundleIdentifier": "com.massimotter.weave", "CFBundleExecutable": "Runner"}
        (self.app / "Info.plist").write_bytes(plistlib.dumps(self.info))
        self.intermediates = self.root / "DerivedData/Runner.build"
        self.intermediates.mkdir(parents=True)
        self.expected_path = self.intermediates / "Runner.app-Simulated.xcent"
        self.der = b"fixture DER bytes"
        self.expected = {"application-identifier": "EXISTING42.com.massimotter.weave",
                         "keychain-access-groups": ["EXISTING42.com.massimotter.weave"]}
        self.settings = {"PRODUCT_BUNDLE_IDENTIFIER": self.info["CFBundleIdentifier"], "FULL_PRODUCT_NAME": "Runner.app",
            "SRCROOT": str(self.client / "ios"), "CODE_SIGN_ENTITLEMENTS": "Runner/Runner.entitlements",
            "TARGET_TEMP_DIR": str(self.intermediates), "DEVELOPMENT_TEAM": "OTHERTEAM0"}
        self.commands = []
        self.signature_valid = True
        self.write_expected()

    def write_expected(self):
        self.expected_path.write_bytes(plistlib.dumps(self.expected))
        Path(str(self.expected_path) + ".der").write_bytes(self.der)
        (self.app / "Runner").write_bytes(simulator_executable(plistlib.dumps(self.expected), self.der))

    def run_command(self, root, arguments, **kwargs):
        self.commands.append(arguments)
        if arguments[0] == "codesign":
            self.assertEqual(arguments[1:6], ["--verify", "--deep", "--strict", "--all-architectures", arguments[-1]])
            if not self.signature_valid:
                raise subprocess.CalledProcessError(1, arguments)
            return ""
        if "-showBuildSettings" in arguments:
            self.assertIn("BUILD_DIR=" + str(self.client / "build/ios"), arguments)
            return json.dumps([{"target": "Runner", "buildSettings": self.settings}])
        pins = self.definitions["ios-simulator"]["toolchain"]
        if arguments == ["xcodebuild", "-version"]:
            return "Xcode " + pins["xcode"] + "\nBuild version " + pins["xcodeBuild"]
        if arguments[:2] == ["xcrun", "--sdk"]:
            return pins["sdk"]
        if arguments == ["pod", "--version"]:
            return pins["cocoapods"]
        if arguments[:2] == ["flutter", "--version"]:
            return json.dumps({"frameworkVersion": pins["flutter"], "channel": "stable"})
        if arguments[:3] == ["flutter", "build", "ios"] and "--simulator" not in arguments and "--config-only" not in arguments:
            device_app = self.client / "build/ios/iphoneos/Runner.app"
            device_app.mkdir(parents=True)
            (device_app / "Info.plist").write_bytes(plistlib.dumps(self.info))
        if "build-for-testing" in arguments:
            products = Path(arguments[arguments.index("-derivedDataPath") + 1]) / "Build/Products"
            products.mkdir(parents=True)
            (products / "Driver.xctestrun").write_bytes(plistlib.dumps({"TestBundlePath": str(products / "Driver.xctest")}))
        return ""

    def verify(self):
        with patch.object(hosted, "run", side_effect=self.run_command), patch("sys.stdout", new_callable=io.StringIO):
            hosted.verify_simulator_app(self.client, self.app, {})

    def test_valid_simulator_identity_preserves_generated_prefix_and_does_not_require_a_team(self):
        for prefix in ("EXISTING42.", "", "DIFFERENT."):
            with self.subTest(prefix=prefix):
                identifier = prefix + self.info["CFBundleIdentifier"]
                self.expected = {"application-identifier": identifier, "keychain-access-groups": [identifier]}
                self.write_expected()
                self.verify()
        self.expected.pop("application-identifier")
        self.write_expected()
        self.verify()

    def test_xml_only_is_valid_only_when_no_der_input_or_section_exists(self):
        Path(str(self.expected_path) + ".der").unlink()
        with self.assertRaisesRegex(artifacts.ArtifactError, "entitlements-mismatch"):
            self.verify()
        (self.app / "Runner").write_bytes(simulator_executable(plistlib.dumps(self.expected)))
        self.verify()

    def test_every_universal_executable_slice_must_have_the_expected_entitlements(self):
        arm = (self.app / "Runner").read_bytes()
        for x86_entitlements in (plistlib.dumps(self.expected), None):
            x86 = simulator_executable(x86_entitlements, self.der, cpu=0x01000007)
            binary = struct.pack(">II", 0xCAFEBABE, 2)
            binary += struct.pack(">5I", 0x01000007, 0, 48, len(x86), 0)
            binary += struct.pack(">5I", 0x0100000C, 0, 48 + len(x86), len(arm), 0)
            (self.app / "Runner").write_bytes(binary + x86 + arm)
            if x86_entitlements is None:
                with self.assertRaisesRegex(artifacts.ArtifactError, "entitlements-missing-or-malformed"):
                    self.verify()
            else:
                self.verify()

    def test_missing_malformed_or_mismatched_actual_entitlements_fail_closed(self):
        wrong_group = {**self.expected, "keychain-access-groups": ["OTHER.com.massimotter.weave"]}
        wrong_app = {**self.expected, "application-identifier": "EXISTING42.other.app"}
        for binary in (b"not Mach-O", simulator_executable(None), simulator_executable(b"not a plist", self.der),
                       simulator_executable(plistlib.dumps({}), self.der),
                       simulator_executable(plistlib.dumps(wrong_group), self.der),
                       simulator_executable(plistlib.dumps(wrong_app), self.der),
                       simulator_executable(plistlib.dumps(self.expected)),
                       simulator_executable(plistlib.dumps(self.expected), b"wrong DER"),
                       simulator_executable(plistlib.dumps(self.expected), self.der)[:-1]):
            with self.subTest(binary=binary[:16]):
                (self.app / "Runner").write_bytes(binary)
                with self.assertRaises(artifacts.ArtifactError):
                    self.verify()

    def test_section_and_architecture_identity_must_match_their_containers(self):
        arm = (self.app / "Runner").read_bytes()
        wrong_segment = bytearray(arm)
        wrong_segment[40:56] = b"__DATA".ljust(16, b"\0")
        binaries = [bytes(wrong_segment)]
        for cpu, subtype in ((0x01000007, 0), (0x0100000C, 1)):
            binaries.append(struct.pack(">7I", 0xCAFEBABE, 1, cpu, subtype, 28, len(arm), 0) + arm)
        for binary in binaries:
            with self.subTest(binary=binary[:16]):
                (self.app / "Runner").write_bytes(binary)
                with self.assertRaisesRegex(artifacts.ArtifactError, "entitlements-missing-or-malformed"):
                    hosted.simulator_entitlement_sections(self.app / "Runner")

    def test_expected_identity_and_keychain_configuration_must_be_present_and_match(self):
        for change in ({"keychain-access-groups": []}, {"keychain-access-groups": "not a list"},
                       {"keychain-access-groups": ["$(AppIdentifierPrefix)com.massimotter.weave"]},
                       {"keychain-access-groups": ["EXISTING42.other.app"]},
                       {"application-identifier": "EXISTING42.other.app"}):
            original = self.expected
            self.expected = {**original, **change}
            self.write_expected()
            with self.subTest(change=change), self.assertRaisesRegex(artifacts.ArtifactError, "signing-evidence-invalid"):
                self.verify()
            self.expected = original
        self.write_expected()
        self.settings["PRODUCT_BUNDLE_IDENTIFIER"] = "other.app"
        with self.assertRaisesRegex(artifacts.ArtifactError, "signing-evidence-invalid"):
            self.verify()
        self.settings["PRODUCT_BUNDLE_IDENTIFIER"] = self.info["CFBundleIdentifier"]
        self.expected_path.unlink()
        with self.assertRaisesRegex(artifacts.ArtifactError, "signing-evidence-invalid"):
            self.verify()

    def test_invalid_signature_stops_before_reading_entitlements(self):
        self.signature_valid = False
        with self.assertRaises(subprocess.CalledProcessError):
            self.verify()
        self.assertEqual(len(self.commands), 1)

    def test_actual_producer_signs_both_simulator_variants_and_keeps_device_unsigned(self):
        for component in ("ios-simulator", "ios-device"):
            self.commands.clear()
            payload = self.root / component
            with self.subTest(component=component), patch.object(hosted.platform, "system", return_value="Darwin"), \
                    patch.object(hosted.platform, "machine", return_value="arm64"), \
                    patch.object(hosted, "component_configuration", side_effect=lambda root, name: self.definitions[name]), \
                    patch.object(hosted, "run", side_effect=self.run_command), patch.dict(os.environ, {"GITHUB_RUN_NUMBER": "12"}), \
                    patch.object(hosted, "require_clean_source") as clean, patch("sys.stdout", new_callable=io.StringIO):
                hosted.apple(self.root, component, payload, "a" * 40)
            builds = [command for command in self.commands if command[:3] == ["flutter", "build", "ios"]]
            signatures = [command for command in self.commands if command[0] == "codesign"]
            if component == "ios-simulator":
                self.assertEqual(len(builds), 3)
                self.assertTrue(all("--no-codesign" not in command for command in builds))
                self.assertEqual([Path(command[-1]) for command in signatures],
                                 [payload / "ordinary/Runner.app", payload / "collaboration/Runner.app"])
                self.assertIn("apple-collaboration", [call.args[1] for call in clean.call_args_list])
            else:
                self.assertEqual(len(builds), 2)
                self.assertTrue(all("--no-codesign" in command and "--profile" in command for command in builds))
                self.assertFalse(signatures)
                self.assertEqual((payload / "ordinary/entitlements.plist").read_bytes(),
                                 (self.entitlements_directory / "RunnerDevelopment.entitlements").read_bytes())
            self.assertIn("apple-ordinary", [call.args[1] for call in clean.call_args_list])
            self.assertIn("apple-driver", [call.args[1] for call in clean.call_args_list])

    def test_failed_simulator_signature_prevents_publication(self):
        self.signature_valid = False
        arguments = ["build_artifacts_hosted.py", "--root", str(self.root), "--component", "ios-simulator",
                     "--output", str(self.root / "unpublished")]
        with patch("sys.argv", arguments), patch.object(hosted.platform, "system", return_value="Darwin"), \
                patch.object(hosted.platform, "machine", return_value="arm64"), patch.object(hosted, "run", side_effect=self.run_command), \
                patch.object(hosted, "component_configuration", side_effect=lambda root, name: self.definitions[name]), \
                patch.object(hosted, "git", return_value=b"a" * 40), patch.object(hosted, "require_clean_source"), \
                patch.object(hosted, "publish_manifest") as publish, \
                patch.dict(os.environ, {"GITHUB_ACTIONS": "true", "RUNNER_ENVIRONMENT": "github-hosted",
                                        "GITHUB_SHA": "a" * 40, "GITHUB_RUN_NUMBER": "12"}), \
                self.assertRaises(subprocess.CalledProcessError):
            hosted.main()
        publish.assert_not_called()
        self.assertTrue(any(command[0] == "codesign" for command in self.commands))
        self.assertFalse((self.root / "unpublished").exists())


class ArchiveSafety(unittest.TestCase):
    def archive(self, members):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        root = Path(temporary.name)
        path = root / "payload.tar.gz"
        with tarfile.open(path, "w:gz") as archive:
            for name, kind, target in members:
                item = tarfile.TarInfo(name)
                item.type = kind
                if kind == tarfile.REGTYPE:
                    item.size = len(target)
                    archive.addfile(item, io.BytesIO(target))
                else:
                    item.linkname = target
                    archive.addfile(item)
        return path, root / "extracted"

    def test_rejects_escape_duplicates_hardlinks_and_symlink_parent_writes(self):
        cases = [
            [("../outside", tarfile.REGTYPE, b"bad")],
            [("/absolute", tarfile.REGTYPE, b"bad")],
            [("a//b", tarfile.REGTYPE, b"alias")],
            [("a/./b", tarfile.REGTYPE, b"alias")],
            [("same", tarfile.REGTYPE, b"first"), ("same", tarfile.REGTYPE, b"second")],
            [("hard", tarfile.LNKTYPE, "target")],
            [("link", tarfile.SYMTYPE, "../../outside")],
            [("link", tarfile.SYMTYPE, "target"), ("link/overwrite", tarfile.REGTYPE, b"bad")],
            [("device", tarfile.CHRTYPE, "")],
        ]
        for case in cases:
            archive, destination = self.archive(case)
            with self.subTest(case=case), self.assertRaises(artifacts.ArtifactError):
                artifacts.extract_payload(archive, destination)
            self.assertFalse(destination.exists())


if __name__ == "__main__":
    unittest.main()
