#!/usr/bin/env python3
"""Validate Weave repo-local specification contracts."""

from __future__ import annotations

import re
import sys
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[1]
SPECS_DIR = ROOT / "specs"

REQUIRED_KEYS = {
    "id",
    "title",
    "version",
    "status",
    "domain",
    "owner",
    "github_issue",
    "supersedes",
    "depends_on",
    "acceptance_features",
    "evidence_gates",
}

ALLOWED_STATUSES = {
    "draft",
    "proposed",
    "accepted",
    "implementing",
    "implemented",
    "superseded",
    "deprecated",
    "rejected",
}

CLARIFICATION_ALLOWED = {"draft", "proposed"}
SPEC_ID_RE = re.compile(r"^WEAVE-SPEC-(\d{4})$")
VERSION_RE = re.compile(r"^\d+\.\d+\.\d+(?:[-+][0-9A-Za-z.-]+)?$")
CLARIFICATION_RE = re.compile(r"\[NEEDS CLARIFICATION:[^\]]+\]")
FRONTMATTER_RE = re.compile(r"^---\n(.*?)\n---\n", re.DOTALL)

FRAMEWORK_FORBIDDEN_FILES = {
    ".specify/templates/weave-agent-team-config.example.json5": "operator-runtime configuration example",
}

FRAMEWORK_FORBIDDEN_MARKERS = {
    "allowAgents": "live agent allowlist",
    "requireAgentId": "live runtime policy",
    "agents.list": "live agent registry",
    ".openclaw": "personal operator path",
}

FRAMEWORK_REQUIRED_FILES = {
    ".specify/memory/constitution.md": ["Repo truth over chat memory", "Assistant governance"],
    ".specify/templates/weave-spec-template.md": ["[NEEDS CLARIFICATION:"],
    ".specify/templates/weave-plan-template.md": ["Constitution check"],
    ".specify/templates/weave-tasks-template.md": ["Assistant handoff"],
    ".specify/templates/weave-agent-briefs.md": [
        "Optimization-Review",
        "Coding-Harness-Brief",
        "Live runtime configuration",
    ],
    "docs/spec-driven-development.md": [
        "Specification truth",
        "Implementation/evidence truth",
        "agent-team-orchestration.md",
        "Do not add live agent allowlists",
    ],
    "docs/agent-team-orchestration.md": [
        "Material optimization",
        "Runtime boundary",
        "Forbidden repo-local content",
        "operator-runtime JSON examples",
    ],
}


def fail(message: str) -> None:
    print(f"spec-contract-check: {message}", file=sys.stderr)
    raise SystemExit(1)


def parse_scalar(value: str) -> Any:
    value = value.strip()
    if value == "null":
        return None
    if value == "[]":
        return []
    if (value.startswith('"') and value.endswith('"')) or (value.startswith("'") and value.endswith("'")):
        return value[1:-1]
    return value


def parse_frontmatter(text: str, path: Path) -> dict[str, Any]:
    match = FRONTMATTER_RE.match(text)
    if not match:
        fail(f"{path.relative_to(ROOT)} missing YAML frontmatter")
    data: dict[str, Any] = {}
    current_key: str | None = None
    for raw_line in match.group(1).splitlines():
        if not raw_line.strip() or raw_line.lstrip().startswith("#"):
            continue
        stripped = raw_line.lstrip()
        if stripped.startswith("- "):
            if current_key is None:
                fail(f"{path.relative_to(ROOT)} has list item without key: {raw_line!r}")
            data.setdefault(current_key, [])
            if not isinstance(data[current_key], list):
                fail(f"{path.relative_to(ROOT)} key {current_key!r} mixes scalar and list values")
            data[current_key].append(parse_scalar(stripped[2:]))
            continue
        if ":" not in raw_line:
            fail(f"{path.relative_to(ROOT)} unsupported frontmatter line: {raw_line!r}")
        key, value = raw_line.split(":", 1)
        key = key.strip()
        if not key:
            fail(f"{path.relative_to(ROOT)} has empty frontmatter key")
        current_key = key
        value = value.strip()
        data[key] = [] if value == "" else parse_scalar(value)
    return data


