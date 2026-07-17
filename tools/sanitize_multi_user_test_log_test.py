#!/usr/bin/env python3
"""Fixture tests for support-safe multi-user Flutter failure diagnostics."""

from __future__ import annotations

import json
import re
import runpy
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SCRIPT = ROOT / "client" / "tool" / "sanitize_multi_user_test_log.py"
DART_TEST = (
    ROOT
    / "client"
    / "integration_test"
    / "multi_user_collaboration_e2e_test.dart"
)


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
            r"category=authentication phase=unknown "
            r"signatureHash=[0-9a-f]{64} supportSafe=true",
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

    def test_failed_assertion_reports_only_allowlisted_last_phase(self) -> None:
        raw = """
00:00 +0: MULTI_USER_PROGRESS phase=author-write runIndex=1
MULTI_USER_PROGRESS phase=author-chat-room runIndex=1
MULTI_USER_PROGRESS phase=outsider-authorization runIndex=1
MULTI_USER_PROGRESS phase=containment-calendar runIndex=2
TestFailure: Expected: true Actual: false
"""

        result = self.run_sanitizer(raw, exit_code=1)

        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn(
            "category=assertion phase=outsider-authorization",
            result.stdout,
        )
        self.assertNotIn("phase=author-chat-room", result.stdout)
        self.assertNotIn("containment-calendar", result.stdout)

    def test_fine_grained_author_chat_phase_is_support_safe(self) -> None:
        raw = """
MULTI_USER_PROGRESS phase=author-write runIndex=1
MULTI_USER_PROGRESS phase=author-chat-room runIndex=1
TestFailure: Expected: true Actual: false
"""

        result = self.run_sanitizer(raw, exit_code=1)

        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("category=assertion phase=author-chat-room", result.stdout)

    def test_encrypted_device_exchange_phase_is_support_safe(self) -> None:
        raw = """
MULTI_USER_PROGRESS phase=room-provision runIndex=1
MULTI_USER_PROGRESS phase=room-key-exchange-collaborator runIndex=1
TestFailure: Expected: true Actual: false
"""

        result = self.run_sanitizer(raw, exit_code=1)

        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn(
            "category=assertion phase=room-key-exchange-collaborator",
            result.stdout,
        )

    def test_actor_bootstrap_subphases_are_support_safe(self) -> None:
        raw = """
MULTI_USER_PROGRESS phase=room-author-bootstrap runIndex=1
MULTI_USER_PROGRESS phase=containment-session-exchange runIndex=2
TestFailure: Expected: true Actual: false
"""

        first = self.run_sanitizer(raw, exit_code=1)
        second = self.run_sanitizer(raw, exit_code=1, run_index=2)

        self.assertEqual(first.returncode, 0, first.stderr)
        self.assertEqual(second.returncode, 0, second.stderr)
        self.assertIn(
            "category=assertion phase=room-author-bootstrap",
            first.stdout,
        )
        self.assertIn(
            "category=assertion phase=containment-session-exchange",
            second.stdout,
        )

    def test_encrypted_peer_decryption_subphase_is_support_safe(self) -> None:
        raw = """
MULTI_USER_PROGRESS phase=room-key-exchange-author-send runIndex=1
MULTI_USER_PROGRESS phase=room-key-exchange-collaborator-observe-author runIndex=1
TestFailure: Expected: true Actual: false
"""

        result = self.run_sanitizer(raw, exit_code=1)

        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn(
            "category=assertion "
            "phase=room-key-exchange-collaborator-observe-author",
            result.stdout,
        )

    def test_allowlisted_e2ee_support_code_is_preserved(self) -> None:
        raw = """
MULTI_USER_PROGRESS phase=room-key-exchange-collaborator-observe-author runIndex=1
The two established Matrix devices could not exchange encrypted messages.
Failure code: M_WEAVE_E2EE_MISSING_MEGOLM_SESSION.
"""

        result = self.run_sanitizer(raw, exit_code=1)

        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn(
            "supportCode=M_WEAVE_E2EE_MISSING_MEGOLM_SESSION supportSafe=true",
            result.stdout,
        )

    def test_allowlisted_olm_support_code_is_preserved(self) -> None:
        raw = """
MULTI_USER_PROGRESS phase=room-key-exchange-collaborator-observe-author runIndex=1
The receiving Matrix device could not decrypt a to-device envelope.
Failure code: M_WEAVE_E2EE_OLM_DECRYPTION_FAILURE.
"""

        result = self.run_sanitizer(raw, exit_code=1)

        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn(
            "supportCode=M_WEAVE_E2EE_OLM_DECRYPTION_FAILURE supportSafe=true",
            result.stdout,
        )

    def test_send_and_session_support_codes_are_preserved(self) -> None:
        for support_code in (
            "M_WEAVE_E2EE_PEER_DEVICE_PENDING",
            "M_WEAVE_E2EE_MEMBER_KEYS",
            "M_WEAVE_E2EE_SEND",
            "M_UNKNOWN_TOKEN",
        ):
            with self.subTest(support_code=support_code):
                raw = f"""
MULTI_USER_PROGRESS phase=room-key-exchange-author-send runIndex=1
Failure code: {support_code}.
"""

                result = self.run_sanitizer(raw, exit_code=1)

                self.assertEqual(result.returncode, 0, result.stderr)
                self.assertIn(
                    f"supportCode={support_code} supportSafe=true",
                    result.stdout,
                )

    def test_unrecognized_support_code_is_not_preserved(self) -> None:
        raw = """
MULTI_USER_PROGRESS phase=room-key-exchange-author-observe-collaborator runIndex=1
Failure code: M_WEAVE_E2EE_PROVIDER_PAYLOAD_PRIVATE.
"""

        result = self.run_sanitizer(raw, exit_code=1)

        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertNotIn("supportCode=", result.stdout)
        self.assertNotIn("PROVIDER_PAYLOAD_PRIVATE", result.stdout)

    def test_fine_grained_collaborator_domain_phase_is_support_safe(self) -> None:
        raw = """
MULTI_USER_PROGRESS phase=collaborator-observe runIndex=1
MULTI_USER_PROGRESS phase=collaborator-files-observe runIndex=1
TestFailure: Expected: true Actual: false
"""

        result = self.run_sanitizer(raw, exit_code=1)

        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn(
            "category=assertion phase=collaborator-files-observe",
            result.stdout,
        )

    def test_dart_and_sanitizer_progress_phase_allowlists_match(self) -> None:
        dart_source = DART_TEST.read_text(encoding="utf-8")
        dart_block = re.search(
            r"const _supportSafeProgressPhases = <String>\{(.*?)\n\};",
            dart_source,
            flags=re.DOTALL,
        )
        self.assertIsNotNone(dart_block)
        dart_phases = set(re.findall(r"'([^']+)'", dart_block.group(1)))
        sanitizer_phases = set(runpy.run_path(str(SCRIPT))["PROGRESS_PHASES"])

        self.assertEqual(dart_phases, sanitizer_phases)

    def run_sanitizer(
        self,
        raw_log: str,
        *,
        exit_code: int,
        run_index: int = 1,
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
                    str(run_index),
                    "--test-exit-code",
                    str(exit_code),
                ],
                capture_output=True,
                text=True,
                check=False,
            )


if __name__ == "__main__":
    unittest.main()
