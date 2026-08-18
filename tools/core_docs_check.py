#!/usr/bin/env python3
"""Objective integrity checks for the active Weave core documentation."""

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
    ROOT / "docs/architecture/canonical-transfer-kernel.md",
    ROOT / "docs/development/core-workflow.md",
    ROOT / "docs/development/workflow-disposition.md",
    ROOT / "docs/testing/core-test-strategy.md",
    ROOT / "docs/documentation-audit.md",
)
REDIRECT_DOCS = (
    ROOT / "docs/architecture.md",
    ROOT / "docs/bootstrap-foundation-contract.md",
    ROOT / "docs/architecture/adr-004-server-openapi-contract-authority.md",
    ROOT / "docs/architecture/adr-006-enterprise-hard-plan-decision-lock.md",
    ROOT / "docs/architecture/adr-007-persistence-entity-strategy.md",
    ROOT / "docs/architecture/canonical-domains.md",
    ROOT / "docs/architecture/domain-facade-protocol-projections.md",
    ROOT / "docs/architecture/provider-and-infrastructure-boundaries.md",
)
REQUIRED_HEADINGS = (
    "# Weave",
    "## What Weave is",
    "## Core architecture",
    "## Current status",
    "## Ordered roadmap",
    "## Develop and test",
    "## Documentation",
    "## License",
)
REQUIRED_TASKS = (
    "coreArchitectureCi",
    "canonicalDataCi",
    "postgresPersistenceCi",
    "protocolFacadeFoundationCi",
    "mcpFoundationCi",
    "coreDocsCheck",
    "coreCheck",
)
FORBIDDEN_ACTIVE_CLAIMS = (
    "mcp supports files, calendar and chat",
    "chat is exposed through mcp",
    "openapi is the canonical external contract",
    "openapi is the data-plane authority",
    "home-core integration is part of the current core",
    "candidate cut is the active",
    "testflight is required for core",
)
LINK_PATTERN = re.compile(r"\[[^\]]+\]\(([^)]+)\)")


def fail(message: str, failures: list[str]) -> None:
    failures.append(message)


def check_links(path: Path, failures: list[str]) -> None:
    text = path.read_text(encoding="utf-8")
    for raw_target in LINK_PATTERN.findall(text):
        target = raw_target.strip().split(maxsplit=1)[0].strip("<>")
        if not target or target.startswith(("http://", "https://", "mailto:", "#")):
            continue
        relative_target = target.split("#", maxsplit=1)[0]
        if not relative_target:
            continue
        resolved = (path.parent / relative_target).resolve()
        try:
            resolved.relative_to(ROOT)
        except ValueError:
            fail(f"{path.relative_to(ROOT)} links outside the repository: {target}", failures)
            continue
        if not resolved.exists():
            fail(f"{path.relative_to(ROOT)} has a missing link target: {target}", failures)


def main() -> int:
    failures: list[str] = []

    for path in (*ACTIVE_DOCS, *REDIRECT_DOCS):
        if not path.is_file():
            fail(f"missing required documentation file: {path.relative_to(ROOT)}", failures)

    if failures:
        for message in failures:
            print(f"core-docs-check: {message}", file=sys.stderr)
        return 1

    readme_text = README.read_text(encoding="utf-8")
    non_empty_lines = sum(1 for line in readme_text.splitlines() if line.strip())
    if non_empty_lines > 180:
        fail(f"README has {non_empty_lines} non-empty lines; maximum is 180", failures)
    for heading in REQUIRED_HEADINGS:
        if heading not in readme_text:
            fail(f"README is missing heading: {heading}", failures)

    for path in ACTIVE_DOCS:
        text = path.read_text(encoding="utf-8")
        lower = text.lower()
        for forbidden in FORBIDDEN_ACTIVE_CLAIMS:
            if forbidden in lower:
                fail(
                    f"{path.relative_to(ROOT)} contains forbidden active claim: {forbidden}",
                    failures,
                )
        check_links(path, failures)

    for path in REDIRECT_DOCS:
        first_lines = "\n".join(path.read_text(encoding="utf-8").splitlines()[:6]).lower()
        if "superseded" not in first_lines:
            fail(f"redirect is not marked superseded near the top: {path.relative_to(ROOT)}", failures)
        check_links(path, failures)

    task_sources = [ROOT / "build.gradle", *sorted((ROOT / "gradle/tasks").glob("*.gradle"))]
    combined_tasks = "\n".join(path.read_text(encoding="utf-8") for path in task_sources)
    for task in REQUIRED_TASKS:
        if task not in combined_tasks:
            fail(f"documented Gradle task is not registered: {task}", failures)

    disposition = (ROOT / "docs/development/workflow-disposition.md").read_text(encoding="utf-8")
    for workflow in sorted((ROOT / ".github/workflows").glob("*.yml")):
        if workflow.name not in disposition:
            fail(f"workflow has no documented disposition: {workflow.name}", failures)

    if failures:
        for message in failures:
            print(f"core-docs-check: {message}", file=sys.stderr)
        return 1

    print(
        "core-docs-check: OK "
        f"({non_empty_lines} non-empty README lines, "
        f"{len(ACTIVE_DOCS)} active documents, "
        f"{len(list((ROOT / '.github/workflows').glob('*.yml')))} workflows inventoried)"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
