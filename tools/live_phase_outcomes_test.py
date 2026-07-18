#!/usr/bin/env python3
"""Regression tests for support-safe Live Stack phase outcomes."""

from __future__ import annotations

import unittest

from live_phase_outcomes import OutcomeError, build_evidence, parse_outcome


COMMIT = "a" * 40
OBSERVED_AT = "2026-07-18T12:30:00Z"


class LivePhaseOutcomesTest(unittest.TestCase):
    def test_records_independent_pass_and_failure_with_stable_signatures(self) -> None:
        evidence = build_evidence(
            candidate_commit=COMMIT,
            workflow_run_id="29628117267",
            run_index=2,
            observed_at=OBSERVED_AT,
            outcomes=(
                parse_outcome("single-user-appshell-e2ee|0|success"),
                parse_outcome("provider-persistence-exactly-once|1|failure"),
                parse_outcome("identity-cleanup|0|success"),
                parse_outcome("stack-teardown|0|success"),
            ),
        )

        self.assertEqual(evidence["contractVersion"], "live-phase-outcomes-v2")
        self.assertEqual(evidence["overallStatus"], "failed")
        self.assertTrue(evidence["supportSafe"])
        records = evidence["outcomes"]
        self.assertEqual(len(records), 4)
        self.assertEqual(records[1]["stableCategory"], "failed")
        self.assertEqual(
            records[1]["stableCode"],
            "WEAVE_PROVIDER_PERSISTENCE_EXACTLY_ONCE_FAILED",
        )
        self.assertRegex(records[1]["signatureSha256"], r"^[0-9a-f]{64}$")
        self.assertEqual(records[2]["status"], "passed")
        self.assertEqual(records[3]["status"], "passed")
        self.assertEqual({record["runIndex"] for record in records}, {2})

        repeated = build_evidence(
            candidate_commit=COMMIT,
            workflow_run_id="different-run",
            run_index=1,
            observed_at="2026-07-19T00:00:00+00:00",
            outcomes=(parse_outcome("provider-persistence-exactly-once|1|failure"),),
        )
        self.assertEqual(
            records[1]["signatureSha256"],
            repeated["outcomes"][0]["signatureSha256"],
        )

    def test_rejects_duplicate_phase_and_unbounded_or_unsafe_input(self) -> None:
        with self.assertRaises(OutcomeError):
            build_evidence(
                candidate_commit=COMMIT,
                workflow_run_id="run",
                run_index=1,
                observed_at=OBSERVED_AT,
                outcomes=(
                    parse_outcome("cleanup|0|success"),
                    parse_outcome("cleanup|1|failure"),
                ),
            )
        for value in (
            "room-$opaque|1|failure",
            "phase|999|failure",
            "phase|1|unknown",
            "phase|1",
        ):
            with self.subTest(value=value), self.assertRaises(OutcomeError):
                parse_outcome(value)

    def test_cancelled_or_skipped_phase_keeps_overall_verdict_red(self) -> None:
        evidence = build_evidence(
            candidate_commit=COMMIT,
            workflow_run_id="run",
            run_index=1,
            observed_at=OBSERVED_AT,
            outcomes=(
                parse_outcome("cleanup|2|skipped"),
                parse_outcome("teardown|3|cancelled"),
            ),
        )
        self.assertEqual(
            [record["status"] for record in evidence["outcomes"]],
            ["not_run", "cancelled"],
        )
        self.assertEqual(evidence["overallStatus"], "failed")

    def test_enclosing_step_failure_does_not_erase_a_passing_sibling(self) -> None:
        evidence = build_evidence(
            candidate_commit=COMMIT,
            workflow_run_id="run",
            run_index=1,
            observed_at=OBSERVED_AT,
            outcomes=(
                parse_outcome("single-user-appshell-e2ee|0|failure"),
                parse_outcome("three-identity-collaboration|1|failure"),
            ),
        )
        self.assertEqual(
            [record["status"] for record in evidence["outcomes"]],
            ["passed", "failed"],
        )
        self.assertEqual(evidence["overallStatus"], "failed")


if __name__ == "__main__":
    unittest.main()
