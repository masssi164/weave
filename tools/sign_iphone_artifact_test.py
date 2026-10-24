#!/usr/bin/env python3
"""Signing prerequisites cannot silently change identities, capabilities or device scope."""
from datetime import datetime, timedelta, timezone
import copy
import hashlib
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

import sign_iphone_artifact as signing


class SigningPrerequisites(unittest.TestCase):
    def setUp(self):
        self.certificate = b"fixture certificate DER"
        self.identity = hashlib.sha1(self.certificate).hexdigest().upper()
        self.team = "ABCDEFGHIJ"
        self.device = "00008020-0012345678901234"
        self.bundle = "com.massimotter.weave"
        self.now = datetime(2026, 10, 24, tzinfo=timezone.utc)
        self.profile = {"ExpirationDate": self.now + timedelta(days=6), "TeamIdentifier": [self.team],
            "ApplicationIdentifierPrefix": [self.team], "ProvisionedDevices": [self.device],
            "DeveloperCertificates": [self.certificate], "Entitlements": {
                "application-identifier": self.team + "." + self.bundle,
                "com.apple.developer.team-identifier": self.team, "get-task-allow": True,
                "keychain-access-groups": [self.team + ".*"]}}

    def compatible(self, profile):
        return signing.profile_compatible(profile, self.bundle, self.team, self.identity, self.device, self.now)

    def test_existing_matching_profile_is_accepted(self):
        self.assertTrue(self.compatible(self.profile))
        entitlements = signing.signing_entitlements(self.profile, self.bundle,
            {"keychain-access-groups": ["$(AppIdentifierPrefix)com.massimotter.weave"]})
        self.assertEqual(entitlements["keychain-access-groups"], [self.team + ".com.massimotter.weave"])
        self.assertEqual(entitlements["application-identifier"], self.team + "." + self.bundle)

    def test_expired_wrong_team_device_or_certificate_profile_is_rejected(self):
        for key, value in (("ExpirationDate", self.now), ("TeamIdentifier", ["OTHERTEAM00"]),
                           ("ProvisionedDevices", ["different-device"]), ("DeveloperCertificates", [b"wrong"])):
            with self.subTest(key=key):
                self.assertFalse(self.compatible({**self.profile, key: value}))

    def test_profile_must_match_application_and_debugging_capability(self):
        for key, value in (("application-identifier", self.team + ".other.app"), ("get-task-allow", False)):
            profile = copy.deepcopy(self.profile)
            profile["Entitlements"][key] = value
            with self.subTest(key=key):
                self.assertFalse(self.compatible(profile))

    def test_missing_capability_is_a_boundary_not_silently_stripped(self):
        with self.assertRaisesRegex(signing.SigningError, "profile-capability-mismatch"):
            signing.signing_entitlements(self.profile, self.bundle,
                {"com.apple.developer.associated-domains": ["applinks:weave.test"]})

    def test_no_local_compilation_fallback_when_profile_is_missing(self):
        with tempfile.TemporaryDirectory() as name, patch.object(signing, "run") as command:
            with self.assertRaisesRegex(signing.SigningError, "existing-device-provisioning-profile-required"):
                signing.select_profile([Path(name)], self.bundle, self.team, self.identity, self.device)
            command.assert_not_called()

    def test_unique_existing_identity_must_cover_both_applications(self):
        with patch.object(signing, "run", return_value=(self.identity + " Apple Development").encode()), \
                patch.object(signing, "select_profile", return_value=(Path("profile"), self.profile)) as select:
            actual = signing.select_identity([], [self.bundle, "test.runner"], self.team, self.device)
            self.assertEqual(actual, self.identity)
            self.assertEqual(select.call_count, 2)
        with patch.object(signing, "run", return_value=(self.identity + "\n" + "0" * 40).encode()), \
                patch.object(signing, "select_profile", return_value=(Path("profile"), self.profile)), \
                self.assertRaisesRegex(signing.SigningError, "one-existing-compatible-signing-identity-required"):
            signing.select_identity([], [self.bundle, "test.runner"], self.team, self.device)

    def test_certificate_team_can_differ_from_provisioning_team(self):
        certificate_team = "CERTTEAM01"
        with patch.object(signing, "run", return_value=("subject=\n OU=" + certificate_team + "\n").encode()):
            signing.verify_certificate_team(self.profile, self.identity, certificate_team)
            with self.assertRaisesRegex(signing.SigningError, "developer-certificate-team-mismatch"):
                signing.verify_certificate_team(self.profile, self.identity, self.team)

    def test_application_identifier_prefix_is_preserved_independently_from_team(self):
        profile = copy.deepcopy(self.profile)
        prefix = "PREFIX0001"
        profile["ApplicationIdentifierPrefix"] = [prefix]
        profile["Entitlements"]["application-identifier"] = prefix + "." + self.bundle
        profile["Entitlements"]["keychain-access-groups"] = [prefix + ".*"]
        self.assertTrue(self.compatible(profile))
        result = signing.signing_entitlements(profile, self.bundle,
            {"keychain-access-groups": ["$(AppIdentifierPrefix)$(CFBundleIdentifier)"]})
        self.assertEqual(result["com.apple.developer.team-identifier"], self.team)
        self.assertEqual(result["keychain-access-groups"], [prefix + "." + self.bundle])


if __name__ == "__main__":
    unittest.main()
