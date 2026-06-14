#!/usr/bin/env python3
"""Guard README/product-architecture drift and unsupported sovereignty/legal claims."""
from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
README = ROOT / "README.md"
PRODUCT_ARCH = ROOT / "docs" / "product-architecture.md"
GLOSSARY = ROOT / "docs" / "glossary.md"
REGISTRY = ROOT / "docs" / "domain-registry-v1.md"
CONTRACTS_INDEX = ROOT / "docs" / "contracts-index.md"

REQUIRED_README = [
    "Weave is a provider-neutral organization collaboration suite",
    "docs/product-architecture.md",
    "docs/glossary.md",
    "docs/contracts-index.md",
    "Sovereignty/data-sovereignty posture belongs to each adapter/provider implementation",
    "Weaver uses the Weave chat channel plus Weave-owned domain tools/facades",
    "OpenClaw is a governed runtime/harness adapter candidate, not product truth",
]

REQUIRED_ARCH = [
    "adapter exchange with visible risk",
    "Data-sovereignty posture is recorded per adapter/provider implementation",
    "Approval/read-write semantics belong to MCP/domain-tool actions",
    "Weave and Weaver are distinct",
    "OpenClaw is a possible governed runtime/harness adapter",
    "Weaver uses the Weave channel and Weave-owned domain tools/facades",
]

REQUIRED_REGISTRY = [
    "Canonical-domain adapter status registry",
    "Sovereignty/data-sovereignty posture",
    "Provider/jurisdiction posture",
    "Migration/replacement path",
    "`weaver` | Governed OpenClaw-derived runtime/harness adapter candidate",
]

FORBIDDEN_PATTERNS = [
    r"Cloud\s*Act",
    r"CLOUD\s*Act",
    r"Cloud-Act",
    r"Cloud-Act-proof",
    r"legal immunity",
    r"immune to legal process",
    r"fully sovereign",
    r"solves legal exposure",
]

README_FORBIDDEN = [
    r"ApprovalReceipt",
    r"MCP",
    r"read/write",
    r"read-write",
]


def fail(message: str) -> None:
    print(f"product-architecture-claim-check: {message}", file=sys.stderr)
    raise SystemExit(1)


def read(path: Path) -> str:
    try:
        return path.read_text(encoding="utf-8")
    except FileNotFoundError:
        fail(f"missing {path.relative_to(ROOT)}")


def assert_contains(path: Path, text: str, phrases: list[str]) -> None:
    for phrase in phrases:
        if phrase not in text:
            fail(f"{path.relative_to(ROOT)} missing required phrase: {phrase}")


def assert_forbidden(path: Path, text: str, patterns: list[str]) -> None:
    for pattern in patterns:
        if re.search(pattern, text, re.IGNORECASE):
            fail(f"{path.relative_to(ROOT)} contains forbidden claim pattern: {pattern}")


def main() -> None:
    readme = read(README)
    arch = read(PRODUCT_ARCH)
    glossary = read(GLOSSARY)
    registry = read(REGISTRY)
    contracts = read(CONTRACTS_INDEX)

    assert_contains(README, readme, REQUIRED_README)
    assert_contains(PRODUCT_ARCH, arch, REQUIRED_ARCH)
    assert_contains(REGISTRY, registry, REQUIRED_REGISTRY)
    assert_contains(GLOSSARY, glossary, ["Weave", "Weaver", "Adapter", "Sovereignty posture", "ApprovalReceipt", "OpenClaw"])
    assert_contains(CONTRACTS_INDEX, contracts, ["Canonical product/domain contracts", "Supporting contracts", "Evidence and claim-control docs", "Historical or research-oriented docs"])

    for path, text in [(README, readme), (PRODUCT_ARCH, arch), (GLOSSARY, glossary), (REGISTRY, registry), (CONTRACTS_INDEX, contracts)]:
        assert_forbidden(path, text, FORBIDDEN_PATTERNS)

    assert_forbidden(README, readme, README_FORBIDDEN)
    if len(readme.splitlines()) > 180:
        fail("README must remain a compact product front door (<= 180 lines)")

    print("product-architecture-claim-check: ok")


if __name__ == "__main__":
    main()
