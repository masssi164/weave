#!/usr/bin/env python3
"""Two owned live Flutter processes; credentials stay in actor-private XCTest bundles.

This extends the existing native qualification owner. It never upgrades missing
UI journeys, transport feasibility, or server denial proofs to a passed result.
"""
from __future__ import annotations

import concurrent.futures
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import socket
import select
import subprocess
import time
import queue
import threading
from datetime import datetime, timezone

import run_ios_simulator_product_acceptance as ios
from run_native_product_acceptance import stop_flutter_process_group

SCENARIOS = ('authentication', 'chat', 'calendar', 'files', 'authorization', 'recovery', 'accessibility')
UUID = re.compile(r'[0-9A-Fa-f]{8}(?:-[0-9A-Fa-f]{4}){3}-[0-9A-Fa-f]{12}')
DIGEST = re.compile(r'[0-9a-f]{64}')


_ACTIVE = []
_ACTIVE_LOCK = threading.Lock()
_CANCELLED = threading.Event()


def track_process(process):
    with _ACTIVE_LOCK:
        if _CANCELLED.is_set():
            stop_flutter_process_group(process)
            raise ios.NativeFailure('infrastructure', 'dual-cleanup', 'qualification-interrupted')
        _ACTIVE.append(process)
    return process


def stop_owned_processes():
    _CANCELLED.set()
    with _ACTIVE_LOCK:
        processes = list(_ACTIVE)
    failed = False
    for process in processes:
        try:
            stop_flutter_process_group(process)
        except (OSError, RuntimeError):
            failed = True
    if failed:
        raise ios.NativeFailure('infrastructure', 'dual-cleanup', 'owned-process-cleanup-unverified')


def cleanup_owned_devices(devices: list[str]) -> bool:
    """Attempt every owned resource even when another resource cannot be removed."""
    complete = True
    try:
        stop_owned_processes()
    except (OSError, RuntimeError):
        complete = False
    for udid in devices:
        try:
            subprocess.run(['xcrun', 'simctl', 'shutdown', udid], capture_output=True, timeout=60, check=False)
        except (OSError, subprocess.TimeoutExpired):
            complete = False
        try:
            ios.run(['xcrun', 'simctl', 'delete', udid], timeout=60)
        except (OSError, RuntimeError, subprocess.TimeoutExpired):
            complete = False
    try:
        remaining = json.loads(ios.run(['xcrun', 'simctl', 'list', 'devices', '-j']))['devices'] if devices else {}
        complete = complete and not any(d['udid'] in devices for group in remaining.values() for d in group)
    except (OSError, RuntimeError, ValueError, KeyError, subprocess.TimeoutExpired):
        complete = False
    return complete


class OwnedFaultRelay:
    """Run-owned CONNECT transport: forwards encrypted bytes without TLS termination.

    Only the selected test authority may be reached. Blocking closes A's actual
    sockets; no HTTP product response, session or application result is invented.
    """
    def __init__(self, authorities: set[str]):
        if not authorities or any(not re.fullmatch(r'[a-z0-9.-]+:[1-9][0-9]{0,4}', item) for item in authorities):
            raise ios.NativeFailure('infrastructure', 'dual-network', 'invalid-owned-authority')
        self.authorities = frozenset(authorities)
        self._stop = threading.Event()
        self._blocked = threading.Event()
        self._lock = threading.Lock()
        self._sockets = set()
        self._workers = []
        self.forwarded = 0
        self.blocked = 0
        self.rejected = 0
        self.listener = socket.socket()
        self.listener.bind(('127.0.0.1', 0))
        self.listener.listen(16)
        self.listener.settimeout(0.25)
        self.port = self.listener.getsockname()[1]
        self._thread = threading.Thread(target=self._accept, daemon=True)
        self._thread.start()

    def _accept(self):
        while not self._stop.is_set():
            try: client, _ = self.listener.accept()
            except socket.timeout: continue
            except OSError: break
            with self._lock:
                self._sockets.add(client)
                worker = threading.Thread(target=self._forward, args=(client,), daemon=True)
                self._workers.append(worker)
            worker.start()

    def _forward(self, client):
        upstream = None
        try:
            client.settimeout(5)
            header = bytearray()
            while b'\r\n\r\n' not in header and len(header) < 8192:
                packet = client.recv(1)
                if not packet: return
                header.extend(packet)
            request = bytes(header).split(b'\r\n', 1)[0]
            match = re.fullmatch(rb'CONNECT ([a-z0-9.-]+:[1-9][0-9]{0,4}) HTTP/1\.[01]', request)
            if match is None or match[1].decode('ascii') not in self.authorities:
                with self._lock: self.rejected += 1
                return
            if self._blocked.is_set():
                with self._lock: self.blocked += 1
                return
            host, port = match[1].decode('ascii').rsplit(':', 1)
            upstream = socket.create_connection((host, int(port)), timeout=10)
            with self._lock:
                self._sockets.add(upstream)
                self.forwarded += 1
            if self._blocked.is_set():
                with self._lock: self.blocked += 1
                return
            client.sendall(b'HTTP/1.1 200 Connection Established\r\n\r\n')
            client.setblocking(False); upstream.setblocking(False)
            while not self._stop.is_set() and not self._blocked.is_set():
                readable, _, _ = select.select([client, upstream], [], [], 0.25)
                for source in readable:
                    data = source.recv(65536)
                    if not data: return
                    destination = upstream if source is client else client
                    destination.settimeout(5); destination.sendall(data); destination.setblocking(False)
        except (OSError, ValueError):
            pass  # Connection loss is the actual fault; raw traffic is never logged.
        finally:
            for connection in (client, upstream):
                if connection is not None:
                    with self._lock: self._sockets.discard(connection)
                    connection.close()

    def interrupt(self):
        self._blocked.set()
        with self._lock:
            connections = list(self._sockets)
            self.blocked += len(connections) // 2
        for connection in connections:
            try: connection.shutdown(socket.SHUT_RDWR)
            except OSError: pass

    def restore(self):
        self._blocked.clear()

    def close(self):
        self._stop.set(); self.listener.close(); self.interrupt()
        self._thread.join(timeout=5)
        for worker in self._workers: worker.join(timeout=5)
        if self._thread.is_alive() or any(worker.is_alive() for worker in self._workers):
            raise ios.NativeFailure('infrastructure', 'dual-network-cleanup', 'owned-transport-still-active')


