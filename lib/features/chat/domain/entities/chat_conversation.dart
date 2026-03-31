enum ChatConversationPreviewType { none, text, encrypted, unsupported }

class ChatConversation {
  const ChatConversation({
    required this.id,
    required this.title,
    required this.previewType,
    required this.unreadCount,
    required this.isInvite,
    required this.isDirectMessage,
    this.previewText,
    this.lastActivityAt,
  });

  final String id;
  final String title;
  final ChatConversationPreviewType previewType;
  final String? previewText;
  final DateTime? lastActivityAt;
  final int unreadCount;
  final bool isInvite;
  final bool isDirectMessage;
}
