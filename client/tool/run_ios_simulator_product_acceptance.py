#!/usr/bin/env python3
"""Execute production AppAuth and live Flutter journeys on one owned simulator.

Raw XCTest/Flutter output and the disposable login fixture stay in a private
temporary directory. Only bounded support-safe outcome markers are emitted.
"""
from __future__ import annotations

import argparse
from contextlib import contextmanager
import json
import os
import platform
import plistlib
import shutil
import signal
import stat
import re
import subprocess
import tempfile
import time
import tomllib
from pathlib import Path
from urllib.parse import urlparse

from run_native_product_acceptance import restart_disposable_services, stop_flutter_process_group

CLIENT = Path(__file__).resolve().parents[1]
ROOT = CLIENT.parent
RUNTIME = 'com.apple.CoreSimulator.SimRuntime.iOS-26-5'
DEVICE = 'com.apple.CoreSimulator.SimDeviceType.iPhone-17-Pro'
BUNDLE = 'com.massimotter.weave'
# Xcode/build/SDK are the supported runtime pair already used to build
# and launch qualification. Flutter and Rust pins retain their existing owners.
XCODE_VERSION = '26.5'
XCODE_BUILD = '17F42'
IOS_SIMULATOR_SDK_VERSION = '26.5'
SAFE_PRODUCT_STAGES = {
    'shell-ready', 'driver-attached', 'sign-in-tapped', 'appauth-requested', 'auth-failed',
    'appauth-pending', 'workspace-ready', 'files-start', 'files-passed',
    'calendar-start', 'calendar-agenda-failed', 'calendar-passed',
    'matrix-start', 'matrix-open-failed', 'matrix-admission',
    'matrix-admission-diagnostic-failed', 'matrix-passed', 'navigation-passed',
    'refresh-passed', 'session-reopen-passed', 'app-state-restored',
    'process-restart-restored', 'expired-bearer-denied',
    'own-room-leave-denied', 'logout-denial-passed',
}
SAFE_BROWSER_STAGES = {
    'driver-started', 'sign-in-tapped', 'app-wait-started', 'app-observed', 'issuer-observed',
    'username-submitted', 'password-submitted', 'password-save-declined', 'auth-completion-observed', 'password-return-submitted',
}


class NativeFailure(RuntimeError):
    def __init__(self, category: str, stage: str, reason: str):
        self.category = category
        self.stage = stage
        self.reason = reason
        super().__init__(f'{category}: {stage}: {reason}')


def verified_private_parent() -> Path:
    parent = CLIENT / 'build/private-ios-qualification'
    # Never follow an existing build/private directory symlink. The persistent
    # private ancestor protects even artifacts recreated by asynchronous Xcode
    # writers after the run directory has been removed.
    for directory in (parent.parent, parent):
        directory.mkdir(exist_ok=True)
        if directory.is_symlink() or not directory.is_dir():
            raise NativeFailure('infrastructure', 'artifact-cleanup', 'unsafe-private-parent')
    descriptor = os.open(parent, os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW)
    try:
        if os.fstat(descriptor).st_uid != os.getuid():
            raise NativeFailure('infrastructure', 'artifact-cleanup', 'unowned-private-parent')
        os.fchmod(descriptor, 0o700)
    finally:
        os.close(descriptor)
    return parent


def cleanup_private_run(directory: Path, parent_identity: tuple[int, int], *,
                        timeout: float = 15, quiet_seconds: float = 2) -> None:
    parent = directory.parent
    deadline = time.monotonic() + timeout
    absent_since = None
    while time.monotonic() < deadline:
        descriptor = os.open(parent, os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW)
        try:
            info = os.fstat(descriptor)
            if (info.st_uid != os.getuid() or stat.S_IMODE(info.st_mode) != 0o700
                    or (info.st_dev, info.st_ino) != parent_identity):
                raise NativeFailure('infrastructure', 'artifact-cleanup', 'private-parent-changed')
            try:
                entry = os.stat(directory.name, dir_fd=descriptor, follow_symlinks=False)
            except FileNotFoundError:
                if absent_since is None:
                    absent_since = time.monotonic()
                if time.monotonic() - absent_since >= quiet_seconds:
                    # Bounded absence is not proof of writer quiescence. Keep the
                    # verified 0700 parent permanently, including on cleanup failure.
                    print('NATIVE_IOS_PRIVATE_CLEANUP_RESULT status=passed scope=owned-run privateParent=retained supportSafe=true', flush=True)
                    return
            else:
                absent_since = None
                if not stat.S_ISDIR(entry.st_mode) or entry.st_uid != os.getuid():
                    raise NativeFailure('infrastructure', 'artifact-cleanup', 'unsafe-owned-run-path')
                try:
                    shutil.rmtree(directory.name, dir_fd=descriptor)
                except FileNotFoundError:
                    pass
                except OSError:
                    # A result writer can race removal. Retry only this owned path.
                    pass
        finally:
            os.close(descriptor)
        time.sleep(0.25)
    raise NativeFailure('infrastructure', 'artifact-cleanup', 'owned-run-removal-timeout')


@contextmanager
def private_run_directory():
    parent = verified_private_parent()
    info = parent.lstat()
    directory = Path(tempfile.mkdtemp(prefix='weave-ios-qualification-', dir=parent))
    try:
        yield directory
    finally:
        cleanup_private_run(directory, (info.st_dev, info.st_ino))