def actor_proxy_environment(env: dict, relay: OwnedFaultRelay) -> dict:
    selected = dict(env)
    proxy = 'http://127.0.0.1:' + str(relay.port)
    for name in ('http_proxy', 'https_proxy', 'HTTP_PROXY', 'HTTPS_PROXY'):
        selected['SIMCTL_CHILD_' + name] = proxy
    for name in ('no_proxy', 'NO_PROXY'):
        selected['SIMCTL_CHILD_' + name] = ''
    return selected


def proxy_authorities(input_data: dict, env: dict) -> set[str]:
    from urllib.parse import urlsplit
    result = set()
    for url in (input_data['publicOrigin'], env['WEAVE_NATIVE_API_BASE_URL'], env['WEAVE_NATIVE_MATRIX_URL']):
        parsed = urlsplit(url)
        if parsed.scheme != 'https' or parsed.hostname not in {'weave.localhost', 'api.weave.localhost', 'matrix.weave.localhost'} or parsed.username is not None:
            raise ios.NativeFailure('infrastructure', 'dual-network', 'non-owned-product-authority')
        result.add(parsed.hostname + ':' + str(parsed.port or 443))
    return result


def now() -> str:
    return datetime.now(timezone.utc).isoformat()


def app_build_digest(app: Path) -> str:
    """Hash the actual signed bundle once; both devices install these same bytes."""
    value = hashlib.sha256()
    for path in sorted(app.rglob('*')):
        relative = path.relative_to(app).as_posix().encode()
        if path.is_symlink():
            raise ios.NativeFailure('infrastructure', 'dual-build', 'bundle-symlink')
        if path.is_file():
            value.update(len(relative).to_bytes(4, 'big'))
            value.update(relative)
            value.update(hashlib.sha256(path.read_bytes()).digest())
    return value.hexdigest()


def private_input(path: Path, run_id: str) -> dict:
    if path.is_symlink() or not path.is_file():
        raise ios.NativeFailure('infrastructure', 'dual-fixture', 'private-fixture-missing')
    info = path.stat()
    if info.st_uid != os.getuid() or info.st_mode & 0o777 != 0o600 or path.parent.stat().st_mode & 0o777 != 0o700:
        raise ios.NativeFailure('infrastructure', 'dual-fixture', 'unsafe-private-fixture')
    data = json.loads(path.read_text())
    actors = data.get('actors', [])
    if (data.get('schemaVersion') != 'weave.dual-ios-input/v1' or data.get('runId') != run_id
            or len(actors) != 2 or [a.get('role') for a in actors] != ['A', 'B']
            or any(not a.get('email') or not a.get('password') or not a.get('identityRef') for a in actors)
            or actors[0]['identityRef'] == actors[1]['identityRef']):
        raise ios.NativeFailure('infrastructure', 'dual-fixture', 'independent-actors-required')
    # B must originate from the real public completion page, not an invented
    # provider callback or inferred activation outcome.
    if not actors[1].get('entryUri'):
        raise ios.NativeFailure('infrastructure', 'dual-fixture', 'invitation-completion-link-missing')
    origin = data.get('publicOrigin')
    from urllib.parse import urlsplit, parse_qsl
    parsed = urlsplit(origin or '')
    if (parsed.scheme != 'https' or parsed.hostname != 'weave.localhost' or parsed.path not in ('', '/')
            or parsed.username is not None or parsed.query or parsed.fragment):
        raise ios.NativeFailure('infrastructure', 'dual-fixture', 'disposable-public-origin-required')
    entry = urlsplit(actors[1]['entryUri'])
    if (entry.scheme != 'weave' or entry.netloc != 'join' or entry.path or entry.fragment
            or parse_qsl(entry.query, keep_blank_values=True) != [('organization_origin', origin)]):
        raise ios.NativeFailure('infrastructure', 'dual-fixture', 'invitation-completion-link-invalid')
    return data


