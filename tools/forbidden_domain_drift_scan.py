#!/usr/bin/env python3
"""Scan active repo text for obsolete local dogfood domain aliases."""
from __future__ import annotations

import re
import subprocess
import sys
from pathlib import Path

HOST_RE = re.compile(r"(?<![A-Za-z0-9_-])([A-Za-z0-9_-]+(?:\.[A-Za-z0-9_-]+)*\.local)(?![A-Za-z0-9_-])")
ROOTS = (
    ".github/",
    "client/",
    "docs/",
    "e2e/",
    "infra/",
    "release/",
    "server/",
    "specs/",
    "tools/",
)
BINARY_SUFFIXES = {".gif", ".ico", ".jpg", ".jpeg", ".lock", ".png", ".webp"}

# Intentional non-dogfood occurrences: package/directory names, XML local-name
# accessors, local CA filenames, artifact identifiers, and explicit rejection tests.
ALLOW_PATTERNS = (
    re.compile(r"\bname\.local\b"),
    re.compile(r"\bboards\.local\b"),
    re.compile(r"weave-local-ca\.(?:pem|crt)"),
    re.compile(r"weave-local-[A-Za-z0-9_-]+"),
    re.compile(r"legacy_local_host\b|legacy\.local"),
    re.compile(r"'printer\.local'|\"printer\.local\""),
    re.compile(r"assert_no_legacy_local_truth|assert_url_no_legacy_local_truth"),
    re.compile(r"obsolete .*weave\.local|forbidden .*weave\.local|forbidden-domain scan"),
)


def tracked_files() -> list[str]:
    out = subprocess.check_output(["git", "ls-files"], text=True)
    return [
        line
        for line in out.splitlines()
        if (line.startswith(ROOTS) or line in {"gradle.properties", "settings.gradle.kts"})
        and Path(line).suffix.lower() not in BINARY_SUFFIXES
    ]


def allowed(line: str) -> bool:
    return any(pattern.search(line) for pattern in ALLOW_PATTERNS)


def main() -> int:
    findings: list[str] = []
    for file_name in tracked_files():
        path = Path(file_name)
        try:
            lines = path.read_text(encoding="utf-8", errors="ignore").splitlines()
        except OSError as exc:
            findings.append(f"{file_name}:0: cannot read file: {exc}")
            continue
        for line_no, line in enumerate(lines, start=1):
            if not HOST_RE.search(line) or allowed(line):
                continue
            findings.append(f"{file_name}:{line_no}: {line.strip()}")
    if findings:
        print("Forbidden obsolete .local/weave.local domain drift found:")
        print("\n".join(findings))
        return 1
    print("No forbidden obsolete .local/weave.local domain drift found in tracked active repo text.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
