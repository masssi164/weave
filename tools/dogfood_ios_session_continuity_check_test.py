#!/usr/bin/env python3
"""Fixture tests for the iOS dogfood session continuity evidence gate."""

from __future__ import annotations

import json
import plistlib
import subprocess
import tempfile
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
CHECK = ROOT / "tools/dogfood_ios_session_continuity_check.py"


def main() -> int:
    with tempfile.TemporaryDirectory() as temporary_directory:
        root = Path(temporary_directory)
        before = root / "before.plist"
        after = root / "after.plist"
        installed = root / "installed.json"
        write_plist(before, [state("workspace_ready")])
        write_plist(
            after,
            [state("workspace_ready"), state("session_restored"), state("workspace_ready")],
        )
        installed.write_text(
            json.dumps(
                {
                    "result": {
                        "apps": [{"bundleIdentifier": "com.massimotter.weave"}],
                    }
                }
            ),
            encoding="utf-8",
        )

        success = run_check(before, after, installed)
        require(success.returncode == 0, success.stderr)
        require("DOGFOOD_SESSION_CONTINUITY_RESULT" in success.stdout, "success marker missing")

        write_plist(after, [state("workspace_ready"), state("workspace_ready")])
        missing_restore = run_check(before, after, installed)
        require(missing_restore.returncode != 0, "missing session_restored evidence passed")

        unsafe = plist_payload([state("workspace_ready"), state("session_restored"), state("workspace_ready")])
        unsafe["accEssToken"] = "must-not-be-exported"
        with after.open("wb") as handle:
            plistlib.dump(unsafe, handle)
        secret_leak = run_check(before, after, installed)
        require(secret_leak.returncode != 0, "secret-bearing preferences passed")

    print("dogfood-ios-session-continuity-check-tests: ok")
    return 0


def state(value: str) -> dict[str, object]:
    return {
        "schemaVersion": "weave.client.dogfood_auth_state.v1",
        "recordedAt": "2026-07-10T00:00:00Z",
        "state": value,
        "handoffRef": "handoff-dogfood",
        "runId": "run-dogfood",
        "supportSafe": True,
    }


def plist_payload(history: list[dict[str, object]]) -> dict[str, str]:
    return {
        "dogfood_auth_state_v1": json.dumps(history[-1]),
        "dogfood_auth_state_history_v1": json.dumps(history),
    }


def write_plist(path: Path, history: list[dict[str, object]]) -> None:
    with path.open("wb") as handle:
        plistlib.dump(plist_payload(history), handle)


def run_check(before: Path, after: Path, installed: Path) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        [
            "python3",
            str(CHECK),
            "--before-prefs-plist",
            str(before),
            "--after-prefs-plist",
            str(after),
            "--installed-app-json",
            str(installed),
        ],
        capture_output=True,
        text=True,
        check=False,
    )


def require(condition: bool, message: str) -> None:
    if not condition:
        raise SystemExit(message)


if __name__ == "__main__":
    raise SystemExit(main())
