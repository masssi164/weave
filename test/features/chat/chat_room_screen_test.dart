import 'package:flutter/material.dart';
import 'package:flutter/semantics.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:weave/core/persistence/shared_preferences_store.dart';
import 'package:weave/features/chat/data/services/archived_message_store.dart';
import 'package:weave/features/chat/domain/entities/chat_conversation.dart';
import 'package:weave/features/chat/domain/entities/chat_failure.dart';
import 'package:weave/features/chat/domain/entities/chat_message.dart';
import 'package:weave/features/chat/domain/entities/chat_room_timeline.dart';
import 'package:weave/features/chat/presentation/chat_room_screen.dart';
import 'package:weave/features/chat/presentation/providers/chat_repository_provider.dart';

import '../../helpers/fake_chat_repository.dart';
import '../../helpers/in_memory_stores.dart';
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

  List<dynamic> overridesFor(
    FakeChatRepository repository, {
    InMemoryPreferencesStore? store,
  }) {
    return [
      chatRepositoryProvider.overrideWithValue(repository),
      preferencesStoreProvider.overrideWith(
        (ref) => store ?? InMemoryPreferencesStore(),
      ),
    ];
  }

  testWidgets('loads and renders a room timeline', (tester) async {
    final repository = FakeChatRepository(
      loadRoomTimelineHandler: (_) async => buildTimeline(),
    );

    await tester.pumpWidget(
      createTestApp(
        const ChatRoomScreen(conversation: conversation),
        overrides: overridesFor(repository),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('Project'), findsAtLeastNWidgets(1));
    expect(find.text('Context for this room'), findsOneWidget);
    expect(find.text('Current room'), findsOneWidget);
    expect(find.text('Selected files'), findsOneWidget);
    expect(find.text('Linked tasks'), findsOneWidget);
    expect(find.text('Recent decisions'), findsOneWidget);
    expect(
      find.text('No agent is reading this room in the background.'),
      findsOneWidget,
    );
    expect(
      find.text('Decisions, risks, questions, and evidence'),
      findsOneWidget,
    );
    expect(find.text('Decisions: 0'), findsOneWidget);
    expect(find.text('Risks: 0'), findsOneWidget);
    expect(find.text('Open questions: 0'), findsOneWidget);
    expect(find.text('Evidence: 0'), findsOneWidget);
    expect(
      find.textContaining('no automatic continuous room reading'),
      findsOneWidget,
    );
    expect(find.text('Alex'), findsOneWidget);
    expect(find.text('Hey there'), findsOneWidget);
    expect(repository.markRoomReadCalls, 1);
  });

  testWidgets('captures a message as a source-linked decision record', (
    tester,
  ) async {
    final semantics = tester.ensureSemantics();
    final repository = FakeChatRepository(
      loadRoomTimelineHandler: (_) async => buildTimeline(),
    );

    await tester.pumpWidget(
      createTestApp(
        const ChatRoomScreen(conversation: conversation),
        overrides: overridesFor(repository),
      ),
    );
    await tester.pumpAndSettle();

    _expectSemanticTapAction(
      tester,
      find.byTooltip('Message actions'),
      label: 'Message actions',
    );

    await tester.tap(find.byTooltip('Message actions'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Capture as decision'));
    await tester.pumpAndSettle();

    expect(find.text('Decision'), findsOneWidget);
    expect(find.text('Decisions: 1'), findsOneWidget);
    expect(find.text('Hey there'), findsAtLeastNWidgets(1));
    expect(
      find.text('Active. Captured by You. Source: message from Alex.'),
      findsOneWidget,
    );
    expect(
      find.text('Captured as decision. Source linked to this message.'),
      findsOneWidget,
    );
    semantics.dispose();
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
        overrides: overridesFor(repository),
      ),
    );
    await tester.pumpAndSettle();

    await tester.enterText(find.byType(TextField), 'Reply sent');
    await tester.tap(find.text('Send'));
    await tester.pumpAndSettle();

    expect(repository.sendMessageCalls, 1);
    expect(find.text('Reply sent'), findsOneWidget);
  });

  testWidgets('restores a local unsent draft for the room', (tester) async {
    final repository = FakeChatRepository(
      loadRoomTimelineHandler: (_) async => buildTimeline(),
    );
    final store = InMemoryPreferencesStore({
      'chat.roomDraft.v1.${Uri.encodeComponent(conversation.id)}':
          'Remember the release notes',
    });

    await tester.pumpWidget(
      createTestApp(
        const ChatRoomScreen(conversation: conversation),
        overrides: overridesFor(repository, store: store),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('Draft restored from this device.'), findsOneWidget);
    expect(find.text('Remember the release notes'), findsOneWidget);
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
        overrides: overridesFor(repository),
      ),
    );
    await tester.pumpAndSettle();

    expect(
      find.text('Unable to load this Matrix room right now.'),
      findsAtLeastNWidgets(1),
    );
    expect(find.text('Retry'), findsAtLeastNWidgets(1));
  });

  testWidgets('keeps a failed outgoing message visible with retry actions', (
    tester,
  ) async {
    final semantics = tester.ensureSemantics();
    var sendAttempts = 0;
    final repository = FakeChatRepository(
      loadRoomTimelineHandler: (_) async => buildTimeline(),
      sendMessageHandler: ({required roomId, required message}) async {
        sendAttempts++;
        if (sendAttempts == 1) {
          throw const ChatFailure.protocol(
            'Message could not be sent. Check your connection and try again.',
          );
        }
      },
    );

    await tester.pumpWidget(
      createTestApp(
        const ChatRoomScreen(conversation: conversation),
        overrides: overridesFor(repository),
      ),
    );
    await tester.pumpAndSettle();

    await tester.enterText(find.byType(TextField), 'Retry me');
    await tester.tap(find.text('Send'));
    await tester.pumpAndSettle();

    expect(find.text('Retry me'), findsAtLeastNWidgets(1));
    expect(find.text('Not sent'), findsOneWidget);
    expect(
      find.text(
        'Message could not be sent. Check your connection and try again.',
      ),
      findsOneWidget,
    );
    expect(
      find.byWidgetPredicate(
        (widget) =>
            widget is Semantics &&
            widget.properties.liveRegion == true &&
            widget.child is Text &&
            ((widget.child! as Text).data?.contains(
                  'Message could not be sent',
                ) ??
                false),
      ),
      findsOneWidget,
    );
    expect(find.text('Retry send'), findsOneWidget);
    _expectSemanticTapAction(
      tester,
      find.byTooltip('Retry send'),
      label: 'Retry send',
    );
    semantics.dispose();

    await tester.tap(find.text('Retry send').first);
    await tester.pumpAndSettle();

    expect(repository.sendMessageCalls, 2);
    expect(find.text('Not sent'), findsNothing);
    expect(
      find.text(
        'Message could not be sent. Check your connection and try again.',
      ),
      findsNothing,
    );
  });

  testWidgets('disables the composer for invite-only rooms', (tester) async {
    final repository = FakeChatRepository(
      loadRoomTimelineHandler: (_) async =>
          buildTimeline(canSendMessages: false),
    );

    await tester.pumpWidget(
      createTestApp(
        const ChatRoomScreen(conversation: conversation),
        overrides: overridesFor(repository),
      ),
    );
    await tester.pumpAndSettle();

    final textField = tester.widget<TextField>(find.byType(TextField));
    final sendButton = tester.widget<FilledButton>(find.byType(FilledButton));
    expect(textField.enabled, isFalse);
    expect(sendButton.onPressed, isNull);
  });

  testWidgets('archives a message from the actions menu', (tester) async {
    final semantics = tester.ensureSemantics();
    final store = InMemoryPreferencesStore();
    final repository = FakeChatRepository(
      loadRoomTimelineHandler: (_) async => buildTimeline(),
    );

    await tester.pumpWidget(
      createTestApp(
        const ChatRoomScreen(conversation: conversation),
        overrides: overridesFor(repository, store: store),
      ),
    );
    await tester.pumpAndSettle();

    _expectSemanticTapAction(
      tester,
      find.byTooltip('Message actions'),
      label: 'Message actions',
    );
    semantics.dispose();

    await tester.tap(find.byIcon(Icons.more_vert));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Archive'));
    await tester.pumpAndSettle();
    await tester.tap(find.widgetWithText(FilledButton, 'Archive'));
    await tester.pumpAndSettle();

    expect(find.text('Hey there'), findsNothing);
    expect(
      find.text('Archived messages are hidden from this timeline.'),
      findsOneWidget,
    );
    expect(
      store.rawString(
        '${ArchivedMessageStore.storageKeyPrefix}${conversation.id}',
      ),
      contains(r'$one'),
    );
  });

  testWidgets('reviews and restores archived messages from a distinct view', (
    tester,
  ) async {
    final store = InMemoryPreferencesStore();
    final repository = FakeChatRepository(
      loadRoomTimelineHandler: (_) async => buildTimeline(),
    );
    final storageKey =
        '${ArchivedMessageStore.storageKeyPrefix}${conversation.id}';
    await store.setString(storageKey, r'["$one"]');

    await tester.pumpWidget(
      createTestApp(
        const ChatRoomScreen(conversation: conversation),
        overrides: overridesFor(repository, store: store),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('Hey there'), findsNothing);
    expect(
      find.text('Archived messages are hidden from this timeline.'),
      findsOneWidget,
    );

    await tester.tap(find.byTooltip('Review archived messages'));
    await tester.pumpAndSettle();

    expect(find.text('Archived messages'), findsOneWidget);
    expect(find.text('Hey there'), findsOneWidget);
    expect(find.text('Archived'), findsOneWidget);
    expect(find.byType(TextField), findsNothing);

    await tester.tap(find.byIcon(Icons.more_vert));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Restore to timeline'));
    await tester.pumpAndSettle();

    expect(store.rawString(storageKey), isNull);
    expect(find.text('Archived messages'), findsNothing);
    expect(find.text('Hey there'), findsOneWidget);
    expect(find.byType(TextField), findsOneWidget);
  });
}

void _expectSemanticTapAction(
  WidgetTester tester,
  Finder finder, {
  required String label,
}) {
  final data = tester.getSemantics(finder).getSemanticsData();
  expect(data.label == label || data.tooltip == label, isTrue);
  expect(data.hasAction(SemanticsAction.tap), isTrue);
}
