#!/usr/bin/env python3
"""Closed environment and naming contract for the Weave Compose deployment."""

from __future__ import annotations

import hashlib
import json
import os
import re
import subprocess
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable, Mapping
from urllib.parse import urlsplit


PROFILES = ("dev", "dogfood", "main")
KEY_RE = re.compile(r"^[A-Z][A-Z0-9_]*$")
ISOLATED_RUN_RE = re.compile(r"^[a-z0-9][a-z0-9-]{5,39}$")
PUBLISHED_DIGEST_IMAGE_RE = re.compile(r"^[^\s@]+@sha256:[0-9a-f]{64}$")
LOCAL_IMAGE_ID_RE = re.compile(r"^sha256:[0-9a-f]{64}$")
COMMIT_RE = re.compile(r"^[0-9a-f]{40}$")
SECRET_NAME_MARKERS = ("PASSWORD", "SECRET", "TOKEN", "ASSERTION", "PRIVATE_KEY", "CREDENTIAL")
PUBLIC_SECRET_COORDINATES = {
    "WEAVE_SECRET_ROOT",
}
PUBLIC_PROCESS_COORDINATES = {
    "WEAVE_SPEC_CORPUS_ROOT",
}
DEPLOYMENT_PROCESS_OVERRIDES = {
    "WEAVE_BACKEND_IMAGE",
    "WEAVE_KEYCLOAK_IMAGE",
    "WEAVE_KEYCLOAK_SANITIZER_IMAGE",
    "WEAVE_MCP_IMAGE",
}
OPERATOR_PROCESS_INPUTS = {
    "WEAVE_ADOPTION_RECEIPT",
    "WEAVE_BACKUP_ROOT",
    "WEAVE_CANDIDATE_COMMIT",
    "WEAVE_IMAGE_SOURCE_COMMIT",
    "WEAVE_E2E_RUN_ID",
    "WEAVE_E2E_RUN_NAMESPACE",
    "WEAVE_E2E_STACK_SCOPE",
    "WEAVE_KEYCLOAK_SUPERVISOR",
    "WEAVE_RECONCILIATION_NONCE",
    "WEAVE_SPEC_CORPUS_ROOT",
}
PROCESS_RUNTIME_COORDINATES = {
    "DOCKER_CONFIG",
    "DOCKER_CONTEXT",
    "DOCKER_HOST",
    "HOME",
    "LANG",
    "LC_ALL",
    "PATH",
    "SSL_CERT_DIR",
    "SSL_CERT_FILE",
    "TMPDIR",
    "XDG_CONFIG_HOME",
}


class ContractError(RuntimeError):
    pass


@dataclass(frozen=True)
class ComposeContext:
    profile: str
    root: Path
    repository_root: Path
    common_env_file: Path
    profile_env_file: Path
    env: dict[str, str]
    isolated_namespace: str | None

    @property
    def compose_files(self) -> tuple[Path, Path]:
        return self.root / "compose.yaml", self.root / f"compose.{self.profile}.yaml"

    @property
    def generated_root(self) -> Path:
        return resolve_deployment_path(self.root, self.env["WEAVE_GENERATED_ROOT"])

    @property
    def secret_root(self) -> Path:
        return resolve_deployment_path(self.root, self.env["WEAVE_SECRET_ROOT"])

    @property
    def tls_root(self) -> Path:
        return resolve_deployment_path(self.root, self.env["WEAVE_TLS_ROOT"])

    @property
    def compose_base_command(self) -> list[str]:
        command = ["docker", "compose"]
        for path in self.compose_files:
            command.extend(("--file", str(path)))
        command.extend(("--project-name", self.env["WEAVE_COMPOSE_PROJECT"], "--profile", self.profile))
        return command


def fail(message: str) -> None:
    raise ContractError(message)


def resolve_deployment_path(root: Path, value: str) -> Path:
    path = Path(value)
    return path if path.is_absolute() else (root / path).resolve()


def parse_env_file(path: Path) -> dict[str, str]:
    if not path.is_file() or path.is_symlink():
        fail(f"environment file is missing or is a symlink: {path}")
    values: dict[str, str] = {}
    for number, original in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
        line = original.strip()
        if not line or line.startswith("#"):
            continue
        if line.startswith("export ") or "=" not in line:
            fail(f"{path}:{number}: only literal KEY=VALUE entries are allowed")
        key, value = line.split("=", 1)
        if key != key.strip() or not KEY_RE.fullmatch(key):
            fail(f"{path}:{number}: invalid environment key")
        if key in values:
            fail(f"{path}:{number}: duplicate environment key {key}")
        if "\x00" in value or "\r" in value:
            fail(f"{path}:{number}: invalid control byte")
        if any(marker in key for marker in SECRET_NAME_MARKERS) and key not in PUBLIC_SECRET_COORDINATES:
            fail(f"{path}:{number}: credential-shaped input {key} belongs in WEAVE_SECRET_ROOT")
        values[key] = value
    return values


