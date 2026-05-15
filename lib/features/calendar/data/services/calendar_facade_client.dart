import 'dart:convert';

import 'package:http/http.dart' as http;
import 'package:weave/core/failures/app_failure.dart';
import 'package:weave/features/auth/domain/entities/auth_configuration.dart';
import 'package:weave/features/auth/domain/repositories/auth_session_repository.dart';
import 'package:weave/features/calendar/domain/entities/calendar_event.dart';
import 'package:weave/features/server_config/domain/entities/server_configuration.dart';
import 'package:weave/features/server_config/domain/repositories/server_configuration_repository.dart';

/// HTTP client for the Weave backend calendar product facade.
///
/// The backend owns direct CalDAV/Nextcloud access; Flutter calls these product
/// endpoints only for MVP calendar flows.
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

  Future<CalendarEventList> listEvents({DateTime? from, DateTime? to}) async {
    final context = await _requireContext();
    final query = <String, String>{
      if (from != null) 'from': from.toUtc().toIso8601String(),
      if (to != null) 'to': to.toUtc().toIso8601String(),
    };
    final response = await _send(
      () => _httpClient.get(
        _apiUri(context.baseUrl, const [
          'api',
          'calendar',
          'events',
        ], query: query.isEmpty ? null : query),
        headers: _jsonHeaders(context.accessToken),
      ),
      fallbackMessage: 'Unable to load calendar events from the Weave backend.',
    );
    _ensureSuccess(response, successCodes: const {200});
    final payload = _decodeObject(response.body);
    final rawEvents = payload['events'];
    if (rawEvents is! List) {
      throw const AppFailure.unknown(
        'The Weave backend returned an invalid calendar payload.',
      );
    }
    final scope = _decodeScope(payload['scope']);
    final events = rawEvents
        .whereType<Map<String, dynamic>>()
        .map((event) => _decodeEvent(event, defaultScope: scope))
        .toList(growable: false);
    return CalendarEventList(scope: scope, events: events);
  }

  Future<CalendarClientSetup> clientSetup() async {
    final context = await _requireContext();
    final response = await _send(
      () => _httpClient.get(
        _apiUri(context.baseUrl, const ['api', 'calendar', 'client-setup']),
        headers: _jsonHeaders(context.accessToken),
      ),
      fallbackMessage:
          'Unable to load calendar setup metadata from the Weave backend.',
    );
    _ensureSuccess(response, successCodes: const {200});
    final payload = _decodeObject(response.body);
    final endpoints = _decodeObjectValue(
      payload['endpoints'],
      'calendar setup endpoints',
    );
    final rawOptions = payload['options'];
    if (rawOptions is! List) {
      throw const AppFailure.unknown(
        'The Weave backend returned calendar setup data without options.',
      );
    }

    return CalendarClientSetup(
      scope: _decodeScope(payload['scope']),
      username: _readString(payload, 'username'),
      endpoints: CalendarExternalEndpoints(
        serverUrl: _readString(endpoints, 'serverUrl'),
        caldavDiscoveryUrl: _readString(endpoints, 'caldavDiscoveryUrl'),
        principalUrl: _readString(endpoints, 'principalUrl'),
      ),
      credentialPolicy: _readString(payload, 'credentialPolicy'),
      accessModel: _decodeAccessModel(payload['accessModel']),
      credentialReadiness: _decodeCredentialReadiness(
        payload['credentialReadiness'],
      ),
      options: rawOptions
          .whereType<Map<String, dynamic>>()
          .map(_decodeSetupOption)
          .toList(growable: false),
    );
  }

  Future<CalendarEvent> readEvent(String id) async {
    final context = await _requireContext();
    final response = await _send(
      () => _httpClient.get(
        _apiUri(context.baseUrl, ['api', 'calendar', 'events', id]),
        headers: _jsonHeaders(context.accessToken),
      ),
      fallbackMessage: 'Unable to read the calendar event.',
    );
    _ensureSuccess(response, successCodes: const {200});
    return _decodeEvent(_decodeObject(response.body));
  }

  Future<CalendarEvent> createEvent(CalendarEventDraft draft) async {
    final context = await _requireContext();
    final response = await _send(
      () => _httpClient.post(
        _apiUri(context.baseUrl, const ['api', 'calendar', 'events']),
        headers: _jsonHeaders(context.accessToken),
        body: jsonEncode(draft.toJson()),
      ),
      fallbackMessage: 'Unable to create the calendar event.',
    );
    _ensureSuccess(response, successCodes: const {200});
    return _decodeEvent(_decodeObject(response.body));
  }

  Future<CalendarEvent> updateEvent({
    required String id,
    required CalendarEventPatch patch,
  }) async {
    final context = await _requireContext();
    final response = await _send(
      () => _httpClient.patch(
        _apiUri(context.baseUrl, ['api', 'calendar', 'events', id]),
        headers: _jsonHeaders(context.accessToken),
        body: jsonEncode(patch.toJson()),
      ),
      fallbackMessage: 'Unable to update the calendar event.',
    );
    _ensureSuccess(response, successCodes: const {200});
    return _decodeEvent(_decodeObject(response.body));
  }

  Future<void> deleteEvent(String id) async {
    final context = await _requireContext();
    final response = await _send(
      () => _httpClient.delete(
        _apiUri(context.baseUrl, ['api', 'calendar', 'events', id]),
        headers: _jsonHeaders(context.accessToken),
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

    final authState = await _authSessionRepository.restoreSession(
      _authConfiguration(configuration),
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

  CalendarEvent _decodeEvent(
    Map<String, dynamic> json, {
    CalendarScope defaultScope = CalendarScope.workspace,
  }) {
    return CalendarEvent(
      id: _readString(json, 'id'),
      title: _readString(json, 'title'),
      description: _readNullableString(json, 'description'),
      startTime: _readDateTime(json, 'startsAt'),
      endTime: _readDateTime(json, 'endsAt'),
      timezone: _readNullableString(json, 'timezone'),
      location: _readNullableString(json, 'location'),
      allDay: json['allDay'] == true,
      etag: _readNullableString(json, 'etag'),
      scope: _decodeScope(json['scope'], defaultScope: defaultScope),
    );
  }

  CalendarScope _decodeScope(
    Object? rawScope, {
    CalendarScope defaultScope = CalendarScope.workspace,
  }) {
    if (rawScope is! Map<String, dynamic>) {
      return defaultScope;
    }

    final rawType = rawScope['type'];
    final type = rawType is String ? rawType.trim() : '';
    if (type.isEmpty) {
      return defaultScope;
    }

    final rawLabel = rawScope['label'];
    final label = rawLabel is String && rawLabel.trim().isNotEmpty
        ? rawLabel.trim()
        : switch (type) {
            'workspace' => CalendarScope.workspace.label,
            _ => type,
          };

    return CalendarScope(type: type, label: label);
  }

  CalendarAccessModel _decodeAccessModel(Object? rawAccessModel) {
    if (rawAccessModel is! Map<String, dynamic>) {
      return CalendarAccessModel.workspaceBlockedPrivateCalendars;
    }

    return CalendarAccessModel(
      type: _readString(rawAccessModel, 'type'),
      productScope: _readString(rawAccessModel, 'productScope'),
      privateUserCalendarsAvailable:
          rawAccessModel['privateUserCalendarsAvailable'] == true,
      privateUserCalendarsReason: _readString(
        rawAccessModel,
        'privateUserCalendarsReason',
      ),
      externalClientCredentialModel: _readString(
        rawAccessModel,
        'externalClientCredentialModel',
      ),
      notes: _readStringList(rawAccessModel['notes']),
    );
  }

  CalendarCredentialReadiness _decodeCredentialReadiness(
    Object? rawCredentialReadiness,
  ) {
    if (rawCredentialReadiness is! Map<String, dynamic>) {
      return CalendarCredentialReadiness.blockedUntilRevocableCredentials;
    }

    return CalendarCredentialReadiness(
      status: _readString(rawCredentialReadiness, 'status'),
      appleProfileSigned: rawCredentialReadiness['appleProfileSigned'] == true,
      appleProfilePasswordIncluded:
          rawCredentialReadiness['appleProfilePasswordIncluded'] == true,
      revocableCredentialsAvailable:
          rawCredentialReadiness['revocableCredentialsAvailable'] == true,
      readOnlySubscriptionTokensAvailable:
          rawCredentialReadiness['readOnlySubscriptionTokensAvailable'] == true,
      backendActorCredentialsExposed:
          rawCredentialReadiness['backendActorCredentialsExposed'] == true,
      blockers: _readStringList(rawCredentialReadiness['blockers']),
    );
  }

  CalendarClientSetupOption _decodeSetupOption(Map<String, dynamic> json) {
    final rawGuidance = json['guidance'];
    return CalendarClientSetupOption(
      platform: _readString(json, 'platform'),
      method: _readString(json, 'method'),
      available: json['available'] == true,
      actionUrl: _readNullableString(json, 'actionUrl'),
      unavailableReason: _readNullableString(json, 'unavailableReason'),
      guidance: _readStringList(rawGuidance),
    );
  }

  List<String> _readStringList(Object? value) {
    return value is List
        ? value.whereType<String>().toList(growable: false)
        : const [];
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

  Map<String, dynamic> _decodeObjectValue(Object? value, String label) {
    if (value is Map<String, dynamic>) {
      return value;
    }
    throw AppFailure.unknown('The Weave backend returned invalid $label.');
  }

  String? _errorMessage(String body) {
    try {
      final payload = jsonDecode(body);
      if (payload is Map<String, dynamic>) {
        final message = payload['message'];
        if (message is String && message.trim().isNotEmpty) {
          return message;
        }
      }
    } catch (_) {
      return null;
    }
    return null;
  }

  String _readString(Map<String, dynamic> json, String key) {
    final value = json[key];
    if (value is String && value.trim().isNotEmpty) {
      return value;
    }
    throw AppFailure.unknown(
      'The Weave backend returned a calendar event without $key.',
    );
  }

  String? _readNullableString(Map<String, dynamic> json, String key) {
    final value = json[key];
    if (value is String && value.trim().isNotEmpty) {
      return value;
    }
    return null;
  }

  DateTime _readDateTime(Map<String, dynamic> json, String key) {
    final value = json[key];
    if (value is String) {
      final parsed = DateTime.tryParse(value);
      if (parsed != null) {
        return parsed;
      }
    }
    throw AppFailure.unknown(
      'The Weave backend returned a calendar event without a valid $key.',
    );
  }

  Map<String, String> _jsonHeaders(String accessToken) {
    return {
      'Accept': 'application/json',
      'Content-Type': 'application/json',
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
}

class _CalendarFacadeContext {
  const _CalendarFacadeContext({
    required this.baseUrl,
    required this.accessToken,
  });

  final Uri baseUrl;
  final String accessToken;
}
