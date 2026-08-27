#!/usr/bin/env python3
from __future__ import annotations

import json
import os
import re
import subprocess
import sys
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[1]
STACK_ROOT = ROOT / "e2e" / "private-runner"
COMPOSE_FILE = STACK_ROOT / "compose.yaml"
PREPARE_SCRIPT = STACK_ROOT / "prepare-state.sh"

BASE_SERVICES = {
    "weave-postgres",
    "weave-server",
    "weave-mcp",
    "private-runner",
    "internal-api",
}
PROVIDER_SERVICES = {
    "keycloak-postgres",
    "keycloak",
    "nextcloud-postgres",
    "nextcloud-redis",
    "nextcloud",
    "nextcloud-cron",
    "tuwunel",
}
CUTOVER_SERVICES = {"native-files"}

SENSITIVE_KEY = re.compile(
    r"(?:password|secret|token|authorization|credential|private[_-]?key)",
    re.IGNORECASE,
)


class ContractError(RuntimeError):
    pass


def run(*command: str, cwd: Path = ROOT) -> str:
    result = subprocess.run(
        command,
        cwd=cwd,
        check=False,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        env={**os.environ, "COMPOSE_STATUS_STDOUT": "1"},
    )
    if result.returncode != 0:
        raise ContractError(
            f"command failed ({result.returncode}): {' '.join(command)}\n{result.stderr.strip()}"
        )
    return result.stdout


def compose(profile: str) -> dict[str, Any]:
    raw = run(
        "docker",
        "compose",
        "-f",
        str(COMPOSE_FILE),
        "--profile",
        profile,
        "config",
        "--format",
        "json",
        cwd=STACK_ROOT,
    )
    try:
        return json.loads(raw)
    except json.JSONDecodeError as error:
        raise ContractError(f"Docker Compose returned invalid JSON for profile {profile}") from error


def service_networks(service: dict[str, Any]) -> set[str]:
    networks = service.get("networks", {})
    if isinstance(networks, list):
        return set(networks)
    if isinstance(networks, dict):
        return set(networks)
    raise ContractError("service networks must be a list or object")


def assert_exact_services(config: dict[str, Any], expected: set[str], profile: str) -> None:
    actual = set(config.get("services", {}))
    if actual != expected:
        missing = sorted(expected - actual)
        unexpected = sorted(actual - expected)
        raise ContractError(
            f"profile {profile} service graph differs; missing={missing}, unexpected={unexpected}"
        )


def assert_private_network(config: dict[str, Any]) -> None:
    networks = config.get("networks", {})
    private = networks.get("company-private") or networks.get("company_private")
    if not isinstance(private, dict) or private.get("internal") is not True:
        raise ContractError("company-private must be an internal Docker network")

    services = config["services"]
    runner = services["private-runner"]
    internal_api = services["internal-api"]
    if service_networks(runner) != {"runner-egress", "company-private"}:
        raise ContractError("private-runner must join only runner-egress and company-private")
    if service_networks(internal_api) != {"company-private"}:
        raise ContractError("internal-api must be reachable only from company-private")
    for name in ("private-runner", "internal-api"):
        service = services[name]
        if service.get("ports") or service.get("expose"):
            raise ContractError(f"{name} must publish no inbound port")
    if "company-private" in service_networks(services["weave-server"]):
        raise ContractError("the Engine must not join the company-private network")


def assert_provider_isolation(config: dict[str, Any]) -> None:
    for name in PROVIDER_SERVICES | CUTOVER_SERVICES:
        service = config["services"].get(name)
        if service is None:
            continue
        if "company-private" in service_networks(service):
            raise ContractError(f"provider service {name} must not join company-private")
        if service.get("ports"):
            raise ContractError(f"provider service {name} must not publish a host port in E2E")


def assert_no_plaintext_secrets(config: dict[str, Any]) -> None:
    for service_name, service in config.get("services", {}).items():
        environment = service.get("environment") or {}
        if isinstance(environment, list):
            environment = dict(item.split("=", 1) for item in environment if "=" in item)
        for key, value in environment.items():
            if not SENSITIVE_KEY.search(key):
                continue
            text = "" if value is None else str(value)
            if key.endswith("_FILE") or text.startswith("/run/secrets/"):
                continue
            raise ContractError(
                f"service {service_name} exposes secret-shaped environment key {key} directly"
            )


def assert_pinned_images(config: dict[str, Any]) -> None:
    for service_name, service in config.get("services", {}).items():
        image = service.get("image")
        if not image:
            raise ContractError(f"service {service_name} has no explicit image coordinate")
        image_without_digest = image.split("@", 1)[0]
        final_segment = image_without_digest.rsplit("/", 1)[-1]
        if ":" not in final_segment or final_segment.endswith(":latest"):
            raise ContractError(f"service {service_name} uses an unpinned image: {image}")


def assert_hardening(config: dict[str, Any]) -> None:
    for name in ("weave-server", "weave-mcp", "private-runner", "internal-api"):
        service = config["services"][name]
        if service.get("read_only") is not True:
            raise ContractError(f"{name} must use a read-only root filesystem")
        cap_drop = set(service.get("cap_drop") or [])
        if "ALL" not in cap_drop:
            raise ContractError(f"{name} must drop all Linux capabilities")
        options = set(service.get("security_opt") or [])
        if "no-new-privileges:true" not in options:
            raise ContractError(f"{name} must set no-new-privileges")


def main() -> int:
    if not COMPOSE_FILE.is_file():
        raise ContractError(f"missing Compose file: {COMPOSE_FILE}")
    run("bash", str(PREPARE_SCRIPT), cwd=STACK_ROOT)

    contract = compose("runner-contract")
    reference = compose("provider-reference")
    cutover = compose("provider-cutover")

    assert_exact_services(contract, BASE_SERVICES, "runner-contract")
    assert_exact_services(reference, BASE_SERVICES | PROVIDER_SERVICES, "provider-reference")
    assert_exact_services(
        cutover,
        BASE_SERVICES | PROVIDER_SERVICES | CUTOVER_SERVICES,
        "provider-cutover",
    )

    for config in (contract, reference, cutover):
        assert_private_network(config)
        assert_provider_isolation(config)
        assert_no_plaintext_secrets(config)
        assert_pinned_images(config)
        assert_hardening(config)

    print(
        "Private Runner stack contract: OK "
        f"(contract={len(BASE_SERVICES)}, "
        f"reference={len(BASE_SERVICES | PROVIDER_SERVICES)}, "
        f"cutover={len(BASE_SERVICES | PROVIDER_SERVICES | CUTOVER_SERVICES)})"
    )
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except ContractError as error:
        print(f"Private Runner stack contract failed: {error}", file=sys.stderr)
        raise SystemExit(1)
