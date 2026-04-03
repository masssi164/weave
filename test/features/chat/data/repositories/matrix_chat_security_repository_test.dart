import 'package:flutter_test/flutter_test.dart';
import 'package:weave/features/chat/data/repositories/matrix_chat_security_repository.dart';
import 'package:weave/features/chat/data/services/matrix_client.dart';
import 'package:weave/features/chat/domain/entities/chat_failure.dart';
import 'package:weave/features/chat/domain/entities/chat_security_state.dart';
import 'package:weave/features/server_config/domain/entities/server_configuration.dart';
import 'package:weave/features/server_config/domain/repositories/'
    'server_configuration_repository.dart';

import '../../../../helpers/server_config_test_data.dart';

class _FakeMatrixClient implements MatrixClient {
  MatrixSecuritySnapshot snapshot = const MatrixSecuritySnapshot(
    isMatrixSignedIn: true,
    bootstrapState: MatrixSecurityBootstrapState.ready,
    accountVerificationState: MatrixAccountVerificationState.verified,
    deviceVerificationState: MatrixDeviceVerificationState.verified,
    keyBackupState: MatrixKeyBackupState.ready,
    roomEncryptionReadiness: MatrixRoomEncryptionReadiness.ready,
    secretStorageReady: true,
    crossSigningReady: true,
    hasEncryptedConversations: true,
  );

  @override
  Stream<MatrixVerificationSnapshot> get verificationUpdates =>
      const Stream<MatrixVerificationSnapshot>.empty();

  @override
  Future<String> bootstrapSecurity({
    required Uri homeserver,
    String? passphrase,
  }) async {
    return 'RECOVERY-KEY';
  }

  @override
  Future<void> acceptVerification({required Uri homeserver}) async {}

  @override
  Future<void> cancelVerification({required Uri homeserver}) async {}

  @override
  Future<void> clearSession() async {}

  @override
  Future<void> confirmSas({
    required Uri homeserver,
    required bool matches,
  }) async {}

  @override
  Future<void> connect({required Uri homeserver}) async {}

  @override
  Future<void> dismissVerificationResult({required Uri homeserver}) async {}

  @override
  Future<void> dispose() async {}

  @override
  Future<List<MatrixRoomSnapshot>> loadConversations({
    required Uri homeserver,
  }) async {
    return const <MatrixRoomSnapshot>[];
  }

  @override
  Future<MatrixSecuritySnapshot> loadSecurityState({
    required Uri homeserver,
    bool refresh = false,
  }) async {
    return snapshot;
  }

  @override
  Future<void> restoreSecurity({
    required Uri homeserver,
    required String recoveryKeyOrPassphrase,
  }) async {}

  @override
  Future<void> signOut() async {}

  @override
  Future<void> startSasVerification({required Uri homeserver}) async {}

  @override
  Future<void> unlockVerification({
    required Uri homeserver,
    required String recoveryKeyOrPassphrase,
  }) async {}

  @override
  Future<void> startVerification({required Uri homeserver}) async {}
}

class _FakeServerConfigurationRepository
    implements ServerConfigurationRepository {
  _FakeServerConfigurationRepository(this.configuration);

  ServerConfiguration? configuration;

  @override
  Future<void> clearConfiguration() async {
    configuration = null;
  }

  @override
  Future<ServerConfiguration?> loadConfiguration() async => configuration;

  @override
  Future<void> saveConfiguration(ServerConfiguration configuration) async {
    this.configuration = configuration;
  }
}

void main() {
  group('MatrixChatSecurityRepository', () {
    test('maps the Matrix security snapshot into chat-owned models', () async {
      final client = _FakeMatrixClient()
        ..snapshot = const MatrixSecuritySnapshot(
          isMatrixSignedIn: true,
          bootstrapState: MatrixSecurityBootstrapState.recoveryRequired,
          accountVerificationState:
              MatrixAccountVerificationState.verificationRequired,
          deviceVerificationState: MatrixDeviceVerificationState.unverified,
          keyBackupState: MatrixKeyBackupState.recoveryRequired,
          roomEncryptionReadiness:
              MatrixRoomEncryptionReadiness.encryptedRoomsNeedAttention,
          secretStorageReady: true,
          crossSigningReady: false,
          hasEncryptedConversations: true,
          verification: MatrixVerificationSnapshot(
            phase: MatrixVerificationPhase.compareSas,
            message: 'Compare security emoji or numbers on both devices.',
            sasNumbers: <int>[1234, 5678, 9012],
            sasEmojis: <MatrixVerificationEmoji>[
              MatrixVerificationEmoji(symbol: '🐶', label: 'Dog'),
            ],
          ),
        );

      final repository = MatrixChatSecurityRepository(
        client: client,
        serverConfigurationRepository: _FakeServerConfigurationRepository(
          buildTestConfiguration(),
        ),
      );

      final security = await repository.loadSecurityState();

      expect(
        security.bootstrapState,
        ChatSecurityBootstrapState.recoveryRequired,
      );
      expect(
        security.accountVerificationState,
        ChatAccountVerificationState.verificationRequired,
      );
      expect(
        security.deviceVerificationState,
        ChatDeviceVerificationState.unverified,
      );
      expect(security.keyBackupState, ChatKeyBackupState.recoveryRequired);
      expect(
        security.roomEncryptionReadiness,
        ChatRoomEncryptionReadiness.encryptedRoomsNeedAttention,
      );
      expect(
        security.verificationSession.phase,
        ChatVerificationPhase.compareSas,
      );
      expect(security.verificationSession.sasNumbers, <int>[1234, 5678, 9012]);
      expect(security.verificationSession.sasEmojis.single.label, 'Dog');
    });

    test('fails clearly when setup is missing', () async {
      final repository = MatrixChatSecurityRepository(
        client: _FakeMatrixClient(),
        serverConfigurationRepository: _FakeServerConfigurationRepository(null),
      );

      expect(
        repository.loadSecurityState(),
        throwsA(
          isA<ChatFailure>().having(
            (failure) => failure.type,
            'type',
            ChatFailureType.configuration,
          ),
        ),
      );
    });

    test(
      'maps verification recovery-key requests into chat-owned phases',
      () async {
        final client = _FakeMatrixClient()
          ..snapshot = const MatrixSecuritySnapshot(
            isMatrixSignedIn: true,
            bootstrapState: MatrixSecurityBootstrapState.ready,
            accountVerificationState: MatrixAccountVerificationState.verified,
            deviceVerificationState: MatrixDeviceVerificationState.unverified,
            keyBackupState: MatrixKeyBackupState.ready,
            roomEncryptionReadiness: MatrixRoomEncryptionReadiness.ready,
            secretStorageReady: true,
            crossSigningReady: true,
            hasEncryptedConversations: true,
            verification: MatrixVerificationSnapshot(
              phase: MatrixVerificationPhase.needsRecoveryKey,
              message:
                  'Enter your Matrix recovery key or passphrase to continue verification.',
            ),
          );

        final repository = MatrixChatSecurityRepository(
          client: client,
          serverConfigurationRepository: _FakeServerConfigurationRepository(
            buildTestConfiguration(),
          ),
        );

        final security = await repository.loadSecurityState();

        expect(
          security.verificationSession.phase,
          ChatVerificationPhase.needsRecoveryKey,
        );
        expect(security.verificationSession.message, contains('recovery key'));
      },
    );
  });
}
