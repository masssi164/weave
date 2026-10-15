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
import signal
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
        self.forwarded_bytes = 0
        self.blocked = 0
        self.rejected = 0
        self.errors = {name: 0 for name in ('connect-read-timeout', 'connect-read-error',
                       'upstream-connect-timeout', 'upstream-connect-error',
                       'forwarding-timeout', 'forwarding-error')}
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
        stage = 'connect-read'
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
            stage = 'upstream-connect'
            upstream = socket.create_connection((host, int(port)), timeout=10)
            with self._lock:
                self._sockets.add(upstream)
                self.forwarded += 1
            if self._blocked.is_set():
                with self._lock: self.blocked += 1
                return
            stage = 'forwarding'
            client.sendall(b'HTTP/1.1 200 Connection Established\r\n\r\n')
            client.setblocking(False); upstream.setblocking(False)
            while not self._stop.is_set() and not self._blocked.is_set():
                readable, _, _ = select.select([client, upstream], [], [], 0.25)
                for source in readable:
                    data = source.recv(65536)
                    if not data: return
                    destination = upstream if source is client else client
                    destination.settimeout(5); destination.sendall(data); destination.setblocking(False)
                    with self._lock: self.forwarded_bytes += len(data)
        except socket.timeout:
            with self._lock: self.errors[stage + '-timeout'] += 1
        except (OSError, ValueError):
            with self._lock: self.errors[stage + '-error'] += 1
            # Connection loss is the actual fault; raw traffic is never logged.
        finally:
            for connection in (client, upstream):
                if connection is not None:
                    with self._lock: self._sockets.discard(connection)
                    connection.close()

    def diagnostic(self, role: str) -> str:
        if role not in {'A', 'B'}:
            raise ValueError('Unknown private actor role')
        with self._lock:
            counters = {'connections': self.forwarded, 'forwardedBytes': self.forwarded_bytes,
                        'blocked': self.blocked, 'rejected': self.rejected,
                        'activeSockets': len(self._sockets), **self.errors}
        # Fixed counters only: never addresses, CONNECT headers, TLS bytes or errors.
        fields = ' '.join(key + '=' + str(min(999999999999, max(0, value))) for key, value in counters.items())
        return 'NATIVE_IOS_RELAY_DIAGNOSTIC actor=' + role + ' ' + fields

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
    if not isinstance(data.get('nativeOrganizationId'), str) or not data['nativeOrganizationId'].strip():
        raise ios.NativeFailure('infrastructure', 'dual-fixture', 'native-organization-binding-required')
    if not isinstance(data.get('organizationId'), str) or not data['organizationId'].strip():
        raise ios.NativeFailure('infrastructure', 'dual-fixture', 'organization-binding-required')
    actors = data.get('actors', [])
    if (data.get('schemaVersion') != 'weave.dual-ios-input/v1' or data.get('runId') != run_id
            or len(actors) != 2 or [a.get('role') for a in actors] != ['A', 'B']
            or any(not a.get('email') or not a.get('password') or not a.get('identityRef')
                   or not isinstance(a.get('displayName'), str) or not a['displayName'].strip() for a in actors)
            or actors[0]['displayName'] == actors[1]['displayName']
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
    fields = parse_qsl(entry.query, keep_blank_values=True)
    joined = urlsplit(fields[0][1]) if len(fields) == 1 and fields[0][0] == 'organization_origin' else None
    if (entry.scheme != 'weave' or entry.netloc != 'join' or entry.path or entry.fragment or '&' in entry.query
            or joined is None or joined.scheme != parsed.scheme or joined.netloc != parsed.netloc
            or joined.path not in ('', '/') or joined.query or joined.fragment or joined.username is not None):
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
            if (not isinstance(proof, dict) or set(proof) != {'status', 'evidence', 'deniedIdentityDigest', 'resourceDigest', 'denials'}
                    or proof['status'] != 'passed' or proof['evidence'] not in {'server-denial', 'flutter-ui'}
                    or not DIGEST.fullmatch(proof['deniedIdentityDigest'])
                    or not DIGEST.fullmatch(proof['resourceDigest'])
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


def recovery_actor_proof(proof: dict, initial_pid: str, restored_pid: str,
                         initial_started: str, initial_ended: str, restored_started: str, restored_ended: str) -> dict:
    state = proof.get('recovery', {})
    required = ['network', 'background', 'processRestored', 'persistentReadback', 'expiryRefresh', 'logoutIndependent']
    if (any(state.get(name) is not True for name in required)
            or (proof['role'] == 'B' and state.get('grantRegrantReadback') is not True)
            or not re.fullmatch(r'[1-9][0-9]{0,9}', initial_pid)
            or not re.fullmatch(r'[1-9][0-9]{0,9}', restored_pid) or initial_pid == restored_pid):
        raise ios.NativeFailure('integration', 'dual-recovery', 'actual-recovery-proof-missing')
    try:
        times = [datetime.fromisoformat(value) for value in (initial_started, initial_ended, restored_started, restored_ended)]
        if any(value.utcoffset() is None for value in times) or not times[0] < times[1] <= times[2] < times[3]:
            raise ValueError()
    except (TypeError, ValueError):
        raise ios.NativeFailure('infrastructure', 'dual-recovery', 'actual-process-interval-invalid') from None
    return {'role': proof['role'], 'initialProcessId': int(initial_pid), 'restoredProcessId': int(restored_pid),
            'initialStartedAt': initial_started, 'initialEndedAt': initial_ended,
            'restoredStartedAt': restored_started, 'restoredEndedAt': restored_ended, 'previousProcessAbsent': True,
            'restoredIdentityDigest': proof['identityDigest'], 'restoredMatrixDeviceDigest': proof['matrixDeviceDigest'],
            'restoredStorageDigest': proof['storageDigest'], 'expiryDenied': state['expiryRefresh'],
            'refreshObserved': state['expiryRefresh'], 'logoutIndependent': state['logoutIndependent']}


def flutter_drive_command(uri: str, udid: str) -> list[str]:
    # drive uses a host integration driver, not flutter test's reporter flags.
    # Live safe target markers already bypass test-zone buffering.
    return ['flutter', 'drive', '--no-pub', '--keep-app-running',
            '--driver=test_driver/integration_test.dart',
            '--target=integration_test/dual_ios_collaboration_test.dart',
            '--use-existing-app=' + uri, '-d', udid]


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


def prepare_actor_storage(actor: dict, udid: str, app: Path, input_data: dict, env: dict) -> tuple[Path, dict]:
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
                       'organizationOrigin': input_data['publicOrigin'], 'nativeOrganizationId': input_data['nativeOrganizationId'],
                       'identityIssuer': env['WEAVE_NATIVE_ISSUER'],
                       'identityDigestExpected': hashlib.sha256(actor['identityRef'].encode()).hexdigest(),
                       'sharedSpaceRef': input_data['sharedSpaceRef'], 'storageDigest': storage_digest,
                       'peerDisplayName': next(peer['displayName'] for peer in input_data['actors'] if peer['role'] != role),
                       'initialFileName': initial_name, 'replacementFileName': replacement_name,
                       'initialContent': initial_content, 'replacementContent': replacement_content}
    fixture_path = support / 'weave-dual-ios-input.json'
    fixture_path.write_text(json.dumps(runtime_fixture)); fixture_path.chmod(0o600)
    publish_private_json(support / 'weave-dual-ios-result.json', {})
    if env.get('WEAVE_NATIVE_LOCAL_INSPECTION') == 'true':
        publish_private_json(support / 'weave-local-inspection-state.json', {})
    return support, runtime_fixture


def run_actor(actor: dict, udid: str, runtime: Path, products: Path, temporary: Path, app: Path,
              input_data: dict, env: dict, build_digest: str, ready: queue.Queue, restart_barrier: threading.Barrier, restart_gate: threading.Event) -> dict:
    role = actor['role']
    support, runtime_fixture = prepare_actor_storage(actor, udid, app, input_data, env)
    fixture_path = support / 'weave-dual-ios-input.json'
    storage_digest = hashlib.sha256(str(support.parent.parent).encode()).hexdigest()
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
                           appBundleIdentifier=ios.BUNDLE, phase='initial', nativeAuthCompletionPath=str(completion),
                           flutterInitiatesSignIn=True)
    private = bundles[0] / 'private-member.json'; private.write_text(json.dumps(browser_fixture)); private.chmod(0o600)
    flutter = driver = None
    succeeded = False
    with (temporary / 'flutter-initial.log').open('w+') as log, (temporary / 'browser-initial.log').open('w+') as browser_log:
        try:
            flutter = track_process(subprocess.Popen(flutter_drive_command(uri, udid), cwd=runtime, env=env,
                                       stdout=log, stderr=subprocess.STDOUT, start_new_session=True))
            ios.wait_for_flutter_shell(flutter, temporary / 'flutter-initial.log', 'initial')
            barrier = ios.simulator_driver_barrier(udid, input_data['runId'])
            driver = track_process(subprocess.Popen(['xcodebuild', 'test-without-building', '-xctestrun', str(plans[0]),
                                       '-destination', 'platform=iOS Simulator,id=' + udid,
                                       '-parallel-testing-enabled', 'NO', '-resultBundlePath', str(temporary / 'browser.xcresult')],
                                      env=env, stdout=browser_log, stderr=subprocess.STDOUT, start_new_session=True))
            ios.release_driver_barrier(flutter, driver, temporary / 'browser-initial.log', udid, pid, barrier, 'initial', require_sign_in_observation=True)
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
            checkpoint = json.loads((support / 'weave-dual-ios-ack.json').read_text())
            if checkpoint.get('action') != 'restart-checkpoint' or checkpoint.get('status') != 'passed':
                raise ios.NativeFailure('integration', 'dual-process-restart', 'actual-ui-checkpoint-missing')
            stop_flutter_process_group(flutter); flutter = None
            stop_flutter_process_group(driver); driver = None
            # Both actual initial UIs finish before either app is stopped. The
            # same signed binary restores its own production Keychain/profile.
            deadline = time.monotonic() + 120
            while not restart_gate.wait(timeout=0.25):
                if _CANCELLED.is_set() or time.monotonic() >= deadline:
                    raise ios.NativeFailure('infrastructure', 'dual-process-restart', 'restart-coordination-unavailable')
            restart_barrier.wait(timeout=120)
            old_pid = pid
            initial_started = started
            initial_ended = now()
            ios.terminate_initial_app(udid, old_pid)
            runtime_fixture.update({'phase': 'restore', 'lastSequence': checkpoint['sequence']})
            staging = fixture_path.with_name('weave-dual-ios-restore.json')
            publish_private_json(staging, runtime_fixture)
            staging.replace(fixture_path)
            started_at = ios.simulator_vm.log_start_timestamp()
            launch = ios.run(['xcrun', 'simctl', 'launch', udid, ios.BUNDLE, '--enable-dart-profiling',
                              '--disable-vm-service-publication', '--start-paused'], env=env).strip()
            match = re.fullmatch(re.escape(ios.BUNDLE) + r': ([1-9][0-9]{0,9})', launch)
            if match is None or match[1] == old_pid:
                raise ios.NativeFailure('infrastructure', 'dual-process-restart', 'distinct-owned-process-missing')
            pid = match[1]
            if ios.simulator_app_pid(udid) != pid:
                raise ios.NativeFailure('infrastructure', 'dual-process-restart', 'restored-process-identity-changed')
            started = now()
            uri = ios.discover_owned_vm_service(udid, pid, started_at)
            with (temporary / 'flutter-restore.log').open('w+') as restored_log:
                flutter = track_process(subprocess.Popen(flutter_drive_command(uri, udid), cwd=runtime, env=env,
                                           stdout=restored_log, stderr=subprocess.STDOUT, start_new_session=True))
                ios.wait_for_flutter_shell(flutter, temporary / 'flutter-restore.log', 'restore')
                ready.put((role, support, udid, pid, products))
                if flutter.wait(timeout=900) != 0:
                    raise ios.NativeFailure('integration', 'dual-restored-product', 'flutter-ui-failed')
            if ios.simulator_app_pid(udid) != pid:
                raise ios.NativeFailure('infrastructure', 'dual-process-restart', 'restored-process-replaced')
            proof = actor_result(support / 'weave-dual-ios-result.json', role, actor['identityRef'], storage_digest)
            ended = now()
            recovery = recovery_actor_proof(proof, old_pid, pid, initial_started, initial_ended, started, ended)
            succeeded = True
            return {'role': role, 'simulatorId': udid, 'processId': int(old_pid), 'identityDigest': proof['identityDigest'],
                    'matrixDeviceDigest': proof['matrixDeviceDigest'], 'storageDigest': storage_digest,
                    'startedAt': initial_started, 'endedAt': initial_ended, 'scenarios': proof['scenarios'], 'encryption': proof['encryption'],
                    'recovery': recovery}
        finally:
            try:
                if not succeeded:
                    for command, process, path in (('flutter', flutter, temporary / 'flutter-initial.log'),
                                                   ('flutter', flutter, temporary / 'flutter-restore.log'),
                                                   ('xcodebuild', driver, temporary / 'browser-initial.log')):
                        if not path.is_file(): continue
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
                        if (temporary / 'flutter-restore.log').is_file():
                            ios.emit_phase_stages('restore', (temporary / 'flutter-restore.log').read_text(), '')


