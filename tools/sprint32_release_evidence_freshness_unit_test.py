#!/usr/bin/env python3
"""Fixture tests for Sprint 32 release evidence freshness states."""
from __future__ import annotations

import json
import tempfile
from datetime import datetime, timezone
from pathlib import Path

import sprint32_release_evidence_freshness_test as freshness

NOW = datetime(2026, 6, 16, 12, 0, tzinfo=timezone.utc)


def write_json(path: Path, data: dict) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")


def with_fixture_paths(tmp: Path) -> None:
    freshness.LIVE_HANDOFF = tmp / "live.json"
    freshness.RESTORE_RECEIPT = tmp / "restore.json"
    freshness.BACKUP_MANIFEST = tmp / "backup.json"
    freshness.CLAIM_DOC = tmp / "unreleased.md"
    freshness.current_main_head = lambda: "abc123def456"
    freshness.release_claim_requires_closure_evidence = lambda force=False: force
    freshness.CLAIM_DOC.write_text("# Unreleased\n\nNo closure claim.\n", encoding="utf-8")


def fresh_live(observed: str = "2026-06-16T10:00:00Z") -> dict:
    return {
        "artifactKind": "weave-local-forgejo-e2e-handoff-v1",
        "supportSafe": True,
        "observedAt": observed,
        "mainlineDependencyStatus": {"originMainHead": "abc123"},
    }


def fresh_restore(observed: str = "2026-06-16T10:00:00Z") -> dict:
    return {"artifactKind": "weave-restore-receipt-v1", "supportSafe": True, "createdAt": observed, "status": "passed"}


def private_backup() -> dict:
    return {
        "artifactKind": "weave-backup-manifest-v1",
        "supportSafe": False,
        "createdAt": "2026-06-16T10:00:00Z",
        "scope": {"shareExternally": False, "artifactsContainSecretsOrMemberData": True},
    }


def run_case(tmp: Path, *, require: bool = True):
    with_fixture_paths(tmp)
    return freshness.run(NOW, closure_required=require)


def test_fresh_required_evidence_passes() -> None:
    with tempfile.TemporaryDirectory() as td:
        tmp = Path(td)
        with_fixture_paths(tmp)
        write_json(freshness.LIVE_HANDOFF, fresh_live())
        write_json(freshness.RESTORE_RECEIPT, fresh_restore())
        write_json(freshness.BACKUP_MANIFEST, private_backup())
        results, ok = freshness.run(NOW, closure_required=True)
        assert ok
        assert {result.id: result.state for result in results}["live_stack_e2e"] == freshness.EvidenceState.FRESH
        assert {result.id: result.state for result in results}["backup_restore_posture"] == freshness.EvidenceState.FRESH


def test_stale_live_evidence_blocks_required_closure() -> None:
    with tempfile.TemporaryDirectory() as td:
        tmp = Path(td)
        with_fixture_paths(tmp)
        write_json(freshness.LIVE_HANDOFF, fresh_live("2026-06-14T10:00:00Z"))
        write_json(freshness.RESTORE_RECEIPT, fresh_restore())
        write_json(freshness.BACKUP_MANIFEST, private_backup())
        results, ok = freshness.run(NOW, closure_required=True)
        assert not ok
        assert {result.id: result.state for result in results}["live_stack_e2e"] == freshness.EvidenceState.STALE


def test_missing_backup_restore_blocks_required_closure() -> None:
    with tempfile.TemporaryDirectory() as td:
        tmp = Path(td)
        with_fixture_paths(tmp)
        write_json(freshness.LIVE_HANDOFF, fresh_live())
        results, ok = freshness.run(NOW, closure_required=True)
        assert not ok
        assert {result.id: result.state for result in results}["backup_restore_posture"] == freshness.EvidenceState.MISSING


def test_normal_pr_gate_does_not_require_live_or_restore() -> None:
    with tempfile.TemporaryDirectory() as td:
        tmp = Path(td)
        with_fixture_paths(tmp)
        results, ok = freshness.run(NOW, closure_required=False)
        assert ok
        states = {result.id: result.state for result in results}
        assert states["live_stack_e2e"] == freshness.EvidenceState.NOT_REQUIRED
        assert states["backup_restore_posture"] == freshness.EvidenceState.NOT_REQUIRED


if __name__ == "__main__":
    test_fresh_required_evidence_passes()
    test_stale_live_evidence_blocks_required_closure()
    test_missing_backup_restore_blocks_required_closure()
    test_normal_pr_gate_does_not_require_live_or_restore()
