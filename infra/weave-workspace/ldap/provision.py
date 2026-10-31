#!/usr/bin/env python3
"""Apply standard LDIF without resetting credentials, identities or revoked access."""
from __future__ import annotations

import argparse
import base64
import fcntl
import json
import os
from pathlib import Path
import re
import shutil
import stat
import subprocess
import sys
import tempfile
import uuid

SOURCE = Path(__file__).resolve().parent
TEMPLATE_SUFFIX = 'dc=weave,dc=test'
AUTOMATION = {'weave-test-a': 'ldap-member-a-password',
              'weave-test-b': 'ldap-member-b-password', 'keycloak': 'ldap-bind-password'}


class ProvisionError(ValueError):
    """A support-safe failure category; never include LDAP output or secret bytes."""


def safe_path(path: Path, *, private: bool = False) -> Path:
    path = path.absolute()
    if any(p.is_symlink() for p in (path, *path.parents)):
        raise ProvisionError('ldap-path-symlink')
    if path.exists() and private:
        info = path.stat()
        if not (stat.S_ISREG(info.st_mode) or stat.S_ISDIR(info.st_mode)):
            raise ProvisionError('ldap-private-path-type')
        if info.st_uid != os.getuid() or stat.S_IMODE(info.st_mode) & 0o077:
            raise ProvisionError('ldap-private-path-permissions')
    return path


def password(value: bytes) -> bytes:
    if not 8 <= len(value) <= 4096 or any(x in value for x in (b'\0', b'\n', b'\r')):
        raise ProvisionError('ldap-initial-password-invalid')
    return value


def read_password(path: Path) -> bytes:
    path = safe_path(path, private=True)
    if not path.is_file():
        raise ProvisionError('ldap-initial-password-required')
    return password(path.read_bytes())


def call(command: list[str], data: bytes | None = None, *, missing: bool = False) -> bytes | None:
    result = subprocess.run(command, input=data, stdout=subprocess.PIPE, stderr=subprocess.PIPE,
                            timeout=30, check=False)
    if missing and result.returncode == 32:
        return None
    if result.returncode:
        raise ProvisionError('ldap-provider-operation-failed')
    return result.stdout


def attributes(record: bytes) -> dict[str, list[str]]:
    result: dict[str, list[str]] = {}
    for line in record.decode('utf-8').splitlines():
        if not line or line.startswith('#'):
            continue
        key, separator, value = line.partition(':')
        if not separator or line.startswith(' '):
            raise ProvisionError('ldap-unexpected-ldif')
        if value.startswith(':'):
            value = base64.b64decode(value[1:].strip(), validate=True).decode('utf-8')
        else:
            value = value.lstrip(' ')
        result.setdefault(key.lower(), []).append(value)
    return result


def suffix(domain: str) -> str:
    if not re.fullmatch(r'[a-z0-9]+(?:[a-z0-9-]*[a-z0-9])?(?:\.[a-z0-9]+(?:[a-z0-9-]*[a-z0-9])?)+', domain):
        raise ProvisionError('ldap-directory-domain-invalid')
    return ','.join('dc=' + label for label in domain.split('.'))


def records(name: str, domain: str) -> list[bytes]:
    text = (SOURCE / name).read_text().replace(TEMPLATE_SUFFIX, suffix(domain))
    text = text.replace('dc: weave\n', 'dc: ' + domain.split('.')[0] + '\n')
    text = text.replace('@weave.test', '@' + domain)
    result = [block.strip('\n').encode() + b'\n' for block in re.split(r'\n\s*\n', text)
              if any(line and not line.startswith('#') for line in block.splitlines())]
    if any({'userpassword', 'entryuuid'} & attributes(value).keys() for value in result):
        raise ProvisionError('ldap-source-must-not-contain-passwords-or-generated-uuids')
    return result


class Directory:
    def __init__(self, uri: str):
        if not uri.startswith('ldapi://') or '\n' in uri:
            raise ProvisionError('ldap-local-external-authority-required')
        self.auth = ['-Q', '-Y', 'EXTERNAL', '-H', uri]

    def read(self, dn: str) -> dict[str, list[str]] | None:
        value = call(['ldapsearch', '-LLL', '-o', 'ldif-wrap=no', *self.auth, '-b', dn,
                      '-s', 'base', '(objectClass=*)', 'entryUUID', 'uid', 'description',
                      'objectClass', 'member', 'entryCSN', 'pwdAccountLockedTime'], missing=True)
        return None if value is None else attributes(value)

    def by_uuid(self, identity: str, base: str) -> dict[str, list[str]] | None:
        identity = str(uuid.UUID(identity))
        raw = call(['ldapsearch', '-LLL', '-o', 'ldif-wrap=no', *self.auth, '-b', base, '-s', 'sub',
                    '(entryUUID=' + identity + ')', 'entryUUID', 'uid', 'description', 'objectClass',
                    'member', 'entryCSN', 'pwdAccountLockedTime'])
        found = attributes(raw)
        if not found.get('dn'):
            return None
        if len(found['dn']) != 1 or found.get('entryuuid') != [identity]:
            raise ProvisionError('ldap-immutable-identity-ambiguous')
        return found

    def add(self, data: bytes) -> None:
        call(['ldapadd', *self.auth], data)

    def modify(self, data: bytes, *, assertion: str | None = None) -> None:
        control = ['-e', '!assert=' + assertion] if assertion else []
        call(['ldapmodify', *self.auth, *control], data)


