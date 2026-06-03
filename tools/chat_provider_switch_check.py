#!/usr/bin/env python3
"""Validate Sprint 23 Chat Provider Switch canonical coverage and redaction fixtures."""
from __future__ import annotations

import json
import re
import sys
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[1]
ARTIFACT_DIR = ROOT / "release" / "provider-lab" / "chat-switch"
COVERAGE = ARTIFACT_DIR / "canonical-object-coverage.json"
POSITIVE = ARTIFACT_DIR / "matrix-zulip-lossy-field-report.fixture.json"
NEGATIVE = ARTIFACT_DIR / "matrix-zulip-silent-drop-negative.fixture.json"
MIGRATION_PROOF = ARTIFACT_DIR / "matrix-zulip-migration-proof.fixture.json"
ROLLBACK_PROOF = ARTIFACT_DIR / "zulip-matrix-rollback-proof.fixture.json"
CLAIM_GATE = ARTIFACT_DIR / "sprint-23-claim-gate.fixture.json"
REGISTRY = ROOT / "specs" / "0004-domain-registry" / "canonical-domain-registry-v1.json"
SERVER_REGISTRY = ROOT / "server" / "src" / "main" / "resources" / "canonical-domain-registry-v1.json"
PRODUCT_REALITY = ROOT / "release" / "product-reality-gates.json"
PROVIDER_FIXTURE = ROOT / "fixtures" / "provider-lab" / "chat-fixture.json"
SCOREBOARD = ROOT / "release" / "provider-lab" / "sprint-23-entry-scoreboard.json"

EXPECTED_OBJECTS = [
    "WeaveSpace",
    "WeaveConversation",
    "WeaveMessage",
    "WeaveThread",
    "WeaveReaction",
    "WeaveAttachment",
    "WeaveMembership",
    "WeaveHistoryPolicy",
    "ProviderRef",
    "MigrationReceipt",
    "RollbackReceipt",
    "LossyFieldReport",
]
LOSS_CLASSES = {"portable", "lossy", "unsupported", "manual_review", "vendor_locked", "archive_only"}
FORBIDDEN_REF_KEYS = {
    "url",
    "homeserverUrl",
    "apiUrl",
    "accessToken",
    "refreshToken",
    "password",
    "secret",
    "rawPayload",
    "messageBody",
    "attachmentContent",
}
FORBIDDEN_RAW_PATTERNS = [
    r"access_token",
    r"refresh_token",
    r"clientsecret",
    r"password",
    r"homeserverurl",
    r"https://matrix",
    r"https://zulip",
    r"mxc://",
]
REQUIRED_EVIDENCE_REFS = {
    "release/provider-lab/chat-switch/canonical-object-coverage.json",
    "release/provider-lab/chat-switch/matrix-zulip-migration-proof.fixture.json",
    "release/provider-lab/chat-switch/matrix-zulip-lossy-field-report.fixture.json",
    "release/provider-lab/chat-switch/zulip-matrix-rollback-proof.fixture.json",
}
REQUIRED_CLAIM_EVIDENCE_TERMS = {"MigrationReceipt", "LossyFieldReport", "RollbackReceipt", "UI validation transcript"}
REJECTED_GENERIC_TERMS = {"interchangeable", "production", "fully preserved"}


def fail(message: str) -> None:
    print(f"chat-provider-switch-check: {message}", file=sys.stderr)
    raise SystemExit(1)


def load(path: Path) -> Any:
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except FileNotFoundError:
        fail(f"missing {path.relative_to(ROOT)}")
    except json.JSONDecodeError as error:
        fail(f"invalid JSON in {path.relative_to(ROOT)}: {error}")


def chat_objects_from_registry(path: Path) -> set[str]:
    data = load(path)
    raw = json.dumps(data)
    return {name for name in EXPECTED_OBJECTS if name in raw}


def assert_support_safe(raw: str, label: str) -> None:
    lowered = raw.lower()
    for pattern in FORBIDDEN_RAW_PATTERNS:
        if re.search(pattern, lowered):
            fail(f"{label} leaks forbidden provider/secret payload pattern {pattern}")


