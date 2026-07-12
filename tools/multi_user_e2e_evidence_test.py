#!/usr/bin/env python3
"""Fixture tests for two-pass multi-user live evidence aggregation."""

from __future__ import annotations

import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SCRIPT = ROOT / "tools" / "multi_user_e2e_evidence.py"
COMMIT = "1" * 40


class MultiUserE2EEvidenceTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        self.log = self.root / "live.log"
        self.identity = self.root / "identity.json"
        self.authorization = self.root / "authorization.json"
        self.calendar_outage = self.root / "calendar-outage.json"
        self.cleanup = self.root / "cleanup.json"
        self.output = self.root / "result.json"
        markers = (
            "MULTI_USER_AUTH_SHELL_RESULT",
            "MULTI_USER_HOME_RESULT",
            "MULTI_USER_CHAT_RESULT",
            "MULTI_USER_FILES_RESULT",
            "MULTI_USER_CALENDAR_RESULT",
            "MULTI_USER_SETTINGS_PROFILE_RESULT",
            "MULTI_USER_FAILURE_CONTAINMENT_RESULT",
            "MULTI_USER_AUTHORIZATION_RESULT",
        )
        passed_facts = {
            "MULTI_USER_AUTH_SHELL_RESULT": {
                "actorCount": 3,
                "authorHash": "1" * 16,
                "collaboratorHash": "2" * 16,
                "outsiderHash": "3" * 16,
                "sessionRestoreCount": 3,
                "shellReached": True,
                "authorNavigationCount": 6,
                "collaboratorNavigationCount": 6,
                "authorAllDestinationsVisited": True,
                "collaboratorAllDestinationsVisited": True,
                "organizationDiscoveryCount": 3,
                "authorOrganizationDiscovered": True,
                "collaboratorOrganizationDiscovered": True,
                "realDeviceStorageProfiles": True,
            },
            "MULTI_USER_HOME_RESULT": {
                "authorObservedCount": 3,
                "collaboratorObservedCount": 3,
                "outsiderObservedCount": 0,
                "sharedActivityCount": 3,
                "authorizedProjectionMatches": True,
                "actorPerspectiveMatches": True,
                "itemLevelProjectionAvailable": True,
                "unauthorizedActivityExcluded": True,
            },
            "MULTI_USER_CHAT_RESULT": {
                "authorMessageObserved": True,
                "collaboratorReplyObserved": True,
                "ciphertextOnlyTransport": True,
                "outsiderDenied": True,
                "messageCount": 2,
                "messageCleanupComplete": True,
                "redactedMessageCount": 2,
                "roomMembershipCleanupComplete": True,
            },
            "MULTI_USER_FILES_RESULT": {
                "collaboratorObserved": True,
                "authorUpdateObserved": True,
                "outsiderDenied": True,
                "outsiderReadDenied": True,
                "outsiderMutationDenied": True,
                "initialChecksumHash": "4" * 64,
                "updatedChecksumHash": "5" * 64,
                "cleanupComplete": True,
            },
            "MULTI_USER_CALENDAR_RESULT": {
                "collaboratorObserved": True,
                "authorUpdateObserved": True,
                "outsiderDenied": True,
                "outsiderReadDenied": True,
                "outsiderMutationDenied": True,
                "eventCount": 1,
                "cleanupComplete": True,
            },
            "MULTI_USER_SETTINGS_PROFILE_RESULT": {
                "profileCount": 3,
                "settingsPersisted": True,
                "profilePersisted": True,
                "identityIsolation": True,
                "independentLogout": True,
                "buildIdentityVisible": True,
                "cleanupComplete": True,
            },
            "MULTI_USER_FAILURE_CONTAINMENT_RESULT": {
                "calendarUnavailable": True,
                "realCapabilitySnapshot": True,
                "unrelatedRouteCount": 5,
                "shellPreserved": True,
            },
            "MULTI_USER_AUTHORIZATION_RESULT": {
                "chatDenied": True,
                "filesDenied": True,
                "calendarDenied": True,
                "wrongWorkspaceVerified": True,
                "missingCapabilityVerified": True,
                "expiredTokenVerified": True,
                "revokedSessionVerified": True,
                "verifiedModeCount": 4,
            },
        }
        lines = []
        for run_index in (1, 2):
            for marker in markers:
                lines.append(
                    marker
                    + " "
                    + json.dumps(
                        {
                            "status": "passed",
                            "runIndex": run_index,
                            "runHash": "a" * 16,
                            "supportSafe": True,
                            **passed_facts[marker],
                        }
                    )
                )
        self.log.write_text("\n".join(lines) + "\n", encoding="utf-8")
        self.identity.write_text(
            json.dumps(
                {
                    "schemaVersion": "weave.isolated-e2e-identities.v1",
                    "namespaceSha256": "d" * 64,
                    "supportSafe": True,
                    "credentialsIncluded": False,
                    "persistentHumanIdentityChanged": False,
                    "contextAuthorization": {"status": "active_runtime_verified"},
                    "actors": [
                        {"role": "author", "subjectSha256": "a" * 64, "contextSha256": "1" * 64},
                        {"role": "collaborator", "subjectSha256": "b" * 64, "contextSha256": "1" * 64},
                        {"role": "outsider", "subjectSha256": "c" * 64, "contextSha256": "2" * 64},
                    ],
                }
            ),
            encoding="utf-8",
        )
        self.authorization.write_text(
            json.dumps(
                {
                    "schemaVersion": "weave.isolated-e2e-authorization.v1",
                    "completedAtUtc": "2026-07-12T12:00:00Z",
                    "namespaceSha256": "d" * 64,
                    "isolatedRuntimeVerified": True,
                    "markerOwnedIdentitiesVerified": True,
                    "missingCapability": {
                        "actorSha256": "b" * 64,
                        "calendarWriteStatus": 403,
                        "groupRemovedBeforeMint": True,
                        "freshTokenClaimExcludedGroup": True,
                        "supportSafeResponse": True,
                        "groupRestored": True,
                    },
                    "expiredToken": {
                        "boundedLifetimeVerified": True,
                        "realmSettingRestoredBeforeWait": True,
                        "chatStatus": 401,
                        "filesStatus": 401,
                        "calendarStatus": 401,
                    },
                    "revokedSession": {
                        "actorSha256": "a" * 64,
                        "matrixLogoutStatus": 200,
                        "tokenUnexpiredAtLogout": True,
                        "chatReuseStatus": 401,
                    },
                    "restoration": {
                        "calendarEditorMembership": True,
                        "realmAccessTokenLifespan": True,
                        "weaveAppDirectAccessGrants": True,
                    },
                    "persistentHumanChanged": False,
                    "rawIdentityIncluded": False,
                    "rawTokenIncluded": False,
                    "rawProviderPayloadIncluded": False,
                    "supportSafe": True,
                }
            ),
            encoding="utf-8",
        )
        self.cleanup.write_text(
            json.dumps(
                {
                    "schemaVersion": "weave.isolated-e2e-identity-cleanup.v1",
                    "namespaceSha256": "d" * 64,
                    "supportSafe": True,
                    "persistentHumanIdentityChanged": False,
                    "broadCleanupPerformed": False,
                    "credentialsIncluded": False,
                    "rawProviderPayloadIncluded": False,
                    "keycloak": {"usersDeleted": 3, "groupsDeleted": 2, "runMarkerVerified": True},
                }
            ),
            encoding="utf-8",
        )
        self.calendar_outage.write_text(
            json.dumps(
                {
                    "schemaVersion": "weave.isolated-calendar-outage-fixture.v1",
                    "state": "restored",
                    "observedAtUtc": "2026-07-12T12:00:00Z",
                    "namespaceSha256": "d" * 64,
                    "actorSha256": "e" * 64,
                    "calendarSha256": "f" * 64,
                    "cachedHealth": {"calendarStatus": 2, "filesStatus": 2},
                    "recoveryRequired": False,
                    "persistentDogfoodEligible": False,
                    "credentialsIncluded": False,
                    "rawIdentityIncluded": False,
                    "rawProviderPayloadIncluded": False,
                    "supportSafe": True,
                }
            ),
            encoding="utf-8",
        )

    def tearDown(self) -> None:
        self.temp.cleanup()

    def run_script(
        self,
        *,
        run_url: str = "https://github.com/example/weave/actions/runs/1",
    ) -> subprocess.CompletedProcess[str]:
        return subprocess.run(
            [
                sys.executable,
                str(SCRIPT),
                "--candidate-commit",
                COMMIT,
                "--run-url",
                run_url,
                "--test-log",
                str(self.log),
                "--identity-evidence",
                str(self.identity),
                "--authorization-evidence",
                str(self.authorization),
                "--calendar-outage-evidence",
                str(self.calendar_outage),
                "--cleanup-evidence",
                str(self.cleanup),
                "--output",
                str(self.output),
            ],
            cwd=ROOT,
            text=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            check=False,
        )

    def test_two_complete_passes_are_accepted(self) -> None:
        completed = self.run_script()
        self.assertEqual(completed.returncode, 0, completed.stdout + completed.stderr)
        result = json.loads(self.output.read_text(encoding="utf-8"))
        self.assertEqual(result["collaboration"]["repeatCount"], 2)
        self.assertEqual(result["collaboration"]["cleanupStatus"], "passed")

    def test_missing_second_pass_is_rejected(self) -> None:
        lines = [line for line in self.log.read_text(encoding="utf-8").splitlines() if '"runIndex": 2' not in line]
        self.log.write_text("\n".join(lines) + "\n", encoding="utf-8")
        completed = self.run_script()
        self.assertEqual(completed.returncode, 2)
        self.assertIn("exactly twice", completed.stderr)

    def test_wrong_context_isolation_is_rejected(self) -> None:
        data = json.loads(self.identity.read_text(encoding="utf-8"))
        data["actors"][2]["contextSha256"] = "1" * 64
        self.identity.write_text(json.dumps(data), encoding="utf-8")
        completed = self.run_script()
        self.assertEqual(completed.returncode, 2)
        self.assertIn("outsider must not", completed.stderr)

    def test_incomplete_authorization_restoration_is_rejected(self) -> None:
        data = json.loads(self.authorization.read_text(encoding="utf-8"))
        data["restoration"]["calendarEditorMembership"] = False
        self.authorization.write_text(json.dumps(data), encoding="utf-8")
        completed = self.run_script()
        self.assertEqual(completed.returncode, 2)
        self.assertIn("calendarEditorMembership must be true", completed.stderr)

    def test_authorization_namespace_must_match_identity_namespace(self) -> None:
        data = json.loads(self.authorization.read_text(encoding="utf-8"))
        data["namespaceSha256"] = "e" * 64
        self.authorization.write_text(json.dumps(data), encoding="utf-8")
        completed = self.run_script()
        self.assertEqual(completed.returncode, 2)
        self.assertIn("not bound to the identity namespace", completed.stderr)

    def test_calendar_outage_must_be_restored_in_the_same_namespace(self) -> None:
        data = json.loads(self.calendar_outage.read_text(encoding="utf-8"))
        data["state"] = "outage_active"
        data["recoveryRequired"] = True
        self.calendar_outage.write_text(json.dumps(data), encoding="utf-8")
        completed = self.run_script()
        self.assertEqual(completed.returncode, 2)
        self.assertIn("state must be restored", completed.stderr)

        data["state"] = "restored"
        data["recoveryRequired"] = False
        data["namespaceSha256"] = "0" * 64
        self.calendar_outage.write_text(json.dumps(data), encoding="utf-8")
        completed = self.run_script()
        self.assertEqual(completed.returncode, 2)
        self.assertIn("not bound to the identity namespace", completed.stderr)

    def test_raw_identity_field_is_rejected_even_when_safety_flag_is_true(self) -> None:
        data = json.loads(self.authorization.read_text(encoding="utf-8"))
        data["username"] = "disposable@example.invalid"
        self.authorization.write_text(json.dumps(data), encoding="utf-8")
        completed = self.run_script()
        self.assertEqual(completed.returncode, 2)
        self.assertIn("forbidden sensitive field", completed.stderr)

    def test_raw_identity_field_in_marker_is_rejected(self) -> None:
        content = self.log.read_text(encoding="utf-8").replace(
            '"supportSafe": true',
            '"supportSafe": true, "username": "disposable@example.invalid"',
            1,
        )
        self.log.write_text(content, encoding="utf-8")
        completed = self.run_script()
        self.assertEqual(completed.returncode, 2)
        self.assertIn("forbidden sensitive field", completed.stderr)

    def test_run_hash_must_be_lowercase_hex(self) -> None:
        self.log.write_text(
            self.log.read_text(encoding="utf-8").replace(
                "a" * 16,
                "not-a-run-hash!!",
            ),
            encoding="utf-8",
        )
        completed = self.run_script()
        self.assertEqual(completed.returncode, 2)
        self.assertIn("one run hash", completed.stderr)

    def test_run_url_must_be_support_safe_https(self) -> None:
        for run_url in (
            "http://github.com/example/weave/actions/runs/1",
            "https://user:secret@example.invalid/runs/1",
            "https://example.invalid/runs/1?token=secret",
        ):
            with self.subTest(run_url=run_url):
                completed = self.run_script(run_url=run_url)
                self.assertEqual(completed.returncode, 2)
                self.assertIn("run URL", completed.stderr)

    def test_client_authorization_marker_must_cover_all_modes(self) -> None:
        content = self.log.read_text(encoding="utf-8").replace(
            '"expiredTokenVerified": true',
            '"expiredTokenVerified": false',
            1,
        )
        self.log.write_text(content, encoding="utf-8")
        completed = self.run_script()
        self.assertEqual(completed.returncode, 2)
        self.assertIn("expiredTokenVerified=True", completed.stderr)

    def test_green_marker_without_required_live_fact_is_rejected(self) -> None:
        content = self.log.read_text(encoding="utf-8").replace(
            '"ciphertextOnlyTransport": true',
            '"ciphertextOnlyTransport": false',
            1,
        )
        self.log.write_text(content, encoding="utf-8")
        completed = self.run_script()
        self.assertEqual(completed.returncode, 2)
        self.assertIn("ciphertextOnlyTransport=True", completed.stderr)

    def test_blocked_markers_produce_explicit_blocked_evidence(self) -> None:
        content = self.log.read_text(encoding="utf-8")
        content = content.replace(
            'MULTI_USER_HOME_RESULT {"status": "passed"',
            'MULTI_USER_HOME_RESULT {"status": "blocked"',
        )
        self.log.write_text(content, encoding="utf-8")
        completed = self.run_script()
        self.assertEqual(completed.returncode, 0, completed.stdout + completed.stderr)
        result = json.loads(self.output.read_text(encoding="utf-8"))
        self.assertEqual(result["collaboration"]["status"], "blocked")
        self.assertEqual(result["surfaces"]["home"]["status"], "blocked")
        self.assertEqual(result["blockers"][0]["code"], "multi-user-home-not-passed")


if __name__ == "__main__":
    unittest.main(verbosity=2)
