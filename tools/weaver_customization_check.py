#!/usr/bin/env python3
"""Validate Sprint 25 Weaver customization evidence, scoreboard, and claim gates."""

from __future__ import annotations

import json
import re
import sys
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[1]
ARTIFACT_DIR = ROOT / "release" / "provider-lab" / "weaver-runtime"
PROFILE = ARTIFACT_DIR / "profile-customization-proof.fixture.json"
POLICY = ARTIFACT_DIR / "policy-boundary-proof.fixture.json"
TOOLS = ARTIFACT_DIR / "tool-approval-gate-proof.fixture.json"
CLAIMS = ARTIFACT_DIR / "sprint-25-claim-gate.fixture.json"
SCOREBOARD = ARTIFACT_DIR / "sprint-25-scoreboard.json"
SPRINT32_GOVERNED = ARTIFACT_DIR / "sprint-32-governed-foundation.fixture.json"
EVIDENCE = ROOT / "docs" / "evidence" / "weaver-customization-report.md"
CLOSURE = ROOT / "docs" / "sprint-25-closure-report.md"

SECRET_PATTERNS = [
    re.compile(pattern, re.IGNORECASE)
    for pattern in [
        r"bearer\s+[a-z0-9._-]+",
        r"refresh[_-]?token\s*[:=]\s*[^\s,}\"]+",
        r"api[_-]?key\s*[:=]",
        r"secret\s*[:=]",
        r"rawProviderPayload\s*[:=]",
        r"openclaw\.json\s*[{:]",
        r"memory://",
        r"https?://[^\s)\"]*@",
    ]
]

REQUIRED_SPRINT32_POLICY_CASES = {
    "default-disabled": "deny",
    "missing-user-flag": "deny",
    "missing-generator": "deny",
    "missing-opt-in": "deny",
    "all-enablement-conditions-present": "permit",
}
REQUIRED_SPRINT32_READONLY_TOOLS = {
    "calendar.search_events",
    "boards.search_tasks",
    "files.search",
    "chat.search_messages",
}


def fail(message: str) -> None:
    print(f"weaver-customization-check: {message}", file=sys.stderr)
    raise SystemExit(1)


def load(path: Path) -> dict[str, Any]:
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except FileNotFoundError:
        fail(f"missing {path.relative_to(ROOT)}")
    except json.JSONDecodeError as error:
        fail(f"invalid JSON in {path.relative_to(ROOT)}: {error}")
    if not isinstance(value, dict):
        fail(f"{path.relative_to(ROOT)} must contain a JSON object")
    return value


def assert_support_safe(value: Any, label: str) -> None:
    text = json.dumps(value, sort_keys=True)
    for pattern in SECRET_PATTERNS:
        if pattern.search(text):
            fail(f"{label} contains forbidden support-unsafe pattern {pattern.pattern!r}")


def validate_profile(artifact: dict[str, Any]) -> None:
    if artifact.get("artifactKind") != "weave-weaver-runtime-sprint-25-profile-customization-proof":
        fail("profile customization artifact kind mismatch")
    change = artifact.get("profileChange", {})
    if change.get("versionIncremented") is not True or change.get("hashChanged") is not True:
        fail("profile customization must increment version and change profile hash")
    if change.get("previousProfileHash") != change.get("baseProfileHash"):
        fail("customized profile must point at the previous profile hash")
    rollback = artifact.get("rollback", {})
    if rollback.get("restored") is not True or rollback.get("rollbackProfileHash") != change.get("baseProfileHash"):
        fail("rollback must restore the previous profile hash")
    allowed = set(artifact.get("allowedCustomizationFields", []))
    required = {"displayName", "style", "language", "answerStyle", "workingHours", "notifications", "personalNotes", "memoryOptIn", "allowedSkills", "workspaceStructure"}
    if not required.issubset(allowed):
        fail("allowed customization field list is incomplete")


def validate_policy(artifact: dict[str, Any]) -> None:
    if artifact.get("artifactKind") != "weave-weaver-runtime-sprint-25-policy-boundary-proof":
        fail("policy boundary artifact kind mismatch")
    attempts = artifact.get("forbiddenCustomizationAttempts")
    if not isinstance(attempts, list) or len(attempts) < 4:
        fail("policy boundary proof must list forbidden customization attempts")
    if any(attempt.get("decision") != "blocked" or not attempt.get("policyReason") for attempt in attempts):
        fail("every forbidden customization attempt must be blocked with a policy reason")
    for key in ["rawOpenClawConfigAccepted", "rawSecretsAccepted", "uncheckedPluginsAccepted", "teamWideActionsAccepted"]:
        if artifact.get(key) is not False:
            fail(f"{key} must be false")
    required_audit = {"user", "action", "domain", "decision", "policyReason", "requestedFields", "supportSafe"}
    if set(artifact.get("auditRequiredFields", [])) != required_audit:
        fail("policy audit required fields must be exact")


