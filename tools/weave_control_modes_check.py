#!/usr/bin/env python3
"""Validate Weave Control setup-mode scenario fixtures for issues #681/#685."""

from __future__ import annotations

import json
import re
import sys
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[1]
FEATURE = ROOT / "e2e" / "features" / "weave_control_modes.feature"
MAPPING = ROOT / "e2e" / "scenario_mappings.json"
CATALOG = ROOT / "e2e" / "suites" / "scenario_catalog.json"
CONTRACT = ROOT / "docs" / "weave-control-bootstrap-to-client-contract.md"
ADMIN_FIRST_USE = ROOT / "docs" / "admin-provisioned-first-use.md"
MODE_MATRIX = ROOT / "release" / "provider-lab" / "weave-control-modes" / "weave-control-mode-matrix.fixture.json"
ATTACH_FIXTURE = ROOT / "release" / "provider-lab" / "weave-control-modes" / "attach-existing-preflight.fixture.json"
HYBRID_FIXTURE = ROOT / "release" / "provider-lab" / "weave-control-modes" / "hybrid-member-manifest.fixture.json"

REQUIRED_MODES = {"deploy_new", "attach_existing", "hybrid"}
REQUIRED_CLAIM_STATES = {
    "dispatch_preflight_only",
    "pipeline_terminal_success",
    "stack_readiness_passed",
    "weave_e2e_passed",
    "member_provider_neutral_join_passed",
}
REQUIRED_TAGS = {
    "@weave-control-plan-preflight-modes",
    "@weave-control-deploy-new-local-forgejo-e2e-boundary",
    "@weave-control-attach-existing-preflight-boundary",
    "@weave-control-hybrid-domain-separation",
    "@weave-control-member-bootstrap-invariant",
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
    print(f"weave-control-modes-check: {message}", file=sys.stderr)
    raise SystemExit(1)


def read(path: Path) -> str:
    try:
        return path.read_text(encoding="utf-8")
    except FileNotFoundError:
        fail(f"missing {path.relative_to(ROOT)}")


def load(path: Path) -> dict[str, Any]:
    try:
        decoded = json.loads(read(path))
    except json.JSONDecodeError as exc:
        fail(f"invalid JSON in {path.relative_to(ROOT)}: {exc}")
    if not isinstance(decoded, dict):
        fail(f"{path.relative_to(ROOT)} must contain an object")
    return decoded


def assert_contains(path: Path, fragments: list[str]) -> None:
    text = read(path)
    for fragment in fragments:
        if fragment not in text:
            fail(f"{path.relative_to(ROOT)} missing {fragment!r}")


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
                fail(f"{label} contains support-unsafe pattern at {'.'.join(path)}")


def main() -> int:
    feature_text = read(FEATURE)
    for tag in REQUIRED_TAGS:
        if tag not in feature_text:
            fail(f"feature missing {tag}")
    assert_contains(
        FEATURE,
        [
            "deploy_new",
            "attach_existing",
            "hybrid",
            "pipeline_terminal_success",
            "stack_readiness_passed",
            "weave_e2e_passed",
            "member_provider_neutral_join_passed",
            "dispatch_preflight_only",
            "GitHub-only Live Stack evidence is not counted",
        ],
    )

    matrix = load(MODE_MATRIX)
    attach = load(ATTACH_FIXTURE)
    hybrid = load(HYBRID_FIXTURE)
    for artifact, kind in [
        (matrix, "weave-control-mode-matrix-v1"),
        (attach, "weave-control-attach-existing-preflight-v1"),
        (hybrid, "weave-control-hybrid-member-manifest-v1"),
    ]:
        if artifact.get("artifactKind") != kind:
            fail(f"fixture kind mismatch for {kind}")
        if artifact.get("supportSafe") is not True:
            fail(f"{kind} must be supportSafe")
        assert_support_safe(artifact, kind)

    modes = {item.get("mode") for item in matrix.get("modes", []) if isinstance(item, dict)}
    if modes != REQUIRED_MODES:
        fail(f"mode matrix mismatch: {modes}")
    if set(matrix.get("claimStates", {})) != REQUIRED_CLAIM_STATES:
        fail("claim state matrix mismatch")
    invariant = matrix.get("planPreflightInvariant", {})
    if invariant.get("rawSecretsAccepted") is not False or invariant.get("mutationBeforeApprovalAllowed") is not False:
        fail("plan preflight invariant must fail closed before mutation")

    if attach.get("mode") != "attach_existing":
        fail("attach fixture mode mismatch")
    if "deploy_new_pipeline_run" not in attach.get("cannotBeSatisfiedBy", []):
        fail("attach-existing fixture must not be satisfiable by deploy-new pipeline")
    boundary = attach.get("mutationBoundary", {})
    for key in ["providerRedeployPlanned", "destructiveMigrationPlanned", "credentialRotationPlanned"]:
        if boundary.get(key) is not False:
            fail(f"attach-existing boundary must keep {key}=false")
    if boundary.get("separateApprovalRequiredForMutation") is not True:
        fail("attach-existing mutation must require separate approval")

    if hybrid.get("mode") != "hybrid":
        fail("hybrid fixture mode mismatch")
    domain_modes = {item.get("mode") for item in hybrid.get("domainModes", []) if isinstance(item, dict)}
    if not {"deploy_new", "attach_existing"}.issubset(domain_modes):
        fail("hybrid fixture must include deploy_new and attach_existing domains")
    manifest = hybrid.get("memberManifest", {})
    if manifest.get("coherentOrganizationView") is not True:
        fail("hybrid member manifest must stay coherent")
    for key in [
        "providerSetupControlsVisible",
        "oidcEndpointSetupVisible",
        "cicdTargetsVisible",
        "secretRefsVisible",
        "bootstrapDiagnosticsVisible",
        "rawProviderErrorsVisible",
    ]:
        if manifest.get(key) is not False:
            fail(f"hybrid member manifest leaks {key}")

    assert_contains(
        CONTRACT,
        [
            "| `deploy_new` |",
            "| `attach_existing` |",
            "| `hybrid` |",
            "dispatch_preflight_only",
            "pipeline_terminal_success",
            "stack_readiness_passed",
            "weave_e2e_passed",
            "GitHub-only Live Stack evidence is not a substitute",
        ],
    )
    assert_contains(
        ADMIN_FIRST_USE,
        [
            "deploy-new, attach-existing, and hybrid bootstrap modes are Weave Control concepts only",
            "Members enter or open only an organization auth URL, invite link, or deep link",
        ],
    )
    assert_contains(MAPPING, sorted(REQUIRED_TAGS))
    assert_contains(CATALOG, sorted(REQUIRED_TAGS) + ["weave-control-setup-modes"])

    print("weave-control-modes-check: ok issues=681,685 modes=deploy_new,attach_existing,hybrid")
    print("WEAVE_CONTROL_MODE_MATRIX")
    print("WEAVE_CONTROL_DEPLOY_NEW_E2E_BOUNDARY")
    print("WEAVE_CONTROL_ATTACH_EXISTING_PREFLIGHT")
    print("WEAVE_CONTROL_HYBRID_DOMAIN_SEPARATION")
    print("WEAVE_CONTROL_MEMBER_BOOTSTRAP_INVARIANT")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