def _profile_file(root: Path, profile: str, supplied: str | None) -> Path:
    if profile == "dev":
        return Path(supplied).expanduser().resolve() if supplied else root / "environments/dev.env"
    if not supplied:
        fail(f"{profile} requires WEAVE_ENV_FILE pointing to a private, reviewed environment file")
    path = Path(supplied).expanduser().resolve()
    if path.name.endswith(".example"):
        fail(f"refusing to deploy {profile} from an example environment file")
    return path


def _isolated_overrides(profile: str, env: dict[str, str]) -> tuple[dict[str, str], str | None]:
    scope = os.environ.get("WEAVE_E2E_STACK_SCOPE", "")
    if scope not in ("", "persistent", "isolated"):
        fail("WEAVE_E2E_STACK_SCOPE must be persistent or isolated")
    if scope != "isolated":
        env["WEAVE_STACK_SCOPE"] = "persistent"
        return env, None
    if profile != "dogfood":
        fail("isolated E2E uses the dogfood topology")
    run_id = os.environ.get("WEAVE_E2E_RUN_ID", "")
    if not ISOLATED_RUN_RE.fullmatch(run_id):
        fail("isolated E2E requires WEAVE_E2E_RUN_ID matching [a-z0-9][a-z0-9-]{5,39}")
    namespace = "weave-e2e-" + hashlib.sha256(run_id.encode("ascii")).hexdigest()[:16]
    declared_namespace = os.environ.get("WEAVE_E2E_RUN_NAMESPACE", namespace)
    if declared_namespace != namespace:
        fail("WEAVE_E2E_RUN_NAMESPACE does not match the deterministic isolated run namespace")
    root = f"./.generated/isolated/{namespace}"
    volume_prefix = namespace.replace("-", "_")
    env.update(
        {
            "WEAVE_ENVIRONMENT": "dogfood",
            "WEAVE_DEPLOYMENT_SCOPE": "isolated-e2e",
            "WEAVE_DEPLOYMENT_INSTANCE": namespace,
            "WEAVE_COMPOSE_PROJECT": namespace,
            "WEAVE_RESOURCE_PREFIX": namespace,
            "WEAVE_E2E_RUN_NAMESPACE": namespace,
            "WEAVE_STACK_SCOPE": "isolated",
            "WEAVE_DOCKER_NETWORK": f"{namespace}_network",
            "WEAVE_GENERATED_ROOT": root,
            "WEAVE_SECRET_ROOT": f"{root}/secrets",
            "WEAVE_TLS_ROOT": f"{root}/tls",
            "WEAVE_CADDY_DATA_VOLUME": f"{volume_prefix}_caddy_data",
            "WEAVE_CADDY_CONFIG_VOLUME": f"{volume_prefix}_caddy_config",
            "WEAVE_DB_DATA_VOLUME": f"{volume_prefix}_db_data",
            "WEAVE_KEYCLOAK_DATA_VOLUME": f"{volume_prefix}_keycloak_data",
            "WEAVE_MAILPIT_DATA_VOLUME": f"{volume_prefix}_mailpit_data",
            "WEAVE_NEXTCLOUD_DATA_VOLUME": f"{volume_prefix}_nextcloud_data",
            "WEAVE_SYNAPSE_DATA_VOLUME": f"{volume_prefix}_synapse_data",
            "WEAVE_MATRIX_APPSERVICE_VOLUME": f"{volume_prefix}_matrix_chat_appservice_runtime",
        }
    )
    port_names = (
        "WEAVE_PROXY_HTTP_HOST_PORT",
        "WEAVE_PROXY_HTTPS_HOST_PORT",
        "WEAVE_KEYCLOAK_HOST_PORT",
        "WEAVE_KEYCLOAK_MANAGEMENT_HOST_PORT",
        "WEAVE_MAILPIT_WEB_HOST_PORT",
        "WEAVE_MAS_HOST_PORT",
        "WEAVE_SYNAPSE_HOST_PORT",
        "WEAVE_NEXTCLOUD_HOST_PORT",
        "WEAVE_BACKEND_HOST_PORT",
        "WEAVE_MCP_HOST_PORT",
    )
    supplied_ports = {name: os.environ.get(name, "") for name in port_names}
    if any(supplied_ports.values()):
        if not all(value.isdigit() and 1024 <= int(value) <= 65535 for value in supplied_ports.values()):
            fail("isolated E2E requires every declared host port to be an integer from 1024 through 65535")
        if len(set(supplied_ports.values())) != len(port_names):
            fail("isolated E2E host ports must be unique")
        env.update(supplied_ports)
    else:
        for name in port_names:
            env[name] = "0"
    return env, namespace


