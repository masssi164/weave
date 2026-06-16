#!/usr/bin/env python3
"""Validate the RuntimeProfile keyed-signing contract fixture."""
from __future__ import annotations

import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
FIXTURE = ROOT / "tools" / "fixtures" / "security" / "runtime_profile_signing_contract.json"
REJECTED = {"hash_only_marker", "unsigned_profile_payload", "raw_key_material_in_response"}


def main() -> int:
    data = json.loads(FIXTURE.read_text(encoding="utf-8"))
    assert data["artifactKind"] == "weave-runtime-profile-signing-contract-v1"
    assert data["issue"] == 794
    accepted = data["acceptedEvidence"]
    assert accepted["signatureEnvelope"] == "required"
    assert accepted["profileHash"] == "content_address_only_not_authenticity"
    assert accepted["supportSafeAudit"] == "required"
    assert {item["id"] for item in data["rejectedEvidence"]} == REJECTED
    assert data["requiredFailureState"] == "policy_blocked"
    serialized_rejections = json.dumps(data["rejectedEvidence"])
    for fragment in data["forbiddenResponseFragments"]:
        assert fragment not in serialized_rejections
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
