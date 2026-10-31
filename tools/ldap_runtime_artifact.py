#!/usr/bin/env python3
"""Carry the pinned LDAP image in the verified runtime bundle, without a new public package."""
from __future__ import annotations

import hashlib
import json
from pathlib import Path
import re
import subprocess
import tarfile

from build_artifacts import ArtifactError, ROOT, digest_file


def docker(arguments: list[str]) -> bytes:
    try:
        return subprocess.run(['docker', *arguments], cwd=ROOT, check=True,
                              capture_output=True, timeout=900).stdout
    except (OSError, subprocess.SubprocessError):
        raise ArtifactError('ldap-runtime-image-operation-unavailable') from None


def produce(payload: Path, source: str, platform: str, build_arguments: list[str]) -> None:
    if not re.fullmatch(r'[0-9a-f]{40}', source) or platform not in {'linux/amd64', 'linux/arm64'}:
        raise ArtifactError('ldap-runtime-build-context-invalid')
    tag = 'weave-ldap:artifact-' + source[:12] + '-' + platform.split('/')[1]
    docker(['build', '--platform', platform, *build_arguments, '--tag', tag,
            '--file', str(ROOT / 'infra/weave-workspace/ldap/Dockerfile'), str(ROOT)])
    image = docker(['image', 'inspect', tag, '--format', '{{.Id}}']).decode().strip()
    if not re.fullmatch(r'sha256:[0-9a-f]{64}', image):
        raise ArtifactError('ldap-runtime-image-id-invalid')
    archive = payload / 'ldap-image.tar'
    # Save by immutable image ID: no mutable tag is installed by consumers.
    docker(['image', 'save', '--output', str(archive), image])
    receipt = {'schemaVersion': 'weave.ldap-runtime/v1', 'sourceCommit': source,
               'platform': platform, 'imageId': image, 'archiveSha256': digest_file(archive)}
    (payload / 'ldap-image.json').write_text(json.dumps(receipt, sort_keys=True) + '\n')
    validate(payload, source, platform, digest_file(ROOT / 'specs/weave-specs.lock.json'))


def validate(payload: Path, source: str, platform: str, spec_digest: str) -> str:
    """Validate the archive and actual OCI config before giving any bytes to Docker."""
    try:
        path, metadata = payload / 'ldap-image.tar', payload / 'ldap-image.json'
        if any(p.is_symlink() for p in (path, metadata)):
            raise ValueError()
        receipt = json.loads(metadata.read_text())
        if (set(receipt) != {'schemaVersion', 'sourceCommit', 'platform', 'imageId', 'archiveSha256'}
                or receipt['schemaVersion'] != 'weave.ldap-runtime/v1'
                or receipt['sourceCommit'] != source or receipt['platform'] != platform
                or platform not in {'linux/amd64', 'linux/arm64'}
                or not re.fullmatch(r'sha256:[0-9a-f]{64}', receipt['imageId'])
                or digest_file(path) != receipt['archiveSha256']):
            raise ValueError()
        image = receipt['imageId']
        with tarfile.open(path, 'r') as archive:
            entry = archive.getmember('manifest.json')
            if not entry.isfile() or entry.size > 65536:
                raise ValueError()
            manifest = json.load(archive.extractfile(entry))
            if not isinstance(manifest, list) or len(manifest) != 1 or manifest[0].get('RepoTags'):
                raise ValueError()
            config_name = manifest[0]['Config']
            if config_name not in {image[7:] + '.json', 'blobs/sha256/' + image[7:]}:
                raise ValueError()
            entry = archive.getmember(config_name)
            if not entry.isfile() or entry.size > 1048576:
                raise ValueError()
            raw = archive.extractfile(entry).read()
            if 'sha256:' + hashlib.sha256(raw).hexdigest() != image:
                raise ValueError()
            config = json.loads(raw)
            labels = config.get('config', {}).get('Labels', {})
            if (config.get('os') != 'linux' or config.get('architecture') != platform.split('/')[1]
                    or config.get('config', {}).get('User') != '1000:0'
                    or labels.get('org.opencontainers.image.revision') != source
                    or labels.get('com.massimotter.weave.spec-digest') != spec_digest):
                raise ValueError()
        return image
    except (OSError, KeyError, TypeError, ValueError, tarfile.TarError):
        raise ArtifactError('ldap-runtime-artifact-invalid') from None


def consume(payload: Path, source: str, platform: str, spec_digest: str) -> str:
    image = validate(payload, source, platform, spec_digest)
    docker(['image', 'load', '--input', str(payload / 'ldap-image.tar')])
    try:
        installed = json.loads(docker(['image', 'inspect', image]))
        if (len(installed) != 1 or installed[0].get('Id') != image
                or installed[0].get('Architecture') != platform.split('/')[1]
                or installed[0].get('Os') != 'linux'):
            raise ValueError()
    except (ValueError, TypeError):
        raise ArtifactError('ldap-runtime-loaded-image-mismatch') from None
    return image
