import 'package:flutter_test/flutter_test.dart';
import 'package:weave/core/application_identity/domain/client_build_identity.dart';

void main() {
  group('ClientBuildIdentity', () {
    test('accepts support-safe immutable candidate metadata', () {
      final identity = ClientBuildIdentity.supportSafe(
        candidateCommit: '1111111111111111111111111111111111111111',
        version: '0.1.0',
        buildNumber: '1042',
        bundleIdentifier: 'com.massimotter.weave',
        evidenceReference: 'dogfood/1042/manifest-v1',
      );

      expect(identity.isCandidateTraceable, isTrue);
      expect(identity.buildNumber, '1042');
    });

    test('redacts unsafe or missing metadata and fails closed', () {
      final identity = ClientBuildIdentity.supportSafe(
        candidateCommit: 'not-a-commit',
        version: '0.1.0\nsecret',
        buildNumber: '',
        bundleIdentifier: 'com.massimotter.weave',
        evidenceReference: 'https://evidence.test/run?token=secret',
      );

      expect(identity.isCandidateTraceable, isFalse);
      expect(identity.candidateCommit, ClientBuildIdentity.unavailableValue);
      expect(identity.version, ClientBuildIdentity.unavailableValue);
      expect(identity.buildNumber, ClientBuildIdentity.unavailableValue);
      expect(identity.evidenceReference, ClientBuildIdentity.unavailableValue);
    });

    test('requires the exact lowercase candidate commit identity', () {
      for (final candidateCommit in <String>[
        '1111111',
        '111111111111111111111111111111111111111A',
        '11111111111111111111111111111111111111111',
      ]) {
        final identity = ClientBuildIdentity.supportSafe(
          candidateCommit: candidateCommit,
          version: '0.1.0',
          buildNumber: '1042',
          bundleIdentifier: 'com.massimotter.weave',
          evidenceReference: 'dogfood/1042/manifest-v1',
        );

        expect(
          identity.candidateCommit,
          ClientBuildIdentity.unavailableValue,
          reason: '$candidateCommit is not an immutable full commit identity.',
        );
        expect(identity.isCandidateTraceable, isFalse);
      }
    });
  });
}
