import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:weave/core/persistence/flutter_secure_store.dart';
import 'package:weave/core/router/app_routes.dart';
import 'package:weave/core/router/app_router.dart';
import 'package:weave/features/auth/data/dtos/auth_session_dto.dart';
import 'package:weave/features/auth/data/repositories/oidc_auth_session_repository.dart';
import 'package:weave/features/auth/data/services/flutter_appauth_oidc_client.dart';
import 'package:weave/features/auth/data/services/oidc_client.dart';
import 'package:weave/features/chat/presentation/providers/chat_repository_provider.dart';
import 'package:weave/features/auth/presentation/sign_in_screen.dart';
import 'package:weave/features/files/domain/entities/files_connection_state.dart';
import 'package:weave/features/files/presentation/providers/files_repository_provider.dart';
import 'package:weave/features/calendar/domain/entities/calendar_event.dart';
import 'package:weave/features/calendar/domain/repositories/calendar_repository.dart';
import 'package:weave/features/calendar/presentation/calendar_screen.dart';
import 'package:weave/features/calendar/presentation/providers/calendar_provider.dart';
import 'package:weave/features/onboarding/domain/entities/first_run_status.dart';
import 'package:weave/features/onboarding/presentation/first_run_screen.dart';
import 'package:weave/features/onboarding/presentation/providers/first_run_status_provider.dart';
import 'package:weave/features/onboarding/presentation/welcome_screen.dart';
import 'package:weave/features/profile/presentation/providers/user_profile_provider.dart';
import 'package:weave/features/server_config/domain/entities/server_configuration.dart';
import 'package:weave/features/server_config/domain/repositories/server_configuration_repository.dart';
import 'package:weave/features/server_config/presentation/providers/server_configuration_repository_provider.dart';
import 'package:weave/main.dart';

import '../../helpers/auth_test_data.dart';
import '../../helpers/fake_chat_repository.dart';
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

class _EmptyCalendarRepository implements CalendarRepository {
  @override
  Future<CalendarEvent> createEvent(CalendarEventDraft draft) {
    throw UnimplementedError();
  }

  @override
  Future<void> deleteEvent(String id) async {}

  @override
  Future<List<CalendarEvent>> loadEvents() async => const <CalendarEvent>[];

  @override
  Future<CalendarEvent> updateEvent(String id, CalendarEventDraft draft) {
    throw UnimplementedError();
  }
}

void main() {
  group('AppRouter', () {
    ProviderContainer createContainer({
      required ServerConfiguration? configuration,
      InMemorySecureStore? secureStore,
      FirstRunStatus? firstRunStatus,
    }) {
      final container = ProviderContainer.test(
        overrides: [
          serverConfigurationRepositoryProvider.overrideWith(
            (ref) => _FakeServerConfigurationRepository(
              configuration: configuration,
            ),
          ),
          secureStoreProvider.overrideWithValue(
            secureStore ?? InMemorySecureStore(),
          ),
          oidcClientProvider.overrideWithValue(_FakeOidcClient()),
          chatRepositoryProvider.overrideWithValue(FakeChatRepository()),
          filesRepositoryProvider.overrideWithValue(
            FakeFilesRepository(
              connectionState: const FilesConnectionState.disconnected(),
            ),
          ),
          firstRunStatusProvider.overrideWith(
            (ref) async => firstRunStatus ?? buildTestFirstRunStatus(),
          ),
          userProfileProvider.overrideWith((ref) async => null),
          calendarRepositoryProvider.overrideWithValue(
            _EmptyCalendarRepository(),
          ),
        ],
      );
      return container;
    }

    testWidgets('shows welcome flow when no saved configuration exists', (
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

      expect(find.byType(WelcomeScreen), findsOneWidget);
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

      expect(find.byType(FirstRunScreen), findsNothing);
      expect(find.byType(NavigationBar), findsOneWidget);
    });

    testWidgets('redirects pending first-run users to status guidance', (
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
        firstRunStatus: buildTestFirstRunStatus(
          firstRunComplete: false,
          matrix: const FirstRunModuleStatus(
            state: FirstRunProvisioningState.pending,
            message: 'Matrix chat provisioning is pending.',
            action: 'Wait briefly, then refresh status.',
          ),
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

      container.read(appRouterProvider).go(AppRoutes.calendar);
      await tester.pumpAndSettle();

      expect(find.byType(FirstRunScreen), findsOneWidget);
      expect(
        find.text('Your Weave workspace is being prepared'),
        findsOneWidget,
      );
      expect(find.text('Wait briefly, then refresh status.'), findsOneWidget);
    });

    testWidgets('opens the calendar route when ready', (tester) async {
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

      container.read(appRouterProvider).go(AppRoutes.calendar);
      await tester.pumpAndSettle();

      expect(find.byType(CalendarScreen), findsOneWidget);
    });

    testWidgets('redirects shell routes back to welcome when setup is needed', (
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

      container.read(appRouterProvider).go(AppRoutes.settings);
      await tester.pumpAndSettle();

      expect(find.byType(WelcomeScreen), findsOneWidget);
    });

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
