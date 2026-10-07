import 'dart:convert';
import 'dart:math';

import 'package:http/http.dart' as http;
import 'package:crypto/crypto.dart';
import 'package:timezone/data/latest.dart' as timezone_data;
import 'package:timezone/timezone.dart' as tz;
import 'package:weave/features/auth/domain/entities/auth_configuration.dart';
import 'package:weave/features/auth/domain/repositories/auth_session_repository.dart';
import 'package:weave/features/calendar/domain/entities/calendar_event.dart';
import 'package:weave/features/calendar/domain/entities/calendar_failure.dart';
import 'package:weave/features/server_config/domain/repositories/server_configuration_repository.dart';
import 'package:weave/generated/user_api/api.dart' as api;
import 'package:weave/integrations/weave_api/data/services/weave_user_api_client.dart';

final bool _zonesReady = (() {
  timezone_data.initializeTimeZones();
  return true;
})();

/// Calendar product operations use only the generated User HTTP contract.
/// Complete generated content stays separate from agenda display projections.
class CalendarFacadeClient {
  CalendarFacadeClient({
    required http.Client httpClient,
    required ServerConfigurationRepository serverConfigurationRepository,
    required AuthSessionRepository authSessionRepository,
    required Future<String> Function() evaluationTimeZone,
  }) : _http = httpClient,
       _configuration = serverConfigurationRepository,
       _auth = authSessionRepository,
       _evaluationTimeZone = evaluationTimeZone;

  final http.Client _http;
  final ServerConfigurationRepository _configuration;
  final AuthSessionRepository _auth;
  final Future<String> Function() _evaluationTimeZone;
  final _events = <String, _EventSnapshot>{};
  final _previews = <String, _PreviewSnapshot>{};
  final _calendars = <String, CalendarScope>{};
  String? _owner;
  final _pendingCreates = <String, String>{};

  Future<CalendarScopeList> listScopes() async {
    final context = await _context();
    return _discover(context);
  }

  Future<CalendarScopeList> _discover(_Context context) async {
    final result = await _invoke(
      context,
      (client) => client.listUserCalendars(),
    );
    if (result == null) {
      throw const CalendarFailure(CalendarFailureKind.unavailable);
    }
    final scopes = result.calendars
        .map((calendar) {
          _requireId(calendar.id, 'calendar');
          return _scope(calendar.id, calendar.scope, calendar.allowedActions);
        })
        .toList(growable: false);
    _calendars
      ..clear()
      ..addEntries(scopes.map((scope) => MapEntry(scope.id, scope)));
    return CalendarScopeList(scopes: scopes);
  }

  Future<CalendarEventList> listEvents({
    DateTime? from,
    DateTime? to,
    CalendarScope? selectedScope,
  }) async {
    final context = await _context();
    final zone = await _zone();
    final scopes = await _discover(context);
    if (scopes.scopes.isEmpty) return const CalendarEventList();
    final scope = _selected(scopes, selectedScope);
    final now = DateTime.now().toUtc();
    final result = await _invoke(
      context,
      (client) => client.queryCalendarAgenda(
        scope.id,
        from ?? now.subtract(const Duration(days: 30)),
        to ?? now.add(const Duration(days: 180)),
        zone.name,
      ),
    );
    if (result == null ||
        result.calendarId != scope.id ||
        result.evaluationTimeZone != zone.name) {
      throw const CalendarFailure(CalendarFailureKind.unavailable);
    }
    final masters = <String, api.CalendarUserEvent>{};
    for (final event in result.events) {
      _remember(context, event, scope.id);
      masters[event.id] = event;
    }
    _previews.clear();
    final previewMasters = <String, api.CalendarUserEventPreview>{};
    for (final preview in result.previews) {
      _rememberPreview(context, preview, scope.id);
      previewMasters[preview.handle] = preview;
    }
    final rows = <CalendarEvent>[];
    for (final occurrence in result.occurrences) {
      final event = masters[occurrence.eventId];
      if (event == null) {
        throw const CalendarFailure(CalendarFailureKind.unavailable);
      }
      rows.add(
        _view(
          event,
          scope,
          start: _wallClock(tz.TZDateTime.from(occurrence.startsAt, zone)),
          end: _wallClock(tz.TZDateTime.from(occurrence.endsAt, zone)),
          displayZone: zone.name,
        ),
      );
    }
    for (final occurrence in result.previewOccurrences) {
      final preview = previewMasters[occurrence.previewHandle];
      if (preview == null) {
        throw const CalendarFailure(CalendarFailureKind.unavailable);
      }
      rows.add(
        _viewPreview(
          preview,
          scope,
          start: _wallClock(tz.TZDateTime.from(occurrence.startsAt, zone)),
          end: _wallClock(tz.TZDateTime.from(occurrence.endsAt, zone)),
          displayZone: zone.name,
        ),
      );
    }
    rows.sort((left, right) => left.startTime.compareTo(right.startTime));
    return CalendarEventList(scope: scope, events: rows);
  }

