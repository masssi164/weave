#!/usr/bin/env python3
"""Validate Admin Console dependency version policy."""

from __future__ import annotations

import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ADMIN = ROOT / "admin-console"
PACKAGE_JSON = ADMIN / "package.json"
PACKAGE_LOCK = ADMIN / "package-lock.json"
README = ADMIN / "README.md"
SECTIONS = ("dependencies", "devDependencies")


def fail(message: str) -> None:
    print(f"admin-console-dependency-policy-check: {message}", file=sys.stderr)
    raise SystemExit(1)


def read_json(path: Path) -> dict:
    if not path.exists():
        fail(f"missing required file: {path.relative_to(ROOT)}")
    return json.loads(path.read_text(encoding="utf-8"))


def main() -> None:
    package = read_json(PACKAGE_JSON)
    lock = read_json(PACKAGE_LOCK)
    lock_root = lock.get("packages", {}).get("", {})
    readme = README.read_text(encoding="utf-8")

    for section in SECTIONS:
        manifest_versions = package.get(section, {})
        lock_versions = lock_root.get(section, {})
        if set(manifest_versions) != set(lock_versions):
            fail(f"{section} entries differ between package.json and package-lock.json")
        for name, version in manifest_versions.items():
            if version == "latest" or "latest" in version.lower():
                fail(f"{name} in {section} uses non-reproducible version {version!r}")
            if lock_versions.get(name) != version:
                fail(f"{name} version differs between package.json and package-lock.json")
            package_entry = lock.get("packages", {}).get(f"node_modules/{name}", {})
            if package_entry.get("version") != version:
                fail(f"{name} root pin {version!r} does not match locked package version {package_entry.get('version')!r}")

    for fragment in (
        "exact dependency versions",
        "do not use `latest` ranges",
        "npm install --package-lock-only",
        "npm ci",
        "npm run ci",
    ):
        if fragment not in readme:
            fail(f"README dependency policy missing {fragment!r}")

    print("admin-console-dependency-policy-check: ok")


if __name__ == "__main__":
    main()
