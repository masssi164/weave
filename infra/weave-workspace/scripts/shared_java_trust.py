#!/usr/bin/env python3
"""Build one certificate-only Java truststore without changing any CA or host trust."""
from __future__ import annotations

import argparse
import fcntl
import json
import os
import re
import shutil
import subprocess
import tempfile
from datetime import datetime, timezone
from pathlib import Path

from cryptography import x509
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.serialization import pkcs12


class TrustError(ValueError):
    """A safe category; input bytes and exception payloads are never logged."""


def regular_path(path: Path) -> Path:
    path = path.absolute()
    if any(part.is_symlink() for part in (path, *path.parents)):
        raise TrustError('trust-path-must-not-be-a-symlink')
    if path.exists() and not path.is_file():
        raise TrustError('trust-path-must-be-a-regular-file')
    return path


def certificates(path: Path) -> list[x509.Certificate]:
    path = regular_path(path)
    data = path.read_bytes()
    if len(data) > 4 * 1024 * 1024 or b'PRIVATE KEY' in data:
        raise TrustError('trust-input-must-contain-only-public-certificates')
    try:
        certs = x509.load_pem_x509_certificates(data)
    except ValueError:
        raise TrustError('invalid-certificate-bundle') from None
    # Reject extra PEM objects rather than quietly ignoring a key/CSR/CRL.
    if not certs or data.count(b'-----BEGIN ') != len(certs):
        raise TrustError('trust-input-must-contain-only-public-certificates')
    return certs


def fingerprint(cert: x509.Certificate) -> str:
    return cert.fingerprint(hashes.SHA256()).hex()


def trust_entries(path: Path) -> dict[str, str]:
    try:
        bundle = pkcs12.load_pkcs12(regular_path(path).read_bytes(), None)
        if bundle.key is not None or bundle.cert is not None:
            raise TrustError('truststore-must-not-contain-private-identity')
        entries = {item.friendly_name.decode('ascii'): fingerprint(item.certificate)
                   for item in bundle.additional_certs if item.friendly_name}
        if len(entries) != len(bundle.additional_certs) or not entries:
            raise TrustError('truststore-aliases-missing-or-duplicated')
        return entries
    except (ValueError, UnicodeError) as error:
        if isinstance(error, TrustError):
            raise
        raise TrustError('invalid-shared-truststore') from None


def publish_truststore(jvm_roots: Path, deployment_ca: Path, destination: Path,
                      *, enroll: bool = False) -> dict[str, object]:
    """Preserve all reviewed JVM roots; enroll only explicit, currently valid CAs.

    Existing stores must match their reviewed input exactly. A CA or root-bundle
    change is an explicit trust migration, never implicit certificate renewal.
    """
    destination = regular_path(destination)
    if not destination.parent.is_dir():
        raise TrustError('trust-directory-must-exist')
    if destination in {jvm_roots.absolute(), deployment_ca.absolute()}:
        raise TrustError('trust-output-must-not-replace-input')
    baseline = certificates(jvm_roots)
    additions = certificates(deployment_ca)
    now = datetime.now(timezone.utc)
    for cert in additions:
        try:
            if not cert.extensions.get_extension_for_class(x509.BasicConstraints).value.ca:
                raise TrustError('deployment-trust-requires-ca-not-leaf')
            usage = cert.extensions.get_extension_for_class(x509.KeyUsage).value
            if not usage.key_cert_sign:
                raise TrustError('deployment-ca-cannot-sign-certificates')
        except x509.ExtensionNotFound:
            raise TrustError('deployment-ca-constraints-required') from None
        if not cert.not_valid_before_utc <= now < cert.not_valid_after_utc:
            raise TrustError('deployment-ca-not-currently-valid')
    # One alias per actual certificate. A deployment CA already present in the
    # reviewed baseline is not duplicated.
    by_digest = {fingerprint(cert): cert for cert in baseline + additions}
    expected = {digest: digest for digest in by_digest}
    lock = regular_path(destination.with_suffix(destination.suffix + '.lock'))
    with lock.open('a') as handle:
        fcntl.flock(handle, fcntl.LOCK_EX)
        regular_path(destination)
        if destination.exists():
            if trust_entries(destination) != expected:
                raise TrustError('existing-trust-differs-explicit-migration-required')
            status = 'unchanged'
        else:
            if not enroll:
                raise TrustError('initial-ca-enrollment-required')
            data = pkcs12.serialize_java_truststore(
                [pkcs12.PKCS12Certificate(by_digest[digest], digest.encode('ascii'))
                 for digest in sorted(by_digest)], serialization.NoEncryption())
            temporary: Path | None = None
            try:
                with tempfile.NamedTemporaryFile(dir=destination.parent, delete=False) as stream:
                    temporary = Path(stream.name)
                    stream.write(data)
                    stream.flush()
                    os.fsync(stream.fileno())
                if trust_entries(temporary) != expected:
                    raise TrustError('generated-truststore-readback-mismatch')
                # Public certificates only; Java containers can read this one
                # artifact even when they use different unprivileged UIDs.
                temporary.chmod(0o644)
                os.replace(temporary, destination)
            finally:
                if temporary is not None:
                    temporary.unlink(missing_ok=True)
            status = 'created'
    return {'status': status, 'certificateCount': len(expected),
            'deploymentCaSha256': sorted({fingerprint(cert) for cert in additions}),
            'containsPrivateKeys': False}


def prepare_shared_trust(context) -> dict[str, object]:
    """Derive shared trust from the selected JDK and the existing deployment CA.

    Only public certificates are read. Existing CA, leaf and private-key files
    are never rewritten, and an unchanged truststore retains its inode/mtime.
    """
    destination = regular_path(Path(context.env['WEAVE_JAVA_TRUSTSTORE']))
    destination.parent.mkdir(parents=True, exist_ok=True)
    java_home = os.environ.get('JAVA_HOME')
    keytool = str(Path(java_home) / 'bin/keytool') if java_home else shutil.which('keytool')
    if not keytool:
        raise TrustError('jdk-keytool-required-for-bootstrap')
    try:
        result = subprocess.run(
            [keytool, '-list', '-rfc', '-cacerts', '-storepass', 'changeit'],
            check=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=60)
    except (OSError, subprocess.SubprocessError):
        raise TrustError('jdk-public-roots-unavailable') from None
    roots = re.findall(rb'-----BEGIN CERTIFICATE-----.*?-----END CERTIFICATE-----',
                       result.stdout, re.DOTALL)
    if not roots:
        raise TrustError('jdk-public-roots-unavailable')
    with tempfile.TemporaryDirectory(prefix='weave-java-roots-', dir=destination.parent) as temporary:
        baseline = Path(temporary) / 'jvm-roots.pem'
        baseline.write_bytes(b'\n'.join(roots) + b'\n')
        return publish_truststore(baseline, context.tls_root / 'ca.pem', destination,
                                 enroll=True)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--jvm-roots', required=True, type=Path)
    parser.add_argument('--deployment-ca', required=True, type=Path)
    parser.add_argument('--destination', required=True, type=Path)
    parser.add_argument('--enroll', action='store_true')
    args = parser.parse_args()
    try:
        result = publish_truststore(args.jvm_roots, args.deployment_ca,
                                    args.destination, enroll=args.enroll)
    except (TrustError, OSError) as error:
        result = {'status': 'blocked', 'reason': str(error) if isinstance(error, TrustError)
                  else 'trust-files-unavailable'}
    print(json.dumps(result, sort_keys=True))
    return 1 if result['status'] == 'blocked' else 0


if __name__ == '__main__':
    raise SystemExit(main())
