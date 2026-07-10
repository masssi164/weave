import 'dart:convert';

import 'package:http/http.dart' as http;
import 'package:weave/core/failures/app_failure.dart';
import 'package:weave/features/auth/domain/entities/auth_configuration.dart';
import 'package:weave/features/auth/domain/repositories/auth_session_repository.dart';
import 'package:weave/features/calendar/data/dtos/calendar_openapi_mappers.dart';
import 'package:weave/features/calendar/domain/entities/calendar_event.dart';
import 'package:weave/features/server_config/domain/entities/server_configuration.dart';
import 'package:weave/features/server_config/domain/repositories/server_configuration_repository.dart';
import 'package:weave/generated/openapi_models.dart' as openapi;
import 'package:xml/xml.dart';

/// HTTP client for the Weave backend calendar product facade.
///
/// OpenAPI is used for setup/readiness/control metadata. Event data-plane
/// behavior uses the Weave CalDAV/iCalendar facade under `/caldav/**`.
class CalendarFacadeClient {
  const CalendarFacadeClient({
    required http.Client httpClient,
    required ServerConfigurationRepository serverConfigurationRepository,
    required AuthSessionRepository authSessionRepository,
  }) : _httpClient = httpClient,
       _serverConfigurationRepository = serverConfigurationRepository,
       _authSessionRepository = authSessionRepository;

  final http.Client _httpClient;
  final ServerConfigurationRepository _serverConfigurationRepository;
  final AuthSessionRepository _authSessionRepository;

  Future<CalendarScopeList> listScopes() async {
    final context = await _requireContext();
    final response = await _sendAuthenticated(
      context,
      (accessToken) => _httpClient.get(
        _apiUri(context.baseUrl, const ['api', 'calendar', 'scopes']),
        headers: _jsonHeaders(accessToken),
      ),
      fallbackMessage: 'Unable to load calendar scopes from the Weave backend.',
    );
    _ensureSuccess(response, successCodes: const {200});
    return openapi.CalendarScopesResponse.fromJson(
      _decodeObject(response.body),
    ).toDomain();
  }

  Future<CalendarEventList> listEvents({
    DateTime? from,
    DateTime? to,
    CalendarScope? selectedScope,
  }) async {
    final context = await _requireContext();
    final scope = selectedScope ?? CalendarScope.workspace;
    final rangeStart =
        from ?? DateTime.now().toUtc().subtract(const Duration(days: 30));
    final rangeEnd =
        to ?? DateTime.now().toUtc().add(const Duration(days: 365));
    final response = await _sendAuthenticated(
      context,
      (accessToken) async => http.Response.fromStream(
        await _httpClient.send(
          http.Request('REPORT', _caldavUri(context.baseUrl, [scope.id]))
            ..headers.addAll(
              _caldavHeaders(
                accessToken,
                contentType: 'application/xml; charset=utf-8',
              ),
            )
            ..headers['Depth'] = '1'
            ..body = _calendarQueryBody(rangeStart, rangeEnd),
        ),
      ),
      fallbackMessage: 'Unable to load calendar events from the Weave backend.',
    );
    _ensureSuccess(response, successCodes: const {207});
    return CalendarEventList(
      scope: scope,
      events: _eventsFromMultistatus(response.body, defaultScope: scope),
    );
  }

  Future<CalendarClientSetup> clientSetup() async {
    final context = await _requireContext();
    final response = await _sendAuthenticated(
      context,
      (accessToken) => _httpClient.get(
        _apiUri(context.baseUrl, const ['api', 'calendar', 'client-setup']),
        headers: _jsonHeaders(accessToken),
      ),
      fallbackMessage:
          'Unable to load calendar setup metadata from the Weave backend.',
    );
    _ensureSuccess(response, successCodes: const {200});
    return openapi.CalendarClientSetupResponse.fromJson(
      _decodeObject(response.body),
    ).toDomain();
  }

  Future<CalendarEvent> readEvent(String id) async {
    final ref = _eventRef(id);
    return _readEvent(ref);
  }

  Future<CalendarEvent> _readEvent(_CalDavEventRef ref) async {
    final context = await _requireContext();
    final response = await _sendAuthenticated(
      context,
      (accessToken) => _httpClient.get(
        _caldavUri(context.baseUrl, [ref.scope.id, '${ref.uid}.ics']),
        headers: _caldavHeaders(accessToken),
      ),
      fallbackMessage: 'Unable to read the calendar event.',
    );
    _ensureSuccess(response, successCodes: const {200});
    return _eventFromIcs(
      response.body,
      id: _domainEventId(ref.scope, ref.uid),
      etag: response.headers['etag'],
      scope: ref.scope,
    );
  }

