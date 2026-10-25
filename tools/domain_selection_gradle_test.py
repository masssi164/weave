"""Real Gradle wiring regressions. Run after the normal JVM dependency setup."""
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
        result = subprocess.run([str(ROOT / 'gradlew'), '--no-daemon', '--console=plain',
                                 '--offline', *arguments], cwd=ROOT,
                                text=True, capture_output=True, timeout=180)
        output = result.stdout + result.stderr
        self.assertEqual(result.returncode == 0, success, output)
        return output

    def unit_tasks(self, output):
        return set(re.findall(r'^(:server:domain[A-Z][a-z]+UnitTest) SKIPPED$', output, re.MULTILINE))

    def test_subset_check_uses_only_selected_inventory(self):
        output = self.gradle(':server:domains:check', '-Pdomains=files,chat', '--dry-run')
        self.assertEqual(self.unit_tasks(output), {':server:domainFilesUnitTest', ':server:domainChatUnitTest'})
        self.assertIn(':server:domains:files:model:test SKIPPED', output)
        self.assertNotIn(':server:test SKIPPED', output)
        self.assertNotIn(':server:postgresJpaTest', output)
        self.assertNotIn(':server:buildMatrixRustCoreJni', output)

    def test_test_alias_is_scoped_to_the_requested_domain(self):
        output = self.gradle(':server:domains:test', '-Pdomains=calendar', '--dry-run')
        self.assertEqual(self.unit_tasks(output), {':server:domainCalendarUnitTest'})
        self.assertNotIn(':server:domains:files:model:test SKIPPED', output)

    def test_default_and_explicit_all_have_the_same_domain_graph(self):
        default = self.gradle(':server:domains:check', '--dry-run')
        explicit = self.gradle(':server:domains:check', '-Pdomains=all', '--dry-run')
        expected = {':server:domainCalendarUnitTest', ':server:domainChatUnitTest', ':server:domainFilesUnitTest'}
        self.assertEqual(self.unit_tasks(default), expected)
        self.assertEqual(self.unit_tasks(explicit), expected)

    def test_boot_jar_has_an_independent_packaging_guard(self):
        output = self.gradle(':server:bootJar', '-Pdomains=files', '--dry-run')
        self.assertIn(':server:verifyFullDomainPackaging SKIPPED', output)

    def test_guard_runs_even_when_its_packaging_consumer_is_up_to_date(self):
        with tempfile.TemporaryDirectory(prefix='weave-domain-gradle-') as tmp:
            marker = Path(tmp) / 'packaging-probe.txt'
            init = Path(tmp) / 'packaging-probe.gradle'
            init.write_text('''gradle.projectsEvaluated {
    def server = gradle.rootProject.project(':server')
    server.tasks.register('domainPackagingGuardProbe') {
        dependsOn ':server:verifyFullDomainPackaging'
        def marker = new File(MARKER)
        outputs.file(marker)
        doLast { marker.text = 'test-only packaging probe' }
    }
}
'''.replace('MARKER', json.dumps(str(marker))))
            args = ['-I', str(init), ':server:domainPackagingGuardProbe']
            self.gradle(*args, '-Pdomains=all')
            repeat = self.gradle(*args, '-Pdomains=all')
            self.assertIn(':server:domainPackagingGuardProbe UP-TO-DATE', repeat)
            self.assertIn('> Task :server:verifyFullDomainPackaging', repeat)
            self.assertNotIn(':server:verifyFullDomainPackaging UP-TO-DATE', repeat)
            refused = self.gradle(*args, '-Pdomains=files', success=False)
            self.assertIn('production domain packaging is not implemented', refused)
            self.assertEqual(marker.read_text(), 'test-only packaging probe')


if __name__ == '__main__':
    unittest.main()
