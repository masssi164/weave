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
  AuthState? refreshedState;
  int refreshCalls = 0;

  @override
  Future<void> clearLocalSession() async {}

  @override
  Future<AuthState> refreshSession(AuthConfiguration configuration) async {
    refreshCalls++;
    return refreshedState ?? state;
  }

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

    test('lists events through the CalDAV calendar facade', () async {
      // FLUTTER_CALDAV_DATA_PLANE
      late http.Request capturedRequest;
      final facade = client(
        MockClient((request) async {
          capturedRequest = request;
          return http.Response(
            '''
<?xml version="1.0" encoding="UTF-8"?>
<d:multistatus xmlns:d="DAV:" xmlns:c="urn:ietf:params:xml:ns:caldav">
  <d:response>
    <d:href>/caldav/channel%3Aengineering-general/planning.ics</d:href>
    <d:propstat>
      <d:prop>
        <d:getetag>"abc"</d:getetag>
        <c:calendar-data>BEGIN:VCALENDAR
VERSION:2.0
BEGIN:VEVENT
UID:planning
DTSTAMP:20260426T084500Z
DTSTART:20260426T090000Z
DTEND:20260426T100000Z
SUMMARY:Planning
DESCRIPTION:Roadmap
LOCATION:Office
END:VEVENT
END:VCALENDAR</c:calendar-data>
      </d:prop>
      <d:status>HTTP/1.1 200 OK</d:status>
    </d:propstat>
  </d:response>
</d:multistatus>
''',
            207,
            headers: {'content-type': 'application/xml'},
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
          contextId: 'channel-engineering-general',
          teamId: 'engineering',
          channelId: 'engineering-general',
          accessModel: 'shared-channel-calendar',
        ),
      );

      expect(capturedRequest.method, 'REPORT');
      expect(
        capturedRequest.url.toString(),
        'https://api.home.internal/caldav/channel:engineering-general',
      );
      expect(capturedRequest.headers['authorization'], 'Bearer calendar-token');
      expect(capturedRequest.body, contains('calendar-query'));
      expect(capturedRequest.body, contains('20260426T000000Z'));
      expect(capturedRequest.body, contains('20260427T000000Z'));
      expect(events.scope.type, 'channel');
      expect(events.scope.label, 'Engineering / general channel calendar');
      expect(events.events, hasLength(1));
      expect(
        events.events.single.id,
        'caldav:channel%3Aengineering-general:planning',
      );
      expect(events.events.single.title, 'Planning');
      expect(events.events.single.timezone, 'UTC');
      expect(events.events.single.etag, '"abc"');
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
      expect(events.events.single.attendees, isEmpty);
      expect(events.events.single.providerRef, isNull);
      expect(events.events.single.updatedAt, DateTime.utc(2026, 4, 26, 8, 45));
    });

    test(
      'defaults empty CalDAV multistatus payloads to workspace scope',
      () async {
        final facade = client(
          MockClient(
            (_) async => http.Response(
              '<d:multistatus xmlns:d="DAV:" xmlns:c="urn:ietf:params:xml:ns:caldav"/>',
              207,
              headers: {'content-type': 'application/xml'},
            ),
          ),
        );

        final events = await facade.listEvents();

        expect(events.scope, CalendarScope.workspace);
        expect(events.events, isEmpty);
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
                    'weave-issued-scoped-setup-credential',
                'notes': ['Workspace calendar setup only.'],
              },
              'credentialReadiness': {
                'status': 'revocable_credentials_ready',
                'appleProfileSigned': false,
                'appleProfilePasswordIncluded': false,
                'revocableCredentialsAvailable': true,
                'readOnlySubscriptionTokensAvailable': false,
                'backendActorCredentialsExposed': false,
                'blockers': ['Apple profiles are unsigned.'],
              },
              'username': 'user-123',
              'endpoints': {
                'serverUrl': '/caldav',
                'caldavDiscoveryUrl': '/caldav',
                'principalUrl': '/caldav/principals/users/user-123/',
              },
              'credentialPolicy':
                  'The backend never returns passwords, bearer tokens, static profile secrets, or provider endpoints.',
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
                  'method': 'sync-adapter',
                  'available': false,
                  'unavailableReason':
                      'Android Calendar setup waits for the Weave Account/SyncAdapter boundary.',
                  'guidance': ['Use the Weave SyncAdapter boundary.'],
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
        'weave-issued-scoped-setup-credential',
      );
      expect(setup.credentialReadiness.appleProfileSigned, isFalse);
      expect(setup.credentialReadiness.backendActorCredentialsExposed, isFalse);
      expect(
        setup.credentialReadiness.blockers,
        contains('Apple profiles are unsigned.'),
      );
      expect(setup.endpoints.serverUrl, '/caldav');
      expect(
        setup.endpoints.principalUrl,
        '/caldav/principals/users/user-123/',
      );
      expect(setup.credentialPolicy, contains('never returns'));
      expect(setup.options.first.platform, 'apple');
      expect(setup.options.first.available, isFalse);
      expect(setup.options.last.method, 'sync-adapter');
      expect(setup.options.last.available, isFalse);
      expect(setup.options.last.actionUrl, isNull);
    });

    test('reads event details through the CalDAV calendar facade', () async {
      late http.Request capturedRequest;
      final facade = client(
        MockClient((request) async {
          capturedRequest = request;
          return http.Response(
            '''
BEGIN:VCALENDAR
VERSION:2.0
BEGIN:VEVENT
UID:calendar-workspace-1
DTSTART:20260426T090000Z
DTEND:20260426T100000Z
SUMMARY:Planning details
DESCRIPTION:Fetched from CalDAV
END:VEVENT
END:VCALENDAR
''',
            200,
            headers: {'etag': '"read-etag"', 'content-type': 'text/calendar'},
          );
        }),
      );

      final event = await facade.readEvent('calendar-workspace-1');

      expect(capturedRequest.method, 'GET');
      expect(
        capturedRequest.url.toString(),
        'https://api.home.internal/caldav/workspace/calendar-workspace-1.ics',
      );
      expect(capturedRequest.headers['authorization'], 'Bearer calendar-token');
      expect(event.title, 'Planning details');
      expect(event.description, 'Fetched from CalDAV');
      expect(event.etag, '"read-etag"');
      expect(event.scope.type, 'workspace');
    });

    test(
      'creates, updates, and deletes events through CalDAV endpoints',
      () async {
        // FLUTTER_CALDAV_MUTATION_DATA_PLANE
        final requests = <http.Request>[];
        final facade = client(
          MockClient((request) async {
            requests.add(request);
            if (request.method == 'DELETE') {
              return http.Response('', 204);
            }
            if (request.method == 'GET') {
              final uid = request.url.pathSegments.last.replaceAll('.ics', '');
              return http.Response(
                '''
BEGIN:VCALENDAR
VERSION:2.0
BEGIN:VEVENT
UID:$uid
DTSTART:20260426T090000Z
DTEND:20260426T100000Z
SUMMARY:Planning
END:VEVENT
END:VCALENDAR
''',
                200,
                headers: {'etag': '"etag-$uid"'},
              );
            }
            if (request.method == 'PUT' &&
                request.headers['If-None-Match'] == '*') {
              final uid = request.url.pathSegments.last.replaceAll('.ics', '');
              return http.Response(
                '',
                201,
                headers: {'location': '/caldav/workspace/$uid.ics'},
              );
            }
            return http.Response('', 204, headers: {'etag': '"etag-updated"'});
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
          id: 'planning',
          patch: const CalendarEventPatch(
            title: 'Updated Planning',
            etag: 'abc',
          ),
        );
        await facade.deleteEvent('planning');

        expect(requests.map((request) => request.method), [
          'PUT',
          'GET',
          'PUT',
          'GET',
          'DELETE',
        ]);
        expect(
          requests[0].url.toString(),
          startsWith('https://api.home.internal/caldav/workspace/weave-'),
        );
        expect(requests[0].headers['If-None-Match'], '*');
        expect(requests[0].body, contains('BEGIN:VCALENDAR'));
        expect(requests[0].body, contains('SUMMARY:Planning'));
        expect(
          requests[2].url.toString(),
          'https://api.home.internal/caldav/workspace/planning.ics',
        );
        expect(requests[2].headers['If-Match'], 'abc');
        expect(requests[2].body, contains('SUMMARY:Updated Planning'));
        expect(
          requests[4].url.toString(),
          'https://api.home.internal/caldav/workspace/planning.ics',
        );
      },
    );

    test('refreshes the Weave session once after a backend 401', () async {
      authSessionRepository.refreshedState = AuthState.authenticated(
        buildTestAuthSession(accessToken: 'fresh-calendar-token'),
      );
      final authorizationHeaders = <String?>[];
      final facade = client(
        MockClient((request) async {
          authorizationHeaders.add(request.headers['authorization']);
          if (authorizationHeaders.length == 1) {
            return http.Response(
              jsonEncode({'message': 'Authentication is required.'}),
              401,
            );
          }
          return http.Response(
            '<d:multistatus xmlns:d="DAV:" xmlns:c="urn:ietf:params:xml:ns:caldav"/>',
            207,
            headers: {'content-type': 'application/xml'},
          );
        }),
      );

      final events = await facade.listEvents();

      expect(events.events, isEmpty);
      expect(authSessionRepository.refreshCalls, 1);
      expect(authorizationHeaders, [
        'Bearer calendar-token',
        'Bearer fresh-calendar-token',
      ]);
    });

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
