import 'package:flutter_test/flutter_test.dart';
import 'package:weave/features/chat/data/repositories/matrix_chat_repository.dart';
import 'package:weave/features/chat/data/services/matrix_client.dart';
import 'package:weave/features/chat/domain/entities/chat_conversation.dart';
import 'package:weave/features/chat/domain/entities/chat_failure.dart';
import 'package:weave/features/server_config/domain/entities/server_configuration.dart';
import 'package:weave/features/server_config/domain/repositories/'
    'server_configuration_repository.dart';

import '../../../../helpers/server_config_test_data.dart';

class _FakeMatrixClient implements MatrixClient {
  Uri? lastHomeserverForLoad;
  Uri? lastHomeserverForConnect;

  List<MatrixRoomSnapshot> rooms = const <MatrixRoomSnapshot>[];

  @override
  Stream<MatrixVerificationSnapshot> get verificationUpdates =>
      const Stream<MatrixVerificationSnapshot>.empty();

  @override
  Future<void> acceptVerification({required Uri homeserver}) async {}

  @override
  Future<String> bootstrapSecurity({
    required Uri homeserver,
    String? passphrase,
  }) async {
    return 'RECOVERY-KEY';
  }

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
  Future<void> connect({required Uri homeserver}) async {
    lastHomeserverForConnect = homeserver;
  }

  @override
  Future<void> dismissVerificationResult({required Uri homeserver}) async {}

  @override
  Future<void> dispose() async {}

  @override
  Future<List<MatrixRoomSnapshot>> loadConversations({
    required Uri homeserver,
  }) async {
    lastHomeserverForLoad = homeserver;
    return rooms;
  }

  @override
  Future<MatrixSecuritySnapshot> loadSecurityState({
    required Uri homeserver,
    bool refresh = false,
  }) async {
    return const MatrixSecuritySnapshot(
      isMatrixSignedIn: false,
      bootstrapState: MatrixSecurityBootstrapState.signedOut,
      accountVerificationState: MatrixAccountVerificationState.unavailable,
      deviceVerificationState: MatrixDeviceVerificationState.unavailable,
      keyBackupState: MatrixKeyBackupState.unavailable,
      roomEncryptionReadiness: MatrixRoomEncryptionReadiness.unavailable,
      secretStorageReady: false,
      crossSigningReady: false,
      hasEncryptedConversations: false,
    );
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
  group('MatrixChatRepository', () {
    test('loads conversations from the configured Matrix homeserver', () async {
      final client = _FakeMatrixClient()
        ..rooms = const <MatrixRoomSnapshot>[
          MatrixRoomSnapshot(
            id: '!room:home.internal',
            title: 'Project',
            previewType: MatrixRoomPreviewType.text,
            previewText: 'Latest update',
            unreadCount: 3,
            isInvite: false,
            isDirectMessage: false,
          ),
        ];
      final repository = MatrixChatRepository(
        client: client,
        serverConfigurationRepository: _FakeServerConfigurationRepository(
          buildTestConfiguration(),
        ),
      );

      final conversations = await repository.loadConversations();

      expect(
        client.lastHomeserverForLoad.toString(),
        'https://matrix.home.internal',
      );
      expect(conversations, hasLength(1));
      expect(conversations.first.previewType, ChatConversationPreviewType.text);
      expect(conversations.first.unreadCount, 3);
    });

    test('connect uses the configured Matrix homeserver', () async {
      final client = _FakeMatrixClient();
      final repository = MatrixChatRepository(
        client: client,
        serverConfigurationRepository: _FakeServerConfigurationRepository(
          buildTestConfiguration(),
        ),
      );

      await repository.connect();

      expect(
        client.lastHomeserverForConnect.toString(),
        'https://matrix.home.internal',
      );
    });

    test('fails clearly when setup is missing', () async {
      final repository = MatrixChatRepository(
        client: _FakeMatrixClient(),
        serverConfigurationRepository: _FakeServerConfigurationRepository(null),
      );

      expect(
        repository.loadConversations(),
        throwsA(
          isA<ChatFailure>().having(
            (failure) => failure.type,
            'type',
            ChatFailureType.configuration,
          ),
        ),
      );
    });
  });
}
