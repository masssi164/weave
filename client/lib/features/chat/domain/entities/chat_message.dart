enum ChatMessageDeliveryState { sending, sent, failed }

enum ChatMessageContentType { text, encrypted, unsupported }

class ChatMessage {
  const ChatMessage({
    required this.id,
    required this.senderId,
    required this.senderDisplayName,
    required this.sentAt,
    required this.isMine,
    required this.deliveryState,
    required this.contentType,
    this.text,
  });

  final String id;
  final String senderId;
  final String senderDisplayName;
  final DateTime sentAt;
  final bool isMine;
  final ChatMessageDeliveryState deliveryState;
  final ChatMessageContentType contentType;
  final String? text;
}
