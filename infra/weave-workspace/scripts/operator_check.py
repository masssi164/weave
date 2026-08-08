#!/usr/bin/env python3
"""Support-safe readiness checks for one exact Compose deployment."""

from __future__ import annotations

import argparse
import json
import os
import re
import stat
import subprocess
from datetime import datetime, timezone
from pathlib import Path
from urllib.parse import urlsplit

from compose_env import ComposeContext, ContractError, compose_environment, load_context


CORE_SERVICES = ("postgres", "keycloak", "mas", "synapse", "nextcloud", "caddy")
APPLICATION_SERVICES = ("backend", "mcp")
ACTIVATION_SERVICES = ("mailpit",)


def _curl(
    context: ComposeContext,
    url: str,
    *,
    method: str = "GET",
    bearer: str | None = None,
) -> tuple[int, bytes]:
    parsed = urlsplit(url)
    if parsed.scheme != "https" or not parsed.hostname:
        raise ContractError("readiness URL is not a declared HTTPS authority")
    port = parsed.port or 443
    command = [
        "curl", "--silent", "--show-error", "--cacert", str(context.tls_root / "ca.pem"),
        "--resolve", f"{parsed.hostname}:{port}:127.0.0.1", "--request", method,
        "--output", "-", "--write-out", "\n%{http_code}", url,
    ]
    config = b""
    if bearer is not None:
        if re.fullmatch(r"[A-Za-z0-9._~-]{32,8192}", bearer) is None:
            raise ContractError("member access token has an invalid support-safe transport shape")
        command[1:1] = ["--config", "-"]
        config = f'header = "Authorization: Bearer {bearer}"\n'.encode("ascii")
    result = subprocess.run(command, input=config, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    if result.returncode != 0:
        raise ContractError("declared public readiness endpoint is unreachable")
    body, separator, status = result.stdout.rpartition(b"\n")
    if not separator or not status.isdigit():
        raise ContractError("readiness endpoint returned an invalid HTTP envelope")
    return int(status), body


def _container(context: ComposeContext, service: str) -> dict[str, object]:
    result = subprocess.run(
        [*context.compose_base_command, "ps", "--format", "json", service],
        cwd=context.root,
        env=compose_environment(context),
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
    )
    if result.returncode != 0 or not result.stdout.strip():
        raise ContractError(f"Compose service {service} is unavailable")
    try:
        value = json.loads(result.stdout)
    except json.JSONDecodeError:
        rows = [json.loads(line) for line in result.stdout.splitlines() if line.strip()]
        value = rows
    rows = value if isinstance(value, list) else [value]
    if len(rows) != 1 or not isinstance(rows[0], dict):
        raise ContractError(f"Compose service {service} has ambiguous runtime identity")
    row = rows[0]
    if row.get("State") != "running":
        raise ContractError(f"Compose service {service} is not running")
    health = row.get("Health")
    if health not in (None, "", "healthy"):
        raise ContractError(f"Compose service {service} is not healthy")
    return {
        "service": service,
        "state": "running",
        "health": health or "not-declared",
        "image": row.get("Image", "unknown"),
    }


def _member_token(path: Path) -> str:
    if path.is_symlink() or not path.is_file():
        raise ContractError("member access token input must be a regular non-symlink file")
    if stat.S_IMODE(path.stat().st_mode) != 0o600:
        raise ContractError("member access token input must be mode 0600")
    if path.stat().st_size > 8192:
        raise ContractError("member access token input exceeds the bounded token size")
    value = path.read_text(encoding="ascii").strip()
    if not value or "\n" in value or "\r" in value:
        raise ContractError("member access token input is empty or multi-line")
    return value


def check(
    context: ComposeContext,
    *,
    require_application: bool,
    member_access_token_file: Path | None = None,
) -> dict[str, object]:
    services = list(CORE_SERVICES)
    if context.environment == "e2e" or "dev-tools" in context.active_profiles:
        services.extend(ACTIVATION_SERVICES)
    if require_application:
        services.extend(APPLICATION_SERVICES)
    runtime = [_container(context, service) for service in services]

    endpoints: dict[str, dict[str, object]] = {}
    issuer = context.env["WEAVE_AUTH_URL"].rstrip("/") + "/realms/weave"
    status, body = _curl(context, issuer + "/.well-known/openid-configuration")
    if status != 200:
        raise ContractError("OIDC discovery is not ready")
    discovery = json.loads(body)
    if discovery.get("issuer") != issuer:
        raise ContractError("OIDC discovery issuer differs from the declared public authority")
    endpoints["oidcDiscovery"] = {"status": status, "issuerExact": True}

    probes = {
        "matrixVersions": context.env["WEAVE_MATRIX_URL"].rstrip("/") + "/_matrix/client/versions",
        "nextcloudStatus": context.env["WEAVE_FILES_URL"].rstrip("/") + "/status.php",
    }
    if require_application:
        probes["backendReadiness"] = context.env["WEAVE_API_URL"].rstrip("/") + "/health/ready"
    for name, url in probes.items():
        status, _body = _curl(context, url)
        if status != 200:
            raise ContractError(f"{name} returned HTTP {status}")
        endpoints[name] = {"status": status}

    if member_access_token_file is not None:
        token = _member_token(member_access_token_file)
        status, _body = _curl(
            context,
            context.env["WEAVE_API_URL"].rstrip("/") + "/admin/control-plane",
            bearer=token,
        )
        token = ""
        if status != 403:
            raise ContractError(f"member token did not receive HTTP 403 from admin control plane (HTTP {status})")
        endpoints["adminControlPlaneMemberDenial"] = {"status": 403, "tokenClass": "member"}

    provider_evidence = context.generated_root / "nextcloud/readiness.json"
    if provider_evidence.is_symlink() or not provider_evidence.is_file():
        raise ContractError("Nextcloud authenticated DAV readiness evidence is missing")
    provider = json.loads(provider_evidence.read_text(encoding="utf-8"))
    if provider.get("ready") is not True or provider.get("supportSafe") is not True:
        raise ContractError("Nextcloud authenticated DAV provider evidence is unsuccessful")

    return {
        "schemaVersion": "weave.compose-operator-readiness.v1",
        "profile": context.environment,
        "composeProject": context.env["WEAVE_COMPOSE_PROJECT"],
        "checkedAt": datetime.now(timezone.utc).isoformat().replace("+00:00", "Z"),
        "services": runtime,
        "endpoints": endpoints,
        "providerEvidenceRef": "evidence:nextcloud-provider-readiness:" + context.env["WEAVE_COMPOSE_PROJECT"],
        "containsSecretValues": False,
        "supportSafe": True,
        "ready": True,
    }


def _write(path: Path, value: dict[str, object]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
    temporary = path.with_suffix(path.suffix + ".tmp")
    temporary.write_text(json.dumps(value, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    os.chmod(temporary, 0o600)
    os.replace(temporary, path)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("profile", choices=("dev", "dogfood", "prod", "e2e"))
    parser.add_argument("--root", type=Path, required=True)
    parser.add_argument("--env-file")
    parser.add_argument("--require-application", action="store_true")
    parser.add_argument("--member-access-token-file", type=Path)
    parser.add_argument("--evidence-file", type=Path)
    args = parser.parse_args()
    try:
        context = load_context(args.profile, args.root, args.env_file)
        value = check(
            context,
            require_application=args.require_application or args.profile != "dev",
            member_access_token_file=(
                args.member_access_token_file.expanduser().resolve()
                if args.member_access_token_file is not None else None
            ),
        )
        output = args.evidence_file or context.generated_root / "operator/readiness.json"
        _write(output.resolve(), value)
    except (ContractError, OSError, ValueError, KeyError, json.JSONDecodeError) as error:
        print(f"WEAVE_OPERATOR_CHECK_ERROR {error}", file=os.sys.stderr)
        return 1
    print(f"operator-check: ready; evidence={output}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
