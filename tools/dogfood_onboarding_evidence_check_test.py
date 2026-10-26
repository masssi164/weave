#!/usr/bin/env python3
"""Fail-closed contract tests for external native member evidence."""

import io
import json
from contextlib import redirect_stderr, redirect_stdout
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parent))
from tools import dogfood_onboarding_evidence_check as checker
from tools.dogfood_functional_smoke_test import receipt


class NativeOnboardingEvidenceTest(unittest.TestCase):
    def check_native_receipt(self, stage, evidence):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'functional-evidence.json'
            path.write_text(json.dumps(evidence))
            arguments = ['dogfood_onboarding_evidence_check.py',
                         '--functional-evidence-file', str(path),
                         '--expected-candidate', 'a' * 40,
                         '--expected-source-tree', 'b' * 40,
                         '--expected-specification', 'c' * 40,
                         '--qualification-run-id', '123',
                         '--qualification-run-attempt', '1',
                         '--stage', stage]
            output = io.StringIO()
            with patch.object(sys, 'argv', arguments), redirect_stdout(output), redirect_stderr(io.StringIO()):
                try:
                    exit_code = checker.main()
                except SystemExit as failure:
                    exit_code = failure.code
            return exit_code, output.getvalue()

    def test_native_receipt_proves_ordinary_product_use_without_owner_hil(self):
        for stage in ('deployment', 'physical'):
            with self.subTest(stage=stage):
                evidence = receipt(stage=stage)
                if stage == 'physical':
                    evidence['ownerEntryObserved'] = True
                exit_code, output = self.check_native_receipt(stage, evidence)
                self.assertEqual(exit_code, 0)
                self.assertIn('DOGFOOD_MEMBER_ONBOARDING_RESULT status=passed', output)
                self.assertIn('DOGFOOD_POST_LOGIN_CHAT_FILES_RESULT chatState=usable filesState=usable', output)
                self.assertIn('DOGFOOD_SESSION_CONTINUITY_RESULT status=passed', output)
                self.assertIn('ownerActivation=not-proven', output)

    def test_wrong_candidate_and_incomplete_product_readback_fail_closed(self):
        for field, value in (('candidateCommit', 'd' * 40),
                             ('independentPeerReadbackVerified', False),
                             ('retainedStateRestartVerified', False)):
            with self.subTest(field=field):
                evidence = receipt(stage='deployment')
                evidence[field] = value
                exit_code, output = self.check_native_receipt('deployment', evidence)
                self.assertNotEqual(exit_code, 0)
                self.assertEqual(output, '')

    def test_observed_owner_entry_is_required_for_physical_stage(self):
        exit_code, output = self.check_native_receipt('physical', receipt(stage='physical'))
        self.assertNotEqual(exit_code, 0)
        self.assertEqual(output, '')


if __name__ == '__main__':
    unittest.main()
