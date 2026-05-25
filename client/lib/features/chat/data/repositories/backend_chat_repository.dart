import 'dart:convert';

import 'package:http/http.dart' as http;
import 'package:weave/features/auth/domain/entities/auth_configuration.dart';
import 'package:weave/features/auth/domain/repositories/auth_session_repository.dart';
import 'package:weave/features/chat/domain/entities/chat_conversation.dart';
import 'package:weave/features/chat/domain/entities/chat_failure.dart';
import 'package:weave/features/chat/domain/entities/chat_message.dart';
import 'package:weave/features/chat/domain/entities/chat_room_timeline.dart';
import 'package:weave/features/chat/domain/repositories/chat_repository.dart';
import 'package:weave/features/server_config/domain/entities/server_configuration.dart';
import 'package:weave/features/server_config/domain/repositories/server_configuration_repository.dart';

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
    final conversations = payload['conversations'];
    if (conversations is! List<dynamic>) {
      throw const ChatFailure.configuration(
        'The Weave Chat facade returned an invalid conversation list.',
      );
    }
    final values = conversations
        .whereType<Map<String, dynamic>>()
        .map(_conversationFromJson)
        .toList(growable: false);
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
    final messages = payload['messages'];
    if (messages is! List<dynamic>) {
      throw const ChatFailure.configuration(
        'The Weave Chat facade returned an invalid timeline.',
      );
    }
    final parsedMessages = messages
        .whereType<Map<String, dynamic>>()
        .map(_messageFromJson)
        .toList(growable: false);
    return ChatRoomTimeline(
      roomId: roomId,
      roomTitle: roomId,
      isInvite: false,
      canSendMessages: true,
      messages: parsedMessages,
    );
  }

  @override
  Future<void> sendMessage({
    required String roomId,
    required String message,
  }) async {
    await _requestJson(
      'POST',
      '/api/chat/conversations/$roomId/messages',
      body: <String, Object?>{'text': message, 'attachmentRefs': <String>[]},
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
    final state = readiness['impactState'] ?? readiness['memberState'];
    if (state != 'usable' && state != 'ready') {
      throw ChatFailure.configuration(
        readiness['memberImpact'] is String
            ? readiness['memberImpact'] as String
            : 'Weave Chat is not ready in this workspace.',
      );
    }
  }

  @override
  Future<void> signOut() async {
    final configuration = await _loadConfiguration();
    await _authSessionRepository.signOut(_authConfiguration(configuration));
  }

  @override
  Future<void> clearSession() => _authSessionRepository.clearLocalSession();

  ChatConversation _conversationFromJson(Map<String, dynamic> json) {
    final id = _readString(json, 'id');
    final title = _readString(json, 'title');
    final kind = json['kind'] is String ? json['kind'] as String : 'channel';
    return ChatConversation(
      id: id,
      title: title,
      previewType: ChatConversationPreviewType.text,
      previewText: 'Weave Chat conversation',
      lastActivityAt: _readDateTime(json['lastMessageAt']),
      unreadCount: 0,
      isInvite: false,
      isDirectMessage: kind == 'direct',
      isAiChat: kind == 'ai',
    );
  }

  ChatMessage _messageFromJson(Map<String, dynamic> json) {
    final redacted = json['encryptedProviderContentRedacted'] == true;
    return ChatMessage(
      id: _readString(json, 'id'),
      senderId: _readString(json, 'senderRef'),
      senderDisplayName: _readString(json, 'senderRef'),
      sentAt:
          _readDateTime(json['sentAt']) ??
          DateTime.fromMillisecondsSinceEpoch(0),
      isMine: json['isMine'] == true,
      deliveryState: ChatMessageDeliveryState.sent,
      contentType: redacted
          ? ChatMessageContentType.encrypted
          : ChatMessageContentType.text,
      text: json['text'] is String ? json['text'] as String : null,
    );
  }

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

  String _readString(Map<String, dynamic> json, String key) {
    final value = json[key];
    if (value is String && value.trim().isNotEmpty) {
      return value.trim();
    }
    throw ChatFailure.configuration(
      'The Weave Chat facade returned an invalid "$key" value.',
    );
  }

  DateTime? _readDateTime(Object? value) {
    if (value is! String || value.trim().isEmpty) {
      return null;
    }
    return DateTime.tryParse(value)?.toLocal();
  }
}
