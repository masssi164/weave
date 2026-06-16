#!/usr/bin/env python3
"""Validate Sprint 32 release evidence freshness requirements."""
from __future__ import annotations

import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
FIXTURE = ROOT / "tools" / "fixtures" / "release_evidence" / "sprint32_freshness_requirements.json"
REQUIRED_IDS = {"live_stack_e2e", "backup_restore_posture", "release_claim_hygiene"}


def main() -> int:
    data = json.loads(FIXTURE.read_text(encoding="utf-8"))
    assert data["artifactKind"] == "weave-sprint32-release-evidence-freshness-v1"
    assert data["issue"] == 796
    evidence = data["requiredEvidence"]
    assert {item["id"] for item in evidence} == REQUIRED_IDS
    for item in evidence:
        assert item["mustBeSupportSafe"] is True
        assert item["maximumAgeHours"] <= 72
        assert item["claimBoundary"]
    serialized = json.dumps(data).lower()
    for fragment in data["forbiddenEvidenceFragments"]:
        assert fragment.lower() not in serialized.replace(fragment.lower(), "")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
