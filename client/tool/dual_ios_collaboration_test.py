#!/usr/bin/env python3
"""Private actor ownership and mandatory real-proof contract regressions."""
import contextlib
import io
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
    def test_inspection_run_uses_canonical_inputs_and_independent_outputs(self):
        with tempfile.TemporaryDirectory() as temporary, mock.patch.dict(os.environ, {'WEAVE_E2E_RUN_ID': 'owned-test'}):
            root = Path(temporary)
            client = root / 'client'
            mapping = client / '.dart_tool/flutter_build/owned/native_assets.json'
            app = client / 'build/ios/iphonesimulator/Runner.app'
            commands = []
            with mock.patch.object(ios, 'CLIENT', client):
                for role in ['A', 'B']:
                    compiler = root / role / 'compiler'
                    commands.append(dual.inspection_run_command('owned-' + role, compiler, app, mapping))
            for command in commands:
                self.assertIn('--packages=' + str(client / '.dart_tool/package_config.json'), command)
                self.assertIn('--native-assets-yaml-file=' + str(mapping), command)
                self.assertIn('--use-application-binary=' + str(app), command)
                self.assertTrue({'run', '--debug', '--no-build', '--no-pub', '--no-track-widget-creation'}.issubset(command))
                self.assertFalse(any('start-paused' in arg or 'debug-url' in arg for arg in command))
            for flag in ['--output-dill=', '--pid-file=']:
                self.assertNotEqual(next(x for x in commands[0] if x.startswith(flag)), next(x for x in commands[1] if x.startswith(flag)))

    def test_initial_build_map_comes_only_from_completed_ios_build_receipt(self):
        with tempfile.TemporaryDirectory() as temporary:
            client = Path(temporary)
            receipt = client / 'build/ios/Debug-iphonesimulator/.last_build_id'
            receipt.parent.mkdir(parents=True)
            build_id = 'a' * 32
            mapping = client / '.dart_tool/flutter_build' / build_id / 'native_assets.json'
            mapping.parent.mkdir(parents=True)
            receipt.write_text(build_id)
            mapping.write_text(json.dumps({'format-version': [1, 0, 0], 'native-assets': {'ios_arm64': {'owned': ['absolute', '/owned']}}}))
            self.assertEqual(dual.initial_build_native_assets(client), mapping)
            for invalid in ['../outside', 'b' * 32, '']:
                receipt.write_text(invalid)
                with self.assertRaises(ios.NativeFailure): dual.initial_build_native_assets(client)
            receipt.write_text(build_id)
            mapping.write_text('{}')
            with self.assertRaises(ios.NativeFailure): dual.initial_build_native_assets(client)
            mapping.unlink(); mapping.symlink_to(receipt)
            with self.assertRaises(ios.NativeFailure): dual.initial_build_native_assets(client)

    def test_inspection_run_starts_in_canonical_project_with_owned_pid_and_actual_marker(self):
        with tempfile.TemporaryDirectory() as temporary, mock.patch.dict(os.environ, {'WEAVE_E2E_RUN_ID': 'owned-test'}):
            root = Path(temporary); compiler = root / 'compiler'; compiler.mkdir()
            streams = []
            process = mock.Mock(pid=123)
            def start(command, **kwargs):
                self.assertEqual(kwargs['cwd'], ios.CLIENT)
                (compiler / 'run.pid').write_text('123')
                kwargs['stdout'].write('NATIVE_PRODUCT_STAGE phase=local-inspection-entry-ready qualificationPassed=false supportSafe=true\n')
                kwargs['stdout'].flush()
                return process
            try:
                with mock.patch.object(dual.subprocess, 'Popen', side_effect=start), mock.patch.object(dual, 'track_process', side_effect=lambda p: p), mock.patch.object(ios, 'simulator_app_pid', return_value='456'), mock.patch.object(dual, 'current_actor_support', return_value=root / 'current-support'):
                    actor = dual.start_local_actor('owned', compiler, root / 'Runner.app', root / 'native_assets.json', {}, root, streams, require_entry=True, generation=1, expected_fixture={'runId': 'owned-test', 'role': 'A', 'storageDigest': 'preinstall'})
                self.assertEqual(actor['pid'], '456')
                self.assertEqual(actor['process'], process)
                self.assertFalse((root / 'client').exists(), 'local inspection must not copy a project')
            finally:
                for stream in streams: stream.close()

    def test_current_support_follows_owned_reinstall_and_rejects_bad_fixture(self):
        with tempfile.TemporaryDirectory() as temporary, mock.patch.dict(os.environ, {'WEAVE_E2E_RUN_ID': 'owned-test'}):
            root = Path(temporary).resolve()
            udid = '11111111-1111-1111-1111-111111111111'
            current = root / 'CoreSimulator/Devices' / udid / 'data/Containers/Data/Application/current'
            support = current / 'Library/Application Support'; support.mkdir(parents=True)
            fixture = support / 'weave-dual-ios-input.json'
            expected = {'runId': 'owned-test', 'role': 'A', 'storageDigest': 'preinstall-digest'}
            fixture.write_text(json.dumps(expected)); fixture.chmod(0o600)
            with mock.patch.object(ios, 'simulator_app_pid', return_value='123'), mock.patch.object(ios, 'run', return_value=str(current)):
                self.assertEqual(dual.current_actor_support(udid, '123', expected, {}), support)
                self.assertFalse((current.parent / 'old').exists())
                for changed in [{'role': 'B'}, {'runId': 'other'}, {'storageDigest': 'tampered'}]:
                    fixture.write_text(json.dumps({**expected, **changed}))
                    with self.assertRaises(ios.NativeFailure): dual.current_actor_support(udid, '123', expected, {})
                fixture.write_text(json.dumps(expected)); fixture.chmod(0o644)
                with self.assertRaises(ios.NativeFailure): dual.current_actor_support(udid, '123', expected, {})
                fixture.chmod(0o600); original = support / 'original'; fixture.rename(original); fixture.symlink_to(original)
                with self.assertRaises(ios.NativeFailure): dual.current_actor_support(udid, '123', expected, {})
            with mock.patch.object(ios, 'simulator_app_pid', return_value='different'), mock.patch.object(ios, 'run') as query:
                with self.assertRaises(ios.NativeFailure): dual.current_actor_support(udid, '123', expected, {})
                query.assert_not_called()

    def test_picker_reuses_main_thread_child_finally_on_owner_interruption(self):
        with tempfile.TemporaryDirectory() as temporary:
            products = Path(temporary); (products / 'owned.xctestrun').touch()
            (products / 'BrowserDriver.xctest').mkdir()
            child = mock.Mock(); interruption = SystemExit(130)
            child.communicate.side_effect = interruption
            with mock.patch.object(ios, 'simulator_app_pid', return_value='123'), \
                 mock.patch.object(ios.subprocess, 'Popen', return_value=child) as launch, \
                 mock.patch.object(ios, 'stop_flutter_process_group') as stop:
                with self.assertRaises(SystemExit) as caught:
                    dual.perform_owned_picker('owned', '123', products, {}, 'import', 'weave-dual-initial-owned.txt')
            self.assertIs(caught.exception, interruption)
            stop.assert_called_once_with(child)
            child.communicate.assert_called_once_with(timeout=90)
            self.assertTrue(launch.call_args.kwargs['start_new_session'])
            self.assertIn('-only-testing:BrowserDriver/BrowserDriver/testNativeDocumentPicker', launch.call_args.args[0])

    def test_local_browser_waits_for_cu_and_picker_uses_same_owned_operation(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary); scope = root / 'scope'; scope.mkdir()
            derived = root / 'derived'; products = derived / 'Build/Products'; products.mkdir(parents=True)
            (products / 'owned.xctestrun').touch(); (products / 'BrowserDriver.xctest').mkdir()
            prepared = {}
            def prepare(actor, _udid, _app, _data, _env):
                expected = {'role': actor['role'], 'runId': 'owned-test', 'storageDigest': 'preinstall'}
                prepared[actor['role']] = expected
                return root / 'obsolete', expected
            def start(udid, compiler, _app, _map, env, actor_scope, streams, **kwargs):
                log = actor_scope / 'run-1.log'; stream = log.open('w'); streams.append(stream)
                process = mock.Mock(pid=123 if udid == 'A' else 456); process.poll.return_value = None
                return {'udid': udid, 'pid': udid + '-pid', 'compiler': compiler, 'scope': actor_scope,
                        'support': root / ('current-' + udid), 'fixture': kwargs['expected_fixture'],
                        'env': env, 'process': process, 'stream': stream, 'log': log, 'generation': 1}
            def relay(*_args, **_kwargs):
                fixture = json.loads((scope / 'A/Build/Products/BrowserDriver.xctest/private-member.json').read_text())
                self.assertIs(fixture['flutterInitiatesSignIn'], True)
                self.assertEqual(fixture['phase'], 'initial')
                return True
            commands = [{'sequence': 1, 'action': 'browser', 'role': 'A'},
                        {'sequence': 2, 'action': 'picker-replacement', 'role': 'A'},
                        {'sequence': 3, 'action': 'close', 'role': 'both'}]
            driver = mock.Mock(); driver.wait.return_value = 0
            data = {'runId': 'owned-test', 'actors': [{'role': r, 'email': 'private-email', 'password': 'private-password'} for r in ['A', 'B']]}
            with mock.patch.object(dual, 'prepare_actor_storage', side_effect=prepare), \
                 mock.patch.object(dual, 'start_local_actor', side_effect=start), \
                 mock.patch.object(dual, 'actor_proxy_environment', return_value={}), \
                 mock.patch.object(dual, 'inspection_source', return_value={}), \
                 mock.patch.object(ios, 'simulator_app_pid', side_effect=lambda udid: udid + '-pid'), \
                 mock.patch.object(dual, 'read_inspection_command', side_effect=commands), \
                 mock.patch.object(dual.subprocess, 'Popen', return_value=driver), \
                 mock.patch.object(dual, 'track_process', side_effect=lambda p: p), \
                 mock.patch.object(ios, 'relay_workspace_completion', side_effect=relay), \
                 mock.patch.object(dual, 'perform_owned_picker') as picker, \
                 mock.patch.object(dual, 'stop_owned_processes'), \
                 mock.patch.object(dual.time, 'sleep'), contextlib.redirect_stdout(io.StringIO()):
                self.assertEqual(dual.inspect_local_apps(data, {}, scope, ['A', 'B'], root / 'app', root / 'map', derived, {'A': None, 'B': None}, {'builtSource': {}}), 70)
            self.assertEqual(picker.call_args.args[4:], ('import', 'weave-dual-replacement-owned-test.txt'))
            self.assertEqual(picker.call_args.kwargs['result_path'], scope / 'A/picker-2.xcresult')
            session = json.loads((scope / 'inspection-session-2.json').read_text())
            self.assertEqual(session['actors']['A']['appSupport'], str(root / 'current-A'))
            self.assertNotEqual(session['actors']['A']['storageDigest'], 'preinstall')
            self.assertEqual(session['actors']['A']['fixturePreinstallStorageDigest'], 'preinstall')

    def test_picker_inspection_commands_are_single_actor_and_bounded(self):
        with tempfile.TemporaryDirectory() as temporary:
            p = Path(temporary) / 'command.json'
            for action in ['picker-initial', 'picker-replacement', 'picker-export']:
                for role in ['A', 'B']:
                    if p.exists(): p.unlink()
                    dual.publish_private_json(p, {'sequence': 1, 'action': action, 'role': role})
                    self.assertEqual(dual.read_inspection_command(p, 0)['action'], action)
                p.unlink(); dual.publish_private_json(p, {'sequence': 1, 'action': action, 'role': 'both'})
                with self.assertRaises(ios.NativeFailure): dual.read_inspection_command(p, 0)
            p.unlink(); dual.publish_private_json(p, {'sequence': 1, 'action': 'picker-arbitrary', 'role': 'A'})
            with self.assertRaises(ios.NativeFailure): dual.read_inspection_command(p, 0)

    def test_inspection_command_is_private_bounded_and_consumed_once(self):
        with tempfile.TemporaryDirectory() as temporary:
            path = Path(temporary) / 'inspection-command.json'
            value = {'sequence': 1, 'action': 'reload', 'role': 'both'}
            dual.publish_private_json(path, value)
            self.assertEqual(dual.read_inspection_command(path, 0), value)
            self.assertIsNone(dual.read_inspection_command(path, 1))
            for replacement in [{'sequence': 0}, {'sequence': True}, {'action': 'arbitrary'}, {'role': 'C'}]:
                path.unlink()
                dual.publish_private_json(path, {**value, **replacement})
                with self.assertRaises(ios.NativeFailure):
                    dual.read_inspection_command(path, 1)
            path.unlink()
            dual.publish_private_json(path, value)
            path.chmod(0o644)
            with self.assertRaises(ios.NativeFailure): dual.read_inspection_command(path, 0)
            path.unlink()
            target = Path(temporary) / 'foreign'
            target.write_text(json.dumps(value))
            path.symlink_to(target)
            with self.assertRaises(ios.NativeFailure): dual.read_inspection_command(path, 0)

    def test_inspection_refuses_or_yields_to_actual_worker_detection(self):
        operation = mock.Mock()
        with mock.patch.object(dual, 'inspection_ci_workers', return_value=1):
            with self.assertRaises(ios.NativeFailure): dual.inspect_with_ci_priority(operation)
        operation.assert_not_called()
        yielded = threading.Event()
        with mock.patch.object(dual, 'inspection_ci_workers', side_effect=[0, 1]), \
             mock.patch.object(dual.os, 'kill', side_effect=lambda pid, sig: yielded.set()) as stop:
            self.assertEqual(dual.inspect_with_ci_priority(lambda: 70 if yielded.wait(3) else self.fail('CI not observed')), 70)
        stop.assert_called_once_with(os.getpid(), dual.signal.SIGINT)

    def test_inspection_priority_handoff_is_armed_and_unknown_still_yields(self):
        threads = []
        real_thread = threading.Thread
        observed = threading.Event()
        output = io.StringIO()
        def thread(*args, **kwargs):
            created = real_thread(*args, **kwargs); threads.append(created); return created
        def operation():
            self.assertIn('phase=priority-watch-ready', output.getvalue())
            self.assertTrue(threads[0].is_alive())
            self.assertTrue(observed.wait(3), 'unknown ownership must interrupt')
            return 70
        with contextlib.redirect_stdout(output), \
             mock.patch.object(dual.threading, 'Thread', side_effect=thread), \
             mock.patch.object(dual, 'inspection_ci_workers', side_effect=[0, OSError('private detector failure')]), \
             mock.patch.object(dual.os, 'kill', side_effect=lambda _pid, _signal: observed.set()) as stop:
            self.assertEqual(dual.inspect_with_ci_priority(operation), 70)
        stop.assert_called_once_with(os.getpid(), dual.signal.SIGINT)
        self.assertFalse(threads[0].is_alive())
        self.assertIn('phase=yield-ci reason=unknown workerCount=unavailable exceptionClass=OSError returnCode=unavailable', output.getvalue())
        self.assertNotIn('private detector failure', output.getvalue())

    def test_inspection_priority_failed_ps_exit_is_fixed_and_interrupts(self):
        observed = threading.Event()
        output = io.StringIO()
        with contextlib.redirect_stdout(output), \
             mock.patch.object(dual, 'inspection_ci_workers', side_effect=[0, subprocess.CalledProcessError(7, ['private-command'], stderr='private-stderr')]), \
             mock.patch.object(dual.os, 'kill', side_effect=lambda _pid, _signal: observed.set()):
            self.assertEqual(dual.inspect_with_ci_priority(lambda: 70 if observed.wait(3) else self.fail('PS error was ignored')), 70)
        self.assertIn('reason=unknown workerCount=unavailable exceptionClass=CalledProcessError returnCode=7', output.getvalue())
        self.assertNotIn('private-', output.getvalue())

    def test_inspection_priority_monitor_stops_when_operation_fails(self):
        threads = []
        real_thread = threading.Thread
        def thread(*args, **kwargs):
            created = real_thread(*args, **kwargs); threads.append(created); return created
        original = RuntimeError('original operation failure')
        with contextlib.redirect_stdout(io.StringIO()), mock.patch.object(dual.threading, 'Thread', side_effect=thread), mock.patch.object(dual, 'inspection_ci_workers', return_value=0):
            with self.assertRaises(RuntimeError) as caught:
                dual.inspect_with_ci_priority(lambda: (_ for _ in ()).throw(original))
        self.assertIs(caught.exception, original)
        self.assertFalse(threads[0].is_alive())

    def test_inspection_run_uses_pinned_real_cli_parser(self):
        with tempfile.TemporaryDirectory() as temporary, mock.patch.dict(os.environ, {'WEAVE_E2E_RUN_ID': 'owned-test'}):
            command = dual.inspection_run_command('owned-disposable-simulator', Path(temporary), Path(temporary) / 'Runner.app', Path(temporary) / 'native_assets.json')
            if not shutil.which('flutter'):
                self.skipTest('Flutter CLI required for actual run parser validation')
            accepted = subprocess.run([*command, '--help'], capture_output=True, text=True, timeout=30)
            self.assertEqual(accepted.returncode, 0, accepted.stderr)
            self.assertIn('--use-application-binary', accepted.stdout)
            self.assertNotIn('--use-existing-app', command)

    def test_partial_inspection_run_retains_private_failure_before_cleanup(self):
        for retention_fails, stop_fails in ((False, False), (True, False), (False, True)):
            with self.subTest(retention_fails=retention_fails, stop_fails=stop_fails), tempfile.TemporaryDirectory() as directory:
                root = Path(directory); scope = root / 'owned'; scope.mkdir()
                derived = root / 'derived'; (derived / 'Build/Products').mkdir(parents=True)
                private = root / 'retained'; private.mkdir(mode=0o700)
                failure = RuntimeError('original resident startup failure')
                raw = 'private-token private-device private-path\nresident startup failed\n'
                def attach(_udid, _compiler, _app, _native_assets, _env, actor_scope, streams, **_kwargs):
                    stream = (actor_scope / 'run-1.log').open('w')
                    stream.write(raw); streams.append(stream)
                    raise failure
                output = io.StringIO()
                with mock.patch.object(dual, 'actor_proxy_environment', return_value={}), \
                     mock.patch.object(dual, 'prepare_actor_storage', return_value=(root, {})), \
                     mock.patch.object(dual, 'start_local_actor', side_effect=attach), \
                     mock.patch.object(dual, 'stop_owned_processes', side_effect=[OSError('stop failed'), None] if stop_fails else None), \
                     mock.patch.object(ios, 'verified_private_parent', return_value=private), \
                     contextlib.redirect_stdout(output):
                    retain = mock.patch.object(ios, 'retain_private_diagnostic', side_effect=OSError('private retention failure')) if retention_fails else contextlib.nullcontext()
                    with retain, self.assertRaises(RuntimeError) as caught:
                        dual.inspect_local_apps({'actors': [{'role': 'A'}]}, {}, scope, ['owned-device'], root / 'app', root / 'native_assets.json', derived, {'A': object()}, {'builtSource': {}})
                self.assertIs(caught.exception, failure)
                logs = list(private.glob('command-*.log'))
                self.assertEqual(len(logs), 0 if retention_fails else 1)
                if logs:
                    self.assertEqual(logs[0].read_text(), raw)
                    self.assertEqual(logs[0].stat().st_mode & 0o777, 0o600)
                for secret in ('private-token', 'private-device', 'private-path', 'private retention failure', str(root)):
                    self.assertNotIn(secret, output.getvalue())
                self.assertIn('failure-log-unavailable' if retention_fails else 'failure-log-retained', output.getvalue())

    def test_actor_prepares_owner_only_result_before_actual_launch(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory).resolve()
            udid = '11111111-1111-1111-1111-111111111111'
            container = root / 'CoreSimulator' / 'Devices' / udid / 'data'
            container.mkdir(parents=True)
            stopped = ios.NativeFailure('infrastructure', 'owned-test', 'launch-stopped')
            def command(args, **_kwargs):
                if args[2] == 'install': return ''
                if args[2] == 'get_app_container': return str(container)
                if args[2] == 'launch':
                    result = container / 'Library/Application Support/weave-dual-ios-result.json'
                    self.assertEqual(json.loads(result.read_text()), {})
                    self.assertEqual(result.stat().st_mode & 0o777, 0o600)
                    with self.assertRaises(ios.NativeFailure):
                        dual.actor_result(result, 'A', 'owned-actor', 'a' * 64)
                    raise stopped
                self.fail('Unexpected native command')
            data = {'runId': 'owned-test', 'publicOrigin': 'https://owned.invalid', 'organizationId': 'canonical-tenant', 'nativeOrganizationId': 'owned-org',
                    'sharedSpaceRef': 'workspace-default', 'actors': [
                        {'role': 'A', 'displayName': 'Owned author'},
                        {'role': 'B', 'displayName': 'Owned peer'}]}
            with mock.patch.object(ios, 'run', side_effect=command), \
                 mock.patch.object(ios.simulator_vm, 'log_start_timestamp', return_value='owned-time'):
                with self.assertRaises(ios.NativeFailure) as caught:
                    dual.run_actor({'role': 'A', 'entryUri': 'https://owned.invalid', 'identityRef': 'owned-actor'}, udid,
                                   root, root, root, root / 'app', data, {'WEAVE_NATIVE_ISSUER': 'https://issuer.test'}, 'a' * 64,
                                   None, None, None)
                self.assertIs(caught.exception, stopped)

    def test_inspection_storage_seeds_private_observer_without_workspace_proof(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory).resolve()
            udid = '11111111-1111-1111-1111-111111111111'
            container = root / 'CoreSimulator/Devices' / udid / 'data'
            container.mkdir(parents=True)
            actor = {'role': 'A', 'entryUri': 'https://owned.invalid', 'identityRef': 'owned-actor'}
            data = {'runId': 'owned-test', 'publicOrigin': 'https://owned.invalid', 'nativeOrganizationId': 'owned-org',
                    'sharedSpaceRef': 'workspace-default', 'actors': [{'role': 'A', 'displayName': 'Author'}, {'role': 'B', 'displayName': 'Peer'}]}
            with mock.patch.object(ios, 'run', side_effect=['', str(container)]):
                support, _ = dual.prepare_actor_storage(actor, udid, root / 'app', data,
                    {'WEAVE_NATIVE_ISSUER': 'https://issuer.test', 'WEAVE_NATIVE_LOCAL_INSPECTION': 'true'})
            proof = support / 'weave-local-inspection-state.json'
            self.assertEqual(json.loads(proof.read_text()), {})
            self.assertEqual(proof.stat().st_mode & 0o777, 0o600)
            self.assertFalse(json.loads(proof.read_text()).get('workspaceObserved', False))

    def test_picker_rejects_actual_skip_and_unverified_completion_before_pass_marker(self):
        # Fixed excerpts from the actual 0321 skip and successful import transcripts.
        selected = "Test Case '-[BrowserDriver.BrowserDriver testNativeDocumentPicker]'"
        passed = selected + " passed (6.766 seconds).\n"
        actual_skip = (selected + " skipped (0.138 seconds).\n"
                       "Executed 1 test, with 1 test skipped and 0 failures (0 unexpected) in 0.138 (0.139) seconds\n"
                       "** TEST EXECUTE SUCCEEDED **\n")
        imported = 'NATIVE_IOS_FILES_STAGE phase=import-selected\n' + passed
        exported = 'NATIVE_IOS_FILES_STAGE phase=export-saved\n' + passed
        cases = [('import', imported, True), ('export', exported, True),
                 ('export', actual_skip, False), ('export', passed, False),
                 ('export', imported, False), ('export', exported + imported, False),
                 ('export', exported + 'NATIVE_IOS_FILES_STAGE phase=export-saved\n', False),
                 ('export', exported + actual_skip, False),
                 ('export', exported + selected + ' skipped (0.138 seconds).\n', False),
                 ('export', exported + 'Executed 1 test, with 1 test skipped and 0 failures\n', False),
                 ('export', 'NATIVE_IOS_FILES_STAGE phase=export-saved\n', False),
                 ('export', exported + passed, False)]
        for mode, transcript, valid in cases:
            with self.subTest(mode=mode, valid=valid, transcript=transcript), tempfile.TemporaryDirectory() as name:
                root = Path(name); products = root / 'products'; products.mkdir()
                (products / 'owned.xctestrun').write_text('owned plan')
                (products / 'BrowserDriver.xctest').mkdir()
                diagnostics = root / 'diagnostics'; diagnostics.mkdir(mode=0o700)
                raw = transcript + 'private-credential private/path\n'
                with mock.patch.object(ios, 'simulator_app_pid', return_value='123'), mock.patch.object(ios, 'run', return_value=raw) as command, mock.patch.object(ios, 'verified_private_parent', return_value=diagnostics), contextlib.redirect_stdout(io.StringIO()) as public:
                    if valid:
                        dual.perform_owned_picker('owned', '123', products, {}, mode, 'weave-dual-initial-owned.txt')
                    else:
                        with self.assertRaises(ios.NativeFailure) as caught:
                            dual.perform_owned_picker('owned', '123', products, {}, mode, 'weave-dual-initial-owned.txt')
                        self.assertEqual((caught.exception.category, caught.exception.reason),
                                         ('infrastructure', 'native-picker-result-unverified'))
                command.assert_called_once()
                self.assertEqual(command.call_args.kwargs['timeout'], 90)
                logs = list(diagnostics.glob('command-*.log')); self.assertEqual(len(logs), 1)
                self.assertEqual(logs[0].read_text(), raw)
                self.assertEqual(logs[0].stat().st_mode & 0o777, 0o600)
                self.assertEqual('status=passed' in public.getvalue(), valid)
                for private in ['private-credential', 'private/path', transcript.strip(), str(diagnostics)]:
                    self.assertNotIn(private, public.getvalue())

    def test_picker_pass_marker_requires_unchanged_owned_process(self):
        transcript = ("NATIVE_IOS_FILES_STAGE phase=export-saved\n"
                      "Test Case '-[BrowserDriver.BrowserDriver testNativeDocumentPicker]' passed (6.766 seconds).\n")
        with tempfile.TemporaryDirectory() as name:
            root = Path(name); products = root / 'products'; products.mkdir()
            (products / 'owned.xctestrun').write_text('owned plan')
            (products / 'BrowserDriver.xctest').mkdir()
            diagnostics = root / 'diagnostics'; diagnostics.mkdir(mode=0o700)
            with mock.patch.object(ios, 'simulator_app_pid', side_effect=['123', '456']), mock.patch.object(ios, 'run', return_value=transcript), mock.patch.object(ios, 'verified_private_parent', return_value=diagnostics), contextlib.redirect_stdout(io.StringIO()) as public:
                with self.assertRaises(ios.NativeFailure) as caught:
                    dual.perform_owned_picker('owned', '123', products, {}, 'export', 'weave-dual-initial-owned.txt')
            self.assertEqual(caught.exception.reason, 'owned-process-replaced')
            self.assertNotIn('status=passed', public.getvalue())
            logs = list(diagnostics.glob('command-*.log')); self.assertEqual(len(logs), 1)
            self.assertEqual(logs[0].read_text(), transcript)

    def test_successful_picker_stdout_is_owner_only_and_never_public(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            diagnostics = root / 'diagnostics'
            diagnostics.mkdir(mode=0o700)
            scopes = {}
            for role, pid in [('A', '123'), ('B', '456')]:
                support = root / role
                support.mkdir()
                products = support / 'products'
                products.mkdir()
                (products / 'owned.xctestrun').write_text('plan')
                (products / 'BrowserDriver.xctest').mkdir()
                scopes[role] = (support, role, pid, products)
            (scopes['A'][0] / 'weave-dual-ios-native-request.json').write_text(json.dumps({
                'sequence': 8, 'mode': 'import', 'fileName': 'weave-dual-initial-owned-test.txt'}))
            picker_ran = False
            stopped = RuntimeError('controlled stop after diagnostic retention')
            raw = ("private-owned-file.txt private/path identity-sentinel\nNATIVE_IOS_FILES_STAGE phase=import-selected\n"
                   "Test Case '-[BrowserDriver.BrowserDriver testNativeDocumentPicker]' passed (4.643 seconds).\n")
            def picker(args, **kwargs):
                nonlocal picker_ran
                self.assertIn('-only-testing:BrowserDriver/BrowserDriver/testNativeDocumentPicker', args)
                self.assertEqual(kwargs['timeout'], 90)
                picker_ran = True
                return raw
            def acknowledge(_delay):
                for support, *_ in scopes.values():
                    command = support / 'weave-dual-ios-command.json'
                    if command.exists():
                        value = json.loads(command.read_text())
                        (support / 'weave-dual-ios-ack.json').write_text(json.dumps({**value, 'status': 'passed'}))
            task = mock.Mock()
            task.done.side_effect = lambda: picker_ran
            task.result.side_effect = stopped
            with mock.patch.object(ios, 'simulator_app_pid', side_effect=lambda role: {'A': '123', 'B': '456'}[role]), \
                 mock.patch.object(ios, 'run', side_effect=picker), \
                 mock.patch.object(ios, 'verified_private_parent', return_value=diagnostics), \
                 mock.patch.object(dual.time, 'monotonic', return_value=0), \
                 mock.patch.object(dual.time, 'sleep', side_effect=acknowledge), \
                 contextlib.redirect_stdout(io.StringIO()) as output:
                with self.assertRaises(RuntimeError) as caught:
                    dual.run_ui_commands(scopes, [task], {}, {'runId': 'owned-test'})
            self.assertIs(caught.exception, stopped)
            retained = list(diagnostics.glob('command-*.log'))
            self.assertEqual(len(retained), 1)
            self.assertEqual(retained[0].read_text(), raw)
            self.assertEqual(retained[0].stat().st_mode & 0o777, 0o600)
            self.assertIn('NATIVE_IOS_PICKER_RESULT status=passed privateDiagnostic=' + retained[0].name + ' supportSafe=true', output.getvalue())
            for private in ['private-owned-file', 'private/path', 'identity-sentinel', str(diagnostics), 'import-selected']:
                self.assertNotIn(private, output.getvalue())
            picker_ran = False
            with mock.patch.object(ios, 'simulator_app_pid', side_effect=lambda role: {'A': '123', 'B': '456'}[role]), \
                 mock.patch.object(ios, 'run', side_effect=picker), \
                 mock.patch.object(ios, 'retain_private_diagnostic', side_effect=OSError('private retention failure')), \
                 mock.patch.object(dual.time, 'monotonic', return_value=0), \
                 mock.patch.object(dual.time, 'sleep', side_effect=acknowledge), \
                 contextlib.redirect_stdout(io.StringIO()) as unavailable:
                with self.assertRaises(RuntimeError) as caught:
                    dual.run_ui_commands(scopes, [task], {}, {'runId': 'owned-test'})
            self.assertIs(caught.exception, stopped)
            self.assertIn('NATIVE_IOS_PICKER_RESULT status=unavailable supportSafe=true', unavailable.getvalue())
            self.assertNotIn('private retention failure', unavailable.getvalue())

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
            input_data = {'runId': 'owned-test', 'organizationId': 'canonical-tenant', 'nativeOrganizationId': 'owned-org', 'actors': [
                {'role': 'A', 'identityRef': 'actor-A'}, {'role': 'B', 'identityRef': 'actor-B'}]}
            result = support / 'weave-dual-ios-result.json'
            payload = {'role': 'A', 'identityDigest': hashlib.sha256(b'actor-A').hexdigest(),
                       'identityIssuerDigest': hashlib.sha256(b'https://issuer.test').hexdigest(),
                       'nativeOrganizationDigest': hashlib.sha256(b'owned-org').hexdigest(),
                       'resources': {'roomId': '!owned:weave.test', 'roomTitle': 'private-displayed-name'},
                       'privateError': 'private-message-must-not-transfer'}
            result.write_text(json.dumps(payload)); result.chmod(0o600)
            peer = root / 'peer'; peer.mkdir(mode=0o700)
            peer_result = peer / 'weave-dual-ios-result.json'
            peer_payload = dict(payload, role='B', identityDigest=hashlib.sha256(b'actor-B').hexdigest())
            peer_result.write_text(json.dumps(peer_payload)); peer_result.chmod(0o600)
            scopes = {'A': (support, 'owned-device', '123', support), 'B': (peer, 'owned-peer', '456', peer)}
            dual.retain_failure_room_reference(scopes, input_path, input_data, 'a' * 40, 'https://issuer.test')
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
                dual.retain_failure_room_reference(scopes, input_path, input_data, 'a' * 40, 'https://issuer.test')
            self.assertEqual(json.loads(snapshot.read_text()), actual)
            snapshot.unlink()  # Existing host collector consumes then deletes it.
            result.write_text(json.dumps(payload)); result.chmod(0o600)
            for mutation in [dict(payload, role='B'), dict(payload, identityDigest='b' * 64),
                             dict(payload, resources={'roomId': '../private'})]:
                result.write_text(json.dumps(mutation))
                with self.assertRaises(ios.NativeFailure):
                    dual.retain_failure_room_reference(scopes, input_path, input_data, 'a' * 40, 'https://issuer.test')
                self.assertFalse(snapshot.exists())
            result.write_text(json.dumps(payload))
            for key, reason in [('identityDigest', 'actor-mismatch'), ('identityIssuerDigest', 'issuer-mismatch'),
                                ('nativeOrganizationDigest', 'organization-mismatch')]:
                invalid_peer = dict(peer_payload, **{key: 'b' * 64})
                peer_result.write_text(json.dumps(invalid_peer))
                with self.assertRaises(ios.NativeFailure) as caught:
                    dual.retain_failure_room_reference(scopes, input_path, input_data, 'a' * 40, 'https://issuer.test')
                self.assertEqual(caught.exception.reason, reason)
                self.assertFalse(snapshot.exists())
            peer_result.write_text(json.dumps(peer_payload))
            for key in ['identityDigest', 'identityIssuerDigest', 'nativeOrganizationDigest']:
                missing = dict(peer_payload); missing.pop(key)
                peer_result.write_text(json.dumps(missing))
                with self.assertRaises(ios.NativeFailure):
                    dual.retain_failure_room_reference(scopes, input_path, input_data, 'a' * 40, 'https://issuer.test')
                self.assertFalse(snapshot.exists())
            peer_result.write_text(json.dumps(peer_payload))
            peer_result.chmod(0o644)
            with self.assertRaises(ios.NativeFailure):
                dual.retain_failure_room_reference(scopes, input_path, input_data, 'a' * 40, 'https://issuer.test')
            peer_result.chmod(0o600)
            result.write_text(json.dumps(payload)); result.chmod(0o644)
            with self.assertRaises(ios.NativeFailure) as caught:
                dual.retain_failure_room_reference(scopes, input_path, input_data, 'a' * 40, 'https://issuer.test')
            self.assertEqual(caught.exception.stage, 'dual-chat-snapshot')
            self.assertEqual(caught.exception.reason, 'unsafe-actor-result')
            result.unlink(); result.symlink_to(input_path)
            with self.assertRaises(ios.NativeFailure):
                dual.retain_failure_room_reference(scopes, input_path, input_data, 'a' * 40, 'https://issuer.test')
            self.assertFalse(snapshot.exists())

    def test_failed_actor_retains_private_relay_counters_before_cleanup(self):
        for transfer_failure, expected_stage in [
            (RuntimeError('private-token-must-not-print'), 'unavailable'),
            (ios.NativeFailure('infrastructure', 'dual-chat-snapshot', 'unsafe-actor-result'), 'unsafe-actor-result'),
            (ios.NativeFailure('infrastructure', 'dual-chat-snapshot', 'private-token-must-not-print'), 'unavailable'),
        ]:
            with tempfile.TemporaryDirectory() as directory:
                client = Path(directory) / 'client'
                client.mkdir()
                output = Path(directory) / 'failed-result.json'
                failure = ios.NativeFailure('integration', 'initial-product', 'owned-test-failure')
                actors = [{'role': 'A'}, {'role': 'B'}]
                snapshot_markers = []
                def fail_diagnostic_output(*args, **kwargs):
                    if args and str(args[0]).startswith('NATIVE_IOS_CHAT_FAILURE_SNAPSHOT'):
                        snapshot_markers.append(args[0])
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
                     mock.patch.object(dual, 'retain_failure_room_reference', side_effect=transfer_failure), \
                     mock.patch('builtins.print', side_effect=fail_diagnostic_output), \
                     mock.patch.object(dual, 'stop_owned_processes'), \
                     mock.patch.object(dual, 'cleanup_owned_devices', return_value=True):
                    with self.assertRaises(ios.NativeFailure) as caught:
                        dual.qualify(Path('unused-private-input'), output,
                                     {'WEAVE_E2E_RUN_ID': 'owned-test', 'WEAVE_NATIVE_CA': 'owned-ca'})
                    self.assertIs(caught.exception, failure)
                self.assertEqual(snapshot_markers, [
                    f'NATIVE_IOS_CHAT_FAILURE_SNAPSHOT status=unavailable stage={expected_stage} supportSafe=true'])
                self.assertNotIn('private-token', ''.join(snapshot_markers))
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
                     'organizationId': 'canonical-tenant', 'nativeOrganizationId': 'owned-org',
                     'publicOrigin': 'https://weave.localhost', 'actors': [
                         {'role': 'A', 'email': 'a@example.invalid', 'password': 'private-a', 'identityRef': 'a', 'displayName': 'Current owner', 'entryUri': 'weave.localhost'},
                         {'role': 'B', 'email': 'b@example.invalid', 'password': 'private-b', 'identityRef': 'b', 'displayName': 'Current peer', 'entryUri': 'weave://join?organization_origin=https%3A%2F%2Fweave.localhost'}]}
            def write():
                path.write_text(json.dumps(value));path.chmod(0o600)
            write()
            self.assertEqual(dual.private_input(path, 'owned-run'), value)
            for invalid in [None, '', ' ']:
                value['nativeOrganizationId'] = invalid; write()
                with self.assertRaises(ios.NativeFailure): dual.private_input(path, 'owned-run')
            del value['nativeOrganizationId']; write()
            with self.assertRaises(ios.NativeFailure): dual.private_input(path, 'owned-run')
            value['nativeOrganizationId'] = 'owned-org'
            for invalid in [None, '', ' ']:
                value['organizationId'] = invalid; write()
                with self.assertRaises(ios.NativeFailure): dual.private_input(path, 'owned-run')
            value['organizationId'] = 'owned-org'; write()
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
