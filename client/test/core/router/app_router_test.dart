import 'dart:async';
import 'package:weave/core/failures/app_failure.dart';
import 'package:weave/features/auth/domain/entities/auth_failure.dart';
import 'package:weave/features/app/domain/use_cases/sign_in_with_oidc.dart';
import 'package:weave/features/app/domain/use_cases/resolve_app_bootstrap.dart';
import 'package:weave/core/bootstrap/domain/bootstrap_state.dart';
import 'package:weave/core/bootstrap/presentation/providers/app_bootstrap_provider.dart';
import 'package:weave/l10n/generated/app_localizations.dart';
import 'dart:io';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:weave/core/widgets/error_state.dart';
import 'package:weave/features/server_config/presentation/providers/saved_server_configuration_provider.dart';
import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
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
import 'package:weave/features/app/domain/entities/member_space_access_snapshot.dart';
import 'package:weave/features/app/domain/entities/workspace_capability_snapshot.dart';
import 'package:weave/features/app/domain/entities/workspace_connection_state.dart';
import 'package:weave/features/app/presentation/providers/workspace_connection_provider.dart';
import 'package:weave/features/app/presentation/providers/app_application_providers.dart';
import 'package:weave/features/auth/presentation/sign_in_screen.dart';
import 'package:weave/features/calendar/domain/entities/calendar_event.dart';
import 'package:weave/features/calendar/domain/entities/calendar_workspace_context.dart';
import 'package:weave/features/calendar/domain/repositories/calendar_repository.dart';
import 'package:weave/features/calendar/presentation/calendar_screen.dart';
import 'package:weave/features/calendar/presentation/providers/calendar_provider.dart';
import 'package:weave/features/chat/presentation/chat_screen.dart';
import 'package:weave/features/chat/presentation/chat_room_screen.dart';
import 'package:weave/features/chat/domain/entities/chat_conversation.dart';
import 'package:weave/features/files/domain/entities/files_connection_state.dart';
import 'package:weave/features/files/presentation/files_screen.dart';
import 'package:weave/features/files/presentation/files_locations_screen.dart';
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
import 'package:weave/features/server_config/domain/repositories/server_configuration_repository.dart';
import 'package:weave/features/server_config/presentation/providers/server_configuration_repository_provider.dart';
import 'package:weave/features/settings/presentation/settings_screen.dart';
import 'package:weave/integrations/weave_api/presentation/providers/weave_api_provider.dart';
import 'package:weave/main.dart';

import '../../../integration_test/dual_ios_collaboration_test.dart'
    show tapNativeDualChatConversation, nativeDualChatSurfaceDiagnostic;

import '../../../integration_test/system_browser_auth_e2e_test.dart'
    show expectNativeMatrixEndpoint, prepareNativeCanonicalOrganization;
import 'package:weave/features/server_config/data/repositories/shared_preferences_server_configuration_repository.dart';
import 'package:weave/features/server_config/data/services/service_endpoint_deriver.dart';

import '../../helpers/auth_test_data.dart';
import '../../helpers/fake_chat_repository.dart';
import '../../helpers/fake_files_repository.dart';
import '../../helpers/fake_identity_session_port.dart';
import '../../helpers/in_memory_stores.dart';
import '../../helpers/server_config_test_data.dart';

class _ControlledBootstrapResolver implements ResolveAppBootstrap {
  _ControlledBootstrapResolver({
    this.initial = const BootstrapState.needsSignIn(),
  });
  final BootstrapState initial;
  final Completer<BootstrapState> next = Completer<BootstrapState>();
  var calls = 0;
  @override
  Future<BootstrapState> call() async {
    if (calls++ == 0) return initial;
    return next.future;
  }
}

class _ControlledInteractiveSignIn implements SignInWithOidc {
  final Completer<void> returned = Completer<void>();
  var calls = 0;
  @override
  Future<void> call({required bool isInteractiveSignInSupported}) {
    calls++;
    return returned.future;
  }
}

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

