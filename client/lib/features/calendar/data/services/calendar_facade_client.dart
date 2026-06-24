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
    final query = <String, String>{
      if (from != null) 'from': from.toUtc().toIso8601String(),
      if (to != null) 'to': to.toUtc().toIso8601String(),
      if (selectedScope != null && !selectedScope.isWorkspace)
        'scopeType': selectedScope.type,
      if (selectedScope?.teamId case final teamId?) 'teamId': teamId,
      if (selectedScope?.channelId case final channelId?)
        'channelId': channelId,
    };
    final response = await _sendAuthenticated(
      context,
      (accessToken) => _httpClient.get(
        _apiUri(context.baseUrl, const [
          'api',
          'calendar',
          'events',
        ], query: query.isEmpty ? null : query),
        headers: _jsonHeaders(accessToken),
      ),
      fallbackMessage: 'Unable to load calendar events from the Weave backend.',
    );
    _ensureSuccess(response, successCodes: const {200});
    return openapi.CalendarEventsResponse.fromJson(
      _decodeObject(response.body),
    ).toDomain();
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
    final context = await _requireContext();
    final response = await _sendAuthenticated(
      context,
      (accessToken) => _httpClient.get(
        _apiUri(context.baseUrl, ['api', 'calendar', 'events', id]),
        headers: _jsonHeaders(accessToken),
      ),
      fallbackMessage: 'Unable to read the calendar event.',
    );
    _ensureSuccess(response, successCodes: const {200});
    return openapi.CalendarEventResponse.fromJson(
      _decodeObject(response.body),
    ).toDomain();
  }

  Future<CalendarEvent> createEvent(CalendarEventDraft draft) async {
    final context = await _requireContext();
    final response = await _sendAuthenticated(
      context,
      (accessToken) => _httpClient.post(
        _apiUri(context.baseUrl, const ['api', 'calendar', 'events']),
        headers: _jsonHeaders(accessToken),
        body: jsonEncode(draft.toJson()),
      ),
      fallbackMessage: 'Unable to create the calendar event.',
    );
    _ensureSuccess(response, successCodes: const {200});
    return openapi.CalendarEventResponse.fromJson(
      _decodeObject(response.body),
    ).toDomain();
  }

  Future<CalendarEvent> updateEvent({
    required String id,
    required CalendarEventPatch patch,
  }) async {
    final context = await _requireContext();
    final response = await _sendAuthenticated(
      context,
      (accessToken) => _httpClient.patch(
        _apiUri(context.baseUrl, ['api', 'calendar', 'events', id]),
        headers: _jsonHeaders(accessToken),
        body: jsonEncode(patch.toJson()),
      ),
      fallbackMessage: 'Unable to update the calendar event.',
    );
    _ensureSuccess(response, successCodes: const {200});
    return openapi.CalendarEventResponse.fromJson(
      _decodeObject(response.body),
    ).toDomain();
  }

  Future<void> deleteEvent(String id) async {
    final context = await _requireContext();
    final response = await _sendAuthenticated(
      context,
      (accessToken) => _httpClient.delete(
        _apiUri(context.baseUrl, ['api', 'calendar', 'events', id]),
        headers: _jsonHeaders(accessToken),
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
    required this.authConfiguration,
  });

  final Uri baseUrl;
  final String accessToken;
  final AuthConfiguration authConfiguration;
}
