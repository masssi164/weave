#!/usr/bin/env python3
"""Run one command in a bounded process group without disclosing its arguments."""

from __future__ import annotations

import argparse
import os
import signal
import subprocess
import sys
import time
from pathlib import Path
from typing import Mapping, Sequence


TIMEOUT_EXIT_STATUS = 124
TIMEOUT_MARKER = "WEAVE_BOUNDED_PROCESS_TIMEOUT"
FAILURE_EXIT_STATUS = 125
FAILURE_MARKER = "WEAVE_BOUNDED_PROCESS_FAILED"
TERMINATION_GRACE_SECONDS = 2


class BoundedProcessTimeout(RuntimeError):
    """Fixed, support-safe indication that a process group exceeded its budget."""


def _terminate_group(process: subprocess.Popen[str]) -> None:
    try:
        os.killpg(process.pid, signal.SIGTERM)
    except ProcessLookupError:
        return
    # Keep the root unreaped during the grace window so its process-group ID
    # cannot be reused while descendants are still shutting down.
    time.sleep(TERMINATION_GRACE_SECONDS)
    try:
        os.killpg(process.pid, signal.SIGKILL)
    except ProcessLookupError:
        pass
    try:
        process.wait(timeout=TERMINATION_GRACE_SECONDS)
    except subprocess.TimeoutExpired:
        # The operating system owns final reaping after the bounded caller exits.
        pass


def run_bounded(
    command: Sequence[str],
    timeout_seconds: float,
    *,
    cwd: Path | str | None = None,
    env: Mapping[str, str] | None = None,
    capture_output: bool = False,
) -> subprocess.CompletedProcess[str]:
    if not command:
        raise ValueError("bounded command is required")
    if timeout_seconds <= 0:
        raise ValueError("bounded timeout must be positive")
    process = subprocess.Popen(
        list(command),
        cwd=cwd,
        env=None if env is None else dict(env),
        text=True,
        stdout=subprocess.PIPE if capture_output else None,
        stderr=subprocess.PIPE if capture_output else None,
        start_new_session=True,
    )
    try:
        stdout, stderr = process.communicate(timeout=timeout_seconds)
    except subprocess.TimeoutExpired as error:
        _terminate_group(process)
        raise BoundedProcessTimeout(TIMEOUT_MARKER) from error
    return subprocess.CompletedProcess(
        list(command), process.returncode, stdout, stderr
    )


def parse_args(argv: list[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--timeout-seconds", required=True, type=float)
    parser.add_argument("command", nargs=argparse.REMAINDER)
    args = parser.parse_args(argv)
    if args.command and args.command[0] == "--":
        args.command = args.command[1:]
    if not args.command:
        parser.error("a command is required after --")
    return args


def main(argv: list[str]) -> int:
    args = parse_args(argv)
    try:
        return run_bounded(args.command, args.timeout_seconds).returncode
    except BoundedProcessTimeout:
        print(TIMEOUT_MARKER, file=sys.stderr)
        return TIMEOUT_EXIT_STATUS
    except (OSError, ValueError):
        print(FAILURE_MARKER, file=sys.stderr)
        return FAILURE_EXIT_STATUS


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
