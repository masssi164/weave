#!/usr/bin/env python3
"""Offline negative tests for real UI producer/receipt boundaries, never live proof."""
import copy
import json
import os
from pathlib import Path
import signal
import plistlib
from unittest.mock import patch
import subprocess
import sys
import tempfile
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parent))
import dogfood_functional_smoke as smoke


def receipt(stage='physical', sha='a' * 40, tree='b' * 40, spec='c' * 40, run_id=123, attempt=1):
    return {
        'schemaVersion': 'weave.dogfood-functional-smoke/v1', 'candidateCommit': sha,
        'sourceTree': tree, 'specificationCommit': spec, 'qualificationRunId': run_id,
        'qualificationRunAttempt': attempt, 'stage': stage, 'status': 'passed',
        'entrypoint': 'lib/main.dart', 'routing': 'reviewed-direct', 'appSessionInjection': False,
        'proxyUsed': False, 'ownerSessionReplaced': False, 'cleanupStatus': 'retained-owned-content',
        'ownedSimulatorsRemoved': True, 'privateInvocationInputsRemoved': True,
        'supportSafe': True, 'containsSecretValues': False, 'retainedStateRestartVerified': True,
        'retainedStateUpdateVerified': True, 'calendarPeerAbsenceVerified': True,
        'independentPeerReadbackVerified': True, 'deployedSourceTree': tree,
        'operations': list(smoke.OPERATIONS),
        'resourceIdentityDigests': {**{key: 'd' * 64 for key in ('conversation', 'folder', 'file', 'event', 'account', 'organization')}, 'peerAccount': 'e' * 64},
        'appBuildSha256': '1' * 64, 'peerAppBuildSha256': '2' * 64, 'routingSha256': '3' * 64,
        'exportedFileSha256': '4' * 64, 'deployedImage': 'sha256:' + '5' * 64,
        'deviceIdentitySha256': '6' * 64,
        'build': {'bundleId': smoke.BUNDLE, 'buildNumber': '20', 'mode': 'profile' if stage == 'physical' else 'debug'},
        'startedAtUtc': '2026-10-22T10:00:00Z', 'verifiedAtUtc': '2026-10-22T10:30:00Z',
    }


