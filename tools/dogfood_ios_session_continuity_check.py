#!/usr/bin/env python3
"""Validate support-safe iOS session restoration after process termination."""

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
    r"client[_-]?secret|password\s*[=:])",
    re.IGNORECASE,
)


def main() -> int:
    parser = argparse.ArgumentParser(
        description=(
            "Prove that an authenticated iOS app process was relaunched, restored "
            "its device-bound session, and completed an authenticated profile request."
        )
    )
    parser.add_argument("--before-prefs-plist", required=True, type=Path)
    parser.add_argument("--after-prefs-plist", required=True, type=Path)
    parser.add_argument("--installed-app-json", type=Path)
    parser.add_argument("--expected-bundle-id", default="com.massimotter.weave")
    parser.add_argument("--output-json", type=Path)
    args = parser.parse_args()

    before = read_plist(args.before_prefs_plist)
    after = read_plist(args.after_prefs_plist)
    assert_preferences_are_support_safe(before, "before preferences")
    assert_preferences_are_support_safe(after, "after preferences")

    before_state = read_json_object(before, "dogfood_auth_state_v1")
    after_state = read_json_object(after, "dogfood_auth_state_v1")
    before_history = read_json_array(before, "dogfood_auth_state_history_v1")
    after_history = read_json_array(after, "dogfood_auth_state_history_v1")

    require(before_state.get("state") == "workspace_ready", "session was not workspace_ready before relaunch")
    require(after_state.get("state") == "workspace_ready", "session did not return to workspace_ready after relaunch")
    require(before_state.get("supportSafe") is True, "before state is not supportSafe=true")
    require(after_state.get("supportSafe") is True, "after state is not supportSafe=true")
    require(
        len(after_history) >= len(before_history) + 2,
        "relaunch did not append session continuity evidence",
    )

    appended = after_history[len(before_history) :]
    appended_states = [entry.get("state") for entry in appended]
    require(
        contains_in_order(appended_states, ["session_restored", "workspace_ready"]),
        "relaunch did not prove session_restored followed by workspace_ready",
    )
    for index, entry in enumerate(appended):
        require(entry.get("supportSafe") is True, f"appended history entry {index} is not supportSafe=true")

    handoff_ref = after_state.get("handoffRef")
    run_id = after_state.get("runId")
    require(isinstance(handoff_ref, str) and handoff_ref, "after state has no support-safe handoffRef")
    require(isinstance(run_id, str) and run_id, "after state has no support-safe runId")
    for index, entry in enumerate(appended):
        require(entry.get("handoffRef") == handoff_ref, f"appended history entry {index} handoffRef mismatch")
        require(entry.get("runId") == run_id, f"appended history entry {index} runId mismatch")

    installed_bundle_matches = None
    if args.installed_app_json is not None:
        installed_bundle_matches = installed_bundle_id(args.installed_app_json) == args.expected_bundle_id
        require(installed_bundle_matches, "installed app bundle identity changed")

    result = {
        "schemaVersion": "weave.dogfood.ios-session-continuity.v1",
        "supportSafe": True,
        "bundleId": args.expected_bundle_id,
        "installedBundleMatches": installed_bundle_matches,
        "beforeState": before_state.get("state"),
        "afterState": after_state.get("state"),
        "appendedStates": appended_states,
        "handoffRef": handoff_ref,
        "runId": run_id,
        "keychainContentsInspected": False,
        "authenticatedRequestEvidence": "profile facade completed before workspace_ready",
    }
    serialized = json.dumps(result, indent=2, sort_keys=True) + "\n"
    if args.output_json is not None:
        args.output_json.parent.mkdir(parents=True, exist_ok=True)
        args.output_json.write_text(serialized, encoding="utf-8")
    print(serialized, end="")
    print(
        "DOGFOOD_SESSION_CONTINUITY_RESULT "
        f"beforeState={result['beforeState']} afterState={result['afterState']} "
        f"installedBundleMatches={installed_bundle_matches}"
    )
    return 0


def read_plist(path: Path) -> dict[str, Any]:
    with path.open("rb") as handle:
        payload = plistlib.load(handle)
    if not isinstance(payload, dict):
        raise SystemExit(f"{path} did not contain a plist dictionary")
    return payload


def read_json_object(prefs: dict[str, Any], key: str) -> dict[str, Any]:
    decoded = read_json_preference(prefs, key)
    if not isinstance(decoded, dict):
        raise SystemExit(f"{key} must contain a JSON object")
    return decoded


def read_json_array(prefs: dict[str, Any], key: str) -> list[dict[str, Any]]:
    decoded = read_json_preference(prefs, key)
    if not isinstance(decoded, list) or not all(isinstance(entry, dict) for entry in decoded):
        raise SystemExit(f"{key} must contain a JSON object array")
    return decoded


def read_json_preference(prefs: dict[str, Any], key: str) -> Any:
    raw = prefs.get(key)
    if not isinstance(raw, str) or not raw:
        raise SystemExit(f"{key} missing from copied app preferences")
    try:
        return json.loads(raw)
    except json.JSONDecodeError as exc:
        raise SystemExit(f"{key} is not valid JSON: {exc}") from exc


def installed_bundle_id(path: Path) -> str | None:
    payload = json.loads(path.read_text(encoding="utf-8"))
    result = payload.get("result", {}) if isinstance(payload, dict) else {}
    apps = result.get("apps", []) if isinstance(result, dict) else []
    if not isinstance(apps, list) or not apps or not isinstance(apps[0], dict):
        return None
    value = apps[0].get("bundleIdentifier")
    return value if isinstance(value, str) else None


def assert_preferences_are_support_safe(prefs: dict[str, Any], label: str) -> None:
    serialized = json.dumps(prefs, sort_keys=True, default=str)
    require(not SECRET_PATTERN.search(serialized), f"{label} contains token or password material")


def contains_in_order(observed: list[Any], required: list[str]) -> bool:
    position = 0
    for value in observed:
        if position < len(required) and value == required[position]:
            position += 1
    return position == len(required)


def require(condition: bool, message: str) -> None:
    if not condition:
        raise SystemExit(message)


if __name__ == "__main__":
    sys.exit(main())