def load_context(profile: str, root: Path, supplied_env_file: str | None = None) -> ComposeContext:
    if profile not in PROFILES:
        fail(f"profile must be one of: {', '.join(PROFILES)}")
    root = root.resolve()
    repository_root = root.parents[1]
    common = root / "environments/common.env"
    selected = _profile_file(root, profile, supplied_env_file or os.environ.get("WEAVE_ENV_FILE"))
    env = parse_env_file(common)
    env.update(parse_env_file(selected))
    for name in DEPLOYMENT_PROCESS_OVERRIDES:
        value = os.environ.get(name)
        if value:
            env[name] = value
    env.setdefault("WEAVE_RUNTIME_UID", str(os.getuid()))
    env.setdefault("WEAVE_RUNTIME_GID", str(os.getgid()))
    env.setdefault("WEAVE_MATRIX_HOST", urlsplit(env.get("WEAVE_MATRIX_URL", "")).hostname or "")
    public = urlsplit(env.get("WEAVE_PUBLIC_URL", ""))
    admin_host = f"{env.get('WEAVE_ADMIN_SUBDOMAIN', 'admin')}.{env.get('WEAVE_TENANT_DOMAIN', '')}"
    admin_authority = admin_host + (f":{public.port}" if public.port is not None else "")
    env.setdefault("WEAVE_ADMIN_CONSOLE_URL", f"{public.scheme}://{admin_authority}")
    env.setdefault("WEAVE_PROVIDER_PROFILE", "sovereign-default")
    if env.get("WEAVE_ENVIRONMENT") != profile:
        fail(f"{selected} declares WEAVE_ENVIRONMENT={env.get('WEAVE_ENVIRONMENT')!r}, expected {profile}")
    env, namespace = _isolated_overrides(profile, env)
    _validate_environment(profile, env)
    return ComposeContext(profile, root, repository_root, common, selected, env, namespace)


def _validate_environment(profile: str, env: Mapping[str, str]) -> None:
    required = (
        "WEAVE_COMPOSE_PROJECT",
        "WEAVE_RESOURCE_PREFIX",
        "WEAVE_DOCKER_NETWORK",
        "WEAVE_GENERATED_ROOT",
        "WEAVE_SECRET_ROOT",
        "WEAVE_TLS_ROOT",
        "WEAVE_PUBLIC_URL",
        "WEAVE_API_URL",
        "WEAVE_AUTH_URL",
        "WEAVE_MATRIX_URL",
        "WEAVE_FILES_URL",
        "WEAVE_ADMIN_CONSOLE_URL",
        "WEAVE_PROVIDER_PROFILE",
    )
    missing = [name for name in required if not env.get(name)]
    if missing:
        fail(f"missing public deployment inputs: {', '.join(missing)}")
    expected_scope = "isolated-e2e" if env.get("WEAVE_STACK_SCOPE") == "isolated" else {
        "dev": "developer", "dogfood": "persistent-dogfood", "main": "main"
    }[profile]
    if env.get("WEAVE_DEPLOYMENT_SCOPE") != expected_scope:
        fail(f"{profile} requires WEAVE_DEPLOYMENT_SCOPE={expected_scope}")
    for name in (
        "WEAVE_PUBLIC_URL",
        "WEAVE_API_URL",
        "WEAVE_AUTH_URL",
        "WEAVE_MATRIX_URL",
        "WEAVE_FILES_URL",
        "WEAVE_ADMIN_CONSOLE_URL",
    ):
        if not env[name].startswith("https://"):
            fail(f"{name} must be HTTPS")
        if any(value in env[name] for value in ("@", "?", "#", "\\")):
            fail(f"{name} must not carry credentials, query, fragment, or backslash")
    if env["WEAVE_PROVIDER_PROFILE"] != "sovereign-default":
        fail("Core Compose profiles require WEAVE_PROVIDER_PROFILE=sovereign-default")
    if profile in ("dogfood", "main"):
        image_names = [
            "WEAVE_POSTGRES_IMAGE",
            "WEAVE_CADDY_IMAGE",
            "WEAVE_KEYCLOAK_IMAGE",
            "WEAVE_KEYCLOAK_SANITIZER_IMAGE",
            "WEAVE_MAS_IMAGE",
            "WEAVE_SYNAPSE_IMAGE",
            "WEAVE_NEXTCLOUD_IMAGE",
            "WEAVE_BACKEND_IMAGE",
            "WEAVE_MCP_IMAGE",
        ]
        if profile == "dogfood":
            image_names.append("WEAVE_MAILPIT_IMAGE")
        local_candidate_images = {
            "WEAVE_BACKEND_IMAGE", "WEAVE_KEYCLOAK_IMAGE",
            "WEAVE_KEYCLOAK_SANITIZER_IMAGE", "WEAVE_MCP_IMAGE"
        } if profile == "dogfood" else set()
        unpinned = [
            name for name in image_names
            if not PUBLISHED_DIGEST_IMAGE_RE.fullmatch(env[name])
            and not (name in local_candidate_images and LOCAL_IMAGE_ID_RE.fullmatch(env[name]))
        ]
        if unpinned:
            fail(f"{profile} requires digest-pinned images: {', '.join(sorted(unpinned))}")


