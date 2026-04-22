import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:weave/core/persistence/flutter_secure_store.dart';
import 'package:weave/features/chat/domain/entities/chat_conversation.dart';
import 'package:weave/features/chat/domain/entities/chat_room_timeline.dart';
import 'package:weave/features/auth/data/dtos/auth_session_dto.dart';
import 'package:weave/features/auth/data/repositories/oidc_auth_session_repository.dart';
import 'package:weave/features/auth/data/services/flutter_appauth_oidc_client.dart';
import 'package:weave/features/auth/data/services/oidc_client.dart';
import 'package:weave/features/chat/presentation/chat_room_screen.dart';
import 'package:weave/features/chat/presentation/providers/chat_repository_provider.dart';
import 'package:weave/features/chat/presentation/providers/chat_security_repository_provider.dart';
import 'package:weave/features/server_config/domain/entities/server_configuration.dart';
import 'package:weave/features/server_config/domain/repositories/server_configuration_repository.dart';
import 'package:weave/features/server_config/presentation/providers/server_configuration_repository_provider.dart';
import 'package:weave/main.dart';

import '../../helpers/auth_test_data.dart';
import '../../helpers/fake_chat_repository.dart';
import '../../helpers/fake_chat_security_repository.dart';
import '../../helpers/in_memory_stores.dart';
import '../../helpers/server_config_test_data.dart';

class _FakeServerConfigurationRepository
    implements ServerConfigurationRepository {
  _FakeServerConfigurationRepository({required this.configuration});

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

class _FakeOidcClient implements OidcClient {
  @override
  Future<OidcTokenBundle> authorizeAndExchangeCode(configuration) {
    throw UnimplementedError();
  }

  @override
  Future<void> endSession(configuration, {required String idTokenHint}) async {}

  @override
  Future<OidcTokenBundle> refresh(
    configuration, {
    required String refreshToken,
  }) {
    throw UnimplementedError();
  }
}

void main() {
  group('AppShell', () {
    ProviderScope buildApp({FakeChatRepository? chatRepository}) {
      final secureStore = InMemorySecureStore({
        authSessionStorageKey: AuthSessionDto.fromSession(
          buildTestAuthSession(),
        ).encode(),
      });

      return ProviderScope(
        overrides: [
          serverConfigurationRepositoryProvider.overrideWith(
            (ref) => _FakeServerConfigurationRepository(
              configuration: buildTestConfiguration(),
            ),
          ),
          secureStoreProvider.overrideWithValue(secureStore),
          oidcClientProvider.overrideWithValue(_FakeOidcClient()),
          chatRepositoryProvider.overrideWithValue(
            chatRepository ?? FakeChatRepository(),
          ),
          chatSecurityRepositoryProvider.overrideWithValue(
            FakeChatSecurityRepository(),
          ),
        ],
        child: const WeaveApp(),
      );
    }

    testWidgets('renders the Release 1 bottom navigation destinations', (
      tester,
    ) async {
      await tester.pumpWidget(buildApp());
      await tester.pumpAndSettle();

      expect(find.byType(NavigationBar), findsOneWidget);
      expect(find.byIcon(Icons.chat_bubble), findsOneWidget);
      expect(find.byIcon(Icons.folder_outlined), findsOneWidget);
      expect(find.byIcon(Icons.settings_outlined), findsOneWidget);
      expect(find.byIcon(Icons.calendar_today_outlined), findsNothing);
      expect(find.byIcon(Icons.dashboard_outlined), findsNothing);
    });

    testWidgets('navigates to settings from the bottom navigation bar', (
      tester,
    ) async {
      await tester.pumpWidget(buildApp());
      await tester.pumpAndSettle();

      await tester.tap(find.byIcon(Icons.settings_outlined));
      await tester.pumpAndSettle();

      expect(find.text('Server Configuration'), findsOneWidget);
    });

    testWidgets('shows recent room activity on non-chat shell tabs', (
      tester,
    ) async {
      final chatRepository = FakeChatRepository(
        loadConversationsHandler: () async => <ChatConversation>[
          const ChatConversation(
            id: '!planning:weave.local',
            title: 'Planning',
            previewType: ChatConversationPreviewType.text,
            previewText: 'Roadmap draft is ready',
            unreadCount: 0,
            isInvite: false,
            isDirectMessage: false,
            lastActivityAt: null,
          ),
        ],
      );

      await tester.pumpWidget(buildApp(chatRepository: chatRepository));
      await tester.pumpAndSettle();

      await tester.tap(find.byIcon(Icons.settings_outlined));
      await tester.pumpAndSettle();

      expect(find.text('Recent chat rooms'), findsOneWidget);
      expect(find.text('Planning'), findsOneWidget);
      expect(find.text('Roadmap draft is ready'), findsOneWidget);
    });

    testWidgets('opens a recent room from the shell card', (tester) async {
      const conversation = ChatConversation(
        id: '!planning:weave.local',
        title: 'Planning',
        previewType: ChatConversationPreviewType.text,
        previewText: 'Roadmap draft is ready',
        unreadCount: 0,
        isInvite: false,
        isDirectMessage: false,
        lastActivityAt: null,
      );
      final chatRepository = FakeChatRepository(
        loadConversationsHandler: () async => <ChatConversation>[conversation],
        loadRoomTimelineHandler: (roomId) async => ChatRoomTimeline(
          roomId: roomId,
          roomTitle: 'Planning',
          isInvite: false,
          canSendMessages: true,
          messages: const [],
        ),
      );

      await tester.pumpWidget(buildApp(chatRepository: chatRepository));
      await tester.pumpAndSettle();

      await tester.tap(find.byIcon(Icons.folder_outlined));
      await tester.pumpAndSettle();

      await tester.tap(find.text('Planning'));
      await tester.pumpAndSettle();

      expect(find.byType(ChatRoomScreen), findsOneWidget);
      expect(find.byIcon(Icons.refresh), findsOneWidget);
    });
  });
}