def validate_provider_refs(fixture: dict[str, Any], coverage: dict[str, Any]) -> None:
    policy = coverage.get("providerRefPolicy", {})
    pattern = re.compile(policy.get("opaqueIdPattern", r"^$"))
    allowed = set(policy.get("allowedFields", []))
    forbidden = set(policy.get("forbiddenFields", [])) | FORBIDDEN_REF_KEYS
    refs = fixture.get("providerRefs", [])
    if not refs:
        fail(f"{fixture.get('fixtureId')} must include providerRefs")
    for ref in refs:
        keys = set(ref)
        leaked_keys = keys & forbidden
        if leaked_keys:
            fail(f"{fixture.get('fixtureId')} providerRef leaks forbidden keys: {', '.join(sorted(leaked_keys))}")
        unknown_keys = keys - allowed
        if unknown_keys:
            fail(f"{fixture.get('fixtureId')} providerRef contains non-policy keys: {', '.join(sorted(unknown_keys))}")
        if ref.get("redacted") is not True:
            fail(f"{fixture.get('fixtureId')} providerRef must be explicitly redacted")
        opaque = ref.get("opaqueId", "")
        if not pattern.match(opaque):
            fail(f"{fixture.get('fixtureId')} providerRef opaqueId is not support-safe: {opaque!r}")


def unreported_drops(fixture: dict[str, Any]) -> list[str]:
    entries = fixture.get("lossyFieldReport", {}).get("entries", [])
    reported = {(entry.get("canonicalObject"), entry.get("field")) for entry in entries}
    missing = []
    for item in fixture.get("droppedOrChangedFields", []):
        key = (item.get("canonicalObject"), item.get("field"))
        if key not in reported:
            missing.append(f"{key[0]}.{key[1]}")
        if item.get("lossClass") not in LOSS_CLASSES:
            fail(f"{fixture.get('fixtureId')} uses non-canonical loss class {item.get('lossClass')!r}")
    return missing


def validate_fixture(fixture: dict[str, Any], coverage: dict[str, Any]) -> bool:
    raw = json.dumps(fixture)
    assert_support_safe(raw, fixture.get("fixtureId", "fixture"))
    if fixture.get("redaction") != "support_safe":
        fail(f"{fixture.get('fixtureId')} must be support_safe")
    if fixture.get("sourceProvider") != "matrix-synapse" or fixture.get("targetProvider") != "zulip":
        fail(f"{fixture.get('fixtureId')} must be Matrix/Synapse to Zulip scoped")
    validate_provider_refs(fixture, coverage)
    missing = unreported_drops(fixture)
    return not missing


def assert_domain_stable(artifact: dict[str, Any], label: str) -> None:
    before = artifact.get("domainBefore", {})
    after = artifact.get("domainAfter", {})
    for field in ["domainKey", "weaveSpaceId", "weaveConversationId"]:
        if before.get(field) != after.get(field):
            fail(f"{label} must keep {field} stable")


def validate_migration_proof(artifact: dict[str, Any], coverage: dict[str, Any]) -> None:
    assert_support_safe(json.dumps(artifact), "matrix-zulip-migration-proof")
    if artifact.get("artifactKind") != "weave-chat-switch-matrix-zulip-migration-proof":
        fail("migration proof artifact kind mismatch")
    if artifact.get("redaction") != "support_safe" or artifact.get("supportSafe") is not True:
        fail("migration proof must be support_safe")
    if artifact.get("sourceProvider") != "matrix-synapse" or artifact.get("targetProvider") != "zulip":
        fail("migration proof must be Matrix/Synapse to Zulip scoped")
    assert_domain_stable(artifact, "migration proof")
    if artifact.get("domainAfter", {}).get("activeProvider") != "zulip":
        fail("migration proof must end with Zulip active")
    receipt = artifact.get("migrationReceipt", {})
    if receipt.get("weaveDomainIdsStable") is not True:
        fail("MigrationReceipt must assert stable Weave domain IDs")
    if receipt.get("productionMutationPerformed") is not False:
        fail("migration proof must not claim production mutation")
    for required in ["lossyFieldReportRef", "attachmentValidationReportRef", "uiValidationTranscriptRef"]:
        if not receipt.get(required):
            fail(f"MigrationReceipt missing {required}")
    validate_provider_refs({"fixtureId": "migration proof", "providerRefs": receipt.get("providerRefs", [])}, coverage)
    required_statuses = set(coverage.get("requiredHistoryStatuses", []))
    receipt_statuses = set(receipt.get("historyStatuses", []))
    validation_statuses = {item.get("historyStatus") for item in artifact.get("historyValidation", [])}
    if receipt_statuses != required_statuses or validation_statuses != required_statuses:
        fail("migration proof must report every required history status")
    if artifact.get("attachmentValidationReport", {}).get("failed") != 0:
        fail("attachment validation report must not hide failed attachments")
    ui = artifact.get("uiValidationTranscript", {})
    if ui.get("visibleDomain") != "Chat" or ui.get("activeProvider") != "zulip" or ui.get("providerSetupHiddenFromMember") is not True:
        fail("migration UI transcript must show stable member Chat domain after Zulip activation")
    audit_links = {link for event in artifact.get("auditLog", []) for link in event.get("links", [])}
    for required_ref in [receipt.get("lossyFieldReportRef"), receipt.get("attachmentValidationReportRef"), receipt.get("uiValidationTranscriptRef")]:
        if required_ref not in audit_links:
            fail(f"migration audit log must link {required_ref}")


