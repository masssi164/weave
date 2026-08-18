#!/usr/bin/env python3
"""Validate the stable Weave bootstrap foundation contract.

This is intentionally CI-safe: it validates docs and machine-readable contract
fixtures only. It must not start Docker, call providers, inspect secrets, or
perform live infrastructure mutation.
"""

from __future__ import annotations

import json
import re
import shutil
import subprocess
import sys
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[1]
DOC = ROOT / "docs" / "bootstrap-foundation-contract.md"
PROFILES = ROOT / "release" / "bootstrap-foundation" / "bootstrap-profiles.v1.json"
ENVIRONMENT_PROFILES = ROOT / "release" / "bootstrap-foundation" / "environment-profiles.v1.json"
COMPONENTS = ROOT / "release" / "bootstrap-foundation" / "component-matrix.v1.json"
README = ROOT / "README.md"
MKDOCS = ROOT / "mkdocs.yml"
CONTROL_DOC = ROOT / "docs" / "weave-control-bootstrap-to-client-contract.md"
INFRA_DOC = ROOT / "docs" / "control-plane-infra-bootstrap.md"
ADMIN_README = ROOT / "admin-console" / "README.md"
INFRA_README = ROOT / "infra" / "README.md"
LOCAL_BOOTSTRAP = ROOT / "infra" / "docs" / "local-bootstrap.md"
FEATURE = ROOT / "e2e" / "features" / "weave_control_modes.feature"
WEAVECTL = ROOT / "tools" / "weavectl"
BOOTSTRAP_RUNTIME_EVIDENCE = ROOT / "build" / "evidence" / "bootstrap-foundation" / "foundation-check"

REQUIRED_PROFILES = {
    "local-minimal",
    "local-dogfood",
    "local-lan-dogfood",
    "external-providers",
    "hybrid",
    "full-selfhosted",
}
REQUIRED_MODES = {"deploy_new", "attach_existing", "hybrid"}
REQUIRED_ENVIRONMENT_PROFILES = {"local-dogfood", "local-lan-dogfood", "production"}
REQUIRED_ENVIRONMENT_VARIABLES = {
    "tenant_slug",
    "tenant_domain",
    "public_scheme",
    "auth_subdomain",
    "api_subdomain",
    "admin_subdomain",
    "matrix_subdomain",
    "nextcloud_subdomain",
    "proxy_host_port",
    "proxy_http_host_port",
    "local_lan_host",
}
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


def run_command(args: list[str]) -> str:
    try:
        completed = subprocess.run(args, cwd=ROOT, check=True, text=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=20)
    except subprocess.CalledProcessError as exc:
        fail(f"command failed {' '.join(args)}: {exc.stderr.strip() or exc.stdout.strip()}")
    except subprocess.TimeoutExpired:
        fail(f"command timed out {' '.join(args)}")
    return completed.stdout


def run_command_expect_failure(args: list[str], expected_fragment: str) -> None:
    try:
        completed = subprocess.run(args, cwd=ROOT, check=False, text=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=20)
    except subprocess.TimeoutExpired:
        fail(f"command timed out {' '.join(args)}")
    output = f"{completed.stdout}\n{completed.stderr}"
    if completed.returncode == 0:
        fail(f"command unexpectedly succeeded {' '.join(args)}")
    if expected_fragment not in output:
        fail(f"command failure for {' '.join(args)} did not include {expected_fragment!r}: {output.strip()}")


