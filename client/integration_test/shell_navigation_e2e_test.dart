import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:integration_test/integration_test.dart';
import 'package:weave/core/l10n/shared_preferences_app_locale_preference_repository.dart';
import 'package:weave/core/persistence/flutter_secure_store.dart';
import 'package:weave/core/persistence/shared_preferences_store.dart';
import 'package:weave/features/app/domain/entities/workspace_capability_snapshot.dart';
import 'package:weave/features/app/domain/ports/identity_session_port.dart';
import 'package:weave/features/app/presentation/providers/app_application_providers.dart';
import 'package:weave/features/app/presentation/providers/workspace_connection_provider.dart';
import 'package:weave/features/auth/data/dtos/auth_session_dto.dart';
import 'package:weave/features/auth/data/repositories/oidc_auth_session_repository.dart';
import 'package:weave/features/auth/data/services/flutter_appauth_oidc_client.dart';
import 'package:weave/features/auth/data/services/oidc_client.dart';
import 'package:weave/features/calendar/domain/entities/calendar_event.dart';
import 'package:weave/features/calendar/domain/repositories/calendar_repository.dart';
import 'package:weave/features/calendar/presentation/providers/calendar_provider.dart';
import 'package:weave/features/chat/domain/entities/chat_conversation.dart';
import 'package:weave/features/chat/presentation/providers/chat_repository_provider.dart';
import 'package:weave/features/chat/presentation/providers/chat_security_repository_provider.dart';
import 'package:weave/features/files/domain/entities/directory_listing.dart';
import 'package:weave/features/files/domain/entities/file_entry.dart';
import 'package:weave/features/files/domain/entities/files_connection_state.dart';
import 'package:weave/features/files/presentation/providers/files_repository_provider.dart';
import 'package:weave/features/profile/domain/entities/user_profile.dart';
import 'package:weave/features/profile/presentation/providers/user_profile_provider.dart';
import 'package:weave/features/server_config/domain/entities/server_configuration.dart';
import 'package:weave/features/server_config/domain/repositories/server_configuration_repository.dart';
import 'package:weave/features/server_config/presentation/providers/server_configuration_repository_provider.dart';
import 'package:weave/main.dart';

import '../test/helpers/auth_test_data.dart';
import '../test/helpers/fake_chat_repository.dart';
import '../test/helpers/fake_chat_security_repository.dart';
import '../test/helpers/fake_files_repository.dart';
import '../test/helpers/in_memory_stores.dart';
import '../test/helpers/server_config_test_data.dart';

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

class _FakeIdentitySessionPort implements IdentitySessionPort {
  const _FakeIdentitySessionPort();

  @override
  Future<IdentitySessionReconciliation> reconcile({
    required Uri baseUrl,
    required String accessToken,
  }) async => IdentitySessionReconciliation.unchanged;
}

class _FakeCalendarRepository implements CalendarRepository {
  const _FakeCalendarRepository();

  @override
  Future<CalendarScopeList> loadScopes() async =>
      const CalendarScopeList(scopes: [CalendarScope.workspace]);

  @override
  Future<CalendarEventList> loadEvents({CalendarScope? scope}) async =>
      CalendarEventList(
        scope: scope ?? CalendarScope.workspace,
        events: const [],
      );

  @override
  Future<CalendarClientSetup>
  loadClientSetup() async => const CalendarClientSetup(
    scope: CalendarScope.workspace,
    username: 'member',
    endpoints: CalendarExternalEndpoints(
      serverUrl: 'https://files.weave.test',
      caldavDiscoveryUrl: 'https://files.weave.test/remote.php/dav',
      principalUrl:
          'https://files.weave.test/remote.php/dav/principals/users/member/',
    ),
    credentialPolicy: 'No credentials are returned to the client.',
    accessModel: CalendarAccessModel(
      type: 'workspace-calendar',
      productScope: 'workspace',
      privateUserCalendarsAvailable: false,
      privateUserCalendarsReason: 'Not part of this smoke test.',
      externalClientCredentialModel: 'revocable-credentials',
      notes: [],
    ),
    credentialReadiness: CalendarCredentialReadiness(
      status: 'ready',
      appleProfileSigned: false,
      appleProfilePasswordIncluded: false,
      revocableCredentialsAvailable: false,
      readOnlySubscriptionTokensAvailable: false,
      backendActorCredentialsExposed: false,
      blockers: [],
    ),
    options: [],
  );

