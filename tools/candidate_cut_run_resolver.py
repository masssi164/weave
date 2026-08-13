#!/usr/bin/env python3
"""Resolve one immutable Candidate Cut GitHub Actions run from API JSON."""

from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path
from typing import Any

WORKFLOW_PATH = ".github/workflows/candidate-images.yml"
SOURCE_SHA = re.compile(r"^[0-9a-f]{40}$")


class ResolutionError(ValueError):
    """Raised when no unambiguous, valid Candidate Cut can be selected."""


def _flatten_runs(payload: Any) -> list[dict[str, Any]]:
    if isinstance(payload, dict):
        workflow_runs = payload.get("workflow_runs")
        if isinstance(workflow_runs, list):
            return [run for run in workflow_runs if isinstance(run, dict)]
        return [payload]
    if isinstance(payload, list):
        runs: list[dict[str, Any]] = []
        for item in payload:
            runs.extend(_flatten_runs(item))
        return runs
    raise ResolutionError("GitHub run payload must be an object or array")


def _repository_name(run: dict[str, Any]) -> str | None:
    repository = run.get("repository")
    if isinstance(repository, dict):
        value = repository.get("full_name")
        return value if isinstance(value, str) else None
    return repository if isinstance(repository, str) else None


def _mismatches(
    run: dict[str, Any], *, source_sha: str, repository: str
) -> list[str]:
    expected = {
        "repository": repository,
        "path": WORKFLOW_PATH,
        "event": "workflow_dispatch",
        "head_branch": "dev",
        "head_sha": source_sha,
        "display_title": f"Candidate Cut {source_sha}",
        "conclusion": "success",
    }
    actual = {
        "repository": _repository_name(run),
        "path": run.get("path"),
        "event": run.get("event"),
        "head_branch": run.get("head_branch"),
        "head_sha": run.get("head_sha"),
        "display_title": run.get("display_title"),
        "conclusion": run.get("conclusion"),
    }
    mismatches = [key for key, value in expected.items() if actual[key] != value]
    run_id = run.get("id")
    if isinstance(run_id, bool) or not isinstance(run_id, int) or run_id <= 0:
        mismatches.append("id")
    return mismatches


def resolve_candidate_run(
    payload: Any,
    *,
    source_sha: str,
    repository: str,
    requested_run_id: int | None = None,
) -> int:
    if not SOURCE_SHA.fullmatch(source_sha):
        raise ResolutionError("source SHA must be a full lowercase Git object ID")
    if not re.fullmatch(r"[^/\s]+/[^/\s]+", repository):
        raise ResolutionError("repository must be in owner/name form")

    runs = _flatten_runs(payload)
    if requested_run_id is not None:
        selected = [run for run in runs if run.get("id") == requested_run_id]
        if len(selected) != 1:
            raise ResolutionError("requested run ID was not returned exactly once")
        mismatches = _mismatches(
            selected[0], source_sha=source_sha, repository=repository
        )
        if mismatches:
            raise ResolutionError(
                "requested Candidate Cut failed metadata validation: "
                + ", ".join(mismatches)
            )
        return requested_run_id

    matching = [
        run
        for run in runs
        if not _mismatches(run, source_sha=source_sha, repository=repository)
    ]
    if len(matching) != 1:
        raise ResolutionError(
            "automatic discovery requires exactly one successful exact-source "
            f"Candidate Cut; found {len(matching)}"
        )
    return int(matching[0]["id"])


def _load_payload(path: str) -> Any:
    if path == "-":
        return json.load(sys.stdin)
    with Path(path).open(encoding="utf-8") as stream:
        return json.load(stream)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", required=True, help="GitHub run JSON or '-' for stdin")
    parser.add_argument("--source-sha", required=True)
    parser.add_argument("--repository", required=True)
    parser.add_argument("--run-id", type=int)
    args = parser.parse_args()
    try:
        run_id = resolve_candidate_run(
            _load_payload(args.input),
            source_sha=args.source_sha,
            repository=args.repository,
            requested_run_id=args.run_id,
        )
    except (OSError, json.JSONDecodeError, ResolutionError) as error:
        print(f"candidate-cut-run-resolver: {error}", file=sys.stderr)
        return 1
    print(run_id)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
