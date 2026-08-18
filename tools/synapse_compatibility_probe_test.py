#!/usr/bin/env python3
"""Unit tests for support-safe Synapse compatibility evidence."""

from __future__ import annotations

import unittest

from synapse_compatibility_probe import (
    CallbackCapture,
    ProbeError,
    _assert_support_safe,
    _canonical_semantic_events,
    _normalized_synapse_version,
    _support_safe_failure,
    _support_safe_result,
)


class SynapseCompatibilityProbeTest(unittest.TestCase):
    def test_semantic_fingerprint_ignores_age_but_rejects_content_change(self) -> None:
        first = {
            "events": [
                {
                    "event_id": "$private",
                    "type": "m.room.canonical_alias",
                    "state_key": "",
                    "content": {"alias": "#private"},
                    "unsigned": {"age": 1},
                    "age": 1,
                }
            ]
        }
        retry = {
            "events": [
                {
                    "age": 999,
                    "unsigned": {"age": 999},
                    "content": {"alias": "#private"},
                    "state_key": "",
                    "type": "m.room.canonical_alias",
                    "event_id": "$private",
                }
            ]
        }
        changed = {"events": [{**retry["events"][0], "content": {"alias": "#changed"}}]}

        self.assertEqual(
            _canonical_semantic_events(first), _canonical_semantic_events(retry)
        )
        self.assertNotEqual(
            _canonical_semantic_events(first), _canonical_semantic_events(changed)
        )

    def test_result_contains_only_support_safe_compatibility_facts(self) -> None:
        capture = CallbackCapture()
        callback = {
            "events": [
                {
                    "event_id": "$private",
                    "room_id": "!private",
                    "sender": "@private",
                    "type": "m.room.canonical_alias",
                    "state_key": "",
                    "content": {"alias": "#private"},
                },
                {
                    "event_id": "$private-two",
                    "room_id": "!private",
                    "sender": "@private",
                    "type": "org.example.future_state",
                    "state_key": "compatibility",
                    "content": {"enabled": True},
                },
                {
                    "event_id": "$private-three",
                    "room_id": "!private",
                    "sender": "@private",
                    "type": "m.room.message",
                    "content": {"body": "private content"},
                },
            ]
        }
        self.assertFalse(capture.record("private-transaction", callback))
        self.assertTrue(capture.record("private-transaction", callback))
        self.assertTrue(capture.record("another-private-transaction", callback))

        result = _support_safe_result(
            target_version="1.136.0",
            reported_version="1.136.0",
            room_version="10",
            capture=capture,
        )

        self.assertEqual(result["status"], "passed")
        self.assertTrue(result["supportSafe"])
        self.assertRegex(result["signatureSha256"], r"^[0-9a-f]{64}$")
        serialized = str(result)
        self.assertNotIn("$private", serialized)
        self.assertNotIn("!private", serialized)
        self.assertNotIn("private content", serialized)

    def test_forbidden_shared_evidence_field_fails_closed(self) -> None:
        with self.assertRaises(ProbeError):
            _assert_support_safe({"supportSafe": True, "roomId": "!private"})

    def test_synapse_version_normalization_ignores_build_suffix(self) -> None:
        self.assertEqual(
            _normalized_synapse_version("1.156.0 (b=release,abc123)"), "1.156.0"
        )
        self.assertEqual(_normalized_synapse_version("not-a-version"), "")

    def test_failed_probe_still_emits_stable_support_safe_evidence(self) -> None:
        result = _support_safe_failure(
            "1.156.0", "COMPATIBILITY_INVARIANT_UNPROVEN"
        )

        self.assertEqual(result["status"], "failed")
        self.assertTrue(result["supportSafe"])
        self.assertRegex(result["signatureSha256"], r"^[0-9a-f]{64}$")


if __name__ == "__main__":
    unittest.main()