def validate_tools(artifact: dict[str, Any]) -> None:
    if artifact.get("artifactKind") != "weave-weaver-runtime-sprint-25-tool-approval-gate-proof":
        fail("tool approval artifact kind mismatch")
    scenarios = artifact.get("scenarios", {})
    expected = {
        "readOnlyAllowed": "passed",
        "writeWithoutApproval": "approval_required",
        "writeWithApproval": "ok",
        "revokedToolBlocked": "runtime_profile_revoked",
        "expiredTokenBlocked": "runtime_token_expired",
        "overbroadGrantBlocked": "overbroad_grant",
        "missingConsentBlocked": "consent_required",
    }
    for key, value in expected.items():
        if scenarios.get(key) != value:
            fail(f"tool scenario {key} must be {value}")
    receipt = artifact.get("approvalReceipt", {})
    required = {"receiptRef", "actorRef", "action", "scopeRefs", "policyVersion", "expiresAt", "auditRef"}
    if set(receipt.get("requiredFields", [])) != required:
        fail("approval receipt required fields must be exact")
    if receipt.get("validatedBeforeInvocation") is not True or receipt.get("auditRefRecorded") is not True:
        fail("approval receipt must be validated and audited before invocation")


def validate_claims(artifact: dict[str, Any]) -> None:
    if artifact.get("artifactKind") != "weave-weaver-runtime-sprint-25-claim-gate":
        fail("claim gate artifact kind mismatch")
    claim = artifact.get("acceptedClaim", {}).get("claim", "")
    for term in ["Sprint 25", "support-safe", "Weaver customization", "RuntimeProfile", "ApprovalReceipts", "rollback"]:
        if term not in claim:
            fail(f"accepted claim missing {term!r}")
    rejected_text = "\n".join(item.get("claim", "") for item in artifact.get("rejectedClaims", []))
    for forbidden in ["customer-ready", "raw OpenClaw config", "without approval receipts", "Teams"]:
        if forbidden.lower() not in rejected_text.lower():
            fail(f"claim gate missing rejected overclaim {forbidden!r}")
    boundary = artifact.get("claimBoundary", "").lower()
    for phrase in ["provider-lab", "production pa availability", "customer-ready weaver", "raw openclaw config", "raw secrets"]:
        if phrase not in boundary:
            fail(f"claim boundary missing {phrase}")


def validate_sprint32_governed_foundation(artifact: dict[str, Any]) -> None:
    if artifact.get("artifactKind") != "weave-weaver-runtime-sprint-32-governed-foundation-proof":
        fail("Sprint 32 governed foundation artifact kind mismatch")
    default_posture = artifact.get("defaultPosture", {})
    if default_posture.get("weaverCategoryDefault") != "disabled" or default_posture.get("runtimeGeneratorDefault") != "disabled":
        fail("Sprint 32 governed foundation must keep Weaver disabled by default")
    if default_posture.get("rawToolChannelMcpSecretSettingsEditableByMember") is not False:
        fail("normal members must not edit raw OpenClaw tool/channel/MCP/secret settings")

    policy_cases = {case.get("id"): case for case in artifact.get("policyEvaluationCases", []) if isinstance(case, dict)}
    if set(policy_cases) != set(REQUIRED_SPRINT32_POLICY_CASES):
        fail(f"Sprint 32 policy case mismatch: {sorted(policy_cases)}")
    for case_id, decision in REQUIRED_SPRINT32_POLICY_CASES.items():
        if policy_cases[case_id].get("decision") != decision or not policy_cases[case_id].get("reason"):
            fail(f"Sprint 32 policy case {case_id} must decide {decision} with a reason")

    for case in artifact.get("grantIntersectionCases", []):
        effective = set(case.get("effectiveGrants", []))
        allowlist = set(case.get("organizationAllowlist", []))
        rights = set(case.get("userRights", []))
        if effective != allowlist.intersection(rights):
            fail(f"Sprint 32 grant intersection case {case.get('id')} has wrong effective grants")
        if not set(case.get("blockedGrants", [])).isdisjoint(effective):
            fail(f"Sprint 32 grant intersection case {case.get('id')} blocks an effective grant")

    registry = artifact.get("toolRegistryV1", {})
    if set(registry.get("allowedReadOnlyTools", [])) != REQUIRED_SPRINT32_READONLY_TOOLS:
        fail("Sprint 32 read-only tool registry must contain the exact initial tool set")
    tool_cases = {case.get("id"): case for case in registry.get("cases", []) if isinstance(case, dict)}
    required_decisions = {
        "calendar-search-allowed": "allow",
        "boards-comment-without-approval": "deny_approval_required",
        "external-send-with-approval": "allow_with_audit",
        "unknown-tool-fails-closed": "deny_unknown_tool",
    }
    for case_id, decision in required_decisions.items():
        if tool_cases.get(case_id, {}).get("decision") != decision:
            fail(f"Sprint 32 tool registry case {case_id} must decide {decision}")
    if tool_cases["boards-comment-without-approval"].get("approvalReceiptPresent") is not False:
        fail("write without ApprovalReceipt must be denied")

    projection = artifact.get("runtimeProfileProjection", {})
    if projection.get("sourceOfTruth") != "Weave policy and user rights" or projection.get("projectionDirection") != "one_way_to_openclaw_runtime_profile":
        fail("RuntimeProfile/OpenClaw projection must be generated one-way from Weave policy")
    for key in ["normalMemberCanEditRawOpenClaw", "containsProviderSecrets", "containsRawToolOutput"]:
        if projection.get(key) is not False:
            fail(f"Sprint 32 projection must set {key}=false")
    required_audit = {"runtimeProfileHash", "userRef", "tool", "domain", "providerRef", "credentialRef", "policyDecision", "approvalReceiptRef"}
    if set(projection.get("auditPolicyRequiredFields", [])) != required_audit:
        fail("Sprint 32 audit policy required fields mismatch")

    claims = artifact.get("claimGate", {})
    accepted = str(claims.get("acceptedClaim", ""))
    for phrase in ["disabled-by-default", "read-only first tool registry", "ApprovalReceipt-gated", "Weave policy"]:
        if phrase not in accepted:
            fail(f"Sprint 32 accepted claim missing {phrase}")
    rejected = "\n".join(claims.get("rejectedClaims", []))
    for phrase in ["broadly available", "marketplace MCPs", "raw OpenClaw config", "without ApprovalReceipt", "#591"]:
        if phrase.lower() not in rejected.lower():
            fail(f"Sprint 32 rejected claims missing {phrase}")


