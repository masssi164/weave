#!/usr/bin/env python3
"""Validate the enterprise target architecture acceptance spine."""

from __future__ import annotations

import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

MARKERS = {
    "ENTERPRISE_TARGET_DECISION_LOCK": [
        ROOT / "docs/architecture/adr-006-enterprise-hard-plan-decision-lock.md",
        ROOT / "e2e/features/enterprise_target_architecture.feature",
    ],
    "ENTERPRISE_TARGET_OPEN_STANDARD_NORTHBOUND": [
        ROOT / "docs/architecture/adr-006-enterprise-hard-plan-decision-lock.md",
        ROOT / "docs/architecture.md",
        ROOT / "e2e/features/enterprise_target_architecture.feature",
    ],
    "ENTERPRISE_TARGET_OPENAPI_CONTROL_PLANE_ONLY": [
        ROOT / "docs/architecture/adr-004-server-openapi-contract-authority.md",
        ROOT / "docs/architecture/adr-006-enterprise-hard-plan-decision-lock.md",
        ROOT / "e2e/features/enterprise_target_architecture.feature",
    ],
    "ENTERPRISE_TARGET_NO_TRANSITIONAL_COMPATIBILITY": [
        ROOT / "docs/architecture/adr-006-enterprise-hard-plan-decision-lock.md",
        ROOT
        / "docs/architecture/adr-008-no-transitional-compatibility-as-architecture.md",
        ROOT / "e2e/features/enterprise_target_architecture.feature",
    ],
    "ENTERPRISE_TARGET_BOUNDARY_GATE": [
        ROOT
        / "server/src/test/java/com/massimotter/weave/backend/architecture/ServerArchitectureBoundaryTest.java",
        ROOT / "e2e/features/enterprise_target_architecture.feature",
    ],
    "ENTERPRISE_TARGET_E2E_SPINE": [
        ROOT / "e2e/scenario_mappings.json",
        ROOT / "e2e/suites/scenario_catalog.json",
        ROOT / "e2e/features/enterprise_target_architecture.feature",
    ],
    "ENTERPRISE_TARGET_PERSISTENCE_FOUNDATION": [
        ROOT / "docs/architecture/adr-007-persistence-entity-strategy.md",
        ROOT
        / "server/src/test/java/com/massimotter/weave/backend/provider/JdbcProviderSelectionRepositoryTest.java",
        ROOT / "e2e/features/enterprise_target_architecture.feature",
    ],
    "ENTERPRISE_TARGET_AUDIT_PERSISTENCE_FOUNDATION": [
        ROOT / "docs/architecture/adr-007-persistence-entity-strategy.md",
        ROOT / "server/src/test/java/com/massimotter/weave/backend/audit/JdbcAuditEventPublisherTest.java",
        ROOT / "e2e/features/enterprise_target_architecture.feature",
    ],
    "ENTERPRISE_TARGET_MIGRATION_EVIDENCE_PERSISTENCE_FOUNDATION": [
        ROOT / "docs/architecture/adr-007-persistence-entity-strategy.md",
        ROOT
        / "server/src/test/java/com/massimotter/weave/backend/service/migration/JdbcMigrationRunEvidenceRepositoryTest.java",
        ROOT / "e2e/features/enterprise_target_architecture.feature",
    ],
    "ENTERPRISE_TARGET_PROVIDER_SWITCH_NO_DRIFT_FOUNDATION": [
        ROOT / "docs/architecture/adr-007-persistence-entity-strategy.md",
        ROOT / "server/src/test/java/com/massimotter/weave/backend/service/AdminControlPlaneServiceTest.java",
        ROOT / "e2e/features/enterprise_target_architecture.feature",
    ],
}

REQUIRED_ISSUES = {
    "#1011",
    "#1012",
    "#1013",
    "#1014",
    "#1015",
    "#1016",
    "#1017",
    "#1018",
    "#1019",
    "#1020",
    "#1021",
    "#1022",
    "#1023",
    "#1024",
    "#1025",
    "#1031",
}


def fail(message: str) -> None:
    print(f"enterprise-target-architecture-spine-check: {message}", file=sys.stderr)
    sys.exit(1)


def text(path: Path) -> str:
    if not path.exists():
        fail(f"missing required file {path.relative_to(ROOT)}")
    return path.read_text()


def main() -> int:
    combined = "\n".join(text(path) for paths in MARKERS.values() for path in paths)
    for marker, paths in MARKERS.items():
        marker_text = "\n".join(text(path) for path in paths)
        if marker not in marker_text:
            fail(f"missing marker {marker}")

    adr = text(ROOT / "docs/architecture/adr-006-enterprise-hard-plan-decision-lock.md")
    missing_issues = sorted(issue for issue in REQUIRED_ISSUES if issue not in adr)
    if missing_issues:
        fail("ADR does not link required issue refs: " + ", ".join(missing_issues))

    mapping = json.loads(text(ROOT / "e2e/scenario_mappings.json"))
    tags = {scenario.get("tag") for scenario in mapping.get("scenarios", [])}
    expected_tags = {
        "@enterprise-target-decision-lock",
        "@enterprise-target-open-standard-northbound",
        "@enterprise-target-openapi-control-plane-only",
        "@enterprise-target-no-transitional-compatibility",
        "@enterprise-target-boundary-gate",
        "@enterprise-target-e2e-spine",
        "@enterprise-target-persistence-foundation",
        "@enterprise-target-audit-persistence-foundation",
        "@enterprise-target-migration-evidence-persistence-foundation",
        "@enterprise-target-provider-switch-no-drift-foundation",
    }
    missing_tags = sorted(expected_tags - tags)
    if missing_tags:
        fail("scenario_mappings.json misses target tags: " + ", ".join(missing_tags))

    catalog = json.loads(text(ROOT / "e2e/suites/scenario_catalog.json"))
    catalog_tags = {scenario.get("tag") for scenario in catalog.get("scenarios", [])}
    missing_catalog_tags = sorted(expected_tags - catalog_tags)
    if missing_catalog_tags:
        fail("scenario_catalog.json misses target tags: " + ", ".join(missing_catalog_tags))

    if "credential-bearing locations" not in combined or "private operator paths" not in combined:
        fail("support-safe evidence exclusions are not recorded")

    if "Transitional behavior is not architecture" not in combined:
        fail("no-transitional-compatibility decision is not recorded")

    print(
        "enterprise-target-architecture-spine-check: ok "
        f"markers={len(MARKERS)} issues={len(REQUIRED_ISSUES)} scenarios={len(expected_tags)}"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
