#!/usr/bin/env python3
"""Bounded ordinary-main/direct-Dogfood UI operations using the existing XCTest driver.

Credentials, UI transcripts and raw resource identities stay private. A successful
receipt is assembled only from fresh XCTest observations and native exported bytes.
No app actor/session fixture, proxy, database seed or owner login replacement exists.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import plistlib
import re
import secrets
import shutil
import signal
import ssl
import stat
import subprocess
import sys
import tempfile
import time
from datetime import datetime, timezone
from urllib.parse import urlsplit
from urllib.request import Request

ROOT = Path(__file__).resolve().parents[1]
CLIENT = ROOT / 'client'
sys.path.insert(0, str(ROOT / 'tools'))
sys.path.insert(0, str(CLIENT / 'tool'))
sys.path.insert(0, str(ROOT / 'infra/weave-workspace/scripts'))
from run_ios_simulator_product_acceptance import NativeFailure, inspect_toolchain, prepare_browser_fixture, select_runtime
from compose_env import load_context
from compose_runtime import _dogfood_session_lock, _service_container, verify_dogfood_identity
from dogfood_handoff_bundle import canonical_origin
from bootstrap_https import open_https

BUNDLE = 'com.massimotter.weave'
HASH = re.compile(r'^[0-9a-f]{64}$')
SHA = re.compile(r'^[0-9a-f]{40}$')
OPERATIONS = (
    'A:identity-before', 'B:identity', 'A:chat-create', 'B:chat-peer', 'A:chat-readback',
    'A:folder-create', 'B:file-upload', 'A:file-readback', 'B:file-export',
    'A:private-folder-create', 'A:private-scope-switch', 'A:private-tab-restore', 'B:private-folder-absent',
    'A:calendar-create', 'B:calendar-read', 'A:calendar-update', 'B:calendar-updated',
    'A:calendar-delete', 'B:calendar-absent', 'A:restart-chat', 'A:restart-file',
    'A:restart-private-folder', 'A:update-chat', 'A:update-file', 'A:update-private-folder', 'A:identity-after',
)


class SmokeError(RuntimeError):
    """Closed support-safe failure category; raw subprocess errors are private."""


def digest(value: bytes) -> str:
    return hashlib.sha256(value).hexdigest()


def private_json(path: Path) -> dict:
    try:
        if any(parent.is_symlink() for parent in (path, *path.parents)):
            raise ValueError()
        info = path.stat()
        if not stat.S_ISREG(info.st_mode) or info.st_uid != os.getuid() or info.st_mode & 0o077:
            raise ValueError()
        if path.parent.stat().st_mode & 0o077 or not 0 < info.st_size <= 8192:
            raise ValueError()
        result = json.loads(path.read_text())
        if not isinstance(result, dict):
            raise ValueError()
        return result
    except (OSError, ValueError):
        raise SmokeError('approved-test-member-authentication-required') from None


def identities(path: Path, origin: str) -> dict:
    value = private_json(path)
    if (set(value) != {'schemaVersion', 'organizationOrigin', 'members', 'ownerDisplayName'}
            or value['schemaVersion'] != 'weave.dogfood-functional-identities/v1'
            or canonical_origin(value['organizationOrigin']) != origin
            or not isinstance(value['members'], list) or len(value['members']) != 2
            or not isinstance(value['ownerDisplayName'], str) or not value['ownerDisplayName']):
        raise SmokeError('approved-test-member-fixture-invalid')
    for member in value['members']:
        if (not isinstance(member, dict) or set(member) != {'email', 'password', 'displayName'}
                or any(not isinstance(member.get(key), str) or not 1 <= len(member[key]) <= 256
                       for key in ('email', 'password', 'displayName'))):
            raise SmokeError('approved-test-member-fixture-invalid')
    if value['members'][0]['email'].casefold() == value['members'][1]['email'].casefold():
        raise SmokeError('independent-test-members-required')
    return value


def ensure_prepared_identities(context, path: Path) -> None:
    """Client acceptance consumes prepared identities; it never starts onboarding."""
    if path.exists() or path.is_symlink():
        return
    raise SmokeError('prepared-member-scenario-required')


def approved_identity_binding(context, path: Path, approved: dict) -> dict:
    """Validate the distinct Console or operator ledger before actual UI identity comparison."""
    from dogfood_prepare import fixture_binding
    from compose_env import ContractError
    try:
        return fixture_binding(context, path, approved)
    except (ContractError, KeyError, TypeError, ValueError):
        raise SmokeError('approved-test-member-preparation-binding-required') from None


def verify_actor_binding(binding: dict, resources: dict, role: str) -> None:
    expected = binding['members'][role]
    account = resources.get('account' if role == 'A' else 'peerAccount')
    if account != expected['accountDigest'] or resources.get('organization') != expected['organizationDigest']:
        raise SmokeError('approved-current-test-member-identity-mismatch')


def parse_ui_result(output: str, fixture: dict) -> dict:
    results = []
    for line in output.splitlines():
        marker = 'WEAVE_FUNCTIONAL_UI_RESULT '
        if marker in line:
            try:
                results.append(json.loads(line.split(marker, 1)[1]))
            except ValueError:
                raise SmokeError('ui-result-malformed') from None
    if len(results) != 1:
        raise SmokeError('ui-result-missing-or-ambiguous')
    value = results[0]
    if (set(value) != {'schemaVersion', 'runId', 'nonce', 'operation', 'status', 'resources'}
            or value['schemaVersion'] != 'weave.ordinary-product-ui-result/v1'
            or any(value[key] != fixture[key] for key in ('runId', 'nonce', 'operation'))
            or value['status'] != 'passed' or not isinstance(value['resources'], dict)
            or any(not isinstance(item, str) or not HASH.fullmatch(item) for item in value['resources'].values())):
        raise SmokeError('ui-result-stale-or-invalid')
    return value


def functional_smoke_passed(value: dict, sha: str, tree: str, spec: str,
                            run_id: int, attempt: int, stage: str) -> bool:
    """Shared fail-closed receipt contract used by both delivery and Main."""
    if stage != 'deployment' or not isinstance(value, dict):
        return False
    expected = {
        'schemaVersion': 'weave.dogfood-functional-smoke/v3', 'candidateCommit': sha,
        'sourceTree': tree, 'specificationCommit': spec, 'qualificationRunId': run_id,
        'qualificationRunAttempt': attempt, 'stage': stage, 'status': 'passed',
        'entrypoint': 'lib/main.dart', 'routing': 'reviewed-direct',
        'appSessionInjection': False, 'proxyUsed': False, 'ownerSessionReplaced': False,
        'cleanupStatus': 'retained-owned-content', 'ownedSimulatorsRemoved': True,
        'privateInvocationInputsRemoved': True, 'supportSafe': True, 'containsSecretValues': False,
        'retainedStateRestartVerified': True, 'retainedStateUpdateVerified': True,
        'calendarPeerAbsenceVerified': True, 'independentPeerReadbackVerified': True,
        'privateFilesScopeIsolationVerified': True, 'privateFilesPeerIsolationVerified': True,
        'privateFilesContinuityVerified': True,
    }
    if any(value.get(key) != item or type(value.get(key)) is not type(item) for key, item in expected.items()):
        return False
    from member_test_scenario import scenario_sha256
    if value.get('scenarioSha256') != scenario_sha256():
        return False
    if value.get('actorProfile') != 'two-ordinary-members':
        return False
    from artifact_evidence import composition_passed, component_matches
    composition = value.get('artifactComposition')
    if not composition_passed(composition, sha, tree, spec, value['scenarioSha256']):
        return False
    runtime = value.get('runtimeBuildArtifact')
    native = value.get('simulatorBuildArtifact')
    for artifact, component in ((runtime, 'runtime-linux-amd64'), (native, 'ios-simulator')):
        if not component_matches(artifact, composition, component):
            return False
    if value.get('deployedSourceTree') != runtime['sourceTree']:
        return False
    if value.get('operations') != list(OPERATIONS):
        return False
    resources = value.get('resourceIdentityDigests')
    if not isinstance(resources, dict) or set(resources) != {'conversation', 'folder', 'privateFolder', 'file', 'event', 'account', 'peerAccount', 'organization'}:
        return False
    if any(not isinstance(item, str) or not HASH.fullmatch(item) for item in resources.values()):
        return False
    if resources['account'] == resources['peerAccount'] or resources['folder'] == resources['privateFolder']:
        return False
    for key in ('appBuildSha256', 'peerAppBuildSha256', 'routingSha256', 'exportedFileSha256', 'deviceIdentitySha256'):
        if not isinstance(value.get(key), str) or not HASH.fullmatch(value[key]):
            return False
    if not re.fullmatch(r'sha256:[0-9a-f]{64}', value.get('deployedImage', '')):
        return False
    build = value.get('build')
    if (not isinstance(build, dict) or build.get('bundleId') != BUNDLE
            or not isinstance(build.get('buildNumber'), str) or not build['buildNumber']
            or build.get('mode') != 'debug'):
        return False
    try:
        started = datetime.fromisoformat(value['startedAtUtc'].replace('Z', '+00:00'))
        finished = datetime.fromisoformat(value['verifiedAtUtc'].replace('Z', '+00:00'))
        return (started.tzinfo is not None and finished.tzinfo is not None
                and 0 < (finished - started).total_seconds() <= 3600)
    except (KeyError, TypeError, ValueError):
        return False


class Runner:
    def __init__(self, directory: Path):
        self.directory = directory
        self.sequence = 0
        self.deadline = time.monotonic() + 2700
        self.environment = os.environ.copy()

    def configure_native_toolchain(self) -> None:
        """Use the qualified pins and rustup shims for ordinary native builds too."""
        try:
            toolchain = inspect_toolchain()
            rustup = shutil.which('rustup', path=self.environment.get('PATH', ''))
            if rustup is None:
                raise SmokeError('pinned-native-toolchain-unavailable')
            # Keep the shim directory: resolving rustup/cargo symlinks can select
            # a different compiler when Flutter's native hook launches rustc.
            self.environment['PATH'] = str(Path(rustup).parent) + os.pathsep + self.environment.get('PATH', '')
            self.environment['RUSTUP_TOOLCHAIN'] = toolchain['rustToolchain']
        except (NativeFailure, OSError, ValueError, KeyError, subprocess.SubprocessError):
            raise SmokeError('pinned-native-toolchain-unavailable') from None

    def run(self, arguments: list[str], *, cwd: Path = ROOT, timeout: int = 300) -> str:
        timeout = min(timeout, max(0, self.deadline - time.monotonic()))
        if timeout <= 0:
            raise SmokeError('bounded-functional-window-expired')
        self.sequence += 1
        log = self.directory / f'command-{self.sequence}.log'
        # A private file avoids unbounded raw UI output in memory/public logs.
        with log.open('w+') as stream:
            log.chmod(0o600)
            child = subprocess.Popen(arguments, cwd=cwd, stdout=stream, stderr=subprocess.STDOUT,
                                     start_new_session=True, env=self.environment)
            try:
                child.wait(timeout=timeout)
            finally:
                if child.poll() is None:
                    os.killpg(child.pid, signal.SIGTERM)
                    try:
                        child.wait(timeout=10)
                    except subprocess.TimeoutExpired:
                        os.killpg(child.pid, signal.SIGKILL)
                        child.wait(timeout=10)
                # Reap stragglers even when the direct child has exited after
                # TERM, so detached raw-result writers cannot outlive cleanup.
                try:
                    os.killpg(child.pid, signal.SIGKILL)
                except ProcessLookupError:
                    pass
                child.wait(timeout=10)
            if child.returncode:
                stream.seek(0)
                if 'WEAVE_FUNCTIONAL_UI_FAILURE category=authentication-required' in stream.read():
                    raise SmokeError('normal-authentication-required')
                raise SmokeError('native-command-failed')
            stream.seek(0)
            return stream.read()

    def ui(self, actor: dict, fixture: dict, method: str = 'testOrdinaryProductSmoke', *, result_parser=None) -> dict:
        private = prepare_browser_fixture(actor['products'], fixture)
        try:
            plan, = actor['products'].glob('*.xctestrun')
            if actor['physical']:
                self.run(['xcrun', 'devicectl', 'device', 'copy', 'to', '--device', actor['id'],
                          '--domain-type', 'appDataContainer', '--domain-identifier', actor['driverBundle'],
                          '--source', str(private), '--destination', 'Documents/private-member.json'])
                configuration = plistlib.loads(plan.read_bytes())
                targets = ([target for item in configuration['TestConfigurations'] for target in item.get('TestTargets', [])]
                           if 'TestConfigurations' in configuration else
                           [target for name, target in configuration.items() if name != '__xctestrun_metadata__' and isinstance(target, dict)])
                targets[0]['EnvironmentVariables']['WEAVE_NATIVE_DRIVER_FIXTURE_PATH'] = 'runner-documents:private-member.json'
                plan.write_bytes(plistlib.dumps(configuration))
            output = self.run(['xcodebuild', 'test-without-building', '-xctestrun', str(plan),
                               '-destination', actor['destination'], '-parallel-testing-enabled', 'NO',
                               '-only-testing:BrowserDriver/BrowserDriver/' + method,
                               '-resultBundlePath', str(self.directory / f'result-{self.sequence + 1}')], timeout=480)
            if result_parser is not None:
                return result_parser(output, fixture)
            return parse_ui_result(output, fixture) if method == 'testOrdinaryProductSmoke' else {}
        except SmokeError as error:
            if method == 'testProductionBrowser':
                raise SmokeError('normal-test-member-browser-authentication-required') from None
            if str(error) == 'normal-authentication-required':
                raise SmokeError('normal-test-member-authentication-required') from None
            raise
        finally:
            private.unlink(missing_ok=True)


def app_digest(app: Path) -> str:
    value = hashlib.sha256()
    for path in sorted(app.rglob('*')):
        if path.is_symlink():
            raise SmokeError('app-bundle-symlink')
        if path.is_file():
            relative = path.relative_to(app).as_posix().encode()
            value.update(len(relative).to_bytes(4, 'big'))
            value.update(relative)
            value.update(hashlib.sha256(path.read_bytes()).digest())
    return value.hexdigest()


def build_info(app: Path, mode: str) -> dict:
    info = plistlib.loads((app / 'Info.plist').read_bytes())
    if info.get('CFBundleIdentifier') != BUNDLE:
        raise SmokeError('ordinary-bundle-identity-mismatch')
    return {'bundleId': BUNDLE, 'buildNumber': str(info['CFBundleVersion']), 'mode': mode}


def verify_installed_build(runner: Runner, actor: dict) -> None:
    expected = build_info(actor['app'], 'profile' if actor['physical'] else 'debug')
    if actor['physical']:
        inventory = runner.directory / ('installed-' + str(runner.sequence) + '.json')
        runner.run(['xcrun', 'devicectl', 'device', 'info', 'apps', '--device', actor['id'],
                    '--bundle-id', BUNDLE, '--json-output', str(inventory)])
        apps = json.loads(inventory.read_text())['result']['apps']
        matching = [app for app in apps if app.get('bundleIdentifier') == BUNDLE]
        if len(matching) != 1 or str(matching[0].get('bundleVersion')) != expected['buildNumber']:
            raise SmokeError('installed-physical-build-mismatch')
    else:
        installed = Path(runner.run(['xcrun', 'simctl', 'get_app_container', actor['id'], BUNDLE, 'app']).strip())
        if build_info(installed, 'debug') != expected or app_digest(installed) != app_digest(actor['app']):
            raise SmokeError('installed-normal-peer-build-mismatch')


def parse_routing_result(output: str, fixture: dict) -> dict:
    from dogfood_prepare import strict_json
    marker = 'WEAVE_ORDINARY_ROUTING_RESULT '
    try:
        results = [strict_json(line.split(marker, 1)[1]) for line in output.splitlines() if marker in line]
        if len(results) != 1:
            raise ValueError()
        value = results[0]
        state = value['visibleState']
        expected_keys = {'account', 'organization'} if state == 'retained-workspace' else {'organizationOrigin'}
        if (set(value) != {'schemaVersion', 'runId', 'nonce', 'status', 'visibleState', 'resources'}
                or value['schemaVersion'] != 'weave.ordinary-routing-result/v1'
                or any(value[key] != fixture[key] for key in ('runId', 'nonce'))
                or value['status'] != 'passed' or state not in {'retained-workspace', 'normal-sign-in'}
                or not isinstance(value['resources'], dict) or set(value['resources']) != expected_keys
                or any(not isinstance(item, str) or not HASH.fullmatch(item) for item in value['resources'].values())):
            raise ValueError()
        return value
    except (KeyError, TypeError, ValueError):
        raise SmokeError('ordinary-routing-result-missing-stale-or-invalid') from None


def verify_discovered_routing(runner: Runner, actor: dict, context, expected_identity: dict | None = None) -> str:
    """Bind trusted public discovery to the current normal UI, without app storage access.

    On sign-in the app displays its discovered origin. In a retained workspace,
    Help displays fresh User API identity references bound to the configured issuer;
    these must match the independently prepared actor and organization.
    """
    from dogfood_prepare import strict_json
    try:
        origin = canonical_origin(context.env['WEAVE_PUBLIC_URL'])
        request = Request(origin + 'api/platform/config', headers={'Accept': 'application/json'})
        tls = ssl.create_default_context(cafile=str(context.tls_root / 'ca.pem'))
        with open_https(request, tls, timeout=20) as response:
            if response.status != 200 or response.geturl() != request.full_url:
                raise ValueError()
            payload = response.read(1024 * 1024 + 1)
        if len(payload) > 1024 * 1024:
            raise ValueError()
        manifest = strict_json(payload.decode('utf-8'))
        if (type(manifest['schemaVersion']) is not int or manifest['schemaVersion'] != 2
                or canonical_origin(manifest['organizationOrigin']) != origin
                or manifest['userApiBaseUrl'].rstrip('/') != context.env['WEAVE_API_URL'].rstrip('/')
                or canonical_origin(manifest['protocols']['matrixClientServerBaseUrl']) != canonical_origin(context.env['WEAVE_API_ORIGIN'])
                or manifest['oidc']['issuer'] != canonical_origin(context.env['WEAVE_AUTH_URL']).rstrip('/') + '/realms/weave'
                or manifest['oidc']['clientId'] != context.env.get('WEAVE_CLIENT_ID', 'weave-app')):
            raise ValueError()
    except (OSError, KeyError, TypeError, ValueError, AttributeError):
        raise SmokeError('public-discovery-differs-from-reviewed-direct-context') from None
    fixture = {'schemaVersion': 'weave.ordinary-routing-input/v1', 'runId': 'routing-' + secrets.token_hex(8),
               'nonce': secrets.token_hex(16), 'organizationOrigin': origin}
    result = runner.ui(actor, fixture, 'testOrdinaryRouting', result_parser=parse_routing_result)
    if result['visibleState'] == 'normal-sign-in':
        if result['resources']['organizationOrigin'] != digest(origin.encode()):
            raise SmokeError('visible-organization-origin-mismatch')
    else:
        if (not isinstance(expected_identity, dict)
                or result['resources'] != {'account': expected_identity.get('accountDigest'),
                                           'organization': expected_identity.get('organizationDigest')}):
            raise SmokeError('observed-application-identity-differs-from-reviewed-direct-context')
    return result['visibleState']

def restart(runner: Runner, actor: dict) -> None:
    if actor['physical']:
        runner.run(['xcrun', 'devicectl', 'device', 'process', 'launch', '--device', actor['id'],
                    '--terminate-existing', BUNDLE])
    else:
        # Launch --terminate-running-process preserves the existing app container.
        runner.run(['xcrun', 'simctl', 'launch', '--terminate-running-process', actor['id'], BUNDLE])


def reinstall(runner: Runner, actor: dict) -> None:
    if actor['physical']:
        runner.run(['xcrun', 'devicectl', 'device', 'install', 'app', '--device', actor['id'], str(actor['app'])])
    else:
        runner.run(['xcrun', 'simctl', 'install', actor['id'], str(actor['app'])])
    verify_installed_build(runner, actor)
    restart(runner, actor)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--stage', required=True, choices=('deployment',))
    parser.add_argument('--candidate', required=True)
    parser.add_argument('--qualification-run-id', required=True, type=int)
    parser.add_argument('--qualification-run-attempt', required=True, type=int)
    parser.add_argument('--output', required=True, type=Path)
    parser.add_argument('--identity-file', type=Path, default=Path.home() / '.weave/dogfood/functional-smoke-identities.json')
    args = parser.parse_args()
    receipt = {'schemaVersion': 'weave.dogfood-functional-smoke/v3', 'status': 'incomplete',
               'stage': args.stage, 'candidateCommit': args.candidate,
               'supportSafe': True, 'containsSecretValues': False}
    previous_term = signal.getsignal(signal.SIGTERM)
    def terminate(_number, _frame):
        signal.signal(signal.SIGTERM, signal.SIG_IGN)
        raise SmokeError('functional-operation-terminated')
    signal.signal(signal.SIGTERM, terminate)
    devices: list[str] = []
    cleanup_complete = False
    started = datetime.now(timezone.utc).isoformat()
    try:
        if not SHA.fullmatch(args.candidate) or min(args.qualification_run_id, args.qualification_run_attempt) < 1:
            raise SmokeError('exact-candidate-and-qualification-required')
        if (os.environ.get('GITHUB_ACTIONS') != 'true' or os.environ.get('GITHUB_REF') != 'refs/heads/dogfood'
                or os.environ.get('RUNNER_NAME') != 'weave-live-mac-mini'):
            raise SmokeError('protected-exclusive-dogfood-workflow-required')
        source = subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT, text=True).strip()
        if source != args.candidate or subprocess.check_output(['git', 'status', '--porcelain'], cwd=ROOT):
            raise SmokeError('clean-exact-candidate-required')
        tree = subprocess.check_output(['git', 'rev-parse', 'HEAD^{tree}'], cwd=ROOT, text=True).strip()
        spec = json.loads((ROOT / 'specs/weave-specs.lock.json').read_text())['specCorpus']['gitCommit']
        context = load_context('dogfood', ROOT / 'infra/weave-workspace',
                               str(Path.home() / '.weave/dogfood/reviewed-compose.env'))
        origin = canonical_origin(context.env['WEAVE_PUBLIC_URL'])
        routing = {key: context.env[key] for key in ('WEAVE_PUBLIC_URL', 'WEAVE_API_ORIGIN', 'WEAVE_AUTH_URL', 'WEAVE_MATRIX_URL')}
        for value in routing.values():
            canonical_origin(value)
        receipt.update(sourceTree=tree, specificationCommit=spec, qualificationRunId=args.qualification_run_id,
                       qualificationRunAttempt=args.qualification_run_attempt)
        ensure_prepared_identities(context, args.identity_file)
        with _dogfood_session_lock(context), tempfile.TemporaryDirectory(prefix='weave-functional-') as temporary:
            directory = Path(temporary); directory.chmod(0o700)
            runner = Runner(directory)
            runner.configure_native_toolchain()
            verify_dogfood_identity(context)
            before = _service_container(context, 'backend', deadline=time.monotonic() + 30)
            from artifact_consumers import verify_runtime_container, native_bundle, copy_driver_products, component_evidence, candidate_composition
            runtime_artifact = verify_runtime_container(before, args.candidate)
            receipt['artifactComposition'] = candidate_composition(args.candidate)
            run_id = f"functional-{os.environ.get('GITHUB_RUN_ID', '')}-{os.environ.get('GITHUB_RUN_ATTEMPT', '')}-{args.stage}"
            if not re.fullmatch(r'functional-[1-9][0-9]*-[1-9][0-9]*-deployment', run_id):
                raise SmokeError('protected-run-identity-required')
            actors = {}
            approved = identities(args.identity_file, origin)
            binding = approved_identity_binding(context, args.identity_file, approved)
            runtime, device_type = select_runtime()
            simulator_manifest, simulator_app, simulator_products = native_bundle(candidate=args.candidate)
            simulator_build = build_info(simulator_app, 'debug')
            for role in ('A', 'B'):
                udid = runner.run(['xcrun', 'simctl', 'create', run_id + '-' + role, device_type, runtime]).strip()
                if not re.fullmatch(r'[0-9A-Fa-f-]{36}', udid):
                    raise SmokeError('owned-simulator-identity-invalid')
                devices.append(udid)
                runner.run(['xcrun', 'simctl', 'boot', udid])
                runner.run(['xcrun', 'simctl', 'bootstatus', udid, '-b'], timeout=180)
                runner.run(['xcrun', 'simctl', 'keychain', udid, 'add-root-cert', str(context.tls_root / 'ca.pem')])
                runner.run(['xcrun', 'simctl', 'install', udid, str(simulator_app)])
                derived = directory / ('driver-' + role)
                copy_driver_products(simulator_products, derived / 'Build/Products')
                actor = {'id': udid, 'physical': False, 'destination': 'platform=iOS Simulator,id=' + udid,
                         'products': derived / 'Build/Products', 'app': simulator_app}
                actors[role] = actor
                restart(runner, actor)
                runner.ui(actor, {'organizationOrigin': origin}, 'testOrdinaryOrganizationEntry')
                member = approved['members'][0 if role == 'A' else 1]
                runner.ui(actor, {**member, 'issuerHost': urlsplit(context.env['WEAVE_AUTH_URL']).hostname,
                                 'appBundleIdentifier': BUNDLE, 'phase': 'initial', 'nativeAuthCompletionPath': '',
                                 'ordinaryOrganizationOrigin': origin}, 'testProductionBrowser')
            resources = {}
            operations = []
            from member_test_scenario import resolve_scenario
            scenario = resolve_scenario(run_id)
            content = scenario['content']
            receipt.update(scenarioSha256=scenario['scenarioSha256'],
                           actorProfile='two-ordinary-members',
                           runtimeBuildArtifact=runtime_artifact,
                           simulatorBuildArtifact=component_evidence(simulator_manifest))
            base = {'schemaVersion': 'weave.ordinary-product-ui/v1', 'runId': run_id,
                    'organizationOrigin': origin, 'conversation': content['conversation'],
                    'messageA': content['messageA'], 'messageB': content['messageB'],
                    'folder': content['folder'], 'privateFolder': content['privateFolder'], 'filename': content['initialFileName'],
                    'filePayload': content['initialContent'], 'event': content['event'],
                    'updatedEvent': content['updatedEvent']}
            def phase(role: str, operation: str, evidence_name: str | None = None, **expected):
                receipt['boundaryOperation'] = role + ':' + (evidence_name or operation)
                restart(runner, actors[role])
                fixture = {**base, 'nonce': secrets.token_hex(16), 'operation': operation,
                           'participant': approved['members'][1 if role == 'A' else 0]['displayName'],
                           'expected': {**resources, **expected}}
                result = runner.ui(actors[role], fixture)
                for key, value in result['resources'].items():
                    if role == 'B' and key == 'account':
                        key = 'peerAccount'
                    if key in resources and resources[key] != value:
                        raise SmokeError('independent-resource-identity-mismatch')
                    if key != 'deletedEvent':
                        resources[key] = value
                if operation == 'identity':
                    verify_installed_build(runner, actors[role])
                    expected_identity = binding['members'][role]
                    if verify_discovered_routing(runner, actors[role], context, expected_identity) != 'retained-workspace':
                        raise SmokeError('current-member-workspace-required')
                operations.append(role + ':' + (evidence_name or operation))
                # Keep a private ownership ledger for retained run-owned content.
                # It is not a credential input or release acceptance evidence.
                ledger = Path.home() / '.weave/dogfood/functional-smoke-owned-content'
                if ledger.is_symlink() or any(parent.is_symlink() for parent in ledger.parents):
                    raise SmokeError('private-owned-content-ledger-unsafe')
                ledger.mkdir(mode=0o700, exist_ok=True)
                if ledger.stat().st_uid != os.getuid() or ledger.stat().st_mode & 0o077:
                    raise SmokeError('private-owned-content-ledger-unsafe')
                record = ledger / (run_id + '.json')
                if record.is_symlink():
                    raise SmokeError('private-owned-content-ledger-unsafe')
                with record.open('w') as stream:
                    os.fchmod(stream.fileno(), 0o600)
                    json.dump({'candidateCommit': args.candidate, 'runId': run_id, 'content': base,
                               'resourceIdentityDigests': resources, 'observedOperations': operations,
                               'cleanupStatus': 'retained-owned-content'}, stream)

            phase('A', 'identity', 'identity-before')
            verify_actor_binding(binding, resources, 'A')
            phase('B', 'identity')
            verify_actor_binding(binding, resources, 'B')
            if resources['account'] == resources['peerAccount']:
                raise SmokeError('independent-current-test-member-identities-required')
            phase('A', 'chat-create'); phase('B', 'chat-peer'); phase('A', 'chat-readback')
            phase('A', 'folder-create')
            peer_container = Path(runner.run(['xcrun', 'simctl', 'get_app_container', actors['B']['id'], BUNDLE, 'data']).strip())
            source_file = peer_container / 'Documents' / base['filename']
            if source_file.exists() or source_file.is_symlink():
                raise SmokeError('owned-source-file-conflict')
            source_file.write_text(base['filePayload']); source_file.chmod(0o600)
            phase('B', 'file-upload'); phase('A', 'file-readback')
            if source_file.read_bytes() != base['filePayload'].encode():
                raise SmokeError('owned-source-file-changed')
            source_file.unlink()
            phase('B', 'file-export')
            if source_file.is_symlink() or not source_file.is_file() or source_file.read_bytes() != base['filePayload'].encode():
                raise SmokeError('exported-file-byte-readback-mismatch')
            exported_digest = digest(source_file.read_bytes())
            source_file.unlink()
            phase('A', 'private-folder-create'); phase('A', 'private-scope-switch')
            phase('A', 'private-tab-restore'); phase('B', 'private-folder-absent')
            phase('A', 'calendar-create'); phase('B', 'calendar-read')
            phase('A', 'calendar-update'); phase('B', 'calendar-read', 'calendar-updated', updated='true')
            phase('A', 'calendar-delete', updated='true'); phase('B', 'calendar-absent', updated='true')
            phase('A', 'chat-readback', 'restart-chat'); phase('A', 'file-readback', 'restart-file')
            phase('A', 'private-folder-read', 'restart-private-folder')
            before_build = app_digest(actors['A']['app'])
            reinstall(runner, actors['A'])
            phase('A', 'chat-readback', 'update-chat'); phase('A', 'file-readback', 'update-file')
            phase('A', 'private-folder-read', 'update-private-folder')
            phase('A', 'identity', 'identity-after')
            after = _service_container(context, 'backend', deadline=time.monotonic() + 30)
            if after.get('Image') != before.get('Image') or app_digest(actors['A']['app']) != before_build:
                raise SmokeError('candidate-build-changed-during-smoke')
            receipt.update(status='passed', operations=operations, resourceIdentityDigests=resources,
                           entrypoint='lib/main.dart', routing='reviewed-direct', routingSha256=digest(json.dumps(routing, sort_keys=True).encode()),
                           appSessionInjection=False, proxyUsed=False, ownerSessionReplaced=False,
                           cleanupStatus='retained-owned-content', retainedStateRestartVerified=True,
                           retainedStateUpdateVerified=True, calendarPeerAbsenceVerified=True, independentPeerReadbackVerified=True,
                           privateFilesScopeIsolationVerified=True, privateFilesPeerIsolationVerified=True, privateFilesContinuityVerified=True,
                           deployedSourceTree=runtime_artifact['sourceTree'], deployedImage=before['Image'], appBuildSha256=before_build,
                           peerAppBuildSha256=app_digest(simulator_app), exportedFileSha256=exported_digest,
                           build=simulator_build,
                           deviceIdentitySha256=digest(actors['A']['id'].encode()),
                           startedAtUtc=started, verifiedAtUtc=datetime.now(timezone.utc).isoformat())
            # Remove only the two freshly created, invocation-owned simulators.
            for udid in list(devices):
                runner.run(['xcrun', 'simctl', 'delete', udid], timeout=60)
                devices.remove(udid)
            cleanup_complete = True
        receipt.update(ownedSimulatorsRemoved=True, privateInvocationInputsRemoved=True)
        if not functional_smoke_passed(receipt, args.candidate, tree, spec, args.qualification_run_id,
                                       args.qualification_run_attempt, args.stage):
            raise SmokeError('functional-receipt-incomplete')
    except Exception as failure:
        receipt['status'] = 'incomplete'
        receipt['boundary'] = str(failure) if isinstance(failure, SmokeError) else 'functional-operation-unavailable'
    finally:
        signal.signal(signal.SIGTERM, signal.SIG_IGN)
        # The only remaining runtime resources allowed here are owned simulators.
        for udid in devices:
            try:
                subprocess.run(['xcrun', 'simctl', 'delete', udid], capture_output=True, timeout=60, check=True)
            except (OSError, subprocess.SubprocessError):
                receipt['boundary'] = 'owned-simulator-cleanup-incomplete'
                receipt['ownedSimulatorsRemoved'] = False
                receipt['status'] = 'incomplete'
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(json.dumps(receipt, indent=2, sort_keys=True) + '\n')
        signal.signal(signal.SIGTERM, previous_term)
    print('DOGFOOD_FUNCTIONAL_SMOKE_RESULT status=' + receipt['status'] + ' stage=' + args.stage + ' supportSafe=true')
    return 0 if receipt['status'] == 'passed' and cleanup_complete else 2


if __name__ == '__main__':
    raise SystemExit(main())
