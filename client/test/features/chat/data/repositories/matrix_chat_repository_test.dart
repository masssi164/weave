import 'package:flutter_test/flutter_test.dart';
import 'package:weave/features/chat/data/repositories/matrix_chat_repository.dart';
import 'package:weave/features/chat/data/services/matrix_conversation_service.dart';
import 'package:weave/features/chat/data/services/matrix_room_service.dart';
import 'package:weave/features/chat/data/services/matrix_service_types.dart';
import 'package:weave/features/chat/data/services/matrix_session_service.dart';
import 'package:weave/features/chat/domain/entities/chat_conversation.dart';
import 'package:weave/features/chat/domain/entities/chat_failure.dart';
import 'package:weave/features/chat/domain/entities/chat_message.dart';
import 'package:weave/features/server_config/domain/entities/server_configuration.dart';
import 'package:weave/features/server_config/domain/repositories/'
    'server_configuration_repository.dart';

import '../../../../helpers/server_config_test_data.dart';

class _FakeMatrixSessionService implements MatrixSessionService {
  Uri? lastHomeserverForConnect;

  @override
  Future<void> connect({required Uri homeserver}) async {
    lastHomeserverForConnect = homeserver;
  }

  @override
  Future<void> signOut() async {}

  @override
  Future<void> clearSession() async {}
}

class _FakeMatrixConversationService implements MatrixConversationService {
  Uri? lastHomeserverForLoad;

  List<MatrixRoomSnapshot> rooms = const <MatrixRoomSnapshot>[];

  @override
  Future<List<MatrixRoomSnapshot>> loadConversations({
    required Uri homeserver,
  }) async {
    lastHomeserverForLoad = homeserver;
    return rooms;
  }
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

class _FakeMatrixRoomService implements MatrixRoomService {
  MatrixRoomTimelineSnapshot? timeline;
  Object? loadFailure;
  Object? sendFailure;
  Object? markReadFailure;

  @override
  Future<MatrixRoomTimelineSnapshot> loadRoomTimeline({
    required Uri homeserver,
    required String roomId,
  }) async {
    final failure = loadFailure;
    if (failure != null) throw failure;
    return timeline ??
        MatrixRoomTimelineSnapshot(
          roomId: roomId,
          roomTitle: 'Project',
          isInvite: false,
          canSendMessages: true,
          messages: const <MatrixTimelineMessageSnapshot>[],
        );
  }

  @override
  Future<void> markRoomRead({
    required Uri homeserver,
    required String roomId,
  }) async {
    final failure = markReadFailure;
    if (failure != null) throw failure;
  }