  Future<CalendarEvent> createEvent(CalendarEventDraft draft) async {
    final context = await _requireContext();
    final uid = 'weave-${DateTime.now().toUtc().microsecondsSinceEpoch}';
    final response = await _sendAuthenticated(
      context,
      (accessToken) => _httpClient.put(
        _caldavUri(context.baseUrl, [draft.scope.id, '$uid.ics']),
        headers: {
          ..._caldavHeaders(
            accessToken,
            contentType: 'text/calendar; charset=utf-8',
          ),
          'If-None-Match': '*',
        },
        body: _toIcalendar(uid, draft),
      ),
      fallbackMessage: 'Unable to create the calendar event.',
    );
    _ensureSuccess(response, successCodes: const {201, 204});
    final createdUid =
        _eventIdFromLocation(response.headers['location']) ?? uid;
    return _readEvent(_CalDavEventRef(scope: draft.scope, uid: createdUid));
  }

  Future<CalendarEvent> updateEvent({
    required String id,
    required CalendarEventPatch patch,
  }) async {
    final context = await _requireContext();
    final ref = _eventRef(id, fallbackScope: patch.scope);
    final scope = patch.scope ?? ref.scope;
    final response = await _sendAuthenticated(
      context,
      (accessToken) => _httpClient.put(
        _caldavUri(context.baseUrl, [scope.id, '${ref.uid}.ics']),
        headers: {
          ..._caldavHeaders(
            accessToken,
            contentType: 'text/calendar; charset=utf-8',
          ),
          if (patch.etag != null) 'If-Match': patch.etag!,
        },
        body: _toIcalendar(
          ref.uid,
          CalendarEventDraft(
            title: patch.title ?? 'Calendar event',
            description: patch.description,
            startTime: patch.startTime ?? DateTime.now().toUtc(),
            endTime:
                patch.endTime ??
                (patch.startTime ?? DateTime.now().toUtc()).add(
                  const Duration(hours: 1),
                ),
            timezone: patch.timezone ?? 'UTC',
            location: patch.location,
            allDay: patch.allDay ?? false,
            scope: scope,
          ),
        ),
      ),
      fallbackMessage: 'Unable to update the calendar event.',
    );
    _ensureSuccess(response, successCodes: const {200, 204});
    return _readEvent(_CalDavEventRef(scope: scope, uid: ref.uid));
  }

  Future<void> deleteEvent(String id) async {
    final context = await _requireContext();
    final ref = _eventRef(id);
    final response = await _sendAuthenticated(
      context,
      (accessToken) => _httpClient.delete(
        _caldavUri(context.baseUrl, [ref.scope.id, '${ref.uid}.ics']),
        headers: _caldavHeaders(accessToken),
      ),
      fallbackMessage: 'Unable to delete the calendar event.',
    );
    _ensureSuccess(response, successCodes: const {200, 204});
  }

  Future<_CalendarFacadeContext> _requireContext() async {
    final configuration = await _serverConfigurationRepository
        .loadConfiguration();
    if (configuration == null) {
      throw const AppFailure.unknown(
        'Finish server setup before opening calendar.',
      );
    }

    final authConfiguration = _authConfiguration(configuration);
    final authState = await _authSessionRepository.restoreSession(
      authConfiguration,
    );
    final session = authState.session;
    if (!authState.isAuthenticated || session == null) {
      throw const AppFailure.unknown(
        'Sign in to Weave before opening calendar.',
      );
    }

    return _CalendarFacadeContext(
      baseUrl: configuration.serviceEndpoints.backendApiBaseUrl,
      accessToken: session.accessToken,
      authConfiguration: authConfiguration,
    );
  }

  AuthConfiguration _authConfiguration(ServerConfiguration configuration) {
    return AuthConfiguration(
      issuer: configuration.oidcIssuerUrl,
      clientId: configuration.oidcClientRegistration.clientId.trim(),
    );
  }

  Future<http.Response> _send(
    Future<http.Response> Function() request, {
    required String fallbackMessage,
  }) async {
    try {
      return await request().timeout(const Duration(seconds: 20));
    } on AppFailure {
      rethrow;
    } catch (error) {
      throw AppFailure.unknown(fallbackMessage, cause: error);
    }
  }

