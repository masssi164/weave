#!/usr/bin/env python3
"""Reject executable OpenTofu/Terraform paths after the Compose cutover.

Historical prose may describe the former deployment, but active infrastructure,
workflow, and build surfaces may not contain HCL state or invoke the retired tool.
"""

from __future__ import annotations

import re
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
FORBIDDEN_INFRA_NAMES = frozenset({".terraform.lock.hcl", ".tofu.lock.hcl"})
FORBIDDEN_INFRA_SUFFIXES = (".tf", ".tf.json", ".tfstate")
ACTIVE_SUFFIXES = frozenset({".sh", ".bash", ".zsh", ".py", ".yml", ".yaml", ".gradle", ".mk"})
FORBIDDEN_CONTENT = (
    ("TF_VAR_", re.compile(r"\bTF_VAR_[A-Za-z0-9_]*")),
    ("WEAVE_IAC_BIN", re.compile(r"\bWEAVE_IAC_BIN\b")),
    ("OpenTofu setup action", re.compile(r"opentofu/setup-opentofu", re.IGNORECASE)),
    ("Terraform setup action", re.compile(r"hashicorp/setup-terraform", re.IGNORECASE)),
    (
        "retired state dependency",
        re.compile(r"(?:opentofu-state|terraform-state|\.tfstate(?:\b|[\"']))", re.IGNORECASE),
    ),
    (
        "OpenTofu command",
        re.compile(r"(?<![A-Za-z0-9_.-])tofu(?=\s|$|[\"'])", re.IGNORECASE | re.MULTILINE),
    ),
    (
        "Terraform command",
        re.compile(
            r"(?<![A-Za-z0-9_.-])terraform\s+(?:-chdir\S*\s+)?"
            r"(?:init|plan|apply|destroy|validate|fmt|state|output|show)(?=\s|$)",
            re.IGNORECASE | re.MULTILINE,
        ),
    ),
)


def active_files(root: Path) -> list[Path]:
    files: set[Path] = set()
    infra = root / "infra"
    if infra.is_dir():
        for path in infra.rglob("*"):
            relative_parts = path.relative_to(infra).parts
            if not path.is_file() or any(
                part.startswith(".") and part not in {".github"} for part in relative_parts
            ):
                continue
            # Contract tests quote forbidden commands and environment names as
            # negative fixtures; they are not deployment entrypoints.
            if "tests" in relative_parts:
                continue
            if path.suffix in ACTIVE_SUFFIXES or path.name in {"Makefile", "Dockerfile"}:
                files.add(path)
    workflows = root / ".github" / "workflows"
    if workflows.is_dir():
        files.update(path for path in workflows.iterdir() if path.suffix in {".yml", ".yaml"})
    for relative in ("build.gradle", "settings.gradle", "Makefile"):
        path = root / relative
        if path.is_file():
            files.add(path)
    return sorted(files)


def findings(root: Path) -> list[str]:
    violations: list[str] = []
    infra = root / "infra"
    if infra.is_dir():
        for path in sorted(infra.rglob("*")):
            if not path.is_file():
                continue
            relative = path.relative_to(root)
            if path.name in FORBIDDEN_INFRA_NAMES or any(
                path.name.endswith(suffix) for suffix in FORBIDDEN_INFRA_SUFFIXES
            ):
                violations.append(f"{relative}: retired infrastructure/state file")

    for path in active_files(root):
        try:
            content = path.read_text(encoding="utf-8")
        except UnicodeDecodeError:
            continue
        relative = path.relative_to(root)
        for label, pattern in FORBIDDEN_CONTENT:
            match = pattern.search(content)
            if match:
                line = content.count("\n", 0, match.start()) + 1
                violations.append(f"{relative}:{line}: {label}")
    return violations


def main() -> int:
    violations = findings(ROOT)
    if violations:
        print("no-executable-opentofu-check: failed", file=sys.stderr)
        for violation in violations:
            print(f"- {violation}", file=sys.stderr)
        return 1
    print("no-executable-opentofu-check: passed")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
