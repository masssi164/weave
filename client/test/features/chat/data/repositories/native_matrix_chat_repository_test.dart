import 'package:flutter_test/flutter_test.dart';
import 'package:weave/features/chat/data/repositories/native_matrix_chat_repository.dart';
import 'package:weave/features/chat/domain/entities/chat_conversation.dart';
import 'package:weave/features/chat/domain/entities/chat_failure.dart';
import 'package:weave/features/chat/domain/entities/chat_message.dart';
import 'package:weave/integrations/rust_matrix_core/data/services/matrix_crypto_session_coordinator.dart';
import 'package:weave/integrations/rust_matrix_core/data/services/rust_matrix_core_bridge.dart';

import '../../../../helpers/fake_matrix_crypto.dart';

class _FailingRoomBridge extends FakeRustMatrixCoreBridge {
  @override
  Future<List<RustMatrixEncryptedRoom>> loadEncryptedRooms({
    required String profileKey,
  }) {
    throw const RustMatrixCoreBridgeException('M_WEAVE_E2EE_SYNC');
  }
}

class _ExpiredRoomBridge extends FakeRustMatrixCoreBridge {
  @override
  Future<List<RustMatrixEncryptedRoom>> loadEncryptedRooms({
    required String profileKey,
  }) {
    throw const RustMatrixCoreBridgeException('M_UNKNOWN_TOKEN');
  }
}

class _FailingDescriptorBridge extends FakeRustMatrixCoreBridge {
  @override
  Future<RustMatrixCoreBridgeDescriptor> descriptor({
    String serverName = 'api.weave.test',
  }) {
    throw const RustMatrixCoreBridgeException('M_WEAVE_E2EE_SYNC');
  }
}

class _FailingMatrixSessionPort extends FakeMatrixCryptoSessionPort {
  @override
  Future<MatrixCryptoSession> open({
    bool synchronize = true,
    bool allowInteractiveSignIn = false,
  }) async {
    throw const RustMatrixCoreBridgeException('M_WEAVE_E2EE_SYNC');
  }
}

class _FailingSendBridge extends FakeRustMatrixCoreBridge {
  @override
  Future<String> sendEncryptedText({
    required String profileKey,
    required String roomId,
    required String body,
  }) {
    throw const RustMatrixCoreBridgeException('M_WEAVE_E2EE_SEND_API');
  }
}

class _PendingPeerDeviceBridge extends FakeRustMatrixCoreBridge {
  @override
  Future<String> sendEncryptedText({
    required String profileKey,
    required String roomId,
    required String body,
  }) {
    throw const RustMatrixCoreBridgeException(
      'M_WEAVE_E2EE_PEER_DEVICE_PENDING',
    );
  }
}

class _FailingTimelineBridge extends FakeRustMatrixCoreBridge {
  @override
  Future<List<RustMatrixMessageProjection>> loadEncryptedRoomMessages({
    required String profileKey,
    required String roomId,
    int limit = 100,
  }) {
    throw const RustMatrixCoreBridgeException('M_WEAVE_E2EE_TIMELINE');
  }
}

class _FailingCreateBridge extends FakeRustMatrixCoreBridge {
  @override
  Future<RustMatrixEncryptedRoom> createEncryptedRoom({
    required String profileKey,
    required String title,
  }) {
    throw const RustMatrixCoreBridgeException('M_WEAVE_E2EE_CREATE_ROOM');
  }
}

