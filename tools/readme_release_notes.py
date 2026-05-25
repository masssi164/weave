#!/usr/bin/env python3
"""Check or update the managed README release-notes evidence block."""

from __future__ import annotations

import argparse
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
README = ROOT / "README.md"
START = "<!-- WEAVE_RELEASE_NOTES:START -->"
END = "<!-- WEAVE_RELEASE_NOTES:END -->"
BLOCK = f"""{START}
- Current checked-in draft: [Unreleased](docs/release-notes/unreleased.md)
- Generated review artifact: `build/release-notes/unreleased.md` from `./gradlew generateReleaseNotes`
- Release evidence gate: `./gradlew releaseEvidenceCheck`
{END}"""


def fail(message: str) -> None:
    print(f"readme-release-notes: {message}", file=sys.stderr)
    raise SystemExit(1)


def replace_block(content: str) -> str:
    start = content.find(START)
    end = content.find(END)
    if start == -1 or end == -1 or end < start:
        fail("README.md is missing the managed release-notes markers")
    end += len(END)
    return content[:start] + BLOCK + content[end:]


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--update", action="store_true", help="rewrite README.md with the expected managed block")
    parser.add_argument("--check", action="store_true", help="fail if README.md differs from the expected managed block")
    args = parser.parse_args()
    if args.update == args.check:
        fail("choose exactly one of --check or --update")

    content = README.read_text(encoding="utf-8")
    updated = replace_block(content)
    if args.check:
        if updated != content:
            fail("README.md release-notes block is missing or out of date; run ./gradlew updateReadmeReleaseNotes")
        print("readme-release-notes: ok")
        return

    README.write_text(updated, encoding="utf-8")
    print("readme-release-notes: updated README.md")


if __name__ == "__main__":
    main()