def failure_marker(category: str, stage: str, reason: str) -> None:
    # Values are fixed identifiers chosen in source, never exception text or
    # copied browser output, which can contain disposable credentials.
    print(f'NATIVE_IOS_FAILURE category={category} phase={stage} reason={reason} supportSafe=true', flush=True)


def emit_phase_stages(phase: str, flutter_output: str, browser_output: str) -> None:
    patterns = [
        (flutter_output, r'NATIVE_PRODUCT_STAGE phase=[a-z-]+', SAFE_PRODUCT_STAGES),
        (browser_output, r'NATIVE_IOS_BROWSER_STAGE phase=[a-z-]+', SAFE_BROWSER_STAGES),
    ]
    seen = set()
    for output, pattern, allowed in patterns:
        for marker in re.findall(pattern, output):
            if marker.split('phase=', 1)[1] in allowed and marker not in seen:
                seen.add(marker)
                print(f'IOS_DIAGNOSTIC journey={phase} {marker} supportSafe=true', flush=True)
            if len(seen) >= 40:
                return
    # Only complete fixed-format numeric control diagnostics may leave the
    # private transcript. Do not retain labels, values or trailing messages.
    if phase in {'initial', 'restore'}:
        counts = set()
        pattern = (r'NATIVE_IOS_PROVIDER_CONTROLS phase=(content|username|password|post-submit) '
                   r'leafWebViews=([0-9]{1,3}) usernameFields=([0-9]{1,3}) '
                   r'passwordFields=([0-9]{1,3}) submitButtons=([0-9]{1,3})')
        for line in browser_output.splitlines():
            match = re.fullmatch(pattern, line)
            if match and match.groups() not in counts:
                counts.add(match.groups())
                print(f'IOS_PROVIDER_DIAGNOSTIC journey={phase} {line} supportSafe=true', flush=True)
            if len(counts) >= 6:
                break
    if phase in {'initial', 'restore'}:
        observed = set()
        typed_patterns = [
            r'NATIVE_IOS_PROVIDER_CONTROLS phase=(username-before|password-before) usernameFields=([0-9]{1,3}) passwordFields=([0-9]{1,3}) selectedFieldHittable=(true|false)',
            r'NATIVE_IOS_PROVIDER_CONTROLS phase=username-after readbackEqual=(true|false)',
            r'NATIVE_IOS_PROVIDER_CONTROLS phase=password-after secureValueLengthEqual=(true|false)',
            r'NATIVE_IOS_PROVIDER_CONTROLS phase=provider-error category=(invalid-credentials|invalid-username|invalid-password|account-disabled) matchingElements=([0-9]{1,3})',
        ]
        for line in browser_output.split('\n'):
            if any(re.fullmatch(pattern, line) for pattern in typed_patterns) and line not in observed:
                observed.add(line)
                print(f'IOS_PROVIDER_DIAGNOSTIC journey={phase} {line} supportSafe=true', flush=True)
            if len(observed) >= 16:
                break
    if phase in {'initial', 'restore'}:
        states = set()
        for line in browser_output.splitlines():
            match = re.fullmatch(
                r'NATIVE_IOS_POST_SUBMIT_STATE phase=pending issuerSurfaces=([0-9]{1,3}) providerErrors=([0-9]{1,3}) appAlertCount=([0-9]{1,3}) systemAlertCount=([0-9]{1,3})', line)
            if match and match.groups() not in states:
                states.add(match.groups())
                print(f'IOS_POST_SUBMIT_DIAGNOSTIC journey={phase} {line} supportSafe=true', flush=True)
            if len(states) >= 6:
                break
    # The integration test already emits bounded classification fields. Preserve
    # only its complete ordered record, then map unknown codes/types to unavailable.
    # Never copy exception messages, OAuth response data, or debug descriptions.
    if phase in {'initial', 'restore'}:
        fields = ('category', 'causeType', 'appFailureType', 'nestedCauseType',
                  'backendFailure', 'backendStatus', 'appAuthCode', 'nativeType',
                  'nativeCode', 'nativeDomain')
        pattern = r'NATIVE_PRODUCT_STAGE phase=auth-failed ' + ' '.join(
            name + r'=([A-Za-z0-9_.-]{1,80})' for name in fields)
        auth_types = {'cancelled', 'configuration', 'protocol', 'storage', 'unsupportedPlatform', 'unknown'}
        app_types = {'bootstrap', 'storage', 'validation', 'unknown', 'none'}
        runtime_types = {'FlutterAppAuthPlatformException', 'FlutterAppAuthUserCancelledException',
                         'AppFailure', 'AuthFailure', 'PlatformException', 'StateError',
                         'FormatException', 'TimeoutException', 'SocketException',
                         'int', 'String', 'Null', '_Exception', '_TypeError', '_ClientSocketException'}
        backend_types = {'session-rejected', 'timeout', 'request-failed', 'invalid-payload', 'unknown-state', 'none'}
        appauth_codes = {'authorize_failed', 'authorize_and_exchange_code_failed',
                        'discovery_failed', 'token_failed', 'end_session_failed'}
        domains = {'org.openid.appauth.general', 'org.openid.appauth.oauth_token',
                   'org.openid.appauth.oauth_authorization', 'org.openid.appauth.oauth_registration',
                   'org.openid.appauth.resourceserver', 'org.openid.appauth.remote-http',
                   'NSURLErrorDomain', 'NSCocoaErrorDomain', 'NSPOSIXErrorDomain', 'kCFErrorDomainCFNetwork'}
        allowed = {'category': auth_types, 'causeType': runtime_types, 'appFailureType': app_types,
                   'nestedCauseType': runtime_types, 'backendFailure': backend_types,
                   'appAuthCode': appauth_codes, 'nativeType': domains, 'nativeDomain': domains}
        classifications = set()
        for line in flutter_output.split('\n'):
            match = re.fullmatch(pattern, line)
            if match is None:
                continue
            values = dict(zip(fields, match.groups()))
            for name, valid in allowed.items():
                if values[name] not in valid:
                    values[name] = 'unavailable'
            if not re.fullmatch(r'[1-5][0-9]{2}', values['backendStatus']):
                values['backendStatus'] = 'unavailable'
            if not re.fullmatch(r'-?[0-9]{1,6}', values['nativeCode']):
                values['nativeCode'] = 'unavailable'
            record = ' '.join(name + '=' + values[name] for name in fields)
            if record not in classifications:
                classifications.add(record)
                print(f'IOS_FLUTTER_DIAGNOSTIC journey={phase} phase=auth-failed {record} supportSafe=true', flush=True)
            if len(classifications) >= 3:
                break
    # Preserve fixed Flutter failure categories/static frames as well. Raw
    # messages can include endpoint, account or bearer data and stay private.
    flutter_category = None
    if 'The isolated simulator driver did not attach before authentication.' in flutter_output:
        flutter_category = 'driver-attachment-timeout'
    elif 'EXCEPTION CAUGHT BY FLUTTER TEST FRAMEWORK' in flutter_output:
        flutter_category = 'flutter-test-assertion'
    if flutter_category is not None:
        print(f'IOS_FLUTTER_DIAGNOSTIC journey={phase} category={flutter_category} supportSafe=true', flush=True)
        allowed_sources = {'system_browser_auth_e2e_test.dart', 'flutter_secure_store.dart',
                           'path_provider_foundation_real.dart', 'test_config.dart'}
        locations = set()
        for match in re.finditer(r'([A-Za-z0-9_]+\.dart)[: ]([1-9][0-9]{0,5})', flutter_output):
            location = (match.group(1), match.group(2))
            if location[0] in allowed_sources and location not in locations:
                locations.add(location)
                print(f'IOS_FLUTTER_DIAGNOSTIC journey={phase} file={location[0]} line={location[1]} supportSafe=true', flush=True)
            if len(locations) >= 5:
                break
    # XCTest failure text may include typed values. Retain only the fixed
    # source filename/line and a category chosen here; never copy the message.
    failures = set()
    for line in browser_output.splitlines():
        location = re.search(r'BrowserDriver\.swift:([1-9][0-9]{0,4})', line)
        if location is None or 'error:' not in line:
            continue
        category = 'xctest-assertion'
        if 'not running' in line.lower() or 'not installed' in line.lower():
            category = 'target-not-running'
        elif 'Target application did not start' in line:
            category = 'target-start-timeout'
        elif 'Native workspace did not follow identity-provider submission' in line:
            category = 'native-auth-completion-timeout'
        elif 'Production browser did not complete' in line:
            category = 'browser-timeout'
        marker = (location.group(1), category)
        if marker not in failures:
            failures.add(marker)
            print(f'IOS_XCTEST_DIAGNOSTIC journey={phase} file=BrowserDriver.swift line={marker[0]} category={category} supportSafe=true', flush=True)
        if len(failures) >= 5:
            break