def verify_result_path(path: Path, parent: Path, run_id: str) -> None:
    if (path.parent.resolve() != parent.resolve()
            or path.name != 'dual-ios-result-' + run_id + '.json'
            or path.is_symlink() or path.exists()):
        raise ios.NativeFailure('infrastructure', 'dual-result', 'unsafe-result-path')


def actor_result(path: Path, role: str, expected_identity: str, storage_digest: str) -> dict:
    if path.is_symlink() or not path.is_file() or path.stat().st_size > 8192:
        raise ios.NativeFailure('integration', 'dual-result', 'actor-proof-missing')
    data = json.loads(path.read_text())
    if data.get('role') != role or data.get('identityDigest') != hashlib.sha256(expected_identity.encode()).hexdigest():
        raise ios.NativeFailure('integration', 'dual-result', 'actor-identity-mismatch')
    if data.get('storageDigest') != storage_digest or not DIGEST.fullmatch(data.get('matrixDeviceDigest', '')):
        raise ios.NativeFailure('integration', 'dual-result', 'native-storage-or-device-proof-missing')
    for scenario in SCENARIOS:
        proof = data.get('scenarios', {}).get(scenario)
        evidence = 'server-denial' if scenario == 'authorization' else 'flutter-ui'
        if scenario == 'authorization':
            if (not isinstance(proof, dict) or set(proof) != {'status', 'evidence', 'deniedIdentityDigest', 'denials'}
                    or proof['status'] != 'passed' or proof['evidence'] not in {'server-denial', 'flutter-ui'}
                    or not DIGEST.fullmatch(proof['deniedIdentityDigest'])
                    or proof['deniedIdentityDigest'] == data['identityDigest']
                    or proof['denials'] != {'chat': 'passed', 'files': 'passed', 'calendar': 'passed'}):
                raise ios.NativeFailure('integration', 'dual-authorization', 'third-identity-denial-proof-missing')
        elif proof != {'status': 'passed', 'evidence': evidence}:
            raise ios.NativeFailure('integration', 'dual-' + scenario, 'mandatory-actual-proof-missing')
    encryption = data.get('encryption')
    if (not isinstance(encryption, dict) or set(encryption) != {'policyRequired', 'status', 'plaintextFallback'}
            or type(encryption['policyRequired']) is not bool or encryption['plaintextFallback'] is not False
            or encryption['status'] != ('passed' if encryption['policyRequired'] else 'not-required')):
        raise ios.NativeFailure('integration', 'dual-encryption', 'room-policy-proof-missing')
    return data


def actor_runtime_copy(client: Path, destination: Path) -> None:
    """Separate mutable attach metadata; never run concurrent pub/build commands."""
    shutil.copytree(client, destination, ignore=shutil.ignore_patterns('build', '.git', '.dart_tool', 'private-*'))
    (destination / '.dart_tool').mkdir()
    for metadata in (client / '.dart_tool').iterdir():
        if metadata.is_file():
            shutil.copy2(metadata, destination / '.dart_tool' / metadata.name)
    # The generated package config uses relative roots; each actor needs its own
    # runtime metadata, while the expensive native bundle is built only once.
    if not (destination / '.dart_tool/package_config.json').is_file():
        raise ios.NativeFailure('infrastructure', 'dual-runtime', 'package-metadata-missing')


