#!/usr/bin/env python3
"""Validate the member handoff-first onboarding contract fixture."""
from __future__ import annotations

import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
FIXTURE = ROOT / "tools" / "fixtures" / "client_onboarding" / "member_handoff_onboarding_contract.json"
REQUIRED_ENTRY_POINTS = {"invite_link", "join_url", "organization_auth_url", "signed_organization_manifest"}
REQUIRED_STATES = {"handoff_valid", "invite_expired", "organization_not_ready", "capability_disabled", "admin_action_required", "handoff_invalid"}


def main() -> int:
    data = json.loads(FIXTURE.read_text(encoding="utf-8"))
    assert data["artifactKind"] == "weave-member-handoff-onboarding-contract-v1"
    assert data["issue"] == 786
    assert set(data["acceptedEntryPoints"]) == REQUIRED_ENTRY_POINTS
    assert set(data["productStates"]) == REQUIRED_STATES
    assert set(data["supportSafeRecoveryCopy"].keys()) == REQUIRED_STATES - {"handoff_valid"}
    assert "no_image_only_state" in data["accessibilityRequirements"]
    copy = json.dumps(data["supportSafeRecoveryCopy"])
    for fragment in data["forbiddenMemberCopyFragments"]:
        assert fragment.lower() not in copy.lower()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
