#!/usr/bin/env python3
"""Validate Sprint 32 release evidence freshness against concrete artifacts."""
from __future__ import annotations

import argparse
import json
import subprocess
from dataclasses import dataclass
from datetime import datetime, timezone
from enum import Enum
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[1]
REQUIREMENTS = ROOT / "tools" / "fixtures" / "release_evidence" / "sprint32_freshness_requirements.json"
LIVE_HANDOFF = ROOT / "release" / "provider-lab" / "local-forgejo-e2e" / "local-stack-e2e-handoff.fixture.json"
RESTORE_RECEIPT = ROOT / "release" / "provider-lab" / "operator-recovery" / "restore-receipt.disposable.json"
BACKUP_MANIFEST = ROOT / "release" / "provider-lab" / "operator-recovery" / "backup-manifest.disposable.json"
CLAIM_DOC = ROOT / "docs" / "release-notes" / "unreleased.md"


class EvidenceState(str, Enum):
    FRESH = "fresh"
    STALE = "stale"
    MISSING = "missing"
    NOT_REQUIRED = "not_required"
    PRESENT = "present"


@dataclass(frozen=True)
class EvidenceResult:
    id: str
    state: EvidenceState
    summary: str
    pointers: tuple[str, ...]
    maximum_age_hours: int | None = None
    observed_at: str | None = None

    def to_json(self) -> dict[str, Any]:
        data: dict[str, Any] = {
            "id": self.id,
            "state": self.state.value,
            "summary": self.summary,
            "pointers": list(self.pointers),
        }
        if self.maximum_age_hours is not None:
            data["maximumAgeHours"] = self.maximum_age_hours
        if self.observed_at:
            data["observedAt"] = self.observed_at
        return data


def rel(path: Path) -> str:
    try:
        return path.relative_to(ROOT).as_posix()
    except ValueError:
        return path.as_posix()


def load_json(path: Path) -> dict[str, Any] | None:
    if not path.exists():
        return None
    return json.loads(path.read_text(encoding="utf-8"))


def parse_utc(value: str | None) -> datetime | None:
    if not value:
        return None
    try:
        parsed = datetime.fromisoformat(value.replace("Z", "+00:00"))
    except ValueError:
        return None
    if parsed.tzinfo is None:
        parsed = parsed.replace(tzinfo=timezone.utc)
    return parsed.astimezone(timezone.utc)


def git_output(args: list[str]) -> str | None:
    try:
        return subprocess.check_output(["git", *args], cwd=ROOT, text=True, stderr=subprocess.DEVNULL).strip()
    except (OSError, subprocess.CalledProcessError):
        return None


def current_main_head() -> str:
    return git_output(["rev-parse", "origin/main"]) or git_output(["rev-parse", "HEAD"]) or "unknown"


def evidence_age_state(observed: datetime | None, *, maximum_age_hours: int, now: datetime) -> EvidenceState:
    if observed is None:
        return EvidenceState.MISSING
    age_hours = (now - observed).total_seconds() / 3600
    return EvidenceState.FRESH if 0 <= age_hours <= maximum_age_hours else EvidenceState.STALE


def release_claim_requires_closure_evidence(force: bool = False) -> bool:
    if force:
        return True

    labels = {label.strip() for label in (git_output(["config", "--get-all", "weave.releaseEvidence.labels"]) or "").splitlines()}
    if {"release-closure", "release-candidate", "sprint-closure"} & labels:
        return True
    text = CLAIM_DOC.read_text(encoding="utf-8") if CLAIM_DOC.exists() else ""
    closure_words = ("release candidate", "sprint closure", "closure claim", "dogfood-production closure")
    return any(word in text.lower() for word in closure_words)


def check_support_safe_payload(path: Path, payload: dict[str, Any]) -> tuple[bool, str]:
    serialized = json.dumps(payload, sort_keys=True)
    forbidden = load_json(REQUIREMENTS) or {}
    fragments = forbidden.get("forbiddenEvidenceFragments", [])
    for fragment in fragments:
        if fragment.lower() in serialized.lower():
            return False, f"{rel(path)} contains forbidden evidence fragment {fragment}"
    if payload.get("supportSafe") is not True:
        return False, f"{rel(path)} is not marked supportSafe=true"
    return True, "support-safe metadata present"


def live_stack_evidence(requirement: dict[str, Any], *, now: datetime, main_head: str, closure_required: bool) -> EvidenceResult:
    maximum_age = int(requirement["maximumAgeHours"])
    if not closure_required:
        return EvidenceResult(
            "live_stack_e2e",
            EvidenceState.NOT_REQUIRED,
            "no release/closure claim label or wording requires fresh live-stack evidence for this PR gate",
            (rel(REQUIREMENTS), rel(LIVE_HANDOFF)),
            maximum_age,
        )
    payload = load_json(LIVE_HANDOFF)
    if payload is None:
        return EvidenceResult("live_stack_e2e", EvidenceState.MISSING, "live-stack handoff evidence is missing", (rel(LIVE_HANDOFF),), maximum_age)
    safe, reason = check_support_safe_payload(LIVE_HANDOFF, payload)
    observed = parse_utc(payload.get("observedAt") or payload.get("createdAt"))
    state = evidence_age_state(observed, maximum_age_hours=maximum_age, now=now)
    recorded_head = str(payload.get("mainlineDependencyStatus", {}).get("originMainHead", ""))
    if state == EvidenceState.FRESH and not main_head.startswith(recorded_head):
        state = EvidenceState.STALE
        reason = "live-stack evidence is not tied to current origin/main"
    elif state == EvidenceState.FRESH and not safe:
        state = EvidenceState.MISSING
    summary = reason if state != EvidenceState.FRESH else "fresh live-stack handoff evidence matches current main boundary"
    return EvidenceResult("live_stack_e2e", state, summary, (rel(LIVE_HANDOFF),), maximum_age, observed.isoformat().replace("+00:00", "Z") if observed else None)


