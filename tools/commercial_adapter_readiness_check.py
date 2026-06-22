#!/usr/bin/env python3
"""Validate Sprint 28 commercial adapter readiness specs and fail-closed implementation guard."""

from __future__ import annotations

import json
import re
import sys
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[1]
DOC = ROOT / "docs" / "CommercialAdapterReadiness.md"
MATRIX = ROOT / "release" / "commercial-adapter-readiness" / "go-no-go-matrix.json"
GUARD = ROOT / "release" / "commercial-adapter-readiness" / "implementation-guard.fixture.json"
FEATURE = ROOT / "e2e" / "features" / "sprint_28_commercial_adapter_readiness.feature"
MAPPING = ROOT / "e2e" / "scenario_mappings.json"
RELEASE_NOTES = ROOT / "docs" / "release-notes" / "unreleased.md"

REQUIRED_ISSUES = {647, 648, 649, 650}
REQUIRED_PROVIDER_KEYS = {"microsoft-teams", "slack"}
REQUIRED_TOPICS = [
    "Auth model",
    "API rights",
    "Rate limits",
    "History export",
    "Attachment export",
    "User and guest mapping",
    "Thread mapping",
    "Retention",
    "E2EE/compliance limits",
    "Costs",
    "Admin consent",
    "Rollback capability",
]
FORBIDDEN_CLAIMS = [
    "Slack integration is implemented",
    "Slack integration is available",
    "Microsoft Teams integration is implemented",
    "Microsoft Teams integration is available",
]
ALLOWED_FORBIDDEN_CONTEXT = (
    "blocked",
    "do not claim",
    "unsupportedclaimsblocked",
    "blockedclaims",
    "without claiming",
    "avoid",
    "no ",
)
PROVIDER_TERMS = (
    "slack",
    "microsoft teams",
    "microsoft-teams",
    "microsoft graph",
    "ms teams",
    "teams adapter",
    "teams provider",
    "teams integration",
    "teams connector",
    "teamsadapter",
    "teamsprovider",
    "teamsconnector",
)
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
    print(f"commercial-adapter-readiness-check: {message}", file=sys.stderr)
    raise SystemExit(1)


def read(path: Path) -> str:
    try:
        return path.read_text(encoding="utf-8")
    except FileNotFoundError:
        fail(f"missing {path.relative_to(ROOT)}")


def load_json(path: Path) -> dict[str, Any]:
    try:
        data = json.loads(read(path))
    except json.JSONDecodeError as error:
        fail(f"invalid JSON in {path.relative_to(ROOT)}: {error}")
    if not isinstance(data, dict):
        fail(f"{path.relative_to(ROOT)} must be a JSON object")
    return data


def assert_fragments(path: Path, fragments: list[str]) -> None:
    text = read(path)
    for fragment in fragments:
        if fragment not in text:
            fail(f"{path.relative_to(ROOT)} missing {fragment!r}")


def assert_support_safe(value: Any, label: str) -> None:
    if isinstance(value, dict):
        for child in value.values():
            assert_support_safe(child, label)
        return
    if isinstance(value, list):
        for child in value:
            assert_support_safe(child, label)
        return
    if isinstance(value, str):
        for pattern in FORBIDDEN_VALUE_PATTERNS:
            if pattern.search(value):
                fail(f"{label} contains support-unsafe pattern {pattern.pattern!r}")


def provider_reference_files(roots: list[str]) -> list[str]:
    files: list[str] = []
    for root_rel in roots:
        root = ROOT / root_rel
        if not root.exists():
            continue
        for path in root.rglob("*"):
            if not path.is_file():
                continue
            rel = str(path.relative_to(ROOT))
            rel_path = path.relative_to(ROOT)
            if any(part in {".generated", ".terraform", "build"} for part in rel_path.parts):
                continue
            if rel_path.parts[:3] == ("client", "lib", "generated"):
                continue
            if path.name.startswith("terraform.tfstate"):
                continue
            try:
                text = path.read_text(encoding="utf-8", errors="ignore").lower()
            except UnicodeError:
                continue
            haystack = f"{rel.lower()}\n{text}"
            if any(term in haystack for term in PROVIDER_TERMS):
                files.append(rel)
    return sorted(files)


def assert_claims_blocked(paths: list[Path]) -> None:
    for path in paths:
        text = read(path)
        for line_number, line in enumerate(text.splitlines(), start=1):
            normalized = re.sub(r"\s+", " ", line).strip()
            lowered = normalized.lower()
            for claim in FORBIDDEN_CLAIMS:
                if claim.lower() in lowered and not any(hint in lowered for hint in ALLOWED_FORBIDDEN_CONTEXT):
                    fail(f"{path.relative_to(ROOT)}:{line_number} contains unblocked commercial claim: {normalized}")