  Future<CalendarEvent> readEvent(String id) async {
    final context = await _context();
    final pending = _previews[id];
    if (pending != null) {
      final preview = _previewSnapshot(context, id);
      final result = await _invoke(
        context,
        (client) => client.readCalendarEventPreview(preview.calendarId, id),
      );
      if (result == null || result.handle != id) {
        throw const CalendarFailure(CalendarFailureKind.unavailable);
      }
      _rememberPreview(context, result, preview.calendarId);
      return _viewPreview(result, _calendars[result.calendarId]!);
    }
    final previous = _snapshot(context, id);
    final result = await _invoke(
      context,
      (client) => client.getCalendarEvent(previous.calendarId, id),
    );
    if (result == null || result.id != id) {
      throw const CalendarFailure(CalendarFailureKind.unavailable);
    }
    _remember(context, result, previous.calendarId);
    return _view(result, _calendars[result.calendarId]!);
  }

  Future<CalendarEvent> createEvent(CalendarEventDraft draft) async {
    final context = await _context();
    final scopes = await _discover(context);
    final scope = _selected(scopes, draft.scope);
    _allow(scope.capabilities, 'create');
    final content = _content(draft);
    // An uncertain network outcome must retain the same logical create identity
    // when the member retries the unchanged draft in this session.
    final fingerprint = sha256
        .convert(utf8.encode(jsonEncode([context.owner, scope.id, content])))
        .toString();
    final key = _pendingCreates.putIfAbsent(fingerprint, () {
      final random = Random.secure();
      return base64UrlEncode(
        List<int>.generate(24, (_) => random.nextInt(256)),
      );
    });
    final result = await _invoke(
      context,
      (client) => client.createCalendarEvent(scope.id, key, content),
      mutation: true,
    );
    if (result == null) {
      throw const CalendarFailure(CalendarFailureKind.unavailable);
    }
    _remember(context, result, scope.id);
    _pendingCreates.remove(fingerprint);
    return _view(result, scope);
  }

  Future<CalendarEvent> updateEvent({
    required String id,
    required CalendarEventDraft draft,
    String? version,
  }) async {
    final context = await _context();
    final preview = _previews[id];
    if (preview != null) {
      final pending = _previewSnapshot(context, id);
      _allow(pending.allowedActions, 'update');
      if (_recurringContent(pending.content)) {
        throw const CalendarFailure(CalendarFailureKind.unsupportedEdit);
      }
      if (version != null || draft.scope.id != pending.calendarId) {
        throw const CalendarFailure(CalendarFailureKind.conflict);
      }
      _content(draft, original: pending.content);
    }
    final original = preview == null
        ? _snapshot(context, id)
        : await _materializePreview(context, preview.preview);
    _allow(original.allowedActions, 'update');
    if (_recurring(original)) {
      throw const CalendarFailure(CalendarFailureKind.unsupportedEdit);
    }
    if ((preview == null && (version == null || version != original.version)) ||
        draft.scope.id != original.calendarId) {
      throw const CalendarFailure(CalendarFailureKind.conflict);
    }
    final content = _content(draft, original: original.content);
    final result = await _invoke(
      context,
      (client) => client.updateCalendarEvent(
        original.calendarId,
        original.id,
        original.version,
        content,
      ),
      mutation: true,
    );
    if (result == null || result.id != original.id) {
      throw const CalendarFailure(CalendarFailureKind.unavailable);
    }
    _remember(context, result, original.calendarId);
    _previews.remove(id);
    return _view(result, _calendars[result.calendarId]!);
  }

