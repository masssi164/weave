import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:weave/features/chat/data/repositories/native_matrix_chat_repository.dart';
import 'package:weave/features/chat/domain/entities/chat_conversation.dart';
import 'package:weave/features/chat/domain/entities/chat_failure.dart';
import 'package:weave/features/chat/domain/entities/chat_message.dart';
import 'package:weave/features/chat/domain/entities/chat_participant.dart';
import 'package:weave/integrations/rust_matrix_core/data/services/matrix_crypto_session_coordinator.dart';
import 'package:weave/integrations/rust_matrix_core/data/services/rust_matrix_core_bridge.dart';

import '../../../../helpers/fake_matrix_crypto.dart';

class _FailingRoomBridge extends FakeRustMatrixCoreBridge {
  @override
  Future<List<RustMatrixEncryptedRoom>> loadRooms({
    required String profileKey,
  }) {
    throw const RustMatrixCoreBridgeException('M_WEAVE_E2EE_SYNC');
  }
}

class _ExpiredRoomBridge extends FakeRustMatrixCoreBridge {
  @override
  Future<List<RustMatrixEncryptedRoom>> loadRooms({
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
    bool allowInteractiveSignIn = true,
  }) async {
    throw const RustMatrixCoreBridgeException('M_WEAVE_E2EE_SYNC');
  }
}

class _FailingSendBridge extends FakeRustMatrixCoreBridge {
  @override
  Future<String> sendText({
    required String profileKey,
    required String roomId,
    required String body,
  }) {
    throw const RustMatrixCoreBridgeException('M_WEAVE_E2EE_SEND_API');
  }
}

