import 'package:weave/features/auth/domain/entities/auth_configuration.dart';
import 'package:weave/features/auth/domain/repositories/auth_session_repository.dart';
import 'package:weave/features/chat/domain/entities/chat_conversation.dart';
import 'package:weave/features/chat/domain/entities/chat_failure.dart';
import 'package:weave/features/chat/domain/entities/chat_message.dart';
import 'package:weave/features/chat/domain/entities/chat_room_timeline.dart';
import 'package:weave/features/chat/domain/repositories/chat_repository.dart';
import 'package:weave/features/server_config/domain/entities/server_configuration.dart';
import 'package:weave/features/server_config/domain/repositories/server_configuration_repository.dart';
import 'package:weave/integrations/rust_matrix_core/data/services/matrix_crypto_session_coordinator.dart';
import 'package:weave/integrations/rust_matrix_core/data/services/rust_matrix_core_bridge.dart';

class WeaveMatrixFacadeChatRepository implements ChatRepository {
  WeaveMatrixFacadeChatRepository({
    required ServerConfigurationRepository serverConfigurationRepository,
    required AuthSessionRepository authSessionRepository,
    required MatrixCryptoSessionPort matrixCryptoSessionCoordinator,
    RustMatrixCoreBridge rustMatrixCoreBridge = const RustMatrixCoreBridge(),
  }) : _serverConfigurationRepository = serverConfigurationRepository,
       _authSessionRepository = authSessionRepository,
       _matrixCryptoSessionCoordinator = matrixCryptoSessionCoordinator,
       _rustMatrixCoreBridge = rustMatrixCoreBridge;

  final ServerConfigurationRepository _serverConfigurationRepository;
  final AuthSessionRepository _authSessionRepository;
  final MatrixCryptoSessionPort _matrixCryptoSessionCoordinator;
  final RustMatrixCoreBridge _rustMatrixCoreBridge;
  final Map<String, String> _latestEventByRoom = <String, String>{};

  @override
  Future<List<ChatConversation>> loadConversations() async {
    try {
      final session = await _matrixCryptoSessionCoordinator.open();
      final rooms = await _rustMatrixCoreBridge.loadEncryptedRooms(
        profileKey: session.profileKey,
      );
      final conversations = rooms
          .map(
            (room) => ChatConversation(
              id: room.roomId,
              title: room.title.isEmpty
                  ? _titleFromRoomId(room.roomId)
                  : room.title,
              previewType: room.encrypted
                  ? ChatConversationPreviewType.encrypted
                  : ChatConversationPreviewType.unsupported,
              unreadCount: room.unreadCount,
              isInvite: false,
              isDirectMessage: false,
            ),
          )
          .toList(growable: false);
      conversations.sort((left, right) {
        final unread = right.unreadCount.compareTo(left.unreadCount);
        return unread != 0
            ? unread
            : left.title.toLowerCase().compareTo(right.title.toLowerCase());
      });
      return conversations;
    } on RustMatrixCoreBridgeException {
      throw const ChatFailure.protocol(
        'Weave Chat could not establish the encrypted timeline.',
      );
    }
  }

  @override
  Future<ChatRoomTimeline> loadRoomTimeline(String roomId) async {
    try {
      final session = await _matrixCryptoSessionCoordinator.open();
      final projection = await _rustMatrixCoreBridge.loadEncryptedRoomMessages(
        profileKey: session.profileKey,
        roomId: roomId,
      );
      if (projection.isNotEmpty) {
        _latestEventByRoom[roomId] = projection.last.eventId;
      }
      return ChatRoomTimeline(
        roomId: roomId,
        roomTitle: _titleFromRoomId(roomId),
        isInvite: false,
        canSendMessages: true,
        messages: projection
            .map(
              (message) => _messageFromProjection(
                message,
                currentUserId: session.userId,
              ),
            )
            .toList(growable: false),
      );
    } on RustMatrixCoreBridgeException {
      throw const ChatFailure.protocol(
        'Weave Chat could not decrypt this timeline.',
      );
    }
  }

  @override
  Future<void> sendMessage({
    required String roomId,
    required String message,
  }) async {
    try {
      final session = await _matrixCryptoSessionCoordinator.open(
        synchronize: false,
      );
      await _rustMatrixCoreBridge.sendEncryptedText(
        profileKey: session.profileKey,
        roomId: roomId,
        body: message,
      );
    } on RustMatrixCoreBridgeException catch (error) {
      if (error.code == 'M_INVALID_PARAM') {
        throw const ChatFailure.configuration(
          'Write a message before sending it through Weave Chat.',
        );
      }
      throw ChatFailure.protocol(
        'Weave Chat could not send this encrypted message.',
        cause: error,
      );
    }
  }

  @override
  Future<void> markRoomRead(String roomId) async {
    final eventId = _latestEventByRoom[roomId];
    if (eventId == null) {
      return;
    }
    final session = await _matrixCryptoSessionCoordinator.open(
      synchronize: false,
    );
    await _rustMatrixCoreBridge.markRead(
      profileKey: session.profileKey,
      roomId: roomId,
      eventId: eventId,
    );
  }

  @override
  Future<void> connect() async {
    final configuration = await _loadConfiguration();
    try {
      final descriptor = await _rustMatrixCoreBridge.descriptor(
        serverName: configuration.serviceEndpoints.matrixHomeserverUrl.host,
      );
      if (!descriptor.isWeaveFacade) {
        throw const RustMatrixCoreBridgeException('M_WEAVE_MATRIX_CORE_ERROR');
      }
      await _matrixCryptoSessionCoordinator.open();
    } on RustMatrixCoreBridgeException catch (error) {
      throw ChatFailure.configuration(
        'The encrypted Matrix facade is not compatible with this Weave client.',
        cause: error,
      );
    }
  }

  @override
  Future<void> signOut() async {
    final configuration = await _loadConfiguration();
    await _matrixCryptoSessionCoordinator.disposePreservingCryptoState();
    await _authSessionRepository.signOut(_authConfiguration(configuration));
  }

  @override
  Future<void> clearSession() async {
    await _matrixCryptoSessionCoordinator.disposePreservingCryptoState();
    await _authSessionRepository.clearLocalSession();
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

  ChatMessage _messageFromProjection(
    RustMatrixMessageProjection projection, {
    required String currentUserId,
  }) {
    final isDecrypted = projection.contentType == 'encryptedText';
    return ChatMessage(
      id: projection.eventId,
      senderId: projection.sender,
      senderDisplayName: _displayName(projection.sender),
      sentAt: DateTime.fromMillisecondsSinceEpoch(
        projection.originServerTimestamp,
        isUtc: true,
      ),
      isMine: projection.sender == currentUserId,
      deliveryState: ChatMessageDeliveryState.sent,
      contentType: isDecrypted
          ? ChatMessageContentType.text
          : ChatMessageContentType.unsupported,
      text: isDecrypted ? projection.body : null,
    );
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
