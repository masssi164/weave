#!/usr/bin/env python3
"""Tests for the pinned Matrix SDK vendor provenance guard."""

from __future__ import annotations

import hashlib
import json
import subprocess
import sys
import tarfile
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SCRIPT = ROOT / "tools/matrix_sdk_vendor_provenance.py"


class MatrixSdkVendorProvenanceTest(unittest.TestCase):
    def test_accepts_only_manifested_patch_paths(self) -> None:
        with self.fixture() as fixture:
            result = self.run_guard(fixture)
            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertIn("differences=1", result.stdout)

    def test_rejects_an_unreviewed_vendor_difference(self) -> None:
        with self.fixture() as fixture:
            (fixture["vendor"] / "unexpected.txt").write_text("drift", encoding="utf-8")
            result = self.run_guard(fixture)
            self.assertNotEqual(result.returncode, 0)
            self.assertIn("Unreviewed Matrix SDK vendor differences", result.stderr)

    def test_rejects_a_mismatched_archive_checksum(self) -> None:
        with self.fixture() as fixture:
            manifest = json.loads(fixture["manifest"].read_text(encoding="utf-8"))
            manifest["archiveSha256"] = "0" * 64
            fixture["manifest"].write_text(json.dumps(manifest), encoding="utf-8")
            result = self.run_guard(fixture)
            self.assertNotEqual(result.returncode, 0)
            self.assertIn("archive checksum does not match", result.stderr)

    def fixture(self):
        temporary = tempfile.TemporaryDirectory()
        root = Path(temporary.name)
        upstream = root / "matrix-sdk-crypto-0.18.0"
        vendor = root / "vendor"
        (upstream / "src").mkdir(parents=True)
        (vendor / "src").mkdir(parents=True)
        for directory in (upstream, vendor):
            (directory / "Cargo.toml.orig").write_text(
                '[package]\nname = "matrix-sdk-crypto"\nversion = "0.18.0"\n',
                encoding="utf-8",
            )
        (upstream / "src/patched.rs").write_text("upstream", encoding="utf-8")
        (vendor / "src/patched.rs").write_text("weave", encoding="utf-8")
        archive = root / "matrix-sdk-crypto-0.18.0.crate"
        with tarfile.open(archive, "w:gz") as bundle:
            bundle.add(upstream, arcname=upstream.name)
        manifest = root / "manifest.json"
        lock = root / "Cargo.lock"
        lock.write_text(
            'version = 4\n\n[[package]]\nname = "matrix-sdk-crypto"\n'
            'version = "0.18.0"\n',
            encoding="utf-8",
        )
        manifest.write_text(
            json.dumps(
                {
                    "schemaVersion": 1,
                    "crate": "matrix-sdk-crypto",
                    "version": "0.18.0",
                    "releaseUrl": "https://github.com/matrix-org/matrix-rust-sdk/releases/tag/matrix-sdk-0.18.0",
                    "archiveSha256": hashlib.sha256(archive.read_bytes()).hexdigest(),
                    "downloadUrl": "https://invalid.example.test/unused",
                    "allowedDifferences": ["src/patched.rs"],
                }
            ),
            encoding="utf-8",
        )
        fixture = {
            "temporary": temporary,
            "vendor": vendor,
            "archive": archive,
            "manifest": manifest,
            "lock": lock,
        }

        class FixtureContext:
            def __enter__(self):
                return fixture

            def __exit__(self, exc_type, exc, traceback):
                temporary.cleanup()

        return FixtureContext()

    def run_guard(self, fixture: dict[str, object]) -> subprocess.CompletedProcess[str]:
        return subprocess.run(
            [
                sys.executable,
                str(SCRIPT),
                "--manifest",
                str(fixture["manifest"]),
                "--vendor",
                str(fixture["vendor"]),
                "--lock",
                str(fixture["lock"]),
                "--archive",
                str(fixture["archive"]),
            ],
            text=True,
            capture_output=True,
            check=False,
        )


if __name__ == "__main__":
    unittest.main()
