#!/usr/bin/env python3
"""Tests for Sprint 32 release evidence freshness classification."""
from __future__ import annotations

import json
import tempfile
import unittest
from datetime import datetime, timezone
from pathlib import Path

from sprint32_release_evidence_freshness import DEFAULT_REQUIREMENTS, EvidenceState, validate

NOW = datetime(2026, 6, 16, 12, 0, tzinfo=timezone.utc)


class Sprint32FreshnessTest(unittest.TestCase):
    def test_pr_gate_does_not_require_live_e2e_when_evidence_is_stale(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            live = self.write(root / "live.json", {"supportSafe": True, "capturedAt": "2026-06-01T00:00:00Z"})
            backup = self.write(root / "backup.json", {"supportSafe": False, "createdAt": "2026-06-01T00:00:00Z", "scope": {"disposableOnly": True}})
            claims = self.write(root / "claims.json", {"commit": "abc123", "supportSafe": True})

            results = validate(DEFAULT_REQUIREMENTS, live, backup, claims, NOW, release_claim=False)

            states = {result.evidence_id: result.state for result in results}
            self.assertEqual(states["live_stack_e2e"], EvidenceState.NOT_REQUIRED)
            self.assertEqual(states["backup_restore_posture"], EvidenceState.PRESENT)
            self.assertEqual(states["release_claim_hygiene"], EvidenceState.PR_CI_GREEN)

    def test_release_claim_marks_stale_live_evidence_as_blocking(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            live = self.write(root / "live.json", {"supportSafe": True, "capturedAt": "2026-06-01T00:00:00Z"})
            backup = self.write(root / "backup.json", {"supportSafe": True, "createdAt": "2026-06-16T10:00:00Z"})
            claims = self.write(root / "claims.json", {"commit": "abc123", "supportSafe": True})

            results = validate(DEFAULT_REQUIREMENTS, live, backup, claims, NOW, release_claim=True)

            self.assertEqual({result.evidence_id: result.state for result in results}["live_stack_e2e"], EvidenceState.STALE)

    def test_missing_backup_posture_is_reported(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            live = self.write(root / "live.json", {"supportSafe": True, "capturedAt": "2026-06-16T10:00:00Z"})
            claims = self.write(root / "claims.json", {"commit": "abc123", "supportSafe": True})

            results = validate(DEFAULT_REQUIREMENTS, live, root / "missing.json", claims, NOW, release_claim=True)

            self.assertEqual({result.evidence_id: result.state for result in results}["backup_restore_posture"], EvidenceState.MISSING)

    def write(self, path: Path, data: dict) -> Path:
        path.write_text(json.dumps(data), encoding="utf-8")
        return path


if __name__ == "__main__":
    unittest.main()
