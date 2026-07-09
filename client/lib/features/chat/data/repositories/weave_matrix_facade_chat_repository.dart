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
import 'package:weave/integrations/rust_matrix_core/data/services/rust_matrix_core_bridge.dart';

class WeaveMatrixFacadeChatRepository implements ChatRepository {
  const WeaveMatrixFacadeChatRepository({
    required ServerConfigurationRepository serverConfigurationRepository,
    required AuthSessionRepository authSessionRepository,
    required http.Client httpClient,
    RustMatrixCoreBridge rustMatrixCoreBridge = const RustMatrixCoreBridge(),
  }) : _serverConfigurationRepository = serverConfigurationRepository,
       _authSessionRepository = authSessionRepository,
       _httpClient = httpClient,
       _rustMatrixCoreBridge = rustMatrixCoreBridge;

  final ServerConfigurationRepository _serverConfigurationRepository;
  final AuthSessionRepository _authSessionRepository;
  final http.Client _httpClient;
  final RustMatrixCoreBridge _rustMatrixCoreBridge;

  @override
  Future<List<ChatConversation>> loadConversations() async {
    final payload = await _getMatrixJson('/_matrix/client/v3/sync');
    final join = _joinedRooms(payload);
    final conversations = join.entries
        .map((entry) {
          final room = _asMap(entry.value);
          final events = _roomTimelineEvents(room);
          final latest = _latestMessageEvent(events);
          final content = _asMap(latest?['content']);
          final previewText = _string(content['body']);
          final lastActivityAt = _eventInstant(latest);
          return ChatConversation(
            id: entry.key,
            title: _roomTitle(entry.key, room),
            previewType: _previewType(latest),
            previewText: previewText == null || previewText.isEmpty
                ? null
                : previewText,
            lastActivityAt: lastActivityAt,
            unreadCount: _intValue(
              _asMap(room['unread_notifications'])['notification_count'],
            ),
            isInvite: false,
            isDirectMessage: false,
          );
        })
        .toList(growable: false);

    conversations.sort((a, b) {
      final activityComparison =
          (b.lastActivityAt ?? DateTime.fromMillisecondsSinceEpoch(0))
              .compareTo(
                a.lastActivityAt ?? DateTime.fromMillisecondsSinceEpoch(0),
              );
      if (activityComparison != 0) {
        return activityComparison;
      }

      final unreadComparison = b.unreadCount.compareTo(a.unreadCount);
      if (unreadComparison != 0) {
        return unreadComparison;
      }

      return a.title.toLowerCase().compareTo(b.title.toLowerCase());
    });

    return conversations;
  }

  @override
  Future<ChatRoomTimeline> loadRoomTimeline(String roomId) async {
    final payload = await _getMatrixJson(
      '/_matrix/client/v3/rooms/${Uri.encodeComponent(roomId)}/messages',
    );
    final events = _asList(payload['chunk']).whereType<Object?>();
    final messages = events
        .map(_asMap)
        .where((event) => event.isNotEmpty)
        .map(_messageFromEvent)
        .toList(growable: false);

    return ChatRoomTimeline(
      roomId: roomId,
      roomTitle: _titleFromRoomId(roomId),
      isInvite: false,
      canSendMessages: true,
      messages: messages,
    );
  }

  @override
  Future<void> sendMessage({
    required String roomId,
    required String message,
  }) async {
    final trimmed = message.trim();
    if (trimmed.isEmpty) {
      throw const ChatFailure.configuration(
        'Write a message before sending it through Weave Chat.',
      );
    }
    final txnId = 'weave-${DateTime.now().toUtc().microsecondsSinceEpoch}';
    await _matrixRequestJson(
      'PUT',
      '/_matrix/client/v3/rooms/${Uri.encodeComponent(roomId)}/send/m.room.message/$txnId',
      body: <String, Object?>{'msgtype': 'm.text', 'body': trimmed},
    );
  }

  @override
  Future<void> markRoomRead(String roomId) async {
    // Read receipts are not in the current Weave Matrix facade slice.
    // Keep this support-safe until the Rust Matrix core owns receipt semantics.
  }

