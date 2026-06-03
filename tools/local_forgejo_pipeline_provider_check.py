#!/usr/bin/env python3
"""Validate Sprint 27 local Forgejo PipelineProvider evidence for issue #663."""

from __future__ import annotations

import json
import re
import sys
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[1]
FIXTURE = ROOT / "release" / "provider-lab" / "admin-cicd" / "local-forgejo-pipeline-provider.fixture.json"
DOC = ROOT / "docs" / "evidence" / "local-forgejo-pipeline-provider.md"
FEATURE = ROOT / "e2e" / "features" / "sprint_27_local_forgejo_pipeline_provider.feature"
MAPPING = ROOT / "e2e" / "scenario_mappings.json"
PROVIDER = ROOT / "server" / "src" / "main" / "java" / "com" / "massimotter" / "weave" / "backend" / "cicd" / "LocalForgejoPipelineProvider.java"
PORT = ROOT / "server" / "src" / "main" / "java" / "com" / "massimotter" / "weave" / "backend" / "cicd" / "PipelineProvider.java"
TEST = ROOT / "server" / "src" / "test" / "java" / "com" / "massimotter" / "weave" / "backend" / "cicd" / "LocalForgejoPipelineProviderTest.java"

REQUIRED_STATES = {
    "provider_discovery",
    "ci_cd_registration",
    "domain_selection",
    "adapter_question",
    "preflight",
    "admin_approval",
    "trigger_requested",
    "run_observing",
    "evidence_complete",
    "blocked",
    "failure",
}
REQUIRED_TRANSITIONS = {"queued", "running", "evidence_complete", "failed", "cancelled", "timed_out", "rate_limited", "unknown"}
REQUIRED_FAIL_CLOSED = {"runner_missing", "runner_offline", "runner_secret_missing", "approval_missing", "unknown_status_timeout", "rate_limit_exhausted", "raw_value_supplied"}
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
FORBIDDEN_FIELD_NAMES = {
    "secretValue",
    "tokenValue",
    "rawCiLog",
    "rawProviderPayload",
    "credentialBearingUrl",
    "tenantUrl",
    "memberContent",
    "runnerRegistrationToken",
}


def fail(message: str) -> None:
    print(f"local-forgejo-pipeline-provider-check: {message}", file=sys.stderr)
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
    if fixture.get("artifactKind") != "weave-local-forgejo-pipeline-provider-v1" or fixture.get("issue") != 663:
        fail("fixture kind/issue mismatch")
    if fixture.get("providerKey") != "local-forgejo-actions" or fixture.get("workflowRef") != "weave-admin-setup-e2e":
        fail("provider/workflow mismatch")
    if fixture.get("backendOwnedAbstraction") != "com.massimotter.weave.backend.cicd.PipelineProvider":
        fail("fixture must name backend-owned PipelineProvider abstraction")
    if set(fixture.get("setupStates", [])) != REQUIRED_STATES:
        fail("setup state coverage mismatch")
    if set(fixture.get("statusTransitions", [])) != REQUIRED_TRANSITIONS:
        fail("status transition coverage mismatch")
    if set(fixture.get("failClosedCases", [])) != REQUIRED_FAIL_CLOSED:
        fail("fail-closed cases mismatch")
    live = fixture.get("liveDispatchBoundary", {})
    if live.get("liveDispatchPerformed") is not False or live.get("requiresExplicitMainAction") is not True:
        fail("live dispatch boundary must stay explicit and blocked in repo evidence")
    run_ref = fixture.get("pipelineRunRef", {})
    for key in ["providerKey", "workflowRef", "runRef", "status", "correlationRef", "auditRef", "evidenceRef", "nextActionCode", "supportSafeSummary"]:
        if key not in run_ref:
            fail(f"pipelineRunRef missing {key}")
    assert_support_safe(fixture, "pipeline-provider fixture")
    assert_fragments(PORT, ["interface PipelineProvider", "preflight", "requestDispatch", "observe"])
    assert_fragments(PROVIDER, ["LocalForgejoPipelineProvider", "approval_missing", "raw_value_supplied", "PipelineRunRef"])
    assert_fragments(TEST, ["preflightRequiresSecretRefsVariablesAndApprovalBeforeDispatch", "dispatchAndObservationReturnSupportSafePipelineRunRefs"])
    assert_fragments(DOC, ["PipelineProvider", "PipelineRunRef", "Live dispatch boundary", "explicit admin approval"])
    assert_fragments(FEATURE, ["@sprint27-local-forgejo-pipeline-provider", "approval_missing", "PipelineRunRef"])
    assert_fragments(MAPPING, ["@sprint27-local-forgejo-pipeline-provider", "LOCAL_FORGEJO_PIPELINE_PROVIDER_PROOF", str(FIXTURE.relative_to(ROOT))])
    print("local-forgejo-pipeline-provider-check: ok issue=663 provider=local-forgejo-actions dispatch=explicit")
    print("LOCAL_FORGEJO_PIPELINE_PROVIDER_PROOF")


if __name__ == "__main__":
    main()
