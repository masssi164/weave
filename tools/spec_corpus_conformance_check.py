#!/usr/bin/env python3
"""Validate that the Weave implementation repo consumes the canonical spec corpus.

This check enforces the truth split:
- /code/weave-specs = fachliche specification truth
- /code/weave = implementation/evidence conformance truth
"""

from __future__ import annotations

import json
import os
import subprocess
import sys
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[1]
LOCK_PATH = ROOT / "specs" / "weave-specs.lock.json"
REQUIRED_CORPUS_FILES = [
    "AGENTS.md",
    "README.md",
    "steering/product-constitution.md",
    "steering/sdd-framework.md",
    "steering/ubiquitous-language.md",
    "steering/domain-context-map.md",
    "steering/provider-portability-principles.md",
    "steering/devops-conformance.md",
    "steering/openclaw-orchestrator-pattern.md",
    "platform/identity-security/spec.md",
    "generated/manifest.json",
]
REQUIRED_DOMAIN_DIRS = [
    "spaces",
    "chat",
    "files",
    "calendar",
    "boards-tasks",
    "documents-office",
    "meetings-calls",
    "decisions-evidence",
    "admin-health-ops",
    "agent-runtime-control",
]
IMPLEMENTATION_TRUTH_BOUNDARY_FILES = [
    "AGENTS.md",
    "docs/spec-driven-development.md",
    "docs/weave-operating-model.md",
    "docs/agent-team-orchestration.md",
    "docs/developer-handbook.md",
    "docs/index.md",
    ".specify/memory/constitution.md",
    ".specify/templates/weave-agent-briefs.md",
    ".specify/templates/weave-plan-template.md",
    ".specify/templates/weave-tasks-template.md",
    "specs/README.md",
]
FORBIDDEN_IMPLEMENTATION_TRUTH_MARKERS = [
    "Git-versioned specs are truth; generated docs/wiki views are projections.",
    "Repo-local specs are the versioned product/system contracts for Weave. They are the source",
    "Product and architecture decisions belong in repo docs and repo-local `specs/`",
    "New durable product/system contracts live under `specs/`",
    "Spec-driven development for Weave](spec-driven-development.md) — repo-local specs",
    "Recover truth from `main`, repo specs/docs/tasks",
    "Recover truth from repo/GitHub/CI and identify the contract/spec.",
    "Recover current truth from the Weave repo, GitHub issues/PRs, and CI/evidence.",
    "Do first: recover truth from main, specs, GitHub issues/PRs, CI/evidence.",
    "repo-local spec remains the canonical source",
    "repo docs, specs, issues, PRs, and CI evidence remain the source of truth",
    "The machine-readable source of truth for Sprint",
    "product truth does not drift into orphaned Markdown",
]
REQUIRED_IMPLEMENTATION_TRUTH_MARKERS = {
    "AGENTS.md": ["pinned Weave Specification Corpus", "implementation/evidence truth"],
    "docs/spec-driven-development.md": ["Specification truth", "Implementation/evidence truth"],
    "docs/weave-operating-model.md": ["pinned Weave Specification Corpus", "implementation/evidence truth"],
    "specs/README.md": ["canonical fachliche specification truth", "conformance and evidence truth"],
}


def fail(message: str) -> None:
    print(f"spec-corpus-conformance: {message}", file=sys.stderr)
    raise SystemExit(1)


