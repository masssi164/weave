#!/usr/bin/env python3
"""Private actor ownership and mandatory real-proof contract regressions."""
import hashlib
import json
import os
from pathlib import Path
import sys
import socket
import shutil
import subprocess
import threading
import tempfile
import unittest
from unittest import mock

sys.path.insert(0, str(Path(__file__).resolve().parent))
import run_dual_ios_collaboration as dual
import run_ios_simulator_product_acceptance as ios


class DualIosCollaborationTest(unittest.TestCase):
    @unittest.skipUnless(shutil.which('flutter'), 'Flutter CLI is required for real argument-parser validation')
    def test_actual_flutter_drive_parser_accepts_both_phase_host_arguments(self):
        # --help parses the actual command without launching, attaching or building.
        command = dual.flutter_drive_command('http://127.0.0.1:12345/owned/', 'owned-disposable-simulator')
        accepted = subprocess.run([*command, '--help'], capture_output=True, text=True, timeout=30)
        self.assertEqual(accepted.returncode, 0)
        rejected = subprocess.run([*command, '--reporter', 'expanded', '--help'],
                                  capture_output=True, text=True, timeout=30)
        self.assertNotEqual(rejected.returncode, 0)
        self.assertIn('Could not find an option named "--reporter".', rejected.stderr)

    def test_coordinator_missing_real_ui_ack_still_fails_at_action_deadline(self):
        # An idle peer may remain alive, but no action gets extra proof time.
        for phase, expected_action in [('initial', 'chat-create-send'), ('restore', 'restore-readback')]:
            with self.subTest(phase=phase), tempfile.TemporaryDirectory() as temporary:
                root = Path(temporary)
                scopes = {}
                for role, pid in [('A', '123'), ('B', '456')]:
                    support = root / role
                    support.mkdir()
                    scopes[role] = (support, role, pid, support)
                task = mock.Mock()
                task.done.return_value = False
                with mock.patch.object(dual.ios, 'simulator_app_pid', side_effect=lambda role: {'A': '123', 'B': '456'}[role]), \
                     mock.patch.object(dual.time, 'monotonic', side_effect=[0, 179.999, 180]), \
                     mock.patch.object(dual.time, 'sleep'):
                    with self.assertRaises(ios.NativeFailure) as failure:
                        dual.run_ui_commands(scopes, [task], {}, {}, phase=phase)
                self.assertEqual(failure.exception.reason, 'actual-ui-command-proof-missing')
                command = json.loads((scopes['A'][0] / 'weave-dual-ios-command.json').read_text())
                self.assertEqual(command['action'], expected_action)
                self.assertFalse((scopes['B'][0] / 'weave-dual-ios-command.json').exists())
                task.result.assert_not_called()

    def test_restart_proof_requires_distinct_processes_real_flags_and_safe_intervals(self):
        proof = {'role': 'B', 'identityDigest': 'a' * 64, 'matrixDeviceDigest': 'b' * 64, 'storageDigest': 'c' * 64,
                 'recovery': {key: True for key in ['network', 'background', 'processRestored', 'persistentReadback', 'expiryRefresh', 'logoutIndependent', 'grantRegrantReadback']}}
        proof['recovery'].update({'privateToken': 'must-not-export', 'organizationOrigin': 'private-unused'})
        times = ['2026-10-12T10:00:00+00:00', '2026-10-12T10:05:00+00:00', '2026-10-12T10:06:00+00:00', '2026-10-12T10:10:00+00:00']
        result = dual.recovery_actor_proof(proof, '123', '456', *times)
        self.assertEqual(result['restoredProcessId'], 456)
        self.assertNotIn('must-not-export', json.dumps(result))
        for name in ['network', 'background', 'processRestored', 'persistentReadback', 'expiryRefresh', 'logoutIndependent', 'grantRegrantReadback']:
            for unavailable in [False, 1, None, 'true']:
                proof['recovery'][name] = unavailable
                with self.assertRaises(ios.NativeFailure): dual.recovery_actor_proof(proof, '123', '456', *times)
            proof['recovery'][name] = True
        for old, restored in [('123', '123'), ('0', '456'), ('123', '-1')]:
            with self.assertRaises(ios.NativeFailure): dual.recovery_actor_proof(proof, old, restored, *times)
        for invalid_times in [[times[1], times[0], times[2], times[3]], [times[0][:-6], *times[1:]], [times[0], times[1], times[1], times[1]]]:
            with self.assertRaises(ios.NativeFailure): dual.recovery_actor_proof(proof, '123', '456', *invalid_times)

    def test_actor_proxy_is_private_per_process_and_cannot_select_foreign_authority(self):
        first, second = mock.Mock(port=1234), mock.Mock(port=4321)
        inherited = {'HTTP_PROXY': 'inherited-host-proxy', 'NO_PROXY': '.localhost', 'ordinary': 'kept'}
        a, b = dual.actor_proxy_environment(inherited, first), dual.actor_proxy_environment(inherited, second)
        self.assertEqual(a['SIMCTL_CHILD_https_proxy'], 'http://127.0.0.1:1234')
        self.assertEqual(b['SIMCTL_CHILD_HTTPS_PROXY'], 'http://127.0.0.1:4321')
        self.assertEqual(a['SIMCTL_CHILD_NO_PROXY'], '')
        self.assertEqual(inherited, {'HTTP_PROXY': 'inherited-host-proxy', 'NO_PROXY': '.localhost', 'ordinary': 'kept'})
        env = {'WEAVE_NATIVE_API_BASE_URL': 'https://api.weave.localhost/api', 'WEAVE_NATIVE_MATRIX_URL': 'https://api.weave.localhost'}
        self.assertEqual(dual.proxy_authorities({'publicOrigin': 'https://weave.localhost'}, env), {'weave.localhost:443', 'api.weave.localhost:443'})
        for value in ['http://api.weave.localhost', 'https://foreign.invalid', 'https://credential@api.weave.localhost']:
            with self.assertRaises(ios.NativeFailure):
                dual.proxy_authorities({'publicOrigin': 'https://weave.localhost'}, dict(env, WEAVE_NATIVE_MATRIX_URL=value))

    def test_owned_transport_forwards_bytes_and_fault_is_independent(self):
        listener = socket.socket(); listener.bind(('127.0.0.1', 0)); listener.listen(8)
        listener.settimeout(0.25)
        stop = threading.Event(); handlers = []
        def echo(connection):
            with connection:
                connection.settimeout(3)
                try:
                    while packet := connection.recv(4096): connection.sendall(packet)
                except OSError: pass
        def accept():
            while not stop.is_set():
                try: connection, _ = listener.accept()
                except socket.timeout: continue
                except OSError: break
                thread = threading.Thread(target=echo, args=(connection,), daemon=True)
                handlers.append(thread); thread.start()
        server = threading.Thread(target=accept, daemon=True); server.start()
        authority = '127.0.0.1:' + str(listener.getsockname()[1])
        a, b = dual.OwnedFaultRelay({authority}), dual.OwnedFaultRelay({authority})
        sockets = []
        def connect(relay, target=authority):
            connection = socket.create_connection(('127.0.0.1', relay.port), timeout=3)
            sockets.append(connection)
            connection.sendall(('CONNECT ' + target + ' HTTP/1.1\r\nHost: ' + target + '\r\n\r\n').encode())
            return connection
        try:
            first, second = connect(a), connect(b)
            for connection in (first, second): self.assertIn(b'200 Connection Established', connection.recv(4096))
            payload = b'\x16\x03\x03opaque-encrypted-record\x00'
            first.sendall(payload); self.assertEqual(first.recv(4096), payload)
            a.interrupt()
            self.assertEqual(first.recv(4096), b'')
            rejected = connect(a); self.assertEqual(rejected.recv(4096), b'')
            second.sendall(payload); self.assertEqual(second.recv(4096), payload)
            a.restore(); restored = connect(a)
            self.assertIn(b'200 Connection Established', restored.recv(4096))
            restored.sendall(payload); self.assertEqual(restored.recv(4096), payload)
            foreign = connect(b, 'foreign.invalid:443'); self.assertEqual(foreign.recv(4096), b'')
            self.assertEqual(b.rejected, 1)
            self.assertGreater(a.blocked, 0)
            self.assertEqual(a.forwarded, 2); self.assertEqual(b.forwarded, 1)
            self.assertIn('forwardedBytes=', a.diagnostic('A'))
            self.assertNotIn(authority, a.diagnostic('A'))
            self.assertIn('rejected=1', b.diagnostic('B'))
        finally:
            for connection in sockets: connection.close()
            a.close(); b.close(); stop.set(); listener.close(); server.join(timeout=3)
            for handler in handlers: handler.join(timeout=3)
        self.assertFalse(server.is_alive())

    def test_refused_upstream_records_only_a_fixed_error_counter(self):
        reserved = socket.socket()
        reserved.bind(('127.0.0.1', 0))
        authority = '127.0.0.1:' + str(reserved.getsockname()[1])
        reserved.close()
        relay = dual.OwnedFaultRelay({authority})
        try:
            with socket.create_connection(('127.0.0.1', relay.port), timeout=3) as connection:
                connection.sendall(('CONNECT ' + authority + ' HTTP/1.1\r\n\r\n').encode())
                self.assertEqual(connection.recv(4096), b'')
            record = relay.diagnostic('A')
            self.assertIn('upstream-connect-error=1', record)
            self.assertIn('connections=0', record)
            self.assertNotIn(authority, record)
            self.assertNotIn('Connection refused', record)
            with self.assertRaises(ValueError): relay.diagnostic('private-actor')
        finally:
            relay.close()

    def test_failure_room_transfer_is_private_owned_and_survives_until_host_collection(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory).resolve()
            root.chmod(0o700)
            support = root / 'support'; support.mkdir(mode=0o700)
            input_path = root / 'input.json'
            input_data = {'runId': 'owned-test', 'actors': [
                {'role': 'A', 'identityRef': 'actor-A'}, {'role': 'B', 'identityRef': 'actor-B'}]}
            result = support / 'weave-dual-ios-result.json'
            payload = {'role': 'A', 'identityDigest': hashlib.sha256(b'actor-A').hexdigest(),
                       'resources': {'roomId': '!owned:weave.test', 'roomTitle': 'private-displayed-name'},
                       'privateError': 'private-message-must-not-transfer'}
            result.write_text(json.dumps(payload)); result.chmod(0o600)
            scopes = {'A': (support, 'owned-device', '123', support)}
            dual.retain_failure_room_reference(scopes, input_path, input_data, 'a' * 40)
            snapshot = root / 'dual-ios-chat-failure-owned-test.json'
            self.assertEqual(snapshot.stat().st_mode & 0o777, 0o600)
            result.unlink()  # Owned app storage can now be deleted independently.
            actual = json.loads(snapshot.read_text())
            self.assertEqual(actual['roomId'], '!owned:weave.test')
            self.assertEqual(actual['candidateCommit'], 'a' * 40)
            self.assertNotIn('password', snapshot.read_text())
            self.assertNotIn('private-displayed-name', snapshot.read_text())
            self.assertNotIn('private-message-must-not-transfer', snapshot.read_text())
            with self.assertRaises(FileExistsError):
                result.write_text(json.dumps(payload)); result.chmod(0o600)
                dual.retain_failure_room_reference(scopes, input_path, input_data, 'a' * 40)
            self.assertEqual(json.loads(snapshot.read_text()), actual)
            snapshot.unlink()  # Existing host collector consumes then deletes it.
            result.write_text(json.dumps(payload)); result.chmod(0o600)
            for mutation in [dict(payload, role='B'), dict(payload, identityDigest='b' * 64),
                             dict(payload, resources={'roomId': '../private'})]:
                result.write_text(json.dumps(mutation))
                with self.assertRaises(ValueError):
                    dual.retain_failure_room_reference(scopes, input_path, input_data, 'a' * 40)
                self.assertFalse(snapshot.exists())
            result.write_text(json.dumps(payload)); result.chmod(0o644)
            with self.assertRaises(ValueError):
                dual.retain_failure_room_reference(scopes, input_path, input_data, 'a' * 40)
            result.unlink(); result.symlink_to(input_path)
            with self.assertRaises(ValueError):
                dual.retain_failure_room_reference(scopes, input_path, input_data, 'a' * 40)
            self.assertFalse(snapshot.exists())

    def test_failed_actor_retains_private_relay_counters_before_cleanup(self):
        with tempfile.TemporaryDirectory() as directory:
            client = Path(directory) / 'client'
            client.mkdir()
            output = Path(directory) / 'failed-result.json'
            failure = ios.NativeFailure('integration', 'initial-product', 'owned-test-failure')
            actors = [{'role': 'A'}, {'role': 'B'}]
            def fail_diagnostic_output(*args, **kwargs):
                if args and str(args[0]).startswith('NATIVE_IOS_CHAT_FAILURE_SNAPSHOT'):
                    raise BrokenPipeError('private-output-error-must-not-replace-failure')
            with mock.patch.object(ios, 'CLIENT', client), \
                 mock.patch.object(dual, 'private_input', return_value={'runId': 'owned-test', 'actors': actors}), \
                 mock.patch.object(ios, 'select_runtime', return_value=('runtime', 'device')), \
                 mock.patch.object(ios, 'run', return_value='11111111-1111-1111-1111-111111111111'), \
                 mock.patch.object(dual, 'app_build_digest', return_value='a' * 64), \
                 mock.patch.object(dual, 'proxy_authorities', return_value={'owned.invalid:443'}), \
                 mock.patch.object(dual, 'actor_runtime_copy'), \
                 mock.patch.object(dual.shutil, 'copytree'), \
                 mock.patch.object(dual, 'run_actor', side_effect=failure), \
                 mock.patch.object(dual, 'retain_failure_room_reference', side_effect=RuntimeError('private-token-must-not-print')), \
                 mock.patch('builtins.print', side_effect=fail_diagnostic_output), \
                 mock.patch.object(dual, 'stop_owned_processes'), \
                 mock.patch.object(dual, 'cleanup_owned_devices', return_value=True):
                with self.assertRaises(ios.NativeFailure) as caught:
                    dual.qualify(Path('unused-private-input'), output,
                                 {'WEAVE_E2E_RUN_ID': 'owned-test', 'WEAVE_NATIVE_CA': 'owned-ca'})
                self.assertIs(caught.exception, failure)
            private = client / 'build/private-ios-qualification'
            diagnostics = list(private.glob('command-*.log'))
            self.assertEqual(len(diagnostics), 1)
            self.assertEqual(private.stat().st_mode & 0o777, 0o700)
            self.assertEqual(diagnostics[0].stat().st_mode & 0o777, 0o600)
            records = diagnostics[0].read_text().splitlines()
            self.assertEqual(len(records), 2)
            for role, record in zip(('A', 'B'), records):
                self.assertRegex(record, '^NATIVE_IOS_RELAY_DIAGNOSTIC actor=' + role + r' (?:[A-Za-z-]+=[0-9]{1,12} ?)+$')
                self.assertNotIn('owned.invalid', record)
                self.assertNotIn('owned-test-failure', record)
            self.assertTrue(json.loads(output.read_text())['cleanupComplete'])
            self.assertNotIn('RELAY_DIAGNOSTIC', output.read_text())

    def test_private_json_publish_is_atomic_owner_only_and_never_overwrites(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'denial.json'
            content = {'status': 'passed', 'denials': {'files': 'passed'}}
            dual.publish_private_json(path, content)
            self.assertEqual(json.loads(path.read_text()), content)
            self.assertEqual(path.stat().st_mode & 0o777, 0o600)
            self.assertFalse(path.with_name(path.name + '.tmp').exists())
            with self.assertRaises(FileExistsError): dual.publish_private_json(path, {'status': 'changed'})
            self.assertEqual(json.loads(path.read_text()), content)
            path.unlink(); path.symlink_to(Path(directory) / 'foreign')
            with self.assertRaises(FileExistsError): dual.publish_private_json(path, content)
            self.assertFalse((Path(directory) / 'foreign').exists())
            path.unlink(); path.with_name(path.name + '.tmp').symlink_to(Path(directory) / 'foreign')
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
                         {'role': 'A', 'email': 'a@example.invalid', 'password': 'private-a', 'identityRef': 'a', 'displayName': 'Current owner', 'entryUri': 'weave.localhost'},
                         {'role': 'B', 'email': 'b@example.invalid', 'password': 'private-b', 'identityRef': 'b', 'displayName': 'Current peer', 'entryUri': 'weave://join?organization_origin=https%3A%2F%2Fweave.localhost'}]}
            def write():
                path.write_text(json.dumps(value));path.chmod(0o600)
            write()
            self.assertEqual(dual.private_input(path, 'owned-run'), value)
            value['actors'][1]['entryUri'] += '%2F';write()
            self.assertEqual(dual.private_input(path, 'owned-run'), value)
            value['actors'][1]['entryUri'] = value['actors'][1]['entryUri'][:-3]
            for field, bad in [('displayName', ''), ('displayName', 'Current owner'), ('identityRef', 'a'), ('entryUri', ''), ('role', 'A'),
                               ('entryUri', 'weave://join?organization_origin=https%3A%2F%2Fforeign.example'),
                               ('entryUri', value['actors'][1]['entryUri'] + '&private_token=private'),
                               ('entryUri', value['actors'][1]['entryUri'] + '&'),
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
            value['scenarios']['authorization'].update({'deniedIdentityDigest': 'c' * 64, 'resourceDigest': 'd' * 64,
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
