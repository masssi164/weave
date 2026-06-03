#!/usr/bin/env python3
"""Validate Sprint 24 Weaver Runtime Factory evidence, scoreboard, and claim gates."""

from __future__ import annotations

import json
import re
import sys
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[1]
ARTIFACT_DIR = ROOT / "release" / "provider-lab" / "weaver-runtime"
RUNTIME = ARTIFACT_DIR / "per-user-runtime-proof.fixture.json"
RECONCILE = ARTIFACT_DIR / "desired-state-reconciliation-proof.fixture.json"
ISOLATION = ARTIFACT_DIR / "per-user-isolation-proof.fixture.json"
CLAIM_GATE = ARTIFACT_DIR / "sprint-24-claim-gate.fixture.json"
SCOREBOARD = ARTIFACT_DIR / "sprint-24-scoreboard.json"
MANIFEST = ROOT / "release" / "provider-lab" / "manifests" / "docker-runtime.json"
HEALTH = ROOT / "release" / "provider-lab" / "health-report.sample.json"
CLAIM_MATRIX = ROOT / "docs" / "product-trust-provider-choice-claim-matrix.md"
CLOSURE = ROOT / "docs" / "sprint-24-closure-report.md"
EVIDENCE = ROOT / "docs" / "evidence" / "weaver-runtime-factory-report.md"

SECRET_PATTERNS = [
    re.compile(pattern, re.IGNORECASE)
    for pattern in [
        r"bearer\s+[a-z0-9._-]+",
        r'refresh[_-]?token\s*[:=]\s*[^\s,}"]+',
        r"api[_-]?key\s*[:=]",
        r"rawProviderPayload\\s*[:=]",
        r"rawProviderError\\s*[:=]",
        r"openclaw\.json\s*[{:]",
        r"memory://",
        r'https?://matrix\.weave\.local/_[^\s)\"]+',
    ]
]


def fail(message: str) -> None:
    print(f"weaver-runtime-factory-check: {message}", file=sys.stderr)
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


def validate_runtime(artifact: dict[str, Any]) -> None:
    if artifact.get("artifactKind") != "weave-weaver-runtime-per-user-runtime-proof":
        fail("runtime proof artifact kind mismatch")
    if artifact.get("supportSafe") is not True:
        fail("runtime proof must be supportSafe")
    instances = artifact.get("runtimeInstances")
    if not isinstance(instances, list) or len(instances) != 2:
        fail("runtime proof must contain Alice and Bob runtime instances")
    users = {instance.get("userRef") for instance in instances}
    containers = {instance.get("containerId") for instance in instances}
    workspaces = {instance.get("workspacePath") for instance in instances}
    if len(users) != 2 or len(containers) != 2 or len(workspaces) != 2:
        fail("Alice and Bob must have distinct users, containers, and workspaces")
    for instance in instances:
        labels = instance.get("labels")
        if not isinstance(labels, dict):
            fail("runtime instance labels are required")
        for key in ["weave.org", "weave.user", "weave.profile_hash", "weave.policy_version", "weave.managed_by"]:
            if key not in labels:
                fail(f"runtime labels missing {key}")
        if labels.get("weave.managed_by") != "weave-weaver-runtime-reconciler":
            fail("runtime instance must be managed by reconciler")
    deactivation = artifact.get("deactivationProof", {})
    if deactivation.get("aliceStateAfterDisable") != "stopped" or deactivation.get("bobStateAfterAliceDisable") != "running":
        fail("disabling Alice must stop Alice while Bob remains running")
    health = artifact.get("healthReport", {})
    for key in ["supportSafe", "rawDockerSocketMounted", "rawOpenClawConfigExported", "rawProviderSecretsExported"]:
        if key not in health:
            fail(f"runtime health missing {key}")
    if health.get("rawDockerSocketMounted") is not False or health.get("rawOpenClawConfigExported") is not False:
        fail("runtime health must not expose Docker socket or raw OpenClaw config")


def validate_reconcile(artifact: dict[str, Any]) -> None:
    if artifact.get("artifactKind") != "weave-weaver-runtime-desired-state-reconciliation-proof":
        fail("reconciliation proof artifact kind mismatch")
    decisions = artifact.get("decisions")
    if not isinstance(decisions, list):
        fail("reconciliation decisions are required")
    actions = {decision.get("action") for decision in decisions}
    for action in ["create", "update", "revoke"]:
        if action not in actions:
            fail(f"reconciliation fixture missing {action} action")
    for decision in decisions:
        for field in ["desiredState", "actualState", "action", "outcome"]:
            if not decision.get(field):
                fail(f"reconciliation decision missing {field}")
    if set(artifact.get("auditRequiredFields", [])) != {"desiredState", "actualState", "action", "outcome"}:
        fail("reconciliation audit required fields must be exact")


def validate_isolation(artifact: dict[str, Any]) -> None:
    if artifact.get("artifactKind") != "weave-weaver-runtime-per-user-isolation-proof":
        fail("isolation proof artifact kind mismatch")
    workspace = artifact.get("workspaceIsolation", {})
    if workspace.get("aliceReadsBob") != "blocked" or workspace.get("bobReadsAlice") != "blocked":
        fail("cross-user workspace reads must be blocked")
    profile = artifact.get("profileIsolation", {})
    if profile.get("crossUserFetchByHash") != "blocked" or profile.get("revokedOrExpiredProfile") != "blocked":
        fail("cross-user and revoked profile access must be blocked")
    redaction = artifact.get("supportBundleRedaction", {})
    for key in ["rawWeaverMemoryExported", "rawOpenClawConfigExported", "rawProviderSecretsExported", "rawProviderPayloadsExported"]:
        if redaction.get(key) is not False:
            fail(f"support bundle redaction must set {key}=false")
    disablement = artifact.get("adminDisablement", {})
    if disablement.get("aliceRuntimeStopped") is not True or disablement.get("bobRuntimeUnaffected") is not True:
        fail("admin disablement must stop only the disabled user's runtime")
    if disablement.get("memoryOrSecretsLeakedAfterDisable") is not False:
        fail("admin disablement must not leak memory or secrets")