def validate_rollback_proof(artifact: dict[str, Any], coverage: dict[str, Any]) -> None:
    assert_support_safe(json.dumps(artifact), "zulip-matrix-rollback-proof")
    if artifact.get("artifactKind") != "weave-chat-switch-zulip-matrix-rollback-proof":
        fail("rollback proof artifact kind mismatch")
    if artifact.get("redaction") != "support_safe" or artifact.get("supportSafe") is not True:
        fail("rollback proof must be support_safe")
    if artifact.get("sourceProvider") != "zulip" or artifact.get("targetProvider") != "matrix-synapse":
        fail("rollback proof must be Zulip to Matrix/Synapse scoped")
    assert_domain_stable(artifact, "rollback proof")
    if artifact.get("domainAfter", {}).get("activeProvider") != "matrix-synapse":
        fail("rollback proof must end with Matrix/Synapse active")
    receipt = artifact.get("rollbackReceipt", {})
    if receipt.get("weaveDomainIdsStable") is not True:
        fail("RollbackReceipt must assert stable Weave domain IDs")
    if receipt.get("productionMutationPerformed") is not False:
        fail("rollback proof must not claim production mutation")
    if not receipt.get("conflictSummaryRef") or not receipt.get("limitations"):
        fail("RollbackReceipt must reference conflicts and limitations")
    validate_provider_refs({"fixtureId": "rollback proof", "providerRefs": receipt.get("providerRefs", [])}, coverage)
    classifications = {item.get("historyStatus") for item in artifact.get("conflictReport", {}).get("classifications", [])}
    for required_status in ["conflict", "partially_preserved", "metadata_only", "unsupported"]:
        if required_status not in classifications:
            fail(f"rollback conflict report missing {required_status}")
    ui = artifact.get("uiValidationTranscript", {})
    if ui.get("visibleDomain") != "Chat" or ui.get("activeProvider") != "matrix-synapse" or ui.get("oldHistoryVisible") is not True:
        fail("rollback UI transcript must show stable member Chat domain after Matrix restore")
    audit_links = {link for event in artifact.get("auditLog", []) for link in event.get("links", [])}
    if receipt.get("conflictSummaryRef") not in audit_links:
        fail("rollback audit log must link conflict report")


def validate_claim_gate(artifact: dict[str, Any]) -> None:
    assert_support_safe(json.dumps(artifact), "sprint-23-claim-gate")
    if artifact.get("artifactKind") != "weave-chat-switch-sprint-23-claim-gate":
        fail("claim gate artifact kind mismatch")
    if artifact.get("redaction") != "support_safe":
        fail("claim gate must be support_safe")
    if artifact.get("chatRealityLevel") != "migration_apply_ready" or artifact.get("releaseReady") is not False:
        fail("Sprint 23 scoreboard must stay at migration_apply_ready and blocked releaseReady")
    scoreboard = artifact.get("scoreboard", {})
    if scoreboard.get("scoreboardKind") != "weave-sprint-23-chat-switch-scoreboard":
        fail("Sprint 23 chat scoreboard kind mismatch")
    for field in ["chatProviderSwitch", "matrixToZulipDryRunApply", "zulipToMatrixRollbackHonesty", "canonicalObjectCoverage", "providerRefRedaction", "claimSafety"]:
        if scoreboard.get(field) != "green":
            fail(f"Sprint 23 chat scoreboard field {field} must be green")
    if scoreboard.get("releaseReady") != "blocked":
        fail("Sprint 23 releaseReady must remain blocked")
    if set(artifact.get("requiredEvidenceRefs", [])) != REQUIRED_EVIDENCE_REFS:
        fail("claim gate must require migration, lossy, rollback, and coverage evidence refs")
    accepted = artifact.get("acceptedScopedClaim", {})
    if accepted.get("expectedOutcome") != "accept" or set(accepted.get("mustReference", [])) != REQUIRED_CLAIM_EVIDENCE_TERMS:
        fail("scoped chat switch claim must pass only with required evidence terms")
    rejected = artifact.get("rejectedGenericClaim", {})
    rejected_claim = rejected.get("claim", "").lower()
    if rejected.get("expectedOutcome") != "reject" or not all(term in rejected_claim for term in REJECTED_GENERIC_TERMS):
        fail("generic provider interchangeability/production/full-history claim must be rejected")
    if not artifact.get("openBlockers"):
        fail("claim gate must list open blockers")
    boundary = artifact.get("claimBoundary", "").lower()
    for phrase in ["provider interchangeability", "production apply", "production rollback", "release-ready", "lossless migration", "full-history preservation", "e2ee history migration"]:
        if phrase not in boundary:
            fail(f"claim gate boundary missing {phrase}")


