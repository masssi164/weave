#!/usr/bin/env python3
"""Offline negatives for the protected main promotion evidence gate."""

import os
import copy
import io
import json
import zipfile
from unittest.mock import patch
from pathlib import Path
import subprocess
import tempfile
import unittest

from tools.verify_main_promotion_evidence import (
    artifact_json,
    attempt_artifact,
    native_qualification_passed,
    preparation_passed,
    qualified_run,
    prepared_run,
    deployed_run,
    matches_promotion_pr,
    owner_human_pass,
    parse_human_comment,
    required_jobs_passed,
    resolve_merge_group,
)

SHA = "a" * 40
OTHER = "b" * 40
TOOLCHAIN = {
    "frameworkVersion": "3.41.6", "channel": "stable", "frameworkRevision": "d" * 40,
    "engineRevision": "e" * 40, "dartSdkVersion": "3.11.4", "xcodeVersion": "26.5",
    "xcodeBuild": "17F42", "iosSimulatorSdkVersion": "26.5", "rustToolchain": "1.95.0",
    "rustCompilerVersion": "rustc 1.95.0 (1234567 2026-01-01)",
}
HUMAN = "\n".join(
    [
        "Weave human test v1",
        f"dogfood: {SHA}",
        "sign-in: passed",
        "chat: passed",
        "files: passed",
        "calendar: passed",
        "accessibility: passed",
        "revoke-regrant: passed",
        "identity-continuity: passed",
        "notes: No private content included.",
    ]
)