void main() {
  late FakeMatrixCryptoSessionPort cryptoSession;
  late FakeRustMatrixCoreBridge bridge;

  NativeMatrixChatRepository repository({
    FakeRustMatrixCoreBridge? rustBridge,
  }) {
    return NativeMatrixChatRepository(
      matrixCryptoSessionCoordinator: cryptoSession,
      rustMatrixCoreBridge: rustBridge ?? bridge,
    );
  }

  setUp(() {
    cryptoSession = FakeMatrixCryptoSessionPort();
    bridge = FakeRustMatrixCoreBridge();
  });

  test('connect opens the native encrypted Matrix session', () async {
    // MATRIX_CONNECT_CONTRACT
    await repository().connect();

    expect(cryptoSession.synchronizeValues, <bool>[true]);
    expect(cryptoSession.interactiveValues, <bool>[true]);
  });

  test('connect does not depend on the old server facade descriptor', () async {
    await repository(rustBridge: _FailingDescriptorBridge()).connect();
    expect(cryptoSession.synchronizeValues, <bool>[true]);
  });

  test('connect retains only the support-safe Rust failure code', () async {
    cryptoSession = _FailingMatrixSessionPort();
    await expectLater(
      repository().connect(),
      throwsA(
        isA<ChatFailure>()
            .having(
              (failure) => failure.message,
              'message',
              isNot(contains('M_WEAVE_E2EE_SYNC')),
            )
            .having(
              (failure) => failure.cause,
              'cause',
              isA<RustMatrixCoreBridgeException>().having(
                (cause) => cause.code,
                'code',
                'M_WEAVE_E2EE_SYNC',
              ),
            ),
      ),
    );
  });

  test('expired Matrix transport grant offers explicit reconnect', () async {
    await expectLater(
      repository(rustBridge: _ExpiredRoomBridge()).loadConversations(),
      throwsA(
        isA<ChatFailure>().having(
          (failure) => failure.type,
          'type',
          ChatFailureType.sessionRequired,
        ),
      ),
    );
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
    expect(cryptoSession.interactiveValues, <bool>[false]);
  });

  test(
    'creates an encrypted conversation through the native Rust bridge',
    () async {
      final conversation = await repository().createConversation(
        title: '  Release planning  ',
      );

      expect(cryptoSession.synchronizeValues, <bool>[false]);
      expect(bridge.createdRooms.single, <String, String>{
        'profileKey': 'profile-key',
        'title': 'Release planning',
      });
      expect(conversation.id, '!created:api.weave.test');
      expect(conversation.title, 'Release planning');
      expect(conversation.previewType, ChatConversationPreviewType.encrypted);
    },
  );

  test('conversation creation rejects empty names before transport', () async {
    await expectLater(
      repository().createConversation(title: '   '),
      throwsA(
        isA<ChatFailure>().having(
          (failure) => failure.type,
          'type',
          ChatFailureType.configuration,
        ),
      ),
    );

    expect(bridge.createdRooms, isEmpty);
  });

  test('conversation creation keeps Rust failures support safe', () async {
    await expectLater(
      repository(
        rustBridge: _FailingCreateBridge(),
      ).createConversation(title: 'Release planning'),
      throwsA(
        isA<ChatFailure>()
            .having(
              (failure) => failure.message,
              'message',
              isNot(contains('M_WEAVE_E2EE_CREATE_ROOM')),
            )
            .having(
              (failure) => failure.cause,
              'cause',
              isA<RustMatrixCoreBridgeException>().having(
                (cause) => cause.code,
                'code',
                'M_WEAVE_E2EE_CREATE_ROOM',
              ),
            ),
      ),
    );
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
    expect(cryptoSession.synchronizeValues, <bool>[false, false, false]);
  });

  test(
    'encrypted send retains only the support-safe Rust cause code',
    () async {
      await expectLater(
        repository(rustBridge: _FailingSendBridge()).sendMessage(
          roomId: '!general:api.weave.test',
          message: 'encrypted through Rust',
        ),
        throwsA(
          isA<ChatFailure>()
              .having(
                (failure) => failure.type,
                'type',
                ChatFailureType.protocol,
              )
              .having(
                (failure) => failure.message,
                'message',
                isNot(contains('M_WEAVE_E2EE_SEND_API')),
              )
              .having(
                (failure) => failure.cause,
                'cause',
                isA<RustMatrixCoreBridgeException>().having(
                  (cause) => cause.code,
                  'code',
                  'M_WEAVE_E2EE_SEND_API',
                ),
              ),
        ),
      );
    },
  );

  test(
    'encrypted send exposes a typed retriable peer-device barrier',
    () async {
      await expectLater(
        repository(rustBridge: _PendingPeerDeviceBridge()).sendMessage(
          roomId: '!general:api.weave.test',
          message: 'encrypted through Rust',
        ),
        throwsA(
          isA<ChatFailure>()
              .having(
                (failure) => failure.type,
                'type',
                ChatFailureType.peerDevicePending,
              )
              .having(
                (failure) => failure.message,
                'message',
                contains('Waiting for a participant’s secure device'),
              )
              .having(
                (failure) => failure.cause,
                'cause',
                isA<RustMatrixCoreBridgeException>().having(
                  (cause) => cause.code,
                  'code',
                  'M_WEAVE_E2EE_PEER_DEVICE_PENDING',
                ),
              ),
        ),
      );
    },
  );

  test(
    'encrypted timeline retains only the support-safe Rust cause code',
    () async {
      await expectLater(
        repository(
          rustBridge: _FailingTimelineBridge(),
        ).loadRoomTimeline('!general:api.weave.test'),
        throwsA(
          isA<ChatFailure>()
              .having(
                (failure) => failure.type,
                'type',
                ChatFailureType.protocol,
              )
              .having(
                (failure) => failure.message,
                'message',
                isNot(contains('M_WEAVE_E2EE_TIMELINE')),
              )
              .having(
                (failure) => failure.cause,
                'cause',
                isA<RustMatrixCoreBridgeException>().having(
                  (cause) => cause.code,
                  'code',
                  'M_WEAVE_E2EE_TIMELINE',
                ),
              ),
        ),
      );
    },
  );

  test(
    'sign-out ends Matrix OAuth while setup clear only disposes its client',
    () async {
      final chat = repository();

      await chat.signOut();
      await chat.clearSession();

      expect(cryptoSession.endCalls, 1);
      expect(cryptoSession.disposeCalls, 1);
      expect(cryptoSession.removeCalls, 0);
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
            )
            .having(
              (failure) => failure.cause,
              'cause',
              isA<RustMatrixCoreBridgeException>().having(
                (cause) => cause.code,
                'code',
                'M_WEAVE_E2EE_SYNC',
              ),
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
