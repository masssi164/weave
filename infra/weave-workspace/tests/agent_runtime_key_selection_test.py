"""A selected-domain image must not invoke an omitted domain's key operators."""
import os
from pathlib import Path
import subprocess
import tempfile
from textwrap import dedent
import unittest

WORKSPACE = Path(__file__).resolve().parents[1]
REPOSITORY = WORKSPACE.parents[1]


class AgentRuntimeKeySelectionTest(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name)
        self.selection = self.root / "weave-domains"
        self.calls = self.root / "java-calls"
        java = self.root / "java"
        java.write_text('#!/bin/sh\nprintf "%s\\n" "$*" >> "$CALLS"\nexit "${JAVA_STATUS:-0}"\n')
        java.chmod(0o755)
        source = (WORKSPACE / "compose.yaml").read_text()
        initializer = source.split("\n  agent-runtime-keys-init:\n", 1)[1].split("\n  schema-init:\n", 1)[0]
        self.assertIn("    entrypoint:\n      - /bin/bash\n      - -euc\n", initializer)
        command = initializer.split("    command:\n      - |\n", 1)[1].split("    volumes:\n", 1)[0]
        self.script = dedent(command).replace("$$", "$").replace("/app/weave-domains", str(self.selection))

    def initialize(self, domains, java_status=0):
        if domains is not None:
            self.selection.write_text(domains + "\n")
        return subprocess.run(["/bin/bash", "-euc", self.script], capture_output=True,
                              text=True, timeout=10, env={**os.environ,
                              "PATH": str(self.root) + os.pathsep + os.environ["PATH"],
                              "CALLS": str(self.calls), "JAVA_STATUS": str(java_status)})

    def test_member_release_skips_absent_agent_operators(self):
        self.assertEqual(self.initialize("calendar,chat,files").returncode, 0)
        self.assertFalse(self.calls.exists())

    def test_selected_agent_runtime_initializes_both_key_stores(self):
        self.assertEqual(self.initialize("agent-runtime,calendar,chat,files").returncode, 0)
        calls = self.calls.read_text().splitlines()
        self.assertEqual(len(calls), 2)
        self.assertIn("runtime-profile-signing-keys --action=initialize", calls[0])
        self.assertIn("runtime-state-wrapping-keys --action=initialize", calls[1])

    def test_selection_uses_exact_domain_tokens(self):
        self.assertEqual(self.initialize("not-agent-runtime,files").returncode, 0)
        self.assertFalse(self.calls.exists())

    def test_missing_or_malformed_selection_never_skips_silently(self):
        for value in (None, "", ",files", "files,", "files,,chat", "files chat", "files\nchat"):
            with self.subTest(value=value):
                self.assertNotEqual(self.initialize(value).returncode, 0)
                self.assertFalse(self.calls.exists())

    def test_selected_operator_failure_blocks_the_initializer(self):
        self.assertEqual(self.initialize("agent-runtime", java_status=19).returncode, 19)
        self.assertEqual(len(self.calls.read_text().splitlines()), 1)

    def test_packaged_selection_uses_the_existing_image_provenance_input(self):
        dockerfile = (REPOSITORY / "server/Dockerfile").read_text()
        self.assertIn("printf '%s\\n' \"${WEAVE_DOMAINS}\" > /app/weave-domains", dockerfile)
        self.assertIn('chmod 0444 /app/weave-domains', dockerfile)
        self.assertIn('com.massimotter.weave.domains="${WEAVE_DOMAINS}"', dockerfile)
        self.assertNotIn("WEAVE_PACKAGED_DOMAINS", dockerfile)


if __name__ == "__main__":
    unittest.main()