def perform_owned_lifecycle(scope: tuple, action: str, env: dict, *, inspection_sequence: int | None = None) -> None:
    support, udid, pid, products = scope
    if action not in {'background', 'foreground'} or ios.simulator_app_pid(udid) != pid:
        raise ios.NativeFailure('infrastructure', 'dual-lifecycle', 'owned-process-mismatch')
    plans = list(products.glob('*.xctestrun')); bundles = list(products.glob('**/BrowserDriver.xctest'))
    if len(plans) != 1 or len(bundles) != 1:
        raise ios.NativeFailure('infrastructure', 'dual-lifecycle', 'owned-test-plan-missing')
    private = bundles[0] / 'private-member.json'
    private.write_text(json.dumps({'phase': 'lifecycle', 'action': action})); private.chmod(0o600)
    ios.run(['xcodebuild', 'test-without-building', '-xctestrun', str(plans[0]),
             '-destination', 'platform=iOS Simulator,id=' + udid, '-parallel-testing-enabled', 'NO',
             '-only-testing:BrowserDriver/BrowserDriver/testNativeLifecycle',
             '-resultBundlePath', str(products.parent.parent / ('lifecycle-' + action + (('-' + str(inspection_sequence)) if inspection_sequence is not None else '') + '.xcresult'))], env=env, timeout=90)
    if ios.simulator_app_pid(udid) != pid:
        raise ios.NativeFailure('infrastructure', 'dual-lifecycle', 'owned-process-replaced')