def write_case_plan(case_name: str, payload: dict[str, Any]) -> Path:
    case_dir = BOOTSTRAP_RUNTIME_EVIDENCE / "negative-cases" / case_name
    case_dir.mkdir(parents=True, exist_ok=True)
    path = case_dir / "plan.json"
    path.write_text(json.dumps(payload, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    return path


def extract_output_ref(output: str, key: str) -> str:
    prefix = f"{key}="
    for line in output.splitlines():
        if line.startswith(prefix):
            return line[len(prefix):].strip()
    fail(f"command output missing {key}= line")


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


def assert_bootstrap_runtime() -> None:
    if BOOTSTRAP_RUNTIME_EVIDENCE.exists():
        shutil.rmtree(BOOTSTRAP_RUNTIME_EVIDENCE)
    plan_output = run_command([
        "python3",
        str(WEAVECTL.relative_to(ROOT)),
        "bootstrap",
        "plan",
        "--profile",
        "local-lan-dogfood",
        "--target",
        "local-shell",
        "--lan-host",
        "192.168.0.10",
        "--run-id",
        "foundation-check",
        "--output-dir",
        "build/evidence/bootstrap-foundation",
    ])
    plan_ref = extract_output_ref(plan_output, "plan_ref")
    plan_path = BOOTSTRAP_RUNTIME_EVIDENCE / "plan.json"
    plan = load(plan_path)
    if plan.get("schemaVersion") != "weave.bootstrap.plan.v1":
        fail("weavectl bootstrap plan emitted wrong schema")
    if plan.get("profile") != "local-lan-dogfood" or plan.get("targetProviderLane") != "local-shell":
        fail("weavectl bootstrap plan emitted wrong profile/target")
    mutation = plan.get("mutationBoundary", {})
    if mutation.get("defaultApplyMode") != "dry_run_validate_only":
        fail("weavectl bootstrap plan must default to dry-run apply")
    if mutation.get("requiresApprovalRefForMutation") is not True:
        fail("weavectl bootstrap plan must require an approval ref for mutation")
    member_boundary = plan.get("memberClientBoundary", {})
    if member_boundary.get("deployedByBootstrap") is not False:
        fail("weavectl bootstrap plan must not deploy the member client")
    if member_boundary.get("endpointPolicy", {}).get("endpointClass") != "rfc1918-lan-ip":
        fail("weavectl bootstrap plan must validate LAN endpoint class without storing raw endpoint")
    assert_support_safe(plan, "weavectl bootstrap plan")

    apply_output = run_command([
        "python3",
        str(WEAVECTL.relative_to(ROOT)),
        "bootstrap",
        "apply",
        "--plan",
        plan_ref,
        "--output-dir",
        "build/evidence/bootstrap-foundation",
    ])
    receipt_path = extract_output_ref(apply_output, "receipt")
    receipt = load(ROOT / receipt_path)
    if receipt.get("schemaVersion") != "weave.bootstrap.apply-receipt.v1":
        fail("weavectl bootstrap apply emitted wrong schema")
    if receipt.get("mode") != "dry_run_validate_only" or receipt.get("executorResult") != "not_dispatched":
        fail("weavectl bootstrap apply must be dry-run/non-dispatch by default")
    if receipt.get("terminalBooleans", {}).get("client_bootstrap_handoff_ready") is not False:
        fail("bootstrap apply receipt must not claim client handoff readiness from dry-run")
    if receipt.get("approvalRef") is not None or receipt.get("approvalRefHash") is not None:
        fail("dry-run receipt must not persist an approval ref")
    assert_support_safe(receipt, "weavectl bootstrap apply receipt")

    malicious_executor = dict(plan)
    malicious_executor["mutationBoundary"] = dict(plan["mutationBoundary"], executor="/usr/bin/true")
    malicious_executor_path = write_case_plan("malicious-executor", malicious_executor)
    run_command_expect_failure([
        "python3", str(WEAVECTL.relative_to(ROOT)), "bootstrap", "apply",
        "--plan", str(malicious_executor_path), "--execute", "--approval-ref", "APPROVAL-123",
    ], "bootstrap executor must be the allowlisted repo-relative path")

    traversal_executor = dict(plan)
    traversal_executor["mutationBoundary"] = dict(plan["mutationBoundary"], executor="../../usr/bin/true")
    traversal_executor_path = write_case_plan("traversal-executor", traversal_executor)
    run_command_expect_failure([
        "python3", str(WEAVECTL.relative_to(ROOT)), "bootstrap", "apply",
        "--plan", str(traversal_executor_path), "--execute", "--approval-ref", "APPROVAL-123",
    ], "bootstrap executor must be the allowlisted repo-relative path")

    nonallowlisted_executor = dict(plan)
    nonallowlisted_executor["mutationBoundary"] = dict(plan["mutationBoundary"], executor="tools/weavectl")
    nonallowlisted_executor_path = write_case_plan("nonallowlisted-executor", nonallowlisted_executor)
    run_command_expect_failure([
        "python3", str(WEAVECTL.relative_to(ROOT)), "bootstrap", "apply",
        "--plan", str(nonallowlisted_executor_path), "--execute", "--approval-ref", "APPROVAL-123",
    ], "bootstrap executor is not allowlisted for this profile/target")

    run_command_expect_failure([
        "python3", str(WEAVECTL.relative_to(ROOT)), "bootstrap", "apply",
        "--plan", str(plan_path.relative_to(ROOT)), "--execute", "--approval-ref", "https://example.invalid/ticket",
    ], "--approval-ref must be a support-safe ticket id")

    forged_ref_plan = dict(plan)
    forged_ref_plan["planRef"] = "bootstrap-plan-forged"
    forged_ref_path = write_case_plan("forged-plan-ref", forged_ref_plan)
    run_command_expect_failure([
        "python3", str(WEAVECTL.relative_to(ROOT)), "bootstrap", "apply",
        "--plan", str(forged_ref_path),
    ], "planRef does not match plan content")

    forged_output = run_command([
        "python3", str(WEAVECTL.relative_to(ROOT)), "bootstrap", "plan",
        "--profile", "external-providers", "--target", "github-actions",
        "--run-id", "foundation-check-forged", "--output-dir", "build/evidence/bootstrap-foundation",
    ])
    forged_plan_path = ROOT / extract_output_ref(forged_output, "plan")
    forged_plan = load(forged_plan_path)
    forged_plan["mutationBoundary"] = dict(forged_plan["mutationBoundary"], executeSupportedNow=True, executor="infra/weave-workspace/install.sh")
    forged_path = write_case_plan("forged-execute-supported", forged_plan)
    run_command_expect_failure([
        "python3", str(WEAVECTL.relative_to(ROOT)), "bootstrap", "apply",
        "--plan", str(forged_path), "--execute", "--approval-ref", "APPROVAL-123",
    ], "--execute is not allowed")

    unsafe_plan_input = dict(plan)
    unsafe_plan_input["forbiddenClaims"] = [*plan.get("forbiddenClaims", []), "access_token=SHOULD_BE_REJECTED"]
    unsafe_plan_input_path = write_case_plan("unsafe-plan-input", unsafe_plan_input)
    run_command_expect_failure([
        "python3", str(WEAVECTL.relative_to(ROOT)), "bootstrap", "apply",
        "--plan", str(unsafe_plan_input_path),
    ], "support-safe redaction blocked artifact")

    unsafe_receipt_plan = dict(plan)
    unsafe_receipt_plan["providerStack"] = dict(plan["providerStack"], ref="access_token=unsafe")
    unsafe_receipt_path = write_case_plan("unsafe-receipt", unsafe_receipt_plan)
    run_command_expect_failure([
        "python3", str(WEAVECTL.relative_to(ROOT)), "bootstrap", "apply",
        "--plan", str(unsafe_receipt_path),
    ], "support-safe redaction blocked artifact")


def assert_environment_profile_contract(environment_profiles: dict[str, Any]) -> None:
    if environment_profiles.get("invariant") != "one deployable shape; profile variables select endpoint class, DNS/TLS posture, provider lane, mutation approval, and evidence gates":
        fail("environment profile contract must preserve one deployable shape invariant")
    if "live infrastructure" not in str(environment_profiles.get("noLiveMutationBoundary", "")):
        fail("environment profile contract must name the no-live-mutation boundary")
    if set(environment_profiles.get("requiredVariables", [])) != REQUIRED_ENVIRONMENT_VARIABLES:
        fail("environment profile required variable set mismatch")
    by_id = {item.get("id"): item for item in environment_profiles.get("profiles", []) if isinstance(item, dict)}
    if set(by_id) != REQUIRED_ENVIRONMENT_PROFILES:
        fail(f"environment profile matrix mismatch: {sorted(by_id)}")
    if by_id["local-dogfood"].get("tenantDomainDefault") != "weave.test":
        fail("local-dogfood must default to the reserved weave.test domain")
    if by_id["local-lan-dogfood"].get("localLanHost") != "required-non-canonical-break-glass":
        fail("local LAN host must be explicit and non-canonical")
    production = by_id["production"]
    if production.get("localLanHost") != "forbidden" or production.get("requiresPublicDns") is not True or production.get("requiresTrustedInternetTls") is not True:
        fail("production profile must require public DNS/trusted TLS and forbid local LAN host")
    for item in by_id.values():
        if item.get("mutationAllowedByDefault") is not False or item.get("approvalRequiredForMutation") is not True:
            fail(f"environment profile {item.get('id')} must fail closed for mutation")
        gates = item.get("evidenceGates", [])
        if not isinstance(gates, list) or not gates:
            fail(f"environment profile {item.get('id')} must name evidence gates")


def main() -> int:
    profiles = load(PROFILES)
    environment_profiles = load(ENVIRONMENT_PROFILES)
    components = load(COMPONENTS)
    for artifact, kind in [
        (profiles, "bootstrap-profile-matrix"),
        (environment_profiles, "environment-profile-contract"),
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

    assert_environment_profile_contract(environment_profiles)

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
    assert_contains(README, ["Bootstrap foundation", "docs/bootstrap-foundation-contract.md"])
    assert_contains(MKDOCS, ["Bootstrap foundation contract: bootstrap-foundation-contract.md"])
    assert_contains(CONTROL_DOC, ["Bootstrap foundation", "bootstrap-foundation-contract.md"])
    assert_contains(INFRA_DOC, ["Provider Stack / Infra is optional", "bootstrap-foundation-contract.md"])
    assert_contains(ADMIN_README, ["Control Plane", "Weave Server + Admin Console", "Vite is development-only"])
    assert_contains(INFRA_README, ["provider-stack implementation", "bootstrap-foundation-contract.md"])
    assert_contains(LOCAL_BOOTSTRAP, ["local/dev infrastructure path", "not the canonical product bootstrap entrypoint"])
    assert_contains(FEATURE, ["Bootstrap deploys the Control Plane as server plus Admin Console"])
    assert_bootstrap_runtime()

    print("bootstrap-foundation-check: ok profiles=6 components=6 control_plane=server+admin-console runtime=plan/apply")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
