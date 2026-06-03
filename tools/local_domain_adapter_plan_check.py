#!/usr/bin/env python3
"""Validate Sprint 27 local domain-adapter deployable plan evidence for issue #664."""

from __future__ import annotations

import json
import re
import sys
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[1]
FIXTURE = ROOT / "release" / "provider-lab" / "local-domain-plan" / "deployable-domain-plan.fixture.json"
DOC = ROOT / "docs" / "evidence" / "local-domain-adapter-deployable-plan.md"
FEATURE = ROOT / "e2e" / "features" / "sprint_27_local_domain_adapter_plan.feature"
MAPPING = ROOT / "e2e" / "scenario_mappings.json"
RUNNER_FIXTURE = ROOT / "release" / "provider-lab" / "local-forgejo-runner-readiness" / "runner-readiness.fixture.json"

REQUIRED_DOMAIN_ORDER = [
    "server-backend",
    "infra-stack",
    "identity",
    "chat",
    "files",
    "calendar",
    "boards-tasks",
    "health-readiness",
]
REQUIRED_FAIL_CLOSED = {
    "unsupported_adapter",
    "missing_secret_ref",
    "missing_required_variable",
    "raw_secret_value_supplied",
    "loss_report_missing",
    "rollback_ref_missing",
    "runner_signal_missing",
    "admin_approval_missing",
}
FORBIDDEN_PATTERNS = [
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


def fail(message: str) -> None:
    print(f"local-domain-adapter-plan-check: {message}", file=sys.stderr)
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
            assert_support_safe(child, label, (*path, key))
        return
    if isinstance(value, list):
        for index, child in enumerate(value):
            assert_support_safe(child, label, (*path, str(index)))
        return
    if isinstance(value, str):
        for pattern in FORBIDDEN_PATTERNS:
            if pattern.search(value):
                fail(f"{label} contains forbidden support-unsafe pattern {pattern.pattern!r} at {'.'.join(path)}")


def assert_fragments(path: Path, fragments: list[str]) -> None:
    content = read(path)
    for fragment in fragments:
        if fragment not in content:
            fail(f"{path.relative_to(ROOT)} missing {fragment!r}")


def main() -> None:
    fixture = load(FIXTURE)
    if fixture.get("artifactKind") != "weave-local-domain-adapter-deployable-plan-v1" or fixture.get("issue") != 664:
        fail("fixture kind/issue mismatch")
    if fixture.get("providerKey") != "local-forgejo-actions" or fixture.get("workflowRef") != "weave-admin-setup-e2e":
        fail("provider/workflow mismatch")
    inputs = fixture.get("inputs", {})
    if inputs.get("runnerReadinessRef") != str(RUNNER_FIXTURE.relative_to(ROOT)):
        fail("fixture must depend on #662 runner readiness")
    domains = fixture.get("domains", [])
    if not isinstance(domains, list):
        fail("domains must be a list")
    by_key = {item.get("domainKey"): item for item in domains if isinstance(item, dict)}
    if list(by_key) != REQUIRED_DOMAIN_ORDER:
        fail(f"domain order mismatch: {list(by_key)}")
    for key, domain in by_key.items():
        if domain.get("selectionKind") not in {"existing_or_self_hosted", "self_hosted_default"}:
            fail(f"{key} has invalid selectionKind")
        for field in ["requiredSecretRefs", "requiredVariables", "readinessChecks", "rollbackRefs", "evidenceRefs"]:
            values = domain.get(field)
            if not isinstance(values, list) or not values or not all(isinstance(v, str) and v for v in values):
                fail(f"{key} missing non-empty {field}")
    if set(fixture.get("failClosedCases", [])) != REQUIRED_FAIL_CLOSED:
        fail("fail-closed cases mismatch")
    shape = fixture.get("generatedPlanShape", {})
    for name in ["containsSecretValues", "containsProviderUrls", "containsMemberContent"]:
        if shape.get(name) is not False:
            fail(f"generated plan shape must set {name}=false")
    if shape.get("deterministicOrdering") != "canonical-domain-matrix-order":
        fail("generated plan shape must require canonical domain matrix ordering")
    assert_support_safe(fixture, "domain-plan fixture")
    assert_fragments(DOC, ["Server/backend", "Infrastructure stack", "local-forgejo-actions", "Fail-closed cases"])
    assert_fragments(FEATURE, ["@sprint27-local-domain-adapter-plan", "server/backend", "boards/tasks", "fail closed before dispatch"])
    assert_fragments(MAPPING, ["@sprint27-local-domain-adapter-plan", "LOCAL_DOMAIN_ADAPTER_PLAN_PROOF", str(FIXTURE.relative_to(ROOT))])
    print("local-domain-adapter-plan-check: ok issue=664 domains=8 provider=local-forgejo-actions")
    print("LOCAL_DOMAIN_ADAPTER_PLAN_PROOF")


if __name__ == "__main__":
    main()