class _PendingPeerDeviceBridge extends FakeRustMatrixCoreBridge {
  @override
  Future<String> sendText({
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
  Future<List<RustMatrixMessageProjection>> loadRoomMessages({
    required String profileKey,
    required String roomId,
    int limit = 100,
  }) {
    throw const RustMatrixCoreBridgeException('M_WEAVE_E2EE_TIMELINE');
  }
}

class _RevokedTimelineBridge extends FakeRustMatrixCoreBridge {
  _RevokedTimelineBridge(this.code)
    : failure = RustMatrixCoreBridgeException(code);

  final String code;
  final RustMatrixCoreBridgeException failure;

  @override
  Future<List<RustMatrixMessageProjection>> loadRoomMessages({
    required String profileKey,
    required String roomId,
    int limit = 100,
  }) {
    throw failure;
  }
}

class _FailingCreateBridge extends FakeRustMatrixCoreBridge {
  @override
  Future<RustMatrixEncryptedRoom> createBusinessRoom({
    required String profileKey,
    required String title,
  }) {
    throw const RustMatrixCoreBridgeException('M_WEAVE_E2EE_CREATE_ROOM');
  }
}

class _ParticipantBridge extends FakeRustMatrixCoreBridge {
  List<String>? invited;
  bool? direct;
  String? search;
  @override
  Future<List<RustMatrixMember>> searchMembers({
    required String profileKey,
    required String query,
  }) async {
    search = query;
    return const [
      RustMatrixMember(userId: '@member:api.weave.test', displayName: 'Member'),
    ];
  }

  @override
  Future<RustMatrixEncryptedRoom> createBusinessRoomWithMembers({
    required String profileKey,
    required String title,
    required List<String> memberIds,
    bool isDirect = false,
  }) async {
    invited = memberIds;
    direct = isDirect;
    return RustMatrixEncryptedRoom(
      roomId: '!shared:api.weave.test',
      title: title,
      unreadCount: 0,
      encrypted: false,
      isDirect: isDirect,
    );
  }
}

class _InvitationBridge extends FakeRustMatrixCoreBridge {
  _InvitationBridge(this.error);
  final String? error;
  String? joined;
  @override
  Future<void> acceptRoomInvitation({
    required String profileKey,
    required String roomId,
  }) async {
    expect(profileKey, isNotEmpty);
    joined = roomId;
    if (error != null) throw RustMatrixCoreBridgeException(error!);
  }
}

class _DiagnosticSessionPort extends FakeMatrixCryptoSessionPort {
  _DiagnosticSessionPort(this.failure, this.stack);
  final Object failure;
  final StackTrace stack;
  @override
  Future<MatrixCryptoSession> open({
    bool synchronize = true,
    bool allowInteractiveSignIn = true,
  }) => Future.error(failure, stack);
}

class _DiagnosticSendBridge extends FakeRustMatrixCoreBridge {
  _DiagnosticSendBridge(this.code);
  final String code;
  @override
  Future<String> sendText({
    required String profileKey,
    required String roomId,
    required String body,
  }) => Future.error(RustMatrixCoreBridgeException(code));
}

void main() {
  test(
    'failure diagnostics preserve direct admission failure and stack without payloads',
    () async {
      final originalPrinter = debugPrint;
      final records = <String>[];
      debugPrint = (String? message, {int? wrapWidth}) =>
          records.add(message ?? '');
      addTearDown(() => debugPrint = originalPrinter);
      for (final code in [
        'M_WEAVE_MATRIX_SESSION_CHANGED',
        'private@example.invalid https://secret/token',
        'M_WEAVE_MATRIX_SESSION_CHANGED private token',
        'private' * 2048,
      ]) {
        final failure = ChatFailure.sessionRequired(
          code,
          cause: 'private refresh token',
        );
        final stack = StackTrace.fromString('owned-original-stack');
        final repository = NativeMatrixChatRepository(
          matrixCryptoSessionCoordinator: _DiagnosticSessionPort(
            failure,
            stack,
          ),
          rustMatrixCoreBridge: FakeRustMatrixCoreBridge(),
        );
        for (final operation in ['send', 'timeline']) {
          Object? observed;
          StackTrace? observedStack;
          try {
            if (operation == 'send') {
              await repository.sendMessage(
                roomId: '!private-room',
                message: 'private body',
              );
            } else {
              await repository.loadRoomTimeline('!private-room');
            }
          } catch (error, trace) {
            observed = error;
            observedStack = trace;
          }
          expect(observed, same(failure));
          expect(observedStack.toString(), contains('owned-original-stack'));
          expect(
            records.last,
            'WEAVE_NATIVE_CHAT_FAILURE operation=$operation stage=open type=sessionRequired code=${code == 'M_WEAVE_MATRIX_SESSION_CHANGED' ? code : 'unavailable'} supportSafe=true',
          );
        }
      }
      expect(records.join(), isNot(contains('private')));
      expect(records.join(), isNot(contains('https:')));
    },
  );

  test(
    'SDK failure diagnostics retain existing mappings and redact unknown codes',
    () async {
      final originalPrinter = debugPrint;
      final records = <String>[];
      debugPrint = (String? message, {int? wrapWidth}) =>
          records.add(message ?? '');
      addTearDown(() => debugPrint = originalPrinter);
      final timeline = NativeMatrixChatRepository(
        matrixCryptoSessionCoordinator: FakeMatrixCryptoSessionPort(),
        rustMatrixCoreBridge: _RevokedTimelineBridge('M_NOT_FOUND'),
      );
      await expectLater(
        timeline.loadRoomTimeline('!private-room'),
        throwsA(
          isA<ChatFailure>().having(
            (e) => e.type,
            'type',
            ChatFailureType.sessionRequired,
          ),
        ),
      );
      expect(
        records.single,
        'WEAVE_NATIVE_CHAT_FAILURE operation=timeline stage=timeline type=bridge code=M_NOT_FOUND supportSafe=true',
      );
      for (final code in [
        'M_FORBIDDEN',
        'M_WEAVE_E2EE_SEND',
        'private@example.invalid https://secret/token',
      ]) {
        final repository = NativeMatrixChatRepository(
          matrixCryptoSessionCoordinator: FakeMatrixCryptoSessionPort(),
          rustMatrixCoreBridge: _DiagnosticSendBridge(code),
        );
        await expectLater(
          repository.sendMessage(
            roomId: '!private-room',
            message: 'private body',
          ),
          throwsA(
            isA<ChatFailure>()
                .having(
                  (e) => e.type,
                  'type',
                  code == 'M_FORBIDDEN'
                      ? ChatFailureType.sessionRequired
                      : ChatFailureType.protocol,
                )
                .having(
                  (e) => (e.cause as RustMatrixCoreBridgeException).code,
                  'original code',
                  code,
                ),
          ),
        );
        expect(
          records.last,
          'WEAVE_NATIVE_CHAT_FAILURE operation=send stage=send type=bridge code=${{'M_FORBIDDEN', 'M_WEAVE_E2EE_SEND'}.contains(code) ? code : 'unavailable'} supportSafe=true',
        );
      }
      final opening = NativeMatrixChatRepository(
        matrixCryptoSessionCoordinator: _DiagnosticSessionPort(
          const RustMatrixCoreBridgeException('M_WEAVE_MATRIX_MEMBER_SESSION'),
          StackTrace.current,
        ),
      );
      await expectLater(
        opening.sendMessage(roomId: '!private-room', message: 'private body'),
        throwsA(
          isA<ChatFailure>().having(
            (e) => e.type,
            'type',
            ChatFailureType.protocol,
          ),
        ),
      );
      expect(
        records.last,
        'WEAVE_NATIVE_CHAT_FAILURE operation=send stage=open type=bridge code=M_WEAVE_MATRIX_MEMBER_SESSION supportSafe=true',
      );
      expect(records.join(), isNot(contains('private')));
      expect(records.join(), isNot(contains('https:')));
    },
  );

  test('fixed diagnostics bypass a buffering test print zone', () async {
    final originalPrinter = debugPrint;
    debugPrint = debugPrintSynchronously;
    addTearDown(() => debugPrint = originalPrinter);
    final captured = <String>[];
    const failure = ChatFailure.sessionRequired('M_WEAVE_MATRIX_ACCESS_DENIED');
    final repository = NativeMatrixChatRepository(
      matrixCryptoSessionCoordinator: _DiagnosticSessionPort(
        failure,
        StackTrace.current,
      ),
    );
    Object? observed;
    await runZoned(
      () async {
        try {
          await repository.sendMessage(
            roomId: '!private-room',
            message: 'private body',
          );
        } catch (error) {
          observed = error;
        }
      },
      zoneSpecification: ZoneSpecification(
        print: (self, parent, zone, value) => captured.add(value),
      ),
    );
    expect(observed, same(failure));
    expect(captured, isEmpty);
  });

  test(
    'timeline carries only the existing verified member storage scope',
    () async {
      const owner =
          'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa';
      for (final scope in [null, owner]) {
        final repository = NativeMatrixChatRepository(
          matrixCryptoSessionCoordinator: FakeMatrixCryptoSessionPort(
            session: MatrixCryptoSession(
              profileKey: 'profile',
              userId: '@member:api.weave.test',
              deviceId: 'device',
              memberStorageScope: scope,
            ),
          ),
          rustMatrixCoreBridge: FakeRustMatrixCoreBridge(),
        );
        expect(
          (await repository.loadRoomTimeline(
            '!room:api.weave.test',
          )).memberStorageScope,
          scope,
        );
      }
    },
  );

  test(
    'successful native send and timeline do not emit failure diagnostics',
    () async {
      final originalPrinter = debugPrint;
      final records = <String>[];
      debugPrint = (String? message, {int? wrapWidth}) =>
          records.add(message ?? '');
      addTearDown(() => debugPrint = originalPrinter);
      final repository = NativeMatrixChatRepository(
        matrixCryptoSessionCoordinator: FakeMatrixCryptoSessionPort(),
        rustMatrixCoreBridge: FakeRustMatrixCoreBridge(),
      );
      await repository.sendMessage(
        roomId: '!owned-room',
        message: 'owned message',
      );
      await repository.loadRoomTimeline('!owned-room');
      expect(records, isEmpty);
    },
  );

  test(
    'failed diagnostic emission cannot replace the original direct failure',
    () async {
      final originalPrinter = debugPrint;
      debugPrint = (String? message, {int? wrapWidth}) =>
          throw StateError('private logging failure');
      addTearDown(() => debugPrint = originalPrinter);
      const failure = ChatFailure.sessionRequired(
        'M_WEAVE_MATRIX_ACCESS_DENIED',
      );
      final repository = NativeMatrixChatRepository(
        matrixCryptoSessionCoordinator: _DiagnosticSessionPort(
          failure,
          StackTrace.current,
        ),
      );
      await expectLater(
        repository.sendMessage(
          roomId: '!private-room',
          message: 'private body',
        ),
        throwsA(same(failure)),
      );
    },
  );

  for (final error in <String?>[null, 'M_FORBIDDEN', 'M_UNKNOWN_TOKEN']) {
    test(
      'accept invitation forwards active profile and preserves refusal $error',
      () async {
        final bridge = _InvitationBridge(error);
        final repository = NativeMatrixChatRepository(
          matrixCryptoSessionCoordinator: FakeMatrixCryptoSessionPort(),
          rustMatrixCoreBridge: bridge,
        );
        final operation = repository.acceptInvitation('!invite:api.weave.test');
        if (error == null) {
          await operation;
        } else {
          await expectLater(
            operation,
            throwsA(
              isA<ChatFailure>().having(
                (value) => value.type,
                'type',
                error == 'M_UNKNOWN_TOKEN'
                    ? ChatFailureType.sessionRequired
                    : ChatFailureType.protocol,
              ),
            ),
          );
        }
        expect(bridge.joined, '!invite:api.weave.test');
      },
    );
  }
  test('SDK invited membership remains visible as an invitation', () async {
    final bridge = FakeRustMatrixCoreBridge()
      ..rooms = const [
        RustMatrixEncryptedRoom(
          roomId: '!invite:api.weave.test',
          title: 'Invitation',
          unreadCount: 0,
          encrypted: false,
          isInvite: true,
        ),
      ];
    final repository = NativeMatrixChatRepository(
      matrixCryptoSessionCoordinator: FakeMatrixCryptoSessionPort(),
      rustMatrixCoreBridge: bridge,
    );
    expect((await repository.loadConversations()).single.isInvite, isTrue);
    expect(
      RustMatrixEncryptedRoom.fromJson({
        'roomId': '!invite:api.weave.test',
        'membership': 'invited',
      }).isInvite,
      isTrue,
    );
  });

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

  test(
    'search and create forward current session and selected native members through SDK bridge',
    () async {
      final participantBridge = _ParticipantBridge();
      final chat = repository(rustBridge: participantBridge);
      final members = await chat.searchMembers(' Member ');
      expect(participantBridge.search, 'Member');
      expect(members.single.displayName, 'Member');
      final conversation = await chat.createConversationWithParticipants(
        title: 'Shared conversation',
        participants: members,
      );
      expect(conversation.id, '!shared:api.weave.test');
      expect(participantBridge.invited, ['@member:api.weave.test']);
    },
  );

  test(
    'explicit direct intent uses one peer and retains SDK direct classification',
    () async {
      final participantBridge = _ParticipantBridge();
      final chat = repository(rustBridge: participantBridge);
      final members = await chat.searchMembers('Member');
      final room = await chat.createConversationWithParticipants(
        title: 'Direct',
        participants: members,
        isDirect: true,
      );
      expect(participantBridge.direct, isTrue);
      expect(room.isDirectMessage, isTrue);
      expect(room.isInvite, isFalse);
      for (final invalid in [
        <ChatParticipant>[],
        [members.single, members.single],
      ]) {
        await expectLater(
          chat.createConversationWithParticipants(
            title: 'Invalid',
            participants: invalid,
            isDirect: true,
          ),
          throwsA(
            isA<ChatFailure>().having(
              (f) => f.type,
              'type',
              ChatFailureType.configuration,
            ),
          ),
        );
      }
      expect(
        RustMatrixEncryptedRoom.fromJson({
          'roomId': 'owned',
          'isDirect': true,
          'membership': 'joined',
        }).isDirect,
        isTrue,
      );
    },
  );

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

  test('maps encrypted and business rooms into chat entities', () async {
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
        encrypted: false,
      ),
    ];

    final conversations = await repository().loadConversations();

    expect(conversations.map((room) => room.title), <String>[
      'General',
      'Quiet',
    ]);
    expect(conversations.first.previewType, ChatConversationPreviewType.none);
    expect(conversations.first.previewText, isNull);
    expect(conversations.first.unreadCount, 2);
    expect(cryptoSession.interactiveValues, <bool>[true]);
  });