def validate_scoreboard(scoreboard: dict[str, Any]) -> None:
    if scoreboard.get("artifactKind") != "weave-weaver-runtime-sprint-25-scoreboard":
        fail("scoreboard artifact kind mismatch")
    for key in ["profileVersioning", "policyBoundaries", "toolApprovalGates", "claimSafety"]:
        if scoreboard.get("fields", {}).get(key) != "green":
            fail(f"scoreboard field {key} must be green")
    for issue in ["635", "636", "637", "638"]:
        if scoreboard.get("issues", {}).get(issue) != "green":
            fail(f"scoreboard issue {issue} must be green")
    if scoreboard.get("openReleaseBlockers") != [] or scoreboard.get("sprint25ExitGate") != "green":
        fail("Sprint 25 exit gate must be green with no release blockers")
    for relpath in scoreboard.get("requiredArtifacts", []):
        if not (ROOT / relpath).exists():
            fail(f"scoreboard references missing artifact {relpath}")


def validate_docs() -> None:
    for path in [EVIDENCE, CLOSURE]:
        if not path.exists():
            fail(f"missing {path.relative_to(ROOT)}")
        text = path.read_text(encoding="utf-8")
        for issue in ["#635", "#636", "#637", "#638"]:
            if issue not in text:
                fail(f"{path.relative_to(ROOT)} missing {issue}")
        for artifact in [PROFILE, POLICY, TOOLS, CLAIMS, SCOREBOARD]:
            if str(artifact.relative_to(ROOT)) not in text:
                fail(f"{path.relative_to(ROOT)} missing {artifact.relative_to(ROOT)}")
    evidence_text = EVIDENCE.read_text(encoding="utf-8")
    if "#711" not in evidence_text or str(SPRINT32_GOVERNED.relative_to(ROOT)) not in evidence_text:
        fail("Weaver customization evidence report missing Sprint 32 #711 governed foundation artifact")


def main() -> None:
    profile = load(PROFILE)
    policy = load(POLICY)
    tools = load(TOOLS)
    claims = load(CLAIMS)
    scoreboard = load(SCOREBOARD)
    sprint32_governed = load(SPRINT32_GOVERNED)
    for label, artifact in [("profile", profile), ("policy", policy), ("tools", tools), ("claims", claims), ("scoreboard", scoreboard), ("sprint32_governed", sprint32_governed)]:
        if artifact.get("supportSafe") is not True:
            fail(f"{label} must be supportSafe")
        assert_support_safe(artifact, label)
    validate_profile(profile)
    validate_policy(policy)
    validate_tools(tools)
    validate_claims(claims)
    validate_scoreboard(scoreboard)
    validate_sprint32_governed_foundation(sprint32_governed)
    validate_docs()
    print("weaver-customization-check: ok issues=635,636,637,638,711 claims=scoped governed-foundation customer_ready=false")


if __name__ == "__main__":
    main()