def run_actor(actor: dict, udid: str, runtime: Path, products: Path, temporary: Path, app: Path,
              input_data: dict, env: dict, build_digest: str, ready: queue.Queue) -> dict:
    role = actor['role']
    ios.run(['xcrun', 'simctl', 'install', udid, str(app)], env=env)
    container = Path(ios.run(['xcrun', 'simctl', 'get_app_container', udid, ios.BUNDLE, 'data'], env=env).strip()).resolve(strict=True)
    if f'/CoreSimulator/Devices/{udid}/' not in str(container):
        raise ios.NativeFailure('infrastructure', 'dual-storage', 'owned-container-mismatch')
    support = container / 'Library/Application Support'
    support.mkdir(parents=True, exist_ok=True)
    if support.is_symlink():
        raise ios.NativeFailure('infrastructure', 'dual-storage', 'unsafe-app-support')
    storage_digest = hashlib.sha256(str(container).encode()).hexdigest()
    documents = container / 'Documents'
    documents.mkdir(exist_ok=True)
    if documents.is_symlink():
        raise ios.NativeFailure('infrastructure', 'dual-files', 'unsafe-documents-path')
    initial_name = 'weave-dual-initial-' + input_data['runId'] + '.txt'
    replacement_name = 'weave-dual-replacement-' + input_data['runId'] + '.txt'
    initial_content = 'Weave actual UI initial ' + input_data['runId'] + '\n'
    replacement_content = 'Weave actual UI replacement ' + input_data['runId'] + '\n'
    if role == 'A':
        for name, content in ((initial_name, initial_content), (replacement_name, replacement_content)):
            (documents / name).write_text(content)
    else:
        (documents / '.weave-owned-fixture').write_text('Owned disposable document directory\n')
    runtime_fixture = {'role': role, 'entryUri': actor['entryUri'], 'runId': input_data['runId'],
                       'sharedSpaceRef': input_data['sharedSpaceRef'], 'storageDigest': storage_digest,
                       'peerDisplayName': 'Weave E2E Member' if role == 'A' else 'Weave E2E Owner',
                       'initialFileName': initial_name, 'replacementFileName': replacement_name,
                       'initialContent': initial_content, 'replacementContent': replacement_content}
    fixture_path = support / 'weave-dual-ios-input.json'
    fixture_path.write_text(json.dumps(runtime_fixture)); fixture_path.chmod(0o600)
    started_at = ios.simulator_vm.log_start_timestamp()
    launch = ios.run(['xcrun', 'simctl', 'launch', udid, ios.BUNDLE, '--enable-dart-profiling',
                      '--disable-vm-service-publication', '--start-paused'], env=env).strip()
    match = re.fullmatch(re.escape(ios.BUNDLE) + r': ([1-9][0-9]{0,9})', launch)
    if match is None:
        raise ios.NativeFailure('infrastructure', 'dual-launch', 'invalid-owned-process')
    pid = match.group(1)
    started = now()
    uri = ios.discover_owned_vm_service(udid, pid, started_at)
    plans = list(products.glob('*.xctestrun')); bundles = list(products.glob('**/BrowserDriver.xctest'))
    if len(plans) != 1 or len(bundles) != 1:
        raise ios.NativeFailure('infrastructure', 'dual-browser', 'missing-actor-test-plan')
    completion = ios.prepare_workspace_completion(temporary, 'initial')
    browser_fixture = dict(email=actor['email'], password=actor['password'], issuerHost='auth.weave.localhost',
                           appBundleIdentifier=ios.BUNDLE, phase='initial', nativeAuthCompletionPath=str(completion))
    private = bundles[0] / 'private-member.json'; private.write_text(json.dumps(browser_fixture)); private.chmod(0o600)
    flutter = driver = None
    succeeded = False
    with (temporary / 'flutter-initial.log').open('w+') as log, (temporary / 'browser-initial.log').open('w+') as browser_log:
        try:
            flutter = track_process(subprocess.Popen(['flutter', 'drive', '--no-pub', '--reporter', 'expanded', '--keep-app-running',
                                        '--driver=test_driver/integration_test.dart', '--target=integration_test/dual_ios_collaboration_test.dart',
                                        '--use-existing-app=' + uri, '-d', udid], cwd=runtime, env=env,
                                       stdout=log, stderr=subprocess.STDOUT, start_new_session=True))
            ios.wait_for_flutter_shell(flutter, temporary / 'flutter-initial.log', 'initial')
            barrier = ios.simulator_driver_barrier(udid, input_data['runId'])
            driver = track_process(subprocess.Popen(['xcodebuild', 'test-without-building', '-xctestrun', str(plans[0]),
                                       '-destination', 'platform=iOS Simulator,id=' + udid,
                                       '-parallel-testing-enabled', 'NO', '-resultBundlePath', str(temporary / 'browser.xcresult')],
                                      env=env, stdout=browser_log, stderr=subprocess.STDOUT, start_new_session=True))
            ios.release_driver_barrier(flutter, driver, temporary / 'browser-initial.log', udid, pid, barrier, 'initial')
            if not ios.relay_workspace_completion(flutter, driver, temporary / 'flutter-initial.log', completion, 'initial'):
                raise ios.NativeFailure('integration', 'dual-authentication', 'workspace-not-observed')
            if driver.wait(timeout=420) != 0:
                raise ios.NativeFailure('integration', 'dual-authentication', 'browser-failed')
            ready.put((role, support, udid, pid, products))
            # Flutter keeps each real app alive through all concurrent UI steps;
            # its complete result is mandatory, not merely browser submission.
            if flutter.wait(timeout=1800) != 0:
                raise ios.NativeFailure('integration', 'dual-product', 'flutter-ui-failed')
            if ios.simulator_app_pid(udid) != pid:
                raise ios.NativeFailure('infrastructure', 'dual-process', 'owned-process-replaced')
            proof = actor_result(support / 'weave-dual-ios-result.json', role, actor['identityRef'], storage_digest)
            succeeded = True
            return {'role': role, 'simulatorId': udid, 'processId': int(pid), 'identityDigest': proof['identityDigest'],
                    'matrixDeviceDigest': proof['matrixDeviceDigest'], 'storageDigest': storage_digest,
                    'startedAt': started, 'endedAt': now(), 'scenarios': proof['scenarios'], 'encryption': proof['encryption']}
        finally:
            try:
                if not succeeded:
                    for command, process, path in (('flutter', flutter, temporary / 'flutter-initial.log'),
                                                   ('xcodebuild', driver, temporary / 'browser-initial.log')):
                        contents = path.read_text(errors='replace')
                        if contents:
                            diagnostic = ios.retain_private_diagnostic(contents)
                            status = process.poll() if process is not None else None
                            code = str(status) if isinstance(status, int) and -999 <= status <= 999 else 'unavailable'
                            print(f'NATIVE_IOS_COMMAND_RESULT status=failed command={command} phase=initial exitCode={code} privateDiagnostic={diagnostic.name}', flush=True)
            finally:
                try:
                    if driver is not None:
                        stop_flutter_process_group(driver)
                finally:
                    try:
                        if flutter is not None:
                            stop_flutter_process_group(flutter)
                    finally:
                        ios.emit_phase_stages('initial', (temporary / 'flutter-initial.log').read_text(), (temporary / 'browser-initial.log').read_text())


