#!/usr/bin/env python3
"""Validate Sprint 14 product-trust claim matrix evidence boundaries."""

from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MATRIX = ROOT / "docs" / "product-trust-provider-choice-claim-matrix.md"
README = ROOT / "README.md"
PORTABILITY_FIXTURES = ROOT / "specs" / "0006-portability-contract"

REQUIRED_EVIDENCE_CLASSES = [
    "Source-backed research evidence",
    "Architecture/spec projection evidence",
    "Fixture-based migration contract tests",
    "Live smoke test evidence",
    "Accessibility/manual UX evidence",
    "Operator runbook evidence",
    "Claim matrix entry",
]

REQUIRED_MEMBER_STATES = [
    "available",
    "disabled_by_policy",
    "not_configured",
    "degraded",
    "unavailable",
    "coming_later",
    "unsupported",
]

REQUIRED_MATRIX_REFERENCES = [
    "provider-portability-v2-chat-matrix-dry-run.json",
    "matrix-synapse-chat-migration-proof.json",
    "matrix-synapse-chat-lifecycle-fixture.json",
    "./gradlew portabilityContractCheck",
    "no-unaccounted-data-loss",
]

FORBIDDEN_UNQUALIFIED_CLAIMS = [
    "GDPR-proof",
    "Cloud-Act-proof",
    "guaranteed compliant",
    "legally sovereign",
    "compliant by default",
    "fully sovereign",
    "compliance certified",
    "public/customer-ready",
    "full accessibility",
    "broad Weaver availability",
]

SOVEREIGN_FOUNDATION = ROOT / "docs" / "sovereign-domain-mcp-weaver-foundation.md"

STALE_PENDING_PHRASES = [
    "Matrix fixtures still pending",
    "fixtures pending",
    "future fixture tests",
    "Coming_later for executable migration proof until tests/fixtures merge",
]


def fail(message: str) -> None:
    print(f"product-trust-claim-matrix-check: {message}", file=sys.stderr)
    raise SystemExit(1)


def require_contains(text: str, needle: str, label: str) -> None:
    if needle not in text:
        fail(f"{label} missing {needle}")


def main() -> None:
    if not MATRIX.exists():
        fail("missing docs/product-trust-provider-choice-claim-matrix.md")
    text = MATRIX.read_text(encoding="utf-8")
    for heading in ["## Evidence classes and member-impact states", "## Approved positioning", "## Claim matrix", "## Procurement-risk checklist", "## Source anchors"]:
        require_contains(text, heading, "claim matrix")
    for evidence_class in REQUIRED_EVIDENCE_CLASSES:
        require_contains(text, evidence_class, "claim matrix evidence classes")
    for state in REQUIRED_MEMBER_STATES:
        require_contains(text, f"`{state}`", "claim matrix member states")
    for reference in REQUIRED_MATRIX_REFERENCES:
        require_contains(text, reference, "Matrix migration claim evidence")
    for stale in STALE_PENDING_PHRASES:
        if stale in text:
            fail(f"claim matrix still carries stale pending phrase: {stale}")
    foundation = SOVEREIGN_FOUNDATION.read_text(encoding="utf-8") if SOVEREIGN_FOUNDATION.exists() else ""
    combined = text + "\n" + foundation
    for forbidden in FORBIDDEN_UNQUALIFIED_CLAIMS:
        if forbidden not in combined:
            fail(f"claim controls must explicitly forbid overclaim phrase: {forbidden}")
    for phrase in [
        "provider and jurisdiction exposure visible",
        "reduces dependency on single-vendor stacks",
        "Cloud-Act-proof",
        "#591",
        "model.chat",
        "write-like tools without a valid `ApprovalReceipt` fail closed",
    ]:
        if phrase not in combined:
            fail(f"sovereign foundation claim control missing phrase: {phrase}")

    lossless_pattern = re.compile(r"\|[^\n]*(lossless|Lossless)[^\n]*\|[^\n]*\*\*(Ready|Usable)", re.IGNORECASE)
    if lossless_pattern.search(text):
        fail("lossless migration must not be marked ready/usable")
    for required_phrase in [
        "does not claim lossless",
        "actual apply remains blocked",
        "forbidden to imply lossless E2EE migration",
    ]:
        require_contains(text, required_phrase, "provider migration claim boundary")

    if not README.exists():
        fail("missing README.md")
    readme = README.read_text(encoding="utf-8")
    require_contains(readme, "## Ready / Guarded / Future claim matrix", "README")
    require_contains(readme, "No unaccounted data loss is the portability promise", "README")
    require_contains(readme, "perfect lossless migration is not claimed", "README")

    for fixture in [
        "provider-portability-v2-chat-matrix-dry-run.json",
        "matrix-synapse-chat-migration-proof.json",
        "matrix-synapse-chat-lifecycle-fixture.json",
    ]:
        if not (PORTABILITY_FIXTURES / fixture).exists():
            fail(f"missing referenced Matrix portability fixture: {fixture}")

    print("product-trust-claim-matrix-check: ok")


if __name__ == "__main__":
    main()
