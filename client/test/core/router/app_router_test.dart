import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:weave/core/persistence/flutter_secure_store.dart';
import 'package:weave/core/persistence/shared_preferences_store.dart';
import 'package:weave/core/router/app_routes.dart';
import 'package:weave/core/router/app_router.dart';
import 'package:weave/features/auth/data/dtos/auth_session_dto.dart';
import 'package:weave/features/auth/data/repositories/oidc_auth_session_repository.dart';
import 'package:weave/features/auth/data/services/flutter_appauth_oidc_client.dart';
import 'package:weave/features/auth/data/services/oidc_client.dart';
import 'package:weave/features/chat/presentation/providers/chat_repository_provider.dart';
import 'package:weave/features/app/domain/entities/integration_invalidation.dart';
import 'package:weave/features/app/domain/entities/workspace_capability_snapshot.dart';
import 'package:weave/features/app/domain/entities/workspace_connection_state.dart';
import 'package:weave/features/app/data/services/persisted_client_upgrade_service.dart';
import 'package:weave/features/app/presentation/providers/workspace_connection_provider.dart';
import 'package:weave/features/app/presentation/providers/app_application_providers.dart';
import 'package:weave/features/auth/presentation/sign_in_screen.dart';
import 'package:weave/features/calendar/domain/entities/calendar_event.dart';
import 'package:weave/features/calendar/domain/repositories/calendar_repository.dart';
import 'package:weave/features/calendar/presentation/calendar_screen.dart';
import 'package:weave/features/calendar/presentation/providers/calendar_provider.dart';
import 'package:weave/features/chat/presentation/chat_screen.dart';
import 'package:weave/features/files/domain/entities/files_connection_state.dart';
import 'package:weave/features/files/presentation/files_screen.dart';
import 'package:weave/features/files/presentation/providers/files_repository_provider.dart';
import 'package:weave/features/help/presentation/help_screen.dart';
import 'package:weave/features/home/presentation/home_screen.dart';
import 'package:weave/features/onboarding/domain/entities/member_auth_onboarding_state.dart';
import 'package:weave/features/onboarding/domain/use_cases/discover_organization_access.dart';
import 'package:weave/features/onboarding/presentation/member_handoff_screen.dart';
import 'package:weave/features/onboarding/presentation/organization_access_screen.dart';
import 'package:weave/features/profile/domain/entities/user_profile.dart';
import 'package:weave/features/profile/presentation/profile_screen.dart';
import 'package:weave/features/profile/presentation/providers/user_profile_provider.dart';
import 'package:weave/features/server_config/domain/entities/server_configuration.dart';
import 'package:weave/features/server_config/data/repositories/shared_preferences_server_configuration_repository.dart';
import 'package:weave/features/server_config/domain/repositories/server_configuration_repository.dart';
import 'package:weave/features/server_config/presentation/providers/server_configuration_repository_provider.dart';
import 'package:weave/features/settings/presentation/settings_screen.dart';
import 'package:weave/integrations/weave_api/presentation/providers/weave_api_provider.dart';
import 'package:weave/main.dart';

import '../../helpers/auth_test_data.dart';
import '../../helpers/fake_chat_repository.dart';
import '../../helpers/fake_files_repository.dart';
import '../../helpers/fake_identity_session_port.dart';
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

AsyncValue<WorkspaceConnectionState> _workspaceConnectionState() {
  return const AsyncData(
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
  );
}