def main() -> None:
    doc = read(DOC)
    for heading in ["# Commercial Adapter Readiness", "## Microsoft Teams readiness section", "## Slack readiness section", "## Commercial go/no-go summary"]:
        if heading not in doc:
            fail(f"readiness doc missing heading {heading!r}")
    for topic in REQUIRED_TOPICS:
        if doc.count(topic) < 2:
            fail(f"readiness doc must cover {topic!r} for both Teams and Slack")
    for phrase in [
        "Sprint 28 readiness/specification evidence only",
        "This document does not approve or implement Slack or Microsoft Teams adapters.",
        "Do not claim Slack or Teams integration is implemented",
    ]:
        if phrase not in doc:
            fail(f"readiness doc missing boundary phrase {phrase!r}")

    matrix = load_json(MATRIX)
    if matrix.get("artifactKind") != "weave-commercial-adapter-go-no-go-matrix-v1" or matrix.get("sprint") != 28:
        fail("go/no-go matrix kind or sprint mismatch")
    if set(matrix.get("issues", [])) != REQUIRED_ISSUES:
        fail("go/no-go matrix must cover issues 647, 648, 649, and 650")
    providers = {entry.get("providerKey"): entry for entry in matrix.get("providers", []) if isinstance(entry, dict)}
    if set(providers) != REQUIRED_PROVIDER_KEYS:
        fail("go/no-go matrix must contain only microsoft-teams and slack providers")
    for key, entry in providers.items():
        if entry.get("decision") != "blocked" or entry.get("implementationStartAllowed") is not False:
            fail(f"{key} implementation start must remain blocked")
        proof = entry.get("requiredProofBeforeImplementation")
        if not isinstance(proof, list) or len(proof) < 10:
            fail(f"{key} must list detailed proof before implementation")
        if not entry.get("blockingReasons"):
            fail(f"{key} must list blocking reasons")
    for claim in FORBIDDEN_CLAIMS:
        if claim not in matrix.get("unsupportedClaimsBlocked", []):
            fail(f"matrix missing blocked claim {claim!r}")
    assert_support_safe(matrix, "go/no-go matrix")

    guard = load_json(GUARD)
    if (
        guard.get("artifactKind") != "weave-commercial-adapter-implementation-guard-v1"
        or guard.get("issue") != 650
        or guard.get("sprint") != 28
    ):
        fail("implementation guard fixture kind/issue/sprint mismatch")
    if guard.get("implementationStartDefault") != "blocked":
        fail("implementation guard must default to blocked")
    if guard.get("approvedImplementationProviders") != []:
        fail("no commercial provider implementation may be approved in Sprint 28")
    if set(guard.get("guardedProviderKeys", [])) != REQUIRED_PROVIDER_KEYS:
        fail("implementation guard must cover Teams and Slack")
    allowed_readiness_paths = set(guard.get("allowedReadinessOnlyPaths", []))
    actual_refs = [
        ref
        for ref in provider_reference_files(list(guard.get("sourceRootsScanned", [])))
        if ref not in allowed_readiness_paths
    ]
    expected_refs = sorted(guard.get("preExistingReadinessOnlyOrSandboxReferences", []))
    if actual_refs != expected_refs:
        added = sorted(set(actual_refs) - set(expected_refs))
        removed = sorted(set(expected_refs) - set(actual_refs))
        if added:
            fail("new unapproved commercial provider source reference(s): " + ", ".join(added))
        fail("commercial provider source reference baseline changed; removed: " + ", ".join(removed))
    assert_support_safe(guard, "implementation guard fixture")

    assert_fragments(FEATURE, ["@sprint28-commercial-adapter-readiness", "Microsoft Teams implementation remains blocked", "Slack implementation remains blocked"])
    assert_fragments(MAPPING, ["@sprint28-commercial-adapter-readiness", "COMMERCIAL_ADAPTER_READINESS_PROOF", str(MATRIX.relative_to(ROOT)), str(GUARD.relative_to(ROOT))])
    assert_claims_blocked([DOC, RELEASE_NOTES, ROOT / "README.md"])

    print("commercial-adapter-readiness-check: ok sprint=28 providers=slack,microsoft-teams decision=blocked")
    print("COMMERCIAL_ADAPTER_READINESS_PROOF")


if __name__ == "__main__":
    main()
