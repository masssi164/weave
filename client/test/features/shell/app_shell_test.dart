import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:weave/core/l10n/shared_preferences_app_locale_preference_repository.dart';
import 'package:weave/core/persistence/flutter_secure_store.dart';
import 'package:weave/core/persistence/shared_preferences_store.dart';
import 'package:weave/core/theme/shared_preferences_app_theme_preference_repository.dart';
import 'package:weave/features/auth/data/dtos/auth_session_dto.dart';
import 'package:weave/features/auth/data/repositories/oidc_auth_session_repository.dart';
import 'package:weave/features/auth/data/services/flutter_appauth_oidc_client.dart';
import 'package:weave/features/auth/data/services/oidc_client.dart';
import 'package:weave/features/chat/domain/entities/chat_conversation.dart';
import 'package:weave/features/chat/domain/entities/chat_message.dart';
import 'package:weave/features/chat/domain/entities/chat_room_timeline.dart';
import 'package:weave/features/chat/presentation/providers/chat_repository_provider.dart';
import 'package:weave/features/chat/presentation/providers/chat_security_repository_provider.dart';
import 'package:weave/features/app/domain/entities/integration_invalidation.dart';
import 'package:weave/features/app/domain/entities/workspace_capability_snapshot.dart';
import 'package:weave/features/app/domain/entities/workspace_connection_state.dart';
import 'package:weave/features/app/presentation/providers/workspace_connection_provider.dart';
import 'package:weave/features/files/domain/entities/directory_listing.dart';
import 'package:weave/features/files/domain/entities/file_entry.dart';
import 'package:weave/features/files/domain/entities/files_connection_state.dart';
import 'package:weave/features/files/presentation/providers/files_repository_provider.dart';
import 'package:weave/features/onboarding/domain/entities/first_run_status.dart';
import 'package:weave/features/onboarding/presentation/providers/first_run_status_provider.dart';
import 'package:weave/features/profile/domain/entities/user_profile.dart';
import 'package:weave/features/profile/presentation/providers/user_profile_provider.dart';
import 'package:weave/features/server_config/domain/entities/server_configuration.dart';
import 'package:weave/features/server_config/domain/repositories/server_configuration_repository.dart';
import 'package:weave/features/server_config/presentation/providers/server_configuration_repository_provider.dart';
import 'package:weave/features/shell/data/repositories/shared_preferences_shell_module_preferences_repository.dart';
import 'package:weave/features/shell/presentation/shell_workspace_status.dart';
import 'package:weave/integrations/weave_api/presentation/providers/weave_api_provider.dart';
import 'package:weave/l10n/generated/app_localizations.dart';
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

