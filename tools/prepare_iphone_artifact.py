#!/usr/bin/env python3
"""Install the qualified hosted iPhone artifact and observe ordinary startup.

Signing uses existing local development credentials. No application or driver is
compiled, no personal login is automated, and the owner's container is preserved.
"""
from __future__ import annotations

import argparse
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import plistlib
import re
import secrets
import sys
import tempfile
import time

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'tools'))
from artifact_consumers import candidate_composition, component_evidence, native_bundle, copy_driver_products, verify_runtime_container
from build_artifacts import ArtifactError
from dogfood_functional_smoke import Runner, SmokeError, verify_discovered_routing, verify_installed_build
from sign_iphone_artifact import sign, verify_signed
from compose_env import load_context
from compose_runtime import _dogfood_session_lock, _service_container, verify_dogfood_identity
from dogfood_handoff_bundle import canonical_origin

BUNDLE = 'com.massimotter.weave'
DRIVER = 'com.massimotter.weave.integration.browser.xctrunner'


def parse_startup_result(output: str, fixture: dict) -> dict:
    marker = 'WEAVE_IPHONE_STARTUP_RESULT '
    records = []
    for line in output.splitlines():
        if marker in line:
            try:
                records.append(json.loads(line.split(marker, 1)[1]))
            except ValueError:
                raise SmokeError('physical-startup-result-malformed') from None
    if len(records) != 1:
        raise SmokeError('physical-startup-result-missing-or-ambiguous')
    value = records[0]
    if (not isinstance(value, dict)
            or set(value) != {'schemaVersion', 'runId', 'nonce', 'status', 'visibleState', 'ownerSessionReplaced'}
            or value['schemaVersion'] != 'weave.iphone-startup-ui/v1'
            or any(value[key] != fixture[key] for key in ('runId', 'nonce'))
            or value['status'] != 'passed' or value['ownerSessionReplaced'] is not False
            or value['visibleState'] not in {'retained-workspace', 'normal-sign-in'}):
        raise SmokeError('physical-startup-result-stale-or-invalid')
    return value


def physical_udid(details: dict) -> str:
    value = details.get('result', {}).get('hardwareProperties', {}).get('udid', '')
    if not isinstance(value, str) or not re.fullmatch(r'(?:[A-Fa-f0-9]{8}-[A-Fa-f0-9]{16}|[A-Fa-f0-9]{40})', value):
        raise SmokeError('paired-physical-hardware-udid-required')
    return value


