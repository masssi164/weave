#!/usr/bin/env python3
"""Execute production AppAuth and live Flutter journeys on one owned simulator.

Raw XCTest/Flutter output and the disposable login fixture stay in a private
temporary directory. Only bounded support-safe outcome markers are emitted.
"""
from __future__ import annotations

import argparse
import json
import os
import platform
import shutil
import signal
import re
import subprocess
import tempfile
from pathlib import Path
from urllib.parse import urlparse

from run_native_product_acceptance import restart_disposable_services, stop_flutter_process_group

CLIENT = Path(__file__).resolve().parents[1]
ROOT = CLIENT.parent
RUNTIME = 'com.apple.CoreSimulator.SimRuntime.iOS-26-5'
DEVICE = 'com.apple.CoreSimulator.SimDeviceType.iPhone-17-Pro'
BUNDLE = 'com.massimotter.weave'


def run(arguments: list[str], *, env=None, timeout=900) -> str:
    child_env = (os.environ if env is None else env).copy()
    child_env.pop('WEAVE_NATIVE_MEMBER_EMAIL', None)
    child_env.pop('WEAVE_NATIVE_MEMBER_PASSWORD', None)
    process = subprocess.Popen(arguments, cwd=CLIENT, env=child_env, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True, start_new_session=True)
    try:
        stdout, stderr = process.communicate(timeout=timeout)
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
        raise RuntimeError(f'Native simulator command failed: {arguments[0]} (exit {process.returncode})')
    return stdout


def select_runtime() -> tuple[str, str]:
    runtime = RUNTIME
    device = DEVICE
    runtimes = json.loads(run(['xcrun', 'simctl', 'list', 'runtimes', '-j']))['runtimes']
    matching = [item for item in runtimes if item['identifier'] == runtime and item.get('isAvailable')]
    if len(matching) != 1 or 'arm64' not in matching[0].get('supportedArchitectures', []):
        raise RuntimeError('Selected Apple Silicon iOS runtime is unavailable')
    if device not in [item['identifier'] for item in matching[0].get('supportedDeviceTypes', [])]:
        raise RuntimeError('Selected iPhone device type is unsupported by the pinned runtime')
    return runtime, device