def save(path: Path, state: dict) -> None:
    fd, name = tempfile.mkstemp(prefix='.state-', dir=path.parent)
    try:
        with os.fdopen(fd, 'w') as stream:
            json.dump(state, stream, sort_keys=True)
            stream.flush()
            os.fsync(stream.fileno())
        os.replace(name, path)
        descriptor = os.open(path.parent, os.O_RDONLY)
        try:
            os.fsync(descriptor)
        finally:
            os.close(descriptor)
    finally:
        Path(name).unlink(missing_ok=True)


def hashed(value: bytes, directory: Path) -> bytes:
    executable = shutil.which('slappasswd')
    if executable is None:
        raise ProvisionError('ldap-password-tool-unavailable')
    module = Path(executable).resolve().parents[1] / 'lib/ldap'
    output = call([executable, '-o', 'module-path=' + str(module),
                   '-o', 'module-load=argon2 m=65536 t=3 p=1', '-h', '{ARGON2}',
                   '-T', '/dev/stdin'], password(value))
    value = output.strip()
    if not value.startswith(b'{ARGON2}$argon2id$') or b'\n' in value:
        raise ProvisionError('ldap-supported-password-hash-required')
    return base64.b64encode(value)


def apply_entry(directory: Directory, raw: bytes, state: dict, state_path: Path,
                secret: bytes | None = None) -> dict[str, list[str]]:
    fields = attributes(raw)
    dn = fields['dn'][0]
    saved = state['entries'].get(dn)
    current = directory.read(dn)
    if current is not None and saved is None:
        raise ProvisionError('ldap-existing-entry-requires-explicit-migration')
    if saved is not None and saved.get('uuid'):
        if 'inetOrgPerson' in fields.get('objectclass', []):
            current = directory.by_uuid(saved['uuid'], 'ou=people,' + suffix(state['domain']))
        if current is None or current.get('entryuuid') != [saved['uuid']]:
            raise ProvisionError('ldap-retained-entry-binding-conflict')
        return current
    if saved is None:
        state['entries'][dn] = {'pending': str(uuid.uuid4())}
        save(state_path, state)
    marker = 'Weave initial provisioning ' + state['entries'][dn]['pending']
    if current is not None:
        if current.get('description') != [marker]:
            raise ProvisionError('ldap-interrupted-entry-binding-conflict')
    else:
        value = raw + ('description: ' + marker + '\n').encode()
        if secret is not None:
            if fields.get('uid') == ['keycloak']:
                value += b'objectClass: simpleSecurityObject\n'
            value += b'userPassword:: ' + hashed(secret, state_path.parent) + b'\n'
        directory.add(value)
        current = directory.read(dn)
    if current is None or len(current.get('entryuuid', [])) != 1:
        raise ProvisionError('ldap-entry-uuid-readback-required')
    state['entries'][dn] = {'uuid': current['entryuuid'][0]}
    save(state_path, state)
    return current


