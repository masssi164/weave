#!/usr/bin/env python3
"""Regression tests for relevant-input reuse, producer trust and archive consumption."""
from __future__ import annotations

import copy
import hashlib
import io
import json
import os
from pathlib import Path
import shutil
import subprocess
import tarfile
import tempfile
import unittest
from unittest.mock import patch
import zipfile

import build_artifacts as artifacts


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

    def test_docs_and_corpus_revision_do_not_rebuild_unchanged_binaries(self):
        before = self.inputs()
        self.write("docs/example.md", "Changed explanatory prose\n")
        self.write("specs/weave-specs.lock.json", json.dumps({"specCorpus": {"gitCommit": "b" * 40}}))
        self.commit()
        self.assertEqual(before, self.inputs())

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