def browser_product_started(output: str) -> bool:
    return 'NATIVE_IOS_BROWSER_STAGE phase=app-observed' in output


def command_stage(arguments: list[str]) -> str:
    name = Path(arguments[0]).name
    if name == 'cargo':
        return 'rust-build'
    if name == 'flutter':
        return 'flutter-build' if arguments[1] == 'build' else 'flutter-dependencies'
    if name == 'xcodebuild':
        return 'browser-driver-build'
    if name == 'xcrun' and len(arguments) > 2 and arguments[1] == 'simctl':
        return 'simulator-' + arguments[2]
    return 'native-tool'


def run(arguments: list[str], *, env=None, timeout=900) -> str:
    child_env = (os.environ if env is None else env).copy()
    child_env.pop('WEAVE_NATIVE_MEMBER_EMAIL', None)
    child_env.pop('WEAVE_NATIVE_MEMBER_PASSWORD', None)
    process = subprocess.Popen(arguments, cwd=CLIENT, env=child_env, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True, start_new_session=True)
    try:
        stdout, stderr = process.communicate(timeout=timeout)
    except subprocess.TimeoutExpired as failure:
        stage = command_stage(arguments)
        failure_marker('infrastructure', stage, 'command-timeout')
        raise NativeFailure('infrastructure', stage, 'command-timeout') from failure
    finally:
        stop_flutter_process_group(process)
    if process.returncode:
        # This helper executes builds/simctl only. UI transcripts are separately
        # contained inside the private per-run directory. Keep build failure
        # output locally for diagnosis, outside the uploaded testApp artifacts.
        diagnostics = CLIENT / 'build/private-ios-qualification'
        diagnostics.mkdir(mode=0o700, parents=True, exist_ok=True)
        if diagnostics.is_symlink():
            raise RuntimeError('Native diagnostic directory is a symlink')
        diagnostics.chmod(0o700)
        with tempfile.NamedTemporaryFile(mode='w', prefix='command-', suffix='.log', dir=diagnostics, delete=False) as log:
            log.write(stdout + stderr)
            os.chmod(log.name, 0o600)
        print(f'NATIVE_IOS_COMMAND_RESULT status=failed command={Path(arguments[0]).name} privateDiagnostic={Path(log.name).name}', flush=True)
        stage = command_stage(arguments)
        failure_marker('infrastructure', stage, 'command-exit-failed')
        raise NativeFailure('infrastructure', stage, 'command-exit-failed')
    return stdout


