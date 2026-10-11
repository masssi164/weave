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
