#!/usr/bin/env python3
"""Verify that Weave's Matrix crypto vendor tree is a narrow pinned patch."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import subprocess
import tarfile
import tempfile
import urllib.request
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
DEFAULT_MANIFEST = ROOT / "rust/vendor/matrix-sdk-crypto.weave-provenance.json"
DEFAULT_VENDOR = ROOT / "rust/vendor/matrix-sdk-crypto"
DEFAULT_LOCK = ROOT / "Cargo.lock"


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--manifest", type=Path, default=DEFAULT_MANIFEST)
    parser.add_argument("--vendor", type=Path, default=DEFAULT_VENDOR)
    parser.add_argument("--lock", type=Path, default=DEFAULT_LOCK)
    parser.add_argument("--archive", type=Path)
    return parser.parse_args()


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def file_hashes(root: Path) -> dict[str, str]:
    return {
        path.relative_to(root).as_posix(): sha256(path)
        for path in root.rglob("*")
        if path.is_file() and not path.is_symlink()
    }


def locate_cached_archive(crate: str, version: str, expected_sha: str) -> Path | None:
    cargo_home = Path(os.environ.get("CARGO_HOME", Path.home() / ".cargo"))
    for candidate in sorted(
        (cargo_home / "registry/cache").glob(f"*/{crate}-{version}.crate")
    ):
        if sha256(candidate) == expected_sha:
            return candidate
    return None


def locked_versions(lock_path: Path, crate: str) -> list[str]:
    contents = lock_path.read_text(encoding="utf-8")
    versions: list[str] = []
    for package in re.split(r"(?m)^\[\[package\]\]\s*$", contents)[1:]:
        name = re.search(r'(?m)^name = "([^"]+)"$', package)
        version = re.search(r'(?m)^version = "([^"]+)"$', package)
        if name is not None and version is not None and name.group(1) == crate:
            versions.append(version.group(1))
    return versions


def safe_extract(archive: Path, destination: Path) -> Path:
    with tarfile.open(archive, "r:gz") as bundle:
        for member in bundle.getmembers():
            member_path = Path(member.name)
            if member_path.is_absolute() or ".." in member_path.parts:
                raise ValueError("The pinned crate archive contains an unsafe path.")
        bundle.extractall(destination)
    roots = [path for path in destination.iterdir() if path.is_dir()]
    if len(roots) != 1:
        raise ValueError("The pinned crate archive must contain exactly one root.")
    return roots[0]


def main() -> int:
    args = parse_args()
    manifest = json.loads(args.manifest.read_text(encoding="utf-8"))
    if manifest.get("schemaVersion") != 2:
        raise ValueError("Unsupported Matrix SDK provenance schema.")
    crate = manifest["crate"]
    version = manifest["version"]
    expected_sha = manifest["archiveSha256"]
    allowed = set(manifest["allowedDifferences"])
    packaging_differences = set(manifest["allowedPackagingDifferences"])
    patch_series = manifest["patchSeries"]
    if not isinstance(patch_series, list) or not patch_series:
        raise ValueError("Matrix SDK provenance must contain a focused patch series.")
    changed_paths: set[str] = set()
    patch_paths: list[Path] = []
    for index, entry in enumerate(patch_series, start=1):
        relative_patch = Path(entry["path"])
        if relative_patch.is_absolute() or ".." in relative_patch.parts:
            raise ValueError(f"Patch {index} path must remain inside the vendor directory.")
        patch_path = (args.manifest.parent / relative_patch).resolve()
        try:
            patch_path.relative_to(args.manifest.parent.resolve())
        except ValueError as error:
            raise ValueError(f"Patch {index} escapes the vendor directory.") from error
        if not patch_path.is_file() or sha256(patch_path) != entry.get("sha256"):
            raise ValueError(f"Patch {index} is missing or has the wrong checksum.")
        if not entry.get("invariant") or not entry.get("regressionTests"):
            raise ValueError(f"Patch {index} must name its invariant and regression tests.")
        issues = entry.get("upstreamIssues")
        if not isinstance(issues, list) or not issues or any(
            not issue.startswith("https://github.com/matrix-org/matrix-rust-sdk/issues/")
            for issue in issues
        ):
            raise ValueError(f"Patch {index} must name Matrix Rust SDK upstream issues.")
        entry_paths = entry.get("changedPaths")
        if not isinstance(entry_paths, list) or not entry_paths:
            raise ValueError(f"Patch {index} must name its changed source paths.")
        changed_paths.update(entry_paths)
        patch_paths.append(patch_path)
    if packaging_differences | changed_paths != allowed:
        raise ValueError("Patch paths and packaging differences must cover the full allowlist.")
    if locked_versions(args.lock, crate) != [version]:
        raise ValueError(
            "Cargo.lock must contain exactly the pinned Matrix SDK crypto version."
        )
    if manifest.get("releaseUrl") != (
        "https://github.com/matrix-org/matrix-rust-sdk/releases/tag/"
        f"matrix-sdk-{version}"
    ):
        raise ValueError("The Matrix SDK provenance release URL does not match the pin.")
    archive = args.archive or locate_cached_archive(crate, version, expected_sha)

    with tempfile.TemporaryDirectory(prefix="weave-matrix-sdk-provenance-") as temp:
        temporary_root = Path(temp)
        if archive is None:
            archive = temporary_root / f"{crate}-{version}.crate"
            urllib.request.urlretrieve(manifest["downloadUrl"], archive)
        if sha256(archive) != expected_sha:
            raise ValueError("The Matrix SDK crate archive checksum does not match the pin.")
        upstream = safe_extract(archive, temporary_root / "upstream")
        upstream_files = file_hashes(upstream)
        vendor_files = file_hashes(args.vendor)
        differences = {
            path
            for path in upstream_files.keys() | vendor_files.keys()
            if upstream_files.get(path) != vendor_files.get(path)
        }
        unexpected = differences - allowed
        stale = allowed - differences
        if unexpected:
            raise ValueError(
                "Unreviewed Matrix SDK vendor differences: " + ", ".join(sorted(unexpected))
            )
        if stale:
            raise ValueError(
                "Matrix SDK provenance allowlist contains unchanged paths: "
                + ", ".join(sorted(stale))
            )

        for patch_path in patch_paths:
            result = subprocess.run(
                ["git", "apply", "--check", str(patch_path)],
                cwd=upstream,
                text=True,
                capture_output=True,
                check=False,
            )
            if result.returncode != 0:
                raise ValueError(
                    f"Matrix SDK patch does not apply cleanly: {patch_path.name}: "
                    + result.stderr.strip()
                )
            subprocess.run(
                ["git", "apply", str(patch_path)],
                cwd=upstream,
                text=True,
                capture_output=True,
                check=True,
            )

        patched_files = file_hashes(upstream)
        remaining = {
            path
            for path in patched_files.keys() | vendor_files.keys()
            if patched_files.get(path) != vendor_files.get(path)
        }
        if remaining != packaging_differences:
            raise ValueError(
                "Focused patch series does not reconstruct the vendored source: "
                + ", ".join(sorted(remaining ^ packaging_differences))
            )
    cargo_manifest = (args.vendor / "Cargo.toml.orig").read_text(encoding="utf-8")
    if f'version = "{version}"' not in cargo_manifest:
        raise ValueError("The vendored Matrix SDK version does not match the pin.")
    print(
        "matrix-sdk-vendor-provenance: passed "
        f"crate={crate} version={version} differences={len(differences)}"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
