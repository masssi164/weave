import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:weave/core/persistence/flutter_secure_store.dart';
import 'package:weave/core/persistence/shared_preferences_store.dart';
import 'package:weave/features/auth/data/dtos/auth_session_dto.dart';
import 'package:weave/features/auth/data/repositories/oidc_auth_session_repository.dart';
import 'package:weave/features/auth/data/services/flutter_appauth_oidc_client.dart';
import 'package:weave/features/auth/data/services/oidc_client.dart';
import 'package:weave/features/chat/domain/entities/chat_conversation.dart';
import 'package:weave/features/chat/domain/entities/chat_message.dart';
import 'package:weave/features/chat/domain/entities/chat_room_timeline.dart';
import 'package:weave/features/chat/presentation/providers/chat_repository_provider.dart';
import 'package:weave/features/chat/presentation/providers/chat_security_repository_provider.dart';
import 'package:weave/features/files/domain/entities/directory_listing.dart';
import 'package:weave/features/files/domain/entities/file_entry.dart';
import 'package:weave/features/files/domain/entities/files_connection_state.dart';
import 'package:weave/features/files/presentation/providers/files_repository_provider.dart';
import 'package:weave/features/onboarding/presentation/providers/first_run_status_provider.dart';
import 'package:weave/features/profile/presentation/providers/user_profile_provider.dart';
import 'package:weave/features/server_config/domain/entities/server_configuration.dart';
import 'package:weave/features/server_config/domain/repositories/server_configuration_repository.dart';
import 'package:weave/features/server_config/presentation/providers/server_configuration_repository_provider.dart';
import 'package:weave/features/shell/data/repositories/shared_preferences_shell_module_preferences_repository.dart';
import 'package:weave/main.dart';