def configured_toolchain_pins() -> tuple[str, str]:
    workflow = (ROOT / '.github/workflows/ci.yml').read_text()
    versions = set(re.findall(r"^\s+flutter-version:\s*['\"]?([^\s'\"#]+)", workflow, re.MULTILINE))
    if len(versions) != 1 or not re.fullmatch(r'[0-9]+\.[0-9]+\.[0-9]+', next(iter(versions), '')):
        raise NativeFailure('infrastructure', 'flutter-pin', 'ci-version-pins-missing-or-inconsistent')
    rust = tomllib.loads((ROOT / 'rust/matrix-client/rust-toolchain.toml').read_text())['toolchain']
    rust_channel = rust['channel']
    if not re.fullmatch(r'[0-9]+\.[0-9]+\.[0-9]+', rust_channel) or 'aarch64-apple-ios-sim' not in rust['targets']:
        raise NativeFailure('infrastructure', 'rust-pin', 'exact-simulator-toolchain-pin-required')
    return next(iter(versions)), rust_channel


def installed_flutter_revisions(flutter: dict, pinned_version: str) -> tuple[str, str]:
    sdk = Path(flutter['flutterRoot'])
    revision = run(['git', '-C', str(sdk), 'rev-parse', 'HEAD']).strip()
    tag = run(['git', '-C', str(sdk), 'describe', '--tags', '--exact-match']).strip()
    engine = (sdk / 'bin/internal/engine.version').read_text().strip()
    if tag != pinned_version:
        raise NativeFailure('infrastructure', 'flutter-toolchain', 'sdk-source-not-pinned-release')
    return revision, engine


def inspect_toolchain() -> dict[str, str]:
    flutter_pin, rust_pin = configured_toolchain_pins()
    flutter = json.loads(run(['flutter', '--version', '--machine']))
    if flutter.get('frameworkVersion') != flutter_pin or flutter.get('channel') != 'stable':
        raise NativeFailure('infrastructure', 'flutter-toolchain', 'unsupported-framework-or-channel')
    framework_revision, engine_revision = installed_flutter_revisions(flutter, flutter_pin)
    for key, expected in [('frameworkRevision', framework_revision), ('engineRevision', engine_revision)]:
        if not re.fullmatch(r'[0-9a-f]{40}', expected) or flutter.get(key) != expected:
            raise NativeFailure('infrastructure', 'flutter-toolchain', 'framework-or-engine-source-mismatch')
    xcode = run(['xcodebuild', '-version']).strip()
    if xcode != f'Xcode {XCODE_VERSION}\nBuild version {XCODE_BUILD}':
        raise NativeFailure('infrastructure', 'xcode-toolchain', 'unsupported-xcode-version-or-build')
    sdk = run(['xcrun', '--sdk', 'iphonesimulator', '--show-sdk-version']).strip()
    if sdk != IOS_SIMULATOR_SDK_VERSION:
        raise NativeFailure('infrastructure', 'simulator-sdk', 'unsupported-ios-simulator-sdk')
    rust = run(['rustup', 'run', rust_pin, 'rustc', '--version']).strip()
    if not re.fullmatch(r'rustc ' + re.escape(rust_pin) + r' \([0-9a-f]{7,40} [0-9]{4}-[0-9]{2}-[0-9]{2}\)', rust):
        raise NativeFailure('infrastructure', 'rust-toolchain', 'unsupported-rust-compiler')
    if not re.fullmatch(r'[0-9]+\.[0-9]+\.[0-9]+', flutter.get('dartSdkVersion', '')):
        raise NativeFailure('infrastructure', 'dart-toolchain', 'invalid-dart-sdk-identity')
    return {
        'frameworkVersion': flutter_pin,
        'channel': 'stable',
        'frameworkRevision': framework_revision,
        'engineRevision': engine_revision,
        'dartSdkVersion': flutter['dartSdkVersion'],
        'xcodeVersion': XCODE_VERSION,
        'xcodeBuild': XCODE_BUILD,
        'iosSimulatorSdkVersion': sdk,
        'rustToolchain': rust_pin,
        'rustCompilerVersion': rust,
    }


def select_runtime() -> tuple[str, str]:
    runtime = RUNTIME
    device = DEVICE
    runtimes = json.loads(run(['xcrun', 'simctl', 'list', 'runtimes', '-j']))['runtimes']
    matching = [item for item in runtimes if item['identifier'] == runtime and item.get('isAvailable')]
    if len(matching) != 1 or 'arm64' not in matching[0].get('supportedArchitectures', []):
        raise NativeFailure('infrastructure', 'simulator-runtime', 'apple-silicon-runtime-unavailable')
    if device not in [item['identifier'] for item in matching[0].get('supportedDeviceTypes', [])]:
        raise NativeFailure('infrastructure', 'simulator-device-type', 'pinned-device-type-unsupported')
    return runtime, device


