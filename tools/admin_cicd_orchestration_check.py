#!/usr/bin/env python3
"""Validate Sprint 26 Admin Console CI/CD setup orchestration fixtures."""

from __future__ import annotations

import json
import re
import sys
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[1]
ARTIFACT_DIR = ROOT / "release" / "provider-lab" / "admin-cicd"
MANIFEST = ARTIFACT_DIR / "pipeline-provider-manifest.fixture.json"
FORGEJO = ARTIFACT_DIR / "local-forgejo-setup-proof.fixture.json"
COPY = ARTIFACT_DIR / "admin-console-copy.fixture.json"
CONTRACT = ROOT / "specs" / "admin-ci-cd-orchestration-contract.md"
EVIDENCE = ROOT / "docs" / "evidence" / "admin-cicd-setup-proof.md"
FEATURE = ROOT / "e2e" / "features" / "sprint_26_admin_cicd_setup.feature"
UI_PLAN = ROOT / "docs" / "evidence" / "admin-cicd-ui-test-plan.md"

FORBIDDEN_PATTERNS = [
    re.compile(pattern, re.IGNORECASE)
    for pattern in [
        r"bearer\s+[a-z0-9._-]+",
        r"ghp_[a-z0-9_]+",
        r"token\s*[:=]\s*[^\s,}\"]+",
        r"secret(Value)?\s*[:=]\s*[^\s,}\"]+",
        r"https?://[^\s)\"]+",
        r"ssh://[^\s)\"]+",
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
    "provider_discovery",
    "ci_cd_registration",
    "domain_selection",
    "adapter_question",
    "self_hosted_recommendation",
    "target_provider_selection",
    "preflight",
    "dry_run_mapping",
    "admin_approval",
    "trigger_requested",
    "run_observing",
    "migration_apply_candidate",
    "abort_requested",
    "resume_requested",
    "post_reconcile_readiness",
    "evidence_complete",
    "go_live_approval_required",
}


def fail(message: str) -> None:
    print(f"admin-cicd-orchestration-check: {message}", file=sys.stderr)
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


def assert_support_safe(value: Any, label: str, path: tuple[str, ...] = ()) -> None:
    if isinstance(value, dict):
        for key, child in value.items():
            child_path = (*path, key)
            if key in FORBIDDEN_FIELD_NAMES and child_path != ("forbiddenFields",):
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


def validate_manifest(manifest: dict[str, Any]) -> None:
    if manifest.get("artifactKind") != "weave-admin-cicd-pipeline-provider-manifest-v1":
        fail("pipeline provider manifest kind mismatch")
    if manifest.get("canonicalSetupSurface") != "Admin Console":
        fail("Admin Console must be the canonical setup surface")
    if set(manifest.get("forbiddenFields", [])) != FORBIDDEN_FIELD_NAMES:
        fail("manifest forbiddenFields must exactly match the support-safety field denylist")
    provider_list = manifest.get("providers", [])
    if not isinstance(provider_list, list):
        fail("manifest providers must be a list")
    providers: dict[str, dict[str, Any]] = {}
    for index, provider in enumerate(provider_list):
        if not isinstance(provider, dict):
            fail(f"provider entry {index} must be an object")
        provider_key = provider.get("providerKey")
        if not isinstance(provider_key, str) or not provider_key:
            fail(f"provider entry {index} missing providerKey")
        if provider_key in providers:
            fail(f"duplicate providerKey {provider_key}")
        providers[provider_key] = provider
    for provider_key in ["github-actions", "azure-devops", "local-forgejo-actions", "woodpecker-ci"]:
        if provider_key not in providers:
            fail(f"manifest missing provider {provider_key}")
    for key, provider in providers.items():
        for section in ["trigger", "status", "approval", "operations"]:
            if not isinstance(provider.get(section), dict):
                fail(f"provider {key} missing {section}")
        if provider["trigger"].get("manualDispatch") is not True:
            fail(f"provider {key} must support manual dispatch contract")
        if provider["trigger"].get("triggerAuthRefType") != "SecretRef":
            fail(f"provider {key} trigger auth must use SecretRef")
        if provider["status"].get("rawLogRedactionRequired") is not True:
            fail(f"provider {key} must require raw log redaction")
        if provider["status"].get("unknownStatusBehavior") != "fail_closed_after_timeout":
            fail(f"provider {key} must fail closed on unknown status")
        if provider["operations"].get("rateLimitPolicy") not in {"backoff_and_fail_closed", "retry_after_or_fail_closed"}:
            fail(f"provider {key} must declare fail-closed rate-limit policy")


def validate_forgejo(proof: dict[str, Any]) -> None:
    if proof.get("artifactKind") != "weave-admin-cicd-local-forgejo-setup-proof-v1":
        fail("local Forgejo proof kind mismatch")
    refs = proof.get("connectionRefs", {})
    required_refs = {
        "baseUrlVariable": "WEAVE_FORGEJO_BASE_URL",
        "apiUrlVariable": "WEAVE_FORGEJO_API_URL",
        "usernameVariable": "WEAVE_FORGEJO_USERNAME",
        "sshHostVariable": "WEAVE_FORGEJO_SSH_HOST",
        "sshPortVariable": "WEAVE_FORGEJO_SSH_PORT",
        "tokenSecret": "WEAVE_FORGEJO_TOKEN",
    }
    if refs != required_refs:
        fail("local Forgejo connection refs must name variables/secrets exactly and contain no values")
    diagnostic = proof.get("localDiagnostic", {})
    if diagnostic.get("forgejoService") != "present" or diagnostic.get("forgejoDatabaseService") != "present":
        fail("local Forgejo proof must show Forgejo and DB services present")
    if diagnostic.get("runnerService") != "missing":
        fail("current local proof must fail closed while runner service is missing")
    validations = proof.get("secretValidation", [])
    if any(item.get("valueDisplayed") is not False for item in validations):
        fail("secret/variable validation must never display values")
    if proof.get("adminConsoleStatus", {}).get("reasonCode") != "runner_missing":
        fail("local Forgejo preflight must block on runner_missing")
    run_ref = proof.get("pipelineRunRef", {})
    if run_ref.get("status") != "blocked" or run_ref.get("runRef") != "none-trigger-blocked-before-dispatch":
        fail("local Forgejo run ref must be blocked before dispatch")


def validate_copy(copy: dict[str, Any]) -> None:
    if copy.get("artifactKind") != "weave-admin-cicd-admin-console-copy-v1":
        fail("Admin Console copy artifact kind mismatch")
    if set(copy.get("setupStates", [])) != REQUIRED_STATES:
        fail("Admin Console copy must cover the complete setup state model")
    missing = copy.get("missingSecretCopy", {})
    for name in ["WEAVE_FORGEJO_TOKEN", "WEAVE_FORGEJO_API_URL", "FORGEJO_ACTIONS_RUNNER_REGISTRATION"]:
        if name not in missing.get("displayedNames", []):
            fail(f"missing-secret copy must display required name {name}")
    for forbidden in ["secret values", "tokens", "raw CI logs", "provider payloads", "credential-bearing URLs", "tenant URLs", "member content"]:
        if forbidden not in missing.get("forbiddenDisplay", []):
            fail(f"copy must forbid displaying {forbidden}")
    if len(copy.get("progressCopy", [])) < 4 or len(copy.get("failClosedCopy", [])) < 6:
        fail("copy fixture must cover progress and fail-closed states")


def validate_docs() -> None:
    for path in [CONTRACT, EVIDENCE, FEATURE, UI_PLAN]:
        if not path.exists():
            fail(f"missing {path.relative_to(ROOT)}")
    contract = CONTRACT.read_text(encoding="utf-8")
    required_fragments = [
        "The Organization/Admin Console is the canonical setup surface",
        "PipelineProviderManifest v1",
        "PipelineRunRef v1",
        "Local Forgejo proof seam",
        "fail closed",
    ]
    for fragment in required_fragments:
        if fragment not in contract:
            fail(f"contract missing fragment {fragment!r}")
    evidence = EVIDENCE.read_text(encoding="utf-8")
    for relpath in [MANIFEST, FORGEJO, COPY, CONTRACT, FEATURE, UI_PLAN]:
        if str(relpath.relative_to(ROOT)) not in evidence:
            fail(f"evidence report missing {relpath.relative_to(ROOT)}")
    feature = FEATURE.read_text(encoding="utf-8")
    if "@sprint26-admin-cicd-setup" not in feature or "runner_missing" not in feature:
        fail("Gherkin feature must map the Sprint 26 local Forgejo runner-missing proof")
    ui_plan = UI_PLAN.read_text(encoding="utf-8")
    for fragment in [
        "Provider-domain selection",
        "Self-hosted fallback",
        "Missing-secret display",
        "Trigger blocked",
        "Run started",
        "Dry-run complete",
        "Migration aborted",
        "Migration applied",
        "Post-reconcile evidence",
        "FORGEJO_ACTIONS_RUNNER_REGISTRATION",
    ]:
        if fragment not in ui_plan:
            fail(f"UI test plan missing {fragment!r}")


def main() -> None:
    manifest = load(MANIFEST)
    forgejo = load(FORGEJO)
    copy = load(COPY)
    for label, artifact in [("manifest", manifest), ("forgejo", forgejo), ("copy", copy)]:
        if artifact.get("supportSafe") is not True:
            fail(f"{label} must be supportSafe")
        assert_support_safe(artifact, label)
    validate_manifest(manifest)
    validate_forgejo(forgejo)
    validate_copy(copy)
    validate_docs()
    print("admin-cicd-orchestration-check: ok issue=659 local_forgejo=preflight_blocked runner_missing")
    print("SPRINT26_ADMIN_CICD_SETUP_PROOF")


if __name__ == "__main__":
    main()
