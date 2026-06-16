#!/usr/bin/env python3
"""Validate the RuntimeProfile durable revocation contract fixture."""
from __future__ import annotations

import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
FIXTURE = ROOT / "tools" / "fixtures" / "security" / "runtime_profile_revocation_contract.json"
REQUIRED_BEHAVIOR = {
    "revocation_is_durable_across_service_restart",
    "revoked_profile_fails_closed_before_runtime_provisioning",
    "revocation_event_is_audit_linked",
    "revocation_decision_uses_signed_profile_identity",
    "support_evidence_excludes_secrets_and_raw_provider_payloads",
}
RECORD_FIELDS = {"runtimeProfileId", "profileSignatureId", "revokedAt", "revokedBy", "reason", "auditEventRef"}


def main() -> int:
    data = json.loads(FIXTURE.read_text(encoding="utf-8"))
    assert data["artifactKind"] == "weave-runtime-profile-revocation-contract-v1"
    assert data["issue"] == 795
    assert data["dependsOn"] == [794]
    assert set(data["requiredBehavior"]) == REQUIRED_BEHAVIOR
    assert set(data["revocationRecord"].keys()) == RECORD_FIELDS
    assert data["requiredFailureState"] == "policy_blocked"
    serialized_record = json.dumps(data["revocationRecord"])
    for fragment in data["forbiddenResponseFragments"]:
        assert fragment not in serialized_record
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
