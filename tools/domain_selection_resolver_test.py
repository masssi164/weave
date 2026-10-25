"""Selection semantics and the bounded, real unit inventory; no product qualification claims."""
from __future__ import annotations

import copy
import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch

from tools.domain_selection_resolver import (
    DomainSelectionError, load_catalog, matrix, resolve, resolve_checkout, source_branch,
)

ROOT = Path(__file__).resolve().parents[1]
CATALOG = {
    'domains': {'files': {}, 'chat': {}, 'calendar': {}},
    'selection': {'pinned_branch': 'dev', 'pinned_domains': ['files', 'chat']},
}


class DomainSelectionTest(unittest.TestCase):
    def test_no_input_on_other_branch_defaults_to_all(self):
        self.assertEqual(resolve(CATALOG, None, 'feature/test'),
                         {'domains': ['calendar', 'chat', 'files'], 'selectionSource': 'all'})

    def test_no_pin_defaults_to_all(self):
        self.assertEqual(resolve({'domains': CATALOG['domains']}, None, 'dev')['selectionSource'], 'all')

    def test_detached_without_source_assertion_defaults_to_all(self):
        self.assertEqual(resolve(CATALOG, None, None)['selectionSource'], 'all')

    def test_matching_branch_uses_pin(self):
        self.assertEqual(resolve(CATALOG, None, 'dev'),
                         {'domains': ['chat', 'files'], 'selectionSource': 'pin'})

    def test_branch_pin_is_exact_not_a_prefix(self):
        self.assertEqual(resolve(CATALOG, None, 'dev/feature')['selectionSource'], 'all')

    def test_empty_workflow_input_means_absent(self):
        for requested in ['', '  ', '\t\n']:
            with self.subTest(requested=requested):
                self.assertEqual(resolve(CATALOG, requested, 'dev')['selectionSource'], 'pin')

    def test_explicit_selection_overrides_pin(self):
        self.assertEqual(resolve(CATALOG, 'calendar', 'dev'),
                         {'domains': ['calendar'], 'selectionSource': 'explicit'})

    def test_explicit_all_overrides_pin(self):
        self.assertEqual(resolve(CATALOG, 'all', 'dev'),
                         {'domains': ['calendar', 'chat', 'files'], 'selectionSource': 'explicit'})

    def test_selection_order_and_duplicates_are_normalized(self):
        self.assertEqual(resolve(CATALOG, ' files,chat,files ', 'dev')['domains'], ['chat', 'files'])

    def test_unknown_names_never_fall_back_to_pin(self):
        with self.assertRaisesRegex(DomainSelectionError, 'unknown domain'):
            resolve(CATALOG, 'unknown', 'dev')

    def test_malformed_lists_are_rejected(self):
        for requested in [',', 'files,', ',files', 'files,,chat', 'Files', 'files chat', '../files']:
            with self.subTest(requested=requested), self.assertRaises(DomainSelectionError):
                resolve(CATALOG, requested, 'dev')

    def test_all_cannot_be_mixed_with_individual_domains(self):
        for requested in ['all,files', 'files,all', 'all,all']:
            with self.subTest(requested=requested), self.assertRaisesRegex(DomainSelectionError, 'combined'):
                resolve(CATALOG, requested, 'dev')

    def test_invalid_matching_pin_is_not_silently_broadened(self):
        for pinned in [None, [], '', ['unknown'], ['all', 'files'], ['files', ''], ['files,chat']]:
            catalog = copy.deepcopy(CATALOG)
            catalog['selection']['pinned_domains'] = pinned
            with self.subTest(pinned=pinned), self.assertRaises(DomainSelectionError):
                resolve(catalog, None, 'dev')

    def test_catalog_expansion_makes_all_include_a_new_domain(self):
        catalog = copy.deepcopy(CATALOG)
        catalog['domains']['new-domain'] = {}
        self.assertEqual(resolve(catalog, 'all', 'dev')['domains'], ['calendar', 'chat', 'files', 'new-domain'])