  Future<void> deleteEvent(String id, {String? version}) async {
    final context = await _context();
    final preview = _previews[id];
    if (preview != null) {
      final pending = _previewSnapshot(context, id);
      _allow(pending.allowedActions, 'delete');
      if (_recurringContent(pending.content)) {
        throw const CalendarFailure(CalendarFailureKind.unsupportedEdit);
      }
      if (version != null) {
        throw const CalendarFailure(CalendarFailureKind.conflict);
      }
    }
    final original = preview == null
        ? _snapshot(context, id)
        : await _materializePreview(context, preview.preview);
    _allow(original.allowedActions, 'delete');
    if (_recurring(original)) {
      throw const CalendarFailure(CalendarFailureKind.unsupportedEdit);
    }
    if (preview == null && (version == null || version != original.version)) {
      throw const CalendarFailure(CalendarFailureKind.conflict);
    }
    await _invoke(
      context,
      (client) => client.deleteCalendarEvent(
        original.calendarId,
        original.id,
        original.version,
      ),
      mutation: true,
    );
    _events.remove(original.id);
    _previews.remove(id);
  }

  CalendarScope _selected(CalendarScopeList scopes, CalendarScope? selected) {
    if (selected == null || selected.id == CalendarScope.workspace.id) {
      return scopes.scopes.firstWhere(
        (scope) => scope.isWorkspace,
        orElse: () =>
            throw const CalendarFailure(CalendarFailureKind.permission),
      );
    }
    return scopes.scopes.firstWhere(
      (scope) => scope.id == selected.id,
      orElse: () => throw const CalendarFailure(CalendarFailureKind.permission),
    );
  }

  CalendarScope _scope(
    String id,
    api.CalendarUserScope scope,
    List<String> actions,
  ) => CalendarScope(
    id: id,
    type: scope.type.value.toLowerCase(),
    label: scope.channelId ?? scope.teamId ?? scope.spaceId,
    contextId: scope.spaceId,
    teamId: scope.teamId,
    channelId: scope.channelId,
    capabilities: List.unmodifiable(actions),
  );

  void _remember(
    _Context context,
    api.CalendarUserEvent event,
    String calendarId,
  ) {
    _requireId(event.id, 'event');
    if (event.calendarId != calendarId ||
        !_calendars.containsKey(calendarId) ||
        !RegExp(r'^"[^"\r\n]+"$').hasMatch(event.version)) {
      throw const CalendarFailure(CalendarFailureKind.unavailable);
    }
    final scope = _calendars[calendarId]!;
    if (event.scope.spaceId != scope.contextId ||
        event.scope.type.value.toLowerCase() != scope.type ||
        event.scope.teamId != scope.teamId ||
        event.scope.channelId != scope.channelId) {
      throw const CalendarFailure(CalendarFailureKind.unavailable);
    }
    _events[event.id] = _EventSnapshot(context.owner, event);
  }

  void _rememberPreview(
    _Context context,
    api.CalendarUserEventPreview preview,
    String calendarId,
  ) {
    if (!RegExp(r'^pv_[A-Za-z0-9_-]{32}$').hasMatch(preview.handle) ||
        preview.calendarId != calendarId ||
        !_calendars.containsKey(calendarId)) {
      throw const CalendarFailure(CalendarFailureKind.unavailable);
    }
    final scope = _calendars[calendarId]!;
    if (preview.scope.spaceId != scope.contextId ||
        preview.scope.type.value.toLowerCase() != scope.type ||
        preview.scope.teamId != scope.teamId ||
        preview.scope.channelId != scope.channelId) {
      throw const CalendarFailure(CalendarFailureKind.unavailable);
    }
    _previews[preview.handle] = _PreviewSnapshot(context.owner, preview);
  }

