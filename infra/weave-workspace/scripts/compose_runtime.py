#!/usr/bin/env python3
"""Closed operator interface for normalized Compose and bounded migrations."""

from __future__ import annotations

import argparse
import fcntl
import hashlib
import json
import os
import re
import secrets
import shutil
import ssl
import stat
import subprocess
import sys
import time
import urllib.error
import urllib.request
from datetime import datetime, timezone
from pathlib import Path, PurePosixPath
from typing import Any

from bounded_process import BoundedProcessTimeout, run_bounded
from compose_env import (
    ComposeContext,
    ContractError,
    compose_environment,
    load_context,
    run,
)
from keycloak_migration import migration_inputs, require_completed_migration
from keycloak_migration_backup import create_backup_proof


COMMANDS = (
    "secrets-init",
    "render",
    "configure",
    "config",
    "prepare",
    "provider-prepare",
    "up",
    "down",
    "reset",
    "ps",
    "logs",
    "keycloak-migration-apply",
    "bootstrap-owner",
    "persistence-restart-proof",
    "chat-provider-stop-proof",
    "chat-provider-start-proof",
    "collaboration-restart-proof",
)
RUNTIME_ROOT_SERVICES = {
    # Normal development runs Server, MCP, and Admin Console on the host. The
    # Compose lifecycle converges only the database/Keycloak dependency path.
    "dev": ("keycloak",),
    "dogfood": ("caddy", "mailpit", "mcp"),
    "e2e": ("caddy", "mailpit", "mcp"),
    "prod": ("caddy", "mcp"),
}
HOST_APPLICATION_SERVICES = (
    "backend",
    "mcp",
    "mcp-secret-check",
    "mcp-keycloak-connectivity-check",
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
    "WEAVE_RUNTIME_STATE_VOLUME",
    "WEAVE_NATIVE_FILES_DATA_VOLUME",
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
    "WEAVE_RUNTIME_STATE_VOLUME": ("runtime-state", "runtime-state-sensitive"),
    "WEAVE_NATIVE_FILES_DATA_VOLUME": ("files-native", "collaboration-sensitive"),
}


def active_volume_keys(context: ComposeContext) -> tuple[str, ...]:
    if getattr(context, "environment", context.profile) == "dev":
        return ()
    if context.environment == "dogfood":
        return (
            "WEAVE_DB_DATA_VOLUME",
            "WEAVE_NATIVE_FILES_DATA_VOLUME",
            "WEAVE_MAILPIT_DATA_VOLUME",
        )
    keys = [
        "WEAVE_CADDY_DATA_VOLUME",
        "WEAVE_CADDY_CONFIG_VOLUME",
        "WEAVE_DB_DATA_VOLUME",
        "WEAVE_KEYCLOAK_DATA_VOLUME",
        "WEAVE_NATIVE_FILES_DATA_VOLUME",
    ]
    profiles = set(context.active_profiles)
    if profiles.intersection({"dogfood", "e2e", "dev-tools"}):
        keys.append("WEAVE_MAILPIT_DATA_VOLUME")
    if "provider-nextcloud" in profiles:
        keys.append("WEAVE_NEXTCLOUD_DATA_VOLUME")
    if "provider-matrix" in profiles:
        keys.extend(("WEAVE_SYNAPSE_DATA_VOLUME", "WEAVE_MATRIX_APPSERVICE_VOLUME"))
    if "storage-s3" in profiles:
        keys.append("WEAVE_RUNTIME_STATE_VOLUME")
    return tuple(keys)
RESOURCE_PROVENANCE_LABEL_PATTERNS = {
    "com.massimotter.weave.spec-commit": re.compile(r"^[0-9a-f]{40}$"),
    "com.massimotter.weave.spec-digest": re.compile(r"^sha256:[0-9a-f]{64}$"),
    "com.massimotter.weave.candidate-commit": re.compile(r"^[0-9a-f]{40}$"),
    "com.massimotter.weave.candidate-manifest-digest": re.compile(r"^sha256:[0-9a-f]{64}$"),
}
AGENT_RUNTIME_ROOT = PurePosixPath("/run/secrets/agent-runtime")
PROFILE_SIGNING_TARGET = AGENT_RUNTIME_ROOT / "profile-signing"
STATE_WRAPPING_TARGET = AGENT_RUNTIME_ROOT / "state-wrapping"
WORKLOADS_TARGET = AGENT_RUNTIME_ROOT / "workloads"
RUNTIME_ADMIN_TARGET = (
    WORKLOADS_TARGET / "weave/keycloak/weave-agent-runtime-admin"
)
IDENTITY_ADMIN_PRIVATE_TARGET = PurePosixPath(
    "/run/secrets/identity-admin/weave-identity-admin-private-jwk.json"
)
AGENT_RUNTIME_MOUNT_POLICY = {
    ("agent-runtime-keys-init", str(PROFILE_SIGNING_TARGET)): ("read-write", "directory"),
    ("agent-runtime-keys-init", str(STATE_WRAPPING_TARGET)): ("read-write", "directory"),
    ("backend", str(WORKLOADS_TARGET)): ("read-write", "directory"),
    ("backend", str(PROFILE_SIGNING_TARGET)): ("read-only", "directory"),
    ("backend", str(STATE_WRAPPING_TARGET)): ("read-only", "directory"),
}
MCP_PROTECTED_SECRET_MARKERS = (
    "weave-agent-runtime-admin",
    "weave-identity-admin",
    "weave-backend-jwk",
    "/agent-runtime/workloads/",
)
COLLABORATION_CONTROL_BUDGET_SECONDS = 240
COLLABORATION_SUBPROCESS_TIMEOUT_SECONDS = 30
COLLABORATION_HEALTH_POLL_SECONDS = 2
RETIRED_DOGFOOD_CONTAINERS = (
    "weave-backend",
    "weave-db",
    "weave-keycloak",
    "weave-mailpit",
    "weave-mas",
    "weave-mcp-server",
    "weave-nextcloud",
    "weave-proxy",
    "weave-synapse",
)
RETIRED_DOGFOOD_VOLUMES = (
    "weave_caddy_config",
    "weave_caddy_data",
    "weave_db_data",
    "weave_keycloak_data",
    "weave_mailpit_data",
    "weave_matrix_chat_appservice_runtime",
    "weave_nextcloud_data",
    "weave_synapse_data",
)
RETIRED_DOGFOOD_NETWORK = "weave_network"


def script(context: ComposeContext, name: str) -> None:
    command = ["python3", str(context.root / "scripts" / name), context.profile, "--root", str(context.root)]
    if context.profile_env_file != context.root / f"environments/{context.profile}.env":
        command.extend(("--env-file", str(context.profile_env_file)))
    subprocess.run(command, cwd=context.root, env=compose_environment(context), check=True)


def runtime_root_services(context: ComposeContext) -> tuple[str, ...]:
    roots = list(RUNTIME_ROOT_SERVICES[context.profile])
    profiles = set(context.active_profiles)
    if "provider-matrix" in profiles:
        roots.append("synapse")
    if "provider-nextcloud" in profiles:
        roots.append("nextcloud")
    if "dev-tools" in profiles and "mailpit" not in roots:
        roots.append("mailpit")
    return tuple(roots)


def compose(context: ComposeContext, *arguments: str, capture: bool = False) -> subprocess.CompletedProcess[str]:
    return run((*context.compose_base_command, *arguments), context, capture=capture)


def _collaboration_remaining(deadline: float) -> float:
    remaining = deadline - time.monotonic()
    if remaining <= 0:
        raise ContractError("collaboration service control exceeded its bounded timeout")
    return remaining


def _bounded_collaboration_run(
    command: list[str],
    context: ComposeContext,
    deadline: float,
    *,
    capture: bool,
) -> subprocess.CompletedProcess[str]:
    timeout = min(
        float(COLLABORATION_SUBPROCESS_TIMEOUT_SECONDS),
        _collaboration_remaining(deadline),
    )
    try:
        completed = run_bounded(
            command,
            cwd=context.root,
            env=compose_environment(context),
            capture_output=capture,
            timeout_seconds=timeout,
        )
    except BoundedProcessTimeout as error:
        raise ContractError(
            "collaboration service control Docker operation exceeded its bounded timeout"
        ) from error
    except OSError as error:
        raise ContractError(
            "collaboration service control Docker operation failed"
        ) from error
    if completed.returncode != 0:
        raise ContractError("collaboration service control Docker operation failed")
    return completed


