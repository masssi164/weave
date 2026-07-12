#!/usr/bin/env python3
"""Emit only allowlisted support-safe evidence from a live multi-user log."""

from __future__ import annotations

import argparse
import json
import re
from pathlib import Path


MARKERS = (
    "MULTI_USER_AUTH_SHELL_RESULT",
    "MULTI_USER_HOME_RESULT",
    "MULTI_USER_CHAT_RESULT",
    "MULTI_USER_FILES_RESULT",
    "MULTI_USER_CALENDAR_RESULT",
    "MULTI_USER_SETTINGS_PROFILE_RESULT",
    "MULTI_USER_FAILURE_CONTAINMENT_RESULT",
    "MULTI_USER_AUTHORIZATION_RESULT",
)
HASH_PATTERN = re.compile(r"^[0-9a-f]{16,64}$")
MARKER_PATTERN = re.compile(
    rf"(?:^|\s)({'|'.join(MARKERS)})\s+(\{{.*\}})\s*$"
)


def _parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", required=True, type=Path)
    parser.add_argument("--run-index", required=True, type=int)
    parser.add_argument("--test-exit-code", required=True, type=int)
    return parser.parse_args()


def _is_safe_payload(payload: object, run_index: int) -> bool:
    if not isinstance(payload, dict):
        return False
    if payload.get("runIndex") != run_index:
        return False
    for key, value in payload.items():
        if isinstance(value, bool):
            continue
        if isinstance(value, int) and not isinstance(value, bool):
            if key == "runIndex" or key.endswith("Count"):
                continue
            return False
        if isinstance(value, str):
            if key == "status" and value in {"passed", "blocked", "failed"}:
                continue
            if key.endswith("Hash") and HASH_PATTERN.fullmatch(value):
                continue
        return False
    return True


def main() -> int:
    args = _parse_args()
    safe_markers: list[tuple[str, dict[str, object]]] = []
    invalid_marker_count = 0
    for line in args.input.read_text(encoding="utf-8", errors="replace").splitlines():
        match = MARKER_PATTERN.search(line)
        if match is None:
            continue
        try:
            payload = json.loads(match.group(2))
        except json.JSONDecodeError:
            invalid_marker_count += 1
            continue
        if not _is_safe_payload(payload, args.run_index):
            invalid_marker_count += 1
            continue
        safe_markers.append((match.group(1), payload))

    for marker, payload in safe_markers:
        print(f"{marker} {json.dumps(payload, sort_keys=True, separators=(',', ':'))}")

    test_status = "passed" if args.test_exit_code == 0 else "failed"
    validation_status = "passed" if invalid_marker_count == 0 else "failed"
    print(
        "SANITIZED_MULTI_USER_TEST_RESULT "
        f"status={test_status} runIndex={args.run_index} "
        f"markerCount={len(safe_markers)} validation={validation_status}"
    )
    return 0 if invalid_marker_count == 0 else 2


if __name__ == "__main__":
    raise SystemExit(main())
