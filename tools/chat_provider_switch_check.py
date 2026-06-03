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

    boundary = coverage.get("claimBoundary", "").lower()
    for phrase in ["does not prove lossless migration", "production apply", "production rollback", "provider interchangeability", "e2ee history migration", "release readiness"]:
        if phrase not in boundary:
            fail(f"claim boundary missing {phrase}")
    print("chat-provider-switch-check: ok objects=12 positive=accept negative=reject providerRefs=support_safe")


if __name__ == "__main__":
    main()
