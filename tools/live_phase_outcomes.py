#!/usr/bin/env python3
"""Build support-safe, independently attributable Live Stack phase outcomes."""

from __future__ import annotations

import argparse
import hashlib
import json
import re
from datetime import datetime, timezone
from pathlib import Path
from typing import Iterable


CONTRACT_VERSION = "live-phase-outcomes-v2"
STEP_OUTCOMES = frozenset(("success", "failure", "skipped", "cancelled"))
PHASE_PATTERN = re.compile(r"^[a-z][a-z0-9-]{1,63}$")
COMMIT_PATTERN = re.compile(r"^[0-9a-f]{40}$")


class OutcomeError(ValueError):
    """Raised when an outcome cannot satisfy the support-safe contract."""


def _parse_observed_at(value: str) -> str:
    try:
        parsed = datetime.fromisoformat(value.replace("Z", "+00:00"))
    except ValueError as error:
        raise OutcomeError("observed-at must be an ISO-8601 timestamp") from error
    if parsed.tzinfo is None:
        raise OutcomeError("observed-at must include a timezone")
    return parsed.astimezone(timezone.utc).isoformat().replace("+00:00", "Z")


def _category(exit_status: int, step_outcome: str) -> str:
    if step_outcome == "cancelled":
        return "cancelled"
    if step_outcome == "skipped":
        return "not_run"
    # Several independently measured phases run inside one always-run workflow
    # step. A later phase can make that enclosing step fail without changing an
    # earlier phase's recorded result, so the per-phase exit status is the
    # authoritative verdict once the step actually ran.
    if exit_status == 0:
        return "passed"
    return "failed"


def _stable_code(phase: str, category: str) -> str:
    return f"WEAVE_{phase.replace('-', '_').upper()}_{category.upper()}"


def _signature(phase: str, exit_status: int, step_outcome: str, category: str, code: str) -> str:
    material = "\0".join(
        (CONTRACT_VERSION, phase, str(exit_status), step_outcome, category, code)
    )
    return hashlib.sha256(material.encode("utf-8")).hexdigest()


def parse_outcome(value: str) -> tuple[str, int, str]:
    parts = value.split("|")
    if len(parts) != 3:
        raise OutcomeError("outcome must use phase|exit-status|step-outcome")
    phase, raw_status, step_outcome = parts
    if not PHASE_PATTERN.fullmatch(phase):
        raise OutcomeError(f"invalid phase: {phase!r}")
    try:
        exit_status = int(raw_status)
    except ValueError as error:
        raise OutcomeError(f"invalid exit status for {phase}") from error
    if not 0 <= exit_status <= 255:
        raise OutcomeError(f"exit status for {phase} must be in [0, 255]")
    if step_outcome not in STEP_OUTCOMES:
        raise OutcomeError(f"invalid step outcome for {phase}: {step_outcome!r}")
    return phase, exit_status, step_outcome


def build_evidence(
    *,
    candidate_commit: str,
    workflow_run_id: str,
    run_index: int,
    observed_at: str,
    outcomes: Iterable[tuple[str, int, str]],
) -> dict[str, object]:
    if not COMMIT_PATTERN.fullmatch(candidate_commit):
        raise OutcomeError("candidate commit must be a lowercase 40-character SHA")
    if not workflow_run_id or len(workflow_run_id) > 64:
        raise OutcomeError("workflow run id must be present and bounded")
    if run_index < 1:
        raise OutcomeError("run index must be positive")

    normalized_time = _parse_observed_at(observed_at)
    records: list[dict[str, object]] = []
    seen: set[str] = set()
    for phase, exit_status, step_outcome in outcomes:
        if phase in seen:
            raise OutcomeError(f"duplicate phase: {phase}")
        seen.add(phase)
        category = _category(exit_status, step_outcome)
        code = _stable_code(phase, category)
        records.append(
            {
                "candidateCommit": candidate_commit,
                "workflowRunId": workflow_run_id,
                "runIndex": run_index,
                "phase": phase,
                "status": category,
                "stableCategory": category,
                "stableCode": code,
                "signatureSha256": _signature(
                    phase, exit_status, step_outcome, category, code
                ),
                "observedAt": normalized_time,
            }
        )
    if not records:
        raise OutcomeError("at least one phase outcome is required")

    return {
        "contractVersion": CONTRACT_VERSION,
        "candidateCommit": candidate_commit,
        "workflowRunId": workflow_run_id,
        "runIndex": run_index,
        "outcomes": records,
        "overallStatus": "passed"
        if all(record["status"] == "passed" for record in records)
        else "failed",
        "supportSafe": True,
    }


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--candidate-commit", required=True)
    parser.add_argument("--workflow-run-id", required=True)
    parser.add_argument("--run-index", type=int, required=True)
    parser.add_argument("--observed-at", required=True)
    parser.add_argument("--outcome", action="append", default=[])
    args = parser.parse_args()

    evidence = build_evidence(
        candidate_commit=args.candidate_commit,
        workflow_run_id=args.workflow_run_id,
        run_index=args.run_index,
        observed_at=args.observed_at,
        outcomes=(parse_outcome(value) for value in args.outcome),
    )
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(evidence, indent=2) + "\n", encoding="utf-8")
    print(
        "LIVE_PHASE_OUTCOMES_RESULT "
        f"status={evidence['overallStatus']} outcomes={len(evidence['outcomes'])} "
        "supportSafe=true"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
