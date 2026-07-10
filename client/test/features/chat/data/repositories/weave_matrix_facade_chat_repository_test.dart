import 'package:flutter_test/flutter_test.dart';
import 'package:weave/features/auth/domain/entities/auth_configuration.dart';
import 'package:weave/features/auth/domain/entities/auth_state.dart';
import 'package:weave/features/auth/domain/repositories/auth_session_repository.dart';
import 'package:weave/features/chat/data/repositories/weave_matrix_facade_chat_repository.dart';
import 'package:weave/features/chat/domain/entities/chat_conversation.dart';
import 'package:weave/features/chat/domain/entities/chat_failure.dart';
import 'package:weave/features/chat/domain/entities/chat_message.dart';
import 'package:weave/features/server_config/domain/entities/server_configuration.dart';
import 'package:weave/features/server_config/domain/repositories/server_configuration_repository.dart';
import 'package:weave/integrations/rust_matrix_core/data/services/rust_matrix_core_bridge.dart';

import '../../../../helpers/auth_test_data.dart';
import '../../../../helpers/fake_matrix_crypto.dart';
import '../../../../helpers/server_config_test_data.dart';

class _FakeServerConfigurationRepository
    implements ServerConfigurationRepository {
  _FakeServerConfigurationRepository(this.configuration);

  ServerConfiguration? configuration;

  @override
  Future<void> clearConfiguration() async => configuration = null;

  @override
  Future<ServerConfiguration?> loadConfiguration() async => configuration;

  @override
  Future<void> saveConfiguration(ServerConfiguration configuration) async {
    this.configuration = configuration;
  }
}

class _FakeAuthSessionRepository implements AuthSessionRepository {
  AuthState state = AuthState.authenticated(buildTestAuthSession());
  AuthConfiguration? signOutConfiguration;
  int clearCalls = 0;

  @override
  Future<void> clearLocalSession() async => clearCalls += 1;

  @override
  Future<AuthState> refreshSession(AuthConfiguration configuration) async =>
      state;

  @override
  Future<AuthState> restoreSession(AuthConfiguration configuration) async =>
      state;

  @override
  Future<AuthState> signIn(AuthConfiguration configuration) async => state;

  @override
  Future<void> signOut(AuthConfiguration configuration) async {
    signOutConfiguration = configuration;
  }
}

class _FailingRoomBridge extends FakeRustMatrixCoreBridge {
  @override
  Future<List<RustMatrixEncryptedRoom>> loadEncryptedRooms({
    required String profileKey,
  }) {
    throw const RustMatrixCoreBridgeException('M_WEAVE_E2EE_SYNC');
  }
}

void main() {
  late _FakeServerConfigurationRepository configurationRepository;
  late _FakeAuthSessionRepository authSessionRepository;
  late FakeMatrixCryptoSessionPort cryptoSession;
  late FakeRustMatrixCoreBridge bridge;

  WeaveMatrixFacadeChatRepository repository({
    FakeRustMatrixCoreBridge? rustBridge,
  }) {
    return WeaveMatrixFacadeChatRepository(
      serverConfigurationRepository: configurationRepository,
      authSessionRepository: authSessionRepository,
      matrixCryptoSessionCoordinator: cryptoSession,
      rustMatrixCoreBridge: rustBridge ?? bridge,
    );
  }

  setUp(() {
    configurationRepository = _FakeServerConfigurationRepository(
      buildTestConfiguration(matrixHomeserverUrl: 'https://api.weave.test'),
    );
    authSessionRepository = _FakeAuthSessionRepository();
    cryptoSession = FakeMatrixCryptoSessionPort();
    bridge = FakeRustMatrixCoreBridge();
  });

  test('connect opens the OIDC-gated encrypted Rust session', () async {
    // MATRIX_CONNECT_CONTRACT
    await repository().connect();

    expect(cryptoSession.synchronizeValues, <bool>[true]);
  });

  test('maps only Rust-projected encrypted rooms into chat entities', () async {
    // MATRIX_SPACES_ROOMS_CONTRACT
    bridge.rooms = const <RustMatrixEncryptedRoom>[
      RustMatrixEncryptedRoom(
        roomId: '!quiet:api.weave.test',
        title: 'Quiet',
        unreadCount: 0,
        encrypted: true,
      ),
      RustMatrixEncryptedRoom(
        roomId: '!general:api.weave.test',
        title: 'General',
        unreadCount: 2,
        encrypted: true,
      ),
    ];

    final conversations = await repository().loadConversations();

    expect(conversations.map((room) => room.title), <String>[
      'General',
      'Quiet',
    ]);
    expect(
      conversations.first.previewType,
      ChatConversationPreviewType.encrypted,
    );
    expect(conversations.first.previewText, isNull);
    expect(conversations.first.unreadCount, 2);
  });

  test('send, decrypt, and receipt stay inside the Rust Matrix core', () async {
    // MATRIX_MESSAGE_CONTRACT
    const roomId = '!general:api.weave.test';
    bridge.messages[roomId] = const <RustMatrixMessageProjection>[
      RustMatrixMessageProjection(
        eventId: r'$sent:api.weave.test',
        sender: '@user:api.weave.test',
        originServerTimestamp: 1778244300000,
        body: 'decrypted only in Rust',
        contentType: 'encryptedText',
      ),
    ];
    final chat = repository();

    await chat.sendMessage(roomId: roomId, message: 'encrypted through Rust');
    final timeline = await chat.loadRoomTimeline(roomId);
    await chat.markRoomRead(roomId);

    expect(bridge.sentMessages.single, <String, String>{
      'profileKey': 'profile-key',
      'roomId': roomId,
      'body': 'encrypted through Rust',
    });
    expect(bridge.receipts.single['eventId'], r'$sent:api.weave.test');
    expect(timeline.messages.single.contentType, ChatMessageContentType.text);
    expect(timeline.messages.single.text, 'decrypted only in Rust');
    expect(timeline.messages.single.isMine, isTrue);
  });

  test(
    'normal sign-out and session clear preserve local crypto state',
    () async {
      final chat = repository();

      await chat.signOut();
      await chat.clearSession();

      expect(cryptoSession.disposeCalls, 2);
      expect(cryptoSession.removeCalls, 0);
      expect(authSessionRepository.signOutConfiguration, isNotNull);
      expect(authSessionRepository.clearCalls, 1);
    },
  );

  test('Rust crypto failures remain support-safe', () async {
    await expectLater(
      repository(rustBridge: _FailingRoomBridge()).loadConversations(),
      throwsA(
        isA<ChatFailure>()
            .having((failure) => failure.type, 'type', ChatFailureType.protocol)
            .having(
              (failure) => failure.message,
              'message',
              isNot(contains('access_token')),
            ),
      ),
    );
  });

  test('an unencrypted room cannot downgrade the E2EE client path', () async {
    // MATRIX_E2EE_CLIENT_FAILS_CLOSED
    bridge.rooms = const <RustMatrixEncryptedRoom>[
      RustMatrixEncryptedRoom(
        roomId: '!legacy:api.weave.test',
        title: 'Legacy',
        unreadCount: 0,
        encrypted: false,
      ),
    ];

    final conversations = await repository().loadConversations();

    expect(
      conversations.single.previewType,
      ChatConversationPreviewType.unsupported,
    );
    expect(conversations.single.previewText, isNull);
  });
}
