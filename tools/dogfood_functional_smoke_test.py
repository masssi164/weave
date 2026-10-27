#!/usr/bin/env python3
"""Offline negative tests for real UI producer/receipt boundaries, never live proof."""
import ast
import copy
from contextlib import nullcontext
import io
import json
import os
from pathlib import Path
import signal
import plistlib
from unittest.mock import patch
import subprocess
import sys
import tempfile
from types import SimpleNamespace
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parent))
import dogfood_functional_smoke as smoke
from tools.artifact_evidence_test import composition_fixture, component_fixture
from tools.member_test_scenario import scenario_sha256


def receipt(stage='deployment', sha='a' * 40, tree='b' * 40, spec='c' * 40, run_id=123, attempt=1):
    composition = composition_fixture(sha, tree, spec)
    return {
        'artifactComposition': composition, 'scenarioSha256': scenario_sha256(),
        'actorProfile': 'two-ordinary-members',
        'runtimeBuildArtifact': component_fixture(composition, 'runtime-linux-amd64'),
        'simulatorBuildArtifact': component_fixture(composition, 'ios-simulator'),
        'schemaVersion': 'weave.dogfood-functional-smoke/v3', 'candidateCommit': sha,
        'sourceTree': tree, 'specificationCommit': spec, 'qualificationRunId': run_id,
        'qualificationRunAttempt': attempt, 'stage': stage, 'status': 'passed',
        'entrypoint': 'lib/main.dart', 'routing': 'reviewed-direct', 'appSessionInjection': False,
        'proxyUsed': False, 'ownerSessionReplaced': False, 'cleanupStatus': 'retained-owned-content',
        'ownedSimulatorsRemoved': True, 'privateInvocationInputsRemoved': True,
        'supportSafe': True, 'containsSecretValues': False, 'retainedStateRestartVerified': True,
        'retainedStateUpdateVerified': True, 'calendarPeerAbsenceVerified': True,
        'independentPeerReadbackVerified': True, 'privateFilesScopeIsolationVerified': True,
        'privateFilesPeerIsolationVerified': True, 'privateFilesContinuityVerified': True, 'deployedSourceTree': composition['components']['runtime-linux-amd64']['sourceTree'],
        'operations': list(smoke.OPERATIONS),
        'resourceIdentityDigests': {**{key: 'd' * 64 for key in ('conversation', 'folder', 'file', 'event', 'account', 'organization')}, 'peerAccount': 'e' * 64, 'privateFolder': 'f' * 64},
        'appBuildSha256': '1' * 64, 'peerAppBuildSha256': '2' * 64, 'routingSha256': '3' * 64,
        'exportedFileSha256': '4' * 64, 'deployedImage': 'sha256:' + '5' * 64,
        'deviceIdentitySha256': '6' * 64,
        'build': {'bundleId': smoke.BUNDLE, 'buildNumber': '20', 'mode': 'debug'},
        'startedAtUtc': '2026-10-22T10:00:00Z', 'verifiedAtUtc': '2026-10-22T10:30:00Z',
    }


