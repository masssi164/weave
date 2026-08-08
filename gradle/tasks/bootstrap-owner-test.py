#!/usr/bin/env python3
"""Focused contract tests for support-safe owner-bootstrap evidence."""

from __future__ import annotations

import hashlib
import importlib.util
import json
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
SPEC = importlib.util.spec_from_file_location(
    "bootstrap_owner", ROOT / "gradle" / "tasks" / "bootstrap-owner.py"
)
assert SPEC and SPEC.loader
bootstrap_owner = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(bootstrap_owner)


class BootstrapOwnerEvidenceTest(unittest.TestCase):
    def test_hashes_the_canonical_invitation_handle_and_email(self) -> None:
        invitation_handle = "inv_opaque-owner-invitation"
        email = "Owner@Example.Test"

        result = bootstrap_owner.support_safe_result(
            {
                "invitationHandle": invitation_handle,
                "organizationId": "weave-dogfood",
                "lifecycleStatus": "pending",
                "provisioningStatus": "pending",
                "requestedRole": "owner",
            },
            email,
        )

        self.assertEqual(result["schema"], "weave-owner-bootstrap-evidence-v2")
        self.assertTrue(result["supportSafe"])
        self.assertEqual(
            result["invitationHandleSha256"],
            hashlib.sha256(invitation_handle.encode("utf-8")).hexdigest(),
        )
        self.assertEqual(
            result["emailSha256"],
            hashlib.sha256(email.lower().encode("utf-8")).hexdigest(),
        )
        self.assertNotIn("invitationHandle", result)
        self.assertNotIn("providerInvitationId", result)
        serialized = json.dumps(result, sort_keys=True)
        self.assertNotIn(invitation_handle, serialized)
        self.assertNotIn(email, serialized)

    def test_rejects_the_removed_provider_identifier_contract(self) -> None:
        with self.assertRaisesRegex(ValueError, "invitationHandle"):
            bootstrap_owner.support_safe_result(
                {
                    "providerInvitationId": "provider-secret-shaped-id",
                    "organizationId": "weave-dogfood",
                    "lifecycleStatus": "pending",
                    "provisioningStatus": "pending",
                    "requestedRole": "owner",
                },
                "owner@example.test",
            )

    def test_rejects_unsupported_invite_time_capabilities(self) -> None:
        with self.assertRaisesRegex(ValueError, "invalid authority projection"):
            bootstrap_owner.support_safe_result(
                {
                    "invitationHandle": "inv_opaque-owner-invitation",
                    "organizationId": "weave-dogfood",
                    "lifecycleStatus": "pending",
                    "provisioningStatus": "pending",
                    "requestedRole": "owner",
                    "capabilities": ["agent-runtime.entitled"],
                },
                "owner@example.test",
            )


if __name__ == "__main__":
    unittest.main(verbosity=2)