const _memberProfile = UserProfile(
  userId: 'member-1',
  username: 'member',
  email: 'member@example.test',
  emailVerified: true,
  displayName: 'Workspace Member',
  locale: 'en',
  timezone: 'Europe/Berlin',
  roles: ['member'],
  groups: ['workspace-default'],
);

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
          userProfileProvider.overrideWith((ref) async => _memberProfile),
          firstRunStatusProvider.overrideWith(
            (ref) async =>
                FirstRunLoadResult.authenticated(buildTestFirstRunStatus()),
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

    testWidgets('renders the core bottom navigation destinations', (
      tester,
    ) async {
      await pumpReadyShell(tester);

      expect(find.byType(NavigationBar), findsOneWidget);
      final navigationBar = find.byType(NavigationBar);
      expect(
        find.descendant(of: navigationBar, matching: find.byIcon(Icons.home)),
        findsOneWidget,
      );
      expect(
        find.descendant(
          of: navigationBar,
          matching: find.byIcon(Icons.chat_bubble_outline),
        ),
        findsOneWidget,
      );
      expect(
        find.descendant(
          of: navigationBar,
          matching: find.byIcon(Icons.folder_outlined),
        ),
        findsOneWidget,
      );
      expect(
        find.descendant(
          of: navigationBar,
          matching: find.byIcon(Icons.calendar_month_outlined),
        ),
        findsOneWidget,
      );
      expect(
        find.descendant(
          of: navigationBar,
          matching: find.byIcon(Icons.settings_outlined),
        ),
        findsOneWidget,
      );
      expect(find.byIcon(Icons.dashboard_outlined), findsNothing);
      expect(
        find.descendant(of: navigationBar, matching: find.text('Home')),
        findsOneWidget,
      );
      expect(
        find.descendant(of: navigationBar, matching: find.text('Chat')),
        findsOneWidget,
      );
      expect(
        find.descendant(of: navigationBar, matching: find.text('Files')),
        findsOneWidget,
      );
      expect(
        find.descendant(of: navigationBar, matching: find.text('Calendar')),
        findsOneWidget,
      );
      expect(
        find.descendant(of: navigationBar, matching: find.text('Settings')),
        findsOneWidget,
      );
    });

    testWidgets('applies the persisted personal theme across the shell', (
      tester,
    ) async {
      await pumpReadyShell(
        tester,
        preferencesStore: InMemoryPreferencesStore({
          appThemePreferenceStorageKey: 'dark',
        }),
      );

      final navigationContext = tester.element(find.byType(NavigationBar));
      expect(Theme.of(navigationContext).brightness, Brightness.dark);
      expect(find.byIcon(Icons.home), findsOneWidget);
      expect(find.byIcon(Icons.chat_bubble_outline), findsWidgets);
      expect(find.byIcon(Icons.settings_outlined), findsOneWidget);
    });

    testWidgets('applies the persisted app language to MaterialApp locale', (
      tester,
    ) async {
      await pumpReadyShell(
        tester,
        preferencesStore: InMemoryPreferencesStore({
          appLocalePreferenceStorageKey: 'de',
        }),
      );

      final app = tester.widget<MaterialApp>(find.byType(MaterialApp));
      expect(app.locale, const Locale('de'));
      expect(find.text('Einstellungen'), findsOneWidget);
      expect(find.text('Settings'), findsNothing);
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
        expect(find.byType(NavigationBar), findsOneWidget);
        expect(find.byIcon(Icons.home), findsOneWidget);
        expect(find.byIcon(Icons.chat_bubble_outline), findsWidgets);
        expect(find.byIcon(Icons.settings_outlined), findsOneWidget);
      },
    );

    testWidgets('navigates to settings from the bottom navigation bar', (
      tester,
    ) async {
      await pumpReadyShell(tester);

      await tester.tap(find.byIcon(Icons.settings_outlined));
      await tester.pumpAndSettle();

      expect(find.text('Settings sections'), findsOneWidget);
      expect(find.text('Appearance'), findsWidgets);
      expect(find.text('Language'), findsWidgets);
      expect(find.text('Profile'), findsWidgets);
      expect(find.text('Weave profile'), findsNothing);
      expect(find.text('Save profile'), findsNothing);
      expect(find.text('Shell modules'), findsWidgets);
      expect(find.text('Workspace setup is admin-only'), findsNothing);
      expect(find.text('Workspace Readiness'), findsNothing);
      expect(find.text('Server Configuration'), findsNothing);
    });

    testWidgets('maps Files and Calendar destinations to matching branches', (
      tester,
    ) async {
      final filesRepository = FakeFilesRepository(
        connectionState: FilesConnectionState.connected(
          baseUrl: Uri.parse('https://api.weave.test/api'),
          accountLabel: 'Weave files',
        ),
        listings: {
          '/': DirectoryListing(
            path: '/',
            entries: [
              FileEntry(
                id: 'file-1',
                name: 'Roadmap.md',
                path: '/Roadmap.md',
                isDirectory: false,
                modifiedAt: DateTime(2026, 6, 26, 12),
              ),
            ],
          ),
        },
      );

      await pumpReadyShell(tester, filesRepository: filesRepository);

      await tester.tap(find.byIcon(Icons.folder_outlined));
      await tester.pumpAndSettle();

      expect(find.text('Roadmap.md'), findsOneWidget);

      await tester.tap(find.byIcon(Icons.calendar_month_outlined));
      await tester.pumpAndSettle();

      expect(find.text('Roadmap.md'), findsNothing);
    });

    testWidgets('workspace status localizes backend recovery states', (
      tester,
    ) async {
      await tester.pumpWidget(
        ProviderScope(
          overrides: [
            workspaceConnectionStateProvider.overrideWithValue(
              const AsyncData(
                WorkspaceConnectionState(
                  appAuth: IntegrationConnectionState(
                    integration: WorkspaceIntegration.appAuth,
                    status: IntegrationConnectionStatus.connected,
                  ),
                  chat: IntegrationConnectionState(
                    integration: WorkspaceIntegration.chat,
                    status: IntegrationConnectionStatus.connected,
                  ),
                  files: IntegrationConnectionState(
                    integration: WorkspaceIntegration.files,
                    status: IntegrationConnectionStatus.connected,
                  ),
                ),
              ),
            ),
            workspaceCapabilitySnapshotProvider.overrideWithValue(
              const AsyncData(
                WorkspaceCapabilitySnapshot(
                  shellAccess: WorkspaceCapabilityState(
                    capability: WorkspaceCapability.shellAccess,
                    readiness: WorkspaceCapabilityReadiness.ready,
                  ),
                  chat: WorkspaceCapabilityState(
                    capability: WorkspaceCapability.chat,
                    readiness: WorkspaceCapabilityReadiness.degraded,
                    memberImpact: 'RAW SHELL BACKEND MEMBER IMPACT',
                  ),
                  files: WorkspaceCapabilityState(
                    capability: WorkspaceCapability.files,
                    readiness: WorkspaceCapabilityReadiness.ready,
                  ),
                  calendar: WorkspaceCapabilityState(
                    capability: WorkspaceCapability.calendar,
                    readiness: WorkspaceCapabilityReadiness.unavailable,
                  ),
                  boards: WorkspaceCapabilityState(
                    capability: WorkspaceCapability.boards,
                    readiness: WorkspaceCapabilityReadiness.blocked,
                  ),
                ),
              ),
            ),
            weaveApiWorkspaceHomeProvider.overrideWith((ref) async => null),
          ],
          child: const MaterialApp(
            localizationsDelegates: AppLocalizations.localizationsDelegates,
            supportedLocales: AppLocalizations.supportedLocales,
            home: Scaffold(body: ShellWorkspaceStatus()),
          ),
        ),
      );
      await tester.pumpAndSettle();

      expect(find.text('RAW SHELL BACKEND MEMBER IMPACT'), findsNothing);
      expect(find.widgetWithText(Chip, 'Chat: Limited'), findsOneWidget);
      expect(
        find.widgetWithText(Chip, 'Calendar: Coming later'),
        findsOneWidget,
      );
      expect(
        find.widgetWithText(Chip, 'Boards: Admin setup needed'),
        findsOneWidget,
      );
      expect(
        find.byWidgetPredicate(
          (widget) =>
              widget is Semantics &&
              (widget.properties.label?.contains(
                    'Support reference: Not provided.',
                  ) ??
                  false),
        ),
        findsWidgets,
      );
    });

    testWidgets('shows recent room and file quick links in the shell', (
      tester,
    ) async {
      final chatRepository = FakeChatRepository(
        loadConversationsHandler: () async => [
          ChatConversation(
            id: '!general:weave.test',
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
          baseUrl: Uri.parse('https://api.weave.test/api'),
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

    testWidgets('prioritizes unread rooms in recent activity quick links', (
      tester,
    ) async {
      final now = DateTime(2026, 5, 29, 12);
      final chatRepository = FakeChatRepository(
        loadConversationsHandler: () async => [
          ChatConversation(
            id: '!quiet-latest:weave.test',
            title: 'Quiet latest',
            previewType: ChatConversationPreviewType.text,
            previewText: 'Latest quiet update',
            lastActivityAt: now.subtract(const Duration(minutes: 1)),
            unreadCount: 0,
            isInvite: false,
            isDirectMessage: false,
          ),
          const ChatConversation(
            id: '!unread-older:weave.test',
            title: 'Unread older',
            previewType: ChatConversationPreviewType.text,
            previewText: 'Still unread',
            unreadCount: 1,
            isInvite: false,
            isDirectMessage: false,
          ),
        ],
      );

      await pumpReadyShell(tester, chatRepository: chatRepository);

      expect(find.widgetWithText(ActionChip, 'Unread older'), findsOneWidget);
      expect(find.widgetWithText(ActionChip, 'Quiet latest'), findsOneWidget);
      expect(
        tester.getTopLeft(find.widgetWithText(ActionChip, 'Unread older')).dx,
        lessThan(
          tester.getTopLeft(find.widgetWithText(ActionChip, 'Quiet latest')).dx,
        ),
      );
    });

    testWidgets('opens a recent room quick link with the app route', (
      tester,
    ) async {
      final conversation = ChatConversation(
        id: '!general:weave.test',
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
              senderId: '@alice:weave.test',
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
          baseUrl: Uri.parse('https://api.weave.test/api'),
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
      await tester.drag(find.byType(ListView).first, const Offset(0, -160));
      await tester.pumpAndSettle();
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