class MainPromotionEvidenceTest(unittest.TestCase):
    def test_native_qualification_requires_both_platforms_exact_identity_and_cleanup(self) -> None:
        valid = {
            "schemaVersion": "weave.native-qualification/v1", "candidateCommit": SHA,
            "sourceTree": OTHER, "specificationCommit": "c" * 40,
            "macos": {"status": "passed"}, "toolchain": TOOLCHAIN,
            "iosSimulator": {"status": "passed", "rustTarget": "aarch64-apple-ios-sim"},
            "cleanupComplete": True, "supportSafe": True, "containsSecretValues": False,
        }
        self.assertTrue(native_qualification_passed(valid, SHA, OTHER, "c" * 40))
        for key in valid:
            broken = copy.deepcopy(valid)
            del broken[key]
            self.assertFalse(native_qualification_passed(broken, SHA, OTHER, "c" * 40), key)
        for key, value in (("candidateCommit", OTHER), ("sourceTree", SHA),
                           ("specificationCommit", SHA), ("cleanupComplete", False)):
            self.assertFalse(native_qualification_passed({**valid, key: value}, SHA, OTHER, "c" * 40))
        for outcome in ("failed", "not-available", "skipped"):
            self.assertFalse(native_qualification_passed({**valid, "iosSimulator": {
                "status": outcome, "rustTarget": "aarch64-apple-ios-sim"}}, SHA, OTHER, "c" * 40))

    def test_native_toolchain_missing_malformed_or_inconsistent_fails_closed(self) -> None:
        from tools.verify_main_promotion_evidence import native_toolchain_recorded
        self.assertTrue(native_toolchain_recorded(TOOLCHAIN))
        for key in TOOLCHAIN:
            self.assertFalse(native_toolchain_recorded({k: v for k, v in TOOLCHAIN.items() if k != key}), key)
            self.assertFalse(native_toolchain_recorded({**TOOLCHAIN, key: True}), key)
        for invalid in (None, [], {**TOOLCHAIN, "engineRevision": "unknown"},
                        {**TOOLCHAIN, "rustCompilerVersion": "rustc 1.94.0 (1234567 2026-01-01)"}):
            self.assertFalse(native_toolchain_recorded(invalid))

    def test_preparation_requires_current_qualification_and_exact_installed_identity(self) -> None:
        valid = {"schemaVersion": "weave.hil-preparation/v1", "candidateCommit": SHA,
                 "sourceTree": OTHER, "specificationCommit": "c" * 40,
                 "qualificationRunId": 123, "qualificationRunAttempt": 1, "status": "passed", "install": "update_in_place",
                 "supportSafe": True, "containsSecretValues": False}
        self.assertTrue(preparation_passed(valid, SHA, OTHER, "c" * 40, 123, 1))
        self.assertFalse(preparation_passed(valid, SHA, OTHER, "c" * 40, 124, 1))
        for key in valid:
            broken = {name: value for name, value in valid.items() if name != key}
            self.assertFalse(preparation_passed(broken, SHA, OTHER, "c" * 40, 123, 1), key)
        self.assertFalse(preparation_passed({**valid, "status": "failed"}, SHA, OTHER, "c" * 40, 123, 1))

    def test_preparation_cannot_reuse_an_earlier_qualification_attempt(self) -> None:
        qualification = {"id": 123, "run_attempt": 2, "updated_at": "2026-10-07T13:00:00Z"}
        run = {"id": 456, "run_attempt": 1, "display_title": f"Prepare Human Test {SHA}",
               "event": "workflow_dispatch", "path": ".github/workflows/ios-dogfood.yml",
               "status": "completed", "conclusion": "success", "created_at": "2026-10-07T12:00:00Z",
               "run_started_at": "2026-10-07T12:00:00Z"}
        with patch("tools.verify_main_promotion_evidence.paged", return_value=[run]):
            with self.assertRaisesRegex(ValueError, "start after"):
                prepared_run("masssi164/weave", SHA, OTHER, "c" * 40, qualification, "fixture-token")
        valid = {"schemaVersion": "weave.hil-preparation/v1", "candidateCommit": SHA,
                 "sourceTree": OTHER, "specificationCommit": "c" * 40,
                 "qualificationRunId": 123, "qualificationRunAttempt": 1,
                 "status": "passed", "install": "update_in_place",
                 "supportSafe": True, "containsSecretValues": False}
        self.assertFalse(preparation_passed(valid, SHA, OTHER, "c" * 40, 123, 2))

    def test_rerun_selects_only_its_attempt_artifact(self) -> None:
        self.assertEqual(attempt_artifact("full-compose-e2e", SHA, {"run_attempt": 2}),
                         f"full-compose-e2e-{SHA}-attempt-2")
        for attempt in (None, 0, True, "2"):
            with self.assertRaises(ValueError):
                attempt_artifact("full-compose-e2e", SHA, {"run_attempt": attempt})

    def test_artifact_rejects_ambiguous_or_missing_native_proof(self) -> None:
        def archive(names: list[str]) -> bytes:
            result = io.BytesIO()
            with zipfile.ZipFile(result, "w") as bundle:
                for name in names:
                    bundle.writestr(name, json.dumps({"status": "passed"}))
            return result.getvalue()
        self.assertEqual(artifact_json(archive(["run/native-qualification.json"]),
                                       "native-qualification.json"), {"status": "passed"})
        for names in ([], ["one/native-qualification.json", "two/native-qualification.json"]):
            with self.assertRaises(ValueError):
                artifact_json(archive(names), "native-qualification.json")

    def test_qualification_validates_native_cleanup_and_deployment_artifacts(self) -> None:
        tree, spec = OTHER, "c" * 40
        run = {"id": 123, "run_attempt": 1, "head_sha": SHA, "head_branch": "dogfood", "event": "push",
               "created_at": "2026-10-07T11:00:00Z", "status": "completed", "conclusion": "success",
               "path": ".github/workflows/live-stack-e2e.yml"}
        jobs = [{"name": name, "conclusion": "success"} for name in
                ("Full Compose E2E", "Deploy dogfood with Compose")]
        native = {"schemaVersion": "weave.native-qualification/v1", "candidateCommit": SHA,
                  "sourceTree": tree, "specificationCommit": spec, "macos": {"status": "passed"}, "toolchain": TOOLCHAIN,
                  "iosSimulator": {"status": "passed", "rustTarget": "aarch64-apple-ios-sim"},
                  "cleanupComplete": True, "supportSafe": True, "containsSecretValues": False}
        teardown = {"candidateCommit": SHA, "dryRun": False, "ownershipLabelsVerified": True,
                    "remainingOwnedResources": 0, "remainingContainerCount": 0,
                    "remainingNetworkCount": 0, "remainingVolumeCount": 0}
        deployment = {"schemaVersion": "weave.dogfood-deployment/v1", "candidateCommit": SHA,
                      "sourceTree": tree, "specificationCommit": spec, "qualificationRunId": 123,
                      "status": "passed", "supportSafe": True, "containsSecretValues": False}
        def bundle(values: dict) -> bytes:
            result = io.BytesIO()
            with zipfile.ZipFile(result, "w") as archive:
                for name, value in values.items():
                    archive.writestr("run/" + name, json.dumps(value))
            return result.getvalue()
        def verify(native_value, teardown_value, deployment_value):
            with patch("tools.verify_main_promotion_evidence.api", return_value={"workflow_runs": [run]}), \
                 patch("tools.verify_main_promotion_evidence.paged", return_value=jobs), \
                 patch("tools.verify_main_promotion_evidence.download_artifact", side_effect=[
                     bundle({"native-qualification.json": native_value, "teardown-evidence.json": teardown_value}),
                     bundle({"deployment-result.json": deployment_value})]):
                return qualified_run("masssi164/weave", SHA, tree, spec, "fixture-token")
        self.assertEqual(verify(native, teardown, deployment), run)
        for native_value, teardown_value, deployment_value in (
            ({**native, "iosSimulator": {"status": "not-run"}}, teardown, deployment),
            (native, {**teardown, "candidateCommit": OTHER}, deployment),
            (native, {**teardown, "remainingOwnedResources": 1}, deployment),
            (native, {**teardown, "remainingOwnedResources": False}, deployment),
            (native, teardown, {**deployment, "specificationCommit": SHA}),
            (native, teardown, {**deployment, "qualificationRunId": 122}),
        ):
            with self.assertRaises(ValueError):
                verify(native_value, teardown_value, deployment_value)

    def test_unchanged_dogfood_can_promote_after_dev_advances(self) -> None:
        workflow = (Path(__file__).resolve().parents[1] / ".github/workflows/main-promotion-gate.yml").read_text()
        step = workflow.split("      - name: Verify exact qualified dogfood tree\n", 1)[1].split("      - name:", 1)[0]
        script = "\n".join(line[10:] for line in step.split("        run: |\n", 1)[1].splitlines())
        with tempfile.TemporaryDirectory() as directory:
            def git(*args: str) -> str:
                return subprocess.check_output(["git", "-C", directory, *args], text=True,
                                               stderr=subprocess.DEVNULL).strip()
            git("init", "--quiet")
            git("config", "user.name", "Disposable gate test")
            git("config", "user.email", "gate@example.invalid")
            git("-c", "commit.gpgsign=false", "commit", "--allow-empty", "-m", "Qualified dogfood")
            candidate = git("rev-parse", "HEAD")
            git("update-ref", "refs/remotes/origin/dogfood", candidate)
            Path(directory, "new-dev-work").write_text("later work")
            git("add", "new-dev-work")
            git("-c", "commit.gpgsign=false", "commit", "-m", "Dev advances")
            git("update-ref", "refs/remotes/origin/dev", git("rev-parse", "HEAD"))
            for sha, expected in ((candidate, 0), (git("rev-parse", "HEAD"), 1)):
                result = subprocess.run(["bash", "-c", script], cwd=directory,
                                        env={**os.environ, "CANDIDATE": sha}, capture_output=True, text=True)
                self.assertEqual(result.returncode, expected, result.stderr)

    def test_queue_commit_cannot_supply_missing_hotfix_head_ancestry(self) -> None:
        workflow = (Path(__file__).resolve().parents[1] / ".github/workflows/main-promotion-gate.yml").read_text()
        step = workflow.split("      - name: Verify hotfix ancestry\n", 1)[1].split("      - name:", 1)[0]
        script = "\n".join(line[10:] for line in step.split("        run: |\n", 1)[1].splitlines())
        with tempfile.TemporaryDirectory() as directory:
            def git(*args: str) -> str:
                return subprocess.check_output(["git", "-C", directory, *args], text=True,
                                               stderr=subprocess.DEVNULL).strip()
            git("init", "--quiet")
            git("config", "user.name", "Disposable gate test")
            git("config", "user.email", "gate@example.invalid")
            git("-c", "commit.gpgsign=false", "commit", "--allow-empty", "-m", "Old hotfix base")
            stale_head = git("rev-parse", "HEAD")
            git("-c", "commit.gpgsign=false", "commit", "--allow-empty", "-m", "Current main")
            current_main = git("rev-parse", "HEAD")
            git("update-ref", "refs/remotes/origin/main", current_main)
            git("-c", "commit.gpgsign=false", "commit", "--allow-empty", "-m", "Synthetic queue candidate")
            candidate = git("rev-parse", "HEAD")
            for head, expected in ((stale_head, 1), (current_main, 0)):
                result = subprocess.run(["bash", "-c", script], cwd=directory,
                                        env={**os.environ, "CANDIDATE": candidate, "EVIDENCE_SHA": head},
                                        capture_output=True, text=True)
                self.assertEqual(result.returncode, expected, result.stderr)

    def test_merge_group_binds_synthetic_head_to_current_same_repo_pr(self) -> None:
        repository = "masssi164/weave"
        pr = {
            "number": 42, "state": "OPEN", "baseRefName": "main",
            "baseRepository": {"nameWithOwner": repository},
            "headRepository": {"nameWithOwner": repository},
            "headRefOid": SHA, "headRefName": "dogfood",
        }
        entry = {"headCommit": {"oid": OTHER}, "pullRequest": pr}
        self.assertEqual(resolve_merge_group([entry], OTHER, repository), pr)
        invalid = [[], [entry, entry], [{**entry, "headCommit": {"oid": SHA}}]]
        for field, value in (
            ("state", "CLOSED"), ("baseRefName", "dev"),
            ("headRepository", {"nameWithOwner": "other/weave"}),
            ("baseRepository", {"nameWithOwner": "other/weave"}),
            ("headRefOid", "invalid"), ("number", 0),
        ):
            invalid.append([{**entry, "pullRequest": {**pr, field: value}}])
        for entries in invalid:
            with self.subTest(entries=entries), self.assertRaises(ValueError):
                resolve_merge_group(entries, OTHER, repository)

    def test_edited_failure_overrides_later_created_pass(self) -> None:
        passed = {
            "id": 2, "user": {"login": "masssi164"}, "author_association": "OWNER",
            "created_at": "2026-10-07T12:00:00Z", "updated_at": "2026-10-07T12:00:00Z",
            "body": HUMAN,
        }
        edited = {
            **passed, "id": 1, "created_at": "2026-10-07T10:00:00Z",
            "updated_at": "2026-10-07T13:00:00Z",
        }
        for body in (HUMAN.replace("chat: passed", "chat: failed"),
                     HUMAN.replace("files: passed\n", ""), HUMAN.replace(SHA, OTHER)):
            self.assertFalse(owner_human_pass([passed, {**edited, "body": body}],
                                             "masssi164", SHA, "2026-10-07T11:00:00Z"))
        self.assertTrue(owner_human_pass([edited], "masssi164", SHA, "2026-10-07T11:00:00Z"))
        self.assertFalse(owner_human_pass([edited], "masssi164", SHA, "2026-10-07T14:00:00Z"))

    def test_human_evidence_belongs_to_exact_open_main_promotion(self) -> None:
        repository = "masssi164/weave"
        pr = {
            "state": "open",
            "base": {"ref": "main", "repo": {"full_name": repository}},
            "head": {"sha": SHA, "repo": {"full_name": repository}},
        }
        self.assertTrue(matches_promotion_pr(pr, repository, SHA))
        self.assertFalse(matches_promotion_pr(pr, repository, OTHER))
        self.assertFalse(matches_promotion_pr({**pr, "state": "closed"}, repository, SHA))
        self.assertFalse(matches_promotion_pr({**pr, "base": {**pr["base"], "ref": "dev"}}, repository, SHA))
        self.assertFalse(matches_promotion_pr({**pr, "head": {**pr["head"],
            "repo": {"full_name": "other/weave"}}}, repository, SHA))

    def test_human_result_needs_exact_commit_and_every_pass(self) -> None:
        self.assertTrue(parse_human_comment(HUMAN, SHA))
        self.assertFalse(parse_human_comment(HUMAN, OTHER))
        self.assertFalse(parse_human_comment(HUMAN.replace("chat: passed", "chat: failed"), SHA))
        self.assertFalse(parse_human_comment(HUMAN.replace("files: passed\n", ""), SHA))
        self.assertFalse(parse_human_comment(HUMAN.replace("chat: passed", "chat: passed\nchat: passed"), SHA))

    def test_only_owner_comment_after_deployment_can_attest(self) -> None:
        comment = {
            "user": {"login": "masssi164"},
            "author_association": "OWNER",
            "created_at": "2026-10-07T12:00:00Z",
            "body": HUMAN,
        }
        self.assertTrue(owner_human_pass([comment], "masssi164", SHA, "2026-10-07T11:00:00Z"))
        self.assertFalse(owner_human_pass([comment], "masssi164", SHA, "2026-10-07T13:00:00Z"))
        self.assertFalse(owner_human_pass([comment], "another-owner", SHA, "2026-10-07T11:00:00Z"))
        self.assertFalse(owner_human_pass([{**comment, "author_association": "COLLABORATOR"}],
                                          "masssi164", SHA, "2026-10-07T11:00:00Z"))
        failed_later = {**comment, "id": 2, "created_at": "2026-10-07T12:01:00Z",
                        "body": HUMAN.replace("chat: passed", "chat: failed")}
        self.assertFalse(owner_human_pass([comment, failed_later], "masssi164", SHA,
                                          "2026-10-07T11:00:00Z"))
        wrong_sha_later = {**failed_later, "body": HUMAN.replace(SHA, OTHER)}
        self.assertFalse(owner_human_pass([comment, wrong_sha_later], "masssi164", SHA,
                                          "2026-10-07T11:00:00Z"))

    def test_latest_exact_push_and_both_jobs_must_pass(self) -> None:
        run = {
            "id": 1,
            "head_sha": SHA,
            "head_branch": "dogfood",
            "event": "push",
            "created_at": "2026-10-07T11:00:00Z",
            "status": "completed",
            "conclusion": "success",
        }
        self.assertEqual(deployed_run([run], SHA), run)
        self.assertIsNone(deployed_run([run], OTHER))
        self.assertIsNone(deployed_run([run, {**run, "id": 2,
            "created_at": "2026-10-07T12:00:00Z", "conclusion": "failure"}], SHA))
        self.assertFalse(required_jobs_passed([{"name": "Full Compose E2E", "conclusion": "success"}]))
        self.assertTrue(required_jobs_passed([
            {"name": "Full Compose E2E", "conclusion": "success"},
            {"name": "Deploy dogfood with Compose", "conclusion": "success"},
        ]))

    def test_duplicate_job_cannot_shadow_a_required_failure_or_skip(self) -> None:
        jobs = [
            {"name": "Full Compose E2E", "conclusion": "success"},
            {"name": "Deploy dogfood with Compose", "conclusion": "success"},
        ]
        for conclusion in ("failure", "skipped", "success"):
            with self.subTest(conclusion=conclusion):
                self.assertFalse(required_jobs_passed([
                    *jobs,
                    {"name": "Full Compose E2E", "conclusion": conclusion},
                ]))


if __name__ == "__main__":
    unittest.main()