def run_ui_commands(scopes: dict, tasks: list, env: dict, input_data: dict, phase: str = 'initial', relays: dict | None = None) -> dict:
    # Existing Python runner coordinates actual Flutter gestures using only
    # owned private files. Commands cannot inject results/auth/session data.
    steps = [('A', 'chat-create-send'), ('B', 'chat-open-reply'), ('A', 'chat-observe'),
             ('A', 'calendar-create'), ('B', 'calendar-observe'),
             ('A', 'calendar-update'), ('B', 'calendar-observe-updated'),
             ('A', 'files-upload'), ('A', 'files-export'), ('B', 'files-open'), ('B', 'files-export'),
             ('A', 'files-replace'), ('A', 'files-export-updated'), ('B', 'files-open-updated'), ('B', 'files-export-updated'),
             ('A', 'authorization'), ('B', 'authorization'),
             ('A', 'calendar-delete'), ('B', 'calendar-observe-deleted'),
             ('A', 'network-ready'), ('B', 'network-ready'),
             ('A', 'network-disconnected'), ('B', 'network-peer-online'),
             ('A', 'network-resume'), ('B', 'network-peer-readback'),
             ('B', 'background-peer-send'), ('A', 'background-catchup'),
             ('A', 'restart-checkpoint'), ('B', 'restart-checkpoint')]
    start_sequence = 1
    if phase == 'restore':
        start_sequence = len(steps) + 1
        steps = [('A', 'restore-readback'), ('B', 'restore-readback'), ('B', 'grant-revocation-readback'),
                 ('A', 'expiry-refresh'), ('B', 'expiry-refresh'), ('A', 'logout'), ('B', 'peer-logout-independent'),
                 ('A', 'recovery'), ('B', 'recovery'), ('A', 'finish'), ('B', 'finish')]
    network = {}
    peer_bytes_before = 0
    restored_bytes_before = 0
    def verify_both_live():
        for _, udid, pid, _products in scopes.values():
            if ios.simulator_app_pid(udid) != pid:
                raise ios.NativeFailure('infrastructure', 'dual-process', 'owned-process-replaced')
    for sequence, (role, action) in enumerate(steps, start_sequence):
        verify_both_live()
        support, udid, pid, products = scopes[role]
        if action == 'network-disconnected':
            if relays is None or any(relay.forwarded_bytes == 0 for relay in relays.values()):
                raise ios.NativeFailure('infrastructure', 'dual-network', 'actual-actor-transport-not-observed')
            network['actorAForwardedBytes'] = relays['A'].forwarded_bytes
            relays['A'].interrupt()
            network['affectedConnections'] = relays['A'].blocked
        if action == 'network-peer-online': peer_bytes_before = relays['B'].forwarded_bytes
        if action == 'network-resume':
            restored_bytes_before = relays['A'].forwarded_bytes
            relays['A'].restore()
        if action == 'background-peer-send': perform_owned_lifecycle(scopes['A'], 'background', env)
        if action == 'background-catchup': perform_owned_lifecycle(scopes['A'], 'foreground', env)
        if action == 'grant-revocation-readback': network['currentGrantRevocation'] = authorize_ui_resources(scopes, input_data, kind='grant-revocation')
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
        deadline = time.monotonic() + (420 if action == 'expiry-refresh' else 180)
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
                    picker_stdout = ios.run(['xcodebuild', 'test-without-building', '-xctestrun', str(plans[0]),
                             '-destination', 'platform=iOS Simulator,id=' + udid, '-parallel-testing-enabled', 'NO',
                             '-only-testing:BrowserDriver/BrowserDriver/testNativeDocumentPicker'], env=env, timeout=90)
                    try:
                        diagnostic = ios.retain_private_diagnostic(picker_stdout)
                        print('NATIVE_IOS_PICKER_RESULT status=passed privateDiagnostic=' + diagnostic.name + ' supportSafe=true', flush=True)
                    except (OSError, ValueError, ios.NativeFailure):
                        print('NATIVE_IOS_PICKER_RESULT status=unavailable supportSafe=true', flush=True)
                    verify_both_live()
                    picker_executed = True
            ack = support / 'weave-dual-ios-ack.json'
            if ack.is_file() and not ack.is_symlink() and ack.stat().st_size <= 1024:
                proof = json.loads(ack.read_text())
                if proof == {'sequence': sequence, 'action': action, 'status': 'passed'}:
                    verify_both_live()
                    if action == 'network-disconnected':
                        network.update({'userApiFailureObserved': True, 'matrixSendFailureObserved': True, 'affectedConnections': relays['A'].blocked})
                    if action == 'network-peer-online':
                        count = relays['B'].forwarded_bytes - peer_bytes_before
                        if count <= 0:
                            raise ios.NativeFailure('infrastructure', 'dual-network', 'peer-actual-transport-not-observed')
                        network['actorBForwardedBytesDuringInterruption'] = count
                        network['peerUiContinuityObserved'] = True
                    if action == 'network-resume':
                        if relays['A'].forwarded_bytes <= restored_bytes_before:
                            raise ios.NativeFailure('infrastructure', 'dual-network', 'restored-actual-transport-not-observed')
                        network.update({'restoreObserved': True, 'matrixRetryObserved': True})
                    if action == 'network-peer-readback': network['peerReceptionObserved'] = True
                    if action == 'grant-revocation-readback': network['currentGrantRevocation'].update({'uiReadback': True, 'evidence': 'server-denial-and-flutter-readback'})
                    print(f'NATIVE_IOS_DUAL_UI_RESULT actor={role} action={action} status=passed supportSafe=true', flush=True)
                    break
            if time.monotonic() >= deadline:
                raise ios.NativeFailure('integration', 'dual-' + action.split('-')[0], 'actual-ui-command-proof-missing')
            time.sleep(0.25)
    return network


