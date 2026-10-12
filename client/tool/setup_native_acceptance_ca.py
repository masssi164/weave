#!/usr/bin/env python3
"""Create and optionally trust the dedicated macOS native acceptance CA once."""

from __future__ import annotations

import argparse
import os
import stat
import subprocess
from pathlib import Path


DEFAULT_ROOT = Path.home() / ".local/share/weave/native-acceptance-ca"


def command(arguments: list[str], *, timeout: int = 30) -> None:
    subprocess.run(arguments, check=True, capture_output=True, timeout=timeout)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, default=DEFAULT_ROOT)
    parser.add_argument("--trust", action="store_true")
    args = parser.parse_args()
    root = args.root.expanduser().resolve()
    cert = root / "ca.pem"
    key = root / "ca-key.pem"
    if root.exists() and (not cert.is_file() or not key.is_file()):
        raise RuntimeError("native acceptance CA directory is incomplete")
    if not root.exists():
        root.mkdir(parents=True, mode=0o700)
        os.chmod(root, 0o700)
        command([
            "openssl", "genpkey", "-algorithm", "RSA",
            "-pkeyopt", "rsa_keygen_bits:3072", "-out", str(key),
        ])
        os.chmod(key, 0o600)
        command([
            "openssl", "req", "-x509", "-new", "-key", str(key),
            "-sha256", "-days", "825",
            "-subj", "/CN=Weave Native Acceptance CA",
            "-addext", "basicConstraints=critical,CA:TRUE",
            "-addext", "keyUsage=critical,keyCertSign,cRLSign",
            "-addext", "subjectKeyIdentifier=hash",
            "-out", str(cert),
        ])
        os.chmod(cert, 0o600)
    for path in (cert, key):
        details = path.lstat()
        if not stat.S_ISREG(details.st_mode) or stat.S_IMODE(details.st_mode) != 0o600:
            raise RuntimeError("native acceptance CA files must be private")
    cert_key = subprocess.check_output(
        ["openssl", "x509", "-in", str(cert), "-pubkey", "-noout"]
    )
    private_key = subprocess.check_output(
        ["openssl", "pkey", "-in", str(key), "-pubout"]
    )
    if cert_key != private_key:
        raise RuntimeError("native acceptance CA key does not match certificate")
    check = subprocess.run(
        ["security", "verify-cert", "-c", str(cert), "-p", "ssl"],
        capture_output=True, timeout=15,
    )
    if args.trust and check.returncode:
        keychain = Path.home() / "Library/Keychains/login.keychain-db"
        command([
            "security", "add-trusted-cert", "-r", "trustRoot", "-p", "ssl",
            "-k", str(keychain), str(cert),
        ], timeout=180)
        check = subprocess.run(
            ["security", "verify-cert", "-c", str(cert), "-p", "ssl"],
            capture_output=True, timeout=15,
        )
    print(
        "NATIVE_ACCEPTANCE_CA_RESULT status="
        + ("trusted" if check.returncode == 0 else "created-untrusted")
    )
    return 0 if check.returncode == 0 or not args.trust else 1


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (OSError, RuntimeError, subprocess.SubprocessError) as failure:
        print(f"NATIVE_ACCEPTANCE_CA_RESULT status=failed reason={type(failure).__name__}")
        raise SystemExit(1)
