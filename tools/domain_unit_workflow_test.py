"""Exercise the actual workflow shell with real selection and a recording Gradle stub."""
from __future__ import annotations

import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import textwrap
import unittest


ROOT = Path(__file__).resolve().parents[1]
WORKFLOW = ROOT / '.github/workflows/domain-unit-checks.yml'


def selection_step() -> str:
    text = WORKFLOW.read_text(encoding='utf-8')
    step = text.split('      - name: Resolve selection and run existing domain units\n', 1)[1]
    block = step.split('        run: |\n', 1)[1].split('\n      - ', 1)[0]
    return textwrap.dedent(block)


class DomainUnitWorkflowTest(unittest.TestCase):
    def setUp(self) -> None:
        self.directory = tempfile.TemporaryDirectory(prefix='weave-domain-workflow-')
        self.addCleanup(self.directory.cleanup)
        self.root = Path(self.directory.name)
        (self.root / 'tools').mkdir()
        (self.root / 'server/domains').mkdir(parents=True)
        shutil.copyfile(ROOT / 'tools/domain_selection_resolver.py',
                        self.root / 'tools/domain_selection_resolver.py')
        catalog = json.loads((ROOT / 'server/domains/catalog.json').read_text(encoding='utf-8'))
        catalog['selection'] = {'pinned_branch': 'dev', 'pinned_domains': ['calendar']}
        (self.root / 'server/domains/catalog.json').write_text(json.dumps(catalog), encoding='utf-8')
        # This stub verifies argument transport only. Actual JVM tests run separately.
        wrapper = self.root / 'gradlew'
        wrapper.write_text('#!/usr/bin/env python3\nimport json, sys\n'
                           'print("GRADLE_ARGUMENTS=" + json.dumps(sys.argv[1:]))\n',
                           encoding='utf-8')
        wrapper.chmod(0o755)
        self.git('init', '--quiet', '--initial-branch=dev')
        self.git('add', 'tools', 'server', 'gradlew')
        self.git('-c', 'user.name=Domain workflow fixture',
                 '-c', 'user.email=domain-fixture@example.invalid',
                 'commit', '--quiet', '-m', 'test fixture')
        self.commit = self.git('rev-parse', 'HEAD').strip()
        self.git('checkout', '--quiet', '--detach', self.commit)

    def git(self, *args: str) -> str:
        return subprocess.run(['git', *args], cwd=self.root, text=True,
                              capture_output=True, check=True).stdout

    def run_step(self, domains: str, ref: str = 'refs/heads/dev',
                 commit: str | None = None) -> subprocess.CompletedProcess:
        return subprocess.run(['bash', '-c', selection_step()], cwd=self.root,
                              env={**os.environ, 'REQUESTED_DOMAINS': domains,
                                   'SOURCE_REF': ref, 'SOURCE_COMMIT': commit or self.commit},
                              text=True, capture_output=True, check=False)

    def assert_selection(self, result: subprocess.CompletedProcess, selection: str) -> None:
        self.assertEqual(result.returncode, 0, result.stderr)
        lines = [line for line in result.stdout.splitlines()
                 if line.startswith('GRADLE_ARGUMENTS=')]
        self.assertEqual(len(lines), 1, result.stdout)
        self.assertEqual(json.loads(lines[0].partition('=')[2]),
                         ['--no-daemon', '--stacktrace', ':server:domains:check',
                          '-Pdomains=' + selection])

    def test_explicit_domains_override_pin_and_are_normalized(self) -> None:
        self.assert_selection(self.run_step(' files,chat,files '), 'chat,files')

    def test_explicit_all_overrides_restrictive_pin(self) -> None:
        self.assert_selection(self.run_step('all'), 'calendar,chat,files')

    def test_omitted_input_uses_matching_source_branch_pin(self) -> None:
        self.assert_selection(self.run_step(''), 'calendar')

    def test_unpinned_source_defaults_to_all(self) -> None:
        self.assert_selection(self.run_step('', 'refs/heads/feature/unit-checks'),
                              'calendar,chat,files')

    def test_pull_request_merge_ref_does_not_inherit_a_base_branch_pin(self) -> None:
        self.assert_selection(self.run_step('', 'refs/pull/7/merge'), 'calendar,chat,files')

    def test_bad_input_is_data_and_does_not_start_gradle(self) -> None:
        result = self.run_step('files,$(touch injected)')
        self.assertNotEqual(result.returncode, 0)
        self.assertNotIn('GRADLE_ARGUMENTS=', result.stdout)
        self.assertFalse((self.root / 'injected').exists())

    def test_checkout_identity_mismatch_stops_before_gradle(self) -> None:
        result = self.run_step('all', commit='0' * 40)
        self.assertNotEqual(result.returncode, 0)
        self.assertNotIn('GRADLE_ARGUMENTS=', result.stdout)

    def test_workflow_remains_read_only_with_focused_pr_manual_and_reusable_events(self) -> None:
        text = WORKFLOW.read_text(encoding='utf-8')
        self.assertIn('  workflow_dispatch:\n', text)
        self.assertIn('  workflow_call:\n', text)
        self.assertIn('  pull_request:\n', text)
        self.assertIn("      - 'server/domains/**'\n", text)
        self.assertIn('  contents: read\n', text)
        self.assertIn('runs-on: ubuntu-24.04-arm', text)
        self.assertIn("${{ inputs.domains || 'default' }}", text.split('concurrency:', 1)[1].split('jobs:', 1)[0])
        self.assertNotIn('default: all', text)
        self.assertNotIn('packages: write', text)
        self.assertNotIn('pull_request_target:', text)
        self.assertNotIn('${{', selection_step())


if __name__ == '__main__':
    unittest.main()
