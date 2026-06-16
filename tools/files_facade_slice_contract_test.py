#!/usr/bin/env python3
from __future__ import annotations
import json
from pathlib import Path
ROOT = Path(__file__).resolve().parents[1]
FIXTURE = ROOT / "tools" / "fixtures" / "domain_facades" / "files_facade_slice_contract.json"
REQUIRED = {"capability_checked_before_provider_access", "space_context_authorized_before_operation", "write_delete_operations_are_audited", "canonical_ids_and_mapping_refs_are_returned", "support_safe_errors_hide_provider_internals", "product_callers_do_not_use_provider_adapters_directly"}

def main() -> int:
    data = json.loads(FIXTURE.read_text(encoding="utf-8"))
    assert data["artifactKind"] == "weave-domain-facade-slice-contract-v1"
    assert data["issue"] == 789
    assert data["dependsOn"] == [788]
    assert set(data["requiredBoundary"]) == REQUIRED
    assert data["firstSliceOperations"]
    assert "policy_blocked" in data["requiredFailureStates"]
    serialized_ops = json.dumps(data["firstSliceOperations"])
    for fragment in data["forbiddenResponseFragments"]:
        assert fragment not in serialized_ops
    return 0

if __name__ == "__main__":
    raise SystemExit(main())
