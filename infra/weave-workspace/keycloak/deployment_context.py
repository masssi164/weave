#!/usr/bin/env python3
"""Closed, environment-independent deployment input parser for the supervisor."""

from __future__ import annotations

import hashlib
import re
from pathlib import Path
from urllib.parse import urlsplit


KEY = re.compile(r"^[A-Z][A-Z0-9_]*$")
NAME = re.compile(r"^[a-z0-9][a-z0-9_-]{1,62}$")
RUN_ID = re.compile(r"^[a-z0-9][a-z0-9-]{5,39}$")
IMAGE = re.compile(r"^(?:sha256:[0-9a-f]{64}|[^\s@]+@sha256:[0-9a-f]{64})$")
SECRET_MARKERS = ("PASSWORD", "TOKEN", "ASSERTION", "PRIVATE_KEY", "CREDENTIAL")


class DeploymentContextError(RuntimeError):
    pass


def _parse(path: Path) -> dict[str, str]:
    if path.is_symlink() or not path.is_file():
        raise DeploymentContextError(f"deployment input is missing or is a symlink: {path}")
    result: dict[str, str] = {}
    for number, original in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
        line = original.strip()
        if not line or line.startswith("#"):
            continue
        if line.startswith("export ") or "=" not in line:
            raise DeploymentContextError(f"{path}:{number}: only literal KEY=VALUE inputs are allowed")
        name, value = line.split("=", 1)
        if not KEY.fullmatch(name) or name in result or any(character in value for character in "\x00\r\n"):
            raise DeploymentContextError(f"{path}:{number}: malformed or duplicate deployment input")
        if any(marker in name for marker in SECRET_MARKERS) and name != "WEAVE_SECRET_ROOT":
            raise DeploymentContextError(f"{path}:{number}: credential-shaped value is forbidden")
        result[name] = value
    return result


def load_supervisor_environment(
    *,
    root: Path,
    profile: str,
    env_file: Path,
    stack_scope: str,
    e2e_run_id: str | None,
    keycloak_image: str,
    sanitizer_image: str,
    runtime_uid: int,
    runtime_gid: int,
) -> dict[str, str]:
    if profile not in {"dev", "dogfood", "main"}:
        raise DeploymentContextError("unsupported Compose profile")
    root = root.resolve()
    values = _parse(root / "environments/common.env")
    values.update(_parse(env_file.resolve()))
    if values.get("WEAVE_ENVIRONMENT") != profile:
        raise DeploymentContextError("environment file does not bind the requested profile")
    if not IMAGE.fullmatch(keycloak_image) or not IMAGE.fullmatch(sanitizer_image):
        if profile != "dev":
            raise DeploymentContextError("persistent supervisor images must be immutable digests")
    values["WEAVE_KEYCLOAK_IMAGE"] = keycloak_image
    values["WEAVE_KEYCLOAK_SANITIZER_IMAGE"] = sanitizer_image
    if not 1 <= runtime_uid <= 2**31 - 1 or not 1 <= runtime_gid <= 2**31 - 1:
        raise DeploymentContextError("supervisor runtime uid/gid must be non-root positive integers")
    values["WEAVE_RUNTIME_UID"] = str(runtime_uid)
    values["WEAVE_RUNTIME_GID"] = str(runtime_gid)
    if stack_scope not in {"persistent", "isolated"}:
        raise DeploymentContextError("stack scope must be persistent or isolated")
    if stack_scope == "isolated":
        if profile != "dogfood" or e2e_run_id is None or not RUN_ID.fullmatch(e2e_run_id):
            raise DeploymentContextError("isolated supervisor scope requires a valid dogfood E2E run id")
        namespace = "weave-e2e-" + hashlib.sha256(e2e_run_id.encode("ascii")).hexdigest()[:16]
        generated = f"./.generated/isolated/{namespace}"
        values.update(
            {
                "WEAVE_DEPLOYMENT_SCOPE": "isolated-e2e",
                "WEAVE_DEPLOYMENT_INSTANCE": namespace,
                "WEAVE_COMPOSE_PROJECT": namespace,
                "WEAVE_RESOURCE_PREFIX": namespace,
                "WEAVE_STACK_SCOPE": "isolated",
                "WEAVE_E2E_STACK_SCOPE": "isolated",
                "WEAVE_E2E_RUN_ID": e2e_run_id,
                "WEAVE_E2E_RUN_NAMESPACE": namespace,
                "WEAVE_DOCKER_NETWORK": f"{namespace}_network",
                "WEAVE_GENERATED_ROOT": generated,
                "WEAVE_SECRET_ROOT": f"{generated}/secrets",
                "WEAVE_TLS_ROOT": f"{generated}/tls",
            }
        )
    else:
        values["WEAVE_STACK_SCOPE"] = "persistent"
        if e2e_run_id is not None:
            raise DeploymentContextError("persistent supervisor scope cannot carry an E2E run id")
    required = (
        "WEAVE_DEPLOYMENT_SCOPE",
        "WEAVE_DEPLOYMENT_INSTANCE",
        "WEAVE_COMPOSE_PROJECT",
        "WEAVE_RESOURCE_PREFIX",
        "WEAVE_STACK_SCOPE",
        "WEAVE_DOCKER_NETWORK",
        "WEAVE_GENERATED_ROOT",
        "WEAVE_SECRET_ROOT",
        "WEAVE_DB_ADMIN_USERNAME",
        "WEAVE_KEYCLOAK_DB_NAME",
        "WEAVE_KEYCLOAK_DB_USERNAME",
        "WEAVE_CONTROL_DB_USERNAME",
        "WEAVE_AUTH_URL",
    )
    missing = [name for name in required if not values.get(name)]
    if missing:
        raise DeploymentContextError("deployment input omits required values: " + ", ".join(missing))
    if not NAME.fullmatch(values["WEAVE_COMPOSE_PROJECT"]) or not NAME.fullmatch(values["WEAVE_RESOURCE_PREFIX"]):
        raise DeploymentContextError("deployment project or resource prefix is malformed")
    authority = urlsplit(values["WEAVE_AUTH_URL"])
    if authority.scheme != "https" or not authority.hostname or authority.username or authority.password:
        raise DeploymentContextError("Keycloak public authority must be credential-free HTTPS")
    return values


def deployment_path(root: Path, value: str) -> Path:
    path = Path(value)
    return path.resolve() if path.is_absolute() else (root / path).resolve()
