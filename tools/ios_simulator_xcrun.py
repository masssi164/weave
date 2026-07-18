#!/usr/bin/env python3
"""Bound Flutter iOS Simulator VM-service discovery on unreliable Xcode logs."""

from __future__ import annotations

import json
import os
import re
import signal
import subprocess
import sys
import threading
import time
from collections.abc import Callable, Sequence
from datetime import datetime
from pathlib import Path
from typing import TextIO
from urllib.parse import urlparse


REAL_XCRUN = Path("/usr/bin/xcrun")
REAL_PS = Path("/bin/ps")
POLL_SECONDS = 0.5
RUNNER_DEADLINE_SECONDS = 60.0
VM_EVENT_DEADLINE_SECONDS = 45.0
SUPPORT_SAFE_ERROR = "WEAVE_IOS_VM_SERVICE_DISCOVERY_ERROR"
VM_EVENT_PREFIX = "flutter: The Dart VM service is listening on "
SIMULATOR_UDID = re.compile(r"^[0-9A-Fa-f]{8}(?:-[0-9A-Fa-f]{4}){3}-[0-9A-Fa-f]{12}$")
EVENT_MESSAGE = re.compile(r'"eventMessage"\s*:\s*("(?:\\.|[^"\\])*")')


def is_simulator_log_stream(arguments: Sequence[str]) -> bool:
    """Return whether this is Flutter's simulator unified-log stream command."""

    try:
        style_index = arguments.index("--style")
        predicate_index = arguments.index("--predicate")
        style = arguments[style_index + 1]
        predicate = arguments[predicate_index + 1]
    except (ValueError, IndexError):
        return False
    return (
        len(arguments) >= 7
        and list(arguments[:2]) == ["simctl", "spawn"]
        and SIMULATOR_UDID.fullmatch(arguments[2]) is not None
        and list(arguments[3:5]) == ["log", "stream"]
        and style == "json"
        and "eventType = logEvent" in predicate
        and 'processImagePath ENDSWITH "Runner"' in predicate
    )


def simulator_runner_pids(processes: str, simulator_udid: str) -> set[int]:
    """Select only Runner processes installed inside the exact simulator."""

    marker = f"/CoreSimulator/Devices/{simulator_udid}/"
    result: set[int] = set()
    for line in processes.splitlines():
        fields = line.strip().split(maxsplit=1)
        if len(fields) != 2 or not fields[0].isdigit():
            continue
        command = fields[1]
        if marker not in command:
            continue
        if re.search(r"/Runner\.app/Runner(?:\s|$)", command) is None:
            continue
        result.add(int(fields[0]))
    return result


def extract_vm_service_message(log_output: str) -> str | None:
    """Extract one strict loopback VM-service event without returning other logs."""

    messages: list[object] = []
    try:
        decoded = json.loads(log_output)
        if isinstance(decoded, list):
            messages.extend(item.get("eventMessage") for item in decoded if isinstance(item, dict))
        elif isinstance(decoded, dict):
            messages.append(decoded.get("eventMessage"))
    except json.JSONDecodeError:
        for match in EVENT_MESSAGE.finditer(log_output):
            try:
                messages.append(json.loads(match.group(1)))
            except json.JSONDecodeError:
                continue

    for candidate in reversed(messages):
        if not isinstance(candidate, str) or not candidate.startswith(VM_EVENT_PREFIX):
            continue
        raw_url = candidate.removeprefix(VM_EVENT_PREFIX).strip()
        parsed = urlparse(raw_url)
        try:
            port = parsed.port
        except ValueError:
            continue
        if (
            parsed.scheme != "http"
            or parsed.hostname != "127.0.0.1"
            or port is None
            or not 1 <= port <= 65535
            or parsed.username is not None
            or parsed.password is not None
            or parsed.query
            or parsed.fragment
            or not parsed.path.startswith("/")
            or len(parsed.path) > 512
        ):
            continue
        return candidate
    return None


def process_snapshot() -> str:
    try:
        completed = subprocess.run(
            [str(REAL_PS), "-axo", "pid=,command="],
            check=False,
            capture_output=True,
            text=True,
            timeout=5,
        )
    except (OSError, subprocess.TimeoutExpired):
        return ""
    return completed.stdout if completed.returncode == 0 else ""


def query_vm_service_event(
    simulator_udid: str,
    process_id: int,
    started_at: str,
) -> str | None:
    predicate = (
        f'processIdentifier == {process_id} AND '
        'eventMessage CONTAINS "Dart VM service is listening on"'
    )
    try:
        completed = subprocess.run(
            [
                str(REAL_XCRUN),
                "simctl",
                "spawn",
                simulator_udid,
                "log",
                "show",
                "--start",
                started_at,
                "--style",
                "json",
                "--predicate",
                predicate,
            ],
            check=False,
            capture_output=True,
            text=True,
            timeout=8,
        )
    except (OSError, subprocess.TimeoutExpired):
        return None
    if completed.returncode != 0:
        return None
    return extract_vm_service_message(completed.stdout)