def publish_private_json(path: Path, value: dict) -> None:
    """Publish complete owner-only bytes without replacing any existing file."""
    staging = path.with_name(path.name + '.tmp')
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


def authorize_ui_resources(scopes: dict, input_data: dict, kind: str = 'authorization') -> dict:
    partial = [json.loads((scopes[role][0] / 'weave-dual-ios-result.json').read_text()) for role in ('A', 'B')]
    for index, role in enumerate(('A', 'B')):
        identity = hashlib.sha256(input_data['actors'][index]['identityRef'].encode()).hexdigest()
        if partial[index].get('role') != role or partial[index].get('identityDigest') != identity:
            raise ios.NativeFailure('integration', 'dual-authorization', 'owned-ui-identity-mismatch')
    if partial[0]['resources'] != partial[1]['resources']:
        raise ios.NativeFailure('integration', 'dual-authorization', 'shared-ui-resource-mismatch')
    parent = ios.verified_private_parent()
    request = parent / ('dual-ios-' + kind + '-request-' + input_data['runId'] + '.json')
    proof = parent / ('dual-ios-' + kind + '-proof-' + input_data['runId'] + '.json')
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
    if kind == 'authorization':
        expected = {'status': 'passed', 'evidence': 'server-denial',
                    'deniedIdentityDigest': hashlib.sha256(input_data['unauthorized']['identityRef'].encode()).hexdigest(),
                    'resourceDigest': hashlib.sha256(partial[0]['resources']['fileId'].encode()).hexdigest(),
                    'denials': {'chat': 'passed', 'files': 'passed', 'calendar': 'passed'}}
        if denial != expected or denial['deniedIdentityDigest'] in {actor['identityDigest'] for actor in partial}:
            raise ios.NativeFailure('integration', 'dual-authorization', 'third-identity-denial-proof-missing')
    elif kind == 'grant-revocation':
        expected = {'status': 'passed', 'evidence': 'server-denial', 'resourceDigest': hashlib.sha256(partial[0]['resources']['fileId'].encode()).hexdigest(),
                    'deniedIdentityDigest': partial[1]['identityDigest'], 'denied': True, 'regrantRestored': True}
        if denial != expected:
            raise ios.NativeFailure('integration', 'dual-authorization', 'current-grant-revocation-proof-missing')
    else:
        raise ios.NativeFailure('infrastructure', 'dual-authorization', 'unsupported-private-exchange')
    for support, udid, pid, _products in scopes.values():
        if ios.simulator_app_pid(udid) != pid:
            raise ios.NativeFailure('infrastructure', 'dual-authorization', 'owned-process-replaced')
        destination = support / ('weave-dual-ios-denial.json' if kind == 'authorization' else 'weave-dual-ios-grant-proof.json')
        publish_private_json(destination, denial)
    return denial


def retain_failure_room_reference(scopes: dict, input_path: Path, input_data: dict, candidate: str, issuer: str) -> None:
    """Transfer only actual UI resources while owned app storage still exists."""
    if not re.fullmatch(r'[0-9a-f]{40}', candidate) or not re.fullmatch(r'[A-Za-z0-9-]{1,40}', input_data['runId']):
        raise ios.NativeFailure('infrastructure', 'dual-chat-snapshot', 'invalid-candidate')
    actors = {actor['role']: actor['identityRef'] for actor in input_data['actors']}
    bound = {}
    for role in ('A', 'B'):
        support = scopes.get(role, (None,))[0]
        if support is None:
            raise ios.NativeFailure('infrastructure', 'dual-chat-snapshot', 'actor-unavailable')
        if support.is_symlink() or support.resolve() != support.absolute() or support.stat().st_uid != os.getuid():
            raise ios.NativeFailure('infrastructure', 'dual-chat-snapshot', 'unsafe-actor-storage')
        result = support / 'weave-dual-ios-result.json'
        if result.is_symlink() or not result.is_file() or result.stat().st_size > 16384 or result.stat().st_mode & 0o777 != 0o600 or result.stat().st_uid != os.getuid():
            raise ios.NativeFailure('infrastructure', 'dual-chat-snapshot', 'unsafe-actor-result')
        data = json.loads(result.read_text())
        if data.get('role') != role or data.get('identityDigest') != hashlib.sha256(actors[role].encode()).hexdigest():
            raise ios.NativeFailure('infrastructure', 'dual-chat-snapshot', 'actor-mismatch')
        if data.get('identityIssuerDigest') != hashlib.sha256(issuer.encode()).hexdigest():
            raise ios.NativeFailure('infrastructure', 'dual-chat-snapshot', 'issuer-mismatch')
        if data.get('nativeOrganizationDigest') != hashlib.sha256(input_data['nativeOrganizationId'].encode()).hexdigest():
            raise ios.NativeFailure('infrastructure', 'dual-chat-snapshot', 'organization-mismatch')
        bound[role] = data
    room = bound['A'].get('resources', {}).get('roomId')
    if room is None:
        return
    if not isinstance(room, str) or not re.fullmatch(r'![A-Za-z0-9._=-]{1,160}:[A-Za-z0-9.:-]{1,255}', room):
        raise ios.NativeFailure('infrastructure', 'dual-chat-snapshot', 'invalid-room-reference')
    parent = input_path.parent
    if parent.is_symlink() or parent.resolve() != parent.absolute() or parent.stat().st_mode & 0o777 != 0o700 or parent.stat().st_uid != os.getuid():
        raise ios.NativeFailure('infrastructure', 'dual-chat-snapshot', 'unsafe-private-parent')
    target = parent / ('dual-ios-chat-failure-' + input_data['runId'] + '.json')
    publish_private_json(target, {'runId': input_data['runId'], 'candidateCommit': candidate,
                                 'actorA': hashlib.sha256(actors['A'].encode()).hexdigest(),
                                 'actorB': hashlib.sha256(actors['B'].encode()).hexdigest(),
                                 'roomId': room})


