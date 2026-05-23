import 'package:weave/features/chat/domain/entities/chat_message.dart';

class ChatRoomTimeline {
  const ChatRoomTimeline({
    required this.roomId,
    required this.roomTitle,
    required this.isInvite,
    required this.canSendMessages,
    required this.messages,
  });

  final String roomId;
  final String roomTitle;
  final bool isInvite;
  final bool canSendMessages;
  final List<ChatMessage> messages;
}
