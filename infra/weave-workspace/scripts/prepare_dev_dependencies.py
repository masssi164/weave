#!/usr/bin/env python3
"""Converge the fast local provider stack for host-run Server and MCP."""

from __future__ import annotations

import argparse
import os
import re
import subprocess
from pathlib import Path

from compose_env import ContractError, load_context
from compose_runtime import compose, execute


IMAGE_ID = re.compile(r"^sha256:[0-9a-f]{64}$")


def _exact_image(reference: str) -> str:
    inspected = subprocess.run(
        ["docker", "image", "inspect", reference, "--format", "{{.Id}}"],
        check=False,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.DEVNULL,
    )
    if inspected.returncode != 0:
        subprocess.run(["docker", "pull", reference], check=True)
        inspected = subprocess.run(
            ["docker", "image", "inspect", reference, "--format", "{{.Id}}"],
            check=True,
            text=True,
            stdout=subprocess.PIPE,
        )
    image = inspected.stdout.strip()
    if not IMAGE_ID.fullmatch(image):
        raise ContractError("the exact dev Keycloak image ID is unavailable")
    return image


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, required=True)
    parser.add_argument("--env-file")
    args = parser.parse_args()
    root = args.root.resolve()

    # Dev intentionally uses the version-pinned stock Keycloak image. The
    # downstream Weaver registration policy is optional and therefore must not
    # block the native product development loop.
    context = load_context("dev", root, args.env_file)
    carrier_image = _exact_image(context.env["WEAVE_KEYCLOAK_IMAGE"])
    os.environ["WEAVE_KEYCLOAK_IMAGE"] = carrier_image
    context = load_context("dev", root, args.env_file)
    execute(context, "up", [])

    # Desired-state application containers are stopped metadata carriers for
    # devRun. Reuse the exact already-running Keycloak image so devUp does not
    # build throwaway Server/MCP images merely to expose environment/mounts.
    os.environ["WEAVE_BACKEND_IMAGE"] = carrier_image
    os.environ["WEAVE_MCP_IMAGE"] = carrier_image
    context = load_context("dev", root, args.env_file)
    compose(context, "up", "--no-start", "--no-deps", "backend", "mcp")
    print(
        "WEAVE_DEV_PREPARE_RESULT provider=keycloak applications=host "
        "desiredStateContainers=created supportSafe=true"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
