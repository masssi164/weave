#!/usr/bin/env python3
"""Validate Sprint 32 IDM/RBAC readiness negative-path fixtures."""
from __future__ import annotations

import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
FIXTURE = ROOT / "tools" / "fixtures" / "admin_readiness" / "idm_rbac_readiness_negative_paths.json"
REQUIRED_CASES = {"issuer_unreachable", "role_mapping_missing", "group_claim_ambiguous"}


def main() -> int:
    data = json.loads(FIXTURE.read_text(encoding="utf-8"))
    assert data["artifactKind"] == "weave-idm-rbac-readiness-negative-paths-v1"
    assert data["issue"] == 792
    cases = data["cases"]
    assert {case["id"] for case in cases} == REQUIRED_CASES
    for case in cases:
        assert case["state"] in {"not_ready", "policy_blocked", "needs_admin_action"}
        assert case["adminMessage"]
        assert case["memberMessage"]
        assert case["expectedActions"]
        combined = json.dumps(case)
        for fragment in data["forbiddenEvidenceFragments"]:
            assert fragment not in combined
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
