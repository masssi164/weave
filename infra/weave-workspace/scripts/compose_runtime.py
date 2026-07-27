#!/usr/bin/env python3
"""Closed operator interface for normalized Compose and one-shot Identity Ops."""

from __future__ import annotations

import argparse
import json
import os
import re
import stat
import subprocess
import sys
from datetime import datetime, timedelta, timezone
from pathlib import Path

from compose_env import ComposeContext, ContractError, compose_environment, load_context, run


COMMANDS = (
    "secrets-init",
    "render",
    "config",
    "prepare",
    "provider-prepare",
    "adoption-check",
    "up",
    "down",
    "ps",
    "logs",
    "identity-plan",
    "identity-apply",
    "identity-verify",
)
RUNTIME_ROOT_SERVICES = {
    "dev": ("caddy", "mailpit"),
    "test": ("caddy", "mailpit", "mcp"),
    "prod": ("caddy", "mcp"),
}
HOST_APPLICATION_SERVICES = (
    "backend",
    "mcp",
    "mcp-secret-check",
    "mcp-keycloak-connectivity-check",
)
ADOPTION_RECEIPT_MAX_AGE = timedelta(hours=6)
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
RESOURCE_METADATA = {
    "WEAVE_CADDY_DATA_VOLUME": ("gateway", "tls-sensitive"),
    "WEAVE_CADDY_CONFIG_VOLUME": ("gateway", "configuration-sensitive"),
    "WEAVE_DB_DATA_VOLUME": ("postgres", "database-sensitive"),
    "WEAVE_KEYCLOAK_DATA_VOLUME": ("identity", "identity-sensitive"),
    "WEAVE_MAILPIT_DATA_VOLUME": ("mail", "activation-sensitive"),
    "WEAVE_NEXTCLOUD_DATA_VOLUME": ("files-calendar", "collaboration-sensitive"),
    "WEAVE_SYNAPSE_DATA_VOLUME": ("chat", "collaboration-sensitive"),
    "WEAVE_MATRIX_APPSERVICE_VOLUME": ("chat-appservice", "credential-sensitive"),
}


def script(context: ComposeContext, name: str) -> None:
    command = ["python3", str(context.root / "scripts" / name), context.profile, "--root", str(context.root)]
    if context.profile_env_file != context.root / f"environments/{context.profile}.env":
        command.extend(("--env-file", str(context.profile_env_file)))
    subprocess.run(command, cwd=context.root, env=compose_environment(context), check=True)


def compose(context: ComposeContext, *arguments: str, capture: bool = False) -> subprocess.CompletedProcess[str]:
    return run((*context.compose_base_command, *arguments), context, capture=capture)


def resource_metadata(context: ComposeContext, kind: str, name: str) -> tuple[str, str]:
    if kind == "network" and name == context.env["WEAVE_DOCKER_NETWORK"]:
        return "network", "connectivity"
    matches = [
        metadata
        for key, metadata in RESOURCE_METADATA.items()
        if kind == "volume" and context.env[key] == name
    ]
    if len(matches) != 1:
        raise ContractError(f"no unique resource metadata for Docker {kind} {name}")
    return matches[0]


def labels(context: ComposeContext, kind: str, name: str) -> dict[str, str]:
    component, data_class = resource_metadata(context, kind, name)
    return {
        "com.massimotter.weave.managed": "true",
        "com.massimotter.weave.environment": context.env["WEAVE_RESOURCE_ENVIRONMENT"],
        "com.massimotter.weave.scope": context.env["WEAVE_STACK_SCOPE"],
        "com.massimotter.weave.stack": context.env["WEAVE_RESOURCE_STACK"],
        "com.massimotter.weave.generation": context.env["WEAVE_RESOURCE_GENERATION"],
        "com.massimotter.weave.namespace": context.env["WEAVE_RESOURCE_PREFIX"],
        "com.massimotter.weave.component": component,
        "com.massimotter.weave.data-class": data_class,
        "com.massimotter.weave.fresh-start-eligible": "true",
        "com.massimotter.weave.spec-commit": context.env["WEAVE_SPEC_COMMIT"],
        "com.massimotter.weave.spec-digest": context.env["WEAVE_SPEC_DIGEST"],
        "com.massimotter.weave.candidate-commit": context.env["WEAVE_CANDIDATE_COMMIT"],
        "com.massimotter.weave.candidate-manifest-digest": context.env[
            "WEAVE_CANDIDATE_MANIFEST_DIGEST"
        ],
    }