def run_ui_commands(scopes: dict, tasks: list, env: dict, input_data: dict) -> None:
    # Existing Python runner coordinates actual Flutter gestures using only
    # owned private files. Commands cannot inject results/auth/session data.
    steps = [('A', 'chat-create-send'), ('B', 'chat-open-reply'), ('A', 'chat-observe'),
             ('A', 'calendar-create'), ('B', 'calendar-observe'),
             ('A', 'calendar-update'), ('B', 'calendar-observe-updated'),
             ('A', 'files-upload'), ('A', 'files-export'), ('B', 'files-open'), ('B', 'files-export'),
             ('A', 'files-replace'), ('A', 'files-export-updated'), ('B', 'files-open-updated'), ('B', 'files-export-updated'),
             ('A', 'authorization'), ('B', 'authorization'),
             ('A', 'calendar-delete'), ('B', 'calendar-observe-deleted'),
             ('A', 'recovery'), ('B', 'recovery'), ('A', 'finish'), ('B', 'finish')]
    def verify_both_live():
        for _, udid, pid, _products in scopes.values():
            if ios.simulator_app_pid(udid) != pid:
                raise ios.NativeFailure('infrastructure', 'dual-process', 'owned-process-replaced')
    for sequence, (role, action) in enumerate(steps, 1):
        verify_both_live()
        support, udid, pid, products = scopes[role]
        if action == 'authorization' and role == 'A':
            authorize_ui_resources(scopes, input_data)
        if action in {'files-export', 'files-export-updated'}:
            # Remove only our own source/export fixture, with exact byte proof;
            # otherwise an existing local file could fake a successful download.
            destination = support.parent.parent / 'Documents' / ('weave-dual-initial-' + input_data['runId'] + '.txt')
            if destination.is_symlink():
                raise ios.NativeFailure('infrastructure', 'dual-files', 'unsafe-export-path')
            if destination.exists():
                expected = 'Weave actual UI initial ' + input_data['runId'] + '\n'
                if destination.read_text() != expected:
                    raise ios.NativeFailure('integration', 'dual-files', 'prior-owned-document-mismatch')
                destination.unlink()
        target = support / 'weave-dual-ios-command.json'
        staging = support / 'weave-dual-ios-command.tmp'
        if target.is_symlink() or staging.is_symlink():
            raise ios.NativeFailure('infrastructure', 'dual-command', 'unsafe-private-command')
        staging.write_text(json.dumps({'sequence': sequence, 'action': action})); staging.chmod(0o600)
        staging.replace(target)
        deadline = time.monotonic() + 120
        picker_executed = False
        while True:
            for task in tasks:
                if task.done(): task.result()
            native_request = support / 'weave-dual-ios-native-request.json'
            if not picker_executed and native_request.is_file() and not native_request.is_symlink() and native_request.stat().st_size <= 1024:
                request = json.loads(native_request.read_text())
                if request.get('sequence') == sequence:
                    mode, name = require_picker_request(request, sequence, action, input_data['runId'])
                    plans = list(products.glob('*.xctestrun')); bundles = list(products.glob('**/BrowserDriver.xctest'))
                    if len(plans) != 1 or len(bundles) != 1:
                        raise ios.NativeFailure('infrastructure', 'dual-files', 'missing-owned-test-plan')
                    fixture = bundles[0] / 'private-member.json'
                    fixture.write_text(json.dumps({'phase': 'document-picker', 'pickerMode': mode, 'fileName': name})); fixture.chmod(0o600)
                    ios.run(['xcodebuild', 'test-without-building', '-xctestrun', str(plans[0]),
                             '-destination', 'platform=iOS Simulator,id=' + udid, '-parallel-testing-enabled', 'NO',
                             '-only-testing:BrowserDriver/BrowserDriver/testNativeDocumentPicker'], env=env, timeout=90)
                    verify_both_live()
                    picker_executed = True
            ack = support / 'weave-dual-ios-ack.json'
            if ack.is_file() and not ack.is_symlink() and ack.stat().st_size <= 1024:
                proof = json.loads(ack.read_text())
                if proof == {'sequence': sequence, 'action': action, 'status': 'passed'}:
                    verify_both_live()
                    print(f'NATIVE_IOS_DUAL_UI_RESULT actor={role} action={action} status=passed supportSafe=true', flush=True)
                    break
            if time.monotonic() >= deadline:
                raise ios.NativeFailure('integration', 'dual-' + action.split('-')[0], 'actual-ui-command-proof-missing')
            time.sleep(0.25)


