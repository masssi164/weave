#!/usr/bin/env python3
"""Validate Sprint 22 free-provider-lab manifests, fixtures, redaction, and Sprint 23 gate."""

from __future__ import annotations

import argparse
import json
import re
import sys
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[1]
MANIFEST_DIR = ROOT / "release" / "provider-lab" / "manifests"
FIXTURE = ROOT / "fixtures" / "provider-lab" / "chat-fixture.json"
REDACTION_REPORT = ROOT / "release" / "provider-lab" / "support-redaction-report.json"
SCOREBOARD = ROOT / "release" / "provider-lab" / "sprint-23-entry-scoreboard.json"
HEALTH_REPORT = ROOT / "release" / "provider-lab" / "health-report.sample.json"
COMPOSE = ROOT / "infra" / "provider-lab" / "docker-compose.yml"
RUNBOOK = ROOT / "docs" / "free-provider-lab.md"

LEVELS = [
    "contract_only",
    "configured",
    "live_read",
    "live_write",
    "migration_dry_run",
    "migration_apply_ready",
    "rollback_ready",
    "release_ready",
]
OLD_LEVELS = {"configured_readiness", "live_adapter_read", "live_adapter_write"}
REQUIRED_PROVIDERS = {
    "keycloak": "identity",
    "authentik": "identity",
    "matrix-synapse": "chat",
    "zulip": "chat",
    "nextcloud": "files",
    "minio-s3": "files",
    "nextcloud-caldav": "calendar",
    "radicale": "calendar",
    "openproject": "boards",
    "docker-runtime": "weaver",
}
REQUIRED_COUNTS = {
    "spaces": 2,
    "channels": 3,
    "people": 3,
    "messages": 50,
    "threads": 5,
    "reactions": 10,
    "attachments": 5,
    "editedMessages": 3,
    "deletedMessages": 2,
    "pinnedDecisions": 1,
    "e2eeUnsupportedHistoryFixtures": 1,
}
REQUIRED_HISTORY_STATUSES = {
    "preserved",
    "partially_preserved",
    "archive_only",
    "unsupported",
    "unexportable",
    "conflict",
    "metadata_only",
}
SECRET_PATTERNS = [
    re.compile(pattern, re.IGNORECASE)
    for pattern in [
        r"bearer\s+[a-z0-9._-]+",
        r"access[_-]?token",
        r"refresh[_-]?token",
        r"password\s*[:=]",
        r"cookie\s*[:=]",
        r"https?://[^\s]*:[^\s]*@",
        r"mxc://",
        r"rawProviderPayload",
        r"rawProviderError",
    ]
]


def fail(message: str) -> None:
    print(f"provider-lab-check: {message}", file=sys.stderr)
    raise SystemExit(1)


def load_json(path: Path) -> Any:
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except FileNotFoundError:
        fail(f"missing {path.relative_to(ROOT)}")
    except json.JSONDecodeError as error:
        fail(f"invalid JSON in {path.relative_to(ROOT)}: {error}")


def assert_object(value: Any, label: str) -> dict[str, Any]:
    if not isinstance(value, dict):
        fail(f"{label} must be an object")
    return value