  Future<http.Response> _sendAuthenticated(
    _CalendarFacadeContext context,
    Future<http.Response> Function(String accessToken) request, {
    required String fallbackMessage,
  }) async {
    final response = await _send(
      () => request(context.accessToken),
      fallbackMessage: fallbackMessage,
    );
    if (response.statusCode != 401) {
      return response;
    }

    final refreshedContext = await _refreshContext(context);
    if (refreshedContext == null ||
        refreshedContext.accessToken == context.accessToken) {
      return response;
    }

    return _send(
      () => request(refreshedContext.accessToken),
      fallbackMessage: fallbackMessage,
    );
  }

  Future<_CalendarFacadeContext?> _refreshContext(
    _CalendarFacadeContext context,
  ) async {
    try {
      final authState = await _authSessionRepository.refreshSession(
        context.authConfiguration,
      );
      final session = authState.session;
      if (!authState.isAuthenticated || session == null) {
        return null;
      }
      return _CalendarFacadeContext(
        baseUrl: context.baseUrl,
        accessToken: session.accessToken,
        authConfiguration: context.authConfiguration,
      );
    } catch (_) {
      return null;
    }
  }

  void _ensureSuccess(
    http.Response response, {
    required Set<int> successCodes,
  }) {
    if (successCodes.contains(response.statusCode)) {
      return;
    }

    final message = _errorMessage(response.body);
    if (response.statusCode == 401 || response.statusCode == 403) {
      throw AppFailure.unknown(
        message ?? 'The Weave backend rejected the current session.',
        cause: response.statusCode,
      );
    }
    if (response.statusCode == 503) {
      throw AppFailure.unknown(
        message ?? 'The Weave backend calendar facade is unavailable.',
        cause: response.statusCode,
      );
    }
    throw AppFailure.unknown(
      message ?? 'The Weave backend failed the calendar request.',
      cause: response.statusCode,
    );
  }

  Map<String, dynamic> _decodeObject(String body) {
    try {
      final payload = jsonDecode(body);
      if (payload is Map<String, dynamic>) {
        return payload;
      }
    } catch (_) {
      // Fall through to failure below.
    }
    throw const AppFailure.unknown(
      'The Weave backend returned an invalid calendar payload.',
    );
  }

  String? _errorMessage(String body) {
    try {
      final decoded = jsonDecode(body);
      if (decoded is Map<String, dynamic>) {
        final message = decoded['message'];
        if (message is String && message.trim().isNotEmpty) {
          return message;
        }
      }
    } catch (_) {
      // CalDAV errors are XML; try that shape below.
    }
    try {
      final document = XmlDocument.parse(body);
      for (final element in document.descendants.whereType<XmlElement>()) {
        if (element.name.local != 'responsedescription') {
          continue;
        }
        final description = element.innerText.trim();
        if (description.isNotEmpty) {
          return description;
        }
      }
    } catch (_) {
      return null;
    }
    return null;
  }

  Map<String, String> _jsonHeaders(String accessToken) {
    return {
      'Accept': 'application/json',
      'Content-Type': 'application/json',
      'Authorization': 'Bearer $accessToken',
    };
  }

  Map<String, String> _caldavHeaders(
    String accessToken, {
    String? contentType,
  }) {
    return {
      'Accept': 'application/xml, text/calendar, */*',
      if (contentType != null) 'Content-Type': contentType,
      'Authorization': 'Bearer $accessToken',
    };
  }

  Uri _apiUri(
    Uri baseUrl,
    List<String> pathSegments, {
    Map<String, String>? query,
  }) {
    return baseUrl.replace(
      pathSegments: _apiPath(baseUrl, pathSegments),
      queryParameters: query,
    );
  }

  List<String> _apiPath(Uri baseUrl, List<String> pathSegments) {
    final baseSegments = baseUrl.pathSegments
        .where((segment) => segment.isNotEmpty)
        .toList(growable: false);
    if (baseSegments.isNotEmpty &&
        pathSegments.isNotEmpty &&
        baseSegments.last == 'api' &&
        pathSegments.first == 'api') {
      return [...baseSegments, ...pathSegments.skip(1)];
    }

    return [...baseSegments, ...pathSegments];
  }

  Uri _caldavUri(Uri baseUrl, List<String> pathSegments) {
    final origin = baseUrl.replace(path: '/', query: null, fragment: null);
    return origin.replace(
      pathSegments: [
        'caldav',
        ...pathSegments
            .where((segment) => segment.isNotEmpty)
            .map((segment) => segment.replaceAll(RegExp(r'^/+|/+$'), '')),
      ],
    );
  }

