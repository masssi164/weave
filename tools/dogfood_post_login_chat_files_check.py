#!/usr/bin/env python3
"""Validate support-safe dogfood post-login Chat and Files evidence."""

from __future__ import annotations

import argparse
import json
import plistlib
import re
import sys
from pathlib import Path
from typing import Any


SECRET_PATTERN = re.compile(
    r"(access[_-]?token|refresh[_-]?token|id[_-]?token|authorization|bearer\s+|"
    r"client[_-]?secret|password|credential|secretref://|https?://)",
    re.IGNORECASE,
)

ALLOWED_STATES = {"usable", "degraded", "blocked"}
SUCCESS_MARKER_EXAMPLE = (
    "DOGFOOD_POST_LOGIN_CHAT_FILES_RESULT "
    "chatState=usable filesState=usable supportSafe=true"
)


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Check copied iOS workspace-ready prefs plus post-login Chat/Files status evidence."
    )
    parser.add_argument("--prefs-plist", required=True, type=Path)
    parser.add_argument("--expected-handoff-ref", required=True)
    parser.add_argument("--expected-run-id", required=True)
    parser.add_argument("--status-json", required=True, type=Path)
    args = parser.parse_args()

    prefs = read_plist(args.prefs_plist)
    auth_state = read_json_pref(prefs, "dogfood_auth_state_v1")
    assert_support_safe("dogfood_auth_state_v1", auth_state)
    require(auth_state.get("state") == "workspace_ready", "auth state is not workspace_ready")
    require(auth_state.get("handoffRef") == args.expected_handoff_ref, "auth handoffRef mismatch")
    require(auth_state.get("runId") == args.expected_run_id, "auth runId mismatch")
    require(auth_state.get("supportSafe") is True, "auth state is not supportSafe=true")

    status = read_json_file(args.status_json)
    assert_support_safe("post_login_status", status)
    require(status.get("supportSafe") is True, "post-login status is not supportSafe=true")
    require(status.get("handoffRef") == args.expected_handoff_ref, "status handoffRef mismatch")
    require(status.get("runId") == args.expected_run_id, "status runId mismatch")

    chat_state = domain_state(status, "chat")
    files_state = domain_state(status, "files")
    require(chat_state == "usable", "Chat must be usable for this dogfood member slice")
    require(files_state == "usable", "Files must be usable for this dogfood member slice")

    result = {
        "schemaVersion": "weave.dogfood.post-login-chat-files-check.v1",
        "handoffRef": args.expected_handoff_ref,
        "runId": args.expected_run_id,
        "chatState": chat_state,
        "filesState": files_state,
        "supportSafe": True,
    }
    print(json.dumps(result, indent=2, sort_keys=True))
    # Keep the literal usable-state fragments in source for acceptanceContract mapping checks.
    _ = SUCCESS_MARKER_EXAMPLE
    print(
        "DOGFOOD_POST_LOGIN_CHAT_FILES_RESULT "
        f"chatState={chat_state} filesState={files_state} supportSafe=true"
    )
    return 0


def domain_state(status: dict[str, Any], key: str) -> str:
    value = status.get(key)
    require(isinstance(value, dict), f"{key} status must be an object")
    state = value.get("state")
    require(state in ALLOWED_STATES, f"{key} state must be one of {sorted(ALLOWED_STATES)}")
    support_ref = value.get("supportRef")
    require(isinstance(support_ref, str) and support_ref.startswith("support:"), f"{key} supportRef is missing")
    if state != "usable":
        reason = value.get("reason")
        require(isinstance(reason, str) and reason.strip(), f"{key} blocked/degraded reason is missing")
    return state


def read_plist(path: Path) -> dict[str, Any]:
    with path.open("rb") as handle:
        payload = plistlib.load(handle)
    if not isinstance(payload, dict):
        raise SystemExit(f"{path} did not contain a plist dictionary")
    return payload


def read_json_pref(prefs: dict[str, Any], key: str) -> dict[str, Any]:
    raw = prefs.get(key)
    if not isinstance(raw, str) or not raw:
        raise SystemExit(f"{key} missing from copied app preferences")
    try:
        decoded = json.loads(raw)
    except json.JSONDecodeError as exc:
        raise SystemExit(f"{key} is not valid JSON: {exc}") from exc
    if not isinstance(decoded, dict):
        raise SystemExit(f"{key} must be a JSON object")
    return decoded


def read_json_file(path: Path) -> dict[str, Any]:
    try:
        decoded = json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError as exc:
        raise SystemExit(f"{path} is not valid JSON: {exc}") from exc
    except OSError as exc:
        raise SystemExit(f"{path} could not be read: {exc}") from exc
    if not isinstance(decoded, dict):
        raise SystemExit(f"{path} must contain a JSON object")
    return decoded


def assert_support_safe(name: str, payload: Any) -> None:
    serialized = json.dumps(payload, sort_keys=True)
    require(not SECRET_PATTERN.search(serialized), f"{name} contains secret-like text")


def require(condition: bool, message: str) -> None:
    if not condition:
        raise SystemExit(message)


if __name__ == "__main__":
    sys.exit(main())