  api.CalendarUserEvent _snapshot(_Context context, String id) {
    final snapshot = _events[id];
    if (snapshot == null || snapshot.owner != context.owner) {
      throw const CalendarFailure(CalendarFailureKind.conflict);
    }
    return snapshot.event;
  }

  api.CalendarUserEventPreview _previewSnapshot(_Context context, String id) {
    final snapshot = _previews[id];
    if (snapshot == null || snapshot.owner != context.owner) {
      throw const CalendarFailure(CalendarFailureKind.conflict);
    }
    return snapshot.preview;
  }

  Future<api.CalendarUserEvent> _materializePreview(
    _Context context,
    api.CalendarUserEventPreview preview,
  ) async {
    final result = await _invoke(
      context,
      (client) => client.materializeCalendarEventPreview(
        preview.calendarId,
        preview.handle,
      ),
      mutation: true,
    );
    if (result == null || result.calendarId != preview.calendarId) {
      throw const CalendarFailure(CalendarFailureKind.unavailable);
    }
    _remember(context, result, preview.calendarId);
    return result;
  }

  bool _recurring(api.CalendarUserEvent event) =>
      _recurringContent(event.content);

  bool _recurringContent(api.CalendarEventWriteRequest content) =>
      content.recurrence != null || content.overrides.isNotEmpty;

  CalendarEvent _view(
    api.CalendarUserEvent event,
    CalendarScope scope, {
    DateTime? start,
    DateTime? end,
    String? displayZone,
  }) => _viewContent(
    id: event.id,
    content: event.content,
    allowedActions: event.allowedActions,
    etag: event.version,
    scope: scope,
    threadRef: CalendarThreadRef(
      contextId: scope.contextId,
      channelId: scope.channelId,
      meetingThreadId: event.meetingThreadRef,
    ),
    start: start,
    end: end,
    displayZone: displayZone,
  );

  CalendarEvent _viewPreview(
    api.CalendarUserEventPreview preview,
    CalendarScope scope, {
    DateTime? start,
    DateTime? end,
    String? displayZone,
  }) => _viewContent(
    id: preview.handle,
    previewHandle: preview.handle,
    content: preview.content,
    allowedActions: preview.allowedActions,
    scope: scope,
    threadRef: CalendarThreadRef.forScope(scope),
    start: start,
    end: end,
    displayZone: displayZone,
  );

  CalendarEvent _viewContent({
    required String id,
    String? previewHandle,
    required api.CalendarEventWriteRequest content,
    required List<String> allowedActions,
    required CalendarScope scope,
    required CalendarThreadRef threadRef,
    String? etag,
    DateTime? start,
    DateTime? end,
    String? displayZone,
  }) => CalendarEvent(
    id: id,
    previewHandle: previewHandle,
    title: content.title,
    description: content.description,
    location: content.location,
    startTime: start ?? _time(content.start),
    endTime: end ?? _time(content.end),
    timezone:
        displayZone ??
        content.start.timeZone ??
        (content.start.kind == api.CalendarTimeValueKindEnum.UTC
            ? 'UTC'
            : null),
    allDay: content.start.kind == api.CalendarTimeValueKindEnum.DATE,
    timeKind: CalendarTimeKind.values.byName(
      content.start.kind.value.toLowerCase(),
    ),
    recurring: _recurringContent(content),
    allowedActions: List.unmodifiable(allowedActions),
    etag: etag,
    scope: scope,
    threadRef: threadRef,
    attendees: content.attendees
        .map(
          (attendee) => CalendarAttendee(
            name: attendee.displayName,
            email: attendee.address,
            role: attendee.role,
            responseStatus: attendee.response,
          ),
        )
        .toList(growable: false),
  );

