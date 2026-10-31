#!/usr/bin/env python3
"""Reject untrusted LDAP image payloads before Docker receives them."""
import hashlib
import io
import json
from pathlib import Path
import tarfile
import tempfile
import unittest

from build_artifacts import ArtifactError, digest_file
from ldap_runtime_artifact import validate

SOURCE = 'a' * 40
SPEC = 'sha256:' + 'b' * 64


class LdapRuntimeArtifactTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)

    def archive(self, *, source=SOURCE, architecture='arm64', user='1000:0', spec=SPEC, tagged=False):
        config = json.dumps({'architecture': architecture, 'os': 'linux', 'config': {
            'User': user, 'Labels': {'org.opencontainers.image.revision': source,
                                    'com.massimotter.weave.spec-digest': spec}}}).encode()
        image = 'sha256:' + hashlib.sha256(config).hexdigest()
        name = image[7:] + '.json'
        with tarfile.open(self.root / 'ldap-image.tar', 'w') as archive:
            for path, value in ((name, config), ('manifest.json', json.dumps([
                {'Config': name, 'RepoTags': ['foreign:latest'] if tagged else None, 'Layers': []}]).encode())):
                entry = tarfile.TarInfo(path)
                entry.size = len(value)
                archive.addfile(entry, io.BytesIO(value))
        metadata = {'schemaVersion': 'weave.ldap-runtime/v1', 'sourceCommit': SOURCE,
                    'platform': 'linux/arm64', 'imageId': image,
                    'archiveSha256': digest_file(self.root / 'ldap-image.tar')}
        (self.root / 'ldap-image.json').write_text(json.dumps(metadata))
        return image

    def test_exact_original_config_id_is_preserved(self):
        image = self.archive()
        self.assertEqual(image, validate(self.root, SOURCE, 'linux/arm64', SPEC))

    def test_tampering_is_rejected_before_loading(self):
        self.archive()
        with (self.root / 'ldap-image.tar').open('ab') as stream:
            stream.write(b'tamper')
        with self.assertRaises(ArtifactError):
            validate(self.root, SOURCE, 'linux/arm64', SPEC)

    def test_wrong_source_platform_spec_root_user_and_tag_are_rejected(self):
        for change in ({'source': 'c' * 40}, {'architecture': 'amd64'}, {'spec': 'sha256:' + 'c' * 64},
                       {'user': '0:0'}, {'tagged': True}):
            with self.subTest(change=change):
                self.archive(**change)
                with self.assertRaises(ArtifactError):
                    validate(self.root, SOURCE, 'linux/arm64', SPEC)

    def test_claimed_config_id_cannot_be_relabelled(self):
        self.archive()
        metadata = json.loads((self.root / 'ldap-image.json').read_text())
        metadata['imageId'] = 'sha256:' + 'c' * 64
        (self.root / 'ldap-image.json').write_text(json.dumps(metadata))
        with self.assertRaises(ArtifactError):
            validate(self.root, SOURCE, 'linux/arm64', SPEC)


if __name__ == '__main__':
    unittest.main()