def main() -> None:
    coverage = load(COVERAGE)
    if coverage.get("artifactKind") != "weave-chat-switch-canonical-object-coverage":
        fail("coverage artifact kind mismatch")
    if coverage.get("redaction") != "support_safe":
        fail("coverage artifact must be support_safe")
    coverage_without_policy = dict(coverage)
    coverage_without_policy.pop("providerRefPolicy", None)
    assert_support_safe(json.dumps(coverage_without_policy), "canonical-object-coverage")

    covered = [item.get("name") for item in coverage.get("canonicalObjects", [])]
    if covered != EXPECTED_OBJECTS:
        fail("coverage canonicalObjects must match required Chat object order")
    for item in coverage.get("canonicalObjects", []):
        for field in ["sourceRefs", "matrixFixtureFields", "zulipFixtureFields"]:
            if not item.get(field):
                fail(f"{item.get('name')} coverage missing {field}")
        if item.get("lossyFieldReportRequiredOnDrop") is not True:
            fail(f"{item.get('name')} must require LossyFieldReport on drops")

    registry_objects = chat_objects_from_registry(REGISTRY)
    server_objects = chat_objects_from_registry(SERVER_REGISTRY)
    reality_objects = set(load(PRODUCT_REALITY).get("canonicalObjects", {}).get("chat", []))
    for source_name, values in [("repo registry", registry_objects), ("server registry", server_objects), ("product reality gate", reality_objects)]:
        missing = set(EXPECTED_OBJECTS) - values
        if missing:
            fail(f"{source_name} missing Chat canonical object(s): {', '.join(sorted(missing))}")

    scoreboard = load(SCOREBOARD)
    if scoreboard.get("scoreboardKind") != "weave-sprint-23-entry-scoreboard":
        fail("Sprint 23 entry scoreboard kind mismatch")
    if scoreboard.get("sprint23EntryGate") != "green":
        fail("Sprint 23 entry scoreboard must be green")
    if scoreboard.get("openReleaseBlockers") != []:
        fail("Sprint 23 entry scoreboard must have no open release blockers")
    fields = scoreboard.get("fields", {})
    for field in ["labHealth", "manifestValidity", "fixtureCompleteness", "supportBundleRedaction", "claimSafety"]:
        if fields.get(field) != "green":
            fail(f"Sprint 23 entry scoreboard field {field} must be green")
    evidence = scoreboard.get("evidence", {})
    if evidence.get("fixture") != "fixtures/provider-lab/chat-fixture.json":
        fail("Sprint 23 entry scoreboard must point at fixtures/provider-lab/chat-fixture.json")
    if evidence.get("gateCommand") != "./gradlew providerLabCheck":
        fail("Sprint 23 entry scoreboard must name ./gradlew providerLabCheck")

    provider_fixture = load(PROVIDER_FIXTURE)
    expected_statuses = set(provider_fixture.get("expectedHistoryStatuses", []))
    required_statuses = set(coverage.get("requiredHistoryStatuses", []))
    if expected_statuses != required_statuses:
        fail("Sprint 23 coverage statuses must match provider-lab chat fixture expectedHistoryStatuses")
    if provider_fixture.get("counts", {}).get("messages") != 50:
        fail("Sprint 23 depends on the Sprint 22 fixture with 50 messages")

    positive = load(POSITIVE)
    if positive.get("expectedOutcome") != "accept" or not validate_fixture(positive, coverage):
        fail("positive LossyFieldReport fixture must pass with all drops reported")

    negative = load(NEGATIVE)
    if negative.get("expectedOutcome") != "reject":
        fail("negative silent-drop fixture must declare expectedOutcome=reject")
    if validate_fixture(negative, coverage):
        fail("negative silent-drop fixture unexpectedly passed; missing LossyFieldReport entry must reject")

    migration_proof = load(MIGRATION_PROOF)
    validate_migration_proof(migration_proof, coverage)

    rollback_proof = load(ROLLBACK_PROOF)
    validate_rollback_proof(rollback_proof, coverage)

    claim_gate = load(CLAIM_GATE)
    validate_claim_gate(claim_gate)

    boundary = coverage.get("claimBoundary", "").lower()
    for phrase in ["does not prove lossless migration", "production apply", "production rollback", "provider interchangeability", "e2ee history migration", "release readiness"]:
        if phrase not in boundary:
            fail(f"claim boundary missing {phrase}")
    print("chat-provider-switch-check: ok objects=12 positive=accept negative=reject migration=matrix-to-zulip rollback=zulip-to-matrix claims=scoped providerRefs=support_safe")


if __name__ == "__main__":
    main()
