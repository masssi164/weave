"""Positive and injected negative cases for the bounded Java agent contract guard."""
import json
from pathlib import Path
import tempfile
import unittest

from tools import java_agent_contract_check as guard


REPOSITORY = Path(__file__).resolve().parents[1]
SELECTOR = '@ConditionalOnProperty(name = "weave.chat.provider", havingValue = "native")'


class JavaAgentContractTest(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name)
        self.write(guard.BASELINE, json.dumps({'schemaVersion': 1, 'selectors': []}))
        for module in guard.MODULES:
            self.write(f'{module}/src/main/java/Example.java', 'class Example {}')
        for module in ('server', 'weave-mcp-server'):
            self.write(f'{module}/src/main/resources/application.yml', 'spring:\n  application:\n    name: test\n')
        self.write(guard.STANDARD, '# Shared Java/Spring standard\n')
        for name in guard.SKILLS:
            self.write(f'.agents/skills/{name}/SKILL.md', self.skill(name))

    def write(self, relative, source):
        path = self.root / relative
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(source)
        return path

    @staticmethod
    def skill(name):
        return (f'---\nname: {name}\ndescription: Review this bounded Java change.\n---\n'
                '[Standard](../../../docs/java-spring-standard.md)\n')

    def assert_selector_rejected(self):
        failures = guard.selector_violations(self.root)
        self.assertTrue(any('new-startup-provider-selector:' in failure for failure in failures), failures)

    def test_minimal_valid_layout_and_real_legacy_baseline_are_accepted(self):
        self.assertEqual([], guard.violations(self.root))
        self.assertEqual([], guard.selector_violations(REPOSITORY))

    def test_new_selector_in_every_production_module_and_new_module_is_rejected(self):
        for module in (*guard.MODULES, 'future-adapters'):
            with self.subTest(module=module):
                path = self.write(f'{module}/src/main/java/NewProvider.java', SELECTOR + '\nclass NewProvider {}')
                self.assert_selector_rejected()
                path.unlink()

    def test_exact_occurrence_budget_not_entire_file_is_allowed(self):
        relative = 'server/src/main/java/Legacy.java'
        signature = guard.compact(SELECTOR)
        self.write(guard.BASELINE, json.dumps({'schemaVersion': 1, 'selectors': [
            {'path': relative, 'kind': 'java-annotation', 'signature': signature, 'count': 1}]}))
        self.write(relative, SELECTOR)
        self.assertEqual([], guard.selector_violations(self.root))
        self.write(relative, SELECTOR + '\n' + SELECTOR)
        self.assert_selector_rejected()
        self.write(relative, SELECTOR.replace('native', 'other'))
        self.assert_selector_rejected()
        self.write(relative, 'class Legacy {}')
        self.assertEqual([], guard.selector_violations(self.root))

    def test_layout_and_comments_do_not_change_legacy_budget(self):
        relative = 'server/src/main/java/Legacy.java'
        self.write(guard.BASELINE, json.dumps({'schemaVersion': 1, 'selectors': [
            {'path': relative, 'kind': 'java-annotation', 'signature': guard.compact(SELECTOR), 'count': 1}]}))
        self.write(relative, '@ConditionalOnProperty(\nname /* comment */ = "weave.chat.provider",\n'
                             'havingValue = "native"\n)\n')
        self.assertEqual([], guard.selector_violations(self.root))

    def test_split_properties_expressions_profiles_and_literal_lookups_are_rejected(self):
        selectors = (
            '@ConditionalOnProperty(prefix="weave.chat", name="provider", havingValue="native")',
            '@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(\n'
            'prefix="weave.calendar", value={"provider"}, havingValue="caldav")',
            '@ConditionalOnExpression("\'${weave.files.provider:native}\' == \'native\'")',
            '@Value("${weave.boards.provider:local}")',
            '@Profile("provider-nextcloud")', '@Profile("matrix-synapse")',
            '@Profile("synapse")', '@Profile("!nextcloud")',
            '@Profile("custom") class Adapter implements ChatProviderPort {}',
            'environment.getProperty("weave.chat.provider")',
            'System.getenv("WEAVE_FILES_PROVIDER")',
        )
        for selector in selectors:
            with self.subTest(selector=selector):
                self.write('server/src/main/java/NewProvider.java', selector)
                self.assert_selector_rejected()

    def test_unrelated_infrastructure_conditions_and_comments_are_allowed(self):
        self.write('server/src/main/java/Infrastructure.java',
                   '// ' + SELECTOR + '\n/* ' + SELECTOR + ' */\n'
                   '@ConditionalOnProperty(name="weave.metrics.enabled", havingValue="true")\n'
                   '@Profile("schema-init")\nclass Infrastructure {\n'
                   'String url = "https://example.invalid";\n}\n')
        self.assertEqual([], guard.selector_violations(self.root))

    def test_tests_docs_fixtures_and_generated_artifacts_are_not_production(self):
        for path in ('server/src/test/java/SelectorFixture.java', 'docs/example.java',
                     'tools/fixtures/example.java', 'server/build/src/main/java/Generated.java',
                     'server/src/test/resources/application-fixture.yml'):
            self.write(path, SELECTOR + '\nprovider: ${WEAVE_CHAT_PROVIDER:native}')
        self.assertEqual([], guard.selector_violations(self.root))

    def test_production_package_named_like_a_build_directory_is_not_skipped(self):
        self.write('server/src/main/java/example/target/NewProvider.java', SELECTOR)
        self.assert_selector_rejected()

    def test_source_symlinks_are_rejected_without_reading_their_targets(self):
        for relative in ('server/src/main/java/Linked.java',
                         'server/src/main/resources/application-linked.yml'):
            with self.subTest(path=relative):
                link = self.root / relative
                link.symlink_to(self.root / 'missing-private-target')
                failures = guard.selector_violations(self.root)
                self.assertTrue(any('source-symlink-not-scanned:' in failure for failure in failures))
                link.unlink()

    def test_yaml_selector_env_reference_and_provider_profile_additions_are_rejected(self):
        for line in ('provider: ${WEAVE_NEW_PROVIDER:native}', 'provider: ${ARBITRARY_CHOICE}',
                     'weave.chat.provider: native', 'binding: ${WEAVE_FILES_PROVIDER:native}',
                     'on-profile: provider-nextcloud'):
            with self.subTest(line=line):
                self.write('weave-mcp-server/src/main/resources/application-new.yaml', line)
                self.assert_selector_rejected()

    def test_yaml_comments_and_secret_delivery_are_not_provider_selection(self):
        self.write('server/src/main/resources/application-extra.yml',
                   '# provider: ${WEAVE_FILES_PROVIDER:native}\n'
                   'password: ${DATABASE_PASSWORD}\nprovider:\n  enabled: true\n')
        self.assertEqual([], guard.selector_violations(self.root))

    def test_missing_source_root_config_or_baseline_fails_closed(self):
        for module in guard.MODULES:
            path = self.root / module / 'src/main/java/Example.java'
            path.unlink()
            self.assertTrue(any('scan-root-missing-or-empty:' in x for x in guard.selector_violations(self.root)))
            path.write_text('class Example {}')
        (self.root / 'server/src/main/resources/application.yml').unlink()
        self.assertTrue(any('configuration-root-missing:' in x for x in guard.selector_violations(self.root)))
        (self.root / guard.BASELINE).unlink()
        self.assertTrue(any('baseline-invalid:' in x for x in guard.selector_violations(self.root)))

    def test_duplicate_or_unbounded_baseline_entries_are_invalid(self):
        entry = {'path': 'server/src/main/java/X.java', 'kind': 'java-annotation',
                 'signature': guard.compact(SELECTOR), 'count': 1}
        for entries in ([entry, entry], [dict(entry, count=0)], [dict(entry, count='*')]):
            self.write(guard.BASELINE, json.dumps({'schemaVersion': 1, 'selectors': entries}))
            self.assertTrue(any('baseline-invalid:' in x for x in guard.selector_violations(self.root)))

    def test_skills_require_names_nonempty_descriptions_and_shared_standard(self):
        name = guard.SKILLS[0]
        path = f'.agents/skills/{name}/SKILL.md'
        valid = self.skill(name)
        for source in (valid.replace(name, 'incorrect-name'), valid.replace('Review this bounded Java change.', ''),
                       valid.replace('Review this bounded Java change.', '""'), valid.split('---\n', 2)[-1]):
            with self.subTest(source=source):
                self.write(path, source)
                self.assertTrue(any('skill-required-metadata-invalid:' in x for x in guard.skill_violations(self.root)))
        self.write(path, valid.replace('[Standard](../../../docs/java-spring-standard.md)', 'No shared link.'))
        self.assertTrue(any('skill-shared-standard-link-missing:' in x for x in guard.skill_violations(self.root)))
        (self.root / path).unlink()
        self.assertTrue(any('skill-missing:' in x for x in guard.skill_violations(self.root)))
        (self.root / guard.STANDARD).unlink()
        self.assertTrue(any('shared-standard-missing:' in x for x in guard.skill_violations(self.root)))

    def test_skill_inline_and_reference_links_are_checked_without_fetching_external_urls(self):
        name = guard.SKILLS[0]
        path = f'.agents/skills/{name}/SKILL.md'
        valid = self.skill(name)
        self.write(path, valid + '[External](https://example.invalid/no-network)\n[Local](#heading)\n')
        self.assertEqual([], guard.skill_violations(self.root))
        for link in ('[Missing](missing.md)', '[ref]: missing.md', '[Outside](../../../../outside.md)'):
            self.write(path, valid + link + '\n')
            self.assertTrue(any('skill-local-link-missing-or-outside-repo:' in x for x in guard.skill_violations(self.root)))
        self.write(path, valid + '[Local absolute URI](file:///etc/passwd)\n')
        self.assertTrue(any('skill-unsupported-link-scheme:' in x for x in guard.skill_violations(self.root)))

    def test_failure_messages_do_not_echo_source_payloads(self):
        self.write('server/src/main/java/Unsafe.java', SELECTOR.replace('native', 'private-secret-sentinel'))
        self.assertNotIn('private-secret-sentinel', '\n'.join(guard.selector_violations(self.root)))


if __name__ == '__main__':
    unittest.main()