def compose_environment(context: ComposeContext) -> dict[str, str]:
    allowed = PROCESS_RUNTIME_COORDINATES | OPERATOR_PROCESS_INPUTS
    result = {key: value for key, value in os.environ.items() if key in allowed}
    result.update(context.env)
    result.pop("WEAVE_ENV_FILE", None)
    return result


def run(command: Iterable[str], context: ComposeContext, *, check: bool = True, capture: bool = False) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        list(command),
        cwd=context.root,
        env=compose_environment(context),
        check=check,
        text=True,
        stdout=subprocess.PIPE if capture else None,
        stderr=subprocess.PIPE if capture else None,
    )


def canonical_json(value: object) -> bytes:
    # Contract documents contain integers, strings, booleans, arrays and maps;
    # this is the RFC 8785 representation for that closed value set.
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":")).encode("utf-8")


def revision(value: Mapping[str, object]) -> str:
    projection = dict(value)
    projection.pop("revision", None)
    return "sha256:" + hashlib.sha256(canonical_json(projection)).hexdigest()


def assert_revision(value: Mapping[str, object], source: Path) -> None:
    expected = value.get("revision")
    actual = revision(value)
    if expected != actual:
        fail(f"canonical revision mismatch for {source}: expected {expected}, computed {actual}")


def specification_context(context: ComposeContext) -> tuple[Path, str]:
    lock_path = context.repository_root / "specs/weave-specs.lock.json"
    lock = json.loads(lock_path.read_text(encoding="utf-8"))
    corpus = lock.get("specCorpus", {})
    commit = corpus.get("gitCommit", "")
    if not COMMIT_RE.fullmatch(commit):
        fail("spec corpus lock does not contain a lowercase 40-character commit")
    override = os.environ.get("WEAVE_SPEC_CORPUS_ROOT", "")
    if override:
        override_path = Path(override)
        if not override_path.is_absolute():
            fail("WEAVE_SPEC_CORPUS_ROOT must be an absolute Git worktree path")
        corpus_root = override_path.resolve()
    else:
        corpus_root = (context.repository_root / corpus.get("localPath", "")).resolve()
    if not corpus_root.is_dir() or not (corpus_root / ".git").exists():
        # Git worktrees store .git as a regular file. Exported trees and bare
        # repositories cannot prove the exact checked-out specification HEAD.
        fail(f"pinned specification corpus Git worktree is unavailable: {corpus_root}")
    try:
        git_details = subprocess.run(
            ["git", "-C", str(corpus_root), "rev-parse", "--show-toplevel", "--is-inside-work-tree", "HEAD"],
            check=True,
            text=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
        ).stdout.splitlines()
    except subprocess.CalledProcessError as error:
        fail(f"pinned specification corpus is not a readable Git worktree: {corpus_root}")
    if len(git_details) != 3 or Path(git_details[0]).resolve() != corpus_root or git_details[1] != "true":
        fail(f"pinned specification corpus override is not a Git worktree root: {corpus_root}")
    observed = git_details[2].strip()
    if observed != commit:
        fail(f"specification corpus is {observed}, lock requires {commit}")
    return corpus_root, commit