  @override
  Future<void> connect() async {
    final configuration = await _loadConfiguration();
    final descriptor = await _rustMatrixCoreBridge.descriptor(
      serverName: configuration.serviceEndpoints.matrixHomeserverUrl.host,
    );
    if (!descriptor.isWeaveFacade) {
      throw const ChatFailure.configuration(
        'The Rust Matrix core bridge is not configured for the Weave facade.',
      );
    }
    final versions = await _getMatrixJson('/_matrix/client/versions');
    final matrixCore = _asMap(versions['matrixCore']);
    if (matrixCore['flutterBridgeBoundary'] != 'flutter-rust-bridge' ||
        matrixCore['northboundHomeserverDependency'] != false) {
      throw const ChatFailure.configuration(
        'The Matrix facade did not advertise the Rust bridge boundary.',
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

  Future<Map<String, dynamic>> _getMatrixJson(String path) {
    return _matrixRequestJson('GET', path);
  }

  Future<Map<String, dynamic>> _matrixRequestJson(
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
      throw const ChatFailure.sessionRequired(
        'Sign in before opening Weave Chat.',
      );
    }

    final request =
        http.Request(
            method,
            configuration.serviceEndpoints.matrixHomeserverUrl.resolve(path),
          )
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
    if (decoded is Map) {
      return Map<String, dynamic>.from(decoded);
    }
    throw const ChatFailure.protocol(
      'The Weave Matrix facade returned an invalid response.',
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

  Map<String, dynamic> _joinedRooms(Map<String, dynamic> payload) {
    return _asMap(_asMap(payload['rooms'])['join']);
  }

  List<Map<String, dynamic>> _roomTimelineEvents(Map<String, dynamic> room) {
    return _asList(
      _asMap(room['timeline'])['events'],
    ).map(_asMap).where((event) => event.isNotEmpty).toList(growable: false);
  }

  Map<String, dynamic>? _latestMessageEvent(List<Map<String, dynamic>> events) {
    for (final event in events.reversed) {
      if (event['type'] == 'm.room.message' ||
          event['type'] == 'm.room.encrypted') {
        return event;
      }
    }
    return null;
  }

  ChatMessage _messageFromEvent(Map<String, dynamic> event) {
    final content = _asMap(event['content']);
    final eventId = _string(event['event_id']) ?? '';
    final sender = _string(event['sender']) ?? '@weave:weave.local';
    final body = _string(content['body']);
    final contentType = _messageContentType(event);
    return ChatMessage(
      id: eventId.isEmpty ? 'weave-event' : eventId,
      senderId: sender,
      senderDisplayName: _displayName(sender),
      sentAt: _eventInstant(event) ?? DateTime.fromMillisecondsSinceEpoch(0),
      isMine: false,
      deliveryState: ChatMessageDeliveryState.sent,
      contentType: contentType,
      text: contentType == ChatMessageContentType.text ? body : null,
    );
  }

  ChatConversationPreviewType _previewType(Map<String, dynamic>? event) {
    if (event == null) {
      return ChatConversationPreviewType.none;
    }
    return switch (_messageContentType(event)) {
      ChatMessageContentType.text => ChatConversationPreviewType.text,
      ChatMessageContentType.encrypted => ChatConversationPreviewType.encrypted,
      ChatMessageContentType.unsupported =>
        ChatConversationPreviewType.unsupported,
    };
  }

  ChatMessageContentType _messageContentType(Map<String, dynamic> event) {
    final type = _string(event['type']);
    final content = _asMap(event['content']);
    if (type == 'm.room.encrypted') {
      return ChatMessageContentType.encrypted;
    }
    if (type == 'm.room.message' && content['msgtype'] == 'm.text') {
      return ChatMessageContentType.text;
    }
    return ChatMessageContentType.unsupported;
  }

  String _roomTitle(String roomId, Map<String, dynamic> room) {
    final stateEvents = _asList(_asMap(room['state'])['events']).map(_asMap);
    for (final event in stateEvents) {
      if (event['type'] == 'm.room.name') {
        final name = _string(_asMap(event['content'])['name']);
        if (name != null && name.trim().isNotEmpty) {
          return name.trim();
        }
      }
    }
    return _titleFromRoomId(roomId);
  }

  String _titleFromRoomId(String roomId) {
    var title = roomId;
    if (title.startsWith('!')) {
      title = title.substring(1);
    }
    final serverSeparator = title.indexOf(':');
    if (serverSeparator >= 0) {
      title = title.substring(0, serverSeparator);
    }
    return title.isEmpty ? 'Weave Chat' : title;
  }

  String _displayName(String sender) {
    var value = sender;
    if (value.startsWith('@')) {
      value = value.substring(1);
    }
    final separator = value.indexOf(':');
    if (separator >= 0) {
      value = value.substring(0, separator);
    }
    return value.replaceAll('_', ' ');
  }

  DateTime? _eventInstant(Map<String, dynamic>? event) {
    if (event == null) {
      return null;
    }
    final raw = event['origin_server_ts'];
    if (raw is int) {
      return DateTime.fromMillisecondsSinceEpoch(raw, isUtc: true);
    }
    if (raw is num) {
      return DateTime.fromMillisecondsSinceEpoch(raw.toInt(), isUtc: true);
    }
    return null;
  }

  int _intValue(Object? value) {
    if (value is int) {
      return value;
    }
    if (value is num) {
      return value.toInt();
    }
    return 0;
  }

  String? _string(Object? value) => value is String ? value : null;

  Map<String, dynamic> _asMap(Object? value) {
    if (value is Map<String, dynamic>) {
      return value;
    }
    if (value is Map) {
      return Map<String, dynamic>.from(value);
    }
    return <String, dynamic>{};
  }

  List<Object?> _asList(Object? value) {
    if (value is List<Object?>) {
      return value;
    }
    if (value is List) {
      return List<Object?>.from(value);
    }
    return const <Object?>[];
  }
}
