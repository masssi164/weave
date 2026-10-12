#!/usr/bin/env python3
"""Regressions for evidence attribution and native qualification failure paths."""
import json
import io
from contextlib import redirect_stdout
import os
import plistlib
import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parent))
import run_ios_simulator_product_acceptance as ios
import run_native_qualification as qualification


class NativeQualificationTest(unittest.TestCase):
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
            ios.emit_phase_stages('initial', safe + '\n' + safe, '')
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
                (products / 'BrowserDriver.xctestrun').write_text('plan')
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
                self.assertFalse((bundle / 'private-member.json').exists())
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
        self.assertIn('prompt.waitForNonExistence(timeout: 10)', handler)
        self.assertNotIn('.firstMatch', handler)
        self.assertNotIn('app.activate()', handler)
        self.assertIn('static let passwordDeclineLabels = ["Später", "Not Now"]', driver)
        wait = driver.split('func waitForNativeWorkspace', 1)[1].split('func testProductionBrowser', 1)[0]
        self.assertLess(wait.index('declinePasswordSavePrompt'), wait.index('Self.nativeWorkspaceObserved'))
        output = io.StringIO()
        with redirect_stdout(output):
            ios.emit_phase_stages('initial', '', 'NATIVE_IOS_BROWSER_STAGE phase=password-save-declined')
        self.assertIn('NATIVE_IOS_BROWSER_STAGE phase=password-save-declined supportSafe=true', output.getvalue())

    def test_ios_browser_pass_waits_for_real_workspace_signal(self):
        driver = (Path(__file__).parent / 'ios_browser_driver/BrowserDriver.swift').read_text()
        submitted = driver.split('phase=password-submitted', 1)[1]
        self.assertLess(submitted.index('guard waitForNativeWorkspace'), submitted.index('status=passed phase=form-submitted'))
        self.assertIn('if Self.nativeWorkspaceObserved(path: fixture.nativeAuthCompletionPath)', driver)
        self.assertIn('phase=auth-completion-observed', driver)
        self.assertNotIn('app.activate()', driver.split('func waitForNativeWorkspace', 1)[1].split('func testProductionBrowser', 1)[0])

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
                (products / 'BrowserDriver.xctestrun').write_text('plan')
                flutter = unittest.mock.Mock()
                flutter.poll.return_value = exit_code
                clock = iter([0, 1, 901])
                output = io.StringIO()
                with redirect_stdout(output), patch.object(ios.subprocess, 'Popen', return_value=flutter) as launch, \
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
                self.assertFalse((bundle / 'private-member.json').exists())
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
elif sys.argv[1] == 'test':
    arg = next(item for item in sys.argv if item.startswith('--dart-define-from-file='))
    with open(arg.split('=', 1)[1]) as source, open(os.environ['HANDOFF_PROBE_OUTPUT'], 'w') as output:
        values=json.load(source)
        values['_probe_reporter']=sys.argv[sys.argv.index('--reporter')+1] if '--reporter' in sys.argv else 'default-buffered-github'
        json.dump(values, output)
else:
    sys.exit(2)
""")
            flutter.chmod(0o755)
            for target, device, expected in [('physical-device-auth-e2e', 'phone', 'false'),
                                             ('physical-device-product-e2e', 'phone', 'false'),
                                             ('physical-device-product-e2e', 'macos', 'false'),
                                             ('ios-simulator-product-e2e', 'owned-sim', 'true')]:
                with self.subTest(target=target, device=device):
                    env = os.environ.copy()
                    env.update({'PATH': str(temporary) + os.pathsep + env['PATH'],
                                'CI': 'true', 'GITHUB_ACTIONS': 'true', 'TERM': 'dumb',
                                'WEAVE_PHYSICAL_DEVICE_ID': device, 'WEAVE_IOS_SIMULATOR_ID': 'owned-sim',
                                'WEAVE_NATIVE_EXPECT_SIMULATOR': 'true', 'WEAVE_NATIVE_TEST_RUN_ID': 'existing-run-id',
                                'WEAVE_DEVICE_DISPOSABLE_STACK': 'true',
                                'WEAVE_DEVICE_API_BASE_URL': 'https://api.weave.localhost/api',
                                'WEAVE_DEVICE_OIDC_ISSUER_URL': 'https://auth.weave.localhost/realms/weave',
                                'HANDOFF_PROBE_OUTPUT': str(temporary / 'defines.json')})
                    result = ios.subprocess.run(['make', target], cwd=Path(__file__).resolve().parents[1], env=env,
                                                capture_output=True, text=True)
                    self.assertEqual(result.returncode, 0, result.stderr)
                    defines = json.loads((temporary / 'defines.json').read_text())
                    self.assertEqual(defines['WEAVE_NATIVE_EXPECT_SIMULATOR'], expected)
                    self.assertEqual(defines['WEAVE_NATIVE_TEST_RUN_ID'], 'existing-run-id')
                    self.assertEqual(defines['_probe_reporter'], 'expanded')

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
        self.assertEqual(driver.count('.activate()'), 1)
        production = driver.split('func testProductionBrowser', 1)[1]
        self.assertLess(production.index('.activate()'), production.index('while Date() < deadline'))
        self.assertNotIn('.activate()', browser_loop)
        pending_loop = driver.split('func waitForNativeWorkspace', 1)[1].split('func testProductionBrowser', 1)[0]
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
            with patch.object(qualification, 'ROOT', root), patch.dict(os.environ, {'WEAVE_TEST_APP_RUN_ROOT': name, 'WEAVE_CANDIDATE_COMMIT': 'a' * 40}), patch.object(qualification.subprocess, 'check_output', side_effect=['a' * 40, '', 'c' * 40]), patch.object(qualification.subprocess, 'Popen', side_effect=results), patch.object(qualification, 'inspect_toolchain', return_value={'frameworkVersion': '3.41.6', 'rustToolchain': '1.95.0'}), patch.object(qualification.shutil, 'which', return_value='/private/rustup/bin/rustup'):
                self.assertEqual(qualification.main(), 7)
            evidence = json.loads((root / 'native-qualification.json').read_text())
            self.assertEqual(evidence['macos']['status'], 'passed')
            self.assertEqual(evidence['iosSimulator']['status'], 'failed')
            self.assertFalse(evidence['cleanupComplete'])

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


if __name__ == '__main__':
    unittest.main()