  DateTime _time(api.CalendarTimeValue value) {
    if (value.kind == api.CalendarTimeValueKindEnum.DATE &&
        value.date != null) {
      return DateTime.utc(value.date!.year, value.date!.month, value.date!.day);
    }
    if (value.kind == api.CalendarTimeValueKindEnum.UTC &&
        value.instant != null) {
      return _wallClock(value.instant!.toUtc());
    }
    if ((value.kind == api.CalendarTimeValueKindEnum.FLOATING ||
            value.kind == api.CalendarTimeValueKindEnum.ZONED) &&
        value.localDateTime != null) {
      return DateTime.parse('${value.localDateTime!}Z');
    }
    throw const CalendarFailure(CalendarFailureKind.unavailable);
  }

  api.CalendarEventWriteRequest _content(
    CalendarEventDraft draft, {
    api.CalendarEventWriteRequest? original,
  }) {
    if (!draft.endTime.isAfter(draft.startTime) || draft.title.trim().isEmpty) {
      throw const CalendarFailure(CalendarFailureKind.unsupportedEdit);
    }
    return api.CalendarEventWriteRequest(
      title: draft.title,
      description: draft.description,
      location: draft.location,
      start: _value(draft.startTime, draft),
      end: _value(draft.endTime, draft),
      attendees: original?.attendees ?? const [],
      recurrence: original?.recurrence,
      overrides: original?.overrides ?? const [],
    );
  }

  api.CalendarTimeValue _value(DateTime value, CalendarEventDraft draft) {
    if (draft.allDay) {
      return api.CalendarTimeValue(
        kind: api.CalendarTimeValueKindEnum.DATE,
        date: DateTime.utc(value.year, value.month, value.day),
      );
    }
    final wall = DateTime.utc(
      value.year,
      value.month,
      value.day,
      value.hour,
      value.minute,
      value.second,
    );
    if (draft.timeKind == CalendarTimeKind.utc) {
      return api.CalendarTimeValue(
        kind: api.CalendarTimeValueKindEnum.UTC,
        instant: wall,
      );
    }
    final local = wall.toIso8601String().substring(0, 19);
    if (draft.timeKind == CalendarTimeKind.floating) {
      return api.CalendarTimeValue(
        kind: api.CalendarTimeValueKindEnum.FLOATING,
        localDateTime: local,
      );
    }
    _location(draft.timezone);
    return api.CalendarTimeValue(
      kind: api.CalendarTimeValueKindEnum.ZONED,
      localDateTime: local,
      timeZone: draft.timezone,
    );
  }

  DateTime _wallClock(DateTime date) => DateTime.utc(
    date.year,
    date.month,
    date.day,
    date.hour,
    date.minute,
    date.second,
  );
  Future<tz.Location> _zone() async => _location(await _evaluationTimeZone());
  tz.Location _location(String name) {
    if (!_zonesReady) {
      throw const CalendarFailure(CalendarFailureKind.unavailable);
    }
    try {
      return tz.getLocation(name);
    } catch (_) {
      throw const CalendarFailure(CalendarFailureKind.unavailable);
    }
  }

  void _requireId(String value, String prefix) {
    if (!RegExp('^$prefix:[a-zA-Z0-9-]+\$').hasMatch(value)) {
      throw const CalendarFailure(CalendarFailureKind.unavailable);
    }
  }

  void _allow(List<String> actions, String action) {
    if (!actions.contains(action)) {
      throw const CalendarFailure(CalendarFailureKind.permission);
    }
  }

  api.ApiClient _client(_Context context) => weaveUserApiClient(
    apiBaseUrl: context.url,
    accessToken: context.token,
    httpClient: _http,
  );

  Future<_Context> _context() async {
    final configuration = await _configuration.loadConfiguration();
    if (configuration == null) {
      throw const CalendarFailure(CalendarFailureKind.session);
    }
    final auth = AuthConfiguration(
      issuer: configuration.oidcIssuerUrl,
      clientId: configuration.oidcClientRegistration.clientId.trim(),
    );
    final state = await _auth.restoreSession(auth);
    if (!state.isAuthenticated || state.session?.matches(auth) != true) {
      throw const CalendarFailure(CalendarFailureKind.session);
    }
    final context = _Context(
      configuration.serviceEndpoints.backendApiBaseUrl,
      auth,
      state.session!.accessToken,
    );
    context.identity = await _identity(context);
    await _assertCurrent(context);
    if (_owner != context.owner) {
      _events.clear();
      _previews.clear();
      _calendars.clear();
      _pendingCreates.clear();
      _owner = context.owner;
    }
    return context;
  }