def _bounded_collaboration_compose(
    context: ComposeContext,
    deadline: float,
    *arguments: str,
    capture: bool = False,
) -> subprocess.CompletedProcess[str]:
    return _bounded_collaboration_run(
        [*context.compose_base_command, *arguments],
        context,
        deadline,
        capture=capture,
    )


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
    values = {
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
    if kind == "network":
        # E2E pre-creates the reviewed isolated network before Compose starts.
        # These are Compose's own identity labels for the logical `weave`
        # network, so Compose can safely adopt that exact project network.
        values.update(
            {
                "com.docker.compose.network": "weave",
                "com.docker.compose.project": context.env["WEAVE_COMPOSE_PROJECT"],
            }
        )
    return values


def resource_inventory(context: ComposeContext) -> set[tuple[str, str]]:
    resources = {("network", context.env["WEAVE_DOCKER_NETWORK"])}
    resources.update(("volume", context.env[key]) for key in active_volume_keys(context))
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
    return True, resource_labels_match(context, kind, name, observed)


def resource_labels_match(
    context: ComposeContext,
    kind: str,
    name: str,
    observed: dict[str, str],
) -> bool:
    """Verify stable identity and the immutable creating provenance tuple.

    Persistent resources intentionally retain the spec/candidate provenance
    from their creation. Routine deployments therefore validate the shape and
    completeness of that immutable tuple, while requiring every stable
    ownership label (including the contract generation) to match exactly.
    """
    owned = all(
        observed.get(key) == value
        for key, value in labels(context, kind, name).items()
        if key not in RESOURCE_PROVENANCE_LABEL_PATTERNS
    ) and all(
        pattern.fullmatch(observed.get(key, "")) is not None
        for key, pattern in RESOURCE_PROVENANCE_LABEL_PATTERNS.items()
    )
    return owned


def ensure_resource(context: ComposeContext, kind: str, name: str) -> None:
    present, owned = inspect_resource(context, kind, name)
    if present:
        if owned:
            return
        raise ContractError(f"refusing unowned existing Docker {kind} {name}")
    command = ["docker", kind, "create"]
    for key, value in sorted(labels(context, kind, name).items()):
        command.extend(("--label", f"{key}={value}"))
    command.append(name)
    subprocess.run(command, check=True, stdout=subprocess.DEVNULL)


def _normalize_secret_target(target: str) -> str:
    return target if target.startswith("/") else f"/run/secrets/{target}"


def _source_type(path: Path) -> str:
    try:
        metadata = path.lstat()
    except FileNotFoundError:
        return "missing"
    if stat.S_ISLNK(metadata.st_mode):
        return "symlink"
    if stat.S_ISREG(metadata.st_mode):
        return "file"
    if stat.S_ISDIR(metadata.st_mode):
        return "directory"
    return "other"


def _target_is_within(target: str, parent: PurePosixPath) -> bool:
    candidate = PurePosixPath(target)
    return candidate == parent or parent in candidate.parents


def normalized_mount_graph(model: dict[str, Any]) -> list[dict[str, Any]]:
    """Return the resolved, support-safe mount responsibility model.

    Compose `uid`, `gid`, and `mode` fields are recorded as declarations only.
    Source-file security is established separately from host `lstat` results.
    """

    graph: list[dict[str, Any]] = []
    top_level_secrets = model.get("secrets", {})
    for service_name, service in sorted(model.get("services", {}).items()):
        user = str(service.get("user", "image-default"))
        lifecycle = (
            "one-shot"
            if service.get("labels", {}).get("com.massimotter.weave.one-shot") == "true"
            else "running"
        )
        for mount in service.get("volumes", []):
            source = str(mount.get("source", ""))
            target = str(mount.get("target", ""))
            read_only = bool(mount.get("read_only", False))
            actual_type = (
                "directory"
                if mount.get("type") == "volume"
                else _source_type(Path(source))
            )
            policy = AGENT_RUNTIME_MOUNT_POLICY.get((service_name, target))
            graph.append(
                {
                    "service": service_name,
                    "source": source,
                    "target": target,
                    "mountKind": str(mount.get("type", "")),
                    "sourceType": actual_type,
                    "expectedSourceType": policy[1] if policy else actual_type,
                    "access": "read-only" if read_only else "read-write",
                    "runtimeUser": user,
                    "lifecycle": lifecycle,
                    "parentTargets": [],
                    "declaredUid": None,
                    "declaredGid": None,
                    "declaredMode": None,
                }
            )
        for secret in service.get("secrets", []):
            secret_name = str(secret.get("source", ""))
            source = str((top_level_secrets.get(secret_name) or {}).get("file", ""))
            graph.append(
                {
                    "service": service_name,
                    "source": source,
                    "target": _normalize_secret_target(str(secret.get("target", secret_name))),
                    "mountKind": "compose-secret",
                    "sourceType": _source_type(Path(source)),
                    "expectedSourceType": "file",
                    "access": "read-only",
                    "runtimeUser": user,
                    "lifecycle": lifecycle,
                    "parentTargets": [],
                    "declaredUid": secret.get("uid"),
                    "declaredGid": secret.get("gid"),
                    "declaredMode": secret.get("mode"),
                }
            )
    for entry in graph:
        target = PurePosixPath(entry["target"])
        entry["parentTargets"] = sorted(
            other["target"]
            for other in graph
            if other["service"] == entry["service"]
            and other is not entry
            and PurePosixPath(other["target"]) in target.parents
        )
    return graph


def validate_mount_contract(model: dict[str, Any]) -> list[dict[str, Any]]:
    graph = normalized_mount_graph(model)
    services = model.get("services", {})
    for child in graph:
        for parent_target in child["parentTargets"]:
            parent = next(
                entry
                for entry in graph
                if entry["service"] == child["service"] and entry["target"] == parent_target
            )
            if parent["access"] == "read-only":
                raise ContractError(
                    "read-only parent mount collides with a required child mount: "
                    f"{child['service']}:{parent_target}"
                )

    agent_entries = [
        entry
        for entry in graph
        if _target_is_within(entry["target"], AGENT_RUNTIME_ROOT)
    ]
    for entry in agent_entries:
        expected = AGENT_RUNTIME_MOUNT_POLICY.get((entry["service"], entry["target"]))
        if expected is None:
            raise ContractError(
                "service receives an undeclared Agent Runtime SecretRef target: "
                f"{entry['service']}:{entry['target']}"
            )
        if (entry["access"], entry["expectedSourceType"]) != expected:
            raise ContractError(
                "Agent Runtime SecretRef target has the wrong access or source type: "
                f"{entry['service']}:{entry['target']}"
            )

    if "backend" in services:
        expected_backend = {
            key[1] for key in AGENT_RUNTIME_MOUNT_POLICY if key[0] == "backend"
        }
        observed_backend = {
            entry["target"] for entry in agent_entries if entry["service"] == "backend"
        }
        if observed_backend != expected_backend:
            raise ContractError("backend Agent Runtime SecretRef boundary is incomplete")
        identity_admin_targets = {
            entry["target"]
            for entry in graph
            if entry["service"] == "backend"
            and "weave-identity-admin" in (entry["source"] + entry["target"])
        }
        if identity_admin_targets != {
            str(IDENTITY_ADMIN_PRIVATE_TARGET)
        }:
            raise ContractError(
                "backend identity-admin access must be one exact private-JWK SecretRef"
            )

    identity_admin_private_entries = [
        entry
        for entry in graph
        if "weave-identity-admin-private-jwk" in (entry["source"] + entry["target"])
    ]
    declared_identity_admin_private = {
        ("backend", str(IDENTITY_ADMIN_PRIVATE_TARGET), "read-only", "file"),
    }
    expected_identity_admin_private = {
        entry for entry in declared_identity_admin_private if entry[0] in services
    }
    observed_identity_admin_private = {
        (
            entry["service"],
            entry["target"],
            entry["access"],
            entry["expectedSourceType"],
        )
        for entry in identity_admin_private_entries
    }
    if observed_identity_admin_private != expected_identity_admin_private:
        raise ContractError(
            "identity-admin private JWK must be mounted only by Server"
        )

    if "agent-runtime-keys-init" in services:
        expected_initializer = {
            key[1]
            for key in AGENT_RUNTIME_MOUNT_POLICY
            if key[0] == "agent-runtime-keys-init"
        }
        observed_initializer = {
            entry["target"]
            for entry in agent_entries
            if entry["service"] == "agent-runtime-keys-init"
        }
        if observed_initializer != expected_initializer:
            raise ContractError("Agent Runtime key initializer has an overbroad or incomplete mount set")

    for target in (PROFILE_SIGNING_TARGET, STATE_WRAPPING_TARGET):
        writers = {
            entry["service"]
            for entry in graph
            if entry["access"] == "read-write"
            and _target_is_within(entry["target"], target)
        }
        if writers and writers != {"agent-runtime-keys-init"}:
            raise ContractError(f"{target} is writable outside the one-time initializer")

    workload_writers = {
        entry["service"]
        for entry in graph
        if entry["access"] == "read-write"
        and _target_is_within(entry["target"], WORKLOADS_TARGET)
    }
    if "backend" in services and workload_writers != {"backend"}:
        raise ContractError("only backend may write the Agent Runtime workload tree")

    for entry in graph:
        if entry["service"] in {"mcp", "mcp-secret-check", "mcp-keycloak-connectivity-check"}:
            coordinate = entry["source"] + entry["target"]
            if any(marker in coordinate for marker in MCP_PROTECTED_SECRET_MARKERS):
                raise ContractError(
                    f"MCP service receives an administrative or Cell SecretRef: {entry['service']}"
                )
        if entry["mountKind"] == "bind" and entry["target"] in {
            "/run/secrets",
            "/run/secrets/weave",
            "/certs",
        }:
            raise ContractError(
                f"service receives a broader protected subtree than required: {entry['service']}:{entry['target']}"
            )
    return graph


def _path_within(path: Path, root: Path) -> bool:
    try:
        path.relative_to(root)
    except ValueError:
        return False
    return True


def _assert_protected_source(
    source: Path,
    *,
    source_type: str,
    writable: bool,
    runtime_uid: int,
    runtime_gid: int,
    container_coordinate: str,
) -> None:
    actual_type = _source_type(source)
    if actual_type != source_type:
        raise ContractError(
            f"protected source for {container_coordinate} must be a regular non-symlink {source_type}"
        )
    metadata = source.lstat()
    expected_mode = 0o600 if source_type == "file" else 0o700
    if stat.S_IMODE(metadata.st_mode) != expected_mode:
        raise ContractError(
            f"protected source for {container_coordinate} must have mode {expected_mode:04o}"
        )
    if metadata.st_uid != runtime_uid or metadata.st_gid != runtime_gid:
        raise ContractError(
            f"protected source for {container_coordinate} has the wrong runtime owner"
        )
    if not metadata.st_mode & stat.S_IRUSR:
        raise ContractError(f"runtime uid cannot read protected source for {container_coordinate}")
    if writable and not metadata.st_mode & stat.S_IWUSR:
        raise ContractError(f"runtime uid cannot write protected source for {container_coordinate}")
    if metadata.st_mode & (stat.S_IRWXG | stat.S_IRWXO):
        raise ContractError(
            f"unrelated service uids could access protected source for {container_coordinate}"
        )


def preflight_protected_sources(
    context: ComposeContext, model: dict[str, Any], graph: list[dict[str, Any]]
) -> None:
    runtime_uid = int(context.env["WEAVE_RUNTIME_UID"])
    runtime_gid = int(context.env["WEAVE_RUNTIME_GID"])
    protected_roots = (context.secret_root, context.tls_root)
    for root in protected_roots:
        _assert_protected_source(
            root,
            source_type="directory",
            writable=True,
            runtime_uid=runtime_uid,
            runtime_gid=runtime_gid,
            container_coordinate="protected-root",
        )
    checked: set[tuple[Path, str, bool]] = set()
    for entry in graph:
        source = Path(entry["source"])
        root = next(
            (candidate for candidate in protected_roots if _path_within(source, candidate)),
            None,
        )
        if root is None or entry["mountKind"] not in {"bind", "compose-secret"}:
            continue
        expected_type = entry["expectedSourceType"]
        writable = entry["access"] == "read-write"
        key = (source, expected_type, writable)
        if key in checked:
            continue
        coordinate = f"{entry['service']}:{entry['target']}"
        _assert_protected_source(
            source,
            source_type=expected_type,
            writable=writable,
            runtime_uid=runtime_uid,
            runtime_gid=runtime_gid,
            container_coordinate=coordinate,
        )
        current = source.parent
        while _path_within(current, root):
            _assert_protected_source(
                current,
                source_type="directory",
                writable=True,
                runtime_uid=runtime_uid,
                runtime_gid=runtime_gid,
                container_coordinate=coordinate,
            )
            if current == root:
                break
            current = current.parent
        if expected_type == "directory":
            for descendant in source.rglob("*"):
                descendant_type = _source_type(descendant)
                if descendant_type not in {"file", "directory"}:
                    raise ContractError(
                        f"protected subtree for {coordinate} contains a non-regular object"
                    )
                _assert_protected_source(
                    descendant,
                    source_type=descendant_type,
                    writable=writable,
                    runtime_uid=runtime_uid,
                    runtime_gid=runtime_gid,
                    container_coordinate=coordinate,
                )
        checked.add(key)

    serialized = json.dumps(model, sort_keys=True).encode("utf-8")
    for root in protected_roots:
        for candidate in root.rglob("*"):
            if _source_type(candidate) != "file":
                continue
            payload = candidate.read_bytes().strip()
            if len(payload) >= 8 and payload in serialized:
                raise ContractError("normalized Compose diagnostics contain a protected SecretRef value")


def prepare_runtime_paths(context: ComposeContext) -> None:
    manifest = context.generated_root / "render-manifest.json"
    if manifest.is_symlink() or not manifest.is_file():
        raise ContractError("render-manifest.json is missing; run render first")
    migration_root = context.generated_root / "keycloak/migrations"
    migration_root.mkdir(parents=True, exist_ok=True, mode=0o700)
    os.chmod(migration_root, 0o700)
    runtime_uid = int(context.env["WEAVE_RUNTIME_UID"])
    runtime_gid = int(context.env["WEAVE_RUNTIME_GID"])
    if migration_root.stat().st_uid != runtime_uid or migration_root.stat().st_gid != runtime_gid:
        try:
            os.chown(migration_root, runtime_uid, runtime_gid)
        except PermissionError as error:
            raise ContractError("Keycloak migration receipt directory is not writable by the rootless runtime uid/gid") from error
    for path in (
        context.secret_root / "agent-runtime/workloads",
        context.secret_root / "agent-runtime/workloads/weave/keycloak",
        context.secret_root / "agent-runtime/profile-signing",
        context.secret_root / "agent-runtime/state-wrapping",
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


def prepare(context: ComposeContext) -> None:
    prepare_runtime_paths(context)
    model = normalized_config(context, emit=False)
    graph = validate_mount_contract(model)
    preflight_protected_sources(context, model, graph)
    # Dev and dogfood use ordinary Compose-owned resources. Keeping creation
    # in Compose gives their down/reset tasks unsurprising lifecycle behavior.
    # Production and isolated E2E retain their reviewed resource policy.
    if context.environment not in {"dev", "dogfood"}:
        ensure_resource(context, "network", context.env["WEAVE_DOCKER_NETWORK"])
        for key in active_volume_keys(context):
            ensure_resource(context, "volume", context.env[key])
    write_native_compose_environment(context)


def write_native_compose_environment(
    context: ComposeContext, destination: Path | None = None
) -> Path:
    """Write the one-file, secret-free descriptor consumed by native Compose.

    The reviewed operator environment intentionally omits derived provenance and
    runtime ownership values.  Native Compose must receive those exact values or
    it could create falsely labelled resources, so only the invariant preparer
    may materialize this finalized descriptor.
    """
    target = destination or context.root / f".env.{context.environment}"
    if target.exists() and target.is_symlink():
        raise ContractError("native Compose environment descriptor must not be a symlink")
    values = dict(context.env)
    values.update(
        {
            "COMPOSE_FILE": f"compose.yaml:compose.{context.environment}.yaml",
            "COMPOSE_PATH_SEPARATOR": ":",
            "COMPOSE_PROJECT_NAME": context.env["WEAVE_COMPOSE_PROJECT"],
        }
    )
    forbidden_markers = ("PASSWORD", "SECRET", "TOKEN", "ASSERTION", "PRIVATE_KEY", "CREDENTIAL")
    for key, value in values.items():
        if "\n" in value or "\r" in value or "\x00" in value:
            raise ContractError("native Compose environment contains an invalid control byte")
        if key != "WEAVE_SECRET_ROOT" and any(marker in key for marker in forbidden_markers):
            raise ContractError(
                f"native Compose environment cannot contain credential-shaped input {key}"
            )
    target.parent.mkdir(parents=True, exist_ok=True)
    temporary = target.with_name(target.name + ".tmp-" + secrets.token_hex(8))
    descriptor = "".join(f"{key}={values[key]}\n" for key in sorted(values))
    descriptor_fd = os.open(
        temporary, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600
    )
    try:
        with os.fdopen(descriptor_fd, "w", encoding="utf-8") as stream:
            stream.write(descriptor)
            stream.flush()
            os.fsync(stream.fileno())
        os.chmod(temporary, 0o600)
        temporary.replace(target)
    finally:
        if temporary.exists() or temporary.is_symlink():
            temporary.unlink()
    return target


def normalized_config(context: ComposeContext, emit: bool) -> dict[str, Any]:
    result = compose(context, "config", "--format", "json", capture=True)
    model = json.loads(result.stdout)
    services = model.get("services", {})
    required = {"keycloak"} if context.environment == "dev" else {"postgres", "keycloak"}
    if not required.issubset(services):
        raise ContractError("normalized Compose model is missing a core service")
    serialized = json.dumps(model)
    forbidden = ("/var/run/docker.sock", "keycloak-supervisor", "keycloak-admin-sanitizer")
    if any(value in serialized for value in forbidden):
        raise ContractError("normalized Compose model contains a retired privileged identity control plane")
    if context.profile == "dev" and {"backend", "mcp"}.intersection(services):
        raise ContractError("dev must keep the application tier on the host")
    if context.environment in {"dogfood", "e2e", "prod"} and not {
        "backend",
        "mcp",
    }.issubset(services):
        raise ContractError(f"{context.profile} normalized model is missing the application tier")
    validate_mount_contract(model)
    if emit:
        print(json.dumps(model, indent=2, sort_keys=True))
    return model


def _write_migration_bootstrap_secret(context: ComposeContext, path: Path) -> None:
    if path.exists() or path.is_symlink():
        raise ContractError("temporary Keycloak migration bootstrap SecretRef already exists")
    descriptor = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    try:
        with os.fdopen(descriptor, "w", encoding="ascii") as stream:
            stream.write(secrets.token_urlsafe(48) + "\n")
            stream.flush()
            os.fsync(stream.fileno())
    finally:
        if path.exists():
            os.chmod(path, 0o600)
            runtime_uid = int(context.env["WEAVE_RUNTIME_UID"])
            runtime_gid = int(context.env["WEAVE_RUNTIME_GID"])
            if path.stat().st_uid != runtime_uid or path.stat().st_gid != runtime_gid:
                try:
                    os.chown(path, runtime_uid, runtime_gid)
                except PermissionError as error:
                    path.unlink()
                    raise ContractError(
                        "temporary Keycloak migration SecretRef is not readable by the rootless migration uid/gid"
                    ) from error


def keycloak_migration_apply(context: ComposeContext) -> None:
    if context.environment != "prod":
        raise ContractError(
            "keycloak-migration-apply is qualified only for production with a verified private backup"
        )
    script(context, "init_secrets.py")
    script(context, "render_config.py")
    prepare(context)
    inputs = migration_inputs(context)
    try:
        require_completed_migration(context)
        print("WEAVE_KEYCLOAK_MIGRATION_RESULT state=already-complete supportSafe=true")
        return
    except ContractError:
        pass
    compose(context, "up", "-d", "--wait", "--wait-timeout", "600", "keycloak")
    backup_proof = create_backup_proof(context)
    credential = context.secret_root / "keycloak-realm-migration-bootstrap-secret"
    _write_migration_bootstrap_secret(context, credential)
    try:
        compose(context, "stop", "--timeout", "30", "keycloak")
        compose(
            context,
            "run",
            "--rm",
            "--no-deps",
            "keycloak-realm-migration-bootstrap",
        )
        compose(context, "up", "-d", "--wait", "--wait-timeout", "600", "keycloak")
        common_arguments = (
            f"--manifest-digest={inputs.manifest_digest}",
            f"--baseline-digest={inputs.baseline_digest}",
            f"--target-revision={inputs.target_revision}",
            f"--environment={context.environment}",
            f"--candidate-commit={context.env['WEAVE_CANDIDATE_COMMIT']}",
            f"--compose-project={context.env['WEAVE_COMPOSE_PROJECT']}",
        )
        compose(
            context,
            "run",
            "--rm",
            "--no-deps",
            "keycloak-realm-migration",
            "keycloak-realm-migration",
            "--artifact-root=/run/weave-generated",
            *common_arguments,
            "--keycloak-base-url=http://keycloak:8080",
            "--bootstrap-secret-file=/run/secrets/keycloak-realm-migration-bootstrap-secret",
            "--backup-proof-file=/run/weave-generated/keycloak/migrations/"
            + backup_proof.name,
            "--timeout=PT10S",
        )
        require_completed_migration(context)
        print("WEAVE_KEYCLOAK_MIGRATION_RESULT state=complete supportSafe=true")
    finally:
        if credential.exists() or credential.is_symlink():
            credential.unlink()


def _service_container(
    context: ComposeContext,
    service: str,
    *,
    include_stopped: bool = False,
    deadline: float | None = None,
) -> dict[str, Any]:
    arguments = ["ps"]
    if include_stopped:
        arguments.append("--all")
    arguments.extend(("-q", service))
    selected_result = (
        compose(context, *arguments, capture=True)
        if deadline is None
        else _bounded_collaboration_compose(
            context, deadline, *arguments, capture=True
        )
    )
    selected = selected_result.stdout.strip().splitlines()
    if len(selected) != 1 or not re.fullmatch(r"[0-9a-f]{64}", selected[0]):
        raise ContractError(f"{service} does not resolve to one exact running container")
    inspect_command = ["docker", "container", "inspect", selected[0]]
    inspected = (
        subprocess.run(
            inspect_command,
            check=True,
            text=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
        )
        if deadline is None
        else _bounded_collaboration_run(
            inspect_command, context, deadline, capture=True
        )
    )
    payload = json.loads(inspected.stdout)
    if not isinstance(payload, list) or len(payload) != 1:
        raise ContractError(f"{service} container inspection is ambiguous")
    container = payload[0]
    labels = container.get("Config", {}).get("Labels", {}) or {}
    if (
        labels.get("com.docker.compose.project") != context.env["WEAVE_COMPOSE_PROJECT"]
        or labels.get("com.docker.compose.service") != service
        or labels.get("com.massimotter.weave.namespace")
        != context.env["WEAVE_RESOURCE_PREFIX"]
    ):
        raise ContractError(f"{service} is outside the exact isolated Compose namespace")
    return container


def _service_snapshot(
    context: ComposeContext,
    service: str,
    *,
    include_stopped: bool = False,
    deadline: float | None = None,
) -> dict[str, Any]:
    container = _service_container(
        context,
        service,
        include_stopped=include_stopped,
        deadline=deadline,
    )
    state = container.get("State", {})
    health = state.get("Health", {}).get("Status", "none")
    return {
        "containerId": str(container.get("Id", "")),
        "startedAt": str(state.get("StartedAt", "")),
        "restartCount": int(container.get("RestartCount", -1)),
        "running": state.get("Running") is True,
        "health": health,
    }


def _await_healthy(
    context: ComposeContext, service: str, deadline: float | None = None
) -> dict[str, Any]:
    effective_deadline = deadline if deadline is not None else time.monotonic() + 180
    last: dict[str, Any] = {}
    while time.monotonic() < effective_deadline:
        try:
            last = _service_snapshot(context, service, deadline=deadline)
            if last["running"] is True and last["health"] == "healthy":
                return last
        except (ContractError, json.JSONDecodeError, subprocess.CalledProcessError):
            last = {}
        remaining = effective_deadline - time.monotonic()
        if remaining > 0:
            time.sleep(min(float(COLLABORATION_HEALTH_POLL_SECONDS), remaining))
    state = "unavailable" if not last else f"running={last['running']} health={last['health']}"
    raise ContractError(f"{service} did not become healthy after the bounded restart: {state}")


def _volume_identity(context: ComposeContext, volume: str) -> str:
    present, owned = inspect_resource(context, "volume", volume)
    if not present or not owned:
        raise ContractError("persistence proof requires the exact owned RuntimeState volume")
    inspected = subprocess.run(
        ["docker", "volume", "inspect", volume, "--format", "{{.Name}}"],
        check=True,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
    ).stdout.strip()
    if inspected != volume:
        raise ContractError("RuntimeState volume identity is ambiguous")
    return "sha256:" + hashlib.sha256(volume.encode("utf-8")).hexdigest()


def _runtime_state_fixture(
    context: ComposeContext,
    operation: str,
    fixture_key: str,
    fixture_value: str,
) -> str:
    if operation not in {"put", "read", "remove"}:
        raise ContractError("unsupported RuntimeState persistence fixture operation")
    common = """
access_key="$(cat /run/secrets/runtime-state-s3-access-key)"
secret_key="$(cat /run/secrets/runtime-state-s3-secret-key)"
mc alias set -- runtime-state http://runtime-state:9000 "${access_key}" "${secret_key}" >/dev/null
""".strip()
    operations = {
        "put": """
printf '%s' "${WEAVE_PERSISTENCE_FIXTURE}" |
  mc pipe "runtime-state/weave-runtime-state/${WEAVE_PERSISTENCE_FIXTURE_KEY}" >/dev/null
mc stat "runtime-state/weave-runtime-state/${WEAVE_PERSISTENCE_FIXTURE_KEY}" >/dev/null
printf '%s' "${WEAVE_PERSISTENCE_FIXTURE}"
""",
        "read": """
mc cat "runtime-state/weave-runtime-state/${WEAVE_PERSISTENCE_FIXTURE_KEY}"
""",
        "remove": """
mc rm --force "runtime-state/weave-runtime-state/${WEAVE_PERSISTENCE_FIXTURE_KEY}" >/dev/null
""",
    }
    result = compose(
        context,
        "run",
        "--rm",
        "--no-deps",
        "--entrypoint",
        "/bin/sh",
        "-e",
        f"WEAVE_PERSISTENCE_FIXTURE_KEY={fixture_key}",
        "-e",
        f"WEAVE_PERSISTENCE_FIXTURE={fixture_value}",
        "runtime-state-init",
        "-euc",
        common + "\n" + operations[operation].strip(),
        capture=True,
    )
    return result.stdout


def _private_json(path: Path, payload: dict[str, Any]) -> None:
    path = path.resolve()
    if path.exists() and (path.is_symlink() or not path.is_file()):
        raise ContractError("private JSON target must be a regular file")
    path.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
    os.chmod(path.parent, 0o700)
    temporary = path.with_name(f".{path.name}.{os.getpid()}.tmp")
    descriptor = os.open(temporary, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    try:
        with os.fdopen(descriptor, "w", encoding="utf-8") as stream:
            json.dump(payload, stream, ensure_ascii=False, indent=2, sort_keys=True)
            stream.write("\n")
            stream.flush()
            os.fsync(stream.fileno())
        os.replace(temporary, path)
        os.chmod(path, 0o600)
    finally:
        if temporary.exists():
            temporary.unlink()


def persistence_restart_proof(context: ComposeContext) -> None:
    if context.environment != "e2e" or context.isolated_namespace is None:
        raise ContractError("persistence-restart-proof is restricted to isolated E2E stacks")
    evidence_value = os.environ.get("WEAVE_TEST_APP_RESTART_EVIDENCE_PATH", "")
    run_root_value = os.environ.get("WEAVE_TEST_APP_RUN_ROOT", "")
    if not evidence_value or not run_root_value:
        raise ContractError("persistence-restart-proof requires the exact private run directory")
    run_root_input = Path(run_root_value)
    if run_root_input.is_symlink():
        raise ContractError("persistence-restart run directory must not be a symlink")
    run_root = run_root_input.resolve()
    evidence = Path(evidence_value).resolve()
    if (
        not run_root.is_dir()
        or evidence.parent != run_root
        or evidence.name != "persistence-restart-evidence.json"
    ):
        raise ContractError("persistence-restart evidence must stay in the exact private run directory")
    candidate = context.env["WEAVE_CANDIDATE_COMMIT"]
    specification = context.env["WEAVE_SPEC_COMMIT"]
    manifest_digest = context.env["WEAVE_CANDIDATE_MANIFEST_DIGEST"]
    fixture_value = "weave.test-app.persistence/v1:" + hashlib.sha256(
        (context.isolated_namespace + "\0" + candidate).encode("utf-8")
    ).hexdigest()
    fixture_key = "test-app-persistence/" + hashlib.sha256(
        fixture_value.encode("ascii")
    ).hexdigest() + ".txt"
    fixture_digest = "sha256:" + hashlib.sha256(fixture_value.encode("ascii")).hexdigest()
    volume = context.env["WEAVE_RUNTIME_STATE_VOLUME"]
    volume_before = _volume_identity(context, volume)
    postgres_before = _service_snapshot(context, "postgres")
    keycloak_before = _service_snapshot(context, "keycloak")
    runtime_state_before = _service_snapshot(context, "runtime-state")
    if (
        not postgres_before["running"]
        or postgres_before["health"] != "healthy"
        or not keycloak_before["running"]
        or keycloak_before["health"] != "healthy"
        or not runtime_state_before["running"]
        or runtime_state_before["health"] != "healthy"
    ):
        raise ContractError("persistence-restart-proof requires healthy starting services")

    fixture_removed = False
    started_at = datetime.now(timezone.utc)
    try:
        stored = _runtime_state_fixture(
            context, "put", fixture_key, fixture_value
        )
        if stored != fixture_value:
            raise ContractError("RuntimeState fixture write was not read back exactly")

        compose(context, "restart", "--no-deps", "--timeout", "20", "postgres")
        postgres_after = _await_healthy(context, "postgres")
        if (
            postgres_after["containerId"] != postgres_before["containerId"]
            or postgres_after["startedAt"] == postgres_before["startedAt"]
        ):
            raise ContractError("PostgreSQL restart identity did not advance exactly")

        # Keycloak owns a JDBC pool against the restarted database. Its
        # management endpoint can stay healthy while a pre-restart connection
        # is stale. Restart the dependent process in the same namespace so the
        # subsequent token flow proves persisted Realm and DCR state.
        compose(context, "restart", "--no-deps", "--timeout", "20", "keycloak")
        keycloak_after = _await_healthy(context, "keycloak")
        if (
            keycloak_after["containerId"] != keycloak_before["containerId"]
            or keycloak_after["startedAt"] == keycloak_before["startedAt"]
        ):
            raise ContractError("Keycloak dependency restart identity did not advance exactly")
        _await_healthy(context, "backend")

        compose(context, "restart", "--no-deps", "--timeout", "20", "runtime-state")
        runtime_state_after = _await_healthy(context, "runtime-state")
        _await_healthy(context, "backend")
        if (
            runtime_state_after["containerId"] != runtime_state_before["containerId"]
            or runtime_state_after["startedAt"] == runtime_state_before["startedAt"]
        ):
            raise ContractError("RuntimeState restart identity did not advance exactly")
        restored = _runtime_state_fixture(
            context, "read", fixture_key, fixture_value
        )
        if restored != fixture_value:
            raise ContractError("RuntimeState fixture did not survive the exact restart")
        volume_after = _volume_identity(context, volume)
        if volume_after != volume_before:
            raise ContractError("RuntimeState volume identity changed across restart")

        _runtime_state_fixture(context, "remove", fixture_key, fixture_value)
        fixture_removed = True
        _private_json(
            evidence,
            {
                "schemaVersion": "weave.test-app-persistence-restart/v1",
                "startedAt": started_at.isoformat().replace("+00:00", "Z"),
                "completedAt": datetime.now(timezone.utc).isoformat().replace("+00:00", "Z"),
                "candidateCommit": candidate,
                "specificationCommit": specification,
                "candidateManifestDigest": manifest_digest,
                "composeProject": context.isolated_namespace,
                "postgres": {
                    "sameContainer": True,
                    "restartObserved": True,
                    "healthyAfterRestart": True,
                    "dependentKeycloakRestartObserved": True,
                    "keycloakHealthyAfterRestart": True,
                },
                "runtimeState": {
                    "sameContainer": True,
                    "restartObserved": True,
                    "healthyAfterRestart": True,
                    "sameVolume": True,
                    "volumeIdentitySha256": volume_after,
                    "fixtureSha256": fixture_digest,
                    "fixtureRestoredExactly": True,
                    "fixtureRemoved": True,
                },
                "classification": {
                    "postgres": "live-product-state",
                    "runtimeState": "live-integration-fixture",
                },
                "credentialsIncluded": False,
                "containsSecretValues": False,
                "supportSafe": True,
            },
        )
    finally:
        if not fixture_removed:
            try:
                _runtime_state_fixture(
                    context, "remove", fixture_key, fixture_value
                )
            except (ContractError, subprocess.CalledProcessError):
                pass
    print(
        "WEAVE_PERSISTENCE_RESTART_RESULT "
        "postgres=healthy runtimeState=healthy fixture=restored supportSafe=true"
    )


def isolated_collaboration_control(context: ComposeContext, operation: str) -> None:
    if context.environment != "e2e" or context.isolated_namespace is None:
        raise ContractError(
            "collaboration service control is restricted to isolated E2E stacks"
        )
    deadline = time.monotonic() + COLLABORATION_CONTROL_BUDGET_SECONDS
    if operation == "stop-provider":
        if "provider-matrix" not in context.active_profiles:
            raise ContractError("Synapse control requires the explicit provider-matrix profile")
        before = _service_snapshot(context, "synapse", deadline=deadline)
        _bounded_collaboration_compose(
            context, deadline, "stop", "--timeout", "20", "synapse"
        )
        snapshot = _service_snapshot(
            context, "synapse", include_stopped=True, deadline=deadline
        )
        if snapshot["containerId"] != before["containerId"] or snapshot["running"]:
            raise ContractError("isolated Synapse provider did not stop")
        print("WEAVE_CHAT_PROVIDER_CONTROL_RESULT state=stopped supportSafe=true")
        return
    if operation == "start-provider":
        if "provider-matrix" not in context.active_profiles:
            raise ContractError("Synapse control requires the explicit provider-matrix profile")
        _bounded_collaboration_compose(context, deadline, "start", "synapse")
        _await_healthy(context, "synapse", deadline)
        _await_healthy(context, "backend", deadline)
        print("WEAVE_CHAT_PROVIDER_CONTROL_RESULT state=healthy supportSafe=true")
        return
    if operation == "restart-collaboration":
        postgres_before = _service_snapshot(context, "postgres", deadline=deadline)
        keycloak_before = _service_snapshot(context, "keycloak", deadline=deadline)
        backend_before = _service_snapshot(context, "backend", deadline=deadline)
        _bounded_collaboration_compose(
            context,
            deadline,
            "restart",
            "--no-deps",
            "--timeout",
            "20",
            "postgres",
        )
        postgres_after = _await_healthy(context, "postgres", deadline)
        # Keycloak retains a JDBC pool to PostgreSQL. A green management
        # endpoint alone does not prove those pre-restart connections are
        # usable, so restart the dependent identity process before exercising
        # the post-restart browser login.
        _bounded_collaboration_compose(
            context,
            deadline,
            "restart",
            "--no-deps",
            "--timeout",
            "20",
            "keycloak",
        )
        keycloak_after = _await_healthy(context, "keycloak", deadline)
        _bounded_collaboration_compose(
            context,
            deadline,
            "restart",
            "--no-deps",
            "--timeout",
            "20",
            "backend",
        )
        backend_after = _await_healthy(context, "backend", deadline)
        if (
            postgres_after["containerId"] != postgres_before["containerId"]
            or postgres_after["startedAt"] == postgres_before["startedAt"]
            or keycloak_after["containerId"] != keycloak_before["containerId"]
            or keycloak_after["startedAt"] == keycloak_before["startedAt"]
            or backend_after["containerId"] != backend_before["containerId"]
            or backend_after["startedAt"] == backend_before["startedAt"]
        ):
            raise ContractError(
                "collaboration service restart identity did not advance exactly"
            )
        print(
            "WEAVE_COLLABORATION_RESTART_RESULT backend=healthy "
            "keycloak=healthy postgres=healthy providerDependency=false supportSafe=true"
        )
        return
    raise ContractError("unsupported isolated collaboration control operation")


def _owner_bootstrap_arguments(extra: list[str]) -> tuple[Path, Path | None]:
    if len(extra) not in {2, 4} or extra[0] != "--request-file":
        raise ContractError(
            "bootstrap-owner requires --request-file <private-json> "
            "[--evidence-file <private-json>]"
        )
    request_file = Path(extra[1]).expanduser().resolve()
    evidence_file: Path | None = None
    if len(extra) == 4:
        if extra[2] != "--evidence-file":
            raise ContractError("bootstrap-owner accepts only --evidence-file after the request")
        evidence_file = Path(extra[3]).expanduser().resolve()
    return request_file, evidence_file


def _owner_bootstrap_request(path: Path) -> dict[str, str]:
    if path.is_symlink() or not path.is_file():
        raise ContractError("owner bootstrap request must be one regular private file")
    if stat.S_IMODE(path.stat().st_mode) & 0o077:
        raise ContractError("owner bootstrap request must not grant group or other access")
    try:
        request = json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError as error:
        raise ContractError("owner bootstrap request is not valid JSON") from error
    expected = {"displayName", "email", "idempotencyKey"}
    if not isinstance(request, dict) or set(request) != expected:
        raise ContractError("owner bootstrap request has an invalid shape")
    if not all(isinstance(request[key], str) for key in expected):
        raise ContractError("owner bootstrap request values must be strings")
    email = request["email"].strip().lower()
    if "@" not in email or len(email) > 320:
        raise ContractError("owner bootstrap request has no bounded email address")
    request["email"] = email
    return request


def _owner_bootstrap_smtp_preflight(context: ComposeContext) -> None:
    realm_path = context.generated_root / "keycloak/import/weave-realm.json"
    if realm_path.is_symlink() or not realm_path.is_file():
        raise ContractError("owner bootstrap requires the rendered Keycloak realm baseline")
    realm = json.loads(realm_path.read_text(encoding="utf-8"))
    smtp = realm.get("smtpServer")
    if smtp != {
        "from": "noreply@weave.test",
        "fromDisplayName": "Weave",
        "host": "mailpit",
        "port": "1025",
        "ssl": "true",
        "starttls": "false",
    }:
        raise ContractError(
            "dogfood owner bootstrap requires the reviewed implicit-TLS Mailpit SMTP baseline"
        )


def _mailpit_addresses(value: object) -> list[str]:
    if isinstance(value, dict):
        addresses: list[str] = []
        for key, child in value.items():
            if key.lower() in {"address", "email"} and isinstance(child, str):
                addresses.append(child.strip().lower())
            elif isinstance(child, (dict, list)):
                addresses.extend(_mailpit_addresses(child))
        return addresses
    if isinstance(value, list):
        addresses = []
        for child in value:
            addresses.extend(_mailpit_addresses(child))
        return addresses
    return []


def _mailpit_recipient_summaries(
    context: ComposeContext, email_sha256: str
) -> dict[str, str]:
    url = (
        "http://127.0.0.1:"
        + context.env["WEAVE_MAILPIT_WEB_HOST_PORT"]
        + "/api/v1/messages"
    )
    with urllib.request.urlopen(url, timeout=10) as response:
        payload_bytes = response.read(2 * 1024 * 1024 + 1)
    if len(payload_bytes) > 2 * 1024 * 1024:
        raise ContractError("Mailpit summary response exceeded the bounded size")
    payload = json.loads(payload_bytes)
    messages = payload.get("messages") if isinstance(payload, dict) else payload
    if not isinstance(messages, list):
        raise ContractError("Mailpit did not return a message summary list")
    matches: dict[str, str] = {}
    for message in messages:
        if not isinstance(message, dict):
            continue
        recipients = _mailpit_addresses(message.get("To", message.get("to", [])))
        if not any(
            hashlib.sha256(recipient.encode("utf-8")).hexdigest() == email_sha256
            for recipient in recipients
        ):
            continue
        message_id = next(
            (
                str(message[key]).strip()
                for key in ("ID", "Id", "id")
                if isinstance(message.get(key), str) and str(message[key]).strip()
            ),
            "",
        )
        if not message_id:
            raise ContractError("Mailpit recipient summary has no bounded message identifier")
        observed_at = next(
            (
                str(message[key]).strip()
                for key in ("Created", "CreatedAt", "created", "createdAt")
                if isinstance(message.get(key), str) and str(message[key]).strip()
            ),
            "unavailable",
        )
        matches[message_id] = observed_at
    return matches


def _bootstrap_boundary_present(container: dict[str, Any]) -> bool:
    environment = container.get("Config", {}).get("Env", []) or []
    mounts = container.get("Mounts", []) or []
    return any(
        str(value).startswith("WEAVE_IDENTITY_BOOTSTRAP_OWNER_")
        for value in environment
    ) or any(
        str(mount.get("Destination", "")).startswith(
            "/run/secrets/weave/bootstrap-owner"
        )
        for mount in mounts
        if isinstance(mount, dict)
    )


def _canonical_backend(context: ComposeContext) -> dict[str, Any]:
    compose(
        context,
        "up",
        "-d",
        "--no-deps",
        "--force-recreate",
        "--wait",
        "--wait-timeout",
        "180",
        "backend",
    )
    container = _service_container(context, "backend")
    if _bootstrap_boundary_present(container):
        raise ContractError("canonical backend retained owner bootstrap authority")
    return container


def _bootstrap_disabled(context: ComposeContext) -> bool:
    url = context.env["WEAVE_API_ORIGIN"].rstrip("/") + "/api/bootstrap/owner-invitation"
    request = urllib.request.Request(
        url,
        data=b"{}",
        method="POST",
        headers={
            "Content-Type": "application/json",
            "X-Weave-Bootstrap-Token": secrets.token_urlsafe(32),
        },
    )
    tls = ssl.create_default_context(cafile=str(context.tls_root / "ca.pem"))
    try:
        urllib.request.urlopen(request, context=tls, timeout=10)
    except urllib.error.HTTPError as error:
        return error.code == 404
    return False


def _bootstrap_override_command(
    context: ComposeContext, override: Path, *arguments: str
) -> subprocess.CompletedProcess[str]:
    command = [*context.compose_base_command, "--file", str(override), *arguments]
    return run(command, context)


def owner_bootstrap(context: ComposeContext, extra: list[str]) -> None:
    if context.environment != "dogfood" or context.isolated_namespace is not None:
        raise ContractError("bootstrap-owner is restricted to persistent dogfood")
    request_path, requested_evidence_path = _owner_bootstrap_arguments(extra)
    owner_request = _owner_bootstrap_request(request_path)
    owner_bootstrap_root = context.generated_root / "owner-bootstrap"
    request_anchor_path = owner_bootstrap_root / "request-anchor.json"
    anchor_evidence_path = owner_bootstrap_root / "evidence.json"
    evidence_path = requested_evidence_path or anchor_evidence_path
    owner_email_sha256 = hashlib.sha256(
        owner_request["email"].encode("utf-8")
    ).hexdigest()
    owner_idempotency_sha256 = hashlib.sha256(
        owner_request["idempotencyKey"].encode("utf-8")
    ).hexdigest()
    prepare(context)
    require_completed_migration(context)
    _owner_bootstrap_smtp_preflight(context)
    lock_root = context.generated_root / "operations"
    lock_root.mkdir(parents=True, exist_ok=True, mode=0o700)
    os.chmod(lock_root, 0o700)
    lock_fd = os.open(lock_root / "owner-bootstrap.lock", os.O_RDWR | os.O_CREAT, 0o600)
    operation_root: Path | None = None
    override: Path | None = None
    primary_error: BaseException | None = None
    helper_evidence: dict[str, Any] | None = None
    matched_message: tuple[str, str] | None = None
    canonical_image = ""
    try:
        try:
            fcntl.flock(lock_fd, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except BlockingIOError as error:
            raise ContractError("another dogfood owner bootstrap operation is active") from error
        for service in ("keycloak", "mailpit", "backend"):
            _await_healthy(context, service)
        canonical_before = _service_container(context, "backend")
        if _bootstrap_boundary_present(canonical_before):
            canonical_before = _canonical_backend(context)
        canonical_image = str(canonical_before.get("Image", ""))
        if not re.fullmatch(r"sha256:[0-9a-f]{64}", canonical_image):
            raise ContractError("canonical backend image identity is unavailable")
        if request_anchor_path.exists() or request_anchor_path.is_symlink():
            if request_anchor_path.is_symlink() or not request_anchor_path.is_file():
                raise ContractError("owner bootstrap request anchor is not a regular file")
            previous = json.loads(request_anchor_path.read_text(encoding="utf-8"))
            if (
                previous.get("emailSha256") != owner_email_sha256
                or previous.get("idempotencyKeySha256") != owner_idempotency_sha256
            ):
                raise ContractError(
                    "owner bootstrap request differs from the protected first-owner anchor"
                )
        else:
            # Persist the support-safe correlation before enabling the mutation
            # route. If anything fails after Keycloak accepts the invitation,
            # only an exact retry can re-enter the bounded bootstrap lifecycle.
            _private_json(
                request_anchor_path,
                {
                    "schemaVersion": "weave-owner-bootstrap-request-anchor-v1",
                    "emailSha256": owner_email_sha256,
                    "idempotencyKeySha256": owner_idempotency_sha256,
                    "supportSafe": True,
                },
            )
        before_messages = _mailpit_recipient_summaries(context, owner_email_sha256)

        operation_root = context.secret_root / (
            ".owner-bootstrap-" + secrets.token_hex(12)
        )
        operation_root.mkdir(mode=0o700)
        runtime_uid = int(context.env["WEAVE_RUNTIME_UID"])
        runtime_gid = int(context.env["WEAVE_RUNTIME_GID"])
        os.chown(operation_root, runtime_uid, runtime_gid)
        token = operation_root / "token"
        token_fd = os.open(token, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
        with os.fdopen(token_fd, "w", encoding="ascii") as stream:
            stream.write(secrets.token_urlsafe(48) + "\n")
            stream.flush()
            os.fsync(stream.fileno())
        os.chown(token, runtime_uid, runtime_gid)

        override = lock_root / (operation_root.name + ".compose.json")
        _private_json(
            override,
            {
                "services": {
                    "backend": {
                        "environment": {
                            "WEAVE_IDENTITY_BOOTSTRAP_OWNER_ENABLED": "true",
                            "WEAVE_IDENTITY_BOOTSTRAP_OWNER_TOKEN_FILE": (
                                "/run/secrets/weave/bootstrap-owner/token"
                            ),
                        },
                        "volumes": [
                            {
                                "type": "bind",
                                "source": str(operation_root),
                                "target": "/run/secrets/weave/bootstrap-owner",
                                "read_only": False,
                            }
                        ],
                    }
                }
            },
        )
        _bootstrap_override_command(
            context,
            override,
            "up",
            "-d",
            "--no-deps",
            "--force-recreate",
            "--wait",
            "--wait-timeout",
            "180",
            "backend",
        )
        temporary_evidence = lock_root / (operation_root.name + ".helper.json")
        helper = subprocess.run(
            [
                "python3",
                str(context.repository_root / "gradle/tasks/bootstrap-owner.py"),
                "--api-base-url",
                context.env["WEAVE_API_ORIGIN"],
                "--token-file",
                str(token),
                "--request-file",
                str(request_path),
                "--ca-file",
                str(context.tls_root / "ca.pem"),
                "--evidence",
                str(temporary_evidence),
            ],
            cwd=context.repository_root,
            env=compose_environment(context),
            check=True,
            text=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
        )
        if helper.stderr.strip():
            raise ContractError("owner bootstrap helper emitted unexpected diagnostics")
        helper_evidence = json.loads(temporary_evidence.read_text(encoding="utf-8"))
        temporary_evidence.unlink()
        deadline = time.monotonic() + 30
        after_messages: dict[str, str] = {}
        while time.monotonic() < deadline:
            after_messages = _mailpit_recipient_summaries(context, owner_email_sha256)
            new_ids = sorted(set(after_messages) - set(before_messages))
            if new_ids:
                message_id = new_ids[-1]
                matched_message = (message_id, after_messages[message_id])
                break
            time.sleep(1)
        if matched_message is None:
            raise ContractError("Mailpit did not capture a new owner invitation message")
    except BaseException as error:
        primary_error = error
    finally:
        restoration_error: BaseException | None = None
        try:
            restored = _canonical_backend(context)
            if canonical_image and restored.get("Image") != canonical_image:
                raise ContractError("owner bootstrap changed the canonical backend image")
            if not _bootstrap_disabled(context):
                raise ContractError("owner bootstrap endpoint remained available after cleanup")
        except BaseException as error:
            restoration_error = error
        if operation_root is not None:
            token = operation_root / "token"
            if token.exists() or token.is_symlink():
                token.unlink()
            if operation_root.exists():
                shutil.rmtree(operation_root)
        if override is not None and (override.exists() or override.is_symlink()):
            override.unlink()
        os.close(lock_fd)
        if restoration_error is not None:
            raise ContractError("owner bootstrap could not restore the canonical backend boundary") from restoration_error
    if primary_error is not None:
        if isinstance(primary_error, ContractError):
            raise primary_error
        raise ContractError("owner bootstrap failed before producing support-safe evidence") from primary_error
    if helper_evidence is None or matched_message is None or operation_root is None:
        raise ContractError("owner bootstrap completed without bounded evidence")
    evidence = {
        **helper_evidence,
        "mailMessageIdSha256": hashlib.sha256(
            matched_message[0].encode("utf-8")
        ).hexdigest(),
        "mailObservedAt": matched_message[1],
        "mailMessageMatched": True,
        "activation": {
            "mode": "keycloak-organizations-invitation",
            "mailSent": True,
            "requiredActions": [],
        },
        "qrOrDeeplinkCarriesSecret": False,
        "appStoresActivationSecret": False,
        "idempotencyKeySha256": owner_idempotency_sha256,
        "bootstrapAuthorityAbsent": True,
        "bootstrapMountAbsent": True,
        "canonicalImageUnchanged": True,
        "requestAnchorPresent": request_anchor_path.is_file(),
        "tokenAbsent": not operation_root.exists(),
    }
    _private_json(anchor_evidence_path, evidence)
    if evidence_path != anchor_evidence_path:
        _private_json(evidence_path, evidence)
    print(
        "WEAVE_OWNER_BOOTSTRAP_RESULT mailMessageMatched=true "
        "bootstrapAuthorityAbsent=true tokenAbsent=true supportSafe=true"
    )


def _remove_exact_dogfood_resource(kind: str, name: str) -> None:
    inspected = subprocess.run(
        ["docker", kind, "inspect", name],
        check=False,
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
    )
    if inspected.returncode == 0:
        subprocess.run(["docker", kind, "rm", name], check=True)


def _inspect_retired_resource(kind: str, name: str) -> dict[str, Any] | None:
    inspected = subprocess.run(
        ["docker", kind, "inspect", name],
        check=False,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.DEVNULL,
    )
    if inspected.returncode != 0:
        return None
    payload = json.loads(inspected.stdout)
    if not isinstance(payload, list) or len(payload) != 1:
        raise ContractError(f"invalid Docker inspection payload for retired {kind} {name}")
    return payload[0]


def cleanup_retired_dogfood() -> None:
    """Remove only the one known unlabeled OpenTofu-era dogfood stack."""

    containers: list[str] = []
    for name in RETIRED_DOGFOOD_CONTAINERS:
        inspected = _inspect_retired_resource("container", name)
        if inspected is None:
            continue
        labels = inspected.get("Config", {}).get("Labels") or {}
        if labels.get("com.docker.compose.project"):
            raise ContractError(f"refusing Compose-owned retired container {name}")
        networks = set((inspected.get("NetworkSettings", {}).get("Networks") or {}).keys())
        if networks != {RETIRED_DOGFOOD_NETWORK}:
            raise ContractError(f"retired container {name} is attached outside weave_network")
        mounted_volumes = {
            str(mount.get("Name", ""))
            for mount in inspected.get("Mounts", [])
            if mount.get("Type") == "volume"
        }
        if not mounted_volumes.issubset(RETIRED_DOGFOOD_VOLUMES):
            raise ContractError(f"retired container {name} mounts an unknown Docker volume")
        containers.append(name)

    network = _inspect_retired_resource("network", RETIRED_DOGFOOD_NETWORK)
    if network is not None:
        labels = network.get("Labels") or {}
        if labels.get("com.docker.compose.project"):
            raise ContractError("refusing Compose-owned retired weave_network")
        attached = {
            str(value.get("Name", ""))
            for value in (network.get("Containers") or {}).values()
        }
        if not attached.issubset(RETIRED_DOGFOOD_CONTAINERS):
            raise ContractError("retired weave_network contains a foreign container")

    volumes: list[str] = []
    for name in RETIRED_DOGFOOD_VOLUMES:
        inspected = _inspect_retired_resource("volume", name)
        if inspected is None:
            continue
        labels = inspected.get("Labels") or {}
        if labels.get("com.docker.compose.project"):
            raise ContractError(f"refusing Compose-owned retired volume {name}")
        users = subprocess.run(
            [
                "docker",
                "container",
                "ls",
                "--all",
                "--filter",
                f"volume={name}",
                "--format",
                "{{.Names}}",
            ],
            check=True,
            text=True,
            stdout=subprocess.PIPE,
        ).stdout.splitlines()
        if not set(users).issubset(RETIRED_DOGFOOD_CONTAINERS):
            raise ContractError(f"retired volume {name} is used by a foreign container")
        volumes.append(name)

    # All ownership checks complete before the first mutation. Bind-mounted
    # paths, including every historical TLS directory, are deliberately not
    # removal targets.
    if containers:
        subprocess.run(
            ["docker", "container", "rm", "--force", *containers], check=True
        )
    if volumes:
        subprocess.run(["docker", "volume", "rm", *volumes], check=True)
    if network is not None:
        subprocess.run(
            ["docker", "network", "rm", RETIRED_DOGFOOD_NETWORK], check=True
        )
    if containers or volumes or network is not None:
        print(
            "WEAVE_DOGFOOD_RETIRED_CLEANUP_RESULT "
            f"containers={len(containers)} volumes={len(volumes)} "
            f"network={str(network is not None).lower()} tls=preserved"
        )


def preflight_dogfood_reset(context: ComposeContext) -> None:
    """Prove configuration, secrets, and stable TLS before any reset mutation."""

    script(context, "init_secrets.py")
    script(context, "render_config.py")
    prepare(context)


def reset_dogfood(context: ComposeContext) -> None:
    """Reset only the fixed dogfood project boundary and its session data."""

    if context.environment != "dogfood":
        raise ContractError("reset is available only for the dogfood environment")
    if context.env["WEAVE_COMPOSE_PROJECT"] != "weave-dogfood":
        raise ContractError("dogfood reset requires WEAVE_COMPOSE_PROJECT=weave-dogfood")
    preflight_dogfood_reset(context)
    compose(context, "down", "--volumes", "--remove-orphans")
    cleanup_retired_dogfood()
    for key in (
        "WEAVE_DB_DATA_VOLUME",
        "WEAVE_NATIVE_FILES_DATA_VOLUME",
        "WEAVE_MAILPIT_DATA_VOLUME",
    ):
        _remove_exact_dogfood_resource("volume", context.env[key])
    _remove_exact_dogfood_resource("network", context.env["WEAVE_DOCKER_NETWORK"])
    execute(context, "up", [])


def execute(context: ComposeContext, command: str, extra: list[str]) -> None:
    if command == "secrets-init":
        script(context, "init_secrets.py")
    elif command == "render":
        script(context, "render_config.py")
    elif command == "configure":
        script(context, "init_secrets.py")
        script(context, "render_config.py")
        prepare(context)
    elif command == "config":
        normalized_config(context, emit=True)
    elif command == "prepare":
        prepare(context)
    elif command == "provider-prepare":
        subprocess.run([str(context.root / "provision-matrix-default-workspace.sh")], cwd=context.root, env=compose_environment(context), check=True)
    elif command == "keycloak-migration-apply":
        if extra:
            raise ContractError("keycloak-migration-apply does not accept command arguments")
        keycloak_migration_apply(context)
    elif command == "bootstrap-owner":
        owner_bootstrap(context, extra)
    elif command == "up":
        script(context, "init_secrets.py")
        script(context, "render_config.py")
        prepare(context)
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
        if context.environment != "dev":
            compose(context, "up", "-d", "postgres", "postgres-reconcile")
        compose(context, "up", "-d", "--wait", "--wait-timeout", "600", "keycloak")
        if context.environment in {"e2e", "prod"}:
            require_completed_migration(context)
        compose(
            context,
            "up",
            "-d",
            "--remove-orphans",
            "--wait",
            "--wait-timeout",
            "600",
            *runtime_root_services(context),
        )
        if context.environment != "dev" and "provider-nextcloud" in context.active_profiles:
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
    elif command == "reset":
        if extra:
            raise ContractError("reset does not accept command arguments")
        reset_dogfood(context)
    elif command in {"ps", "logs"}:
        compose(context, command, *extra)
    elif command == "persistence-restart-proof":
        if extra:
            raise ContractError("persistence-restart-proof does not accept command arguments")
        persistence_restart_proof(context)
    elif command in {
        "chat-provider-stop-proof",
        "chat-provider-start-proof",
        "collaboration-restart-proof",
    }:
        if extra:
            raise ContractError(f"{command} does not accept command arguments")
        operation = {
            "chat-provider-stop-proof": "stop-provider",
            "chat-provider-start-proof": "start-provider",
            "collaboration-restart-proof": "restart-collaboration",
        }[command]
        isolated_collaboration_control(context, operation)
    else:
        raise ContractError(f"unsupported Compose operation: {command}")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, required=True)
    parser.add_argument(
        "profile",
        choices=("dev", "dogfood", "prod", "e2e"),
        help="operator environment",
    )
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
