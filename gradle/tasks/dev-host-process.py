#!/usr/bin/env python3
"""Run one Weave application on the host from its Desired-State container config.

The Infrastructure stack remains the single owner of runtime environment values. This
adapter reads the stopped application container, translates only Docker-local endpoints
and bind-mounted paths to their host equivalents, and then replaces itself with Gradle.
No generated environment file or second OAuth/provider configuration is maintained here.
"""

from __future__ import annotations

import argparse
import json
import os
import subprocess
import sys
from pathlib import Path
from typing import Any

LABEL_PREFIX = "com.massimotter.weave."


def fail(message: str) -> "NoReturn":
    raise SystemExit(f"WEAVE_DEV_HOST_ERROR {message}")


def docker_inspect(name: str) -> dict[str, Any]:
    result = subprocess.run(
        ["docker", "container", "inspect", name],
        check=False,
        capture_output=True,
        text=True,
    )
    if result.returncode != 0:
        fail(f"required Desired-State container {name} is missing; run ./gradlew devUp")
    payload = json.loads(result.stdout)
    if not isinstance(payload, list) or len(payload) != 1:
        fail(f"unexpected Docker inspect result for {name}")
    return payload[0]


def assert_owned(inspected: dict[str, Any], component: str) -> None:
    labels = inspected.get("Config", {}).get("Labels") or {}
    expected = {
        f"{LABEL_PREFIX}managed": "true",
        f"{LABEL_PREFIX}environment": "dev",
        f"{LABEL_PREFIX}stack": "weave",
        f"{LABEL_PREFIX}component": component,
    }
    mismatches = {
        key: {"expected": value, "actual": labels.get(key)}
        for key, value in expected.items()
        if labels.get(key) != value
    }
    if mismatches:
        fail(f"container ownership metadata does not match host-dev: {mismatches}")
    if inspected.get("State", {}).get("Running"):
        fail("Desired-State application container is still running; rerun ./gradlew devUp")


def parse_environment(inspected: dict[str, Any]) -> dict[str, str]:
    parsed: dict[str, str] = {}
    for declaration in inspected.get("Config", {}).get("Env") or []:
        key, separator, value = declaration.partition("=")
        if separator and key:
            parsed[key] = value
    return parsed


def owned_containers() -> list[dict[str, Any]]:
    result = subprocess.run(
        [
            "docker",
            "container",
            "ls",
            "--all",
            "--quiet",
            "--filter",
            f"label={LABEL_PREFIX}managed=true",
            "--filter",
            f"label={LABEL_PREFIX}environment=dev",
            "--filter",
            f"label={LABEL_PREFIX}stack=weave",
        ],
        check=True,
        capture_output=True,
        text=True,
    )
    identifiers = [line for line in result.stdout.splitlines() if line]
    if not identifiers:
        return []
    inspected = subprocess.run(
        ["docker", "container", "inspect", *identifiers],
        check=True,
        capture_output=True,
        text=True,
    )
    payload = json.loads(inspected.stdout)
    return payload if isinstance(payload, list) else []


def endpoint_replacements(
    containers: list[dict[str, Any]],
) -> list[tuple[str, str]]:
    replacements: set[tuple[str, str]] = set()
    for container in containers:
        names = {str(container.get("Name") or "").lstrip("/")}
        for network in (container.get("NetworkSettings", {}).get("Networks") or {}).values():
            names.update(str(alias) for alias in (network.get("Aliases") or []) if alias)
        ports = container.get("NetworkSettings", {}).get("Ports") or {}
        for declaration, bindings in ports.items():
            container_port, separator, protocol = declaration.partition("/")
            if separator == "" or protocol != "tcp" or not bindings:
                continue
            loopback_binding = next(
                (
                    binding
                    for binding in bindings
                    if binding.get("HostIp") in ("127.0.0.1", "::1", "0.0.0.0", "::")
                ),
                None,
            )
            if loopback_binding is None:
                continue
            host_port = loopback_binding.get("HostPort")
            if not host_port:
                continue
            for name in names:
                if name:
                    replacements.add(
                        (f"{name}:{container_port}", f"127.0.0.1:{host_port}")
                    )
    return sorted(replacements, key=lambda item: len(item[0]), reverse=True)


def mount_replacements(inspected: dict[str, Any]) -> list[tuple[str, str]]:
    replacements: list[tuple[str, str]] = []
    for mount in inspected.get("Mounts") or []:
        if mount.get("Type") != "bind":
            continue
        source = mount.get("Source")
        destination = mount.get("Destination")
        if source and destination:
            replacements.append((str(destination), str(source)))
    return sorted(replacements, key=lambda item: len(item[0]), reverse=True)


def translate(value: str, replacements: list[tuple[str, str]]) -> str:
    translated = value
    for source, target in replacements:
        translated = translated.replace(source, target)
    return translated


def application_environment(
    inspected: dict[str, Any],
    component: str,
    host_port: str,
) -> dict[str, str]:
    container_environment = parse_environment(inspected)
    replacements = endpoint_replacements(owned_containers())
    replacements.extend(mount_replacements(inspected))
    translated = {
        key: translate(value, replacements)
        for key, value in container_environment.items()
    }

    if component == "server":
        for key in (
            "WEAVE_PERSISTENCE_URL",
            "WEAVE_PERSISTENCE_USERNAME",
            "WEAVE_PERSISTENCE_PASSWORD",
            "WEAVE_PERSISTENCE_DRIVER",
            "WEAVE_JPA_DDL_AUTO",
            "WEAVE_FLYWAY_ENABLED",
        ):
            translated.pop(key, None)
        translated["SPRING_PROFILES_ACTIVE"] = "dev-h2"
        translated["PORT"] = host_port
        for key in (
            "WEAVE_CHAT_MATRIX_APPSERVICE_AS_TOKEN_FILE",
            "WEAVE_CHAT_MATRIX_APPSERVICE_HS_TOKEN_FILE",
        ):
            override = os.environ.get(key)
            if override:
                translated[key] = override
    else:
        translated["WEAVE_MCP_PORT"] = host_port

    environment = os.environ.copy()
    environment.update(translated)
    return environment


def parser() -> argparse.ArgumentParser:
    result = argparse.ArgumentParser()
    result.add_argument("--component", choices=("server", "mcp"), required=True)
    result.add_argument("--container", required=True)
    result.add_argument("--host-port", required=True)
    result.add_argument("--repository", type=Path, required=True)
    return result


def main() -> None:
    args = parser().parse_args()
    inspected = docker_inspect(args.container)
    assert_owned(inspected, args.component)
    repository = args.repository.resolve()
    if not (repository / "gradlew").is_file():
        fail(f"repository does not contain gradlew: {repository}")
    task = ":server:bootRun" if args.component == "server" else ":weave-mcp-server:bootRun"
    environment = application_environment(inspected, args.component, args.host_port)
    os.chdir(repository)
    os.execvpe(
        str(repository / "gradlew"),
        [str(repository / "gradlew"), "--no-daemon", task],
        environment,
    )


if __name__ == "__main__":
    main()