class MatrixTest(unittest.TestCase):
    def setUp(self):
        self.catalog = {'domains': {
            'chat': {}, 'files': {}, 'decisions': {'required_domains': ['chat']},
            'meeting-archives': {'required_domains': ['decisions']}}}

    def test_default_selected_mode_is_exactly_one_normalized_resolved_row(self):
        selection = resolve(self.catalog, ' files,chat,files ', None)
        self.assertEqual(matrix(self.catalog, selection), {'include': [{
            'domain': 'selected', 'domains': 'chat,files', 'selection': 'chat,files',
            'selectionSource': 'explicit'}]})

    def test_individual_mode_includes_transitive_dependencies_but_never_unrelated_domains(self):
        selection = resolve(self.catalog, 'all', None)
        rows = matrix(self.catalog, selection, 'individual')['include']
        self.assertEqual([(row['domain'], row['domains']) for row in rows], [
            ('chat', 'chat'), ('decisions', 'chat,decisions'), ('files', 'files'),
            ('meeting-archives', 'chat,decisions,meeting-archives')])
        self.assertTrue(all(set(row['domains'].split(',')) <= set(selection['domains']) for row in rows))
        self.assertTrue(all(row['selection'] == 'chat,decisions,files,meeting-archives' for row in rows))

    def test_explicit_invalid_selection_fails_before_individual_scheduling(self):
        with self.assertRaisesRegex(DomainSelectionError, 'requires explicit selection'):
            resolve(self.catalog, 'meeting-archives', None)

    def test_matrix_does_not_widen_a_malformed_previously_resolved_selection(self):
        with self.assertRaisesRegex(DomainSelectionError, 'outside the resolved selection'):
            matrix(self.catalog, {'domains': ['decisions'], 'selectionSource': 'explicit'}, 'individual')

    def test_unknown_iteration_never_defaults_to_aggregate(self):
        with self.assertRaisesRegex(DomainSelectionError, 'iteration must be'):
            matrix(self.catalog, resolve(self.catalog, 'chat', None), 'unsupported')

    def test_checkout_matrix_loads_catalog_and_resolves_selection_once(self):
        with patch('tools.domain_selection_resolver.load_catalog', wraps=load_catalog) as load, \
                patch('tools.domain_selection_resolver.resolve', wraps=resolve) as resolver:
            result = resolve_checkout(ROOT, 'chat,decisions', iteration='individual')
        self.assertEqual(load.call_count, 1)
        self.assertEqual(resolver.call_count, 1)
        self.assertEqual([row['domains'] for row in result['include']], ['chat', 'chat,decisions'])

    def test_catalog_rejects_indirect_dependency_cycles(self):
        with tempfile.TemporaryDirectory(prefix='weave-domain-cycle-') as tmp:
            root = Path(tmp)
            (root / 'server/domains').mkdir(parents=True)
            domains = {name: {'unit_tasks': [f':server:domains:{name}:test'], 'required_domains': [dependency]}
                       for name, dependency in [('chat', 'decisions'), ('decisions', 'meeting-archives'),
                                                ('meeting-archives', 'chat')]}
            (root / 'server/domains/catalog.json').write_text(json.dumps({'domains': domains}))
            with self.assertRaisesRegex(DomainSelectionError,
                                        'dependency cycle: chat -> decisions -> meeting-archives -> chat'):
                load_catalog(root)

    def test_cli_matrix_and_iteration_validation(self):
        script = ROOT / 'tools/domain_selection_resolver.py'
        result = subprocess.run(['python3', str(script), '--root', str(ROOT), '--domains', 'chat,decisions',
                                 '--format', 'matrix', '--iteration', 'individual'],
                                text=True, capture_output=True, check=False)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual([row['domains'] for row in json.loads(result.stdout)['include']],
                         ['chat', 'chat,decisions'])
        for arguments in [('--domains', 'decisions', '--format', 'matrix', '--iteration', 'individual'),
                          ('--domains', 'chat', '--format', 'csv', '--iteration', 'individual'),
                          ('--domains', 'chat', '--format', 'matrix', '--iteration', 'unsupported')]:
            with self.subTest(arguments=arguments):
                result = subprocess.run(['python3', str(script), '--root', str(ROOT), *arguments],
                                        text=True, capture_output=True, check=False)
                self.assertNotEqual(result.returncode, 0)
                self.assertEqual(result.stdout, '')


class SourceIdentityTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory(prefix='weave-domain-source-')
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.git('init', '--initial-branch=feature/test')
        self.git('-c', 'user.name=Domain selection test', '-c', 'user.email=domain-test@example.invalid',
                 'commit', '--allow-empty', '-m', 'Test source')
        self.commit = self.git('rev-parse', 'HEAD')

    def git(self, *args):
        return subprocess.run(['git', '-C', str(self.root), *args], check=True,
                              capture_output=True, text=True).stdout.strip()

    def test_local_symbolic_branch_is_authoritative(self):
        self.assertEqual(source_branch(self.root, None, None), 'feature/test')

    def test_workflow_or_pr_environment_is_not_inferred(self):
        with patch.dict(os.environ, {'GITHUB_REF': 'refs/heads/dev', 'GITHUB_BASE_REF': 'dev'}):
            self.assertEqual(source_branch(self.root, None, None), 'feature/test')

    def test_paired_identity_is_required(self):
        for branch, commit in [('dev', None), (None, self.commit)]:
            with self.subTest(branch=branch), self.assertRaises(DomainSelectionError):
                source_branch(self.root, branch, commit)

    def test_conflicting_symbolic_branch_is_rejected(self):
        with self.assertRaises(DomainSelectionError):
            source_branch(self.root, 'dev', self.commit)

    def test_matching_symbolic_branch_is_accepted(self):
        self.assertEqual(source_branch(self.root, 'refs/heads/feature/test', self.commit), 'feature/test')

    def test_detached_source_assertion_binds_exact_commit(self):
        self.git('checkout', '--detach', self.commit)
        self.assertIsNone(source_branch(self.root, None, None))
        self.assertEqual(source_branch(self.root, 'dev', self.commit), 'dev')
        with self.assertRaises(DomainSelectionError):
            source_branch(self.root, 'dev', '0' * 40)

    def test_invalid_branch_name_is_rejected(self):
        self.git('checkout', '--detach', self.commit)
        with self.assertRaises(DomainSelectionError):
            source_branch(self.root, '../dev', self.commit)

    def test_tag_ref_is_not_a_branch_assertion(self):
        self.git('checkout', '--detach', self.commit)
        with self.assertRaises(DomainSelectionError):
            source_branch(self.root, 'refs/tags/dev', self.commit)

    def test_source_export_has_no_inferred_branch(self):
        with tempfile.TemporaryDirectory(prefix='weave-domain-export-') as tmp:
            self.assertIsNone(source_branch(Path(tmp), None, None))
            with self.assertRaises(DomainSelectionError):
                source_branch(Path(tmp), 'dev', self.commit)

    def test_corrupt_git_metadata_is_not_treated_as_an_export(self):
        with tempfile.TemporaryDirectory(prefix='weave-domain-invalid-git-') as tmp:
            root = Path(tmp)
            (root / '.git').write_text('invalid checkout metadata')
            with self.assertRaises(DomainSelectionError):
                source_branch(root, None, None)

    def test_explicit_selection_does_not_require_git_discovery(self):
        with patch('tools.domain_selection_resolver.source_branch', side_effect=AssertionError('unneeded Git lookup')):
            self.assertEqual(resolve_checkout(ROOT, 'files')['domains'], ['files'])

    def test_explicit_selection_in_a_source_export(self):
        with tempfile.TemporaryDirectory(prefix='weave-domain-export-') as tmp:
            root = Path(tmp)
            (root / 'server/domains').mkdir(parents=True)
            (root / 'server/domains/catalog.json').write_text(json.dumps(load_catalog(ROOT)))
            self.assertEqual(resolve_checkout(root, 'files')['domains'], ['files'])
            self.assertEqual(resolve_checkout(root)['selectionSource'], 'all')

    def test_explicit_selection_does_not_bypass_a_supplied_identity_assertion(self):
        with self.assertRaises(DomainSelectionError):
            resolve_checkout(self.root, 'files', 'dev', '0' * 40)


