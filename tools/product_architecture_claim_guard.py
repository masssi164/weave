#!/usr/bin/env python3
"""Guard Weave product architecture docs against claim/model drift."""
from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
README = ROOT / "README.md"
ADAPTER = ROOT / "docs/architecture/canonical-domain-adapter-registry.md"
TOOLS = ROOT / "docs/architecture/mcp-domain-tool-action-registry.md"
LEGAL = ROOT / "docs/jurisdiction-legal-risk-note.md"
REPO_BOUNDARY = ROOT / "docs/repository-boundary.md"
DOCS = [README, ROOT / "docs/product-architecture.md", ADAPTER, TOOLS, ROOT / "docs/glossary.md", ROOT / "docs/contract-docs-index.md", LEGAL, REPO_BOUNDARY]
UNKNOWN = "unknown/not-yet-selected"


def fail(msg: str) -> None:
    print(f"product-architecture-claim-guard: {msg}", file=sys.stderr)
    raise SystemExit(1)


def text(path: Path) -> str:
    if not path.exists():
        fail(f"missing {path.relative_to(ROOT)}")
    return path.read_text(encoding="utf-8")


def guard_readme() -> None:
    content = text(README)
    table_rows = [line for line in content.splitlines() if line.startswith("|")]
    if len(table_rows) > 16:
        fail("README contains an oversized table/matrix")
    if "Cloud Act" in content or "CLOUD Act" in content:
        fail("README must not discuss specific legal regimes; link sourced legal-risk note instead")


def guard_forbidden_claims() -> None:
    combined = "\n".join(f"{p.relative_to(ROOT)}\n{text(p)}" for p in DOCS)
    forbidden_patterns = [
        r"Weave (is|as|provides|delivers)[^.\n]*Cloud-?Act-?proof", r"CLOUD Act solution", r"legal shield", r"Weave (guarantees|provides)[^.\n]*sovereignty",
        r"Weave (guarantees|provides)[^.\n]*no leaks", r"leak prevention guarantee", r"Weave (is|as|provides|delivers)[^.\n]*compliance-certified", r"Weave (guarantees|provides)[^.\n]*full compliance",
        r"OpenClaw is (Weave|the product|product truth)", r"possible runtime/harness adapter candidate", r"runtime/harness candidate only; not product truth", r"private request history", r"Massimo['’]s request",
    ]
    for pat in forbidden_patterns:
        if re.search(pat, combined, re.IGNORECASE):
            fail(f"unsupported/private/product-truth claim matched: {pat}")
    readme = text(README)
    glossary = text(ROOT / "docs/glossary.md")
    architecture = text(ROOT / "docs/product-architecture.md")
    required_model_phrases = [
        "per-user OpenClaw-derived harness/agent",
        "Weave channel",
        "Weave-provided MCP/domain tools",
        "OpenClaw-derived clone/harness profile",
    ]
    combined_model = "\n".join([readme, glossary, architecture])
    for phrase in required_model_phrases:
        if phrase not in combined_model:
            fail(f"missing corrected Weaver/OpenClaw product model phrase: {phrase}")
    for path in DOCS:
        content = text(path)
        if path != TOOLS and re.search(r"\b(ApprovalReceipt|read/write|read or write|write authority)\b", content, re.IGNORECASE):
            # Allow conceptual boundary wording, reject claims that domains/adapters own action authority.
            bad = re.search(r"(domain|adapter)s?\s+(own|grant|perform|provide|control)\s+[^.\n]*(read|write)", content, re.IGNORECASE)
            if bad:
                fail(f"{path.relative_to(ROOT)} assigns action semantics to domains/adapters: {bad.group(0)}")


def guard_legal_note() -> None:
    content = text(LEGAL)
    for required in ["18 U.S.C. § 2713", "justice.gov/dag/cloudact", "edpb.europa.eu", "not legal advice"]:
        if required not in content:
            fail(f"legal-risk note missing {required}")
    if re.search(r"Weave (solves|avoids|neutralizes|guarantees).*CLOUD", content, re.IGNORECASE):
        fail("legal-risk note frames CLOUD Act as a Weave solution")


def guard_adapter_registry() -> None:
    content = text(ADAPTER)
    required_headers = ["Canonical domain", "Adapter name", "Provider/service", "Provider authoritative link", "Sovereignty state/posture", "Implementation state", "Jurisdiction/provider posture", "Hosting/control model", "Evidence/readiness link", "Caveats", "Migration/replacement path"]
    for header in required_headers:
        if header not in content:
            fail(f"adapter registry missing header {header}")
    rows = [line for line in content.splitlines() if line.startswith("|") and "---" not in line and "Canonical domain" not in line]
    if len(rows) < 12:
        fail("adapter registry has too few canonical-domain rows")
    for i, row in enumerate(rows, start=1):
        cols = [c.strip() for c in row.strip("|").split("|")]
        if len(cols) != 11:
            fail(f"adapter registry row {i} must have 11 columns")
        provider_link = cols[3]
        if not (provider_link.startswith("http") or UNKNOWN in provider_link):
            fail(f"adapter registry row {i} missing provider authoritative link or unknown marker")
        if any(not c for c in cols):
            fail(f"adapter registry row {i} has empty required field")



def guard_repository_boundary() -> None:
    content = text(REPO_BOUNDARY)
    for required in [
        "Weave repository owns",
        "Weaver repository owns",
        "weave-chat",
        "Provider-native transports stay Weave backend `providerRef` values",
    ]:
        if required not in content:
            fail(f"repository boundary missing {required}")

def guard_tool_registry() -> None:
    content = text(TOOLS)
    for header in ["Tool action", "Action kind", "Risk", "ApprovalReceipt requirement", "Audit/evidence", "Support-safe payload", "Adapter binding"]:
        if header not in content:
            fail(f"tool-action registry missing header {header}")
    if "ApprovalReceipt" not in content or "required" not in content:
        fail("tool-action registry must record ApprovalReceipt requirements")
    if "Wire names use existing executable snake_case" not in content:
        fail("tool-action registry must state wire-name semantics")
    rows = [line for line in content.splitlines() if line.startswith("|") and "---" not in line and "Tool action" not in line]
    for i, row in enumerate(rows, start=1):
        cols = [c.strip() for c in row.strip("|").split("|")]
        if len(cols) != 8:
            fail(f"tool-action registry row {i} must have 8 columns")
        action = cols[1]
        if not re.match(r"^[a-z][a-z0-9_]*\.[a-z][a-z0-9_]*$", action):
            fail(f"tool-action registry row {i} uses non-wire/camelCase tool action: {action}")


def main() -> None:
    guard_readme()
    guard_forbidden_claims()
    guard_legal_note()
    guard_adapter_registry()
    guard_repository_boundary()
    guard_tool_registry()
    print("product-architecture-claim-guard: ok")


if __name__ == "__main__":
    main()