def prepare(candidate: str, device: str, qualification_run: int, qualification_attempt: int, output: Path) -> dict:
    if (os.environ.get('GITHUB_ACTIONS') != 'true' or os.environ.get('GITHUB_REF') != 'refs/heads/dogfood'
            or os.environ.get('RUNNER_NAME') != 'weave-live-mac-mini'):
        raise SmokeError('protected-exclusive-dogfood-workflow-required')
    composition = candidate_composition(candidate)
    manifest, _, _ = native_bundle('ios-device', candidate=candidate)
    context = load_context('dogfood', ROOT / 'infra/weave-workspace', os.environ.get('WEAVE_ENV_FILE'))
    run_id = f"human-test-{os.environ.get('GITHUB_RUN_ID', '')}-{os.environ.get('GITHUB_RUN_ATTEMPT', '')}"
    if not re.fullmatch(r'human-test-[1-9][0-9]*-[1-9][0-9]*', run_id) or min(qualification_run, qualification_attempt) < 1:
        raise SmokeError('exact-physical-and-qualification-run-required')
    receipt = {'schemaVersion': 'weave.hil-preparation/v4',
               **{key: composition[key] for key in ('candidateCommit', 'sourceTree', 'specificationCommit')},
               'qualificationRunId': qualification_run, 'qualificationRunAttempt': qualification_attempt,
               'artifactComposition': composition, 'deviceBuildArtifact': component_evidence(manifest),
               'status': 'incomplete', 'install': 'update_in_place', 'ownerHIL': 'pending',
               'supportSafe': True, 'containsSecretValues': False}
    owned_driver = False
    try:
        with _dogfood_session_lock(context), tempfile.TemporaryDirectory(prefix='weave-iphone-artifact-') as temporary:
            directory = Path(temporary)
            directory.chmod(0o700)
            runner = Runner(directory)
            verify_dogfood_identity(context)
            receipt['runtimeBuildArtifact'] = verify_runtime_container(
                _service_container(context, 'backend', deadline=time.monotonic() + 30), candidate)
            if runner.run(['xcodebuild', '-version']).strip() != f"Xcode {manifest['toolchain']['xcode']}\nBuild version {manifest['toolchain']['xcodeBuild']}":
                raise SmokeError('qualified-device-xcode-required')
            details = directory / 'device.json'
            runner.run(['xcrun', 'devicectl', 'device', 'info', 'details', '--device', device,
                        '--timeout', '30', '--json-output', str(details)])
            udid = physical_udid(json.loads(details.read_text()))
            unsigned = Path(os.environ['WEAVE_BUILD_ARTIFACT_ROOT']) / 'ios-device'
            signed = directory / 'signed'
            profiles = [Path.home() / 'Library/MobileDevice/Provisioning Profiles',
                        Path.home() / 'Library/Developer/Xcode/UserData/Provisioning Profiles']
            sign(ROOT, unsigned, signed, candidate,
                 os.environ.get('WEAVE_IOS_EXPECTED_TEAM_ID', 'KNDHGC2KV6'),
                 os.environ.get('WEAVE_IOS_SIGNING_IDENTITY_SHA1') or None, udid, profiles,
                 os.environ.get('WEAVE_IOS_EXPECTED_DEVELOPER_CERT_TEAM_ID', '6RUS2Z848X'))
            receipt['signing'] = verify_signed(ROOT, unsigned, signed, candidate)
            # XCTest needs mutable, private fixture inputs; keep signed payload
            # verification separate from that per-session driver plan.
            products = copy_driver_products(signed / 'driver/Products', directory / 'session-products')
            driver_apps = list(products.glob('**/*-Runner.app'))
            if len(driver_apps) != 1 or plistlib.loads((driver_apps[0] / 'Info.plist').read_bytes()).get('CFBundleIdentifier') != DRIVER:
                raise SmokeError('physical-test-runner-identity-mismatch')
            inventory = directory / 'runner-before.json'
            runner.run(['xcrun', 'devicectl', 'device', 'info', 'apps', '--device', device,
                        '--bundle-id', DRIVER, '--json-output', str(inventory)])
            if json.loads(inventory.read_text())['result']['apps']:
                raise SmokeError('physical-test-runner-already-owned-by-another-session')
            app = signed / 'ordinary/Runner.app'
            actor = {'id': device, 'physical': True, 'destination': 'platform=iOS,id=' + udid,
                     'products': products, 'app': app, 'driverBundle': DRIVER}
            runner.run(['xcrun', 'devicectl', 'device', 'install', 'app', '--device', device, str(app)])
            verify_installed_build(runner, actor)
            runner.run(['xcrun', 'devicectl', 'device', 'install', 'app', '--device', device, str(driver_apps[0])])
            owned_driver = True
            runner.run(['xcrun', 'devicectl', 'device', 'process', 'launch', '--device', device,
                        '--terminate-existing', BUNDLE])
            fixture = {'schemaVersion': 'weave.iphone-startup-input/v1', 'runId': run_id,
                       'nonce': secrets.token_hex(16), 'organizationOrigin': canonical_origin(context.env['WEAVE_PUBLIC_URL'])}
            startup = runner.ui(actor, fixture, 'testOrdinaryPreparation', result_parser=parse_startup_result)
            verify_discovered_routing(runner, actor, context)
            verify_installed_build(runner, actor)
            runner.run(['xcrun', 'devicectl', 'device', 'uninstall', 'app', '--device', device, DRIVER], timeout=60)
            owned_driver = False
            receipt.update(status='passed', ordinaryStartup={**startup, 'organizationContextVerified': True,
                           'installedBuildVerified': True, 'driverCleanupComplete': True},
                           deviceIdentitySha256=hashlib.sha256(udid.encode()).hexdigest(),
                           verifiedAtUtc=datetime.now(timezone.utc).isoformat())
    except (ArtifactError, SmokeError) as error:
        receipt['boundary'] = str(error)
    except (OSError, ValueError, KeyError, TypeError):
        receipt['boundary'] = 'physical-artifact-preparation-incomplete'
    finally:
        if owned_driver:
            import subprocess
            try:
                result = subprocess.run(['xcrun', 'devicectl', 'device', 'uninstall', 'app', '--device', device, DRIVER],
                                        stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, timeout=60)
                cleanup_failed = result.returncode != 0
            except (OSError, subprocess.TimeoutExpired):
                cleanup_failed = True
            if cleanup_failed:
                receipt['boundary'] = 'owned-physical-test-runner-cleanup-incomplete'
            receipt['status'] = 'incomplete'
        if any(path.is_symlink() for path in (output, *output.parents)):
            raise SmokeError('unsafe-physical-evidence-output')
        output.parent.mkdir(parents=True, exist_ok=True)
        output.write_text(json.dumps(receipt, indent=2) + '\n')
    return receipt


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--candidate', required=True)
    parser.add_argument('--device-id', required=True)
    parser.add_argument('--qualification-run-id', type=int, required=True)
    parser.add_argument('--qualification-run-attempt', type=int, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    value = prepare(args.candidate, args.device_id, args.qualification_run_id, args.qualification_run_attempt, args.output)
    print('PREPARE_IPHONE_ARTIFACT status=' + value['status'] + ' boundary=' + value.get('boundary', 'none'))
    return 0 if value['status'] == 'passed' else 2


if __name__ == '__main__':
    raise SystemExit(main())
