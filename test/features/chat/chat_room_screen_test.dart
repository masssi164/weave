import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:weave/features/chat/domain/entities/chat_conversation.dart';
import 'package:weave/features/chat/domain/entities/chat_failure.dart';
import 'package:weave/features/chat/domain/entities/chat_message.dart';
import 'package:weave/features/chat/domain/entities/chat_room_timeline.dart';
import 'package:weave/features/chat/presentation/chat_room_screen.dart';
import 'package:weave/features/chat/presentation/providers/chat_repository_provider.dart';

import '../../helpers/fake_chat_repository.dart';
import '../../helpers/test_app.dart';

void main() {
  const conversation = ChatConversation(
    id: '!room:home.internal',
    title: 'Project',
    previewType: ChatConversationPreviewType.text,
    unreadCount: 2,
    isInvite: false,
    isDirectMessage: false,
  );

  ChatRoomTimeline buildTimeline({bool canSendMessages = true}) {
    return ChatRoomTimeline(
      roomId: conversation.id,
      roomTitle: conversation.title,
      isInvite: !canSendMessages,
      canSendMessages: canSendMessages,
      messages: [
        ChatMessage(
          id: r'$one',
          senderId: '@alex:home.internal',
          senderDisplayName: 'Alex',
          sentAt: DateTime(2026, 4, 20, 12),
          isMine: false,
          deliveryState: ChatMessageDeliveryState.sent,
          contentType: ChatMessageContentType.text,
          text: 'Hey there',
        ),
      ],
    );
  }

  testWidgets('loads and renders a room timeline', (tester) async {
    final repository = FakeChatRepository(
      loadRoomTimelineHandler: (_) async => buildTimeline(),
    );

    await tester.pumpWidget(
      createTestApp(
        const ChatRoomScreen(conversation: conversation),
        overrides: [chatRepositoryProvider.overrideWithValue(repository)],
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('Project'), findsAtLeastNWidgets(1));
    expect(find.text('Alex'), findsOneWidget);
    expect(find.text('Hey there'), findsOneWidget);
    expect(repository.markRoomReadCalls, 1);
  });

  testWidgets('sends a message and reloads the timeline', (tester) async {
    var loadCount = 0;
    final repository = FakeChatRepository(
      loadRoomTimelineHandler: (_) async {
        loadCount++;
        return ChatRoomTimeline(
          roomId: conversation.id,
          roomTitle: conversation.title,
          isInvite: false,
          canSendMessages: true,
          messages: [
            ChatMessage(
              id: r'$one',
              senderId: '@alex:home.internal',
              senderDisplayName: 'Alex',
              sentAt: DateTime(2026, 4, 20, 12),
              isMine: false,
              deliveryState: ChatMessageDeliveryState.sent,
              contentType: ChatMessageContentType.text,
              text: 'Hey there',
            ),
            if (loadCount > 1)
              ChatMessage(
                id: r'$two',
                senderId: '@me:home.internal',
                senderDisplayName: 'Me',
                sentAt: DateTime(2026, 4, 20, 12, 1),
                isMine: true,
                deliveryState: ChatMessageDeliveryState.sent,
                contentType: ChatMessageContentType.text,
                text: 'Reply sent',
              ),
          ],
        );
      },
    );

    await tester.pumpWidget(
      createTestApp(
        const ChatRoomScreen(conversation: conversation),
        overrides: [chatRepositoryProvider.overrideWithValue(repository)],
      ),
    );
    await tester.pumpAndSettle();

    await tester.enterText(find.byType(TextField), 'Reply sent');
    await tester.tap(find.text('Send'));
    await tester.pumpAndSettle();

    expect(repository.sendMessageCalls, 1);
    expect(find.text('Reply sent'), findsOneWidget);
  });

  testWidgets('shows retryable failures in the room', (tester) async {
    final repository = FakeChatRepository(
      loadRoomTimelineHandler: (_) async => throw const ChatFailure.protocol(
        'Unable to load this Matrix room right now.',
      ),
    );

    await tester.pumpWidget(
      createTestApp(
        const ChatRoomScreen(conversation: conversation),
        overrides: [chatRepositoryProvider.overrideWithValue(repository)],
      ),
    );
    await tester.pumpAndSettle();

    expect(
      find.text('Unable to load this Matrix room right now.'),
      findsAtLeastNWidgets(1),
    );
    expect(find.text('Retry'), findsAtLeastNWidgets(1));
  });

  testWidgets('disables the composer for invite-only rooms', (tester) async {
    final repository = FakeChatRepository(
      loadRoomTimelineHandler: (_) async =>
          buildTimeline(canSendMessages: false),
    );

    await tester.pumpWidget(
      createTestApp(
        const ChatRoomScreen(conversation: conversation),
        overrides: [chatRepositoryProvider.overrideWithValue(repository)],
      ),
    );
    await tester.pumpAndSettle();

    final textField = tester.widget<TextField>(find.byType(TextField));
    final sendButton = tester.widget<FilledButton>(find.byType(FilledButton));
    expect(textField.enabled, isFalse);
    expect(sendButton.onPressed, isNull);
  });
}
