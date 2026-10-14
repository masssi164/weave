import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:weave/core/bootstrap/domain/bootstrap_state.dart';
import 'package:weave/core/bootstrap/presentation/providers/app_bootstrap_provider.dart';
import 'package:weave/core/failures/app_failure.dart';
import 'package:weave/core/persistence/shared_preferences_store.dart';
import 'package:weave/features/chat/domain/entities/chat_conversation.dart';
import 'package:weave/features/chat/domain/entities/chat_participant.dart';
import 'package:weave/features/chat/domain/repositories/chat_repository.dart';
import 'package:weave/features/chat/domain/entities/chat_failure.dart';
import 'package:weave/features/chat/domain/entities/chat_message.dart';
import 'package:weave/features/chat/domain/entities/chat_room_timeline.dart';
import 'package:weave/features/chat/presentation/chat_room_screen.dart';
import 'package:weave/features/chat/presentation/chat_screen.dart';
import 'package:weave/features/chat/presentation/providers/chat_provider.dart';
import 'package:weave/features/app/domain/entities/workspace_capability_snapshot.dart';
import 'package:weave/features/app/presentation/providers/workspace_connection_provider.dart';
import 'package:weave/features/chat/presentation/providers/chat_repository_provider.dart';
import 'package:weave/l10n/generated/app_localizations.dart';

import '../helpers/fake_chat_repository.dart';
import '../helpers/in_memory_stores.dart';
import '../helpers/test_app.dart';

import '../../integration_test/dual_ios_collaboration_test.dart'
    show
        navigateNativeDualDestination,
        nativeDualChatSurfaceDiagnostic,
        nativeDualChatListDiagnostic,
        waitNativeDualChatConversation,
        openNativeDualChatCreationDialog,
        sendNativeDualChatMessage,
        requireNativeDualPeerSelected,
        tapNativeDualPeer;

const _readyChatSnapshot = WorkspaceCapabilitySnapshot(
  shellAccess: WorkspaceCapabilityState(
    capability: WorkspaceCapability.shellAccess,
    readiness: WorkspaceCapabilityReadiness.ready,
  ),
  chat: WorkspaceCapabilityState(
    capability: WorkspaceCapability.chat,
    readiness: WorkspaceCapabilityReadiness.ready,
  ),
  files: WorkspaceCapabilityState(
    capability: WorkspaceCapability.files,
    readiness: WorkspaceCapabilityReadiness.ready,
  ),
  calendar: WorkspaceCapabilityState(
    capability: WorkspaceCapability.calendar,
    readiness: WorkspaceCapabilityReadiness.ready,
  ),
  boards: WorkspaceCapabilityState(
    capability: WorkspaceCapability.boards,
    readiness: WorkspaceCapabilityReadiness.ready,
  ),
);

class _PeerRepository extends FakeChatRepository
    implements ChatParticipantRepository {
  List<ChatParticipant>? submitted;
  @override
  Future<ChatConversation> createConversation({required String title}) async {
    submitted = [];
    throw const ChatFailure.protocol('Controlled creation refusal');
  }

  @override
  Future<List<ChatParticipant>> searchMembers(String query) async => const [
    ChatParticipant(userId: '@peer:weave.test', displayName: 'Private peer'),
  ];
  @override
  Future<ChatConversation> createConversationWithParticipants({
    required String title,
    required List<ChatParticipant> participants,
  }) async {
    submitted = participants;
    throw const ChatFailure.protocol('Controlled creation refusal');
  }
}

