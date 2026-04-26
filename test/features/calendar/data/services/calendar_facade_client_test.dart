import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:weave/core/failures/app_failure.dart';
import 'package:weave/features/auth/domain/entities/auth_configuration.dart';
import 'package:weave/features/auth/domain/entities/auth_state.dart';
import 'package:weave/features/auth/domain/repositories/auth_session_repository.dart';
import 'package:weave/features/calendar/data/services/calendar_facade_client.dart';
import 'package:weave/features/server_config/domain/entities/server_configuration.dart';
import 'package:weave/features/server_config/domain/repositories/server_configuration_repository.dart';

import '../../../../helpers/auth_test_data.dart';
import '../../../../helpers/server_config_test_data.dart';

class _FakeServerConfigurationRepository
    implements ServerConfigurationRepository {
  _FakeServerConfigurationRepository(this.configuration);

  ServerConfiguration? configuration;

  @override
  Future<void> clearConfiguration() async {}

  @override
  Future<ServerConfiguration?> loadConfiguration() async => configuration;

  @override
  Future<void> saveConfiguration(ServerConfiguration configuration) async {
    this.configuration = configuration;
  }
}

class _FakeAuthSessionRepository implements AuthSessionRepository {
  _FakeAuthSessionRepository(this.state);

  AuthState state;

  @override
  Future<void> clearLocalSession() async {}

  @override
  Future<AuthState> refreshSession(AuthConfiguration configuration) async =>
      state;

  @override
  Future<AuthState> restoreSession(AuthConfiguration configuration) async =>
      state;

  @override
  Future<void> signOut(AuthConfiguration configuration) async {}

  @override
  Future<AuthState> signIn(AuthConfiguration configuration) async => state;
}

void main() {
  group('CalendarFacadeClient', () {
    late _FakeServerConfigurationRepository configurationRepository;
    late _FakeAuthSessionRepository authSessionRepository;

    CalendarFacadeClient client(http.Client httpClient) {
      return CalendarFacadeClient(
        httpClient: httpClient,
        serverConfigurationRepository: configurationRepository,
        authSessionRepository: authSessionRepository,
      );
    }

    setUp(() {
      configurationRepository = _FakeServerConfigurationRepository(
        buildTestConfiguration(
          backendApiBaseUrl: 'https://api.home.internal/api',
        ),
      );
      authSessionRepository = _FakeAuthSessionRepository(
        AuthState.authenticated(
          buildTestAuthSession(accessToken: 'calendar-token'),
        ),
      );
    });

    test('lists events through the backend calendar facade', () async {
      late http.Request capturedRequest;
      final facade = client(
        MockClient((request) async {
          capturedRequest = request;
          return http.Response(
            jsonEncode({
              'events': [
                {
                  'id': 'calendar:personal:1',
                  'title': 'Planning',
                  'description': 'Roadmap',
                  'startsAt': '2026-04-26T09:00:00Z',
                  'endsAt': '2026-04-26T10:00:00Z',
                  'timezone': 'Europe/Berlin',
                  'location': 'Office',
                  'allDay': false,
                  'etag': 'abc',
                },
              ],
            }),
            200,
          );
        }),
      );

      final events = await facade.listEvents(
        from: DateTime.utc(2026, 4, 26),
        to: DateTime.utc(2026, 4, 27),
      );

      expect(capturedRequest.method, 'GET');
      expect(
        capturedRequest.url.toString(),
        'https://api.home.internal/api/calendar/events?from=2026-04-26T00%3A00%3A00.000Z&to=2026-04-27T00%3A00%3A00.000Z',
      );
      expect(capturedRequest.headers['authorization'], 'Bearer calendar-token');
      expect(events, hasLength(1));
      expect(events.single.title, 'Planning');
      expect(events.single.timezone, 'Europe/Berlin');
      expect(events.single.etag, 'abc');
    });

    test(
      'creates, updates, and deletes events through backend endpoints',
      () async {
        final requests = <http.Request>[];
        final facade = client(
          MockClient((request) async {
            requests.add(request);
            if (request.method == 'DELETE') {
              return http.Response('', 204);
            }
            return http.Response(
              jsonEncode({
                'id': 'calendar:personal:1',
                'title': 'Planning',
                'startsAt': '2026-04-26T09:00:00Z',
                'endsAt': '2026-04-26T10:00:00Z',
                'timezone': 'Europe/Berlin',
                'allDay': false,
              }),
              200,
            );
          }),
        );

        await facade.createEvent(
          CalendarEventDraft(
            title: 'Planning',
            startsAt: DateTime.utc(2026, 4, 26, 9),
            endsAt: DateTime.utc(2026, 4, 26, 10),
            timezone: 'Europe/Berlin',
          ),
        );
        await facade.updateEvent(
          id: 'calendar:personal:1',
          patch: const CalendarEventPatch(
            title: 'Updated Planning',
            etag: 'abc',
          ),
        );
        await facade.deleteEvent('calendar:personal:1');

        expect(requests.map((request) => '${request.method} ${request.url}'), [
          'POST https://api.home.internal/api/calendar/events',
          'PATCH https://api.home.internal/api/calendar/events/calendar:personal:1',
          'DELETE https://api.home.internal/api/calendar/events/calendar:personal:1',
        ]);
        expect(jsonDecode(requests.first.body)['timezone'], 'Europe/Berlin');
        expect(jsonDecode(requests[1].body), {
          'title': 'Updated Planning',
          'etag': 'abc',
        });
      },
    );

    test('maps backend failures without direct CalDAV fallback', () async {
      final facade = client(
        MockClient(
          (_) async => http.Response(
            jsonEncode({'message': 'Calendar facade is unavailable.'}),
            503,
          ),
        ),
      );

      await expectLater(
        facade.listEvents(),
        throwsA(
          isA<AppFailure>().having(
            (failure) => failure.message,
            'message',
            'Calendar facade is unavailable.',
          ),
        ),
      );
    });
  });
}
