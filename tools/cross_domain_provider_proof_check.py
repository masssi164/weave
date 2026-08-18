#!/usr/bin/env python3
"""Validate Sprint 27 provider boundaries and the fixed Keycloak federation boundary."""

from __future__ import annotations

import json
import re
import sys
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[1]
ARTIFACT_DIR = ROOT / "release" / "provider-lab" / "cross-domain-provider-proof"
CALENDAR = ARTIFACT_DIR / "calendar-nextcloud-radicale.fixture.json"
FILES = ARTIFACT_DIR / "files-nextcloud-minio.fixture.json"
PLATFORM_IDENTITY = ARTIFACT_DIR / "platform-identity-federation.fixture.json"
SCOREBOARD = ARTIFACT_DIR / "sprint-27-cross-domain-scoreboard.json"
CLAIM_GATE = ARTIFACT_DIR / "sprint-27-provider-neutrality-claim-gate.fixture.json"
DOC = ROOT / "docs" / "evidence" / "sprint-27-cross-domain-provider-proof.md"
FEATURE = ROOT / "e2e" / "features" / "sprint_27_cross_domain_provider_proof.feature"
MAPPING = ROOT / "e2e" / "scenario_mappings.json"
MANIFEST_DIR = ROOT / "release" / "provider-lab" / "manifests"

SECRET_PATTERNS = [
    re.compile(pattern, re.IGNORECASE)
    for pattern in [
        r"bearer\s+[a-z0-9._-]+",
        r"gh[pousr]_[a-z0-9_]{12,}",
        r"(token|secret|password|private[_-]?key)\s*[:=]\s*[^\s,}\"]+",
        r"rawProviderPayload",
        r"rawProviderError",
        r"https?://[^\s)\"]+",
        r"ssh://[^\s)\"]+",
        r"-----begin\s+((rsa|dsa|ec|openssh|encrypted)\s+)?private\s+key-----",
    ]
]
FORBIDDEN_KEYS = {
    "secretValue",
    "tokenValue",
    "password",
    "rawProviderPayload",
    "rawProviderError",
    "rawAssertion",
    "rawCiLog",
    "bearerToken",
    "credentialBearingUrl",
    "tenantUrl",
    "memberContent",
}


def fail(message: str) -> None:
    print(f"cross-domain-provider-proof-check: {message}", file=sys.stderr)
    raise SystemExit(1)


def read(path: Path) -> str:
    try:
        return path.read_text(encoding="utf-8")
    except FileNotFoundError:
        fail(f"missing {path.relative_to(ROOT)}")


def load(path: Path) -> dict[str, Any]:
    try:
        data = json.loads(read(path))
    except json.JSONDecodeError as error:
        fail(f"invalid JSON in {path.relative_to(ROOT)}: {error}")
    if not isinstance(data, dict):
        fail(f"{path.relative_to(ROOT)} must contain an object")
    return data


def assert_support_safe(value: Any, label: str, path: tuple[str, ...] = ()) -> None:
    if isinstance(value, dict):
        for key, child in value.items():
            if key in FORBIDDEN_KEYS:
                fail(f"{label} contains forbidden field {'.'.join((*path, key))}")
            assert_support_safe(child, label, (*path, key))
        return
    if isinstance(value, list):
        for index, child in enumerate(value):
            assert_support_safe(child, label, (*path, str(index)))
        return
    if isinstance(value, str):
        for pattern in SECRET_PATTERNS:
            if pattern.search(value):
                fail(f"{label} contains support-unsafe pattern {pattern.pattern!r} at {'.'.join(path)}")


def assert_fragments(path: Path, fragments: list[str]) -> None:
    text = read(path)
    for fragment in fragments:
        if fragment not in text:
            fail(f"{path.relative_to(ROOT)} missing {fragment!r}")


def validate_manifest(provider_key: str, domain: str) -> None:
    manifest = load(MANIFEST_DIR / f"{provider_key}.json")
    if manifest.get("manifestKind") != "weave-provider-lab-provider-manifest":
        fail(f"manifest {provider_key} kind mismatch")
    if manifest.get("providerKey") != provider_key or manifest.get("domain") != domain:
        fail(f"manifest {provider_key} provider/domain mismatch")
    if manifest.get("supportEvidence", {}).get("redacted") is not True:
        fail(f"manifest {provider_key} must be support-safe/redacted")