class InventoryTest(unittest.TestCase):
    def test_real_domain_projects_own_actual_unit_and_context_suites(self):
        for domain, inventory in load_catalog(ROOT)['domains'].items():
            source = (ROOT / 'server/domains' / domain / 'build.gradle').read_text()
            self.assertIn("id 'java-library'", source)
            self.assertIn('domain-library.gradle', source)
            self.assertNotIn('domain-project.gradle', source)
            self.assertNotIn('server:app', source)
            self.assertIn(f':server:domains:{domain}:test', inventory['unit_tasks'])
            units = list((ROOT / 'server/domains' / domain / 'src/test/java').rglob('*Test.java'))
            self.assertTrue(units, domain)
            self.assertTrue(any('Tag("context")' not in test.read_text() for test in units), domain)

    def test_extracted_domains_include_their_framework_free_unit_suites(self):
        for domain in ('files', 'calendar'):
            self.assertIn(f':server:domains:{domain}:model:test', load_catalog(ROOT)['domains'][domain]['unit_tasks'])

    def test_real_owning_test_tasks_replace_extracted_app_bridges(self):
        for domain in ('files', 'calendar', 'chat', 'decisions', 'meetings'):
            inventory = load_catalog(ROOT)['domains'][domain]
            self.assertIn(f':server:domains:{domain}:test', inventory['unit_tasks'])
        source = (ROOT / 'server/gradle/tasks/domain-verification.gradle').read_text()
        self.assertNotIn('domain${domain.capitalize()}UnitTest', source)
        self.assertNotIn('server_unit_tests', source)
        self.assertFalse((ROOT / 'server/domains/domain-project.gradle').exists())

    def test_required_domains_are_never_silently_included(self):
        catalog = copy.deepcopy(CATALOG)
        catalog['domains']['decisions'] = {'required_domains': ['chat']}
        with self.assertRaisesRegex(DomainSelectionError, 'decisions requires explicit selection of: chat'):
            resolve(catalog, 'decisions', None)
        self.assertEqual(resolve(catalog, 'decisions,chat', None)['domains'], ['chat', 'decisions'])
        self.assertIn('decisions', resolve(catalog, 'all', 'dev')['domains'])
        catalog['selection']['pinned_domains'] = ['decisions']
        with self.assertRaisesRegex(DomainSelectionError, 'requires explicit selection'):
            resolve(catalog, None, 'dev')

    def test_unknown_and_self_required_domains_are_rejected(self):
        for dependency in ('unknown', 'files'):
            with tempfile.TemporaryDirectory(prefix='weave-domain-dependency-') as tmp:
                root = Path(tmp)
                (root / 'server/domains').mkdir(parents=True)
                (root / 'server/domains/catalog.json').write_text(json.dumps({'domains': {
                    'files': {'unit_tasks': [':server:domains:files:test'],
                              'required_domains': [dependency]}}}))
                with self.assertRaisesRegex(DomainSelectionError, 'domain dependencies'):
                    load_catalog(root)

    def test_invalid_catalog_is_rejected(self):
        for payload in [{}, {'domains': {}}, {'domains': {'all': {}}}, {'domains': {'../files': {}}},
                        {'domains': {'files': {'unit_tasks': []}}},
                        {'domains': {'files': {'server_unit_tests': ['*Test'], 'unit_tasks': []}}}]:
            with self.subTest(payload=payload), tempfile.TemporaryDirectory(prefix='weave-domain-catalog-') as tmp:
                root = Path(tmp)
                (root / 'server/domains').mkdir(parents=True)
                (root / 'server/domains/catalog.json').write_text(json.dumps(payload))
                with self.assertRaises(DomainSelectionError):
                    load_catalog(root)


if __name__ == '__main__':
    unittest.main()