def wait_for_flutter_shell(process, log_path: Path, phase: str, timeout: float = 900) -> None:
    deadline = time.monotonic() + timeout
    # A separate descriptor keeps this observer's read offset independent of
    # the subprocess stdout offset. Seeking its shared writer would corrupt it.
    with log_path.open() as observer:
        tail = ''
        while time.monotonic() < deadline:
            observed = tail + observer.read(65536)
            if process.poll() is not None:
                raise NativeFailure('infrastructure', phase + '-app-start', 'integration-app-exited-before-readiness')
            if 'NATIVE_PRODUCT_STAGE phase=shell-ready' in observed:
                print(f'NATIVE_IOS_APP_START_RESULT status=ready phase={phase} supportSafe=true', flush=True)
                return
            tail = observed[-4096:]
            time.sleep(0.25)
    raise NativeFailure('infrastructure', phase + '-app-start', 'integration-app-ready-timeout')


def simulator_driver_barrier(udid: str, run_id: str) -> Path:
    if not re.fullmatch(r'[A-Za-z0-9-]+', run_id):
        raise NativeFailure('infrastructure', 'driver-attach', 'invalid-isolated-run-id')
    container = Path(run(['xcrun', 'simctl', 'get_app_container', udid, BUNDLE, 'data']).strip()).resolve(strict=True)
    support = container / 'Library' / 'Application Support'
    for directory in (support.parent, support):
        if directory.is_symlink():
            raise NativeFailure('infrastructure', 'driver-attach', 'unsafe-app-container-path')
        directory.mkdir(exist_ok=True)
    barrier = support / f'weave-native-driver-{run_id}.ready'
    if barrier.is_symlink():
        raise NativeFailure('infrastructure', 'driver-attach', 'unsafe-readiness-path')
    barrier.unlink(missing_ok=True)
    return barrier


def exact_simulator_app_pids(processes: str, executable: Path) -> set[str]:
    # comm contains only executable identity, excluding argument values. The
    # installed bundle path binds this executable to one exact owned simulator.
    matches = set()
    for line in processes.splitlines():
        fields = line.strip().split(maxsplit=1)
        if len(fields) == 2 and fields[0].isdigit() and fields[1] == str(executable):
            matches.add(fields[0])
    return matches


def simulator_app_pid(udid: str) -> str:
    app = Path(run(['xcrun', 'simctl', 'get_app_container', udid, BUNDLE, 'app']).strip()).resolve(strict=True)
    if f'/CoreSimulator/Devices/{udid}/' not in str(app):
        raise NativeFailure('infrastructure', 'driver-attach', 'integration-app-container-mismatch')
    metadata = plistlib.loads((app / 'Info.plist').read_bytes())
    executable = metadata.get('CFBundleExecutable')
    if (metadata.get('CFBundleIdentifier') != BUNDLE or not isinstance(executable, str)
            or not re.fullmatch(r'[A-Za-z0-9_-]+', executable) or not (app / executable).is_file()):
        raise NativeFailure('infrastructure', 'driver-attach', 'integration-executable-identity-invalid')
    pids = exact_simulator_app_pids(run(['/bin/ps', '-axo', 'pid=,comm=']), app / executable)
    print(f'NATIVE_IOS_PROCESS_IDENTITY matchingCount={min(len(pids), 9)} unique={str(len(pids) == 1).lower()} supportSafe=true', flush=True)
    if len(pids) != 1:
        reason = 'integration-process-unavailable' if not pids else 'integration-process-ambiguous'
        raise NativeFailure('infrastructure', 'driver-attach', reason)
    return next(iter(pids))


def release_driver_barrier(flutter, driver, log_path: Path, udid: str, pid: str, barrier: Path, phase: str, timeout: float = 90) -> None:
    deadline = time.monotonic() + timeout
    with log_path.open() as observer:
        tail = ''
        while time.monotonic() < deadline:
            observed = tail + observer.read(65536)
            if flutter.poll() is not None or driver.poll() is not None:
                raise NativeFailure('infrastructure', phase + '-driver-attach', 'process-exited-before-attachment')
            if 'NATIVE_IOS_BROWSER_STAGE phase=app-observed' in observed:
                if simulator_app_pid(udid) != pid:
                    raise NativeFailure('infrastructure', phase + '-driver-attach', 'integration-process-replaced')
                descriptor = os.open(barrier, os.O_WRONLY | os.O_CREAT | os.O_EXCL | os.O_NOFOLLOW, 0o600)
                os.close(descriptor)
                print(f'NATIVE_IOS_DRIVER_ATTACH_RESULT phase={phase} existingProcessRetained=true supportSafe=true', flush=True)
                return
            tail = observed[-4096:]
            time.sleep(0.25)
    raise NativeFailure('infrastructure', phase + '-driver-attach', 'driver-attachment-timeout')


