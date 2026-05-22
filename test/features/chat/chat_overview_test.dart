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
  });
}
