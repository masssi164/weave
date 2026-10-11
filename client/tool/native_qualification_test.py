#!/usr/bin/env python3
"""Regressions for evidence attribution and native qualification failure paths."""
import json
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
                with self.assertRaisesRegex(RuntimeError, 'Apple Silicon'):
                    ios.select_runtime()

    def test_runtime_does_not_silently_select_another_device(self):
        entry = {'identifier': ios.RUNTIME, 'isAvailable': True, 'supportedArchitectures': ['arm64'], 'supportedDeviceTypes': [{'identifier': 'other'}]}
        with patch.object(ios, 'run', return_value=json.dumps({'runtimes': [entry]})), patch.dict(os.environ, {}, clear=True):
            with self.assertRaisesRegex(RuntimeError, 'device type'):
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
