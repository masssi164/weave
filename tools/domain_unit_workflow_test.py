"""Execute the actual matrix-plan and row shells with a recording Gradle stub."""
from __future__ import annotations

import json
import os
from pathlib import Path
import shlex
import shutil
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
WORKFLOW = ROOT / '.github/workflows/domain-unit-checks.yml'


def workflow_step(name: str) -> str:
    text = WORKFLOW.read_text(encoding='utf-8')
    step = text.split('      - name: ' + name + '\n', 1)[1]
    block = step.split('        run: |\n', 1)[1]
    lines = []
    for line in block.splitlines():
        if line.strip() and not line.startswith('          '):
            break
        lines.append(line[10:] if line else '')
    return '\n'.join(lines)


class DomainUnitWorkflowTest(unittest.TestCase):
    def setUp(self) -> None:
        self.directory = tempfile.TemporaryDirectory(prefix='weave-domain-workflow-')
        self.addCleanup(self.directory.cleanup)
        self.root = Path(self.directory.name)
        (self.root / 'tools').mkdir()
        (self.root / 'server/domains').mkdir(parents=True)
        shutil.copyfile(ROOT / 'tools/domain_selection_resolver.py',
                        self.root / 'tools/domain_selection_resolver.py')
        self.catalog = json.loads((ROOT / 'server/domains/catalog.json').read_text(encoding='utf-8'))
        self.catalog['selection'] = {'pinned_branch': 'dev', 'pinned_domains': ['calendar']}
        (self.root / 'server/domains/catalog.json').write_text(json.dumps(self.catalog), encoding='utf-8')
        # This verifies transport/scheduling only; actual JVM checks have their own lane.
        wrapper = self.root / 'gradlew'
        wrapper.write_text('#!/usr/bin/env python3\nimport json, subprocess, sys\n'
                           'print("GRADLE_ARGUMENTS=" + json.dumps(sys.argv[1:]))\n'
                           'domains = [arg.partition("=")[2] for arg in sys.argv[1:] if arg.startswith("-Pdomains=")]\n'
                           'result = subprocess.run([sys.executable, "tools/domain_selection_resolver.py",\n'
                           '    "--domains", domains[-1] if domains else ""], text=True, capture_output=True, check=True)\n'
                           'print("DOMAIN_SELECTION=" + result.stdout.strip())\n', encoding='utf-8')
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

    def plan(self, domains: str, iteration: str = 'selected', ref: str = 'refs/heads/dev',
             commit: str | None = None) -> tuple[subprocess.CompletedProcess, dict | None]:
        output = self.root / 'github-output'
        output.unlink(missing_ok=True)
        result = subprocess.run(['bash', '-c', workflow_step('Resolve domain check matrix')], cwd=self.root,
                                env={**os.environ, 'REQUESTED_DOMAINS': domains, 'ITERATION': iteration,
                                     'SOURCE_REF': ref, 'SOURCE_COMMIT': commit or self.commit,
                                     'GITHUB_BASE_REF': 'dev', 'GITHUB_OUTPUT': str(output)},
                                text=True, capture_output=True, check=False)
        matrix = json.loads(output.read_text().removeprefix('matrix=')) if output.exists() else None
        self.assertNotIn('GRADLE_ARGUMENTS=', result.stdout)
        return result, matrix

    def assert_rows(self, domains: str, expected: list[tuple[str, str]], iteration: str = 'selected',
                    ref: str = 'refs/heads/dev') -> dict:
        result, matrix = self.plan(domains, iteration, ref)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual([(row['domain'], row['domains']) for row in matrix['include']], expected)
        for row in matrix['include']:
            result = subprocess.run(['bash', '-c', workflow_step('Run owning domain checks')], cwd=self.root,
                                    env={**os.environ, 'SELECTED_DOMAINS': row['domains'],
                                         'RESOLVED_SELECTION': row['selection'], 'SOURCE_COMMIT': self.commit},
                                    text=True, capture_output=True, check=False)
            self.assertEqual(result.returncode, 0, result.stderr)
            lines = [line for line in result.stdout.splitlines() if line.startswith('GRADLE_ARGUMENTS=')]
            self.assertEqual(len(lines), 1)
            self.assertEqual(json.loads(lines[0].partition('=')[2]),
                             ['--no-daemon', '--stacktrace', ':server:domains:check', '-Pdomains=' + row['domains']])
        return matrix

    def test_explicit_selection_is_normalized_and_forwarded_as_one_argument(self) -> None:
        self.assert_rows(' files,chat,files ', [('selected', 'chat,files')])

    def test_selected_all_overrides_pin_and_preserves_actual_catalog(self) -> None:
        self.assert_rows('all', [('selected', ','.join(sorted(self.catalog['domains'])))])

    def test_omitted_input_uses_matching_source_branch_pin(self) -> None:
        self.assert_rows('', [('selected', 'calendar')])

    def test_unpinned_source_and_pr_target_dev_do_not_inherit_pin(self) -> None:
        for ref in ('refs/heads/feature/unit-checks', 'refs/pull/7/merge'):
            with self.subTest(ref=ref):
                self.assert_rows('', [('selected', ','.join(sorted(self.catalog['domains'])))], ref=ref)

    def test_individual_rows_include_required_domains_without_widening_selection(self) -> None:
        matrix = self.assert_rows('files,chat,decisions,meetings', [
            ('chat', 'chat'), ('decisions', 'chat,decisions'), ('files', 'files'), ('meetings', 'chat,meetings')],
            iteration='individual')
        self.assertTrue(all(row['selection'] == 'chat,decisions,files,meetings' for row in matrix['include']))

    def test_individual_pin_schedules_only_the_pinned_selection(self) -> None:
        self.assert_rows('', [('calendar', 'calendar')], iteration='individual')

    def test_invalid_domains_and_iteration_never_write_a_matrix(self) -> None:
        for domains, iteration in [('files,$(touch injected)', 'selected'), ('decisions', 'individual'),
                                   ('unknown', 'selected'), ('files', 'unsupported')]:
            with self.subTest(domains=domains, iteration=iteration):
                result, matrix = self.plan(domains, iteration)
                self.assertNotEqual(result.returncode, 0)
                self.assertIsNone(matrix)
        self.assertFalse((self.root / 'injected').exists())

    def test_checkout_identity_mismatch_stops_before_matrix_and_gradle(self) -> None:
        result, matrix = self.plan('all', commit='0' * 40)
        self.assertNotEqual(result.returncode, 0)
        self.assertIsNone(matrix)

    def test_row_rechecks_exact_commit_before_gradle(self) -> None:
        result = subprocess.run(['bash', '-c', workflow_step('Run owning domain checks')], cwd=self.root,
                                env={**os.environ, 'SELECTED_DOMAINS': 'files', 'RESOLVED_SELECTION': 'files',
                                     'SOURCE_COMMIT': '0' * 40}, text=True, capture_output=True, check=False)
        self.assertNotEqual(result.returncode, 0)
        self.assertNotIn('GRADLE_ARGUMENTS=', result.stdout)

    def test_workflow_is_read_only_uses_one_resolver_and_preserves_separate_reports(self) -> None:
        text = WORKFLOW.read_text(encoding='utf-8')
        self.assertIn('  workflow_dispatch:\n', text)
        self.assertIn('  workflow_call:\n', text)
        self.assertIn('  pull_request:\n', text)
        self.assertIn('  contents: read\n', text)
        self.assertIn('runs-on: ubuntu-24.04-arm', text)
        self.assertIn("options: [selected, individual]", text)
        self.assertEqual(text.count('python3 tools/domain_selection_resolver.py'), 1)
        self.assertIn('matrix: ${{ fromJSON(needs.resolve.outputs.matrix) }}', text)
        self.assertIn('fail-fast: false', text)
        self.assertIn("${{ inputs.domains || 'default' }}", text)
        self.assertIn('${{ matrix.domain }}-${{ matrix.domains }}-${{ github.sha }}', text)
        for report in ('test-results/test/', 'test-results/contextTest/', 'reports/tests/test/', 'reports/tests/contextTest/'):
            self.assertIn(report, text)
        self.assertNotIn('domain*UnitTest', text)
        self.assertNotIn('bounded inventory', text)
        self.assertNotIn('default: all', text)
        self.assertNotIn('packages: write', text)
        self.assertNotIn('pull_request_target:', text)
        for name in ('Resolve domain check matrix', 'Run owning domain checks'):
            self.assertNotIn('${{', workflow_step(name))
            self.assertNotIn('eval ', workflow_step(name))

    def test_chat_native_inputs_trigger_checks_and_use_pinned_rust_only_for_chat_rows(self) -> None:
        text = WORKFLOW.read_text(encoding='utf-8')
        for path in ('Cargo.toml', 'Cargo.lock', 'rust/matrix-protocol/rust-toolchain.toml', 'rust/matrix-protocol/**'):
            self.assertIn("      - '" + path + "'\n", text)
        self.assertIn("RUSTUP_TOOLCHAIN: '1.95.0'", text)
        self.assertIn("if: contains(format(',{0},', matrix.domains), ',chat,')", text)
        self.assertIn('rustup toolchain install "$RUSTUP_TOOLCHAIN" --profile minimal', text)

    def test_full_core_ci_commands_override_dev_release_pin_and_pr_fallback(self) -> None:
        # Run the actual full CI shell commands through the same resolver that Gradle uses.
        # A detached PR defaults to all domains; the Dev checkout instead uses the release pin.
        self.catalog = json.loads((ROOT / 'server/domains/catalog.json').read_text(encoding='utf-8'))
        (self.root / 'server/domains/catalog.json').write_text(json.dumps(self.catalog), encoding='utf-8')
        full_tasks = {
            'coreArchitectureCi', 'canonicalDataCi', 'mcpFoundationCi', 'testAppContract',
            'postgresPersistenceCi', 'protocolFacadeFoundationCi', 'generatedApiContractCi',
            'generatedUserApiConsumerCi', 'generatedAdminApiConsumerCi',
        }
        commands = []
        for line in (ROOT / '.github/workflows/ci.yml').read_text(encoding='utf-8').splitlines():
            if './gradlew ' in line:
                command = line[line.index('./gradlew '):]
                if full_tasks.intersection(shlex.split(command)):
                    commands.append(command)
        self.assertEqual({task for command in commands for task in shlex.split(command)
                          if task in full_tasks}, full_tasks)

        def selection(command: str) -> dict:
            result = subprocess.run(['bash', '-c', command], cwd=self.root,
                                    text=True, capture_output=True, check=False)
            self.assertEqual(result.returncode, 0, result.stderr)
            selections = [line.removeprefix('DOMAIN_SELECTION=') for line in result.stdout.splitlines()
                          if line.startswith('DOMAIN_SELECTION=')]
            self.assertEqual(len(selections), 1, result.stdout)
            return json.loads(selections[0])

        all_domains = sorted(self.catalog['domains'])
        pinned_domains = sorted(self.catalog['selection']['pinned_domains'])
        self.assertNotEqual(pinned_domains, all_domains)
        for source in ('dev', 'detached-pr'):
            if source == 'dev':
                self.git('checkout', '--quiet', 'dev')
            else:
                self.git('checkout', '--quiet', '--detach', self.commit)
            with self.subTest(source=source, implicit=True):
                self.assertEqual(selection('./gradlew generatedApiContractCi'), {
                    'domains': pinned_domains if source == 'dev' else all_domains,
                    'selectionSource': 'pin' if source == 'dev' else 'all',
                })
            for command in commands:
                with self.subTest(source=source, command=command):
                    self.assertEqual(selection(command), {
                        'domains': all_domains, 'selectionSource': 'explicit',
                    })


if __name__ == '__main__':
    unittest.main()
