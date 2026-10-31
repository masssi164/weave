#!/usr/bin/env python3
"""Real slapd/LDAPS qualification of LDIF, ACLs and retained fixture identity.

Uses synthetic private passwords only. Never authenticates as the personal user.
Run with OpenLDAP 2.6.10 tools installed, or WEAVE_LDAP_TEST_PREFIX pointing to
an extracted distribution's usr directory and its libraries on LD_LIBRARY_PATH.
"""
from __future__ import annotations

import importlib.util
import os
from pathlib import Path
import secrets
import shutil
import socket
import subprocess
import sys
import tempfile
import time
import unittest
from unittest.mock import patch

LDAP = Path(__file__).resolve().parents[1] / 'ldap'
sys.path.insert(0, str(LDAP))
from provision import Directory, ProvisionError, attributes, hashed, provision
from serve import configure
sys.path.insert(0, str(LDAP.parent / "scripts"))
from init_secrets import _random_ldap_secret


class LdapProvisionLiveTest(unittest.TestCase):
    def setUp(self):
        self.prefix = Path(os.environ.get('WEAVE_LDAP_TEST_PREFIX', '/usr'))
        for name in ('slapd', 'slaptest', 'slappasswd'):
            if not (self.prefix / 'sbin' / name).is_file():
                self.fail('Real OpenLDAP tools required; do not substitute mocks')
        self.previous_path = os.environ['PATH']
        os.environ['PATH'] = str(self.prefix / 'sbin') + ':' + str(self.prefix / 'bin') + ':' + self.previous_path
        self.addCleanup(os.environ.__setitem__, 'PATH', self.previous_path)
        self.tmp = tempfile.TemporaryDirectory(prefix='weave-ldap-')
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name).resolve()
        self.tls = self.root / 'tls'
        self.tls.mkdir(mode=0o700)
        for name in ('run', 'secrets', 'state'):
            (self.root / name).mkdir(mode=0o700)
        self.secrets = {key: _random_ldap_secret() for key in
                        ('ldap-member-a-password', 'ldap-member-b-password', 'ldap-bind-password')}
        for name, value in self.secrets.items():
            path = self.root / 'secrets' / name
            path.write_bytes(value)
            path.chmod(0o600)
        def openssl(*args):
            subprocess.run(['openssl', *args], check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        openssl('req', '-x509', '-newkey', 'rsa:2048', '-noenc', '-days', '1',
                '-keyout', str(self.tls / 'ca-key.pem'), '-out', str(self.tls / 'ca.pem'),
                '-subj', '/CN=Weave isolated test CA', '-addext', 'basicConstraints=critical,CA:TRUE')
        openssl('req', '-new', '-newkey', 'rsa:2048', '-noenc', '-keyout', str(self.tls / 'ldap-key.pem'),
                '-out', str(self.tls / 'ldap.csr'), '-subj', '/CN=localhost')
        extension = self.tls / 'extension'
        extension.write_text('subjectAltName=DNS:localhost,DNS:' + socket.gethostname() + '\nbasicConstraints=critical,CA:FALSE\nextendedKeyUsage=serverAuth\n')
        openssl('x509', '-req', '-in', str(self.tls / 'ldap.csr'), '-CA', str(self.tls / 'ca.pem'),
                '-CAkey', str(self.tls / 'ca-key.pem'), '-CAcreateserial', '-days', '1',
                '-extfile', str(extension), '-out', str(self.tls / 'ldap-cert.pem'))
        # This execution environment denies AF_UNIX sockets. Use a private,
        # test-only root credential over real verified LDAPS; production keeps
        # local SASL/EXTERNAL only and has no root password or TCP operator flag.
        operator_input = self.root / 'operator-input'
        operator_input.write_bytes(secrets.token_urlsafe(24).encode())
        operator_input.chmod(0o600)
        import base64
        operator_hash = base64.b64decode(hashed(operator_input.read_bytes(), self.root)).decode()
        operator_dn = 'cn=isolated-operator,dc=weave,dc=test'
        template = self.root / 'test-slapd.conf'
        text = (LDAP / 'slapd.conf').read_text()
        before, _, after = text.rpartition('rootdn "gidNumber=0+uidNumber=1000,cn=peercred,cn=external,cn=auth"')
        text = before + 'rootdn "' + operator_dn + '"\nrootpw ' + operator_hash + after
        template.write_text(text)
        template.chmod(0o600)
        self.template = template
        configuration = configure('weave.test', self.root / 'data', self.root / 'config', self.root / 'run',
                                  self.tls, prefix=self.prefix,
                                  schema=self.prefix.parent / 'etc/ldap/schema', template=template)
        self.uri = 'ldapi://' + str(self.root / 'run/ldapi').replace('/', '%2F')
        with socket.socket() as selected:
            selected.bind(('127.0.0.1', 0))
            self.port = selected.getsockname()[1]
        self.daemon = subprocess.Popen([str(self.prefix / 'sbin/slapd'), '-F', str(configuration),
                                       '-h', f'ldaps://127.0.0.1:{self.port}', '-d', '0'],
                                      stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        self.addCleanup(self.stop)
        os.environ['LDAPTLS_CACERT'] = str(self.tls / 'ca.pem')
        self.addCleanup(os.environ.pop, 'LDAPTLS_CACERT', None)
        self.directory = object.__new__(Directory)
        self.directory.auth = ['-x', '-H', f'ldaps://localhost:{self.port}',
                               '-D', operator_dn, '-y', str(operator_input)]
        for _ in range(100):
            if self.daemon.poll() is not None:
                self.fail('Real slapd failed before qualification')
            try:
                self.directory.read('')
                break
            except ProvisionError:
                time.sleep(0.05)
        else:
            self.fail('Real slapd did not become available')

    def stop(self):
        if self.daemon.poll() is None:
            self.daemon.terminate()
            self.daemon.wait(timeout=10)

    def bootstrap(self):
        provision('automation', 'dogfood', 'weave.test', self.root / 'state', self.root / 'secrets', self.uri, directory=self.directory)

    def personal(self, environment='dogfood'):
        provision('personal', environment, 'weave.test', self.root / 'state', self.root / 'secrets',
                  self.uri, b'isolated-personal-input-never-used-for-login', directory=self.directory)

    def bound(self, uid, credential, command, *args, hostname='localhost'):
        path = self.root / 'bind-input'
        path.write_bytes(credential)
        path.chmod(0o600)
        base = 'ou=services' if uid == 'keycloak' else 'ou=people'
        env = {**os.environ, 'LDAPTLS_CACERT': str(self.tls / 'ca.pem'), 'LDAPTLS_REQCERT': 'demand'}
        return subprocess.run([command, '-x', '-H', f'ldaps://{hostname}:{self.port}',
                               '-D', f'uid={uid},{base},dc=weave,dc=test', '-y', str(path), *args],
                              env=env, capture_output=True, timeout=10)

    def test_automation_only_then_personal_preserves_all_three_uuids_and_passwords(self):
        self.bootstrap()
        self.assertIsNone(self.directory.read('uid=massimotter,ou=people,dc=weave,dc=test'))
        people = self.bound('keycloak', self.secrets['ldap-bind-password'], 'ldapsearch', '-LLL',
                            '-b', 'ou=people,dc=weave,dc=test', '(objectClass=inetOrgPerson)', 'uid')
        self.assertEqual({'weave-test-a', 'weave-test-b'}, set(attributes(people.stdout)['uid']))
        user = 'uid=weave-test-a,ou=people,dc=weave,dc=test'
        before = self.directory.read(user)['entryuuid']
        old = self.secrets['ldap-member-a-password']
        (self.root / 'secrets/ldap-member-a-password').write_bytes(secrets.token_urlsafe(24).encode())
        self.bootstrap()
        self.assertEqual(before, self.directory.read(user)['entryuuid'])
        self.assertEqual(0, self.bound('weave-test-a', old, 'ldapwhoami').returncode)
        self.personal()
        person = 'uid=massimotter,ou=people,dc=weave,dc=test'
        identity = self.directory.read(person)['entryuuid']
        people = self.bound('keycloak', self.secrets['ldap-bind-password'], 'ldapsearch', '-LLL',
                            '-b', 'ou=people,dc=weave,dc=test', '(objectClass=inetOrgPerson)', 'uid')
        self.assertEqual({'weave-test-a', 'weave-test-b', 'massimotter'}, set(attributes(people.stdout)['uid']))
        self.personal()
        self.bootstrap()
        self.assertEqual(identity, self.directory.read(person)['entryuuid'])
        self.assertIn(person, self.directory.read('cn=member,ou=groups,dc=weave,dc=test')['member'])

    def test_interrupted_applied_then_revoked_personal_membership_is_never_regranted(self):
        self.bootstrap()
        import provision as implementation
        original = implementation.save
        def interrupted(path, state):
            if state.get('personalGrant') == 'complete':
                raise OSError('simulated local commit interruption')
            return original(path, state)
        with patch('provision.save', side_effect=interrupted):
            with self.assertRaises(OSError):
                self.personal()
        dn = 'cn=member,ou=groups,dc=weave,dc=test'
        self.directory.modify(('dn: '+dn+'\nchangetype: modify\ndelete: member\nmember: uid=massimotter,ou=people,dc=weave,dc=test\n').encode())
        with self.assertRaises(ProvisionError):
            self.personal()
        self.assertNotIn('uid=massimotter,ou=people,dc=weave,dc=test', self.directory.read(dn)['member'])

    def test_interrupted_owned_empty_initial_database_resumes_without_replacement(self):
        import serve
        original = serve.call
        def interrupted(command, *args, **kwargs):
            if command[0].endswith('/slaptest'):
                raise OSError('simulated first configuration interruption')
            return original(command, *args, **kwargs)
        options = dict(prefix=self.prefix, schema=self.prefix.parent / 'etc/ldap/schema', template=self.template)
        data, config, runtime = (self.root / ('resume-' + value) for value in ('data', 'config', 'run'))
        with patch('serve.call', side_effect=interrupted):
            with self.assertRaises(OSError):
                configure('weave.test', data, config, runtime, self.tls, **options)
        self.assertTrue((data / 'data.mdb').is_file())
        before = (data / 'data.mdb').stat().st_ino
        live = configure('weave.test', data, config, runtime, self.tls, **options)
        self.assertTrue((live / 'cn=config.ldif').is_file())
        self.assertEqual(before, (data / 'data.mdb').stat().st_ino)
        self.assertEqual(live, configure('weave.test', data, config, runtime, self.tls, **options))

    def test_move_within_people_preserves_uuid_credentials_and_personal_membership(self):
        self.bootstrap()
        self.personal()
        self.directory.add(b'dn: ou=moved,ou=people,dc=weave,dc=test\nobjectClass: organizationalUnit\nou: moved\n')
        identities = {}
        for uid in ('weave-test-a', 'massimotter'):
            dn = 'uid=' + uid + ',ou=people,dc=weave,dc=test'
            identities[uid] = self.directory.read(dn)['entryuuid']
            self.directory.modify(('dn: '+dn+'\nchangetype: modrdn\nnewrdn: uid='+uid+
                                   '\ndeleteoldrdn: 1\nnewsuperior: ou=moved,ou=people,dc=weave,dc=test\n').encode())
        self.bootstrap()
        self.personal()
        for uid, identity in identities.items():
            self.assertIsNone(self.directory.read('uid='+uid+',ou=people,dc=weave,dc=test'))
            current = self.directory.read('uid='+uid+',ou=moved,ou=people,dc=weave,dc=test')
            self.assertEqual(identity, current['entryuuid'])
        self.assertIn('uid=massimotter,ou=moved,ou=people,dc=weave,dc=test',
                      self.directory.read('cn=member,ou=groups,dc=weave,dc=test')['member'])

    def test_bind_can_read_identity_but_never_password_hashes_or_modify(self):
        self.bootstrap()
        bind = self.secrets['ldap-bind-password']
        result = self.bound('keycloak', bind, 'ldapsearch', '-LLL', '-b', 'ou=people,dc=weave,dc=test',
                            '(uid=weave-test-a)', 'uid', 'entryUUID', 'userPassword')
        self.assertEqual(0, result.returncode)
        self.assertIn(b'uid: weave-test-a', result.stdout)
        self.assertIn(b'entryUUID:', result.stdout)
        self.assertNotIn(b'userPassword', result.stdout)
        file = self.root / 'mutation.ldif'
        file.write_text('dn: uid=weave-test-a,ou=people,dc=weave,dc=test\nchangetype: modify\nreplace: sn\nsn: denied\n')
        self.assertNotEqual(0, self.bound('keycloak', bind, 'ldapmodify', '-f', str(file)).returncode)

    def test_uid_unique_and_tls_hostname_are_enforced(self):
        self.bootstrap()
        with self.assertRaises(ProvisionError):
            self.directory.add(b'dn: cn=collision,ou=people,dc=weave,dc=test\nobjectClass: inetOrgPerson\ncn: collision\nsn: collision\nuid: weave-test-a\n')
        self.assertNotEqual(0, self.bound('weave-test-a', self.secrets['ldap-member-a-password'],
                                         'ldapwhoami', hostname='127.0.0.1').returncode)

    def test_directory_revocation_is_not_restored_by_bootstrap_or_personal_retry(self):
        self.bootstrap()
        self.personal()
        dn = 'cn=member,ou=groups,dc=weave,dc=test'
        self.directory.modify(('dn: '+dn+'\nchangetype: modify\ndelete: member\nmember: uid=massimotter,ou=people,dc=weave,dc=test\n').encode())
        self.bootstrap()
        with self.assertRaises(ProvisionError):
            self.personal()
        self.assertNotIn('uid=massimotter,ou=people,dc=weave,dc=test', self.directory.read(dn)['member'])

    def test_real_permanent_password_policy_lock_denies_bind_and_keeps_uuid(self):
        self.bootstrap()
        dn = 'uid=weave-test-a,ou=people,dc=weave,dc=test'
        identity = self.directory.read(dn)['entryuuid']
        self.directory.modify(('dn: '+dn+'\nchangetype: modify\nadd: pwdAccountLockedTime\npwdAccountLockedTime: 000001010000Z\n').encode())
        self.bootstrap()
        self.assertNotEqual(0, self.bound('weave-test-a', self.secrets['ldap-member-a-password'], 'ldapwhoami').returncode)
        self.directory.modify(('dn: '+dn+'\nchangetype: modify\ndelete: pwdAccountLockedTime\n').encode())
        self.assertEqual(identity, self.directory.read(dn)['entryuuid'])
        self.assertEqual(0, self.bound('weave-test-a', self.secrets['ldap-member-a-password'], 'ldapwhoami').returncode)

    def test_personal_is_forbidden_in_e2e_and_production_seeds_nothing(self):
        self.bootstrap()
        with self.assertRaises(ProvisionError):
            self.personal('e2e')
        with self.assertRaises(ProvisionError):
            provision('automation', 'prod', 'weave.test', self.root / 'state', self.root / 'secrets', self.uri, directory=self.directory)
        self.assertIsNone(self.directory.read('uid=massimotter,ou=people,dc=weave,dc=test'))

    def test_unowned_existing_entry_is_never_adopted_or_replaced(self):
        self.bootstrap()
        person = 'uid=massimotter,ou=people,dc=weave,dc=test'
        self.directory.add(('dn: '+person+'\nobjectClass: inetOrgPerson\nuid: massimotter\ncn: Existing\nsn: Existing\n').encode())
        identity = self.directory.read(person)['entryuuid']
        with self.assertRaises(ProvisionError):
            self.personal()
        self.assertEqual(identity, self.directory.read(person)['entryuuid'])


if __name__ == '__main__':
    unittest.main()
