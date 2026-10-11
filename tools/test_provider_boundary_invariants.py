"""Negative fixtures for the inherited native provider source boundaries."""
import tempfile
import unittest
from pathlib import Path
from tools import provider_boundary_invariants as guard


class ProviderBoundaryTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.write(guard.FILESYSTEM_STORE, 'import org.apache.opendal.Operator;')
        for migration in guard.MIGRATIONS:
            contents = ('drop table if exists weave_matrix_e2ee_snapshots;'
                        if migration == guard.MIGRATIONS[2] else '-- historical migration')
            self.write(guard.MIGRATION_ROOT + '/' + migration, contents)
        self.write('server/src/main/resources/application.yml', 'ddl-auto: validate')

    def write(self, relative, contents):
        target = self.root / relative
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(contents)

    def test_valid_narrow_protocol_and_optional_s3_adapter_are_allowed(self):
        self.write('rust/matrix-protocol/src/lib.rs', 'use ruma::api;')
        self.write('server/src/main/java/OptionalS3Adapter.java', 'import software.amazon.awssdk;')
        self.assertEqual(guard.violations(self.root), [])

    def test_every_prohibited_identifier_in_every_original_scope_fails(self):
        for rule, expression, roots in guard.FORBIDDEN_CONTENT:
            # Cover every branch of each inherited regular expression.
            tokens = {
                'migration-public-schema': ('public.',),
                'unsafe-flyway-authority': ('baselineOnMigrate(true)', 'cleanDisabled(false)'),
                'protocol-sdk-coupling': ('matrix-sdk', 'matrix_sdk'),
                'snapshot-persistence-authority': ('MatrixE2eeSnapshotStore', 'MatrixE2eeSnapshotJpaEntity', 'MatrixE2eeSnapshotJpaRepository'),
                'retired-matrix-coupling': ('weave.chat.matrix-facade.enabled', 'facadeEnabled', 'weave_matrix_core'),
                'native-s3-configuration': ('S3_COMPATIBLE', 'weave.files.native.blob-store'),
                'filesystem-aws-coupling': ('software.amazon.awssdk',),
            }[rule]
            for scope in roots:
                path = scope if scope in {'Cargo.toml', 'Cargo.lock', 'README.md', guard.FILESYSTEM_STORE} else scope + '/source.txt'
                for token in tokens:
                    with self.subTest(rule=rule, scope=scope, token=token):
                        self.write(path, f'credential=private-payload\n{token}\n')
                        failures = guard.violations(self.root)
                        self.assertTrue(any(f'{rule}: {path}:2:' in failure for failure in failures))
                        self.assertFalse(any('private-payload' in failure for failure in failures))
                        (self.root / path).unlink()
            self.write(guard.FILESYSTEM_STORE, 'import org.apache.opendal.Operator;')

    def test_retired_source_paths_and_directory_fail(self):
        (self.root / 'rust/matrix-core').mkdir(parents=True)
        self.assertTrue(any('retired-matrix-core' in error for error in guard.violations(self.root)))
        for path in guard.FORBIDDEN_FILES:
            self.write(path, '')
            self.assertTrue(any(path in error for error in guard.violations(self.root)))

    def test_opendal_dependency_is_required(self):
        self.write(guard.FILESYSTEM_STORE, 'class FilesystemBlobStore {}')
        self.assertTrue(any('filesystem-opendal-required' in error for error in guard.violations(self.root)))

    def test_generated_artifacts_do_not_redefine_source_boundary(self):
        self.write('server/build/classes/OldClass.class', 'weave_matrix_core S3_COMPATIBLE')
        self.write('rust/matrix-protocol/target/cache.txt', 'matrix-sdk')
        self.assertEqual(guard.violations(self.root), [])

    def test_nested_source_symlinks_do_not_hide_a_violation(self):
        target = self.root / 'linked-source'
        target.mkdir()
        (target / 'source.rs').write_text('matrix_sdk')
        link = self.root / 'rust/matrix-protocol/src'
        link.parent.mkdir(parents=True)
        link.symlink_to(target, target_is_directory=True)
        (target / 'cycle').symlink_to(target, target_is_directory=True)
        self.assertTrue(any('protocol-sdk-coupling' in error for error in guard.violations(self.root)))

    def test_every_historical_migration_must_exist_and_be_nonempty(self):
        for migration in guard.MIGRATIONS:
            path = self.root / guard.MIGRATION_ROOT / migration
            original = path.read_text()
            for contents in (None, '', '  \n'):
                with self.subTest(migration=migration, contents=contents):
                    if contents is None:
                        path.unlink()
                    else:
                        path.write_text(contents)
                    self.assertTrue(any('migration-required:' in error and migration in error
                                        for error in guard.violations(self.root)))
                    path.write_text(original)

    def test_snapshot_drop_and_validate_are_real_positive_requirements(self):
        self.write(guard.MIGRATION_ROOT + '/' + guard.MIGRATIONS[2], '-- no snapshot drop')
        self.assertTrue(any('migration-snapshot-drop-required' in error
                            for error in guard.violations(self.root)))
        self.write('server/src/main/resources/application.yml', 'ddl-auto: none')
        self.write('server/src/main/resources/application-dev-h2.yml', 'ddl-auto: validate')
        self.assertTrue(any('production-ddl-validate-required' in error
                            for error in guard.violations(self.root)))

    def test_unsafe_production_ddl_fails_but_dev_h2_remains_explicit_exception(self):
        self.write('server/src/main/resources/application-dev-h2.yml', 'ddl-auto: update')
        self.assertEqual(guard.violations(self.root), [])
        for profile in ('application.yml', 'application-dev-postgres.yml', 'application-prod.yml'):
            for value in ('update', 'create', 'create-drop'):
                for separator in (': ', '='):
                    with self.subTest(profile=profile, value=value, separator=separator):
                        self.write('server/src/main/resources/' + profile,
                                   'ddl-auto' + separator + value)
                        self.assertTrue(any('unsafe-production-ddl:' in error
                                            for error in guard.violations(self.root)))
                        self.write('server/src/main/resources/' + profile, 'ddl-auto: validate')

    def test_docs_only_ci_still_executes_guard(self):
        root = Path(__file__).resolve().parents[1]
        workflow = (root / '.github/workflows/ci.yml').read_text()
        block = workflow.split('  core_docs:\n', 1)[1].split('\n  api_contract:', 1)[0]
        self.assertIn('name: Provider boundary invariants', block)
        self.assertIn('python3 tools/provider_boundary_invariants.py', block)
        self.assertNotIn('outputs.backend', block)
        self.assertIn("github.event.action != 'labeled'", block)


if __name__ == '__main__':
    unittest.main()
