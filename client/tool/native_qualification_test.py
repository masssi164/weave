#!/usr/bin/env python3
"""Regressions for evidence attribution and native qualification failure paths."""
import json
import io
from contextlib import redirect_stdout
import os
import plistlib
import shutil
import signal
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parent))
import run_ios_simulator_product_acceptance as ios
import run_native_qualification as qualification
import run_native_product_acceptance as macos


class NativeQualificationTest(unittest.TestCase):
    @unittest.skipUnless(shutil.which('swiftc'), 'Swift compiler is unavailable for the no-AX receipt reducer')
    def test_admin_receipt_fresh_approval_and_resume_execute_actual_swift_reducer(self):
        source = (Path(__file__).parent / 'native_macos_appauth_driver.swift').read_text()
        reducer = source.split('// BEGIN ADMIN PREPARATION RECEIPT REDUCER\n', 1)[1].split('// END ADMIN PREPARATION RECEIPT REDUCER', 1)[0]
        checks = r'''
assert(adminPreparationReceiptOrigin(importedHere: true, receiptNonce: "new", requestedNonce: "new") == "fresh")
assert(adminPreparationReceiptOrigin(importedHere: false, receiptNonce: "new", requestedNonce: "new") == "reject")
assert(adminPreparationReceiptOrigin(importedHere: false, receiptNonce: "old", requestedNonce: "new") == "refresh")
assert(adminPreparationReceiptOrigin(importedHere: true, receiptNonce: "old", requestedNonce: "new") == "reject")
assert(adminPreparationReceiptMayComplete(operation: "invitations", importedHere: true, refreshRequested: false, proofImported: false, receiptNonce: "new", requestedNonce: "new", stage: "invitations-ready"))
assert(!adminPreparationReceiptMayComplete(operation: "invitations", importedHere: false, refreshRequested: false, proofImported: false, receiptNonce: "new", requestedNonce: "new", stage: "invitations-ready"))
assert(adminPreparationReceiptMayComplete(operation: "invitations", importedHere: false, refreshRequested: true, proofImported: false, receiptNonce: "new", requestedNonce: "new", stage: "grants-ready"))
assert(!adminPreparationReceiptMayComplete(operation: "grants", importedHere: false, refreshRequested: true, proofImported: false, receiptNonce: "new", requestedNonce: "new", stage: "grants-ready"))
assert(adminPreparationReceiptMayComplete(operation: "grants", importedHere: false, refreshRequested: true, proofImported: true, receiptNonce: "new", requestedNonce: "new", stage: "grants-ready"))
assert(!adminPreparationReceiptMayComplete(operation: "grants", importedHere: false, refreshRequested: true, proofImported: true, receiptNonce: "old", requestedNonce: "new", stage: "grants-ready"))
assert(!adminPreparationReceiptMayComplete(operation: "grants", importedHere: false, refreshRequested: true, proofImported: true, receiptNonce: "new", requestedNonce: "new", stage: "invitations-ready"))
print("receipt-reducer-passed")
'''
        with tempfile.TemporaryDirectory() as directory:
            swift = Path(directory) / 'main.swift'; swift.write_text(reducer + checks)
            executable = Path(directory) / 'receipt-reducer'
            compiled = subprocess.run(['swiftc', str(swift), '-o', str(executable)], capture_output=True, text=True, timeout=30)
            self.assertEqual(compiled.returncode, 0, compiled.stderr)
            result = subprocess.run([str(executable)], capture_output=True, text=True, timeout=10)
            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertEqual(result.stdout.strip(), 'receipt-reducer-passed')

    def test_macos_origin_reuses_strict_private_handoff_and_rejects_run_path(self):
        with tempfile.TemporaryDirectory() as directory:
            client = Path(directory)
            parent = client / 'build/private-ios-qualification'
            parent.mkdir(parents=True); parent.chmod(0o700)
            path = parent / 'dual-ios-input-owned-run.json'
            value = {'schemaVersion': 'weave.dual-ios-input/v1', 'runId': 'owned-run',
                     'organizationId': 'canonical-tenant', 'nativeOrganizationId': 'native-org',
                     'publicOrigin': 'https://weave.localhost', 'actors': [
                         {'role': 'A', 'email': 'a@example.invalid', 'password': 'private-a', 'identityRef': 'a', 'displayName': 'Current owner', 'entryUri': 'weave.localhost'},
                         {'role': 'B', 'email': 'b@example.invalid', 'password': 'private-b', 'identityRef': 'b', 'displayName': 'Current peer', 'entryUri': 'weave://join?organization_origin=https%3A%2F%2Fweave.localhost'}]}
            path.write_text(json.dumps(value)); path.chmod(0o600)
            with patch.object(macos, 'CLIENT', client):
                self.assertEqual(macos.native_organization_origin('owned-run'), 'https://weave.localhost')
                path.chmod(0o644)
                with self.assertRaises(ios.NativeFailure): macos.native_organization_origin('owned-run')
                with self.assertRaises(RuntimeError): macos.native_organization_origin('../foreign')

    def test_macos_owned_cleanup_is_app_owned_fresh_bounded_and_fail_closed(self):
        nonce = 'a' * 32
        marker = ('NATIVE_PREFERENCES_CLEANUP_RESULT status=passed scope=owned-run '
                  'readback=absent invocation=' + nonce)
        for outcome in ('passed', 'missing', 'duplicate', 'stale', 'failed', 'timeout', 'unreaped'):
            with self.subTest(outcome=outcome), tempfile.TemporaryDirectory() as directory:
                process = unittest.mock.Mock()
                process.returncode = 1 if outcome == 'failed' else 0
                output = {'missing': '', 'duplicate': marker + '\n' + marker,
                          'stale': marker.replace(nonce, 'b' * 32)}.get(outcome, 'flutter: ' + marker + '\n')
                process.communicate.return_value = (None, None)
                if outcome == 'timeout':
                    process.communicate.side_effect = subprocess.TimeoutExpired('private', 180, output=b'private-timeout-partial', stderr='private-timeout-error')
                def launch(*args, **kwargs):
                    kwargs['stdout'].write(output + '\nprivate-cleanup-output\n')
                    kwargs['stdout'].flush()
                    return process
                with patch.object(macos, 'CLIENT', Path(directory)), \
                     patch.object(macos, 'verify_signed_app') as verify, \
                     patch.object(macos, 'stop_checkout_app') as stop, \
                     patch.object(macos, 'stop_flutter_process_group', side_effect=RuntimeError('private-unreaped') if outcome == 'unreaped' else None) as group, \
                     patch.object(macos.secrets, 'token_hex', return_value=nonce), \
                     patch.object(macos.subprocess, 'Popen', side_effect=launch) as command, \
                     redirect_stdout(io.StringIO()) as public:
                    if outcome == 'passed':
                        macos.clear_owned_preferences('com.masssi164.weave.nativeacceptance', 'owned-run', team='A123456789', env={'signed': 'fixture', 'WEAVE_NATIVE_MEMBER_PASSWORD': 'private-fixture'})
                        self.assertEqual(verify.call_count, 2)
                        self.assertIn('readback=absent', public.getvalue())
                        self.assertNotIn(nonce, public.getvalue())
                    else:
                        with self.assertRaises(subprocess.TimeoutExpired if outcome == 'timeout' else RuntimeError):
                            macos.clear_owned_preferences('com.masssi164.weave.nativeacceptance', 'owned-run', team='A123456789', env={})
                    self.assertEqual(process.communicate.call_args.kwargs['timeout'], 180)
                    args = command.call_args.args[0]
                    self.assertEqual(args[:3], ['flutter', 'test', 'integration_test/system_browser_auth_e2e_test.dart'])
                    self.assertIn('--dart-define=WEAVE_NATIVE_TEST_RUN_ID=owned-run', args)
                    self.assertIn('--dart-define=WEAVE_NATIVE_CLEANUP_NONCE=' + nonce, args)
                    self.assertTrue(command.call_args.kwargs['start_new_session'])
                    self.assertNotIn('WEAVE_NATIVE_MEMBER_PASSWORD', command.call_args.kwargs['env'])
                    group.assert_called_once_with(process)
                    self.assertEqual(stop.call_count, 2)
                    retained = list(Path(directory).glob('build/private-native-acceptance/preferences-cleanup-*.log'))
                    if outcome == 'passed':
                        self.assertEqual(retained, [])
                    else:
                        self.assertEqual(len(retained), 1)
                        self.assertEqual(retained[0].stat().st_mode & 0o777, 0o600)
                        self.assertIn('private-cleanup-output', retained[0].read_text())
                        if outcome == 'timeout':
                            self.assertIn('private-timeout-partial', retained[0].read_text())
                            self.assertIn('private-timeout-error', retained[0].read_text())
                        self.assertIn('status=retainedPrivate', public.getvalue())
                    self.assertNotIn('private-cleanup-output', public.getvalue())
                    self.assertNotIn('private-timeout', public.getvalue())
        with patch.object(macos, 'verify_signed_app') as verify:
            for bundle, run, team in [('com.example.operator', 'owned-run', 'A123456789'),
                                      ('com.masssi164.weave.nativeacceptance', '../foreign', 'A123456789'),
                                      ('com.masssi164.weave.nativeacceptance', 'owned-run', 'invalid')]:
                with self.assertRaises(RuntimeError):
                    macos.clear_owned_preferences(bundle, run, team=team, env={})
            verify.assert_not_called()

    def test_macos_owned_cleanup_requires_signed_sandbox_and_stops_on_verification_failure(self):
        with patch.object(macos, 'verify_signed_app', side_effect=RuntimeError('denied')), \
             patch.object(macos.subprocess, 'Popen') as command:
            with self.assertRaises(RuntimeError):
                macos.clear_owned_preferences('com.masssi164.weave.nativeacceptance', 'owned-run', team='A123456789', env={})
            command.assert_not_called()


    def test_macos_signing_verification_requires_integrity_exact_identity_and_sandbox(self):
        with tempfile.TemporaryDirectory() as directory:
            app = Path(directory)
            (app / 'Contents').mkdir()
            bundle = 'com.masssi164.weave.nativeacceptance'
            (app / 'Contents/Info.plist').write_bytes(plistlib.dumps({'CFBundleIdentifier': bundle}))
            for outcome in ('passed', 'invalid-signature', 'wrong-identifier', 'wrong-team', 'team-prefix', 'duplicate-team', 'unsandboxed', 'missing-sandbox'):
                with self.subTest(outcome=outcome):
                    rights = {'keychain-access-groups': []}
                    if outcome != 'missing-sandbox':
                        rights['com.apple.security.app-sandbox'] = outcome != 'unsandboxed'
                    identifier = 'com.example.operator' if outcome == 'wrong-identifier' else bundle
                    team = {'wrong-team': 'B123456789', 'team-prefix': 'A123456789EXTRA'}.get(outcome, 'A123456789')
                    metadata = f'Identifier={identifier}\nTeamIdentifier={team}\n'
                    if outcome == 'duplicate-team':
                        metadata += 'TeamIdentifier=A123456789\n'
                    responses = [subprocess.CompletedProcess([], 1 if outcome == 'invalid-signature' else 0, '', ''),
                                 subprocess.CompletedProcess([], 0, '', metadata),
                                 subprocess.CompletedProcess([], 0, plistlib.dumps(rights), b'')]
                    with patch.object(macos, 'APP', app), patch.object(macos.subprocess, 'run', side_effect=responses) as command, redirect_stdout(io.StringIO()):
                        if outcome == 'passed':
                            macos.verify_signed_app('A123456789', bundle)
                        else:
                            with self.assertRaises(RuntimeError):
                                macos.verify_signed_app('A123456789', bundle)
                        self.assertEqual(command.call_args_list[0].args[0][:4], ['codesign', '--verify', '--deep', '--strict'])
                        self.assertEqual(command.call_args_list[0].kwargs['timeout'], 15)

    def test_macos_finalization_attempts_all_cleanup_and_preserves_primary_failure(self):
        for accepted in (False, True):
            for failed in (None, 'browser', 'flutter', 'reader', 'app', 'preferences'):
                with self.subTest(accepted=accepted, failed=failed), tempfile.TemporaryDirectory() as directory:
                    diagnostic = tempfile.NamedTemporaryFile(mode='w', dir=directory, delete=False)
                    diagnostic.write('private-original-diagnostic'); diagnostic.flush()
                    retained = Path(diagnostic.name)
                    browser = unittest.mock.Mock(); browser.poll.return_value = None
                    browser.wait.return_value = 0
                    if failed == 'browser':
                        browser.wait.side_effect = [subprocess.TimeoutExpired('private-driver-error', 10), 0]
                    reader = unittest.mock.Mock(); reader.is_alive.return_value = failed == 'reader'
                    original = ValueError('private-original-product-failure')
                    flutter = unittest.mock.Mock()
                    with patch.object(macos, 'stop_flutter_process_group', side_effect=RuntimeError('private-flutter-error') if failed == 'flutter' else None) as stop_group, \
                         patch.object(macos, 'stop_checkout_app', side_effect=RuntimeError('private-app-error') if failed == 'app' else None) as stop_app, \
                         patch.object(macos, 'clear_owned_preferences', side_effect=RuntimeError('private-preferences-error') if failed == 'preferences' else None) as preferences, \
                         redirect_stdout(io.StringIO()) as public:
                        def finish():
                            macos.finalize_native_run(accepted=accepted, bundle='com.masssi164.weave.nativeacceptance', run_id='owned-run', team='A123456789', env={}, logout_consent=None, driver=browser, flutter=flutter, reader=reader, diagnostics=[diagnostic])
                        if not accepted:
                            try:
                                try:
                                    raise original
                                finally:
                                    finish()
                            except ValueError as observed:
                                self.assertIs(observed, original)
                        elif failed:
                            with self.assertRaisesRegex(RuntimeError, 'native final owned cleanup failed'):
                                finish()
                        else:
                            finish()
                        preferences.assert_called_once()
                        stop_group.assert_called_once_with(flutter)
                        stop_app.assert_called_once()
                        reader.join.assert_called_once_with(timeout=10)
                        if failed == 'browser':
                            browser.kill.assert_called_once()
                            self.assertEqual(browser.wait.call_count, 2)
                        self.assertEqual(retained.exists(), not accepted or failed is not None)
                        self.assertNotIn('private-', public.getvalue())
                        if failed:
                            self.assertIn('NATIVE_FINAL_CLEANUP_RESULT status=failed', public.getvalue())
                    diagnostic.close()

    def test_onboarding_diagnostics_are_exact_bounded_and_classify_executed_ui_failure(self):
        record = ('NATIVE_PRODUCT_STAGE phase=onboarding-failed actor=A authState=ready_for_sso '
                  'visibleState=handoff_ready errorCode=none organizationCount=0 handoffCount=1 '
                  'readyCount=1 signInCount=0 workspaceCount=0')
        self.assertEqual(ios.onboarding_diagnostics(record), [record])
        self.assertEqual(ios.onboarding_diagnostics('flutter: ' + record), [record])
        self.assertEqual(ios.onboarding_diagnostics(record + '\n' + record), [record])
        for bad in [record + ' private=credential', 'private ' + record, 'flutter: flutter: ' + record,
                    record + '\r', record.replace('Count=1', 'Count=1000'), record.replace('actor=A', 'actor=secret'),
                    record.replace('errorCode=none', 'errorCode=https://private.invalid/token')]:
            self.assertEqual(ios.onboarding_diagnostics(bad), [])
        with tempfile.TemporaryDirectory() as directory:
            log = Path(directory) / 'flutter.log'
            process = unittest.mock.Mock();process.poll.return_value = 1
            for marker, category, phase in [(record, 'integration', 'initial-onboarding'),
                (record.replace('errorCode=none', 'errorCode=WEAVE-APP-START-TLS-FAILED'), 'infrastructure', 'initial-onboarding'),
                ('host failed without UI evidence', 'infrastructure', 'initial-app-start')]:
                log.write_text(marker + '\n')
                with self.assertRaises(ios.NativeFailure) as caught: ios.wait_for_flutter_shell(process, log, 'initial')
                self.assertEqual((caught.exception.category, caught.exception.stage), (category, phase))

    def test_onboarding_auth_classifications_preserve_only_existing_public_codes(self):
        template = ('NATIVE_PRODUCT_STAGE phase=onboarding-failed actor=A authState=recoverable_error '
                    'visibleState=handoff_ready errorCode={} organizationCount=0 handoffCount=1 '
                    'readyCount=0 signInCount=0 workspaceCount=0')
        codes = ['WEAVE-SSO-CANCELLED', 'WEAVE-SSO-CONFIGURATION-MISSING', 'WEAVE-SSO-NOT-COMPLETE',
                 'WEAVE-SESSION-STORAGE-FAILED', 'WEAVE-SSO-UNSUPPORTED-DEVICE', 'WEAVE-SSO-UNKNOWN',
                 'WEAVE-MOBILE-OFFLINE-SESSION-DENIED', 'WEAVE-WORKSPACE-BOOTSTRAP-NOT-READY']
        for code in codes:
            with self.subTest(code=code):
                record = template.format(code)
                self.assertEqual(ios.onboarding_diagnostics(record), [record])
                for private in [code + '-private', code + ' account=value', 'https://private.invalid',
                                code + '\r', 'private@example.invalid', code + 'x' * 81]:
                    self.assertEqual(ios.onboarding_diagnostics(template.format(private)), [])

    def test_chat_readiness_diagnostics_are_fixed_bounded_and_private_safe(self):
        marker = ('NATIVE_PRODUCT_STAGE phase=chat-readiness-failed chatPhase=loading '
                  'failureType=none createCount=1 enabled=false hittable=true dialogCount=0')
        output = io.StringIO()
        with redirect_stdout(output):
            ios.emit_phase_stages('initial', marker + '\nflutter: ' + marker, '')
        self.assertEqual(output.getvalue().count('IOS_FLUTTER_DIAGNOSTIC'), 1)
        self.assertIn('enabled=false hittable=true dialogCount=0', output.getvalue())
        for bad in [marker + ' private=value', 'private ' + marker, 'flutter: flutter: ' + marker,
                    marker + '\r', marker.replace('loading', 'private@example.invalid'),
                    marker.replace('failureType=none', 'failureType=https://private.invalid'),
                    marker.replace('createCount=1', 'createCount=1000'),
                    marker.replace('enabled=false', 'enabled=1'), marker.replace('dialogCount=0', 'dialogCount=-1')]:
            output = io.StringIO()
            with redirect_stdout(output): ios.emit_phase_stages('initial', bad, '')
            self.assertNotIn('IOS_FLUTTER_DIAGNOSTIC', output.getvalue())
        output = io.StringIO()
        with redirect_stdout(output): ios.emit_phase_stages('foreign', marker, '')
        self.assertNotIn('IOS_FLUTTER_DIAGNOSTIC', output.getvalue())

    def test_chat_send_boundary_records_require_complete_safe_fields(self):
        markers = [
            'NATIVE_PRODUCT_STAGE phase=chat-send-input matchesExpected=true',
            'NATIVE_PRODUCT_STAGE phase=chat-send-input matchesExpected=false',
            'NATIVE_PRODUCT_STAGE phase=chat-cached-receive events=5 decrypted=4 unableToDecrypt=0 plaintext=1',
        ]
        for marker in markers:
            output = io.StringIO()
            with redirect_stdout(output):
                ios.emit_phase_stages('initial', marker + '\nflutter: ' + marker, '')
            self.assertEqual(output.getvalue().count('IOS_FLUTTER_DIAGNOSTIC'), 1)
            self.assertIn(marker, output.getvalue())
            bad_records = [marker + ' private=value', 'private ' + marker,
                           marker + '\r', marker.replace('=true', '=1'),
                           marker.replace('=false', '=private@example.invalid'),
                           marker.replace('events=5', 'events=-1'),
                           marker.replace('events=5', 'events=1000')]
            for bad in (value for value in bad_records if value != marker):
                output = io.StringIO()
                with redirect_stdout(output): ios.emit_phase_stages('initial', bad, '')
                self.assertNotIn('IOS_FLUTTER_DIAGNOSTIC', output.getvalue())
            output = io.StringIO()
            with redirect_stdout(output): ios.emit_phase_stages('foreign', marker, '')
            self.assertNotIn('IOS_FLUTTER_DIAGNOSTIC', output.getvalue())

    def setUp(self):
        # Failure transcript retention must remain inside this test's owned
        # disposable client directory, never the real workspace build outputs.
        self.private_scope = tempfile.TemporaryDirectory()
        self.addCleanup(self.private_scope.cleanup)
        client = Path(self.private_scope.name) / 'client'
        (client / 'build').mkdir(parents=True)
        client_patch = patch.object(ios, 'CLIENT', client)
        client_patch.start()
        self.addCleanup(client_patch.stop)
        self.actual_phase_start = ios.start_phase_app
        phase_start_patch = patch.object(ios, 'start_phase_app', side_effect=lambda udid, env:
                                        (ios.simulator_app_pid(udid), 'http://127.0.0.1:12345/ownedDebug/'))
        phase_start_patch.start()
        self.addCleanup(phase_start_patch.stop)

    def test_command_timeout_retains_bounded_private_partial_output_and_original_cause(self):
        secret = 'private-password private@example.invalid /private/document'
        for stdout, stderr in [
            (b'old-prefix' + b'x' * 100000 + secret.encode(), b'old-prefix' + b'y' * 100000 + b'private-stderr'),
            (b'old-prefix' + b'\xff' * 40000 + secret.encode(), b'private-stderr'),
            ('old-prefix' + '🔒' * 40000 + secret, 'private-stderr'),
            (None, None),
        ]:
            with self.subTest(outputType=type(stdout).__name__):
                process = unittest.mock.Mock()
                failure = subprocess.TimeoutExpired('private-command', 90, output=stdout, stderr=stderr)
                process.communicate.side_effect = failure
                public = io.StringIO()
                with patch.object(ios.subprocess, 'Popen', return_value=process), patch.object(ios, 'stop_flutter_process_group') as stop, redirect_stdout(public):
                    with self.assertRaises(ios.NativeFailure) as caught:
                        ios.run(['xcodebuild', 'private-argument'], timeout=90)
                self.assertIs(caught.exception.__cause__, failure)
                self.assertEqual((caught.exception.category, caught.exception.stage, caught.exception.reason),
                                 ('infrastructure', 'browser-driver-build', 'command-timeout'))
                process.communicate.assert_called_once_with(timeout=90)
                stop.assert_called_once_with(process)
                logs = list((ios.CLIENT / 'build/private-ios-qualification').glob('command-*.log'))
                self.assertEqual(len(logs), 1)
                log = logs[0]
                self.assertEqual(log.stat().st_mode & 0o777, 0o600)
                self.assertEqual(log.parent.stat().st_mode & 0o777, 0o700)
                self.assertLessEqual(log.stat().st_size, 65537)
                if stdout is not None:
                    self.assertIn(secret, log.read_text())
                    self.assertIn('private-stderr', log.read_text())
                    self.assertNotIn('old-prefix', log.read_text())
                self.assertIn('privateDiagnostic=' + log.name, public.getvalue())
                for private in [secret, 'private-stderr', 'private-command', 'private-argument', str(log.parent)]:
                    self.assertNotIn(private, public.getvalue())
                log.unlink()

    def test_command_timeout_retention_failure_cannot_replace_primary_or_cleanup(self):
        for error in [OSError('private-path'), RuntimeError('private-payload')]:
            with self.subTest(errorType=type(error).__name__):
                process = unittest.mock.Mock()
                failure = subprocess.TimeoutExpired('private-command', 90, output=b'private-stdout')
                process.communicate.side_effect = failure
                public = io.StringIO()
                with patch.object(ios.subprocess, 'Popen', return_value=process), patch.object(ios, 'stop_flutter_process_group') as stop, patch.object(ios, 'retain_private_diagnostic', side_effect=error), redirect_stdout(public):
                    with self.assertRaises(ios.NativeFailure) as caught:
                        ios.run(['xcodebuild'], timeout=90)
                self.assertIs(caught.exception.__cause__, failure)
                self.assertEqual(caught.exception.reason, 'command-timeout')
                stop.assert_called_once_with(process)
                self.assertIn('status=unavailable', public.getvalue())
                self.assertNotIn('private-', public.getvalue())

    def test_command_timeout_diagnostic_print_failure_preserves_primary(self):
        process = unittest.mock.Mock()
        failure = subprocess.TimeoutExpired('private-command', 90, output='private-stdout')
        process.communicate.side_effect = failure
        with patch.object(ios.subprocess, 'Popen', return_value=process), patch.object(ios, 'stop_flutter_process_group') as stop, patch.object(ios, 'print', side_effect=OSError('private-print'), create=True):
            with self.assertRaises(ios.NativeFailure) as caught:
                ios.run(['xcodebuild'], timeout=90)
        self.assertIs(caught.exception.__cause__, failure)
        self.assertEqual(caught.exception.reason, 'command-timeout')
        stop.assert_called_once_with(process)

    def test_private_command_diagnostic_permissions_and_symlink_rejection(self):
        private = 'raw-private-email raw-secret-password\n'
        output = io.StringIO()
        with redirect_stdout(output):
            path = ios.retain_private_diagnostic(private)
        self.assertEqual(path.read_text(), private)
        self.assertEqual(path.stat().st_mode & 0o777, 0o600)
        self.assertEqual(path.parent.stat().st_mode & 0o777, 0o700)
        self.assertEqual(path.stat().st_uid, os.getuid())
        self.assertEqual(output.getvalue(), '')
        path.unlink()
        path.parent.rmdir()
        outside = Path(self.private_scope.name) / 'outside'
        outside.mkdir()
        path.parent.symlink_to(outside, target_is_directory=True)
        with self.assertRaises(RuntimeError):
            ios.retain_private_diagnostic(private)
        self.assertEqual(list(outside.iterdir()), [])

    def test_phase_transcript_retention_only_on_failure_and_never_public(self):
        for succeeded, cleanup_failed in ((True, False), (False, False), (False, True)):
            with self.subTest(succeeded=succeeded, cleanup_failed=cleanup_failed), tempfile.TemporaryDirectory() as name:
                temporary = Path(name)
                derived = temporary / 'derived'
                products = derived / 'Build/Products'
                (products / 'BrowserDriver.xctest').mkdir(parents=True)
                (products / 'test.xctestrun').write_bytes(plistlib.dumps({'BrowserDriver': {'BlueprintName': 'BrowserDriver'}}))
                def launch(arguments, **kwargs):
                    process = unittest.mock.Mock()
                    process.poll.return_value = None if succeeded else 2
                    process.wait.return_value = 0
                    transcript = kwargs['stdout']
                    if arguments[0] == 'make':
                        transcript.write('raw-private-email raw-secret-password\n')
                        transcript.write('NATIVE_PRODUCT_INITIAL_RESULT status=passed\nAll tests passed.\n')
                    else:
                        transcript.write('NATIVE_IOS_BROWSER_RESULT status=passed phase=form-submitted\n')
                        transcript.write('NATIVE_IOS_BROWSER_STAGE phase=auth-completion-observed\n')
                    transcript.flush()
                    return process
                output = io.StringIO()
                def cleanup(_process):
                    if cleanup_failed:
                        diagnostics = list((ios.CLIENT / 'build/private-ios-qualification').glob('command-*.log'))
                        self.assertEqual(len(diagnostics), 1)
                        self.assertIn('raw-secret-password', diagnostics[0].read_text())
                        raise PermissionError('private cleanup details')
                with patch.object(ios.subprocess, 'Popen', side_effect=launch), \
                        patch.object(ios, 'stop_flutter_process_group', side_effect=cleanup), \
                        patch.object(ios, 'wait_for_flutter_shell', side_effect=None if succeeded else ios.NativeFailure('infrastructure', 'initial-app-start', 'integration-app-exited-before-readiness')), \
                        patch.object(ios, 'simulator_driver_barrier', return_value=temporary / 'ready'), \
                        patch.object(ios, 'simulator_app_pid', return_value='101'), \
                        patch.object(ios, 'release_driver_barrier'), \
                        patch.object(ios, 'relay_workspace_completion', return_value=True), redirect_stdout(output):
                    if succeeded:
                        self.assertEqual(ios.execute_phase('initial', 'owned', derived, temporary,
                                                         {'WEAVE_NATIVE_TEST_RUN_ID': 'test-run'}, {}), '101')
                    else:
                        with self.assertRaises(PermissionError if cleanup_failed else ios.NativeFailure):
                            ios.execute_phase('initial', 'owned', derived, temporary,
                                              {'WEAVE_NATIVE_TEST_RUN_ID': 'test-run'}, {})
                diagnostics = list((ios.CLIENT / 'build/private-ios-qualification').glob('command-*.log'))
                self.assertEqual(len(diagnostics), 0 if succeeded else 1)
                if not succeeded:
                    self.assertIn('raw-secret-password', diagnostics[0].read_text())
                    self.assertEqual(diagnostics[0].stat().st_mode & 0o777, 0o600)
                    self.assertIn('command=make phase=initial exitCode=2 privateDiagnostic=command-', output.getvalue())
                    diagnostics[0].unlink()
                self.assertNotIn('raw-secret-password', output.getvalue())
                self.assertNotIn('raw-private-email', output.getvalue())
                self.assertNotIn('private cleanup details', output.getvalue())

    def test_initial_and_restart_browser_invocations_select_only_the_production_journey(self):
        selected = '-only-testing:BrowserDriver/BrowserDriver/testProductionBrowser'
        for phase in ('initial', 'restore'):
            for browser_exit in (0, 65):
                with self.subTest(phase=phase, browser_exit=browser_exit), tempfile.TemporaryDirectory() as name:
                    temporary = Path(name)
                    derived = temporary / 'derived'
                    products = derived / 'Build/Products'
                    (products / 'BrowserDriver.xctest').mkdir(parents=True)
                    (products / 'owned.xctestrun').write_bytes(plistlib.dumps({'BrowserDriver': {'BlueprintName': 'BrowserDriver'}}))
                    observed = []
                    def launch(arguments, **kwargs):
                        process = unittest.mock.Mock()
                        process.poll.return_value = browser_exit if arguments[0] == 'xcodebuild' else 0
                        process.wait.return_value = process.poll.return_value
                        if arguments[0] == 'xcodebuild':
                            observed.append(arguments)
                            self.assertEqual([arg for arg in arguments if arg.startswith('-only-testing:')], [selected])
                            self.assertFalse(any(arg.startswith('-skip-testing:') for arg in arguments))
                            self.assertNotIn('testOrdinaryOrganizationEntry', ' '.join(arguments))
                            self.assertNotIn('testOrdinaryProductSmoke', ' '.join(arguments))
                            kwargs['stdout'].write('NATIVE_IOS_BROWSER_STAGE phase=app-observed\n')
                            kwargs['stdout'].write('NATIVE_IOS_BROWSER_STAGE phase=auth-completion-observed\n')
                            kwargs['stdout'].write('NATIVE_IOS_BROWSER_RESULT status=passed phase=' + ('form-submitted' if phase == 'initial' else 'logout') + '\n')
                        else:
                            kwargs['stdout'].write(('NATIVE_PRODUCT_INITIAL_RESULT' if phase == 'initial' else 'NATIVE_PRODUCT_SIGN_IN_RESULT') + ' status=passed\nAll tests passed.\nNATIVE_PRODUCT_STAGE phase=expired-bearer-denied\n')
                        kwargs['stdout'].flush()
                        return process
                    fixture = {}
                    with patch.object(ios.subprocess, 'Popen', side_effect=launch), \
                         patch.object(ios, 'stop_flutter_process_group'), \
                         patch.object(ios, 'wait_for_flutter_shell'), \
                         patch.object(ios, 'simulator_driver_barrier', return_value=temporary / 'ready'), \
                         patch.object(ios, 'simulator_app_pid', return_value='202'), \
                         patch.object(ios, 'release_driver_barrier'), \
                         patch.object(ios, 'relay_workspace_completion', return_value=True), redirect_stdout(io.StringIO()):
                        if browser_exit:
                            with self.assertRaises(ios.NativeFailure) as caught:
                                ios.execute_phase(phase, 'owned', derived, temporary,
                                                  {'WEAVE_NATIVE_TEST_RUN_ID': 'owned-run'}, fixture,
                                                  previous_pid='101' if phase == 'restore' else None)
                            self.assertEqual(caught.exception.reason, 'browser-ui-failed')
                        else:
                            self.assertEqual(ios.execute_phase(phase, 'owned', derived, temporary,
                                                               {'WEAVE_NATIVE_TEST_RUN_ID': 'owned-run'}, fixture,
                                                               previous_pid='101' if phase == 'restore' else None), '202')
                    self.assertEqual(len(observed), 1)
                    self.assertEqual(fixture['phase'], phase)

    def test_native_sign_in_selector_matches_supported_product_resources(self):
        client = Path(__file__).resolve().parents[1]
        labels = [json.loads((client / f'lib/l10n/app_{locale}.arb').read_text())['signInButton']
                  for locale in ('en', 'de')]
        driver = (client / 'tool/ios_browser_driver/BrowserDriver.swift').read_text()
        self.assertIn('static let signInLabels = ' + json.dumps(labels), driver)
        self.assertIn('NSPredicate(format: "label IN %@", Self.signInLabels)', driver)
        self.assertIn('XCTAssertEqual(signInControls.count, 1)', driver)
        self.assertIn('XCTAssertTrue(signIn.isHittable', driver)
        self.assertNotIn('label CONTAINS', driver.split('if fixture.phase == "initial" {', 1)[1].split('var usernameSubmitted', 1)[0])

    def test_consent_requires_global_uniqueness_before_any_native_tap(self):
        driver = (Path(__file__).parent / 'ios_browser_driver/BrowserDriver.swift').read_text()
        consent = driver.split('let appConsent = ', 1)[1].split('if fixture.phase == "restore"', 1)[0]
        self.assertIn('static let consentLabels = ["Continue", "Fortfahren"]', driver)
        for surface in ('app', 'springboard'):
            self.assertIn(f'{surface}.alerts.buttons.matching(NSPredicate(format: "label IN %@", Self.consentLabels))', consent)
        self.assertIn('Self.uniqueConsentSurface(appCount: appCount, springboardCount: systemCount)', consent)
        self.assertLess(consent.index('guard let surface'), consent.index('consent.tap()'))
        self.assertIn('XCTFail("Expected one native authentication consent control across both surfaces")', consent)
        self.assertIn('if consent.isHittable', consent)
        self.assertNotIn('CONTAINS', consent)
        self.assertNotIn('surface.buttons', consent)

    def test_provider_fields_and_submit_share_verified_leaf_scope(self):
        driver = (Path(__file__).parent / 'ios_browser_driver/BrowserDriver.swift').read_text()
        provider = driver.split('func testProductionBrowser', 1)[1].split('for surface in [app, browser] {', 1)[1]
        self.assertLess(provider.index('guard host.count > 0'), provider.index('let webViews'))
        self.assertIn('guard leaves.count == 1 else', provider)
        self.assertIn('let fields = provider.textFields', provider)
        self.assertIn('let passwords = provider.secureTextFields', provider)
        self.assertEqual(provider.count('let submit = provider.buttons.matching'), 2)
        self.assertEqual(provider.count('XCTAssertEqual(submit.count, 1'), 2)
        self.assertEqual(provider.count('XCTAssertTrue(submit.element.isHittable'), 2)
        self.assertNotIn('let submit = surface.buttons', provider)
        username_transition = provider.split('phase=username-submitted', 1)[1].split('let passwords', 1)[0]
        self.assertIn('continue', username_transition)
        self.assertIn('static let providerSubmitLabels = ["Sign In", "Anmelden"]', driver)

    def test_provider_diagnostics_reject_private_or_unbounded_payloads(self):
        safe = ('NATIVE_IOS_PROVIDER_CONTROLS phase=username leafWebViews=1 '
                'usernameFields=1 passwordFields=0 submitButtons=2')
        unsafe = [safe + ' account=private-account', safe.replace('phase=username', 'phase=private-account'),
                  safe.replace('submitButtons=2', 'submitButtons=private-password'),
                  safe.replace('leafWebViews=1', 'leafWebViews=1234')]
        output = io.StringIO()
        with redirect_stdout(output):
            ios.emit_phase_stages('initial', '', '\n'.join([safe, safe, *unsafe]))
        self.assertEqual(output.getvalue().count('IOS_PROVIDER_DIAGNOSTIC'), 1)
        self.assertIn('submitButtons=2 supportSafe=true', output.getvalue())
        for private in ('private-account', 'private-password', '1234'):
            self.assertNotIn(private, output.getvalue())
        output = io.StringIO()
        with redirect_stdout(output):
            ios.emit_phase_stages('private-journey', '', safe)
        self.assertEqual(output.getvalue(), '')

    def test_input_diagnostics_reject_payloads_and_bound_unique_output(self):
        safe = [
            'NATIVE_IOS_PROVIDER_CONTROLS phase=username-before usernameFields=1 passwordFields=0 selectedFieldHittable=true',
            'NATIVE_IOS_PROVIDER_CONTROLS phase=password-before usernameFields=0 passwordFields=1 selectedFieldHittable=false',
            'NATIVE_IOS_PROVIDER_CONTROLS phase=username-after readbackEqual=false',
            'NATIVE_IOS_PROVIDER_CONTROLS phase=password-after secureValueLengthEqual=true',
            'NATIVE_IOS_PROVIDER_CONTROLS phase=provider-error category=invalid-credentials matchingElements=1',
            'NATIVE_IOS_PROVIDER_CONTROLS phase=provider-error category=invalid-username matchingElements=0',
            'NATIVE_IOS_PROVIDER_CONTROLS phase=provider-error category=invalid-password matchingElements=2',
            'NATIVE_IOS_PROVIDER_CONTROLS phase=provider-error category=account-disabled matchingElements=1',
        ]
        unsafe = [line + ' private-account' for line in safe] + [
            safe[0].replace('usernameFields=1', 'usernameFields=1000'),
            safe[2].replace('false', 'private-password'),
            safe[3].replace('secureValueLengthEqual=true', 'secureValueLength=47'),
            safe[4].replace('invalid-credentials', 'private-error'),
            'prefix ' + safe[2], safe[2] + '\rprivate-data',
        ]
        output = io.StringIO()
        with redirect_stdout(output):
            ios.emit_phase_stages('initial', '', '\n'.join(safe + safe + unsafe))
        self.assertEqual(output.getvalue().count('IOS_PROVIDER_DIAGNOSTIC'), len(safe))
        for marker in safe:
            self.assertIn(marker + ' supportSafe=true', output.getvalue())
        for private in ('private-', '1000', 'secureValueLength=47', 'prefix'):
            self.assertNotIn(private, output.getvalue())
        output = io.StringIO()
        with redirect_stdout(output):
            ios.emit_phase_stages('initial', '', '\n'.join(unsafe))
        self.assertEqual(output.getvalue(), '')
        output = io.StringIO()
        with redirect_stdout(output):
            ios.emit_phase_stages('restore', '', '\n'.join(
                safe[4].replace('matchingElements=1', f'matchingElements={i}') for i in range(30)))
        self.assertEqual(output.getvalue().count('IOS_PROVIDER_DIAGNOSTIC'), 16)
        output = io.StringIO()
        with redirect_stdout(output):
            ios.emit_phase_stages('private-journey', '', '\n'.join(safe))
        self.assertEqual(output.getvalue(), '')

    def test_password_prompt_diagnostics_are_count_only_bounded_and_deduplicated(self):
        safe = 'NATIVE_IOS_PASSWORD_PROMPT_STATE appPrompts=1 systemPrompts=0 appDeclines=0 systemDeclines=0 readiness=ambiguous'
        unsafe = [safe + ' private-password', 'prefix ' + safe,
                  safe.replace('appPrompts=1', 'appPrompts=1000'),
                  safe.replace('ambiguous', 'private-alert'), safe + '\rprivate-data']
        output = io.StringIO()
        with redirect_stdout(output):
            ios.emit_phase_stages('initial', '', '\n'.join([safe, safe, *unsafe]))
        self.assertEqual(output.getvalue(), 'IOS_PROVIDER_DIAGNOSTIC journey=initial ' + safe + ' supportSafe=true\n')
        output = io.StringIO()
        with redirect_stdout(output):
            ios.emit_phase_stages('restore', '', '\n'.join(
                safe.replace('appDeclines=0', f'appDeclines={i}') for i in range(30)))
        self.assertEqual(output.getvalue().count('IOS_PROVIDER_DIAGNOSTIC'), 16)
        output = io.StringIO()
        with redirect_stdout(output):
            ios.emit_phase_stages('private-journey', '', safe)
        self.assertEqual(output.getvalue(), '')

    def test_return_stage_is_fixed_and_never_exports_private_context(self):
        output = io.StringIO()
        with redirect_stdout(output):
            ios.emit_phase_stages('initial', '',
                'NATIVE_IOS_BROWSER_STAGE phase=password-return-submitted private-password\n'
                'NATIVE_IOS_BROWSER_STAGE phase=private-return-result\n'
                'NATIVE_IOS_BROWSER_STAGE phase=password-return-submitted-private\n')
        self.assertEqual(output.getvalue().count('IOS_DIAGNOSTIC'), 1)
        self.assertIn('phase=password-return-submitted supportSafe=true', output.getvalue())
        self.assertNotIn('private-', output.getvalue())

    def test_auth_failure_classification_is_exact_bounded_and_private_safe(self):
        safe = ('NATIVE_PRODUCT_STAGE phase=auth-failed category=protocol '
                'causeType=FlutterAppAuthPlatformException appFailureType=none '
                'nestedCauseType=FlutterAppAuthPlatformException backendFailure=none '
                'backendStatus=unavailable appAuthCode=authorize_and_exchange_code_failed '
                'nativeType=org.openid.appauth.general nativeCode=-3 nativeDomain=NSURLErrorDomain')
        output = io.StringIO()
        with redirect_stdout(output):
            ios.emit_phase_stages('initial', safe + '\nflutter: ' + safe, '')
        self.assertEqual(output.getvalue().count('IOS_FLUTTER_DIAGNOSTIC'), 1)
        self.assertIn('phase=auth-failed category=protocol', output.getvalue())
        self.assertIn('nativeCode=-3 nativeDomain=NSURLErrorDomain supportSafe=true', output.getvalue())
        output = io.StringIO()
        with redirect_stdout(output):
            ios.emit_phase_stages('restore', safe.replace('category=protocol', 'category=storage')
                .replace('causeType=FlutterAppAuthPlatformException', 'causeType=AppFailure')
                .replace('appFailureType=none', 'appFailureType=bootstrap')
                .replace('nestedCauseType=FlutterAppAuthPlatformException', 'nestedCauseType=int')
                .replace('backendFailure=none', 'backendFailure=session-rejected')
                .replace('backendStatus=unavailable', 'backendStatus=403'), '')
        self.assertIn('backendFailure=session-rejected backendStatus=403', output.getvalue())
        unsafe = [safe + ' private-password', '00:01 +1: ' + safe, safe + '\rprivate-data',
                  'flutter: flutter: ' + safe, 'flutter: ' + safe + ' private-password',
                  safe.replace('protocol', 'private@example.invalid'),
                  safe.replace('category=protocol', 'category=' + 'x' * 81),
                  safe.replace('nativeCode=-3', 'nativeCode=invalid value'),
                  safe.replace('phase=auth-failed', 'phase=private-phase'),
                  safe.replace('causeType=', 'debugDescription='),
                  safe.replace(' category=', '  category=')]
        output = io.StringIO()
        with redirect_stdout(output):
            ios.emit_phase_stages('initial', '\n'.join(unsafe), '')
        self.assertNotIn('IOS_FLUTTER_DIAGNOSTIC', output.getvalue())
        self.assertNotIn('private-', output.getvalue())
        self.assertNotIn('example.invalid', output.getvalue())
        output = io.StringIO()
        with redirect_stdout(output):
            ios.emit_phase_stages('restore', safe.replace('causeType=FlutterAppAuthPlatformException',
                'causeType=privatePassword').replace('nativeDomain=NSURLErrorDomain',
                'nativeDomain=privateDomain').replace('backendStatus=unavailable',
                'backendStatus=999').replace('nativeCode=-3', 'nativeCode=1234567'), '')
        self.assertIn('causeType=unavailable', output.getvalue())
        self.assertIn('backendStatus=unavailable', output.getvalue())
        self.assertIn('nativeCode=unavailable nativeDomain=unavailable', output.getvalue())
        self.assertNotIn('privatePassword', output.getvalue())
        self.assertNotIn('privateDomain', output.getvalue())
        output = io.StringIO()
        with redirect_stdout(output):
            ios.emit_phase_stages('initial', '\n'.join(
                safe.replace('nativeCode=-3', f'nativeCode={i}') for i in range(10)), '')
        self.assertEqual(output.getvalue().count('IOS_FLUTTER_DIAGNOSTIC'), 3)
        output = io.StringIO()
        with redirect_stdout(output):
            ios.emit_phase_stages('private-journey', safe, '')
        self.assertNotIn('IOS_FLUTTER_DIAGNOSTIC', output.getvalue())

    def test_runtime_must_be_available_and_apple_silicon(self):
        for entry in [
            {'identifier': ios.RUNTIME, 'isAvailable': False, 'supportedArchitectures': ['arm64']},
            {'identifier': ios.RUNTIME, 'isAvailable': True, 'supportedArchitectures': ['x86_64']},
        ]:
            with self.subTest(entry=entry), patch.object(ios, 'run', return_value=json.dumps({'runtimes': [entry]})), patch.dict(os.environ, {}, clear=True):
                with self.assertRaisesRegex(ios.NativeFailure, 'apple-silicon-runtime-unavailable'):
                    ios.select_runtime()

    def test_runtime_does_not_silently_select_another_device(self):
        entry = {'identifier': ios.RUNTIME, 'isAvailable': True, 'supportedArchitectures': ['arm64'], 'supportedDeviceTypes': [{'identifier': 'other'}]}
        with patch.object(ios, 'run', return_value=json.dumps({'runtimes': [entry]})), patch.dict(os.environ, {}, clear=True):
            with self.assertRaisesRegex(ios.NativeFailure, 'pinned-device-type-unsupported'):
                ios.select_runtime()

    def test_conflicting_ci_flutter_pins_fail_closed(self):
        with tempfile.TemporaryDirectory() as name:
            root = Path(name)
            (root / '.github/workflows').mkdir(parents=True)
            (root / '.github/workflows/ci.yml').write_text("  flutter-version: '3.41.6'\n  flutter-version: '3.41.7'\n")
            with patch.object(ios, 'ROOT', root):
                with self.assertRaisesRegex(ios.NativeFailure, 'ci-version-pins-missing-or-inconsistent'):
                    ios.configured_toolchain_pins()

    def test_toolchain_rejects_framework_engine_xcode_and_sdk_mismatch(self):
        supported = {
            'frameworkVersion': '3.41.6',
            'channel': 'stable',
            'frameworkRevision': 'a' * 40,
            'engineRevision': 'b' * 40,
            'dartSdkVersion': '3.11.4',
        }
        xcode = f'Xcode {ios.XCODE_VERSION}\nBuild version {ios.XCODE_BUILD}\n'
        for case in ['framework', 'engine', 'xcode', 'sdk']:
            with self.subTest(case=case):
                flutter = supported.copy()
                if case == 'framework':
                    flutter['frameworkVersion'] = '0.0.0'
                if case == 'engine':
                    flutter['engineRevision'] = '0' * 40
                outputs = [json.dumps(flutter), 'Xcode 0.0\nBuild version unsupported' if case == 'xcode' else xcode, '0.0' if case == 'sdk' else ios.IOS_SIMULATOR_SDK_VERSION]
                with patch.object(ios, 'run', side_effect=outputs), patch.object(ios, 'configured_toolchain_pins', return_value=('3.41.6', '1.95.0')), patch.object(ios, 'installed_flutter_revisions', return_value=('a' * 40, 'b' * 40)):
                    with self.assertRaises(ios.NativeFailure) as raised:
                        ios.inspect_toolchain()
                self.assertEqual(raised.exception.category, 'infrastructure')

    def test_build_tools_never_receive_disposable_credentials(self):
        process = unittest.mock.Mock()
        process.communicate.return_value = ('built', '')
        process.returncode = 0
        with patch.dict(os.environ, {'WEAVE_NATIVE_MEMBER_EMAIL': 'private@example.test', 'WEAVE_NATIVE_MEMBER_PASSWORD': 'private-password'}), patch.object(ios.subprocess, 'Popen', return_value=process) as execute, patch.object(ios, 'stop_flutter_process_group'):
            self.assertEqual(ios.run(['cargo', 'build']), 'built')
        environment = execute.call_args.kwargs['env']
        self.assertNotIn('WEAVE_NATIVE_MEMBER_EMAIL', environment)
        self.assertNotIn('WEAVE_NATIVE_MEMBER_PASSWORD', environment)

    def test_failed_browser_retains_safe_stages_and_distinguishes_launch_from_ui_failure(self):
        for started, waiting, expected_category, expected_reason in [
            (False, False, 'infrastructure', 'browser-runner-start-failed'),
            (False, True, 'infrastructure', 'target-app-start-failed'),
            (True, False, 'integration', 'browser-ui-failed'),
        ]:
            with self.subTest(started=started, waiting=waiting), tempfile.TemporaryDirectory() as name:
                temporary = Path(name)
                derived = temporary / 'derived'
                products = derived / 'Build/Products'
                bundle = products / 'BrowserDriver.xctest'
                bundle.mkdir(parents=True)
                (products / 'BrowserDriver.xctestrun').write_bytes(plistlib.dumps({'BrowserDriver': {'BlueprintName': 'BrowserDriver'}}))
                def launch(arguments, **kwargs):
                    result = unittest.mock.Mock()
                    result.wait.return_value = 1 if arguments[0] == 'xcodebuild' else 0
                    result.poll.return_value = 1 if arguments[0] == 'xcodebuild' else None
                    log = kwargs['stdout']
                    if arguments[0] == 'xcodebuild':
                        if waiting:
                            log.write('NATIVE_IOS_BROWSER_STAGE phase=driver-started\n')
                            log.write('NATIVE_IOS_BROWSER_STAGE phase=app-wait-started\n')
                        if started:
                            log.write('NATIVE_IOS_BROWSER_STAGE phase=driver-started\n')
                            log.write('NATIVE_IOS_BROWSER_STAGE phase=app-observed\n')
                            log.write('NATIVE_IOS_BROWSER_STAGE phase=issuer-observed\n')
                        log.write('raw-secret-password raw-private-email\n')
                    else:
                        log.write('NATIVE_PRODUCT_STAGE phase=shell-ready\n')
                        log.write('NATIVE_PRODUCT_STAGE phase=sign-in-tapped token=raw-secret-password\n')
                        log.write('NATIVE_PRODUCT_STAGE phase=raw-secret-password\n')
                    log.flush()
                    return result
                output = io.StringIO()
                with redirect_stdout(output), patch.object(ios.subprocess, 'Popen', side_effect=launch), patch.object(ios, 'stop_flutter_process_group'), patch.object(ios, 'simulator_driver_barrier', return_value=temporary / 'ready'), patch.object(ios, 'simulator_app_pid', return_value='123'), patch.object(ios, 'release_driver_barrier', side_effect=lambda *args: (temporary / 'ready').touch()):
                    with self.assertRaises(ios.NativeFailure) as raised:
                        ios.execute_phase('initial', 'owned-simulator', derived, temporary, {'WEAVE_NATIVE_TEST_RUN_ID': 'test-run'}, {'email': 'raw-private-email', 'password': 'raw-secret-password'})
                self.assertEqual(raised.exception.category, expected_category)
                self.assertEqual(raised.exception.reason, expected_reason)
                self.assertIn('category=' + expected_category, output.getvalue())
                self.assertIn('NATIVE_PRODUCT_STAGE phase=sign-in-tapped', output.getvalue())
                self.assertNotIn('raw-secret-password', output.getvalue())
                self.assertNotIn('raw-private-email', output.getvalue())
                self.assertFalse((products / 'private-member.json').exists())
                self.assertFalse((temporary / 'ready').exists())
                self.assertFalse((temporary / 'workspace-initial.ready').exists())

    def test_workspace_relay_requires_exact_completed_marker_and_private_file(self):
        with tempfile.TemporaryDirectory() as name:
            path = Path(name) / 'flutter.log'
            completion = ios.prepare_workspace_completion(Path(name), 'initial')
            flutter = unittest.mock.Mock()
            driver = unittest.mock.Mock()
            flutter.poll.return_value = driver.poll.return_value = None
            output = io.StringIO()
            with path.open('w+') as writer:
                writer.write('private-email private-password\nNATIVE_PRODUCT_STAGE phase=workspace-ready\n')
                writer.flush()
                offset = writer.tell()
                with redirect_stdout(output):
                    self.assertTrue(ios.relay_workspace_completion(flutter, driver, path, completion, 'initial'))
                self.assertEqual(writer.tell(), offset)
            self.assertEqual(completion.read_text(), 'workspace-ready\n')
            self.assertEqual(completion.stat().st_mode & 0o777, 0o600)
            self.assertIn('source=flutter-workspace-assertion', output.getvalue())
            self.assertNotIn('private-email', output.getvalue())
            self.assertNotIn('private-password', output.getvalue())

    def test_workspace_relay_rejects_partial_forged_and_oversized_lines(self):
        for text in ['NATIVE_PRODUCT_STAGE phase=workspace-ready',
                     'NATIVE_PRODUCT_STAGE phase=workspace-ready token=private-token\n',
                     'prefix NATIVE_PRODUCT_STAGE phase=workspace-ready\n',
                     'flutter: flutter: NATIVE_PRODUCT_STAGE phase=workspace-ready\n',
                     'flutter: NATIVE_PRODUCT_STAGE phase=workspace-ready private-token\n',
                     'flutter: NATIVE_PRODUCT_STAGE phase=workspace-ready\r\n',
                     'x' * 5000 + 'NATIVE_PRODUCT_STAGE phase=workspace-ready\n']:
            with self.subTest(text=text[:30]), tempfile.TemporaryDirectory() as name:
                path = Path(name) / 'flutter.log'
                path.write_text(text)
                completion = ios.prepare_workspace_completion(Path(name), 'initial')
                flutter = unittest.mock.Mock()
                driver = unittest.mock.Mock()
                flutter.poll.return_value = driver.poll.return_value = None
                with patch.object(ios.time, 'monotonic', side_effect=[0, 1, 3]), patch.object(ios.time, 'sleep'):
                    with self.assertRaisesRegex(ios.NativeFailure, 'native-workspace-completion-timeout'):
                        ios.relay_workspace_completion(flutter, driver, path, completion, 'initial', timeout=2)
                self.assertFalse(completion.exists())

    def test_drive_log_prefix_preserves_only_genuine_workspace_proof(self):
        with tempfile.TemporaryDirectory() as name:
            path = Path(name) / 'flutter.log'
            path.write_text('flutter: NATIVE_PRODUCT_STAGE phase=workspace-ready\n')
            completion = ios.prepare_workspace_completion(Path(name), 'initial')
            flutter = unittest.mock.Mock()
            driver = unittest.mock.Mock()
            flutter.poll.return_value = driver.poll.return_value = None
            output = io.StringIO()
            with redirect_stdout(output):
                self.assertTrue(ios.relay_workspace_completion(flutter, driver, path, completion, 'initial'))
                ios.emit_phase_stages('initial', path.read_text(), '')
            self.assertEqual(completion.read_text(), 'workspace-ready\n')
            self.assertIn('IOS_DIAGNOSTIC journey=initial NATIVE_PRODUCT_STAGE phase=workspace-ready', output.getvalue())

    def test_workspace_relay_waits_for_split_marker_completed_newline(self):
        with tempfile.TemporaryDirectory() as name:
            completion = ios.prepare_workspace_completion(Path(name), 'initial')
            flutter = unittest.mock.Mock()
            driver = unittest.mock.Mock()
            flutter.poll.return_value = driver.poll.return_value = None
            fragments = iter(['NATIVE_PRODUCT_STA', 'GE phase=workspace-ready', '\n'])
            observer = unittest.mock.Mock()
            reads = []
            def read(limit):
                self.assertFalse(completion.exists())
                reads.append(limit)
                return next(fragments)
            observer.read.side_effect = read
            handle = unittest.mock.MagicMock()
            handle.__enter__.return_value = observer
            with redirect_stdout(io.StringIO()), patch.object(Path, 'open', return_value=handle),                     patch.object(ios.time, 'monotonic', side_effect=[0, 1, 2, 3, 5]), patch.object(ios.time, 'sleep'):
                self.assertTrue(ios.relay_workspace_completion(flutter, driver, Path(name) / 'log', completion, 'initial', timeout=4))
            self.assertEqual(reads, [65536, 65536, 65536])
            self.assertEqual(completion.read_text(), 'workspace-ready\n')

    def test_workspace_relay_requires_fresh_suffix_for_held_actor(self):
        marker = 'NATIVE_PRODUCT_STAGE phase=workspace-ready\n'
        for fresh in (False, True):
            with self.subTest(fresh=fresh), tempfile.TemporaryDirectory() as name:
                path = Path(name) / 'flutter.log'
                path.write_text(marker + (marker if fresh else ''))
                completion = ios.prepare_workspace_completion(Path(name), 'initial')
                flutter = unittest.mock.Mock(); driver = unittest.mock.Mock()
                flutter.poll.return_value = driver.poll.return_value = None
                with redirect_stdout(io.StringIO()), patch.object(ios.time, 'monotonic', side_effect=[0, 1, 3]), patch.object(ios.time, 'sleep'):
                    if fresh:
                        self.assertTrue(ios.relay_workspace_completion(flutter, driver, path, completion, 'initial', timeout=2, start_offset=len(marker)))
                    else:
                        with self.assertRaisesRegex(ios.NativeFailure, 'native-workspace-completion-timeout'):
                            ios.relay_workspace_completion(flutter, driver, path, completion, 'initial', timeout=2, start_offset=len(marker))
                self.assertEqual(completion.exists(), fresh)

    def test_workspace_relay_rejects_invalid_owned_offsets(self):
        with tempfile.TemporaryDirectory() as name:
            path = Path(name) / 'flutter.log'; path.write_text('old\n')
            for offset in (-1, 5):
                with self.subTest(offset=offset):
                    completion = ios.prepare_workspace_completion(Path(name), 'initial')
                    with self.assertRaisesRegex(ios.NativeFailure, 'invalid-owned-log-offset'):
                        ios.relay_workspace_completion(unittest.mock.Mock(), unittest.mock.Mock(), path, completion, 'initial', start_offset=offset)
                    self.assertFalse(completion.exists())

    def test_inspection_dispatch_precedes_qualification_artifacts(self):
        import run_dual_ios_collaboration as dual
        with tempfile.TemporaryDirectory() as name, patch.dict(os.environ, {'WEAVE_NATIVE_LOCAL_INSPECTION': 'true', 'WEAVE_E2E_RUN_ID': 'owned-run'}), patch.object(dual.ios, 'verified_private_parent', return_value=Path(name)), patch.object(dual, 'main', return_value=0) as inspect:
            self.assertEqual(qualification.main(), 70)
            inspect.assert_called_once_with(['--output', str(Path(name) / 'dual-ios-result-owned-run.json')])
            self.assertEqual(list(Path(name).iterdir()), [])

    def test_workspace_relay_never_overwrites_completion_path(self):
        with tempfile.TemporaryDirectory() as name:
            path = Path(name) / 'flutter.log'
            path.write_text('NATIVE_PRODUCT_STAGE phase=workspace-ready\n')
            completion = ios.prepare_workspace_completion(Path(name), 'initial')
            completion.write_text('stale-or-replaced')
            flutter = unittest.mock.Mock()
            driver = unittest.mock.Mock()
            flutter.poll.return_value = driver.poll.return_value = None
            with self.assertRaises(FileExistsError):
                ios.relay_workspace_completion(flutter, driver, path, completion, 'initial')
            self.assertEqual(completion.read_text(), 'stale-or-replaced')

    def test_workspace_relay_never_signals_after_process_exit(self):
        for flutter_status, driver_status, marker in [(7, None, ''), (7, None, 'NATIVE_PRODUCT_STAGE phase=workspace-ready\n'),
                                                      (None, 0, 'NATIVE_PRODUCT_STAGE phase=workspace-ready\n')]:
            with self.subTest(flutter_status=flutter_status, driver_status=driver_status), tempfile.TemporaryDirectory() as name:
                path = Path(name) / 'flutter.log'
                path.write_text(marker)
                completion = ios.prepare_workspace_completion(Path(name), 'initial')
                flutter = unittest.mock.Mock()
                driver = unittest.mock.Mock()
                flutter.poll.return_value, driver.poll.return_value = flutter_status, driver_status
                if driver_status is not None:
                    self.assertFalse(ios.relay_workspace_completion(flutter, driver, path, completion, 'initial'))
                else:
                    with self.assertRaises(ios.NativeFailure):
                        ios.relay_workspace_completion(flutter, driver, path, completion, 'initial')
                self.assertFalse(completion.exists())

    def test_workspace_completion_clears_stale_and_rejects_symlink(self):
        with tempfile.TemporaryDirectory() as name:
            root = Path(name)
            completion = root / 'workspace-initial.ready'
            completion.write_text('workspace-ready\n')
            self.assertEqual(ios.prepare_workspace_completion(root, 'initial'), completion.resolve())
            self.assertFalse(completion.exists())
            target = root / 'private'
            target.write_text('untouched')
            completion.symlink_to(target)
            with self.assertRaises(ios.NativeFailure):
                ios.prepare_workspace_completion(root, 'initial')
            self.assertEqual(target.read_text(), 'untouched')

    def test_post_submit_diagnostics_never_export_private_text(self):
        safe = 'NATIVE_IOS_POST_SUBMIT_STATE phase=pending issuerSurfaces=1 providerErrors=0 appAlertCount=1 systemAlertCount=0'
        output = io.StringIO()
        with redirect_stdout(output):
            ios.emit_phase_stages('initial', '', '\n'.join([safe, safe, safe + ' private-password',
                safe.replace('pending', 'private-account'), safe.replace('providerErrors=0', 'providerErrors=private-token'),
                safe.replace('appAlertCount=1', 'appAlertCount=private-alert'),
                safe.replace('systemAlertCount=0', 'systemAlertCount=1000')]))
        self.assertEqual(output.getvalue().count('IOS_POST_SUBMIT_DIAGNOSTIC'), 1)
        self.assertIn('providerErrors=0 appAlertCount=1 systemAlertCount=0 supportSafe=true', output.getvalue())
        for private in ('private-password', 'private-account', 'private-token', 'private-alert', '1000'):
            self.assertNotIn(private, output.getvalue())

    def test_password_prompt_decline_is_exact_alert_scoped_and_globally_unique(self):
        driver = (Path(__file__).parent / 'ios_browser_driver/BrowserDriver.swift').read_text()
        handler = driver.split('func declinePasswordSavePrompt', 1)[1].split('func waitForNativeWorkspace', 1)[0]
        self.assertIn('app.alerts.matching(NSPredicate(format: "label IN %@", Self.passwordSaveTitles))', handler)
        self.assertIn('system.alerts.matching(NSPredicate(format: "label IN %@", Self.passwordSaveTitles))', handler)
        self.assertIn('Self.uniquePasswordDeclineSurface(', handler)
        self.assertLess(handler.index('Self.uniquePasswordDeclineSurface('), handler.index('decline.tap()'))
        self.assertIn('guard decline.isHittable', handler)
        self.assertIn('prompt.waitForNonExistence(timeout: min(10, max(0, deadline.timeIntervalSinceNow)))', handler)
        self.assertNotIn('.firstMatch', handler)
        self.assertNotIn('app.activate()', handler)
        self.assertIn('static let passwordDeclineLabels = ["Später", "Not Now"]', driver)
        wait = driver.split('func waitForNativeWorkspace', 1)[1].split('func testNativeDocumentPicker', 1)[0]
        self.assertLess(wait.index('Self.nativeWorkspaceObserved'), wait.index('declinePasswordSavePrompt'))
        output = io.StringIO()
        with redirect_stdout(output):
            ios.emit_phase_stages('initial', '', 'NATIVE_IOS_BROWSER_STAGE phase=password-save-declined')
        self.assertIn('NATIVE_IOS_BROWSER_STAGE phase=password-save-declined supportSafe=true', output.getvalue())

    def test_ios_browser_pass_waits_for_real_workspace_signal(self):
        driver = (Path(__file__).parent / 'ios_browser_driver/BrowserDriver.swift').read_text()
        submitted = driver.split('phase=password-submitted', 1)[1]
        self.assertLess(submitted.index('guard waitForNativeWorkspace'), submitted.index('status=passed phase=form-submitted'))
        wait = driver.split('func waitForNativeWorkspace', 1)[1].split('func testNativeDocumentPicker', 1)[0]
        self.assertIn('(fixture.ordinaryOrganizationOrigin == nil && Self.nativeWorkspaceObserved(path: fixture.nativeAuthCompletionPath))', wait)
        self.assertIn('(fixture.ordinaryOrganizationOrigin != nil && Self.ordinaryWorkspaceObserved(app))', wait)
        ordinary = (Path(__file__).parent / 'ios_browser_driver/OrdinaryProductSmoke.swift').read_text()
        workspace = ordinary.split('static func ordinaryWorkspaceObserved', 1)[1].split('struct OrdinaryScrollBudget', 1)[0]
        for identifier in ('nav-chat', 'nav-files', 'nav-calendar', 'nav-settings'):
            self.assertIn(identifier, workspace)
        self.assertIn('app.buttons.matching(identifier: $0).count == 1', workspace)
        self.assertIn('phase=auth-completion-observed', driver)
        self.assertNotIn('app.activate()', driver.split('func waitForNativeWorkspace', 1)[1].split('func testNativeDocumentPicker', 1)[0])

    def test_shell_observer_does_not_move_subprocess_writer_offset(self):
        with tempfile.TemporaryDirectory() as name:
            path = Path(name) / 'flutter.log'
            process = unittest.mock.Mock()
            process.poll.return_value = None
            with path.open('w+') as writer:
                writer.write('NATIVE_PRODUCT_STAGE phase=shell-ready\n')
                writer.flush()
                offset = writer.tell()
                with redirect_stdout(io.StringIO()):
                    ios.wait_for_flutter_shell(process, path, 'initial')
                self.assertEqual(writer.tell(), offset)
                writer.write('subsequent-output\n')
            self.assertTrue(path.read_text().endswith('subsequent-output\n'))

    def test_startup_failure_never_launches_browser_and_removes_private_fixture(self):
        for exit_code, expected_reason in [(7, 'integration-app-exited-before-readiness'),
                                            (None, 'integration-app-ready-timeout')]:
            with self.subTest(exit_code=exit_code), tempfile.TemporaryDirectory() as name:
                temporary = Path(name)
                derived = temporary / 'derived'
                products = derived / 'Build/Products'
                bundle = products / 'BrowserDriver.xctest'
                bundle.mkdir(parents=True)
                (products / 'BrowserDriver.xctestrun').write_bytes(plistlib.dumps({'BrowserDriver': {'BlueprintName': 'BrowserDriver'}}))
                flutter = unittest.mock.Mock()
                flutter.poll.return_value = exit_code
                clock = iter([0, 1, 901])
                output = io.StringIO()
                with redirect_stdout(output), patch.object(ios.subprocess, 'Popen', return_value=flutter) as launch, \
                        patch.object(ios, 'simulator_app_pid', return_value='101'), \
                        patch.object(ios.time, 'monotonic', side_effect=lambda: next(clock)), \
                        patch.object(ios.time, 'sleep'), patch.object(ios, 'stop_flutter_process_group'), \
                        patch.object(ios, 'simulator_driver_barrier', return_value=temporary / 'ready'):
                    with self.assertRaises(ios.NativeFailure) as raised:
                        ios.execute_phase('initial', 'owned-simulator', derived, temporary, {'WEAVE_NATIVE_TEST_RUN_ID': 'test-run'},
                                          {'email': 'raw-private-email', 'password': 'raw-secret-password'})
                self.assertEqual(raised.exception.category, 'infrastructure')
                self.assertEqual(raised.exception.reason, expected_reason)
                self.assertEqual(launch.call_count, 1)
                self.assertEqual(launch.call_args.args[0][0], 'make')
                self.assertFalse((products / 'private-member.json').exists())
                self.assertNotIn('raw-secret-password', output.getvalue())
                self.assertNotIn('raw-private-email', output.getvalue())

    def test_shell_readiness_is_not_lost_inside_large_build_output_chunk(self):
        with tempfile.TemporaryDirectory() as name:
            path = Path(name) / 'flutter.log'
            path.write_text('NATIVE_PRODUCT_STAGE phase=shell-ready\n' + 'x' * 60000)
            process = unittest.mock.Mock()
            process.poll.return_value = None
            with redirect_stdout(io.StringIO()), patch.object(ios.time, 'monotonic', side_effect=[0, 1, 901]):
                ios.wait_for_flutter_shell(process, path, 'initial')

    def test_physical_and_macos_cannot_enable_simulator_handoff(self):
        with tempfile.TemporaryDirectory() as name:
            temporary = Path(name)
            flutter = temporary / 'flutter'
            flutter.write_text("""#!/usr/bin/env python3
import json, os, sys
if sys.argv[1] == 'devices':
    print(json.dumps([{'id': 'phone', 'emulator': False}, {'id': 'macos', 'emulator': False}, {'id': 'owned-sim', 'emulator': True}]))
elif sys.argv[1] in ('test', 'drive', 'build'):
    arg = next(item for item in sys.argv if item.startswith('--dart-define-from-file='))
    with open(arg.split('=', 1)[1]) as source, open(os.environ['HANDOFF_PROBE_OUTPUT'], 'w') as output:
        values=json.load(source)
        values['_probe_reporter']=sys.argv[sys.argv.index('--reporter')+1] if '--reporter' in sys.argv else 'default-buffered-github'
        values['_probe_command']=sys.argv[1]
        values['_probe_keep_app']='--keep-app-running' in sys.argv
        values['_probe_driver']=next((a for a in sys.argv if a.startswith('--driver=')), '')
        values['_probe_existing']=next((a for a in sys.argv if a.startswith('--use-existing-app=')), '')
        values['_probe_no_codesign']='--no-codesign' in sys.argv
        values['_probe_simulator']='--simulator' in sys.argv
        values['_probe_debug']='--debug' in sys.argv
        json.dump(values, output)
else:
    sys.exit(2)
""")
            flutter.chmod(0o755)
            for target, device, expected, phase in [('physical-device-auth-e2e', 'phone', 'false', 'initial'),
                                                    ('physical-device-product-e2e', 'phone', 'false', 'initial'),
                                                    ('physical-device-product-e2e', 'macos', 'false', 'restore'),
                                                    ('ios-simulator-product-e2e', 'owned-sim', 'true', 'initial'),
                                                    ('ios-simulator-product-e2e', 'owned-sim', 'true', 'restore'),
                                                    ('ios-simulator-product-e2e', 'owned-sim', 'true', 'unsupported')]:
                with self.subTest(target=target, device=device, phase=phase):
                    env = os.environ.copy()
                    env.update({'PATH': str(temporary) + os.pathsep + env['PATH'],
                                'CI': 'true', 'GITHUB_ACTIONS': 'true', 'TERM': 'dumb',
                                'WEAVE_PHYSICAL_DEVICE_ID': device, 'WEAVE_IOS_SIMULATOR_ID': 'owned-sim',
                                'WEAVE_NATIVE_EXPECT_SIMULATOR': 'true', 'WEAVE_NATIVE_TEST_RUN_ID': 'existing-run-id',
                                'WEAVE_DEVICE_PRODUCT_PHASE': phase,
                                'WEAVE_DEVICE_ORGANIZATION_ORIGIN': 'https://weave.localhost',
                                'WEAVE_DEVICE_DISPOSABLE_STACK': 'true',
                                'WEAVE_DEVICE_API_BASE_URL': 'https://api.weave.localhost/api',
                                'WEAVE_DEVICE_OIDC_ISSUER_URL': 'https://auth.weave.localhost/realms/weave',
                                'HANDOFF_PROBE_OUTPUT': str(temporary / 'defines.json')})
                    (temporary / 'defines.json').unlink(missing_ok=True)
                    arguments = ['make', target]
                    if expected == 'true':
                        arguments += ['native_simulator_action=attach', 'native_simulator_vm_uri=http://127.0.0.1:12345/ownedDebug/']
                    result = ios.subprocess.run(arguments, cwd=Path(__file__).resolve().parents[1], env=env,
                                                capture_output=True, text=True)
                    if phase == 'unsupported':
                        self.assertNotEqual(result.returncode, 0)
                        self.assertFalse((temporary / 'defines.json').exists())
                        continue
                    self.assertEqual(result.returncode, 0, result.stderr)
                    defines = json.loads((temporary / 'defines.json').read_text())
                    self.assertEqual(defines['WEAVE_NATIVE_EXPECT_SIMULATOR'], expected)
                    self.assertEqual(defines['WEAVE_NATIVE_TEST_RUN_ID'], 'existing-run-id')
                    self.assertEqual(defines['WEAVE_NATIVE_ORGANIZATION_ORIGIN'], 'https://weave.localhost')
                    if expected == 'true':
                        self.assertEqual(defines['_probe_command'], 'drive')
                        self.assertTrue(defines['_probe_keep_app'])
                        self.assertEqual(defines['_probe_driver'], '--driver=test_driver/integration_test.dart')
                        self.assertEqual(defines['_probe_existing'], '--use-existing-app=http://127.0.0.1:12345/ownedDebug/')
                        self.assertEqual(defines['WEAVE_DEVICE_PRODUCT_PHASE'], phase)
                    else:
                        self.assertEqual(defines['_probe_command'], 'test')
                        self.assertFalse(defines['_probe_keep_app'])
                        self.assertEqual(defines['_probe_reporter'], 'expanded')
            missing_origin = env.copy()
            missing_origin.pop('WEAVE_DEVICE_ORGANIZATION_ORIGIN')
            (temporary / 'defines.json').unlink(missing_ok=True)
            rejected = subprocess.run(['make', 'physical-device-auth-e2e'],
                cwd=Path(__file__).resolve().parents[1], env=missing_origin,
                capture_output=True, text=True)
            self.assertNotEqual(rejected.returncode, 0)
            self.assertFalse((temporary / 'defines.json').exists())
            env['WEAVE_DEVICE_PRODUCT_PHASE'] = 'initial'
            for action, uri, succeeds in [('build', '', True), ('attach', '', False),
                                         ('attach', 'http://other.invalid:12345/private/', False),
                                         ('attach', 'http://127.0.0.1:12345/private/?query=private', False),
                                         ('unsupported', '', False), ('', '', False)]:
                with self.subTest(action=action):
                    (temporary / 'defines.json').unlink(missing_ok=True)
                    result = subprocess.run(['make', 'ios-simulator-product-e2e',
                                             'native_simulator_action=' + action,
                                             'native_simulator_vm_uri=' + uri],
                                            cwd=Path(__file__).resolve().parents[1], env=env,
                                            capture_output=True, text=True)
                    self.assertEqual(result.returncode == 0, succeeds)
                    if succeeds:
                        defines = json.loads((temporary / 'defines.json').read_text())
                        self.assertEqual(defines['_probe_command'], 'build')
                        self.assertEqual(defines['WEAVE_DEVICE_PRODUCT_PHASE'], 'initial')
                        self.assertFalse(defines['_probe_no_codesign'])
                        self.assertTrue(defines['_probe_simulator'])
                        self.assertTrue(defines['_probe_debug'])
                    else:
                        self.assertFalse((temporary / 'defines.json').exists())

    def test_owned_vm_uri_requires_bounded_loopback_event_and_never_exports_uri(self):
        prefix = ios.simulator_vm.VM_EVENT_PREFIX
        uri = 'http://127.0.0.1:12345/ownedDebug=/'
        self.assertEqual(ios.owned_vm_service_uri(prefix + uri), uri)
        for invalid in ['https://127.0.0.1:12345/code/', 'http://localhost:12345/code/',
                        'http://other.invalid:12345/code/', 'http://user@127.0.0.1:12345/code/',
                        'http://127.0.0.1:12345/code/?token=private',
                        'http://127.0.0.1:12345/code/#private', 'http://127.0.0.1:99999/code/',
                        'http://127.0.0.1:12345/' + 'x' * 512, uri + '\nprivate',
                        'http://127.0.0.1:12345/code\r/', 'http://127.0.0.1:12345/code with space/']:
            with self.subTest(invalid=invalid[:40]), redirect_stdout(io.StringIO()) as output:
                with self.assertRaises(ios.NativeFailure) as raised:
                    ios.owned_vm_service_uri(prefix + invalid)
                self.assertNotIn(invalid, str(raised.exception))
                self.assertEqual(output.getvalue(), '')

    def test_owned_vm_discovery_requires_unchanged_pid_and_fresh_exact_query(self):
        for pids, message, reason in [(['102'], None, 'owned-process-replaced'),
                                     (['101', '102'], ios.simulator_vm.VM_EVENT_PREFIX + 'http://127.0.0.1:12345/code/', 'owned-process-replaced'),
                                     (['101'], None, 'owned-vm-event-timeout'),
                                     (['101', '101'], ios.simulator_vm.VM_EVENT_PREFIX + 'http://127.0.0.1:12345/code/', None)]:
            with self.subTest(reason=reason), patch.object(ios, 'simulator_app_pid', side_effect=pids), \
                    patch.object(ios.simulator_vm, 'query_vm_service_event', return_value=message) as query, \
                    patch.object(ios.time, 'monotonic', side_effect=[0, 0, 46]), patch.object(ios.time, 'sleep'):
                if reason is None:
                    self.assertEqual(ios.discover_owned_vm_service('owned', '101', '2026-10-12 07:40:00'),
                                     'http://127.0.0.1:12345/code/')
                else:
                    with self.assertRaises(ios.NativeFailure) as raised:
                        ios.discover_owned_vm_service('owned', '101', '2026-10-12 07:40:00')
                    self.assertEqual(raised.exception.reason, reason)
                if query.called:
                    query.assert_called_once_with('owned', 101, '2026-10-12 07:40:00')

    def test_phase_app_build_install_paused_launch_precedes_owned_vm_discovery(self):
        calls = []
        def command(arguments, **kwargs):
            calls.append(arguments)
            return ios.BUNDLE + ': 101' if 'launch' in arguments else ''
        with patch.object(ios, 'run', side_effect=command), \
                patch.object(ios.simulator_vm, 'log_start_timestamp', return_value='2026-10-12 07:40:00'), \
                patch.object(ios, 'discover_owned_vm_service', return_value='http://127.0.0.1:12345/privateDebug/') as discovery:
            self.assertEqual(self.actual_phase_start('owned', {'WEAVE_DEVICE_PRODUCT_PHASE': 'restore'}),
                             ('101', 'http://127.0.0.1:12345/privateDebug/'))
        self.assertEqual(calls[0], ['make', 'ios-simulator-product-e2e', 'native_simulator_action=build'])
        self.assertEqual(calls[1][:4], ['xcrun', 'simctl', 'install', 'owned'])
        self.assertEqual(calls[2], ['xcrun', 'simctl', 'launch', 'owned', ios.BUNDLE,
                                  '--enable-dart-profiling', '--disable-vm-service-publication', '--start-paused'])
        discovery.assert_called_once_with('owned', '101', '2026-10-12 07:40:00')

    def test_phase_build_and_invalid_launch_fail_before_vm_attachment(self):
        for build_failed in (True, False):
            with self.subTest(build_failed=build_failed):
                call_count = 0
                def command(arguments, **kwargs):
                    nonlocal call_count
                    call_count += 1
                    if build_failed:
                        raise ios.NativeFailure('infrastructure', 'native-tool', 'command-exit-failed')
                    return 'other.bundle: 101' if 'launch' in arguments else ''
                with patch.object(ios, 'run', side_effect=command), \
                        patch.object(ios, 'discover_owned_vm_service') as discovery:
                    with self.assertRaises(ios.NativeFailure) as raised:
                        self.actual_phase_start('owned', {})
                self.assertEqual(raised.exception.reason, 'command-exit-failed' if build_failed else 'invalid-owned-launch-identity')
                self.assertEqual(call_count, 1 if build_failed else 3)
                discovery.assert_not_called()

    def test_actual_dart_phase_selection_keeps_non_simulator_journeys(self):
        dart = shutil.which('dart')
        if dart is None:
            self.skipTest('Dart SDK unavailable')
        source = (Path(__file__).resolve().parents[1] / 'integration_test/system_browser_auth_e2e_test.dart').read_text()
        helper = source.split('bool nativeProductPhaseEnabled(', 1)[1].split('\nvoid main()', 1)[0]
        with tempfile.TemporaryDirectory() as name:
            probe = Path(name) / 'phase.dart'
            probe.write_text('bool nativeProductPhaseEnabled(' + helper + '''
void main() {
  const cases = [
    (true, 'initial', 'initial', true), (true, 'initial', 'restore', false),
    (true, 'restore', 'initial', false), (true, 'restore', 'restore', true),
    (true, '', 'initial', false), (true, 'unsupported', 'restore', false),
    (false, 'initial', 'restore', true), (false, 'restore', 'initial', true),
    (false, '', 'initial', true), (false, 'unsupported', 'restore', true),
  ];
  for (final (simulator, selected, journey, expected) in cases) {
    if (nativeProductPhaseEnabled(simulator: simulator, selected: selected, journey: journey) != expected) {
      throw StateError('phase selection mismatch');
    }
  }
}
''')
            result = subprocess.run([dart, 'run', str(probe)], cwd=name, capture_output=True, text=True, timeout=30)
            self.assertEqual(result.returncode, 0, result.stderr)

    def test_actual_native_diagnostic_bypasses_only_simulator_print_capture(self):
        dart = shutil.which('dart')
        if dart is None:
            self.skipTest('Dart SDK unavailable')
        source = (Path(__file__).resolve().parents[1] / 'integration_test/system_browser_auth_e2e_test.dart').read_text()
        helper = source.split('void emitNativeProductDiagnostic(', 1)[1].split('\nvoid _nativeProductDiagnostic(', 1)[0]
        with tempfile.TemporaryDirectory() as name:
            probe = Path(name) / 'diagnostic.dart'
            probe.write_text("import 'dart:async';\n" +
                             'void debugPrint(String message) => print(message);\n' +
                             'void emitNativeProductDiagnostic(' + helper + '''
void main() {
  final captured = <String>[];
  runZoned(() {
    print('ordinary-private-output');
    emitNativeProductDiagnostic('NATIVE_PRODUCT_STAGE phase=shell-ready', simulatorDriver: true);
    emitNativeProductDiagnostic('NATIVE_PRODUCT_STAGE phase=workspace-ready', simulatorDriver: true);
    emitNativeProductDiagnostic('NATIVE_PRODUCT_INITIAL_RESULT status=passed', simulatorDriver: true);
    emitNativeProductDiagnostic('NATIVE_PRODUCT_SIGN_IN_RESULT status=passed', simulatorDriver: true);
    emitNativeProductDiagnostic('NATIVE_PRODUCT_STAGE phase=files-start', simulatorDriver: false);
    for (final invalid in ['ordinary-private-output',
        'NATIVE_PRODUCT_STAGE phase=shell-ready\\nprivate-output',
        'NATIVE_PRODUCT_STAGE phase=shell-ready\\rprivate-output',
        'NATIVE_PRODUCT_STAGE ' + 'x' * 2049]) {
      var rejected = false;
      try { emitNativeProductDiagnostic(invalid, simulatorDriver: true); }
      on ArgumentError { rejected = true; }
      if (!rejected) throw StateError('unsafe diagnostic accepted');
    }
  }, zoneSpecification: ZoneSpecification(print: (_, __, ___, message) => captured.add(message)));
  if (captured.length != 2 || captured[0] != 'ordinary-private-output' ||
      captured[1] != 'NATIVE_PRODUCT_STAGE phase=files-start') {
    throw StateError('unexpected test zone capture');
  }
}
''')
            result = subprocess.run([dart, 'run', str(probe)], cwd=name, capture_output=True, text=True, timeout=30)
            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertEqual(result.stdout.splitlines(), [
                'NATIVE_PRODUCT_STAGE phase=shell-ready',
                'NATIVE_PRODUCT_STAGE phase=workspace-ready',
                'NATIVE_PRODUCT_INITIAL_RESULT status=passed',
                'NATIVE_PRODUCT_SIGN_IN_RESULT status=passed'])
            self.assertNotIn('private-output', result.stdout + result.stderr)

    def test_actual_backend_failure_classifier_reads_only_bounded_typed_causes(self):
        dart = shutil.which('dart')
        if dart is None:
            self.skipTest('Dart SDK unavailable')
        client = Path(__file__).resolve().parents[1]
        source = (client / 'integration_test/system_browser_auth_e2e_test.dart').read_text()
        helper = source.split('({String failure, String status}) nativeBackendFailureDiagnostic(', 1)[1].split('\nvoid emitNativeAuthFailureDiagnostic(', 1)[0]
        with tempfile.TemporaryDirectory() as name:
            probe = Path(name) / 'backend.dart'
            probe.write_text("import 'dart:convert';\n"
                + "import '" + (client / 'lib/core/failures/app_failure.dart').as_uri() + "';\n"
                + "import '" + (client / 'lib/features/auth/domain/entities/auth_failure.dart').as_uri() + "';\n"
                + '({String failure, String status}) nativeBackendFailureDiagnostic(' + helper + '''
class PrivateCause {
  @override
  String toString() => throw StateError('private-secret-must-not-be-read');
}
void main() {
  const rejected = 'The Weave backend rejected the current session.';
  final cases = <Object?>[
    const AppFailure.bootstrap('private-wrapper', cause: AppFailure.unknown(rejected, cause: 403)),
    const AuthFailure.protocol('private-wrapper', cause: AppFailure.unknown(rejected, cause: 401)),
    const AppFailure.bootstrap('private-wrapper', cause: AuthFailure.protocol('private-wrapper',
      cause: AppFailure.unknown('The Weave backend failed to reconcile organization access.', cause: 503))),
    const AppFailure.unknown('Unable to reach the Weave backend right now.'),
    const AppFailure.unknown('Unable to decode a Weave backend response right now.'),
    const AppFailure.unknown('The Weave backend returned an inconsistent identity-session reconciliation state.'),
    AppFailure.unknown('private-password', cause: PrivateCause()),
    for (final status in [99, 600, -401, '401 private-token']) AppFailure.unknown(rejected, cause: status),
    const AppFailure.bootstrap('private-1', cause: AppFailure.bootstrap('private-2',
      cause: AppFailure.bootstrap('private-3', cause: AppFailure.bootstrap('private-4',
        cause: AppFailure.unknown(rejected, cause: 403))))),
  ];
  for (final value in cases) {
    final result = nativeBackendFailureDiagnostic(value);
    print(jsonEncode({'failure': result.failure, 'status': result.status}));
  }
}
''')
            result = subprocess.run([dart, 'run', str(probe)], cwd=name, capture_output=True, text=True, timeout=30)
            self.assertEqual(result.returncode, 0, result.stderr)
            records = [json.loads(line) for line in result.stdout.splitlines()]
            self.assertEqual(records[:7], [
                {'failure': 'session-rejected', 'status': '403'},
                {'failure': 'session-rejected', 'status': '401'},
                {'failure': 'request-failed', 'status': '503'},
                {'failure': 'timeout', 'status': 'unavailable'},
                {'failure': 'response-decode', 'status': 'unavailable'},
                {'failure': 'inconsistent-state', 'status': 'unavailable'},
                {'failure': 'none', 'status': 'unavailable'},
            ])
            self.assertEqual(records[7:11], [{'failure': 'session-rejected', 'status': 'unavailable'}] * 4)
            self.assertEqual(records[11], {'failure': 'none', 'status': 'unavailable'})
            self.assertNotIn('private', result.stdout + result.stderr)

    def test_actual_bootstrap_diagnostic_formats_only_fixed_enums_and_bounded_counts(self):
        dart = shutil.which('dart')
        if dart is None:
            self.skipTest('Dart SDK unavailable')
        source = (Path(__file__).resolve().parents[1] / 'integration_test/system_browser_auth_e2e_test.dart').read_text()
        helper = source.split('String nativeBootstrapDiagnostic(', 1)[1].split('\nvoid safelyReportNativeBootstrapFailure(', 1)[0]
        with tempfile.TemporaryDirectory() as name:
            probe = Path(name) / 'bootstrap.dart'
            probe.write_text('String nativeBootstrapDiagnostic(' + helper + '''
void main() {
  print(nativeBootstrapDiagnostic(asyncState: 'data', bootstrapPhase: 'error', failureType: 'storage',
    causeType: 'PlatformException', platformCode: '-34018', lifecycle: 'resumed',
    counts: {'root': 1, 'bootstrapGate': 1, 'error': 1},
    simulatorDriver: true, productEnabled: true, disposable: true, offline: false));
  print(nativeBootstrapDiagnostic(asyncState: 'private@example.invalid', bootstrapPhase: 'https://private.invalid',
    failureType: 'private-password', causeType: 'private-token', platformCode: '-34018 private', lifecycle: 'private-body',
    backendFailure: 'private-backend', backendStatus: '403 private-token',
    counts: {'root': -1, 'error': 10000, 'private-password': 10},
    simulatorDriver: false, productEnabled: false, disposable: false, offline: true));
}
''')
            result = subprocess.run([dart, 'run', str(probe)], cwd=name, capture_output=True, text=True, timeout=30)
            self.assertEqual(result.returncode, 0, result.stderr)
            records = result.stdout.splitlines()
            self.assertEqual(len(records), 2)
            self.assertIn('asyncState=data bootstrapPhase=error failureType=storage causeType=PlatformException platformCode=-34018 lifecycle=resumed', records[0])
            self.assertIn('backendFailure=none backendStatus=unavailable', records[0])
            self.assertIn('rootCount=1 bootstrapGateCount=1 errorCount=1 loadingCount=0 onboardingCount=0 signInCount=0 workspaceCount=0', records[0])
            self.assertIn('asyncState=unavailable bootstrapPhase=unavailable failureType=unavailable causeType=unavailable platformCode=unavailable lifecycle=unavailable', records[1])
            self.assertIn('rootCount=0 bootstrapGateCount=0 errorCount=999', records[1])
            self.assertIn('backendFailure=unavailable backendStatus=unavailable', records[1])
            self.assertNotIn('private', result.stdout + result.stderr)
            output = io.StringIO()
            with redirect_stdout(output):
                ios.emit_phase_stages('initial', result.stdout, '')
            self.assertEqual(output.getvalue().count('IOS_FLUTTER_DIAGNOSTIC'), 2)

    def test_bootstrap_diagnostic_relay_rejects_private_and_unbounded_records(self):
        record = ('NATIVE_PRODUCT_STAGE phase=shell-bootstrap-failed asyncState=data bootstrapPhase=error '
                  'failureType=storage causeType=PlatformException platformCode=-34018 lifecycle=resumed '
                  'backendFailure=session-rejected backendStatus=403 '
                  'rootCount=1 bootstrapGateCount=1 errorCount=1 loadingCount=0 onboardingCount=0 signInCount=0 workspaceCount=0 '
                  'simulatorDriver=true productEnabled=true disposable=true offline=false')
        output = io.StringIO()
        with redirect_stdout(output):
            ios.emit_phase_stages('initial', record + '\nflutter: ' + record, '')
        self.assertEqual(output.getvalue().count('IOS_FLUTTER_DIAGNOSTIC'), 1)
        invalid = [record + ' private-password', 'flutter: flutter: ' + record, '00:01 +1: ' + record,
                   record + '\rprivate', record.replace('asyncState=data', 'asyncState=private'),
                   record.replace('platformCode=-34018', 'platformCode=-3401800'),
                   record.replace('backendFailure=session-rejected', 'backendFailure=private'),
                   record.replace('backendStatus=403', 'backendStatus=99'),
                   record.replace('backendStatus=403', 'backendStatus=600'),
                   record.replace('backendStatus=403', 'backendStatus=403 private'),
                   record.replace('rootCount=1', 'rootCount=1000'), record.replace('rootCount=1', 'rootCount=-1'),
                   record.replace('simulatorDriver=true', 'simulatorDriver=private'),
                   record.replace('phase=shell-bootstrap-failed', 'phase=private')]
        output = io.StringIO()
        with redirect_stdout(output):
            ios.emit_phase_stages('restore', '\n'.join(invalid), '')
        self.assertNotIn('IOS_FLUTTER_DIAGNOSTIC', output.getvalue())
        self.assertNotIn('private', output.getvalue())
        output = io.StringIO()
        with redirect_stdout(output):
            ios.emit_phase_stages('initial', '\n'.join(record.replace('rootCount=1', f'rootCount={i}') for i in range(10)), '')
        self.assertEqual(output.getvalue().count('IOS_FLUTTER_DIAGNOSTIC'), 3)

    def test_actual_bootstrap_reporter_preserves_original_failure_identity(self):
        dart = shutil.which('dart')
        if dart is None:
            self.skipTest('Dart SDK unavailable')
        source = (Path(__file__).resolve().parents[1] / 'integration_test/system_browser_auth_e2e_test.dart').read_text()
        helper = source.split('void safelyReportNativeBootstrapFailure(', 1)[1].split('\nvoid _reportNativeBootstrapFailure(', 1)[0]
        with tempfile.TemporaryDirectory() as name:
            probe = Path(name) / 'reporter.dart'
            probe.write_text('void safelyReportNativeBootstrapFailure(' + helper + '''
void main() {
  for (final reportThrows in [false, true]) {
    for (final fallbackThrows in [false, true]) {
      final original = StateError('original integration failure');
      var reports = 0;
      var fallbacks = 0;
      try {
        try { throw original; }
        catch (_) {
          safelyReportNativeBootstrapFailure(report: () {
            reports++;
            if (reportThrows) throw StateError('reporting failure');
          }, unavailable: () {
            fallbacks++;
            if (fallbackThrows) throw StateError('fallback failure');
          });
          rethrow;
        }
      } catch (observed) {
        if (!identical(observed, original)) throw StateError('original failure replaced');
      }
      if (reports != 1 || fallbacks != (reportThrows ? 1 : 0)) throw StateError('unexpected reporting invocation');
    }
  }
}
''')
            result = subprocess.run([dart, 'run', str(probe)], cwd=name, capture_output=True, text=True, timeout=30)
            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertEqual(result.stdout, '')

    def test_initial_process_stop_requires_exact_identity_and_absence(self):
        executable = Path('/owned/device/Runner.app/Runner')
        for before, after, expected in [('101', '', None), ('102', '', 'initial-process-identity-changed'),
                                        ('101', '101', 'initial-process-remained-running'),
                                        ('101', '102', 'initial-process-remained-running')]:
            with self.subTest(before=before, after=after):
                def command(args):
                    if args[0] == '/bin/ps':
                        return next(snapshots)
                    self.assertEqual(args, ['xcrun', 'simctl', 'terminate', 'owned', ios.BUNDLE])
                    return ''
                snapshots = iter([f'{before} {executable}', f'{after} {executable}' if after else ''])
                with patch.object(ios, 'simulator_app_executable', return_value=executable), \
                        patch.object(ios, 'run', side_effect=command), \
                        patch.object(ios.time, 'monotonic', side_effect=[0, 0, 11]), \
                        patch.object(ios.time, 'sleep'), redirect_stdout(io.StringIO()):
                    if expected is None:
                        ios.terminate_initial_app('owned', '101')
                    else:
                        with self.assertRaises(ios.NativeFailure) as raised:
                            ios.terminate_initial_app('owned', '101')
                        self.assertEqual(raised.exception.reason, expected)

    def test_restore_rejects_reused_process_before_native_driver_launch(self):
        for pid in ('101', '102'):
            with self.subTest(pid=pid), tempfile.TemporaryDirectory() as name:
                temporary = Path(name)
                derived = temporary / 'derived'
                products = derived / 'Build/Products'
                (products / 'BrowserDriver.xctest').mkdir(parents=True)
                (products / 'test.xctestrun').write_bytes(plistlib.dumps({'BrowserDriver': {'BlueprintName': 'BrowserDriver'}}))
                flutter = unittest.mock.Mock()
                flutter.poll.return_value = None
                output = io.StringIO()
                with patch.object(ios.subprocess, 'Popen', side_effect=[flutter, OSError('private detail')]) as launch, \
                        patch.object(ios, 'stop_flutter_process_group'), \
                        patch.object(ios, 'simulator_driver_barrier', return_value=temporary / 'ready'), \
                        patch.object(ios, 'wait_for_flutter_shell'), \
                        patch.object(ios, 'simulator_app_pid', return_value=pid), redirect_stdout(output):
                    with self.assertRaises(ios.NativeFailure) as raised:
                        ios.execute_phase('restore', 'owned', derived, temporary,
                                          {'WEAVE_NATIVE_TEST_RUN_ID': 'test-run'}, {}, previous_pid='101')
                if pid == '101':
                    self.assertEqual(raised.exception.reason, 'restore-process-not-replaced')
                    self.assertEqual(launch.call_count, 1)
                else:
                    self.assertEqual(raised.exception.reason, 'native-tool-unavailable')
                    self.assertEqual(launch.call_count, 2)
                    self.assertIn('status=relaunched distinctProcess=true', output.getvalue())
                self.assertFalse((products / 'private-member.json').exists())
                self.assertNotIn('private detail', output.getvalue())

    def test_exact_executable_identity_excludes_other_apps_devices_and_arguments(self):
        app = Path('/Users/runner/Library/Developer/CoreSimulator/Devices/owned/data/Containers/Bundle/Application/app/Runner.app')
        snapshot = '\n'.join([
            f'101 {app}/Runner',
            f'102 {str(app).replace("/owned/", "/other/")}/Runner',
            f'103 {app.parent}/Other.app/Runner',
            f'104 {app}/Runner --private-input',
            f'invalid {app}/Runner',
        ])
        self.assertEqual(ios.exact_simulator_app_pids(snapshot, app / 'Runner'), {'101'})

    def test_simulator_pid_requires_one_exact_installed_executable(self):
        for count in (0, 1, 2):
            with self.subTest(count=count), tempfile.TemporaryDirectory() as name:
                app = Path(name) / 'CoreSimulator/Devices/owned/data/Containers/Bundle/Application/app/Runner.app'
                app.mkdir(parents=True)
                (app / 'Runner').touch()
                (app / 'Info.plist').write_bytes(plistlib.dumps({'CFBundleIdentifier': ios.BUNDLE, 'CFBundleExecutable': 'Runner'}))
                app = app.resolve()
                snapshot = '\n'.join(f'{101 + index} {app}/Runner' for index in range(count))
                output = io.StringIO()
                with patch.object(ios, 'run', side_effect=[str(app), snapshot]), redirect_stdout(output):
                    if count == 1:
                        self.assertEqual(ios.simulator_app_pid('owned'), '101')
                    else:
                        with self.assertRaises(ios.NativeFailure) as raised:
                            ios.simulator_app_pid('owned')
                        self.assertEqual(raised.exception.reason, 'integration-process-unavailable' if count == 0 else 'integration-process-ambiguous')
                self.assertIn(f'matchingCount={count}', output.getvalue())
                self.assertNotIn(str(app), output.getvalue())

    def test_other_device_or_bundle_identity_is_rejected_before_process_read(self):
        for other_device in (True, False):
            with self.subTest(other_device=other_device), tempfile.TemporaryDirectory() as name:
                device = 'other' if other_device else 'owned'
                app = Path(name) / f'CoreSimulator/Devices/{device}/data/Runner.app'
                app.mkdir(parents=True)
                (app / 'Runner').touch()
                (app / 'Info.plist').write_bytes(plistlib.dumps({'CFBundleIdentifier': 'other.bundle', 'CFBundleExecutable': 'Runner'}))
                with patch.object(ios, 'run', return_value=str(app)) as command:
                    with self.assertRaises(ios.NativeFailure):
                        ios.simulator_app_pid('owned')
                command.assert_called_once()

    def test_attachment_requires_live_unchanged_process_before_readiness(self):
        for exited, retained, reason in [(True, True, 'process-exited-before-attachment'),
                                        (False, False, 'integration-process-replaced')]:
            with self.subTest(reason=reason), tempfile.TemporaryDirectory() as name:
                root = Path(name)
                log = root / 'browser.log'
                log.write_text('NATIVE_IOS_BROWSER_STAGE phase=app-observed\n')
                barrier = root / 'ready'
                flutter, driver = unittest.mock.Mock(), unittest.mock.Mock()
                flutter.poll.return_value = 7 if exited else None
                driver.poll.return_value = None
                with patch.object(ios, 'simulator_app_pid', return_value='123' if retained else '456'):
                    with self.assertRaises(ios.NativeFailure) as raised:
                        ios.release_driver_barrier(flutter, driver, log, 'owned', '123', barrier, 'restore')
                self.assertEqual(raised.exception.reason, reason)
                self.assertFalse(barrier.exists())

    def test_live_existing_process_releases_private_readiness(self):
        with tempfile.TemporaryDirectory() as name:
            root = Path(name)
            log = root / 'browser.log'
            log.write_text('NATIVE_IOS_BROWSER_STAGE phase=app-observed\n')
            barrier = root / 'ready'
            flutter, driver = unittest.mock.Mock(), unittest.mock.Mock()
            flutter.poll.return_value = driver.poll.return_value = None
            with patch.object(ios, 'simulator_app_pid', return_value='123'), redirect_stdout(io.StringIO()):
                ios.release_driver_barrier(flutter, driver, log, 'owned', '123', barrier, 'restore')
            self.assertEqual(barrier.read_bytes(), b'')
            self.assertEqual(barrier.stat().st_mode & 0o777, 0o600)

    def test_dual_attachment_waits_for_native_control_not_merely_live_app(self):
        for marker in ['app-observed', 'sign-in-control-observed']:
            with self.subTest(marker=marker), tempfile.TemporaryDirectory() as name:
                root = Path(name)
                log = root / 'browser.log'
                log.write_text('NATIVE_IOS_BROWSER_STAGE phase=' + marker + '\n')
                barrier = root / 'ready'
                flutter, driver = unittest.mock.Mock(), unittest.mock.Mock()
                flutter.poll.return_value = driver.poll.return_value = None
                with patch.object(ios, 'simulator_app_pid', return_value='123'), redirect_stdout(io.StringIO()):
                    if marker == 'sign-in-control-observed':
                        ios.release_driver_barrier(flutter, driver, log, 'owned', '123', barrier, 'initial',
                                                   timeout=0.1, require_sign_in_observation=True)
                        self.assertEqual(barrier.stat().st_mode & 0o777, 0o600)
                    else:
                        with self.assertRaises(ios.NativeFailure) as failure:
                            ios.release_driver_barrier(flutter, driver, log, 'owned', '123', barrier, 'initial',
                                                       timeout=0.1, require_sign_in_observation=True)
                        self.assertEqual(failure.exception.reason, 'driver-attachment-timeout')
                        self.assertFalse(barrier.exists())

    def test_dual_attachment_rejects_partial_or_private_embedded_observation(self):
        for marker in ['NATIVE_IOS_BROWSER_STAGE phase=sign-in-control-observed',
                       'private NATIVE_IOS_BROWSER_STAGE phase=sign-in-control-observed\n',
                       'NATIVE_IOS_BROWSER_STAGE phase=sign-in-control-observed private\n']:
            with self.subTest(marker=marker), tempfile.TemporaryDirectory() as name:
                root = Path(name); log = root / 'browser.log'; log.write_text(marker)
                flutter, driver = unittest.mock.Mock(), unittest.mock.Mock()
                flutter.poll.return_value = driver.poll.return_value = None
                with self.assertRaises(ios.NativeFailure):
                    ios.release_driver_barrier(flutter, driver, log, 'owned', '123', root / 'ready', 'initial',
                                               timeout=0.1, require_sign_in_observation=True)
                self.assertFalse((root / 'ready').exists())

    def test_attachment_timeout_does_not_release_readiness(self):
        with tempfile.TemporaryDirectory() as name:
            root = Path(name)
            log = root / 'browser.log'
            log.write_text('NATIVE_IOS_BROWSER_STAGE phase=driver-started\n')
            flutter, driver = unittest.mock.Mock(), unittest.mock.Mock()
            flutter.poll.return_value = driver.poll.return_value = None
            with patch.object(ios.time, 'monotonic', side_effect=[0, 1, 91]), patch.object(ios.time, 'sleep'):
                with self.assertRaises(ios.NativeFailure) as raised:
                    ios.release_driver_barrier(flutter, driver, log, 'owned', '123', root / 'ready', 'initial')
            self.assertEqual(raised.exception.reason, 'driver-attachment-timeout')
            self.assertFalse((root / 'ready').exists())

    def test_stale_readiness_is_removed_and_symlink_is_rejected(self):
        with tempfile.TemporaryDirectory() as name:
            container = Path(name)
            support = container / 'Library' / 'Application Support'
            support.mkdir(parents=True)
            stale = support / 'weave-native-driver-test-run.ready'
            stale.touch()
            with patch.object(ios, 'run', return_value=name):
                self.assertEqual(ios.simulator_driver_barrier('owned', 'test-run'), stale.resolve())
                self.assertFalse(stale.exists())
                stale.symlink_to(container / 'elsewhere')
                with self.assertRaises(ios.NativeFailure):
                    ios.simulator_driver_barrier('owned', 'test-run')

    def test_driver_start_without_target_is_not_a_product_regression(self):
        self.assertFalse(ios.browser_product_started(
            'NATIVE_IOS_BROWSER_STAGE phase=driver-started\n'
            'NATIVE_IOS_BROWSER_STAGE phase=app-wait-started\n'))
        self.assertTrue(ios.browser_product_started(
            'NATIVE_IOS_BROWSER_STAGE phase=app-observed\n'))

    def test_xctest_diagnostic_retains_location_without_typed_values(self):
        output = io.StringIO()
        with redirect_stdout(output):
            ios.emit_phase_stages('initial', '',
                'BrowserDriver.swift:41: error: Application not running raw-secret-password\n'
                'BrowserDriver.swift:57: error: assertion raw-private-email\n')
        self.assertIn('line=41 category=target-not-running', output.getvalue())
        self.assertIn('line=57 category=xctest-assertion', output.getvalue())
        self.assertNotIn('raw-secret-password', output.getvalue())
        self.assertNotIn('raw-private-email', output.getvalue())

    def test_flutter_failure_diagnostic_keeps_static_timeout_and_frames_only(self):
        output = io.StringIO()
        with redirect_stdout(output):
            ios.emit_phase_stages('initial',
                'The isolated simulator driver did not attach before authentication.\n'
                'private-member-email private-password private-token\n'
                'package:weave/system_browser_auth_e2e_test.dart:936:3\n'
                'package:private/private_account.dart:42:8\n', '')
        self.assertIn('category=driver-attachment-timeout', output.getvalue())
        self.assertIn('file=system_browser_auth_e2e_test.dart line=936', output.getvalue())
        for value in ('private-member-email', 'private-password', 'private-token', 'private_account.dart'):
            self.assertNotIn(value, output.getvalue())

    def test_browser_surface_is_guarded_without_foreground_stealing(self):
        driver = (Path(__file__).parent / 'ios_browser_driver/BrowserDriver.swift').read_text()
        browser_loop = driver.split('func testProductionBrowser', 1)[1].split('for surface in [app, browser] {', 1)[1]
        self.assertLess(browser_loop.index('guard surfaceState == .runningForeground || surfaceState == .runningBackground'),
                        browser_loop.index('surface.descendants'))
        self.assertEqual(driver.split('func testProductionBrowser', 1)[1].count('.activate()'), 1)
        production = driver.split('func testProductionBrowser', 1)[1]
        self.assertLess(production.index('.activate()'), production.index('while Date() < deadline'))
        self.assertNotIn('.activate()', browser_loop)
        pending_loop = driver.split('func waitForNativeWorkspace', 1)[1].split('func testNativeDocumentPicker', 1)[0]
        self.assertLess(pending_loop.index('guard state == .runningForeground || state == .runningBackground'), pending_loop.index('surface.descendants'))

    def test_ios_failure_cannot_be_presented_as_macos_or_combined_success(self):
        with tempfile.TemporaryDirectory() as name:
            root = Path(name)
            (root / 'specs').mkdir()
            (root / 'specs/weave-specs.lock.json').write_text(json.dumps({'specCorpus': {'gitCommit': 'b' * 40}}))
            results = []
            for code in [0, 7]:
                result = unittest.mock.Mock()
                result.wait.return_value = code
                results.append(result)
            with patch.object(qualification, 'ROOT', root), patch.dict(os.environ, {'WEAVE_TEST_APP_RUN_ROOT': name, 'WEAVE_CANDIDATE_COMMIT': 'a' * 40, 'WEAVE_E2E_RUN_ID': 'owned-run'}), patch.object(qualification.subprocess, 'check_output', side_effect=['a' * 40, '', 'c' * 40]), patch.object(qualification.subprocess, 'Popen', side_effect=results), patch.object(qualification, 'inspect_toolchain', return_value={'frameworkVersion': '3.41.6', 'rustToolchain': '1.95.0'}), patch.object(qualification.shutil, 'which', return_value='/private/rustup/bin/rustup'):
                self.assertEqual(qualification.main(), 7)
            evidence = json.loads((root / 'native-qualification.json').read_text())
            self.assertEqual(evidence['macos']['status'], 'passed')
            self.assertEqual(evidence['iosSimulator']['status'], 'failed')
            self.assertFalse(evidence['cleanupComplete'])

    def test_interrupted_qualification_retains_platform_and_collaboration_results(self):
        for private_result in ('valid', 'forced', 'cleanup-failed', 'malformed', 'unreadable', 'missing'):
            with self.subTest(private_result=private_result), tempfile.TemporaryDirectory() as name:
                root = Path(name)
                (root / 'specs').mkdir()
                (root / 'specs/weave-specs.lock.json').write_text(json.dumps({'specCorpus': {'gitCommit': 'b' * 40}}))
                parent = root / 'client/build/private-ios-qualification'
                parent.mkdir(parents=True)
                result = parent / 'dual-ios-result-owned-run.json'
                handlers = {}
                mac = unittest.mock.Mock()
                mac.wait.return_value = 0
                ios_process = unittest.mock.Mock()
                def interrupted_wait():
                    if private_result in ('valid', 'forced', 'cleanup-failed'):
                        result.write_text(json.dumps({'status': 'failed', 'cleanupComplete': True,
                                                      'scenarios': {'chat': {'status': 'not-run'}}}))
                    elif private_result in ('malformed', 'unreadable'):
                        result.write_text('private-invalid-json')
                    handlers[signal.SIGTERM](signal.SIGTERM, None)
                ios_process.wait.side_effect = interrupted_wait
                def install(number, handler):
                    previous = handlers.get(number, signal.SIG_DFL)
                    handlers[number] = handler
                    return previous
                original_read = Path.read_text
                original_unlink = Path.unlink
                def unlink(path, *args, **kwargs):
                    if path == result and private_result == 'cleanup-failed':
                        raise PermissionError('private-cleanup-payload')
                    return original_unlink(path, *args, **kwargs)
                def read(path, *args, **kwargs):
                    if path == result and private_result == 'unreadable':
                        raise PermissionError('private-payload')
                    return original_read(path, *args, **kwargs)
                with patch.object(qualification, 'ROOT', root), \
                        patch.dict(os.environ, {'WEAVE_TEST_APP_RUN_ROOT': name, 'WEAVE_CANDIDATE_COMMIT': 'a' * 40, 'WEAVE_E2E_RUN_ID': 'owned-run'}), \
                        patch.object(qualification.subprocess, 'check_output', side_effect=['a' * 40, '', 'c' * 40]), \
                        patch.object(qualification.subprocess, 'Popen', side_effect=[mac, ios_process]), \
                        patch.object(qualification, 'inspect_toolchain', return_value={'rustToolchain': '1.95.0'}), \
                        patch.object(qualification.shutil, 'which', return_value='/private/rustup'), \
                        patch.object(qualification.signal, 'signal', side_effect=install), \
                        patch.object(qualification, 'interrupt_native_process', return_value=private_result != 'forced'), \
                        patch.object(Path, 'unlink', unlink), \
                        patch.object(Path, 'read_text', read):
                    with self.assertRaises(SystemExit) as failure:
                        qualification.main()
                self.assertEqual(failure.exception.code, 128 + signal.SIGTERM)
                evidence = json.loads((root / 'native-qualification.json').read_text())
                self.assertEqual(evidence['macos']['status'], 'passed')
                self.assertEqual(evidence['iosSimulator']['status'], 'failed')
                self.assertEqual(evidence['cleanupComplete'], private_result == 'valid')
                self.assertNotIn('private-invalid-json', json.dumps(evidence))
                self.assertNotIn('private-payload', str(failure.exception))
                if private_result in ('valid', 'forced', 'cleanup-failed'):
                    self.assertEqual(evidence['iosSimulator']['collaboration']['scenarios']['chat']['status'], 'not-run')
                else:
                    self.assertEqual(evidence['iosSimulator']['collaboration']['status'], 'not-run')

    def test_interrupted_native_process_is_forced_and_reaped_within_owner_budget(self):
        process = subprocess.Popen([sys.executable, '-c',
            "import signal,time; signal.signal(signal.SIGINT, signal.SIG_IGN); print('ready',flush=True); time.sleep(60)"],
            start_new_session=True, stdout=subprocess.PIPE, text=True)
        try:
            self.assertEqual(process.stdout.readline().strip(), 'ready')
            self.assertFalse(qualification.interrupt_native_process(process, graceful_seconds=0.05, reap_seconds=2))
            self.assertEqual(process.poll(), -signal.SIGKILL)
            with self.assertRaises(ProcessLookupError):
                os.killpg(process.pid, 0)
        finally:
            if process.poll() is None:
                os.killpg(process.pid, signal.SIGKILL)
            process.wait(timeout=2)
            process.stdout.close()

    def test_dirty_source_stops_before_any_native_execution(self):
        with tempfile.TemporaryDirectory() as name:
            root = Path(name)
            (root / 'specs').mkdir()
            (root / 'specs/weave-specs.lock.json').write_text(json.dumps({'specCorpus': {'gitCommit': 'b' * 40}}))
            with patch.object(qualification, 'ROOT', root), patch.dict(os.environ, {'WEAVE_TEST_APP_RUN_ROOT': name, 'WEAVE_CANDIDATE_COMMIT': 'a' * 40}), patch.object(qualification.subprocess, 'check_output', side_effect=['a' * 40, ' M client/source.dart']), patch.object(qualification.subprocess, 'Popen') as execute:
                with self.assertRaisesRegex(RuntimeError, 'clean exact source'):
                    qualification.main()
                execute.assert_not_called()

    def test_candidate_mismatch_stops_before_any_native_execution(self):
        with tempfile.TemporaryDirectory() as name:
            root = Path(name)
            (root / 'specs').mkdir()
            (root / 'specs/weave-specs.lock.json').write_text(json.dumps({'specCorpus': {'gitCommit': 'b' * 40}}))
            with patch.object(qualification, 'ROOT', root), patch.dict(os.environ, {'WEAVE_TEST_APP_RUN_ROOT': name, 'WEAVE_CANDIDATE_COMMIT': 'a' * 40}), patch.object(qualification.subprocess, 'check_output', return_value='d' * 40), patch.object(qualification.subprocess, 'Popen') as execute:
                with self.assertRaisesRegex(RuntimeError, 'exact candidate'):
                    qualification.main()
                execute.assert_not_called()


