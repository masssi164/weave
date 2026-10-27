#!/usr/bin/env python3
"""Physical delivery consumes signed binaries and preserves owner state on failures."""
from __future__ import annotations

from contextlib import nullcontext
import json
import os
from pathlib import Path
import plistlib
import subprocess
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import patch

import prepare_iphone_artifact as preparation
from dogfood_functional_smoke import Runner, SmokeError


class PhysicalArtifactPreparation(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.candidate = "c" * 40
        self.device = "00008020-0012345678901234"
        self.manifest = {"component": "ios-device", "sourceCommit": "a" * 40, "sourceTree": "b" * 40,
            "inputDigest": "sha256:" + "d" * 64, "target": "aarch64-apple-ios", "buildMode": "profile",
            "buildNumber": "77", "payload": {"sha256": "sha256:" + "e" * 64},
            "toolchain": {"xcode": "26.5", "xcodeBuild": "17F42"}}
        self.composition = {"candidateCommit": self.candidate, "sourceTree": "f" * 40,
                            "specificationCommit": "1" * 40, "compositionDigest": "sha256:" + "2" * 64}
        self.context = SimpleNamespace(env={"WEAVE_PUBLIC_URL": "https://weave.example.test",
            "WEAVE_API_URL": "https://api.weave.example.test/api", "WEAVE_API_ORIGIN": "https://api.weave.example.test",
            "WEAVE_AUTH_URL": "https://auth.weave.example.test"})
        self.instances = []
        self.ui_failure = False
        self.preexisting_driver = False
        owner = self

        class DeviceRunner(Runner):
            def __init__(self, directory):
                super().__init__(directory)
                self.commands = []
                self.fixture = None
                owner.instances.append(self)

            def run(self, arguments, **_):
                self.sequence += 1
                self.commands.append(arguments)
                if arguments == ["xcodebuild", "-version"]:
                    return "Xcode 26.5\nBuild version 17F42\n"
                if "--json-output" in arguments:
                    path = Path(arguments[arguments.index("--json-output") + 1])
                    if "details" in arguments:
                        value = {"result": {"hardwareProperties": {"udid": owner.device}}}
                    elif arguments[arguments.index("--bundle-id") + 1] == preparation.DRIVER:
                        value = {"result": {"apps": [{}] if owner.preexisting_driver else []}}
                    else:
                        value = {"result": {"apps": [{"bundleIdentifier": preparation.BUNDLE, "bundleVersion": "77"}]}}
                    path.write_text(json.dumps(value))
                if arguments[:5] == ["xcrun", "devicectl", "device", "copy", "to"]:
                    self.fixture = json.loads(Path(arguments[arguments.index("--source") + 1]).read_text())
                if arguments[:2] == ["xcodebuild", "test-without-building"]:
                    if owner.ui_failure:
                        raise SmokeError("native-command-failed")
                    result = {"schemaVersion": "weave.iphone-startup-ui/v1", "runId": self.fixture["runId"],
                              "nonce": self.fixture["nonce"], "status": "passed", "visibleState": "retained-workspace",
                              "ownerSessionReplaced": False}
                    return "WEAVE_IPHONE_STARTUP_RESULT " + json.dumps(result)
                return ""

        self.addCleanup(patch.stopall)
        patch.dict(os.environ, {"GITHUB_ACTIONS": "true", "GITHUB_REF": "refs/heads/dogfood",
            "RUNNER_NAME": "weave-live-mac-mini", "GITHUB_RUN_ID": "1000", "GITHUB_RUN_ATTEMPT": "1",
            "WEAVE_BUILD_ARTIFACT_ROOT": str(self.root)}).start()
        patch.object(preparation, "candidate_composition", return_value=self.composition).start()
        patch.object(preparation, "native_bundle", return_value=(self.manifest, None, None)).start()
        patch.object(preparation, "load_context", return_value=self.context).start()
        patch.object(preparation, "_dogfood_session_lock", side_effect=lambda _: nullcontext()).start()
        patch.object(preparation, "verify_dogfood_identity").start()
        patch.object(preparation, "_service_container", return_value={"Image": "sha256:" + "3" * 64}).start()
        patch.object(preparation, "verify_runtime_container", return_value={"sourceCommit": "4" * 40}).start()
        patch.object(preparation, "Runner", DeviceRunner).start()
        patch.object(preparation, "sign", side_effect=self.sign).start()
        patch.object(preparation, "verify_signed", return_value={"status": "passed"}).start()
        self.member = {"accountDigest": "a" * 64, "organizationDigest": "b" * 64, "testerSlot": "A"}
        self.verify_member = patch.object(preparation, "verify_member_workspace", return_value=self.member).start()
        self.verify_routing = patch.object(preparation, "verify_discovered_routing", return_value="retained-workspace").start()

    def sign(self, root, artifact, output, candidate, team, identity, device, profiles, certificate_team):
        self.assertEqual(candidate, self.candidate)
        self.assertEqual(device, self.device)
        for relative, identifier in (("ordinary/Runner.app", preparation.BUNDLE),
                                     ("driver/Products/Debug-iphoneos/BrowserDriver-Runner.app", preparation.DRIVER)):
            directory = output / relative
            directory.mkdir(parents=True)
            (directory / "Info.plist").write_bytes(plistlib.dumps({"CFBundleIdentifier": identifier, "CFBundleVersion": "77"}))
        (output / "driver/Products/BrowserDriver.xctestrun").write_bytes(plistlib.dumps({"BrowserDriver": {
            "BlueprintName": "BrowserDriver", "TestHostPath": "__TESTROOT__/Debug-iphoneos/BrowserDriver-Runner.app/Runner",
            "TestBundlePath": "__TESTROOT__/Debug-iphoneos/BrowserDriver-Runner.app/PlugIns/BrowserDriver.xctest"}}))

    def prepare(self):
        return preparation.prepare(self.candidate, "core-device-identifier", 900, 1, self.root / "receipt.json")

    def test_in_place_install_uses_prebuilt_driver_without_owner_uninstall_or_compilation(self):
        result = self.prepare()
        self.assertEqual(result["status"], "passed")
        self.assertEqual(result["humanAcceptance"], "pending")
        self.assertEqual(result["schemaVersion"], "weave.hil-preparation/v5")
        self.assertEqual(result["memberWorkspace"], self.member)
        self.assertEqual(self.verify_routing.call_args.args[3], {key: self.member[key] for key in ("accountDigest", "organizationDigest")})
        self.assertTrue(result["ordinaryStartup"]["driverCleanupComplete"])
        commands = self.instances[0].commands
        self.assertTrue(any(command[:2] == ["xcodebuild", "test-without-building"] for command in commands))
        self.assertFalse(any("build-for-testing" in command or "flutter" in command or "build" in command for command in commands))
        uninstalls = [command for command in commands if "uninstall" in command]
        self.assertEqual(len(uninstalls), 1)
        self.assertEqual(uninstalls[0][-1], preparation.DRIVER)
        self.assertEqual(json.loads((self.root / "receipt.json").read_text())["status"], "passed")

    def test_revoked_member_prevents_any_device_installation(self):
        self.verify_member.side_effect = preparation.ContractError("space-access-conflict")
        result = self.prepare()
        self.assertEqual(result["status"], "incomplete")
        self.assertEqual(result["boundary"], "space-access-conflict")
        self.assertFalse(any("install" in command for command in self.instances[0].commands))

    def test_changed_visible_identity_cannot_produce_prepared_receipt(self):
        self.verify_routing.return_value = "normal-sign-in"
        with patch("subprocess.run", return_value=SimpleNamespace(returncode=0)):
            result = self.prepare()
        self.assertEqual(result["status"], "incomplete")
        self.assertEqual(result["boundary"], "physical-startup-identity-changed")

    def test_preexisting_driver_is_not_overwritten_or_uninstalled(self):
        self.preexisting_driver = True
        result = self.prepare()
        self.assertEqual(result["status"], "incomplete")
        self.assertEqual(result["boundary"], "physical-test-runner-already-owned-by-another-session")
        self.assertFalse(any("install" in command or "uninstall" in command for command in self.instances[0].commands))

    def test_ui_failure_cleans_only_the_owned_driver_and_preserves_incomplete_evidence(self):
        self.ui_failure = True
        with patch("subprocess.run", return_value=SimpleNamespace(returncode=0)) as cleanup:
            result = self.prepare()
        self.assertEqual(result["status"], "incomplete")
        cleanup.assert_called_once()
        self.assertEqual(cleanup.call_args.args[0][-1], preparation.DRIVER)
        self.assertTrue((self.root / "receipt.json").is_file())

    def test_cleanup_timeout_still_writes_an_incomplete_receipt(self):
        self.ui_failure = True
        with patch("subprocess.run", side_effect=subprocess.TimeoutExpired("devicectl", 60)):
            result = self.prepare()
        self.assertEqual(result["status"], "incomplete")
        self.assertEqual(result["boundary"], "owned-physical-test-runner-cleanup-incomplete")
        self.assertEqual(json.loads((self.root / "receipt.json").read_text())["status"], "incomplete")

    def test_startup_receipt_requires_current_nonce_and_preserved_owner_session(self):
        fixture = {"runId": "human-test-1000-1", "nonce": "a" * 32}
        value = {"schemaVersion": "weave.iphone-startup-ui/v1", **fixture, "status": "passed",
                 "visibleState": "normal-sign-in", "ownerSessionReplaced": False}
        line = "WEAVE_IPHONE_STARTUP_RESULT " + json.dumps(value)
        self.assertEqual(preparation.parse_startup_result(line, fixture), value)
        for invalid in ({**value, "nonce": "b" * 32}, {**value, "ownerSessionReplaced": True}):
            with self.assertRaises(SmokeError):
                preparation.parse_startup_result("WEAVE_IPHONE_STARTUP_RESULT " + json.dumps(invalid), fixture)
        with self.assertRaises(SmokeError):
            preparation.parse_startup_result(line + "\n" + line, fixture)

    def test_physical_udid_comes_from_hardware_details(self):
        self.assertEqual(preparation.physical_udid({"result": {"hardwareProperties": {"udid": self.device}}}), self.device)
        with self.assertRaises(SmokeError):
            preparation.physical_udid({"result": {"identifier": "A1B2C3D4-E5F6-0000-1111-222233334444"}})


if __name__ == "__main__":
    unittest.main()