def inspection_source() -> dict:
    def git(*args):
        return subprocess.check_output(['git', *args], cwd=ios.ROOT)
    lock = json.loads((ios.ROOT / 'specs/weave-specs.lock.json').read_text())
    return {'commit': git('rev-parse', 'HEAD').decode().strip(),
            'tree': git('rev-parse', 'HEAD^{tree}').decode().strip(),
            'specificationCommit': lock['specCorpus']['gitCommit'],
            'dirty': bool(git('status', '--porcelain=v1', '--untracked-files=all').strip()),
            'trackedDiffSha256': hashlib.sha256(git('diff', 'HEAD', '--binary')).hexdigest()}


def inspection_ci_workers() -> int:
    result = subprocess.run(['ps', '-axo', 'comm='], capture_output=True, text=True, timeout=5, check=True)
    return sum(Path(line.strip()).name == 'Runner.Worker' for line in result.stdout.splitlines())


def inspect_with_ci_priority(operation) -> int:
    # This observer yields owned work; it is not an atomic mutex with historical CI.
    if inspection_ci_workers():
        raise ios.NativeFailure('infrastructure', 'local-inspection', 'ci-owns-host')
    stopped = threading.Event()
    def observe():
        while not stopped.wait(0.5):
            try:
                count = inspection_ci_workers()
                conflict = count != 0
                reason = 'worker'
                count_record = str(min(count, 999))
                exception_class = 'unavailable'
                return_code = '0'
            except (OSError, subprocess.SubprocessError) as error:
                conflict = True
                reason = 'unknown'
                count_record = 'unavailable'
                exception_class = type(error).__name__ if type(error).__name__ in {
                    'OSError', 'FileNotFoundError', 'PermissionError', 'TimeoutExpired', 'CalledProcessError'} else 'unavailable'
                code = error.returncode if isinstance(error, subprocess.CalledProcessError) else None
                return_code = str(code) if type(code) is int and -999 <= code <= 999 else 'unavailable'
            if conflict:
                try:
                    print('NATIVE_LOCAL_INSPECTION phase=yield-ci reason=' + reason + ' workerCount=' + count_record
                          + ' exceptionClass=' + exception_class + ' returnCode=' + return_code
                          + ' qualificationPassed=false supportSafe=true', flush=True)
                finally:
                    os.kill(os.getpid(), signal.SIGINT)
                return
    monitor = threading.Thread(target=observe, daemon=True)
    monitor.start()
    try:
        print('NATIVE_LOCAL_INSPECTION phase=priority-watch-ready qualificationPassed=false supportSafe=true', flush=True)
        return operation()
    finally:
        stopped.set()
        monitor.join(timeout=6)


def initial_build_native_assets(client: Path) -> Path:
    # Xcode's completed build receipt identifies the map produced by that build.
    receipt = client / 'build/ios/Debug-iphonesimulator/.last_build_id'
    if receipt.is_symlink() or not receipt.is_file():
        raise ios.NativeFailure('infrastructure', 'local-inspection', 'build-map-unavailable')
    build_id = receipt.read_text().strip()
    if not re.fullmatch(r'[0-9a-f]{32}', build_id):
        raise ios.NativeFailure('infrastructure', 'local-inspection', 'build-map-unavailable')
    mapping = client / '.dart_tool/flutter_build' / build_id / 'native_assets.json'
    if mapping.is_symlink() or not mapping.is_file():
        raise ios.NativeFailure('infrastructure', 'local-inspection', 'build-map-unavailable')
    data = json.loads(mapping.read_text())
    if data.get('format-version') != [1, 0, 0] or not data.get('native-assets', {}).get('ios_arm64'):
        raise ios.NativeFailure('infrastructure', 'local-inspection', 'build-map-unavailable')
    return mapping


def inspection_run_command(udid: str, compiler: Path, app: Path, native_assets: Path) -> list[str]:
    # Canonical project metadata and generated registrant; actor-owned compiler outputs.
    return ['flutter', '--packages=' + str(ios.CLIENT / '.dart_tool/package_config.json'),
            'run', '--debug', '--no-pub', '--no-build', '--use-application-binary=' + str(app), '-d', udid,
            '--native-assets-yaml-file=' + str(native_assets),
            '--target=' + str(ios.CLIENT / 'integration_test/dual_ios_collaboration_test.dart'),
            '--project-root=' + str(ios.CLIENT.resolve()),
            '--output-dill=' + str(compiler / 'app.dill'),
            '--pid-file=' + str(compiler / 'run.pid'),
            '--dart-define=WEAVE_NATIVE_LOCAL_INSPECTION=true',
            '--dart-define=WEAVE_NATIVE_EXPECT_SIMULATOR=true',
            '--dart-define=WEAVE_DEVICE_DISPOSABLE_STACK=true',
            '--dart-define=WEAVE_NATIVE_TEST_RUN_ID=' + os.environ['WEAVE_E2E_RUN_ID']]


