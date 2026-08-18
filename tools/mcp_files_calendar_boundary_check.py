#!/usr/bin/env python3
"""Fail when weave-mcp-server becomes a second data/provider authority or exposes Chat."""

from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MCP_ROOT = ROOT / "weave-mcp-server"
SOURCE_ROOT = MCP_ROOT / "src" / "main"
DEPENDENCY_FILE = MCP_ROOT / "gradle" / "scripts" / "java-and-dependencies.gradle"

PROHIBITED_DEPENDENCY_MARKERS = (
    "spring-boot-starter-data-jpa",
    "flyway",
    "weave-persistence-jpa",
    "weave-runtime-provider-adapters",
    "org.apache.opendal",
    "ical4j",
    "ruma",
)

CHAT_CATALOG = re.compile(
    r"@Mcp(?:Tool|Resource|ResourceTemplate)[\s\S]{0,500}?"
    r"(?:name|uri|value)\s*=\s*[\"'](?:chat(?:[._:/]|$)|weave://chat)",
    re.IGNORECASE,
)


def fail(message: str) -> None:
    print(f"mcp-files-calendar-boundary: {message}", file=sys.stderr)
    raise SystemExit(1)


def source_files() -> list[Path]:
    if not SOURCE_ROOT.is_dir():
        fail(f"missing MCP source root: {SOURCE_ROOT.relative_to(ROOT)}")
    return sorted(path for path in SOURCE_ROOT.rglob("*") if path.is_file())


def main() -> None:
    if not DEPENDENCY_FILE.is_file():
        fail(f"missing dependency file: {DEPENDENCY_FILE.relative_to(ROOT)}")

    dependency_text = DEPENDENCY_FILE.read_text(encoding="utf-8").lower()
    leaked_dependencies = [
        marker for marker in PROHIBITED_DEPENDENCY_MARKERS if marker in dependency_text
    ]
    if leaked_dependencies:
        fail("prohibited persistence/provider/protocol implementation dependencies: "
             + ", ".join(leaked_dependencies))

    inspected = 0
    for path in source_files():
        relative = path.relative_to(MCP_ROOT).as_posix()
        lowered_path = relative.lower()
        if "/chat/" in f"/{lowered_path}" or path.stem.lower().startswith("chat"):
            fail(f"Chat-owned MCP source path is forbidden: {relative}")

        if path.suffix not in {".java", ".kt", ".properties", ".yml", ".yaml"}:
            continue
        inspected += 1
        text = path.read_text(encoding="utf-8")
        lowered = text.lower()
        if CHAT_CATALOG.search(text):
            fail(f"Chat MCP tool/resource declaration is forbidden: {relative}")
        if "/internal/mcp" in lowered:
            fail(f"parallel internal MCP business endpoint is forbidden: {relative}")
        if "/v3/api-docs" in lowered or "openapi route" in lowered or "openapi scraping" in lowered:
            fail(f"OpenAPI-derived MCP catalog is forbidden: {relative}")
        if "jakarta.persistence" in lowered or "javax.persistence" in lowered:
            fail(f"JPA type leaked into MCP source: {relative}")

    if inspected == 0:
        fail("no MCP source files were inspected")

    print(
        "mcp-files-calendar-boundary: ok "
        f"({inspected} source/config files; no Chat catalog or persistence/provider authority)"
    )


if __name__ == "__main__":
    main()