def validate_claim_gate(artifact: dict[str, Any]) -> None:
    if artifact.get("artifactKind") != "weave-weaver-runtime-sprint-24-claim-gate":
        fail("claim gate artifact kind mismatch")
    accepted = artifact.get("acceptedClaim", {})
    claim = accepted.get("claim", "")
    required_terms = ["Sprint 24", "support-safe", "provider-lab", "Weaver Runtime Factory", "per-user Docker", "reconciliation", "isolation"]
    for term in required_terms:
        if term not in claim:
            fail(f"accepted claim missing scoped term {term!r}")
    if accepted.get("expectedOutcome") != "accept":
        fail("accepted claim must be marked accept")
    rejected = artifact.get("rejectedClaims")
    if not isinstance(rejected, list) or len(rejected) < 4:
        fail("claim gate must list rejected overclaims")
    rejected_text = "\n".join(item.get("claim", "") for item in rejected)
    for forbidden in ["Weaver is available", "A PA runs per user", "customer-ready", "release-ready", "Broad autonomous AI"]:
        if forbidden.lower() not in rejected_text.lower():
            fail(f"claim gate missing rejected claim {forbidden!r}")
    boundary = artifact.get("claimBoundary", "").lower()
    for phrase in ["provider-lab", "production pa availability", "customer-ready weaver", "raw openclaw config", "memory", "secrets"]:
        if phrase not in boundary:
            fail(f"claim boundary missing {phrase}")


def validate_scoreboard(scoreboard: dict[str, Any], manifest: dict[str, Any], health: dict[str, Any]) -> None:
    fields = scoreboard.get("fields", {})
    for key in ["runtimeLifecycle", "reconciliation", "perUserIsolation", "supportBundleRedaction", "claimSafety"]:
        if fields.get(key) != "green":
            fail(f"scoreboard field {key} must be green")
    issues = scoreboard.get("issues", {})
    for issue in ["631", "632", "633", "634"]:
        if issues.get(issue) != "green":
            fail(f"scoreboard issue {issue} must be green")
    if scoreboard.get("openReleaseBlockers") != [] or scoreboard.get("sprint24ExitGate") != "green":
        fail("scoreboard must have green exit gate and no open release blockers")
    if manifest.get("realityLevel") != scoreboard.get("dockerRuntimeRealityLevel"):
        fail("docker-runtime manifest realityLevel must agree with Sprint 24 scoreboard")
    providers = [provider for provider in health.get("providers", []) if provider.get("providerKey") == "docker-runtime"]
    if len(providers) != 1 or providers[0].get("realityLevel") != manifest.get("realityLevel"):
        fail("health report docker-runtime realityLevel must match manifest")
    if manifest.get("migrationLimits", {}).get("releaseReadyClaimed") is not False:
        fail("docker-runtime manifest must not claim release_ready")


def validate_docs() -> None:
    for path in [CLAIM_MATRIX, CLOSURE, EVIDENCE]:
        if not path.exists():
            fail(f"missing {path.relative_to(ROOT)}")
    matrix = CLAIM_MATRIX.read_text(encoding="utf-8")
    for phrase in ["Sprint 24 Weaver Runtime Factory", "provider-lab runtime factory", "does not claim production PA availability"]:
        if phrase not in matrix:
            fail(f"claim matrix missing {phrase!r}")
    closure = CLOSURE.read_text(encoding="utf-8")
    for issue in ["#631", "#632", "#633", "#634"]:
        if issue not in closure:
            fail(f"closure report missing {issue}")
    evidence = EVIDENCE.read_text(encoding="utf-8")
    for artifact in [RUNTIME, RECONCILE, ISOLATION, CLAIM_GATE, SCOREBOARD]:
        if str(artifact.relative_to(ROOT)) not in evidence:
            fail(f"evidence report missing {artifact.relative_to(ROOT)}")


def main() -> None:
    runtime = load(RUNTIME)
    reconcile = load(RECONCILE)
    isolation = load(ISOLATION)
    claim_gate = load(CLAIM_GATE)
    scoreboard = load(SCOREBOARD)
    manifest = load(MANIFEST)
    health = load(HEALTH)
    for label, artifact in [
        ("runtime", runtime),
        ("reconciliation", reconcile),
        ("isolation", isolation),
        ("claim_gate", claim_gate),
        ("scoreboard", scoreboard),
        ("manifest", manifest),
        ("health", health),
    ]:
        assert_support_safe(artifact, label)
    validate_runtime(runtime)
    validate_reconcile(reconcile)
    validate_isolation(isolation)
    validate_claim_gate(claim_gate)
    validate_scoreboard(scoreboard, manifest, health)
    validate_docs()
    print("weaver-runtime-factory-check: ok issues=631,632,633,634 claims=scoped provider-lab release_ready=false")


if __name__ == "__main__":
    main()