  String _calendarQueryBody(DateTime from, DateTime to) {
    return '''
<?xml version="1.0" encoding="UTF-8"?>
<c:calendar-query xmlns:d="DAV:" xmlns:c="urn:ietf:params:xml:ns:caldav">
  <d:prop>
    <d:getetag/>
    <c:calendar-data/>
  </d:prop>
  <c:filter>
    <c:comp-filter name="VCALENDAR">
      <c:comp-filter name="VEVENT">
        <c:time-range start="${_caldavTime(from)}" end="${_caldavTime(to)}"/>
      </c:comp-filter>
    </c:comp-filter>
  </c:filter>
</c:calendar-query>
''';
  }

  List<CalendarEvent> _eventsFromMultistatus(
    String body, {
    required CalendarScope defaultScope,
  }) {
    final document = XmlDocument.parse(body);
    final events = <CalendarEvent>[];
    for (final response in document.descendants.whereType<XmlElement>()) {
      if (response.name.local != 'response') continue;
      final calendarData = _firstChildText(response, 'calendar-data');
      if (calendarData == null || calendarData.trim().isEmpty) continue;
      final href = _firstChildText(response, 'href');
      final etag = _firstChildText(response, 'getetag');
      events.add(
        _eventFromIcs(
          calendarData,
          id: _domainEventId(
            defaultScope,
            _eventIdFromLocation(href) ?? 'calendar-event',
          ),
          etag: etag,
          scope: defaultScope,
        ),
      );
    }
    events.sort((left, right) => left.startTime.compareTo(right.startTime));
    return events;
  }

  CalendarEvent _eventFromIcs(
    String body, {
    required String id,
    required String? etag,
    required CalendarScope scope,
  }) {
    final fields = <String, String>{};
    final unfoldedLines = <String>[];
    for (final line
        in body.replaceAll('\r\n', '\n').replaceAll('\r', '\n').split('\n')) {
      if ((line.startsWith(' ') || line.startsWith('\t')) &&
          unfoldedLines.isNotEmpty) {
        unfoldedLines[unfoldedLines.length - 1] += line.substring(1);
      } else {
        unfoldedLines.add(line);
      }
    }
    for (final line in unfoldedLines) {
      final separator = line.indexOf(':');
      if (separator <= 0) continue;
      final key = line.substring(0, separator).split(';').first.toUpperCase();
      fields[key] = line.substring(separator + 1);
    }
    final start = _parseIcsDate(fields['DTSTART']);
    final end = _parseIcsDate(fields['DTEND']);
    final projectedContextId = _optionalIcsText(fields['X-WEAVE-CONTEXT-ID']);
    final projectedChannelId = _optionalIcsText(fields['X-WEAVE-CHANNEL-ID']);
    if (projectedContextId != null && projectedContextId != scope.contextId) {
      throw const AppFailure.unknown(
        'The calendar facade returned an event for a different context.',
      );
    }
    if (projectedChannelId != null && projectedChannelId != scope.channelId) {
      throw const AppFailure.unknown(
        'The calendar facade returned an event for a different channel.',
      );
    }
    return CalendarEvent(
      id: id,
      title: _optionalIcsText(fields['SUMMARY']) ?? 'Calendar event',
      description: _optionalIcsText(fields['DESCRIPTION']),
      startTime: start,
      endTime: end.isAfter(start) ? end : start.add(const Duration(hours: 1)),
      timezone: 'UTC',
      location: _optionalIcsText(fields['LOCATION']),
      etag: etag,
      scope: scope,
      threadRef: CalendarThreadRef(
        contextId: projectedContextId ?? scope.contextId,
        meetingThreadId: _optionalIcsText(fields['X-WEAVE-MEETING-THREAD-ID']),
        channelId:
            projectedChannelId ?? (scope.isChannel ? scope.channelId : null),
      ),
      updatedAt: _parseOptionalIcsDate(fields['DTSTAMP']),
    );
  }

  String _toIcalendar(String uid, CalendarEventDraft draft) {
    return '''
BEGIN:VCALENDAR\r
VERSION:2.0\r
PRODID:-//Weave//Flutter CalDAV Facade//EN\r
BEGIN:VEVENT\r
UID:${_icsText(uid)}\r
DTSTAMP:${_caldavTime(DateTime.now().toUtc())}\r
DTSTART:${_caldavTime(draft.startTime)}\r
DTEND:${_caldavTime(draft.endTime)}\r
SUMMARY:${_icsText(draft.title)}\r
${draft.description == null ? '' : 'DESCRIPTION:${_icsText(draft.description!)}\r\n'}${draft.location == null ? '' : 'LOCATION:${_icsText(draft.location!)}\r\n'}END:VEVENT\r
END:VCALENDAR\r
''';
  }

