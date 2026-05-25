#!/usr/bin/env python3
"""Check or update the managed README release-notes block."""

from __future__ import annotations

import argparse
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
README = ROOT / "README.md"
START = "<!-- WEAVE_RELEASE_NOTES_START -->"
END = "<!-- WEAVE_RELEASE_NOTES_END -->"
EVIDENCE_START = "<!-- WEAVE_RELEASE_NOTES:START -->"
EVIDENCE_END = "<!-- WEAVE_RELEASE_NOTES:END -->"
DEFAULT_SOURCE = ROOT / "docs" / "release-notes" / "unreleased.md"
EVIDENCE_BLOCK = f"""{EVIDENCE_START}
- Current checked-in draft: [Unreleased](docs/release-notes/unreleased.md)
- Offline fixture review artifact: `build/release-notes/unreleased.md` from `./gradlew generateReleaseNotes`
- Release evidence gate: `./gradlew releaseEvidenceCheck`
{EVIDENCE_END}"""


def fail(message: str) -> None:
    print(f"readme-release-notes: {message}", file=sys.stderr)
    raise SystemExit(1)


def replace_marked_block(content: str, start_marker: str, end_marker: str, block: str) -> str:
    start = content.find(start_marker)
    end = content.find(end_marker)
    if start == -1 or end == -1 or end < start:
        fail(f"README.md is missing the managed markers {start_marker} / {end_marker}")
    end += len(end_marker)
    return content[:start] + block + content[end:]


def release_source_to_readme_block(source: Path) -> str:
    if not source.exists():
        fail(f"release notes source not found: {source}")
    text = source.read_text(encoding="utf-8").strip()
    if not text.startswith("# "):
        fail(f"release notes source must start with a level-1 heading: {source}")

    body = text.split("\n", 1)[1].strip() if "\n" in text else ""
    if not body:
        fail(f"release notes source has no body: {source}")

    return f"""{START}
_Generated release notes are review artifacts. A release maintainer may update this block with `python3 tools/readme_release_notes.py --update --source <generated-notes>` before opening the release-draft review._

{body}
{END}"""


def replace_blocks(content: str, source: Path) -> str:
    updated = replace_marked_block(content, START, END, release_source_to_readme_block(source))
    return replace_marked_block(updated, EVIDENCE_START, EVIDENCE_END, EVIDENCE_BLOCK)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--update", action="store_true", help="rewrite README.md with the expected managed blocks")
    parser.add_argument("--check", action="store_true", help="fail if README.md differs from the expected managed blocks")
    parser.add_argument(
        "--source",
        type=Path,
        default=DEFAULT_SOURCE,
        help="release notes markdown used for the README release-notes block",
    )
    args = parser.parse_args()
    if args.update == args.check:
        fail("choose exactly one of --check or --update")

    source = args.source if args.source.is_absolute() else ROOT / args.source
    content = README.read_text(encoding="utf-8")
    updated = replace_blocks(content, source)
    if args.check:
        if updated != content:
            fail("README.md release-notes blocks are missing or out of date; run python3 tools/readme_release_notes.py --update")
        print("readme-release-notes: ok")
        return

    README.write_text(updated, encoding="utf-8")
    print("readme-release-notes: updated README.md")


if __name__ == "__main__":
    main()