def resource_inventory(context: ComposeContext) -> set[tuple[str, str]]:
    resources = {("network", context.env["WEAVE_DOCKER_NETWORK"])}
    resources.update(("volume", context.env[key]) for key in VOLUME_KEYS)
    return resources


def inspect_resource(context: ComposeContext, kind: str, name: str) -> tuple[bool, bool]:
    inspected = subprocess.run(
        ["docker", kind, "inspect", name, "--format", "{{json .Labels}}"],
        check=False,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.DEVNULL,
    )
    if inspected.returncode != 0:
        return False, False
    observed = json.loads(inspected.stdout) or {}
    owned = all(
        observed.get(key) == value
        for key, value in labels(context, kind, name).items()
    )
    return True, owned


def adoption_status(context: ComposeContext) -> dict[str, object]:
    resources = []
    for kind, name in sorted(resource_inventory(context)):
        present, owned = inspect_resource(context, kind, name)
        resources.append(
            {
                "kind": kind,
                "name": name,
                "state": "owned" if owned else "requires-adoption" if present else "absent",
            }
        )
    database_volume = context.env["WEAVE_DB_DATA_VOLUME"]
    return {
        "schemaVersion": "weave.compose-adoption-preflight.v1",
        "profile": context.profile,
        "composeProject": context.env["WEAVE_COMPOSE_PROJECT"],
        "deploymentContext": context.env["WEAVE_DEPLOYMENT_CONTEXT"],
        "adoptionRequired": any(item["state"] == "requires-adoption" for item in resources),
        "databaseVolumePresent": any(
            item["kind"] == "volume" and item["name"] == database_volume and item["state"] != "absent"
            for item in resources
        ),
        "resources": resources,
        "supportSafe": True,
        "containsSecretValues": False,
    }


def validate_adoption_receipt(context: ComposeContext, kind: str, name: str) -> None:
    supplied = os.environ.get("WEAVE_ADOPTION_RECEIPT", "")
    canonical = (context.generated_root / "adoption/adoption-receipt.json").resolve()
    if not supplied:
        raise ContractError(f"persistent adoption of Docker {kind} {name} requires WEAVE_ADOPTION_RECEIPT")
    path = Path(supplied).expanduser()
    try:
        metadata = path.lstat()
    except FileNotFoundError as error:
        raise ContractError("WEAVE_ADOPTION_RECEIPT is unavailable") from error
    if stat.S_ISLNK(metadata.st_mode) or not stat.S_ISREG(metadata.st_mode):
        raise ContractError("WEAVE_ADOPTION_RECEIPT must be a regular non-symlink file")
    if path.resolve() != canonical:
        raise ContractError("WEAVE_ADOPTION_RECEIPT must use the canonical generated-state path")
    if metadata.st_uid != os.getuid() or stat.S_IMODE(metadata.st_mode) != 0o600:
        raise ContractError("WEAVE_ADOPTION_RECEIPT must be owner-controlled mode-0600")
    try:
        receipt = json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError as error:
        raise ContractError("WEAVE_ADOPTION_RECEIPT is not valid JSON") from error
    candidate = os.environ.get("WEAVE_CANDIDATE_COMMIT", "")
    if not re.fullmatch(r"[0-9a-f]{40}", candidate):
        raise ContractError("persistent adoption requires an exact WEAVE_CANDIDATE_COMMIT")
    verified_at_value = receipt.get("verifiedAt", "")
    try:
        verified_at = datetime.fromisoformat(verified_at_value.replace("Z", "+00:00"))
    except (AttributeError, ValueError) as error:
        raise ContractError("WEAVE_ADOPTION_RECEIPT has an invalid verifiedAt") from error
    now = datetime.now(timezone.utc)
    if verified_at.tzinfo is None or verified_at > now or now - verified_at > ADOPTION_RECEIPT_MAX_AGE:
        raise ContractError("WEAVE_ADOPTION_RECEIPT is stale or future-dated")
    supplied_resources = receipt.get("resources")
    if not isinstance(supplied_resources, list):
        raise ContractError("WEAVE_ADOPTION_RECEIPT has no resource inventory")
    try:
        observed_inventory = {
            (item["kind"], item["name"])
            for item in supplied_resources
            if isinstance(item, dict) and set(item) == {"kind", "name"}
        }
    except (KeyError, TypeError) as error:
        raise ContractError("WEAVE_ADOPTION_RECEIPT has an invalid resource inventory") from error
    expected_inventory = resource_inventory(context)
    if len(supplied_resources) != len(expected_inventory) or observed_inventory != expected_inventory:
        raise ContractError("WEAVE_ADOPTION_RECEIPT resource inventory is incomplete or ambiguous")
    if (
        receipt.get("schemaVersion") != "weave.compose-adoption-receipt.v1"
        or receipt.get("profile") != context.profile
        or receipt.get("composeProject") != context.env["WEAVE_COMPOSE_PROJECT"]
        or receipt.get("candidateCommit") != candidate
        or receipt.get("backupVerified") is not True
        or receipt.get("isolatedRestoreVerified") is not True
        or receipt.get("supportSafe") is not True
        or receipt.get("containsSecretValues") is not False
        or (kind, name) not in observed_inventory
    ):
        raise ContractError("WEAVE_ADOPTION_RECEIPT does not authorize this exact adoption")


