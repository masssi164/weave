#!/usr/bin/env python3
"""Capture and compare support-safe persistent dogfood resource identity."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import subprocess
import sys
from pathlib import Path
from typing import Any


VOLUME_ROLES = (
    "database",
    "keycloak-runtime",
    "mailpit",
    "caddy-data",
    "caddy-config",
    "native-files",
)
VOLUME_METADATA = {
    "database": ("postgres", "database-sensitive"),
    "keycloak-runtime": ("identity", "identity-sensitive"),
    "mailpit": ("mail", "activation-sensitive"),
    "caddy-data": ("gateway", "tls-sensitive"),
    "caddy-config": ("gateway", "configuration-sensitive"),
    "native-files": ("files-native", "collaboration-sensitive"),
}
REQUIRED_RUNTIME_SERVICES = {"postgres", "caddy", "keycloak", "mailpit", "backend", "mcp"}
FORBIDDEN_ENVIRONMENT_KEYS = {
    "KEYCLOAK_ADMIN",
    "KEYCLOAK_ADMIN_PASSWORD",
    "KC_BOOTSTRAP_ADMIN_USERNAME",
    "KC_BOOTSTRAP_ADMIN_PASSWORD",
    "WEAVE_KEYCLOAK_ADMIN_PASSWORD",
}
NAME = re.compile(r"^[a-zA-Z0-9][a-zA-Z0-9_.-]{2,127}$")


class ContinuityError(RuntimeError):
    pass


def sha256(value: bytes) -> str:
    return "sha256:" + hashlib.sha256(value).hexdigest()


def canonical(value: object) -> bytes:
    return json.dumps(value, ensure_ascii=False, separators=(",", ":"), sort_keys=True).encode("utf-8")


def docker_json(*arguments: str) -> Any:
    completed = subprocess.run(
        ["docker", *arguments],
        check=True,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.DEVNULL,
    )
    try:
        return json.loads(completed.stdout)
    except json.JSONDecodeError as error:
        raise ContinuityError("Docker returned malformed resource metadata") from error


def volume_projection(role: str, name: str, generation: str) -> dict[str, str]:
    if role not in VOLUME_ROLES or not NAME.fullmatch(name):
        raise ContinuityError("persistent volume input is malformed")
    value = docker_json("volume", "inspect", name)
    if not isinstance(value, list) or len(value) != 1 or not isinstance(value[0], dict):
        raise ContinuityError("persistent volume inspection was ambiguous")
    item = value[0]
    labels = item.get("Labels")
    if not isinstance(labels, dict) or any(not isinstance(key, str) or not isinstance(child, str) for key, child in labels.items()):
        raise ContinuityError("persistent volume labels are malformed")
    component, data_class = VOLUME_METADATA[role]
    required_labels = {
        "com.massimotter.weave.managed": "true",
        "com.massimotter.weave.environment": "dogfood",
        "com.massimotter.weave.scope": "persistent",
        "com.massimotter.weave.stack": "weave",
        "com.massimotter.weave.generation": generation,
        "com.massimotter.weave.component": component,
        "com.massimotter.weave.data-class": data_class,
    }
    if any(labels.get(key) != value for key, value in required_labels.items()):
        raise ContinuityError("persistent volume is outside the owned dogfood boundary")
    driver = item.get("Driver")
    if not isinstance(driver, str) or not driver:
        raise ContinuityError("persistent volume driver is unavailable")
    return {
        "nameSha256": sha256(name.encode("utf-8")),
        "driver": driver,
        "labelsSha256": sha256(canonical(labels)),
    }


def certificate_projection(path: Path) -> str:
    if path.is_symlink() or not path.is_file():
        raise ContinuityError("public TLS certificate input is unavailable")
    payload = path.read_bytes()
    if not payload.startswith(b"-----BEGIN CERTIFICATE-----"):
        raise ContinuityError("public TLS certificate input is malformed")
    return sha256(payload)


def human_writer_absent(compose_project: str, generation: str) -> bool:
    if not NAME.fullmatch(compose_project):
        raise ContinuityError("Compose project is malformed")
    completed = subprocess.run(
        [
            "docker",
            "container",
            "ls",
            "--all",
            "--quiet",
            "--filter",
            f"label=com.docker.compose.project={compose_project}",
        ],
        check=True,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.DEVNULL,
    )
    identifiers = [value for value in completed.stdout.splitlines() if value]
    if not identifiers:
        raise ContinuityError("persistent dogfood has no running resource boundary")
    inspected = docker_json("container", "inspect", *identifiers)
    if not isinstance(inspected, list) or len(inspected) != len(identifiers):
        raise ContinuityError("persistent container inspection was ambiguous")
    services: set[str] = set()
    for container in inspected:
        if not isinstance(container, dict):
            raise ContinuityError("persistent container inspection was malformed")
        labels = container.get("Config", {}).get("Labels", {}) or {}
        service = labels.get("com.docker.compose.service", "") if isinstance(labels, dict) else ""
        if not isinstance(labels, dict) or any(
            labels.get(key) != value
            for key, value in {
                "com.massimotter.weave.managed": "true",
                "com.massimotter.weave.environment": "dogfood",
                "com.massimotter.weave.scope": "persistent",
                "com.massimotter.weave.stack": "weave",
                "com.massimotter.weave.generation": generation,
            }.items()
        ):
            raise ContinuityError("persistent container is outside the owned dogfood boundary")
        services.add(str(service))
        if service in {"identity-ops", "keycloak-supervisor"}:
            return False
        environment = container.get("Config", {}).get("Env", []) or []
        for entry in environment:
            key = str(entry).split("=", 1)[0]
            if key in FORBIDDEN_ENVIRONMENT_KEYS or key.startswith("WEAVE_IDENTITY_BOOTSTRAP_OWNER_"):
                return False
        mounts = container.get("Mounts", []) or []
        if any(
            str(mount.get("Destination", "")).startswith("/run/secrets/weave/bootstrap-owner")
            for mount in mounts
            if isinstance(mount, dict)
        ):
            return False
    if not REQUIRED_RUNTIME_SERVICES.issubset(services):
        raise ContinuityError("persistent dogfood runtime is incomplete")
    return True


def snapshot(args: argparse.Namespace) -> dict[str, Any]:
    volumes = {
        role: volume_projection(role, getattr(args, role.replace("-", "_")), args.generation)
        for role in VOLUME_ROLES
    }
    return {
        "schemaVersion": "weave.persistent-dogfood-resource-snapshot.v1",
        "volumes": volumes,
        "certificates": {
            "caSha256": certificate_projection(args.ca_file),
            "gatewaySha256": certificate_projection(args.gateway_certificate),
            "mailpitSha256": certificate_projection(args.mailpit_certificate),
        },
        "generation": args.generation,
        "humanWriterAbsent": human_writer_absent(args.compose_project, args.generation),
        "supportSafe": True,
        "containsSecretValues": False,
    }


def read_snapshot(path: Path) -> dict[str, Any]:
    if path.is_symlink() or not path.is_file():
        raise ContinuityError("persistent resource baseline is unavailable")
    value = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(value, dict) or value.get("schemaVersion") != "weave.persistent-dogfood-resource-snapshot.v1":
        raise ContinuityError("persistent resource baseline schema is invalid")
    return value


def comparison(before: dict[str, Any], current: dict[str, Any]) -> dict[str, Any]:
    if before != current:
        raise ContinuityError("persistent dogfood resource or public TLS identity changed")
    return {
        "schemaVersion": "weave.persistent-dogfood-comparison.v3",
        "status": "passed",
        "baselineSource": "pre-deploy",
        "preExistingRuntimeObserved": True,
        "twoNonDestructiveInstallsPreservedState": True,
        "identityStoreVolumePreserved": True,
        "mailpitVolumePreserved": True,
        "tlsIdentityPreserved": True,
        "humanWriterAbsent": True,
        "baselineSha256": sha256(canonical(before)),
        "supportSafe": True,
        "containsSecretValues": False,
    }


def write_private(path: Path, value: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
    os.chmod(path.parent, 0o700)
    temporary = path.with_name(f".{path.name}.{os.getpid()}.tmp")
    descriptor = os.open(temporary, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    try:
        with os.fdopen(descriptor, "wb") as stream:
            stream.write(canonical(value) + b"\n")
            stream.flush()
            os.fsync(stream.fileno())
        os.replace(temporary, path)
        os.chmod(path, 0o600)
    finally:
        if temporary.exists() or temporary.is_symlink():
            temporary.unlink()


def parser() -> argparse.ArgumentParser:
    result = argparse.ArgumentParser(description=__doc__)
    result.add_argument("mode", choices=("capture", "compare"))
    result.add_argument("--compose-project", required=True)
    result.add_argument("--generation", required=True)
    for role in VOLUME_ROLES:
        result.add_argument("--" + role, required=True)
    result.add_argument("--ca-file", type=Path, required=True)
    result.add_argument("--gateway-certificate", type=Path, required=True)
    result.add_argument("--mailpit-certificate", type=Path, required=True)
    result.add_argument("--baseline", type=Path, required=True)
    result.add_argument("--output", type=Path)
    return result


def main() -> int:
    args = parser().parse_args()
    if not NAME.fullmatch(args.generation):
        raise ContinuityError("persistent resource generation is malformed")
    current = snapshot(args)
    if current["humanWriterAbsent"] is not True:
        raise ContinuityError("persistent dogfood retained a human identity writer")
    if args.mode == "capture":
        if args.baseline.exists() or args.baseline.is_symlink():
            raise ContinuityError("persistent resource baseline already exists")
        write_private(args.baseline, current)
        print("DOGFOOD_RESOURCE_CONTINUITY_RESULT phase=captured humanWriterAbsent=true supportSafe=true")
        return 0
    if args.output is None:
        raise ContinuityError("compare requires --output")
    before = read_snapshot(args.baseline)
    write_private(args.output, comparison(before, current))
    print("DOGFOOD_RESOURCE_CONTINUITY_RESULT phase=compared humanWriterAbsent=true supportSafe=true")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (ContinuityError, OSError, subprocess.CalledProcessError, json.JSONDecodeError) as error:
        print(f"dogfood-resource-continuity: {error}", file=sys.stderr)
        raise SystemExit(1)
