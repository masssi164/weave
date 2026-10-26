"""Real Gradle graph probes using the normal pinned dependency resolution path."""
from __future__ import annotations

import json
from pathlib import Path
import re
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]


class DomainGradleWiringTest(unittest.TestCase):
    def gradle(self, *arguments, success=True):
        # Owning checks resolve their own libraries, not App-only dependencies.
        # A cold CI cache must resolve the App/export graph normally; --offline
        # makes these guard assertions depend on unrelated earlier builds.
        result = subprocess.run([str(ROOT / 'gradlew'), '--no-daemon', '--console=plain',
                                 '--stacktrace', *arguments], cwd=ROOT,
                                text=True, capture_output=True, timeout=180)
        output = result.stdout + result.stderr
        self.assertEqual(result.returncode == 0, success, output)
        return output

    def unit_tasks(self, output):
        return set(re.findall(r'^(:server:app:domain[A-Z][a-z]+UnitTest) SKIPPED$', output, re.MULTILINE))

    def test_subset_check_uses_only_selected_inventory(self):
        output = self.gradle(':server:domains:check', '-Pdomains=files,chat', '--dry-run')
        self.assertEqual(self.unit_tasks(output), set())
        self.assertIn(':server:domains:files:test SKIPPED', output)
        self.assertIn(':server:domains:files:contextTest SKIPPED', output)
        self.assertIn(':server:domains:chat:test SKIPPED', output)
        self.assertIn(':server:domains:chat:contextTest SKIPPED', output)
        self.assertIn(':server:domains:chat:buildMatrixRustCoreJni SKIPPED', output)
        self.assertNotIn(':server:domains:decisions:test', output)
        self.assertNotIn(':server:domains:meetings:test', output)
        self.assertIn(':server:domains:files:persistence:contextTest SKIPPED', output)
        self.assertIn(':server:core:contextTest SKIPPED', output)
        self.assertNotIn(':server:domains:calendar:contextTest', output)
        self.assertNotIn(':server:core:model:contextTest', output)
        self.assertNotIn(':server:domains:files:model:contextTest', output)
        self.assertIn(':server:domains:files:model:test SKIPPED', output)
        self.assertNotIn(':server:app:test SKIPPED', output)
        self.assertNotIn(':server:app:postgresJpaTest', output)
        self.assertNotIn(':server:app:buildMatrixRustCoreJni', output)

    def test_test_alias_is_scoped_to_the_requested_domain(self):
        output = self.gradle(':server:domains:test', '-Pdomains=calendar', '--dry-run')
        self.assertEqual(self.unit_tasks(output), set())
        self.assertIn(':server:domains:calendar:test SKIPPED', output)
        self.assertNotIn(':server:domains:calendar:contextTest', output)
        self.assertNotIn(':server:domains:files:model:test SKIPPED', output)

    def test_default_and_explicit_all_have_the_same_domain_graph(self):
        default = self.gradle(':server:domains:check', '--dry-run')
        explicit = self.gradle(':server:domains:check', '-Pdomains=all', '--dry-run')
        expected = set()
        self.assertEqual(self.unit_tasks(default), expected)
        self.assertEqual(self.unit_tasks(explicit), expected)
        for output in (default, explicit):
            for domain in ('files', 'calendar', 'chat', 'decisions', 'meetings'):
                self.assertIn(f':server:domains:{domain}:test SKIPPED', output)
                self.assertIn(f':server:domains:{domain}:contextTest SKIPPED', output)
                self.assertNotIn(f':server:domains:{domain}:model:contextTest', output)

    def test_boot_jar_verifies_the_selected_runtime(self):
        output = self.gradle(':server:app:bootJar', '-Pdomains=files', '--dry-run')
        self.assertIn(':server:app:verifySelectedRuntime SKIPPED', output)

    def test_reduced_exports_schedule_the_same_runtime_verification(self):
        for task in ('openApiContractExport', 'entitySchemaExport'):
            output = self.gradle(f':server:app:{task}', '-Pdomains=files', '--dry-run')
            self.assertIn(':server:app:verifySelectedRuntime SKIPPED', output)

    def test_guard_runs_even_when_its_packaging_consumer_is_up_to_date(self):
        with tempfile.TemporaryDirectory(prefix='weave-domain-gradle-') as tmp:
            marker = Path(tmp) / 'packaging-probe.txt'
            init = Path(tmp) / 'packaging-probe.gradle'
            init.write_text('''gradle.projectsEvaluated {
    def server = gradle.rootProject.project(':server:app')
    server.tasks.register('domainPackagingGuardProbe') {
        dependsOn ':server:app:verifySelectedRuntime'
        def marker = new File(MARKER)
        outputs.file(marker)
        doLast { marker.text = 'test-only packaging probe' }
    }
}
'''.replace('MARKER', json.dumps(str(marker))))
            args = ['-I', str(init), ':server:app:domainPackagingGuardProbe']
            self.gradle(*args, '-Pdomains=all')
            repeat = self.gradle(*args, '-Pdomains=all')
            self.assertIn(':server:app:domainPackagingGuardProbe UP-TO-DATE', repeat)
            self.assertIn('> Task :server:app:verifySelectedRuntime', repeat)
            self.assertNotIn(':server:app:verifySelectedRuntime UP-TO-DATE', repeat)
            reduced = self.gradle(*args, '-Pdomains=files')
            self.assertIn('> Task :server:app:verifySelectedRuntime', reduced)
            self.assertEqual(marker.read_text(), 'test-only packaging probe')

    def test_omitted_runtime_dependency_is_rejected(self):
        with tempfile.TemporaryDirectory(prefix='weave-domain-gradle-') as tmp:
            init = Path(tmp) / 'extra-domain.gradle'
            init.write_text("""gradle.projectsEvaluated {
    def app = gradle.rootProject.project(':server:app')
    app.dependencies.add('implementation', gradle.rootProject.project(':server:domains:chat'))
}
""")
            output = self.gradle('-I', str(init), ':server:app:verifySelectedRuntime',
                                 '-Pdomains=files', success=False)
            self.assertIn('Selected runtime mismatch', output)

    def test_reduced_export_never_compiles_the_all_domain_integration_suite(self):
        output = self.gradle(':server:app:openApiContractExport', '-Pdomains=files', '--dry-run')
        self.assertIn(':server:app:compileApiExportJava SKIPPED', output)
        self.assertNotIn(':server:app:compileTestJava', output)
        self.assertNotIn(':server:domains:chat:', output)
        self.assertNotIn(':server:domains:calendar:', output)
        self.assertNotIn('buildMatrixRustCoreJni', output)


if __name__ == '__main__':
    unittest.main()
