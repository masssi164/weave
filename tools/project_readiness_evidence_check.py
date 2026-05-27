#!/usr/bin/env python3
"""Validate Sprint 5 project-readiness evidence stays mapped and support-safe."""

from __future__ import annotations

import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

REPORT = ROOT / "docs" / "sprint-5-closure-report.md"
MAPPING = ROOT / "e2e" / "scenario_mappings.json"

REQUIRED_REPORT_FRAGMENTS = [
    "# Sprint 5 closure report: project readiness foundation",
    "## Issue and PR graph",
    "## Frozen readiness contract",
    "## Evidence snapshot",
    "## Release-candidate checklist",
    "## Live Stack E2E status and exception",
    "## Sprint 6 entry criteria",
    "#346",
    "#347",
    "#348",
    "#349",
    "#350",
    "#351",
    "#283",
    "Live Stack E2E with real provider credentials was not run",
]

REQUIRED_MAPPINGS = {
    "V01_ORG_CONTROL_PLANE_PROVIDER_FACADE": "identity/bootstrap and admin control-plane ownership",
    "V01_ADMIN_POLICY_DECIDES_CAPABILITIES": "effective policy before provider access",
    "V01_CANONICAL_PROVIDER_NEUTRAL_MODELS": "core domain adapter-fit contracts",
    "WEAVE_CHAT_DOMAIN_FACADE": "provider replacement dry-run and anti-silo evidence",
    "V01_ADMIN_CONSOLE_MVP": "Admin Console readiness UX boundary",
    "V01_OPERATOR_RELEASE_PATH": "operator/release evidence path",
}

REQUIRED_SOURCE_FRAGMENTS = {
    "admin-console/src/App.test.tsx": [
        "renders support-safe provider replacement dry-run evidence",
        "diagnostics redacted: yes",
        "member-visible capability states",
        "not.toHaveTextContent(/secretref",
    ],
    "client/test/architecture/member_client_provider_boundary_contract_test.dart": [
        "member client does not call admin/provider control-plane APIs directly",
        "provider replacement dry-run",
        "secretref://",
    ],
    "server/src/test/java/com/massimotter/weave/backend/controller/AdminControlPlaneControllerTest.java": [
        "adminControlPlaneRejectsMembers",
        "bootstrapRejectsUnsafeAdminRecoveryKeys",
        "adminReadinessTestsAndPolicyUpdatesAreAuditedAndRedacted",
        "migrationDryRunRequired",
    ],
    "server/src/test/java/com/massimotter/weave/backend/provider/DomainAdapterRegistryMapperTest.java": [
        "coreProductDomainsCarryExecutableAdapterFitContracts",
        "providerRegistryAdapterFitSupportsMixedProviderPostureWithoutMemberProviderIds",
    ],
    "build.gradle": [
        "sanitized: true",
        "separate-required-release-evidence",
        "artifactPathsForGate",
    ],
}

LEAK_PATTERNS = [
    (re.compile(r"https://x-access-token:[^\s)]+", re.IGNORECASE), "credential-bearing Git remote URL"),
    (re.compile(r"\bgh[pousr]_[A-Za-z0-9_]{20,}\b"), "GitHub token"),
    (re.compile(r"\bBearer\s+[A-Za-z0-9._~+/=-]{12,}\b", re.IGNORECASE), "bearer token"),
    (re.compile(r"\baccess_token=[^\s)]+", re.IGNORECASE), "access_token query value"),
    (re.compile(r"\b(client_secret|password|api[_-]?key)=([^\s)]+)", re.IGNORECASE), "credential query/value"),
    (re.compile(r"secretref://", re.IGNORECASE), "raw SecretRef URI"),
]


def fail(message: str) -> None:
    print(f"project-readiness-evidence-check: {message}", file=sys.stderr)
    raise SystemExit(1)


def read(path: Path) -> str:
    if not path.exists():
        fail(f"missing required file: {path.relative_to(ROOT)}")
    return path.read_text(encoding="utf-8")


def require_fragments(path: Path, fragments: list[str]) -> None:
    text = read(path)
    missing = [fragment for fragment in fragments if fragment not in text]
    if missing:
        fail(f"{path.relative_to(ROOT)} missing fragments: {missing}")


def check_report() -> None:
    text = read(REPORT)
    missing = [fragment for fragment in REQUIRED_REPORT_FRAGMENTS if fragment not in text]
    if missing:
        fail(f"closure report missing fragments: {missing}")
    for pattern, label in LEAK_PATTERNS:
        match = pattern.search(text)
        if match:
            fail(f"closure report contains {label}: {match.group(0)!r}")


def check_mapping() -> None:
    data = json.loads(read(MAPPING))
    scenarios = data.get("scenarios")
    if not isinstance(scenarios, list):
        fail("scenario_mappings.json must contain a scenarios list")
    by_marker: dict[str, dict] = {}
    for scenario in scenarios:
        for marker in scenario.get("evidenceMarkers", []):
            by_marker[marker] = scenario
    missing = [f"{marker} ({purpose})" for marker, purpose in REQUIRED_MAPPINGS.items() if marker not in by_marker]
    if missing:
        fail(f"missing Sprint 5 acceptance mappings: {missing}")
    for marker in REQUIRED_MAPPINGS:
        scenario = by_marker[marker]
        executable = scenario.get("executableTest")
        if not executable or not (ROOT / executable).exists():
            fail(f"mapping {marker} points to missing executableTest: {executable!r}")
        if marker in {"WEAVE_CHAT_DOMAIN_FACADE", "V01_ADMIN_CONSOLE_MVP"}:
            evidence_text = json.dumps(scenario.get("additionalEvidence", []), sort_keys=True)
            required = "providerReplacementDryRun" if marker == "WEAVE_CHAT_DOMAIN_FACADE" else "Talks only to Weave backend admin APIs"
            if required not in evidence_text:
                fail(f"mapping {marker} lacks required evidence fragment {required!r}")


def check_source_evidence() -> None:
    for relative, fragments in REQUIRED_SOURCE_FRAGMENTS.items():
        require_fragments(ROOT / relative, fragments)


def main() -> None:
    check_report()
    check_mapping()
    check_source_evidence()
    print("project-readiness-evidence-check: ok")


if __name__ == "__main__":
    main()