AsyncValue<WorkspaceCapabilitySnapshot> _workspaceCapabilitySnapshot() {
  return const AsyncData(
    WorkspaceCapabilitySnapshot(
      shellAccess: WorkspaceCapabilityState(
        capability: WorkspaceCapability.shellAccess,
        readiness: WorkspaceCapabilityReadiness.ready,
        connectionStatus: IntegrationConnectionStatus.connected,
      ),
      chat: WorkspaceCapabilityState(
        capability: WorkspaceCapability.chat,
        readiness: WorkspaceCapabilityReadiness.ready,
        connectionStatus: IntegrationConnectionStatus.connected,
      ),
      files: WorkspaceCapabilityState(
        capability: WorkspaceCapability.files,
        readiness: WorkspaceCapabilityReadiness.ready,
        connectionStatus: IntegrationConnectionStatus.connected,
      ),
      calendar: WorkspaceCapabilityState(
        capability: WorkspaceCapability.calendar,
        readiness: WorkspaceCapabilityReadiness.unavailable,
      ),
      boards: WorkspaceCapabilityState(
        capability: WorkspaceCapability.boards,
        readiness: WorkspaceCapabilityReadiness.unavailable,
      ),
    ),
  );
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

class _FakeCalendarRepository implements CalendarRepository {
  @override
  Future<CalendarEvent> createEvent(CalendarEventDraft draft) {
    throw UnimplementedError();
  }

  @override
  Future<void> deleteEvent(String id) {
    throw UnimplementedError();
  }

  @override
  Future<CalendarClientSetup> loadClientSetup() {
    throw UnimplementedError();
  }

  @override
  Future<CalendarEventList> loadEvents({CalendarScope? scope}) async {
    return CalendarEventList(scope: scope ?? CalendarScope.workspace);
  }

  @override
  Future<CalendarScopeList> loadScopes() async => const CalendarScopeList();

  @override
  Future<CalendarEvent> readEvent(String id) {
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
}

void main() {
  group('AppRouter', () {
    test('uses organization access as the normal launch route', () {
      expect(initialLocationForDefaultRoute('/'), AppRoutes.organizationAccess);
      expect(initialLocationForDefaultRoute(''), AppRoutes.organizationAccess);
      expect(
        initialLocationForDefaultRoute('/welcome'),
        AppRoutes.organizationAccess,
      );
    });

    test('uses join route for installed iOS custom-scheme launch', () {
      expect(
        initialLocationForDefaultRoute(
          'weave://join?handoff_ref=handoff-s32-massimo-dogfood-home&org=massimo-dogfood&workspace=home&profile=local-lan-dogfood&run_id=s32-massimo-dogfood&product_base_url=https://weave.test:44443&platform_config_url=https://api.weave.test:44443/api/platform/config',
        ),
        '/join?handoff_ref=handoff-s32-massimo-dogfood-home&org=massimo-dogfood&workspace=home&profile=local-lan-dogfood&run_id=s32-massimo-dogfood&product_base_url=https://weave.test:44443&platform_config_url=https://api.weave.test:44443/api/platform/config',
      );
    });

    test('uses join route for installed iOS custom-scheme launch with slash', () {
      expect(
        initialLocationForDefaultRoute(
          'weave://join/?handoff_ref=handoff-s32-massimo-dogfood-home&org=massimo-dogfood&workspace=home&profile=local-lan-dogfood&run_id=s32-massimo-dogfood&product_base_url=https%3A%2F%2Fweave.test%3A44443&platform_config_url=https%3A%2F%2Fweave.test%3A44443%2Fapi%2Fplatform%2Fconfig',
        ),
        '/join?handoff_ref=handoff-s32-massimo-dogfood-home&org=massimo-dogfood&workspace=home&profile=local-lan-dogfood&run_id=s32-massimo-dogfood&product_base_url=https%3A%2F%2Fweave.test%3A44443&platform_config_url=https%3A%2F%2Fweave.test%3A44443%2Fapi%2Fplatform%2Fconfig',
      );
    });

    test('normalizes join links from app scheme and in-app routes', () {
      expect(
        normalizedJoinRouteLocation(
          Uri.parse('weave://join/?handoff_ref=handoff-1'),
        ),
        '/join?handoff_ref=handoff-1',
      );
      expect(
        normalizedJoinRouteLocation(
          Uri.parse('weave:/join?handoff_ref=handoff-2'),
        ),
        '/join?handoff_ref=handoff-2',
      );
      expect(
        normalizedJoinRouteLocation(Uri.parse('/join?handoff_ref=handoff-3')),
        '/join?handoff_ref=handoff-3',
      );
      expect(
        normalizedJoinRouteLocation(Uri.parse('weave://settings')),
        isNull,
      );
    });

    ProviderContainer createContainer({
      required ServerConfiguration? configuration,
      InMemorySecureStore? secureStore,
      InMemoryPreferencesStore? preferencesStore,
      UserProfile? userProfile,
      bool usePersistedConfiguration = false,
    }) {
      final container = ProviderContainer.test(
        overrides: [
          if (!usePersistedConfiguration)
            serverConfigurationRepositoryProvider.overrideWith(
              (ref) => _FakeServerConfigurationRepository(
                configuration: configuration,
              ),
            ),
          secureStoreProvider.overrideWithValue(
            secureStore ?? InMemorySecureStore(),
          ),
          preferencesStoreProvider.overrideWith(
            (ref) => preferencesStore ?? InMemoryPreferencesStore(),
          ),
          oidcClientProvider.overrideWithValue(_FakeOidcClient()),
          identitySessionPortProvider.overrideWithValue(
            FakeIdentitySessionPort(),
          ),
          chatRepositoryProvider.overrideWithValue(FakeChatRepository()),
          filesRepositoryProvider.overrideWithValue(
            FakeFilesRepository(
              connectionState: const FilesConnectionState.disconnected(),
            ),
          ),
          calendarRepositoryProvider.overrideWithValue(
            _FakeCalendarRepository(),
          ),
          userProfileProvider.overrideWith((ref) async => userProfile),
          workspaceConnectionStateProvider.overrideWithValue(
            _workspaceConnectionState(),
          ),
          workspaceCapabilitySnapshotProvider.overrideWithValue(
            _workspaceCapabilitySnapshot(),
          ),
          // The ready shell renders backend-backed workspace status cards.
          // Keep router tests focused on navigation so widget-test HTTP
          // failures do not schedule Riverpod retry timers after disposal.
          weaveApiWorkspaceCapabilitySnapshotProvider.overrideWith(
            (ref) async => _workspaceCapabilitySnapshot().value,
          ),
          weaveApiWorkspaceHomeProvider.overrideWith((ref) => null),
        ],
      );
      return container;
    }

    testWidgets('shows organization access when no configuration exists', (
      tester,
    ) async {
      final container = createContainer(configuration: null);
      addTearDown(container.dispose);

      await tester.pumpWidget(
        UncontrolledProviderScope(
          container: container,
          child: const WeaveApp(),
        ),
      );
      await tester.pumpAndSettle();

      expect(find.byType(OrganizationAccessScreen), findsOneWidget);
    });

    testWidgets('shows the sign-in gate when config exists without a session', (
      tester,
    ) async {
      final container = createContainer(
        configuration: buildTestConfiguration(),
      );
      addTearDown(container.dispose);

      await tester.pumpWidget(
        UncontrolledProviderScope(
          container: container,
          child: const WeaveApp(),
        ),
      );
      await tester.pumpAndSettle();

      expect(find.byType(SignInScreen), findsOneWidget);
    });

    testWidgets('redirects authenticated ready users to the shell', (
      tester,
    ) async {
      final secureStore = InMemorySecureStore();
      await secureStore.write(
        authSessionStorageKey,
        AuthSessionDto.fromSession(buildTestAuthSession()).encode(),
      );
      final container = createContainer(
        configuration: buildTestConfiguration(),
        secureStore: secureStore,
      );
      addTearDown(container.dispose);

      await tester.pumpWidget(
        UncontrolledProviderScope(
          container: container,
          child: const WeaveApp(),
        ),
      );
      await tester.pumpAndSettle();

      expect(find.byType(NavigationBar), findsOneWidget);

      container.read(appRouterProvider).go(AppRoutes.calendar);
      await tester.pumpAndSettle();
      expect(find.byType(CalendarScreen), findsOneWidget);
      expect(find.text('Calendar'), findsWidgets);

      container.read(appRouterProvider).go(AppRoutes.profile);
      await tester.pumpAndSettle();
      expect(find.byType(ProfileScreen), findsOneWidget);
      expect(find.byType(NavigationBar), findsOneWidget);
    });

    testWidgets(
      'upgrades authenticated legacy storage directly into the app shell',
      (tester) async {
        final preferencesStore = InMemoryPreferencesStore({
          ...buildStoredConfiguration(),
          legacySetupCompleteKey: false,
        });
        final secureStore = InMemorySecureStore({
          authSessionStorageKey: AuthSessionDto.fromSession(
            buildTestAuthSession(),
          ).encode(),
          obsoleteProviderSessionStorageKey: 'obsolete-provider-session',
        });
        final container = createContainer(
          configuration: null,
          secureStore: secureStore,
          preferencesStore: preferencesStore,
          usePersistedConfiguration: true,
        );
        addTearDown(container.dispose);

        await tester.pumpWidget(
          UncontrolledProviderScope(
            container: container,
            child: const WeaveApp(),
          ),
        );
        await tester.pumpAndSettle();

        expect(find.byType(NavigationBar), findsOneWidget);
        expect(find.byType(HomeScreen), findsOneWidget);
        expect(await preferencesStore.getBool(legacySetupCompleteKey), isNull);
        expect(secureStore.rawValue(authSessionStorageKey), isNotNull);
        expect(secureStore.rawValue(obsoleteProviderSessionStorageKey), isNull);
        expect(
          container
              .read(appRouterProvider)
              .routeInformationProvider
              .value
              .uri
              .path,
          AppRoutes.home,
        );
        // LEGACY_FIRST_RUN_UPGRADE_RESULT is intentionally support-safe: it
        // records only the upgrade outcomes, never configuration or tokens.
        // ignore: avoid_print
        print(
          'LEGACY_FIRST_RUN_UPGRADE_RESULT '
          'legacyPreferencesRemoved=true legacySecureStateRemoved=true '
          'sessionPreserved=true appShell=true',
        );
      },
    );

    testWidgets(
      'keeps every current member route reachable when Calendar is unavailable',
      (tester) async {
        final secureStore = InMemorySecureStore({
          authSessionStorageKey: AuthSessionDto.fromSession(
            buildTestAuthSession(),
          ).encode(),
        });
        final container = createContainer(
          configuration: buildTestConfiguration(),
          secureStore: secureStore,
          userProfile: const UserProfile(
            userId: 'member-1',
            username: 'member',
            displayName: 'Workspace Member',
            locale: 'en',
            timezone: 'Europe/Berlin',
            emailVerified: true,
            roles: ['member'],
          ),
        );
        addTearDown(container.dispose);

        await tester.pumpWidget(
          UncontrolledProviderScope(
            container: container,
            child: const WeaveApp(),
          ),
        );
        await tester.pumpAndSettle();

        for (final routeAndType in <(String, Type)>[
          (AppRoutes.home, HomeScreen),
          (AppRoutes.chat, ChatScreen),
          (AppRoutes.files, FilesScreen),
          (AppRoutes.settings, SettingsScreen),
          (AppRoutes.profile, ProfileScreen),
        ]) {
          container.read(appRouterProvider).go(routeAndType.$1);
          await tester.pumpAndSettle();
          expect(find.byType(routeAndType.$2), findsOneWidget);
          expect(find.byType(NavigationBar), findsOneWidget);
        }

        container.read(appRouterProvider).go(AppRoutes.calendar);
        await tester.pumpAndSettle();
        expect(find.byType(CalendarScreen), findsOneWidget);
        expect(find.text('Calendar is unavailable'), findsOneWidget);
        expect(find.byType(NavigationBar), findsOneWidget);
      },
    );

    testWidgets(
      'records session restoration after a relaunched profile request succeeds',
      (tester) async {
        final handoffEvidence = <String, Object>{
          'schemaVersion': 'weave.dogfood.handoff-consumed.v1',
          'result': 'saved_configuration',
          'handoffRef': 'handoff-session-restore',
          'runId': 'run-session-restore',
          'organizationSlug': 'massimo-dogfood',
          'workspaceSlug': 'home',
          'profile': 'local-lan-dogfood',
          'supportSafe': true,
        };
        final workspaceReady = <String, Object>{
          'schemaVersion': 'weave.client.dogfood_auth_state.v1',
          'recordedAt': '2026-07-10T00:00:00Z',
          'state': 'workspace_ready',
          'handoffRef': 'handoff-session-restore',
          'runId': 'run-session-restore',
          'organizationSlug': 'massimo-dogfood',
          'workspaceSlug': 'home',
          'profile': 'local-lan-dogfood',
          'supportSafe': true,
        };
        final preferencesStore = InMemoryPreferencesStore({
          lastHandoffConsumedStorageKey: jsonEncode(handoffEvidence),
          dogfoodAuthStateStorageKey: jsonEncode(workspaceReady),
          dogfoodAuthStateHistoryStorageKey: jsonEncode([workspaceReady]),
        });
        final secureStore = InMemorySecureStore();
        await secureStore.write(
          authSessionStorageKey,
          AuthSessionDto.fromSession(buildTestAuthSession()).encode(),
        );
        final container = createContainer(
          configuration: buildTestConfiguration(),
          secureStore: secureStore,
          preferencesStore: preferencesStore,
          userProfile: const UserProfile(
            userId: 'member-1',
            username: 'member',
            displayName: 'Member',
            locale: 'en',
            timezone: 'Europe/Berlin',
            emailVerified: true,
          ),
        );
        addTearDown(container.dispose);

        await tester.pumpWidget(
          UncontrolledProviderScope(
            container: container,
            child: const WeaveApp(),
          ),
        );
        await tester.pumpAndSettle();

        final rawHistory = preferencesStore.rawString(
          dogfoodAuthStateHistoryStorageKey,
        );
        expect(rawHistory, isNotNull);
        final history = jsonDecode(rawHistory!) as List<dynamic>;
        final historyStates = history
            .map((entry) => (entry as Map<String, dynamic>)['state'])
            .toList();
        expect(historyStates.sublist(historyStates.length - 2), [
          'session_restored',
          'workspace_ready',
        ]);
      },
    );

    testWidgets('uses the saved profile locale for the app language', (
      tester,
    ) async {
      final secureStore = InMemorySecureStore();
      await secureStore.write(
        authSessionStorageKey,
        AuthSessionDto.fromSession(buildTestAuthSession()).encode(),
      );
      final container = createContainer(
        configuration: buildTestConfiguration(),
        secureStore: secureStore,
        userProfile: const UserProfile(
          userId: 'member-1',
          username: 'member',
          displayName: 'Member',
          locale: 'de',
          timezone: 'Europe/Berlin',
          emailVerified: true,
        ),
      );
      addTearDown(container.dispose);

      await tester.pumpWidget(
        UncontrolledProviderScope(
          container: container,
          child: const WeaveApp(),
        ),
      );
      await tester.pumpAndSettle();

      final app = tester.widget<MaterialApp>(find.byType(MaterialApp));
      expect(app.locale, const Locale('de'));
    });

    testWidgets(
      'does not replay a dogfood join link after authentication is ready',
      (tester) async {
        setStartupInitialLocation(
          '/join?handoff_ref=handoff-s32-massimo-dogfood-home&org=massimo-dogfood&workspace=home&profile=local-lan-dogfood&run_id=s32-massimo-dogfood&product_base_url=https://weave.test:44443&platform_config_url=https://weave.test:44443/api/platform/config',
        );
        addTearDown(() => setStartupInitialLocation(null));
        final secureStore = InMemorySecureStore();
        final preferencesStore = InMemoryPreferencesStore();
        await secureStore.write(
          authSessionStorageKey,
          AuthSessionDto.fromSession(buildTestAuthSession()).encode(),
        );
        final container = createContainer(
          configuration: buildTestConfiguration(),
          secureStore: secureStore,
          preferencesStore: preferencesStore,
        );
        addTearDown(container.dispose);

        await tester.pumpWidget(
          UncontrolledProviderScope(
            container: container,
            child: const WeaveApp(),
          ),
        );
        await tester.pumpAndSettle();

        expect(find.byType(MemberHandoffScreen), findsNothing);
        expect(find.byType(NavigationBar), findsOneWidget);
        expect(
          container
              .read(appRouterProvider)
              .routeInformationProvider
              .value
              .uri
              .path,
          AppRoutes.home,
        );
        final rawAuthState = preferencesStore.rawString(
          dogfoodAuthStateStorageKey,
        );
        expect(rawAuthState, isNotNull);
        final authState = jsonDecode(rawAuthState!) as Map<String, dynamic>;
        expect(authState['state'], 'workspace_ready');
        expect(authState['handoffRef'], 'handoff-s32-massimo-dogfood-home');
        expect(authState['supportSafe'], isTrue);
      },
    );

    testWidgets('normalizes startup custom-scheme join links before routing', (
      tester,
    ) async {
      setStartupInitialLocation(
        'weave://join/?handoff_ref=handoff-s32-massimo-dogfood-home&org=massimo-dogfood&workspace=home&profile=local-lan-dogfood&run_id=s32-massimo-dogfood&product_base_url=https%3A%2F%2Fweave.test%3A44443&platform_config_url=https%3A%2F%2Fweave.test%3A44443%2Fapi%2Fplatform%2Fconfig',
      );
      addTearDown(() => setStartupInitialLocation(null));
      final container = createContainer(configuration: null);
      addTearDown(container.dispose);

      await tester.pumpWidget(
        UncontrolledProviderScope(
          container: container,
          child: const WeaveApp(),
        ),
      );
      await tester.pumpAndSettle();

      expect(find.byType(MemberHandoffScreen), findsOneWidget);
      expect(
        container
            .read(appRouterProvider)
            .routeInformationProvider
            .value
            .uri
            .toString(),
        '/join?handoff_ref=handoff-s32-massimo-dogfood-home&org=massimo-dogfood&workspace=home&profile=local-lan-dogfood&run_id=s32-massimo-dogfood&product_base_url=https%3A%2F%2Fweave.test%3A44443&platform_config_url=https%3A%2F%2Fweave.test%3A44443%2Fapi%2Fplatform%2Fconfig',
      );
    });

    testWidgets('opens the routed help handbook for ready users', (
      tester,
    ) async {
      final secureStore = InMemorySecureStore();
      await secureStore.write(
        authSessionStorageKey,
        AuthSessionDto.fromSession(buildTestAuthSession()).encode(),
      );
      final container = createContainer(
        configuration: buildTestConfiguration(),
        secureStore: secureStore,
      );
      addTearDown(container.dispose);

      await tester.pumpWidget(
        UncontrolledProviderScope(
          container: container,
          child: const WeaveApp(),
        ),
      );
      await tester.pumpAndSettle();

      container.read(appRouterProvider).go(AppRoutes.help);
      await tester.pumpAndSettle();

      expect(find.byType(HelpScreen), findsOneWidget);
      expect(find.text('User handbook'), findsOneWidget);
    });

    testWidgets('opens the routed profile surface for ready users', (
      tester,
    ) async {
      final secureStore = InMemorySecureStore();
      await secureStore.write(
        authSessionStorageKey,
        AuthSessionDto.fromSession(buildTestAuthSession()).encode(),
      );
      final container = createContainer(
        configuration: buildTestConfiguration(),
        secureStore: secureStore,
        userProfile: const UserProfile(
          userId: 'member-1',
          username: 'member',
          displayName: 'Workspace Member',
          locale: 'en',
          timezone: 'Europe/Berlin',
          emailVerified: true,
          roles: ['member'],
          groups: ['workspace-default'],
        ),
      );
      addTearDown(container.dispose);

      await tester.pumpWidget(
        UncontrolledProviderScope(
          container: container,
          child: const WeaveApp(),
        ),
      );
      await tester.pumpAndSettle();

      container.read(appRouterProvider).go(AppRoutes.profile);
      await tester.pumpAndSettle();

      expect(find.byType(ProfileScreen), findsOneWidget);
      expect(find.text('Profile'), findsWidgets);
      expect(find.text('Weave profile'), findsOneWidget);
      expect(find.text('Save profile'), findsOneWidget);
    });

    testWidgets('opens workspace health boundary for ready members', (
      tester,
    ) async {
      final secureStore = InMemorySecureStore();
      await secureStore.write(
        authSessionStorageKey,
        AuthSessionDto.fromSession(buildTestAuthSession()).encode(),
      );
      final container = createContainer(
        configuration: buildTestConfiguration(),
        secureStore: secureStore,
        userProfile: const UserProfile(
          userId: 'member-1',
          username: 'member',
          displayName: 'Workspace Member',
          locale: 'en',
          timezone: 'Europe/Berlin',
          emailVerified: true,
          roles: ['member'],
          groups: ['workspace-default'],
        ),
      );
      addTearDown(container.dispose);

      await tester.pumpWidget(
        UncontrolledProviderScope(
          container: container,
          child: const WeaveApp(),
        ),
      );
      await tester.pumpAndSettle();

      container.read(appRouterProvider).go(AppRoutes.workspaceHealth);
      await tester.pumpAndSettle();

      expect(find.byType(WorkspaceHealthScreen), findsOneWidget);
      expect(find.text('Workspace Health'), findsWidgets);
      expect(find.text('Workspace setup is admin-only'), findsOneWidget);
      expect(find.text('Workspace Readiness'), findsNothing);
    });

    testWidgets(
      'redirects shell routes to organization access when discovery is needed',
      (tester) async {
        final container = createContainer(configuration: null);
        addTearDown(container.dispose);

        await tester.pumpWidget(
          UncontrolledProviderScope(
            container: container,
            child: const WeaveApp(),
          ),
        );
        await tester.pumpAndSettle();

        container.read(appRouterProvider).go(AppRoutes.settings);
        await tester.pumpAndSettle();

        expect(find.byType(OrganizationAccessScreen), findsOneWidget);
      },
    );

    testWidgets('redirects shell routes to sign-in when auth is required', (
      tester,
    ) async {
      final container = createContainer(
        configuration: buildTestConfiguration(),
      );
      addTearDown(container.dispose);

      await tester.pumpWidget(
        UncontrolledProviderScope(
          container: container,
          child: const WeaveApp(),
        ),
      );
      await tester.pumpAndSettle();

      container.read(appRouterProvider).go(AppRoutes.settings);
      await tester.pumpAndSettle();

      expect(find.byType(SignInScreen), findsOneWidget);
    });
  });
}