def provision(phase: str, environment: str, domain: str, state_root: Path,
              secret_root: Path, uri: str, personal: bytes | None = None, *, directory: Directory | None = None) -> None:
    if environment not in {'dev', 'dogfood', 'e2e'} or (phase == 'personal' and environment != 'dogfood'):
        raise ProvisionError('ldap-fixture-environment-denied')
    suffix(domain)
    state_root = safe_path(state_root, private=True)
    state_root.mkdir(mode=0o700, parents=True, exist_ok=True)
    state_path = safe_path(state_root / 'provisioning.json', private=True)
    lock = safe_path(state_root / '.provisioning.lock', private=True)
    with os.fdopen(os.open(lock, os.O_RDWR | os.O_CREAT | os.O_NOFOLLOW, 0o600), 'a') as stream:
        fcntl.flock(stream, fcntl.LOCK_EX)
        state = json.loads(state_path.read_text()) if state_path.exists() else {
            'schemaVersion': 'weave.ldap-provisioning/v1', 'domain': domain, 'entries': {}}
        if state.get('schemaVersion') != 'weave.ldap-provisioning/v1' or state.get('domain') != domain:
            raise ProvisionError('ldap-state-context-conflict')
        directory = directory or Directory(uri)
        if phase == 'automation':
            # Validate all inputs before creating the first entry. Personal input is never read.
            secrets = {uid: read_password(secret_root / filename) for uid, filename in AUTOMATION.items()}
            for name in ('00-base.ldif', '05-bind.ldif', '10-automation.ldif', '15-groups.ldif'):
                for raw in records(name, domain):
                    uid = attributes(raw).get('uid', [None])[0]
                    apply_entry(directory, raw, state, state_path, secrets.get(uid))
        elif phase == 'personal':
            personal = password(personal or b'')
            group_dn = 'cn=member,ou=groups,' + suffix(domain)
            group_binding = state['entries'].get(group_dn, {}).get('uuid')
            group = directory.read(group_dn)
            if not group_binding or group is None or group.get('entryuuid') != [group_binding]:
                raise ProvisionError('ldap-owned-member-group-required')
            entries = records('20-personal.ldif', domain)
            if len(entries) != 1 or attributes(entries[0]).get('uid') != ['massimotter']:
                raise ProvisionError('ldap-exact-personal-source-required')
            person = apply_entry(directory, entries[0], state, state_path, personal)
            if person.get('uid') != ['massimotter']:
                raise ProvisionError('ldap-personal-login-change-requires-explicit-update')
            if person is None or '000001010000Z' in person.get('pwdaccountlockedtime', []):
                raise ProvisionError('ldap-personal-administrative-lock-preserved')
            modification = records('30-personal-membership.ldif', domain)[0]
            old_member = attributes(modification)['member'][0]
            member = person['dn'][0]
            modification = modification.replace(('member: ' + old_member).encode(),
                b'member:: ' + base64.b64encode(member.encode()))
            group = directory.read(group_dn)
            if state.get('personalGrant') == 'complete':
                if member not in group.get('member', []):
                    raise ProvisionError('ldap-personal-access-revocation-preserved')
                return
            if member in group.get('member', []) and state.get('personalGrant') != 'pending':
                raise ProvisionError('ldap-personal-group-binding-conflict')
            if state.get('personalGrant') != 'pending':
                csn = group.get('entrycsn', [])
                if len(csn) != 1 or not re.fullmatch(r'[0-9]{14}\.[0-9]{6}Z#[0-9a-f]{6}#[0-9a-f]{3}#[0-9a-f]{6}', csn[0]):
                    raise ProvisionError('ldap-group-version-required')
                state['personalGrant'] = 'pending'
                state['personalGrantCSN'] = csn[0]
                save(state_path, state)
            if member not in group.get('member', []):
                if group.get('entrycsn') != [state.get('personalGrantCSN')]:
                    raise ProvisionError('ldap-interrupted-grant-change-preserved')
                directory.modify(modification, assertion='(entryCSN=' + state['personalGrantCSN'] + ')')
            if member not in directory.read(group_dn).get('member', []):
                raise ProvisionError('ldap-personal-membership-readback-required')
            state['personalGrant'] = 'complete'
            save(state_path, state)
        else:
            raise ProvisionError('ldap-provisioning-phase-invalid')


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('phase', choices=('automation', 'personal'))
    parser.add_argument('--environment', required=True, choices=('dev', 'dogfood', 'e2e', 'prod'))
    parser.add_argument('--domain', required=True)
    parser.add_argument('--state-root', type=Path, default=Path('/var/lib/ldap/private/weave'))
    parser.add_argument('--secret-root', type=Path, default=Path('/run/secrets'))
    parser.add_argument('--uri', default='ldapi://%2Frun%2Fldap%2Fldapi')
    args = parser.parse_args()
    try:
        personal = sys.stdin.buffer.read(4097) if args.phase == 'personal' else None
        provision(args.phase, args.environment, args.domain, args.state_root,
                  args.secret_root, args.uri, personal)
    except ProvisionError as failure:
        print('LDAP_PROVISION_RESULT status=blocked reason=' + str(failure), file=sys.stderr)
        return 1
    except (OSError, ValueError, subprocess.TimeoutExpired):
        # Provider details may contain password hashes or directory profile data.
        print('LDAP_PROVISION_RESULT status=blocked reason=private-input-or-provider-unavailable', file=sys.stderr)
        return 1
    print('LDAP_PROVISION_RESULT status=passed phase=' + args.phase + ' credentials=preserved')
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
