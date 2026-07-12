#!/usr/bin/env python3
"""Fixture tests for support-safe multi-user Flutter failure diagnostics."""

from __future__ import annotations

import json
import re
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SCRIPT = ROOT / "client" / "tool" / "sanitize_multi_user_test_log.py"


class SanitizeMultiUserTestLogTest(unittest.TestCase):
    def test_failed_auth_is_categorized_without_leaking_input(self) -> None:
        raw = """
AuthFailure: OIDC sign-in failed for alice@example.org
password=correct-horse-battery-staple
access_token=abcdefghijklmnopqrstuvwxyz0123456789
/Users/example/private/integration_test.dart:42
"""
        result = self.run_sanitizer(raw, exit_code=1)

        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertRegex(
            result.stdout,
            r"SANITIZED_MULTI_USER_FAILURE status=failed runIndex=1 "
            r"category=authentication signatureHash=[0-9a-f]{64} supportSafe=true",
        )
        for secret in (
            "alice@example.org",
            "correct-horse-battery-staple",
            "abcdefghijklmnopqrstuvwxyz0123456789",
            "/Users/example/private",
        ):
            self.assertNotIn(secret, result.stdout)

    def test_compile_failure_has_stable_support_safe_shape(self) -> None:
        raw = "Error: Compilation failed for /private/tmp/runner/test.dart:17"

        first = self.run_sanitizer(raw, exit_code=1)
        second = self.run_sanitizer(raw, exit_code=1)

        self.assertEqual(first.returncode, 0, first.stderr)
        self.assertIn("category=compilation", first.stdout)
        first_hash = re.search(r"signatureHash=([0-9a-f]{64})", first.stdout)
        second_hash = re.search(r"signatureHash=([0-9a-f]{64})", second.stdout)
        self.assertIsNotNone(first_hash)
        self.assertIsNotNone(second_hash)
        self.assertEqual(first_hash.group(1), second_hash.group(1))
        self.assertNotIn("/private/tmp", first.stdout)

    def test_passed_marker_is_preserved_without_failure_diagnostic(self) -> None:
        payload = {
            "status": "passed",
            "runIndex": 1,
            "supportSafe": True,
            "authorHash": "a" * 16,
        }
        raw = "MULTI_USER_AUTH_SHELL_RESULT " + json.dumps(payload)

        result = self.run_sanitizer(raw, exit_code=0)

        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("MULTI_USER_AUTH_SHELL_RESULT", result.stdout)
        self.assertIn("status=passed runIndex=1", result.stdout)
        self.assertNotIn("SANITIZED_MULTI_USER_FAILURE", result.stdout)

    def run_sanitizer(
        self,
        raw_log: str,
        *,
        exit_code: int,
    ) -> subprocess.CompletedProcess[str]:
        with tempfile.TemporaryDirectory() as temporary_directory:
            log_path = Path(temporary_directory) / "raw.log"
            log_path.write_text(raw_log, encoding="utf-8")
            return subprocess.run(
                [
                    sys.executable,
                    str(SCRIPT),
                    "--input",
                    str(log_path),
                    "--run-index",
                    "1",
                    "--test-exit-code",
                    str(exit_code),
                ],
                capture_output=True,
                text=True,
                check=False,
            )


if __name__ == "__main__":
    unittest.main()
