import 'dart:convert';

import 'package:http/http.dart' as http;
import 'package:weave/features/auth/domain/entities/auth_configuration.dart';
import 'package:weave/features/auth/domain/repositories/auth_session_repository.dart';
import 'package:weave/features/chat/data/dtos/chat_openapi_mappers.dart';
import 'package:weave/features/chat/domain/entities/chat_conversation.dart';
import 'package:weave/features/chat/domain/entities/chat_failure.dart';
import 'package:weave/features/chat/domain/entities/chat_room_timeline.dart';
import 'package:weave/features/chat/domain/repositories/chat_repository.dart';
import 'package:weave/features/server_config/domain/entities/server_configuration.dart';
import 'package:weave/features/server_config/domain/repositories/server_configuration_repository.dart';
import 'package:weave/generated/openapi_models.dart' as openapi;

class BackendChatRepository implements ChatRepository {
  const BackendChatRepository({
    required ServerConfigurationRepository serverConfigurationRepository,
    required AuthSessionRepository authSessionRepository,
    required http.Client httpClient,
  }) : _serverConfigurationRepository = serverConfigurationRepository,
       _authSessionRepository = authSessionRepository,
       _httpClient = httpClient;

  final ServerConfigurationRepository _serverConfigurationRepository;
  final AuthSessionRepository _authSessionRepository;
  final http.Client _httpClient;

  @override
  Future<List<ChatConversation>> loadConversations() async {
    final payload = await _getJson('/api/chat/conversations');
    final page = openapi.ChatConversationsResponse.fromJson(
      payload,
    ).toConversationPage();
    final values = [...page.resources];
    values.sort((a, b) {
      final activityComparison =
          (b.lastActivityAt ?? DateTime.fromMillisecondsSinceEpoch(0))
              .compareTo(
                a.lastActivityAt ?? DateTime.fromMillisecondsSinceEpoch(0),
              );
      if (activityComparison != 0) {
        return activityComparison;
      }
      return a.title.toLowerCase().compareTo(b.title.toLowerCase());
    });
    return values;
  }

  @override
  Future<ChatRoomTimeline> loadRoomTimeline(String roomId) async {
    final payload = await _getJson('/api/chat/conversations/$roomId/messages');
    return openapi.ChatMessagesResponse.fromJson(
      payload,
    ).toRoomTimeline(roomId);
  }

  @override
  Future<void> sendMessage({
    required String roomId,
    required String message,
  }) async {
    final request = openapi.ChatSendMessageRequest(
      text: message,
      attachmentRefs: const <String>[],
    );
    await _requestJson(
      'POST',
      '/api/chat/conversations/$roomId/messages',
      body: request.toJson(),
    );
  }

  @override
  Future<void> markRoomRead(String roomId) async {
    // The Sprint 3 backend facade does not expose provider read markers yet.
    // Keep this a support-safe no-op rather than calling a raw provider SDK.
  }

  @override
  Future<void> connect() async {
    final readiness = await _getJson('/api/chat/readiness');
    final featureReadiness = openapi.ChatReadiness.fromJson(
      readiness,
    ).toFeatureReadiness();
    if (!featureReadiness.isUsable) {
      throw ChatFailure.configuration(featureReadiness.memberImpact);
    }
  }

  @override
  Future<void> signOut() async {
    final configuration = await _loadConfiguration();
    await _authSessionRepository.signOut(_authConfiguration(configuration));
  }

  @override
  Future<void> clearSession() => _authSessionRepository.clearLocalSession();

  Future<Map<String, dynamic>> _getJson(String path) {
    return _requestJson('GET', path);
  }

  Future<Map<String, dynamic>> _requestJson(
    String method,
    String path, {
    Map<String, Object?>? body,
  }) async {
    final configuration = await _loadConfiguration();
    final authState = await _authSessionRepository.restoreSession(
      _authConfiguration(configuration),
    );
    final session = authState.session;
    if (!authState.isAuthenticated || session == null) {
      throw const ChatFailure.configuration(
        'Sign in before opening Weave Chat.',
      );
    }
    final uri = configuration.serviceEndpoints.backendApiBaseUrl.resolve(path);
    final request = http.Request(method, uri)
      ..headers['Authorization'] = 'Bearer ${session.accessToken}'
      ..headers['Accept'] = 'application/json';
    if (body != null) {
      request.headers['Content-Type'] = 'application/json';
      request.body = jsonEncode(body);
    }
    final streamed = await _httpClient.send(request);
    final response = await http.Response.fromStream(streamed);
    if (response.statusCode < 200 || response.statusCode >= 300) {
      throw const ChatFailure.protocol(
        'Weave Chat is unavailable. Ask an admin to inspect Workspace Health.',
      );
    }
    final decoded = jsonDecode(response.body);
    if (decoded is Map<String, dynamic>) {
      return decoded;
    }
    throw const ChatFailure.configuration(
      'The Weave Chat facade returned an invalid response.',
    );
  }

  Future<ServerConfiguration> _loadConfiguration() async {
    final configuration = await _serverConfigurationRepository
        .loadConfiguration();
    if (configuration == null) {
      throw const ChatFailure.configuration(
        'Finish setup before opening Weave Chat.',
      );
    }
    return configuration;
  }

  AuthConfiguration _authConfiguration(ServerConfiguration configuration) {
    return AuthConfiguration(
      issuer: configuration.oidcIssuerUrl,
      clientId: configuration.oidcClientRegistration.clientId.trim(),
    );
  }
}
