#!/usr/bin/env python3
"""Unit tests for the bounded iOS Simulator xcrun compatibility shim."""

from __future__ import annotations

import io
import json
import threading
import unittest
from datetime import datetime

import ios_simulator_xcrun as subject


UDID = "AA1201A0-38CC-4B6F-A2FD-7971FF654566"


def ps_line(process_id: int, udid: str = UDID) -> str:
    return (
        f"{process_id} /Users/runner/Library/Developer/CoreSimulator/Devices/"
        f"{udid}/data/Containers/Bundle/Application/APP/Runner.app/Runner\n"
    )


class IosSimulatorXcrunTest(unittest.TestCase):
    def test_formats_the_local_timestamp_required_by_apple_log(self) -> None:
        self.assertEqual(
            subject.log_start_timestamp(datetime(2026, 7, 16, 20, 1, 2)),
            "2026-07-16 20:01:02",
        )

    def test_intercepts_only_exact_simulator_json_log_stream(self) -> None:
        expected = [
            "simctl",
            "spawn",
            UDID,
            "log",
            "stream",
            "--style",
            "json",
            "--predicate",
            'eventType = logEvent AND processImagePath ENDSWITH "Runner"',
        ]
        self.assertTrue(subject.is_simulator_log_stream(expected))
        self.assertFalse(subject.is_simulator_log_stream(["simctl", "delete", UDID]))
        self.assertFalse(
            subject.is_simulator_log_stream(
                ["simctl", "spawn", "booted", "log", "stream", "--style", "json"]
            )
        )
        self.assertFalse(
            subject.is_simulator_log_stream(
                [
                    "simctl",
                    "spawn",
                    UDID,
                    "log",
                    "stream",
                    "--style",
                    "json",
                    "--predicate",
                    "eventType = logEvent",
                ]
            )
        )

    def test_runner_selection_is_scoped_to_exact_simulator_and_binary(self) -> None:
        processes = (
            ps_line(101)
            + ps_line(102, "BB1201A0-38CC-4B6F-A2FD-7971FF654566")
            + f"103 /tmp/{UDID}/NotRunner.app/NotRunner\n"
        )
        self.assertEqual(subject.simulator_runner_pids(processes, UDID), {101})

    def test_extracts_only_bounded_loopback_vm_service_event(self) -> None:
        valid = (
            "flutter: The Dart VM service is listening on "
            "http://127.0.0.1:56387/SJzCqoiFioM=/"
        )
        self.assertEqual(
            subject.extract_vm_service_message(json.dumps([{"eventMessage": valid}])),
            valid,
        )
        self.assertIsNone(
            subject.extract_vm_service_message(
                json.dumps(
                    [
                        {
                            "eventMessage": (
                                "flutter: The Dart VM service is listening on "
                                "http://192.168.1.10:56387/secret/"
                            )
                        }
                    ]
                )
            )
        )
        self.assertIsNone(
            subject.extract_vm_service_message(
                json.dumps([{"eventMessage": "unrelated provider payload"}])
            )
        )

    def test_replays_only_event_from_new_same_simulator_process(self) -> None:
        snapshots = iter([ps_line(100), ps_line(100) + ps_line(200), ps_line(200)])
        queries: list[tuple[str, int, str]] = []
        output = io.StringIO()
        errors = io.StringIO()
        clock = iter([0.0, 0.1, 0.2, 0.3])
        expected = (
            "flutter: The Dart VM service is listening on "
            "http://127.0.0.1:56387/SJzCqoiFioM=/"
        )

        def query(udid: str, process_id: int, started_at: str) -> str | None:
            queries.append((udid, process_id, started_at))
            return expected if process_id == 200 else None

        result = subject.replay_same_process_vm_event(
            UDID,
            "2026-07-16 20:00:00",
            stop=threading.Event(),
            snapshot=lambda: next(snapshots),
            query=query,
            monotonic=lambda: next(clock),
            sleep=lambda _seconds: None,
            output=output,
            errors=errors,
            runner_deadline_seconds=1,
            event_deadline_seconds=1,
        )

        self.assertTrue(result)
        self.assertEqual(queries, [(UDID, 200, "2026-07-16 20:00:00")])
        self.assertIn('"eventMessage" :', output.getvalue())
        self.assertIn(expected, json.loads("{" + output.getvalue().strip() + "}")["eventMessage"])
        self.assertEqual(errors.getvalue(), "")

    def test_fails_support_safely_when_new_runner_never_appears(self) -> None:
        output = io.StringIO()
        errors = io.StringIO()
        ticks = iter([0.0, 0.1, 1.1])
        result = subject.replay_same_process_vm_event(
            UDID,
            "2026-07-16 20:00:00",
            stop=threading.Event(),
            snapshot=lambda: "",
            monotonic=lambda: next(ticks),
            sleep=lambda _seconds: None,
            output=output,
            errors=errors,
            runner_deadline_seconds=1,
            event_deadline_seconds=1,
        )
        self.assertFalse(result)
        self.assertEqual(output.getvalue(), "")
        self.assertEqual(
            errors.getvalue(),
            "WEAVE_IOS_VM_SERVICE_DISCOVERY_ERROR "
            "phase=runner-process status=timeout supportSafe=true\n",
        )

    def test_fails_support_safely_when_vm_event_never_appears(self) -> None:
        output = io.StringIO()
        errors = io.StringIO()
        ticks = iter([0.0, 0.1, 0.2, 0.3, 1.3])
        result = subject.replay_same_process_vm_event(
            UDID,
            "2026-07-16 20:00:00",
            stop=threading.Event(),
            snapshot=lambda: ps_line(200),
            query=lambda _udid, _pid, _started_at: None,
            monotonic=lambda: next(ticks),
            sleep=lambda _seconds: None,
            output=output,
            errors=errors,
            runner_deadline_seconds=1,
            event_deadline_seconds=1,
        )
        self.assertFalse(result)
        self.assertEqual(output.getvalue(), "")
        self.assertEqual(
            errors.getvalue(),
            "WEAVE_IOS_VM_SERVICE_DISCOVERY_ERROR "
            "phase=vm-service-event status=timeout supportSafe=true\n",
        )


if __name__ == "__main__":
    unittest.main()