def validate_domain_fixture(
    artifact: dict[str, Any],
    *,
    label: str,
    kind: str,
    issue: int,
    domain: str,
    source: str,
    target: str,
    required_objects: set[str],
    marker: str,
) -> None:
    if artifact.get("artifactKind") != kind or artifact.get("issue") != issue:
        fail(f"{label} kind/issue mismatch")
    if artifact.get("supportSafe") is not True or artifact.get("realityLevel") != "migration_dry_run":
        fail(f"{label} must be supportSafe migration_dry_run evidence")
    if artifact.get("domain") != domain or artifact.get("sourceProviderKey") != source or artifact.get("targetProviderKey") != target:
        fail(f"{label} provider boundary mismatch")
    objects = set(artifact.get("canonicalObjects", []))
    if objects != required_objects:
        fail(f"{label} canonical object coverage mismatch")
    if len(artifact.get("preservedFields", [])) < 5:
        fail(f"{label} must report preserved fields")
    lossy = artifact.get("lossyFields", [])
    if not isinstance(lossy, list) or len(lossy) < 3:
        fail(f"{label} must report at least three lossy/limited fields")
    for entry in lossy:
        if not {"field", "classification", "adminImpact"}.issubset(entry):
            fail(f"{label} lossy field entries must include field/classification/adminImpact")
    if marker not in artifact.get("evidenceMarkers", []):
        fail(f"{label} missing marker {marker}")
    assert_support_safe(artifact, label)


def validate_platform_identity_fixture(artifact: dict[str, Any]) -> None:
    label = "platform identity"
    if artifact.get("artifactKind") != "weave-sprint-27-platform-identity-federation-proof-v2":
        fail(f"{label} kind mismatch")
    if artifact.get("issue") != 645 or artifact.get("supportSafe") is not True:
        fail(f"{label} issue/support-safety mismatch")
    if artifact.get("domain") != "platform-identity":
        fail(f"{label} domain mismatch")
    if artifact.get("platformAuthority") != "keycloak":
        fail(f"{label} must keep Keycloak as the fixed platform authority")
    if artifact.get("upstreamSourceKind") != "authentik-oidc":
        fail(f"{label} upstream source mismatch")
    if artifact.get("operation") != "federation-readiness-dry-run":
        fail(f"{label} operation mismatch")
    if artifact.get("realityLevel") != "readiness_dry_run":
        fail(f"{label} reality level mismatch")
    if artifact.get("providerSwitchAllowed") is not False:
        fail(f"{label} must forbid a platform identity provider switch")
    expected_objects = {
        "Organization",
        "UserAccount",
        "Person",
        "Group",
        "Role",
        "IdentitySource",
        "CapabilityPolicy",
        "UpstreamIdentitySourceRef",
    }
    if set(artifact.get("canonicalObjects", [])) != expected_objects:
        fail(f"{label} canonical object coverage mismatch")
    if len(artifact.get("preservedFields", [])) < 5:
        fail(f"{label} must report preserved fields")
    risks = artifact.get("federationRisks", [])
    if not isinstance(risks, list) or len(risks) < 3:
        fail(f"{label} must report at least three federation risks")
    for entry in risks:
        if not {"field", "classification", "adminImpact"}.issubset(entry):
            fail(f"{label} risk entries must include field/classification/adminImpact")
    if artifact.get("authEvidenceBoundary", {}).get("containsSecrets") is not False:
        fail(f"{label} auth evidence must contain no secrets")
    if "SPRINT27_PLATFORM_IDENTITY_FEDERATION_PROOF" not in artifact.get("evidenceMarkers", []):
        fail(f"{label} evidence marker missing")
    assert_support_safe(artifact, label)


