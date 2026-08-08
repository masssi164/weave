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


OPERATOR_ENVIRONMENTS = ("dev", "dogfood", "prod", "e2e")
ENVIRONMENT_SELECTORS = OPERATOR_ENVIRONMENTS
COMPOSE_ENVIRONMENT_PROFILES = frozenset(OPERATOR_ENVIRONMENTS)
OPTIONAL_COMPOSE_PROFILES = frozenset(
    ("dev-tools", "provider-matrix", "provider-nextcloud", "storage-s3")
)
KNOWN_COMPOSE_PROFILES = COMPOSE_ENVIRONMENT_PROFILES | OPTIONAL_COMPOSE_PROFILES
DEPLOYMENT_CONTEXTS = {
    "dev": {"developer", "disposable"},
    "dogfood": {"persistent-dogfood"},
    "e2e": {"disposable"},
    "prod": {"production"},
}
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
    "WEAVE_IDENTITY_OPS_IMAGE",
    "WEAVE_MCP_IMAGE",
}
OPERATOR_PROCESS_INPUTS = {
    "WEAVE_BACKUP_ROOT",
    "WEAVE_CANDIDATE_COMMIT",
    "WEAVE_CANDIDATE_MANIFEST_DIGEST",
    "WEAVE_IMAGE_SOURCE_COMMIT",
    "WEAVE_E2E_RUN_ID",
    "WEAVE_E2E_RUN_NAMESPACE",
    "WEAVE_E2E_STACK_SCOPE",
    "WEAVE_IDENTITY_ROTATION_EPOCH",
    "WEAVE_RESOURCE_GENERATION",
    "WEAVE_RESOURCE_STACK",
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


def derived_profiles(
    environment: str, profile: str, env: Mapping[str, str]
) -> tuple[str, ...]:
    if environment not in OPERATOR_ENVIRONMENTS or profile != environment:
        fail("Compose topology profile must equal its operator environment")
    profiles = [environment]
    if env.get("WEAVE_CHAT_PROVIDER") == "matrix-synapse":
        profiles.append("provider-matrix")
    if (
        env.get("WEAVE_FILES_PROVIDER") == "nextcloud-webdav"
        or env.get("WEAVE_CALENDAR_PROVIDER") == "nextcloud-caldav"
    ):
        profiles.append("provider-nextcloud")
    if env.get("WEAVE_FILES_NATIVE_BLOB_STORE") == "s3-compatible":
        profiles.append("storage-s3")
    if environment == "e2e":
        profiles.append("storage-s3")
    return tuple(dict.fromkeys(profiles))


def declared_profiles(env: Mapping[str, str]) -> tuple[str, ...]:
    value = env.get("COMPOSE_PROFILES", "")
    if not value or value != value.strip():
        fail("COMPOSE_PROFILES must be an explicit comma-separated profile list")
    profiles = tuple(value.split(","))
    if any(
        not profile
        or profile != profile.strip()
        or not re.fullmatch(r"[a-z0-9][a-z0-9-]{1,31}", profile)
        for profile in profiles
    ):
        fail("COMPOSE_PROFILES contains an invalid profile name")
    if len(profiles) != len(set(profiles)):
        fail("COMPOSE_PROFILES must not contain duplicate profiles")
    unknown = sorted(set(profiles) - KNOWN_COMPOSE_PROFILES)
    if unknown:
        fail(f"COMPOSE_PROFILES contains unsupported profiles: {', '.join(unknown)}")
    return profiles


@dataclass(frozen=True)
class ComposeContext:
    environment: str
    profile: str
    root: Path
    repository_root: Path
    common_env_file: Path
    profile_env_file: Path
    env: dict[str, str]
    isolated_namespace: str | None

    @property
    def compose_files(self) -> tuple[Path, ...]:
        return (
            self.root / "compose.yaml",
            self.root / f"compose.{self.environment}.yaml",
        )

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
        command = [
            "docker",
            "compose",
            "--env-file",
            str(self.common_env_file),
            "--env-file",
            str(self.profile_env_file),
        ]
        for path in self.compose_files:
            command.extend(("--file", str(path)))
        command.extend(("--project-name", self.env["WEAVE_COMPOSE_PROJECT"]))
        return command

    @property
    def active_profiles(self) -> tuple[str, ...]:
        return declared_profiles(self.env)


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


def _profile_file(root: Path, environment: str, supplied: str | None) -> Path:
    if environment == "dev":
        return Path(supplied).expanduser().resolve() if supplied else root / "environments/dev.env"
    if not supplied:
        fail(f"{environment} requires WEAVE_ENV_FILE pointing to a private, reviewed environment file")
    path = Path(supplied).expanduser().resolve()
    if path.name.endswith(".example"):
        fail(f"refusing to deploy {environment} from an example environment file")
    return path


def _isolated_overrides(environment: str, env: dict[str, str]) -> tuple[dict[str, str], str | None]:
    scope = os.environ.get("WEAVE_E2E_STACK_SCOPE", "")
    if scope not in ("", "persistent", "isolated"):
        fail("WEAVE_E2E_STACK_SCOPE must be persistent or isolated")
    if environment == "e2e" and scope != "isolated":
        fail("e2e requires WEAVE_E2E_STACK_SCOPE=isolated")
    if scope != "isolated":
        if os.environ.get("WEAVE_E2E_NAMESPACE") or os.environ.get("WEAVE_E2E_RUN_NAMESPACE"):
            fail("persistent deployments reject isolated E2E namespace inputs")
        env["WEAVE_STACK_SCOPE"] = "persistent"
        return env, None
    if environment != "e2e":
        fail("isolated E2E uses the e2e environment")
    run_id = os.environ.get("WEAVE_E2E_RUN_ID", "")
    if not ISOLATED_RUN_RE.fullmatch(run_id):
        fail("isolated E2E requires WEAVE_E2E_RUN_ID matching [a-z0-9][a-z0-9-]{5,39}")
    namespace = "weave-e2e-" + hashlib.sha256(run_id.encode("ascii")).hexdigest()[:16]
    declared_namespace = os.environ.get("WEAVE_E2E_RUN_NAMESPACE", namespace)
    if declared_namespace != namespace:
        fail("WEAVE_E2E_RUN_NAMESPACE does not match the deterministic isolated run namespace")
    contract_namespace = os.environ.get("WEAVE_E2E_NAMESPACE", namespace)
    if contract_namespace != namespace:
        fail("WEAVE_E2E_NAMESPACE does not match the deterministic isolated run namespace")
    root = f"./.generated/isolated/{namespace}"
    volume_prefix = namespace.replace("-", "_")
    env.update(
        {
            "WEAVE_ENVIRONMENT": "e2e",
            "WEAVE_DEPLOYMENT_CONTEXT": "disposable",
            "WEAVE_DEPLOYMENT_SCOPE": "isolated-e2e",
            "WEAVE_DEPLOYMENT_INSTANCE": namespace,
            "WEAVE_COMPOSE_PROJECT": namespace,
            "WEAVE_RESOURCE_PREFIX": namespace,
            "WEAVE_E2E_RUN_NAMESPACE": namespace,
            "WEAVE_E2E_NAMESPACE": namespace,
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
            "WEAVE_RUNTIME_STATE_VOLUME": f"{volume_prefix}_runtime_state",
            "WEAVE_NATIVE_FILES_DATA_VOLUME": f"{volume_prefix}_native_files_data",
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
    # The public E2E template uses zero to request Docker-assigned ports.
    # Treat an all-zero/empty set as unsupplied; a partially explicit set still
    # fails the all-values check.
    if any(value not in {"", "0"} for value in supplied_ports.values()):
        if not all(value.isdigit() and 1024 <= int(value) <= 65535 for value in supplied_ports.values()):
            fail("isolated E2E requires every declared host port to be an integer from 1024 through 65535")
        if len(set(supplied_ports.values())) != len(port_names):
            fail("isolated E2E host ports must be unique")
        env.update(supplied_ports)
    else:
        for name in port_names:
            env[name] = "0"
    return env, namespace


def load_context(selector: str, root: Path, supplied_env_file: str | None = None) -> ComposeContext:
    if selector not in ENVIRONMENT_SELECTORS:
        fail(f"environment must be one of: {', '.join(OPERATOR_ENVIRONMENTS)}")
    root = root.resolve()
    repository_root = root.parents[1]
    common = root / "environments/common.env"
    selected = _profile_file(root, selector, supplied_env_file or os.environ.get("WEAVE_ENV_FILE"))
    env = parse_env_file(common)
    env.update(parse_env_file(selected))
    declared_environment = env.get("WEAVE_ENVIRONMENT", "")
    environment = selector
    profile = environment
    if "WEAVE_IDENTITY_ROTATION_EPOCH" in os.environ:
        epoch = os.environ["WEAVE_IDENTITY_ROTATION_EPOCH"]
        if not re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9._:-]{7,127}", epoch):
            fail("WEAVE_IDENTITY_ROTATION_EPOCH is not a valid explicit operator epoch")
        env["WEAVE_IDENTITY_ROTATION_EPOCH"] = epoch
    for name in DEPLOYMENT_PROCESS_OVERRIDES:
        value = os.environ.get(name)
        if value:
            env[name] = value
    lock_path = repository_root / "specs/weave-specs.lock.json"
    lock_bytes = lock_path.read_bytes()
    lock = json.loads(lock_bytes)
    spec_commit = lock["specCorpus"]["gitCommit"]
    if not COMMIT_RE.fullmatch(spec_commit):
        fail("specification lock does not contain an immutable commit")
    candidate_commit = os.environ.get("WEAVE_CANDIDATE_COMMIT", "")
    if not candidate_commit:
        candidate_commit = subprocess.run(
            ["git", "rev-parse", "HEAD"],
            cwd=repository_root,
            check=True,
            text=True,
            stdout=subprocess.PIPE,
        ).stdout.strip()
    if not COMMIT_RE.fullmatch(candidate_commit):
        fail("WEAVE_CANDIDATE_COMMIT must be one immutable implementation commit")
    spec_digest = "sha256:" + hashlib.sha256(lock_bytes).hexdigest()
    resource_environment = environment
    resource_generation = os.environ.get(
        "WEAVE_RESOURCE_GENERATION", env.get("WEAVE_RESOURCE_GENERATION", "fresh-v1")
    )
    resource_stack = os.environ.get(
        "WEAVE_RESOURCE_STACK", env.get("WEAVE_RESOURCE_STACK", "weave")
    )
    for label, value in (
        ("resource generation", resource_generation),
        ("resource stack", resource_stack),
    ):
        if not re.fullmatch(r"[a-z0-9][a-z0-9._-]{2,63}", value):
            fail(f"{label} is not a support-safe immutable label value")
    local_candidate_manifest = json.dumps(
        {
            "schemaVersion": "weave.compose-local-candidate.v1",
            "candidateCommit": candidate_commit,
            "deploymentInstance": env.get("WEAVE_DEPLOYMENT_INSTANCE"),
            "environment": environment,
            "topologyProfile": profile,
            "specDigest": spec_digest,
        },
        ensure_ascii=False,
        separators=(",", ":"),
        sort_keys=True,
    ).encode("utf-8")
    candidate_manifest_digest = os.environ.get(
        "WEAVE_CANDIDATE_MANIFEST_DIGEST",
        "sha256:" + hashlib.sha256(local_candidate_manifest).hexdigest(),
    )
    if not re.fullmatch(r"sha256:[0-9a-f]{64}", candidate_manifest_digest):
        fail("WEAVE_CANDIDATE_MANIFEST_DIGEST must be one full SHA-256 digest")
    source_candidate_commit = os.environ.get("WEAVE_IMAGE_SOURCE_COMMIT", candidate_commit)
    if not COMMIT_RE.fullmatch(source_candidate_commit):
        fail("WEAVE_IMAGE_SOURCE_COMMIT must be one immutable implementation commit")
    env.update(
        {
            "WEAVE_RESOURCE_ENVIRONMENT": resource_environment,
            "WEAVE_RESOURCE_GENERATION": resource_generation,
            "WEAVE_RESOURCE_STACK": resource_stack,
            "WEAVE_SPEC_COMMIT": spec_commit,
            "WEAVE_SPEC_DIGEST": spec_digest,
            "WEAVE_CANDIDATE_COMMIT": candidate_commit,
            "WEAVE_IMAGE_SOURCE_COMMIT": source_candidate_commit,
            "WEAVE_CANDIDATE_MANIFEST_DIGEST": candidate_manifest_digest,
        }
    )
    env.setdefault("WEAVE_RUNTIME_UID", str(os.getuid()))
    env.setdefault("WEAVE_RUNTIME_GID", str(os.getgid()))
    env.setdefault("WEAVE_MATRIX_HOST", urlsplit(env.get("WEAVE_MATRIX_URL", "")).hostname or "")
    public = urlsplit(env.get("WEAVE_PUBLIC_URL", ""))
    admin_host = f"{env.get('WEAVE_ADMIN_SUBDOMAIN', 'admin')}.{env.get('WEAVE_TENANT_DOMAIN', '')}"
    admin_authority = admin_host + (f":{public.port}" if public.port is not None else "")
    env.setdefault("WEAVE_ADMIN_CONSOLE_URL", f"{public.scheme}://{admin_authority}")
    env.setdefault("WEAVE_PROVIDER_PROFILE", "sovereign-default")
    if declared_environment != environment:
        fail(
            f"{selected} declares WEAVE_ENVIRONMENT={declared_environment!r}, "
            f"expected {environment}"
        )
    env, namespace = _isolated_overrides(environment, env)
    _validate_environment(environment, profile, env)
    return ComposeContext(environment, profile, root, repository_root, common, selected, env, namespace)


def _validate_environment(environment: str, profile: str, env: Mapping[str, str]) -> None:
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
        "COMPOSE_PROFILES",
        "WEAVE_NATIVE_FILES_DATA_VOLUME",
        "WEAVE_FILES_PROVIDER",
        "WEAVE_FILES_NATIVE_BLOB_STORE",
        "WEAVE_CHAT_PROVIDER",
        "WEAVE_CALENDAR_PROVIDER",
    )
    missing = [name for name in required if not env.get(name)]
    if missing:
        fail(f"missing public deployment inputs: {', '.join(missing)}")
    deployment_context = env.get("WEAVE_DEPLOYMENT_CONTEXT", "")
    if deployment_context not in DEPLOYMENT_CONTEXTS[environment]:
        fail(
            f"{environment} requires WEAVE_DEPLOYMENT_CONTEXT to be one of: "
            + ", ".join(sorted(DEPLOYMENT_CONTEXTS[environment]))
        )
    expected_scope = {
        "dev": "developer",
        "dogfood": "dogfood",
        "e2e": "isolated-e2e",
        "prod": "production",
    }[environment]
    if env.get("WEAVE_DEPLOYMENT_SCOPE") != expected_scope:
        fail(f"{environment} requires WEAVE_DEPLOYMENT_SCOPE={expected_scope}")
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
    allowed_provider_values = {
        "WEAVE_FILES_PROVIDER": {"weave-native", "nextcloud-webdav"},
        "WEAVE_FILES_NATIVE_BLOB_STORE": {"filesystem", "s3-compatible"},
        "WEAVE_CHAT_PROVIDER": {"weave-native", "matrix-synapse"},
        "WEAVE_CALENDAR_PROVIDER": {"weave-native", "nextcloud-caldav"},
    }
    for name, allowed_values in allowed_provider_values.items():
        if env[name] not in allowed_values:
            fail(f"{name} must be one of: {', '.join(sorted(allowed_values))}")
    active_profiles = set(declared_profiles(env))
    expected_profiles = set(derived_profiles(environment, profile, env))
    environment_profiles = active_profiles & COMPOSE_ENVIRONMENT_PROFILES
    if environment_profiles != {environment}:
        fail(
            f"COMPOSE_PROFILES must select only the {environment} environment profile"
        )
    if not expected_profiles.issubset(active_profiles):
        missing_profiles = sorted(expected_profiles - active_profiles)
        fail(
            "COMPOSE_PROFILES is missing provider/runtime profiles required by public inputs: "
            + ", ".join(missing_profiles)
        )
    unexpected_optional = active_profiles - expected_profiles
    if unexpected_optional - ({"dev-tools"} if environment == "dev" else set()):
        fail(
            "COMPOSE_PROFILES enables provider/runtime profiles that contradict public inputs: "
            + ", ".join(sorted(unexpected_optional))
        )
    if env["WEAVE_FILES_NATIVE_BLOB_STORE"] == "s3-compatible":
        fail(
            "native Files S3-compatible storage is not deployment-qualified with file-based "
            "credentials; select filesystem until its exact SecretRef binding is implemented"
        )
    if (
        env["WEAVE_CHAT_PROVIDER"] == "matrix-synapse"
        or env["WEAVE_FILES_PROVIDER"] == "nextcloud-webdav"
        or env["WEAVE_CALENDAR_PROVIDER"] == "nextcloud-caldav"
    ):
        fail(
            "optional Matrix/Nextcloud provider activation is blocked until an explicit "
            "manifest-bound Keycloak IAM migration is implemented and qualified"
        )
    if environment != "dev":
        image_names = [
            "WEAVE_POSTGRES_IMAGE",
            "WEAVE_CADDY_IMAGE",
            "WEAVE_KEYCLOAK_IMAGE",
            "WEAVE_IDENTITY_OPS_IMAGE",
            "WEAVE_BACKEND_IMAGE",
            "WEAVE_MCP_IMAGE",
        ]
        if "provider-matrix" in active_profiles:
            image_names.extend(("WEAVE_MAS_IMAGE", "WEAVE_SYNAPSE_IMAGE"))
        if "provider-nextcloud" in active_profiles:
            image_names.append("WEAVE_NEXTCLOUD_IMAGE")
        if environment == "e2e":
            image_names.extend(("WEAVE_MAILPIT_IMAGE", "WEAVE_RUNTIME_STATE_IMAGE"))
        local_candidate_images = {
            "WEAVE_BACKEND_IMAGE", "WEAVE_IDENTITY_OPS_IMAGE", "WEAVE_MCP_IMAGE"
        } if environment == "e2e" else set()
        if environment == "e2e" and env.get("WEAVE_STACK_SCOPE") == "isolated":
            # Live E2E resolves the pinned stock multi-arch index to its exact
            # local platform image ID before Compose. Persistent dogfood/prod
            # still require the reviewed published digest reference.
            local_candidate_images.add("WEAVE_KEYCLOAK_IMAGE")
        unpinned = [
            name for name in image_names
            if not PUBLISHED_DIGEST_IMAGE_RE.fullmatch(env[name])
            and not (name in local_candidate_images and LOCAL_IMAGE_ID_RE.fullmatch(env[name]))
        ]
        if unpinned:
            fail(f"{environment} requires digest-pinned images: {', '.join(sorted(unpinned))}")


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
