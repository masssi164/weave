import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:weave/core/persistence/shared_preferences_store.dart';
import 'package:weave/features/chat/domain/entities/chat_conversation.dart';
import 'package:weave/features/chat/domain/entities/chat_failure.dart';
import 'package:weave/features/chat/domain/entities/chat_message.dart';
import 'package:weave/features/chat/domain/entities/chat_room_timeline.dart';
import 'package:weave/features/chat/presentation/chat_room_screen.dart';
import 'package:weave/features/chat/presentation/providers/chat_repository_provider.dart';
import 'package:weave/l10n/generated/app_localizations.dart';

import '../helpers/fake_chat_repository.dart';
import '../helpers/in_memory_stores.dart';
import '../helpers/test_app.dart';

import '../../integration_test/dual_ios_collaboration_test.dart'
    show
        navigateNativeDualDestination,
        openNativeDualChatCreationDialog,
        sendNativeDualChatMessage;

void main() {
  for (final locale in ['en', 'de']) {
    testWidgets('actual localized Home/Chat navigation; locale=$locale', (
      tester,
    ) async {
      var selected = 0;
      await tester.pumpWidget(
        MaterialApp(
          locale: Locale(locale),
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          home: StatefulBuilder(
            builder: (context, setState) {
              final copy = AppLocalizations.of(context);
              return Scaffold(
                body: Text('selected=$selected'),
                bottomNavigationBar: NavigationBar(
                  selectedIndex: selected,
                  onDestinationSelected: (value) =>
                      setState(() => selected = value),
                  destinations: [
                    NavigationDestination(
                      icon: const Icon(Icons.home_outlined),
                      label: copy.navHome,
                    ),
                    NavigationDestination(
                      icon: const Icon(Icons.chat_bubble_outline),
                      label: copy.navChat,
                    ),
                  ],
                ),
              );
            },
          ),
        ),
      );
      await tester.pumpAndSettle();
      await navigateNativeDualDestination(tester, Icons.chat_bubble_outline);
      expect(
        tester.widget<NavigationBar>(find.byType(NavigationBar)).selectedIndex,
        1,
      );
      expect(find.text('selected=1'), findsOneWidget);
      await navigateNativeDualDestination(tester, Icons.home_outlined);
      expect(
        tester.widget<NavigationBar>(find.byType(NavigationBar)).selectedIndex,
        0,
      );
      expect(find.text('selected=0'), findsOneWidget);
    });
  }

  const conversation = ChatConversation(
    id: '!owned-room:example.invalid',
    title: 'Owned room',
    previewType: ChatConversationPreviewType.none,
    unreadCount: 0,
    isInvite: false,
    isDirectMessage: false,
  );
  for (final denied in [false, true]) {
    testWidgets(
      'real delayed composer sends once and verifies proof; denied=$denied',
      (tester) async {
        const body = 'Controlled widget message';
        final messages = <ChatMessage>[];
        final repository = FakeChatRepository(
          loadRoomTimelineHandler: (_) async {
            await Future<void>.delayed(const Duration(milliseconds: 500));
            return ChatRoomTimeline(
              roomId: conversation.id,
              roomTitle: conversation.title,
              isInvite: false,
              canSendMessages: true,
              messages: List.of(messages),
            );
          },
          sendMessageHandler: ({required roomId, required message}) async {
            expect(roomId, conversation.id);
            expect(message, body);
            if (denied) {
              throw const ChatFailure.protocol('Controlled send denial');
            }
            messages.add(
              ChatMessage(
                id: 'owned-event',
                senderId: 'owned-author',
                senderDisplayName: 'Owned author',
                sentAt: DateTime(2026),
                isMine: true,
                deliveryState: ChatMessageDeliveryState.sent,
                contentType: ChatMessageContentType.text,
                text: message,
              ),
            );
          },
        );
        await tester.pumpWidget(
          createTestApp(
            const ChatRoomScreen(conversation: conversation),
            overrides: [
              chatRepositoryProvider.overrideWithValue(repository),
              preferencesStoreProvider.overrideWith(
                (ref) => InMemoryPreferencesStore(),
              ),
            ],
          ),
        );
        expect(repository.sendMessageCalls, 0);
        Object? failure;
        try {
          await sendNativeDualChatMessage(
            tester,
            body,
            deadline: DateTime.now().add(Duration(seconds: denied ? 1 : 5)),
          );
        } catch (error) {
          failure = error;
        }
        expect(repository.sendMessageCalls, 1);
        final composer = find.descendant(
          of: find.byType(ChatRoomScreen),
          matching: find.byType(TextField),
        );
        if (denied) {
          expect(failure, isA<TestFailure>());
          expect(messages, isEmpty);
        } else {
          expect(failure, isNull);
          expect(find.text(body), findsOneWidget);
          expect(tester.widget<TextField>(composer).controller?.text, isEmpty);
        }
        await tester.pumpWidget(const SizedBox.shrink());
        await tester.pump(const Duration(seconds: 1));
      },
    );
  }

  testWidgets('waits for enabled real action and actual delayed dialog', (
    tester,
  ) async {
    var enabled = false;
    var taps = 0;
    late StateSetter update;
    await tester.pumpWidget(
      MaterialApp(
        home: StatefulBuilder(
          builder: (context, setState) {
            update = setState;
            return Scaffold(
              body: IconButton(
                key: const Key('chat-create-conversation-button'),
                onPressed: enabled
                    ? () {
                        taps++;
                        Future<void>.delayed(
                          const Duration(milliseconds: 500),
                          () {
                            if (!context.mounted) return;
                            showDialog<void>(
                              context: context,
                              builder: (_) => const AlertDialog(
                                content: TextField(
                                  key: Key(
                                    'chat-create-conversation-name-field',
                                  ),
                                ),
                              ),
                            );
                          },
                        );
                      }
                    : null,
                icon: const Icon(Icons.add),
              ),
            );
          },
        ),
      ),
    );
    Timer(
      const Duration(milliseconds: 500),
      () => update(() => enabled = true),
    );
    final name = await openNativeDualChatCreationDialog(
      tester,
      deadline: DateTime.now().add(const Duration(seconds: 5)),
    );
    expect(taps, 1);
    expect(name, findsOneWidget);
    await tester.enterText(name, 'Actual dialog content');
    expect(find.text('Actual dialog content'), findsOneWidget);
    await tester.pumpWidget(const SizedBox.shrink());
  });

  testWidgets('disabled action fails closed without invoking a callback', (
    tester,
  ) async {
    await tester.pumpWidget(
      const MaterialApp(
        home: Scaffold(
          body: IconButton(
            key: Key('chat-create-conversation-button'),
            onPressed: null,
            icon: Icon(Icons.add),
          ),
        ),
      ),
    );
    Object? failure;
    try {
      await openNativeDualChatCreationDialog(
        tester,
        deadline: DateTime.now().add(const Duration(milliseconds: 50)),
      );
    } catch (error) {
      failure = error;
    }
    expect(failure, isA<TestFailure>());
    expect(
      find.byKey(const Key('chat-create-conversation-name-field')),
      findsNothing,
    );
  });

  testWidgets('absent real dialog fails after one actual action', (
    tester,
  ) async {
    var taps = 0;
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: IconButton(
            key: const Key('chat-create-conversation-button'),
            onPressed: () => taps++,
            icon: const Icon(Icons.add),
          ),
        ),
      ),
    );
    Object? failure;
    try {
      await openNativeDualChatCreationDialog(
        tester,
        deadline: DateTime.now().add(const Duration(milliseconds: 50)),
      );
    } catch (error) {
      failure = error;
    }
    expect(failure, isA<TestFailure>());
    expect(taps, 1);
    expect(
      find.byKey(const Key('chat-create-conversation-name-field')),
      findsNothing,
    );
  });
}