def validate_scoreboard(scoreboard: dict[str, Any], fixtures: dict[str, dict[str, Any]]) -> None:
    if scoreboard.get("artifactKind") != "weave-sprint-27-cross-domain-provider-proof-scoreboard-v1":
        fail("scoreboard kind mismatch")
    if scoreboard.get("supportSafe") is not True or scoreboard.get("releaseReady") is not False:
        fail("scoreboard must be supportSafe and releaseReady=false")
    for issue in ["643", "644", "645", "646"]:
        if scoreboard.get("issues", {}).get(issue) != "green":
            fail(f"scoreboard issue {issue} must be green")
    levels = scoreboard.get("domainRealityLevels", {})
    for domain, fixture in fixtures.items():
        if levels.get(domain) != fixture.get("realityLevel"):
            fail(f"scoreboard reality level for {domain} must agree with fixture")
    if levels.get("chat") != "migration_apply_ready":
        fail("scoreboard must name chat reality separately from Sprint 27 domains")
    setup = scoreboard.get("setupFlowEvidence", {})
    if setup.get("status") != "obsolete_forgejo_evidence_excluded":
        fail("scoreboard must exclude obsolete Forgejo delivery evidence")
    if setup.get("replacementAuthority") != "github-protected-delivery-lanes":
        fail("scoreboard must name GitHub as the replacement delivery authority")
    for relpath in list(scoreboard.get("domainEvidence", {}).values()) + setup.get("evidenceRefs", []) + [scoreboard.get("claimGateRef")]:
        if not relpath or not (ROOT / relpath).exists():
            fail(f"scoreboard references missing artifact {relpath}")


def validate_claim_gate(claim_gate: dict[str, Any], scoreboard: dict[str, Any]) -> None:
    if claim_gate.get("artifactKind") != "weave-sprint-27-provider-neutrality-claim-gate-v1" or claim_gate.get("issue") != 646:
        fail("claim gate kind/issue mismatch")
    if claim_gate.get("supportSafe") is not True or claim_gate.get("setupFlowEvidenceSeparated") is not True:
        fail("claim gate must be supportSafe and separate setup-flow evidence")
    claim = claim_gate.get("acceptedClaim", {}).get("claim", "")
    for phrase in [
        "Calendar",
        "Files",
        "Keycloak",
        "federation",
        "setup-flow evidence named separately",
        "broad provider-neutrality wording still blocked",
    ]:
        if phrase not in claim:
            fail(f"accepted claim missing {phrase!r}")
    rejected = "\n".join(item.get("claim", "") + " " + item.get("reason", "") for item in claim_gate.get("rejectedClaims", []))
    for phrase in ["Chat switch evidence alone", "customer-ready", "identity authority is provider-switchable"]:
        if phrase.lower() not in rejected.lower():
            fail(f"claim gate missing rejected overclaim {phrase!r}")
    if claim_gate.get("scoreboardRef") != str(SCOREBOARD.relative_to(ROOT)):
        fail("claim gate scoreboardRef mismatch")
    for relpath in claim_gate.get("requiredDomainEvidence", []) + claim_gate.get("setupFlowEvidenceRefs", []):
        if not (ROOT / relpath).exists():
            fail(f"claim gate references missing artifact {relpath}")
    if "separate" not in claim_gate.get("separateIssue665Boundary", "").lower():
        fail("claim gate must describe #665 as separate")
    assert_support_safe({"claim_gate": claim_gate, "scoreboard": scoreboard}, "claim gate and scoreboard")


def validate_safety_regression_samples() -> None:
    pattern_samples = [
        "https://nextcloud.example.invalid/remote.php/dav/calendars/member/work",
        "ssh://git@example.invalid/org/repo.git",
        "-----BEGIN ENCRYPTED PRIVATE KEY-----",
    ]
    for sample in pattern_samples:
        if not any(pattern.search(sample) for pattern in SECRET_PATTERNS):
            fail(f"support-safety regression sample was not rejected: {sample!r}")
    for key in ["rawCiLog", "tenantUrl", "memberContent"]:
        if key not in FORBIDDEN_KEYS:
            fail(f"support-safety forbidden key is missing: {key}")


