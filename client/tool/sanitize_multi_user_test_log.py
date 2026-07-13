#!/usr/bin/env python3
"""Emit only allowlisted support-safe evidence from a live multi-user log."""

from __future__ import annotations

import argparse
import hashlib
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
PROGRESS_PHASES = (
    "room-provision",
    "home-baseline",
    "author-write",
    "collaborator-observe",
    "outsider-authorization",
    "fresh-session-observation",
    "resource-cleanup",
    "independent-logout",
    "author-navigation",
    "collaborator-navigation",
    "collaboration-evidence",
    "containment-session",
    "containment-capability",
    "containment-navigation",
    "containment-calendar",
    "containment-evidence",
)
PROGRESS_PATTERN = re.compile(
    rf"(?:^|\s)MULTI_USER_PROGRESS "
    rf"phase=({'|'.join(PROGRESS_PHASES)}) runIndex=(\d+)\s*$"
)
FAILURE_CATEGORIES = (
    (
        "compilation",
        re.compile(
            r"compilation failed|failed to build|target .* failed|\berror:\s",
            re.IGNORECASE,
        ),
    ),
    (
        "configuration",
        re.compile(
            r"missing .*dart-define|requires .*credential|invalid test configuration",
            re.IGNORECASE,
        ),
    ),
    (
        "authentication",
        re.compile(
            r"oidc|sign[ -]?in|authorization code|access token|login",
            re.IGNORECASE,
        ),
    ),
    (
        "timeout",
        re.compile(r"timed? out|timeoutexception", re.IGNORECASE),
    ),
    (
        "transport",
        re.compile(
            r"socketexception|handshakeexception|connection refused|failed host lookup",
            re.IGNORECASE,
        ),
    ),
    (
        "assertion",
        re.compile(r"testfailure|expected:|actual:|test failed", re.IGNORECASE),
    ),
)
SECRET_VALUE_PATTERN = re.compile(
    r"(?i)(access[_-]?token|refresh[_-]?token|id[_-]?token|password|client[_-]?secret)"
    r"(\s*[=:]\s*)([^\s,;&]+)"
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


def _failure_diagnostic(raw_log: str) -> tuple[str, str]:
    category = "unknown"
    for candidate, pattern in FAILURE_CATEGORIES:
        if pattern.search(raw_log):
            category = candidate
            break

    normalized = SECRET_VALUE_PATTERN.sub(r"\1\2<redacted>", raw_log)
    normalized = re.sub(r"https?://[^\s\"'<>]+", "<url>", normalized)
    normalized = re.sub(
        r"\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}\b",
        "<email>",
        normalized,
        flags=re.IGNORECASE,
    )
    normalized = re.sub(
        r"(?:/[A-Za-z0-9_.-]+){2,}",
        "<path>",
        normalized,
    )
    normalized = re.sub(r"\b[A-Za-z0-9_+/=-]{24,}\b", "<opaque>", normalized)
    normalized = re.sub(r"\d+", "#", normalized)
    normalized = re.sub(r"\s+", " ", normalized).strip()
    signature = hashlib.sha256(
        f"{category}:{normalized}".encode("utf-8")
    ).hexdigest()
    return category, signature


def _last_progress_phase(raw_log: str, run_index: int) -> str:
    phase = "unknown"
    for line in raw_log.splitlines():
        match = PROGRESS_PATTERN.search(line)
        if match is not None and int(match.group(2)) == run_index:
            phase = match.group(1)
    return phase


def main() -> int:
    args = _parse_args()
    raw_log = args.input.read_text(encoding="utf-8", errors="replace")
    progress_phase = _last_progress_phase(raw_log, args.run_index)
    safe_markers: list[tuple[str, dict[str, object]]] = []
    invalid_marker_count = 0
    for line in raw_log.splitlines():
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
    if args.test_exit_code != 0:
        category, signature = _failure_diagnostic(raw_log)
        print(
            "SANITIZED_MULTI_USER_FAILURE "
            f"status=failed runIndex={args.run_index} category={category} "
            f"phase={progress_phase} signatureHash={signature} supportSafe=true"
        )
    validation_status = "passed" if invalid_marker_count == 0 else "failed"
    print(
        "SANITIZED_MULTI_USER_TEST_RESULT "
        f"status={test_status} runIndex={args.run_index} "
        f"markerCount={len(safe_markers)} validation={validation_status}"
    )
    return 0 if invalid_marker_count == 0 else 2


if __name__ == "__main__":
    raise SystemExit(main())
