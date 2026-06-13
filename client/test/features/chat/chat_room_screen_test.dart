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

  ChatRoomTimeline buildTimeline({
    bool canSendMessages = true,
    List<ChatMessage>? messages,
  }) {
    return ChatRoomTimeline(
      roomId: conversation.id,
      roomTitle: conversation.title,
      isInvite: !canSendMessages,
      canSendMessages: canSendMessages,
      messages:
          messages ??
          [
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

  testWidgets('renders accessible Space control room tabs with safe states', (
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

    expect(find.text('Project Space control room'), findsOneWidget);
    expect(find.text('Chat'), findsOneWidget);
    expect(find.text('Files'), findsOneWidget);
    expect(find.text('Documents/collaboration'), findsOneWidget);
    expect(find.text('Calendar'), findsOneWidget);
    expect(find.text('Meetings'), findsOneWidget);
    expect(find.text('Boards'), findsOneWidget);
    expect(find.text('Evidence'), findsOneWidget);
    expect(
      find.byWidgetPredicate(
        (widget) =>
            widget is Semantics &&
            widget.properties.label == 'Space control room tabs for Project',
      ),
      findsOneWidget,
    );

    await tester.tap(find.text('Files'));
    await tester.pumpAndSettle();
    expect(find.text('Channel files'), findsOneWidget);
    expect(find.text('not_configured'), findsOneWidget);
    expect(
      find.textContaining('No Space files are linked yet'),
      findsOneWidget,
    );
    expect(find.textContaining('Object weave:space-channel-'), findsOneWidget);
    expect(find.textContaining('files-link-not-configured'), findsOneWidget);
    expect(find.textContaining('Provider seam'), findsNothing);

    await tester.ensureVisible(find.text('Documents/collaboration'));
    await tester.tap(find.text('Documents/collaboration'));
    await tester.pumpAndSettle();
    expect(find.text('Documents/collaboration'), findsWidgets);
    expect(find.text('available'), findsOneWidget);
    expect(
      find.textContaining('backend-owned launch path is configured'),
      findsOneWidget,
    );
    expect(find.textContaining('document-cabinet'), findsOneWidget);
    expect(find.textContaining('documents-linked'), findsOneWidget);
    expect(find.textContaining('Provider seam'), findsNothing);

    await tester.ensureVisible(find.text('Boards'));
    await tester.tap(find.text('Boards'));
    await tester.pumpAndSettle();
    expect(find.text('Channel boards and tasks'), findsOneWidget);
    expect(find.text('degraded'), findsOneWidget);
    expect(find.textContaining('task freshness is degraded'), findsOneWidget);
    expect(find.textContaining('board-degraded'), findsOneWidget);
    expect(find.textContaining('provider'), findsNothing);

    await tester.tap(find.text('Calendar'));
    await tester.pumpAndSettle();
    expect(find.text('Channel calendar'), findsOneWidget);
    expect(find.text('disabled_by_policy'), findsOneWidget);
    expect(find.textContaining('Calendar writes are blocked'), findsOneWidget);
    expect(find.textContaining('calendar-policy-block'), findsOneWidget);
    expect(find.textContaining('Provider seam'), findsNothing);

    await tester.ensureVisible(find.text('Evidence'));
    await tester.tap(find.text('Evidence'));
    await tester.pumpAndSettle();
    expect(find.text('Decisions and evidence'), findsOneWidget);
    expect(find.text('available'), findsOneWidget);
    expect(
      find.textContaining(
        'supporting links can be cited without exposing graph internals',
      ),
      findsOneWidget,
    );
    expect(find.textContaining('evidence-ledger'), findsOneWidget);
    expect(find.textContaining('cross-domain-evidence'), findsOneWidget);
    expect(find.textContaining('Provider seam'), findsNothing);

    await tester.ensureVisible(find.text('Meetings'));
    await tester.tap(find.text('Meetings'));
    await tester.pumpAndSettle();
    expect(find.text('Channel meetings'), findsOneWidget);
    expect(find.text('coming_later'), findsOneWidget);
    expect(find.text('Meeting readiness is fail-closed'), findsOneWidget);
    expect(
      find.textContaining(
        'workspace owner or admin enables the meeting capability',
      ),
      findsOneWidget,
    );
    expect(find.text('Context pack for this meeting'), findsOneWidget);
    expect(find.text('Agenda'), findsOneWidget);
    expect(find.text('Follow-up evidence'), findsOneWidget);
    expect(find.text('Recording and transcription off'), findsOneWidget);
    expect(find.textContaining('LiveKit'), findsNothing);
    expect(find.textContaining('Provider seam'), findsNothing);
    expect(find.textContaining('preview'), findsNothing);
    expect(
      tester
          .widget<FilledButton>(
            find.widgetWithText(FilledButton, 'Join meeting'),
          )
          .onPressed,
      isNull,
    );
    expect(
      tester
          .widget<FilledButton>(
            find.widgetWithText(FilledButton, 'Start meeting'),
          )
          .onPressed,
      isNull,
    );
    semantics.dispose();
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

    expect(find.text('Decision Ledger'), findsOneWidget);
    expect(find.text('Decision record'), findsOneWidget);
    expect(find.text('Decision'), findsOneWidget);
    expect(find.text('Decisions: 1'), findsOneWidget);
    expect(find.text('Hey there'), findsAtLeastNWidgets(1));
    expect(
      find.text('Proposed. Recorded by You. Source: Message from Alex.'),
      findsOneWidget,
    );
    expect(
      find.textContaining('Weave-owned provenance links this decision'),
      findsOneWidget,
    );
    expect(
      find.textContaining(
        'Export decision records, source refs, and audit refs',
      ),
      findsOneWidget,
    );
    expect(
      find.textContaining('audit://chat/decision-ledger/'),
      findsOneWidget,
    );
    expect(
      find.text('Active. Captured by You. Source: message from Alex.'),
      findsOneWidget,
    );
    expect(
      find.text('Captured as decision record. Source linked to this message.'),
      findsOneWidget,
    );
    semantics.dispose();
  });

  testWidgets('shows actionable empty room recovery and refreshes', (
    tester,
  ) async {
    final semantics = tester.ensureSemantics();
    var loadCount = 0;
    final repository = FakeChatRepository(
      loadRoomTimelineHandler: (_) async {
        loadCount++;
        return ChatRoomTimeline(
          roomId: conversation.id,
          roomTitle: conversation.title,
          isInvite: false,
          canSendMessages: true,
          messages: loadCount > 1
              ? [
                  ChatMessage(
                    id: r'$after-refresh',
                    senderId: '@alex:home.internal',
                    senderDisplayName: 'Alex',
                    sentAt: DateTime(2026, 4, 20, 12),
                    isMine: false,
                    deliveryState: ChatMessageDeliveryState.sent,
                    contentType: ChatMessageContentType.text,
                    text: 'Recovered message',
                  ),
                ]
              : const [],
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

    expect(find.text('No messages yet'), findsOneWidget);
    expect(
      find.text(
        'Start the conversation when you are ready, or refresh if messages should already be here.',
      ),
      findsOneWidget,
    );
    expect(find.widgetWithText(OutlinedButton, 'Refresh room'), findsOneWidget);
    _expectSemanticTapAction(
      tester,
      find.bySemanticsLabel('Refresh room'),
      label: 'Refresh room',
    );

    final refreshRoomButton = find.widgetWithText(
      OutlinedButton,
      'Refresh room',
    );
    tester.widget<OutlinedButton>(refreshRoomButton).onPressed!();
    await tester.pumpAndSettle();

    expect(repository.loadRoomTimelineCalls, 2);
    expect(find.text('Recovered message'), findsOneWidget);
    expect(find.text('No messages yet'), findsNothing);
    semantics.dispose();
  });

  testWidgets('keeps archived empty states distinct and actionable', (
    tester,
  ) async {
    final semantics = tester.ensureSemantics();
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

    expect(
      find.text('Archived messages are hidden from this timeline.'),
      findsOneWidget,
    );
    expect(
      find.text(
        'Review archived messages to restore one, or wait here for new messages.',
      ),
      findsOneWidget,
    );
    expect(
      find.widgetWithText(OutlinedButton, 'Review archived messages'),
      findsOneWidget,
    );
    _expectSemanticTapAction(
      tester,
      find.bySemanticsLabel('Review archived messages').last,
      label: 'Review archived messages',
    );

    final reviewArchivedButton = find.widgetWithText(
      OutlinedButton,
      'Review archived messages',
    );
    tester.widget<OutlinedButton>(reviewArchivedButton).onPressed!();
    await tester.pumpAndSettle();

    expect(find.text('Archived messages'), findsAtLeastNWidgets(1));
    expect(find.text('Hey there'), findsOneWidget);

    await _openMessageActions(tester);
    await tester.pumpAndSettle();
    await tester.tap(find.text('Restore to timeline'));
    await tester.pumpAndSettle();

    expect(find.text('Archived messages'), findsNothing);
    await tester.tap(find.byTooltip('Review archived messages'));
    await tester.pumpAndSettle();

    expect(find.text('No archived messages yet.'), findsOneWidget);
    expect(
      find.text(
        'Messages you archive in this room will appear here. Return to the active timeline to keep chatting.',
      ),
      findsOneWidget,
    );
    expect(
      find.widgetWithText(OutlinedButton, 'Back to active timeline'),
      findsAtLeastNWidgets(1),
    );
    _expectSemanticTapAction(
      tester,
      find.bySemanticsLabel('Back to active timeline').last,
      label: 'Back to active timeline',
    );

    final backToActiveTimelineButton = find
        .widgetWithText(OutlinedButton, 'Back to active timeline')
        .last;
    tester.widget<OutlinedButton>(backToActiveTimelineButton).onPressed!();
    await tester.pumpAndSettle();

    expect(find.text('Archived messages'), findsNothing);
    expect(find.text('Hey there'), findsOneWidget);
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
    final semantics = tester.ensureSemantics();
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
    expect(
      find.byWidgetPredicate(
        (widget) =>
            widget is Semantics &&
            widget.properties.liveRegion == true &&
            widget.child is Text &&
            ((widget.child! as Text).data?.contains('Draft restored') ?? false),
      ),
      findsOneWidget,
    );
    expect(find.bySemanticsLabel('Close'), findsOneWidget);
    semantics.dispose();
  });

  testWidgets('renders encrypted and unsupported messages accessibly', (
    tester,
  ) async {
    final semantics = tester.ensureSemantics();
    final repository = FakeChatRepository(
      loadRoomTimelineHandler: (_) async => buildTimeline(
        messages: [
          ChatMessage(
            id: r'$encrypted',
            senderId: '@alex:home.internal',
            senderDisplayName: 'Alex',
            sentAt: DateTime(2026, 4, 20, 12),
            isMine: false,
            deliveryState: ChatMessageDeliveryState.sent,
            contentType: ChatMessageContentType.encrypted,
          ),
          ChatMessage(
            id: r'$unsupported',
            senderId: '@me:home.internal',
            senderDisplayName: 'Me',
            sentAt: DateTime(2026, 4, 20, 12, 1),
            isMine: true,
            deliveryState: ChatMessageDeliveryState.failed,
            contentType: ChatMessageContentType.unsupported,
          ),
        ],
      ),
    );

    await tester.pumpWidget(
      createTestApp(
        const ChatRoomScreen(conversation: conversation),
        overrides: overridesFor(repository),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('Encrypted message', skipOffstage: false), findsOneWidget);
    expect(
      find.text('Unsupported message', skipOffstage: false),
      findsOneWidget,
    );
    expect(find.text('Not sent', skipOffstage: false), findsOneWidget);
    expect(
      find.bySemanticsLabel(
        RegExp('Alex.*Encrypted message'),
        skipOffstage: false,
      ),
      findsOneWidget,
    );
    expect(
      find.bySemanticsLabel(
        RegExp('Me.*Unsupported message.*Not sent'),
        skipOffstage: false,
      ),
      findsOneWidget,
    );
    semantics.dispose();
  });

  testWidgets('shows retryable failures in the room', (tester) async {
    final repository = FakeChatRepository(
      loadRoomTimelineHandler: (_) async => throw const ChatFailure.protocol(
        'Raw room provider detail should not render.',
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
      find.text('Unable to load this conversation right now.'),
      findsAtLeastNWidgets(1),
    );
    expect(
      find.text('Raw room provider detail should not render.'),
      findsNothing,
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
            'Raw send provider detail should not render.',
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
        'That message could not be sent right now. Check your connection and try again.',
      ),
      findsOneWidget,
    );
    expect(
      find.text('Raw send provider detail should not render.'),
      findsNothing,
    );
    expect(
      find.byWidgetPredicate(
        (widget) =>
            widget is Semantics &&
            widget.properties.liveRegion == true &&
            widget.child is Text &&
            ((widget.child! as Text).data?.contains(
                  'That message could not be sent',
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
        'That message could not be sent right now. Check your connection and try again.',
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

    await _openMessageActions(tester);
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
      find.text(
        'Review archived messages to restore one, or wait here for new messages.',
      ),
      findsOneWidget,
    );
    expect(find.text('Review archived messages'), findsAtLeastNWidgets(1));
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
    expect(
      find.text(
        'Review archived messages to restore one, or wait here for new messages.',
      ),
      findsOneWidget,
    );

    await tester.tap(find.byTooltip('Review archived messages'));
    await tester.pumpAndSettle();

    expect(find.text('Archived messages'), findsOneWidget);
    expect(find.text('Hey there'), findsOneWidget);
    expect(find.text('Archived'), findsOneWidget);
    expect(find.byType(TextField), findsNothing);

    await _openMessageActions(tester);
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
  if (_hasSemanticTapAction(tester, finder, label)) {
    return;
  }

  final semanticAncestor = find.ancestor(
    of: finder,
    matching: find.byWidgetPredicate(
      (widget) => widget is Semantics && widget.properties.button == true,
    ),
  );
  expect(
    _hasSemanticTapAction(tester, semanticAncestor, label),
    isTrue,
    reason: 'Expected a semantic tap action labelled $label.',
  );
}

bool _hasSemanticTapAction(WidgetTester tester, Finder finder, String label) {
  final candidates = finder.evaluate().length;
  if (candidates != 1) {
    return false;
  }

  final data = tester.getSemantics(finder).getSemanticsData();
  final hasExpectedLabel = data.label == label || data.tooltip == label;
  final hasButtonText = find
      .descendant(of: finder, matching: find.text(label))
      .evaluate()
      .isNotEmpty;
  return (hasExpectedLabel || hasButtonText) &&
      data.hasAction(SemanticsAction.tap);
}

Future<void> _openMessageActions(WidgetTester tester) async {
  final finder = find.byWidgetPredicate(
    (widget) =>
        widget is PopupMenuButton && widget.tooltip == 'Message actions',
  );
  final renderBox = tester.renderObject<RenderBox>(finder.last);
  final target = renderBox.localToGlobal(
    Offset(renderBox.size.width - 24, renderBox.size.height / 2),
  );
  await tester.tapAt(target);
}
