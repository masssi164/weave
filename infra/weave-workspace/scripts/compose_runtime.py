#!/usr/bin/env python3
"""Closed operator interface for normalized Compose and one-shot Identity Ops."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import stat
import subprocess
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from datetime import datetime, timezone
from pathlib import Path, PurePosixPath
from typing import Any

from bounded_process import BoundedProcessTimeout, run_bounded
from compose_env import (
    ComposeContext,
    ContractError,
    compose_environment,
    derived_profiles,
    load_context,
    run,
)


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
    "persistence-restart-proof",
    "chat-provider-stop-proof",
    "chat-provider-start-proof",
    "collaboration-restart-proof",
)
RUNTIME_ROOT_SERVICES = {
    # Normal development runs Server, MCP, and Admin Console on the host. The
    # Compose lifecycle converges only the database/Keycloak dependency path.
    "dev": ("keycloak",),
    "dogfood": ("caddy", "mcp"),
    "e2e": ("caddy", "mcp"),
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
        return ("WEAVE_KEYCLOAK_DATA_VOLUME",)
    keys = [
        "WEAVE_CADDY_DATA_VOLUME",
        "WEAVE_CADDY_CONFIG_VOLUME",
        "WEAVE_DB_DATA_VOLUME",
        "WEAVE_KEYCLOAK_DATA_VOLUME",
        "WEAVE_NATIVE_FILES_DATA_VOLUME",
    ]
    profiles = set(
        getattr(
            context,
            "active_profiles",
            derived_profiles(
                getattr(context, "environment", context.profile),
                context.profile,
                context.env,
            ),
        )
    )
    if "e2e" in profiles or "dev-tools" in profiles:
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
IDENTITY_ADMIN_INITIALIZER_TARGET = PurePosixPath(
    "/authority/private/weave-identity-admin-private-jwk.json"
)
AGENT_RUNTIME_MOUNT_POLICY = {
    ("agent-runtime-keys-init", str(PROFILE_SIGNING_TARGET)): ("read-write", "directory"),
    ("agent-runtime-keys-init", str(STATE_WRAPPING_TARGET)): ("read-write", "directory"),
    ("backend", str(WORKLOADS_TARGET)): ("read-write", "directory"),
    ("backend", str(PROFILE_SIGNING_TARGET)): ("read-only", "directory"),
    ("backend", str(STATE_WRAPPING_TARGET)): ("read-only", "directory"),
    ("identity-ops", str(RUNTIME_ADMIN_TARGET)): ("read-only", "file"),
}
MCP_PROTECTED_SECRET_MARKERS = (
    "weave-agent-runtime-admin",
    "weave-identity-admin",
    "weave-backend-jwk",
    "/agent-runtime/workloads/",
)
COLLABORATION_CONTROL_BUDGET_SECONDS = 150
COLLABORATION_SUBPROCESS_TIMEOUT_SECONDS = 30
COLLABORATION_HEALTH_POLL_SECONDS = 2


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
    if "e2e" in profiles:
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
        (
            "identity-admin-key-init",
            str(IDENTITY_ADMIN_INITIALIZER_TARGET),
            "read-only",
            "file",
        ),
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
            "identity-admin private JWK must be mounted only by Server and its one-shot initializer"
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
    ensure_resource(context, "network", context.env["WEAVE_DOCKER_NETWORK"])
    for key in active_volume_keys(context):
        ensure_resource(context, "volume", context.env[key])


def normalized_config(context: ComposeContext, emit: bool) -> dict[str, Any]:
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
    if context.environment in {"dogfood", "e2e", "prod"} and not {
        "backend",
        "mcp",
    }.issubset(services):
        raise ContractError(f"{context.profile} normalized model is missing the application tier")
    validate_mount_contract(model)
    if emit:
        print(json.dumps(model, indent=2, sort_keys=True))
    return model


def identity_ops(context: ComposeContext, action: str) -> None:
    prepare(context)
    compose(context, "up", "-d", "--wait", "keycloak")
    if not bootstrap_authority_available(context):
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
        if not bootstrap_authority_available(context):
            raise ContractError(
                "temporary Identity Ops bootstrap authority is unavailable after recovery"
            )
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


def bootstrap_authority_available(context: ComposeContext) -> bool:
    """Probe the one-shot bootstrap client without retaining its access token."""
    credential = context.secret_root / "keycloak-bootstrap-admin-password"
    try:
        metadata = credential.lstat()
    except OSError as error:
        raise ContractError(
            "Identity Ops bootstrap SecretRef is unavailable"
        ) from error
    if (
        credential.is_symlink()
        or not stat.S_ISREG(metadata.st_mode)
        or stat.S_IMODE(metadata.st_mode) != 0o600
    ):
        raise ContractError("Identity Ops bootstrap SecretRef is unsafe")
    secret = credential.read_text(encoding="utf-8").strip()
    if not secret:
        raise ContractError("Identity Ops bootstrap SecretRef is empty")
    request = urllib.request.Request(
        (
            "http://127.0.0.1:"
            + context.env["WEAVE_KEYCLOAK_HOST_PORT"]
            + "/realms/master/protocol/openid-connect/token"
        ),
        data=urllib.parse.urlencode(
            {
                "grant_type": "client_credentials",
                "client_id": "weave-identity-ops-bootstrap",
                "client_secret": secret,
            }
        ).encode("ascii"),
        headers={"Content-Type": "application/x-www-form-urlencoded"},
        method="POST",
    )
    try:
        with urllib.request.urlopen(request, timeout=10) as response:
            try:
                payload = json.loads(response.read(1024 * 1024))
            except (json.JSONDecodeError, UnicodeDecodeError) as error:
                raise ContractError(
                    "temporary Identity Ops bootstrap authority probe was malformed"
                ) from error
            if response.status != 200 or not isinstance(
                payload.get("access_token"), str
            ):
                raise ContractError(
                    "temporary Identity Ops bootstrap authority probe was malformed"
                )
            return True
    except urllib.error.HTTPError as error:
        try:
            payload = json.loads(error.read(1024 * 1024))
        except (json.JSONDecodeError, UnicodeDecodeError):
            payload = {}
        if error.code in {400, 401} and payload.get("error") == "invalid_client":
            return False
        raise ContractError(
            "temporary Identity Ops bootstrap authority probe was rejected"
        ) from error
    except (urllib.error.URLError, TimeoutError) as error:
        raise ContractError(
            "temporary Identity Ops bootstrap authority probe was unavailable"
        ) from error


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
        raise ContractError("persistence-restart evidence target must be a regular file")
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
        _bounded_collaboration_compose(context, deadline, "start", "synapse")
        _await_healthy(context, "synapse", deadline)
        _await_healthy(context, "backend", deadline)
        print("WEAVE_CHAT_PROVIDER_CONTROL_RESULT state=healthy supportSafe=true")
        return
    if operation == "restart-collaboration":
        synapse_before = _service_snapshot(context, "synapse", deadline=deadline)
        backend_before = _service_snapshot(context, "backend", deadline=deadline)
        _bounded_collaboration_compose(
            context,
            deadline,
            "restart",
            "--no-deps",
            "--timeout",
            "20",
            "synapse",
        )
        synapse_after = _await_healthy(context, "synapse", deadline)
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
            synapse_after["containerId"] != synapse_before["containerId"]
            or synapse_after["startedAt"] == synapse_before["startedAt"]
            or backend_after["containerId"] != backend_before["containerId"]
            or backend_after["startedAt"] == backend_before["startedAt"]
        ):
            raise ContractError(
                "collaboration service restart identity did not advance exactly"
            )
        print(
            "WEAVE_COLLABORATION_RESTART_RESULT backend=healthy "
            "synapse=healthy supportSafe=true"
        )
        return
    raise ContractError("unsupported isolated collaboration control operation")


def adopt_secret_updates(context: ComposeContext) -> None:
    updates = context.generated_root / "identity-ops/secret-updates"
    if not updates.exists():
        return
    allowed = {
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
        identity_ops(context, "identity-apply")
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
        identity_ops(context, command)


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