  test(
    'creates a business conversation through the native Rust bridge',
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
      expect(conversation.previewType, ChatConversationPreviewType.none);
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

  test(
    'current optional sender name never changes native sender identity or ownership',
    () async {
      const room = '!metadata:api.weave.test';
      for (final name in ['Current Peer', null, '', '   ', 'You']) {
        bridge.messages[room] = [
          RustMatrixMessageProjection(
            eventId: 'named-event',
            sender: '@peer:api.weave.test',
            senderDisplayName: name,
            originServerTimestamp: 1,
            body: 'peer body',
            contentType: 'text',
          ),
        ];
        final timeline = await repository().loadRoomTimeline(room);
        final message = timeline.messages.single;
        expect(message.senderId, '@peer:api.weave.test');
        expect(message.isMine, isFalse);
        expect(
          message.senderDisplayName,
          name?.trim().isNotEmpty == true ? name!.trim() : 'peer',
        );
      }
    },
  );

  test('send, decrypt, and receipt stay inside the Rust Matrix core', () async {
    // MATRIX_MESSAGE_CONTRACT
    // MATRIX_READ_RECEIPT_CONTRACT
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
    expect(
      timeline.roomTitle,
      isEmpty,
      reason: 'Message-only reads must not fabricate room metadata.',
    );
    expect(timeline.messages.single.isMine, isTrue);
    expect(cryptoSession.synchronizeValues, <bool>[false, false, false]);
  });

  test('business-room plaintext is presented as ordinary chat text', () async {
    const roomId = '!business:api.weave.test';
    bridge.messages[roomId] = const <RustMatrixMessageProjection>[
      RustMatrixMessageProjection(
        eventId: r'$plain:api.weave.test',
        sender: '@user:api.weave.test',
        originServerTimestamp: 1778244300000,
        body: 'Business update',
        contentType: 'text',
      ),
    ];

    final timeline = await repository().loadRoomTimeline(roomId);

    expect(timeline.messages.single.contentType, ChatMessageContentType.text);
    expect(timeline.messages.single.text, 'Business update');
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
    'left SDK room requires current room access without clearing the member session',
    () async {
      final denied = _RevokedTimelineBridge('M_WEAVE_CHAT_JOIN_REQUIRED');
      final session = cryptoSession.session;
      await expectLater(
        repository(rustBridge: denied).loadRoomTimeline('!left:api.weave.test'),
        throwsA(
          isA<ChatFailure>()
              .having(
                (failure) => failure.type,
                'type',
                ChatFailureType.sessionRequired,
              )
              .having(
                (failure) => failure.cause,
                'original cause',
                same(denied.failure),
              ),
        ),
      );
      expect(cryptoSession.session, same(session));
      expect(cryptoSession.endCalls, 0);
      expect(cryptoSession.removeCalls, 0);
      expect(cryptoSession.disposeCalls, 0);
    },
  );

  test(
    'unrecognized and sync timeline failures remain protocol failures',
    () async {
      for (final code in ['M_WEAVE_E2EE_SYNC', 'M_UNKNOWN_PRIVATE_FAILURE']) {
        final failed = _RevokedTimelineBridge(code);
        await expectLater(
          repository(
            rustBridge: failed,
          ).loadRoomTimeline('!owned:api.weave.test'),
          throwsA(
            isA<ChatFailure>()
                .having(
                  (failure) => failure.type,
                  'type',
                  ChatFailureType.protocol,
                )
                .having(
                  (failure) => failure.cause,
                  'original cause',
                  same(failed.failure),
                ),
          ),
        );
        expect(cryptoSession.endCalls, 0);
        expect(cryptoSession.removeCalls, 0);
      }
    },
  );

  test(
    'room denial is an access failure rather than a stale timeline',
    () async {
      for (final code in <String>['M_FORBIDDEN', 'M_NOT_FOUND']) {
        await expectLater(
          repository(
            rustBridge: _RevokedTimelineBridge(code),
          ).loadRoomTimeline('!general:api.weave.test'),
          throwsA(
            isA<ChatFailure>()
                .having(
                  (failure) => failure.type,
                  'type',
                  ChatFailureType.sessionRequired,
                )
                .having(
                  (failure) => failure.message,
                  'message',
                  isNot(contains(code)),
                ),
          ),
        );
      }
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

  test('a business room remains non-E2EE after native sync', () async {
    bridge.rooms = const <RustMatrixEncryptedRoom>[
      RustMatrixEncryptedRoom(
        roomId: '!legacy:api.weave.test',
        title: 'Legacy',
        unreadCount: 0,
        encrypted: false,
      ),
    ];

    final conversations = await repository().loadConversations();

    expect(conversations.single.previewType, ChatConversationPreviewType.none);
    expect(conversations.single.previewText, isNull);
  });
}
