#!/usr/bin/env python3
from __future__ import annotations

import json
import tempfile
import unittest
from pathlib import Path

import portability_contract_check as check


class PortabilityContractCheckTest(unittest.TestCase):
    def test_matrix_fixture_blocks_e2ee_apply_and_is_support_safe(self):
        path = Path("tools/fixtures/portability/matrix_chat_preflight_apply_blocked.json")
        self.assertEqual(check.validate_fixture(path), [])

    def test_rejects_e2ee_history_available_claim_and_raw_secret(self):
        with tempfile.TemporaryDirectory() as tmp:
            fixture = Path(tmp) / "bad.json"
            fixture.write_text(json.dumps({
                "domain": "chat",
                "provider": "matrix",
                "operation": "preflight",
                "supportSafe": True,
                "providerDiagnosticsRedacted": True,
                "capabilities": [{
                    "key": "encrypted_room_history",
                    "classification": "available",
                    "losslessClaim": True,
                    "evidence": ["Bearer raw-token"]
                }],
                "blockedOperations": [],
                "rollback": {"classification": "available"}
            }), encoding="utf-8")

            errors = check.validate_fixture(fixture)

        self.assertTrue(any("encrypted_room_history must be unsupported" in error for error in errors))
        self.assertTrue(any("leaks provider secret/url" in error for error in errors))
        self.assertTrue(any("destructive apply" in error for error in errors))

    def test_forbidden_marketing_claim_scan(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            docs = root / "docs"
            docs.mkdir()
            (docs / "claim.md").write_text("This is a GDPR-proof lossless migration.\n", encoding="utf-8")

            errors = check.validate_claim_text(root)

        self.assertGreaterEqual(len(errors), 2)


if __name__ == "__main__":
    unittest.main()
