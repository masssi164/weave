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
    DomainSelectionError, load_catalog, resolve, resolve_checkout, source_branch,
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
    def test_each_declared_unit_class_exists_and_is_not_an_obvious_integration_suite(self):
        catalog = load_catalog(ROOT)
        for domain, inventory in catalog['domains'].items():
            self.assertTrue(inventory['server_unit_tests'] or inventory['additional_unit_tasks'], domain)
            self.assertEqual(len(inventory['server_unit_tests']), len(set(inventory['server_unit_tests'])))
            for name in inventory['server_unit_tests']:
                with self.subTest(domain=domain, name=name):
                    path = ROOT / 'server/app/src/test/java' / (name.replace('.', '/') + '.java')
                    source = path.read_text()
                    self.assertNotIn('@Testcontainers', source)
                    self.assertNotIn('@SpringBootTest', source)
                    self.assertNotIn('PostgresTest', name)

    def test_domain_bridge_projects_exist_for_the_catalog(self):
        for domain in load_catalog(ROOT)['domains']:
            source = (ROOT / 'server/domains' / domain / 'build.gradle').read_text()
            if domain == 'files':
                self.assertIn("id 'java-library'", source)
                self.assertNotIn('domain-project.gradle', source)
            else:
                self.assertIn('domain-project.gradle', source)

    def test_files_includes_its_existing_framework_free_unit_suite(self):
        self.assertIn(':server:domains:files:model:test', load_catalog(ROOT)['domains']['files']['additional_unit_tasks'])

    def test_real_owning_test_tasks_replace_the_files_app_bridge(self):
        inventory = load_catalog(ROOT)['domains']['files']
        self.assertEqual(inventory['server_unit_tests'], [])
        self.assertIn(':server:domains:files:test', inventory['additional_unit_tasks'])
        source = (ROOT / 'server/gradle/tasks/domain-verification.gradle').read_text()
        self.assertIn('inventory.server_unit_tests }.each', source)

    def test_invalid_catalog_is_rejected(self):
        for payload in [{}, {'domains': {}}, {'domains': {'all': {}}}, {'domains': {'../files': {}}},
                        {'domains': {'files': {'server_unit_tests': [], 'additional_unit_tasks': []}}},
                        {'domains': {'files': {'server_unit_tests': ['*Test'], 'additional_unit_tasks': []}}}]:
            with self.subTest(payload=payload), tempfile.TemporaryDirectory(prefix='weave-domain-catalog-') as tmp:
                root = Path(tmp)
                (root / 'server/domains').mkdir(parents=True)
                (root / 'server/domains/catalog.json').write_text(json.dumps(payload))
                with self.assertRaises(DomainSelectionError):
                    load_catalog(root)


if __name__ == '__main__':
    unittest.main()
