#!/usr/bin/env python3
"""Build and operate the resettable local dogfood Compose stack."""

from __future__ import annotations

import argparse
import hashlib
import os
import re
import subprocess
from pathlib import Path

from build_keycloak_image import STOCK_KEYCLOAK_REFERENCE
from compose_env import ContractError, LOCAL_IMAGE_ID_RE, PUBLISHED_DIGEST_IMAGE_RE, load_context
from compose_runtime import execute

DOGFOOD_APPLICATION_PLATFORM = "linux/amd64"


def _run(command: list[str], *, cwd: Path, capture: bool = False) -> str:
    completed = subprocess.run(
        command,
        cwd=cwd,
        check=True,
        text=True,
        stdout=subprocess.PIPE if capture else None,
    )
    return completed.stdout.strip() if capture else ""


def _exact_checkout(repository: Path) -> str:
    commit = _run(["git", "rev-parse", "HEAD"], cwd=repository, capture=True)
    if not re.fullmatch(r"[0-9a-f]{40}", commit):
        raise ContractError("dogfood requires one exact Git checkout")
    dirty = _run(
        ["git", "status", "--porcelain=v1", "--untracked-files=all"],
        cwd=repository,
        capture=True,
    )
    if dirty:
        raise ContractError("dogfoodUp and dogfoodReset require a clean exact checkout")
    return commit


def _image_id(reference: str, repository: Path) -> str:
    image = _run(
        ["docker", "image", "inspect", reference, "--format", "{{.Id}}"],
        cwd=repository,
        capture=True,
    )
    if not LOCAL_IMAGE_ID_RE.fullmatch(image):
        raise ContractError(f"Docker did not resolve an exact local image ID for {reference}")
    return image


def _resolve_keycloak(reference: str, repository: Path) -> str:
    if PUBLISHED_DIGEST_IMAGE_RE.fullmatch(reference):
        _run(["docker", "pull", reference], cwd=repository)
        return _image_id(reference, repository)
    if LOCAL_IMAGE_ID_RE.fullmatch(reference):
        available = subprocess.run(
            ["docker", "image", "inspect", reference],
            cwd=repository,
            check=False,
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
        )
        if available.returncode == 0:
            return reference
        # A local image ID may disappear after an OrbStack reset. Weaver is
        # opt-in, so the immutable stock 26.7.1 runtime is the correct native
        # dogfood fallback and needs no downstream registration executor.
        _run(["docker", "pull", STOCK_KEYCLOAK_REFERENCE], cwd=repository)
        return _image_id(STOCK_KEYCLOAK_REFERENCE, repository)
    raise ContractError("dogfood Keycloak must use a registry digest or exact local image ID")


def _build_application_images(repository: Path, commit: str) -> tuple[str, str]:
    lock = repository / "specs/weave-specs.lock.json"
    spec_digest = "sha256:" + hashlib.sha256(lock.read_bytes()).hexdigest()
    # Use immutable source metadata so repeated dogfoodUp calls resolve the
    # same image ID instead of recreating healthy containers for a wall-clock
    # label change.
    created = _run(
        ["git", "show", "-s", "--format=%cI", commit],
        cwd=repository,
        capture=True,
    )
    if not re.fullmatch(r"\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}[+-]\d{2}:\d{2}", created):
        raise ContractError("dogfood requires one exact Git commit timestamp")
    common = [
        "--build-arg",
        f"WEAVE_IMAGE_CREATED={created}",
        "--build-arg",
        f"WEAVE_IMAGE_REVISION={commit}",
        "--build-arg",
        f"WEAVE_IMAGE_VERSION=dogfood-{commit[:12]}",
        "--build-arg",
        f"WEAVE_SPEC_DIGEST={spec_digest}",
        "--build-arg",
        "WEAVE_SBOM_REFERENCE=local-dogfood-not-published",
        "--build-arg",
        "WEAVE_PROVENANCE_REFERENCE=local-dogfood-not-published",
    ]
    server_tag = f"weave-backend:dogfood-{commit[:12]}"
    mcp_tag = f"weave-mcp-server:dogfood-{commit[:12]}"
    _run(
        [
            "docker",
            "build",
            "--platform",
            DOGFOOD_APPLICATION_PLATFORM,
            *common,
            "--tag",
            server_tag,
            "--file",
            str(repository / "server/Dockerfile"),
            str(repository),
        ],
        cwd=repository,
    )
    _run(
        [
            "docker",
            "build",
            "--platform",
            DOGFOOD_APPLICATION_PLATFORM,
            *common,
            "--tag",
            mcp_tag,
            "--file",
            str(repository / "weave-mcp-server/Dockerfile"),
            str(repository),
        ],
        cwd=repository,
    )
    return _image_id(server_tag, repository), _image_id(mcp_tag, repository)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("operation", choices=("up", "down", "reset", "bootstrap-owner"))
    parser.add_argument("--root", type=Path, required=True)
    parser.add_argument("--env-file", type=Path)
    args, operation_arguments = parser.parse_known_args()
    root = args.root.resolve()
    repository = root.parents[1]
    env_file = (
        args.env_file.expanduser().resolve()
        if args.env_file
        else Path.home() / ".weave/dogfood/reviewed-compose.env"
    )
    os.environ["WEAVE_ENV_FILE"] = str(env_file)
    context = load_context("dogfood", root, str(env_file))

    if args.operation == "down":
        if operation_arguments:
            raise ContractError("dogfoodDown does not accept operation arguments")
        execute(context, "down", ["--remove-orphans"])
        print("WEAVE_DOGFOOD_DOWN_RESULT sessionVolumes=preserved tls=preserved")
        return 0

    commit = _exact_checkout(repository)
    keycloak_image = _resolve_keycloak(context.env["WEAVE_KEYCLOAK_IMAGE"], repository)
    backend_image, mcp_image = _build_application_images(repository, commit)
    os.environ["WEAVE_KEYCLOAK_IMAGE"] = keycloak_image
    os.environ["WEAVE_BACKEND_IMAGE"] = backend_image
    os.environ["WEAVE_MCP_IMAGE"] = mcp_image
    context = load_context("dogfood", root, str(env_file))
    operation_arguments = list(operation_arguments)
    if operation_arguments[:1] == ["--"]:
        operation_arguments.pop(0)
    if args.operation != "bootstrap-owner" and operation_arguments:
        raise ContractError(f"dogfood {args.operation} does not accept operation arguments")
    execute(context, args.operation, operation_arguments)
    print(
        "WEAVE_DOGFOOD_LIFECYCLE_RESULT "
        f"operation={args.operation} candidateCommit={commit} "
        "sessionVolumes=postgres,native-files,mailpit tls=preserved"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