def ensure_resource(context: ComposeContext, kind: str, name: str) -> None:
    present, owned = inspect_resource(context, kind, name)
    if present:
        if owned:
            return
        if context.env["WEAVE_DEPLOYMENT_CONTEXT"] == "persistent-adoption":
            validate_adoption_receipt(context, kind, name)
            return
        raise ContractError(f"refusing unowned existing Docker {kind} {name}")
    command = ["docker", kind, "create"]
    for key, value in sorted(labels(context, kind, name).items()):
        command.extend(("--label", f"{key}={value}"))
    command.append(name)
    subprocess.run(command, check=True, stdout=subprocess.DEVNULL)


def prepare(context: ComposeContext) -> None:
    manifest = context.generated_root / "render-manifest.json"
    if manifest.is_symlink() or not manifest.is_file():
        raise ContractError("render-manifest.json is missing; run render first")
    ensure_resource(context, "network", context.env["WEAVE_DOCKER_NETWORK"])
    for key in VOLUME_KEYS:
        ensure_resource(context, "volume", context.env[key])
    evidence_root = context.generated_root / "identity-ops"
    evidence_root.mkdir(parents=True, exist_ok=True, mode=0o700)
    os.chmod(evidence_root, 0o700)
    runtime_uid = int(context.env["WEAVE_RUNTIME_UID"])
    runtime_gid = int(context.env["WEAVE_RUNTIME_GID"])
    if evidence_root.stat().st_uid != runtime_uid or evidence_root.stat().st_gid != runtime_gid:
        try:
            os.chown(evidence_root, runtime_uid, runtime_gid)
        except PermissionError as error:
            raise ContractError("Identity Ops evidence directory is not writable by the rootless runtime uid/gid") from error
    for path in (
        context.secret_root / "agent-runtime/workloads",
        context.secret_root / "agent-runtime/profile-signing",
    ):
        path.mkdir(parents=True, exist_ok=True, mode=0o700)
        os.chmod(path, 0o700)
        if path.stat().st_uid != runtime_uid or path.stat().st_gid != runtime_gid:
            try:
                os.chown(path, runtime_uid, runtime_gid)
            except PermissionError as error:
                raise ContractError(
                    "Agent Runtime SecretRef directory is not writable by the rootless runtime uid/gid"
                ) from error


def normalized_config(context: ComposeContext, emit: bool) -> None:
    result = compose(context, "config", "--format", "json", capture=True)
    model = json.loads(result.stdout)
    services = model.get("services", {})
    required = {"postgres", "keycloak", "identity-ops"}
    if not required.issubset(services):
        raise ContractError("normalized Compose model is missing a core or Identity Ops service")
    serialized = json.dumps(model)
    forbidden = ("/var/run/docker.sock", "keycloak-supervisor", "keycloak-admin-sanitizer")
    if any(value in serialized for value in forbidden):
        raise ContractError("normalized Compose model contains a retired privileged identity control plane")
    if context.profile == "dev" and {"backend", "mcp"}.intersection(services):
        raise ContractError("dev must keep the application tier on the host")
    if context.profile in {"test", "prod"} and not {"backend", "mcp"}.issubset(services):
        raise ContractError(f"{context.profile} normalized model is missing the application tier")
    if emit:
        print(json.dumps(model, indent=2, sort_keys=True))


