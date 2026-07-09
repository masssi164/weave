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
    final configuration = await _loadConfiguration();
    final serverName = configuration.serviceEndpoints.matrixHomeserverUrl.host;
    try {
      final projection = await _rustMatrixCoreBridge.parseSync(
        responseJson: await _matrixRequestBody(
          'GET',
          '/_matrix/client/v3/sync',
          configuration: configuration,
        ),
        serverName: serverName,
      );
      final conversations = projection.rooms
          .map((room) {
            final latest = room.messages.isEmpty ? null : room.messages.last;
            return ChatConversation(
              id: room.roomId,
              title: room.title.isEmpty
                  ? _titleFromRoomId(room.roomId)
                  : room.title,
              previewType: _previewType(latest),
              previewText: latest?.contentType == 'text' ? latest?.body : null,
              lastActivityAt: latest == null
                  ? null
                  : DateTime.fromMillisecondsSinceEpoch(
                      latest.originServerTimestamp,
                      isUtc: true,
                    ),
              unreadCount: room.unreadCount,
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
        return unreadComparison != 0
            ? unreadComparison
            : a.title.toLowerCase().compareTo(b.title.toLowerCase());
      });
      return conversations;
    } on RustMatrixCoreBridgeException {
      throw const ChatFailure.protocol(
        'Weave Chat returned a Matrix response that could not be validated.',
      );
    }
  }

  @override
  Future<ChatRoomTimeline> loadRoomTimeline(String roomId) async {
    final configuration = await _loadConfiguration();
    try {
      final projection = await _rustMatrixCoreBridge.parseMessages(
        responseJson: await _matrixRequestBody(
          'GET',
          '/_matrix/client/v3/rooms/${Uri.encodeComponent(roomId)}/messages',
          configuration: configuration,
        ),
        serverName: configuration.serviceEndpoints.matrixHomeserverUrl.host,
      );
      return ChatRoomTimeline(
        roomId: roomId,
        roomTitle: _titleFromRoomId(roomId),
        isInvite: false,
        canSendMessages: true,
        messages: projection
            .map(_messageFromProjection)
            .toList(growable: false),
      );
    } on RustMatrixCoreBridgeException {
      throw const ChatFailure.protocol(
        'Weave Chat returned a Matrix timeline that could not be validated.',
      );
    }
  }

  @override
  Future<void> sendMessage({
    required String roomId,
    required String message,
  }) async {
    final configuration = await _loadConfiguration();
    try {
      final body = await _rustMatrixCoreBridge.serializeTextMessage(
        body: message,
        serverName: configuration.serviceEndpoints.matrixHomeserverUrl.host,
      );
      final transactionId =
          'weave-${DateTime.now().toUtc().microsecondsSinceEpoch}';
      await _matrixRequestBody(
        'PUT',
        '/_matrix/client/v3/rooms/${Uri.encodeComponent(roomId)}/send/m.room.message/$transactionId',
        configuration: configuration,
        body: body,
      );
    } on RustMatrixCoreBridgeException {
      throw const ChatFailure.configuration(
        'Write a message before sending it through Weave Chat.',
      );
    }
  }

  @override
  Future<void> markRoomRead(String roomId) async {
    // Read receipts stay unavailable until the canonical port and Rust core own them.
  }

  @override
  Future<void> connect() async {
    final configuration = await _loadConfiguration();
    final serverName = configuration.serviceEndpoints.matrixHomeserverUrl.host;
    try {
      final descriptor = await _rustMatrixCoreBridge.descriptor(
        serverName: serverName,
      );
      if (!descriptor.isWeaveFacade) {
        throw const RustMatrixCoreBridgeException('M_WEAVE_MATRIX_CORE_ERROR');
      }
      await _rustMatrixCoreBridge.validateVersions(
        responseJson: await _matrixRequestBody(
          'GET',
          '/_matrix/client/versions',
          configuration: configuration,
        ),
        serverName: serverName,
      );
    } on RustMatrixCoreBridgeException {
      throw const ChatFailure.configuration(
        'The Matrix facade is not compatible with this Weave client.',
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

  Future<String> _matrixRequestBody(
    String method,
    String path, {
    required ServerConfiguration configuration,
    String? body,
  }) async {
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
      request.body = body;
    }

    final response = await http.Response.fromStream(
      await _httpClient.send(request),
    );
    if (response.statusCode < 200 || response.statusCode >= 300) {
      throw const ChatFailure.protocol(
        'Weave Chat is unavailable. Ask an admin to inspect Workspace Health.',
      );
    }
    return response.body;
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

  ChatMessage _messageFromProjection(RustMatrixMessageProjection projection) {
    final contentType = switch (projection.contentType) {
      'text' => ChatMessageContentType.text,
      'encrypted' => ChatMessageContentType.encrypted,
      _ => ChatMessageContentType.unsupported,
    };
    return ChatMessage(
      id: projection.eventId,
      senderId: projection.sender,
      senderDisplayName: _displayName(projection.sender),
      sentAt: DateTime.fromMillisecondsSinceEpoch(
        projection.originServerTimestamp,
        isUtc: true,
      ),
      isMine: false,
      deliveryState: ChatMessageDeliveryState.sent,
      contentType: contentType,
      text: contentType == ChatMessageContentType.text ? projection.body : null,
    );
  }

  ChatConversationPreviewType _previewType(
    RustMatrixMessageProjection? message,
  ) {
    return switch (message?.contentType) {
      null => ChatConversationPreviewType.none,
      'text' => ChatConversationPreviewType.text,
      'encrypted' => ChatConversationPreviewType.encrypted,
      _ => ChatConversationPreviewType.unsupported,
    };
  }

  String _titleFromRoomId(String roomId) {
    var title = roomId.startsWith('!') ? roomId.substring(1) : roomId;
    final separator = title.indexOf(':');
    if (separator >= 0) {
      title = title.substring(0, separator);
    }
    return title.isEmpty ? 'Weave Chat' : title;
  }

  String _displayName(String sender) {
    var value = sender.startsWith('@') ? sender.substring(1) : sender;
    final separator = value.indexOf(':');
    if (separator >= 0) {
      value = value.substring(0, separator);
    }
    return value.replaceAll('_', ' ');
  }
}