class PrivateIosCleanupTest(unittest.TestCase):
    def test_run_directory_has_persistent_private_parent_even_after_failure(self):
        with tempfile.TemporaryDirectory() as root:
            client = Path(root)
            clock = [0.0]
            with patch.object(ios, 'CLIENT', client), patch.object(ios.time, 'monotonic', side_effect=lambda: clock[0]), patch.object(ios.time, 'sleep', side_effect=lambda delay: clock.__setitem__(0, clock[0] + delay)):
                with self.assertRaisesRegex(RuntimeError, 'original journey failure'):
                    with ios.private_run_directory() as directory:
                        (directory / 'initial.xcresult').mkdir()
                        self.assertEqual(directory.parent.stat().st_mode & 0o777, 0o700)
                        raise RuntimeError('original journey failure')
                self.assertFalse(directory.exists())
                self.assertTrue(directory.parent.is_dir())
                self.assertEqual(directory.parent.stat().st_mode & 0o777, 0o700)

    def test_cleanup_retries_late_owned_result_recreation_without_touching_sibling(self):
        with tempfile.TemporaryDirectory() as root:
            parent = Path(root)
            parent.chmod(0o700)
            owned = parent / 'owned'
            owned.mkdir()
            sibling = parent / 'other-run'
            sibling.mkdir()
            info = parent.stat()
            clock = [0.0]
            recreated = [False]
            def pause(delay):
                clock[0] += delay
                if clock[0] >= 0.5 and not recreated[0]:
                    (owned / 'initial.xcresult').mkdir(parents=True)
                    recreated[0] = True
            with patch.object(ios.time, 'monotonic', side_effect=lambda: clock[0]), patch.object(ios.time, 'sleep', side_effect=pause):
                ios.cleanup_private_run(owned, (info.st_dev, info.st_ino), quiet_seconds=1)
            self.assertTrue(recreated[0])
            self.assertFalse(owned.exists())
            self.assertTrue(sibling.exists())
            self.assertGreaterEqual(clock[0], 1.75)

    def test_repeated_writer_or_removal_error_fails_cleanup(self):
        with tempfile.TemporaryDirectory() as root:
            parent = Path(root)
            parent.chmod(0o700)
            owned = parent / 'owned'
            owned.mkdir()
            info = parent.stat()
            clock = [0.0]
            with patch.object(ios.time, 'monotonic', side_effect=lambda: clock[0]), patch.object(ios.time, 'sleep', side_effect=lambda delay: clock.__setitem__(0, clock[0] + delay)), patch.object(ios.shutil, 'rmtree', side_effect=PermissionError):
                with self.assertRaisesRegex(ios.NativeFailure, 'owned-run-removal-timeout'):
                    ios.cleanup_private_run(owned, (info.st_dev, info.st_ino), timeout=1)
            self.assertTrue(owned.exists())
            self.assertEqual(parent.stat().st_mode & 0o777, 0o700)

    def test_symlink_owned_path_and_changed_parent_are_rejected(self):
        with tempfile.TemporaryDirectory() as root:
            parent = Path(root)
            parent.chmod(0o700)
            target = parent / 'other-run'
            target.mkdir()
            owned = parent / 'owned'
            owned.symlink_to(target, target_is_directory=True)
            info = parent.stat()
            with self.assertRaisesRegex(ios.NativeFailure, 'unsafe-owned-run-path'):
                ios.cleanup_private_run(owned, (info.st_dev, info.st_ino))
            self.assertTrue(target.exists())
            with self.assertRaisesRegex(ios.NativeFailure, 'private-parent-changed'):
                ios.cleanup_private_run(owned, (info.st_dev, info.st_ino + 1))

    def test_private_parent_symlink_is_rejected_without_changing_target_mode(self):
        with tempfile.TemporaryDirectory() as root:
            client = Path(root)
            (client / 'build').mkdir()
            target = client / 'target'
            target.mkdir(mode=0o755)
            original_mode = target.stat().st_mode & 0o777
            (client / 'build/private-ios-qualification').symlink_to(target, target_is_directory=True)
            with patch.object(ios, 'CLIENT', client), self.assertRaisesRegex(ios.NativeFailure, 'unsafe-private-parent'):
                ios.verified_private_parent()
            self.assertEqual(target.stat().st_mode & 0o777, original_mode)

    def test_successful_body_cannot_hide_cleanup_failure(self):
        with tempfile.TemporaryDirectory() as root:
            with patch.object(ios, 'CLIENT', Path(root)), patch.object(ios, 'cleanup_private_run', side_effect=ios.NativeFailure('infrastructure', 'artifact-cleanup', 'owned-run-removal-timeout')):
                with self.assertRaisesRegex(ios.NativeFailure, 'owned-run-removal-timeout'):
                    with ios.private_run_directory() as directory:
                        self.assertTrue(directory.exists())
                self.assertEqual(directory.parent.stat().st_mode & 0o777, 0o700)


if __name__ == '__main__':
    unittest.main()