class FunctionalSmokeTest(unittest.TestCase):
    def valid(self, value):
        return smoke.functional_smoke_passed(value, 'a' * 40, 'b' * 40, 'c' * 40, 123, 1, 'physical')

    def test_only_complete_ordinary_producer_receipt_passes(self):
        value = receipt()
        self.assertTrue(self.valid(value))
        for key in value:
            broken = copy.deepcopy(value); del broken[key]
            self.assertFalse(self.valid(broken), key)
        for key, bad in [('status', 'incomplete'), ('entrypoint', 'integration_test/dual_ios_collaboration_test.dart'),
                         ('routing', 'relay'), ('proxyUsed', True), ('appSessionInjection', True),
                         ('ownerSessionReplaced', True), ('qualificationRunAttempt', 2),
                         ('qualificationRunId', True), ('cleanupStatus', 'all-data-removed'),
                         ('deployedSourceTree', 'e' * 40), ('sourceTree', 'f' * 40)]:
            self.assertFalse(self.valid({**value, key: bad}), key)
        for bad in ([], ['A:discover'], list(smoke.OPERATIONS)[:-1], list(reversed(smoke.OPERATIONS))):
            self.assertFalse(self.valid({**value, 'operations': bad}))
        self.assertFalse(self.valid({**value, 'build': {**value['build'], 'mode': 'debug'}}))
        self.assertFalse(self.valid({**value, 'verifiedAtUtc': '2026-10-22T12:00:00Z'}))

    def test_xctest_result_is_fresh_unique_and_closed(self):
        fixture = {'runId': 'owned-run', 'nonce': '1' * 32, 'operation': 'chat-peer'}
        result = {'schemaVersion': 'weave.ordinary-product-ui-result/v1', **fixture, 'status': 'passed',
                  'resources': {'conversation': 'f' * 64}}
        output = 'prefix WEAVE_FUNCTIONAL_UI_RESULT ' + json.dumps(result)
        self.assertEqual(smoke.parse_ui_result(output, fixture), result)
        for broken in ('', output + '\n' + output, output.replace('owned-run', 'old-run'),
                       output.replace('"passed"', '"incomplete"'), output.replace('f' * 64, 'raw-member-id')):
            with self.assertRaises(smoke.SmokeError):
                smoke.parse_ui_result(broken, fixture)

    def test_private_identity_input_rejects_unsafe_permissions_and_session_material(self):
        with tempfile.TemporaryDirectory() as directory:
            parent = Path(directory).resolve(); parent.chmod(0o700)
            path = parent / 'members.json'
            fixture = {'schemaVersion': 'weave.dogfood-functional-identities/v1',
                       'organizationOrigin': 'https://org.fixture.invalid/', 'ownerDisplayName': 'Owner',
                       'members': [{'email': f'{role}@fixture.invalid', 'password': 'private', 'displayName': role} for role in ('a', 'b')]}
            path.write_text(json.dumps(fixture)); path.chmod(0o600)
            self.assertEqual(smoke.identities(path, fixture['organizationOrigin'], True), fixture)
            path.chmod(0o644)
            with self.assertRaises(smoke.SmokeError): smoke.identities(path, fixture['organizationOrigin'], True)
            path.chmod(0o600)
            fixture['sessionToken'] = 'never-supported'; path.write_text(json.dumps(fixture))
            with self.assertRaises(smoke.SmokeError): smoke.identities(path, fixture['organizationOrigin'], True)
            link = parent / 'link'; link.symlink_to(path)
            with self.assertRaises(smoke.SmokeError): smoke.private_json(link)

    def test_native_command_timeout_reaps_process_and_keeps_raw_output_private(self):
        with tempfile.TemporaryDirectory() as directory:
            runner = smoke.Runner(Path(directory))
            with self.assertRaises(subprocess.TimeoutExpired):
                runner.run([sys.executable, '-c', 'import time; print("private fixture value", flush=True); time.sleep(60)'], timeout=0.05)
            self.assertIn('private fixture value', (Path(directory) / 'command-1.log').read_text())
            self.assertEqual((Path(directory) / 'command-1.log').stat().st_mode & 0o777, 0o600)

    def test_missing_normal_auth_remains_incomplete_without_setup_impersonation(self):
        with tempfile.TemporaryDirectory() as directory:
            with self.assertRaisesRegex(smoke.SmokeError, 'approved-test-member-authentication-required'):
                smoke.identities(Path(directory) / 'missing.json', 'https://org.fixture.invalid/', True)

    def test_physical_fixture_uses_only_owned_signed_runner_container(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory).resolve()
            products = root / 'Build/Products'; products.mkdir(parents=True)
            plan = products / 'driver.xctestrun'
            plan.write_bytes(plistlib.dumps({'BrowserDriver': {'BlueprintName': 'BrowserDriver'}}))
            actor = {'products': products, 'physical': True, 'id': 'owned-device',
                     'destination': 'platform=iOS,id=owned-device',
                     'driverBundle': 'com.massimotter.weave.integration.browser.xctrunner'}
            fixture = {'runId': 'run-owned', 'nonce': '1' * 32, 'operation': 'discover'}
            result = {'schemaVersion': 'weave.ordinary-product-ui-result/v1', **fixture,
                      'status': 'passed', 'resources': {}}
            runner = smoke.Runner(root)
            with patch.object(runner, 'run', side_effect=['', 'WEAVE_FUNCTIONAL_UI_RESULT ' + json.dumps(result)]) as execute:
                self.assertEqual(runner.ui(actor, fixture), result)
            command = execute.call_args_list[0].args[0]
            self.assertIn('appDataContainer', command)
            self.assertIn(actor['driverBundle'], command)
            self.assertNotIn(smoke.BUNDLE, command)
            self.assertIn('Documents/private-member.json', command)
            self.assertEqual(plistlib.loads(plan.read_bytes())['BrowserDriver']['EnvironmentVariables']['WEAVE_NATIVE_DRIVER_FIXTURE_PATH'],
                             'runner-documents:private-member.json')
            self.assertFalse((products / 'private-member.json').exists())


    def test_ordinary_install_launch_has_no_onboarding_payload_or_readiness_claim(self):
        script = (smoke.ROOT / 'tools/dogfood_ios_deeplink_smoke.sh').read_text()
        launch = script.split('  if [[ "${ORDINARY_STARTUP}" == "1" ]]; then\n    xcrun devicectl device process launch', 1)[1].split('\n  fi', 1)[0]
        ordinary, first_use = launch.split('\n  else\n')
        self.assertNotIn('--payload-url', ordinary)
        self.assertIn('--payload-url', first_use)
        result = script.split('if [[ "${ORDINARY_STARTUP}" == "1" ]]; then\n  EVIDENCE_DIR=', 1)[1].split('\nfi', 1)[0]
        self.assertIn('"status": "installed-launch-requested"', result)
        self.assertIn('"functionalProof": "pending"', result)
        self.assertNotIn('"physicalDeviceHttpsVerified": true', result)
        self.assertIn('ordinary startup requires update_in_place', script)
        workflow = (smoke.ROOT / '.github/workflows/ios-dogfood.yml').read_text()
        self.assertIn('--run --ordinary-startup', workflow)
        self.assertIn('--reset-mode update_in_place', workflow)

    def test_ui_readback_requires_updated_title_and_completed_new_refresh(self):
        driver = (smoke.CLIENT / 'tool/ios_browser_driver/OrdinaryProductSmoke.swift').read_text()
        self.assertIn('&& (name.isEmpty || row.label.contains(name))', driver)
        self.assertIn('loaded.element.identifier != previousGeneration', driver)
        self.assertIn('guard refreshed else', driver)
        self.assertIn('fixture.expected["updated"] == "true"', driver)
        self.assertIn('settings-current-account-reference', driver)
        self.assertIn('settings-current-organization-reference', driver)
        self.assertNotIn('Data(fixture.organizationOrigin.utf8)', driver)


    def test_ordinary_identity_and_scope_follow_real_navigation_with_bounded_scrolling(self):
        driver = (smoke.CLIENT / 'tool/ios_browser_driver/OrdinaryProductSmoke.swift').read_text()
        identity = driver.split('case "identity":', 1)[1].split('case "chat-create":', 1)[0]
        self.assertLess(identity.index('"nav-settings"'), identity.index('"settings-help"'))
        self.assertLess(identity.index('"settings-help"'), identity.index('"settings-current-account-reference"'))
        self.assertIn('"files-space-option-workspace-default"', driver)
        self.assertNotIn('shared-files-choice', driver)
        self.assertNotIn('Shared organization files', driver)
        self.assertIn('var remaining = 8', driver)
        self.assertIn('remaining > 0 && now >= nextAllowedAt', driver)
        self.assertIn('target.exists && target.isHittable', driver)
        self.assertIn('identified.count > 1', driver)
        self.assertIn('labelled.count > 1', driver)
        self.assertIn('start.press(forDuration: 0.05, thenDragTo: end)', driver)

    def test_message_and_file_preview_readback_use_actual_accessible_content(self):
        driver = (smoke.CLIENT / 'tool/ios_browser_driver/OrdinaryProductSmoke.swift').read_text()
        message = driver.split('func smokeObserveMessage(', 1)[1].split('func smokeObserveFilePreview(', 1)[0]
        self.assertIn('"chat-active-conversation-"', message)
        self.assertIn('observed == conversationDigest', message)
        self.assertIn('room.element.descendants', message)
        self.assertIn('"chat-message-", ". " + text + ". "', message)
        self.assertIn('messages.count <= 1', message)
        self.assertNotIn('app.staticTexts', message)
        preview = driver.split('func smokeObserveFilePreview(', 1)[1].split('func smokeSharedFolder(', 1)[0]
        self.assertIn('"files-preview-content"', preview)
        self.assertIn('preview.label == expected || (preview.value as? String) == expected', preview)
        self.assertNotIn('smokeObserveText', driver)
        producer = Path(smoke.__file__).read_text()
        self.assertIn("'CODE_SIGN_STYLE=Automatic', '-allowProvisioningUpdates'", producer)

    def test_unprotected_invocation_is_incomplete_without_native_side_effects(self):
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory) / 'result.json'
            result = subprocess.run([sys.executable, str(Path(smoke.__file__)), '--stage', 'deployment', '--candidate', 'a' * 40,
                                     '--qualification-run-id', '123', '--qualification-run-attempt', '1', '--output', str(output)],
                                    env={**os.environ, 'GITHUB_ACTIONS': 'false'}, capture_output=True, text=True)
            self.assertEqual(result.returncode, 2)
            self.assertEqual(json.loads(output.read_text())['status'], 'incomplete')
            self.assertNotIn('private', result.stdout)


if __name__ == '__main__':
    unittest.main()
