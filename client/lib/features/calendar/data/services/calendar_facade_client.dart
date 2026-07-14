import 'dart:convert';

import 'package:http/http.dart' as http;
import 'package:timezone/data/latest.dart' as timezone_data;
import 'package:timezone/timezone.dart' as timezone;
import 'package:weave/core/failures/app_failure.dart';
import 'package:weave/features/auth/domain/entities/auth_configuration.dart';
import 'package:weave/features/auth/domain/repositories/auth_session_repository.dart';
import 'package:weave/features/calendar/data/dtos/calendar_openapi_mappers.dart';
import 'package:weave/features/calendar/domain/entities/calendar_event.dart';
import 'package:weave/features/server_config/domain/entities/server_configuration.dart';
import 'package:weave/features/server_config/domain/repositories/server_configuration_repository.dart';
import 'package:weave/generated/openapi_models.dart' as openapi;
import 'package:xml/xml.dart';

final bool _calendarTimeZonesInitialized = (() {
  timezone_data.initializeTimeZones();
  return true;
})();

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
    final fields = <String, _IcsProperty>{};
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
      final property = _IcsProperty.tryParse(line);
      if (property != null) {
        fields[property.name] = property;
      }
    }
    final startProperty = fields['DTSTART'];
    final endProperty = fields['DTEND'];
    final start = _parseIcsDate(startProperty, fieldName: 'DTSTART');
    final end = _parseIcsDate(endProperty, fieldName: 'DTEND');
    if (!end.isAfter(start)) {
      throw const AppFailure.validation(
        'The calendar event end must be after its start.',
      );
    }
    final projectedContextId = _optionalIcsText(
      fields['X-WEAVE-CONTEXT-ID']?.value,
    );
    final projectedChannelId = _optionalIcsText(
      fields['X-WEAVE-CHANNEL-ID']?.value,
    );
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
      title: _optionalIcsText(fields['SUMMARY']?.value) ?? 'Calendar event',
      description: _optionalIcsText(fields['DESCRIPTION']?.value),
      startTime: start,
      endTime: end,
      timezone: _calendarTimeZone(startProperty),
      location: _optionalIcsText(fields['LOCATION']?.value),
      allDay: _isAllDay(startProperty),
      etag: etag,
      scope: scope,
      threadRef: CalendarThreadRef(
        contextId: projectedContextId ?? scope.contextId,
        meetingThreadId: _optionalIcsText(
          fields['X-WEAVE-MEETING-THREAD-ID']?.value,
        ),
        channelId:
            projectedChannelId ?? (scope.isChannel ? scope.channelId : null),
      ),
      updatedAt: _parseOptionalIcsDate(
        fields['LAST-MODIFIED'] ?? fields['DTSTAMP'],
        fieldName: 'calendar timestamp',
      ),
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
${_icsDateTimeProperty('DTSTART', draft.startTime, draft.timezone, draft.allDay)}\r
${_icsDateTimeProperty('DTEND', draft.endTime, draft.timezone, draft.allDay)}\r
SUMMARY:${_icsText(draft.title)}\r
${draft.description == null ? '' : 'DESCRIPTION:${_icsText(draft.description!)}\r\n'}${draft.location == null ? '' : 'LOCATION:${_icsText(draft.location!)}\r\n'}END:VEVENT\r
END:VCALENDAR\r
''';
  }

  DateTime _parseIcsDate(_IcsProperty? property, {required String fieldName}) {
    final parsed = _parseOptionalIcsDate(property, fieldName: fieldName);
    if (parsed == null) {
      throw AppFailure.validation(
        'The calendar event is missing its required $fieldName value.',
      );
    }
    return parsed;
  }

  DateTime? _parseOptionalIcsDate(
    _IcsProperty? property, {
    required String fieldName,
  }) {
    if (property == null || property.value.trim().isEmpty) {
      return null;
    }
    try {
      final value = property.value.trim().toUpperCase();
      final dateOnly = _isAllDay(property);
      final match =
          (dateOnly
                  ? RegExp(r'^(\d{4})(\d{2})(\d{2})$')
                  : RegExp(
                      r'^(\d{4})(\d{2})(\d{2})T(\d{2})(\d{2})(\d{2})(Z)?$',
                    ))
              .firstMatch(value);
      if (match == null) {
        throw const FormatException('Unsupported iCalendar date shape.');
      }
      final year = int.parse(match.group(1)!);
      final month = int.parse(match.group(2)!);
      final day = int.parse(match.group(3)!);
      final hour = dateOnly ? 0 : int.parse(match.group(4)!);
      final minute = dateOnly ? 0 : int.parse(match.group(5)!);
      final second = dateOnly ? 0 : int.parse(match.group(6)!);
      final isUtc = !dateOnly && match.group(7) != null;
      final parsed = isUtc
          ? DateTime.utc(year, month, day, hour, minute, second)
          : timezone.TZDateTime(
              _timeZoneLocation(_calendarTimeZone(property)),
              year,
              month,
              day,
              hour,
              minute,
              second,
            ).toUtc();
      final local = isUtc
          ? parsed
          : timezone.TZDateTime.from(
              parsed,
              _timeZoneLocation(_calendarTimeZone(property)),
            );
      if (local.year != year ||
          local.month != month ||
          local.day != day ||
          local.hour != hour ||
          local.minute != minute ||
          local.second != second) {
        throw const FormatException('Invalid iCalendar local date.');
      }
      return parsed;
    } catch (error) {
      throw AppFailure.validation(
        'The calendar event contains an invalid $fieldName value.',
        cause: error,
      );
    }
  }

  bool _isAllDay(_IcsProperty? property) =>
      property?.parameters['VALUE']?.toUpperCase() == 'DATE';

  String _calendarTimeZone(_IcsProperty? property) {
    if (property == null || property.value.trim().toUpperCase().endsWith('Z')) {
      return 'UTC';
    }
    final timeZoneId = property.parameters['TZID']?.trim();
    return timeZoneId == null || timeZoneId.isEmpty ? 'UTC' : timeZoneId;
  }

  timezone.Location _timeZoneLocation(String timeZoneId) {
    final normalized = timeZoneId.trim();
    if (!RegExp(r'^[A-Za-z0-9_+./-]{1,128}$').hasMatch(normalized)) {
      throw const AppFailure.validation(
        'The calendar event timezone is invalid.',
      );
    }
    if (<String>{
      'UTC',
      'ETC/UTC',
      'GMT',
      'Z',
    }.contains(normalized.toUpperCase())) {
      return timezone.UTC;
    }
    if (!_calendarTimeZonesInitialized) {
      throw const AppFailure.validation(
        'The calendar timezone database is unavailable.',
      );
    }
    try {
      return timezone.getLocation(normalized);
    } on timezone.LocationNotFoundException catch (error) {
      throw AppFailure.validation(
        'The calendar event timezone is not supported.',
        cause: error,
      );
    }
  }

  String _icsDateTimeProperty(
    String name,
    DateTime value,
    String timeZoneId,
    bool allDay,
  ) {
    final location = _timeZoneLocation(timeZoneId);
    final local = timezone.TZDateTime.from(value.toUtc(), location);
    String two(int number) => number.toString().padLeft(2, '0');
    final date = '${local.year}${two(local.month)}${two(local.day)}';
    if (allDay) {
      return '$name;VALUE=DATE:$date';
    }
    if (identical(location, timezone.UTC)) {
      return '$name:${_caldavTime(value)}';
    }
    final localTime =
        '${date}T${two(local.hour)}${two(local.minute)}'
        '${two(local.second)}';
    return '$name;TZID=$timeZoneId:$localTime';
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

class _IcsProperty {
  const _IcsProperty({
    required this.name,
    required this.parameters,
    required this.value,
  });

  static _IcsProperty? tryParse(String line) {
    final separator = line.indexOf(':');
    if (separator <= 0) {
      return null;
    }
    final metadata = line.substring(0, separator).split(';');
    final name = metadata.first.trim().toUpperCase();
    if (name.isEmpty) {
      return null;
    }
    final parameters = <String, String>{};
    for (final component in metadata.skip(1)) {
      final equals = component.indexOf('=');
      if (equals <= 0 || equals == component.length - 1) {
        continue;
      }
      final key = component.substring(0, equals).trim().toUpperCase();
      var value = component.substring(equals + 1).trim();
      if (value.length >= 2 && value.startsWith('"') && value.endsWith('"')) {
        value = value.substring(1, value.length - 1);
      }
      if (key.isNotEmpty && value.isNotEmpty) {
        parameters[key] = value;
      }
    }
    return _IcsProperty(
      name: name,
      parameters: Map.unmodifiable(parameters),
      value: line.substring(separator + 1),
    );
  }

  final String name;
  final Map<String, String> parameters;
  final String value;
}