def read_inspection_command(path: Path, sequence: int) -> dict | None:
    if not path.exists() and not path.is_symlink():
        return None
    info = path.lstat()
    if path.is_symlink() or not path.is_file() or info.st_uid != os.getuid() or info.st_mode & 0o777 != 0o600 or info.st_size > 1024:
        raise ios.NativeFailure('infrastructure', 'local-inspection', 'unsafe-owned-command')
    value = json.loads(path.read_text())
    if (set(value) != {'sequence', 'action', 'role'} or type(value['sequence']) is not int
            or value['sequence'] < sequence or value['action'] not in {'browser', 'reload', 'restart', 'close', 'background', 'foreground', 'network-off', 'network-on', 'relaunch'}
            or value['role'] not in {'A', 'B', 'both'} or (value['action'] == 'browser' and value['role'] == 'both')):
        raise ios.NativeFailure('infrastructure', 'local-inspection', 'invalid-owned-command')
    return None if value['sequence'] == sequence else value


def start_local_actor(udid, compiler, app, native_assets, actor_env, scope, streams, *, require_entry, generation):
    log = scope / ('run-' + str(generation) + '.log')
    stream = log.open('w'); os.chmod(log, 0o600)
    streams.append(stream)
    process = track_process(subprocess.Popen(inspection_run_command(udid, compiler, app, native_assets), cwd=ios.CLIENT,
                            env=actor_env, stdin=subprocess.PIPE, stdout=stream, stderr=subprocess.STDOUT,
                            text=True, start_new_session=True))
    deadline = time.monotonic() + 900
    pid_file = compiler / 'run.pid'
    marker = ('NATIVE_PRODUCT_STAGE phase=local-inspection-entry-ready' if require_entry else 'NATIVE_PRODUCT_STAGE phase=workspace-ready')
    while not pid_file.is_file() or marker not in log.read_text(errors='replace'):
        if process.poll() is not None or time.monotonic() >= deadline:
            raise ios.NativeFailure('infrastructure', 'local-inspection', 'run-not-ready')
        time.sleep(0.25)
    pid = ios.simulator_app_pid(udid)
    if not pid:
        raise ios.NativeFailure('infrastructure', 'local-inspection', 'owned-process-missing')
    if pid_file.read_text().strip() != str(process.pid):
        raise ios.NativeFailure('infrastructure', 'local-inspection', 'run-owner-mismatch')
    return {'udid': udid, 'pid': pid, 'compiler': compiler, 'scope': scope, 'env': actor_env,
            'process': process, 'stream': stream, 'log': log, 'generation': generation}


