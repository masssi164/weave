#!/usr/bin/env python3
"""Exercise managed README updates without touching repository documentation."""

from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest

import readme_release_notes as notes


class ReadmeReleaseNotesTest(unittest.TestCase):
    def run_cli(self, content, mode="--check", source="# Draft\n\nHistorical changes.\n"):
        with tempfile.TemporaryDirectory(prefix="weave-readme-check-") as directory:
            root = Path(directory)
            (root / "tools").mkdir()
            shutil.copyfile(notes.__file__, root / "tools/readme_release_notes.py")
            readme = root / "README.md"
            readme.write_text(content)
            source_path = root / "docs/release-notes/unreleased.md"
            source_path.parent.mkdir(parents=True)
            source_path.write_text(source)
            result = subprocess.run(
                [sys.executable, str(root / "tools/readme_release_notes.py"), mode],
                text=True, capture_output=True, check=False,
            )
            return result, readme.read_text()

    def setUp(self):
        self.readme = notes.README.read_text()

    def test_current_readme_passes_without_modification(self):
        result, after = self.run_cli(self.readme)
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual(self.readme, after)

    def test_stale_pointer_check_is_nonmutating_and_update_preserves_surroundings(self):
        stale = self.readme.replace("Historical change draft:", "Old label:")
        result, after = self.run_cli(stale)
        self.assertNotEqual(0, result.returncode)
        self.assertEqual(stale, after)
        result, after = self.run_cli(stale, "--update")
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual(self.readme, after)

    def test_missing_duplicate_or_reversed_markers_cannot_be_updated(self):
        cases = [
            self.readme.replace(notes.START, ""),
            self.readme + "\n" + notes.START,
            self.readme.replace(notes.START, "SWAP").replace(notes.END, notes.START)
                .replace("SWAP", notes.END),
        ]
        for content in cases:
            with self.subTest(content=content[-100:]):
                result, after = self.run_cli(content, "--update")
                self.assertNotEqual(0, result.returncode)
                self.assertEqual(content, after)

    def test_marker_blocks_must_stay_in_their_own_sections(self):
        misplaced = self.readme.replace(notes.EVIDENCE_BLOCK, "") + "\n" + notes.EVIDENCE_BLOCK
        result, after = self.run_cli(misplaced, "--update")
        self.assertNotEqual(0, result.returncode)
        self.assertEqual(misplaced, after)

    def test_empty_source_cannot_mutate_valid_readme(self):
        result, after = self.run_cli(self.readme, "--update", "# Draft\n")
        self.assertNotEqual(0, result.returncode)
        self.assertEqual(self.readme, after)


if __name__ == "__main__":
    unittest.main()