def publish_private_json(path: Path, value: dict) -> None:
    """Publish complete owner-only bytes without replacing any existing file."""
    staging = path.with_suffix('.tmp')
    descriptor = os.open(staging, os.O_WRONLY | os.O_CREAT | os.O_EXCL | os.O_NOFOLLOW, 0o600)
    try:
        with os.fdopen(descriptor, 'w') as output:
            json.dump(value, output)
            output.flush()
            os.fsync(output.fileno())
        os.link(staging, path, follow_symlinks=False)
    finally:
        staging.unlink()


def require_picker_request(request: dict, sequence: int, action: str, run_id: str) -> tuple[str, str]:
    if action not in {'files-upload', 'files-replace', 'files-export', 'files-export-updated'}:
        raise ios.NativeFailure('infrastructure', 'dual-files', 'unsafe-native-picker-request')
    mode = 'export' if action in {'files-export', 'files-export-updated'} else 'import'
    name = ('weave-dual-replacement-' if action == 'files-replace' else 'weave-dual-initial-') + run_id + '.txt'
    if request != {'sequence': sequence, 'mode': mode, 'fileName': name}:
        raise ios.NativeFailure('infrastructure', 'dual-files', 'unsafe-native-picker-request')
    return mode, name


def authorize_ui_resources(scopes: dict, input_data: dict) -> None:
    partial = [json.loads((scopes[role][0] / 'weave-dual-ios-result.json').read_text()) for role in ('A', 'B')]
    for index, role in enumerate(('A', 'B')):
        identity = hashlib.sha256(input_data['actors'][index]['identityRef'].encode()).hexdigest()
        if partial[index].get('role') != role or partial[index].get('identityDigest') != identity:
            raise ios.NativeFailure('integration', 'dual-authorization', 'owned-ui-identity-mismatch')
    if partial[0]['resources'] != partial[1]['resources']:
        raise ios.NativeFailure('integration', 'dual-authorization', 'shared-ui-resource-mismatch')
    parent = ios.verified_private_parent()
    request = parent / ('dual-ios-authorization-request-' + input_data['runId'] + '.json')
    proof = parent / ('dual-ios-authorization-proof-' + input_data['runId'] + '.json')
    if request.exists() or request.is_symlink() or proof.exists() or proof.is_symlink():
        raise ios.NativeFailure('infrastructure', 'dual-authorization', 'stale-private-denial-exchange')
    content = {'runId': input_data['runId'], 'spaceRef': input_data['sharedSpaceRef'],
               'actorA': partial[0]['identityDigest'], 'actorB': partial[1]['identityDigest'], 'resources': partial[0]['resources']}
    publish_private_json(request, content)
    deadline = time.monotonic() + 120
    while not proof.exists():
        if time.monotonic() >= deadline:
            raise ios.NativeFailure('integration', 'dual-authorization', 'actual-server-denial-proof-missing')
        time.sleep(0.25)
    if proof.is_symlink() or proof.stat().st_size > 1024 or proof.stat().st_mode & 0o777 != 0o600:
        raise ios.NativeFailure('infrastructure', 'dual-authorization', 'unsafe-private-denial-proof')
    denial = json.loads(proof.read_text())
    expected = {'status': 'passed', 'evidence': 'server-denial',
                'deniedIdentityDigest': hashlib.sha256(input_data['unauthorized']['identityRef'].encode()).hexdigest(),
                'denials': {'chat': 'passed', 'files': 'passed', 'calendar': 'passed'}}
    if denial != expected or denial['deniedIdentityDigest'] in {actor['identityDigest'] for actor in partial}:
        raise ios.NativeFailure('integration', 'dual-authorization', 'third-identity-denial-proof-missing')
    for support, udid, pid, _products in scopes.values():
        if ios.simulator_app_pid(udid) != pid:
            raise ios.NativeFailure('infrastructure', 'dual-authorization', 'owned-process-replaced')
        destination = support / 'weave-dual-ios-denial.json'
        publish_private_json(destination, denial)


