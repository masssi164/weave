import 'dart:async';
import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:weave/features/auth/domain/entities/auth_configuration.dart';
import 'package:weave/features/auth/domain/entities/auth_state.dart';
import 'package:weave/features/auth/domain/repositories/auth_session_repository.dart';
import 'package:weave/features/calendar/data/services/calendar_facade_client.dart';
import 'package:weave/features/calendar/domain/entities/calendar_event.dart';
import 'package:weave/features/calendar/domain/entities/calendar_failure.dart';
import 'package:weave/features/server_config/domain/entities/server_configuration.dart';
import 'package:weave/features/server_config/domain/repositories/server_configuration_repository.dart';

import '../../../../helpers/auth_test_data.dart';
import '../../../../helpers/server_config_test_data.dart';

class _Configuration implements ServerConfigurationRepository {
  ServerConfiguration? configuration = buildTestConfiguration(
    backendApiBaseUrl: 'https://api.example.test/api',
  );
  @override
  Future<ServerConfiguration?> loadConfiguration() async => configuration;
  @override
  Future<void> saveConfiguration(ServerConfiguration configuration) async {
    this.configuration = configuration;
  }

  @override
  Future<void> clearConfiguration() async {
    configuration = null;
  }
}

class _Auth implements AuthSessionRepository {
  AuthState state = AuthState.authenticated(
    buildTestAuthSession(accessToken: 'old-token'),
  );
  int refreshes = 0;
  @override
  Future<AuthState> restoreSession(AuthConfiguration configuration) async =>
      state;
  @override
  Future<AuthState> refreshSession(AuthConfiguration configuration) async {
    refreshes++;
    state = AuthState.authenticated(
      buildTestAuthSession(accessToken: 'new-token'),
    );
    return state;
  }

  @override
  Future<void> clearLocalSession() async {
    state = const AuthState.signedOut();
  }

  @override
  Future<void> signOut(AuthConfiguration configuration) async =>
      clearLocalSession();
  @override
  Future<AuthState> signIn(AuthConfiguration configuration) async => state;
}

const calendarId = 'calendar:12345678-1234-1234-1234-123456789abc';
const eventId = 'event:12345678-1234-1234-1234-123456789abc';
const scopeJson = {'type': 'WORKSPACE', 'spaceId': 'workspace-default'};
const scope = CalendarScope(
  id: calendarId,
  type: 'workspace',
  label: 'Workspace',
  contextId: 'workspace-default',
  capabilities: ['read', 'create', 'update', 'delete'],
);
const version = '"weave-version-1"';
Map<String, Object?> content({String kind = 'ZONED'}) => {
  'title': 'Planning',
  'description': 'Details',
  'location': 'Room',
  'start': time(kind, false),
  'end': time(kind, true),
  'attendees': [
    {
      'address': 'mailto:member@example.test',
      'displayName': 'Member',
      'role': 'REQ-PARTICIPANT',
      'response': 'ACCEPTED',
    },
  ],
  'overrides': [],
  'recurrence': null,
};
Map<String, Object?> time(String kind, bool end) => {
  'kind': kind,
  if (kind == 'DATE') 'date': end ? '2026-10-26' : '2026-10-25',
  if (kind == 'UTC')
    'instant': end ? '2026-10-25T11:00:00Z' : '2026-10-25T10:00:00Z',
  if (kind == 'FLOATING' || kind == 'ZONED')
    'localDateTime': end ? '2026-10-25T11:00:00' : '2026-10-25T10:00:00',
  if (kind == 'ZONED') 'timeZone': 'Europe/Berlin',
};
Map<String, Object?> event({
  Map<String, Object?>? payload,
  List<String> actions = const ['read', 'update', 'delete'],
}) => {
  'id': eventId,
  'calendarId': calendarId,
  'scope': scopeJson,
  'version': version,
  'meetingThreadRef': 'meeting:stable',
  'allowedActions': actions,
  'content': payload ?? content(),
};
http.Response json(Object payload, [int status = 200]) => http.Response(
  jsonEncode(payload),
  status,
  headers: {'content-type': 'application/json'},
);
Matcher fails(CalendarFailureKind kind) => throwsA(
  isA<CalendarFailure>().having((failure) => failure.kind, 'kind', kind),
);
CalendarEventDraft draft({
  String title = 'Planning',
  CalendarTimeKind kind = CalendarTimeKind.zoned,
  bool allDay = false,
}) => CalendarEventDraft(
  title: title,
  description: 'Details',
  location: 'Room',
  startTime: DateTime(2026, 10, 25, allDay ? 0 : 10),
  endTime: allDay ? DateTime(2026, 10, 26) : DateTime(2026, 10, 25, 11),
  timezone: 'Europe/Berlin',
  scope: scope,
  timeKind: kind,
  allDay: allDay,
);

