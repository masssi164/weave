#!/usr/bin/env python3
"""The new LDAP leaf must never replace the retained CA or gateway identity."""
import hashlib
from pathlib import Path
import subprocess
import sys
import tempfile
from types import SimpleNamespace
import unittest

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'scripts'))
from init_secrets import _atomic_write, _ensure_ldap_tls, _generate_ca_certificate, _generate_leaf_certificate


class LdapTlsTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory(prefix='weave-ldap-leaf-')
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name).resolve()
        self.context = SimpleNamespace(tls_root=self.root)
        subprocess.run(['openssl', 'genpkey', '-algorithm', 'RSA', '-pkeyopt', 'rsa_keygen_bits:2048',
                        '-out', str(self.root / 'ca-key.pem')], check=True, capture_output=True)
        _generate_ca_certificate(self.root / 'ca-key.pem', self.root / 'ca.pem')
        with tempfile.TemporaryDirectory() as temporary:
            key, cert = _generate_leaf_certificate(Path(temporary), self.root / 'ca-key.pem',
                                                    self.root / 'ca.pem', 'gateway', ['weave.test'])
            _atomic_write(self.root / 'key.pem', key.read_bytes())
            _atomic_write(self.root / 'cert.pem', cert.read_bytes())
        for path in self.root.glob('*.pem'):
            path.chmod(0o600)

    def identities(self):
        return {path.name: (hashlib.sha256(path.read_bytes()).hexdigest(), path.stat().st_ino, path.stat().st_mtime_ns)
                for path in self.root.glob('*.pem')}

    def test_new_ldap_leaf_and_second_bootstrap_preserve_exact_ca_and_gateway(self):
        original = self.identities()
        _ensure_ldap_tls(self.context)
        current = self.identities()
        self.assertEqual(original, {key: current[key] for key in original})
        self.assertIn('ldap-cert.pem', current)
        _ensure_ldap_tls(self.context)
        self.assertEqual(current, self.identities())

    def test_wrong_ldap_hostname_fails_without_certificate_repair_or_ca_rotation(self):
        with tempfile.TemporaryDirectory() as temporary:
            key, cert = _generate_leaf_certificate(Path(temporary), self.root / 'ca-key.pem',
                                                    self.root / 'ca.pem', 'wrong', ['wrong-host'])
            _atomic_write(self.root / 'ldap-key.pem', key.read_bytes())
            _atomic_write(self.root / 'ldap-cert.pem', cert.read_bytes())
        original = self.identities()
        with self.assertRaises(subprocess.CalledProcessError):
            _ensure_ldap_tls(self.context)
        self.assertEqual(original, self.identities())


if __name__ == '__main__':
    unittest.main()
