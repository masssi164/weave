#!/usr/bin/env python3
"""Fixture tests for dogfood activation mail evidence checks."""

from __future__ import annotations

import hashlib
import json
import subprocess
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
CHECK = ROOT / "tools" / "dogfood_activation_mail_check.py"


class DogfoodActivationMailCheckTest(unittest.TestCase):
    def test_accepts_bounded_first_owner_activation_evidence(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            tmp_path = Path(tmp)
            email = "human@example.test"
            evidence = self._write_json(
                tmp_path / "first-owner.json",
                {
                    "schemaVersion": "weave-owner-bootstrap-evidence-v2",
                    "requestedRole": "owner",
                    "lifecycleStatus": "pending",
                    "mailMessageMatched": True,
                    "bootstrapAuthorityAbsent": True,
                    "bootstrapMountAbsent": True,
                    "requestAnchorPresent": True,
                    "tokenAbsent": True,
                    "emailSha256": hashlib.sha256(email.encode("utf-8")).hexdigest(),
                    "activation": {
                        "mode": "keycloak-organizations-invitation",
                        "requiredActions": [],
                        "mailSent": True,
                    },
                    "qrOrDeeplinkCarriesSecret": False,
                    "appStoresActivationSecret": False,
                    "supportSafe": True,
                },
            )
            mailpit = self._write_json(
                tmp_path / "mailpit.json",
                {"messages": [{"ID": "mail-1", "To": [{"Address": email}], "Subject": "Update Your Account"}]},
            )

            result = self._run(
                "--activation-evidence-file",
                str(evidence),
                "--expected-invite-ref",
                "handoff-persistent-human",
                "--mailpit-fixture",
                str(mailpit),
            )

            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertIn("DOGFOOD_ACTIVATION_MAIL_RESULT", result.stdout)
            self.assertNotIn(email, result.stdout)

    def test_accepts_support_safe_activation_evidence_and_mailpit_fixture(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            tmp_path = Path(tmp)
            email = "massimo@example.test"
            evidence = self._write_json(
                tmp_path / "activation.json",
                {
                    "schemaVersion": "weave.dogfood.activation-invite.v1",
                    "realm": "weave",
                    "usernameSha256": hashlib.sha256(b"massimo").hexdigest(),
                    "emailSha256": hashlib.sha256(email.encode("utf-8")).hexdigest(),
                    "role": "member",
                    "workspaceGroup": "/members",
                    "inviteRef": "handoff-s32-massimo-dogfood-home",
                    "activation": {
                        "mode": "keycloak-required-actions-email",
                        "requiredActions": ["VERIFY_EMAIL", "UPDATE_PASSWORD"],
                        "lifespanSeconds": 900,
                        "clientId": "weave-app",
                        "redirectUriClass": "weave-ios-custom-scheme",
                        "mailSent": True,
                    },
                    "qrOrDeeplinkCarriesSecret": False,
                    "appStoresActivationSecret": False,
                    "supportSafe": True,
                },
            )
            mailpit = self._write_json(
                tmp_path / "mailpit.json",
                {
                    "messages": [
                        {
                            "ID": "mail-1",
                            "To": [{"Address": email}],
                            "Subject": "Update Your Account",
                        }
                    ]
                },
            )

            result = self._run(
                "--activation-evidence-file",
                str(evidence),
                "--expected-invite-ref",
                "handoff-s32-massimo-dogfood-home",
                "--mailpit-fixture",
                str(mailpit),
            )

            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertIn("DOGFOOD_ACTIVATION_MAIL_RESULT", result.stdout)
            self.assertNotIn(email, result.stdout)

    def test_rejects_activation_action_link_in_support_safe_evidence(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            tmp_path = Path(tmp)
            email = "massimo@example.test"
            evidence = self._write_json(
                tmp_path / "activation.json",
                {
                    "schemaVersion": "weave.dogfood.activation-invite.v1",
                    "emailSha256": hashlib.sha256(email.encode("utf-8")).hexdigest(),
                    "inviteRef": "handoff-s32-massimo-dogfood-home",
                    "activation": {
                        "mode": "keycloak-required-actions-email",
                        "requiredActions": ["VERIFY_EMAIL", "UPDATE_PASSWORD"],
                        "mailSent": True,
                        "debugActionLink": (
                            "https://auth.weave.test/realms/weave/"
                            "protocol/openid-connect/registrations?key=secret"
                        ),
                    },
                    "qrOrDeeplinkCarriesSecret": False,
                    "appStoresActivationSecret": False,
                    "supportSafe": True,
                },
            )
            mailpit = self._write_json(tmp_path / "mailpit.json", {"messages": []})

            result = self._run(
                "--activation-evidence-file",
                str(evidence),
                "--expected-invite-ref",
                "handoff-s32-massimo-dogfood-home",
                "--mailpit-fixture",
                str(mailpit),
            )

            self.assertNotEqual(result.returncode, 0)
            self.assertIn("activation action link", result.stderr)

    def _run(self, *args: str) -> subprocess.CompletedProcess[str]:
        return subprocess.run(
            ["python3", str(CHECK), *args],
            cwd=ROOT,
            text=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
        )

    def _write_json(self, path: Path, payload: object) -> Path:
        path.write_text(json.dumps(payload), encoding="utf-8")
        return path


if __name__ == "__main__":
    unittest.main(verbosity=2)
