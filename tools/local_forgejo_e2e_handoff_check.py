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
WORKFLOW = ROOT / ".forgejo" / "workflows" / "weave-admin-setup-e2e.yml"

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
    "server_infra_readiness_passed",
    "weave_control_ready",
    "client_bootstrap_handoff_ready",
    "member_provider_neutral_join_passed",
    "weave_client_e2e_passed",
}
REQUIRED_FAILURE_CASES = {
    "missing_test_credential",
    "server_infra_not_ready",
    "client_e2e_failed",
    "timeout",
    "evidence_redaction_failed",
}
FORGEJO_WORKFLOW_FORBIDDEN_FRAGMENTS = [
    "flutter",
    "chromium",
    "xvfb",
    "libgtk-3-dev",
    "member_provider_neutral_join_passed=",
    "weave_client_e2e_passed=",
    '"member_provider_neutral_join_passed":',
    '"weave_client_e2e_passed":',
]
FORGEJO_WORKFLOW_REQUIRED_FRAGMENTS = [
    "name: Weave Control Local Deployment",
    "Deploy Weave Control, server, and infra",
    "pipeline_terminal_success",
    "server_infra_readiness_passed",
    "weave_control_ready",
    "client_bootstrap_handoff_ready",
    "separateClientLaneEvidence",
    "handoff_hold_seconds",
    "Hold deployed handoff open for external client lane",
    "runner_loopback_host_resolved=true",
    "WEAVE_LOCAL_FORGEJO_DOCKER_GATEWAY",
    "operator_loopback_host",
]


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
    expected_status = "forgejo_runner_handoff_and_separate_client_e2e_passed"
    if fixture.get("status") != expected_status:
        fail(f"#665 fixture status mismatch: {fixture.get('status')!r}")
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
    admin_blockers = set(admin_state.get("blockedUntilSignals", []))
    if not REQUIRED_SIGNALS.issubset(admin_blockers):
        fail("Admin Console evidence state must include all #665 concise signals")
    if "forgejo_runner_workflow_terminal_success_if_required" not in admin_blockers:
        fail("Admin Console evidence state must keep strict Forgejo-runner proof explicit")
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
    for name in [
        "localRunnerRequired",
        "pipelineDispatchRequiredForForgejoRunnerProof",
        "serverInfraDeploymentPassedDirectly",
        "weaveControlHandoffPassedDirectly",
        "separateClientE2ePassed",
    ]:
        if boundary.get(name) is not True:
            fail(f"claim boundary missing {name}=true")
    if boundary.get("forgejoRunnerWorkflowTerminalSuccess") is not True:
        fail("claim boundary must record forgejoRunnerWorkflowTerminalSuccess=true")
    if boundary.get("issueClosureClaimAllowed") is not True:
        fail("claim boundary must allow #665 evidence closure")
    if boundary.get("releaseReadyClaimAllowed") is not False:
        fail("claim boundary must keep releaseReadyClaimAllowed=false")
    preflight = fixture.get("currentLocalForgejoPreflight", {})
    for key in ["repoTargetObserved", "workflowTargetObserved", "dispatchAccepted", "dispatchPreflightTerminalSuccess"]:
        if preflight.get(key) is not True:
            fail(f"current local Forgejo preflight must record {key}=true")
    for key in ["localDeploymentPipelineTerminalSuccess", "serverInfraReadinessPassed", "weaveControlReady", "clientBootstrapHandoffReady", "forgejoRunnerWorkflowTerminalSuccess"]:
        if preflight.get(key) is not True:
            fail(f"current local Forgejo runner proof must record {key}=true")
    for key in ["memberProviderNeutralJoinPassed", "weaveClientE2ePassed", "clientSignalsEmittedByDeploymentRunner"]:
        if preflight.get(key) is not False:
            fail(f"deployment runner must keep client-lane field {key}=false")
    if preflight.get("claimBoundary") != "forgejo_runner_deployment_handoff_plus_separate_client_lane":
        fail("current local Forgejo proof must distinguish deployment handoff from separate client lane")
    for key, expected in {"supportSafeRunRef": "local-forgejo-actions-run-121", "supportSafeTaskRef": "local-forgejo-actions-task-220", "commitSha": "c0470eec04232b2271e49a82566796d66aab99d7", "backendImageRevision": "c0470eec04232b2271e49a82566796d66aab99d7"}.items():
        if preflight.get(key) != expected:
            fail(f"current local Forgejo proof {key} mismatch: {preflight.get(key)!r}")
    if preflight.get("handoffHoldSeconds") != 1800 or preflight.get("oidcDiscoveryReadyDuringHandoff") is not True:
        fail("current local Forgejo proof must record the 1800s OIDC-ready handoff")
    if preflight.get("terminalStatus") != "success" or preflight.get("taskLogInStorage") is not True:
        fail("current local Forgejo proof must record terminal success with log storage")
    if preflight.get("holdStepStatus") != "success" or preflight.get("destroyStepStatus") != "success":
        fail("current local Forgejo proof must record hold and cleanup success")
    direct = fixture.get("currentLocalDirectProof", {})
    for key in ["serverInfraReadinessPassed", "weaveControlReady", "clientBootstrapHandoffReady", "operatorCheckPassed"]:
        if direct.get(key) is not True:
            fail(f"direct local proof must record {key}=true")
    if direct.get("rawLogsPersisted") is not False:
        fail("direct local proof must not persist raw logs")
    client_lane = fixture.get("currentSeparateClientLane", {})
    for key in ["makeFailMaskingFixed", "memberProviderNeutralJoinPassed", "weaveClientE2ePassed"]:
        if client_lane.get(key) is not True:
            fail(f"separate client lane must record {key}=true")
    for marker in ["AUTH_RESULT", "PROFILE_RESULT", "CHAT_RESULT", "MATRIX_RESULT", "E2EE_RESULT", "FILES_RESULT", "CALENDAR_RESULT", "BOARDS_RESULT", "WORKSPACE_LOOP_RESULT", "PROVIDER_REALITY_RESULT"]:
        if marker not in client_lane.get("observedMarkers", []):
            fail(f"separate client lane missing observed marker {marker}")
    if client_lane.get("rawLogsPersisted") is not False:
        fail("separate client lane must not persist raw logs")
    live_boundary = fixture.get("liveEvidenceBoundary", {})
    if live_boundary.get("liveDispatchPerformed") is not True or live_boundary.get("requiresExplicitApprovalBeforeMutation") is not True:
        fail("live dispatch boundary must record approved preflight and keep explicit approval for stack mutation")
    if live_boundary.get("directLocalDeploymentProofPerformed") is not True or live_boundary.get("separateClientE2eProofPerformed") is not True:
        fail("live evidence boundary must record direct deployment and separate client proofs")
    if live_boundary.get("forgejoRunnerWorkflowTerminalSuccessRecorded") is not True:
        fail("live evidence boundary must record current Forgejo-runner terminal success")
    if live_boundary.get("terminalRunRef") != "local-forgejo-actions-run-121" or live_boundary.get("terminalTaskRef") != "local-forgejo-actions-task-220":
        fail("live evidence boundary terminal Forgejo refs mismatch")
    if live_boundary.get("handoffHoldSeconds") != 1800 or live_boundary.get("separateClientLaneAgainstRunnerHandoffPassed") is not True:
        fail("live evidence boundary must record separate client lane against 1800s handoff")
    if live_boundary.get("terminalStatus") != "success" or live_boundary.get("taskLogInStorage") is not True:
        fail("live evidence boundary must record terminal success with log storage")
    if live_boundary.get("forgejoWorkflowSupportsExternalClientHandoffHold") is not True:
        fail("live evidence boundary must record workflow handoff-hold support for the separate client lane")
    if live_boundary.get("handoffHoldInput") != "handoff_hold_seconds":
        fail("live evidence boundary handoff hold input mismatch")
    for key in ["forgejoRepositoryTargetObserved", "forgejoWorkflowFileObserved", "dispatchPreflightPerformed", "dispatchPreflightTerminalSuccess"]:
        if live_boundary.get(key) is not True:
            fail(f"live evidence boundary must record {key}=true")
    workflow_text = read(WORKFLOW)
    workflow_lower = workflow_text.lower()
    for fragment in FORGEJO_WORKFLOW_FORBIDDEN_FRAGMENTS:
        if fragment.lower() in workflow_lower:
            fail(f"Forgejo deployment workflow must not emit/install client-lane or Flutter evidence fragment {fragment!r}")
    for fragment in FORGEJO_WORKFLOW_REQUIRED_FRAGMENTS:
        if fragment not in workflow_text:
            fail(f"Forgejo deployment workflow missing deployment-handoff fragment {fragment!r}")

    assert_support_safe(fixture, "e2e handoff fixture")
    assert_fragments(DOC, [expected_status, "local-forgejo-actions-run-121", "local-forgejo-actions-task-220", "handoff_hold_seconds=1800", "weave_client_e2e_passed", "forgejo_runner_workflow_terminal_success", "Forgejo deployment runner must stay client-free", "Responsibility split evidence", "No v0.1 Spec 0001 Weaver/AI runtime claim", "Admin Console readiness/evidence state", "Failure cases", "Activity boundary"])
    assert_fragments(ROOT / "docs" / "weave-control-bootstrap-to-client-contract.md", ["```mermaid", "Admin selects deploy_new in Weave Control", "Receive deployment handoff target from Weave Control"])
    assert_fragments(FEATURE, ["@sprint27-local-forgejo-e2e-handoff", expected_status, "pipeline_terminal_success", "forgejo_runner_workflow_terminal_success"])
    assert_fragments(MAPPING, ["@sprint27-local-forgejo-e2e-handoff", "LOCAL_FORGEJO_E2E_HANDOFF_PROOF", str(FIXTURE.relative_to(ROOT))])
    print(f"local-forgejo-e2e-handoff-check: ok issue=665 status={expected_status}")
    print("LOCAL_FORGEJO_E2E_HANDOFF_PROOF")


if __name__ == "__main__":
    main()
