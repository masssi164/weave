#!/usr/bin/env python3
"""Validate release-trust claim control and accessibility evidence accounting."""

from __future__ import annotations

import json
import re
import sys
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[1]
A11Y_GATE = ROOT / "release" / "accessibility-gate.json"
A11Y_DOC = ROOT / "docs" / "accessibility-release-gate.md"
BLOCKER = ROOT / "docs" / "evidence" / "accessibility" / "sprint-18-manual-at-blocker.md"
CLAIM_MATRIX = ROOT / "docs" / "product-trust-provider-choice-claim-matrix.md"
RELEASE_NOTES = ROOT / "docs" / "release-notes" / "unreleased.md"
ADMIN_CONTRACT = ROOT / "docs" / "admin-suite-readiness-setup-contract.md"

REQUIRED_SPRINT18_FLOWS = {
    "member-workspace-loop",
    "admin-migration-apply-recovery",
    "admin-go-live-claim-control",
    "agent-runtime-control-admin-lifecycle",
}

REQUIRED_BLOCKER_PHRASES = [
    "historical accounting artifact",
    "#591",
    "closed_not_planned",
    "Current release promotion still requires current accessibility evidence",
    "Do not claim production provider migration apply",
    "Do not claim RC/prod readiness",
    "Do not claim broad Weaver availability",
    "contains no screenshots, raw logs, endpoint URLs, tokens, provider diagnostics",
]

REQUIRED_CLAIM_BOUNDARIES = [
    "Release claim-control evidence",
    "historical #591 blocker is closed",
    "production cutover remains unavailable",
    "support-bundle, audit, export/import, release notes, CI, and Live Stack references are support-safe evidence pointers",
]

UNSUPPORTED_READY_PATTERNS = [
    (re.compile(r"\b(full|complete|completed) accessibility (signoff|coverage|certification)\b", re.IGNORECASE), "unqualified accessibility completion"),
    (re.compile(r"\bproduction (provider )?(migration|cutover|apply) (is )?(ready|available|complete|completed)\b", re.IGNORECASE), "production migration/cutover overclaim"),
    (re.compile(r"\bbroad Weaver availability\b", re.IGNORECASE), "broad Weaver availability overclaim"),
    (re.compile(r"\bfinal release (is )?(ready|approved|complete|completed)\b", re.IGNORECASE), "final release overclaim"),
]

ALLOWED_CONTEXT_HINTS = (
    "do not claim",
    "must not claim",
    "avoid",
    "forbidden",
    "non-goal",
    "blocked",
    "remains unavailable",
)

SCAN_DOCS = [
    ROOT / "README.md",
    A11Y_DOC,
    CLAIM_MATRIX,
    RELEASE_NOTES,
    ADMIN_CONTRACT,
    ROOT / "docs" / "release-v0.1-dogfood-plan.md",
]


def fail(message: str) -> None:
    print(f"release-trust-claim-control-check: {message}", file=sys.stderr)
    raise SystemExit(1)


def read(path: Path) -> str:
    try:
        return path.read_text(encoding="utf-8")
    except FileNotFoundError:
        fail(f"missing {path.relative_to(ROOT)}")


def load_gate() -> dict[str, Any]:
    try:
        return json.loads(read(A11Y_GATE))
    except json.JSONDecodeError as error:
        fail(f"invalid JSON in {A11Y_GATE.relative_to(ROOT)}: {error}")


def line_allowed(line: str) -> bool:
    lowered = line.lower()
    return any(hint in lowered for hint in ALLOWED_CONTEXT_HINTS)


def main() -> None:
    gate = load_gate()
    flows = gate.get("criticalFlows")
    if not isinstance(flows, list):
        fail("accessibility gate criticalFlows must be a list")
    flow_ids = {flow.get("id") for flow in flows if isinstance(flow, dict)}
    missing = REQUIRED_SPRINT18_FLOWS - flow_ids
    if missing:
        fail("accessibility gate missing Sprint 18 flow(s): " + ", ".join(sorted(missing)))
    for flow in flows:
        if not isinstance(flow, dict) or flow.get("id") not in REQUIRED_SPRINT18_FLOWS:
            continue
        if flow.get("releaseBlocking") is not True:
            fail(f"Sprint 18 flow {flow.get('id')} must be releaseBlocking=true")
        evidence = flow.get("requiredEvidence")
        if not isinstance(evidence, list) or "manual-at" not in evidence:
            fail(f"Sprint 18 flow {flow.get('id')} must require manual-at evidence")
        blocker = flow.get("historicalBlocker")
        if not isinstance(blocker, dict) or blocker.get("issue") != "#591" or blocker.get("status") != "closed_not_planned":
            fail(f"Sprint 18 flow {flow.get('id')} must carry historicalBlocker issue #591 status closed_not_planned")

    blocker_text = read(BLOCKER)
    for flow_id in REQUIRED_SPRINT18_FLOWS:
        if flow_id not in blocker_text:
            fail(f"manual AT blocker missing flow {flow_id}")
    for phrase in REQUIRED_BLOCKER_PHRASES:
        if phrase not in blocker_text:
            fail(f"manual AT blocker missing required phrase: {phrase}")

    claim_text = read(CLAIM_MATRIX)
    for phrase in REQUIRED_CLAIM_BOUNDARIES:
        if phrase not in claim_text:
            fail(f"claim matrix missing Sprint 18 boundary: {phrase}")

    release_notes = read(RELEASE_NOTES)
    if "Accessibility and assistive-technology readiness remain evidence-gated" not in release_notes:
        fail("unreleased notes must mention current accessibility evidence-gated release posture")

    for path in SCAN_DOCS:
        text = read(path)
        for line_number, line in enumerate(text.splitlines(), start=1):
            for pattern, label in UNSUPPORTED_READY_PATTERNS:
                if pattern.search(line) and not line_allowed(line):
                    fail(f"{path.relative_to(ROOT)}:{line_number} contains {label}: {line.strip()}")

    print("release-trust-claim-control-check: ok")


if __name__ == "__main__":
    main()