void main() {
  testWidgets(
    'phone keyboard peer uses actual visibility preparation before one selection',
    (tester) async {
      tester.view.physicalSize = const Size(390, 844);
      tester.view.devicePixelRatio = 1;
      tester.view.viewInsets = const FakeViewPadding(bottom: 336);
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);
      addTearDown(tester.view.resetViewInsets);
      final repository = _PeerRepository();
      await tester.pumpWidget(
        createTestApp(
          const ChatScreen(),
          overrides: [
            chatRepositoryProvider.overrideWithValue(repository),
            workspaceCapabilitySnapshotProvider.overrideWith(
              (ref) => const AsyncData(_readyChatSnapshot),
            ),
          ],
        ),
      );
      await tester.pumpAndSettle();
      await tester.tap(
        find.byKey(const Key('chat-create-conversation-button')),
      );
      await tester.pumpAndSettle();
      await tester.enterText(
        find.byKey(const Key('chat-create-conversation-name-field')),
        'Private room',
      );
      await tester.enterText(
        find.byKey(const Key('chat-create-conversation-participant-field')),
        'Private peer',
      );
      await tester.pump(const Duration(milliseconds: 300));
      await tester.pumpAndSettle();
      final peer = find.widgetWithText(CheckboxListTile, 'Private peer');
      expect(peer, findsOneWidget);
      expect(peer.hitTestable(), findsNothing);
      var pointerDowns = 0;
      void observePointer(PointerEvent event) {
        if (event is PointerDownEvent) pointerDowns++;
      }

      tester.binding.pointerRouter.addGlobalRoute(observePointer);
      try {
        await tapNativeDualPeer(
          tester,
          peer,
          deadline: DateTime.now().add(const Duration(seconds: 2)),
        );
      } finally {
        tester.binding.pointerRouter.removeGlobalRoute(observePointer);
      }
      expect(pointerDowns, 1);
      expect(tester.widget<CheckboxListTile>(peer).value, isTrue);
      expect(peer.hitTestable(), findsOneWidget);
      final create = find.byKey(
        const Key('chat-create-conversation-submit-button'),
      );
      await tester.ensureVisible(create);
      await tester.tap(create);
      await tester.pumpAndSettle();
      expect(repository.submitted?.single.userId, '@peer:weave.test');
    },
  );

  for (final activate in [true, false]) {
    testWidgets('actual dialog requires checked peer; activation=$activate', (
      tester,
    ) async {
      final repository = _PeerRepository();
      await tester.pumpWidget(
        createTestApp(
          const ChatScreen(),
          overrides: [
            chatRepositoryProvider.overrideWithValue(repository),
            workspaceCapabilitySnapshotProvider.overrideWith(
              (ref) => const AsyncData(_readyChatSnapshot),
            ),
          ],
        ),
      );
      await tester.pumpAndSettle();
      await tester.tap(
        find.byKey(const Key('chat-create-conversation-button')),
      );
      await tester.pumpAndSettle();
      await tester.enterText(
        find.byKey(const Key('chat-create-conversation-name-field')),
        'Private room',
      );
      await tester.enterText(
        find.byKey(const Key('chat-create-conversation-participant-field')),
        'Private peer',
      );
      await tester.pump(const Duration(milliseconds: 300));
      await tester.pumpAndSettle();
      final peer = find.widgetWithText(CheckboxListTile, 'Private peer');
      expect(peer, findsOneWidget);
      if (activate) {
        await tester.tap(peer);
        await tester.pumpAndSettle();
        await requireNativeDualPeerSelected(
          tester,
          peer,
          deadline: DateTime.now().add(const Duration(seconds: 1)),
        );
      } else {
        // A cancelled real pointer gesture never reaches the selection callback.
        // The former continuation still reaches Create; checked admission cannot.
        final gesture = await tester.startGesture(tester.getCenter(peer));
        await gesture.cancel();
        await tester.pumpAndSettle();
        await expectLater(
          requireNativeDualPeerSelected(
            tester,
            peer,
            deadline: DateTime.now().add(const Duration(seconds: 1)),
          ),
          throwsA(isA<TestFailure>()),
        );
        expect(repository.submitted, isNull);
      }
      // Former unchecked continuation admits Create with zero selected members.
      final create = find.byKey(
        const Key('chat-create-conversation-submit-button'),
      );
      await tester.ensureVisible(create);
      await tester.tap(create);
      await tester.pumpAndSettle();
      expect(repository.submitted?.length, activate ? 1 : 0);
      if (activate) {
        expect(repository.submitted?.single.userId, '@peer:weave.test');
      }
    });
  }

  testWidgets(
    'cold list timeout diagnostics never initialize product providers',
    (tester) async {
      final repository = FakeChatRepository();
      await tester.pumpWidget(
        createTestApp(
          const SizedBox(),
          overrides: [chatRepositoryProvider.overrideWithValue(repository)],
        ),
      );
      final container = ProviderScope.containerOf(
        tester.element(find.byType(Scaffold)),
      );
      expect(container.exists(chatProvider), isFalse);
      expect(container.exists(appBootstrapProvider), isFalse);
      expect(container.exists(workspaceCapabilitySnapshotProvider), isFalse);
      Object? failure;
      try {
        await waitNativeDualChatConversation(
          tester,
          container,
          'Private missing room',
          deadline: DateTime.now().add(const Duration(milliseconds: 30)),
        );
      } catch (error) {
        failure = error;
      }
      expect(failure, isA<TestFailure>());
      await tester.pump();
      final diagnostic = nativeDualChatListDiagnostic(
        tester,
        null,
        null,
        null,
        'Private missing room',
      );
      expect(diagnostic, contains('listPhase=unavailable'));
      expect(diagnostic, contains('listInitialized=false'));
      expect(
        diagnostic,
        contains('bootstrapInitialized=false capabilitiesInitialized=false'),
      );
      expect(diagnostic, isNot(contains('Private missing room')));
      expect(container.exists(chatProvider), isFalse);
      expect(container.exists(appBootstrapProvider), isFalse);
      expect(container.exists(workspaceCapabilitySnapshotProvider), isFalse);
      expect(repository.loadConversationsCalls, 0);
    },
  );

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

  for (final locale in ['en', 'de']) {
    testWidgets(
      'actual localized Chat refresh discovers invitation once; $locale',
      (tester) async {
        const target = ChatConversation(
          id: '!owned-invite:example.invalid',
          title: 'Owned new invitation',
          previewType: ChatConversationPreviewType.none,
          unreadCount: 0,
          isInvite: true,
          isDirectMessage: false,
        );
        var calls = 0;
        final repository = FakeChatRepository(
          loadConversationsHandler: () async => ++calls == 1 ? [] : [target],
        );
        await tester.pumpWidget(
          ProviderScope(
            overrides: [
              chatRepositoryProvider.overrideWithValue(repository),
              workspaceCapabilitySnapshotProvider.overrideWith(
                (ref) => const AsyncData(_readyChatSnapshot),
              ),
            ],
            child: MaterialApp(
              locale: Locale(locale),
              localizationsDelegates: AppLocalizations.localizationsDelegates,
              supportedLocales: AppLocalizations.supportedLocales,
              home: const Scaffold(body: ChatScreen()),
            ),
          ),
        );
        await tester.pumpAndSettle();
        expect(find.text(target.title), findsNothing);
        final container = ProviderScope.containerOf(
          tester.element(find.byType(ChatScreen)),
        );
        await waitNativeDualChatConversation(
          tester,
          container,
          target.title,
          deadline: DateTime.now().add(const Duration(seconds: 2)),
        );
        expect(find.text(target.title), findsOneWidget);
        expect(repository.loadConversationsCalls, 2);
        await waitNativeDualChatConversation(
          tester,
          container,
          target.title,
          deadline: DateTime.now().add(const Duration(seconds: 2)),
        );
        expect(repository.loadConversationsCalls, 2);
        expect(
          container.read(chatProvider).conversations.single.isInvite,
          isTrue,
        );
      },
    );
  }

  for (final blocked in ['non-hittable', 'ambiguous', 'no-result']) {
    testWidgets('room lookup never bypasses actual UI recovery: $blocked', (
      tester,
    ) async {
      final repository = FakeChatRepository();
      final child = blocked == 'ambiguous'
          ? const Column(
              children: [
                Expanded(child: ChatScreen()),
                Expanded(child: ChatScreen()),
              ],
            )
          : IgnorePointer(
              ignoring: blocked == 'non-hittable',
              child: const ChatScreen(),
            );
      await tester.pumpWidget(
        createTestApp(
          child,
          overrides: [
            chatRepositoryProvider.overrideWithValue(repository),
            workspaceCapabilitySnapshotProvider.overrideWith(
              (ref) => const AsyncData(_readyChatSnapshot),
            ),
          ],
        ),
      );
      await tester.pumpAndSettle();
      final container = ProviderScope.containerOf(
        tester.element(find.byType(ChatScreen).first),
      );
      final before = repository.loadConversationsCalls;
      Object? failure;
      try {
        await waitNativeDualChatConversation(
          tester,
          container,
          'Private missing room',
          deadline: DateTime.now().add(const Duration(milliseconds: 50)),
        );
      } catch (error) {
        failure = error;
      }
      expect(failure, isA<TestFailure>());
      expect(
        repository.loadConversationsCalls,
        before + (blocked == 'no-result' ? 1 : 0),
      );
      expect(find.text('Private missing room'), findsNothing);
    });
  }

  testWidgets(
    'private actual Chat list snapshots loading, invite and hidden error',
    (tester) async {
      const privateTitle =
          'private-user@example.invalid token=https://secret.invalid';
      const invitation = ChatConversation(
        id: '!private-id:secret.invalid',
        title: privateTitle,
        previewType: ChatConversationPreviewType.none,
        unreadCount: 0,
        isInvite: true,
        isDirectMessage: false,
      );
      final pending = Completer<List<ChatConversation>>();
      final repository = FakeChatRepository(
        loadConversationsHandler: () => pending.future,
      );
      Future<void> render(bool hidden) async {
        await tester.pumpWidget(
          createTestApp(
            Offstage(offstage: hidden, child: const ChatScreen()),
            overrides: [
              chatRepositoryProvider.overrideWithValue(repository),
              workspaceCapabilitySnapshotProvider.overrideWith(
                (ref) => const AsyncData(_readyChatSnapshot),
              ),
            ],
          ),
        );
        await tester.pump();
      }

      await render(false);
      final container = ProviderScope.containerOf(
        tester.element(find.byType(ChatScreen)),
      );
      String snapshot() => nativeDualChatListDiagnostic(
        tester,
        container.read(chatProvider),
        const AsyncData(BootstrapState.ready()),
        const AsyncData(_readyChatSnapshot),
        privateTitle,
      );
      final loading = snapshot();
      expect(loading, contains('listPhase=loading'));
      expect(
        loading,
        contains('conversationCount=0 invitedCount=0 matchingCount=0'),
      );
      pending.complete(const [invitation]);
      await tester.pumpAndSettle();
      final content = snapshot();
      expect(
        content,
        contains('listVisible=1 listMounted=1 listPhase=content'),
      );
      expect(
        content,
        contains(
          'conversationCount=1 invitedCount=1 matchingCount=1 visibleMatchingCount=1',
        ),
      );
      await render(true);
      final hidden = snapshot();
      expect(hidden, contains('listVisible=0 listMounted=1'));
      expect(hidden, contains('visibleMatchingCount=0 mountedMatchingCount=1'));
      repository.loadConversationsHandler = () async =>
          throw const ChatFailure.sessionRequired(privateTitle);
      await container.read(chatProvider.notifier).retry();
      await tester.pump();
      final failed = snapshot();
      expect(failed, contains('listPhase=error listFailure=sessionRequired'));
      expect(
        failed,
        contains('conversationCount=0 invitedCount=0 matchingCount=0'),
      );
      for (final diagnostic in [loading, content, hidden, failed]) {
        expect(diagnostic, isNot(contains('private-user')));
        expect(diagnostic, isNot(contains('secret.invalid')));
        expect(diagnostic, isNot(contains('private-id')));
        expect(diagnostic, isNot(contains('token=')));
        expect(diagnostic, isNot(contains('https:')));
      }
    },
  );

  const conversation = ChatConversation(
    id: '!owned-room:example.invalid',
    title: 'Owned room',
    previewType: ChatConversationPreviewType.none,
    unreadCount: 0,
    isInvite: false,
    isDirectMessage: false,
  );
  testWidgets('private Chat snapshot distinguishes offstage and redacts errors', (
    tester,
  ) async {
    final repository = FakeChatRepository();
    Future<void> render(bool hidden) async {
      await tester.pumpWidget(
        createTestApp(
          Offstage(
            offstage: hidden,
            child: const ChatRoomScreen(conversation: conversation),
          ),
          overrides: [
            chatRepositoryProvider.overrideWithValue(repository),
            preferencesStoreProvider.overrideWith(
              (ref) => InMemoryPreferencesStore(),
            ),
          ],
        ),
      );
      await tester.pumpAndSettle();
    }

    await render(true);
    final hidden = nativeDualChatSurfaceDiagnostic(
      tester,
      const AsyncLoading(),
      const AsyncLoading(),
    );
    expect(hidden, contains('roomVisible=0 roomMounted=1'));
    expect(hidden, contains('bootstrapLoading=true bootstrapError=false'));
    await render(false);
    const privateError = AppFailure.storage(
      'private-account@example.invalid secret=https://example.invalid/token',
    );
    final visible = nativeDualChatSurfaceDiagnostic(
      tester,
      AsyncError(privateError, StackTrace.current),
      AsyncError(privateError, StackTrace.current),
    );
    expect(visible, contains('roomVisible=1 roomMounted=1'));
    expect(visible, contains('bootstrapError=true'));
    expect(
      visible,
      contains(
        'bootstrapErrorClass=AppFailure capabilitiesLoading=false capabilitiesError=true',
      ),
    );
    expect(visible, isNot(contains('private-account')));
    expect(visible, isNot(contains('secret')));
    expect(visible, isNot(contains('https:')));
    final resolved = nativeDualChatSurfaceDiagnostic(
      tester,
      const AsyncData(BootstrapState.error(AppFailure.validation('private'))),
      const AsyncLoading(),
    );
    expect(
      resolved,
      contains('bootstrapPhase=error bootstrapFailure=validation'),
    );
    expect(resolved, isNot(contains('private')));
    final unknown = nativeDualChatSurfaceDiagnostic(
      tester,
      AsyncError(Object(), StackTrace.current),
      const AsyncLoading(),
    );
    expect(unknown, contains('bootstrapErrorClass=unavailable'));
  });

  for (final locale in ['en', 'de']) {
    for (final branch in ['session', 'invitation', 'archive']) {
      testWidgets('private actual room branch; locale=$locale branch=$branch', (
        tester,
      ) async {
        final repository = FakeChatRepository(
          loadRoomTimelineHandler: (roomId) async {
            if (branch == 'session') {
              throw const ChatFailure.sessionRequired(
                'private provider details',
              );
            }
            return ChatRoomTimeline(
              roomId: roomId,
              roomTitle: 'Owned room',
              isInvite: false,
              canSendMessages: true,
              messages: [],
            );
          },
        );
        final room = ChatConversation(
          id: conversation.id,
          title: conversation.title,
          previewType: conversation.previewType,
          unreadCount: 0,
          isInvite: branch == 'invitation',
          isDirectMessage: false,
        );
        await tester.pumpWidget(
          ProviderScope(
            overrides: [
              chatRepositoryProvider.overrideWithValue(repository),
              preferencesStoreProvider.overrideWith(
                (ref) => InMemoryPreferencesStore(),
              ),
            ],
            child: MaterialApp(
              locale: Locale(locale),
              localizationsDelegates: AppLocalizations.localizationsDelegates,
              supportedLocales: AppLocalizations.supportedLocales,
              home: ChatRoomScreen(conversation: room),
            ),
          ),
        );
        await tester.pumpAndSettle();
        if (branch == 'archive') {
          final copy = AppLocalizations.of(
            tester.element(find.byType(ChatRoomScreen)),
          );
          await tester.tap(find.byTooltip(copy.chatRoomArchivedMessagesAction));
          await tester.pumpAndSettle();
        }
        final diagnostic = nativeDualChatSurfaceDiagnostic(
          tester,
          const AsyncData(BootstrapState.ready()),
          const AsyncLoading(),
        );
        expect(diagnostic, contains('roomVisible=1 roomMounted=1'));
        expect(
          diagnostic,
          contains(
            'sessionRequiredCount=${branch == 'session' ? 1 : 0} invitationCount=${branch == 'invitation' ? 1 : 0} archiveReturnCount=${branch == 'archive' ? 1 : 0}',
          ),
        );
        expect(find.byType(TextField), findsNothing);
        expect(diagnostic, isNot(contains('private')));
        expect(diagnostic, isNot(contains(conversation.id)));
      });
    }
  }

  for (final inset in [0.0, 350.0, 380.0]) {
    testWidgets(
      'phone Chat send retains selected tab with keyboard inset=$inset',
      (tester) async {
        tester.view.physicalSize = const Size(1206, 2622);
        tester.view.devicePixelRatio = 3;
        addTearDown(tester.view.resetPhysicalSize);
        addTearDown(tester.view.resetDevicePixelRatio);
        addTearDown(tester.view.resetViewInsets);
        final messages = <ChatMessage>[];
        final repository = FakeChatRepository(
          loadRoomTimelineHandler: (_) async => ChatRoomTimeline(
            roomId: conversation.id,
            roomTitle: conversation.title,
            isInvite: false,
            canSendMessages: true,
            messages: List.of(messages),
          ),
          sendMessageHandler: ({required roomId, required message}) async {
            await Future<void>.delayed(const Duration(milliseconds: 500));
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
        await tester.pumpAndSettle();
        final tabs = DefaultTabController.of(
          tester.element(find.byType(TabBar)),
        );
        expect(tabs.index, 0);
        tester.view.viewInsets = FakeViewPadding(bottom: inset * 3);
        await tester.pumpAndSettle();
        await sendNativeDualChatMessage(
          tester,
          'Owned message',
          deadline: DateTime.now().add(const Duration(seconds: 5)),
        );
        await tester.pumpAndSettle();
        expect(repository.sendMessageCalls, 1);
        expect(tabs.index, 0);
        expect(find.text('Owned message'), findsOneWidget);
        final composer = find.byType(TextField);
        expect(composer, findsOneWidget);
        expect(tester.widget<TextField>(composer).controller!.text, isEmpty);
        expect(
          nativeDualChatSurfaceDiagnostic(
            tester,
            const AsyncData(BootstrapState.ready()),
            const AsyncLoading(),
          ),
          contains('workspaceTabCount=1 workspaceTabIndex=0'),
        );
      },
    );
  }

  testWidgets('offscreen navigation target is scrolled then tapped once', (
    tester,
  ) async {
    var selected = 1;
    var taps = 0;
    await tester.pumpWidget(
      createTestApp(
        StatefulBuilder(
          builder: (context, setState) {
            final copy = AppLocalizations.of(context);
            return SingleChildScrollView(
              child: Column(
                children: [
                  const SizedBox(height: 1200),
                  NavigationBar(
                    selectedIndex: selected,
                    onDestinationSelected: (index) => setState(() {
                      selected = index;
                      taps++;
                    }),
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
                ],
              ),
            );
          },
        ),
      ),
    );
    await tester.pumpAndSettle();
    final copy = AppLocalizations.of(
      tester.element(find.byType(NavigationBar)),
    );
    final home = find.text(copy.navHome);
    expect(home.hitTestable(), findsNothing);
    await navigateNativeDualDestination(tester, Icons.home_outlined);
    await tester.pumpAndSettle();
    expect(selected, 0);
    expect(taps, 1);
    expect(home.hitTestable(), findsOneWidget);
  });

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
