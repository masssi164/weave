#!/usr/bin/env python3
"""Validate support-safe dogfood member onboarding evidence."""

from __future__ import annotations

import argparse
import json
import plistlib
import re
import sys
import urllib.request
from pathlib import Path
from typing import Any


SECRET_PATTERN = re.compile(
    r"(access[_-]?token|refresh[_-]?token|id[_-]?token|authorization|bearer\s+|"
    r"client[_-]?secret|password|credential|secretref://)",
    re.IGNORECASE,
)


def main() -> int:
    parser = argparse.ArgumentParser(
        description=(
            "Check copied iOS app preferences and optional Mailpit API evidence "
            "for the full dogfood member onboarding path."
        )
    )
    parser.add_argument("--prefs-plist", required=True, type=Path)
    parser.add_argument("--expected-handoff-ref", required=True)
    parser.add_argument("--expected-run-id", required=True)
    parser.add_argument("--required-auth-state", default="workspace_ready")
    parser.add_argument(
        "--mailpit-api",
        default="http://127.0.0.1:8025/api/v1/messages",
        help="Mailpit messages API URL. Use --skip-mailpit to skip.",
    )
    parser.add_argument("--skip-mailpit", action="store_true")
    args = parser.parse_args()

    prefs = read_plist(args.prefs_plist)
    handoff = read_json_pref(prefs, "last_handoff_consumed_v1")
    visible = read_json_pref(prefs, "dogfood_visible_state_v1")
    auth_state = read_json_pref(prefs, "dogfood_auth_state_v1")

    assert_support_safe("last_handoff_consumed_v1", handoff)
    assert_support_safe("dogfood_visible_state_v1", visible)
    assert_support_safe("dogfood_auth_state_v1", auth_state)

    require(handoff.get("result") == "saved_configuration", "handoff was not saved")
    require(visible.get("state") == "handoff_ready", "visible state is not handoff_ready")
    require(
        auth_state.get("state") == args.required_auth_state,
        f"auth state is not {args.required_auth_state}",
    )

    for name, payload in (
        ("handoff", handoff),
        ("visible", visible),
        ("auth_state", auth_state),
    ):
        require(
            payload.get("handoffRef") == args.expected_handoff_ref,
            f"{name} handoffRef mismatch",
        )
        require(payload.get("runId") == args.expected_run_id, f"{name} runId mismatch")
        require(payload.get("supportSafe") is True, f"{name} is not supportSafe=true")

    mailpit_count = None
    if not args.skip_mailpit:
        mailpit_count = mailpit_message_count(args.mailpit_api)
        require(mailpit_count > 0, "Mailpit did not report captured local mail")

    result = {
        "schemaVersion": "weave.dogfood.member-onboarding-evidence-check.v1",
        "handoffRef": args.expected_handoff_ref,
        "runId": args.expected_run_id,
        "visibleState": visible.get("state"),
        "authState": auth_state.get("state"),
        "mailpitMessageCount": mailpit_count,
        "supportSafe": True,
    }
    print(json.dumps(result, indent=2, sort_keys=True))
    print(
        "DOGFOOD_MEMBER_ONBOARDING_RESULT "
        f"visibleState={visible.get('state')} "
        f"authState={auth_state.get('state')} "
        f"mailpitMessageCount={mailpit_count if mailpit_count is not None else 'skipped'}"
    )
    return 0


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


def assert_support_safe(name: str, payload: dict[str, Any]) -> None:
    serialized = json.dumps(payload, sort_keys=True)
    require(not SECRET_PATTERN.search(serialized), f"{name} contains secret-like text")


def mailpit_message_count(api_url: str) -> int:
    with urllib.request.urlopen(api_url, timeout=10) as response:
        payload = json.load(response)
    if isinstance(payload, dict):
        if isinstance(payload.get("messages"), list):
            return len(payload["messages"])
        if isinstance(payload.get("total"), int):
            return payload["total"]
        if isinstance(payload.get("count"), int):
            return payload["count"]
    raise SystemExit("Mailpit API response did not contain messages/count")


def require(condition: bool, message: str) -> None:
    if not condition:
        raise SystemExit(message)


if __name__ == "__main__":
    sys.exit(main())
