#!/usr/bin/env python3
from __future__ import annotations

import tempfile
import unittest
from collections import namedtuple
from pathlib import Path

from runner_capacity_preflight import GIB, PreflightError, run_preflight


Usage = namedtuple("Usage", "total used free")


class RunnerCapacityPreflightTest(unittest.TestCase):
    def test_passes_without_mutating_for_sufficient_capacity(self) -> None:
        commands: list[list[str]] = []
        with tempfile.TemporaryDirectory() as directory:
            free = run_preflight(
                Path(directory),
                minimum_free_gib=20,
                require_docker=True,
                disk_usage=lambda _: Usage(100 * GIB, 60 * GIB, 40 * GIB),
                command_check=commands.append,
            )
        self.assertEqual(free, 40 * GIB)
        self.assertEqual(
            commands, [["docker", "info"], ["docker", "compose", "version"]]
        )

    def test_fails_before_runtime_checks_when_space_is_low(self) -> None:
        commands: list[list[str]] = []
        with tempfile.TemporaryDirectory() as directory:
            with self.assertRaisesRegex(PreflightError, "below required 20 GiB"):
                run_preflight(
                    Path(directory),
                    minimum_free_gib=20,
                    require_docker=True,
                    disk_usage=lambda _: Usage(100 * GIB, 81 * GIB, 19 * GIB),
                    command_check=commands.append,
                )
        self.assertEqual(commands, [])

    def test_propagates_docker_or_compose_failure(self) -> None:
        def fail_compose(command: list[str]) -> None:
            if command == ["docker", "compose", "version"]:
                raise PreflightError("compose unavailable")

        with tempfile.TemporaryDirectory() as directory:
            with self.assertRaisesRegex(PreflightError, "compose unavailable"):
                run_preflight(
                    Path(directory),
                    minimum_free_gib=20,
                    require_docker=True,
                    disk_usage=lambda _: Usage(100 * GIB, 60 * GIB, 40 * GIB),
                    command_check=fail_compose,
                )

    def test_rejects_non_positive_threshold(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            with self.assertRaisesRegex(PreflightError, "must be positive"):
                run_preflight(
                    Path(directory), minimum_free_gib=0, require_docker=False
                )


if __name__ == "__main__":
    unittest.main()
