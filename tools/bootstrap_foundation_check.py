#!/usr/bin/env python3
"""Validate the stable Weave bootstrap foundation contract.

This is intentionally CI-safe: it validates docs and machine-readable contract
fixtures only. It must not start Docker, call providers, inspect secrets, or
perform live infrastructure mutation.
"""

from __future__ import annotations

import json
import re
import sys
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[1]
DOC = ROOT / "docs" / "bootstrap-foundation-contract.md"
PROFILES = ROOT / "release" / "bootstrap-foundation" / "bootstrap-profiles.v1.json"
COMPONENTS = ROOT / "release" / "bootstrap-foundation" / "component-matrix.v1.json"
README = ROOT / "README.md"
MKDOCS = ROOT / "mkdocs.yml"
CONTROL_DOC = ROOT / "docs" / "weave-control-bootstrap-to-client-contract.md"
INFRA_DOC = ROOT / "docs" / "control-plane-infra-bootstrap.md"
ADMIN_README = ROOT / "admin-console" / "README.md"
INFRA_README = ROOT / "infra" / "README.md"
LOCAL_BOOTSTRAP = ROOT / "infra" / "docs" / "local-bootstrap.md"
FEATURE = ROOT / "e2e" / "features" / "weave_control_modes.feature"

REQUIRED_PROFILES = {
    "local-minimal",
    "local-dogfood",
    "local-lan-dogfood",
    "external-providers",
    "hybrid",
    "full-selfhosted",
}
REQUIRED_MODES = {"deploy_new", "attach_existing", "hybrid"}
REQUIRED_COMPONENTS = {
    "bootstrap-weave-control",
    "weave-server",
    "admin-console",
    "provider-stack-infra",
    "cicd-target",
    "member-client",
}
FORBIDDEN_MEMBER_INPUTS = {
    "OIDC issuer",
    "OIDC client ID",
    "Matrix URL",
    "Nextcloud URL",
    "provider hostname",
    "TLS certificate",
    "provider diagnostic",
    "SecretRef",
    "CredentialRef",
    "credential URL",
    "CI/CD target",
    "bootstrap diagnostics",
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
    print(f"bootstrap-foundation-check: {message}", file=sys.stderr)
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
            assert_support_safe(child, label, (*path, str(key)))
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
    profiles = load(PROFILES)
    components = load(COMPONENTS)
    for artifact, kind in [
        (profiles, "bootstrap-profile-matrix"),
        (components, "bootstrap-component-matrix"),
    ]:
        if artifact.get("artifactKind") != kind:
            fail(f"artifact kind mismatch for {kind}")
        if artifact.get("supportSafe") is not True:
            fail(f"{kind} must be supportSafe=true")
        assert_support_safe(artifact, kind)

    profile_ids = {item.get("id") for item in profiles.get("profiles", []) if isinstance(item, dict)}
    if profile_ids != REQUIRED_PROFILES:
        fail(f"profile matrix mismatch: {sorted(profile_ids)}")
    if set(profiles.get("requiredModes", [])) != REQUIRED_MODES:
        fail("required mode matrix mismatch")
    for item in profiles.get("profiles", []):
        if item.get("controlPlane") != "deploy":
            fail(f"profile {item.get('id')} must deploy the Control Plane")
        if item.get("providerStack") in {None, "mandatory"}:
            fail(f"profile {item.get('id')} must make provider stack semantics explicit and profile-driven")
        steps = item.get("finalOperatorSteps", [])
        if not (1 <= len(steps) <= 2):
            fail(f"profile {item.get('id')} must have one or two final operator steps")
    handoff = profiles.get("memberHandoffInvariant", {})
    if handoff.get("clientDeploymentByBootstrap") is not False:
        fail("bootstrap must not deploy the member client")
    missing_inputs = FORBIDDEN_MEMBER_INPUTS.difference(handoff.get("forbiddenMemberInputs", []))
    if missing_inputs:
        fail(f"member handoff invariant missing forbidden inputs: {sorted(missing_inputs)}")

    component_ids = {item.get("id") for item in components.get("components", []) if isinstance(item, dict)}
    if component_ids != REQUIRED_COMPONENTS:
        fail(f"component matrix mismatch: {sorted(component_ids)}")
    by_id = {item.get("id"): item for item in components.get("components", []) if isinstance(item, dict)}
    if by_id["member-client"].get("deployedByBootstrap") is not False:
        fail("member client must not be deployed by bootstrap")
    if by_id["provider-stack-infra"].get("deployedByBootstrap") != "profile_driven":
        fail("provider stack must be profile-driven")
    definition = components.get("controlPlaneDefinition", {})
    if set(definition.get("requiredComponents", [])) != {"weave-server", "admin-console"}:
        fail("Control Plane must be exactly server plus admin-console at this foundation layer")
    terminal = components.get("terminalBooleans", {})
    if "weave_client_e2e_passed" not in terminal.get("clientLaneOnly", []):
        fail("client E2E boolean must remain client-lane-only")
    if "client_bootstrap_handoff_ready" not in terminal.get("deploymentLane", []):
        fail("deployment lane must emit client_bootstrap_handoff_ready")

    assert_contains(
        DOC,
        [
            "Bootstrap foundation contract",
            "Control Plane = server/backend + admin-console web UI",
            "bootstrap deploys the Control Plane and optional Provider Stack; it does not deploy the member client",
            "weavectl bootstrap plan --profile <profile> --target <provider-lane>",
            "weavectl bootstrap apply --plan <plan-ref>",
            "Do not use `npm exec vite` as the reproducible Admin Console deployment target",
            "Provider Stack / Infra is optional and profile-driven",
            "Flutter/mobile/desktop/web clients are not deployed by bootstrap",
        ] + [f"`{profile_id}`" for profile_id in sorted(REQUIRED_PROFILES)],
    )
    assert_contains(README, ["Bootstrap foundation", "docs/bootstrap-foundation-contract.md", "Control Plane = Weave Server + Admin Console"])
    assert_contains(MKDOCS, ["Bootstrap foundation contract: bootstrap-foundation-contract.md"])
    assert_contains(CONTROL_DOC, ["Bootstrap foundation", "bootstrap-foundation-contract.md"])
    assert_contains(INFRA_DOC, ["Provider Stack / Infra is optional", "bootstrap-foundation-contract.md"])
    assert_contains(ADMIN_README, ["Control Plane", "Weave Server + Admin Console", "Vite is development-only"])
    assert_contains(INFRA_README, ["provider-stack implementation", "bootstrap-foundation-contract.md"])
    assert_contains(LOCAL_BOOTSTRAP, ["provider-stack implementation path", "not the canonical product bootstrap entrypoint"])
    assert_contains(FEATURE, ["Bootstrap deploys the Control Plane as server plus Admin Console"])

    print("bootstrap-foundation-check: ok profiles=6 components=6 control_plane=server+admin-console")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
