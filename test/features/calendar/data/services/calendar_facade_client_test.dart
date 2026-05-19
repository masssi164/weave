import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:weave/core/failures/app_failure.dart';
import 'package:weave/features/auth/domain/entities/auth_configuration.dart';
import 'package:weave/features/auth/domain/entities/auth_state.dart';
import 'package:weave/features/auth/domain/repositories/auth_session_repository.dart';
import 'package:weave/features/calendar/data/services/calendar_facade_client.dart';
import 'package:weave/features/calendar/domain/entities/calendar_event.dart';
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

    test('loads workspace, team, and channel calendar scopes', () async {
      late http.Request capturedRequest;
      final facade = client(
        MockClient((request) async {
          capturedRequest = request;
          return http.Response(
            jsonEncode({
              'scopes': [
                {
                  'id': 'workspace',
                  'type': 'workspace',
                  'label': 'Weave workspace calendar',
                  'workspaceId': 'workspace',
                  'contextId': 'workspace-default',
                  'accessModel': 'shared-workspace-calendar',
                  'capabilities': ['read', 'create'],
                },
                {
                  'id': 'team:engineering',
                  'type': 'team',
                  'label': 'Engineering team calendar',
                  'workspaceId': 'workspace',
                  'contextId': 'team-engineering',
                  'teamId': 'engineering',
                  'accessModel': 'shared-team-calendar',
                  'capabilities': ['read', 'create'],
                },
                {
                  'id': 'channel:engineering-general',
                  'type': 'channel',
                  'label': 'Engineering / general channel calendar',
                  'workspaceId': 'workspace',
                  'contextId': 'channel-engineering-general',
                  'teamId': 'engineering',
                  'channelId': 'engineering-general',
                  'accessModel': 'shared-channel-calendar',
                  'capabilities': ['read', 'create'],
                },
              ],
            }),
            200,
          );
        }),
      );

      final scopes = await facade.listScopes();

      expect(capturedRequest.method, 'GET');
      expect(
        capturedRequest.url.toString(),
        'https://api.home.internal/api/calendar/scopes',
      );
      expect(scopes.scopes.map((scope) => scope.type), [
        'workspace',
        'team',
        'channel',
      ]);
      expect(scopes.scopes[1].teamId, 'engineering');
      expect(scopes.scopes[1].contextId, 'team-engineering');
      expect(scopes.scopes[2].channelId, 'engineering-general');
      expect(scopes.scopes[2].contextId, 'channel-engineering-general');
      expect(scopes.scopes[2].accessModel, 'shared-channel-calendar');
    });

    test('lists events through the backend calendar facade', () async {
      late http.Request capturedRequest;
      final facade = client(
        MockClient((request) async {
          capturedRequest = request;
          return http.Response(
            jsonEncode({
              'scope': {
                'type': 'workspace',
                'label': 'Weave workspace calendar',
              },
              'events': [
                {
                  'id': 'calendar:workspace:1',
                  'title': 'Planning',
                  'description': 'Roadmap',
                  'startsAt': '2026-04-26T09:00:00Z',
                  'endsAt': '2026-04-26T10:00:00Z',
                  'timezone': 'Europe/Berlin',
                  'location': 'Office',
                  'allDay': false,
                  'etag': 'abc',
                  'scope': {
                    'id': 'channel:engineering-general',
                    'type': 'channel',
                    'label': 'Engineering / general channel calendar',
                    'workspaceId': 'workspace',
                    'contextId': 'channel-engineering-general',
                    'teamId': 'engineering',
                    'channelId': 'engineering-general',
                  },
                  'threadRef': {
                    'kind': 'context',
                    'contextId': 'channel-engineering-general',
                    'channelId': 'engineering-general',
                    'matrixRoomId': null,
                    'matrixThreadId': null,
                    'boardTaskIds': [],
                  },
                  'attendees': [
                    {
                      'name': 'Ada Lovelace',
                      'email': 'ada@example.com',
                      'role': 'req-participant',
                      'responseStatus': 'accepted',
                    },
                  ],
                  'providerRef': {
                    'provider': 'nextcloud-caldav',
                    'objectKind': 'calendar-event',
                    'opaqueId': 'calendar:workspace:1',
                    'etag': 'abc',
                    'lastSyncedAt': '2026-04-25T09:00:00Z',
                    'rawProviderPathExposed': false,
                  },
                  'updatedAt': '2026-04-25T09:00:00Z',
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
        selectedScope: const CalendarScope(
          id: 'channel:engineering-general',
          type: 'channel',
          label: 'Engineering / general channel calendar',
          teamId: 'engineering',
          channelId: 'engineering-general',
        ),
      );

      expect(capturedRequest.method, 'GET');
      expect(
        capturedRequest.url.toString(),
        'https://api.home.internal/api/calendar/events?from=2026-04-26T00%3A00%3A00.000Z&to=2026-04-27T00%3A00%3A00.000Z&scopeType=channel&teamId=engineering&channelId=engineering-general',
      );
      expect(capturedRequest.headers['authorization'], 'Bearer calendar-token');
      expect(events.scope.type, 'workspace');
      expect(events.scope.label, 'Weave workspace calendar');
      expect(events.events, hasLength(1));
      expect(events.events.single.title, 'Planning');
      expect(events.events.single.timezone, 'Europe/Berlin');
      expect(events.events.single.etag, 'abc');
      expect(events.events.single.scope.type, 'channel');
      expect(
        events.events.single.scope.contextId,
        'channel-engineering-general',
      );
      expect(events.events.single.threadRef.kind, 'context');
      expect(
        events.events.single.threadRef.contextId,
        'channel-engineering-general',
      );
      expect(events.events.single.threadRef.channelId, 'engineering-general');
      expect(events.events.single.threadRef.matrixThreadId, isNull);
      expect(events.events.single.attendees.single.name, 'Ada Lovelace');
      expect(events.events.single.attendees.single.email, 'ada@example.com');
      expect(events.events.single.attendees.single.role, 'req-participant');
      expect(events.events.single.attendees.single.responseStatus, 'accepted');
      expect(events.events.single.providerRef?.provider, 'nextcloud-caldav');
      expect(events.events.single.providerRef?.objectKind, 'calendar-event');
      expect(
        events.events.single.providerRef?.opaqueId,
        'calendar:workspace:1',
      );
      expect(events.events.single.providerRef?.rawProviderPathExposed, isFalse);
      expect(
        events.events.single.providerRef?.lastSyncedAt,
        DateTime.parse('2026-04-25T09:00:00Z'),
      );
      expect(
        events.events.single.updatedAt,
        DateTime.parse('2026-04-25T09:00:00Z'),
      );
    });

    test(
      'defaults older calendar facade payloads to workspace scope',
      () async {
        final facade = client(
          MockClient(
            (_) async => http.Response(
              jsonEncode({
                'events': [
                  {
                    'id': 'calendar:workspace:1',
                    'title': 'Planning',
                    'startsAt': '2026-04-26T09:00:00Z',
                    'endsAt': '2026-04-26T10:00:00Z',
                  },
                ],
              }),
              200,
            ),
          ),
        );

        final events = await facade.listEvents();

        expect(events.scope, CalendarScope.workspace);
        expect(events.events.single.scope, CalendarScope.workspace);
      },
    );

    test('loads secret-free external calendar client setup metadata', () async {
      late http.Request capturedRequest;
      final facade = client(
        MockClient((request) async {
          capturedRequest = request;
          return http.Response(
            jsonEncode({
              'scope': {
                'type': 'workspace',
                'label': 'Weave workspace calendar',
              },
              'accessModel': {
                'type': 'workspace-calendar',
                'productScope': 'workspace',
                'privateUserCalendarsAvailable': false,
                'privateUserCalendarsReason':
                    'Private personal calendars require a reviewed access model.',
                'externalClientCredentialModel':
                    'nextcloud-login-flow-or-revocable-app-password',
                'notes': ['Workspace calendar setup only.'],
              },
              'credentialReadiness': {
                'status': 'blocked_until_revocable_credentials',
                'appleProfileSigned': false,
                'appleProfilePasswordIncluded': false,
                'revocableCredentialsAvailable': false,
                'readOnlySubscriptionTokensAvailable': false,
                'backendActorCredentialsExposed': false,
                'blockers': ['Apple profiles are unsigned.'],
              },
              'username': 'user-123',
              'endpoints': {
                'serverUrl': 'https://files.weave.local',
                'caldavDiscoveryUrl':
                    'https://files.weave.local/remote.php/dav',
                'principalUrl':
                    'https://files.weave.local/remote.php/dav/principals/users/user-123/',
              },
              'credentialPolicy':
                  'The backend never returns Nextcloud passwords, app passwords, bearer tokens, or static profile secrets.',
              'options': [
                {
                  'platform': 'apple',
                  'method': 'mobileconfig',
                  'available': false,
                  'unavailableReason':
                      'Signed .mobileconfig generation is not implemented yet.',
                  'guidance': ['Do not embed permanent passwords.'],
                },
                {
                  'platform': 'android',
                  'method': 'davx5',
                  'available': true,
                  'actionUrl': 'davx5://files.weave.local/remote.php/dav',
                  'guidance': ['Use DAVx5 for two-way sync.'],
                },
              ],
            }),
            200,
          );
        }),
      );

      final setup = await facade.clientSetup();

      expect(capturedRequest.method, 'GET');
      expect(
        capturedRequest.url.toString(),
        'https://api.home.internal/api/calendar/client-setup',
      );
      expect(capturedRequest.headers['authorization'], 'Bearer calendar-token');
      expect(setup.scope.type, 'workspace');
      expect(setup.username, 'user-123');
      expect(setup.accessModel.privateUserCalendarsAvailable, isFalse);
      expect(
        setup.accessModel.externalClientCredentialModel,
        'nextcloud-login-flow-or-revocable-app-password',
      );
      expect(setup.credentialReadiness.appleProfileSigned, isFalse);
      expect(setup.credentialReadiness.backendActorCredentialsExposed, isFalse);
      expect(
        setup.credentialReadiness.blockers,
        contains('Apple profiles are unsigned.'),
      );
      expect(setup.endpoints.serverUrl, 'https://files.weave.local');
      expect(
        setup.endpoints.principalUrl,
        'https://files.weave.local/remote.php/dav/principals/users/user-123/',
      );
      expect(setup.credentialPolicy, contains('never returns'));
      expect(setup.options.first.platform, 'apple');
      expect(setup.options.first.available, isFalse);
      expect(
        setup.options.last.actionUrl,
        'davx5://files.weave.local/remote.php/dav',
      );
    });

    test('reads event details through the backend calendar facade', () async {
      late http.Request capturedRequest;
      final facade = client(
        MockClient((request) async {
          capturedRequest = request;
          return http.Response(
            jsonEncode({
              'id': 'calendar:workspace:1',
              'title': 'Planning details',
              'description': 'Fetched from backend read endpoint',
              'startsAt': '2026-04-26T09:00:00Z',
              'endsAt': '2026-04-26T10:00:00Z',
              'timezone': 'Europe/Berlin',
              'allDay': false,
              'scope': {
                'type': 'workspace',
                'label': 'Weave workspace calendar',
              },
            }),
            200,
          );
        }),
      );

      final event = await facade.readEvent('calendar:workspace:1');

      expect(capturedRequest.method, 'GET');
      expect(
        capturedRequest.url.toString(),
        'https://api.home.internal/api/calendar/events/calendar:workspace:1',
      );
      expect(capturedRequest.headers['authorization'], 'Bearer calendar-token');
      expect(event.title, 'Planning details');
      expect(event.description, 'Fetched from backend read endpoint');
      expect(event.scope.type, 'workspace');
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
                'id': 'calendar:workspace:1',
                'title': 'Planning',
                'startsAt': '2026-04-26T09:00:00Z',
                'endsAt': '2026-04-26T10:00:00Z',
                'timezone': 'Europe/Berlin',
                'allDay': false,
                'scope': {
                  'type': 'workspace',
                  'label': 'Weave workspace calendar',
                },
              }),
              200,
            );
          }),
        );

        await facade.createEvent(
          CalendarEventDraft(
            title: 'Planning',
            startTime: DateTime.utc(2026, 4, 26, 9),
            endTime: DateTime.utc(2026, 4, 26, 10),
            timezone: 'Europe/Berlin',
          ),
        );
        await facade.updateEvent(
          id: 'calendar:workspace:1',
          patch: const CalendarEventPatch(
            title: 'Updated Planning',
            etag: 'abc',
          ),
        );
        await facade.deleteEvent('calendar:workspace:1');

        expect(requests.map((request) => '${request.method} ${request.url}'), [
          'POST https://api.home.internal/api/calendar/events',
          'PATCH https://api.home.internal/api/calendar/events/calendar:workspace:1',
          'DELETE https://api.home.internal/api/calendar/events/calendar:workspace:1',
        ]);
        final createBody =
            jsonDecode(requests.first.body) as Map<String, dynamic>;
        expect(createBody['timezone'], 'Europe/Berlin');
        expect(createBody['scope']['type'], 'workspace');
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
