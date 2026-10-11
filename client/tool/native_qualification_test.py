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
        for started, expected_category in [(False, 'infrastructure'), (True, 'integration')]:
            with self.subTest(started=started), tempfile.TemporaryDirectory() as name:
                temporary = Path(name)
                derived = temporary / 'derived'
                products = derived / 'Build/Products'
                bundle = products / 'BrowserDriver.xctest'
                bundle.mkdir(parents=True)
                (products / 'BrowserDriver.xctestrun').write_text('plan')
                def launch(arguments, **kwargs):
                    result = unittest.mock.Mock()
                    result.wait.return_value = 1 if arguments[0] == 'xcodebuild' else 0
                    log = kwargs['stdout']
                    if arguments[0] == 'xcodebuild':
                        if started:
                            log.write('NATIVE_IOS_BROWSER_STAGE phase=driver-started\n')
                            log.write('NATIVE_IOS_BROWSER_STAGE phase=issuer-observed\n')
                        log.write('raw-secret-password raw-private-email\n')
                    else:
                        log.write('NATIVE_PRODUCT_STAGE phase=sign-in-tapped token=raw-secret-password\n')
                        log.write('NATIVE_PRODUCT_STAGE phase=raw-secret-password\n')
                    log.flush()
                    return result
                output = io.StringIO()
                with redirect_stdout(output), patch.object(ios.subprocess, 'Popen', side_effect=launch), patch.object(ios, 'stop_flutter_process_group'):
                    with self.assertRaises(ios.NativeFailure) as raised:
                        ios.execute_phase('initial', 'owned-simulator', derived, temporary, {}, {'email': 'raw-private-email', 'password': 'raw-secret-password'})
                self.assertEqual(raised.exception.category, expected_category)
                self.assertIn('category=' + expected_category, output.getvalue())
                self.assertIn('NATIVE_PRODUCT_STAGE phase=sign-in-tapped', output.getvalue())
                self.assertNotIn('raw-secret-password', output.getvalue())
                self.assertNotIn('raw-private-email', output.getvalue())
                self.assertFalse((bundle / 'private-member.json').exists())

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
            with patch.object(qualification, 'ROOT', root), patch.dict(os.environ, {'WEAVE_TEST_APP_RUN_ROOT': name, 'WEAVE_CANDIDATE_COMMIT': 'a' * 40}), patch.object(qualification.subprocess, 'check_output', side_effect=['a' * 40, '', 'c' * 40]), patch.object(qualification.subprocess, 'Popen', side_effect=results):
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