def validate_docs_and_mapping() -> None:
    assert_fragments(DOC, [
        "#643", "#644", "#645", "#646", "Historical #665 evidence is obsolete",
        "release/provider-lab/cross-domain-provider-proof/calendar-nextcloud-radicale.fixture.json",
        "release/provider-lab/cross-domain-provider-proof/files-nextcloud-minio.fixture.json",
        "release/provider-lab/cross-domain-provider-proof/platform-identity-federation.fixture.json",
        "release/provider-lab/cross-domain-provider-proof/sprint-27-provider-neutrality-claim-gate.fixture.json",
    ])
    assert_fragments(FEATURE, [
        "@sprint27-calendar-provider-boundary",
        "@sprint27-files-provider-boundary",
        "@sprint27-platform-identity-federation-boundary",
        "@sprint27-provider-neutrality-claim-gate",
        "setup-flow evidence is named separately",
    ])
    assert_fragments(MAPPING, [
        "@sprint27-calendar-provider-boundary",
        "@sprint27-files-provider-boundary",
        "@sprint27-platform-identity-federation-boundary",
        "@sprint27-provider-neutrality-claim-gate",
        "SPRINT27_CALENDAR_PROVIDER_BOUNDARY_PROOF",
        "SPRINT27_PROVIDER_NEUTRALITY_CLAIM_GATE",
        "tools/cross_domain_provider_proof_check.py",
    ])


def main() -> None:
    validate_safety_regression_samples()

    for provider, domain in [
        ("nextcloud-caldav", "calendar"),
        ("radicale", "calendar"),
        ("nextcloud", "files"),
        ("minio-s3", "files"),
    ]:
        validate_manifest(provider, domain)

    calendar = load(CALENDAR)
    files = load(FILES)
    platform_identity = load(PLATFORM_IDENTITY)
    validate_domain_fixture(
        calendar,
        label="calendar",
        kind="weave-sprint-27-calendar-boundary-proof-v1",
        issue=643,
        domain="calendar",
        source="nextcloud-caldav",
        target="radicale",
        required_objects={"WeaveCalendar", "WeaveEvent", "WeaveRecurrence", "WeaveAttendee", "WeaveResource", "WeaveAvailability", "ProviderRef"},
        marker="SPRINT27_CALENDAR_PROVIDER_BOUNDARY_PROOF",
    )
    validate_domain_fixture(
        files,
        label="files",
        kind="weave-sprint-27-files-boundary-proof-v1",
        issue=644,
        domain="files",
        source="nextcloud",
        target="minio-s3",
        required_objects={"WeaveDrive", "WeaveFolder", "WeaveFile", "WeaveVersion", "WeaveShare", "WeavePermission", "WeaveLock", "WeaveQuota", "ProviderRef"},
        marker="SPRINT27_FILES_PROVIDER_BOUNDARY_PROOF",
    )
    validate_platform_identity_fixture(platform_identity)
    if files.get("permissionValidation", {}).get("silentPermissionDropsAllowed") is not False:
        fail("files proof must forbid silent permission drops")
    if calendar.get("uiDomainStability", {}).get("memberSurface") != "Weave Calendar":
        fail("calendar UI must remain domain-stable")

    scoreboard = load(SCOREBOARD)
    claim_gate = load(CLAIM_GATE)
    validate_scoreboard(
        scoreboard,
        {
            "calendar": calendar,
            "files": files,
            "platform-identity": platform_identity,
        },
    )
    validate_claim_gate(claim_gate, scoreboard)
    validate_docs_and_mapping()
    print("cross-domain-provider-proof-check: ok issues=643,644,645,646 reality=migration_dry_run delivery=github-only")
    print("SPRINT27_CALENDAR_PROVIDER_BOUNDARY_PROOF")
    print("SPRINT27_FILES_PROVIDER_BOUNDARY_PROOF")
    print("SPRINT27_PLATFORM_IDENTITY_FEDERATION_PROOF")
    print("SPRINT27_PROVIDER_NEUTRALITY_SCOREBOARD")
    print("SPRINT27_PROVIDER_NEUTRALITY_CLAIM_GATE")


if __name__ == "__main__":
    main()