def inspect_local_apps(input_data, env, temporary, devices, app, native_assets, derived, relays, evidence) -> int:
    actors = {}
    streams = []
    try:
        evidence['operations'] = []
        for actor, udid in zip(input_data['actors'], devices):
            role = actor['role']
            scope = temporary / role
            scope.mkdir(mode=0o700)
            products = scope / 'Build/Products'
            shutil.copytree(derived / 'Build/Products', products)
            compiler = scope / 'compiler'
            compiler.mkdir(mode=0o700)
            actor_env = actor_proxy_environment(env, relays[role])
            support, _fixture = prepare_actor_storage(actor, udid, app, input_data, actor_env)
            actors[role] = start_local_actor(udid, compiler, app, native_assets, actor_env, scope, streams, require_entry=True, generation=1)
            actors[role].update({'support': support, 'products': products})
            evidence['operations'].append({'action': 'initial-run', 'role': role, 'source': inspection_source(), 'at': now()})
        sequence = 0
        def publish_session():
            publish_private_json(temporary / ('inspection-session-' + str(sequence) + '.json'), {'mode': 'local-inspection', 'qualificationPassed': False,
                'runId': input_data['runId'], 'builtSource': evidence['builtSource'],
                'commandPath': str(temporary / 'inspection-command.json'),
                'actors': {role: {'simulatorId': item['udid'], 'processId': item['pid'],
                                 'residentPid': item['process'].pid, 'appSupport': str(item['support']),
                                 'logPath': str(item['log'])} for role, item in actors.items()}})
        publish_session()
        evidence['status'] = 'held'
        print('NATIVE_LOCAL_INSPECTION phase=held actors=2 qualificationPassed=false privateScope=' + temporary.name + ' supportSafe=true', flush=True)
        while True:
            for item in actors.values():
                if item['process'].poll() is not None or ios.simulator_app_pid(item['udid']) != item['pid']:
                    raise ios.NativeFailure('infrastructure', 'local-inspection', 'owned-process-exited')
            command = read_inspection_command(temporary / 'inspection-command.json', sequence)
            if command:
                sequence = command['sequence']
                action = command['action']
                record = {'sequence': sequence, 'action': action, 'role': command['role'], 'requestedSource': inspection_source(), 'at': now(), 'status': 'requested'}
                if action == 'close':
                    evidence['status'] = 'closed'
                    evidence['operations'].append(record)
                    return 70  # Explicit nonqualification exit invokes the existing outer teardown.
                selected = actors.values() if command['role'] == 'both' else [actors[command['role']]]
                for item in list(selected):
                    role = next(r for r, a in actors.items() if a is item)
                    if action in {'reload', 'restart'}:
                        offset = item['log'].stat().st_size
                        os.kill(item['process'].pid, signal.SIGUSR1 if action == 'reload' else signal.SIGUSR2)
                        deadline = time.monotonic() + 60
                        acknowledged = False
                        while time.monotonic() < deadline and item['process'].poll() is None:
                            tail = item['log'].read_bytes()[offset:].decode(errors='replace')
                            pattern = r'Reloaded [0-9]+(?: of [0-9]+)? libraries' if action == 'reload' else r'Restarted application in '
                            if re.search(pattern, tail):
                                acknowledged = True
                                break
                            if re.search(r'Hot reload was rejected|hot restart failed|Error:', tail):
                                break
                            time.sleep(0.25)
                        record.setdefault('residentAcknowledged', {})[role] = acknowledged
                        record['sourceAfterRequest'] = inspection_source()
                        record['status'] = 'resident-acknowledged' if acknowledged else 'unverified'
                        record['loadedScriptDigestVerified'] = False
                    elif action in {'background', 'foreground'}:
                        perform_owned_lifecycle((item['support'], item['udid'], item['pid'], item['products']), action, item['env'], inspection_sequence=sequence)
                        record['status'] = 'observed'
                    elif action in {'network-off', 'network-on'}:
                        relays[role].interrupt() if action == 'network-off' else relays[role].restore()
                    elif action == 'relaunch':
                        old_pid = item['pid']
                        stop_flutter_process_group(item['process'])
                        item['stream'].close()
                        ios.terminate_initial_app(item['udid'], old_pid)
                        pid_file = item['compiler'] / 'run.pid'
                        if pid_file.is_symlink():
                            raise ios.NativeFailure('infrastructure', 'local-inspection', 'unsafe-run-pid')
                        pid_file.unlink(missing_ok=True)
                        replacement = start_local_actor(item['udid'], item['compiler'], app, native_assets, item['env'], item['scope'], streams,
                                                         require_entry=False, generation=item['generation'] + 1)
                        if replacement['pid'] == old_pid:
                            raise ios.NativeFailure('infrastructure', 'local-inspection', 'distinct-owned-process-missing')
                        replacement.update({'support': item['support'], 'products': item['products']})
                        actors[role] = replacement
                        record['status'] = 'observed'
                        record['distinctProcessObserved'] = True
                    elif action == 'browser':
                        plans = list(item['products'].glob('*.xctestrun'))
                        bundles = list(item['products'].glob('**/BrowserDriver.xctest'))
                        if len(plans) != 1 or len(bundles) != 1:
                            raise ios.NativeFailure('infrastructure', 'local-inspection', 'owned-browser-plan-missing')
                        completion = item['scope'] / ('workspace-' + str(sequence) + '.ready')
                        if completion.exists():
                            raise ios.NativeFailure('infrastructure', 'local-inspection', 'browser-already-completed')
                        actor = next(a for a in input_data['actors'] if a['role'] == role)
                        browser_fixture = {
                            'email': actor['email'], 'password': actor['password'], 'issuerHost': 'auth.weave.localhost',
                            'appBundleIdentifier': ios.BUNDLE, 'phase': 'initial', 'nativeAuthCompletionPath': str(completion)}
                        private = bundles[0] / 'private-member.json'
                        if private.is_symlink():
                            raise ios.NativeFailure('infrastructure', 'local-inspection', 'unsafe-browser-fixture')
                        private.write_text(json.dumps(browser_fixture)); private.chmod(0o600)
                        browser_log = item['scope'] / ('browser-' + str(sequence) + '.log')
                        completion_offset = item['log'].stat().st_size
                        with browser_log.open('w') as stream:
                            streams.append(stream)
                            os.chmod(browser_log, 0o600)
                            driver = track_process(subprocess.Popen(['xcodebuild', 'test-without-building', '-xctestrun', str(plans[0]),
                                '-destination', 'platform=iOS Simulator,id=' + item['udid'], '-parallel-testing-enabled', 'NO',
                                '-only-testing:BrowserDriver/BrowserDriver/testProductionBrowser',
                                '-resultBundlePath', str(item['scope'] / ('browser-' + str(sequence) + '.xcresult'))],
                                env=item['env'], stdout=stream, stderr=subprocess.STDOUT, start_new_session=True))
                            observed = ios.relay_workspace_completion(item['process'], driver, item['log'], completion, 'initial',
                                                                     start_offset=completion_offset)
                            if not observed or driver.wait(timeout=30) != 0:
                                raise ios.NativeFailure('integration', 'local-inspection', 'actual-browser-failed')
                        record['status'] = 'observed'
                publish_session()
                evidence['operations'].append(record)
                publish_private_json(temporary / ('inspection-operations-' + str(sequence) + '.json'), evidence['operations'])
                print('NATIVE_LOCAL_INSPECTION phase=command-recorded action=' + action + ' qualificationPassed=false supportSafe=true', flush=True)
            time.sleep(0.25)
    except BaseException:
        # Streams are registered before launch, including an attach that never
        # returned an actor. Preserve private failure evidence before teardown.
        try:
            stop_owned_processes()
        except BaseException:
            pass  # Final cleanup remains mandatory; readable logs still survive.
        try:
            for stream in streams:
                if not stream.closed:
                    stream.flush()
                path = Path(stream.name)
                if (path.parent not in {temporary / 'A', temporary / 'B'}
                        or not re.fullmatch(r'(run|browser)-[0-9]+\.log', path.name)
                        or path.is_symlink() or not path.is_file()):
                    continue
                retained = ios.retain_private_diagnostic(path.read_text(errors='replace'))
                print('NATIVE_LOCAL_INSPECTION phase=failure-log-retained privateDiagnostic=' + retained.name + ' supportSafe=true', flush=True)
        except BaseException:
            try:
                print('NATIVE_LOCAL_INSPECTION phase=failure-log-unavailable supportSafe=true', flush=True)
            except BaseException:
                pass
        raise
    finally:
        try:
            stop_owned_processes()
        finally:
            for stream in streams:
                stream.close()


