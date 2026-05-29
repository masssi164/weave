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
          .sortedByActivity(),
      personalMessages: conversations
          .where(
            (conversation) =>
                conversation.isDirectMessage && !conversation.isAiChat,
          )
          .sortedByActivity(),
      channels: conversations
          .where(
            (conversation) =>
                !conversation.isDirectMessage && !conversation.isAiChat,
          )
          .sortedByActivity(),
      aiChats: conversations
          .where((conversation) => conversation.isAiChat)
          .sortedByActivity(),
    );
  }

  final List<ChatConversation> favorites;
  final List<ChatConversation> personalMessages;
  final List<ChatConversation> channels;
  final List<ChatConversation> aiChats;

  int get unreadCount {
    final unique = <String>{};
    var total = 0;

    for (final conversation in [
      ...favorites,
      ...personalMessages,
      ...channels,
      ...aiChats,
    ]) {
      if (unique.add(conversation.id)) {
        total += conversation.unreadCount;
      }
    }

    return total;
  }

  ChatConversation? get nextConversation {
    final unique = <String, ChatConversation>{};
    for (final conversation in [
      ...favorites,
      ...personalMessages,
      ...channels,
      ...aiChats,
    ]) {
      unique.putIfAbsent(conversation.id, () => conversation);
    }

    final candidates = unique.values.sortedByActivity();
    return candidates.isEmpty ? null : candidates.first;
  }
}

extension on Iterable<ChatConversation> {
  List<ChatConversation> sortedByActivity() {
    return toList(growable: false)..sort(_compareConversationActivity);
  }
}

int _compareConversationActivity(
  ChatConversation left,
  ChatConversation right,
) {
  final unreadComparison = _compareUnread(left.unreadCount, right.unreadCount);
  if (unreadComparison != 0) {
    return unreadComparison;
  }

  final leftActivity = left.lastActivityAt;
  final rightActivity = right.lastActivityAt;
  if (leftActivity != null && rightActivity != null) {
    final activityComparison = rightActivity.compareTo(leftActivity);
    if (activityComparison != 0) {
      return activityComparison;
    }
  } else if (leftActivity != null) {
    return -1;
  } else if (rightActivity != null) {
    return 1;
  }

  return left.title.toLowerCase().compareTo(right.title.toLowerCase());
}

int _compareUnread(int left, int right) {
  final leftHasUnread = left > 0;
  final rightHasUnread = right > 0;
  if (leftHasUnread && !rightHasUnread) {
    return -1;
  }
  if (!leftHasUnread && rightHasUnread) {
    return 1;
  }
  return 0;
}
