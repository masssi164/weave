#!/usr/bin/env python3
"""Offline negatives for the protected main promotion evidence gate."""

import os
from pathlib import Path
import subprocess
import tempfile
import unittest

from tools.verify_main_promotion_evidence import (
    deployed_run,
    matches_promotion_pr,
    owner_human_pass,
    parse_human_comment,
    required_jobs_passed,
    resolve_merge_group,
)

SHA = "a" * 40
OTHER = "b" * 40
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
