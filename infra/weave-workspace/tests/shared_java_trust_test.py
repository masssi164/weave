#!/usr/bin/env python3
"""Executable certificate, PKCS12, and Java TLS regressions (no Docker required)."""
from __future__ import annotations

import shutil
import socket
import ssl
import subprocess
import sys
import tempfile
import threading
import unittest
from types import SimpleNamespace
from datetime import datetime, timedelta, timezone
from pathlib import Path

from cryptography import x509
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import rsa
from cryptography.hazmat.primitives.serialization import pkcs12
from cryptography.x509.oid import NameOID

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'scripts'))
from shared_java_trust import TrustError, fingerprint, prepare_shared_trust, publish_truststore, trust_entries


def certificate(name, *, signer=None, ca=True, expired=False):
    key = rsa.generate_private_key(public_exponent=65537, key_size=2048)
    subject = x509.Name([x509.NameAttribute(NameOID.COMMON_NAME, name)])
    now = datetime.now(timezone.utc)
    cert = (x509.CertificateBuilder().subject_name(subject)
            .issuer_name(signer[1].subject if signer else subject)
            .public_key(key.public_key()).serial_number(x509.random_serial_number())
            .not_valid_before(now - timedelta(days=2))
            .not_valid_after(now + timedelta(days=-1 if expired else 30))
            .add_extension(x509.BasicConstraints(ca=ca, path_length=None), critical=True)
            .add_extension(x509.KeyUsage(digital_signature=True, content_commitment=False,
                           key_encipherment=True, data_encipherment=False, key_agreement=False,
                           key_cert_sign=ca, crl_sign=ca, encipher_only=None, decipher_only=None), critical=True)
            .add_extension(x509.SubjectAlternativeName([x509.DNSName(name)]), critical=False)
            .sign(signer[0] if signer else key, hashes.SHA256()))
    return key, cert


def pem(cert):
    return cert.public_bytes(serialization.Encoding.PEM)


class SharedTrustTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.base_ca = certificate('approved-public-root')
        cls.local_ca = certificate('deployment-root')
        cls.other_ca = certificate('unapproved-root')

    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        self.base = self.root / 'reviewed-jvm.pem'
        self.ca = self.root / 'deployment-ca.pem'
        self.output = self.root / 'java-truststore.p12'
        self.base.write_bytes(pem(self.base_ca[1]))
        self.ca.write_bytes(pem(self.local_ca[1]))

    def publish(self, **options):
        return publish_truststore(self.base, self.ca, self.output, **options)

    def test_initial_trust_requires_explicit_enrollment(self):
        with self.assertRaisesRegex(TrustError, 'initial-ca-enrollment-required'):
            self.publish()
        self.assertFalse(self.output.exists())

    def test_preserves_both_public_and_deployment_ca(self):
        result = self.publish(enroll=True)
        self.assertEqual(result['status'], 'created')
        self.assertEqual(set(trust_entries(self.output)),
                         {fingerprint(self.base_ca[1]), fingerprint(self.local_ca[1])})
        bundle = pkcs12.load_pkcs12(self.output.read_bytes(), None)
        self.assertIsNone(bundle.key)
        self.assertIsNone(bundle.cert)
        self.assertEqual(self.output.stat().st_mode & 0o777, 0o644)

    def test_second_bootstrap_does_not_rewrite_store_or_inputs(self):
        self.publish(enroll=True)
        before = [(p.read_bytes(), p.stat().st_mtime_ns) for p in (self.output, self.base, self.ca)]
        self.assertEqual(self.publish()['status'], 'unchanged')
        self.assertEqual(before, [(p.read_bytes(), p.stat().st_mtime_ns)
                                 for p in (self.output, self.base, self.ca)])

    @unittest.skipUnless(shutil.which('keytool'), 'JDK keytool required')
    def test_bootstrap_uses_jdk_roots_and_preserves_existing_tls_and_store(self):
        tls = self.root / 'tls'
        tls.mkdir()
        (tls / 'ca.pem').write_bytes(pem(self.local_ca[1]))
        (tls / 'cert.pem').write_bytes(pem(certificate('localhost', signer=self.local_ca, ca=False)[1]))
        (tls / 'key.pem').write_bytes(b'existing-private-key-not-read-by-trust-preparation')
        before = {p: (p.read_bytes(), p.stat().st_ino, p.stat().st_mtime_ns)
                  for p in tls.iterdir()}
        output = self.root / 'generated/trust/java-truststore.p12'
        context = SimpleNamespace(env={'WEAVE_JAVA_TRUSTSTORE': str(output)}, tls_root=tls)
        result = prepare_shared_trust(context)
        self.assertEqual(result['status'], 'created')
        self.assertGreater(result['certificateCount'], 1)
        self.assertIn(fingerprint(self.local_ca[1]), trust_entries(output))
        published = (output.read_bytes(), output.stat().st_ino, output.stat().st_mtime_ns)
        self.assertEqual(prepare_shared_trust(context)['status'], 'unchanged')
        self.assertEqual(published, (output.read_bytes(), output.stat().st_ino, output.stat().st_mtime_ns))
        self.assertEqual(before, {p: (p.read_bytes(), p.stat().st_ino, p.stat().st_mtime_ns)
                                  for p in tls.iterdir()})

    def test_duplicate_ca_is_not_added_twice(self):
        self.base.write_bytes(pem(self.local_ca[1]) + pem(self.base_ca[1]))
        self.assertEqual(self.publish(enroll=True)['certificateCount'], 2)

    def test_ca_rotation_is_not_silent_even_with_enroll(self):
        self.publish(enroll=True)
        before = self.output.read_bytes()
        self.ca.write_bytes(pem(self.other_ca[1]))
        with self.assertRaisesRegex(TrustError, 'explicit-migration-required'):
            self.publish(enroll=True)
        self.assertEqual(self.output.read_bytes(), before)

    def test_public_root_changes_require_reviewed_migration(self):
        self.publish(enroll=True)
        self.base.write_bytes(pem(self.other_ca[1]))
        with self.assertRaisesRegex(TrustError, 'explicit-migration-required'):
            self.publish()

    def test_corrupt_existing_store_is_not_overwritten(self):
        self.output.write_bytes(b'not a PKCS12 truststore')
        with self.assertRaisesRegex(TrustError, 'invalid-shared-truststore'):
            self.publish(enroll=True)
        self.assertEqual(self.output.read_bytes(), b'not a PKCS12 truststore')

    def test_private_key_input_is_rejected(self):
        self.ca.write_bytes(self.local_ca[0].private_bytes(serialization.Encoding.PEM,
                           serialization.PrivateFormat.PKCS8, serialization.NoEncryption()))
        with self.assertRaisesRegex(TrustError, 'only-public-certificates'):
            self.publish(enroll=True)
        self.assertFalse(self.output.exists())

    def test_leaf_is_not_enrolled_as_ca(self):
        self.ca.write_bytes(pem(certificate('localhost', signer=self.local_ca, ca=False)[1]))
        with self.assertRaisesRegex(TrustError, 'ca-not-leaf'):
            self.publish(enroll=True)

    def test_expired_ca_is_rejected(self):
        self.ca.write_bytes(pem(certificate('expired', expired=True)[1]))
        with self.assertRaisesRegex(TrustError, 'not-currently-valid'):
            self.publish(enroll=True)

    def test_empty_ca_is_rejected(self):
        self.ca.write_bytes(b'')
        with self.assertRaises(TrustError):
            self.publish(enroll=True)

    def test_output_cannot_overwrite_input(self):
        with self.assertRaisesRegex(TrustError, 'must-not-replace-input'):
            publish_truststore(self.base, self.ca, self.ca, enroll=True)

    def test_symlink_input_is_rejected(self):
        target = self.root / 'source.pem'
        self.ca.rename(target)
        self.ca.symlink_to(target)
        with self.assertRaisesRegex(TrustError, 'symlink'):
            self.publish(enroll=True)

    def test_symlink_output_and_lock_are_rejected(self):
        for path in (self.output, self.output.with_suffix('.p12.lock')):
            with self.subTest(path=path.name):
                path.symlink_to(self.ca)
                with self.assertRaisesRegex(TrustError, 'symlink'):
                    self.publish(enroll=True)
                path.unlink()

    @unittest.skipUnless(shutil.which('java') and shutil.which('javac') and shutil.which('keytool'),
                         'JDK required for real truststore consumer and TLS tests')
    def test_real_java_uses_same_store_for_renewed_leaf_and_rejects_other_ca_and_hostname(self):
        self.publish(enroll=True)
        inspected = subprocess.run(['keytool', '-list', '-keystore', str(self.output),
                                    '-storetype', 'PKCS12', '-storepass', ''],
                                   capture_output=True, text=True, timeout=15)
        self.assertEqual(inspected.returncode, 0, inspected.stderr)
        self.assertIn('trustedCertEntry', inspected.stdout)
        source = self.root / 'TrustClient.java'
        source.write_text('''import javax.net.ssl.*;
public class TrustClient {
  public static void main(String[] args) throws Exception {
    try (SSLSocket socket = (SSLSocket) SSLSocketFactory.getDefault()
            .createSocket("localhost", Integer.parseInt(args[0]))) {
      socket.setSoTimeout(5000);
      SSLParameters parameters = socket.getSSLParameters();
      parameters.setEndpointIdentificationAlgorithm("HTTPS");
      socket.setSSLParameters(parameters);
      socket.startHandshake();
    }
  }
}
''')
        compiled = subprocess.run(['javac', str(source)], capture_output=True, timeout=15)
        self.assertEqual(compiled.returncode, 0, compiled.stderr)
        before = self.output.read_bytes()
        for signer, name, accepted in ((self.local_ca, 'localhost', True),
                                       (self.local_ca, 'localhost', True),
                                       (self.base_ca, 'localhost', True),
                                       (self.other_ca, 'localhost', False),
                                       (self.local_ca, 'wrong.example.test', False)):
            with self.subTest(signer=signer[1].subject, name=name, accepted=accepted):
                key, leaf = certificate(name, signer=signer, ca=False)
                cert_path, key_path = self.root / 'leaf.pem', self.root / 'leaf.key'
                cert_path.write_bytes(pem(leaf))
                key_path.write_bytes(key.private_bytes(serialization.Encoding.PEM,
                                     serialization.PrivateFormat.PKCS8, serialization.NoEncryption()))
                key_path.chmod(0o600)
                context = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
                context.load_cert_chain(cert_path, key_path)
                with socket.socket() as listener:
                    listener.bind(('127.0.0.1', 0))
                    listener.listen(1)
                    listener.settimeout(8)
                    def serve():
                        try:
                            connection, _ = listener.accept()
                            with connection, context.wrap_socket(connection, server_side=True):
                                pass
                        except (OSError, ssl.SSLError):
                            pass
                    server = threading.Thread(target=serve, daemon=True)
                    server.start()
                    result = subprocess.run(['java', '-Djavax.net.ssl.trustStore=' + str(self.output),
                                             '-Djavax.net.ssl.trustStoreType=PKCS12',
                                             '-cp', str(self.root), 'TrustClient',
                                             str(listener.getsockname()[1])], capture_output=True, timeout=10)
                    server.join(timeout=9)
                    self.assertFalse(server.is_alive())
                    self.assertEqual(result.returncode == 0, accepted, result.stderr.decode())
        self.assertEqual(self.output.read_bytes(), before)


if __name__ == '__main__':
    unittest.main()
