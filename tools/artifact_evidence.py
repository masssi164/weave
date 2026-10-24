"""Pure checks for the composition carried by trusted runtime evidence.

The artifact consumer authenticates producers and verifies bytes before execution.
These checks bind the resulting receipts to one composition without downloading
multi-gigabyte build artifacts into the promotion verifier.
"""
from __future__ import annotations

import hashlib
import json
import re

SHA = re.compile(r'[0-9a-f]{40}')
DIGEST = re.compile(r'sha256:[0-9a-f]{64}')
COMPONENTS = {'runtime-linux-amd64': ('linux/amd64', 'release'),
              'ios-simulator': ('aarch64-apple-ios-sim', 'debug'),
              'ios-device': ('aarch64-apple-ios', 'profile')}


def matches(pattern, value) -> bool:
    return isinstance(value, str) and pattern.fullmatch(value) is not None


def composition_passed(value: object, sha: str, tree: str, spec: str, scenario: str | None = None) -> bool:
    if not isinstance(value, dict):
        return False
    if (value.get('schemaVersion') != 'weave.candidate-artifacts/v1'
            or value.get('candidateCommit') != sha or value.get('sourceTree') != tree
            or value.get('specificationCommit') != spec
            or any(not matches(SHA, item) for item in (sha, tree, spec))
            or not matches(DIGEST, value.get('scenarioDigest'))
            or (scenario is not None and value['scenarioDigest'] != 'sha256:' + scenario)):
        return False
    expected_digest = 'sha256:' + hashlib.sha256(json.dumps(
        {key: item for key, item in value.items() if key != 'compositionDigest'},
        sort_keys=True, separators=(',', ':'), ensure_ascii=False).encode()).hexdigest()
    if value.get('compositionDigest') != expected_digest:
        return False
    entries = value.get('components')
    if not isinstance(entries, dict) or set(entries) != set(COMPONENTS):
        return False
    for name, (target, mode) in COMPONENTS.items():
        entry = entries[name]
        if (not isinstance(entry, dict) or entry.get('target') != target or entry.get('buildMode') != mode
                or not matches(DIGEST, entry.get('inputDigest')) or not matches(DIGEST, entry.get('payloadSha256'))
                or not matches(SHA, entry.get('sourceCommit')) or not matches(SHA, entry.get('sourceTree'))
                or not isinstance(entry.get('buildNumber'), str) or not re.fullmatch(r'[1-9][0-9]*', entry['buildNumber'])
                or entry.get('artifactName') != f"weave-build-{name}-{entry['inputDigest'][7:]}"
                or any(type(entry.get(key)) is not int or entry[key] < 1
                       for key in ('artifactId', 'producerRunId', 'producerRunAttempt'))):
            return False
    return True


def component_matches(value: object, composition: dict, name: str) -> bool:
    entry = composition.get('components', {}).get(name)
    return (isinstance(value, dict) and isinstance(entry, dict) and value.get('component') == name
            and all(value.get(key) == entry.get(key) for key in (
                'sourceCommit', 'sourceTree', 'inputDigest', 'target', 'buildMode', 'buildNumber', 'payloadSha256')))
