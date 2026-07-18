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
import 'package:weave/features/chat/presentation/providers/chat_repository_provider.dart';
import 'package:weave/features/chat/presentation/providers/chat_security_repository_provider.dart';
import 'package:weave/features/app/domain/entities/integration_invalidation.dart';
import 'package:weave/features/app/domain/entities/workspace_capability_snapshot.dart';
import 'package:weave/features/app/domain/entities/workspace_connection_state.dart';
import 'package:weave/features/app/domain/entities/workspace_home_snapshot.dart';
import 'package:weave/features/app/presentation/providers/workspace_connection_provider.dart';
import 'package:weave/features/files/domain/entities/directory_listing.dart';
import 'package:weave/features/files/domain/entities/file_entry.dart';
import 'package:weave/features/files/domain/entities/files_connection_state.dart';
import 'package:weave/features/files/presentation/providers/files_repository_provider.dart';
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
      WorkspaceHomeSnapshot? homeSnapshot,
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
          filesRepositoryProvider.overrideWithValue(
            filesRepository ??
                FakeFilesRepository(
                  connectionState: const FilesConnectionState.disconnected(),
                ),
          ),
          weaveApiWorkspaceHomeProvider.overrideWith(
            (ref) async => homeSnapshot,
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
      WorkspaceHomeSnapshot? homeSnapshot,
    }) async {
      await tester.pumpWidget(
        buildApp(
          chatRepository: chatRepository,
          filesRepository: filesRepository,
          preferencesStore: preferencesStore,
          homeSnapshot: homeSnapshot,
        ),
      );
      await tester.pumpAndSettle();
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

    testWidgets(
      'keeps every current destination labeled and at least 48 by 48',
      (tester) async {
        final semantics = tester.ensureSemantics();
        await pumpReadyShell(tester);

        const destinationLabels = [
          'Home',
          'Chat',
          'Files',
          'Calendar',
          'Settings',
        ];
        expect(
          find.byType(NavigationDestination),
          findsNWidgets(destinationLabels.length),
        );
        for (var index = 0; index < destinationLabels.length; index++) {
          final destination = find.byType(NavigationDestination).at(index);
          final size = tester.getSize(destination);
          expect(size.width, greaterThanOrEqualTo(48));
          expect(size.height, greaterThanOrEqualTo(48));
          expect(
            find.bySemanticsLabel(RegExp(destinationLabels[index])),
            findsWidgets,
          );
        }
        semantics.dispose();
      },
    );

    testWidgets('keeps the current shell usable at large Dynamic Type', (
      tester,
    ) async {
      tester.view.physicalSize = const Size(390, 844);
      tester.view.devicePixelRatio = 1;
      tester.platformDispatcher.textScaleFactorTestValue = 2;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);
      addTearDown(tester.platformDispatcher.clearTextScaleFactorTestValue);

      await pumpReadyShell(tester);

      expect(find.byType(NavigationBar), findsOneWidget);
      expect(find.text('Home'), findsOneWidget);
      expect(find.text('Chat'), findsOneWidget);
      expect(find.text('Files'), findsOneWidget);
      expect(find.text('Calendar'), findsOneWidget);
      expect(find.text('Settings'), findsOneWidget);
      expect(tester.takeException(), isNull);
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

      expect(find.text('Appearance'), findsOneWidget);
      expect(find.text('Language'), findsOneWidget);
      expect(find.text('Profile'), findsOneWidget);
      expect(find.text('Weave profile'), findsNothing);
      expect(find.text('Save profile'), findsNothing);
      expect(find.text('Shell modules'), findsOneWidget);
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
        find.widgetWithText(Chip, 'Calendar: Unavailable'),
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

    testWidgets(
      'shows generic authorized activity without content or opaque references',
      (tester) async {
        final chatRepository = FakeChatRepository();
        final filesRepository = FakeFilesRepository(
          connectionState: FilesConnectionState.connected(
            baseUrl: Uri.parse('https://api.weave.test/api'),
            accountLabel: 'Weave files',
          ),
        );
        final home = WorkspaceHomeSnapshot(
          version: 2,
          readiness: WorkspaceCapabilityReadiness.ready,
          summary: 'Weave Home is ready.',
          sections: const [],
          actions: const [],
          recentActivity: [
            WorkspaceHomeActivity(
              activityRef:
                  'activity:sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa',
              domain: WorkspaceHomeActivityDomain.files,
              action: WorkspaceHomeActivityAction.filesWebDavWriteCompleted,
              occurredAt: DateTime.now().toUtc(),
              visibility: WorkspaceHomeActivityVisibility.workspace,
              actorRefHash:
                  'sha256:bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb',
              actorIsCurrentUser: false,
              supportSafe: true,
            ),
          ],
          supportSafe: true,
        );

        await pumpReadyShell(
          tester,
          chatRepository: chatRepository,
          filesRepository: filesRepository,
          homeSnapshot: home,
        );

        expect(find.text('Recent activity'), findsOneWidget);
        expect(
          find.text('A workspace member completed a Files change'),
          findsOneWidget,
        );
        expect(
          find.bySemanticsLabel(
            RegExp(
              'A workspace member completed a Files change.*Shared workspace activity',
            ),
          ),
          findsOneWidget,
        );
        expect(find.textContaining('sha256:'), findsNothing);
        expect(find.textContaining('Roadmap.md'), findsNothing);
        expect(find.textContaining('Standup notes'), findsNothing);
        expect(chatRepository.connectCalls, 0);
        expect(filesRepository.requestedPaths, isEmpty);
      },
    );

    testWidgets('shows a generic empty state for an empty projection', (
      tester,
    ) async {
      await pumpReadyShell(
        tester,
        homeSnapshot: const WorkspaceHomeSnapshot(
          version: 2,
          readiness: WorkspaceCapabilityReadiness.ready,
          summary: 'Weave Home is ready.',
          sections: [],
          actions: [],
          recentActivity: [],
          supportSafe: true,
        ),
      );

      expect(
        find.text('No completed workspace activity is visible yet.'),
        findsOneWidget,
      );
    });
  });
}
