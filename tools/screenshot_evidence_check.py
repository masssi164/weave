#!/usr/bin/env python3
"""Validate deterministic README and roadmap screenshot evidence."""

from __future__ import annotations

import json
import re
import sys
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[1]
MANIFEST = ROOT / "docs" / "assets" / "screenshot-evidence.json"
README_FILES = (
    ROOT / "README.md",
    ROOT / "client" / "README.md",
)
GENERATOR = "client/tool/generate_marketing_screenshots.py"
ASSET_DIRS = (
    ROOT / "docs" / "assets" / "marketing",
    ROOT / "docs" / "assets" / "roadmap",
)
ALLOWED_STATUSES = {"ready_foundation", "guarded_roadmap"}
FORBIDDEN_CLAIM_FRAGMENTS = (
    "is production ready",
    "is release ready",
    "is release-ready",
    "is ga ready",
    "is fully available",
    "are fully available",
    "are provider interchangeable",
    "guarantees lossless migration",
    "enables autonomous agent operation",
)


def fail(message: str) -> None:
    print(f"screenshot-evidence-check: {message}", file=sys.stderr)
    raise SystemExit(1)


def load_manifest() -> dict[str, Any]:
    try:
        data = json.loads(MANIFEST.read_text(encoding="utf-8"))
    except json.JSONDecodeError as error:
        fail(f"{MANIFEST.relative_to(ROOT)} is not valid JSON: {error}")
    if not isinstance(data, dict):
        fail("manifest root must be an object")
    return data


def svg_asset_paths() -> set[str]:
    paths: set[str] = set()
    for directory in ASSET_DIRS:
        paths.update(path.relative_to(ROOT).as_posix() for path in directory.glob("*.svg"))
    return paths


def readme_asset_paths() -> set[str]:
    paths: set[str] = set()
    pattern = re.compile(r"(?:\.\./)?docs/assets/(?:marketing|roadmap)/[A-Za-z0-9_.-]+\.svg")
    for readme in README_FILES:
        text = readme.read_text(encoding="utf-8")
        for match in pattern.findall(text):
            paths.add(match.removeprefix("../"))
    return paths


def validate_entry(entry: Any, seen: set[str]) -> str:
    if not isinstance(entry, dict):
        fail("asset entries must be objects")
    path = entry.get("path")
    if not isinstance(path, str) or not path:
        fail("asset entry is missing a path")
    if path in seen:
        fail(f"duplicate asset entry: {path}")
    seen.add(path)
    if not (ROOT / path).is_file():
        fail(f"asset listed in manifest does not exist: {path}")
    if entry.get("source") != "deterministic-svg-generator":
        fail(f"{path} must use source=deterministic-svg-generator")
    if entry.get("maturityStatus") not in ALLOWED_STATUSES:
        fail(f"{path} has unsupported maturityStatus={entry.get('maturityStatus')!r}")
    for key in ("claimBoundary", "altText"):
        value = entry.get(key)
        if not isinstance(value, str) or len(value.strip()) < 24:
            fail(f"{path} needs a descriptive {key}")
    claim = entry["claimBoundary"].lower()
    for fragment in FORBIDDEN_CLAIM_FRAGMENTS:
        if fragment in claim:
            fail(f"{path} contains forbidden broad claim wording: {fragment}")
    if entry["maturityStatus"] == "guarded_roadmap" and "guarded" not in claim:
        fail(f"{path} roadmap asset must state a guarded claim boundary")
    return path


def main() -> None:
    data = load_manifest()
    if data.get("schemaVersion") != 1:
        fail("schemaVersion must be 1")
    if data.get("generator") != GENERATOR:
        fail(f"generator must be {GENERATOR}")
    gate = data.get("gateCommand")
    if not isinstance(gate, str) or "make marketing-screenshots" not in gate:
        fail("gateCommand must include make marketing-screenshots")
    assets = data.get("assets")
    if not isinstance(assets, list) or not assets:
        fail("assets must be a non-empty list")

    seen: set[str] = set()
    manifest_paths = {validate_entry(entry, seen) for entry in assets}
    checked_in_paths = svg_asset_paths()
    if manifest_paths != checked_in_paths:
        missing = sorted(checked_in_paths - manifest_paths)
        stale = sorted(manifest_paths - checked_in_paths)
        fail(f"manifest/assets mismatch; missing={missing} stale={stale}")

    readme_paths = readme_asset_paths()
    missing_readme_assets = sorted(readme_paths - manifest_paths)
    if missing_readme_assets:
        fail(f"README references assets not covered by manifest: {missing_readme_assets}")

    print(f"screenshot-evidence-check: ok assets={len(manifest_paths)}")


if __name__ == "__main__":
    main()
