#!/usr/bin/env python3
"""Objective integrity checks for the active data-sovereignty documentation."""

from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
README = ROOT / "README.md"
ACTIVE_DOCS = (
    README,
    ROOT / "docs/architecture/data-sovereignty-core.md",
    ROOT / "docs/architecture/core-package-boundaries.md",
    ROOT / "docs/development/core-workflow.md",
    ROOT / "docs/testing/core-test-strategy.md",
    ROOT / "docs/documentation-audit.md",
)
REQUIRED_README_HEADINGS = (
    "# Weave",
    "## Core architecture",
    "## Open interfaces",
    "## MCP and Weaver",
    "## Data sovereignty",
    "## Current status",
    "## Develop and test",
    "## Ordered roadmap",
    "## Documentation",
    "## License",
)
DOCUMENTED_TASKS = (
    "coreArchitectureCi",
    "canonicalDataCi",
    "postgresPersistenceCi",
    "protocolFacadeCi",
    "providerConnectorCi",
    "mcpFilesCalendarCi",
    "coreCheck",
)
LINK = re.compile(r"\[[^\]]+\]\(([^)]+)\)")


def fail(message: str) -> None:
    print(f"core-docs-check: {message}", file=sys.stderr)
    raise SystemExit(1)


def verify_files() -> None:
    missing = [str(path.relative_to(ROOT)) for path in ACTIVE_DOCS if not path.is_file()]
    if missing:
        fail("missing active documentation: " + ", ".join(missing))


def verify_readme() -> None:
    text = README.read_text(encoding="utf-8")
    non_empty_lines = sum(1 for line in text.splitlines() if line.strip())
    if non_empty_lines > 180:
        fail(f"README has {non_empty_lines} non-empty lines; maximum is 180")

    position = -1
    for heading in REQUIRED_README_HEADINGS:
        current = text.find(heading)
        if current < 0:
            fail(f"README is missing required heading: {heading}")
        if current <= position:
            fail(f"README heading is out of order: {heading}")
        position = current

    required_statements = (
        "canonical data authority",
        "JPA entities are private persistence mappings",
        "Files and Calendar only",
        "Chat is intentionally not exposed through MCP",
        "no unaccounted data loss",
    )
    lowered = text.lower()
    for statement in required_statements:
        if statement.lower() not in lowered:
            fail(f"README is missing required architecture statement: {statement}")


def verify_relative_links() -> None:
    for document in ACTIVE_DOCS:
        text = document.read_text(encoding="utf-8")
        for raw_target in LINK.findall(text):
            target = raw_target.strip().split("#", 1)[0]
            if not target or "://" in target or target.startswith("mailto:"):
                continue
            resolved = (document.parent / target).resolve()
            try:
                resolved.relative_to(ROOT.resolve())
            except ValueError:
                fail(f"link escapes repository in {document.relative_to(ROOT)}: {raw_target}")
            if not resolved.exists():
                fail(f"broken link in {document.relative_to(ROOT)}: {raw_target}")


def verify_documented_tasks() -> None:
    gradle_text = "\n".join(
        path.read_text(encoding="utf-8", errors="replace")
        for path in ROOT.rglob("*.gradle")
        if "build" not in path.parts and ".gradle" not in path.parts
    )
    for task in DOCUMENTED_TASKS:
        if task not in gradle_text:
            fail(f"documented Gradle task is not registered: {task}")


def verify_active_truth() -> None:
    combined = "\n".join(path.read_text(encoding="utf-8") for path in ACTIVE_DOCS).lower()
    required = (
        "canonical weave state is the product authority",
        "mcp supports files and calendar only",
        "weaver/openclaw uses matrix for chat",
        "home-core integration",
    )
    for statement in required:
        if statement not in combined:
            fail(f"active documentation is missing required truth boundary: {statement}")

    forbidden = (
        "mcp supports files, calendar and chat",
        "openapi is the canonical external contract authority",
        "nextcloud is required for the default core",
        "tuwunel is required for the default core",
    )
    for statement in forbidden:
        if statement in combined:
            fail(f"active documentation contains obsolete claim: {statement}")


def main() -> None:
    verify_files()
    verify_readme()
    verify_relative_links()
    verify_documented_tasks()
    verify_active_truth()
    print("core-docs-check: ok")


if __name__ == "__main__":
    main()
