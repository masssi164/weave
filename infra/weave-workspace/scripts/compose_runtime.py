#!/usr/bin/env python3
"""Closed operator interface for normalized Compose and one-shot Identity Ops."""

from __future__ import annotations

import argparse
import json
import os
import stat
import subprocess
import sys
from pathlib import Path

from compose_env import ComposeContext, ContractError, compose_environment, load_context, run


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
    "identity-plan",
    "identity-apply",
    "identity-verify",
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


def script(context: ComposeContext, name: str) -> None:
    command = ["python3", str(context.root / "scripts" / name), context.profile, "--root", str(context.root)]
    if context.profile_env_file != context.root / f"environments/{context.profile}.env":
        command.extend(("--env-file", str(context.profile_env_file)))
    subprocess.run(command, cwd=context.root, env=compose_environment(context), check=True)


def compose(context: ComposeContext, *arguments: str, capture: bool = False) -> subprocess.CompletedProcess[str]:
    return run((*context.compose_base_command, *arguments), context, capture=capture)


def labels(context: ComposeContext) -> dict[str, str]:
    return {
        "com.massimotter.weave.managed": "true",
        "com.massimotter.weave.environment": context.profile,
        "com.massimotter.weave.namespace": context.env["WEAVE_RESOURCE_PREFIX"],
        "com.massimotter.weave.scope": context.env["WEAVE_STACK_SCOPE"],
    }


def ensure_resource(context: ComposeContext, kind: str, name: str) -> None:
    inspected = subprocess.run(
        ["docker", kind, "inspect", name, "--format", "{{json .Labels}}"],
        check=False,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.DEVNULL,
    )
    if inspected.returncode == 0:
        observed = json.loads(inspected.stdout) or {}
        if all(observed.get(key) == value for key, value in labels(context).items()):
            return
        if context.env["WEAVE_DEPLOYMENT_CONTEXT"] == "persistent-adoption":
            return
        raise ContractError(f"refusing unowned existing Docker {kind} {name}")
    command = ["docker", kind, "create"]
    for key, value in sorted(labels(context).items()):
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


def test_user_volume(context: ComposeContext) -> tuple[str, ...]:
    if context.profile == "prod" and "WEAVE_TEST_USERS_FILE" in context.env:
        raise ContractError("prod rejects WEAVE_TEST_USERS_FILE before Identity Ops mutation")
    supplied = context.env.get("WEAVE_TEST_USERS_FILE", "")
    if context.profile == "test" and not supplied:
        supplied = str(context.root / ".generated/test/test-users.json")
    if not supplied:
        return ()
    supplied_path = Path(supplied).expanduser()
    try:
        supplied_metadata = supplied_path.lstat()
    except FileNotFoundError as error:
        raise ContractError("WEAVE_TEST_USERS_FILE is unavailable") from error
    if stat.S_ISLNK(supplied_metadata.st_mode) or not stat.S_ISREG(supplied_metadata.st_mode):
        raise ContractError("WEAVE_TEST_USERS_FILE must be a regular non-symlink file")
    path = supplied_path.resolve()
    metadata = path.stat()
    if stat.S_IMODE(metadata.st_mode) != 0o600 or metadata.st_uid != os.getuid():
        raise ContractError("WEAVE_TEST_USERS_FILE must be owner-controlled mode-0600")
    json.loads(path.read_text(encoding="utf-8"))
    return ("--volume", f"{path}:/run/weave/test-users.json:ro")


def identity_ops(context: ComposeContext, action: str) -> None:
    test_users = test_user_volume(context)
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
        *test_users,
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
    elif command == "up":
        test_user_volume(context)
        script(context, "init_secrets.py")
        script(context, "render_config.py")
        prepare(context)
        normalized_config(context, emit=False)
        compose(context, "up", "-d", "postgres", "postgres-reconcile")
        identity_ops(context, "identity-apply")
        compose(context, "up", "-d", "--remove-orphans")
    elif command == "down":
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