def backup_restore_evidence(requirement: dict[str, Any], *, now: datetime, closure_required: bool) -> EvidenceResult:
    maximum_age = int(requirement["maximumAgeHours"])
    receipt = load_json(RESTORE_RECEIPT)
    manifest = load_json(BACKUP_MANIFEST)
    if not closure_required:
        return EvidenceResult("backup_restore_posture", EvidenceState.NOT_REQUIRED, "no release/closure claim requires fresh backup/restore posture for this PR gate", (rel(RESTORE_RECEIPT), rel(BACKUP_MANIFEST)), maximum_age)
    if receipt is None or manifest is None:
        return EvidenceResult("backup_restore_posture", EvidenceState.MISSING, "backup/restore posture evidence is missing", (rel(RESTORE_RECEIPT), rel(BACKUP_MANIFEST)), maximum_age)
    receipt_safe, receipt_reason = check_support_safe_payload(RESTORE_RECEIPT, receipt)
    manifest_safe = manifest.get("scope", {}).get("shareExternally") is False and manifest.get("scope", {}).get("artifactsContainSecretsOrMemberData") is True
    manifest_reason = "private backup manifest is referenced by support-safe receipt and not shareable externally"
    observed = parse_utc(receipt.get("observedAt") or receipt.get("createdAt"))
    state = evidence_age_state(observed, maximum_age_hours=maximum_age, now=now)
    if receipt.get("status") != "passed":
        state = EvidenceState.MISSING
        summary = "restore receipt is not passing"
    elif not (receipt_safe and manifest_safe):
        state = EvidenceState.MISSING
        summary = receipt_reason if not receipt_safe else manifest_reason
    else:
        summary = "fresh backup/restore posture evidence is present" if state == EvidenceState.FRESH else "backup/restore posture evidence is stale"
    return EvidenceResult("backup_restore_posture", state, summary, (rel(RESTORE_RECEIPT), rel(BACKUP_MANIFEST)), maximum_age, observed.isoformat().replace("+00:00", "Z") if observed else None)


def claim_hygiene_evidence(requirement: dict[str, Any]) -> EvidenceResult:
    if not CLAIM_DOC.exists():
        return EvidenceResult("release_claim_hygiene", EvidenceState.MISSING, "release claim hygiene document is missing", (rel(CLAIM_DOC),), int(requirement["maximumAgeHours"]))
    return EvidenceResult("release_claim_hygiene", EvidenceState.PRESENT, "claim hygiene is covered by release_trust_claim_control_check in releaseEvidenceCheck", (rel(CLAIM_DOC), "tools/release_trust_claim_control_check.py"), int(requirement["maximumAgeHours"]))


def validate_requirements(data: dict[str, Any]) -> dict[str, dict[str, Any]]:
    assert data["artifactKind"] == "weave-sprint32-release-evidence-freshness-v1"
    assert data["issue"] == 796
    evidence = data["requiredEvidence"]
    by_id = {item["id"]: item for item in evidence}
    assert set(by_id) == {"live_stack_e2e", "backup_restore_posture", "release_claim_hygiene"}
    for item in evidence:
        assert item["mustBeSupportSafe"] is True
        assert item["maximumAgeHours"] <= 72
        assert item["claimBoundary"]
    serialized = json.dumps(data).lower()
    for fragment in data["forbiddenEvidenceFragments"]:
        assert fragment.lower() not in serialized.replace(fragment.lower(), "")
    return by_id


def run(now: datetime, *, closure_required: bool = False) -> tuple[list[EvidenceResult], bool]:
    requirements = validate_requirements(json.loads(REQUIREMENTS.read_text(encoding="utf-8")))
    main_head = current_main_head()
    closure_required = release_claim_requires_closure_evidence(closure_required)
    results = [
        live_stack_evidence(requirements["live_stack_e2e"], now=now, main_head=main_head, closure_required=closure_required),
        backup_restore_evidence(requirements["backup_restore_posture"], now=now, closure_required=closure_required),
        claim_hygiene_evidence(requirements["release_claim_hygiene"]),
    ]
    failing = {EvidenceState.MISSING, EvidenceState.STALE}
    ok = all(result.state not in failing for result in results)
    return results, ok


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--now", help="UTC timestamp override for deterministic tests")
    parser.add_argument("--json", action="store_true")
    parser.add_argument("--require-closure-evidence", action="store_true", help="Require fresh live-stack and backup/restore evidence for release or sprint-closure claims")
    args = parser.parse_args()
    now = parse_utc(args.now) if args.now else datetime.now(timezone.utc)
    assert now is not None
    results, ok = run(now, closure_required=args.require_closure_evidence)
    report = {
        "artifactKind": "weave-sprint32-release-evidence-freshness-report-v1",
        "issue": 796,
        "generatedAt": now.isoformat().replace("+00:00", "Z"),
        "ok": ok,
        "results": [result.to_json() for result in results],
    }
    if args.json:
        print(json.dumps(report, indent=2, sort_keys=True))
    else:
        for result in results:
            print(f"{result.id}: {result.state.value} - {result.summary}")
    return 0 if ok else 1


if __name__ == "__main__":
    raise SystemExit(main())
