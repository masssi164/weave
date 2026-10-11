#!/usr/bin/env python3
"""Regressions for evidence attribution and native qualification failure paths."""
import json
import io
from contextlib import redirect_stdout
import os
import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parent))
import run_ios_simulator_product_acceptance as ios
import run_native_qualification as qualification


class NativeQualificationTest(unittest.TestCase):
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
                    result.poll.return_value = None
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
        json.dump(json.load(source), output)
else:
    sys.exit(2)
""")
            flutter.chmod(0o755)
            for target, device, expected in [('physical-device-product-e2e', 'phone', 'false'),
                                             ('physical-device-product-e2e', 'macos', 'false'),
                                             ('ios-simulator-product-e2e', 'owned-sim', 'true')]:
                with self.subTest(target=target, device=device):
                    env = os.environ.copy()
                    env.update({'PATH': str(temporary) + os.pathsep + env['PATH'],
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

    def test_browser_surface_is_guarded_without_foreground_stealing(self):
        driver = (Path(__file__).parent / 'ios_browser_driver/BrowserDriver.swift').read_text()
        browser_loop = driver.split('for surface in [app, browser] {', 1)[1]
        self.assertLess(browser_loop.index('guard surfaceState == .runningForeground || surfaceState == .runningBackground'),
                        browser_loop.index('surface.descendants'))
        self.assertEqual(driver.count('.activate()'), 1)
        self.assertLess(driver.index('.activate()'), driver.index('while Date() < deadline'))
        self.assertNotIn('.activate()', browser_loop)

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
