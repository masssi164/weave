#!/usr/bin/env python3
"""Check the offline architecture acceptance spine against the pinned release scope.

This guard checks checked-in contracts and mappings. It never reports live E2E success.
"""

from __future__ import annotations

import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
FEATURE = ROOT / "e2e/features/enterprise_target_architecture.feature"
DOGFOOD_FEATURE = ROOT / "e2e/features/v0_1_dogfood_release.feature"
MAPPINGS = ROOT / "e2e/scenario_mappings.json"
CATALOG = ROOT / "e2e/suites/scenario_catalog.json"
LOCK = ROOT / "specs/weave-specs.lock.json"
SOURCE_OF_TRUTH = ROOT / "docs/specification-source-of-truth.md"
E2E_README = ROOT / "e2e/README.md"
SERVER_BOUNDARY_TEST = ROOT / "server/src/test/java/com/massimotter/weave/backend/architecture/ServerArchitectureBoundaryTest.java"

# Keep stable historical tags while their scenarios assert the approved target.
MARKERS = {
    "@enterprise-target-decision-lock": "ENTERPRISE_TARGET_DECISION_LOCK",
    "@enterprise-target-open-standard-northbound": "ENTERPRISE_TARGET_OPEN_STANDARD_NORTHBOUND",
    "@enterprise-target-openapi-control-plane-only": "ENTERPRISE_TARGET_OPENAPI_CONTROL_PLANE_ONLY",
    "@enterprise-target-no-transitional-compatibility": "ENTERPRISE_TARGET_NO_TRANSITIONAL_COMPATIBILITY",
    "@enterprise-target-boundary-gate": "ENTERPRISE_TARGET_BOUNDARY_GATE",
    "@enterprise-target-e2e-spine": "ENTERPRISE_TARGET_E2E_SPINE",
    "@enterprise-target-persistence-foundation": "ENTERPRISE_TARGET_PERSISTENCE_FOUNDATION",
    "@enterprise-target-audit-persistence-foundation": "ENTERPRISE_TARGET_AUDIT_PERSISTENCE_FOUNDATION",
    "@enterprise-target-migration-evidence-persistence-foundation": "ENTERPRISE_TARGET_MIGRATION_EVIDENCE_PERSISTENCE_FOUNDATION",
    "@enterprise-target-provider-switch-no-drift-foundation": "ENTERPRISE_TARGET_PROVIDER_SWITCH_NO_DRIFT_FOUNDATION",
    "@weave-v01-agent-runtime-control-policy": "V01_AGENT_RUNTIME_CONTROL_POLICY",
}

SUPERSEDED_EVIDENCE = {
    "docs/architecture.md",
    "docs/architecture/adr-004-server-openapi-contract-authority.md",
    "docs/architecture/adr-006-enterprise-hard-plan-decision-lock.md",
    "docs/architecture/adr-007-persistence-entity-strategy.md",
}
CURRENT_SCOPE_TAGS = {
    "@enterprise-target-decision-lock",
    "@enterprise-target-open-standard-northbound",
    "@enterprise-target-openapi-control-plane-only",
    "@enterprise-target-no-transitional-compatibility",
    "@weave-v01-agent-runtime-control-policy",
}


def fail(message: str) -> None:
    print(f"enterprise-target-architecture-spine-check: {message}", file=sys.stderr)
    raise SystemExit(1)


def read(path: Path) -> str:
    if not path.is_file():
        fail(f"missing required file {path.relative_to(ROOT)}")
    return path.read_text(encoding="utf-8")


def require(text: str, context: str, *fragments: str) -> None:
    for fragment in fragments:
        if fragment not in text:
            fail(f"{context} is missing current-release fragment {fragment!r}")


def main() -> int:
    lock = json.loads(read(LOCK))
    commit = lock.get("specCorpus", {}).get("gitCommit", "")
    if not isinstance(commit, str) or not re.fullmatch(r"[0-9a-f]{40}", commit):
        fail("spec corpus lock needs an exact commit")

    source = read(SOURCE_OF_TRUTH)
    readme = read(E2E_README)
    feature = read(FEATURE)
    dogfood_feature = read(DOGFOOD_FEATURE)
    require(source, "source-of-truth policy", "#1470 consolidation release", "release-2026-10-product-consolidation.md")
    require(
        readme,
        "E2E scope",
        "#1470/#1480",
        "separate User and Admin OpenAPI artifacts",
        "Flutter native Matrix SDK",
        "DAV remains private inside",
        "effective allow and deny permissions",
        "Offline dry-run/JPA tests",
        "offline specification check",
    )
    require(
        feature,
        "enterprise feature",
        "separate generated User and Admin APIs",
        "bounded Weave Matrix facade",
        "Files migration and rollback belong to #1498",
        "effective permissions",
        "private Runner",
        "offline dry-run does not claim activation",
        "support-safe evidence excludes secrets, raw provider payloads",
        "read/write ordering and restart recovery",
        "H2-only evidence is not claimed as PostgreSQL production readiness",
        "without enabling provider-switch apply",
    )
    for obsolete in (
        'Files uses the Weave WebDAV projection under "/dav/files"',
        'Calendar uses the Weave CalDAV and iCalendar projection under "/caldav"',
        "OpenAPI does not become the normal collaboration data plane",
        "Calls uses MatrixRTC Profile 0 signaling",
    ):
        if obsolete in feature:
            fail(f"enterprise feature still asserts superseded architecture: {obsolete}")
    require(read(SERVER_BOUNDARY_TEST), "server architecture guard", "ENTERPRISE_TARGET_BOUNDARY_GATE")

    mapping = json.loads(read(MAPPINGS))
    by_tag = {item.get("tag"): item for item in mapping.get("scenarios", [])}
    catalog = json.loads(read(CATALOG))
    catalog_tags = {item.get("tag") for item in catalog.get("scenarios", [])}
    for tag, marker in MARKERS.items():
        item = by_tag.get(tag)
        if item is None or tag not in catalog_tags or tag not in feature + dogfood_feature:
            fail(f"missing feature, mapping, or catalog tag {tag}")
        if marker not in item.get("evidenceMarkers", []):
            fail(f"mapping {tag} does not retain marker {marker}")
        if item.get("evidenceMode") != "offline-spec":
            fail(f"mapping {tag} must remain offline-spec until live behavior is proved")
        executable = ROOT / item.get("executableTest", "")
        if marker not in read(executable):
            fail(f"mapping {tag} marker is absent from its executable test")
        if tag in CURRENT_SCOPE_TAGS:
            for evidence in item.get("additionalEvidence", []):
                if evidence.get("path") in SUPERSEDED_EVIDENCE:
                    fail(f"mapping {tag} still cites superseded architecture as proof")

    for tag in (
        "@enterprise-target-persistence-foundation",
        "@enterprise-target-audit-persistence-foundation",
        "@enterprise-target-migration-evidence-persistence-foundation",
        "@enterprise-target-provider-switch-no-drift-foundation",
    ):
        paths = {entry.get("path") for entry in by_tag[tag].get("additionalEvidence", [])}
        if not any(path and path.startswith("server/src/test/") for path in paths):
            fail(f"mapping {tag} lost its independent server test reference")

    print(
        "enterprise-target-architecture-spine-check: ok "
        f"offline-scope-tags={len(MARKERS)} pinned-corpus={commit[:12]}"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
