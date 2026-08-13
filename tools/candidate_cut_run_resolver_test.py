#!/usr/bin/env python3
from __future__ import annotations

import copy
import unittest

from candidate_cut_run_resolver import ResolutionError, resolve_candidate_run


SOURCE = "b" * 40
REPOSITORY = "masssi164/weave"


def valid_run(run_id: int = 42) -> dict[str, object]:
    return {
        "id": run_id,
        "name": f"Candidate Cut {SOURCE}",
        "display_title": f"Candidate Cut {SOURCE}",
        "path": ".github/workflows/candidate-images.yml",
        "event": "workflow_dispatch",
        "head_branch": "dev",
        "head_sha": SOURCE,
        "conclusion": "success",
        "repository": {"full_name": REPOSITORY},
    }


class CandidateCutRunResolverTest(unittest.TestCase):
    def test_accepts_dynamic_github_run_name(self) -> None:
        self.assertEqual(
            resolve_candidate_run(
                {"workflow_runs": [valid_run()]},
                source_sha=SOURCE,
                repository=REPOSITORY,
            ),
            42,
        )

    def test_explicit_and_automatic_paths_use_the_same_contract(self) -> None:
        run = valid_run(73)
        self.assertEqual(
            resolve_candidate_run(
                run,
                source_sha=SOURCE,
                repository=REPOSITORY,
                requested_run_id=73,
            ),
            73,
        )
        broken = copy.deepcopy(run)
        broken["head_sha"] = "c" * 40
        with self.assertRaisesRegex(ResolutionError, "head_sha"):
            resolve_candidate_run(
                broken,
                source_sha=SOURCE,
                repository=REPOSITORY,
                requested_run_id=73,
            )

    def test_rejects_each_required_metadata_mismatch(self) -> None:
        changes = {
            "path": ".github/workflows/other.yml",
            "event": "push",
            "head_branch": "main",
            "head_sha": "c" * 40,
            "display_title": "Candidate Cut stale",
            "conclusion": "failure",
            "id": "42",
            "repository": {"full_name": "other/weave"},
        }
        for key, value in changes.items():
            with self.subTest(key=key):
                run = valid_run()
                run[key] = value
                with self.assertRaises(ResolutionError):
                    resolve_candidate_run(
                        run,
                        source_sha=SOURCE,
                        repository=REPOSITORY,
                        requested_run_id=42,
                    )

    def test_rejects_ambiguous_automatic_selection(self) -> None:
        payload = [{"workflow_runs": [valid_run(1)]}, {"workflow_runs": [valid_run(2)]}]
        with self.assertRaisesRegex(ResolutionError, "found 2"):
            resolve_candidate_run(
                payload, source_sha=SOURCE, repository=REPOSITORY
            )

    def test_rejects_missing_automatic_selection(self) -> None:
        run = valid_run()
        run["conclusion"] = "failure"
        with self.assertRaisesRegex(ResolutionError, "found 0"):
            resolve_candidate_run(
                {"workflow_runs": [run]},
                source_sha=SOURCE,
                repository=REPOSITORY,
            )


if __name__ == "__main__":
    unittest.main()