  DateTime _parseIcsDate(String? value) {
    return _parseOptionalIcsDate(value) ?? DateTime.now().toUtc();
  }

  DateTime? _parseOptionalIcsDate(String? value) {
    if (value == null || value.length < 16) return null;
    final normalized = value.trim().toUpperCase();
    final year = int.parse(normalized.substring(0, 4));
    final month = int.parse(normalized.substring(4, 6));
    final day = int.parse(normalized.substring(6, 8));
    final hour = int.parse(normalized.substring(9, 11));
    final minute = int.parse(normalized.substring(11, 13));
    final second = int.parse(normalized.substring(13, 15));
    return DateTime.utc(year, month, day, hour, minute, second);
  }

  String _caldavTime(DateTime value) {
    final utc = value.toUtc();
    String two(int number) => number.toString().padLeft(2, '0');
    return '${utc.year}${two(utc.month)}${two(utc.day)}T'
        '${two(utc.hour)}${two(utc.minute)}${two(utc.second)}Z';
  }

  String _icsText(String value) {
    return value
        .replaceAll('\\', r'\\')
        .replaceAll('\n', r'\n')
        .replaceAll(',', r'\,')
        .replaceAll(';', r'\;');
  }

  String? _optionalIcsText(String? value) {
    if (value == null || value.trim().isEmpty) return null;
    final decoded = StringBuffer();
    var escaped = false;
    for (final codePoint in value.runes) {
      final character = String.fromCharCode(codePoint);
      if (escaped) {
        decoded.write(character.toLowerCase() == 'n' ? '\n' : character);
        escaped = false;
      } else if (character == r'\') {
        escaped = true;
      } else {
        decoded.write(character);
      }
    }
    if (escaped) decoded.write(r'\');
    return decoded.toString();
  }

  String? _firstChildText(XmlElement element, String localName) {
    for (final child in element.descendants.whereType<XmlElement>()) {
      if (child.name.local == localName) {
        return child.innerText;
      }
    }
    return null;
  }

  String? _eventIdFromLocation(String? location) {
    if (location == null || location.trim().isEmpty) return null;
    final uri = Uri.tryParse(location);
    final path = uri?.path ?? location;
    final segments = path.split('/').where((segment) => segment.isNotEmpty);
    final last = segments.isEmpty ? null : segments.last;
    if (last == null) return null;
    return last.endsWith('.ics') ? last.substring(0, last.length - 4) : last;
  }

  String _domainEventId(CalendarScope scope, String uid) {
    if (scope.isWorkspace) {
      return uid;
    }
    return 'caldav:${Uri.encodeComponent(scope.id)}:$uid';
  }

  _CalDavEventRef _eventRef(String id, {CalendarScope? fallbackScope}) {
    if (id.startsWith('caldav:')) {
      final parts = id.split(':');
      if (parts.length >= 3) {
        final scopeId = Uri.decodeComponent(parts[1]);
        final uid = parts.sublist(2).join(':');
        return _CalDavEventRef(scope: _scopeFromId(scopeId), uid: uid);
      }
    }
    return _CalDavEventRef(
      scope: fallbackScope ?? CalendarScope.workspace,
      uid: id,
    );
  }

  CalendarScope _scopeFromId(String scopeId) {
    if (scopeId == 'workspace' || scopeId.isEmpty) {
      return CalendarScope.workspace;
    }
    if (scopeId.startsWith('team:')) {
      final teamId = scopeId.substring('team:'.length);
      return CalendarScope(
        id: scopeId,
        type: 'team',
        label: '$teamId team calendar',
        contextId: 'team-$teamId',
        teamId: teamId,
        accessModel: 'shared-team-calendar',
      );
    }
    if (scopeId.startsWith('channel:')) {
      final channelId = scopeId.substring('channel:'.length);
      return CalendarScope(
        id: scopeId,
        type: 'channel',
        label: '$channelId channel calendar',
        contextId: 'channel-$channelId',
        teamId: 'engineering',
        channelId: channelId,
        accessModel: 'shared-channel-calendar',
      );
    }
    return CalendarScope.workspace;
  }
}

class _CalendarFacadeContext {
  const _CalendarFacadeContext({
    required this.baseUrl,
    required this.accessToken,
    required this.authConfiguration,
  });

  final Uri baseUrl;
  final String accessToken;
  final AuthConfiguration authConfiguration;
}

class _CalDavEventRef {
  const _CalDavEventRef({required this.scope, required this.uid});

  final CalendarScope scope;
  final String uid;
}