  Future<String> _identity(_Context context) async {
    try {
      final identity = await api.IdentityApi(
        _client(context),
      ).me().timeout(const Duration(seconds: 8));
      if (identity?.subject?.isNotEmpty != true ||
          identity?.organizationId?.isNotEmpty != true ||
          identity?.identityIssuer != context.auth.issuer.toString()) {
        throw const CalendarFailure(CalendarFailureKind.session);
      }
      return jsonEncode([identity!.subject, identity.organizationId]);
    } catch (_) {
      throw const CalendarFailure(CalendarFailureKind.session);
    }
  }

  Future<void> _assertCurrent(_Context context) async {
    final configuration = await _configuration.loadConfiguration();
    final state = await _auth.restoreSession(context.auth);
    if (configuration?.serviceEndpoints.backendApiBaseUrl != context.url ||
        configuration?.oidcIssuerUrl != context.auth.issuer ||
        configuration?.oidcClientRegistration.clientId.trim() !=
            context.auth.clientId ||
        !state.isAuthenticated ||
        state.session?.matches(context.auth) != true ||
        state.session?.accessToken != context.token) {
      throw const CalendarFailure(CalendarFailureKind.session);
    }
  }

  Future<T> _invoke<T>(
    _Context context,
    Future<T> Function(api.CalendarUserApi) request, {
    bool mutation = false,
  }) async {
    Future<T> send() async {
      await _assertCurrent(context);
      if (mutation) {
        if (await _identity(context) != context.identity) {
          throw const CalendarFailure(CalendarFailureKind.session);
        }
        await _assertCurrent(context);
      }
      final result = await request(
        api.CalendarUserApi(_client(context)),
      ).timeout(const Duration(seconds: 20));
      await _assertCurrent(context);
      return result;
    }

    try {
      return await send();
    } on api.ApiException catch (error) {
      if (error.code == 401) {
        await _assertCurrent(context);
        final state = await _auth.refreshSession(context.auth);
        final session = state.session;
        if (!state.isAuthenticated ||
            session == null ||
            !session.matches(context.auth) ||
            session.accessToken == context.token) {
          throw const CalendarFailure(CalendarFailureKind.session);
        }
        context.token = session.accessToken;
        if (await _identity(context) != context.identity) {
          throw const CalendarFailure(CalendarFailureKind.session);
        }
        try {
          return await send();
        } on api.ApiException catch (retry) {
          throw _failure(retry.code);
        } on CalendarFailure {
          rethrow;
        } catch (_) {
          throw const CalendarFailure(CalendarFailureKind.unavailable);
        }
      }
      throw _failure(error.code);
    } on CalendarFailure {
      rethrow;
    } catch (_) {
      throw const CalendarFailure(CalendarFailureKind.unavailable);
    }
  }

  CalendarFailure _failure(int status) => CalendarFailure(switch (status) {
    401 => CalendarFailureKind.session,
    403 => CalendarFailureKind.permission,
    409 || 412 || 428 => CalendarFailureKind.conflict,
    400 || 422 => CalendarFailureKind.unsupportedEdit,
    _ => CalendarFailureKind.unavailable,
  });
}

class _Context {
  _Context(this.url, this.auth, this.token);
  final Uri url;
  final AuthConfiguration auth;
  String token;
  late final String identity;
  String get owner => jsonEncode([
    url.toString(),
    auth.issuer.toString(),
    auth.clientId,
    identity,
  ]);
}

class _EventSnapshot {
  _EventSnapshot(this.owner, this.event);
  final String owner;
  final api.CalendarUserEvent event;
}

class _PreviewSnapshot {
  _PreviewSnapshot(this.owner, this.preview);
  final String owner;
  final api.CalendarUserEventPreview preview;
}
