#!/usr/bin/env python3
"""Resolve the OpenSSL runtime required by Weave's Ed25519 contracts.

macOS still ships LibreSSL as ``/usr/bin/openssl`` while Homebrew exposes a
current OpenSSL earlier in an interactive shell.  CI login shells can reverse
that ordering.  Resolve and capability-probe the executable once so key
generation, receipt signing, verification, and supervisor installation all
use the same implementation.
"""

from __future__ import annotations

import os
import shutil
import subprocess
from pathlib import Path


class CryptoRuntimeError(RuntimeError):
    pass


def resolve_openssl() -> str:
    candidates = [
        Path("/opt/homebrew/bin/openssl"),
        Path("/usr/local/opt/openssl@3/bin/openssl"),
        Path("/usr/local/bin/openssl"),
        Path("/usr/bin/openssl"),
    ]
    discovered = shutil.which("openssl")
    if discovered:
        candidates.append(Path(discovered))

    seen: set[Path] = set()
    for candidate in candidates:
        if candidate in seen or not candidate.is_file() or not os.access(candidate, os.X_OK):
            continue
        seen.add(candidate)
        probe = subprocess.run(
            [str(candidate), "genpkey", "-algorithm", "ED25519"],
            check=False,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
        )
        if probe.returncode == 0 and probe.stdout.startswith(b"-----BEGIN PRIVATE KEY-----"):
            return str(candidate)
    raise CryptoRuntimeError("OpenSSL with Ed25519 genpkey support is required")


OPENSSL = resolve_openssl()
