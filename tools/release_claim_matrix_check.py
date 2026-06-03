#!/usr/bin/env python3
"""Validate README release claims stay aligned with provider reality vocabulary."""

from __future__ import annotations

import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
README = ROOT / "README.md"
REGISTRY = ROOT / "specs" / "0004-domain-registry" / "canonical-domain-registry-v1.json"
REQUIRED_CLAIMS = {
    "Weave is a monorepo product stack",
    "v0.1 is dogfood-production",
    "Members work in provider-neutral Weave domains",
    "Provider adapters are replaceable behind Weave-owned contracts",
    "No unaccounted data loss is the portability promise",
    "Calls/meetings use LiveKit readiness today",
    "Workspace/Admin Health is the support-safe readiness and diagnostics control plane",
    "Weaver is OpenClaw-derived, optional, per-user, governed, and disabled by default",
    "Autonomous agent/team writes are available in v0.1",
}
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
    "Autonomous agent/team writes are available in v0.1. | **Ready",
    "Weaver is OpenClaw-derived, optional, per-user, governed, and disabled by default. | **Ready",
]


def fail(message: str) -> None:
    print(f"release-claim-matrix-check: {message}", file=sys.stderr)
    raise SystemExit(1)


def main() -> None:
    if not README.exists():
        fail("missing README.md")
    text = README.read_text(encoding="utf-8")
    if "## Ready / Guarded / Future claim matrix" not in text:
        fail("README missing Ready / Guarded / Future claim matrix")
    if "v0.1 is dogfood-production, not preview" not in text:
        fail("README must keep the dogfood-production boundary")
    for claim in sorted(REQUIRED_CLAIMS):
        if claim not in text:
            fail(f"README claim matrix missing claim: {claim}")
    for forbidden in FORBIDDEN_READY_CLAIMS:
        if forbidden in text:
            fail(f"README overclaims guarded/future surface: {forbidden}")

    if not REGISTRY.exists():
        fail("missing canonical domain registry fixture")
    registry = json.loads(REGISTRY.read_text(encoding="utf-8"))
    levels = registry.get("providerRealityLevels")
    if levels != REQUIRED_REALITY_LEVELS:
        fail(f"providerRealityLevels must equal {REQUIRED_REALITY_LEVELS}, got {levels}")
    for level in REQUIRED_REALITY_LEVELS:
        if level not in text:
            fail(f"README claim matrix must mention provider reality level {level}")

    guarded_pattern = re.compile(r"\| Provider adapters are replaceable behind Weave-owned contracts\. \| \*\*Guarded\*\* \|")
    if not guarded_pattern.search(text):
        fail("provider adapter replaceability must remain Guarded in README claim matrix")

    print("release-claim-matrix-check: ok")


if __name__ == "__main__":
    main()
