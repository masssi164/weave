#!/usr/bin/env python3
"""Destroy only an exactly owned disposable Compose namespace."""

from __future__ import annotations

import argparse
import json
import os
import re
import subprocess
import time
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path

from compose_env import ComposeContext, ContractError, compose_environment, load_context


VOLUME_KEYS = (
    "WEAVE_CADDY_DATA_VOLUME", "WEAVE_CADDY_CONFIG_VOLUME", "WEAVE_DB_DATA_VOLUME",
    "WEAVE_KEYCLOAK_DATA_VOLUME", "WEAVE_MAILPIT_DATA_VOLUME", "WEAVE_NEXTCLOUD_DATA_VOLUME",
    "WEAVE_SYNAPSE_DATA_VOLUME", "WEAVE_MATRIX_APPSERVICE_VOLUME",
    "WEAVE_RUNTIME_STATE_VOLUME",
    "WEAVE_NATIVE_FILES_DATA_VOLUME",
)
TEARDOWN_BUDGET_SECONDS = 240
COMPOSE_DOWN_TIMEOUT_SECONDS = 120
DOCKER_CALL_TIMEOUT_SECONDS = 20
INSPECT_CONSISTENCY_ATTEMPTS = 5
INSPECT_CONSISTENCY_WAIT_SECONDS = 1
CONTAINER_REMOVAL_ATTEMPTS = 12
CONTAINER_REMOVAL_WAIT_SECONDS = 5


@dataclass(frozen=True)
class OwnershipBinding:
    candidate_commit: str
    candidate_manifest_digest: str
    compose_project: str


def _remaining_timeout(deadline: float, limit: int = DOCKER_CALL_TIMEOUT_SECONDS) -> float:
    remaining = deadline - time.monotonic()
    if remaining <= 0:
        raise ContractError("isolated teardown deadline exhausted")
    return min(float(limit), remaining)


def _run_docker(
    command: list[str],
    *,
    deadline: float,
    operation: str,
    timeout_limit: int = DOCKER_CALL_TIMEOUT_SECONDS,
    tolerate_timeout: bool = False,
    **kwargs,
) -> subprocess.CompletedProcess:
    try:
        return subprocess.run(
            command,
            timeout=_remaining_timeout(deadline, timeout_limit),
            **kwargs,
        )
    except subprocess.TimeoutExpired:
        if tolerate_timeout:
            return subprocess.CompletedProcess(command, 124)
        raise ContractError(
            f"isolated teardown Docker {operation} timed out"
        ) from None


def _labels(
    kind: str, name: str, *, deadline: float
) -> dict[str, str] | None:
    for attempt in range(INSPECT_CONSISTENCY_ATTEMPTS):
        result = _run_docker(
            ["docker", kind, "inspect", name, "--format", "{{json .Labels}}"],
            deadline=deadline,
            operation=f"{kind}-inspect",
            text=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.DEVNULL,
        )
        if result.returncode == 0:
            value = json.loads(result.stdout)
            return value if isinstance(value, dict) else {}

        identity_field = "{{.ID}}" if kind == "container" else "{{.Name}}"
        inventory = _run_docker(
            ["docker", kind, "ls", "--all", "--no-trunc", "--format", identity_field]
            if kind == "container"
            else ["docker", kind, "ls", "--format", identity_field],
            deadline=deadline,
            operation=f"{kind}-inventory-after-inspect",
            text=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.DEVNULL,
        )
        if inventory.returncode != 0:
            raise ContractError(
                f"isolated teardown could not inventory Docker {kind} resources"
            )
        identities = {
            line.strip()
            for line in inventory.stdout.splitlines()
            if line.strip()
        }
        if name not in identities:
            return None
        if attempt + 1 < INSPECT_CONSISTENCY_ATTEMPTS:
            time.sleep(
                min(
                    float(INSPECT_CONSISTENCY_WAIT_SECONDS),
                    _remaining_timeout(deadline, INSPECT_CONSISTENCY_WAIT_SECONDS),
                )
            )
    raise ContractError(
        f"isolated teardown could not inspect existing Docker {kind} {name}"
    )


def _assert_owned(
    context: ComposeContext,
    binding: OwnershipBinding,
    kind: str,
    name: str,
    *,
    deadline: float,
) -> bool:
    observed = _labels(kind, name, deadline=deadline)
    if observed is None:
        return False
    expected = _expected_labels(context, binding, kind)
    if any(observed.get(key) != value for key, value in expected.items()):
        raise ContractError(f"refusing to remove unowned Docker {kind} {name}")
    return True


def _expected_labels(
    context: ComposeContext,
    binding: OwnershipBinding,
    kind: str,
) -> dict[str, str]:
    expected = {
        "com.massimotter.weave.managed": "true",
        "com.massimotter.weave.environment": "test",
        "com.massimotter.weave.namespace": context.env["WEAVE_RESOURCE_PREFIX"],
        "com.massimotter.weave.scope": "isolated",
        "com.massimotter.weave.candidate-commit": binding.candidate_commit,
        "com.massimotter.weave.candidate-manifest-digest": (
            binding.candidate_manifest_digest
        ),
    }
    if kind == "container":
        expected["com.docker.compose.project"] = binding.compose_project
    return expected


