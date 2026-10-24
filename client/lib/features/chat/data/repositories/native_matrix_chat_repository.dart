import 'package:weave/features/chat/domain/entities/chat_participant.dart';
import 'package:weave/features/chat/domain/entities/chat_conversation.dart';
import 'package:weave/features/chat/domain/entities/chat_failure.dart';
import 'package:weave/features/chat/domain/entities/chat_message.dart';
import 'package:weave/features/chat/domain/entities/chat_room_timeline.dart';
import 'package:weave/features/chat/domain/repositories/chat_repository.dart';
import 'package:weave/integrations/rust_matrix_core/data/services/matrix_crypto_session_coordinator.dart';
import 'package:weave/integrations/rust_matrix_core/data/services/chat_operation_diagnostics.dart';
import 'package:weave/integrations/rust_matrix_core/data/services/rust_matrix_core_bridge.dart';

class NativeMatrixChatRepository
    implements
        ChatRepository,
        ChatParticipantRepository,
        ChatInvitationRepository {
  NativeMatrixChatRepository({
    required MatrixCryptoSessionPort matrixCryptoSessionCoordinator,
    RustMatrixCoreBridge rustMatrixCoreBridge = const RustMatrixCoreBridge(),
  }) : _matrixCryptoSessionCoordinator = matrixCryptoSessionCoordinator,
       _rustMatrixCoreBridge = rustMatrixCoreBridge;

  final MatrixCryptoSessionPort _matrixCryptoSessionCoordinator;
  final RustMatrixCoreBridge _rustMatrixCoreBridge;
  final Map<String, String> _latestEventByRoom = <String, String>{};

  @override
  Future<void> acceptInvitation(String roomId) => ChatOperationDiagnostics.run(
    ChatOperation.invitation,
    () => _acceptInvitation(roomId),
  );
  @override
  Future<List<ChatConversation>> loadConversations() =>
      ChatOperationDiagnostics.run(
        ChatOperation.conversations,
        _loadConversations,
      );
  @override
  Future<List<ChatParticipant>> searchMembers(
    String query, {
    String? spaceRef,
  }) => ChatOperationDiagnostics.run(
    ChatOperation.search,
    () => _searchMembers(query, spaceRef: spaceRef),
  );
  @override
  Future<ChatRoomTimeline> loadRoomTimeline(String roomId) =>
      ChatOperationDiagnostics.run(
        ChatOperation.timeline,
        () => _loadRoomTimeline(roomId),
      );
  @override
  Future<void> sendMessage({required String roomId, required String message}) =>
      ChatOperationDiagnostics.run(
        ChatOperation.send,
        () => _sendMessage(roomId: roomId, message: message),
      );
  @override
  Future<void> markRoomRead(String roomId) => ChatOperationDiagnostics.run(
    ChatOperation.read,
    () => _markRoomRead(roomId),
  );
  @override
  Future<void> connect() =>
      ChatOperationDiagnostics.run(ChatOperation.connect, _connect);
  @override
  Future<void> signOut() =>
      ChatOperationDiagnostics.run(ChatOperation.signOut, _signOut);
  @override
  Future<void> clearSession() =>
      ChatOperationDiagnostics.run(ChatOperation.clearSession, _clearSession);

  Future<ChatConversation> _createConversation({
    required String title,
    required List<ChatParticipant> participants,
    bool isDirect = false,
    String? spaceRef,
  }) => ChatOperationDiagnostics.run(
    ChatOperation.create,
    () => _createConversationInternal(
      title: title,
      participants: participants,
      isDirect: isDirect,
      spaceRef: spaceRef,
    ),
  );

  Future<void> _acceptInvitation(String roomId) async {
    try {
      final session = await _matrixCryptoSessionCoordinator.open();
      ChatOperationDiagnostics.mark(ChatStage.invitation);
      await _rustMatrixCoreBridge.acceptRoomInvitation(
        profileKey: session.profileKey,
        roomId: roomId,
      );
    } on RustMatrixCoreBridgeException catch (error) {
      if (isMatrixSessionExpiredCode(error.code)) {
        throw ChatFailure.sessionRequired(
          'Sign in again before accepting the invitation.',
          cause: error,
        );
      }
      throw ChatFailure.protocol(
        'The invitation could not be accepted.',
        cause: error,
      );
    }
  }

  Future<List<ChatConversation>> _loadConversations() async {
    try {
      final session = await _matrixCryptoSessionCoordinator.open();
      ChatOperationDiagnostics.mark(ChatStage.roomList);
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
              isInvite: room.isInvite,
              isDirectMessage: room.isDirect,
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
      if (isMatrixSessionExpiredCode(error.code) ||
          error.code == 'M_FORBIDDEN') {
        throw ChatFailure.sessionRequired(
          'Chat access is no longer current. Retry with your Weave sign-in.',
          cause: error,
        );
      }
      throw ChatFailure.protocol(
        'Weave Chat could not load conversations.',
        cause: error,
      );
    }
  }

  Future<List<ChatParticipant>> _searchMembers(
    String query, {
    String? spaceRef,
  }) async {
    final normalized = query.trim();
    if (normalized.isEmpty || normalized.runes.length > 200) return [];
    try {
      final session = await _matrixCryptoSessionCoordinator.open(
        synchronize: false,
      );
      ChatOperationDiagnostics.mark(ChatStage.search);
      final members = await _rustMatrixCoreBridge.searchMembers(
        profileKey: session.profileKey,
        query: normalized,
        spaceRef: spaceRef,
      );
      return members
          .where((member) => member.userId.isNotEmpty)
          .map(
            (member) => ChatParticipant(
              userId: member.userId,
              displayName: member.displayName.isEmpty
                  ? member.userId
                  : member.displayName,
            ),
          )
          .toList(growable: false);
    } on RustMatrixCoreBridgeException catch (error) {
      if (error.code == 'M_WEAVE_CHAT_SPACE_SCOPE_UNSUPPORTED') {
        throw ChatFailure.unsupportedConfiguration(error.code, cause: error);
      }
      if (isMatrixSessionExpiredCode(error.code) ||
          error.code == 'M_FORBIDDEN') {
        throw ChatFailure.sessionRequired(
          'Chat access is no longer current. Retry with your Weave sign-in.',
          cause: error,
        );
      }
      throw ChatFailure.protocol(
        'Weave Chat could not find members.',
        cause: error,
      );
    }
  }

  @override
  Future<ChatConversation> createConversationWithParticipants({
    required String title,
    required List<ChatParticipant> participants,
    bool isDirect = false,
    String? spaceRef,
  }) => _createConversation(
    title: title,
    participants: participants,
    isDirect: isDirect,
    spaceRef: spaceRef,
  );

  @override
  Future<ChatConversation> createConversation({
    required String title,
    String? spaceRef,
  }) => _createConversation(
    title: title,
    participants: const [],
    spaceRef: spaceRef,
  );

  Future<ChatConversation> _createConversationInternal({
    required String title,
    required List<ChatParticipant> participants,
    bool isDirect = false,
    String? spaceRef,
  }) async {
    if (isDirect && participants.length != 1) {
      throw const ChatFailure.configuration(
        'Choose exactly one member for a direct conversation.',
      );
    }
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
      ChatOperationDiagnostics.mark(ChatStage.create);
      final room = participants.isEmpty
          ? await _rustMatrixCoreBridge.createBusinessRoom(
              profileKey: session.profileKey,
              title: normalizedTitle,
              spaceRef: spaceRef,
            )
          : await _rustMatrixCoreBridge.createBusinessRoomWithMembers(
              profileKey: session.profileKey,
              title: normalizedTitle,
              isDirect: isDirect,
              spaceRef: spaceRef,
              memberIds: participants
                  .map((member) => member.userId)
                  .toList(growable: false),
            );
      if (room.roomId.isEmpty || room.encrypted || room.isDirect != isDirect) {
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
        isDirectMessage: room.isDirect,
      );
    } on RustMatrixCoreBridgeException catch (error) {
      if (error.code == 'M_WEAVE_CHAT_SPACE_SCOPE_UNSUPPORTED') {
        throw ChatFailure.unsupportedConfiguration(error.code, cause: error);
      }
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

  Future<ChatRoomTimeline> _loadRoomTimeline(String roomId) async {
    try {
      // The native timeline read owns sync, to-device processing, cursor
      // acknowledgement, and message decryption as one receive transaction.
      final session = await _matrixCryptoSessionCoordinator.open(
        synchronize: false,
      );
      ChatOperationDiagnostics.mark(ChatStage.timeline);
      final projection = await _rustMatrixCoreBridge.loadRoomMessages(
        profileKey: session.profileKey,
        roomId: roomId,
      );
      if (projection.isNotEmpty) {
        _latestEventByRoom[roomId] = projection.last.eventId;
      }
      return ChatRoomTimeline(
        roomId: roomId,
        // Message reads do not carry room metadata. Preserve the room-list title.
        roomTitle: '',
        isInvite: false,
        canSendMessages: true,
        memberStorageScope: session.memberStorageScope,
        messages: projection
            .map(
              (message) => _messageFromProjection(
                message,
                currentUserId: session.userId,
              ),
            )
            .toList(growable: false),
      );
    } on ChatFailure {
      rethrow;
    } on RustMatrixCoreBridgeException catch (error) {
      if (isMatrixSessionExpiredCode(error.code) ||
          error.code == 'M_FORBIDDEN' ||
          error.code == 'M_NOT_FOUND' ||
          error.code == 'M_WEAVE_CHAT_JOIN_REQUIRED') {
        throw ChatFailure.sessionRequired(
          'Chat room access is no longer current. Retry with your Weave sign-in.',
          cause: error,
        );
      }
      throw ChatFailure.protocol(
        'Weave Chat could not load this timeline.',
        cause: error,
      );
    }
  }

  Future<void> _sendMessage({
    required String roomId,
    required String message,
  }) async {
    try {
      final session = await _matrixCryptoSessionCoordinator.open(
        synchronize: false,
      );
      ChatOperationDiagnostics.mark(ChatStage.send);
      await _rustMatrixCoreBridge.sendText(
        profileKey: session.profileKey,
        roomId: roomId,
        body: message,
      );
    } on ChatFailure {
      rethrow;
    } on RustMatrixCoreBridgeException catch (error) {
      if (isMatrixSessionExpiredCode(error.code) ||
          error.code == 'M_FORBIDDEN') {
        throw ChatFailure.sessionRequired(
          'Chat room access is no longer current. Retry with your Weave sign-in.',
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

  Future<void> _markRoomRead(String roomId) async {
    final eventId = _latestEventByRoom[roomId];
    if (eventId == null) {
      return;
    }
    try {
      final session = await _matrixCryptoSessionCoordinator.open(
        synchronize: false,
      );
      ChatOperationDiagnostics.mark(ChatStage.read);
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

  Future<void> _connect() async {
    try {
      await _matrixCryptoSessionCoordinator.open(allowInteractiveSignIn: true);
    } on RustMatrixCoreBridgeException catch (error) {
      throw ChatFailure.protocol(
        'The Weave Matrix endpoint could not establish a client session.',
        cause: error,
      );
    }
  }

  Future<void> _signOut() async {
    ChatOperationDiagnostics.mark(ChatStage.cleanup);
    await _matrixCryptoSessionCoordinator.endSession();
  }

  Future<void> _clearSession() async {
    ChatOperationDiagnostics.mark(ChatStage.cleanup);
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
      senderDisplayName: projection.senderDisplayName?.trim().isNotEmpty == true
          ? projection.senderDisplayName!.trim()
          : _displayName(projection.sender),
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
