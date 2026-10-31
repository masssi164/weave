#!/usr/bin/env python3
"""Run the managed OpenLDAP dependency with persistent configuration and data."""
from __future__ import annotations

import hashlib
import fcntl
import json
import os
from pathlib import Path
import signal
import subprocess
import sys
import tempfile
import time

from provision import Directory, ProvisionError, SOURCE, call, provision, safe_path, save, suffix


def configure(domain: str, data: Path, configuration: Path, runtime: Path, tls: Path, **options) -> Path:
    safe_path(configuration, private=True).mkdir(parents=True, exist_ok=True, mode=0o700)
    lock = safe_path(configuration / '.initialization.lock', private=True)
    with os.fdopen(os.open(lock, os.O_RDWR | os.O_CREAT | os.O_NOFOLLOW, 0o600), 'a') as stream:
        fcntl.flock(stream, fcntl.LOCK_EX)
        return _configure(domain, data, configuration, runtime, tls, **options)


def _configure(domain: str, data: Path, configuration: Path, runtime: Path, tls: Path,
              *, prefix: Path = Path('/usr'), schema: Path = Path('/etc/ldap/schema'),
              template: Path = SOURCE / 'slapd.conf') -> Path:
    base = suffix(domain)
    for directory in (data, configuration, runtime):
        safe_path(directory, private=True).mkdir(parents=True, exist_ok=True, mode=0o700)
    for name in ('ca.pem', 'ldap-cert.pem', 'ldap-key.pem'):
        if not safe_path(tls / name).is_file():
            raise ProvisionError('ldap-existing-tls-material-required')
    public = template.read_text()
    public = public.replace('dc=weave,dc=test', base)
    public = public.replace('gidNumber=0+uidNumber=1000', f'gidNumber={os.getgid()}+uidNumber={os.getuid()}')
    for source, target in (('/etc/ldap/schema', schema), ('/usr/lib/ldap', prefix / 'lib/ldap'),
                           ('/run/ldap', runtime), ('/run/certs', tls), ('/var/lib/ldap/data', data)):
        public = public.replace(source, str(target))
    digest = hashlib.sha256(public.encode()).hexdigest()
    live = configuration / 'live'
    marker = live / 'weave-source.json'
    if live.exists():
        if (not safe_path(marker, private=True).is_file()
                or json.loads(marker.read_text()) != {'configurationSha256': digest}):
            raise ProvisionError('ldap-existing-config-requires-explicit-migration')
        return live
    # Publish only a complete first generation; never replace a live configuration.
    with tempfile.TemporaryDirectory(prefix='.configuration-', dir=configuration) as tmp:
        staging = Path(tmp) / 'live'
        staging.mkdir(mode=0o700)
        source = Path(tmp) / 'slapd.conf'
        source.write_text(public)
        source.chmod(0o600)
        ownership = configuration / 'initial-generation.json'
        expected = {'schemaVersion': 'weave.ldap-initial-generation/v1',
                    'configurationSha256': digest, 'dataDirectory': str(data.absolute())}
        if ownership.exists():
            if not safe_path(ownership, private=True).is_file() or json.loads(ownership.read_text()) != expected:
                raise ProvisionError('ldap-initial-generation-binding-conflict')
        else:
            if any(data.iterdir()):
                raise ProvisionError('ldap-existing-data-without-config-migration-required')
            save(ownership, expected)
        if any(data.iterdir()):
            # No serving process exists yet. Only an empty, owned first MDB
            # may be resumed after interruption; never adopt existing entries.
            entries = call([str(prefix / 'sbin/slapcat'), '-f', str(source), '-b', base])
            if entries.strip():
                raise ProvisionError('ldap-unpublished-initial-generation-has-entries')
        else:
            call([str(prefix / 'sbin/slapadd'), '-f', str(source), '-b', base, '-l', '/dev/null'])
        call([str(prefix / 'sbin/slaptest'), '-f', str(source), '-F', str(staging)])
        marker = staging / 'weave-source.json'
        save(marker, {'configurationSha256': digest})
        os.rename(staging, live)
        descriptor = os.open(configuration, os.O_RDONLY)
        try:
            os.fsync(descriptor)
        finally:
            os.close(descriptor)
    return live


def main() -> int:
    os.umask(0o077)
    domain = os.environ['WEAVE_LDAP_DOMAIN']
    environment = os.environ['WEAVE_ENVIRONMENT']
    if environment not in {'dev', 'dogfood', 'e2e'}:
        raise ProvisionError('managed-demo-directory-environment-denied')
    configuration = configure(domain, Path('/var/lib/ldap/private/data'), Path('/etc/ldap/slapd.d/private'),
                              Path('/run/ldap'), Path('/run/certs'))
    uri = 'ldapi://%2Frun%2Fldap%2Fldapi'
    daemon = subprocess.Popen(['slapd', '-F', str(configuration), '-h', uri + ' ldaps://0.0.0.0:1636', '-d', '0'],
                               stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    def stop(signum, frame):
        daemon.terminate()
    signal.signal(signal.SIGTERM, stop)
    signal.signal(signal.SIGINT, stop)
    try:
        for _ in range(100):
            if daemon.poll() is not None:
                raise ProvisionError('ldap-startup-failed')
            try:
                Directory(uri).read('')
                break
            except ProvisionError:
                time.sleep(0.1)
        else:
            raise ProvisionError('ldap-startup-timeout')
        provision('automation', environment, domain, Path('/var/lib/ldap/private/weave'), Path('/run/secrets'), uri)
        Path('/run/ldap/ready').touch(mode=0o600)
        print('LDAP_READY personalAccount=not-provisioned-by-startup', flush=True)
        return daemon.wait()
    finally:
        if daemon.poll() is None:
            daemon.terminate()
            try:
                daemon.wait(timeout=10)
            except subprocess.TimeoutExpired:
                daemon.kill()
                daemon.wait()


if __name__ == '__main__':
    try:
        raise SystemExit(main())
    except (ProvisionError, OSError, ValueError, subprocess.TimeoutExpired):
        print('LDAP_READY status=blocked', file=sys.stderr)
        raise SystemExit(1)