def _owned_containers(
    context: ComposeContext,
    binding: OwnershipBinding,
    *,
    deadline: float,
) -> list[tuple[str, str]]:
    expected = _expected_labels(context, binding, "container")
    format_fields = ["{{json .ID}}", "{{json .Names}}"]
    format_fields.extend(
        f'{{{{json (.Label "{key}")}}}}' for key in expected
    )
    result = _run_docker(
        [
            "docker",
            "container",
            "ls",
            "--all",
            "--no-trunc",
            "--filter",
            f"label=com.docker.compose.project={binding.compose_project}",
            "--format",
            f"[{','.join(format_fields)}]",
        ],
        deadline=deadline,
        operation="container-list",
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.DEVNULL,
    )
    if result.returncode != 0:
        raise ContractError("isolated teardown could not enumerate exact project containers")
    verified: list[tuple[str, str]] = []
    for line in sorted(set(result.stdout.splitlines())):
        try:
            parts = json.loads(line)
        except json.JSONDecodeError:
            raise ContractError(
                "isolated teardown received an invalid container inventory record"
            ) from None
        if (
            not isinstance(parts, list)
            or len(parts) != len(expected) + 2
            or not all(isinstance(part, str) for part in parts)
            or re.fullmatch(r"[0-9a-f]{64}", parts[0]) is None
        ):
            raise ContractError(
                "isolated teardown received an invalid container inventory record"
            )
        identifier, name = parts[:2]
        observed = dict(zip(expected, parts[2:]))
        if not name or any(
            observed.get(key) != value for key, value in expected.items()
        ):
            raise ContractError(
                f"refusing to remove unowned Docker container {identifier}"
            )
        verified.append((identifier, name))
    return verified


def _remove_remaining_owned_containers(
    context: ComposeContext,
    binding: OwnershipBinding,
    *,
    deadline: float,
) -> tuple[int, int]:
    observed: set[str] = set()
    removed: set[str] = set()
    for attempt in range(CONTAINER_REMOVAL_ATTEMPTS):
        remaining = _owned_containers(context, binding, deadline=deadline)
        if not remaining:
            return len(observed), len(removed)
        for identifier, _name in remaining:
            observed.add(identifier)
            result = _run_docker(
                ["docker", "container", "rm", "--force", identifier],
                deadline=deadline,
                operation="container-remove",
                tolerate_timeout=True,
                stdout=subprocess.DEVNULL,
                stderr=subprocess.DEVNULL,
            )
            if result.returncode == 0:
                removed.add(identifier)
        if attempt + 1 < CONTAINER_REMOVAL_ATTEMPTS:
            time.sleep(
                min(
                    float(CONTAINER_REMOVAL_WAIT_SECONDS),
                    _remaining_timeout(deadline, CONTAINER_REMOVAL_WAIT_SECONDS),
                )
            )
    if _owned_containers(context, binding, deadline=deadline):
        raise ContractError("isolated teardown left exact owned containers")
    return len(observed), len(removed)


def _remaining_owned_resource_counts(
    context: ComposeContext,
    binding: OwnershipBinding,
    volumes: list[str],
    network: str,
    *,
    deadline: float,
) -> tuple[int, int, int]:
    containers = len(_owned_containers(context, binding, deadline=deadline))
    remaining_volumes = sum(
        _labels("volume", name, deadline=deadline) is not None for name in volumes
    )
    remaining_networks = int(_labels("network", network, deadline=deadline) is not None)
    return containers, remaining_volumes, remaining_networks


