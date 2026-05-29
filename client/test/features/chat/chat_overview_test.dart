import 'package:flutter_test/flutter_test.dart';
import 'package:weave/features/chat/domain/entities/chat_conversation.dart';

void main() {
  test('groups conversations into favorites, DMs, channels, and AI chats', () {
    const dm = ChatConversation(
      id: '@alice:home.internal',
      title: 'Alice',
      previewType: ChatConversationPreviewType.text,
      unreadCount: 0,
      isInvite: false,
      isDirectMessage: true,
    );
    const favoriteChannel = ChatConversation(
      id: '!ops:home.internal',
      title: 'Ops',
      previewType: ChatConversationPreviewType.text,
      unreadCount: 2,
      isInvite: false,
      isDirectMessage: false,
      isFavorite: true,
    );
    const aiChat = ChatConversation(
      id: 'agent:release-coach',
      title: 'Release coach',
      previewType: ChatConversationPreviewType.text,
      unreadCount: 0,
      isInvite: false,
      isDirectMessage: true,
      isAiChat: true,
    );

    final overview = ChatOverview.fromConversations([
      dm,
      favoriteChannel,
      aiChat,
    ]);

    expect(overview.favorites, [favoriteChannel]);
    expect(overview.personalMessages, [dm]);
    expect(overview.channels, [favoriteChannel]);
    expect(overview.aiChats, [aiChat]);
    expect(overview.unreadCount, 2);
    expect(overview.nextConversation, favoriteChannel);
  });

  test('prioritizes unread and recent activity within overview groups', () {
    final quietRecentChannel = ChatConversation(
      id: '!recent:home.internal',
      title: 'Recent channel',
      previewType: ChatConversationPreviewType.text,
      unreadCount: 0,
      isInvite: false,
      isDirectMessage: false,
      lastActivityAt: DateTime(2026, 5, 1, 12),
      isFavorite: true,
    );
    final unreadOlderChannel = ChatConversation(
      id: '!unread-older:home.internal',
      title: 'Unread older channel',
      previewType: ChatConversationPreviewType.text,
      unreadCount: 1,
      isInvite: false,
      isDirectMessage: false,
      lastActivityAt: DateTime(2026, 4, 30, 12),
    );
    final unreadRecentDm = ChatConversation(
      id: '@alex:home.internal',
      title: 'Alex',
      previewType: ChatConversationPreviewType.text,
      unreadCount: 2,
      isInvite: false,
      isDirectMessage: true,
      lastActivityAt: DateTime(2026, 5, 1, 11),
    );
    final unreadOlderDm = ChatConversation(
      id: '@drew:home.internal',
      title: 'Drew',
      previewType: ChatConversationPreviewType.text,
      unreadCount: 5,
      isInvite: false,
      isDirectMessage: true,
      lastActivityAt: DateTime(2026, 4, 30, 10),
    );
    const quietNoActivityDm = ChatConversation(
      id: '@bea:home.internal',
      title: 'Bea',
      previewType: ChatConversationPreviewType.text,
      unreadCount: 0,
      isInvite: false,
      isDirectMessage: true,
    );
    final quietOlderDm = ChatConversation(
      id: '@casey:home.internal',
      title: 'Casey',
      previewType: ChatConversationPreviewType.text,
      unreadCount: 0,
      isInvite: false,
      isDirectMessage: true,
      lastActivityAt: DateTime(2026, 4, 29, 9),
    );

    final overview = ChatOverview.fromConversations([
      quietRecentChannel,
      quietNoActivityDm,
      unreadOlderChannel,
      quietOlderDm,
      unreadOlderDm,
      unreadRecentDm,
    ]);

    expect(overview.personalMessages, [
      unreadRecentDm,
      unreadOlderDm,
      quietOlderDm,
      quietNoActivityDm,
    ]);
    expect(overview.channels, [unreadOlderChannel, quietRecentChannel]);
    expect(overview.nextConversation, unreadRecentDm);
  });
}