import '../../helpers/auth_test_data.dart';
import '../../helpers/fake_chat_repository.dart';
import '../../helpers/fake_chat_security_repository.dart';
import '../../helpers/fake_files_repository.dart';
import '../../helpers/first_run_status_fixture.dart';
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
    ProviderScope buildApp({
      FakeChatRepository? chatRepository,
      FakeFilesRepository? filesRepository,
      InMemoryPreferencesStore? preferencesStore,
    }) {
      final secureStore = InMemorySecureStore({
        authSessionStorageKey: AuthSessionDto.fromSession(
          buildTestAuthSession(),
        ).encode(),
      });

      return ProviderScope(
        overrides: [
          preferencesStoreProvider.overrideWith(
            (ref) => preferencesStore ?? InMemoryPreferencesStore(),
          ),
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
          userProfileProvider.overrideWith((ref) async => null),
          firstRunStatusProvider.overrideWith(
            (ref) async => buildTestFirstRunStatus(),
          ),
          filesRepositoryProvider.overrideWithValue(
            filesRepository ??
                FakeFilesRepository(
                  connectionState: const FilesConnectionState.disconnected(),
                ),
          ),
        ],
        child: const WeaveApp(),
      );
    }

    Future<void> pumpReadyShell(
      WidgetTester tester, {
      FakeChatRepository? chatRepository,
      FakeFilesRepository? filesRepository,
      InMemoryPreferencesStore? preferencesStore,
    }) async {
      await tester.pumpWidget(
        buildApp(
          chatRepository: chatRepository,
          filesRepository: filesRepository,
          preferencesStore: preferencesStore,
        ),
      );
      await tester.pumpAndSettle();
      await _continueFirstRunIfPresent(tester);
    }

    testWidgets('renders the Release 1 bottom navigation destinations', (
      tester,
    ) async {
      await pumpReadyShell(tester);

      expect(find.byType(NavigationBar), findsOneWidget);
      expect(find.byType(NavigationDestination), findsNWidgets(3));
      expect(find.byIcon(Icons.calendar_today_outlined), findsNothing);
      expect(find.byIcon(Icons.dashboard_outlined), findsNothing);
      expect(find.text('Workspace overview'), findsOneWidget);
    });

    testWidgets(
      'hides recent activity when shell module preferences disable it',
      (tester) async {
        final preferencesStore = InMemoryPreferencesStore({
          shellModulePreferencesStorageKey:
              '{"hiddenModules":["recentActivity"]}',
        });

        await pumpReadyShell(tester, preferencesStore: preferencesStore);

        expect(find.text('Recent activity'), findsNothing);
        expect(find.text('Workspace overview'), findsOneWidget);
        expect(find.byType(NavigationBar), findsOneWidget);
        expect(find.byIcon(Icons.chat_bubble), findsOneWidget);
        expect(find.byIcon(Icons.settings_outlined), findsOneWidget);
      },
    );

    testWidgets('navigates to settings from the bottom navigation bar', (
      tester,
    ) async {
      await pumpReadyShell(tester);

      await tester.tap(find.widgetWithText(NavigationDestination, 'Settings'));
      await tester.pumpAndSettle();

      expect(find.text('Server Configuration'), findsOneWidget);
    });

    testWidgets('applies persisted shell module order', (tester) async {
      final preferencesStore = InMemoryPreferencesStore({
        shellModulePreferencesStorageKey:
            '{"hiddenModules":[],"moduleOrder":["recentActivity","workspaceOverview"]}',
      });

      await pumpReadyShell(tester, preferencesStore: preferencesStore);

      final recentTop = tester.getTopLeft(find.text('Recent activity')).dy;
      final overviewTop = tester.getTopLeft(find.text('Workspace overview')).dy;
      expect(recentTop, lessThan(overviewTop));
    });

    testWidgets('shows recent room and file quick links in the shell', (
      tester,
    ) async {
      final chatRepository = FakeChatRepository(
        loadConversationsHandler: () async => [
          ChatConversation(
            id: '!general:weave.local',
            title: 'General',
            previewType: ChatConversationPreviewType.text,
            previewText: 'Standup notes are ready',
            lastActivityAt: DateTime.now().subtract(const Duration(minutes: 5)),
            unreadCount: 0,
            isInvite: false,
            isDirectMessage: false,
          ),
        ],
      );
      final filesRepository = FakeFilesRepository(
        connectionState: FilesConnectionState.connected(
          baseUrl: Uri.parse('https://api.weave.local/api'),
          accountLabel: 'Weave files',
        ),
        listings: {
          '/': DirectoryListing(
            path: '/',
            entries: [
              FileEntry(
                id: 'file-1',
                name: 'Roadmap.md',
                path: '/Planning/Roadmap.md',
                isDirectory: false,
                modifiedAt: DateTime.now().subtract(const Duration(minutes: 3)),
              ),
            ],
          ),
        },
      );

      await pumpReadyShell(
        tester,
        chatRepository: chatRepository,
        filesRepository: filesRepository,
      );

      expect(find.text('Recent activity'), findsOneWidget);
      expect(find.widgetWithText(ActionChip, 'General'), findsOneWidget);
      expect(find.widgetWithText(ActionChip, 'Roadmap.md'), findsOneWidget);

      expect(
        find.ancestor(
          of: find.widgetWithText(ActionChip, 'General'),
          matching: find.byWidgetPredicate(
            (widget) =>
                widget is Semantics &&
                (widget.properties.label?.contains('Open room General') ??
                    false) &&
                (widget.properties.label?.contains('Standup notes are ready') ??
                    false),
          ),
        ),
        findsOneWidget,
      );
    });

    testWidgets('opens a recent room quick link with the app route', (
      tester,
    ) async {
      final conversation = ChatConversation(
        id: '!general:weave.local',
        title: 'General',
        previewType: ChatConversationPreviewType.text,
        previewText: 'Standup notes are ready',
        lastActivityAt: DateTime.now().subtract(const Duration(minutes: 5)),
        unreadCount: 0,
        isInvite: false,
        isDirectMessage: false,
      );
      final chatRepository = FakeChatRepository(
        loadConversationsHandler: () async => [conversation],
        loadRoomTimelineHandler: (roomId) async => ChatRoomTimeline(
          roomId: roomId,
          roomTitle: 'General',
          isInvite: false,
          canSendMessages: true,
          messages: [
            ChatMessage(
              id: 'message-1',
              senderId: '@alice:weave.local',
              senderDisplayName: 'Alice',
              sentAt: DateTime.now().subtract(const Duration(minutes: 5)),
              isMine: false,
              deliveryState: ChatMessageDeliveryState.sent,
              contentType: ChatMessageContentType.text,
              text: 'Standup notes are ready',
            ),
          ],
        ),
      );

      await pumpReadyShell(tester, chatRepository: chatRepository);

      await tester.tap(find.widgetWithText(ActionChip, 'General'));
      await tester.pumpAndSettle();

      expect(chatRepository.loadRoomTimelineCalls, 1);
    });

    testWidgets('opens a recent file quick link at its folder context', (
      tester,
    ) async {
      final filesRepository = FakeFilesRepository(
        connectionState: FilesConnectionState.connected(
          baseUrl: Uri.parse('https://api.weave.local/api'),
          accountLabel: 'Weave files',
        ),
        listings: {
          '/': DirectoryListing(
            path: '/',
            entries: [
              FileEntry(
                id: 'file-1',
                name: 'Roadmap.md',
                path: '/Planning/Roadmap.md',
                isDirectory: false,
                modifiedAt: DateTime.now().subtract(const Duration(minutes: 3)),
              ),
            ],
          ),
          '/Planning': const DirectoryListing(
            path: '/Planning',
            entries: [
              FileEntry(
                id: 'file-1',
                name: 'Roadmap.md',
                path: '/Planning/Roadmap.md',
                isDirectory: false,
              ),
            ],
          ),
        },
      );

      await pumpReadyShell(tester, filesRepository: filesRepository);

      final roadmapChip = find.widgetWithText(ActionChip, 'Roadmap.md');
      await tester.ensureVisible(roadmapChip);
      await tester.tap(roadmapChip);
      await tester.pumpAndSettle();

      expect(filesRepository.requestedPaths, contains('/Planning'));
      expect(find.text('/Planning'), findsWidgets);
    });
  });
}

Future<void> _continueFirstRunIfPresent(WidgetTester tester) async {
  final continueButton = find.text('Continue to chat');
  if (continueButton.evaluate().isEmpty) {
    return;
  }

  await tester.ensureVisible(continueButton);
  await tester.pumpAndSettle();
  await tester.tap(continueButton);
  await tester.pumpAndSettle();
}