class _HeldServerConfigurationRepository
    extends _FakeServerConfigurationRepository {
  _HeldServerConfigurationRepository({required super.configuration});
  final pendingLoad = Completer<ServerConfiguration?>();
  bool hold = false;
  @override
  Future<ServerConfiguration?> loadConfiguration() =>
      hold ? pendingLoad.future : super.loadConfiguration();
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
  Future<void> deleteEvent(String id, {String? etag}) {
    throw UnimplementedError();
  }

  @override
  Future<CalendarEventList> loadEvents({
    CalendarScope? scope,
    DateTime? from,
    DateTime? to,
  }) async {
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
  test(
    'startup app-link normalization never discards outer credentials or fragment',
    () {
      for (final link in [
        'weave://join?organization_origin=https%3A%2F%2Fweave.example#fragment',
        'weave://user:secret@join?organization_origin=https%3A%2F%2Fweave.example',
      ]) {
        expect(normalizedJoinRouteLocation(Uri.parse(link)), isNull);
        expect(
          initialLocationForDefaultRoute(link),
          AppRoutes.organizationAccess,
        );
      }
    },
  );
  test('app-local reset consumes its control before canonical discovery', () {
    final clean = canonicalDogfoodAppStateResetLink(
      Uri.parse(
        'weave://join?organization_origin=https%3A%2F%2Fweave.test%3A9443%2F&dogfood_reset=app_state',
      ),
    );
    expect(clean, isNotNull);
    expect(clean!.queryParameters, {
      'organization_origin': 'https://weave.test:9443/',
    });
    expect(
      initialLocationForDefaultRoute(clean.toString()),
      '/join?organization_origin=https%3A%2F%2Fweave.test%3A9443%2F',
    );
  });

  for (final link in [
    'weave://join?organization_origin=https%3A%2F%2Fweave.test%2F',
    'weave://join?organization_origin=https%3A%2F%2Fweave.test%2F&dogfood_reset=destructive_uninstall',
    'weave://join?profile=local-lan-dogfood&dogfood_reset=app_state',
    'weave://join?organization_origin=https%3A%2F%2Fweave.test%2F&dogfood_reset=app_state&profile=local-lan-dogfood',
    'weave://join?organization_origin=http%3A%2F%2Fweave.test%2F&dogfood_reset=app_state',
    'weave://join?organization_origin=https%3A%2F%2Fuser%3Asecret%40weave.test%2F&dogfood_reset=app_state',
    'weave://join?organization_origin=https%3A%2F%2Fweave.test%2Fapi&dogfood_reset=app_state',
    'weave://join?organization_origin=https%3A%2F%2Fweave.test%2F&dogfood_reset=app_state&dogfood_reset=app_state',
    'weave://join?organization_origin=https%3A%2F%2Fweave.test%2F&organization_origin=https%3A%2F%2Fother.test%2F&dogfood_reset=app_state',
    'weave://join?organization_origin=https%3A%2F%2Fweave.test%2F&dogfood_reset=app_state#fragment',
    'https://weave.test/join?organization_origin=https%3A%2F%2Fweave.test%2F&dogfood_reset=app_state',
    'weave://other?organization_origin=https%3A%2F%2Fweave.test%2F&dogfood_reset=app_state',
  ]) {
    test('ordinary or invalid link cannot reset app state: $link', () {
      expect(canonicalDogfoodAppStateResetLink(Uri.parse(link)), isNull);
    });
  }

  group('AppRouter', () {
    setUp(() {
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
          .setMockMethodCallHandler(
            const MethodChannel('com.llfbandit.app_links/events'),
            (_) async => null,
          );
    });
    tearDown(() {
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
          .setMockMethodCallHandler(
            const MethodChannel('com.llfbandit.app_links/events'),
            null,
          );
    });
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
      FakeChatRepository? chatRepository,
      AsyncValue<WorkspaceCapabilitySnapshot> Function()? capabilities,
      bool usePersistedConfiguration = false,
      ResolveAppBootstrap? resolver,
      SignInWithOidc? interactiveSignIn,
      bool useHandoffDiscoveryFixture = false,
      AppStartDiscoveryClient? discoveryClient,
      ServerConfigurationRepository? configurationRepository,
    }) {
      final container = ProviderContainer.test(
        overrides: [
          if (useHandoffDiscoveryFixture)
            discoverOrganizationAccessProvider.overrideWith(
              (ref) => DiscoverOrganizationAccess(
                repository: ref.watch(serverConfigurationRepositoryProvider),
                discoveryClient: buildTestDiscoveryClient(
                  ref.watch(serverConfigurationRepositoryProvider),
                ),
                evidenceStore: ref.watch(preferencesStoreProvider),
              ),
            ),
          if (discoveryClient != null)
            discoverOrganizationAccessProvider.overrideWith(
              (ref) => DiscoverOrganizationAccess(
                repository: ref.watch(serverConfigurationRepositoryProvider),
                discoveryClient: discoveryClient,
                evidenceStore: ref.watch(preferencesStoreProvider),
              ),
            ),
          if (resolver != null)
            resolveAppBootstrapProvider.overrideWithValue(resolver),
          if (interactiveSignIn != null)
            signInWithOidcProvider.overrideWithValue(interactiveSignIn),
          if (discoveryClient != null)
            appStartDiscoveryClientProvider.overrideWithValue(discoveryClient),
          if (!usePersistedConfiguration && discoveryClient == null)
            appStartDiscoveryClientProvider.overrideWith(
              (ref) => buildTestDiscoveryClient(
                ref.watch(serverConfigurationRepositoryProvider),
              ),
            ),
          serverConfigurationRepositoryProvider.overrideWith(
            (ref) =>
                configurationRepository ??
                _FakeServerConfigurationRepository(
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
          chatRepositoryProvider.overrideWithValue(
            chatRepository ?? FakeChatRepository(),
          ),
          filesRepositoryProvider.overrideWithValue(
            FakeFilesRepository(
              connectionState: const FilesConnectionState.disconnected(),
            ),
          ),
          calendarWorkspaceContextProvider.overrideWith(
            (ref) async => const CalendarWorkspaceContext(
              ownerKey: 'fixture-member',
              scopes: CalendarScopeList(),
              selectedScope: CalendarScope.workspace,
              evaluationTimeZone: 'Europe/Berlin',
            ),
          ),
          calendarRepositoryProvider.overrideWithValue(
            _FakeCalendarRepository(),
          ),
          userProfileProvider.overrideWith((ref) async => userProfile),
          workspaceConnectionStateProvider.overrideWithValue(
            _workspaceConnectionState(),
          ),
          workspaceCapabilitySnapshotProvider.overrideWith(
            (ref) => capabilities?.call() ?? _workspaceCapabilitySnapshot(),
          ),
          // The ready shell renders backend-backed workspace status cards.
          // Keep router tests focused on navigation so widget-test HTTP
          // failures do not schedule Riverpod retry timers after disposal.
          weaveApiWorkspaceCapabilitySnapshotProvider.overrideWith(
            (ref) async => _workspaceCapabilitySnapshot().value,
          ),
          weaveApiMemberSpacesProvider.overrideWith(
            (ref) async => const MemberSpaceAccessSnapshot(
              visibleSpaceRefs: {'workspace-default'},
              displayNames: {'workspace-default': 'Organization workspace'},
              defaultSpaceReadable: true,
            ),
          ),
          weaveApiWorkspaceHomeProvider.overrideWith((ref) => null),
        ],
      );
      return container;
    }

    testWidgets(
      'real router preserves pending invitation without timeline and diagnoses admission gate',
      (tester) async {
        const invitation = ChatConversation(
          id: '!private-room:example.invalid',
          title: 'Private invitation title',
          previewType: ChatConversationPreviewType.none,
          unreadCount: 0,
          isInvite: true,
          isDirectMessage: false,
        );
        final repository = FakeChatRepository(
          loadConversationsHandler: () async => [invitation],
        );
        var capabilities = _workspaceCapabilitySnapshot();
        final container = createContainer(
          configuration: buildTestConfiguration(),
          resolver: _ControlledBootstrapResolver(
            initial: const BootstrapState.ready(),
          ),
          chatRepository: repository,
          capabilities: () => capabilities,
        );
        addTearDown(container.dispose);
        await container.read(appBootstrapProvider.future);
        await tester.pumpWidget(
          UncontrolledProviderScope(
            container: container,
            child: const WeaveApp(),
          ),
        );
        await tester.pumpAndSettle();
        container.read(appRouterProvider).go(AppRoutes.chat);
        await tester.pumpAndSettle();
        expect(find.byType(ChatScreen), findsOneWidget);
        await tapNativeDualChatConversation(tester, invitation.title);
        await tester.pumpAndSettle();
        expect(find.byType(ChatRoomScreen), findsOneWidget);
        expect(
          find.byKey(const Key('chat-accept-invitation-button')),
          findsOneWidget,
        );
        expect(repository.loadRoomTimelineCalls, 0);
        final ready = nativeDualChatSurfaceDiagnostic(
          tester,
          container.read(appBootstrapProvider),
          container.read(workspaceCapabilitySnapshotProvider),
        );
        expect(ready, contains('invitedRoomVisible=1 nonInvitedRoomVisible=0'));
        expect(ready, contains('invitationCount=1'));
        expect(ready, isNot(contains(invitation.id)));
        expect(ready, isNot(contains(invitation.title)));
        capabilities = const AsyncLoading();
        container.invalidate(workspaceCapabilitySnapshotProvider);
        expect(
          container.read(workspaceCapabilitySnapshotProvider).isLoading,
          isTrue,
        );
        await tester.pump();
        await tester.pump();
        expect(find.byType(ChatRoomScreen), findsNothing);
        final gated = nativeDualChatSurfaceDiagnostic(
          tester,
          container.read(appBootstrapProvider),
          container.read(workspaceCapabilitySnapshotProvider),
        );
        expect(gated, contains('roomVisible=0 roomMounted=0'));
        expect(gated, contains('invitedRoomVisible=0 nonInvitedRoomVisible=0'));
        expect(gated, contains('invitationCount=0'));
        expect(gated, contains('capabilitiesLoading=true'));
        expect(repository.loadRoomTimelineCalls, 0);
        capabilities = _workspaceCapabilitySnapshot();
        container.invalidate(workspaceCapabilitySnapshotProvider);
        await tester.pumpAndSettle();
        expect(
          find.byKey(const Key('chat-accept-invitation-button')),
          findsOneWidget,
        );
        expect(repository.loadRoomTimelineCalls, 0);
      },
    );

    testWidgets(
      'native canonical entry reaches sign-in through production discovery',
      (tester) async {
        final store = InMemoryPreferencesStore();
        var requests = 0;
        final client = AppStartDiscoveryClient(
          httpClient: MockClient((request) async {
            requests++;
            expect(
              request.url,
              Uri.parse('https://weave.example/api/platform/config'),
            );
            final manifest =
                jsonDecode(
                      File(
                        'test/fixtures/org_manifest_v2_valid.json',
                      ).readAsStringSync(),
                    )
                    as Map<String, dynamic>;
            manifest['organizationOrigin'] = 'https://weave.example';
            return http.Response(
              jsonEncode(manifest),
              200,
              headers: {'content-type': 'application/json'},
            );
          }),
        );
        final repository = SharedPreferencesServerConfigurationRepository(
          store: store,
          deriver: ServiceEndpointDeriver(),
        );
        final container = createContainer(
          configuration: null,
          preferencesStore: store,
          usePersistedConfiguration: true,
          configurationRepository: repository,
          discoveryClient: client,
        );
        addTearDown(container.dispose);
        await tester.pumpWidget(
          UncontrolledProviderScope(
            container: container,
            child: const WeaveApp(),
          ),
        );
        await tester.pumpAndSettle();
        expect(
          (await container.read(appBootstrapProvider.future)).phase,
          BootstrapPhase.needsSetup,
        );
        expect(find.byKey(const ValueKey('weave.auth.sign-in')), findsNothing);
        await prepareNativeCanonicalOrganization(
          tester,
          Uri.parse('https://weave.example'),
          deadline: DateTime.now().add(const Duration(seconds: 3)),
          restoring: false,
        );
        expect(find.byType(MemberHandoffScreen), findsOneWidget);
        expect(requests, 1);
        expectNativeMatrixEndpoint(
          (await repository.loadConfiguration())!
              .serviceEndpoints
              .matrixHomeserverUrl,
          Uri.parse('https://api.weave.local/'),
        );
        expect(
          (await repository.loadConfiguration())!.organizationOrigin?.origin,
          'https://weave.example',
        );
        await tester.pumpWidget(const SizedBox.shrink());
        await tester.pumpAndSettle();
        final secureStore = InMemorySecureStore();
        await secureStore.write(
          authSessionStorageKey,
          AuthSessionDto.fromSession(
            buildTestAuthSession(
              issuer: 'https://auth.weave.local/realms/weave',
            ),
          ).encode(),
        );
        final restored = createContainer(
          configuration: null,
          secureStore: secureStore,
          preferencesStore: store,
          usePersistedConfiguration: true,
          configurationRepository: repository,
          discoveryClient: client,
        );
        addTearDown(restored.dispose);
        await tester.pumpWidget(
          UncontrolledProviderScope(
            container: restored,
            child: const WeaveApp(),
          ),
        );
        await prepareNativeCanonicalOrganization(
          tester,
          Uri.parse('https://weave.example'),
          deadline: DateTime.now().add(const Duration(seconds: 3)),
          restoring: true,
        );
        expect(
          find.byKey(const ValueKey('weave.workspace.home')),
          findsOneWidget,
        );
        expect(find.byType(OrganizationAccessScreen), findsNothing);
        expect(find.byType(MemberHandoffScreen), findsNothing);
        expect(
          requests,
          greaterThan(1),
          reason: 'Restore retains production manifest refresh.',
        );
        await tester.pumpWidget(const SizedBox.shrink());
        await tester.pumpAndSettle();
      },
    );

    testWidgets(
      'interactive bootstrap retry retains actual routed state and blocks stale content',
      (tester) async {
        final resolver = _ControlledBootstrapResolver();
        final signIn = _ControlledInteractiveSignIn();
        final container = createContainer(
          configuration: buildTestConfiguration(),
          resolver: resolver,
          interactiveSignIn: signIn,
        );
        addTearDown(container.dispose);
        await container.read(appBootstrapProvider.future);
        await tester.pumpWidget(
          UncontrolledProviderScope(
            container: container,
            child: const WeaveApp(),
          ),
        );
        await tester.pumpAndSettle();
        final router = container.read(appRouterProvider);
        final initialElement = tester.element(find.byType(SignInScreen));
        expect(TickerMode.valuesOf(initialElement).enabled, isTrue);
        final focus = Focus.of(
          tester.element(
            find
                .descendant(
                  of: find.byKey(const ValueKey('weave.auth.sign-in')),
                  matching: find.byType(Text),
                )
                .first,
          ),
        );
        focus.requestFocus();
        await tester.pump();
        expect(focus.hasFocus, isTrue);
        await tester.ensureVisible(
          find.byKey(const ValueKey('weave.auth.sign-in')),
        );
        await tester.pumpAndSettle();
        await tester.tap(find.byKey(const ValueKey('weave.auth.sign-in')));
        await tester.pump();
        expect(signIn.calls, 1);
        signIn.returned.complete();
        await tester.pump();
        await tester.pump(const Duration(milliseconds: 10));
        expect(container.read(appBootstrapProvider).isLoading, isTrue);
        expect(initialElement.mounted, isTrue);
        expect(TickerMode.valuesOf(initialElement).enabled, isFalse);
        expect(container.read(appRouterProvider), same(router));
        expect(focus.hasFocus, isFalse);
        expect(
          tester.semantics.simulatedAccessibilityTraversal().map(
            (node) => node.label,
          ),
          isNot(contains('Sign In')),
        );
        expect(
          find.byType(SignInScreen),
          findsNothing,
          reason: 'Pending bootstrap must block stale routed content',
        );
        expect(find.byType(SignInScreen, skipOffstage: false), findsOneWidget);
        resolver.next.complete(const BootstrapState.ready());
        await tester.pumpAndSettle();
        expect(find.byType(HomeScreen), findsOneWidget);
        expect(
          TickerMode.valuesOf(tester.element(find.byType(HomeScreen))).enabled,
          isTrue,
        );
        expect(tester.takeException(), isNull);
      },
    );

    testWidgets(
      'actual handoff records workspace proof after retained bootstrap retry',
      (tester) async {
        final resolver = _ControlledBootstrapResolver();
        final signIn = _ControlledInteractiveSignIn();
        final store = InMemoryPreferencesStore();
        final container = createContainer(
          configuration: buildTestConfiguration(),
          resolver: resolver,
          interactiveSignIn: signIn,
          preferencesStore: store,
          useHandoffDiscoveryFixture: true,
        );
        addTearDown(container.dispose);
        await container.read(appBootstrapProvider.future);
        await tester.pumpWidget(
          UncontrolledProviderScope(
            container: container,
            child: const WeaveApp(),
          ),
        );
        await tester.pumpAndSettle();
        final router = container.read(appRouterProvider);
        router.go(
          '/join?organization_origin=https%3A%2F%2Fweave.home.internal',
        );
        for (var attempt = 0; attempt < 25; attempt++) {
          await tester.runAsync(
            () => Future<void>.delayed(const Duration(milliseconds: 10)),
          );
          await tester.pumpAndSettle();
          if (find.text('Sign In').evaluate().isNotEmpty) break;
        }
        final handoff = tester.element(find.byType(MemberHandoffScreen));
        await tester.ensureVisible(find.text('Sign In'));
        await tester.tap(find.text('Sign In'));
        await tester.pump();
        expect(signIn.calls, 1);
        signIn.returned.complete();
        await tester.pump();
        expect(container.read(appBootstrapProvider).isLoading, isTrue);
        expect(handoff.mounted, isTrue);
        expect(find.byType(MemberHandoffScreen), findsNothing);
        expect(container.read(appRouterProvider), same(router));
        resolver.next.complete(const BootstrapState.ready());
        await tester.pumpAndSettle();
        expect(find.byType(HomeScreen), findsOneWidget);
        expect(
          jsonDecode(store.rawString(dogfoodAuthStateStorageKey)!)['state'],
          'workspace_ready',
        );
        expect(tester.takeException(), isNull);
      },
    );

    testWidgets('cold bootstrap error leaves incoming handoff unmounted', (
      tester,
    ) async {
      setStartupInitialLocation(
        '/join?organization_origin=https%3A%2F%2Fweave.home.internal',
      );
      addTearDown(() => setStartupInitialLocation(null));
      final store = InMemoryPreferencesStore();
      final container = createContainer(
        configuration: buildTestConfiguration(),
        resolver: _ControlledBootstrapResolver(
          initial: const BootstrapState.error(
            AppFailure.validation('Initial failure'),
          ),
        ),
        preferencesStore: store,
        useHandoffDiscoveryFixture: true,
      );
      addTearDown(container.dispose);
      await container.read(appBootstrapProvider.future);
      await tester.pumpWidget(
        UncontrolledProviderScope(
          container: container,
          child: const WeaveApp(),
        ),
      );
      await tester.pumpAndSettle();
      expect(
        find.byType(MemberHandoffScreen, skipOffstage: false),
        findsNothing,
      );
      expect(store.rawString(dogfoodAuthStateStorageKey), isNull);
      expect(find.byType(HomeScreen), findsNothing);
      expect(tester.takeException(), isNull);
    });

    testWidgets(
      'incoming handoff waits for pending bootstrap before discovery or reuse',
      (tester) async {
        final resolver = _ControlledBootstrapResolver(
          initial: const BootstrapState.ready(),
        );
        final store = InMemoryPreferencesStore();
        final container = createContainer(
          configuration: buildTestConfiguration(),
          resolver: resolver,
          preferencesStore: store,
          useHandoffDiscoveryFixture: true,
        );
        addTearDown(container.dispose);
        await container.read(appBootstrapProvider.future);
        await tester.pumpWidget(
          UncontrolledProviderScope(
            container: container,
            child: const WeaveApp(),
          ),
        );
        await tester.pump(const Duration(milliseconds: 100));
        final retry = container.read(appBootstrapProvider.notifier).retry();
        await tester.pump();
        container
            .read(appRouterProvider)
            .go('/join?organization_origin=https%3A%2F%2Fweave.home.internal');
        await tester.pump(const Duration(milliseconds: 100));
        await tester.runAsync(
          () => Future<void>.delayed(const Duration(milliseconds: 20)),
        );
        await tester.pump(const Duration(milliseconds: 100));
        expect(
          store.rawString(dogfoodAuthStateStorageKey),
          isNull,
          reason:
              'Blocked refresh must not start discovery or record stale workspace readiness',
        );
        resolver.next.complete(const BootstrapState.ready());
        await retry;
        await tester.pumpAndSettle();
        expect(find.byType(HomeScreen), findsOneWidget);
        expect(
          jsonDecode(store.rawString(dogfoodAuthStateStorageKey)!)['state'],
          'workspace_ready',
        );
        expect(tester.takeException(), isNull);
      },
    );

    testWidgets(
      'disposed handoff cannot attribute cancelled auth to workspace readiness',
      (tester) async {
        final resolver = _ControlledBootstrapResolver();
        final signIn = _ControlledInteractiveSignIn();
        final store = InMemoryPreferencesStore();
        final container = createContainer(
          configuration: buildTestConfiguration(),
          resolver: resolver,
          interactiveSignIn: signIn,
          preferencesStore: store,
          useHandoffDiscoveryFixture: true,
        );
        addTearDown(container.dispose);
        await container.read(appBootstrapProvider.future);
        await tester.pumpWidget(
          UncontrolledProviderScope(
            container: container,
            child: const WeaveApp(),
          ),
        );
        await tester.pumpAndSettle();
        container
            .read(appRouterProvider)
            .go('/join?organization_origin=https%3A%2F%2Fweave.home.internal');
        for (var attempt = 0; attempt < 25; attempt++) {
          await tester.runAsync(
            () => Future<void>.delayed(const Duration(milliseconds: 10)),
          );
          await tester.pumpAndSettle();
          if (find.text('Sign In').evaluate().isNotEmpty) break;
        }
        await tester.tap(find.text('Sign In'));
        await tester.pump();
        expect(signIn.calls, 1);
        await tester.pumpWidget(const SizedBox.shrink());
        signIn.returned.completeError(const AuthFailure.cancelled('Cancelled'));
        await tester.pumpAndSettle();
        expect(tester.takeException(), isNull);
        expect(resolver.calls, 1);
        expect(
          jsonDecode(store.rawString(dogfoodAuthStateStorageKey)!)['state'],
          isNot('workspace_ready'),
        );
      },
    );

    for (final outcome in [
      'needsSetup',
      'needsSignIn',
      'error',
      'thrown-error',
      'data-loading',
    ]) {
      testWidgets('retained ready route blocks refresh and resolves $outcome', (
        tester,
      ) async {
        final resolver = _ControlledBootstrapResolver(
          initial: const BootstrapState.ready(),
        );
        final container = createContainer(
          configuration: buildTestConfiguration(),
          resolver: resolver,
        );
        addTearDown(container.dispose);
        await container.read(appBootstrapProvider.future);
        await tester.pumpWidget(
          UncontrolledProviderScope(
            container: container,
            child: const WeaveApp(),
          ),
        );
        await tester.pumpAndSettle();
        final router = container.read(appRouterProvider);
        final retry = container.read(appBootstrapProvider.notifier).retry();
        await tester.pump();
        expect(find.byType(HomeScreen), findsNothing);
        expect(find.byType(HomeScreen, skipOffstage: false), findsOneWidget);
        expect(
          container.read(resolvedBootstrapStateProvider).phase,
          BootstrapPhase.loading,
        );
        if (outcome == 'thrown-error') {
          resolver.next.completeError(StateError('Resolver failed'));
        } else {
          resolver.next.complete(switch (outcome) {
            'needsSetup' => const BootstrapState.needsSetup(),
            'needsSignIn' => const BootstrapState.needsSignIn(),
            'data-loading' => const BootstrapState.loading(),
            _ => const BootstrapState.error(
              AppFailure.validation('Blocked refresh'),
            ),
          });
        }
        if (outcome == 'thrown-error') {
          await expectLater(retry, throwsStateError);
        } else {
          await retry;
        }
        if (outcome == 'data-loading') {
          await tester.pump(const Duration(milliseconds: 100));
        } else {
          await tester.pumpAndSettle();
        }
        expect(container.read(appRouterProvider), same(router));
        expect(find.byType(HomeScreen), findsNothing);
        expect(
          find.byType(OrganizationAccessScreen),
          outcome == 'needsSetup' ? findsOneWidget : findsNothing,
        );
        expect(
          find.byType(SignInScreen),
          outcome == 'needsSignIn' ? findsOneWidget : findsNothing,
        );
        expect(tester.takeException(), isNull);
      });
    }

    testWidgets(
      'disposed ordinary sign-in cannot read widget ref after cancellation',
      (tester) async {
        final resolver = _ControlledBootstrapResolver();
        final signIn = _ControlledInteractiveSignIn();
        final container = createContainer(
          configuration: buildTestConfiguration(),
          resolver: resolver,
          interactiveSignIn: signIn,
        );
        addTearDown(container.dispose);
        await container.read(appBootstrapProvider.future);
        await tester.pumpWidget(
          UncontrolledProviderScope(
            container: container,
            child: const WeaveApp(),
          ),
        );
        await tester.pumpAndSettle();
        final button = find.byKey(const ValueKey('weave.auth.sign-in'));
        await tester.ensureVisible(button);
        await tester.pumpAndSettle();
        await tester.tap(button);
        await tester.pump();
        expect(signIn.calls, 1);
        await tester.pumpWidget(const SizedBox.shrink());
        signIn.returned.completeError(const AuthFailure.cancelled('Cancelled'));
        await tester.pumpAndSettle();
        expect(tester.takeException(), isNull);
        expect(resolver.calls, 1);
      },
    );

    for (final failure in ['data-error', 'thrown-error', 'needsSignIn']) {
      testWidgets(
        'ordinary sign-in never records workspace-ready after $failure',
        (tester) async {
          final resolver = _ControlledBootstrapResolver();
          final signIn = _ControlledInteractiveSignIn();
          final store = InMemoryPreferencesStore({
            lastHandoffConsumedStorageKey: jsonEncode({
              'supportSafe': true,
              'organizationOriginHost': 'weave.home.internal',
            }),
          });
          final container = createContainer(
            configuration: buildTestConfiguration(),
            resolver: resolver,
            interactiveSignIn: signIn,
            preferencesStore: store,
          );
          addTearDown(container.dispose);
          await container.read(appBootstrapProvider.future);
          await tester.pumpWidget(
            UncontrolledProviderScope(
              container: container,
              child: const WeaveApp(),
            ),
          );
          await tester.pumpAndSettle();
          final button = find.byKey(const ValueKey('weave.auth.sign-in'));
          await tester.ensureVisible(button);
          await tester.pumpAndSettle();
          await tester.tap(button);
          await tester.pump();
          expect(signIn.calls, 1);
          signIn.returned.complete();
          await tester.pump();
          if (failure == 'thrown-error') {
            resolver.next.completeError(StateError('Resolver failed'));
          } else {
            resolver.next.complete(
              failure == 'data-error'
                  ? const BootstrapState.error(
                      AppFailure.validation('Blocked workspace'),
                    )
                  : const BootstrapState.needsSignIn(),
            );
          }
          await tester.pumpAndSettle();
          expect(find.byType(HomeScreen), findsNothing);
          expect(
            jsonDecode(store.rawString(dogfoodAuthStateStorageKey)!)['state'],
            'recoverable_error',
          );
          final history =
              jsonDecode(store.rawString(dogfoodAuthStateHistoryStorageKey)!)
                  as List;
          expect(
            history.map((entry) => entry['state']),
            isNot(contains('workspace_ready')),
          );
          expect(tester.takeException(), isNull);
        },
      );
    }

    testWidgets(
      'held authenticated profile cannot record readiness during a newer bootstrap refresh',
      (tester) async {
        final configuration = buildTestConfiguration();
        final repository = _HeldServerConfigurationRepository(
          configuration: configuration,
        );
        final resolver = _ControlledBootstrapResolver(
          initial: const BootstrapState.ready(),
        );
        final store = InMemoryPreferencesStore();
        final container = createContainer(
          configuration: configuration,
          resolver: resolver,
          configurationRepository: repository,
          preferencesStore: store,
        );
        addTearDown(container.dispose);
        await container.read(appBootstrapProvider.future);
        await tester.pumpWidget(
          UncontrolledProviderScope(
            container: container,
            child: const WeaveApp(),
          ),
        );
        await tester.pumpAndSettle();
        repository.hold = true;
        container
            .read(appRouterProvider)
            .go('/join?organization_origin=https%3A%2F%2Fweave.home.internal');
        await tester.pump(const Duration(milliseconds: 100));
        final retry = container.read(appBootstrapProvider.notifier).retry();
        await tester.pump();
        repository.pendingLoad.complete(configuration);
        await tester.pump(const Duration(milliseconds: 100));
        expect(
          store.rawString(dogfoodAuthStateStorageKey),
          isNull,
          reason:
              'A prior ready snapshot must not authorize proof after a newer refresh starts',
        );
        expect(find.byType(HomeScreen), findsNothing);
        expect(repository.configuration, same(configuration));
        resolver.next.complete(const BootstrapState.needsSignIn());
        await retry;
        await tester.pumpAndSettle();
        expect(find.byType(HomeScreen), findsNothing);
        expect(store.rawString(dogfoodAuthStateStorageKey), isNull);
        expect(repository.configuration, same(configuration));
        expect(tester.takeException(), isNull);
      },
    );

    for (final target in [
      'https://weave.home.internal',
      'https://weave.home.internal:443/',
      'https://other.example',
      'https://weave.home.internal:4443',
    ]) {
      testWidgets(
        'authenticated handoff binds existing profile origin $target',
        (tester) async {
          final resolver = _ControlledBootstrapResolver(
            initial: const BootstrapState.ready(),
          );
          final store = InMemoryPreferencesStore();
          final configuration = buildTestConfiguration();
          final container = createContainer(
            configuration: configuration,
            resolver: resolver,
            preferencesStore: store,
          );
          addTearDown(container.dispose);
          await container.read(appBootstrapProvider.future);
          await tester.pumpWidget(
            UncontrolledProviderScope(
              container: container,
              child: const WeaveApp(),
            ),
          );
          await tester.pumpAndSettle();
          container
              .read(appRouterProvider)
              .go('/join?organization_origin=${Uri.encodeComponent(target)}');
          await tester.pumpAndSettle();
          final accepted =
              !target.contains('other.example') && !target.contains('4443');
          expect(
            find.byType(ErrorState),
            accepted ? findsNothing : findsOneWidget,
          );
          expect(
            find.byType(HomeScreen),
            accepted ? findsOneWidget : findsNothing,
          );
          expect(
            await container
                .read(serverConfigurationRepositoryProvider)
                .loadConfiguration(),
            same(configuration),
          );
          final raw = store.rawString(dogfoodAuthStateStorageKey);
          expect(
            raw == null ? null : jsonDecode(raw)['state'],
            accepted ? 'workspace_ready' : isNot('workspace_ready'),
          );
          expect(tester.takeException(), isNull);
        },
      );
    }

    for (final extra in [
      '',
      '&unexpected=1',
      '&dogfood_reset=app_state',
      '#fragment',
      'outer-credentials',
      'encoded-origin-credentials',
    ]) {
      testWidgets(
        extra.isEmpty
            ? 'pasted canonical app completion link reaches actual handoff discovery on needsSetup router'
            : 'pasted app completion link rejects unsupported extra controls $extra',
        (tester) async {
          var discoveryCalls = 0;
          final store = InMemoryPreferencesStore();
          final client = MockClient((request) async {
            discoveryCalls++;
            expect(
              request.url,
              Uri.parse('https://weave.example/api/platform/config'),
            );
            final manifest =
                jsonDecode(
                      File(
                        'test/fixtures/org_manifest_v2_valid.json',
                      ).readAsStringSync(),
                    )
                    as Map<String, dynamic>;
            manifest['organizationOrigin'] = 'https://weave.example';
            return http.Response(
              jsonEncode(manifest),
              200,
              headers: {'content-type': 'application/json'},
            );
          });
          final container = ProviderContainer.test(
            overrides: [
              preferencesStoreProvider.overrideWith((ref) => store),
              secureStoreProvider.overrideWithValue(InMemorySecureStore()),
              appStartDiscoveryClientProvider.overrideWithValue(
                AppStartDiscoveryClient(httpClient: client),
              ),
              discoverOrganizationAccessProvider.overrideWith(
                (ref) => DiscoverOrganizationAccess(
                  repository: ref.watch(serverConfigurationRepositoryProvider),
                  discoveryClient: AppStartDiscoveryClient(httpClient: client),
                  evidenceStore: store,
                ),
              ),
            ],
          );
          addTearDown(container.dispose);
          final routerSubscription = container.listen(
            appRouterProvider,
            (_, _) {},
          );
          addTearDown(routerSubscription.close);
          final bootstrap = await container.read(appBootstrapProvider.future);
          expect(bootstrap.phase, BootstrapPhase.needsSetup);
          await tester.pumpWidget(
            UncontrolledProviderScope(
              container: container,
              child: MaterialApp.router(
                localizationsDelegates: AppLocalizations.localizationsDelegates,
                supportedLocales: AppLocalizations.supportedLocales,
                routerConfig: container.read(appRouterProvider),
              ),
            ),
          );
          await tester.pumpAndSettle();
          expect(find.byType(OrganizationAccessScreen), findsOneWidget);
          await tester.enterText(
            find.byType(TextField),
            extra == 'outer-credentials'
                ? 'weave://user:secret@join?organization_origin=https%3A%2F%2Fweave.example'
                : extra == 'encoded-origin-credentials'
                ? 'weave://join?organization_origin=https%3A%2F%2Fuser%3Asecret%40weave.example'
                : 'weave://join?organization_origin=https%3A%2F%2Fweave.example$extra',
          );
          await tester.tap(find.text('Continue to organization'));
          for (var attempt = 0; attempt < 50; attempt++) {
            await tester.runAsync(
              () => Future<void>.delayed(const Duration(milliseconds: 10)),
            );
            await tester.pumpAndSettle();
            if (store.rawString(dogfoodVisibleStateStorageKey) != null) break;
          }
          final rejectedAtEntry = {
            '#fragment',
            'outer-credentials',
          }.contains(extra);
          expect(
            find.byType(MemberHandoffScreen),
            rejectedAtEntry ? findsNothing : findsOneWidget,
          );
          expect(
            find.byType(OrganizationAccessScreen),
            rejectedAtEntry ? findsOneWidget : findsNothing,
          );
          if (extra.isEmpty) {
            expect(find.text('Sign In'), findsOneWidget);
            expect(discoveryCalls, 1);
            expect(
              jsonDecode(store.rawString(dogfoodAuthStateStorageKey)!)['state'],
              'ready_for_sso',
            );
            final profile = await container.read(
              savedServerConfigurationProvider.future,
            );
            expect(
              profile!.organizationOrigin,
              Uri.parse('https://weave.example/'),
            );
          } else {
            if (!rejectedAtEntry) {
              expect(find.byType(ErrorState), findsOneWidget);
            }
            expect(discoveryCalls, 0);
            expect(
              await container.read(savedServerConfigurationProvider.future),
              isNull,
            );
          }
        },
      );
    }
    for (final locale in ['en', 'de']) {
      for (final deepLink in [false, true]) {
        for (final successful in deepLink ? [true, false] : [true]) {
          testWidgets(
            'handoff Back preserves entry and profile $locale deepLink=$deepLink successful=$successful',
            (tester) async {
              final repository = _FakeServerConfigurationRepository(
                configuration: deepLink ? buildTestConfiguration() : null,
              );
              final client = MockClient((request) async {
                final manifest =
                    jsonDecode(
                          File(
                            'test/fixtures/org_manifest_v2_valid.json',
                          ).readAsStringSync(),
                        )
                        as Map<String, dynamic>;
                manifest['organizationOrigin'] = 'https://weave.example';
                return http.Response(
                  successful ? jsonEncode(manifest) : '{}',
                  successful ? 200 : 503,
                  headers: {'content-type': 'application/json'},
                );
              });
              addTearDown(client.close);
              final container = createContainer(
                configuration: null,
                configurationRepository: repository,
                discoveryClient: AppStartDiscoveryClient(httpClient: client),
              );
              addTearDown(container.dispose);
              await container.read(appBootstrapProvider.future);
              final routerSubscription = container.listen(
                appRouterProvider,
                (_, _) {},
              );
              addTearDown(routerSubscription.close);
              final router = routerSubscription.read();
              if (deepLink) {
                router.go(
                  '/join?organization_origin=https%3A%2F%2Fweave.example',
                );
              }
              await tester.pumpWidget(
                UncontrolledProviderScope(
                  container: container,
                  child: MaterialApp.router(
                    locale: Locale(locale),
                    localizationsDelegates:
                        AppLocalizations.localizationsDelegates,
                    supportedLocales: AppLocalizations.supportedLocales,
                    routerConfig: router,
                  ),
                ),
              );
              await tester.pumpAndSettle();
              final copy = AppLocalizations.of(
                tester.element(find.byType(Scaffold).first),
              );
              if (!deepLink) {
                await tester.enterText(find.byType(TextField), 'weave.example');
                await tester.tap(
                  find.text(copy.setupOrganizationContinueButton),
                );
              }
              for (var attempt = 0; attempt < 50; attempt++) {
                await tester.runAsync(
                  () => Future<void>.delayed(const Duration(milliseconds: 10)),
                );
                await tester.pumpAndSettle();
                if (find
                    .byTooltip(copy.signInBackToSetupButton)
                    .evaluate()
                    .isNotEmpty) {
                  break;
                }
              }
              expect(
                find.text(
                  successful
                      ? copy.memberHandoffReadyTitle
                      : copy.memberHandoffErrorTitle,
                ),
                findsOneWidget,
              );
              final saved = repository.configuration;
              expect(saved, isNotNull);
              await tester.tap(find.byTooltip(copy.signInBackToSetupButton));
              await tester.pumpAndSettle();
              expect(find.byType(OrganizationAccessScreen), findsOneWidget);
              expect(
                tester
                    .widget<TextField>(find.byType(TextField))
                    .controller!
                    .text,
                deepLink ? '' : 'weave.example',
              );
              expect(
                repository.configuration,
                same(saved),
                reason: 'Back is navigation, not a workspace reset.',
              );
              expect(tester.takeException(), isNull);
            },
          );
        }
      }
    }

    testWidgets(
      'saved sign-in can revisit public entry while product routes remain protected',
      (tester) async {
        final container = createContainer(
          configuration: buildTestConfiguration(),
        );
        addTearDown(container.dispose);
        await container.read(appBootstrapProvider.future);
        final router = container.read(appRouterProvider);
        await tester.pumpWidget(
          UncontrolledProviderScope(
            container: container,
            child: const WeaveApp(),
          ),
        );
        await tester.pumpAndSettle();
        expect(find.byType(SignInScreen), findsOneWidget);
        router.go(AppRoutes.organizationAccess);
        await tester.pumpAndSettle();
        expect(find.byType(OrganizationAccessScreen), findsOneWidget);
        for (final location in [
          AppRoutes.home,
          AppRoutes.chat,
          AppRoutes.files,
          AppRoutes.calendar,
          AppRoutes.settings,
        ]) {
          router.go(location);
          await tester.pumpAndSettle();
          expect(find.byType(SignInScreen), findsOneWidget, reason: location);
          expect(find.byType(HomeScreen), findsNothing);
          expect(find.byType(FilesScreen), findsNothing);
          expect(find.byType(CalendarScreen), findsNothing);
        }
        expect(tester.takeException(), isNull);
      },
    );

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
          (AppRoutes.files, FilesLocationsScreen),
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
          '/join?handoff_ref=handoff-s32-massimo-dogfood-home&org=massimo-dogfood&workspace=home&profile=local-lan-dogfood&run_id=s32-massimo-dogfood&product_base_url=https://weave.home.internal&platform_config_url=https://weave.home.internal/api/platform/config',
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
