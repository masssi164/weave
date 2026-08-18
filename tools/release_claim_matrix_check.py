#!/usr/bin/env python3
"""Validate README release claims stay aligned with provider reality vocabulary."""

from __future__ import annotations

import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
README = ROOT / "README.md"
CLAIM_MATRIX = ROOT / "docs" / "product-trust-provider-choice-claim-matrix.md"
MEETING_DECISION = ROOT / "docs" / "meeting-architecture-decision.md"
REGISTRY = ROOT / "specs" / "0004-domain-registry" / "canonical-domain-registry-v1.json"
README_REQUIRED_BOUNDARIES = [
    "active dogfood",
    "does not claim public production readiness",
    "perfect lossless migration",
    "unrestricted autonomous agents",
    "universal provider interchangeability",
    "The portability promise is no unaccounted data loss",
]
CLAIM_MATRIX_REQUIRED_CLAIMS = [
    "Provider-neutral control plane",
    "Consistent member UX across providers",
    "Provider-neutral replacement dry-run",
    "no-unaccounted-data-loss",
    "Workload-only Agent Runtime Control target",
    "unrestricted autonomous AI",
]
MEETING_REQUIRED_BOUNDARIES = [
    "LiveKit is the first replaceable southbound SFU adapter",
    "internal RTC Authorizer",
    "obsolete member `/api/calls` routes",
    "Do not claim `secure meetings`, `encrypted meetings`, or `end-to-end encrypted meetings`",
]
REQUIRED_REALITY_LEVELS = [
    "contract_only",
    "configured",
    "live_read",
    "live_write",
    "migration_dry_run",
    "migration_apply_ready",
    "rollback_ready",
    "release_ready",
]
FORBIDDEN_READY_CLAIMS = [
    "unrestricted autonomous AI. | Ready",
    "unrestricted autonomous AI | Ready",
    "production provider migration. | Ready",
    "lossless migration. | Ready",
]


def fail(message: str) -> None:
    print(f"release-claim-matrix-check: {message}", file=sys.stderr)
    raise SystemExit(1)


def main() -> None:
    if not README.exists():
        fail("missing README.md")
    text = README.read_text(encoding="utf-8")
    if "## What Is Guarded" not in text:
        fail("README missing compact guarded-claims section")
    if "## Release Evidence" not in text:
        fail("README missing release evidence pointer")
    for boundary in README_REQUIRED_BOUNDARIES:
        if boundary not in text:
            fail(f"README missing compact claim boundary: {boundary}")

    if not CLAIM_MATRIX.exists():
        fail("missing product trust claim matrix")
    claim_text = CLAIM_MATRIX.read_text(encoding="utf-8")
    for claim in CLAIM_MATRIX_REQUIRED_CLAIMS:
        if claim not in claim_text:
            fail(f"product trust claim matrix missing claim boundary: {claim}")
    for forbidden in FORBIDDEN_READY_CLAIMS:
        if forbidden in claim_text:
            fail(f"claim matrix overclaims guarded/future surface: {forbidden}")

    if not MEETING_DECISION.exists():
        fail("missing meeting architecture decision")
    meeting_text = MEETING_DECISION.read_text(encoding="utf-8")
    for boundary in MEETING_REQUIRED_BOUNDARIES:
        if boundary not in meeting_text:
            fail(f"meeting decision missing LiveKit boundary: {boundary}")

    if not REGISTRY.exists():
        fail("missing canonical domain registry fixture")
    registry = json.loads(REGISTRY.read_text(encoding="utf-8"))
    levels = registry.get("providerRealityLevels")
    if levels != REQUIRED_REALITY_LEVELS:
        fail(f"providerRealityLevels must equal {REQUIRED_REALITY_LEVELS}, got {levels}")
    for level in REQUIRED_REALITY_LEVELS:
        if level not in claim_text:
            fail(f"product trust claim matrix must mention provider reality level {level}")

    guarded_pattern = re.compile(r"provider (replacement|changes|switching).*?(guarded|dry-run|review)", re.IGNORECASE | re.DOTALL)
    if not guarded_pattern.search(text + "\n" + claim_text):
        fail("provider adapter replacement must remain guarded")

    print("release-claim-matrix-check: ok")


if __name__ == "__main__":
    main()
