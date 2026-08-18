#!/usr/bin/env python3
"""Fail closed before expensive self-hosted runner work without mutating state."""

from __future__ import annotations

import argparse
import shutil
import subprocess
import sys
from pathlib import Path
from typing import Callable

GIB = 1024**3


class PreflightError(RuntimeError):
    """Raised when a required runner precondition is unavailable."""


def _run_checked(command: list[str]) -> None:
    try:
        result = subprocess.run(
            command,
            check=False,
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
            timeout=20,
        )
    except (OSError, subprocess.TimeoutExpired) as error:
        raise PreflightError(f"required command unavailable: {command[0]}") from error
    if result.returncode != 0:
        raise PreflightError(f"required runtime check failed: {' '.join(command)}")


def run_preflight(
    path: Path,
    *,
    minimum_free_gib: int,
    require_docker: bool,
    disk_usage: Callable[[Path], object] = shutil.disk_usage,
    command_check: Callable[[list[str]], None] = _run_checked,
) -> int:
    if minimum_free_gib <= 0:
        raise PreflightError("minimum free GiB must be positive")
    resolved = path.resolve(strict=True)
    usage = disk_usage(resolved)
    free_bytes = int(getattr(usage, "free"))
    minimum_bytes = minimum_free_gib * GIB
    if free_bytes < minimum_bytes:
        raise PreflightError(
            f"free space {free_bytes // GIB} GiB is below required {minimum_free_gib} GiB"
        )
    if require_docker:
        command_check(["docker", "info"])
        command_check(["docker", "compose", "version"])
    return free_bytes


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--path", required=True)
    parser.add_argument("--minimum-free-gib", required=True, type=int)
    parser.add_argument("--require-docker", action="store_true")
    args = parser.parse_args()
    try:
        free_bytes = run_preflight(
            Path(args.path),
            minimum_free_gib=args.minimum_free_gib,
            require_docker=args.require_docker,
        )
    except (OSError, PreflightError, ValueError) as error:
        print(f"runner-capacity-preflight: {error}", file=sys.stderr)
        return 1
    print(
        "RUNNER_CAPACITY_PREFLIGHT "
        f"status=passed freeGiB={free_bytes // GIB} "
        f"minimumGiB={args.minimum_free_gib} "
        f"dockerRequired={str(args.require_docker).lower()} supportSafe=true"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