def teardown(context: ComposeContext, *, dry_run: bool) -> dict[str, object]:
    if context.isolated_namespace is None or context.env.get("WEAVE_STACK_SCOPE") != "isolated":
        raise ContractError("destructive teardown is restricted to a run-scoped isolated test project")
    candidate = os.environ.get("WEAVE_CANDIDATE_COMMIT", "")
    if re.fullmatch(r"[0-9a-f]{40}", candidate) is None:
        raise ContractError("isolated teardown requires exact WEAVE_CANDIDATE_COMMIT evidence")
    candidate_manifest_digest = os.environ.get("WEAVE_CANDIDATE_MANIFEST_DIGEST", "")
    if re.fullmatch(r"sha256:[0-9a-f]{64}", candidate_manifest_digest) is None:
        raise ContractError(
            "isolated teardown requires exact WEAVE_CANDIDATE_MANIFEST_DIGEST evidence"
        )
    deadline = time.monotonic() + TEARDOWN_BUDGET_SECONDS
    binding = OwnershipBinding(
        candidate,
        candidate_manifest_digest,
        context.env["WEAVE_COMPOSE_PROJECT"],
    )

    volumes = [context.env[key] for key in VOLUME_KEYS]
    network = context.env["WEAVE_DOCKER_NETWORK"]
    existing_volumes = [
        name
        for name in volumes
        if _assert_owned(
            context, binding, "volume", name, deadline=deadline
        )
    ]
    network_exists = _assert_owned(
        context, binding, "network", network, deadline=deadline
    )
    initial_containers = _owned_containers(context, binding, deadline=deadline)
    compose_down_status = "not-run"
    fallback_attempted = False
    fallback_observed_container_count = 0
    removed_container_count = 0
    if not dry_run:
        try:
            result = subprocess.run(
                [*context.compose_base_command, "down", "--remove-orphans"],
                cwd=context.root,
                env=compose_environment(context),
                timeout=_remaining_timeout(deadline, COMPOSE_DOWN_TIMEOUT_SECONDS),
            )
            compose_down_status = "passed" if result.returncode == 0 else "failed"
        except subprocess.TimeoutExpired:
            compose_down_status = "timed-out"
        remaining_after_compose = _owned_containers(
            context, binding, deadline=deadline
        )
        if compose_down_status != "passed" or remaining_after_compose:
            fallback_attempted = True
            (
                fallback_observed_container_count,
                removed_container_count,
            ) = _remove_remaining_owned_containers(
                context, binding, deadline=deadline
            )
        for name in existing_volumes:
            result = _run_docker(
                ["docker", "volume", "rm", name],
                deadline=deadline,
                operation="volume-remove",
                stdout=subprocess.DEVNULL,
            )
            if result.returncode != 0:
                raise ContractError("isolated teardown could not remove an owned volume")
        if network_exists:
            result = _run_docker(
                ["docker", "network", "rm", network],
                deadline=deadline,
                operation="network-remove",
                stdout=subprocess.DEVNULL,
            )
            if result.returncode != 0:
                raise ContractError("isolated teardown could not remove the owned network")
        remaining_containers, remaining_volumes, remaining_networks = (
            _remaining_owned_resource_counts(
                context,
                binding,
                volumes,
                network,
                deadline=deadline,
            )
        )
        if remaining_containers + remaining_volumes + remaining_networks != 0:
            raise ContractError("isolated teardown left exact owned resources")
    else:
        remaining_containers = len(initial_containers)
        remaining_volumes = len(existing_volumes)
        remaining_networks = int(network_exists)

    return {
        "schemaVersion": "weave.compose-isolated-teardown.v1",
        "profile": getattr(context, "environment", context.profile),
        "namespace": context.isolated_namespace,
        "composeProject": context.env["WEAVE_COMPOSE_PROJECT"],
        "candidateCommit": candidate,
        "candidateManifestDigest": candidate_manifest_digest,
        "completedAt": datetime.now(timezone.utc).isoformat().replace("+00:00", "Z"),
        "dryRun": dry_run,
        "removedVolumeNames": [] if dry_run else sorted(existing_volumes),
        "removedNetworkName": network if network_exists and not dry_run else "",
        "networkRemoved": bool(network_exists and not dry_run),
        "composeDownStatus": compose_down_status,
        "fallbackAttempted": fallback_attempted,
        "observedContainerCount": len(initial_containers),
        "fallbackObservedContainerCount": fallback_observed_container_count,
        "removedContainerCount": removed_container_count,
        "remainingContainerCount": remaining_containers,
        "remainingVolumeCount": remaining_volumes,
        "remainingNetworkCount": remaining_networks,
        "remainingOwnedResources": (
            remaining_containers + remaining_volumes + remaining_networks
        ),
        "ownershipLabelsVerified": True,
        "containsSecretValues": False,
        "supportSafe": True,
    }


def _evidence_output_path(context: ComposeContext, explicit: Path | None) -> Path:
    if explicit is not None:
        return explicit.resolve()
    configured = os.environ.get("WEAVE_TEARDOWN_EVIDENCE_FILE", "")
    if configured:
        return Path(configured).resolve()
    return (context.generated_root / "teardown/evidence.json").resolve()


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("profile", choices=("test",))
    parser.add_argument("--root", type=Path, required=True)
    parser.add_argument("--env-file")
    parser.add_argument("--isolated", action="store_true")
    parser.add_argument("--dry-run", action="store_true")
    parser.add_argument("--evidence-file", type=Path)
    args = parser.parse_args()
    try:
        if not args.isolated:
            raise ContractError("teardown requires the explicit --isolated intent")
        context = load_context(args.profile, args.root, args.env_file)
        value = teardown(context, dry_run=args.dry_run)
        output = _evidence_output_path(context, args.evidence_file)
        output.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
        temporary = output.with_suffix(output.suffix + ".tmp")
        temporary.write_text(json.dumps(value, indent=2, sort_keys=True) + "\n", encoding="utf-8")
        os.chmod(temporary, 0o600)
        os.replace(temporary, output)
    except (ContractError, OSError, ValueError, KeyError, json.JSONDecodeError, subprocess.CalledProcessError) as error:
        print(f"WEAVE_TEARDOWN_ERROR {error}", file=os.sys.stderr)
        return 1
    print(f"teardown: isolated namespace handled; evidence={output}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
