#!/usr/bin/env python3
"""Classify Sprint 32 release evidence freshness without requiring live E2E on every PR."""
from __future__ import annotations

import argparse
import json
from dataclasses import dataclass
from datetime import datetime, timezone
from enum import Enum
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[1]
DEFAULT_REQUIREMENTS = ROOT / "tools" / "fixtures" / "release_evidence" / "sprint32_freshness_requirements.json"
DEFAULT_LIVE = ROOT / "release" / "provider-lab" / "local-forgejo-e2e" / "local-stack-e2e-handoff.fixture.json"
DEFAULT_BACKUP = ROOT / "release" / "provider-lab" / "operator-recovery" / "backup-manifest.disposable.json"
DEFAULT_CLAIMS = ROOT / "release" / "north-star-maturity-scoreboard.json"


class EvidenceState(str, Enum):
    PR_CI_GREEN = "pr_ci_green"
    LOCAL_GATE_GREEN = "local_gate_green"
    FRESH = "fresh"
    STALE = "stale"
    MISSING = "missing"
    NOT_REQUIRED = "not_required"
    PRESENT = "present"


@dataclass(frozen=True)
class EvidenceResult:
    evidence_id: str
    state: EvidenceState
    required_for_release_claim: bool
    support_safe: bool
    source: str
    summary: str

    def as_dict(self) -> dict[str, Any]:
        return {
            "id": self.evidence_id,
            "state": self.state.value,
            "requiredForReleaseClaim": self.required_for_release_claim,
            "supportSafe": self.support_safe,
            "source": self.source,
            "summary": self.summary,
        }


def load_json(path: Path) -> dict[str, Any] | None:
    if not path.exists():
        return None
    return json.loads(path.read_text(encoding="utf-8"))


def parse_time(value: Any) -> datetime | None:
    if not isinstance(value, str) or not value:
        return None
    try:
        return datetime.fromisoformat(value.replace("Z", "+00:00")).astimezone(timezone.utc)
    except ValueError:
        return None


def age_state(timestamp: datetime | None, now: datetime, maximum_age_hours: int) -> EvidenceState:
    if timestamp is None:
        return EvidenceState.PRESENT
    age_hours = (now - timestamp).total_seconds() / 3600
    return EvidenceState.FRESH if 0 <= age_hours <= maximum_age_hours else EvidenceState.STALE


def is_support_safe(data: dict[str, Any] | None, forbidden: list[str]) -> bool:
    if data is None:
        return False
    scrubbed = {key: value for key, value in data.items() if key not in {"forbiddenEvidence", "forbiddenEvidenceFragments", "forbiddenResponseFragments", "limitations"}}
    rendered = json.dumps(scrubbed, sort_keys=True)
    if data.get("supportSafe") is False and not data.get("scope", {}).get("disposableOnly"):
        return False
    return not any(fragment in rendered for fragment in forbidden)


def classify_live_stack(data: dict[str, Any] | None, now: datetime, max_hours: int, forbidden: list[str], release_claim: bool) -> EvidenceResult:
    if data is None:
        return EvidenceResult("live_stack_e2e", EvidenceState.MISSING, release_claim, False, str(DEFAULT_LIVE), "live-stack handoff evidence is missing")
    timestamp = parse_time(data.get("capturedAt") or data.get("createdAt") or data.get("evidenceCapturedAt"))
    state = age_state(timestamp, now, max_hours)
    if not release_claim and state in {EvidenceState.STALE, EvidenceState.MISSING}:
        state = EvidenceState.NOT_REQUIRED
    return EvidenceResult("live_stack_e2e", state, release_claim, is_support_safe(data, forbidden), str(DEFAULT_LIVE), "live-stack evidence classified without requiring PR-time E2E")


def classify_backup(data: dict[str, Any] | None, now: datetime, max_hours: int, forbidden: list[str], release_claim: bool) -> EvidenceResult:
    if data is None:
        return EvidenceResult("backup_restore_posture", EvidenceState.MISSING, release_claim, False, str(DEFAULT_BACKUP), "backup/restore posture is missing")
    created = parse_time(data.get("createdAt"))
    state = age_state(created, now, max_hours)
    if not release_claim and state == EvidenceState.STALE:
        state = EvidenceState.PRESENT
    return EvidenceResult("backup_restore_posture", state, release_claim, is_support_safe(data, forbidden), str(DEFAULT_BACKUP), "backup/restore posture classified from manifest metadata")


def classify_claims(data: dict[str, Any] | None, forbidden: list[str]) -> EvidenceResult:
    if data is None:
        return EvidenceResult("release_claim_hygiene", EvidenceState.MISSING, True, False, str(DEFAULT_CLAIMS), "release claim hygiene artifact is missing")
    commit = data.get("commit") or data.get("metadata", {}).get("commit")
    state = EvidenceState.PR_CI_GREEN if commit else EvidenceState.LOCAL_GATE_GREEN
    return EvidenceResult("release_claim_hygiene", state, True, is_support_safe(data, forbidden), str(DEFAULT_CLAIMS), "claim hygiene artifact names concrete merged evidence")


def validate(requirements_path: Path, live_path: Path, backup_path: Path, claims_path: Path, now: datetime, release_claim: bool) -> list[EvidenceResult]:
    requirements = load_json(requirements_path)
    if requirements is None or requirements.get("artifactKind") != "weave-sprint32-release-evidence-freshness-v1":
        raise ValueError("Sprint 32 freshness requirements are missing or invalid")
    required = {item["id"]: item for item in requirements["requiredEvidence"]}
    forbidden = list(requirements.get("forbiddenEvidenceFragments", []))
    return [
        classify_live_stack(load_json(live_path), now, int(required["live_stack_e2e"]["maximumAgeHours"]), forbidden, release_claim),
        classify_backup(load_json(backup_path), now, int(required["backup_restore_posture"]["maximumAgeHours"]), forbidden, release_claim),
        classify_claims(load_json(claims_path), forbidden),
    ]


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--requirements", type=Path, default=DEFAULT_REQUIREMENTS)
    parser.add_argument("--live-evidence", type=Path, default=DEFAULT_LIVE)
    parser.add_argument("--backup-evidence", type=Path, default=DEFAULT_BACKUP)
    parser.add_argument("--claim-evidence", type=Path, default=DEFAULT_CLAIMS)
    parser.add_argument("--now", default=datetime.now(timezone.utc).isoformat())
    parser.add_argument("--release-claim", action="store_true")
    parser.add_argument("--json", action="store_true")
    args = parser.parse_args()
    now = parse_time(args.now) or datetime.now(timezone.utc)
    results = validate(args.requirements, args.live_evidence, args.backup_evidence, args.claim_evidence, now, args.release_claim)
    report = {"artifactKind": "weave-sprint32-release-evidence-freshness-report-v1", "issue": 796, "releaseClaim": args.release_claim, "results": [r.as_dict() for r in results]}
    if args.json:
        print(json.dumps(report, indent=2, sort_keys=True))
    else:
        for result in results:
            print(f"sprint32-freshness: {result.evidence_id}={result.state.value} supportSafe={str(result.support_safe).lower()} releaseRequired={str(result.required_for_release_claim).lower()}")
    blocking = {EvidenceState.MISSING, EvidenceState.STALE}
    if any((r.required_for_release_claim and r.state in blocking) or not r.support_safe for r in results):
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