def log_start_timestamp(now: datetime | None = None) -> str:
    """Format the local timestamp accepted by Apple's `log show --start`."""

    current = now if now is not None else datetime.now().astimezone()
    return current.strftime("%Y-%m-%d %H:%M:%S")


def replay_same_process_vm_event(
    simulator_udid: str,
    started_at: str,
    *,
    stop: threading.Event,
    snapshot: Callable[[], str] = process_snapshot,
    query: Callable[[str, int, str], str | None] = query_vm_service_event,
    monotonic: Callable[[], float] = time.monotonic,
    sleep: Callable[[float], None] = time.sleep,
    output: TextIO = sys.stdout,
    errors: TextIO = sys.stderr,
    runner_deadline_seconds: float = RUNNER_DEADLINE_SECONDS,
    event_deadline_seconds: float = VM_EVENT_DEADLINE_SECONDS,
) -> bool:
    """Replay the event from the exact simulator/new process or fail bounded."""

    runner_deadline = monotonic() + runner_deadline_seconds
    candidate_pids: set[int] = set()

    while not stop.is_set() and monotonic() < runner_deadline:
        candidate_pids = simulator_runner_pids(snapshot(), simulator_udid)
        if candidate_pids:
            break
        sleep(POLL_SECONDS)

    if stop.is_set():
        return True
    if not candidate_pids:
        print(
            f"{SUPPORT_SAFE_ERROR} phase=runner-process status=timeout supportSafe=true",
            file=errors,
            flush=True,
        )
        return False

    event_deadline = monotonic() + event_deadline_seconds
    while not stop.is_set() and monotonic() < event_deadline:
        current = simulator_runner_pids(snapshot(), simulator_udid)
        candidate_pids = current or candidate_pids
        for process_id in sorted(candidate_pids & current, reverse=True):
            message = query(simulator_udid, process_id, started_at)
            if message is None:
                continue
            # Flutter's parser expects the spacing emitted by `log --style json`.
            print(
                f'  "eventMessage" : {json.dumps(message)}',
                file=output,
                flush=True,
            )
            return True
        sleep(POLL_SECONDS)

    if stop.is_set():
        return True
    print(
        f"{SUPPORT_SAFE_ERROR} phase=vm-service-event status=timeout supportSafe=true",
        file=errors,
        flush=True,
    )
    return False


def terminate_process_group(process: subprocess.Popen[bytes]) -> None:
    if process.poll() is not None:
        return
    try:
        os.killpg(process.pid, signal.SIGTERM)
        process.wait(timeout=5)
    except (ProcessLookupError, subprocess.TimeoutExpired):
        try:
            os.killpg(process.pid, signal.SIGKILL)
        except ProcessLookupError:
            pass


def run_log_stream(arguments: Sequence[str]) -> int:
    simulator_udid = arguments[2]
    started_at = log_start_timestamp()
    stream = subprocess.Popen(
        [str(REAL_XCRUN), *arguments],
        start_new_session=True,
    )
    stop = threading.Event()
    outcome: list[bool] = []

    def watch() -> None:
        try:
            outcome.append(
                replay_same_process_vm_event(
                    simulator_udid,
                    started_at,
                    stop=stop,
                )
            )
        except Exception:
            print(
                f"{SUPPORT_SAFE_ERROR} phase=replay status=failed supportSafe=true",
                file=sys.stderr,
                flush=True,
            )
            outcome.append(False)

    watcher = threading.Thread(target=watch, name="weave-vm-service-replay", daemon=True)
    watcher.start()

    def stop_stream(signum: int, _frame: object) -> None:
        stop.set()
        terminate_process_group(stream)
        raise SystemExit(128 + signum)

    signal.signal(signal.SIGINT, stop_stream)
    signal.signal(signal.SIGTERM, stop_stream)

    try:
        while stream.poll() is None:
            if outcome and not outcome[0]:
                terminate_process_group(stream)
                return os.EX_IOERR
            time.sleep(0.1)
        return stream.returncode or 0
    finally:
        stop.set()
        terminate_process_group(stream)
        watcher.join(timeout=2)


def main(arguments: Sequence[str] | None = None) -> int:
    resolved = list(sys.argv[1:] if arguments is None else arguments)
    if not is_simulator_log_stream(resolved):
        os.execv(str(REAL_XCRUN), [str(REAL_XCRUN), *resolved])
    return run_log_stream(resolved)


if __name__ == "__main__":
    raise SystemExit(main())
