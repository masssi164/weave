#!/usr/bin/env python3
"""Private actor ownership and mandatory real-proof contract regressions."""
import hashlib
import json
import os
from pathlib import Path
import sys
import tempfile
import unittest
from unittest import mock

sys.path.insert(0, str(Path(__file__).resolve().parent))
import run_dual_ios_collaboration as dual
import run_ios_simulator_product_acceptance as ios


class DualIosCollaborationTest(unittest.TestCase):
    def test_private_json_publish_is_atomic_owner_only_and_never_overwrites(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'denial.json'
            content = {'status': 'passed', 'denials': {'files': 'passed'}}
            dual.publish_private_json(path, content)
            self.assertEqual(json.loads(path.read_text()), content)
            self.assertEqual(path.stat().st_mode & 0o777, 0o600)
            self.assertFalse(path.with_suffix('.tmp').exists())
            with self.assertRaises(FileExistsError): dual.publish_private_json(path, {'status': 'changed'})
            self.assertEqual(json.loads(path.read_text()), content)
            path.unlink(); path.symlink_to(Path(directory) / 'foreign')
            with self.assertRaises(FileExistsError): dual.publish_private_json(path, content)
            self.assertFalse((Path(directory) / 'foreign').exists())
            path.unlink(); path.with_suffix('.tmp').symlink_to(Path(directory) / 'foreign')
            with self.assertRaises(FileExistsError): dual.publish_private_json(path, content)

    def test_picker_requests_bind_real_action_sequence_and_known_owned_file(self):
        for action, mode, prefix in [('files-upload', 'import', 'initial'), ('files-replace', 'import', 'replacement'),
                                     ('files-export', 'export', 'initial'), ('files-export-updated', 'export', 'initial')]:
            request = {'sequence': 4, 'mode': mode, 'fileName': 'weave-dual-' + prefix + '-owned.txt'}
            self.assertEqual(dual.require_picker_request(request, 4, action, 'owned'), (mode, request['fileName']))
            for changed in [dict(request, sequence=5), dict(request, mode='foreign'),
                            dict(request, fileName='../private.json'), dict(request, secret='private')]:
                with self.assertRaises(ios.NativeFailure): dual.require_picker_request(changed, 4, action, 'owned')
            with self.assertRaises(ios.NativeFailure): dual.require_picker_request(request, 4, 'files-open', 'owned')

    def test_result_path_cannot_overwrite_private_input_stale_or_foreign_output(self):
        with tempfile.TemporaryDirectory() as directory:
            parent = Path(directory)
            owned = parent / 'dual-ios-result-owned-run.json'
            dual.verify_result_path(owned, parent, 'owned-run')
            for path in [parent / 'dual-ios-input-owned-run.json', parent / 'dual-ios-result-other-run.json',
                         parent / 'foreign' / owned.name]:
                with self.assertRaises(ios.NativeFailure): dual.verify_result_path(path, parent, 'owned-run')
            owned.write_text('{}')
            with self.assertRaises(ios.NativeFailure): dual.verify_result_path(owned, parent, 'owned-run')
            owned.unlink(); owned.symlink_to(parent / 'absent')
            with self.assertRaises(ios.NativeFailure): dual.verify_result_path(owned, parent, 'owned-run')

    def test_cleanup_attempts_both_devices_and_fails_closed_on_one_error(self):
        devices = ['owned-A', 'owned-B']
        deleted = []
        def simulator(arguments, **_kwargs):
            if arguments[2] == 'delete':
                deleted.append(arguments[3])
                if arguments[3] == 'owned-A':
                    raise ios.NativeFailure('infrastructure', 'cleanup', 'cannot-remove')
                return ''
            return json.dumps({'devices': {'runtime': [{'udid': 'owned-A'}]}})
        with mock.patch.object(dual, 'stop_owned_processes', side_effect=RuntimeError('inaccessible group')), \
             mock.patch.object(dual.subprocess, 'run') as shutdown, mock.patch.object(ios, 'run', side_effect=simulator):
            self.assertFalse(dual.cleanup_owned_devices(devices))
        self.assertEqual(deleted, devices)
        self.assertEqual(shutdown.call_count, 2)

    def test_cleanup_requires_absence_and_verified_process_teardown(self):
        for remaining, expected in [([], True), ([{'udid': 'owned-A'}], False)]:
            with mock.patch.object(dual, 'stop_owned_processes'), mock.patch.object(dual.subprocess, 'run'), \
                 mock.patch.object(ios, 'run', side_effect=['', json.dumps({'devices': {'runtime': remaining}})]):
                self.assertEqual(dual.cleanup_owned_devices(['owned-A']), expected)

    def test_cancellation_stops_every_tracked_group_and_rejects_new_launch(self):
        dual._CANCELLED.clear()
        with dual._ACTIVE_LOCK: dual._ACTIVE.clear()
        first, second, late = object(), object(), object()
        dual.track_process(first); dual.track_process(second)
        with mock.patch.object(dual, 'stop_flutter_process_group', side_effect=[RuntimeError('inaccessible group'), None, None]) as stop:
            with self.assertRaises(ios.NativeFailure): dual.stop_owned_processes()
            with self.assertRaises(ios.NativeFailure): dual.track_process(late)
            self.assertEqual([call.args[0] for call in stop.call_args_list], [first, second, late])
        dual._CANCELLED.clear()
        with dual._ACTIVE_LOCK: dual._ACTIVE.clear()

    def test_private_handoff_requires_independent_actors_actual_completion_and_permissions(self):
        with tempfile.TemporaryDirectory() as directory:
            parent = Path(directory); parent.chmod(0o700)
            path = parent / 'fixture.json'
            value = {'schemaVersion': 'weave.dual-ios-input/v1', 'runId': 'owned-run',
                     'publicOrigin': 'https://weave.localhost', 'actors': [
                         {'role': 'A', 'email': 'a@example.invalid', 'password': 'private-a', 'identityRef': 'a', 'entryUri': 'weave.localhost'},
                         {'role': 'B', 'email': 'b@example.invalid', 'password': 'private-b', 'identityRef': 'b', 'entryUri': 'weave://join?organization_origin=https%3A%2F%2Fweave.localhost'}]}
            def write():
                path.write_text(json.dumps(value));path.chmod(0o600)
            write()
            self.assertEqual(dual.private_input(path, 'owned-run'), value)
            for field, bad in [('identityRef', 'a'), ('entryUri', ''), ('role', 'A'),
                               ('entryUri', 'weave://join?organization_origin=https%3A%2F%2Fforeign.example'),
                               ('entryUri', value['actors'][1]['entryUri'] + '&private_token=private'),
                               ('entryUri', value['actors'][1]['entryUri'] + '#foreign')]:
                old = value['actors'][1][field];value['actors'][1][field] = bad;write()
                with self.assertRaises(ios.NativeFailure): dual.private_input(path, 'owned-run')
                value['actors'][1][field] = old
            write();path.chmod(0o644)
            with self.assertRaises(ios.NativeFailure): dual.private_input(path, 'owned-run')
            path.chmod(0o600)
            with self.assertRaises(ios.NativeFailure): dual.private_input(path, 'other-run')
            alias = parent / 'alias';alias.symlink_to(path)
            with self.assertRaises(ios.NativeFailure): dual.private_input(alias, 'owned-run')

    def test_actual_actor_proof_rejects_partial_api_only_cross_identity_and_plaintext(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'result.json'
            value = {'role': 'A', 'identityDigest': hashlib.sha256(b'actual-subject').hexdigest(),
                     'storageDigest': 'a' * 64, 'matrixDeviceDigest': 'b' * 64,
                     'scenarios': {scenario: {'status': 'passed', 'evidence': 'server-denial' if scenario == 'authorization' else 'flutter-ui'} for scenario in dual.SCENARIOS},
                     'encryption': {'policyRequired': True, 'status': 'passed', 'plaintextFallback': False}}
            value['scenarios']['authorization'].update({'deniedIdentityDigest': 'c' * 64,
                                                       'denials': {'chat': 'passed', 'files': 'passed', 'calendar': 'passed'}})
            def write():path.write_text(json.dumps(value))
            write();self.assertEqual(dual.actor_result(path, 'A', 'actual-subject', 'a' * 64), value)
            for scenario in dual.SCENARIOS:
                original = value['scenarios'][scenario]
                for invalid in ({'status': 'not-run'}, {'status': 'passed', 'evidence': 'api'}, {'status': 'failed', 'evidence': 'flutter-ui'}):
                    value['scenarios'][scenario] = invalid;write()
                    with self.assertRaises(ios.NativeFailure):dual.actor_result(path, 'A', 'actual-subject', 'a' * 64)
                value['scenarios'][scenario] = original
            write()
            for role, identity, storage in [('B', 'actual-subject', 'a' * 64), ('A', 'other-subject', 'a' * 64), ('A', 'actual-subject', 'c' * 64)]:
                with self.assertRaises(ios.NativeFailure):dual.actor_result(path, role, identity, storage)
            value['encryption']['plaintextFallback'] = True;write()
            with self.assertRaises(ios.NativeFailure):dual.actor_result(path, 'A', 'actual-subject', 'a' * 64)
            value['encryption'] = {'policyRequired': False, 'status': 'not-required', 'plaintextFallback': False};write()
            dual.actor_result(path, 'A', 'actual-subject', 'a' * 64)

    def test_same_binary_digest_binds_content_and_paths_rejects_symlinks(self):
        with tempfile.TemporaryDirectory() as directory:
            app = Path(directory) / 'Runner.app';app.mkdir()
            binary = app / 'Runner';binary.write_bytes(b'actual app bytes')
            before = dual.app_build_digest(app)
            binary.write_bytes(b'changed actual app bytes')
            self.assertNotEqual(before, dual.app_build_digest(app))
            binary.rename(app / 'OtherExecutable')
            self.assertNotEqual(before, dual.app_build_digest(app))
            (app / 'alias').symlink_to(app / 'OtherExecutable')
            with self.assertRaises(ios.NativeFailure):dual.app_build_digest(app)

    def test_independent_attach_metadata_copies_no_native_build_cache(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory);client = root / 'client';metadata = client / '.dart_tool'
            metadata.mkdir(parents=True)
            (metadata / 'package_config.json').write_text('{}')
            (metadata / 'hooks_runner').mkdir();(metadata / 'hooks_runner/private').write_text('shared native build')
            (client / 'build').mkdir();(client / 'build/private-secret').write_text('must stay private')
            (client / 'test_driver').mkdir();(client / 'test_driver/integration_test.dart').write_text('driver')
            for role in ('A', 'B'):dual.actor_runtime_copy(client, root / role)
            (root / 'A/.dart_tool/package_config.json').write_text('mutated A metadata')
            self.assertEqual((root / 'B/.dart_tool/package_config.json').read_text(), '{}')
            self.assertEqual((metadata / 'package_config.json').read_text(), '{}')
            for role in ('A', 'B'):
                self.assertFalse((root / role / 'build').exists())
                self.assertFalse((root / role / '.dart_tool/hooks_runner').exists())


if __name__ == '__main__':unittest.main()
