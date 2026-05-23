import 'package:flutter_test/flutter_test.dart';
import 'package:weave/features/chat/data/repositories/matrix_chat_repository.dart';
import 'package:weave/features/chat/data/services/matrix_conversation_service.dart';
import 'package:weave/features/chat/data/services/matrix_room_service.dart';
import 'package:weave/features/chat/data/services/matrix_service_types.dart';
import 'package:weave/features/chat/data/services/matrix_session_service.dart';
import 'package:weave/features/chat/domain/entities/chat_conversation.dart';
import 'package:weave/features/chat/domain/entities/chat_failure.dart';
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
  @override
  Future<MatrixRoomTimelineSnapshot> loadRoomTimeline({
    required Uri homeserver,
    required String roomId,
  }) async {
    throw UnimplementedError();
  }

  @override
  Future<void> markRoomRead({
    required Uri homeserver,
    required String roomId,
  }) async {}

  @override
  Future<void> sendMessage({
    required Uri homeserver,
    required String roomId,
    required String message,
  }) async {}
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
        'https://matrix.home.internal',
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
        'https://matrix.home.internal',
      );
    });

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
