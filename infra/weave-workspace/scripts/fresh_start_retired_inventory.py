#!/usr/bin/env python3
"""Closed exact-name inventory for the one-time retired dogfood generation."""

from __future__ import annotations

import hashlib
import json
import re
from dataclasses import dataclass
from pathlib import Path
from typing import Any

from compose_env import ContractError


RESOURCE_KINDS = frozenset(("container", "network", "volume"))
COMPONENT_SERVICES = {
    "server": "backend",
    "postgres": "db",
    "identity": "keycloak",
    "mail": "mailpit",
    "matrix-auth": "mas",
    "mcp": "mcp",
    "files-calendar": "nextcloud",
    "gateway": "caddy",
    "chat": "synapse",
}
BACKUP_ARTIFACTS = {
    ("gateway", "configuration-sensitive"): (
        "caddy-config.tgz",
        "gateway-config-state",
    ),
    ("gateway", "tls-sensitive"): (
        "caddy-data.tgz",
        "gateway-runtime-state",
    ),
    ("identity", "identity-sensitive"): (
        "keycloak-data.tgz",
        "keycloak-runtime-state",
    ),
    ("chat-appservice", "credential-sensitive"): (
        "matrix-appservice.tgz",
        "matrix-appservice-runtime",
    ),
    ("files-calendar", "collaboration-sensitive"): (
        "nextcloud-data.tgz",
        "files-calendar-provider-data",
    ),
    ("chat", "collaboration-sensitive"): (
        "synapse-data.tgz",
        "matrix-media-and-local-state",
    ),
}


@dataclass(frozen=True)
class RetiredVolumeArtifact:
    name: str
    archive: str
    kind: str


@dataclass(frozen=True)
class RetiredInventory:
    path: Path
    digest: str
    generation: str
    namespace: str
    containers: dict[str, str]
    network: str
    volumes: tuple[str, ...]
    backup_volumes: tuple[RetiredVolumeArtifact, ...]

    @property
    def database_container(self) -> str:
        return self.containers["db"]


def _entry(value: Any) -> dict[str, str]:
    required = {"kind", "name", "component", "dataClass"}
    if not isinstance(value, dict) or set(value) != required:
        raise ContractError("retired Fresh Start target has an unsupported shape")
    if value.get("kind") not in RESOURCE_KINDS:
        raise ContractError("retired Fresh Start target kind is unsupported")
    for field in required:
        item = value.get(field)
        if (
            not isinstance(item, str)
            or not item
            or any(ord(character) < 0x20 for character in item)
        ):
            raise ContractError("retired Fresh Start target is not support-safe")
    return value


def load_retired_inventory(path: Path) -> RetiredInventory:
    resolved = path.resolve()
    if not resolved.is_file() or resolved.is_symlink():
        raise ContractError("retired Fresh Start inventory is missing or unsafe")
    raw = resolved.read_bytes()
    try:
        payload = json.loads(raw)
    except json.JSONDecodeError as error:
        raise ContractError("retired Fresh Start inventory is not valid JSON") from error
    if (
        not isinstance(payload, dict)
        or set(payload) != {
            "schemaVersion",
            "environment",
            "stack",
            "retiredGeneration",
            "retiredNamespace",
            "targets",
            "exclusions",
        }
        or payload.get("schemaVersion") != "weave.infra.fresh-start-targets.v1"
        or payload.get("environment") != "persistent-dogfood"
        or payload.get("stack") != "weave"
        or payload.get("exclusions") != []
    ):
        raise ContractError("retired Fresh Start inventory contract is invalid")
    generation = payload.get("retiredGeneration")
    namespace = payload.get("retiredNamespace")
    if not isinstance(generation, str) or not re.fullmatch(
        r"[a-z0-9][a-z0-9._-]{2,63}", generation
    ):
        raise ContractError("retired Fresh Start generation is invalid")
    if not isinstance(namespace, str) or not re.fullmatch(
        r"[a-z0-9][a-z0-9-]{1,63}", namespace
    ):
        raise ContractError("retired Fresh Start namespace is invalid")
    targets_value = payload.get("targets")
    if not isinstance(targets_value, list) or not targets_value:
        raise ContractError("retired Fresh Start inventory is empty")
    targets = [_entry(item) for item in targets_value]
    identities = [(item["kind"], item["name"]) for item in targets]
    if len(identities) != len(set(identities)):
        raise ContractError("retired Fresh Start inventory is ambiguous")

    containers: dict[str, str] = {}
    networks: list[str] = []
    volumes: list[str] = []
    backup_volumes: list[RetiredVolumeArtifact] = []
    for item in targets:
        if item["kind"] == "container":
            service = COMPONENT_SERVICES.get(item["component"])
            if service is None or service in containers:
                raise ContractError(
                    "retired Fresh Start container components are incomplete or ambiguous"
                )
            containers[service] = item["name"]
        elif item["kind"] == "network":
            networks.append(item["name"])
        else:
            volumes.append(item["name"])
            artifact = BACKUP_ARTIFACTS.get(
                (item["component"], item["dataClass"])
            )
            if artifact is not None:
                backup_volumes.append(
                    RetiredVolumeArtifact(item["name"], artifact[0], artifact[1])
                )
    if set(containers) != set(COMPONENT_SERVICES.values()):
        raise ContractError("retired Fresh Start container inventory is incomplete")
    if len(networks) != 1:
        raise ContractError("retired Fresh Start requires exactly one network")
    if len(backup_volumes) != len(BACKUP_ARTIFACTS):
        raise ContractError("retired Fresh Start backup volume inventory is incomplete")
    return RetiredInventory(
        path=resolved,
        digest="sha256:" + hashlib.sha256(raw).hexdigest(),
        generation=generation,
        namespace=namespace,
        containers=containers,
        network=networks[0],
        volumes=tuple(sorted(volumes)),
        backup_volumes=tuple(sorted(backup_volumes, key=lambda item: item.name)),
    )