def qualify(input_path: Path, output_path: Path, env: dict) -> int:
    _CANCELLED.clear()
    with _ACTIVE_LOCK:
        _ACTIVE.clear()
    input_data = private_input(input_path, env['WEAVE_E2E_RUN_ID'])
    inspection = env.get('WEAVE_NATIVE_LOCAL_INSPECTION') == 'true'
    evidence = {'status': 'failed', 'actors': [], 'scenarios': {name: {'status': 'not-run'} for name in SCENARIOS},
                'encryption': {'policyRequired': True, 'status': 'not-run', 'plaintextFallback': False}, 'cleanupComplete': False}
    if inspection:
        evidence = {'mode': 'local-inspection', 'status': 'starting', 'qualificationPassed': False, 'cleanupComplete': False, 'builtSource': inspection_source()}
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
                                          'WEAVE_NATIVE_TEST_RUN_ID': input_data['runId'],
                                          **({'WEAVE_NATIVE_LOCAL_INSPECTION': True} if inspection else {})})); defines.chmod(0o600)
            ios.run(['flutter', 'pub', 'get', '--enforce-lockfile'], env=env)
            ios.run(['flutter', 'build', 'ios', '--simulator', '--debug', '--target=integration_test/dual_ios_collaboration_test.dart',
                     '--dart-define-from-file=' + str(defines)], env=env, timeout=1800)
            app = ios.CLIENT / 'build/ios/iphonesimulator/Runner.app'; evidence['appBuildSha256'] = app_build_digest(app)
            if inspection:
                native_assets = initial_build_native_assets(ios.CLIENT)
                evidence['nativeAssetsSha256'] = hashlib.sha256(native_assets.read_bytes()).hexdigest()
            derived = temporary / 'derived'
            ios.run(['xcodebuild', 'build-for-testing', '-project', str(ios.CLIENT / 'tool/ios_browser_driver/BrowserDriver.xcodeproj'),
                     '-scheme', 'BrowserDriver', '-destination', 'platform=iOS Simulator,id=' + devices[0],
                     '-derivedDataPath', str(derived), 'CODE_SIGNING_ALLOWED=NO'], env=env)
            authorities = proxy_authorities(input_data, env)
            for role in ('A', 'B'):
                relays[role] = OwnedFaultRelay(authorities)
            if inspection:
                return inspect_local_apps(input_data, env, temporary, devices, app, native_assets, derived, relays, evidence)
            ready = queue.Queue()
            restart_barrier = threading.Barrier(2)
            restart_gate = threading.Event()
            with concurrent.futures.ThreadPoolExecutor(max_workers=2) as executor:
                try:
                    tasks = []
                    for actor, udid in zip(input_data['actors'], devices):
                        scope = temporary / actor['role']; scope.mkdir(mode=0o700)
                        products = scope / 'Build/Products'; shutil.copytree(derived / 'Build/Products', products)
                        runtime_client = scope / 'client'; actor_runtime_copy(ios.CLIENT, runtime_client)
                        tasks.append(executor.submit(run_actor, actor, udid, runtime_client, products, scope, app, input_data, actor_proxy_environment(env, relays[actor['role']]), evidence['appBuildSha256'], ready, restart_barrier, restart_gate))
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
                    network_proof = run_ui_commands(scopes, tasks, env, input_data, relays=relays)
                    restart_gate.set()
                    scopes = {}
                    deadline = time.monotonic() + 240
                    while len(scopes) < 2:
                        if time.monotonic() >= deadline:
                            raise ios.NativeFailure('integration', 'dual-process-restart', 'restored-actor-readiness-timeout')
                        for task in tasks:
                            if task.done(): task.result()
                        try: role, support, udid, pid, products = ready.get(timeout=0.25)
                        except queue.Empty: continue
                        scopes[role] = (support, udid, pid, products)
                    restored_proof = run_ui_commands(scopes, tasks, env, input_data, phase='restore')
                    evidence['actors'] = [task.result() for task in tasks]
                except BaseException:
                    try:
                        retain_failure_room_reference(scopes, input_path, input_data, env.get('WEAVE_CANDIDATE_COMMIT', ''), env.get('WEAVE_NATIVE_ISSUER', ''))
                    except BaseException as unavailable:
                        try:
                            reason = unavailable.reason if (isinstance(unavailable, ios.NativeFailure)
                                and unavailable.stage == 'dual-chat-snapshot'
                                and unavailable.reason in {'invalid-candidate', 'unsafe-actor-storage', 'unsafe-actor-result',
                                                           'actor-mismatch', 'issuer-mismatch', 'organization-mismatch', 'actor-unavailable', 'invalid-room-reference', 'unsafe-private-parent'}) else 'unavailable'
                            print(f'NATIVE_IOS_CHAT_FAILURE_SNAPSHOT status=unavailable stage={reason} supportSafe=true', flush=True)
                        except BaseException:
                            pass  # Diagnostic output cannot replace the original UI failure.
                    stop_owned_processes()
                    raise
            actors = evidence['actors']
            for key in ('simulatorId', 'processId', 'identityDigest', 'matrixDeviceDigest', 'storageDigest'):
                if actors[0][key] == actors[1][key]:
                    raise ios.NativeFailure('integration', 'dual-isolation', 'actor-identity-collision')
            all_pids = [actor['recovery'][key] for actor in actors for key in ('initialProcessId', 'restoredProcessId')]
            if len(set(all_pids)) != 4:
                raise ios.NativeFailure('integration', 'dual-isolation', 'actor-process-collision')
            started = max(actor['startedAt'] for actor in actors)
            ended = min(actor['endedAt'] for actor in actors)
            if datetime.fromisoformat(started) >= datetime.fromisoformat(ended):
                raise ios.NativeFailure('integration', 'dual-concurrency', 'live-overlap-missing')
            evidence['overlap'] = {'startedAt': started, 'endedAt': ended}
            initial_started = max(actor['recovery']['initialStartedAt'] for actor in actors)
            initial_ended = min(actor['recovery']['initialEndedAt'] for actor in actors)
            if datetime.fromisoformat(initial_started) >= datetime.fromisoformat(initial_ended):
                raise ios.NativeFailure('integration', 'dual-concurrency', 'initial-live-overlap-missing')
            network_proof['interruptedActor'] = 'A'
            evidence['recovery'] = {'actors': [actor['recovery'] for actor in actors],
                                    'initialOverlap': {'startedAt': initial_started, 'endedAt': initial_ended},
                                    'network': network_proof, 'currentGrantRevocation': restored_proof['currentGrantRevocation']}
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
            evidence['actors'] = [{key: value for key, value in actor.items() if key not in {'scenarios', 'encryption', 'recovery'}}
                                  for actor in actors]
            evidence['status'] = 'passed'
            return 0
    finally:
        if inspection and evidence['status'] in {'starting', 'held'}:
            evidence['status'] = 'interrupted'
        if not inspection and evidence['status'] != 'passed' and relays:
            try:
                transcript = '\n'.join(relays[role].diagnostic(role) for role in sorted(relays)) + '\n'
                diagnostic = ios.retain_private_diagnostic(transcript)
                print('NATIVE_IOS_RELAY_RESULT status=retained privateDiagnostic=' + diagnostic.name + ' supportSafe=true', flush=True)
            except (OSError, ValueError, ios.NativeFailure):
                # Diagnostics must never replace the original product failure.
                print('NATIVE_IOS_RELAY_RESULT status=unavailable supportSafe=true', flush=True)
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


def main(argv=None) -> int:
    import argparse
    import platform
    import signal
    parser = argparse.ArgumentParser()
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args(argv)
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
    if env.get('WEAVE_NATIVE_LOCAL_INSPECTION') == 'true':
        return inspect_with_ci_priority(lambda: qualify(parent / ('dual-ios-input-' + run_id + '.json'), args.output, env))
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
