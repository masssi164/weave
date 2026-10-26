"""Offline receipt fixtures and negatives; these are never live acceptance."""
import copy
import unittest

from tools.artifact_evidence import COMPONENTS, composition_passed, component_matches
from tools.build_artifacts import composition_digest
from tools.member_test_scenario import scenario_sha256


def composition_fixture(sha='a' * 40, tree='b' * 40, spec='c' * 40):
    result = {'schemaVersion': 'weave.candidate-artifacts/v1', 'candidateCommit': sha,
              'sourceTree': tree, 'specificationCommit': spec,
              'scenarioDigest': 'sha256:' + scenario_sha256(), 'components': {}}
    for number, (name, (target, mode)) in enumerate(COMPONENTS.items(), 1):
        digest = str(number) * 64
        result['components'][name] = {
            'artifactId': 100 + number, 'artifactName': f'weave-build-{name}-{digest}',
            'producerRunId': 200 + number, 'producerRunAttempt': 1,
            'sourceCommit': 'd' * 40, 'sourceTree': 'e' * 40,
            'inputDigest': 'sha256:' + digest, 'payloadSha256': 'sha256:' + str(number + 3) * 64,
            'target': target, 'buildMode': mode, 'buildNumber': '20'}
    result['components']['runtime-linux-amd64']['runtime'] = {
        'domains': ['calendar', 'chat', 'files'], 'flavor': 'jvm'}
    result['compositionDigest'] = composition_digest(result)
    return result


def component_fixture(composition, name):
    return ({'runtime': composition['components'][name]['runtime']} if name.startswith('runtime-') else {}) | {'component': name, **{key: composition['components'][name][key] for key in (
        'sourceCommit', 'sourceTree', 'inputDigest', 'target', 'buildMode', 'buildNumber', 'payloadSha256')}}


class ArtifactEvidenceTest(unittest.TestCase):
    def test_reused_binary_keeps_producer_identity_while_candidate_changes(self):
        value = composition_fixture()
        self.assertTrue(composition_passed(value, 'a' * 40, 'b' * 40, 'c' * 40, scenario_sha256()))
        self.assertNotEqual(value['candidateCommit'], value['components']['ios-device']['sourceCommit'])
        self.assertTrue(component_matches(component_fixture(value, 'ios-device'), value, 'ios-device'))

    def test_runtime_receipt_binding_is_required_and_compared(self):
        value = composition_fixture()
        name = 'runtime-linux-amd64'
        receipt = component_fixture(value, name)
        self.assertTrue(component_matches(receipt, value, name))
        for runtime in (None, {'domains': ['files'], 'flavor': 'jvm'},
                        {'domains': ['calendar', 'chat', 'files'], 'flavor': 'native'}):
            changed = {**receipt, 'runtime': runtime}
            self.assertFalse(component_matches(changed, value, name))
        for runtime in (None, {}, {'domains': [], 'flavor': 'jvm'}, {'domains': ['files'], 'flavor': 'native'},
                        {'domains': [12], 'flavor': 'jvm'}, {'domains': [' files'], 'flavor': 'jvm'},
                        {'domains': ['Files'], 'flavor': 'jvm'}, {'domains': ['files', 'files'], 'flavor': 'jvm'},
                        {'domains': ['files', 'chat'], 'flavor': 'jvm'},
                        {'domains': ['files'], 'flavor': 'jvm', 'unexpected': True}):
            changed = copy.deepcopy(value)
            changed['components'][name]['runtime'] = runtime
            changed['compositionDigest'] = composition_digest(changed)
            self.assertFalse(composition_passed(changed, 'a' * 40, 'b' * 40, 'c' * 40))

    def test_substitution_and_incomplete_or_malformed_compositions_fail(self):
        value = composition_fixture()
        for key in value:
            changed = copy.deepcopy(value); changed.pop(key)
            self.assertFalse(composition_passed(changed, 'a' * 40, 'b' * 40, 'c' * 40))
        for key, changed_value in (('artifactId', True), ('buildNumber', '0'), ('target', 'aarch64-apple-ios-sim'),
                                   ('sourceCommit', []), ('payloadSha256', None)):
            changed = copy.deepcopy(value)
            changed['components']['ios-device'][key] = changed_value
            changed['compositionDigest'] = composition_digest(changed)
            self.assertFalse(composition_passed(changed, 'a' * 40, 'b' * 40, 'c' * 40), key)
        artifact = component_fixture(value, 'ios-device')
        for key in ('sourceCommit', 'sourceTree', 'inputDigest', 'payloadSha256', 'buildNumber'):
            changed = {**artifact, key: 'different'}
            self.assertFalse(component_matches(changed, value, 'ios-device'), key)


if __name__ == '__main__':
    unittest.main()
