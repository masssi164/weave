import 'dart:async';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart' as http_testing;
import 'package:weave/features/chat/domain/repositories/chat_repository.dart';
import 'package:flutter/material.dart';
import 'package:flutter/semantics.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:weave/core/persistence/shared_preferences_store.dart';
import 'package:weave/core/bootstrap/presentation/providers/app_bootstrap_provider.dart';
import 'package:weave/features/app/domain/entities/integration_invalidation.dart';
import 'package:weave/features/app/presentation/providers/workspace_invalidation_provider.dart';
import 'package:weave/features/app/domain/entities/workspace_capability_snapshot.dart';
import 'package:weave/features/app/presentation/providers/workspace_connection_provider.dart';
import 'package:weave/features/chat/data/services/archived_message_store.dart';
import 'package:weave/features/chat/domain/entities/chat_conversation.dart';
import 'package:weave/features/chat/domain/entities/chat_failure.dart';
import 'package:weave/features/chat/domain/entities/chat_message.dart';
import 'package:weave/features/chat/domain/entities/chat_room_timeline.dart';
import 'package:weave/features/chat/presentation/chat_room_screen.dart';
import 'package:weave/features/chat/presentation/chat_admission_gate.dart';
import 'package:weave/integrations/rust_matrix_core/data/services/matrix_session_access.dart';
import 'package:weave/l10n/generated/app_localizations.dart';
import 'package:weave/features/chat/presentation/providers/chat_repository_provider.dart';

import '../../helpers/fake_chat_repository.dart';
import '../../helpers/in_memory_stores.dart';
import '../../helpers/test_app.dart';

class _InvitationChatRepository extends FakeChatRepository
    implements ChatInvitationRepository {
  _InvitationChatRepository(this.join)
    : super(
        loadRoomTimelineHandler: (roomId) async => ChatRoomTimeline(
          roomId: roomId,
          roomTitle: 'Invitation',
          isInvite: false,
          canSendMessages: true,
          messages: [],
        ),
      );
  final Future<void> Function(String) join;
  @override
  Future<void> acceptInvitation(String roomId) => join(roomId);
}

const _ownerA =
    'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa';
const _ownerB =
    'bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb';

class _DraftProbeStore extends InMemoryPreferencesStore {
  _DraftProbeStore([super.initialValues]);
  final reads = <String>[];
  final writes = <String>[];
  final removals = <String>[];
  Future<String?> Function(String)? readDraft;
  Future<String?> Function(String)? readOther;
  Future<void> Function(String, String)? writeDraft;

  @override
  Future<String?> getString(String key) async {
    if (key.startsWith('chat.roomDraft.')) {
      reads.add(key);
      if (readDraft != null) return readDraft!(key);
    }
    if (!key.startsWith('chat.roomDraft.') && readOther != null) {
      return readOther!(key);
    }
    return super.getString(key);
  }

  @override
  Future<void> setString(String key, String value) async {
    if (key.startsWith('chat.roomDraft.')) {
      writes.add(key);
      await writeDraft?.call(key, value);
    }
    await super.setString(key, value);
  }

  @override
  Future<void> remove(String key) async {
    if (key.startsWith('chat.roomDraft.')) removals.add(key);
    await super.remove(key);
  }
}

class _HeldDraftRemovalStore extends InMemoryPreferencesStore {
  final removal = Completer<void>();

  @override
  Future<void> remove(String key) async {
    if (key.startsWith('chat.roomDraft.v2.')) await removal.future;
    await super.remove(key);
  }
}

