#!/usr/bin/env python3
"""Issue per-run disposable-stack TLS leaves from a locally trusted test CA.

The CA is a private, operator-provisioned macOS test fixture. Its key is never
mounted into a container; the isolated copy is removed by testApp teardown.
"""

from __future__ import annotations

import argparse
import os
import shutil
import stat
import subprocess
import sys
import tempfile
from pathlib import Path


SCRIPTS = Path(__file__).resolve().parents[2] / "infra/weave-workspace/scripts"
sys.path.insert(0, str(SCRIPTS))
from init_secrets import (  # noqa: E402
    _atomic_write,
    _certificate_public_key,
    _generate_leaf_certificate,
    _private_public_key,
)


def private_file(path: Path) -> None:
    details = path.lstat()
    if not stat.S_ISREG(details.st_mode) or stat.S_IMODE(details.st_mode) != 0o600:
        raise RuntimeError("native test CA inputs must be private regular files")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--ca-root", type=Path, required=True)
    parser.add_argument("--tls-root", type=Path, required=True)
    parser.add_argument("--domain", choices=["weave.localhost"], required=True)
    args = parser.parse_args()
    ca_root = args.ca_root.resolve(strict=True)
    root = args.tls_root.resolve()
    cert = ca_root / "ca.pem"
    key = ca_root / "ca-key.pem"
    private_file(cert)
    private_file(key)
    if _certificate_public_key(cert) != _private_public_key(key):
        raise RuntimeError("native test CA certificate and key do not match")
    trusted = subprocess.run(
        ["security", "verify-cert", "-c", str(cert), "-p", "ssl"],
        capture_output=True, timeout=15,
    )
    if trusted.returncode:
        raise RuntimeError("native test CA is not trusted by macOS for SSL")
    if root.exists() or root.is_symlink():
        raise RuntimeError("native test TLS root already exists")
    root.mkdir(parents=True, mode=0o700)
    os.chmod(root, 0o700)
    hosts = [
        args.domain,
        *[f"{name}.{args.domain}" for name in
          ("api", "auth", "mail", "matrix", "files")],
    ]
    with tempfile.TemporaryDirectory(prefix="weave-native-tls-") as temporary:
        temp = Path(temporary)
        signing_cert = temp / "ca.pem"
        signing_key = temp / "ca-key.pem"
        shutil.copyfile(cert, signing_cert)
        shutil.copyfile(key, signing_key)
        os.chmod(signing_cert, 0o600)
        os.chmod(signing_key, 0o600)
        gateway_key, gateway_cert = _generate_leaf_certificate(
            temp, signing_key, signing_cert, "gateway", hosts
        )
        mailpit_key, mailpit_cert = _generate_leaf_certificate(
            temp, signing_key, signing_cert, "mailpit", ["mailpit"]
        )
        for name, source in (
            ("ca.pem", cert),
            ("ca-key.pem", key),
            ("key.pem", gateway_key),
            ("cert.pem", gateway_cert),
            ("mailpit-key.pem", mailpit_key),
            ("mailpit-cert.pem", mailpit_cert),
        ):
            _atomic_write(root / name, source.read_bytes())
    subprocess.run(
        ["openssl", "verify", "-CAfile", str(root / "ca.pem"),
         "-verify_hostname", "auth.weave.localhost", str(root / "cert.pem")],
        check=True, capture_output=True,
    )
    print("NATIVE_TEST_TLS_RESULT status=prepared perRunLeaf=true macOsTrusted=true")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (OSError, RuntimeError, subprocess.SubprocessError) as failure:
        print(f"NATIVE_TEST_TLS_RESULT status=failed reason={type(failure).__name__}")
        raise SystemExit(1)