def identity_ops(context: ComposeContext, action: str) -> None:
    prepare(context)
    compose(context, "stop", "keycloak")
    compose(
        context,
        "run",
        "--rm",
        "--no-deps",
        "keycloak",
        "bootstrap-admin",
        "service",
        "--client-id",
        "weave-identity-ops-bootstrap",
        "--client-secret:env=WEAVE_IDENTITY_OPS_BOOTSTRAP_SECRET",
        "--no-prompt",
    )
    compose(context, "up", "-d", "--wait", "keycloak")
    command = {
        "identity-plan": "plan",
        "identity-apply": "apply",
        "identity-verify": "verify",
    }[action]
    compose(
        context,
        "run",
        "--rm",
        "--no-deps",
        "identity-ops",
        command,
    )
    if action == "identity-apply":
        adopt_secret_updates(context)


def adopt_secret_updates(context: ComposeContext) -> None:
    updates = context.generated_root / "identity-ops/secret-updates"
    if not updates.exists():
        return
    allowed = {
        "keycloak-weave-identity-admin",
        "keycloak-weave-agent-runtime-admin",
        "keycloak-nextcloud",
        "keycloak-matrix-mas",
    }
    for source in updates.iterdir():
        if source.name not in allowed or source.is_symlink() or not source.is_file():
            raise ContractError("Identity Ops produced an unexpected SecretRef update")
        if stat.S_IMODE(source.stat().st_mode) != 0o600:
            raise ContractError("Identity Ops SecretRef update is not mode-0600")
        target = context.secret_root / source.name
        temporary = target.with_name(f".{target.name}.{os.getpid()}.identity-ops")
        temporary.write_bytes(source.read_bytes())
        os.chmod(temporary, 0o600)
        os.replace(temporary, target)
        source.unlink()


def execute(context: ComposeContext, command: str, extra: list[str]) -> None:
    if command == "secrets-init":
        script(context, "init_secrets.py")
    elif command == "render":
        script(context, "render_config.py")
    elif command == "config":
        normalized_config(context, emit=True)
    elif command == "prepare":
        prepare(context)
    elif command == "provider-prepare":
        subprocess.run([str(context.root / "provision-matrix-default-workspace.sh")], cwd=context.root, env=compose_environment(context), check=True)
    elif command == "adoption-check":
        print(json.dumps(adoption_status(context), indent=2, sort_keys=True))
    elif command == "up":
        script(context, "init_secrets.py")
        script(context, "render_config.py")
        prepare(context)
        normalized_config(context, emit=False)
        if context.profile == "dev":
            # Explicitly targeting a Compose service activates its otherwise
            # disabled profile. Remove application-tier containers left by an
            # older/dev-drifted invocation before converging the provider-only
            # host-development topology.
            compose(
                context,
                "rm",
                "--stop",
                "--force",
                *HOST_APPLICATION_SERVICES,
            )
        compose(context, "up", "-d", "postgres", "postgres-reconcile")
        identity_ops(context, "identity-apply")
        compose(
            context,
            "up",
            "-d",
            "--remove-orphans",
            "--wait",
            "--wait-timeout",
            "600",
            *RUNTIME_ROOT_SERVICES[context.profile],
        )
        script(context, "nextcloud_reconcile.py")
    elif command == "down":
        if context.profile == "dev":
            compose(
                context,
                "rm",
                "--stop",
                "--force",
                *HOST_APPLICATION_SERVICES,
            )
        compose(context, "down", *extra)
    elif command in {"ps", "logs"}:
        compose(context, command, *extra)
    else:
        identity_ops(context, command)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, required=True)
    parser.add_argument("profile", choices=("dev", "test", "prod"))
    parser.add_argument("command", choices=COMMANDS)
    parser.add_argument("extra", nargs=argparse.REMAINDER)
    args = parser.parse_args()
    try:
        context = load_context(args.profile, args.root)
        execute(context, args.command, args.extra)
        return 0
    except (ContractError, OSError, ValueError, KeyError, json.JSONDecodeError, subprocess.CalledProcessError) as error:
        print(f"WEAVE_COMPOSE_ERROR {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
