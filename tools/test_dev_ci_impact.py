"""Regression cases for complete consumer inputs and the protected CI aggregate."""
import json
import re
import subprocess
import tempfile
import unittest
from pathlib import Path
from tools import dev_ci_impact as impact
from tools import dev_ci_results as results


class ImpactTest(unittest.TestCase):
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

    def test_docs_and_server_internal_changes_skip_consumers(self):
        for path in ('docs/architecture.md', 'server/src/main/java/InternalService.java'):
            selected = impact.select([path])
            self.assertFalse(selected['user'])
            self.assertFalse(selected['admin'])

    def test_backend_closure_skips_only_known_docs_and_flutter_inputs(self):
        for paths in (['docs/developer-handbook.md'], ['README.md'],
                      ['client/lib/features/chat/presentation/screen.dart'],
                      ['client/pubspec.lock', 'docs/quality-and-evidence.md']):
            self.assertFalse(impact.select(paths)['backend'], paths)
        for path in ('server/src/test/resources/fixture.json', 'server/docs/context-space-adr.md',
                     'weave-runtime-security-adapters/src/main/java/Authz.java',
                     'weave-mcp-server/gradle/scripts/java-and-dependencies.gradle',
                     'weave-persistence-jpa/src/main/resources/db/migration/V1.sql',
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
                return subprocess.check_output(['git', '-C', tmp, *args], stderr=subprocess.DEVNULL).decode().strip()
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

    def test_workflow_runs_freshness_before_selection_and_boundary_separately(self):
        root = Path(__file__).resolve().parents[1]
        workflow = (root/'.github/workflows/ci.yml').read_text()
        gradle = (root/'gradle/tasks/verification.gradle').read_text()
        self.assertIn('needs: api_contract', workflow)
        self.assertIn('clientDeterministicCi', workflow)
        self.assertIn("tasks.named('clientDeterministicCi') {\n    dependsOn 'clientOpenApiDriftPrevention', 'nativeQualificationContractTest'", gradle)
        self.assertNotIn('needs: core_architecture', workflow)
        self.assertNotIn('testApp', workflow)
        self.assertIn("    name: Gradle CI\n    if: always()\n", workflow)


if __name__ == '__main__':
    unittest.main()
