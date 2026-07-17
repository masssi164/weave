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
    "room-author-discovery",
    "room-author-session-exchange",
    "room-author-bootstrap",
    "room-collaborator-discovery",
    "room-collaborator-session-exchange",
    "room-collaborator-bootstrap",
    "room-author-chat-connect",
    "room-collaborator-chat-connect",
    "room-transport-credentials",
    "room-device-provision",
    "room-conversation-sync",
    "room-key-exchange-author",
    "room-key-exchange-collaborator",
    "room-key-exchange-author-send",
    "room-key-exchange-author-self-observe",
    "room-key-exchange-collaborator-observe-author",
    "room-key-exchange-collaborator-observed-author",
    "room-key-exchange-collaborator-send",
    "room-key-exchange-collaborator-self-observe",
    "room-key-exchange-author-observe-collaborator",
    "room-key-exchange-author-observed-collaborator",
    "home-baseline",
    "author-write",
    "author-capabilities",
    "author-profile",
    "author-chat-connect",
    "author-chat-room",
    "author-chat-send",
    "author-chat-observe",
    "author-files-connect",
    "author-files-upload",
    "author-calendar-scopes",
    "author-calendar-create",
    "collaborator-observe",
    "collaborator-capabilities",
    "collaborator-profile",
    "collaborator-chat-connect",
    "collaborator-chat-room",
    "collaborator-chat-observe",
    "collaborator-chat-send",
    "collaborator-files-connect",
    "collaborator-files-observe",
    "collaborator-files-update",
    "collaborator-calendar-scopes",
    "collaborator-calendar-observe",
    "collaborator-calendar-update",
    "outsider-authorization",
    "fresh-session-observation",
    "resource-cleanup",
    "independent-logout",
    "author-navigation",
    "collaborator-navigation",
    "collaboration-evidence",
    "containment-session",
    "containment-discovery",
    "containment-session-exchange",
    "containment-bootstrap",
    "containment-capability",
    "containment-calendar-health",
    "containment-shell-health",
    "containment-chat-health",
    "containment-files-health",
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
        "event-id-mismatch",
        re.compile(
            r"resolved a different encrypted Chat event",
            re.IGNORECASE,
        ),
    ),
    (
        "message-not-observed",
        re.compile(
            r"committed Chat message was not observed|M_WEAVE_E2EE_MESSAGE_NOT_OBSERVED",
            re.IGNORECASE,
        ),
    ),
    (
        "native-process",
        re.compile(
            r"lost connection to device|service protocol connection closed|"
            r"process.*(?:sigabrt|sigsegv)|rust panicked|fatal signal",
            re.IGNORECASE,
        ),
    ),
    (
        "test-timeout",
        re.compile(
            r"test timed out after|TimeoutException after",
            re.IGNORECASE,
        ),
    ),
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
SAFE_E2EE_SUPPORT_CODES = (
    "M_FORBIDDEN",
    "M_MISSING_TOKEN",
    "M_UNKNOWN_TOKEN",
    "M_WEAVE_E2EE_MEMBER_KEYS",
    "M_WEAVE_E2EE_NOT_INITIALIZED",
    "M_WEAVE_E2EE_OLM_DECRYPTION_FAILURE",
    "M_WEAVE_E2EE_OLM_SENDER_NOT_TRUSTED",
    "M_WEAVE_E2EE_OLM_UNAVAILABLE",
    "M_WEAVE_E2EE_PEER_DEVICE_BLOCKED",
    "M_WEAVE_E2EE_PEER_DEVICE_INVALID",
    "M_WEAVE_E2EE_PEER_DEVICE_PENDING",
    "M_WEAVE_E2EE_PEER_DEVICE_REJECTED",
    "M_WEAVE_E2EE_ROOM_KEY_ROTATION",
    "M_WEAVE_E2EE_ROOM_MEMBERS",
    "M_WEAVE_E2EE_ROOM_STATE",
    "M_WEAVE_E2EE_SEND",
    "M_WEAVE_E2EE_SYNC",
    "M_WEAVE_E2EE_TIMELINE",
    "M_WEAVE_E2EE_TO_DEVICE_INVALID",
    "M_WEAVE_E2EE_ROOM_KEY_NOT_RECEIVED",
    "M_WEAVE_E2EE_ROOM_KEY_NOT_IMPORTED",
    "M_WEAVE_E2EE_MISSING_MEGOLM_SESSION",
    "M_WEAVE_E2EE_MISMATCHED_IDENTITY_KEYS",
    "M_WEAVE_E2EE_SENDER_NOT_TRUSTED",
    "M_WEAVE_E2EE_UNABLE_TO_DECRYPT",
    "M_WEAVE_E2EE_MESSAGE_NOT_OBSERVED",
    "M_WEAVE_E2EE_DEVICE_EXCHANGE_FAILED",
)
SAFE_E2EE_DIAGNOSTIC_CODES = SAFE_E2EE_SUPPORT_CODES + (
    "M_WEAVE_E2EE_DIAGNOSTICS_UNAVAILABLE",
)
E2EE_SUPPORT_CODE_PATTERN = re.compile(
    rf"Failure code:\s*({'|'.join(SAFE_E2EE_SUPPORT_CODES)})(?:\.|\s|$)"
)
E2EE_DIAGNOSTIC_FIELDS = (
    "eventCount",
    "decryptedCount",
    "unableToDecryptCount",
    "toDeviceDecryptedCount",
    "toDeviceRoomKeyCount",
    "toDeviceForwardedRoomKeyCount",
    "toDeviceOtherCount",
    "toDeviceUnknownTypeCount",
    "toDeviceUnableToDecryptCount",
    "toDevicePlaintextCount",
    "toDeviceInvalidCount",
    "joinedPeerCount",
    "authoritativeDeviceCount",
    "sdkDeviceCount",
    "sdkUsableDeviceCount",
    "sdkDeletedDeviceCount",
    "sdkBlacklistedDeviceCount",
    "sdkMissingCurve25519Count",
    "sdkMissingAuthoritativeDeviceCount",
    "sdkUnexpectedDeviceCount",
    "deviceQueryAttemptCount",
    "convergedPeerCount",
    "pendingPeerCount",
    "rejectedPeerCount",
    "blockedPeerCount",
    "invalidPeerCount",
)
E2EE_DIAGNOSTIC_PATTERN = re.compile(
    r"(?:^|\s)MULTI_USER_E2EE_DIAGNOSTIC "
    r"role=(author|collaborator) runIndex=(\d+) available=([01]) "
    + r" ".join(rf"{field}=(\d+)" for field in E2EE_DIAGNOSTIC_FIELDS)
    + r"\s*$"
)
E2EE_CRYPTO_DIAGNOSTIC_FIELDS = (
    "tdDec",
    "tdKey",
    "tdUtd",
    "tdFail",
    "tdUnverified",
)
E2EE_CRYPTO_DIAGNOSTIC_PATTERN = re.compile(
    r"(?:^|\s)MULTI_USER_E2EE_CRYPTO_DIAGNOSTIC "
    r"role=(author|collaborator) runIndex=(\d+) available=([01]) "
    rf"supportCode=({'|'.join(SAFE_E2EE_DIAGNOSTIC_CODES)}) "
    + r" ".join(rf"{field}=(\d+)" for field in E2EE_CRYPTO_DIAGNOSTIC_FIELDS)
    + r"\s*$"
)
E2EE_EVENT_ID_MISMATCH_FIELDS = (
    "sameSender",
    "sameTimestamp",
    "expectedLength",
    "observedLength",
    "transportAvailable",
    "authorHasExpected",
    "authorHasObserved",
    "collaboratorHasExpected",
    "collaboratorHasObserved",
)
E2EE_EVENT_ID_MISMATCH_PATTERN = re.compile(
    r"(?:^|\s)MULTI_USER_E2EE_EVENT_ID_MISMATCH "
    r"direction=(author-to-collaborator|collaborator-to-author) "
    r"runIndex=(\d+) expectedHash=([0-9a-f]{16}) observedHash=([0-9a-f]{16}) "
    + r" ".join(rf"{field}=(\d+)" for field in E2EE_EVENT_ID_MISMATCH_FIELDS)
    + r"\s*$"
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


def _e2ee_support_code(raw_log: str) -> str | None:
    matches = E2EE_SUPPORT_CODE_PATTERN.findall(raw_log)
    return matches[-1] if matches else None


def _e2ee_diagnostics(raw_log: str, run_index: int) -> list[str]:
    diagnostics: list[str] = []
    for line in raw_log.splitlines():
        match = E2EE_DIAGNOSTIC_PATTERN.search(line)
        if match is None or int(match.group(2)) != run_index:
            continue
        role = match.group(1)
        available = match.group(3)
        counts = " ".join(
            f"{field}={match.group(index + 4)}"
            for index, field in enumerate(E2EE_DIAGNOSTIC_FIELDS)
        )
        diagnostics.append(
            "SANITIZED_MULTI_USER_E2EE_DIAGNOSTIC "
            f"role={role} runIndex={run_index} available={available} "
            f"{counts} supportSafe=true"
        )
    return diagnostics[-2:]


def _e2ee_crypto_diagnostics(raw_log: str, run_index: int) -> list[str]:
    diagnostics: list[str] = []
    for line in raw_log.splitlines():
        match = E2EE_CRYPTO_DIAGNOSTIC_PATTERN.search(line)
        if match is None or int(match.group(2)) != run_index:
            continue
        counts = " ".join(
            f"{field}={match.group(index + 5)}"
            for index, field in enumerate(E2EE_CRYPTO_DIAGNOSTIC_FIELDS)
        )
        diagnostics.append(
            "SANITIZED_MULTI_USER_E2EE_CRYPTO_DIAGNOSTIC "
            f"role={match.group(1)} runIndex={run_index} "
            f"available={match.group(3)} supportCode={match.group(4)} "
            f"{counts} supportSafe=true"
        )
    return diagnostics[-2:]


def _e2ee_event_id_mismatches(raw_log: str, run_index: int) -> list[str]:
    diagnostics: list[str] = []
    for line in raw_log.splitlines():
        match = E2EE_EVENT_ID_MISMATCH_PATTERN.search(line)
        if match is None or int(match.group(2)) != run_index:
            continue
        counts = " ".join(
            f"{field}={match.group(index + 5)}"
            for index, field in enumerate(E2EE_EVENT_ID_MISMATCH_FIELDS)
        )
        diagnostics.append(
            "SANITIZED_MULTI_USER_E2EE_EVENT_ID_MISMATCH "
            f"direction={match.group(1)} runIndex={run_index} "
            f"expectedHash={match.group(3)} observedHash={match.group(4)} "
            f"{counts} supportSafe=true"
        )
    return diagnostics[-2:]


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

    for diagnostic in _e2ee_crypto_diagnostics(raw_log, args.run_index):
        print(diagnostic)

    for diagnostic in _e2ee_diagnostics(raw_log, args.run_index):
        print(diagnostic)

    for diagnostic in _e2ee_event_id_mismatches(raw_log, args.run_index):
        print(diagnostic)

    test_status = "passed" if args.test_exit_code == 0 else "failed"
    if args.test_exit_code != 0:
        category, signature = _failure_diagnostic(raw_log)
        support_code = _e2ee_support_code(raw_log)
        support_code_field = f" supportCode={support_code}" if support_code else ""
        print(
            "SANITIZED_MULTI_USER_FAILURE "
            f"status=failed runIndex={args.run_index} category={category} "
            f"phase={progress_phase} signatureHash={signature}"
            f"{support_code_field} supportSafe=true"
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
