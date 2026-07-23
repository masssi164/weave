#!/usr/bin/env python3
"""Narrow, idempotent operator interface for the Weave Compose model."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import platform
import re
import secrets
import stat
import subprocess
import sys
from pathlib import Path

from compose_env import (
    ComposeContext,
    ContractError,
    compose_environment,
    load_context,
    run,
    specification_context,
)

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "keycloak"))



COMMANDS = (
    "secrets-init",
    "render",
    "config",
    "prepare",
    "provider-prepare",
    "up",
    "down",
    "ps",
    "logs",
    "keycloak-plan",
    "keycloak-apply",
    "keycloak-verify",
)
VOLUME_KEYS = (
    "WEAVE_CADDY_DATA_VOLUME",
    "WEAVE_CADDY_CONFIG_VOLUME",
    "WEAVE_DB_DATA_VOLUME",
    "WEAVE_KEYCLOAK_DATA_VOLUME",
    "WEAVE_MAILPIT_DATA_VOLUME",
    "WEAVE_NEXTCLOUD_DATA_VOLUME",
    "WEAVE_SYNAPSE_DATA_VOLUME",
    "WEAVE_MATRIX_APPSERVICE_VOLUME",
)


def _script(context: ComposeContext, name: str, *arguments: str) -> None:
    command = ["python3", str(context.root / "scripts" / name), context.profile, "--root", str(context.root)]
    if context.profile_env_file != context.root / f"environments/{context.profile}.env":
        command.extend(("--env-file", str(context.profile_env_file)))
    command.extend(arguments)
    subprocess.run(command, cwd=context.root, env=compose_environment(context), check=True)


def _compose(context: ComposeContext, *arguments: str, capture: bool = False) -> subprocess.CompletedProcess[str]:
    return run((*context.compose_base_command, *arguments), context, capture=capture)


def _labels(context: ComposeContext) -> dict[str, str]:
    return {
        "com.massimotter.weave.managed": "true",
        "com.massimotter.weave.environment": context.env["WEAVE_ENVIRONMENT"],
        "com.massimotter.weave.namespace": context.env["WEAVE_RESOURCE_PREFIX"],
        "com.massimotter.weave.scope": context.env["WEAVE_STACK_SCOPE"],
    }


def _inspect_labels(kind: str, name: str) -> dict[str, str] | None:
    result = subprocess.run(
        ["docker", kind, "inspect", name, "--format", "{{json .Labels}}"],
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.DEVNULL,
    )
    if result.returncode != 0:
        return None
    value = json.loads(result.stdout)
    return value if isinstance(value, dict) else {}


def _adoption_allows(context: ComposeContext, kind: str, name: str) -> bool:
    receipt_path = os.environ.get("WEAVE_ADOPTION_RECEIPT", "")
    if not receipt_path:
        return False
    path = Path(receipt_path).expanduser().resolve()
    if path.is_symlink() or not path.is_file():
        raise ContractError("WEAVE_ADOPTION_RECEIPT must be a regular file")
    receipt = json.loads(path.read_text(encoding="utf-8"))
    expected = {
        "schemaVersion": "weave.compose-adoption-receipt.v1",
        "profile": context.profile,
        "composeProject": context.env["WEAVE_COMPOSE_PROJECT"],
        "backupVerified": True,
        "isolatedRestoreVerified": True,
        "supportSafe": True,
        "containsSecretValues": False,
    }
    if any(receipt.get(key) != value for key, value in expected.items()):
        raise ContractError("adoption receipt does not bind the exact profile/project and successful backup/restore proof")
    candidate = os.environ.get("WEAVE_CANDIDATE_COMMIT", "")
    if not re.fullmatch(r"[0-9a-f]{40}", candidate) or receipt.get("candidateCommit") != candidate:
        raise ContractError("adoption receipt does not bind the current exact candidate")
    resources = receipt.get("resources", [])
    return {"kind": kind, "name": name} in resources


def _ensure_resource(context: ComposeContext, kind: str, name: str) -> None:
    expected = _labels(context)
    observed = _inspect_labels(kind, name)
    if observed is not None:
        if all(observed.get(key) == value for key, value in expected.items()):
            return
        if _adoption_allows(context, kind, name):
            return
        raise ContractError(f"refusing unowned existing Docker {kind} {name}; use an exact backup/restore adoption receipt")
    command = ["docker", kind, "create"]
    for key, value in sorted(expected.items()):
        command.extend(("--label", f"{key}={value}"))
    command.append(name)
    subprocess.run(command, check=True, stdout=subprocess.DEVNULL)


def prepare(context: ComposeContext) -> None:
    _assert_render(context)
    _ensure_resource(context, "network", context.env["WEAVE_DOCKER_NETWORK"])
    for key in VOLUME_KEYS:
        name = context.env.get(key, "")
        if not name:
            raise ContractError(f"missing stable volume name {key}")
        _ensure_resource(context, "volume", name)


def _assert_render(context: ComposeContext) -> dict[str, object]:
    path = context.generated_root / "render-manifest.json"
    if path.is_symlink() or not path.is_file():
        raise ContractError("render-manifest.json is missing; run render first")
    manifest = json.loads(path.read_text(encoding="utf-8"))
    if manifest.get("schemaVersion") != "weave.compose-render.v1" or manifest.get("profile") != context.profile:
        raise ContractError("render manifest does not match the selected profile")
    desired = json.loads((context.generated_root / "keycloak/desired-state.json").read_text(encoding="utf-8"))
    if manifest.get("desiredStateRevision") != desired.get("revision"):
        raise ContractError("render manifest and desired state revision differ")
    return manifest


def _assert_secret_safe_model(context: ComposeContext, model: str) -> None:
    for path in context.secret_root.iterdir():
        if path.is_file() and not path.is_symlink():
            value = path.read_text(encoding="utf-8", errors="ignore").strip()
            if value and value in model:
                raise ContractError(f"normalized Compose model contains the value of mounted secret {path.name}")
    forbidden = ("WEAVE_MCP_EXCHANGE_CLIENT_SECRET", "mcp:tools")
    finding = next((item for item in forbidden if item in model), None)
    if finding:
        raise ContractError(f"normalized Compose model contains retired contract {finding}")


def _git_output(context: ComposeContext, *arguments: str) -> str:
    result = subprocess.run(
        ["git", "-C", str(context.repository_root), *arguments],
        check=False,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.DEVNULL,
    )
    return result.stdout.strip() if result.returncode == 0 else ""


def _assert_local_image_provenance(context: ComposeContext) -> None:
    local = {
        name: context.env[name]
        for name in (
            "WEAVE_BACKEND_IMAGE",
            "WEAVE_KEYCLOAK_IMAGE",
            "WEAVE_KEYCLOAK_SANITIZER_IMAGE",
            "WEAVE_MCP_IMAGE",
        )
        if context.env.get(name, "").startswith("sha256:")
    }
    if not local:
        return
    if context.profile not in {"dev", "dogfood"}:
        raise ContractError("immutable local image IDs are restricted to dev, dogfood, and isolated E2E")
    candidate = os.environ.get("WEAVE_CANDIDATE_COMMIT", "")
    if not re.fullmatch(r"[0-9a-f]{40}", candidate):
        raise ContractError("local candidate images require exact WEAVE_CANDIDATE_COMMIT provenance")
    source_candidate = os.environ.get("WEAVE_IMAGE_SOURCE_COMMIT", "")
    if not source_candidate and context.profile == "dev":
        source_candidate = candidate
    if not re.fullmatch(r"[0-9a-f]{40}", source_candidate):
        raise ContractError(
            "local dogfood images require exact WEAVE_IMAGE_SOURCE_COMMIT provenance"
        )
    if context.profile == "dogfood":
        observed_head = _git_output(context, "rev-parse", "HEAD")
        if observed_head != candidate:
            raise ContractError("checked-out HEAD does not equal WEAVE_CANDIDATE_COMMIT")
        ancestry = subprocess.run(
            [
                "git",
                "-C",
                str(context.repository_root),
                "merge-base",
                "--is-ancestor",
                source_candidate,
                candidate,
            ],
            check=False,
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
        )
        source_tree = _git_output(context, "rev-parse", f"{source_candidate}^{{tree}}")
        candidate_tree = _git_output(context, "rev-parse", f"{candidate}^{{tree}}")
        if (
            ancestry.returncode != 0
            or not re.fullmatch(r"[0-9a-f]{40}", source_tree)
            or source_tree != candidate_tree
        ):
            raise ContractError(
                "WEAVE_IMAGE_SOURCE_COMMIT is not a tree-identical ancestor of the lane candidate"
            )
    for name, image_ref in sorted(local.items()):
        result = subprocess.run(
            ["docker", "image", "inspect", image_ref, "--format", "{{json .}}"],
            text=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
        )
        if result.returncode != 0:
            raise ContractError(f"local immutable image is unavailable: {name}")
        inspected = json.loads(result.stdout)
        if inspected.get("Id") != image_ref:
            raise ContractError(f"local image ID changed during provenance validation: {name}")
        labels = (inspected.get("Config") or {}).get("Labels") or {}
        if labels.get("org.opencontainers.image.revision") != source_candidate:
            raise ContractError(
                f"local image is not bound to the exact protected source candidate: {name}"
            )
        if name == "WEAVE_KEYCLOAK_IMAGE" and labels.get(
            "com.massimotter.weave.keycloak.version"
        ) != "26.7.0":
            raise ContractError("local Keycloak image is not the pinned 26.7.0 distribution")
        if name == "WEAVE_KEYCLOAK_SANITIZER_IMAGE" and labels.get(
            "com.massimotter.weave.component"
        ) != "keycloak-admin-sanitizer":
            raise ContractError("local Keycloak sanitizer image has no exact component provenance")


def normalized_config(context: ComposeContext, *, emit: bool) -> str:
    _assert_local_image_provenance(context)
    result = _compose(context, "config", "--format", "json", capture=True)
    model = result.stdout
    _assert_secret_safe_model(context, model)
    parsed = json.loads(model)
    active = parsed.get("services", {})
    expected_absent = {"backend", "mcp", "mcp-secret-check", "mcp-keycloak-connectivity-check"} if context.profile == "dev" else set()
    if expected_absent.intersection(active):
        raise ContractError("host-dev normalized model unexpectedly contains the containerized application tier")
    if context.profile in ("dogfood", "main") and not {
        "backend", "mcp", "mcp-secret-check", "mcp-keycloak-connectivity-check"
    }.issubset(active):
        raise ContractError("persistent normalized model is missing the application tier or JWK permission check")
    if emit:
        print(json.dumps(parsed, indent=2, sort_keys=True))
    return model


def _candidate_commit(context: ComposeContext) -> str:
    candidate = os.environ.get("WEAVE_CANDIDATE_COMMIT", "")
    if re.fullmatch(r"[0-9a-f]{40}", candidate):
        return candidate
    return subprocess.run(
        ["git", "-C", str(context.repository_root), "rev-parse", "HEAD"],
        check=True,
        text=True,
        stdout=subprocess.PIPE,
    ).stdout.strip()


SUPERVISOR_PACKAGE_FILES = (
    "admin_sanitizer.py",
    "crypto_runtime.py",
    "deployment_context.py",
    "desired_state_authority.py",
    "kcadm_driver.py",
    "lease_control.py",
    "receipt.py",
    "reconciler.py",
    "rfc8785.py",
    "sanitizer_daemon.py",
    "supervisor.py",
)
SUPERVISOR_COMMAND_ALLOWLIST = (
    "acquire",
    "stop-keycloak",
    "bootstrap-admin-service",
    "start-keycloak",
    "reconcile-through-sanitizer",
    "probe",
    "teardown",
    "sign-receipt",
)


def _sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(chunk)
    return "sha256:" + digest.hexdigest()


def _root_owned_regular(path: Path, label: str) -> os.stat_result:
    if path.is_symlink() or not path.is_file():
        raise ContractError(f"{label} must be a regular non-symlink file")
    metadata = path.stat()
    if not stat.S_ISREG(metadata.st_mode):
        raise ContractError(f"{label} must be a regular file")
    if metadata.st_uid != 0:
        raise ContractError(f"{label} must be owned by root")
    if metadata.st_mode & (stat.S_IWGRP | stat.S_IWOTH):
        raise ContractError(f"{label} must not be group- or world-writable")
    return metadata


def _public_root_owned_key(path: Path, label: str) -> os.stat_result:
    metadata = _root_owned_regular(path, label)
    if stat.S_IMODE(metadata.st_mode) not in {0o444, 0o644}:
        raise ContractError(f"{label} must be root-owned public material with mode 0444 or 0644")
    return metadata


def _reviewed_supervisor_env_file() -> Path:
    supplied = os.environ.get("WEAVE_KEYCLOAK_REVIEWED_ENV_FILE", "")
    if not supplied:
        raise ContractError("persistent reconciliation requires WEAVE_KEYCLOAK_REVIEWED_ENV_FILE")
    path = Path(supplied).expanduser()
    if not path.is_absolute() or path.is_symlink():
        raise ContractError("reviewed supervisor environment file must be an absolute non-symlink path")
    path = path.resolve()
    metadata = _root_owned_regular(path, "reviewed supervisor environment file")
    if stat.S_IMODE(metadata.st_mode) not in {0o444, 0o644}:
        raise ContractError("reviewed supervisor environment file must be runner-readable mode 0444 or 0644")
    return path


def _supervisor_selection(context: ComposeContext) -> tuple[Path, Path | None]:
    """Resolve the trusted supervisor package without candidate self-assertion.

    Dogfood, main, and isolated evidence must select an externally installed,
    root-owned package.  The adjacent root-owned attestation binds every
    executable module, the fixed command surface, and the host platform.  The
    candidate checkout is accepted only for the non-release local dev loop.
    """

    supplied = os.environ.get("WEAVE_KEYCLOAK_SUPERVISOR", "")
    candidate = (context.root / "keycloak/supervisor.py").resolve()
    if not supplied:
        if context.profile == "dev" and context.isolated_namespace is None:
            if not candidate.is_file() or candidate.is_symlink():
                raise ContractError("development Keycloak supervisor implementation is unavailable")
            return candidate, None
        raise ContractError(
            "persistent and isolated reconciliation requires an externally installed "
            "WEAVE_KEYCLOAK_SUPERVISOR"
        )
    selected = Path(supplied).expanduser()
    if not selected.is_absolute():
        raise ContractError("WEAVE_KEYCLOAK_SUPERVISOR must be an absolute installed path")
    if selected.is_symlink():
        raise ContractError("WEAVE_KEYCLOAK_SUPERVISOR must not be a symlink")
    selected = selected.resolve()
    repository = context.repository_root.resolve()
    if selected == repository or repository in selected.parents:
        raise ContractError("persistent reconciliation cannot execute a supervisor from the candidate checkout")
    _root_owned_regular(selected, "Keycloak supervisor")

    attestation_path = Path(str(selected) + ".attestation.json")
    _root_owned_regular(attestation_path, "Keycloak supervisor platform attestation")
    try:
        attestation = json.loads(attestation_path.read_text(encoding="utf-8"))
    except json.JSONDecodeError as error:
        raise ContractError("Keycloak supervisor platform attestation is malformed") from error
    package = selected.parent
    observed_files: dict[str, str] = {}
    for name in SUPERVISOR_PACKAGE_FILES:
        module = package / name
        _root_owned_regular(module, f"Keycloak supervisor module {name}")
        observed_files[name] = _sha256_file(module)
    package_digest = "sha256:" + hashlib.sha256(
        json.dumps(observed_files, sort_keys=True, separators=(",", ":")).encode("utf-8")
    ).hexdigest()
    trust_key = context.secret_root / "keycloak-supervisor-trust-key.pem"
    _public_root_owned_key(trust_key, "externally governed Keycloak supervisor trust key")
    expected = {
        "schemaVersion": "weave.keycloak-supervisor-platform-attestation.v1",
        "supervisorVersion": "1.0.0",
        "installedPath": str(selected),
        "candidateIndependent": True,
        "controlPlane": "root-owned-run-bound-supervisor",
        "commandAllowlist": list(SUPERVISOR_COMMAND_ALLOWLIST),
        "packageFiles": observed_files,
        "packageDigest": package_digest,
        "trustKeySha256": _sha256_file(trust_key),
        "platform": {
            "system": platform.system().lower(),
            "machine": platform.machine().lower(),
        },
    }
    if any(attestation.get(key) != value for key, value in expected.items()):
        raise ContractError("Keycloak supervisor package/platform attestation does not match this host")
    reference = attestation.get("attestationRef")
    if not isinstance(reference, str) or re.fullmatch(
        r"attestation:keycloak-supervisor:[A-Za-z0-9._:/-]+", reference
    ) is None:
        raise ContractError("Keycloak supervisor platform attestation reference is invalid")
    if attestation.get("keyGenerationRef") != "keyref:keycloak-supervisor/current":
        raise ContractError("platform attestation does not bind the opaque supervisor key generation")
    if "signingKeyPath" in attestation:
        raise ContractError("public platform attestation must not disclose the private signing-key path")
    if attestation.get("privilegedInvocation") != "sudo-noninteractive-fixed-executable":
        raise ContractError("platform attestation does not bind the fixed privileged invocation")
    operator_group = attestation.get("operatorGroup")
    if not isinstance(operator_group, str) or re.fullmatch(r"[a-z_][a-z0-9_-]{0,31}", operator_group) is None:
        raise ContractError("platform attestation operator group is invalid")
    sudoers_path = attestation.get("sudoersPolicyPath")
    if not isinstance(sudoers_path, str) or re.fullmatch(
        r"/etc/sudoers\.d/weave-keycloak-supervisor-[0-9a-f]{20}", sudoers_path
    ) is None:
        raise ContractError("platform attestation sudoers policy path is invalid")
    if not isinstance(attestation.get("sudoersPolicySha256"), str) or re.fullmatch(
        r"sha256:[0-9a-f]{64}", str(attestation.get("sudoersPolicySha256"))
    ) is None:
        raise ContractError("platform attestation sudoers policy digest is invalid")
    approved_images = attestation.get("approvedKeycloakImageDigests")
    expected_image = _image_digest(context.env["WEAVE_KEYCLOAK_IMAGE"])
    if (
        not isinstance(approved_images, list)
        or approved_images != sorted(set(approved_images))
        or any(not isinstance(value, str) or re.fullmatch(r"sha256:[0-9a-f]{64}", value) is None for value in approved_images)
        or expected_image not in approved_images
    ):
        raise ContractError("platform attestation does not approve the exact immutable Keycloak image")
    approved_sanitizers = attestation.get("approvedSanitizerImageDigests")
    expected_sanitizer = _image_digest(context.env["WEAVE_KEYCLOAK_SANITIZER_IMAGE"])
    if (
        not isinstance(approved_sanitizers, list)
        or approved_sanitizers != sorted(set(approved_sanitizers))
        or any(not isinstance(value, str) or re.fullmatch(r"sha256:[0-9a-f]{64}", value) is None for value in approved_sanitizers)
        or expected_sanitizer not in approved_sanitizers
    ):
        raise ContractError("platform attestation does not approve the exact immutable sanitizer image")
    approvals = {
        "packageApprovalRef": r"approval:keycloak-supervisor-package:[A-Za-z0-9._:/-]+",
        "keycloakImageApprovalRef": r"approval:keycloak-image:[A-Za-z0-9._:/-]+",
    }
    if any(
        not isinstance(attestation.get(name), str)
        or re.fullmatch(pattern, str(attestation.get(name))) is None
        for name, pattern in approvals.items()
    ):
        raise ContractError("platform attestation omits the independently reviewed package or image approval")
    return selected, attestation_path


def _image_digest(image: str) -> str:
    if image.startswith("sha256:"):
        return image
    if "@sha256:" in image:
        return "sha256:" + image.rsplit("@sha256:", 1)[1]
    raise ContractError("Keycloak receipt cannot bind a mutable image reference")


def _deployment(context: ComposeContext) -> dict[str, str]:
    scope = "isolated-e2e" if context.isolated_namespace else {
        "dev": "developer",
        "dogfood": "persistent-dogfood",
        "main": "main",
    }[context.profile]
    value = {
        "scope": scope,
        "instanceRef": context.env["WEAVE_DEPLOYMENT_INSTANCE"],
        "composeProject": context.env["WEAVE_COMPOSE_PROJECT"],
    }
    if context.isolated_namespace:
        value["namespace"] = context.isolated_namespace
    return value


def _keycloak(context: ComposeContext, mode: str) -> None:
    _assert_render(context)
    normalized_config(context, emit=False)
    prepare(context)
    # The deployment control store and its forward-only lease schema must be
    # available before the external supervisor can acquire authority.  No
    # Keycloak container stop or bootstrap action is allowed before that
    # session-held lease exists.
    _compose(context, "up", "--detach", "--wait", "postgres", "postgres-reconcile")
    # Materialize the stopped container definition so the external supervisor
    # can attest and control one exact node. `create` does not start Keycloak or
    # grant bootstrap authority; every state-changing runtime action remains
    # behind the fenced supervisor lease.
    _compose(context, "create", "--no-recreate", "keycloak")
    candidate = _candidate_commit(context)
    specification_commit = specification_context(context)[1]
    nonce = os.environ.get("WEAVE_RECONCILIATION_NONCE") or secrets.token_urlsafe(32)
    supervisor, platform_attestation = _supervisor_selection(context)
    supervisor_env_file = (
        _reviewed_supervisor_env_file()
        if platform_attestation is not None
        else context.profile_env_file
    )
    command = [
        str(supervisor),
        mode,
        "--root",
        str(context.root),
        "--profile",
        context.profile,
        "--candidate-commit",
        candidate,
        "--specification-commit",
        specification_commit,
        "--spec-root",
        str(specification_context(context)[0]),
        "--nonce",
        nonce,
        "--env-file",
        str(supervisor_env_file),
        "--stack-scope",
        "isolated" if context.isolated_namespace is not None else "persistent",
        "--keycloak-image",
        context.env["WEAVE_KEYCLOAK_IMAGE"],
        "--sanitizer-image",
        context.env["WEAVE_KEYCLOAK_SANITIZER_IMAGE"],
        "--runtime-uid",
        context.env["WEAVE_RUNTIME_UID"],
        "--runtime-gid",
        context.env["WEAVE_RUNTIME_GID"],
    ]
    if context.isolated_namespace is not None:
        command.extend(("--e2e-run-id", os.environ["WEAVE_E2E_RUN_ID"]))
    if platform_attestation is not None:
        command.extend(("--platform-attestation", str(platform_attestation)))
        command[0:0] = ["sudo", "--non-interactive"]
    else:
        command[0:0] = ["python3"]
        command.append("--development-candidate-supervisor")
    process_environment = compose_environment(context) if platform_attestation is None else {
        name: value for name, value in os.environ.items() if name in {"LANG", "LC_ALL", "PATH", "TERM"}
    }
    subprocess.run(command, check=True, cwd=context.root, env=process_environment)


def up(context: ComposeContext) -> None:
    if context.profile != "dev" or context.isolated_namespace is not None:
        # The installed privileged supervisor verifies both flattened JWS
        # artifacts and atomically consumes their nonce. Candidate code only
        # forwards the fixed accept command and never parses trust evidence.
        _keycloak(context, "accept")
    prepare(context)
    normalized_config(context, emit=False)
    _script(context, "nextcloud_reconcile.py")
    _compose(context, "up", "--detach", "--remove-orphans", "--wait")


def down(context: ComposeContext) -> None:
    # Normal stop/update is deliberately non-destructive. Run teardown.sh with
    # exact isolated ownership evidence for disposable volume removal.
    _compose(context, "down", "--remove-orphans")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, required=True)
    parser.add_argument("profile", choices=("dev", "dogfood", "main"))
    parser.add_argument("command", choices=COMMANDS)
    parser.add_argument("remainder", nargs="*")
    args = parser.parse_args()
    try:
        context = load_context(args.profile, args.root)
        if args.command == "secrets-init":
            _script(context, "init_secrets.py")
        elif args.command == "render":
            _script(context, "render_config.py")
        elif args.command == "config":
            _assert_render(context)
            normalized_config(context, emit=True)
        elif args.command == "prepare":
            prepare(context)
        elif args.command == "provider-prepare":
            _assert_render(context)
            prepare(context)
            _script(context, "nextcloud_reconcile.py")
        elif args.command == "up":
            up(context)
        elif args.command == "down":
            down(context)
        elif args.command == "ps":
            _compose(context, "ps", *args.remainder)
        elif args.command == "logs":
            _compose(context, "logs", *args.remainder)
        elif args.command.startswith("keycloak-"):
            _keycloak(context, args.command.removeprefix("keycloak-"))
        return 0
    except (
        ContractError,
        OSError,
        ValueError,
        json.JSONDecodeError,
        subprocess.CalledProcessError,
    ) as error:
        print(f"WEAVE_COMPOSE_ERROR {error}", file=os.sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
