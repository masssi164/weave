#!/usr/bin/env python3
"""Validate Sprint 27 local Forgejo deployed-stack E2E handoff evidence for issue #665."""

from __future__ import annotations

import json
import re
import sys
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[1]
FIXTURE = ROOT / "release" / "provider-lab" / "local-forgejo-e2e" / "local-stack-e2e-handoff.fixture.json"
DOC = ROOT / "docs" / "evidence" / "local-forgejo-e2e-handoff.md"
FEATURE = ROOT / "e2e" / "features" / "sprint_27_local_forgejo_e2e_handoff.feature"
MAPPING = ROOT / "e2e" / "scenario_mappings.json"
RUNNER_FIXTURE = ROOT / "release" / "provider-lab" / "local-forgejo-runner-readiness" / "runner-readiness.fixture.json"
DEPLOYABLE_PLAN_FIXTURE = ROOT / "release" / "provider-lab" / "local-domain-plan" / "deployable-domain-plan.fixture.json"
PIPELINE_PROVIDER_FIXTURE = ROOT / "release" / "provider-lab" / "admin-cicd" / "local-forgejo-pipeline-provider.fixture.json"

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
REQUIRED_SIGNALS = {
    "pipeline_terminal_success",
    "stack_readiness_passed",
    "weave_e2e_passed",
}
REQUIRED_FAILURE_CASES = {
    "missing_test_credential",
    "stack_not_ready",
    "e2e_failed",
    "timeout",
    "evidence_redaction_failed",
}


def fail(message: str) -> None:
    print(f"local-forgejo-e2e-handoff-check: {message}", file=sys.stderr)
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
    if fixture.get("artifactKind") != "weave-local-forgejo-e2e-handoff-v1" or fixture.get("issue") != 665:
        fail("fixture kind/issue mismatch")
    if fixture.get("status") != "blocked_awaiting_pipeline_deployed_stack_and_e2e_signal":
        fail("#665 fixture must remain blocked until pipeline, deployed-stack, and E2E signals exist")
    if set(fixture.get("requiredConciseLocalSignals", [])) != REQUIRED_SIGNALS:
        fail("fixture required concise signals mismatch")
    upstream = fixture.get("requiredUpstreamEvidence", {})
    expected_upstream = {
        "runnerReadinessRef": RUNNER_FIXTURE,
        "deployablePlanRef": DEPLOYABLE_PLAN_FIXTURE,
        "pipelineRunRef": PIPELINE_PROVIDER_FIXTURE,
    }
    for key, path in expected_upstream.items():
        if upstream.get(key) != str(path.relative_to(ROOT)):
            fail(f"#665 fixture upstream {key} mismatch: {upstream.get(key)!r}")
        if not path.exists():
            fail(f"#665 upstream evidence missing {path.relative_to(ROOT)}")
    admin_state = fixture.get("adminConsoleEvidenceState", {})
    if admin_state.get("rawDetailsDisplayed") is not False:
        fail("Admin Console evidence state must not display raw details")
    if set(admin_state.get("blockedUntilSignals", [])) != REQUIRED_SIGNALS:
        fail("Admin Console evidence state must block until all #665 concise signals")
    failure_cases = fixture.get("failureCases", [])
    if {case.get("case") for case in failure_cases if isinstance(case, dict)} != REQUIRED_FAILURE_CASES:
        fail("#665 failure-case coverage mismatch")
    for case in failure_cases:
        if case.get("case") == "evidence_redaction_failed" and case.get("status") != "blocked":
            fail("redaction failure must block evidence persistence")
    mainline = fixture.get("mainlineDependencyStatus", {})
    for key in [
        "localCicdBootstrapperIssue666PresentOnMain",
        "localForgejoRunnerReadinessIssue662PresentOnMain",
        "deployableDomainPlanIssue664PresentOnMain",
        "pipelineProviderIssue663PresentOnMain",
    ]:
        if mainline.get(key) is not True:
            fail(f"#665 mainline dependency boundary must claim {key}=true only after artifacts are present")
    runner_status = fixture.get("runnerSignalStatus", {})
    for name in ["service_exists", "config_path_exists", "registered", "running", "secret_refs_present"]:
        if runner_status.get(name) is not True:
            fail(f"runner signal status missing {name}=true")
    if runner_status.get("secret_values_logged") is not False:
        fail("runner signal status must state secret values were not logged")
    boundary = fixture.get("currentClaimBoundary", {})
    for name in ["localRunnerRequired", "pipelineDispatchRequired", "deployedStackRequired", "weaveE2eRequired"]:
        if boundary.get(name) is not True:
            fail(f"claim boundary missing {name}=true")
    if boundary.get("releaseReadyClaimAllowed") is not False:
        fail("#665 blocked handoff must not allow release-ready claim")
    preflight = fixture.get("currentLocalForgejoPreflight", {})
    for key in ["repoTargetObserved", "workflowTargetObserved", "dispatchAccepted", "dispatchPreflightTerminalSuccess"]:
        if preflight.get(key) is not True:
            fail(f"current local Forgejo preflight must record {key}=true")
    for key in ["deployedStackPipelineTerminalSuccess", "stackReadinessPassed", "weaveE2ePassed"]:
        if preflight.get(key) is not False:
            fail(f"current local Forgejo preflight must keep {key}=false until deployed-stack E2E exists")
    if preflight.get("claimBoundary") != "dispatch_preflight_only":
        fail("current local Forgejo preflight must remain dispatch_preflight_only")
    live_boundary = fixture.get("liveEvidenceBoundary", {})
    if live_boundary.get("liveDispatchPerformed") is not True or live_boundary.get("requiresExplicitApprovalBeforeMutation") is not True:
        fail("live dispatch boundary must record approved preflight and keep explicit approval for stack mutation")
    for key in ["forgejoRepositoryTargetObserved", "forgejoWorkflowFileObserved", "dispatchPreflightPerformed", "dispatchPreflightTerminalSuccess"]:
        if live_boundary.get(key) is not True:
            fail(f"live evidence boundary must record {key}=true")
    assert_support_safe(fixture, "e2e handoff fixture")
    assert_fragments(DOC, ["blocked_awaiting_pipeline_deployed_stack_and_e2e_signal", "concise `~/server` signal", "dispatch_preflight_only", "local-forgejo-actions-run-7", "weave_e2e_passed", "Admin Console readiness/evidence state", "Failure cases", "Live evidence boundary"])
    assert_fragments(FEATURE, ["@sprint27-local-forgejo-e2e-handoff", "blocked_awaiting_pipeline_deployed_stack_and_e2e_signal", "pipeline_terminal_success"])
    assert_fragments(MAPPING, ["@sprint27-local-forgejo-e2e-handoff", "LOCAL_FORGEJO_E2E_HANDOFF_PROOF", str(FIXTURE.relative_to(ROOT))])
    print("local-forgejo-e2e-handoff-check: ok issue=665 status=blocked_awaiting_pipeline_deployed_stack_and_e2e_signal")
    print("LOCAL_FORGEJO_E2E_HANDOFF_PROOF")


if __name__ == "__main__":
    main()