def validate_manifests() -> list[dict[str, Any]]:
    manifests: list[dict[str, Any]] = []
    seen: dict[str, Path] = {}
    for path in sorted(MANIFEST_DIR.glob("*.json")):
        manifest = assert_object(load_json(path), str(path.relative_to(ROOT)))
        key = manifest.get("providerKey")
        if not isinstance(key, str):
            fail(f"{path.relative_to(ROOT)} missing providerKey")
        if key in seen:
            fail(f"duplicate manifest for provider {key}: {seen[key]} and {path}")
        seen[key] = path
        expected_domain = REQUIRED_PROVIDERS.get(key)
        if expected_domain is None:
            fail(f"unexpected provider manifest {key}")
        if manifest.get("domain") != expected_domain:
            fail(f"{key} domain must be {expected_domain}")
        level = manifest.get("realityLevel")
        if level in OLD_LEVELS:
            fail(f"{key} uses rejected old reality level {level}")
        if level not in LEVELS:
            fail(f"{key} has invalid realityLevel {level!r}")
        if sum(1 for candidate in LEVELS if candidate == level) != 1:
            fail(f"{key} must declare exactly one realityLevel")
        for field in ["capabilityFlags", "historyHonesty", "rollback", "migrationLimits", "secretStorage", "supportEvidence"]:
            if field not in manifest:
                fail(f"{key} missing {field}")
        support = assert_object(manifest["supportEvidence"], f"{key}.supportEvidence")
        if support.get("redacted") is not True:
            fail(f"{key} supportEvidence.redacted must be true")
        rollback = assert_object(manifest["rollback"], f"{key}.rollback")
        if "claim" not in rollback or "notes" not in rollback:
            fail(f"{key} rollback must include claim and notes")
        history = assert_object(manifest["historyHonesty"], f"{key}.historyHonesty")
        statuses = set(history.get("statuses", []))
        if statuses != REQUIRED_HISTORY_STATUSES:
            fail(f"{key} historyHonesty.statuses must match fixture statuses")
        if history.get("fullHistoryPreserved") is not False:
            fail(f"{key} must not claim full history preservation")
        if manifest.get("migrationLimits", {}).get("releaseReadyClaimed") is not False:
            fail(f"{key} must not claim release_ready")
        if key == "matrix-synapse":
            text = json.dumps(manifest).lower()
            if "partial" not in text or "e2ee" not in text or "archive_only" not in text:
                fail("matrix-synapse manifest must name partial rollback and E2EE archive_only/blocked history")
        manifests.append(manifest)
    missing = set(REQUIRED_PROVIDERS) - set(seen)
    if missing:
        fail("missing provider manifest(s): " + ", ".join(sorted(missing)))
    return manifests


def validate_fixture() -> dict[str, Any]:
    fixture = assert_object(load_json(FIXTURE), str(FIXTURE.relative_to(ROOT)))
    counts = assert_object(fixture.get("counts"), "fixture.counts")
    for key, expected in REQUIRED_COUNTS.items():
        if counts.get(key) != expected:
            fail(f"fixture count {key} expected {expected}, got {counts.get(key)!r}")
    if set(fixture.get("expectedHistoryStatuses", [])) != REQUIRED_HISTORY_STATUSES:
        fail("fixture expectedHistoryStatuses are incomplete")
    records = assert_object(fixture.get("records"), "fixture.records")
    record_map = {
        "messages": "messages",
        "threads": "threads",
        "reactions": "reactions",
        "attachments": "attachments",
        "editedMessages": "editedMessages",
        "deletedMessages": "deletedMessages",
        "pinnedDecisions": "pinnedDecisions",
        "e2eeUnsupportedHistoryFixtures": "e2eeUnsupportedHistoryFixtures",
    }
    for count_key, record_key in record_map.items():
        values = records.get(record_key)
        if not isinstance(values, list) or len(values) != REQUIRED_COUNTS[count_key]:
            fail(f"fixture records.{record_key} must contain {REQUIRED_COUNTS[count_key]} stable id(s)")
    policy = assert_object(fixture.get("supportSafeContentPolicy"), "fixture.supportSafeContentPolicy")
    for key in ["messageBodiesIncludedInLogs", "attachmentContentsIncludedInLogs", "providerRoomIdsIncludedInLogs"]:
        if policy.get(key) is not False:
            fail(f"fixture support-safe policy {key} must be false")
    return fixture


def validate_redaction_report() -> dict[str, Any]:
    report = assert_object(load_json(REDACTION_REPORT), str(REDACTION_REPORT.relative_to(ROOT)))
    if report.get("supportSafe") is not True:
        fail("support redaction report must be supportSafe=true")
    paths = report.get("artifactPaths")
    if not isinstance(paths, list) or not paths:
        fail("support redaction report requires artifactPaths")
    for relative in paths:
        path = ROOT / relative
        if not path.exists():
            fail(f"redaction artifact path does not exist: {relative}")
        text = path.read_text(encoding="utf-8")
        for pattern in SECRET_PATTERNS:
            if pattern.search(text):
                fail(f"redaction scan found forbidden pattern {pattern.pattern!r} in {relative}")
    return report