def run(args: list[str], cwd: Path) -> str:
    proc = subprocess.run(args, cwd=cwd, text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
    if proc.returncode != 0:
        fail(f"command failed in {cwd}: {' '.join(args)}\n{proc.stdout}")
    return proc.stdout.strip()


def run_from_lock(command: str, cwd: Path) -> None:
    proc = subprocess.run(
        command,
        cwd=cwd,
        shell=True,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        executable="/bin/bash",
    )
    if proc.returncode != 0:
        fail(f"command failed in {cwd}: {command}\n{proc.stdout}")


def load_lock() -> dict[str, Any]:
    if not LOCK_PATH.exists():
        fail("missing specs/weave-specs.lock.json")
    try:
        data = json.loads(LOCK_PATH.read_text(encoding="utf-8"))
    except json.JSONDecodeError as exc:
        fail(f"invalid lock JSON: {exc}")
    if data.get("schemaVersion") != 1:
        fail("lock schemaVersion must be 1")
    return data


def main() -> None:
    lock = load_lock()
    corpus_cfg = lock.get("specCorpus") or {}
    truth_boundary = lock.get("truthBoundary") or {}
    local_path = corpus_cfg.get("localPath")
    expected_commit = corpus_cfg.get("gitCommit")
    if not local_path or not expected_commit:
        fail("lock specCorpus.localPath and specCorpus.gitCommit are required")
    if truth_boundary.get("specificationTruth") != local_path:
        fail("lock truthBoundary.specificationTruth must match specCorpus.localPath")
    if truth_boundary.get("implementationEvidenceTruth") != ".":
        fail("lock truthBoundary.implementationEvidenceTruth must be '.'")
    if "must not redefine" not in str(lock.get("generatedProjectionPolicy", "")):
        fail("lock generatedProjectionPolicy must state that repo projections do not redefine corpus truth")

    corpus_override = os.environ.get("WEAVE_SPEC_CORPUS_ROOT", "")
    if corpus_override and not Path(corpus_override).is_absolute():
        fail("WEAVE_SPEC_CORPUS_ROOT must be an absolute Git worktree path")
    corpus_root = (
        Path(corpus_override).resolve()
        if corpus_override
        else (ROOT / local_path).resolve()
    )
    if not corpus_root.exists():
        fail(f"spec corpus path not found: {corpus_root}")
    if not (corpus_root / ".git").exists():
        fail(f"spec corpus is not a git repository: {corpus_root}")

    actual_commit = run(["git", "rev-parse", "HEAD"], corpus_root)
    if actual_commit != expected_commit:
        fail(f"spec corpus commit mismatch: lock={expected_commit}, actual={actual_commit}")
    dirty = run(["git", "status", "--porcelain"], corpus_root)
    if dirty:
        fail(f"spec corpus has uncommitted changes; pinned conformance requires a clean corpus tree\n{dirty}")

    for rel in REQUIRED_CORPUS_FILES:
        if not (corpus_root / rel).exists():
            fail(f"spec corpus missing required file: {rel}")
    for slug in REQUIRED_DOMAIN_DIRS:
        if not (corpus_root / "domains" / slug / "spec.md").exists():
            fail(f"spec corpus missing domain spec: domains/{slug}/spec.md")

    lint_command = str(corpus_cfg.get("lintCommand") or "python3 tools/spec_lint.py")
    run_from_lock(lint_command, corpus_root)

    manifest_rel = str(corpus_cfg.get("manifest", "generated/manifest.json"))
    manifest_path = corpus_root / manifest_rel
    if not manifest_path.exists():
        fail(f"spec corpus manifest missing at locked path: {manifest_rel}")
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    if not manifest.get("do_not_hand_edit"):
        fail("spec corpus manifest must be generated and marked do_not_hand_edit")
    seen_manifest_ids: set[str] = set()
    for entry in manifest.get("entries", []):
        entry_id = entry.get("id")
        entry_path = entry.get("path")
        if not entry_id or not entry_path:
            fail(f"spec corpus manifest entry missing id/path: {entry}")
        if entry_id in seen_manifest_ids:
            fail(f"spec corpus manifest duplicate id: {entry_id}")
        seen_manifest_ids.add(entry_id)
        if entry.get("truth") != "specification":
            fail(f"spec corpus manifest entry {entry_id} must declare truth=specification")
        if not (corpus_root / str(entry_path)).exists():
            fail(f"spec corpus manifest entry path missing: {entry_path}")
    ids = {entry.get("id") for entry in manifest.get("entries", [])}
    required_ids = {
        "WEAVE-STEERING-PRODUCT-CONSTITUTION",
        "WEAVE-STEERING-SDD-FRAMEWORK",
        "WEAVE-STEERING-DEVOPS-CONFORMANCE",
        "WEAVE-PLATFORM-IDENTITY-SECURITY",
        "WEAVE-DOMAIN-DOCUMENTS-OFFICE",
        "WEAVE-DOMAIN-AGENT-RUNTIME-CONTROL",
        "WEAVE-DOMAIN-MEETINGS-CALLS",
        "WEAVE-DOMAIN-BOARDS-TASKS",
        "WEAVE-ACCEPTANCE-AGENT_RUNTIME_ACTION_AUTHORIZATION",
        "WEAVE-ACCEPTANCE-WORKLOAD_IDENTITY_TOKEN_EXCHANGE",
        "WEAVE-CONTRACT-ACTION_EVIDENCE_SCHEMA",
        "WEAVE-CONTRACT-SIGNED_RUNTIME_PROFILE_SCHEMA",
        "WEAVE-CONTRACT-APPROVAL_DECISION_EVIDENCE_SCHEMA",
        "WEAVE-CONTRACT-SIGNED_APPROVAL_DECISION_EVIDENCE_SCHEMA",
    }
    missing = sorted(required_ids - ids)
    if missing:
        fail(f"spec corpus manifest missing required IDs: {missing}")

    for rel in IMPLEMENTATION_TRUTH_BOUNDARY_FILES:
        path = ROOT / rel
        if path.exists():
            text = path.read_text(encoding="utf-8")
            for marker in FORBIDDEN_IMPLEMENTATION_TRUTH_MARKERS:
                if marker in text:
                    fail(f"{rel} still presents implementation repo specs as canonical spec truth")
            for marker in REQUIRED_IMPLEMENTATION_TRUTH_MARKERS.get(rel, []):
                if marker not in text:
                    fail(f"{rel} missing required truth-boundary marker: {marker!r}")

    print(
        "spec-corpus-conformance: ok "
        f"(spec corpus {corpus_root}, commit {actual_commit[:12]}, {len(manifest.get('entries', []))} entries)"
    )


if __name__ == "__main__":
    main()
