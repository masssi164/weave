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
REQUIRED_TOP_LEVEL_SECTIONS = [
    "Product architecture",
    "Release notes",
    "Product screenshots",
    "Repository layout",
    "v0.1 product truth",
    "Boards and provider boundary",
    "Infrastructure and OpenTofu",
    "Evidence contract",
    "Release evidence",
    "Common local gates",
    "Working agreements",
]


def fail(message: str) -> None:
    print(f"readme-release-notes: {message}", file=sys.stderr)
    raise SystemExit(1)


def replace_marked_block(content: str, start_marker: str, end_marker: str, block: str) -> str:
    if content.count(start_marker) != 1 or content.count(end_marker) != 1:
        fail(f"README.md must contain exactly one {start_marker} / {end_marker} marker pair")
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


def check_readme_structure(content: str) -> None:
    if content.count("# Weave Monorepo\n") != 1:
        fail("README.md must contain exactly one top-level '# Weave Monorepo' heading")

    for marker in (START, END, EVIDENCE_START, EVIDENCE_END):
        if content.count(marker) != 1:
            fail(f"README.md must contain exactly one {marker} marker")

    generated_start = content.index(START)
    generated_end = content.index(END)
    evidence_start = content.index(EVIDENCE_START)
    evidence_end = content.index(EVIDENCE_END)
    if not (generated_start < generated_end < evidence_start < evidence_end):
        fail("README.md release-note draft block must appear before the release evidence block")

    top_level_sections = [line.removeprefix("## ").strip() for line in content.splitlines() if line.startswith("## ")]
    missing = [section for section in REQUIRED_TOP_LEVEL_SECTIONS if section not in top_level_sections]
    if missing:
        fail("README.md is missing required top-level sections: " + ", ".join(missing))

    repeated = [section for section in REQUIRED_TOP_LEVEL_SECTIONS if top_level_sections.count(section) != 1]
    if repeated:
        fail("README.md required top-level sections must appear exactly once: " + ", ".join(repeated))

    release_section = content.index("## Release notes")
    screenshots_section = content.index("## Product screenshots")
    evidence_section = content.index("## Release evidence")
    gates_section = content.index("## Common local gates")
    if not (release_section < generated_start < generated_end < screenshots_section):
        fail("README.md release-note draft markers must stay inside the Release notes section")
    if not (evidence_section < evidence_start < evidence_end < gates_section):
        fail("README.md release-evidence markers must stay inside the Release evidence section")


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
    check_readme_structure(content)
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
