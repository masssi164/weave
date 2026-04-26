import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:weave/core/persistence/flutter_secure_store.dart';
import 'package:weave/features/auth/data/dtos/auth_session_dto.dart';
import 'package:weave/features/auth/data/repositories/oidc_auth_session_repository.dart';
import 'package:weave/features/auth/data/services/flutter_appauth_oidc_client.dart';
import 'package:weave/features/auth/data/services/oidc_client.dart';
import 'package:weave/features/calendar/domain/entities/calendar_event.dart';
import 'package:weave/features/calendar/domain/repositories/calendar_repository.dart';
import 'package:weave/features/calendar/presentation/providers/calendar_provider.dart';
import 'package:weave/features/chat/presentation/providers/chat_repository_provider.dart';
import 'package:weave/features/chat/presentation/providers/chat_security_repository_provider.dart';
import 'package:weave/features/profile/presentation/providers/user_profile_provider.dart';
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
  group('AppShell', () {
    ProviderScope buildApp() {
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
          chatRepositoryProvider.overrideWithValue(FakeChatRepository()),
          chatSecurityRepositoryProvider.overrideWithValue(
            FakeChatSecurityRepository(),
          ),
          userProfileProvider.overrideWith((ref) async => null),
          calendarRepositoryProvider.overrideWithValue(
            _EmptyCalendarRepository(),
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
      expect(find.byIcon(Icons.calendar_today_outlined), findsOneWidget);
      expect(find.byIcon(Icons.settings_outlined), findsOneWidget);
      expect(find.byIcon(Icons.dashboard_outlined), findsNothing);
    });

    testWidgets('navigates to calendar from the bottom navigation bar', (
      tester,
    ) async {
      await tester.pumpWidget(buildApp());
      await tester.pumpAndSettle();

      await tester.tap(find.byIcon(Icons.calendar_today_outlined));
      await tester.pumpAndSettle();

      expect(find.text('No events yet'), findsOneWidget);
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
  });
}
