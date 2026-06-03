#!/usr/bin/env python3
"""Validate Sprint 27 local Forgejo runner readiness evidence for issue #662."""

from __future__ import annotations

import json
import re
import sys
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[1]
FIXTURE = ROOT / "release" / "provider-lab" / "local-forgejo-runner-readiness" / "runner-readiness.fixture.json"
DOC = ROOT / "docs" / "evidence" / "local-forgejo-runner-readiness.md"
FEATURE = ROOT / "e2e" / "features" / "sprint_27_local_forgejo_runner_readiness.feature"
MAPPING = ROOT / "e2e" / "scenario_mappings.json"
CORE = ROOT / "tools" / "weave-setup" / "internal" / "bootstrap" / "core.go"
TEST = ROOT / "tools" / "weave-setup" / "internal" / "bootstrap" / "core_test.go"
BOOTSTRAPPER_FIXTURE = ROOT / "release" / "provider-lab" / "local-cicd-bootstrapper" / "support-safe-plan.fixture.json"

FORBIDDEN_VALUE_PATTERNS = [
    re.compile(pattern, re.IGNORECASE)
    for pattern in [
        r"bearer\s+[a-z0-9._-]+",
        r"gh[pousr]_[a-z0-9_]{12,}",
        r"(token|secret|password|private[_-]?key)\s*[:=]\s*[^\s,}\"]+",
        r"https?://[^\s)\"]+",
        r"ssh://[^\s)\"]+",
        r"-----begin\s+(rsa|dsa|ec|openssh|private)\s+private\s+key-----",
    ]
]
FORBIDDEN_FIELD_NAMES = {
    "secretValue",
    "tokenValue",
    "rawCiLog",
    "rawProviderPayload",
    "credentialBearingUrl",
    "tenantUrl",
    "memberContent",
}
REQUIRED_STATES = {
    "runner_missing",
    "runner_registered",
    "runner_offline",
    "runner_secret_missing",
    "dispatch_allowed",
}


def fail(message: str) -> None:
    print(f"local-forgejo-runner-readiness-check: {message}", file=sys.stderr)
    raise SystemExit(1)


def read(path: Path) -> str:
    try:
        return path.read_text(encoding="utf-8")
    except FileNotFoundError:
        fail(f"missing {path.relative_to(ROOT)}")


def load(path: Path) -> dict[str, Any]:
    try:
        data = json.loads(read(path))
    except json.JSONDecodeError as error:
        fail(f"invalid JSON in {path.relative_to(ROOT)}: {error}")
    if not isinstance(data, dict):
        fail(f"{path.relative_to(ROOT)} must contain an object")
    return data


def assert_support_safe(value: Any, label: str, path: tuple[str, ...] = ()) -> None:
    if isinstance(value, dict):
        for key, child in value.items():
            child_path = (*path, key)
            if key in FORBIDDEN_FIELD_NAMES and child_path != ("forbiddenPersistence",):
                fail(f"{label} contains forbidden field {'.'.join(child_path)}")
            assert_support_safe(child, label, child_path)
        return
    if isinstance(value, list):
        for index, child in enumerate(value):
            assert_support_safe(child, label, (*path, str(index)))
        return
    if isinstance(value, str):
        for pattern in FORBIDDEN_VALUE_PATTERNS:
            if pattern.search(value):
                fail(f"{label} contains forbidden support-unsafe pattern {pattern.pattern!r} at {'.'.join(path)}")


def assert_fragments(path: Path, fragments: list[str]) -> None:
    content = read(path)
    for fragment in fragments:
        if fragment not in content:
            fail(f"{path.relative_to(ROOT)} missing {fragment!r}")


def main() -> None:
    fixture = load(FIXTURE)
    if fixture.get("artifactKind") != "weave-local-forgejo-runner-readiness-v1" or fixture.get("issue") != 662:
        fail("fixture kind/issue mismatch")
    if fixture.get("providerKey") != "local-forgejo-actions" or fixture.get("workflowRef") != "weave-admin-setup-e2e":
        fail("fixture provider/workflow mismatch")
    if fixture.get("sourceOfTruth") != "customer-owned Forgejo Actions runner plus SecretRef mechanism":
        fail("fixture must name customer-owned Forgejo runner/SecretRef source of truth")
    if "GitHub repository secrets" not in fixture.get("notSourceOfTruth", []):
        fail("fixture must state GitHub repository secrets are not source of truth")
    states = {item.get("code"): item for item in fixture.get("states", []) if isinstance(item, dict)}
    if set(states) != REQUIRED_STATES:
        fail(f"fixture readiness states mismatch: {sorted(states)}")
    if states["runner_missing"].get("dispatchAllowed") is not False:
        fail("runner_missing must block dispatch")
    if states["runner_registered"].get("nextState") != "dispatch_allowed_after_secret_refs_present_and_admin_approval":
        fail("runner_registered transition must still require SecretRefs and admin approval")
    if states["runner_offline"].get("dispatchAllowed") is not False:
        fail("runner_offline must block dispatch")
    if states["runner_secret_missing"].get("missingNamesDisplayed") != ["WEAVE_FORGEJO_TOKEN"]:
        fail("runner_secret_missing must display missing names only")
    if states["dispatch_allowed"].get("requiresAdminApproval") is not True:
        fail("dispatch_allowed still requires explicit admin approval")
    if fixture.get("currentLocalObservation", {}).get("state") != "runner_missing":
        fail("current local proof must remain runner_missing")
    forbidden_ops = fixture.get("approvalBoundary", {}).get("forbiddenWithoutExplicitApproval", [])
    for op in ["mutate ~/server", "register runner", "create secret", "dispatch live workflow"]:
        if op not in forbidden_ops:
            fail(f"approval boundary missing {op}")
    assert_support_safe(fixture, "runner-readiness fixture")
    assert_fragments(DOC, ["customer-owned Forgejo Actions runner", "runner_missing", "runner_registered", "dispatch_allowed", "Do not mutate `~/server`"])
    assert_fragments(FEATURE, ["@sprint27-local-forgejo-runner-readiness", "runner_missing", "runner_registered", "dispatch_allowed", "not GitHub repository secrets"])
    assert_fragments(MAPPING, ["@sprint27-local-forgejo-runner-readiness", "LOCAL_FORGEJO_RUNNER_READINESS_PROOF", str(FIXTURE.relative_to(ROOT))])
    assert_fragments(CORE, ["EvaluateRunnerReadiness", "RunnerMissing", "RunnerRegistered", "RunnerOffline", "ReadinessDispatchAllowed"])
    assert_fragments(TEST, ["TestEvaluateRunnerReadinessStates", "runner_secret_missing", "dispatch_allowed"])
    bootstrapper = load(BOOTSTRAPPER_FIXTURE)
    if bootstrapper.get("downstreamReadinessContractRef") != str(FIXTURE.relative_to(ROOT)):
        fail("bootstrapper fixture must point to runner readiness contract")
    print("local-forgejo-runner-readiness-check: ok issue=662 current=runner_missing future=dispatch_allowed")
    print("LOCAL_FORGEJO_RUNNER_READINESS_PROOF")


if __name__ == "__main__":
    main()