def check_spec_dir(spec_dir: Path) -> None:
    spec_path = spec_dir / "spec.md"
    if not spec_path.exists():
        fail(f"{spec_dir.relative_to(ROOT)} missing spec.md")
    text = spec_path.read_text(encoding="utf-8")
    meta = parse_frontmatter(text, spec_path)

    missing = sorted(REQUIRED_KEYS.difference(meta))
    if missing:
        fail(f"{spec_path.relative_to(ROOT)} missing required frontmatter keys: {missing}")

    spec_id = str(meta["id"])
    id_match = SPEC_ID_RE.match(spec_id)
    if not id_match:
        fail(f"{spec_path.relative_to(ROOT)} id must match WEAVE-SPEC-NNNN, got {spec_id!r}")
    if not spec_dir.name.startswith(id_match.group(1) + "-"):
        fail(f"{spec_dir.relative_to(ROOT)} directory must start with {id_match.group(1)}-")

    status = str(meta["status"])
    if status not in ALLOWED_STATUSES:
        fail(f"{spec_path.relative_to(ROOT)} invalid status {status!r}; allowed {sorted(ALLOWED_STATUSES)}")

    version = str(meta["version"])
    if not VERSION_RE.match(version):
        fail(f"{spec_path.relative_to(ROOT)} version must be semantic, got {version!r}")

    for key in ["supersedes", "depends_on", "acceptance_features", "evidence_gates"]:
        if not isinstance(meta[key], list):
            fail(f"{spec_path.relative_to(ROOT)} {key} must be a YAML list")

    if not meta["evidence_gates"]:
        fail(f"{spec_path.relative_to(ROOT)} evidence_gates must name at least one gate")

    if status not in CLARIFICATION_ALLOWED:
        for path in sorted(spec_dir.rglob("*.md")):
            body = path.read_text(encoding="utf-8")
            match = CLARIFICATION_RE.search(body)
            if match:
                fail(
                    f"{path.relative_to(ROOT)} status {status!r} cannot contain "
                    f"clarification marker {match.group(0)!r}"
                )

    if status in {"accepted", "implementing", "implemented"}:
        trace = spec_dir / "traceability.yaml"
        if not trace.exists():
            fail(f"{spec_dir.relative_to(ROOT)} status {status!r} requires traceability.yaml")
        trace_text = trace.read_text(encoding="utf-8")
        if f"spec_id: {spec_id}" not in trace_text:
            fail(f"{trace.relative_to(ROOT)} must include spec_id: {spec_id}")

    for feature in meta["acceptance_features"]:
        feature_path = ROOT / str(feature)
        if not feature_path.exists():
            fail(f"{spec_path.relative_to(ROOT)} acceptance feature does not exist: {feature}")


def check_framework_artifacts(spec_dirs: list[Path]) -> None:
    has_framework_spec = False
    for spec_dir in spec_dirs:
        spec_path = spec_dir / "spec.md"
        meta = parse_frontmatter(spec_path.read_text(encoding="utf-8"), spec_path)
        if meta.get("id") == "WEAVE-SPEC-0000":
            has_framework_spec = True
            break
    if not has_framework_spec:
        return
    for relative, reason in FRAMEWORK_FORBIDDEN_FILES.items():
        path = ROOT / relative
        if path.exists():
            fail(f"framework spec forbids {relative}: {reason}")
    for relative, required_markers in FRAMEWORK_REQUIRED_FILES.items():
        path = ROOT / relative
        if not path.exists():
            fail(f"framework spec requires {relative}")
        text = path.read_text(encoding="utf-8")
        for forbidden_marker, reason in FRAMEWORK_FORBIDDEN_MARKERS.items():
            if forbidden_marker in text:
                fail(f"{relative} must not contain {reason} marker {forbidden_marker!r}")
        for marker in required_markers:
            if marker not in text:
                fail(f"{relative} must contain framework marker {marker!r}")


def check_unique_spec_ids(spec_dirs: list[Path]) -> None:
    owners: dict[str, Path] = {}
    for spec_dir in spec_dirs:
        spec_path = spec_dir / "spec.md"
        meta = parse_frontmatter(spec_path.read_text(encoding="utf-8"), spec_path)
        spec_id = str(meta.get("id", ""))
        prior = owners.get(spec_id)
        if prior is not None:
            fail(
                "duplicate global spec id "
                f"{spec_id}: {prior.relative_to(ROOT)} and {spec_path.relative_to(ROOT)}"
            )
        owners[spec_id] = spec_path


def main() -> None:
    if not SPECS_DIR.exists():
        fail("missing specs/ directory")
    spec_dirs = sorted(path for path in SPECS_DIR.iterdir() if path.is_dir() and not path.name.startswith("."))
    if not spec_dirs:
        fail("specs/ contains no spec directories")
    for spec_dir in spec_dirs:
        check_spec_dir(spec_dir)
    check_unique_spec_ids(spec_dirs)
    check_framework_artifacts(spec_dirs)
    print(f"spec-contract-check: ok ({len(spec_dirs)} spec directory/directories)")


if __name__ == "__main__":
    main()