void main() {
  late _Auth auth;
  late _Configuration configuration;
  late List<http.Request> calls;
  String Function(String token)? subject;
  String Function(String token)? organization;
  late Map<String, Object?> stored;
  late List<String> calendarActions;
  CalendarFacadeClient client(
    Future<http.Response?> Function(http.Request) handle, {
    String zone = 'Europe/Berlin',
  }) => CalendarFacadeClient(
    httpClient: MockClient((request) async {
      calls.add(request);
      if (request.url.path == '/api/me') {
        return json({
          'subject':
              subject?.call(request.headers['authorization']!) ?? 'member',
          'organizationId':
              organization?.call(request.headers['authorization']!) ?? 'org',
          'identityIssuer': 'https://auth.home.internal',
        });
      }
      if (request.url.path == '/api/calendar/calendars') {
        return json({
          'calendars': [
            {
              'id': calendarId,
              'scope': scopeJson,
              'allowedActions': calendarActions,
            },
          ],
        });
      }
      final response = await handle(request);
      if (response != null) return response;
      if (request.url.path.endsWith('/events') && request.method == 'GET') {
        return json({
          'calendarId': calendarId,
          'evaluationTimeZone': zone,
          'from': '2026-10-01T00:00:00Z',
          'to': '2026-11-01T00:00:00Z',
          'events': [stored],
          'occurrences': [
            {
              'eventId': eventId,
              'startsAt': '2026-10-25T09:00:00Z',
              'endsAt': '2026-10-25T10:00:00Z',
            },
          ],
        });
      }
      if (request.method == 'GET') return json(stored);
      if (request.method == 'DELETE') return http.Response('', 204);
      final result = event(
        payload: (jsonDecode(request.body) as Map).cast<String, Object?>(),
      );
      return json(result, request.method == 'POST' ? 201 : 200);
    }),
    serverConfigurationRepository: configuration,
    authSessionRepository: auth,
    evaluationTimeZone: () async => zone,
  );
  setUp(() {
    auth = _Auth();
    configuration = _Configuration();
    calls = [];
    subject = null;
    organization = null;
    stored = event();
    calendarActions = ['read', 'create', 'update', 'delete'];
  });

  test(
    'CALENDAR_USER_API_GENERATED_CLIENT discovers opaque scope before bounded agenda',
    () async {
      final service = client((_) async => null);
      final result = await service.listEvents(
        from: DateTime.utc(2026, 10),
        to: DateTime.utc(2026, 11),
      );
      final requests = calls
          .where((request) => request.url.path.contains('/calendar/'))
          .toList();
      expect(requests.map((request) => request.method), ['GET', 'GET']);
      expect(requests.first.url.path, '/api/calendar/calendars');
      expect(
        requests.last.url.path,
        '/api/calendar/calendars/$calendarId/events',
      );
      expect(
        requests.last.url.queryParameters['evaluationTimeZone'],
        'Europe/Berlin',
      );
      expect(requests.last.headers['authorization'], 'Bearer old-token');
      expect(result.scope.id, calendarId);
      expect(result.events.single.startTime, DateTime.utc(2026, 10, 25, 10));
      expect(result.events.single.threadRef.meetingThreadId, 'meeting:stable');
      expect(result.events.single.threadRef.contextId, 'workspace-default');
      expect(
        calls.any((request) => request.url.path.contains('caldav')),
        isFalse,
      );
    },
  );

  for (final kind in ['DATE', 'FLOATING', 'UTC', 'ZONED']) {
    test(
      'CALENDAR_USER_API_TEMPORAL_PRESERVATION $kind and attendee content survive update',
      () async {
        stored = event(payload: content(kind: kind));
        final service = client((_) async => null);
        await service.listEvents();
        final read = await service.readEvent(eventId);
        expect(
          read.timeKind,
          CalendarTimeKind.values.byName(kind.toLowerCase()),
        );
        final changed = CalendarEventDraft(
          title: 'New title',
          description: read.description,
          location: read.location,
          startTime: read.startTime,
          endTime: read.endTime,
          timezone: read.timezone ?? 'Europe/Berlin',
          scope: read.scope,
          allDay: read.allDay,
          timeKind: read.timeKind,
        );
        await service.updateEvent(
          id: eventId,
          draft: changed,
          version: read.etag,
        );
        final request = calls.singleWhere((call) => call.method == 'PUT');
        expect(request.headers['if-match'], version);
        final sent = jsonDecode(request.body) as Map;
        for (final endpoint in ['start', 'end']) {
          final original = time(kind, endpoint == 'end');
          final actual = (sent[endpoint] as Map)
            ..removeWhere((key, value) => value == null);
          if (kind == 'UTC') {
            // Generated DateTime transport emits a zero-only millisecond suffix.
            expect(
              DateTime.parse(actual['instant'] as String),
              DateTime.parse(original['instant'] as String),
            );
            expect(actual['kind'], 'UTC');
          } else {
            expect(actual, original);
          }
        }
        expect(sent['attendees'], content(kind: kind)['attendees']);
      },
    );
  }

  test(
    'recurrence and override masters stay intact; occurrence editing/deletion is guarded',
    () async {
      final payload = content();
      payload['recurrence'] = {
        'frequency': 'WEEKLY',
        'interval': 1,
        'count': 3,
        'byDay': ['SU'],
        'byMonthDay': <int>[],
        'byMonth': <int>[],
        'bySetPos': <int>[],
        'additionalDates': <Object>[],
        'excludedDates': <Object>[],
      };
      stored = event(payload: payload);
      final service = client((_) async => null);
      final agenda = await service.listEvents();
      expect(agenda.events.single.recurring, isTrue);
      await expectLater(
        service.updateEvent(id: eventId, draft: draft(), version: version),
        fails(CalendarFailureKind.unsupportedEdit),
      );
      await expectLater(
        service.deleteEvent(eventId, version: version),
        fails(CalendarFailureKind.unsupportedEdit),
      );
      expect(
        calls.where((call) => call.method == 'PUT' || call.method == 'DELETE'),
        isEmpty,
      );
    },
  );

  test(
    'create retries once with the same idempotency key and generated payload after same-member refresh',
    () async {
      var attempts = 0;
      final service = client((request) async {
        if (request.method == 'POST' && attempts++ == 0) {
          return http.Response('', 401);
        }
        return null;
      });
      await service.createEvent(draft());
      final writes = calls.where((call) => call.method == 'POST').toList();
      expect(writes, hasLength(2));
      expect(writes.first.headers['idempotency-key'], hasLength(32));
      expect(
        writes.last.headers['idempotency-key'],
        writes.first.headers['idempotency-key'],
      );
      expect(writes.last.body, writes.first.body);
      expect(writes.last.headers['authorization'], 'Bearer new-token');
      expect(auth.refreshes, 1);
    },
  );

  for (final boundary in ['member', 'organization']) {
    test('refresh into a different $boundary never replays a write', () async {
      if (boundary == 'member') {
        subject = (token) => token.contains('new-token') ? 'other' : 'member';
      }
      if (boundary == 'organization') {
        organization = (token) => token.contains('new-token') ? 'other' : 'org';
      }
      final service = client(
        (request) async =>
            request.method == 'POST' ? http.Response('', 401) : null,
      );
      await expectLater(
        service.createEvent(draft()),
        fails(CalendarFailureKind.session),
      );
      expect(calls.where((call) => call.method == 'POST'), hasLength(1));
    });
  }

  test(
    'late agenda response after sign-out is rejected and cannot populate event cache',
    () async {
      final reached = Completer<void>();
      final resume = Completer<void>();
      final service = client((request) async {
        if (request.url.path.endsWith('/events')) {
          reached.complete();
          await resume.future;
        }
        return null;
      });
      final pending = service.listEvents();
      final assertion = expectLater(
        pending,
        fails(CalendarFailureKind.session),
      );
      await reached.future;
      await auth.clearLocalSession();
      resume.complete();
      await assertion;
    },
  );

  test(
    'changed server between discovery and mutation prevents any write',
    () async {
      final service = client((request) async {
        if (request.method == 'GET') await configuration.clearConfiguration();
        return null;
      });
      await expectLater(
        service.listEvents(),
        fails(CalendarFailureKind.session),
      );
      expect(calls.where((call) => call.method == 'POST'), isEmpty);
    },
  );

  test(
    'stale update/delete are actionable conflicts and are never blindly retried',
    () async {
      final service = client(
        (request) async => request.method == 'PUT' || request.method == 'DELETE'
            ? http.Response('', 412)
            : null,
      );
      await service.listEvents();
      await expectLater(
        service.updateEvent(id: eventId, draft: draft(), version: version),
        fails(CalendarFailureKind.conflict),
      );
      await expectLater(
        service.deleteEvent(eventId, version: version),
        fails(CalendarFailureKind.conflict),
      );
      expect(calls.where((call) => call.method == 'PUT'), hasLength(1));
      expect(
        calls
            .singleWhere((call) => call.method == 'DELETE')
            .headers['if-match'],
        version,
      );
      expect(auth.refreshes, 0);
    },
  );

  test('read-only allowed actions disable writes before transport', () async {
    calendarActions = ['read'];
    stored = event(actions: ['read']);
    final service = client((_) async => null);
    await service.listEvents();
    await expectLater(
      service.createEvent(draft()),
      fails(CalendarFailureKind.permission),
    );
    await expectLater(
      service.updateEvent(id: eventId, draft: draft(), version: version),
      fails(CalendarFailureKind.permission),
    );
    await expectLater(
      service.deleteEvent(eventId, version: version),
      fails(CalendarFailureKind.permission),
    );
    expect(calls.every((call) => call.method == 'GET'), isTrue);
  });

  test('invalid IANA profile zone fails without an agenda request', () async {
    final service = client((_) async => null, zone: 'CET invented zone');
    await expectLater(
      service.listEvents(),
      fails(CalendarFailureKind.unavailable),
    );
    expect(calls.where((call) => call.url.path.endsWith('/events')), isEmpty);
  });
  test(
    'uncertain create outcome reuses logical identity when the unchanged draft is retried',
    () async {
      var attempts = 0;
      final service = client((request) async {
        if (request.method == 'POST' && attempts++ == 0) {
          return http.Response('', 503);
        }
        return null;
      });
      await expectLater(
        service.createEvent(draft()),
        fails(CalendarFailureKind.unavailable),
      );
      await service.createEvent(draft());
      final requests = calls.where((call) => call.method == 'POST').toList();
      expect(
        requests[1].headers['idempotency-key'],
        requests[0].headers['idempotency-key'],
      );
      await service.createEvent(draft());
      expect(
        calls.last.headers['idempotency-key'],
        isNot(requests[0].headers['idempotency-key']),
      );
    },
  );
  for (final kind in ['FLOATING', 'ZONED', 'UTC']) {
    test(
      'host DST gap cannot normalize $kind source time on title-only update',
      () async {
        final payload = content(kind: kind);
        payload['start'] = {
          'kind': kind,
          if (kind == 'UTC') 'instant': '2026-03-29T02:30:00Z',
          if (kind != 'UTC') 'localDateTime': '2026-03-29T02:30:00',
          if (kind == 'ZONED') 'timeZone': 'America/New_York',
        };
        payload['end'] = {
          'kind': kind,
          if (kind == 'UTC') 'instant': '2026-03-29T03:30:00Z',
          if (kind != 'UTC') 'localDateTime': '2026-03-29T03:30:00',
          if (kind == 'ZONED') 'timeZone': 'America/New_York',
        };
        stored = event(payload: payload);
        final service = client((_) async => null);
        await service.listEvents();
        final master = await service.readEvent(eventId);
        expect(master.startTime.hour, 2);
        expect(master.startTime.minute, 30);
        await service.updateEvent(
          id: eventId,
          version: version,
          draft: CalendarEventDraft(
            title: 'Title only',
            startTime: master.startTime,
            endTime: master.endTime,
            timezone: master.timezone ?? 'Europe/Berlin',
            timeKind: master.timeKind,
            scope: master.scope,
          ),
        );
        final sent =
            jsonDecode(calls.singleWhere((call) => call.method == 'PUT').body)
                as Map;
        final start = sent['start'] as Map;
        if (kind == 'UTC') {
          expect(
            DateTime.parse(start['instant'] as String),
            DateTime.utc(2026, 3, 29, 2, 30),
          );
        } else {
          expect(start['localDateTime'], '2026-03-29T02:30:00');
          expect(
            start['timeZone'],
            kind == 'ZONED' ? 'America/New_York' : null,
          );
        }
      },
    );
  }
}