def validate_compose_topology() -> None:
    try:
        compose_text = COMPOSE.read_text(encoding="utf-8")
    except FileNotFoundError:
        fail(f"missing {COMPOSE.relative_to(ROOT)}")
    runbook_text = RUNBOOK.read_text(encoding="utf-8") if RUNBOOK.exists() else ""
    if "zulip/zulip:" in compose_text:
        fail("Zulip must not use the legacy zulip/zulip Docker Hub image")
    if "ghcr.io/zulip/zulip-server:" not in compose_text:
        fail("Zulip profile must use the current ghcr.io/zulip/zulip-server image")
    if re.search(r"zulip:\n(?:.*\n){0,8}\s+profiles:\s+\[\"zulip\"\]", compose_text) is None:
        fail("Zulip service must remain profile-gated because Docker deployment needs documented bootstrap")
    if "/var/run/docker.sock" in compose_text:
        fail("provider lab compose must not mount the host Docker socket")
    for phrase in ["synapse-config", "zulip-init", "Zulip one-command parity", "reproducible provider lab smoke/fixture environment"]:
        if phrase not in runbook_text:
            fail(f"provider lab runbook must document {phrase!r}")


def build_health(manifests: list[dict[str, Any]]) -> dict[str, Any]:
    checked_at = datetime.now(timezone.utc).replace(microsecond=0).isoformat().replace("+00:00", "Z")
    return {
        "schemaVersion": 1,
        "reportKind": "weave-provider-lab-health-report",
        "supportSafe": True,
        "checkedAt": checked_at,
        "providers": [
            {
                "providerKey": manifest["providerKey"],
                "domain": manifest["domain"],
                "realityLevel": manifest["realityLevel"],
                "evidenceTimestamp": checked_at,
                "supportEvidenceRedacted": manifest["supportEvidence"]["redacted"],
            }
            for manifest in sorted(manifests, key=lambda item: item["providerKey"])
        ],
    }


def validate_scoreboard(manifests: list[dict[str, Any]], fixture: dict[str, Any], redaction: dict[str, Any]) -> dict[str, Any]:
    scoreboard = assert_object(load_json(SCOREBOARD), str(SCOREBOARD.relative_to(ROOT)))
    fields = assert_object(scoreboard.get("fields"), "scoreboard.fields")
    expected = {
        "labHealth": "green" if COMPOSE.exists() and HEALTH_REPORT.exists() else "red",
        "manifestValidity": "green" if len(manifests) == len(REQUIRED_PROVIDERS) else "red",
        "fixtureCompleteness": "green" if fixture.get("counts") == REQUIRED_COUNTS else "red",
        "supportBundleRedaction": "green" if redaction.get("supportSafe") is True else "red",
        "claimSafety": "green",
    }
    for key, value in expected.items():
        if fields.get(key) != value:
            fail(f"scoreboard field {key}={fields.get(key)!r} does not match gate {value!r}")
    blockers = scoreboard.get("openReleaseBlockers")
    if blockers != []:
        fail("open release blockers prevent Sprint 23 entry gate promotion")
    if scoreboard.get("sprint23EntryGate") != "green":
        fail("sprint23EntryGate must be green when all scoreboard fields are green and blockers are empty")
    return scoreboard


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--health-report", action="store_true", help="Emit a support-safe health report from validated manifests.")
    args = parser.parse_args()

    manifests = validate_manifests()
    validate_compose_topology()
    fixture = validate_fixture()
    redaction = validate_redaction_report()
    health = build_health(manifests)
    if args.health_report:
        print(json.dumps(health, indent=2))
        return
    validate_scoreboard(manifests, fixture, redaction)
    print(
        "provider-lab-check: ok "
        f"providers={len(manifests)} fixtureMessages={fixture['counts']['messages']} "
        "scoreboard=sprint23EntryGate:green"
    )


if __name__ == "__main__":
    main()