  @override
  Future<void> sendMessage({
    required Uri homeserver,
    required String roomId,
    required String message,
  }) async {
    final failure = sendFailure;
    if (failure != null) throw failure;
  }
}

void main() {
  group('MatrixChatRepository', () {
    test('loads conversations from the configured Matrix homeserver', () async {
      final conversationService = _FakeMatrixConversationService()
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
        sessionService: _FakeMatrixSessionService(),
        conversationService: conversationService,
        roomService: _FakeMatrixRoomService(),
        serverConfigurationRepository: _FakeServerConfigurationRepository(
          buildTestConfiguration(),
        ),
      );

      final conversations = await repository.loadConversations();

      expect(
        conversationService.lastHomeserverForLoad.toString(),
        'https://api.home.internal',
      );
      expect(conversations, hasLength(1));
      expect(conversations.first.previewType, ChatConversationPreviewType.text);
      expect(conversations.first.unreadCount, 3);
    });

    test(
      'sorts conversations by recency, then unread count, then title',
      () async {
        final conversationService = _FakeMatrixConversationService()
          ..rooms = <MatrixRoomSnapshot>[
            MatrixRoomSnapshot(
              id: '!later:home.internal',
              title: 'Later unread',
              previewType: MatrixRoomPreviewType.text,
              previewText: 'Later',
              lastActivityAt: DateTime(2026, 4, 22, 9),
              unreadCount: 1,
              isInvite: false,
              isDirectMessage: false,
            ),
            MatrixRoomSnapshot(
              id: '!earlier:home.internal',
              title: 'Earlier',
              previewType: MatrixRoomPreviewType.text,
              previewText: 'Earlier',
              lastActivityAt: DateTime(2026, 4, 21, 9),
              unreadCount: 99,
              isInvite: false,
              isDirectMessage: false,
            ),
            MatrixRoomSnapshot(
              id: '!same-time-more-unread:home.internal',
              title: 'Same time more unread',
              previewType: MatrixRoomPreviewType.text,
              previewText: 'Same time',
              lastActivityAt: DateTime(2026, 4, 22, 9),
              unreadCount: 5,
              isInvite: false,
              isDirectMessage: false,
            ),
            const MatrixRoomSnapshot(
              id: '!no-activity-a:home.internal',
              title: 'Alpha',
              previewType: MatrixRoomPreviewType.none,
              unreadCount: 0,
              isInvite: false,
              isDirectMessage: false,
            ),
            const MatrixRoomSnapshot(
              id: '!no-activity-z:home.internal',
              title: 'Zulu',
              previewType: MatrixRoomPreviewType.none,
              unreadCount: 0,
              isInvite: false,
              isDirectMessage: false,
            ),
          ];
        final repository = MatrixChatRepository(
          sessionService: _FakeMatrixSessionService(),
          conversationService: conversationService,
          roomService: _FakeMatrixRoomService(),
          serverConfigurationRepository: _FakeServerConfigurationRepository(
            buildTestConfiguration(),
          ),
        );

        final conversations = await repository.loadConversations();

        expect(
          conversations.map((conversation) => conversation.title).toList(),
          <String>[
            'Same time more unread',
            'Later unread',
            'Earlier',
            'Alpha',
            'Zulu',
          ],
        );
      },
    );

    test('connect uses the configured Matrix homeserver', () async {
      final sessionService = _FakeMatrixSessionService();
      final repository = MatrixChatRepository(
        sessionService: sessionService,
        conversationService: _FakeMatrixConversationService(),
        roomService: _FakeMatrixRoomService(),
        serverConfigurationRepository: _FakeServerConfigurationRepository(
          buildTestConfiguration(),
        ),
      );

      await repository.connect();

      expect(
        sessionService.lastHomeserverForConnect.toString(),
        'https://api.home.internal',
      );
    });

    test(
      'maps Matrix timeline message safety states into chat entities',
      () async {
        final roomService = _FakeMatrixRoomService()
          ..timeline = MatrixRoomTimelineSnapshot(
            roomId: '!room:home.internal',
            roomTitle: 'Project',
            isInvite: false,
            canSendMessages: true,
            messages: [
              MatrixTimelineMessageSnapshot(
                id: r'$sending',
                senderId: '@me:home.internal',
                senderDisplayName: 'Me',
                sentAt: DateTime(2026, 5, 31, 10),
                isMine: true,
                deliveryState: MatrixMessageDeliveryState.sending,
                contentType: MatrixMessageContentType.encrypted,
              ),
              MatrixTimelineMessageSnapshot(
                id: r'$failed',
                senderId: '@alex:home.internal',
                senderDisplayName: 'Alex',
                sentAt: DateTime(2026, 5, 31, 10, 1),
                isMine: false,
                deliveryState: MatrixMessageDeliveryState.failed,
                contentType: MatrixMessageContentType.unsupported,
              ),
            ],
          );
        final repository = MatrixChatRepository(
          sessionService: _FakeMatrixSessionService(),
          conversationService: _FakeMatrixConversationService(),
          roomService: roomService,
          serverConfigurationRepository: _FakeServerConfigurationRepository(
            buildTestConfiguration(),
          ),
        );

        final timeline = await repository.loadRoomTimeline(
          '!room:home.internal',
        );

        expect(
          timeline.messages.map((message) => message.deliveryState),
          <ChatMessageDeliveryState>[
            ChatMessageDeliveryState.sending,
            ChatMessageDeliveryState.failed,
          ],
        );
        expect(
          timeline.messages.map((message) => message.contentType),
          <ChatMessageContentType>[
            ChatMessageContentType.encrypted,
            ChatMessageContentType.unsupported,
          ],
        );
        expect(
          timeline.messages.map((message) => message.text),
          everyElement(isNull),
        );
      },
    );

    test(
      'send and read-marker failures remain support-safe chat failures',
      () async {
        final roomService = _FakeMatrixRoomService()
          ..sendFailure = const ChatFailure.protocol('Chat send failed safely.')
          ..markReadFailure = const ChatFailure.protocol(
            'Chat read marker failed safely.',
          );
        final repository = MatrixChatRepository(
          sessionService: _FakeMatrixSessionService(),
          conversationService: _FakeMatrixConversationService(),
          roomService: roomService,
          serverConfigurationRepository: _FakeServerConfigurationRepository(
            buildTestConfiguration(),
          ),
        );

        await expectLater(
          repository.sendMessage(
            roomId: '!room:home.internal',
            message: 'hello',
          ),
          throwsA(
            isA<ChatFailure>()
                .having(
                  (failure) => failure.message,
                  'message',
                  contains('Chat'),
                )
                .having(
                  (failure) => failure.message,
                  'message',
                  isNot(contains('access_token')),
                ),
          ),
        );
        await expectLater(
          repository.markRoomRead('!room:home.internal'),
          throwsA(
            isA<ChatFailure>()
                .having(
                  (failure) => failure.message,
                  'message',
                  contains('Chat'),
                )
                .having(
                  (failure) => failure.message,
                  'message',
                  isNot(contains('homeserver')),
                ),
          ),
        );
      },
    );

    test('fails clearly when setup is missing', () async {
      final repository = MatrixChatRepository(
        sessionService: _FakeMatrixSessionService(),
        conversationService: _FakeMatrixConversationService(),
        roomService: _FakeMatrixRoomService(),
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