def qualify(input_path: Path, output_path: Path, env: dict) -> int:
    _CANCELLED.clear()
    with _ACTIVE_LOCK:
        _ACTIVE.clear()
    input_data = private_input(input_path, env['WEAVE_E2E_RUN_ID'])
    evidence = {'status': 'failed', 'actors': [], 'scenarios': {name: {'status': 'not-run'} for name in SCENARIOS},
                'encryption': {'policyRequired': True, 'status': 'not-run', 'plaintextFallback': False}, 'cleanupComplete': False}
    devices = []
    relays = {}
    try:
        with ios.private_run_directory() as temporary:
            runtime, device = ios.select_runtime()
            for role in ('A', 'B'):
                udid = ios.run(['xcrun', 'simctl', 'create', temporary.name + '-' + role, device, runtime]).strip()
                if not UUID.fullmatch(udid):
                    raise ios.NativeFailure('infrastructure', 'dual-device', 'invalid-owned-simulator')
                devices.append(udid)
                ios.run(['xcrun', 'simctl', 'boot', udid]); ios.run(['xcrun', 'simctl', 'bootstatus', udid, '-b'], timeout=180)
                ios.run(['xcrun', 'simctl', 'keychain', udid, 'add-root-cert', env['WEAVE_NATIVE_CA']])
            defines = temporary / 'defines.json'
            defines.write_text(json.dumps({'WEAVE_NATIVE_EXPECT_SIMULATOR': True, 'WEAVE_DEVICE_DISPOSABLE_STACK': True,
                                          'WEAVE_NATIVE_TEST_RUN_ID': input_data['runId']})); defines.chmod(0o600)
            ios.run(['flutter', 'pub', 'get', '--enforce-lockfile'], env=env)
            ios.run(['flutter', 'build', 'ios', '--simulator', '--debug', '--target=integration_test/dual_ios_collaboration_test.dart',
                     '--dart-define-from-file=' + str(defines)], env=env, timeout=1800)
            app = ios.CLIENT / 'build/ios/iphonesimulator/Runner.app'; evidence['appBuildSha256'] = app_build_digest(app)
            derived = temporary / 'derived'
            ios.run(['xcodebuild', 'build-for-testing', '-project', str(ios.CLIENT / 'tool/ios_browser_driver/BrowserDriver.xcodeproj'),
                     '-scheme', 'BrowserDriver', '-destination', 'platform=iOS Simulator,id=' + devices[0],
                     '-derivedDataPath', str(derived), 'CODE_SIGNING_ALLOWED=NO'], env=env)
            authorities = proxy_authorities(input_data, env)
            for role in ('A', 'B'):
                relays[role] = OwnedFaultRelay(authorities)
            ready = queue.Queue()
            with concurrent.futures.ThreadPoolExecutor(max_workers=2) as executor:
                try:
                    tasks = []
                    for actor, udid in zip(input_data['actors'], devices):
                        scope = temporary / actor['role']; scope.mkdir(mode=0o700)
                        products = scope / 'Build/Products'; shutil.copytree(derived / 'Build/Products', products)
                        runtime_client = scope / 'client'; actor_runtime_copy(ios.CLIENT, runtime_client)
                        tasks.append(executor.submit(run_actor, actor, udid, runtime_client, products, scope, app, input_data, actor_proxy_environment(env, relays[actor['role']]), evidence['appBuildSha256'], ready))
                    scopes = {}
                    deadline = time.monotonic() + 540
                    while len(scopes) < 2:
                        if time.monotonic() >= deadline:
                            raise ios.NativeFailure('integration', 'dual-authentication', 'concurrent-actor-readiness-timeout')
                        for task in tasks:
                            if task.done():
                                task.result()
                        try:
                            role, support, udid, pid, products = ready.get(timeout=0.25)
                        except queue.Empty:
                            continue
                        scopes[role] = (support, udid, pid, products)
                    run_ui_commands(scopes, tasks, env, input_data)
                    evidence['actors'] = [task.result() for task in tasks]
                except BaseException:
                    stop_owned_processes()
                    raise
            actors = evidence['actors']
            for key in ('simulatorId', 'processId', 'identityDigest', 'matrixDeviceDigest', 'storageDigest'):
                if actors[0][key] == actors[1][key]:
                    raise ios.NativeFailure('integration', 'dual-isolation', 'actor-identity-collision')
            started = max(actor['startedAt'] for actor in actors)
            ended = min(actor['endedAt'] for actor in actors)
            if datetime.fromisoformat(started) >= datetime.fromisoformat(ended):
                raise ios.NativeFailure('integration', 'dual-concurrency', 'live-overlap-missing')
            evidence['overlap'] = {'startedAt': started, 'endedAt': ended}
            evidence['scenarios'] = {name: {'status': 'passed', 'evidence': 'server-denial' if name == 'authorization' else 'flutter-ui',
                                           'actors': ['A', 'B']} for name in SCENARIOS}
            denial = actors[0]['scenarios']['authorization']
            if (denial != actors[1]['scenarios']['authorization']
                    or denial['deniedIdentityDigest'] in {actor['identityDigest'] for actor in actors}):
                raise ios.NativeFailure('integration', 'dual-authorization', 'third-identity-denial-proof-missing')
            evidence['scenarios']['authorization'].update(denial)
            # Room-policy proof must be supplied by both real Flutter journeys.
            if actors[0]['encryption'] != actors[1]['encryption']:
                raise ios.NativeFailure('integration', 'dual-encryption', 'room-policy-proof-missing')
            evidence['encryption'] = actors[0]['encryption']
            evidence['actors'] = [{key: value for key, value in actor.items() if key not in {'scenarios', 'encryption'}}
                                  for actor in actors]
            evidence['status'] = 'passed'
            return 0
    finally:
        relay_cleanup = True
        for relay in relays.values():
            try: relay.close()
            except BaseException: relay_cleanup = False
        evidence['cleanupComplete'] = cleanup_owned_devices(devices) and relay_cleanup
        if not evidence['cleanupComplete']:
            evidence['status'] = 'failed'
        output_path.write_text(json.dumps(evidence, indent=2) + '\n')
        output_path.chmod(0o600)
        if not evidence['cleanupComplete']:
            raise ios.NativeFailure('infrastructure', 'dual-cleanup', 'owned-resource-cleanup-unverified')


