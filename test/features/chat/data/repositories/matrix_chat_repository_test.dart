import 'package:flutter_test/flutter_test.dart';
import 'package:weave/features/chat/data/repositories/matrix_chat_repository.dart';
import 'package:weave/features/chat/data/services/matrix_conversation_service.dart';
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

    test('connect uses the configured Matrix homeserver', () async {
      final sessionService = _FakeMatrixSessionService();
      final repository = MatrixChatRepository(
        sessionService: sessionService,
        conversationService: _FakeMatrixConversationService(),
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
