#!/usr/bin/env python3
"""Lightweight documentation structure checks for the Weave MkDocs site."""

from __future__ import annotations

import re
import sys
from pathlib import Path

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


def fail(message: str) -> None:
    print(f"docs-check: {message}", file=sys.stderr)
    raise SystemExit(1)


def read(path: Path) -> str:
    try:
        return path.read_text(encoding="utf-8")
    except FileNotFoundError:
        fail(f"missing required file: {path.relative_to(ROOT)}")


def main() -> None:
    mkdocs = read(MKDOCS)
    for rel in REQUIRED_DOCS:
        path = DOCS / rel
        read(path)
        if rel not in mkdocs:
            fail(f"{rel} is not linked from mkdocs.yml nav")

    for rel in ("release-notes/unreleased.md", "release-notes/v0.1.md"):
        content = read(DOCS / rel)
        for heading in REQUIRED_RELEASE_HEADINGS:
            if not re.search(rf"^## {re.escape(heading)}$", content, re.MULTILINE):
                fail(f"{rel} is missing release notes category: {heading}")

    diagrams_index = read(DOCS / "diagrams" / "index.md")
    for name in REQUIRED_MERMAID:
        source = DOCS / "diagrams" / name
        text = read(source).lstrip()
        if not (text.startswith("flowchart") or text.startswith("erDiagram")):
            fail(f"{source.relative_to(ROOT)} does not look like Mermaid source")
        if name not in diagrams_index or f"diagrams/{name}" not in mkdocs:
            fail(f"{name} is not linked from diagrams index and mkdocs nav")

    print("docs-check: ok")


if __name__ == "__main__":
    main()
