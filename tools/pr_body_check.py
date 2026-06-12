#!/usr/bin/env python3
"""Validate minimal Weave PR body governance fields."""
from __future__ import annotations

import re
import sys
from pathlib import Path

RELEASE_NOTE_RE = re.compile(r"(?im)^\s*-?\s*Release note:\s*(?!\s*$).+")
TARGET_LANE_RE = re.compile(r"(?im)^\s*-?\s*Target lane:\s*(dev|future/\*|future/[^\s]+|rc/\*|rc/[^\s]+|main exception)\b")
SPEC_IMPACT_RE = re.compile(r"(?im)^\s*- \[[xX]\]\s*(none|implements locked spec|updates spec|changes evidence only)\b")
ISSUE_RE = re.compile(r"(?im)^\s*-?\s*(Issue|Linked issue\(s\)):\s*(?!\s*$).+")
GATES_RE = re.compile(r"(?im)^## Checks run\b")


def validate(text: str) -> list[str]:
    errors: list[str] = []
    if not TARGET_LANE_RE.search(text):
        errors.append("missing target lane declaration: dev, future/*, rc/*, or main exception")
    if not ISSUE_RE.search(text):
        errors.append("missing linked issue or explicit spec/evidence note")
    if not RELEASE_NOTE_RE.search(text):
        errors.append("missing release note line or 'Release note: none — reason'")
    if not SPEC_IMPACT_RE.search(text):
        errors.append("missing checked spec impact option")
    if not GATES_RE.search(text):
        errors.append("missing Checks run section")
    return errors


def main(argv: list[str]) -> int:
    if len(argv) != 2:
        print("usage: tools/pr_body_check.py <pr-body.md>", file=sys.stderr)
        return 2
    text = Path(argv[1]).read_text(encoding="utf-8")
    errors = validate(text)
    if errors:
        for error in errors:
            print(f"ERROR: {error}", file=sys.stderr)
        return 1
    print("PR body check passed")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
