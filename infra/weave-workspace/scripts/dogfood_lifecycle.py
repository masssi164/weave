#!/usr/bin/env python3
"""Operate the resettable Dogfood stack from verified hosted artifacts."""

from __future__ import annotations

import argparse
import os
import re
import subprocess
import sys
from pathlib import Path

from compose_env import ContractError, load_context
from compose_runtime import execute


def effective_reviewed_env_file(value: Path | None = None) -> Path:
    selected = (value if value is not None else Path.home() / ".weave/dogfood/reviewed-compose.env").expanduser()
    if any(path.is_symlink() for path in (selected, *selected.parents)):
        raise ContractError("reviewed Dogfood environment path must not contain symlinks")
    return selected


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


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("operation", choices=("up", "down", "reset", "bootstrap-owner", "bootstrap-space"))
    parser.add_argument("--root", type=Path, required=True)
    parser.add_argument("--env-file", type=Path)
    args, operation_arguments = parser.parse_known_args()
    root = args.root.resolve()
    repository = root.parents[1]
    env_file = effective_reviewed_env_file(args.env_file)
    os.environ["WEAVE_ENV_FILE"] = str(env_file)
    context = load_context("dogfood", root, str(env_file))

    if args.operation == "down":
        if operation_arguments:
            raise ContractError("dogfoodDown does not accept operation arguments")
        execute(context, "down", ["--remove-orphans"])
        print("WEAVE_DOGFOOD_DOWN_RESULT sessionVolumes=preserved tls=preserved")
        return 0

    commit = _exact_checkout(repository)
    sys.path.insert(0, str(repository / "tools"))
    from artifact_consumers import load_runtime_images
    # Deployment consumes immutable hosted output. A missing package is a
    # preparation failure, never an implicit build on the single UI runner.
    os.environ.update(load_runtime_images(commit))
    context = load_context("dogfood", root, str(env_file))
    operation_arguments = list(operation_arguments)
    if operation_arguments[:1] == ["--"]:
        operation_arguments.pop(0)
    if args.operation not in {"bootstrap-owner", "bootstrap-space"} and operation_arguments:
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
