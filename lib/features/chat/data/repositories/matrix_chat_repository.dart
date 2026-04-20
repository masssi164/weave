import 'package:weave/features/chat/data/services/matrix_conversation_service.dart';
import 'package:weave/features/chat/data/services/matrix_room_service.dart';
import 'package:weave/features/chat/data/services/matrix_service_types.dart';
import 'package:weave/features/chat/data/services/matrix_session_service.dart';
import 'package:weave/features/chat/domain/entities/chat_conversation.dart';
import 'package:weave/features/chat/domain/entities/chat_failure.dart';
import 'package:weave/features/chat/domain/entities/chat_message.dart';
import 'package:weave/features/chat/domain/entities/chat_room_timeline.dart';
import 'package:weave/features/chat/domain/repositories/chat_repository.dart';
import 'package:weave/features/server_config/domain/repositories/server_configuration_repository.dart';

class MatrixChatRepository implements ChatRepository {
  const MatrixChatRepository({
    required MatrixSessionService sessionService,
    required MatrixConversationService conversationService,
    required MatrixRoomService roomService,
    required ServerConfigurationRepository serverConfigurationRepository,
  }) : _sessionService = sessionService,
       _conversationService = conversationService,
       _roomService = roomService,
       _serverConfigurationRepository = serverConfigurationRepository;

  final MatrixSessionService _sessionService;
  final MatrixConversationService _conversationService;
  final MatrixRoomService _roomService;
  final ServerConfigurationRepository _serverConfigurationRepository;

  @override
  Future<List<ChatConversation>> loadConversations() async {
    final homeserver = await _loadHomeserver();
    final rooms = await _conversationService.loadConversations(
      homeserver: homeserver,
    );

    return rooms
        .map(
          (room) => ChatConversation(
            id: room.id,
            title: room.title,
            previewType: switch (room.previewType) {
              MatrixRoomPreviewType.none => ChatConversationPreviewType.none,
              MatrixRoomPreviewType.text => ChatConversationPreviewType.text,
              MatrixRoomPreviewType.encrypted =>
                ChatConversationPreviewType.encrypted,
              MatrixRoomPreviewType.unsupported =>
                ChatConversationPreviewType.unsupported,
            },
            previewText: room.previewText,
            lastActivityAt: room.lastActivityAt,
            unreadCount: room.unreadCount,
            isInvite: room.isInvite,
            isDirectMessage: room.isDirectMessage,
          ),
        )
        .toList(growable: false);
  }

  @override
  Future<ChatRoomTimeline> loadRoomTimeline(String roomId) async {
    final homeserver = await _loadHomeserver();
    final timeline = await _roomService.loadRoomTimeline(
      homeserver: homeserver,
      roomId: roomId,
    );

    return ChatRoomTimeline(
      roomId: timeline.roomId,
      roomTitle: timeline.roomTitle,
      isInvite: timeline.isInvite,
      canSendMessages: timeline.canSendMessages,
      messages: timeline.messages
          .map(
            (message) => ChatMessage(
              id: message.id,
              senderId: message.senderId,
              senderDisplayName: message.senderDisplayName,
              sentAt: message.sentAt,
              isMine: message.isMine,
              deliveryState: switch (message.deliveryState) {
                MatrixMessageDeliveryState.sending =>
                  ChatMessageDeliveryState.sending,
                MatrixMessageDeliveryState.sent =>
                  ChatMessageDeliveryState.sent,
                MatrixMessageDeliveryState.failed =>
                  ChatMessageDeliveryState.failed,
              },
              contentType: switch (message.contentType) {
                MatrixMessageContentType.text => ChatMessageContentType.text,
                MatrixMessageContentType.encrypted =>
                  ChatMessageContentType.encrypted,
                MatrixMessageContentType.unsupported =>
                  ChatMessageContentType.unsupported,
              },
              text: message.text,
            ),
          )
          .toList(growable: false),
    );
  }

  @override
  Future<void> sendMessage({
    required String roomId,
    required String message,
  }) async {
    final homeserver = await _loadHomeserver();
    await _roomService.sendMessage(
      homeserver: homeserver,
      roomId: roomId,
      message: message,
    );
  }

  @override
  Future<void> markRoomRead(String roomId) async {
    final homeserver = await _loadHomeserver();
    await _roomService.markRoomRead(homeserver: homeserver, roomId: roomId);
  }

  @override
  Future<void> connect() async {
    final homeserver = await _loadHomeserver();
    await _sessionService.connect(homeserver: homeserver);
  }

  @override
  Future<void> signOut() => _sessionService.signOut();

  @override
  Future<void> clearSession() => _sessionService.clearSession();

  Future<Uri> _loadHomeserver() async {
    final configuration = await _serverConfigurationRepository
        .loadConfiguration();
    if (configuration == null) {
      throw const ChatFailure.configuration(
        'Finish setup before opening Matrix chat.',
      );
    }

    return configuration.serviceEndpoints.matrixHomeserverUrl;
  }
}