void main() {
  const conversation = ChatConversation(
    id: '!room:home.internal',
    title: 'Project',
    previewType: ChatConversationPreviewType.text,
    unreadCount: 2,
    isInvite: false,
    isDirectMessage: false,
  );

  testWidgets('direct room route does not read a revoked timeline', (
    tester,
  ) async {
    final repository = FakeChatRepository();
    await tester.pumpWidget(
      createTestApp(
        const ChatAdmissionGate(
          child: ChatRoomScreen(conversation: conversation),
        ),
        overrides: [
          workspaceCapabilitySnapshotProvider.overrideWithValue(
            const AsyncData(
              WorkspaceCapabilitySnapshot(
                shellAccess: WorkspaceCapabilityState(
                  capability: WorkspaceCapability.shellAccess,
                  readiness: WorkspaceCapabilityReadiness.ready,
                ),
                chat: WorkspaceCapabilityState(
                  capability: WorkspaceCapability.chat,
                  readiness: WorkspaceCapabilityReadiness.blocked,
                  policyState: WorkspaceCapabilityPolicyState.policyBlocked,
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
                  readiness: WorkspaceCapabilityReadiness.unavailable,
                ),
              ),
            ),
          ),
          chatRepositoryProvider.overrideWithValue(repository),
        ],
      ),
    );
    await tester.pumpAndSettle();

    expect(repository.loadRoomTimelineCalls, 0);
    expect(find.byType(ChatRoomScreen), findsNothing);
  });

  ChatRoomTimeline buildTimeline({
    bool canSendMessages = true,
    List<ChatMessage>? messages,
    String? memberStorageScope,
  }) {
    return ChatRoomTimeline(
      roomId: conversation.id,
      roomTitle: conversation.title,
      memberStorageScope: memberStorageScope,
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

  for (final denied in [false, true]) {
    testWidgets('invited room requires confirmed acceptance; denied=$denied', (
      tester,
    ) async {
      final pending = Completer<void>();
      var joins = 0;
      final repository = _InvitationChatRepository((roomId) {
        expect(roomId, '!invited:api.weave.test');
        joins++;
        return pending.future;
      });
      const invitation = ChatConversation(
        id: '!invited:api.weave.test',
        title: 'Invitation',
        previewType: ChatConversationPreviewType.none,
        unreadCount: 0,
        isInvite: true,
        isDirectMessage: false,
      );
      await tester.pumpWidget(
        createTestApp(
          const ChatRoomScreen(conversation: invitation),
          overrides: overridesFor(repository),
        ),
      );
      await tester.pumpAndSettle();
      expect(repository.loadRoomTimelineCalls, 0);
      expect(find.byType(TextField), findsNothing);
      await tester.tap(find.byKey(const Key('chat-accept-invitation-button')));
      await tester.pump();
      expect(joins, 1);
      expect(repository.loadRoomTimelineCalls, 0);
      expect(find.byType(TextField), findsNothing);
      if (denied) {
        pending.completeError(const ChatFailure.protocol('Denied.'));
      } else {
        pending.complete();
      }
      await tester.pumpAndSettle();
      expect(repository.loadRoomTimelineCalls, denied ? 0 : 1);
      expect(
        find.byKey(const Key('chat-accept-invitation-button')),
        denied ? findsOneWidget : findsNothing,
      );
      expect(find.byType(TextField), denied ? findsNothing : findsOneWidget);
      if (denied) {
        expect(
          find.textContaining('invitation could not be accepted'),
          findsOneWidget,
        );
      }
    });
  }

  testWidgets(
    'receives a remote peer message while the admitted room stays open',
    (tester) async {
      var remoteArrived = false;
      final repository = FakeChatRepository(
        loadRoomTimelineHandler: (_) async => buildTimeline(
          messages: remoteArrived
              ? [
                  ChatMessage(
                    id: r'$remote',
                    senderId: '@peer:home.internal',
                    senderDisplayName: 'Peer',
                    sentAt: DateTime(2026, 4, 20, 12, 1),
                    isMine: false,
                    deliveryState: ChatMessageDeliveryState.sent,
                    contentType: ChatMessageContentType.text,
                    text: 'Incoming peer reply',
                  ),
                ]
              : [],
        ),
      );
      await tester.pumpWidget(
        createTestApp(
          const ChatRoomScreen(conversation: conversation),
          overrides: overridesFor(repository),
        ),
      );
      await tester.pumpAndSettle();
      expect(find.text('Incoming peer reply'), findsNothing);
      remoteArrived = true;
      // Time passes; no local gesture, route change, notifier or manual refresh.
      await tester.pump(const Duration(seconds: 3));
      await tester.pumpAndSettle();
      expect(find.text('Incoming peer reply'), findsOneWidget);
    },
  );

  testWidgets(
    'receive completion coalesces manual and resume reads without draft or focus flicker',
    (tester) async {
      final held = Completer<ChatRoomTimeline>();
      var active = 0;
      var maximum = 0;
      var calls = 0;
      final repository = FakeChatRepository(
        loadRoomTimelineHandler: (_) async {
          active++;
          if (active > maximum) maximum = active;
          final call = ++calls;
          try {
            if (call == 2) return await held.future;
            return buildTimeline();
          } finally {
            active--;
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
      final editable = find.byType(EditableText);
      await tester.enterText(find.byType(TextField), 'Unsent draft');
      await tester.showKeyboard(find.byType(TextField));
      final input = tester.widget<EditableText>(editable);
      expect(input.focusNode.hasFocus, isTrue);
      await tester.pump(const Duration(seconds: 2));
      expect(calls, 2);
      expect(find.text('Hey there'), findsOneWidget);
      expect(find.byType(CircularProgressIndicator), findsNothing);
      await tester.tap(find.byTooltip('Retry').first);
      tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.inactive);
      await tester.pump();
      tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.resumed);
      await tester.pump();
      await tester.pump(const Duration(seconds: 5));
      expect(calls, 2);
      expect(
        tester.widget<EditableText>(editable).controller,
        same(input.controller),
      );
      expect(input.controller.text, 'Unsent draft');
      expect(input.focusNode.hasFocus, isTrue);
      held.complete(buildTimeline());
      await tester.pumpAndSettle();
      expect(calls, 3);
      expect(maximum, 1);
      expect(input.controller.text, 'Unsent draft');
      expect(input.focusNode.hasFocus, isTrue);
    },
  );

  testWidgets(
    'hidden and inactive rooms pause receive and catch up immediately on resume',
    (tester) async {
      final visible = ValueNotifier(true);
      addTearDown(visible.dispose);
      final repository = FakeChatRepository(
        loadRoomTimelineHandler: (_) async => buildTimeline(),
      );
      await tester.pumpWidget(
        createTestApp(
          ValueListenableBuilder<bool>(
            valueListenable: visible,
            child: const ChatRoomScreen(conversation: conversation),
            builder: (_, enabled, child) =>
                TickerMode(enabled: enabled, child: child!),
          ),
          overrides: overridesFor(repository),
        ),
      );
      await tester.pumpAndSettle();
      expect(repository.loadRoomTimelineCalls, 1);
      visible.value = false;
      await tester.pump();
      await tester.pump(const Duration(seconds: 10));
      expect(repository.loadRoomTimelineCalls, 1);
      visible.value = true;
      await tester.pumpAndSettle();
      expect(repository.loadRoomTimelineCalls, 2);
      tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.inactive);
      await tester.pump(const Duration(seconds: 10));
      expect(repository.loadRoomTimelineCalls, 2);
      tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.resumed);
      await tester.pumpAndSettle();
      expect(repository.loadRoomTimelineCalls, 3);
      final container = ProviderScope.containerOf(
        tester.element(find.byType(ChatRoomScreen)),
      );
      expect(container.exists(appBootstrapProvider), isFalse);
    },
  );

  testWidgets(
    'routine receive failure keeps the timeline and waits before retrying',
    (tester) async {
      var fail = false;
      final repository = FakeChatRepository(
        loadRoomTimelineHandler: (_) async {
          if (fail) throw const ChatFailure.protocol('Unavailable');
          return buildTimeline();
        },
      );
      await tester.pumpWidget(
        createTestApp(
          const ChatRoomScreen(conversation: conversation),
          overrides: overridesFor(repository),
        ),
      );
      await tester.pumpAndSettle();
      fail = true;
      await tester.pump(const Duration(seconds: 2));
      await tester.pumpAndSettle();
      expect(repository.loadRoomTimelineCalls, 2);
      expect(find.text('Hey there'), findsOneWidget);
      expect(find.byType(TextField), findsOneWidget);
      await tester.pump(const Duration(milliseconds: 100));
      expect(repository.loadRoomTimelineCalls, 2);
      await tester.pump(const Duration(seconds: 2));
      await tester.pumpAndSettle();
      expect(repository.loadRoomTimelineCalls, 3);
    },
  );

  for (final invalidation in [false, true]) {
    testWidgets(
      'access loss rejects stale reads and pauses receive; invalidation=$invalidation',
      (tester) async {
        final held = Completer<ChatRoomTimeline>();
        var calls = 0;
        final repository = FakeChatRepository(
          loadRoomTimelineHandler: (_) async {
            calls++;
            if (calls == 2) {
              if (invalidation) return held.future;
              throw const ChatFailure.sessionRequired('Denied');
            }
            return buildTimeline();
          },
        );
        await tester.pumpWidget(
          createTestApp(
            const ChatRoomScreen(conversation: conversation),
            overrides: overridesFor(repository),
          ),
        );
        await tester.pumpAndSettle();
        await tester.pump(const Duration(seconds: 2));
        await tester.pump();
        if (invalidation) {
          final container = ProviderScope.containerOf(
            tester.element(find.byType(ChatRoomScreen)),
          );
          container
              .read(workspaceInvalidationProvider.notifier)
              .invalidate(
                integration: WorkspaceIntegration.appAuth,
                reason: IntegrationInvalidationReason.explicitSignOut,
              );
          await tester.pump();
          held.complete(buildTimeline());
        }
        await tester.pumpAndSettle();
        expect(find.text('Hey there'), findsNothing);
        expect(find.byType(TextField), findsNothing);
        await tester.pump(const Duration(seconds: 8));
        expect(calls, 2);
        // Explicit retry rechecks current authority through the same repository.
        await tester.tap(find.text('Retry').first);
        await tester.pumpAndSettle();
        expect(calls, 3);
        expect(find.text('Hey there'), findsOneWidget);
      },
    );
  }

  testWidgets(
    'send completion requests one fresh read behind an in-flight receive',
    (tester) async {
      final held = Completer<ChatRoomTimeline>();
      var calls = 0;
      final repository = FakeChatRepository(
        loadRoomTimelineHandler: (_) async {
          if (++calls == 2) return held.future;
          return buildTimeline();
        },
      );
      await tester.pumpWidget(
        createTestApp(
          const ChatRoomScreen(conversation: conversation),
          overrides: overridesFor(repository),
        ),
      );
      await tester.pumpAndSettle();
      await tester.pump(const Duration(seconds: 2));
      await tester.enterText(find.byType(TextField), 'Local message');
      await tester.tap(find.widgetWithText(FilledButton, 'Send'));
      await tester.pump();
      expect(repository.sendMessageCalls, 1);
      expect(calls, 2);
      held.complete(buildTimeline());
      await tester.pumpAndSettle();
      expect(calls, 3);
      expect(
        tester.widget<TextField>(find.byType(TextField)).controller!.text,
        isEmpty,
      );
    },
  );

  testWidgets(
    'disposing during receive never publishes or schedules another read',
    (tester) async {
      final held = Completer<ChatRoomTimeline>();
      final repository = FakeChatRepository(
        loadRoomTimelineHandler: (_) => held.future,
      );
      await tester.pumpWidget(
        createTestApp(
          const ChatRoomScreen(conversation: conversation),
          overrides: overridesFor(repository),
        ),
      );
      await tester.pump();
      expect(repository.loadRoomTimelineCalls, 1);
      await tester.pumpWidget(const SizedBox.shrink());
      held.complete(buildTimeline());
      await tester.pump(const Duration(seconds: 10));
      expect(repository.loadRoomTimelineCalls, 1);
      expect(tester.takeException(), isNull);
    },
  );

  testWidgets('covered routes pause receive until the original route returns', (
    tester,
  ) async {
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
    final navigator = tester.state<NavigatorState>(
      find.byType(Navigator).first,
    );
    final covered = navigator.push<void>(
      MaterialPageRoute(builder: (_) => const Scaffold(body: Text('Covered'))),
    );
    await tester.pumpAndSettle();
    final calls = repository.loadRoomTimelineCalls;
    await tester.pump(const Duration(seconds: 10));
    expect(repository.loadRoomTimelineCalls, calls);
    navigator.pop();
    await covered;
    await tester.pumpAndSettle();
    expect(repository.loadRoomTimelineCalls, calls + 1);
  });

  testWidgets('room replacement rejects a previous room receive result', (
    tester,
  ) async {
    final held = Completer<ChatRoomTimeline>();
    final selected = ValueNotifier(conversation);
    addTearDown(selected.dispose);
    final repository = FakeChatRepository(
      loadRoomTimelineHandler: (id) async {
        if (id == conversation.id) return held.future;
        return ChatRoomTimeline(
          roomId: id,
          roomTitle: 'Replacement',
          isInvite: false,
          canSendMessages: true,
          messages: [],
        );
      },
    );
    await tester.pumpWidget(
      createTestApp(
        ValueListenableBuilder<ChatConversation>(
          valueListenable: selected,
          builder: (_, room, _) => ChatRoomScreen(conversation: room),
        ),
        overrides: overridesFor(repository),
      ),
    );
    await tester.pump();
    expect(repository.loadRoomTimelineCalls, 1);
    selected.value = const ChatConversation(
      id: '!replacement:home.internal',
      title: 'Replacement',
      previewType: ChatConversationPreviewType.none,
      unreadCount: 0,
      isInvite: false,
      isDirectMessage: true,
    );
    await tester.pump();
    held.complete(buildTimeline());
    await tester.pumpAndSettle();
    expect(find.text('Hey there'), findsNothing);
    expect(find.text('Replacement'), findsAtLeastNWidgets(1));
    expect(repository.loadRoomTimelineCalls, 2);
  });

  testWidgets(
    'configuration repository replacement rejects a late receive until explicit recheck',
    (tester) async {
      final held = Completer<ChatRoomTimeline>();
      final old = FakeChatRepository(
        loadRoomTimelineHandler: (_) => held.future,
      );
      final current = FakeChatRepository(
        loadRoomTimelineHandler: (_) async => buildTimeline(messages: []),
      );
      final store = InMemoryPreferencesStore();
      final preferences = preferencesStoreProvider.overrideWithValue(store);
      final container = ProviderContainer(
        overrides: [chatRepositoryProvider.overrideWithValue(old), preferences],
      );
      addTearDown(container.dispose);
      await tester.pumpWidget(
        UncontrolledProviderScope(
          container: container,
          child: const MaterialApp(
            localizationsDelegates: AppLocalizations.localizationsDelegates,
            supportedLocales: AppLocalizations.supportedLocales,
            home: ChatRoomScreen(conversation: conversation),
          ),
        ),
      );
      await tester.pump();
      container.updateOverrides([
        chatRepositoryProvider.overrideWithValue(current),
        preferences,
      ]);
      await tester.pump();
      held.complete(buildTimeline());
      await tester.pumpAndSettle();
      expect(find.text('Hey there'), findsNothing);
      expect(current.loadRoomTimelineCalls, 0);
      await tester.tap(find.text('Retry').first);
      await tester.pumpAndSettle();
      expect(current.loadRoomTimelineCalls, 1);
      expect(find.byType(TextField), findsOneWidget);
    },
  );

  testWidgets('unaccepted invitations never start routine timeline reads', (
    tester,
  ) async {
    final repository = _InvitationChatRepository((_) async {});
    const invitation = ChatConversation(
      id: '!pending:home.internal',
      title: 'Invitation',
      previewType: ChatConversationPreviewType.none,
      unreadCount: 0,
      isInvite: true,
      isDirectMessage: false,
    );
    await tester.pumpWidget(
      createTestApp(
        const ChatRoomScreen(conversation: invitation),
        overrides: overridesFor(repository),
      ),
    );
    await tester.pumpAndSettle();
    await tester.pump(const Duration(seconds: 10));
    expect(repository.loadRoomTimelineCalls, 0);
    expect(
      find.byKey(const Key('chat-accept-invitation-button')),
      findsOneWidget,
    );
  });

  testWidgets(
    'receive denial clears pending send and rejects its later completion',
    (tester) async {
      final receive = Completer<ChatRoomTimeline>();
      final send = Completer<void>();
      var calls = 0;
      final repository = FakeChatRepository(
        loadRoomTimelineHandler: (_) async {
          if (++calls == 2) return receive.future;
          return buildTimeline();
        },
        sendMessageHandler: ({required roomId, required message}) =>
            send.future,
      );
      await tester.pumpWidget(
        createTestApp(
          const ChatRoomScreen(conversation: conversation),
          overrides: overridesFor(repository),
        ),
      );
      await tester.pumpAndSettle();
      await tester.pump(const Duration(seconds: 2));
      await tester.enterText(find.byType(TextField), 'Pending message');
      await tester.tap(find.widgetWithText(FilledButton, 'Send'));
      await tester.pump();
      expect(repository.sendMessageCalls, 1);
      receive.completeError(const ChatFailure.sessionRequired('Revoked'));
      await tester.pumpAndSettle();
      expect(find.text('Hey there'), findsNothing);
      expect(find.byType(TextField), findsNothing);
      send.complete();
      await tester.pumpAndSettle();
      await tester.pump(const Duration(seconds: 5));
      expect(calls, 2);
      expect(find.byType(TextField), findsNothing);
      expect(tester.takeException(), isNull);
    },
  );

  testWidgets(
    'background send completion stays valid and resume receives fresh state',
    (tester) async {
      final send = Completer<void>();
      final repository = FakeChatRepository(
        loadRoomTimelineHandler: (_) async => buildTimeline(),
        sendMessageHandler: ({required roomId, required message}) =>
            send.future,
      );
      await tester.pumpWidget(
        createTestApp(
          const ChatRoomScreen(conversation: conversation),
          overrides: overridesFor(repository),
        ),
      );
      await tester.pumpAndSettle();
      await tester.enterText(find.byType(TextField), 'Sending once');
      await tester.tap(find.widgetWithText(FilledButton, 'Send'));
      await tester.pump();
      tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.inactive);
      send.complete();
      await tester.pumpAndSettle();
      expect(repository.loadRoomTimelineCalls, 1);
      tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.resumed);
      await tester.pumpAndSettle();
      expect(repository.loadRoomTimelineCalls, 2);
      expect(repository.sendMessageCalls, 1);
      expect(
        tester.widget<TextField>(find.byType(TextField)).controller!.text,
        isEmpty,
      );
    },
  );

  testWidgets(
    'sign-out during post-send draft persistence cannot start a fresh receive',
    (tester) async {
      final store = _HeldDraftRemovalStore();
      final repository = FakeChatRepository(
        loadRoomTimelineHandler: (_) async =>
            buildTimeline(memberStorageScope: _ownerA),
      );
      await tester.pumpWidget(
        createTestApp(
          const ChatRoomScreen(conversation: conversation),
          overrides: overridesFor(repository, store: store),
        ),
      );
      await tester.pumpAndSettle();
      await tester.enterText(find.byType(TextField), 'One send');
      await tester.tap(find.widgetWithText(FilledButton, 'Send'));
      await tester.pump();
      expect(repository.sendMessageCalls, 1);
      final container = ProviderScope.containerOf(
        tester.element(find.byType(ChatRoomScreen)),
      );
      container
          .read(workspaceInvalidationProvider.notifier)
          .invalidate(
            integration: WorkspaceIntegration.appAuth,
            reason: IntegrationInvalidationReason.explicitSignOut,
          );
      await tester.pump();
      store.removal.complete();
      await tester.pumpAndSettle();
      expect(repository.loadRoomTimelineCalls, 1);
      expect(find.text('Hey there'), findsNothing);
      expect(find.byType(TextField), findsNothing);
    },
  );

  testWidgets(
    'authority replacement clears old-owner composer and cancels its draft debounce',
    (tester) async {
      final old = FakeChatRepository(
        loadRoomTimelineHandler: (_) async =>
            buildTimeline(memberStorageScope: _ownerA),
      );
      final current = FakeChatRepository(
        loadRoomTimelineHandler: (_) async =>
            buildTimeline(messages: [], memberStorageScope: _ownerB),
      );
      final store = InMemoryPreferencesStore();
      final preferences = preferencesStoreProvider.overrideWithValue(store);
      final container = ProviderContainer(
        overrides: [chatRepositoryProvider.overrideWithValue(old), preferences],
      );
      addTearDown(container.dispose);
      await tester.pumpWidget(
        UncontrolledProviderScope(
          container: container,
          child: const MaterialApp(
            localizationsDelegates: AppLocalizations.localizationsDelegates,
            supportedLocales: AppLocalizations.supportedLocales,
            home: ChatRoomScreen(conversation: conversation),
          ),
        ),
      );
      await tester.pumpAndSettle();
      await tester.enterText(find.byType(TextField), 'Old owner unsent text');
      // Replace authority before the existing 300ms persistence debounce fires.
      container.updateOverrides([
        chatRepositoryProvider.overrideWithValue(current),
        preferences,
      ]);
      await tester.pump();
      await tester.pump(const Duration(seconds: 1));
      expect(
        store.rawString(
          'chat.roomDraft.v2.$_ownerA.${Uri.encodeComponent(conversation.id)}',
        ),
        isNull,
      );
      await tester.tap(find.text('Retry').first);
      await tester.pumpAndSettle();
      expect(
        tester.widget<TextField>(find.byType(TextField)).controller!.text,
        isEmpty,
      );
      expect(find.text('Old owner unsent text'), findsNothing);
      expect(current.loadRoomTimelineCalls, 1);
    },
  );

  for (final locale in ['en', 'de']) {
    testWidgets(
      'metadata title, own sender and local time agree with semantics $locale',
      (tester) async {
        tester.view.physicalSize = const Size(390, 844);
        tester.view.devicePixelRatio = 1;
        addTearDown(tester.view.resetPhysicalSize);
        addTearDown(tester.view.resetDevicePixelRatio);
        tester.platformDispatcher.localesTestValue = [Locale(locale)];
        addTearDown(tester.platformDispatcher.clearLocalesTestValue);
        final semantics = tester.ensureSemantics();
        final instant = DateTime.utc(2026, 10, 15, 21, 51);
        final repository = FakeChatRepository(
          loadRoomTimelineHandler: (_) async => ChatRoomTimeline(
            roomId: conversation.id,
            roomTitle: '',
            isInvite: false,
            canSendMessages: true,
            messages: [
              ChatMessage(
                id: 'own-echo',
                senderId: 'opaque-id',
                senderDisplayName: 'opaque-acct',
                sentAt: instant,
                isMine: true,
                deliveryState: ChatMessageDeliveryState.sent,
                contentType: ChatMessageContentType.text,
                text: 'Actual own echo',
              ),
            ],
          ),
        );
        await tester.pumpWidget(
          createTestApp(
            Builder(
              builder: (context) => MediaQuery(
                data: MediaQuery.of(
                  context,
                ).copyWith(textScaler: const TextScaler.linear(1.4)),
                child: const ChatRoomScreen(conversation: conversation),
              ),
            ),
            overrides: overridesFor(repository),
          ),
        );
        await tester.pumpAndSettle();
        final context = tester.element(find.byType(ChatRoomScreen));
        final you = AppLocalizations.of(context).chatRoomYouLabel;
        final localTime = MaterialLocalizations.of(
          context,
        ).formatTimeOfDay(TimeOfDay.fromDateTime(instant.toLocal()));
        expect(find.text('Project'), findsWidgets);
        expect(find.text('opaque-acct'), findsNothing);
        expect(find.text(you), findsOneWidget);
        expect(find.text(localTime), findsOneWidget);
        expect(
          find.bySemanticsLabel('$you. Actual own echo. $localTime'),
          findsOneWidget,
        );
        semantics.dispose();
      },
    );
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

    final composer = find.byType(TextField);
    await tester.enterText(composer, 'Reply sent');
    Finder focused() => find.byElementPredicate(
      (element) =>
          identical(element, FocusManager.instance.primaryFocus?.context),
    );
    expect(find.ancestor(of: focused(), matching: composer), findsOneWidget);
    await tester.sendKeyEvent(LogicalKeyboardKey.tab);
    await tester.pump();
    expect(
      find.ancestor(
        of: focused(),
        matching: find.widgetWithText(FilledButton, 'Send'),
      ),
      findsOneWidget,
    );
    await tester.sendKeyEvent(LogicalKeyboardKey.enter);
    await tester.pumpAndSettle();

    expect(repository.sendMessageCalls, 1);
    expect(find.text('Reply sent'), findsOneWidget);
  });

  for (final locale in ['en', 'de']) {
    for (final retry in [false, true]) {
      testWidgets(
        'shows an own send from older history with keyboard $locale retry=$retry',
        (tester) async {
          tester.view.physicalSize = const Size(390, 844);
          tester.view.devicePixelRatio = 1;
          tester.view.viewInsets = const FakeViewPadding(bottom: 280);
          addTearDown(tester.view.resetPhysicalSize);
          addTearDown(tester.view.resetDevicePixelRatio);
          addTearDown(tester.view.resetViewInsets);
          tester.platformDispatcher.localesTestValue = [Locale(locale)];
          addTearDown(tester.platformDispatcher.clearLocalesTestValue);

          ChatMessage chatMessage(
            String id,
            String text, {
            bool mine = false,
          }) => ChatMessage(
            id: id,
            senderId: mine ? '@me:home.internal' : '@peer:home.internal',
            senderDisplayName: mine ? 'Me' : 'Peer',
            sentAt: DateTime(2026, 4, 20, 12),
            isMine: mine,
            deliveryState: ChatMessageDeliveryState.sent,
            contentType: ChatMessageContentType.text,
            text: text,
          );
          final messages = [
            for (var index = 0; index < 30; index++)
              chatMessage('history-$index', 'History $index'),
          ];
          final sent = Completer<void>();
          addTearDown(() {
            if (!sent.isCompleted) sent.complete();
          });
          var attempts = 0;
          const outgoing = 'Reply after reading older history';
          final outgoingBubble = find.byWidgetPredicate(
            (widget) => widget is Text && widget.data == outgoing,
          );
          final repository = FakeChatRepository(
            loadRoomTimelineHandler: (_) async =>
                buildTimeline(messages: List.of(messages)),
            sendMessageHandler: ({required roomId, required message}) async {
              attempts++;
              if (retry && attempts == 1) {
                throw const ChatFailure.protocol('Connection unavailable.');
              }
              await sent.future;
              messages.add(chatMessage('sent-reply', message, mine: true));
            },
          );
          await tester.pumpWidget(
            createTestApp(
              const ChatRoomScreen(conversation: conversation),
              overrides: overridesFor(repository),
            ),
          );
          await tester.pumpAndSettle();
          final copy = AppLocalizations.of(
            tester.element(find.byType(ChatRoomScreen)),
          );
          final timeline = find.byWidgetPredicate(
            (widget) => widget is ListView && widget.reverse,
          );
          final scrollable = find.descendant(
            of: timeline,
            matching: find.byType(Scrollable),
          );
          Future<void> readOlderHistory() async {
            await tester.scrollUntilVisible(
              find.text('History 0'),
              300,
              scrollable: scrollable,
            );
            await Scrollable.ensureVisible(
              tester.element(find.text('History 0')),
              alignment: 0.5,
            );
            await tester.pumpAndSettle();
            expect(find.text('History 0').hitTestable(), findsOneWidget);
          }

          await readOlderHistory();
          messages.add(chatMessage('incoming', 'Incoming while reading'));
          await tester.pump(const Duration(seconds: 3));
          await tester.pumpAndSettle();
          expect(
            find.textContaining('History ').hitTestable(),
            findsAtLeastNWidgets(1),
          );
          expect(
            find.text('Incoming while reading').hitTestable(),
            findsNothing,
          );

          await tester.enterText(find.byType(TextField), outgoing);
          final send = find.widgetWithText(
            FilledButton,
            copy.chatRoomSendButton,
          );
          expect(send.hitTestable(), findsOneWidget);
          await tester.tap(send);
          await tester.pumpAndSettle();
          expect(outgoingBubble.hitTestable(), findsOneWidget);
          if (retry) {
            final retrySend = find.descendant(
              of: find.byType(MaterialBanner),
              matching: find.widgetWithText(
                TextButton,
                copy.chatRoomRetrySendAction,
              ),
            );
            await readOlderHistory();
            expect(retrySend.hitTestable(), findsOneWidget);
            await tester.tap(retrySend);
            await tester.pumpAndSettle();
            expect(outgoingBubble.hitTestable(), findsOneWidget);
          }
          sent.complete();
          await tester.pumpAndSettle();
          expect(outgoingBubble, findsOneWidget);
          expect(outgoingBubble.hitTestable(), findsOneWidget);
          expect(
            tester.widget<TextField>(find.byType(TextField)).controller!.text,
            isEmpty,
          );
          expect(attempts, retry ? 2 : 1);
          expect(tester.takeException(), isNull);
        },
      );
    }
  }

  testWidgets(
    'a persisted draft from member A is not restored for member B in the same room',
    (tester) async {
      final store = InMemoryPreferencesStore();
      final memberA = FakeChatRepository(
        loadRoomTimelineHandler: (_) async =>
            buildTimeline(memberStorageScope: _ownerA),
      );
      await tester.pumpWidget(
        createTestApp(
          const ChatRoomScreen(conversation: conversation),
          overrides: overridesFor(memberA, store: store),
        ),
      );
      await tester.pumpAndSettle();
      await tester.enterText(find.byType(TextField), 'Private member A draft');
      await tester.pump(const Duration(milliseconds: 400));
      await tester.pumpWidget(const SizedBox.shrink());
      final memberB = FakeChatRepository(
        loadRoomTimelineHandler: (_) async =>
            buildTimeline(memberStorageScope: _ownerB),
      );
      await tester.pumpWidget(
        createTestApp(
          const ChatRoomScreen(conversation: conversation),
          overrides: overridesFor(memberB, store: store),
        ),
      );
      await tester.pumpAndSettle();
      expect(
        tester.widget<TextField>(find.byType(TextField)).controller!.text,
        isEmpty,
      );
      expect(find.text('Private member A draft'), findsNothing);
      await tester.pumpWidget(const SizedBox.shrink());
      await tester.pumpWidget(
        createTestApp(
          const ChatRoomScreen(conversation: conversation),
          overrides: overridesFor(memberA, store: store),
        ),
      );
      await tester.pumpAndSettle();
      expect(
        tester.widget<TextField>(find.byType(TextField)).controller!.text,
        'Private member A draft',
      );
    },
  );

  for (final owner in [null, 'malformed', _ownerA]) {
    testWidgets(
      'legacy drafts are never adopted and unknown owners cannot persist; owner=$owner',
      (tester) async {
        final legacy =
            'chat.roomDraft.v1.${Uri.encodeComponent(conversation.id)}';
        final store = _DraftProbeStore({legacy: 'Legacy ownerless text'});
        final repository = FakeChatRepository(
          loadRoomTimelineHandler: (_) async =>
              buildTimeline(memberStorageScope: owner),
        );
        await tester.pumpWidget(
          createTestApp(
            const ChatRoomScreen(conversation: conversation),
            overrides: overridesFor(repository, store: store),
          ),
        );
        await tester.pumpAndSettle();
        expect(
          tester.widget<TextField>(find.byType(TextField)).controller!.text,
          isEmpty,
        );
        expect(store.reads, isNot(contains(legacy)));
        await tester.enterText(find.byType(TextField), 'Current draft');
        await tester.pump(const Duration(milliseconds: 400));
        if (owner == _ownerA) {
          expect(
            store.writes.single,
            'chat.roomDraft.v2.$_ownerA.${Uri.encodeComponent(conversation.id)}',
          );
        } else {
          expect(store.reads, isEmpty);
          expect(store.writes, isEmpty);
          await tester.tap(find.widgetWithText(FilledButton, 'Send'));
          await tester.pumpAndSettle();
          expect(store.removals, isEmpty);
        }
        expect(store.rawString(legacy), 'Legacy ownerless text');
      },
    );
  }

  testWidgets(
    'deferred draft restoration never replaces typing followed by deletion',
    (tester) async {
      final held = Completer<String?>();
      final store = _DraftProbeStore()..readDraft = (_) => held.future;
      final repository = FakeChatRepository(
        loadRoomTimelineHandler: (_) async =>
            buildTimeline(memberStorageScope: _ownerA),
      );
      await tester.pumpWidget(
        createTestApp(
          const ChatRoomScreen(conversation: conversation),
          overrides: overridesFor(repository, store: store),
        ),
      );
      await tester.pumpAndSettle();
      expect(store.reads, hasLength(1));
      await tester.enterText(find.byType(TextField), 'New typing');
      await tester.enterText(find.byType(TextField), '');
      held.complete('Older persisted text');
      await tester.pumpAndSettle();
      expect(
        tester.widget<TextField>(find.byType(TextField)).controller!.text,
        isEmpty,
      );
      expect(find.text('Draft restored from this device.'), findsNothing);
    },
  );

  testWidgets(
    'verified owner change rejects a held old draft without workspace invalidation',
    (tester) async {
      final keyA =
          'chat.roomDraft.v2.$_ownerA.${Uri.encodeComponent(conversation.id)}';
      final keyB =
          'chat.roomDraft.v2.$_ownerB.${Uri.encodeComponent(conversation.id)}';
      final held = Completer<String?>();
      var owner = _ownerA;
      final store = _DraftProbeStore({keyB: 'Member B persisted text'});
      store.readDraft = (key) =>
          key == keyA ? held.future : Future.value(store.rawString(key));
      final repository = FakeChatRepository(
        loadRoomTimelineHandler: (_) async =>
            buildTimeline(memberStorageScope: owner),
      );
      await tester.pumpWidget(
        createTestApp(
          const ChatRoomScreen(conversation: conversation),
          overrides: overridesFor(repository, store: store),
        ),
      );
      await tester.pumpAndSettle();
      owner = _ownerB;
      await tester.pump(const Duration(seconds: 2));
      await tester.pumpAndSettle();
      expect(
        tester.widget<TextField>(find.byType(TextField)).controller!.text,
        'Member B persisted text',
      );
      held.complete('Member A private text');
      await tester.pumpAndSettle();
      expect(
        tester.widget<TextField>(find.byType(TextField)).controller!.text,
        'Member B persisted text',
      );
      expect(store.writes, isEmpty);
    },
  );

  testWidgets(
    'an already started old-owner draft write never retargets the new owner',
    (tester) async {
      final keyA =
          'chat.roomDraft.v2.$_ownerA.${Uri.encodeComponent(conversation.id)}';
      final keyB =
          'chat.roomDraft.v2.$_ownerB.${Uri.encodeComponent(conversation.id)}';
      final held = Completer<void>();
      var owner = _ownerA;
      final store = _DraftProbeStore()..writeDraft = (_, _) => held.future;
      final repository = FakeChatRepository(
        loadRoomTimelineHandler: (_) async =>
            buildTimeline(memberStorageScope: owner),
      );
      await tester.pumpWidget(
        createTestApp(
          const ChatRoomScreen(conversation: conversation),
          overrides: overridesFor(repository, store: store),
        ),
      );
      await tester.pumpAndSettle();
      await tester.enterText(find.byType(TextField), 'Member A unsent');
      await tester.pump(const Duration(milliseconds: 400));
      expect(store.writes, [keyA]);
      owner = _ownerB;
      await tester.pump(const Duration(seconds: 2));
      await tester.pumpAndSettle();
      expect(
        tester.widget<TextField>(find.byType(TextField)).controller!.text,
        isEmpty,
      );
      held.complete();
      await tester.pumpAndSettle();
      expect(store.rawString(keyA), 'Member A unsent');
      expect(store.rawString(keyB), isNull);
      expect(store.writes, [keyA]);
    },
  );

  testWidgets(
    'verified owner change clears old content before delayed local restoration',
    (tester) async {
      final held = Completer<String?>();
      var owner = _ownerA;
      final store = _DraftProbeStore();
      final repository = FakeChatRepository(
        loadRoomTimelineHandler: (_) async => buildTimeline(
          memberStorageScope: owner,
          messages: owner == _ownerA ? null : [],
        ),
      );
      await tester.pumpWidget(
        createTestApp(
          const ChatRoomScreen(conversation: conversation),
          overrides: overridesFor(repository, store: store),
        ),
      );
      await tester.pumpAndSettle();
      expect(find.text('Hey there'), findsOneWidget);
      await tester.enterText(find.byType(TextField), 'Member A local text');
      store.readOther = (key) =>
          key.startsWith(ArchivedMessageStore.storageKeyPrefix)
          ? held.future
          : Future.value(store.rawString(key));
      owner = _ownerB;
      await tester.pump(const Duration(seconds: 2));
      await tester.pump();
      expect(find.text('Hey there'), findsNothing);
      expect(find.text('Member A local text'), findsNothing);
      held.complete(null);
      await tester.pumpAndSettle();
      expect(
        tester.widget<TextField>(find.byType(TextField)).controller!.text,
        isEmpty,
      );
    },
  );

  testWidgets('restores a local unsent draft for the room', (tester) async {
    final semantics = tester.ensureSemantics();
    final repository = FakeChatRepository(
      loadRoomTimelineHandler: (_) async =>
          buildTimeline(memberStorageScope: _ownerA),
    );
    final store = InMemoryPreferencesStore({
      'chat.roomDraft.v2.$_ownerA.${Uri.encodeComponent(conversation.id)}':
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
        RegExp('You.*Unsupported message.*Not sent'),
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

  testWidgets('clears a loaded timeline after authority is revoked', (
    tester,
  ) async {
    var failureOnRefresh = false;
    final repository = FakeChatRepository(
      loadRoomTimelineHandler: (_) async {
        if (failureOnRefresh) {
          throw const ChatFailure.sessionRequired('M_FORBIDDEN');
        }
        return buildTimeline();
      },
    );
    await tester.pumpWidget(
      createTestApp(
        const ChatRoomScreen(conversation: conversation),
        overrides: overridesFor(repository),
      ),
    );
    await tester.pumpAndSettle();
    expect(find.text('Hey there'), findsOneWidget);

    failureOnRefresh = true;
    await tester.tap(find.byTooltip('Retry').first);
    await tester.pumpAndSettle();

    expect(find.text('Hey there'), findsNothing);
    expect(find.text('Alex'), findsNothing);
    expect(find.text('Project'), findsNothing);
    expect(
      find.text('Raw room provider detail should not render.'),
      findsNothing,
    );
    expect(find.text('Retry'), findsAtLeastNWidgets(1));
    expect(find.byType(TextField), findsNothing);
  });

  for (final locale in ['en', 'de']) {
    testWidgets(
      'retries a send after unavailable admission with keyboard $locale',
      (tester) async {
        tester.view.physicalSize = const Size(390, 844);
        tester.view.devicePixelRatio = 1;
        tester.view.viewInsets = const FakeViewPadding(bottom: 280);
        addTearDown(tester.view.resetPhysicalSize);
        addTearDown(tester.view.resetDevicePixelRatio);
        addTearDown(tester.view.resetViewInsets);
        tester.platformDispatcher.localesTestValue = [Locale(locale)];
        addTearDown(tester.platformDispatcher.clearLocalesTestValue);
        final semantics = tester.ensureSemantics();
        try {
          var sendAttempts = 0;
          final client = http_testing.MockClient(
            (_) async => http.Response(
              'Raw send provider detail should not render.',
              503,
            ),
          );
          addTearDown(client.close);
          final repository = FakeChatRepository(
            loadRoomTimelineHandler: (_) async => buildTimeline(),
            sendMessageHandler: ({required roomId, required message}) async {
              sendAttempts++;
              expect(roomId, conversation.id);
              expect(message, 'Retry me');
              if (sendAttempts == 1) {
                await GeneratedMatrixSessionAccess(
                  httpClient: client,
                ).authorize(
                  userApiBaseUrl: Uri.parse('https://api.weave.test/api'),
                  weaveAccessToken: 'test-member-session',
                  expectedSubject: 'member-1',
                  expectedIssuer: Uri.parse(
                    'https://auth.weave.test/realms/weave',
                  ),
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
          final copy = AppLocalizations.of(
            tester.element(find.byType(ChatRoomScreen)),
          );
          final summary = find.text(
            copy.channelWorkspaceSummaryTitle(conversation.title),
          );
          expect(summary, findsOneWidget);

          await tester.enterText(find.byType(TextField), 'Retry me');
          final send = find.widgetWithText(
            FilledButton,
            copy.chatRoomSendButton,
          );
          expect(send.hitTestable(), findsOneWidget);
          await tester.tap(send);
          await tester.pumpAndSettle();

          expect(find.text('Retry me'), findsAtLeastNWidgets(1));
          expect(find.text(copy.chatRoomMessageFailedStatus), findsOneWidget);
          expect(summary, findsNothing);
          final timeline = find.byWidgetPredicate(
            (widget) => widget is ListView && widget.reverse,
          );
          final scrollable = find.descendant(
            of: timeline,
            matching: find.byType(Scrollable),
          );
          await tester.scrollUntilVisible(
            find.text('Hey there'),
            100,
            scrollable: scrollable,
          );
          expect(find.text('Hey there'), findsOneWidget);
          await tester.scrollUntilVisible(
            find.text(copy.chatRoomMessageFailedStatus),
            -100,
            scrollable: scrollable,
          );
          expect(
            tester.widget<TextField>(find.byType(TextField)).controller!.text,
            'Retry me',
          );
          expect(find.text(copy.chatRoomSendFailureMessage), findsOneWidget);
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
                        copy.chatRoomSendFailureMessage,
                      ) ??
                      false),
            ),
            findsOneWidget,
          );
          expect(find.text(copy.chatRoomRetrySendAction), findsOneWidget);
          _expectSemanticTapAction(
            tester,
            find.byTooltip(copy.chatRoomRetrySendAction),
            label: copy.chatRoomRetrySendAction,
          );

          final retry = find.descendant(
            of: find.byType(MaterialBanner),
            matching: find.widgetWithText(
              TextButton,
              copy.chatRoomRetrySendAction,
            ),
          );
          expect(retry.hitTestable(), findsOneWidget);
          await tester.tap(retry);
          await tester.pumpAndSettle();

          expect(repository.sendMessageCalls, 2);
          expect(find.text(copy.chatRoomMessageFailedStatus), findsNothing);
          expect(
            tester.widget<TextField>(find.byType(TextField)).controller!.text,
            isEmpty,
          );
          expect(find.text(copy.chatRoomSendFailureMessage), findsNothing);
          expect(summary, findsOneWidget);
        } finally {
          semantics.dispose();
        }
      },
    );
  }

  testWidgets('announces a pending secure peer device with retry', (
    tester,
  ) async {
    final semantics = tester.ensureSemantics();
    final repository = FakeChatRepository(
      loadRoomTimelineHandler: (_) async => buildTimeline(),
      sendMessageHandler: ({required roomId, required message}) async {
        throw const ChatFailure.peerDevicePending(
          'Waiting for a participant’s secure device. Try again shortly or contact support if this continues.',
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

    await tester.enterText(find.byType(TextField), 'Wait for secure device');
    await tester.tap(find.text('Send'));
    await tester.pumpAndSettle();

    expect(
      find.text(
        'Waiting for a participant’s secure device. Try again shortly or contact support if this continues.',
      ),
      findsOneWidget,
    );
    expect(find.text('Retry send'), findsOneWidget);
    expect(
      find.byWidgetPredicate(
        (widget) =>
            widget is Semantics &&
            widget.properties.liveRegion == true &&
            widget.child is Text &&
            ((widget.child! as Text).data?.startsWith(
                  'Waiting for a participant’s secure device',
                ) ??
                false),
      ),
      findsOneWidget,
    );
    semantics.dispose();
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
