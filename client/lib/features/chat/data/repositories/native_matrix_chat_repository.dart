import 'package:weave/features/chat/domain/entities/chat_conversation.dart';
import 'package:weave/features/chat/domain/entities/chat_failure.dart';
import 'package:weave/features/chat/domain/entities/chat_message.dart';
import 'package:weave/features/chat/domain/entities/chat_room_timeline.dart';
import 'package:weave/features/chat/domain/repositories/chat_repository.dart';
import 'package:weave/integrations/rust_matrix_core/data/services/matrix_crypto_session_coordinator.dart';
import 'package:weave/integrations/rust_matrix_core/data/services/rust_matrix_core_bridge.dart';

class NativeMatrixChatRepository implements ChatRepository {
  NativeMatrixChatRepository({
    required MatrixCryptoSessionPort matrixCryptoSessionCoordinator,
    RustMatrixCoreBridge rustMatrixCoreBridge = const RustMatrixCoreBridge(),
  }) : _matrixCryptoSessionCoordinator = matrixCryptoSessionCoordinator,
       _rustMatrixCoreBridge = rustMatrixCoreBridge;

  final MatrixCryptoSessionPort _matrixCryptoSessionCoordinator;
  final RustMatrixCoreBridge _rustMatrixCoreBridge;
  final Map<String, String> _latestEventByRoom = <String, String>{};

  @override
  Future<List<ChatConversation>> loadConversations() async {
    try {
      final session = await _matrixCryptoSessionCoordinator.open();
      final rooms = await _rustMatrixCoreBridge.loadRooms(
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
                  : ChatConversationPreviewType.none,
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
    } on RustMatrixCoreBridgeException catch (error) {
      if (isMatrixSessionExpiredCode(error.code)) {
        throw ChatFailure.sessionRequired(
          'Chat authorization expired. Retry with your Weave sign-in.',
          cause: error,
        );
      }
      throw ChatFailure.protocol(
        'Weave Chat could not load conversations.',
        cause: error,
      );
    }
  }

  @override
  Future<ChatConversation> createConversation({required String title}) async {
    final normalizedTitle = title.trim();
    if (normalizedTitle.isEmpty || normalizedTitle.runes.length > 200) {
      throw const ChatFailure.configuration(
        'Give the conversation a name between 1 and 200 characters.',
      );
    }
    try {
      final session = await _matrixCryptoSessionCoordinator.open(
        synchronize: false,
      );
      final room = await _rustMatrixCoreBridge.createBusinessRoom(
        profileKey: session.profileKey,
        title: normalizedTitle,
      );
      if (room.roomId.isEmpty || room.encrypted) {
        throw const RustMatrixCoreBridgeException(
          'M_WEAVE_CHAT_CREATE_ROOM_INVALID',
        );
      }
      return ChatConversation(
        id: room.roomId,
        title: room.title.isEmpty ? normalizedTitle : room.title,
        previewType: ChatConversationPreviewType.none,
        unreadCount: room.unreadCount,
        isInvite: false,
        isDirectMessage: false,
      );
    } on RustMatrixCoreBridgeException catch (error) {
      if (isMatrixSessionExpiredCode(error.code)) {
        throw ChatFailure.sessionRequired(
          'Chat authorization expired. Retry with your Weave sign-in.',
          cause: error,
        );
      }
      if (error.code == 'M_INVALID_PARAM') {
        throw const ChatFailure.configuration(
          'Give the conversation a name between 1 and 200 characters.',
        );
      }
      throw ChatFailure.protocol(
        'Weave Chat could not create this conversation.',
        cause: error,
      );
    }
  }

  @override
  Future<ChatRoomTimeline> loadRoomTimeline(String roomId) async {
    try {
      // The native timeline read owns sync, to-device processing, cursor
      // acknowledgement, and message decryption as one receive transaction.
      final session = await _matrixCryptoSessionCoordinator.open(
        synchronize: false,
      );
      final projection = await _rustMatrixCoreBridge.loadRoomMessages(
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
    } on RustMatrixCoreBridgeException catch (error) {
      if (isMatrixSessionExpiredCode(error.code)) {
        throw ChatFailure.sessionRequired(
          'Chat authorization expired. Retry with your Weave sign-in.',
          cause: error,
        );
      }
      throw ChatFailure.protocol(
        'Weave Chat could not load this timeline.',
        cause: error,
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
      await _rustMatrixCoreBridge.sendText(
        profileKey: session.profileKey,
        roomId: roomId,
        body: message,
      );
    } on RustMatrixCoreBridgeException catch (error) {
      if (isMatrixSessionExpiredCode(error.code)) {
        throw ChatFailure.sessionRequired(
          'Chat authorization expired. Retry with your Weave sign-in.',
          cause: error,
        );
      }
      if (error.code == 'M_INVALID_PARAM') {
        throw const ChatFailure.configuration(
          'Write a message before sending it through Weave Chat.',
        );
      }
      if (error.code == 'M_WEAVE_E2EE_PEER_DEVICE_PENDING') {
        throw ChatFailure.peerDevicePending(
          'Waiting for a participant’s secure device. Try again shortly or contact support if this continues.',
          cause: error,
        );
      }
      throw ChatFailure.protocol(
        'Weave Chat could not send this message.',
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
    try {
      final session = await _matrixCryptoSessionCoordinator.open(
        synchronize: false,
      );
      await _rustMatrixCoreBridge.markRead(
        profileKey: session.profileKey,
        roomId: roomId,
        eventId: eventId,
      );
    } on RustMatrixCoreBridgeException catch (error) {
      if (isMatrixSessionExpiredCode(error.code)) {
        throw ChatFailure.sessionRequired(
          'Chat authorization expired. Retry with your Weave sign-in.',
          cause: error,
        );
      }
      rethrow;
    }
  }

  @override
  Future<void> connect() async {
    try {
      await _matrixCryptoSessionCoordinator.open(allowInteractiveSignIn: true);
    } on RustMatrixCoreBridgeException catch (error) {
      throw ChatFailure.configuration(
        'The Weave Matrix endpoint could not establish an encrypted client session.',
        cause: error,
      );
    }
  }

  @override
  Future<void> signOut() async {
    await _matrixCryptoSessionCoordinator.endSession();
  }

  @override
  Future<void> clearSession() async {
    await _matrixCryptoSessionCoordinator.disposePreservingCryptoState();
  }

  ChatMessage _messageFromProjection(
    RustMatrixMessageProjection projection, {
    required String currentUserId,
  }) {
    final isText =
        projection.contentType == 'encryptedText' ||
        projection.contentType == 'text';
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
      contentType: isText
          ? ChatMessageContentType.text
          : ChatMessageContentType.unsupported,
      text: isText ? projection.body : null,
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