class FunctionalSmokeTest(unittest.TestCase):
    def test_native_toolchain_overrides_homebrew_first_path_for_actual_child_without_global_mutation(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            brew = root / 'homebrew/bin'; brew.mkdir(parents=True)
            shims = root / 'rustup/bin'; shims.mkdir(parents=True)
            # All three shims share one executable, as rustup's real proxies do.
            proxy = root / 'proxy'
            proxy.write_text('#!' + sys.executable + '\nimport os, pathlib\n'
                             'print(pathlib.Path(__file__).name + ":" + os.environ.get("RUSTUP_TOOLCHAIN", "unset"))\n')
            proxy.chmod(0o700)
            for name in ('rustup', 'cargo', 'rustc'):
                (shims / name).symlink_to(proxy)
            for name in ('cargo', 'rustc'):
                executable = brew / name
                executable.write_text('#!' + sys.executable + '\nprint("homebrew-1.98.0")\n')
                executable.chmod(0o700)
            ambient = str(brew) + os.pathsep + str(shims)
            with patch.dict(os.environ, {'PATH': ambient, 'RUSTUP_TOOLCHAIN': 'foreign'}):
                original = os.environ.copy()
                runner = smoke.Runner(root)
                self.assertEqual(runner.run(['cargo']).strip(), 'homebrew-1.98.0')
                with patch.object(smoke, 'inspect_toolchain', return_value={'rustToolchain': '1.95.0'}) as inspect:
                    runner.configure_native_toolchain()
                inspect.assert_called_once_with()
                self.assertEqual(runner.run(['cargo']).strip(), 'cargo:1.95.0')
                self.assertEqual(runner.run(['rustc']).strip(), 'rustc:1.95.0')
                self.assertEqual(runner.environment['PATH'], str(shims) + os.pathsep + ambient)
                self.assertEqual(os.environ, original)

    def test_unavailable_native_toolchain_is_closed_and_keeps_environment_unchanged(self):
        for failure in (None, smoke.NativeFailure('infrastructure', 'rust-toolchain', 'private-detail'),
                        FileNotFoundError('private-path')):
            with self.subTest(failure=type(failure).__name__), tempfile.TemporaryDirectory() as directory:
                runner = smoke.Runner(Path(directory))
                original = runner.environment.copy()
                with patch.object(smoke, 'inspect_toolchain', return_value={'rustToolchain': '1.95.0'}, side_effect=failure), \
                        patch.object(smoke.shutil, 'which', return_value=None), patch.object(runner, 'run') as execute:
                    with self.assertRaisesRegex(smoke.SmokeError, '^pinned-native-toolchain-unavailable$'):
                        runner.configure_native_toolchain()
                self.assertEqual(runner.environment, original)
                execute.assert_not_called()

    def test_deployment_checks_toolchain_before_native_commands(self):
        for stage in ('deployment',):
            with self.subTest(stage=stage), tempfile.TemporaryDirectory() as directory:
                output = Path(directory) / 'functional-smoke.json'
                arguments = ['dogfood_functional_smoke.py', '--stage', stage, '--candidate', 'a' * 40,
                             '--qualification-run-id', '123', '--qualification-run-attempt', '1',
                             '--output', str(output)]
                context = SimpleNamespace(env={key: 'https://' + host + '.fixture.invalid' for key, host in (
                    ('WEAVE_PUBLIC_URL', 'org'), ('WEAVE_API_ORIGIN', 'api'),
                    ('WEAVE_AUTH_URL', 'auth'), ('WEAVE_MATRIX_URL', 'matrix'))})
                with patch.object(sys, 'argv', arguments), \
                        patch.dict(os.environ, {'GITHUB_ACTIONS': 'true', 'GITHUB_REF': 'refs/heads/dogfood',
                                                'RUNNER_NAME': 'weave-live-mac-mini'}), \
                        patch.object(smoke.subprocess, 'check_output', side_effect=['a' * 40, b'', 'b' * 40]), \
                        patch.object(smoke, 'load_context', return_value=context), \
                        patch.object(smoke, 'ensure_prepared_identities'), \
                        patch.object(smoke, '_dogfood_session_lock', return_value=nullcontext()), \
                        patch.object(smoke, 'inspect_toolchain', side_effect=smoke.NativeFailure('infrastructure', 'rust-toolchain', 'private-detail')) as inspect, \
                        patch.object(smoke.Runner, 'run') as execute, patch.object(smoke, 'verify_dogfood_identity') as identity, \
                        patch('sys.stdout', new_callable=io.StringIO):
                    self.assertEqual(smoke.main(), 2)
                inspect.assert_called_once_with(); execute.assert_not_called(); identity.assert_not_called()
                result = json.loads(output.read_text())
                self.assertEqual(result['status'], 'incomplete')
                self.assertEqual(result['boundary'], 'pinned-native-toolchain-unavailable')
                self.assertNotIn('private-detail', output.read_text())

    def test_missing_input_requires_preparation_before_functional_execution(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'identities.json'
            with patch.object(smoke, '_dogfood_session_lock') as lock:
                with self.assertRaisesRegex(smoke.SmokeError, '^prepared-member-scenario-required$'):
                    smoke.ensure_prepared_identities(SimpleNamespace(), path)
            lock.assert_not_called()
            path.write_text('existing')
            smoke.ensure_prepared_identities(SimpleNamespace(), path)

    def test_ordinary_member_binding_precedes_writes_and_rejects_an_owner_session(self):
        binding = {'owner': {'accountDigest': 'a' * 64, 'organizationDigest': 'c' * 64},
                   'members': {'A': {'accountDigest': 'd' * 64, 'organizationDigest': 'c' * 64},
                               'B': {'accountDigest': 'b' * 64, 'organizationDigest': 'c' * 64}}}
        values = {'account': 'd' * 64, 'peerAccount': 'b' * 64, 'organization': 'c' * 64}
        smoke.verify_actor_binding(binding, values, 'A')
        smoke.verify_actor_binding(binding, values, 'B')
        for key, wrong in (('account', 'a' * 64), ('organization', 'e' * 64)):
            with self.assertRaisesRegex(smoke.SmokeError, '^approved-current-test-member-identity-mismatch$'):
                smoke.verify_actor_binding(binding, {**values, key: wrong}, 'A')
        source = Path(smoke.__file__).read_text()
        self.assertLess(source.index("verify_actor_binding(binding, resources, 'A')"), source.index("phase('A', 'chat-create')"))
        self.assertLess(source.index('ensure_prepared_identities(context, args.identity_file)'), source.index('with _dogfood_session_lock(context),'))

    def test_retired_physical_functional_entry_and_receipts_are_rejected(self):
        for option in ('--stage physical', '--stage deployment --diagnostic-only', '--stage deployment --device-id phone'):
            with patch.object(sys, 'argv', ['dogfood_functional_smoke.py', *option.split(), '--candidate', 'a' * 40,
                                           '--qualification-run-id', '123', '--qualification-run-attempt', '1', '--output', 'unused.json']), \
                    patch('sys.stderr', new_callable=io.StringIO), patch.object(smoke, 'load_context') as context:
                with self.assertRaises(SystemExit) as failure:
                    smoke.main()
                self.assertEqual(failure.exception.code, 2)
                context.assert_not_called()
        self.assertFalse(smoke.functional_smoke_passed(receipt(stage='physical'), 'a' * 40, 'b' * 40, 'c' * 40, 123, 1, 'physical'))

    def valid(self, value):
        return smoke.functional_smoke_passed(value, 'a' * 40, 'b' * 40, 'c' * 40, 123, 1, 'deployment')

    def routing_manifest(self):
        return {
            'schemaVersion': 2,
            'organizationOrigin': 'https://org.fixture.invalid/',
            'userApiBaseUrl': 'https://api.fixture.invalid/api',
            'protocols': {'matrixClientServerBaseUrl': 'https://api.fixture.invalid'},
            'oidc': {'issuer': 'https://auth.fixture.invalid/realms/weave', 'clientId': 'weave-app'},
        }

    def verify_external_routing(self, manifest=None, *, physical=False, state='retained-workspace',
                                resources=None, expected_identity=True, status=200, response_url=None, payload=None):
        origin = 'https://org.fixture.invalid/'
        context = SimpleNamespace(env={
            'WEAVE_PUBLIC_URL': origin,
            'WEAVE_API_ORIGIN': 'https://api.fixture.invalid',
            'WEAVE_API_URL': 'https://api.fixture.invalid/api',
            'WEAVE_AUTH_URL': 'https://auth.fixture.invalid',
        }, tls_root=Path('/reviewed/tls'))
        expected = {'accountDigest': 'a' * 64, 'organizationDigest': 'b' * 64} if expected_identity is True else expected_identity
        observed = resources if resources is not None else (
            {'account': 'a' * 64, 'organization': 'b' * 64} if state == 'retained-workspace' else
            {'organizationOrigin': smoke.digest(origin.encode())})
        class Response(io.BytesIO):
            def geturl(self):
                return response_url or origin + 'api/platform/config'
        response = Response(payload if payload is not None else json.dumps(manifest or self.routing_manifest()).encode())
        response.status = status
        tls = smoke.ssl.create_default_context()
        with tempfile.TemporaryDirectory() as directory:
            runner = smoke.Runner(Path(directory))
            actor = {'physical': physical, 'id': 'owned-device'}
            def visible_context(actual_actor, fixture, method, *, result_parser):
                self.assertEqual(actual_actor, actor)
                self.assertEqual(method, 'testOrdinaryRouting')
                self.assertEqual(fixture['organizationOrigin'], origin)
                self.assertEqual(fixture['schemaVersion'], 'weave.ordinary-routing-input/v1')
                self.assertEqual(len(fixture['nonce']), 32)
                result = {'schemaVersion': 'weave.ordinary-routing-result/v1',
                          'runId': fixture['runId'], 'nonce': fixture['nonce'], 'status': 'passed',
                          'visibleState': state, 'resources': observed}
                return result_parser('WEAVE_ORDINARY_ROUTING_RESULT ' + json.dumps(result), fixture)
            with patch.object(smoke.ssl, 'create_default_context', return_value=tls) as trust, \
                    patch.object(smoke, 'open_https', return_value=response) as request, \
                    patch.object(runner, 'ui', side_effect=visible_context) as ui, \
                    patch.object(runner, 'run', side_effect=AssertionError('product storage must not be read')) as command:
                try:
                    return smoke.verify_discovered_routing(runner, actor, context, expected)
                finally:
                    trust.assert_called_once_with(cafile='/reviewed/tls/ca.pem')
                    request.assert_called_once()
                    self.assertEqual(request.call_args.args[0].full_url, origin + 'api/platform/config')
                    self.assertEqual(request.call_args.args[0].get_method(), 'GET')
                    self.assertEqual(request.call_args.kwargs, {'timeout': 20})
                    self.assertTrue(request.call_args.args[1].check_hostname)
                    self.assertEqual(request.call_args.args[1].verify_mode, smoke.ssl.CERT_REQUIRED)
                    command.assert_not_called()
                    if ui.called:
                        self.assertEqual(ui.call_count, 1)

    def test_external_routing_binds_trusted_discovery_to_current_identity_on_both_devices(self):
        for physical in (False, True):
            for facade in ('https://api.fixture.invalid', 'https://api.fixture.invalid/'):
                with self.subTest(physical=physical, facade=facade):
                    manifest = self.routing_manifest()
                    manifest['protocols']['matrixClientServerBaseUrl'] = facade
                    self.assertEqual(self.verify_external_routing(manifest, physical=physical), 'retained-workspace')

    def test_external_routing_rejects_wrong_origin_api_provider_issuer_and_client(self):
        changes = (
            (('schemaVersion',), 3),
            (('organizationOrigin',), 'https://foreign.fixture.invalid'),
            (('userApiBaseUrl',), 'https://other-api.fixture.invalid/api'),
            (('userApiBaseUrl',), 'https://api.fixture.invalid/other-api'),
            (('oidc', 'issuer'), 'https://other-auth.fixture.invalid/realms/weave'),
            (('oidc', 'issuer'), 'http://auth.fixture.invalid/realms/weave'),
            (('oidc', 'issuer'), 'https://user:private@auth.fixture.invalid/realms/weave'),
            (('oidc', 'issuer'), 'https://auth.fixture.invalid/realms/other'),
            (('oidc', 'issuer'), 'https://auth.fixture.invalid/realms/weave?unexpected=1'),
            (('oidc', 'clientId'), 'other-client'),
            (('protocols', 'matrixClientServerBaseUrl'), 'https://matrix-provider.fixture.invalid'),
            (('protocols', 'matrixClientServerBaseUrl'), 'https://other-api.fixture.invalid'),
            (('protocols', 'matrixClientServerBaseUrl'), 'https://api.fixture.invalid:8443'),
            (('protocols', 'matrixClientServerBaseUrl'), 'http://api.fixture.invalid'),
            (('protocols', 'matrixClientServerBaseUrl'), 'https://user:private@api.fixture.invalid'),
            (('protocols', 'matrixClientServerBaseUrl'), 'https://api.fixture.invalid/provider'),
            (('protocols', 'matrixClientServerBaseUrl'), 'https://api.fixture.invalid?unexpected=1'),
            (('protocols', 'matrixClientServerBaseUrl'), 'https://api.fixture.invalid#unexpected'),
        )
        for keys, value in changes:
            with self.subTest(keys=keys, value=value):
                manifest = self.routing_manifest()
                target = manifest
                for key in keys[:-1]:
                    target = target[key]
                target[keys[-1]] = value
                with self.assertRaisesRegex(smoke.SmokeError, '^public-discovery-differs-from-reviewed-direct-context$'):
                    self.verify_external_routing(manifest)

    def test_public_discovery_cannot_substitute_for_the_current_app_context(self):
        for resources, expected in (({'account': 'c' * 64, 'organization': 'b' * 64}, True),
                                    ({'account': 'a' * 64, 'organization': 'c' * 64}, True),
                                    ({'account': 'a' * 64, 'organization': 'b' * 64}, None)):
            with self.assertRaisesRegex(smoke.SmokeError, '^observed-application-identity-differs-from-reviewed-direct-context$'):
                self.verify_external_routing(resources=resources, expected_identity=expected)
        self.assertEqual(self.verify_external_routing(state='normal-sign-in', expected_identity=None), 'normal-sign-in')
        with self.assertRaisesRegex(smoke.SmokeError, '^visible-organization-origin-mismatch$'):
            self.verify_external_routing(state='normal-sign-in', resources={'organizationOrigin': 'f' * 64})

    def test_external_routing_rejects_redirect_errors_oversize_and_ambiguous_discovery(self):
        for options in ({'status': 302}, {'response_url': 'https://foreign.fixture.invalid/api/platform/config'},
                        {'payload': b'x' * (1024 * 1024 + 1)}, {'payload': b'[]'},
                        {'payload': b'{"schemaVersion":2,"schemaVersion":2}'}, {'payload': b'\xff'}):
            with self.subTest(options=list(options)):
                with self.assertRaisesRegex(smoke.SmokeError, '^public-discovery-differs-from-reviewed-direct-context$'):
                    self.verify_external_routing(**options)

    def test_routing_ui_result_is_nonce_bound_unique_and_requires_observed_resources(self):
        fixture = {'runId': 'routing-current', 'nonce': '1' * 32}
        result = {'schemaVersion': 'weave.ordinary-routing-result/v1', **fixture, 'status': 'passed',
                  'visibleState': 'retained-workspace', 'resources': {'account': 'a' * 64, 'organization': 'b' * 64}}
        encode = lambda value: 'WEAVE_ORDINARY_ROUTING_RESULT ' + json.dumps(value)
        self.assertEqual(smoke.parse_routing_result(encode(result), fixture), result)
        invalid = ['', encode(result) + '\n' + encode(result), 'WEAVE_ORDINARY_ROUTING_RESULT []']
        for key in result:
            missing = dict(result); missing.pop(key); invalid.append(encode(missing))
        for key, value in (('nonce', '2' * 32), ('runId', 'routing-old'), ('status', 'incomplete'),
                           ('visibleState', 'entry-requested'), ('resources', {}),
                           ('resources', {'organizationOrigin': 'a' * 64}),
                           ('resources', {'account': 'raw-id', 'organization': 'b' * 64})):
            invalid.append(encode({**result, key: value}))
        for output in invalid:
            with self.assertRaises(smoke.SmokeError):
                smoke.parse_routing_result(output, fixture)

    def test_only_complete_ordinary_producer_receipt_passes(self):
        value = receipt()
        self.assertTrue(self.valid(value))
        for key in value:
            broken = copy.deepcopy(value); del broken[key]
            self.assertFalse(self.valid(broken), key)
        for key, bad in [('status', 'incomplete'), ('entrypoint', 'integration_test/dual_ios_collaboration_test.dart'),
                         ('routing', 'relay'), ('proxyUsed', True), ('appSessionInjection', True),
                         ('ownerSessionReplaced', True), ('qualificationRunAttempt', 2),
                         ('qualificationRunId', True), ('cleanupStatus', 'all-data-removed'),
                         ('deployedSourceTree', '9' * 40), ('sourceTree', 'f' * 40)]:
            self.assertFalse(self.valid({**value, key: bad}), key)
        for bad in ([], ['A:discover'], list(smoke.OPERATIONS)[:-1], list(reversed(smoke.OPERATIONS))):
            self.assertFalse(self.valid({**value, 'operations': bad}))
        self.assertFalse(self.valid({**value, 'build': {**value['build'], 'mode': 'profile'}}))
        self.assertFalse(self.valid({**value, 'verifiedAtUtc': '2026-10-22T12:00:00Z'}))

    def test_private_files_proof_rejects_old_partial_and_scope_reused_receipts(self):
        value = receipt()
        self.assertFalse(self.valid({**value, 'schemaVersion': 'weave.dogfood-functional-smoke/v1'}))
        for flag in ('privateFilesScopeIsolationVerified', 'privateFilesPeerIsolationVerified', 'privateFilesContinuityVerified'):
            for bad in (False, None, 1, 'passed'):
                self.assertFalse(self.valid({**value, flag: bad}), (flag, bad))
        for bad in (None, '', 'raw-private-folder-id', value['resourceIdentityDigests']['folder']):
            broken = copy.deepcopy(value)
            broken['resourceIdentityDigests']['privateFolder'] = bad
            self.assertFalse(self.valid(broken), bad)
        broken = copy.deepcopy(value)
        del broken['resourceIdentityDigests']['privateFolder']
        self.assertFalse(self.valid(broken))
        required = ('A:private-folder-create', 'A:private-scope-switch', 'A:private-tab-restore',
                    'B:private-folder-absent', 'A:restart-private-folder', 'A:update-private-folder')
        for operation in required:
            self.assertIn(operation, smoke.OPERATIONS)
            self.assertFalse(self.valid({**value, 'operations': [item for item in smoke.OPERATIONS if item != operation]}))

    def test_host_observed_operation_order_matches_the_strict_receipt(self):
        tree = ast.parse(Path(smoke.__file__).read_text())
        main = next(item for item in tree.body if isinstance(item, ast.FunctionDef) and item.name == 'main')
        observed = []
        class Phases(ast.NodeVisitor):
            def visit_Call(self, node):
                if isinstance(node.func, ast.Name) and node.func.id == 'phase':
                    role, operation = (arg.value for arg in node.args[:2])
                    observed.append(role + ':' + (node.args[2].value if len(node.args) > 2 else operation))
                self.generic_visit(node)
        Phases().visit(main)
        self.assertEqual(observed, list(smoke.OPERATIONS))

    def test_private_negative_ui_requires_current_complete_listing_not_visible_rows_only(self):
        driver = (smoke.CLIENT / 'tool/ios_browser_driver/OrdinaryProductSmoke.swift').read_text()
        fresh = driver.split('struct OrdinaryFilesRead {', 1)[1].split('// End current Files read proof.', 1)[0]
        self.assertIn('value > 0', fresh)
        self.assertIn('guard let observed = observed else { return false }', fresh)
        self.assertIn('observed > previous', fresh)
        root = driver.split('func smokeFilesRoot(', 1)[1].split('func smokeCurrentPrivateFolder(', 1)[0]
        self.assertIn('try smokeAwaitFilesRead(app)', root)
        self.assertIn('try smokeRefreshFiles(app)', root)
        self.assertIn('back.element.tap()', root)
        self.assertIn('remainingBacks > 0', root)
        self.assertIn('overview.count == 1 && overview.element.isHittable', root)
        self.assertLess(root.index('"files-location-space-workspace-default"'), root.index('try smokeRefreshFiles(app)'))
        self.assertNotIn('files-space-selector', root)
        self.assertIn('guard root.isSelected', root)
        negative = driver.split('func smokePrivateFolderAbsent(', 1)[1].split('func smokeConversation(', 1)[0]
        self.assertIn('try smokeFilesGeneration(app) == generation', negative)
        self.assertIn('observed == expected || row.label.contains(fixture.privateFolder)', negative)
        self.assertIn('"files-listing-end"', negative)
        self.assertIn('end.count == 1 && end.element.isHittable', negative)
        self.assertIn('guard scrolling.remaining > 0 else', negative)
        self.assertIn('try smokeScroll(&scrolling)', negative)
        self.assertNotIn('guard rows.count == 0', negative)
        current = driver.split('func smokeCurrentPrivateFolder(', 1)[1].split('func smokeFolder(', 1)[0]
        self.assertIn('"files-entry-" + current.element.identifier.dropFirst("files-current-folder-".count)', current)
        self.assertIn('guard observed == expected', current)
        tab = driver.split('case "private-tab-restore":', 1)[1].split('case "private-folder-absent":', 1)[0]
        restored = tab.split('try smokeTap(app, "nav-chat")', 1)[1]
        self.assertEqual(restored.count('try smokeCurrentPrivateFolder(app, expected: expected)'), 2)
        self.assertNotIn('smokeFilesRoot', restored)
        self.assertNotIn('smokePrivateFolder(app', restored)

    def test_xctest_result_is_fresh_unique_and_closed(self):
        fixture = {'runId': 'owned-run', 'nonce': '1' * 32, 'operation': 'chat-peer'}
        result = {'schemaVersion': 'weave.ordinary-product-ui-result/v1', **fixture, 'status': 'passed',
                  'resources': {'conversation': 'f' * 64}}
        output = 'prefix WEAVE_FUNCTIONAL_UI_RESULT ' + json.dumps(result)
        self.assertEqual(smoke.parse_ui_result(output, fixture), result)
        for broken in ('', output + '\n' + output, output.replace('owned-run', 'old-run'),
                       output.replace('"passed"', '"incomplete"'), output.replace('f' * 64, 'raw-member-id')):
            with self.assertRaises(smoke.SmokeError):
                smoke.parse_ui_result(broken, fixture)

    def test_private_identity_input_rejects_unsafe_permissions_and_session_material(self):
        with tempfile.TemporaryDirectory() as directory:
            parent = Path(directory).resolve(); parent.chmod(0o700)
            path = parent / 'members.json'
            fixture = {'schemaVersion': 'weave.dogfood-functional-identities/v1',
                       'organizationOrigin': 'https://org.fixture.invalid/', 'ownerDisplayName': 'Owner',
                       'members': [{'email': f'{role}@fixture.invalid', 'password': 'private', 'displayName': role} for role in ('a', 'b')]}
            path.write_text(json.dumps(fixture)); path.chmod(0o600)
            self.assertEqual(smoke.identities(path, fixture['organizationOrigin']), fixture)
            path.chmod(0o644)
            with self.assertRaises(smoke.SmokeError): smoke.identities(path, fixture['organizationOrigin'])
            path.chmod(0o600)
            fixture['sessionToken'] = 'never-supported'; path.write_text(json.dumps(fixture))
            with self.assertRaises(smoke.SmokeError): smoke.identities(path, fixture['organizationOrigin'])
            link = parent / 'link'; link.symlink_to(path)
            with self.assertRaises(smoke.SmokeError): smoke.private_json(link)

    def test_native_command_timeout_reaps_process_and_keeps_raw_output_private(self):
        with tempfile.TemporaryDirectory() as directory:
            runner = smoke.Runner(Path(directory))
            with self.assertRaises(subprocess.TimeoutExpired):
                runner.run([sys.executable, '-c', 'import time; print("private fixture value", flush=True); time.sleep(60)'], timeout=1)
            self.assertIn('private fixture value', (Path(directory) / 'command-1.log').read_text())
            self.assertEqual((Path(directory) / 'command-1.log').stat().st_mode & 0o777, 0o600)

    def test_missing_normal_auth_remains_incomplete_without_setup_impersonation(self):
        with tempfile.TemporaryDirectory() as directory:
            with self.assertRaisesRegex(smoke.SmokeError, 'approved-test-member-authentication-required'):
                smoke.identities(Path(directory) / 'missing.json', 'https://org.fixture.invalid/')

    def test_physical_fixture_uses_only_owned_signed_runner_container(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory).resolve()
            products = root / 'Build/Products'; products.mkdir(parents=True)
            plan = products / 'driver.xctestrun'
            plan.write_bytes(plistlib.dumps({'BrowserDriver': {'BlueprintName': 'BrowserDriver'}}))
            actor = {'products': products, 'physical': True, 'id': 'owned-device',
                     'destination': 'platform=iOS,id=owned-device',
                     'driverBundle': 'com.massimotter.weave.integration.browser.xctrunner'}
            fixture = {'runId': 'run-owned', 'nonce': '1' * 32, 'operation': 'discover'}
            result = {'schemaVersion': 'weave.ordinary-product-ui-result/v1', **fixture,
                      'status': 'passed', 'resources': {}}
            runner = smoke.Runner(root)
            with patch.object(runner, 'run', side_effect=['', 'WEAVE_FUNCTIONAL_UI_RESULT ' + json.dumps(result)]) as execute:
                self.assertEqual(runner.ui(actor, fixture), result)
            command = execute.call_args_list[0].args[0]
            self.assertIn('appDataContainer', command)
            self.assertIn(actor['driverBundle'], command)
            self.assertNotIn(smoke.BUNDLE, command)
            self.assertIn('Documents/private-member.json', command)
            self.assertEqual(plistlib.loads(plan.read_bytes())['BrowserDriver']['EnvironmentVariables']['WEAVE_NATIVE_DRIVER_FIXTURE_PATH'],
                             'runner-documents:private-member.json')
            self.assertFalse((products / 'private-member.json').exists())


    def test_external_routing_uses_normal_visible_controls_without_app_preferences(self):
        driver = (smoke.CLIENT / 'tool/ios_browser_driver/OrdinaryProductSmoke.swift').read_text()
        routing = driver.split('func testOrdinaryRouting()', 1)[1].split('func testOrdinaryPreparation()', 1)[0]
        self.assertIn('origins.element.label', routing)
        self.assertIn('origins.count == 1 && origins.element.isHittable', routing)
        self.assertIn('Data(observed.utf8)', routing)
        self.assertIn('smokeAwaitIdentityReferences', routing)
        self.assertIn('"settings-current-account-reference"', routing)
        self.assertIn('"settings-current-organization-reference"', routing)
        self.assertNotIn('Data(origin.utf8)', routing)
        self.assertNotIn('UserDefaults', routing)
        producer = Path(smoke.__file__).read_text()
        self.assertNotIn('server_configuration_v1', producer)
        self.assertNotIn('Library/Preferences', producer)

    def test_ui_readback_requires_updated_title_and_completed_new_refresh(self):
        driver = (smoke.CLIENT / 'tool/ios_browser_driver/OrdinaryProductSmoke.swift').read_text()
        self.assertIn('&& (name.isEmpty || row.label.contains(name))', driver)
        self.assertIn('loaded.element.identifier != previousGeneration', driver)
        self.assertIn('guard refreshed else', driver)
        self.assertIn('fixture.expected["updated"] == "true"', driver)
        self.assertIn('settings-current-account-reference', driver)
        self.assertIn('settings-current-organization-reference', driver)
        self.assertNotIn('Data(fixture.organizationOrigin.utf8)', driver)


    def test_ordinary_identity_and_scope_follow_real_navigation_with_bounded_scrolling(self):
        driver = (smoke.CLIENT / 'tool/ios_browser_driver/OrdinaryProductSmoke.swift').read_text()
        identity = driver.split('case "identity":', 1)[1].split('case "chat-create":', 1)[0]
        self.assertLess(identity.index('"nav-settings"'), identity.index('"settings-help"'))
        self.assertLess(identity.index('"settings-help"'), identity.index('"settings-current-account-reference"'))
        self.assertIn('"files-location-space-workspace-default"', driver)
        self.assertNotIn('shared-files-choice', driver)
        self.assertNotIn('Shared organization files', driver)
        self.assertIn('var remaining = 8', driver)
        self.assertIn('remaining > 0 && now >= nextAllowedAt', driver)
        self.assertIn('target.exists && target.isHittable', driver)
        self.assertIn('identified.count > 1', driver)
        self.assertIn('labelled.count > 1', driver)
        self.assertIn('start.press(forDuration: 0.05, thenDragTo: end)', driver)

    def test_message_and_file_preview_readback_use_actual_accessible_content(self):
        driver = (smoke.CLIENT / 'tool/ios_browser_driver/OrdinaryProductSmoke.swift').read_text()
        message = driver.split('func smokeObserveMessage(', 1)[1].split('func smokeObserveFilePreview(', 1)[0]
        self.assertIn('"chat-active-conversation-"', message)
        self.assertIn('observed == conversationDigest', message)
        self.assertIn('room.element.descendants', message)
        self.assertIn('"chat-message-", ". " + text + ". "', message)
        self.assertIn('messages.count <= 1', message)
        self.assertNotIn('app.staticTexts', message)
        preview = driver.split('func smokeObserveFilePreview(', 1)[1].split('func smokeSharedFolder(', 1)[0]
        self.assertIn('"files-preview-content"', preview)
        self.assertIn('preview.label == expected || (preview.value as? String) == expected', preview)
        self.assertNotIn('smokeObserveText', driver)
        producer = Path(smoke.__file__).read_text()
        self.assertIn('verify_runtime_container(before, args.candidate)', producer)
        self.assertIn('native_bundle(candidate=args.candidate)', producer)
        self.assertNotIn('build-for-testing', producer)
        self.assertNotIn('-allowProvisioningUpdates', producer)

    def test_unprotected_invocation_is_incomplete_without_native_side_effects(self):
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory) / 'result.json'
            result = subprocess.run([sys.executable, str(Path(smoke.__file__)), '--stage', 'deployment', '--candidate', 'a' * 40,
                                     '--qualification-run-id', '123', '--qualification-run-attempt', '1', '--output', str(output)],
                                    env={**os.environ, 'GITHUB_ACTIONS': 'false'}, capture_output=True, text=True)
            self.assertEqual(result.returncode, 2)
            self.assertEqual(json.loads(output.read_text())['status'], 'incomplete')
            self.assertNotIn('private', result.stdout)


if __name__ == '__main__':
    unittest.main()
