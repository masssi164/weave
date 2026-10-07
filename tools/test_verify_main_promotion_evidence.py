#!/usr/bin/env python3
"""Offline negatives for the protected main promotion evidence gate."""

import unittest

from tools.verify_main_promotion_evidence import (
    deployed_run,
    owner_human_pass,
    parse_human_comment,
    required_jobs_passed,
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


if __name__ == "__main__":
    unittest.main()
