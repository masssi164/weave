#!/usr/bin/env python3
"""Lightweight documentation structure checks for the Weave MkDocs site."""

from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[1]
DOCS = ROOT / "docs"
MKDOCS = ROOT / "mkdocs.yml"
REQUIRED_RELEASE_HEADINGS = [
    "Added",
    "Changed",
    "Fixed",
    "Security",
    "Accessibility",
    "Migration/Operator Notes",
    "Known Issues",
]
REQUIRED_DOCS = [
    "index.md",
    "user-handbook.md",
    "admin-operator-handbook.md",
    "developer-handbook.md",
    "gitflow-pr-workflow.md",
    "enterprise-release-foundation.md",
    "diagrams/index.md",
    "release-notes/index.md",
    "release-notes/unreleased.md",
    "release-notes/v0.1.md",
]
REQUIRED_MERMAID = [
    "architecture_facade.mmd",
    "er_chat.mmd",
    "er_files_docs.mmd",
    "er_calendar_meetings.mmd",
    "er_boards_tasks.mmd",
    "er_identity_admin.mmd",
]
RELEASE_NOTE_LABELS = [
    "release-notes-feature",
    "release-notes-bugfix",
    "release-notes-skip",
]


def fail(message: str) -> None:
    print(f"docs-check: {message}", file=sys.stderr)
    raise SystemExit(1)


def read(path: Path) -> str:
    try:
        return path.read_text(encoding="utf-8")
    except FileNotFoundError:
        fail(f"missing required file: {path.relative_to(ROOT)}")


def mkdocs_nav_paths() -> set[str]:
    try:
        import yaml
    except ImportError as error:
        fail("PyYAML is required for docs structure checks; install docs/requirements.txt")

    nav_lines: list[str] = []
    in_nav = False
    for line in read(MKDOCS).splitlines():
        if line.startswith("nav:"):
            in_nav = True
        elif in_nav and line and not line.startswith(" "):
            break
        if in_nav:
            nav_lines.append(line)

    try:
        config = yaml.safe_load("\n".join(nav_lines))
    except yaml.YAMLError as error:
        fail(f"could not parse mkdocs.yml nav: {error}")

    paths: set[str] = set()

    def walk(node: Any) -> None:
        if isinstance(node, str):
            paths.add(node)
        elif isinstance(node, list):
            for item in node:
                walk(item)
        elif isinstance(node, dict):
            for value in node.values():
                walk(value)

    if not isinstance(config, dict):
        fail("mkdocs.yml did not parse as a mapping")
    walk(config.get("nav", []))
    return paths


def check_required_docs() -> None:
    nav_paths = mkdocs_nav_paths()
    for rel in REQUIRED_DOCS:
        read(DOCS / rel)
        if rel not in nav_paths:
            fail(f"{rel} is not linked from mkdocs.yml nav")


def check_release_notes() -> None:
    for rel in ("release-notes/unreleased.md", "release-notes/v0.1.md"):
        content = read(DOCS / rel)
        for heading in REQUIRED_RELEASE_HEADINGS:
            if not re.search(rf"^## {re.escape(heading)}$", content, re.MULTILINE):
                fail(f"{rel} is missing release notes category: {heading}")

    workflow = read(DOCS / "gitflow-pr-workflow.md")
    for label in RELEASE_NOTE_LABELS:
        if label not in workflow:
            fail(f"gitflow-pr-workflow.md is missing release notes label {label}")


def check_diagrams() -> None:
    nav_paths = mkdocs_nav_paths()
    diagrams_index = read(DOCS / "diagrams" / "index.md")
    for name in REQUIRED_MERMAID:
        source = DOCS / "diagrams" / name
        text = read(source).lstrip()
        if not (text.startswith("flowchart") or text.startswith("erDiagram")):
            fail(f"{source.relative_to(ROOT)} does not look like Mermaid source")
        if name not in diagrams_index or f"diagrams/{name}" not in nav_paths:
            fail(f"{name} is not linked from diagrams index and mkdocs nav")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--release-notes-only",
        action="store_true",
        help="check only release notes headings and label policy docs",
    )
    args = parser.parse_args()

    check_release_notes()
    if not args.release_notes_only:
        check_required_docs()
        check_diagrams()

    print("docs-check: ok")


if __name__ == "__main__":
    main()
