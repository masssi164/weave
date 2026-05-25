#!/usr/bin/env python3
"""Smoke-test release notes label validation behavior without pytest."""

from __future__ import annotations

import json
import os
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SCRIPT = ROOT / "tools" / "release_notes_label_check.py"


def run_case(labels: list[str], expected_code: int) -> None:
    env = os.environ.copy()
    env["PR_LABELS_JSON"] = json.dumps(labels)
    result = subprocess.run(
        [sys.executable, str(SCRIPT)],
        cwd=ROOT,
        env=env,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        check=False,
    )
    if result.returncode != expected_code:
        print(result.stdout, end="")
        print(result.stderr, end="", file=sys.stderr)
        raise SystemExit(
            f"release-notes-label-check-test: labels={labels!r} expected {expected_code}, got {result.returncode}"
        )


def main() -> None:
    run_case(["release-notes-feature"], 0)
    run_case(["documentation", "release-notes-bugfix"], 0)
    run_case([], 1)
    run_case(["release-notes-feature", "release-notes-bugfix"], 1)
    run_case(["release-notes-skip", "release-notes-bugfix"], 1)
    print("release-notes-label-check-test: ok")


if __name__ == "__main__":
    main()