def execute_phase(phase: str, udid: str, derived: Path, temporary: Path, env: dict[str, str], fixture: dict) -> None:
    plans = list((derived / 'Build/Products').glob('*.xctestrun'))
    bundles = list((derived / 'Build/Products').glob('**/BrowserDriver.xctest'))
    if len(plans) != 1 or len(bundles) != 1:
        raise RuntimeError('Native browser driver build did not produce one test plan and bundle')
    fixture['phase'] = phase
    private_fixture = bundles[0] / 'private-member.json'
    private_fixture.write_text(json.dumps(fixture))
    private_fixture.chmod(0o600)
    env['WEAVE_DEVICE_PRODUCT_PHASE'] = phase
    with open(temporary / f'flutter-{phase}.log', 'w+') as flutter_log, open(temporary / f'browser-{phase}.log', 'w+') as browser_log:
        flutter = subprocess.Popen(['make', 'ios-simulator-product-e2e'], cwd=CLIENT, env=env, stdout=flutter_log, stderr=subprocess.STDOUT, start_new_session=True)
        driver = None
        try:
            driver = subprocess.Popen(['xcodebuild', 'test-without-building', '-xctestrun', str(plans[0]), '-destination', f'platform=iOS Simulator,id={udid}', '-resultBundlePath', str(temporary / f'{phase}.xcresult'), '-parallel-testing-enabled', 'NO', '-maximum-concurrent-test-simulator-destinations', '1'], cwd=CLIENT, env=env, stdout=browser_log, stderr=subprocess.STDOUT, start_new_session=True)
            driver_status = driver.wait(timeout=420)
            if driver_status:
                raise RuntimeError(f'iOS {phase} system-browser driver failed')
            flutter_status = flutter.wait(timeout=900)
            flutter_log.seek(0)
            observed = flutter_log.read()
            browser_log.seek(0)
            browser_observed = browser_log.read()
            for stage in re.findall(r'NATIVE_PRODUCT_STAGE phase=[a-z-]+', observed):
                print('IOS_' + stage, flush=True)
            expected = 'NATIVE_PRODUCT_INITIAL_RESULT status=passed' if phase == 'initial' else 'NATIVE_PRODUCT_SIGN_IN_RESULT status=passed'
            browser_expected = 'phase=form-submitted' if phase == 'initial' else 'phase=logout'
            if driver_status or flutter_status or expected not in observed or not re.search(r'All tests passed|[1-9][0-9]* tests? passed\.', observed) or f'NATIVE_IOS_BROWSER_RESULT status=passed {browser_expected}' not in browser_observed:
                raise RuntimeError(f'iOS {phase} production browser or live Flutter assertions failed')
            if phase == 'restore' and 'NATIVE_PRODUCT_STAGE phase=expired-bearer-denied' not in observed:
                raise RuntimeError('iOS expired-session denial was not observed')
            print(f'NATIVE_IOS_PRODUCT_RESULT status=passed phase={phase} supportSafe=true', flush=True)
        finally:
            if driver is not None:
                stop_flutter_process_group(driver)
            stop_flutter_process_group(flutter)
            private_fixture.unlink(missing_ok=True)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument('--build-launch-only', action='store_true', help='Build and launch only; never emits qualification evidence')
    args = parser.parse_args()
    def interrupted(number, _frame):
        raise SystemExit(128 + number)
    signal.signal(signal.SIGTERM, interrupted)
    signal.signal(signal.SIGINT, interrupted)
    if platform.system() != 'Darwin' or platform.machine() != 'arm64':
        raise RuntimeError('Native simulator qualification requires Apple Silicon macOS')
    runtime, device = select_runtime()
    env = os.environ.copy()
    for name in ('WEAVE_NATIVE_MEMBER_EMAIL', 'WEAVE_NATIVE_MEMBER_PASSWORD'):
        env.pop(name, None)
    rustup = shutil.which('rustup')
    if rustup is None:
        raise RuntimeError('Pinned Rust toolchain requires rustup')
    env['RUSTUP_TOOLCHAIN'] = '1.95.0'
    env['PATH'] = os.pathsep.join([str(ROOT / 'tools/ios-simulator-xcrun'), str(Path(rustup).parent), env['PATH']])
    udid = None
    cleanup_complete = False
    with tempfile.TemporaryDirectory(prefix='weave-ios-qualification-') as name:
        temporary = Path(name)
        temporary.chmod(0o700)
        try:
            created = run(['xcrun', 'simctl', 'create', temporary.name, device, runtime]).strip()
            if not re.fullmatch(r'[0-9a-fA-F]{8}(?:-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}', created):
                raise RuntimeError('Simulator creation did not return an exact owned device identity')
            udid = created
            run(['xcrun', 'simctl', 'boot', udid])
            run(['xcrun', 'simctl', 'bootstatus', udid, '-b'], timeout=180)
            print('NATIVE_IOS_SIMULATOR_RESULT status=booted ownership=run scope=isolated', flush=True)
            cargo_target = temporary / 'rust-target'
            run(['cargo', 'build', '--manifest-path', str(ROOT / 'rust/matrix-client/Cargo.toml'), '--locked', '--target', 'aarch64-apple-ios-sim', '--target-dir', str(cargo_target), '--features', 'flutter'], env=env, timeout=1800)
            library = cargo_target / 'aarch64-apple-ios-sim/debug/libweave_matrix_client.dylib'
            if 'arm64' not in run(['file', str(library)]) or 'IOSSIMULATOR' not in run(['xcrun', 'vtool', '-show-build', str(library)]):
                raise RuntimeError('Rust bridge output is not an Apple Silicon iOS Simulator library')
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
                raise RuntimeError('Native qualification requires the disposable localhost profile')
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
                    raise RuntimeError('Run-owned simulator survived cleanup')
                cleanup_complete = True
                print('NATIVE_IOS_CLEANUP_RESULT status=passed remainingOwnedSimulators=0', flush=True)
    if not cleanup_complete:
        raise RuntimeError('Native simulator cleanup was not verified')
    print('NATIVE_IOS_QUALIFICATION_RESULT status=passed supportSafe=true', flush=True)
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
