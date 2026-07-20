#!/usr/bin/env python3
"""Validate Sprint 21 product-reality levels and forbidden release claims."""

from __future__ import annotations

import json
import re
import sys
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[1]
GATES = ROOT / "release" / "product-reality-gates.json"
FOUNDATION = ROOT / "docs" / "product-reality-foundation.md"
CLAIM_MATRIX = ROOT / "docs" / "product-trust-provider-choice-claim-matrix.md"
PRODUCT_LINE = ROOT / "docs" / "product-line-and-weaver-plan.md"
RELEASE_NOTES = ROOT / "docs" / "release-notes" / "unreleased.md"
README = ROOT / "README.md"

EXPECTED_LEVELS = [
    "contract_only",
    "configured",
    "live_read",
    "live_write",
    "migration_dry_run",
    "migration_apply_ready",
    "rollback_ready",
    "release_ready",
]

REQUIRED_FORBIDDEN = [
    "Providers are interchangeable",
    "Weaver is available",
    "A PA runs per user",
    "History remains fully preserved",
    "Rollback works in production",
    "v0.1 is release-ready",
]

REQUIRED_DOMAINS = {
    "identity": {"keycloak", "authentik"},
    "chat": {"matrix-synapse", "zulip"},
    "files": {"nextcloud", "minio-s3"},
    "calendar": {"nextcloud-caldav", "radicale"},
    "boards": {"openproject"},
    "agent-runtime-control": {"weaver-openclaw"},
}

REQUIRED_CANONICAL = {
    "chat": {"WeaveSpace", "WeaveConversation", "WeaveMessage", "WeaveThread", "WeaveReaction", "WeaveAttachment", "WeaveMembership", "WeaveHistoryPolicy", "ProviderRef", "MigrationReceipt", "RollbackReceipt", "LossyFieldReport"},
    "files": {"WeaveDrive", "WeaveFolder", "WeaveFile", "WeaveVersion", "WeaveShare", "WeavePermission", "WeaveLock", "WeaveQuota", "ProviderRef"},
    "calendar": {"WeaveCalendar", "WeaveEvent", "WeaveRecurrence", "WeaveAttendee", "WeaveResource", "WeaveAvailability", "ProviderRef"},
    "agent-runtime-control": {"RuntimeEntitlementRef", "RuntimeProfile", "ApprovalChallenge", "RuntimeCell", "WorkspaceRevision", "RuntimeRevocation", "RuntimeAuditCorrelation"},
}

ALLOWED_CONTEXT_HINTS = (
    "forbidden",
    "forbiddenclaim",
    "do not claim",
    "must not claim",
    "until evidenced",
    "until named evidence",
    "blocked",
    "not claim",
    "nonclaim",
    "non-claim",
    "cannot claim",
    "claim boundary",
    "not customer-ready",
    "not proof",
    "release-ready remains blocked",
)

SCAN_PATHS = [
    README,
    PRODUCT_LINE,
    CLAIM_MATRIX,
    RELEASE_NOTES,
    FOUNDATION,
    ROOT / "docs" / "release-v0.1-rc3-evidence.md",
    ROOT / "docs" / "admin-operator-handbook.md",
    ROOT / "docs" / "domain-registry-v1.md",
    ROOT / "client" / "test" / "features" / "settings" / "settings_screen_test.dart",
]

OLD_REALITY_LEVELS = [
    "configured_readiness",
    "live_adapter_read",
    "live_adapter_write",
]


def fail(message: str) -> None:
    print(f"product-reality-claim-gate-check: {message}", file=sys.stderr)
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
        fail(f"{path.relative_to(ROOT)} must contain a JSON object")
    return data


def line_allowed(line: str) -> bool:
    lowered = line.lower()
    return any(hint in lowered for hint in ALLOWED_CONTEXT_HINTS)


def main() -> None:
    gates = load_json(GATES)
    if gates.get("releasePrinciple") != "No statement is release-capable unless proven by E2E/runtime/migration/rollback evidence.":
        fail("release principle is missing or changed")
    if gates.get("allowedClaim") != "Weave builds provider-neutral domains and proves them first with free self-hosted providers.":
        fail("allowed claim is missing or changed")
    if gates.get("realityLevels") != EXPECTED_LEVELS:
        fail("realityLevels must match the exact ordered Sprint 21 list")
    serialized_gates = json.dumps(gates)
    for old_level in OLD_REALITY_LEVELS:
        if old_level in serialized_gates:
            fail(f"release gates still contain rejected old reality level {old_level}")
    if gates.get("onlyCustomerReadyLevel") != "release_ready":
        fail("onlyCustomerReadyLevel must be release_ready")
    missing_forbidden = set(REQUIRED_FORBIDDEN) - set(gates.get("forbiddenClaimsUntilEvidence", []))
    if missing_forbidden:
        fail("missing forbidden claim(s): " + ", ".join(sorted(missing_forbidden)))

    lab = gates.get("providerLab")
    if not isinstance(lab, list):
        fail("providerLab must be a list")
    by_domain: dict[str, set[str]] = {}
    for entry in lab:
        if not isinstance(entry, dict):
            fail("providerLab entries must be objects")
        domain = entry.get("domain")
        providers = entry.get("providers")
        level = entry.get("minimumSprint21RealityLevel")
        if not isinstance(domain, str) or not isinstance(providers, list):
            fail("providerLab entries require domain and providers")
        if level not in EXPECTED_LEVELS:
            fail(f"providerLab {domain} has invalid reality level {level!r}")
        by_domain[domain] = {str(provider) for provider in providers}
    for domain, required_providers in REQUIRED_DOMAINS.items():
        if required_providers - by_domain.get(domain, set()):
            fail(f"providerLab domain {domain} missing provider(s): " + ", ".join(sorted(required_providers - by_domain.get(domain, set()))))

    canonical = gates.get("canonicalObjects")
    if not isinstance(canonical, dict):
        fail("canonicalObjects must be an object")
    for domain, required_objects in REQUIRED_CANONICAL.items():
        values = canonical.get(domain)
        if not isinstance(values, list):
            fail(f"canonicalObjects.{domain} must be a list")
        missing = required_objects - set(values)
        if missing:
            fail(f"canonicalObjects.{domain} missing: " + ", ".join(sorted(missing)))

    foundation = read(FOUNDATION)
    for required in [gates["releasePrinciple"], gates["allowedClaim"], "Only `release_ready` may be described as customer-ready", "Human-in-the-Loop Release Validation"]:
        if required not in foundation:
            fail(f"foundation doc missing required phrase: {required}")

    for path in SCAN_PATHS:
        text = read(path)
        for old_level in OLD_REALITY_LEVELS:
            if old_level in text:
                fail(f"{path.relative_to(ROOT)} contains rejected old reality level {old_level}")
        for line_number, line in enumerate(text.splitlines(), start=1):
            normalized = re.sub(r"\s+", " ", line).strip()
            for claim in REQUIRED_FORBIDDEN:
                if claim.lower() in normalized.lower() and not line_allowed(normalized):
                    fail(f"{path.relative_to(ROOT)}:{line_number} contains unsupported claim: {normalized}")
        if "release_ready" in text and "customer-ready" in text and path != FOUNDATION:
            for line_number, line in enumerate(text.splitlines(), start=1):
                if "release_ready" in line and "customer-ready" in line and not line_allowed(line):
                    fail(f"{path.relative_to(ROOT)}:{line_number} has unguarded release_ready/customer-ready wording: {line.strip()}")

    print("product-reality-claim-gate-check: ok")


if __name__ == "__main__":
    main()