def prepare_workspace_completion(temporary: Path, phase: str) -> Path:
    if phase not in {'initial', 'restore'} or temporary.is_symlink():
        raise NativeFailure('infrastructure', 'auth-completion', 'unsafe-completion-scope')
    completion = temporary.resolve(strict=True) / f'workspace-{phase}.ready'
    if completion.is_symlink() or (completion.exists() and not completion.is_file()):
        raise NativeFailure('infrastructure', 'auth-completion', 'unsafe-completion-path')
    completion.unlink(missing_ok=True)
    return completion


def relay_workspace_completion(flutter, driver, log_path: Path, completion: Path, phase: str, timeout: float = 360) -> bool:
    # Independent read handle preserves the subprocess writer's offset. The
    # private IPC file carries no token/callback; its only producer is the exact
    # real Flutter workspace assertion. A submitted form is insufficient.
    deadline = time.monotonic() + timeout
    pending = ''
    discard_line = False
    with log_path.open(errors='replace') as observer:
        while time.monotonic() < deadline:
            if driver.poll() is not None:
                return False
            observed = False
            for fragment in observer.read(65536).splitlines(keepends=True):
                complete = fragment.endswith('\n')
                if not discard_line:
                    pending += fragment
                    if len(pending) > 4096:
                        discard_line = True
                        pending = ''
                if complete:
                    if not discard_line and pending.rstrip('\r\n') == 'NATIVE_PRODUCT_STAGE phase=workspace-ready':
                        observed = True
                    pending = ''
                    discard_line = False
            if observed:
                if flutter.poll() is not None or driver.poll() is not None:
                    raise NativeFailure('integration', phase + '-auth-completion', 'process-exited-before-completion-signal')
                descriptor = os.open(completion, os.O_WRONLY | os.O_CREAT | os.O_EXCL | os.O_NOFOLLOW, 0o600)
                with os.fdopen(descriptor, 'w') as signal:
                    signal.write('workspace-ready\n')
                print(f'NATIVE_IOS_AUTH_COMPLETION_SIGNAL phase={phase} source=flutter-workspace-assertion supportSafe=true', flush=True)
                return True
            if flutter.poll() is not None:
                raise NativeFailure('integration', phase + '-product', 'integration-app-exited-before-workspace')
            time.sleep(0.25)
    raise NativeFailure('integration', phase + '-auth-completion', 'native-workspace-completion-timeout')


