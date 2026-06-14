#!/usr/bin/env python3
"""Fixture tests for product_architecture_claim_guard.py."""
from __future__ import annotations

import importlib.util
import io
import tempfile
from contextlib import redirect_stderr, redirect_stdout
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
GUARD_PATH = ROOT / "tools" / "product_architecture_claim_guard.py"

spec = importlib.util.spec_from_file_location("product_architecture_claim_guard", GUARD_PATH)
assert spec and spec.loader
mod = importlib.util.module_from_spec(spec)
spec.loader.exec_module(mod)  # type: ignore[union-attr]


def write(path: Path, content: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(content, encoding="utf-8")


def expect_fail(fn, contains: str) -> None:
    captured = io.StringIO()
    try:
        with redirect_stdout(captured), redirect_stderr(captured):
            fn()
    except SystemExit as error:
        if error.code != 1:
            raise AssertionError(f"expected exit 1, got {error.code}") from error
        if contains not in captured.getvalue():
            raise AssertionError(f"expected failure containing {contains!r}, got {captured.getvalue()!r}") from error
        return
    raise AssertionError(f"expected failure containing {contains!r}")


def with_fixture() -> Path:
    tmp = Path(tempfile.mkdtemp(prefix="weave-product-guard-"))
    docs = tmp / "docs"
    arch = docs / "architecture"
    write(tmp / "README.md", """# Weave\n\nper-user OpenClaw-derived harness/agent via the Weave channel and Weave-provided MCP/domain tools.\n\n## Repository boundary\n\n## Bootstrap foundation\n""")
    write(docs / "product-architecture.md", """# Product\n\nWeave and Weaver are distinct. OpenClaw-derived clone/harness profile. Weave-provided MCP/domain tools. member-editable OpenClaw config.\n""")
    write(docs / "glossary.md", """# Glossary\n\nOpenClaw-derived clone/harness profile.\n""")
    write(docs / "contract-docs-index.md", "# Index\n")
    write(docs / "repository-boundary.md", """# Boundary\n\nWeave repository owns product policy. Weaver repository owns runtime truth. weave-chat. Provider-native transports stay Weave backend `providerRef` values.\n""")
    write(docs / "jurisdiction-legal-risk-note.md", """# Legal risk\n\n18 U.S.C. § 2713\nhttps://www.justice.gov/dag/cloudact\nhttps://edpb.europa.eu/\nnot legal advice\n""")
    write(arch / "canonical-domain-adapter-registry.md", """# Registry\n\n| Canonical domain | Adapter name | Provider/service | Provider authoritative link | Sovereignty state/posture | Implementation state | Jurisdiction/provider posture | Hosting/control model | Evidence/readiness link | Caveats | Migration/replacement path |\n| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |\n""" + "\n".join(f"| Domain {i} | adapter-{i} | Provider {i} | https://example.org/{i} | posture | state | jurisdiction | hosting | evidence | caveat | path |" for i in range(12)) + "\n")
    write(arch / "mcp-domain-tool-action-registry.md", """# Tool registry\n\nWire names use existing executable snake_case domain-tool names where they already exist.\n\n| Domain | Tool action | Action kind | Risk | ApprovalReceipt requirement | Audit/evidence | Support-safe payload | Adapter binding |\n| --- | --- | --- | --- | --- | --- | --- | --- |\n| Calendar | calendar.search_events | read | low | not required | audit | refs | radicale-caldav |\n""")
    return tmp


def run_with_root(root: Path) -> None:
    old_root, old_readme, old_adapter, old_tools, old_legal, old_boundary, old_docs = mod.ROOT, mod.README, mod.ADAPTER, mod.TOOLS, mod.LEGAL, mod.REPO_BOUNDARY, mod.DOCS
    try:
        mod.ROOT = root
        mod.README = root / "README.md"
        mod.ADAPTER = root / "docs/architecture/canonical-domain-adapter-registry.md"
        mod.TOOLS = root / "docs/architecture/mcp-domain-tool-action-registry.md"
        mod.LEGAL = root / "docs/jurisdiction-legal-risk-note.md"
        mod.REPO_BOUNDARY = root / "docs/repository-boundary.md"
        mod.DOCS = [mod.README, root / "docs/product-architecture.md", mod.ADAPTER, mod.TOOLS, root / "docs/glossary.md", root / "docs/contract-docs-index.md", mod.LEGAL, mod.REPO_BOUNDARY]
        mod.guard_readme(); mod.guard_forbidden_claims(); mod.guard_legal_note(); mod.guard_adapter_registry(); mod.guard_repository_boundary(); mod.guard_tool_registry()
    finally:
        mod.ROOT, mod.README, mod.ADAPTER, mod.TOOLS, mod.LEGAL, mod.REPO_BOUNDARY, mod.DOCS = old_root, old_readme, old_adapter, old_tools, old_legal, old_boundary, old_docs


def test_positive_fixture() -> None:
    run_with_root(with_fixture())


def test_rejects_cloud_act_readme_claim() -> None:
    root = with_fixture()
    readme = root / "README.md"
    readme.write_text(readme.read_text(encoding="utf-8") + "\nWeave is a CLOUD Act solution.\n", encoding="utf-8")
    expect_fail(lambda: run_with_root(root), "specific legal regimes")


def test_rejects_camel_case_tool_wire_name() -> None:
    root = with_fixture()
    tools = root / "docs/architecture/mcp-domain-tool-action-registry.md"
    tools.write_text(tools.read_text(encoding="utf-8").replace("calendar.search_events", "calendar.listEvents"), encoding="utf-8")
    expect_fail(lambda: run_with_root(root), "camelCase")


def test_rejects_missing_repository_boundary_phrase() -> None:
    root = with_fixture()
    boundary = root / "docs/repository-boundary.md"
    boundary.write_text("# Boundary\n\nWeave repository owns product policy.\n", encoding="utf-8")
    expect_fail(lambda: run_with_root(root), "repository boundary missing Weaver repository owns")


if __name__ == "__main__":
    test_positive_fixture()
    test_rejects_cloud_act_readme_claim()
    test_rejects_camel_case_tool_wire_name()
    test_rejects_missing_repository_boundary_phrase()
    print("product-architecture-claim-guard-test: ok")
