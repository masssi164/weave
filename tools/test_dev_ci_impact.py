"""Regression cases for complete consumer inputs and the protected CI aggregate."""
import contextlib
import io
import json
import os
import re
import subprocess
import tempfile
import unittest
from unittest.mock import patch
from pathlib import Path
from tools import dev_ci_impact as impact
from tools import dev_ci_results as results


# Disposable repositories must not spawn maintenance that outlives their cleanup.
DISPOSABLE_GIT_CONFIG = ('-c', 'maintenance.auto=false', '-c', 'gc.auto=0')


class ImpactTest(unittest.TestCase):
    def test_native_driver_profile_requires_complete_existing_single_input(self):
        with tempfile.TemporaryDirectory() as tmp, patch.object(impact, 'ROOT', Path(tmp)):
            driver = Path(tmp) / impact.NATIVE_DRIVER
            driver.parent.mkdir(parents=True)
            driver.write_text('owned driver')
            self.assertTrue(impact.native_driver_only([impact.NATIVE_DRIVER]))
            for other in (
                'client/lib/main.dart', 'client/pubspec.yaml',
                'client/tool/run_dual_ios_collaboration.py',
                'client/ios/Runner.xcodeproj/project.pbxproj', 'client/Makefile',
                'gradle/tasks/verification.gradle', '.github/workflows/ci.yml',
                'unknown/input',
            ):
                for paths in ([other], [impact.NATIVE_DRIVER, other], [other, impact.NATIVE_DRIVER]):
                    with self.subTest(paths=paths):
                        self.assertFalse(impact.native_driver_only(paths))
            for paths in (None, []):
                self.assertFalse(impact.native_driver_only(paths))
            driver.unlink()
            self.assertFalse(impact.native_driver_only([impact.NATIVE_DRIVER]))
            target = Path(tmp) / 'other.swift'
            target.write_text('not the owned regular driver')
            driver.symlink_to(target)
            self.assertFalse(impact.native_driver_only([impact.NATIVE_DRIVER]))

    def test_actual_driver_git_rename_delete_and_failed_base_cannot_narrow(self):
        with tempfile.TemporaryDirectory() as tmp, patch.object(impact, 'ROOT', Path(tmp)):
            def git(*args):
                return subprocess.check_output(['git', *DISPOSABLE_GIT_CONFIG, '-C', tmp, *args],
                                               stderr=subprocess.DEVNULL).decode().strip()
            git('init'); git('config', 'user.email', 'ci@example.invalid'); git('config', 'user.name', 'CI')
            driver = Path(tmp) / impact.NATIVE_DRIVER
            driver.parent.mkdir(parents=True)
            driver.write_text('before')
            git('add', '.'); git('commit', '-m', 'base')
            base = git('rev-parse', 'HEAD')
            driver.write_text('after')
            git('add', '.'); git('commit', '-m', 'driver edit')
            real_run = subprocess.run
            with patch('tools.dev_ci_impact.subprocess.run',
                       side_effect=lambda *args, **kwargs: real_run(*args, cwd=tmp, **kwargs)):
                self.assertTrue(impact.native_driver_only(impact.changed_paths(base)))
                self.assertFalse(impact.native_driver_only(impact.changed_paths('e' * 40)))
                driver.rename(driver.with_name('Renamed.swift'))
                git('add', '.'); git('commit', '-m', 'rename')
                paths = impact.changed_paths(base)
                self.assertEqual(set(paths), {impact.NATIVE_DRIVER,
                    'client/tool/ios_browser_driver/Renamed.swift'})
                self.assertFalse(impact.native_driver_only(paths))
                driver.with_name('Renamed.swift').unlink()
                git('add', '.'); git('commit', '-m', 'delete')
                self.assertEqual(impact.changed_paths(base), [impact.NATIVE_DRIVER])
                self.assertFalse(impact.native_driver_only(impact.changed_paths(base)))

    def test_profile_output_preserves_gate_and_aggregate_contract(self):
        with tempfile.TemporaryDirectory() as tmp, patch.object(impact, 'ROOT', Path(tmp)):
            driver = Path(tmp) / impact.NATIVE_DRIVER
            driver.parent.mkdir(parents=True)
            driver.write_text('driver')
            for paths in ([impact.NATIVE_DRIVER], None, []):
                output_file = Path(tmp) / 'output'
                output_file.write_text('')
                with patch.object(impact, 'changed_paths', return_value=paths), \
                        patch('sys.argv', ['dev_ci_impact.py']), \
                        patch.dict(os.environ, {'GITHUB_OUTPUT': str(output_file)}), \
                        contextlib.redirect_stdout(io.StringIO()) as stdout:
                    impact.main()
                self.assertEqual(output_file.read_text(), stdout.getvalue())
                outputs = dict(line.split('=', 1) for line in stdout.getvalue().splitlines())
                gates = impact.select(paths)
                self.assertEqual({k: outputs[k] for k in impact.GATES},
                                 {k: str(v).lower() for k, v in gates.items()})
                self.assertEqual(outputs['client_native_driver_only'],
                                 'true' if paths else 'false')
                impact.validate_results(outputs, {k: 'success' if v else 'skipped'
                                                  for k, v in gates.items()})

    def test_workflow_executes_only_static_commands_and_defaults_to_full(self):
        workflow = (Path(__file__).resolve().parents[1] / '.github/workflows/ci.yml').read_text()
        job = workflow.split('  client_deterministic:\n', 1)[1].split('  admin_deterministic:', 1)[0]
        branch = re.search(r'(?ms)^          if \[\[.*?^          fi$', job).group()
        script = '\n'.join(line[10:] for line in branch.splitlines())
        with tempfile.TemporaryDirectory() as tmp:
            gradle = Path(tmp) / 'gradlew'
            gradle.write_text('#!/bin/sh\nprintf "%s\n" "$@"\n')
            gradle.chmod(0o700)
            for value, expected in (
                ('true', 'nativeQualificationContractTest'),
                ('false', 'clientDeterministicCi'), ('', 'clientDeterministicCi'),
                ('TRUE', 'clientDeterministicCi'), ('true extra', 'clientDeterministicCi'),
                ('$(touch injected)', 'clientDeterministicCi'),
            ):
                with self.subTest(value=value):
                    result = subprocess.run(['bash', '-eu', '-c', script], cwd=tmp,
                        env={**os.environ, 'CLIENT_NATIVE_DRIVER_ONLY': value},
                        capture_output=True, text=True, check=True)
                    self.assertEqual(result.stdout.splitlines(), ['--no-daemon', '--stacktrace', expected])
                    self.assertFalse((Path(tmp) / 'injected').exists())
            result = subprocess.run(['bash', '-e', '-c', script], cwd=tmp,
                env={k: v for k, v in os.environ.items() if k != 'CLIENT_NATIVE_DRIVER_ONLY'},
                capture_output=True, text=True, check=True)
            self.assertEqual(result.stdout.splitlines()[-1], 'clientDeterministicCi')

    def test_audiences_are_independent(self):
        user = impact.select(['contracts/openapi/weave-user-openapi.json'])
        admin = impact.select(['contracts/openapi/weave-admin-openapi.json'])
        self.assertTrue(user['user'])
        self.assertFalse(user['admin'])
        self.assertTrue(admin['admin'])
        self.assertFalse(admin['user'])
        self.assertFalse(admin['client'])
        self.assertTrue(admin['admin_console'])
        combined = impact.select(['contracts/openapi/weave-openapi.json'])
        self.assertFalse(combined['user'])
        self.assertFalse(combined['admin'])
        self.assertFalse(combined['client'])

    def test_generated_corruption_is_an_input(self):
        for path, gate in (
            ('client/lib/generated/user_api/api.dart', 'user'),
            ('admin-console/src/generated/user-client/index.ts', 'user'),
            ('admin-console/src/generated/admin-client/index.ts', 'admin'),
            ('weave-user-api-client/src/test/java/ReproducibilityTest.java', 'user'),
            ('weave-admin-api-client/build.gradle', 'admin'),
        ):
            with self.subTest(path=path):
                self.assertTrue(impact.select([path])[gate])

    def test_all_generator_config_toolchain_inputs_are_covered(self):
        for path in ('tools/patch_openapi_java_binary_body.py', 'tools/check_jvm_api_client.py',
                     'tools/check_generated_calendar_temporal_wire.sh', 'gradle/libs.versions.toml',
                     '.github/workflows/ci.yml', 'build.gradle', 'settings.gradle', 'new-toolchain.lock'):
            with self.subTest(path=path):
                self.assertTrue(all(impact.select([path]).values()))
        self.assertTrue(impact.select(['client/pubspec.lock'])['user'])
        self.assertTrue(impact.select(['client/analysis_options.yaml'])['user'])
        for path in ('admin-console/package-lock.json', 'admin-console/tsconfig.json'):
            self.assertTrue(impact.select([path])['user'])
            self.assertTrue(impact.select([path])['admin'])

    def test_ordinary_production_change_checks_boundary_without_regeneration(self):
        selected = impact.select(['client/lib/features/files/data/new_service.dart'])
        self.assertTrue(selected['client'])
        self.assertFalse(selected['user'])
        self.assertFalse(selected['admin'])

    def test_reviewed_native_inputs_keep_client_checks_without_consumer_regeneration(self):
        native_inputs = (
            'client/Makefile',
            'client/integration_test/system_browser_auth_e2e_test.dart',
            'client/tool/ios_browser_driver/BrowserDriver.swift',
            'client/tool/run_ios_simulator_product_acceptance.py',
            'client/tool/native_qualification_test.py',
            'client/tool/dual_ios_collaboration_test.py',
            'client/tool/run_dual_ios_collaboration.py',
            'client/tool/run_native_qualification.py',
            'client/integration_test/dual_ios_collaboration_test.dart',
        )
        self.assertEqual(impact.CLIENT_NON_CONSUMER_INPUTS, frozenset(native_inputs))
        for paths in ([path] for path in native_inputs):
            with self.subTest(paths=paths):
                selected = impact.select(paths)
                self.assertFalse(selected['user'])
                self.assertFalse(selected['admin'])
                self.assertTrue(all(selected[gate] for gate in ('client', 'infra', 'docs')))
        self.assertFalse(impact.select(list(native_inputs))['user'])
        for native in native_inputs:
            for consumer_input in ('contracts/openapi/weave-user-openapi.json',
                                   'client/lib/generated/user_api/api.dart',
                                   'client/pubspec.lock'):
                for paths in ([native, consumer_input], [consumer_input, native]):
                    with self.subTest(paths=paths):
                        self.assertTrue(impact.select(paths)['user'])

    def test_temporal_verification_and_unreviewed_client_tools_select_user_gate(self):
        for path in (
            'client/test/integrations/weave_api/data/services/generated_calendar_time_value_test.dart',
            'client/tool/new_qualification.py',
            'client/tool/ios_browser_driver/Unknown.swift',
            'client/integration_test/new_browser_test.dart',
            'client/hook/build.dart', 'client/pubspec.yaml', 'client/pubspec.lock',
            'client/analysis_options.yaml', 'client/dart_test.yaml',
        ):
            with self.subTest(path=path):
                selected = impact.select([path])
                self.assertTrue(selected['user'])
                self.assertTrue(selected['client'])
        self.assertTrue(impact.select(['rust/matrix-client/src/lib.rs'])['user'])
        self.assertTrue(all(impact.select(['tools/generate_client_user_api.py']).values()))

    def test_native_renames_and_consumer_deletions_use_complete_actual_git_inputs(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            def git(*args):
                return subprocess.check_output(['git', *DISPOSABLE_GIT_CONFIG, '-C', tmp, *args],
                                               stderr=subprocess.DEVNULL).decode().strip()
            git('init'); git('config', 'user.email', 'ci@example.invalid'); git('config', 'user.name', 'CI')
            native = root / 'client/tool/native_qualification_test.py'
            generated = root / 'client/lib/generated/user_api/api.dart'
            temporal = root / impact.USER_TEMPORAL_VERIFICATION
            for path in (native, generated, temporal):
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text('fixture')
            git('add', '.'); git('commit', '-m', 'base')
            base = git('rev-parse', 'HEAD')
            native.rename(native.with_name('unreviewed_native_test.py'))
            generated.unlink()
            temporal.unlink()
            git('add', '.'); git('commit', '-m', 'rename and delete')
            real_run = subprocess.run
            with patch('tools.dev_ci_impact.subprocess.run',
                       side_effect=lambda *args, **kwargs: real_run(*args, cwd=tmp, **kwargs)):
                paths = impact.changed_paths(base)
            self.assertEqual(set(paths), {
                'client/tool/native_qualification_test.py', 'client/tool/unreviewed_native_test.py',
                'client/lib/generated/user_api/api.dart', impact.USER_TEMPORAL_VERIFICATION,
            })
            self.assertTrue(impact.select(paths)['user'])
            for path in paths:
                if path != 'client/tool/native_qualification_test.py':
                    self.assertTrue(impact.select([path])['user'])

    def test_docs_and_server_internal_changes_skip_consumers(self):
        for path in ('docs/architecture.md', 'server/src/main/java/InternalService.java'):
            selected = impact.select([path])
            self.assertFalse(selected['user'])
            self.assertFalse(selected['admin'])

    def test_reviewed_java_skill_documents_keep_only_static_gates(self):
        reviewed = {
            '.agents/skills/weave-spring-architecture/SKILL.md',
            '.agents/skills/weave-spring-architecture/agents/openai.yaml',
            '.agents/skills/weave-http-clients/SKILL.md',
            '.agents/skills/weave-http-clients/agents/openai.yaml',
            '.agents/skills/weave-config-and-beans/SKILL.md',
            '.agents/skills/weave-config-and-beans/agents/openai.yaml',
            '.agents/skills/weave-native-aot/SKILL.md',
            '.agents/skills/weave-native-aot/agents/openai.yaml',
        }
        self.assertEqual(impact.JAVA_SKILL_DOCUMENTATION_INPUTS, frozenset(reviewed))
        expected = {gate: gate in {'infra', 'docs'} for gate in impact.GATES}
        with tempfile.TemporaryDirectory() as tmp, patch.object(impact, 'ROOT', Path(tmp)):
            for path in reviewed:
                document = Path(tmp) / path
                document.parent.mkdir(parents=True, exist_ok=True)
                document.write_text('reviewed documentation')
            for paths in ([path] for path in sorted(reviewed)):
                with self.subTest(paths=paths):
                    self.assertEqual(impact.select(paths), expected)
            self.assertEqual(impact.select(sorted(reviewed)), expected)
            for path in ('server/src/main/java/InternalService.java',
                         'client/lib/generated/user_api/api.dart',
                         'contracts/openapi/weave-admin-openapi.json'):
                for paths in ([*reviewed, path], [path, *reviewed]):
                    self.assertEqual(impact.select(paths), impact.select([path]))

    def test_unreviewed_agent_paths_remain_conservative(self):
        known = '.agents/skills/weave-http-clients/SKILL.md'
        with tempfile.TemporaryDirectory() as tmp, patch.object(impact, 'ROOT', Path(tmp)):
            document = Path(tmp) / known
            document.parent.mkdir(parents=True)
            document.write_text('reviewed documentation')
            for unknown in (
                '.agents/skills/weave-http-clients/scripts/check.py',
                '.agents/skills/weave-http-clients/scripts/check.md',
                '.agents/skills/weave-http-clients/references/new-guide.md',
                '.agents/skills/weave-http-clients/SKILL.md.py',
                '.agents/skills/new-skill/SKILL.md',
                '.agents/skills/new-skill/agents/openai.yaml',
                '.agents/unknown.yaml', '.agent/skills/flutter/SKILL.md',
            ):
                for paths in ([unknown], [known, unknown], [unknown, known]):
                    with self.subTest(paths=paths):
                        self.assertTrue(all(impact.select(paths).values()))

    def test_reviewed_java_skill_paths_reject_unsafe_spelling_and_missing_files(self):
        path = '.agents/skills/weave-http-clients/SKILL.md'
        with tempfile.TemporaryDirectory() as tmp, patch.object(impact, 'ROOT', Path(tmp)):
            self.assertTrue(all(impact.select([path]).values()))
            document = Path(tmp) / path
            document.parent.mkdir(parents=True)
            document.write_text('reviewed documentation')
            for unsafe in ('./' + path, '/' + path, path + '/',
                           path.replace('/skills/', '/skills/../skills/'),
                           path.replace('/skills/', '//skills/'),
                           path.replace('/', '\\')):
                with self.subTest(path=unsafe):
                    self.assertTrue(all(impact.select([unsafe]).values()))
            document.unlink()
            document.mkdir()
            self.assertTrue(all(impact.select([path]).values()))

    def test_reviewed_java_skill_paths_reject_symlink_at_any_component(self):
        path = '.agents/skills/weave-http-clients/agents/openai.yaml'
        parts = Path(path).parts
        for index in range(len(parts)):
            with self.subTest(component=parts[index]), tempfile.TemporaryDirectory() as tmp, \
                    patch.object(impact, 'ROOT', Path(tmp)):
                target = Path(tmp) / 'target'
                remaining = parts[index + 1:]
                document = target.joinpath(*remaining)
                document.parent.mkdir(parents=True, exist_ok=True)
                document.write_text('not an owned regular document')
                link = Path(tmp).joinpath(*parts[:index + 1])
                link.parent.mkdir(parents=True, exist_ok=True)
                link.symlink_to(target, target_is_directory=bool(remaining))
                self.assertTrue(all(impact.select([path]).values()))

    def test_backend_closure_skips_only_known_docs_and_flutter_inputs(self):
        for paths in (['docs/developer-handbook.md'], ['README.md'],
                      ['client/lib/features/chat/presentation/screen.dart'],
                      ['client/pubspec.lock', 'docs/quality-and-evidence.md']):
            self.assertFalse(impact.select(paths)['backend'], paths)
        for path in ('server/src/test/resources/fixture.json', 'server/docs/context-space-adr.md',
                     'server/domains/agent-runtime/security/src/main/java/Authz.java',
                     'weave-mcp-server/gradle/scripts/java-and-dependencies.gradle',
                     'server/core/src/main/resources/db/migration/V1.sql',
                     'contracts/openapi/weave-user-openapi.json', 'fixtures/test.json',
                     'specs/weave-specs.lock.json', 'e2e/features/auth.feature',
                     'infra/weave-workspace/scripts/build_runtime_state_image.sh',
                     'rust/matrix-protocol/src/lib.rs', 'Cargo.lock'):
            self.assertTrue(impact.select([path])['backend'], path)
        self.assertTrue(impact.select(['docs/readme.md', 'server/src/main/resources/application.yml'])['backend'])

    def test_backend_aggregate_requires_every_selected_module_job(self):
        needs = {job: {'result': 'success'} for job in results.REQUIRED}
        needs['api_impact']['outputs'] = dict.fromkeys(impact.GATES, 'false')
        needs.update({job: {'result': 'skipped'} for job in (*results.SELECTED.values(), *results.BACKEND)})
        results.validate(needs)
        needs['api_impact']['outputs']['backend'] = 'true'
        for job in results.BACKEND:
            needs[job]['result'] = 'success'
        results.validate(needs)
        for job in results.BACKEND:
            needs[job]['result'] = 'skipped'
            with self.assertRaises(ValueError): results.validate(needs)
            needs[job]['result'] = 'success'
        del needs['api_impact']['outputs']['backend']
        with self.assertRaises(ValueError): results.validate(needs)

    def test_cross_boundary_static_guards_are_not_underselected(self):
        selected = impact.select(['server/src/main/resources/application-base.yml'])
        self.assertTrue(selected['infra'])
        self.assertTrue(selected['docs'])

    def test_unknown_empty_and_unsafe_input_fail_closed(self):
        for paths in (None, [], ['unknown/subsystem.rs'], ['../client/foo'], ['/tmp/source']):
            self.assertTrue(all(impact.select(paths).values()))
        self.assertIsNone(impact.changed_paths(''))
        self.assertIsNone(impact.changed_paths('0' * 40))
        self.assertIsNone(impact.changed_paths('e' * 40))

    def test_rename_and_deletion_include_both_owners(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            def git(*args):
                return subprocess.check_output(['git', *DISPOSABLE_GIT_CONFIG, '-C', tmp, *args],
                                               stderr=subprocess.DEVNULL).decode().strip()
            git('init'); git('config', 'user.email', 'ci@example.invalid'); git('config', 'user.name', 'CI')
            (root/'admin-console/src/generated/admin-client').mkdir(parents=True)
            source = root/'admin-console/src/generated/admin-client/file.ts'
            source.write_text('export const value=1;')
            git('add', '.'); git('commit', '-m', 'base')
            base = git('rev-parse', 'HEAD')
            (root/'client/lib/generated/user_api').mkdir(parents=True)
            source.rename(root/'client/lib/generated/user_api/file.ts')
            git('add', '.'); git('commit', '-m', 'rename')
            diff = git('diff', '--name-only', '--no-renames', base, 'HEAD').splitlines()
            self.assertTrue(impact.select(diff)['user'])
            self.assertTrue(impact.select(diff)['admin'])

    def test_aggregate_rejects_malformed_missing_cancelled_or_skipped_selected(self):
        selected = dict.fromkeys(impact.GATES, 'false')
        needs = {job: {'result': 'success'} for job in results.REQUIRED}
        needs['api_impact']['outputs'] = selected
        needs.update({job: {'result': 'skipped'} for job in (*results.SELECTED.values(), *results.BACKEND)})
        results.validate(needs)
        for value in ('', 'maybe', None):
            broken = json.loads(json.dumps(needs))
            broken['api_impact']['outputs']['user'] = value
            with self.assertRaises(ValueError): results.validate(broken)
        for failure in ('skipped', 'failure', 'cancelled'):
            broken = json.loads(json.dumps(needs))
            broken['api_impact']['outputs']['user'] = 'true'
            broken['generated_user_api']['result'] = failure
            with self.assertRaises(ValueError): results.validate(broken)
        del needs['api_contract']
        with self.assertRaises(ValueError): results.validate(needs)

    def test_acceptance_text_guard_does_not_build_native_app(self):
        root = Path(__file__).resolve().parents[1]
        gradle = (root / 'gradle/tasks/verification.gradle').read_text()
        script = (root / 'client/tool/acceptance_contract.dart').read_text()
        self.assertIn('dart client/tool/acceptance_contract.dart guard --root .', gradle)
        imports = re.findall(r"^import ['\"]([^'\"]+)", script, re.MULTILINE)
        self.assertTrue(imports)
        self.assertTrue(all(import_name.startswith('dart:') for import_name in imports))
        workflow = (root / '.github/workflows/ci.yml').read_text()
        block = re.search(r'(?ms)^  spec_acceptance:\n.*?(?=^  [a-z_]+:|\Z)', workflow).group()
        self.assertNotIn('flutter pub get', block)

    def test_every_backend_job_is_linked_to_validated_impact(self):
        workflow = (Path(__file__).resolve().parents[1] / '.github/workflows/ci.yml').read_text()
        for job in results.BACKEND:
            block = re.search(rf'(?ms)^  {job}:\n.*?(?=^  [a-z_]+:|\Z)', workflow).group()
            self.assertIn('    needs: api_impact', block)
            self.assertIn("if: needs.api_impact.outputs.backend == 'true'", block)
            self.assertNotIn("github.event.action", block)
        self.assertIn('backend: ${{ steps.impact.outputs.backend }}', workflow)

    def test_label_events_cannot_shadow_the_protected_core_suite(self):
        root = Path(__file__).resolve().parents[1]
        core = (root / '.github/workflows/ci.yml').read_text()
        labels = (root / '.github/workflows/release-notes-label.yml').read_text()
        core_events = core.split('on:\n', 1)[1].split('\npermissions:', 1)[0]
        self.assertNotIn('- labeled', core_events)
        self.assertNotIn('- unlabeled', core_events)
        self.assertIn('merge_group:', core_events)
        self.assertNotIn('github.event.action', core)
        self.assertNotIn('Label-only', core)
        aggregate = re.search(r'(?ms)^  gradle_ci:\n.*?(?=^  [a-z_]+:|\Z)', core).group()
        self.assertIn('    name: Gradle CI\n', aggregate)
        self.assertIn('    if: always()\n', aggregate)
        self.assertIn('NEEDS_JSON: ${{ toJSON(needs) }}', aggregate)
        self.assertIn('run: python3 tools/dev_ci_results.py', aggregate)
        for job in (*results.REQUIRED, *results.BACKEND, *results.SELECTED.values()):
            self.assertIn(job, aggregate)
        owners = [path.name for path in (root / '.github/workflows').glob('*.y*ml')
                  for _ in re.finditer(r'^    name: Release Notes Label Check$', path.read_text(), re.MULTILINE)]
        self.assertEqual(owners, ['release-notes-label.yml'])
        for event in ('opened', 'synchronize', 'reopened', 'ready_for_review', 'labeled', 'unlabeled'):
            self.assertIn('      - ' + event + '\n', labels)
        self.assertIn('  merge_group:\n', labels)
        self.assertIn("    if: github.event_name == 'pull_request'\n", labels)
        self.assertIn('PR_LABELS_JSON="$labels_json" ./gradlew --no-daemon releaseNotesLabelCheck', labels)
        self.assertIn('release-notes-labels-${{', labels)
        self.assertIn('group: core-ci-${{', core)
        self.assertNotIn('pull_request_target', labels)
        gradle = (root / 'gradle/tasks/verification.gradle').read_text()
        self.assertIn("inputs.files(fileTree('.github/workflows') { include '*.yml', '*.yaml' })", gradle)

    def test_core_verifies_draft_source_without_repeating_unchanged_readiness(self):
        root = Path(__file__).resolve().parents[1]
        core = (root / '.github/workflows/ci.yml').read_text()
        events = core.split('on:\n', 1)[1].split('\npermissions:', 1)[0]
        pr_events = events.split('  pull_request:\n', 1)[1].split('  merge_group:', 1)[0]
        self.assertEqual(re.findall(r'^      - ([a-z_]+)$', pr_events, re.MULTILINE),
                         ['opened', 'synchronize', 'reopened'])
        self.assertIn('  push:\n    branches:\n      - dev\n      - main\n', events)
        self.assertIn('  merge_group:\n', events)
        self.assertIn('  workflow_dispatch:\n', events)
        self.assertNotIn('ready_for_review', core)
        self.assertNotIn('pull_request.draft', core)
        self.assertNotIn('github.event.action', core)
        self.assertIn('    name: Gradle CI\n    if: always()\n', core)
        self.assertIn('run: python3 tools/dev_ci_results.py', core)
        for workflow in ('main-promotion-gate.yml', 'release-notes-label.yml'):
            document = (root / '.github/workflows' / workflow).read_text()
            event_block = document.split('on:\n', 1)[1].split('\npermissions:', 1)[0]
            self.assertIn('      - ready_for_review\n', event_block)

    def test_workflow_runs_freshness_before_selection_and_boundary_separately(self):
        root = Path(__file__).resolve().parents[1]
        workflow = (root/'.github/workflows/ci.yml').read_text()
        gradle = (root/'gradle/tasks/verification.gradle').read_text()
        self.assertIn('needs: api_contract', workflow)
        self.assertIn('clientDeterministicCi', workflow)
        self.assertIn("tasks.named('clientDeterministicCi') {\n    dependsOn 'clientOpenApiDriftPrevention', 'nativeQualificationContractTest'", gradle)
        self.assertNotIn('needs: core_architecture', workflow)
        self.assertNotRegex(workflow, r'\btestApp\b')
        self.assertIn("    name: Gradle CI\n    if: always()\n", workflow)


if __name__ == '__main__':
    unittest.main()
