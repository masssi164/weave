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
    this.isFavorite = false,
    this.isAiChat = false,
  });

  final String id;
  final String title;
  final ChatConversationPreviewType previewType;
  final String? previewText;
  final DateTime? lastActivityAt;
  final int unreadCount;
  final bool isInvite;
  final bool isDirectMessage;
  final bool isFavorite;
  final bool isAiChat;
}

class ChatOverview {
  const ChatOverview({
    required this.favorites,
    required this.personalMessages,
    required this.channels,
    required this.aiChats,
  });

  factory ChatOverview.fromConversations(List<ChatConversation> conversations) {
    return ChatOverview(
      favorites: conversations
          .where((conversation) => conversation.isFavorite)
          .toList(growable: false),
      personalMessages: conversations
          .where(
            (conversation) =>
                conversation.isDirectMessage && !conversation.isAiChat,
          )
          .toList(growable: false),
      channels: conversations
          .where(
            (conversation) =>
                !conversation.isDirectMessage && !conversation.isAiChat,
          )
          .toList(growable: false),
      aiChats: conversations
          .where((conversation) => conversation.isAiChat)
          .toList(growable: false),
    );
  }

  final List<ChatConversation> favorites;
  final List<ChatConversation> personalMessages;
  final List<ChatConversation> channels;
  final List<ChatConversation> aiChats;
}