def execute_phase(phase: str, udid: str, derived: Path, temporary: Path, env: dict[str, str], fixture: dict) -> None:
    plans = list((derived / 'Build/Products').glob('*.xctestrun'))
    bundles = list((derived / 'Build/Products').glob('**/BrowserDriver.xctest'))
    if len(plans) != 1 or len(bundles) != 1:
        raise NativeFailure('infrastructure', 'browser-driver-build', 'missing-test-plan-or-bundle')
    completion = prepare_workspace_completion(temporary, phase)
    fixture['phase'] = phase
    fixture['nativeAuthCompletionPath'] = str(completion)
    private_fixture = bundles[0] / 'private-member.json'
    private_fixture.write_text(json.dumps(fixture))
    private_fixture.chmod(0o600)
    env['WEAVE_DEVICE_PRODUCT_PHASE'] = phase
    with open(temporary / f'flutter-{phase}.log', 'w+') as flutter_log, open(temporary / f'browser-{phase}.log', 'w+') as browser_log:
        flutter = None
        driver = None
        barrier = None
        try:
            barrier = simulator_driver_barrier(udid, env['WEAVE_NATIVE_TEST_RUN_ID'])
            flutter = subprocess.Popen(['make', 'ios-simulator-product-e2e'], cwd=CLIENT, env=env, stdout=flutter_log, stderr=subprocess.STDOUT, start_new_session=True)
            # This is a different target from the preflight app. Its compile,
            # install and launch must complete before the authentication clock.
            wait_for_flutter_shell(flutter, temporary / f'flutter-{phase}.log', phase)
            # Flutter can replace its app container during installation. Resolve
            # the actual running app after readiness, keeping stale IPC cleared.
            actual_barrier = simulator_driver_barrier(udid, env['WEAVE_NATIVE_TEST_RUN_ID'])
            if actual_barrier != barrier:
                barrier.unlink(missing_ok=True)
                barrier = actual_barrier
            pid = simulator_app_pid(udid)
            driver_deadline = time.monotonic() + 420
            driver = subprocess.Popen(['xcodebuild', 'test-without-building', '-xctestrun', str(plans[0]), '-destination', f'platform=iOS Simulator,id={udid}', '-resultBundlePath', str(temporary / f'{phase}.xcresult'), '-parallel-testing-enabled', 'NO', '-maximum-concurrent-test-simulator-destinations', '1'], cwd=CLIENT, env=env, stdout=browser_log, stderr=subprocess.STDOUT, start_new_session=True)
            release_driver_barrier(flutter, driver, temporary / f'browser-{phase}.log', udid, pid, barrier, phase)
            completion_relayed = True
            if phase == 'initial':
                completion_relayed = relay_workspace_completion(
                    flutter, driver, temporary / f'flutter-{phase}.log', completion, phase,
                    timeout=max(0, driver_deadline - time.monotonic()))
            driver_status = driver.wait(timeout=max(1, driver_deadline - time.monotonic()))
            if not driver_status and not completion_relayed:
                raise NativeFailure('integration', phase + '-auth-completion', 'browser-exited-before-workspace-proof')
            if driver_status:
                browser_log.seek(0)
                browser_observed = browser_log.read()
                started = browser_product_started(browser_observed)
                category = 'integration' if started else 'infrastructure'
                reason = 'browser-ui-failed' if started else 'browser-runner-start-failed'
                if not started and 'NATIVE_IOS_BROWSER_STAGE phase=app-wait-started' in browser_observed:
                    reason = 'target-app-start-failed'
                raise NativeFailure(category, phase + '-browser', reason)
            flutter_status = flutter.wait(timeout=900)
            flutter_log.seek(0)
            observed = flutter_log.read()
            browser_log.seek(0)
            browser_observed = browser_log.read()
            expected = 'NATIVE_PRODUCT_INITIAL_RESULT status=passed' if phase == 'initial' else 'NATIVE_PRODUCT_SIGN_IN_RESULT status=passed'
            browser_expected = 'phase=form-submitted' if phase == 'initial' else 'phase=logout'
            if driver_status or flutter_status or expected not in observed or not re.search(r'All tests passed|[1-9][0-9]* tests? passed\.', observed) or f'NATIVE_IOS_BROWSER_RESULT status=passed {browser_expected}' not in browser_observed:
                raise NativeFailure('integration', phase + '-product', 'live-assertion-or-browser-proof-missing')
            if phase == 'initial' and 'NATIVE_IOS_BROWSER_STAGE phase=auth-completion-observed' not in browser_observed:
                raise NativeFailure('integration', 'initial-auth-completion', 'native-workspace-completion-proof-missing')
            if phase == 'restore' and 'NATIVE_PRODUCT_STAGE phase=expired-bearer-denied' not in observed:
                raise NativeFailure('integration', 'restore-authorization', 'expired-session-denial-missing')
            print(f'NATIVE_IOS_PRODUCT_RESULT status=passed phase={phase} supportSafe=true', flush=True)
        except subprocess.TimeoutExpired as failure:
            if failure.cmd and failure.cmd[0] == 'xcodebuild':
                browser_log.seek(0)
                started = browser_product_started(browser_log.read())
                category, stage = ('integration' if started else 'infrastructure'), phase + '-browser'
            else:
                flutter_log.seek(0)
                started = 'NATIVE_PRODUCT_STAGE phase=shell-ready' in flutter_log.read()
                category, stage = ('integration' if started else 'infrastructure'), phase + '-product'
            failure_marker(category, stage, 'journey-timeout')
            raise NativeFailure(category, stage, 'journey-timeout') from failure
        except NativeFailure as failure:
            failure_marker(failure.category, failure.stage, failure.reason)
            raise
        except OSError as failure:
            failure_marker('infrastructure', phase + '-runner', 'native-tool-unavailable')
            raise NativeFailure('infrastructure', phase + '-runner', 'native-tool-unavailable') from failure
        finally:
            try:
                try:
                    if driver is not None:
                        stop_flutter_process_group(driver)
                finally:
                    if flutter is not None:
                        stop_flutter_process_group(flutter)
            finally:
                # Retain only allowlisted diagnostic stages even if tool startup
                # or cleanup failed; stages never substitute for passing tests.
                flutter_log.seek(0)
                browser_log.seek(0)
                emit_phase_stages(phase, flutter_log.read(), browser_log.read())
                private_fixture.unlink(missing_ok=True)
                completion.unlink(missing_ok=True)
                if barrier is not None:
                    barrier.unlink(missing_ok=True)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument('--build-launch-only', action='store_true', help='Build and launch only; never emits qualification evidence')
    args = parser.parse_args()
    def interrupted(number, _frame):
        raise SystemExit(128 + number)
    signal.signal(signal.SIGTERM, interrupted)
    signal.signal(signal.SIGINT, interrupted)
    if platform.system() != 'Darwin' or platform.machine() != 'arm64':
        raise NativeFailure('infrastructure', 'host-preflight', 'apple-silicon-macos-required')
    toolchain = inspect_toolchain()
    runtime, device = select_runtime()
    env = os.environ.copy()
    for name in ('WEAVE_NATIVE_MEMBER_EMAIL', 'WEAVE_NATIVE_MEMBER_PASSWORD'):
        env.pop(name, None)
    rustup = shutil.which('rustup')
    if rustup is None:
        raise NativeFailure('infrastructure', 'rust-toolchain', 'rustup-unavailable')
    env['RUSTUP_TOOLCHAIN'] = toolchain['rustToolchain']
    env['PATH'] = os.pathsep.join([str(ROOT / 'tools/ios-simulator-xcrun'), str(Path(rustup).parent), env['PATH']])
    udid = None
    cleanup_complete = False
    with private_run_directory() as temporary:
        temporary.chmod(0o700)
        try:
            created = run(['xcrun', 'simctl', 'create', temporary.name, device, runtime]).strip()
            if not re.fullmatch(r'[0-9a-fA-F]{8}(?:-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}', created):
                raise NativeFailure('infrastructure', 'simulator-identity', 'invalid-owned-device-identity')
            udid = created
            run(['xcrun', 'simctl', 'boot', udid])
            run(['xcrun', 'simctl', 'bootstatus', udid, '-b'], timeout=180)
            print('NATIVE_IOS_SIMULATOR_RESULT status=booted ownership=run scope=isolated', flush=True)
            cargo_target = temporary / 'rust-target'
            run(['cargo', 'build', '--manifest-path', str(ROOT / 'rust/matrix-client/Cargo.toml'), '--locked', '--target', 'aarch64-apple-ios-sim', '--target-dir', str(cargo_target), '--features', 'flutter'], env=env, timeout=1800)
            library = cargo_target / 'aarch64-apple-ios-sim/debug/libweave_matrix_client.dylib'
            if 'arm64' not in run(['file', str(library)]) or 'IOSSIMULATOR' not in run(['xcrun', 'vtool', '-show-build', str(library)]):
                raise NativeFailure('infrastructure', 'rust-output', 'incorrect-simulator-abi')
            print('NATIVE_IOS_RUST_RESULT status=passed target=aarch64-apple-ios-sim locked=true', flush=True)
            # Explicit cross-compilation proof is transient; FRB owns its own
            # build cache. Do not retain two copies of the Rust target outputs.
            shutil.rmtree(cargo_target)
            run(['flutter', 'pub', 'get', '--enforce-lockfile'], env=env)
            run(['flutter', 'build', 'ios', '--simulator', '--debug', '--no-codesign'], env=env, timeout=1800)
            app = CLIENT / 'build/ios/iphonesimulator/Runner.app'
            run(['xcrun', 'simctl', 'install', udid, str(app)])
            run(['xcrun', 'simctl', 'launch', udid, BUNDLE])
            print('NATIVE_IOS_APP_RESULT status=launched rustTarget=aarch64-apple-ios-sim qualification=false', flush=True)
            if args.build_launch_only:
                return 0
            # The preflight launch has no credentials; terminate before the test.
            run(['xcrun', 'simctl', 'terminate', udid, BUNDLE])
            issuer = os.environ['WEAVE_NATIVE_ISSUER']
            if urlparse(issuer).hostname != 'auth.weave.localhost' or urlparse(os.environ['WEAVE_NATIVE_API_BASE_URL']).hostname != 'api.weave.localhost':
                raise NativeFailure('infrastructure', 'endpoint-profile', 'disposable-localhost-profile-required')
            run(['xcrun', 'simctl', 'keychain', udid, 'add-root-cert', os.environ['WEAVE_NATIVE_CA']])
            env.update({'WEAVE_IOS_SIMULATOR_ID': udid, 'WEAVE_DEVICE_DISPOSABLE_STACK': 'true', 'WEAVE_DEVICE_API_BASE_URL': os.environ['WEAVE_NATIVE_API_BASE_URL'], 'WEAVE_DEVICE_OIDC_ISSUER_URL': issuer, 'WEAVE_DEVICE_MATRIX_HOMESERVER_URL': os.environ['WEAVE_NATIVE_MATRIX_URL'], 'WEAVE_NATIVE_TEST_RUN_ID': os.environ['WEAVE_E2E_RUN_ID']})
            derived = temporary / 'derived'
            run(['xcodebuild', 'build-for-testing', '-project', str(CLIENT / 'tool/ios_browser_driver/BrowserDriver.xcodeproj'), '-scheme', 'BrowserDriver', '-destination', f'platform=iOS Simulator,id={udid}', '-derivedDataPath', str(derived), 'CODE_SIGNING_ALLOWED=NO'], env=env)
            fixture = {'email': os.environ['WEAVE_NATIVE_MEMBER_EMAIL'], 'password': os.environ['WEAVE_NATIVE_MEMBER_PASSWORD'], 'issuerHost': 'auth.weave.localhost', 'appBundleIdentifier': BUNDLE}
            execute_phase('initial', udid, derived, temporary, env, fixture)
            restart_disposable_services(env)
            execute_phase('restore', udid, derived, temporary, env, fixture)
        finally:
            if udid:
                # Delete only the device whose identity this process created.
                try:
                    subprocess.run(['xcrun', 'simctl', 'shutdown', udid], env=env, capture_output=True, timeout=60, check=False)
                except subprocess.TimeoutExpired:
                    print('NATIVE_IOS_CLEANUP_STAGE phase=shutdown-timeout deleting-owned-device=true', flush=True)
                run(['xcrun', 'simctl', 'delete', udid], timeout=60)
                devices = json.loads(run(['xcrun', 'simctl', 'list', 'devices', '-j']))['devices']
                if any(item['udid'] == udid for items in devices.values() for item in items):
                    raise NativeFailure('infrastructure', 'simulator-cleanup', 'owned-device-remained')
                cleanup_complete = True
                print('NATIVE_IOS_CLEANUP_RESULT status=passed remainingOwnedSimulators=0', flush=True)
    if not cleanup_complete:
        raise NativeFailure('infrastructure', 'simulator-cleanup', 'cleanup-unverified')
    print('NATIVE_IOS_QUALIFICATION_RESULT status=passed supportSafe=true', flush=True)
    return 0


if __name__ == '__main__':
    try:
        raise SystemExit(main())
    except NativeFailure as failure:
        failure_marker(failure.category, failure.stage, failure.reason)
        raise SystemExit(1)
    except (RuntimeError, OSError, ValueError, KeyError, subprocess.TimeoutExpired):
        # Preflight, configuration and cleanup failures are infrastructure;
        # their raw exception text is deliberately not emitted as evidence.
        failure_marker('infrastructure', 'preflight-or-cleanup', 'qualification-runtime-failed')
        raise SystemExit(1)
