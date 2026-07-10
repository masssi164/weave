#!/usr/bin/env python3
"""Guard Spec 0009 domain-first MCP tool names.

This is intentionally narrow: it checks executable MCP discovery sources,
contracts, and fixture evidence for tool identifiers that start with adapter or
provider vocabulary instead of Weave domain vocabulary.
"""

from __future__ import annotations

import json
import re
import sys
from pathlib import Path
from typing import Any, Iterable

ROOT = Path(__file__).resolve().parents[1]

DOMAIN_FIRST_PREFIXES = {
    "admin",
    "audit",
    "boards",
    "calendar",
    "chat",
    "documents",
    "files",
    "identity",
    "people",
    "policy",
    "registry",
    "tasks",
    "weaver",
}

ADAPTER_FIRST_PREFIXES = {
    "adapter",
    "adapters",
    "authentik",
    "caldav",
    "fastmcp",
    "keycloak",
    "matrix",
    "minio",
    "nextcloud",
    "provider",
    "providers",
    "radicale",
    "synapse",
    "webdav",
    "zulip",
}

ADAPTER_TOKEN_RE = re.compile(
    r"(^|[._-])(adapter|authentik|caldav|keycloak|matrix|minio|nextcloud|radicale|synapse|webdav|zulip)([._-]|$)",
    re.IGNORECASE,
)
TOOL_NAME_RE = re.compile(r"^[a-z][a-z0-9]*(?:\.[a-z][a-z0-9_]*)+$")

SOURCES = [
    ROOT / "weave-contract/src/main/java/com/massimotter/weave/contract/mcp/MemberMcpToolCatalog.java",
    ROOT / "infra/weave-workspace/weave-mcp-tool-contract.json",
    ROOT / "release/provider-lab/weaver-runtime/sprint-32-weaver-mcp-tool-execution.fixture.json",
    ROOT / "release/provider-lab/weaver-runtime/tool-approval-gate-proof.fixture.json",
]


def fail(message: str) -> None:
    print(f"mcp-domain-tool-name-check: {message}", file=sys.stderr)
    raise SystemExit(1)


JAVA_STRING_RE = re.compile(r'"((?:\\.|[^"\\])*)"')


def java_strings(path: Path) -> Iterable[tuple[str, str]]:
    for line_number, line in enumerate(path.read_text(encoding="utf-8").splitlines(), start=1):
        for match in JAVA_STRING_RE.finditer(line):
            yield bytes(match.group(1), "utf-8").decode("unicode_escape"), f"{path.relative_to(ROOT)}:{line_number}"


JSON_TOOL_KEYS = {
    "allowedProofTools",
    "writeToolsRequireApproval",
    "grantedTools",
    "tools",
    "tool",
    "toolName",
}


def json_strings(value: Any, path: Path, pointer: str = "$", active: bool = False) -> Iterable[tuple[str, str]]:
    if isinstance(value, dict):
        for key, child in value.items():
            child_pointer = f"{pointer}.{key}"
            yield from json_strings(child, path, child_pointer, active or key in JSON_TOOL_KEYS)
    elif isinstance(value, list):
        for index, child in enumerate(value):
            yield from json_strings(child, path, f"{pointer}[{index}]", active)
    elif active and isinstance(value, str):
        yield value, pointer


def candidate_tool_names(path: Path) -> Iterable[tuple[str, str]]:
    if path.suffix == ".java":
        yield from java_strings(path)
        return
    data = json.loads(path.read_text(encoding="utf-8"))
    yield from json_strings(data, path)


def is_tool_name(value: str) -> bool:
    return bool(TOOL_NAME_RE.match(value))


def check_tool_name(name: str, location: str) -> list[str]:
    reasons: list[str] = []
    prefix = name.split(".", 1)[0]
    if prefix in ADAPTER_FIRST_PREFIXES:
        reasons.append(f"adapter/provider-first prefix {prefix!r}")
    if prefix not in DOMAIN_FIRST_PREFIXES:
        reasons.append(f"unknown Weave domain prefix {prefix!r}")
    if ADAPTER_TOKEN_RE.search(name):
        reasons.append("adapter/provider token in MCP tool name")
    if reasons:
        return [f"{location}: {name!r} ({'; '.join(reasons)})"]
    return []


def main() -> None:
    failures: list[str] = []
    seen = 0
    for path in SOURCES:
        if not path.exists():
            fail(f"missing source {path.relative_to(ROOT)}")
        for value, location in candidate_tool_names(path):
            if not is_tool_name(value):
                continue
            seen += 1
            failures.extend(check_tool_name(value, location))
    if failures:
        fail("adapter-first MCP names found:\n" + "\n".join(f"  - {item}" for item in failures))
    print(f"mcp-domain-tool-name-check: ok ({seen} MCP-like name(s) checked)")


if __name__ == "__main__":
    main()