def main() -> int:
    import argparse
    import platform
    import signal
    parser = argparse.ArgumentParser()
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    def interrupted(number, _frame):
        stop_owned_processes()
        raise SystemExit(128 + number)
    for number in (signal.SIGINT, signal.SIGTERM):
        signal.signal(number, interrupted)
    if platform.system() != 'Darwin' or platform.machine() != 'arm64':
        raise ios.NativeFailure('infrastructure', 'dual-host', 'apple-silicon-required')
    toolchain = ios.inspect_toolchain()
    parent = ios.verified_private_parent()
    run_id = os.environ['WEAVE_E2E_RUN_ID']
    if not re.fullmatch(r'[A-Za-z0-9-]{1,40}', run_id):
        raise ios.NativeFailure('infrastructure', 'dual-fixture', 'invalid-owned-run-id')
    verify_result_path(args.output, parent, run_id)
    rustup = shutil.which('rustup')
    if rustup is None:
        raise ios.NativeFailure('infrastructure', 'dual-toolchain', 'pinned-rustup-missing')
    env = os.environ.copy()
    env['RUSTUP_TOOLCHAIN'] = toolchain['rustToolchain']
    env['PATH'] = os.pathsep.join([str(ios.ROOT / 'tools/ios-simulator-xcrun'), str(Path(rustup).parent), env['PATH']])
    for name in ('WEAVE_NATIVE_MEMBER_EMAIL', 'WEAVE_NATIVE_MEMBER_PASSWORD'):
        env.pop(name, None)
    return qualify(parent / ('dual-ios-input-' + run_id + '.json'), args.output, env)


if __name__ == '__main__':
    try:
        raise SystemExit(main())
    except ios.NativeFailure as failure:
        ios.failure_marker(failure.category, failure.stage, failure.reason)
        raise SystemExit(1)
    except (RuntimeError, OSError, ValueError, KeyError, subprocess.TimeoutExpired):
        ios.failure_marker('infrastructure', 'dual-runner', 'qualification-runtime-failed')
        raise SystemExit(1)