  @override
  Future<CalendarEvent> readEvent(String id) {
    throw UnimplementedError();
  }

  @override
  Future<CalendarEvent> createEvent(CalendarEventDraft draft) {
    throw UnimplementedError();
  }

  @override
  Future<CalendarEvent> updateEvent(
    String id,
    CalendarEventDraft draft, {
    String? etag,
  }) {
    throw UnimplementedError();
  }

  @override
  Future<void> deleteEvent(String id) {
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

const _readyCapabilities = WorkspaceCapabilitySnapshot(
  shellAccess: WorkspaceCapabilityState(
    capability: WorkspaceCapability.shellAccess,
    readiness: WorkspaceCapabilityReadiness.ready,
  ),
  chat: WorkspaceCapabilityState(
    capability: WorkspaceCapability.chat,
    readiness: WorkspaceCapabilityReadiness.ready,
  ),
  files: WorkspaceCapabilityState(
    capability: WorkspaceCapability.files,
    readiness: WorkspaceCapabilityReadiness.ready,
  ),
  calendar: WorkspaceCapabilityState(
    capability: WorkspaceCapability.calendar,
    readiness: WorkspaceCapabilityReadiness.ready,
  ),
  boards: WorkspaceCapabilityState(
    capability: WorkspaceCapability.boards,
    readiness: WorkspaceCapabilityReadiness.unavailable,
  ),
);

void main() {
  IntegrationTestWidgetsFlutterBinding.ensureInitialized();

  testWidgets(
    'fresh Simulator opens the five required shell tabs and nested Profile',
    (tester) async {
      final secureStore = InMemorySecureStore({
        authSessionStorageKey: AuthSessionDto.fromSession(
          buildTestAuthSession(),
        ).encode(),
      });
      final filesRepository = FakeFilesRepository(
        connectionState: FilesConnectionState.connected(
          baseUrl: Uri.parse('https://files.weave.test'),
          accountLabel: 'Weave files',
        ),
        listings: const {
          '/': DirectoryListing(
            path: '/',
            entries: [
              FileEntry(
                id: 'file-1',
                name: 'SimulatorProof.md',
                path: '/SimulatorProof.md',
                isDirectory: false,
              ),
            ],
          ),
        },
      );

      await tester.pumpWidget(
        ProviderScope(
          overrides: [
            preferencesStoreProvider.overrideWith(
              (ref) => InMemoryPreferencesStore(),
            ),
            serverConfigurationRepositoryProvider.overrideWith(
              (ref) => _FakeServerConfigurationRepository(
                configuration: buildTestConfiguration(),
              ),
            ),
            secureStoreProvider.overrideWithValue(secureStore),
            oidcClientProvider.overrideWithValue(_FakeOidcClient()),
            identitySessionPortProvider.overrideWithValue(
              const _FakeIdentitySessionPort(),
            ),
            chatRepositoryProvider.overrideWithValue(
              FakeChatRepository(
                loadConversationsHandler: () async => const [
                  ChatConversation(
                    id: 'simulator-room',
                    title: 'Simulator Collaboration',
                    previewType: ChatConversationPreviewType.encrypted,
                    unreadCount: 1,
                    isInvite: false,
                    isDirectMessage: false,
                  ),
                ],
              ),
            ),
            chatSecurityRepositoryProvider.overrideWithValue(
              FakeChatSecurityRepository(),
            ),
            userProfileProvider.overrideWith((ref) async => _memberProfile),
            filesRepositoryProvider.overrideWithValue(filesRepository),
            calendarRepositoryProvider.overrideWithValue(
              const _FakeCalendarRepository(),
            ),
            workspaceCapabilitySnapshotProvider.overrideWithValue(
              const AsyncData(_readyCapabilities),
            ),
          ],
          child: const WeaveApp(),
        ),
      );
      await tester.pumpAndSettle();

      expect(
        find.byKey(const ValueKey('weave.workspace.home')),
        findsOneWidget,
      );

      await tester.tap(find.byIcon(Icons.chat_bubble_outline));
      await tester.pumpAndSettle();
      expect(find.text('Chat'), findsWidgets);
      expect(find.text('Simulator Collaboration'), findsWidgets);

      await tester.tap(find.byIcon(Icons.folder_outlined));
      await tester.pumpAndSettle();
      expect(find.text('SimulatorProof.md'), findsOneWidget);
      expect(_localizedCreateEventFinder(), findsNothing);

      await tester.tap(find.byIcon(Icons.calendar_month_outlined));
      await tester.pumpAndSettle();
      expect(_localizedCreateEventFinder(), findsOneWidget);
      expect(find.text('SimulatorProof.md'), findsNothing);

      await tester.tap(find.byIcon(Icons.settings_outlined));
      await tester.pumpAndSettle();
      expect(find.text('Settings'), findsWidgets);

      final profileLink = find.byIcon(Icons.account_circle_outlined);
      await tester.ensureVisible(profileLink);
      await tester.pumpAndSettle();
      await tester.tap(profileLink);
      await tester.pumpAndSettle();
      expect(find.text('Profile'), findsWidgets);
      expect(find.text('Workspace Member'), findsWidgets);

      // This marker is fixture UI evidence only. Real identity, provider, and
      // authorization claims are produced by the isolated live-stack lane.
      debugPrint(
        'IOS_SIMULATOR_UI_RESULT status=passed evidenceMode=fixture-ui '
        'surfaces=home,chat,files,calendar,settings,profile supportSafe=true',
      );
    },
  );

  testWidgets('settings language change updates the running app locale', (
    tester,
  ) async {
    final secureStore = InMemorySecureStore({
      authSessionStorageKey: AuthSessionDto.fromSession(
        buildTestAuthSession(),
      ).encode(),
    });
    final preferencesStore = InMemoryPreferencesStore({
      appLocalePreferenceStorageKey: 'en',
    });

    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          preferencesStoreProvider.overrideWith((ref) => preferencesStore),
          serverConfigurationRepositoryProvider.overrideWith(
            (ref) => _FakeServerConfigurationRepository(
              configuration: buildTestConfiguration(),
            ),
          ),
          secureStoreProvider.overrideWithValue(secureStore),
          oidcClientProvider.overrideWithValue(_FakeOidcClient()),
          identitySessionPortProvider.overrideWithValue(
            const _FakeIdentitySessionPort(),
          ),
          chatRepositoryProvider.overrideWithValue(FakeChatRepository()),
          chatSecurityRepositoryProvider.overrideWithValue(
            FakeChatSecurityRepository(),
          ),
          userProfileProvider.overrideWith((ref) async => _memberProfile),
          filesRepositoryProvider.overrideWithValue(
            FakeFilesRepository(
              connectionState: const FilesConnectionState.disconnected(),
            ),
          ),
          calendarRepositoryProvider.overrideWithValue(
            const _FakeCalendarRepository(),
          ),
          workspaceCapabilitySnapshotProvider.overrideWithValue(
            const AsyncData(_readyCapabilities),
          ),
        ],
        child: const WeaveApp(),
      ),
    );
    await tester.pumpAndSettle();

    expect(
      tester.widget<MaterialApp>(find.byType(MaterialApp)).locale,
      const Locale('en'),
    );

    await tester.tap(find.byIcon(Icons.settings_outlined));
    await tester.pumpAndSettle();
    expect(find.text('Settings'), findsWidgets);

    final germanOption = find.text('German');
    await tester.ensureVisible(germanOption);
    await tester.pumpAndSettle();
    await tester.tap(germanOption);
    await tester.pumpAndSettle();

    expect(preferencesStore.rawString(appLocalePreferenceStorageKey), 'de');
    expect(
      tester.widget<MaterialApp>(find.byType(MaterialApp)).locale,
      const Locale('de'),
    );
    expect(find.text('Einstellungen'), findsWidgets);
    expect(find.text('Settings'), findsNothing);
  });
}

Finder _localizedCreateEventFinder() {
  return find.byWidgetPredicate(
    (widget) =>
        widget is Text &&
        (widget.data == 'Create event' || widget.data == 'Termin erstellen'),
  );
}
