#!/usr/bin/env python3
"""Validate Sprint 27 local CI/CD bootstrapper evidence for issue #666."""

from __future__ import annotations

import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
GO_ROOT = ROOT / "tools" / "weave-setup"
CORE = GO_ROOT / "internal" / "bootstrap" / "core.go"
CLI = GO_ROOT / "cmd" / "weave-setup" / "main.go"
TEST = GO_ROOT / "internal" / "bootstrap" / "core_test.go"
FIXTURE = ROOT / "release" / "provider-lab" / "local-cicd-bootstrapper" / "support-safe-plan.fixture.json"
DOC = ROOT / "docs" / "evidence" / "local-cicd-bootstrapper.md"
FEATURE = ROOT / "e2e" / "features" / "sprint_27_local_cicd_bootstrapper.feature"
MAPPING = ROOT / "e2e" / "scenario_mappings.json"

FORBIDDEN_VALUE_PATTERNS = [
    re.compile(pattern, re.IGNORECASE)
    for pattern in [
        r"bearer\s+[a-z0-9._-]+",
        r"gh[pousr]_[a-z0-9_]{12,}",
        r"(token|secret|password|private[_-]?key)\s*[:=]\s*[^\s,}\"]+",
        r"-----begin\s+(rsa|dsa|ec|openssh|private)\s+private\s+key-----",
    ]
]


def fail(message: str) -> None:
    print(f"local-cicd-bootstrapper-check: {message}", file=sys.stderr)
    raise SystemExit(1)


def read(path: Path) -> str:
    try:
        return path.read_text(encoding="utf-8")
    except FileNotFoundError:
        fail(f"missing {path.relative_to(ROOT)}")


def assert_fragments(path: Path, fragments: list[str]) -> None:
    content = read(path)
    for fragment in fragments:
        if fragment not in content:
            fail(f"{path.relative_to(ROOT)} missing {fragment!r}")


def assert_support_safe_text(path: Path) -> None:
    content = read(path)
    for pattern in FORBIDDEN_VALUE_PATTERNS:
        if pattern.search(content):
            fail(f"{path.relative_to(ROOT)} contains forbidden secret-like value pattern {pattern.pattern!r}")


def main() -> None:
    assert_fragments(CORE, ["TargetForgejo", "TargetGitHub", "Detect", "BuildPlan", "GitHubSecretsRequired", "validateNoSecrets"])
    assert_fragments(CLI, ["case \"app\"", "case \"detect\"", "case \"validate\"", "case \"plan\", \"init\"", "case \"commit\"", "case \"push\""])
    assert_fragments(TEST, ["TestDetectCICDFiles", "TestForgejoPlanDoesNotRequireGitHubSecrets", "TestRejectsRawSecretPersistence", "TestConflictExistingGitHubWorkflowWithForgejoTarget"])
    data = json.loads(read(FIXTURE))
    if data.get("artifactKind") != "weave-local-cicd-bootstrap-plan-v1" or data.get("issue") != 666:
        fail("fixture kind/issue mismatch")
    rules = data.get("targetRules", {})
    if rules.get("forgejoRequiresGitHubSecrets") is not False:
        fail("Forgejo path must not require GitHub secrets")
    if rules.get("pushOnlySelectedTarget") is not True or rules.get("explicitPushRequired") is not True:
        fail("fixture must fail closed for push target selection")
    for target in ["forgejo", "github-actions", "gitlab-ci", "azure-devops"]:
        if target not in rules.get("supportedTargets", []):
            fail(f"fixture missing target {target}")
    assert_fragments(DOC, ["Go-built executable", "weave-setup app", "GitHub repository secrets are backend-specific", "must not claim runner registration"])
    assert_fragments(FEATURE, ["@sprint27-local-cicd-bootstrapper", "GitHub repository secrets are not required for the Forgejo path", "selected Forgejo remote and branch"])
    mapping = read(MAPPING)
    if "@sprint27-local-cicd-bootstrapper" not in mapping or "LOCAL_CICD_BOOTSTRAPPER_PROOF" not in mapping:
        fail("scenario mapping missing Sprint 27 bootstrapper proof")
    for path in [FIXTURE, DOC, FEATURE]:
        assert_support_safe_text(path)
    print("local-cicd-bootstrapper-check: ok issue=666 target=forgejo github_secrets_required=false")
    print("LOCAL_CICD_BOOTSTRAPPER_PROOF")


if __name__ == "__main__":
    main()
