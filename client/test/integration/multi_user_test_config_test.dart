import 'package:flutter_test/flutter_test.dart';

import '../../integration_test/helpers/multi_user_test_config.dart';
import '../../integration_test/helpers/test_config.dart';

final _common = TestConfig(
  baseUrl: Uri.parse('https://api.weave.test/api'),
  username: '',
  password: '',
  issuerUrl: Uri.parse('https://auth.weave.test/realms/weave'),
  clientId: 'weave-app',
  matrixHomeserverUrl: Uri.parse('https://api.weave.test'),
  nextcloudBaseUrl: Uri.parse('https://files.weave.test'),
  backendApiBaseUrl: Uri.parse('https://api.weave.test/api'),
  offlineContractOnly: false,
);

void main() {
  test('accepts three distinct disposable identities and hashes evidence', () {
    final configuration = MultiUserTestConfig(
      common: _common,
      runId: 'run-42',
      runIndex: 2,
      author: const CollaborationActorCredentials(
        username: 'author-42',
        password: 'secret-a',
      ),
      collaborator: const CollaborationActorCredentials(
        username: 'collaborator-42',
        password: 'secret-b',
      ),
      outsider: const CollaborationActorCredentials(
        username: 'outsider-42',
        password: 'secret-c',
      ),
      missingCapabilityVerified: true,
      expiredTokenVerified: true,
      revokedSessionVerified: true,
    );

    configuration.requireReady();

    expect(configuration.runHash, hasLength(16));
    expect(
      configuration.runHash,
      MultiUserTestConfig(
        common: _common,
        runId: configuration.runId,
        runIndex: 1,
        author: configuration.author,
        collaborator: configuration.collaborator,
        outsider: configuration.outsider,
        missingCapabilityVerified: configuration.missingCapabilityVerified,
        expiredTokenVerified: configuration.expiredTokenVerified,
        revokedSessionVerified: configuration.revokedSessionVerified,
      ).runHash,
      reason: 'Repeated passes must bind to one stable run hash.',
    );
    expect(
      CollaborationActorRole.values.map(configuration.actorHash).toSet(),
      hasLength(3),
    );
    expect(
      configuration.actorConfig(CollaborationActorRole.author).username,
      'author-42',
    );
    expect(configuration.missingCapabilityVerified, isTrue);
    expect(configuration.expiredTokenVerified, isTrue);
    expect(configuration.revokedSessionVerified, isTrue);
  });

  test('fails closed when identities are reused or run input is unsafe', () {
    final configuration = MultiUserTestConfig(
      common: _common,
      runId: 'unsafe/run?id=42',
      runIndex: 0,
      author: const CollaborationActorCredentials(
        username: 'same-user',
        password: 'secret-a',
      ),
      collaborator: const CollaborationActorCredentials(
        username: 'same-user',
        password: 'secret-b',
      ),
      outsider: const CollaborationActorCredentials(username: '', password: ''),
      missingCapabilityVerified: false,
      expiredTokenVerified: false,
      revokedSessionVerified: false,
    );

    expect(configuration.requireReady, throwsStateError);
  });
}
