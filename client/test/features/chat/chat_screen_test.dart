import 'package:weave/features/chat/domain/repositories/chat_repository.dart';
import 'dart:async';

import 'package:flutter/semantics.dart';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:go_router/go_router.dart';
import 'package:weave/core/persistence/shared_preferences_store.dart';
import 'package:weave/core/theme/app_theme.dart';
import 'package:weave/features/app/domain/entities/integration_invalidation.dart';
import 'package:weave/features/app/domain/entities/member_space_access_snapshot.dart';
import 'package:weave/features/app/domain/entities/workspace_capability_snapshot.dart';
import 'package:weave/features/app/presentation/providers/workspace_connection_provider.dart';
import 'package:weave/features/app/presentation/providers/workspace_invalidation_provider.dart';
import 'package:weave/features/chat/domain/entities/chat_conversation.dart';
import 'package:weave/features/chat/domain/entities/chat_message.dart';
import 'package:weave/features/chat/domain/entities/chat_participant.dart';
import 'package:weave/features/chat/domain/entities/chat_failure.dart';
import 'package:weave/features/chat/domain/entities/chat_room_timeline.dart';
import 'package:weave/features/chat/domain/entities/chat_security_state.dart';
import 'package:weave/features/chat/presentation/chat_room_screen.dart';
import 'package:weave/features/chat/presentation/chat_screen.dart';
import 'package:weave/features/chat/presentation/providers/chat_provider.dart';
import 'package:weave/features/chat/presentation/providers/chat_repository_provider.dart';
import 'package:weave/features/chat/presentation/providers/chat_security_repository_provider.dart';
import 'package:weave/l10n/generated/app_localizations.dart';
import 'package:weave/integrations/weave_api/presentation/providers/weave_api_provider.dart';

import '../../helpers/fake_chat_repository.dart';
import '../../helpers/in_memory_stores.dart';
import '../../helpers/fake_chat_security_repository.dart';
import '../../helpers/test_app.dart';

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

final _blockedChatSnapshot = WorkspaceCapabilitySnapshot(
  shellAccess: _readyChatSnapshot.shellAccess,
  chat: const WorkspaceCapabilityState(
    capability: WorkspaceCapability.chat,
    readiness: WorkspaceCapabilityReadiness.blocked,
    policyState: WorkspaceCapabilityPolicyState.policyBlocked,
  ),
  files: _readyChatSnapshot.files,
  calendar: _readyChatSnapshot.calendar,
  boards: _readyChatSnapshot.boards,
);

class _ChatCapabilityController
    extends Notifier<AsyncValue<WorkspaceCapabilitySnapshot>> {
  @override
  AsyncValue<WorkspaceCapabilitySnapshot> build() =>
      const AsyncData(_readyChatSnapshot);

  void show(WorkspaceCapabilitySnapshot snapshot) {
    state = AsyncData(snapshot);
  }
}

const _defaultChatSpace = MemberSpaceAccessSnapshot(
  visibleSpaceRefs: {'workspace-default'},
  defaultSpaceReadable: true,
  displayNames: {'workspace-default': 'Product team'},
);

Widget _chatTestApp(
  Widget child, {
  List<dynamic> overrides = const [],
  MemberSpaceAccessSnapshot? spaces = _defaultChatSpace,
  bool provideReadyCapabilities = true,
}) {
  return createTestApp(
    child,
    overrides: [
      if (provideReadyCapabilities)
        workspaceCapabilitySnapshotProvider.overrideWithValue(
          const AsyncData(_readyChatSnapshot),
        ),
      if (spaces != null)
        weaveApiMemberSpacesProvider.overrideWith((ref) async => spaces),
      ...overrides,
    ],
  );
}

Widget _chatTestRouterApp(
  GoRouter router, {
  List<dynamic> overrides = const [],
}) {
  return createTestRouterApp(
    router,
    overrides: [
      workspaceCapabilitySnapshotProvider.overrideWithValue(
        const AsyncData(_readyChatSnapshot),
      ),
      weaveApiMemberSpacesProvider.overrideWith(
        (ref) async => _defaultChatSpace,
      ),
      ...overrides,
    ],
  );
}

class _ParticipantChatRepository extends FakeChatRepository
    implements ChatParticipantRepository {
  _ParticipantChatRepository({
    this.refuse = true,
    this.searchHandler,
    this.createFailure,
    this.createHandler,
  }) : super(
         loadConversationsHandler: () async => [],
         loadRoomTimelineHandler: (room) async => ChatRoomTimeline(
           roomId: room,
           roomTitle: 'Controlled conversation',
           isInvite: false,
           canSendMessages: true,
           messages: [],
         ),
       );
  final bool refuse;
  final Future<List<ChatParticipant>> Function(String, String?)? searchHandler;
  final Object? createFailure;
  final Future<ChatConversation> Function(String)? createHandler;
  List<ChatParticipant>? invited;
  bool? direct;
  String? createdSpace;
  final searchedSpaces = <String?>[];
  @override
  Future<List<ChatParticipant>> searchMembers(
    String query, {
    String? spaceRef,
  }) async {
    searchedSpaces.add(spaceRef);
    return searchHandler?.call(query, spaceRef) ??
        const [
          ChatParticipant(
            userId: '@member:api.weave.test',
            displayName: 'Team member',
          ),
        ];
  }

  @override
  Future<ChatConversation> createConversation({
    required String title,
    String? spaceRef,
  }) => createConversationWithParticipants(
    title: title,
    participants: const [],
    spaceRef: spaceRef,
  );
  @override
  Future<ChatConversation> createConversationWithParticipants({
    required String title,
    required List<ChatParticipant> participants,
    bool isDirect = false,
    String? spaceRef,
  }) async {
    invited = participants;
    direct = isDirect;
    createdSpace = spaceRef;
    if (createFailure case final failure?) throw failure;
    if (refuse) throw const ChatFailure.protocol('Creation refused');
    if (createHandler case final handler?) return handler(title);
    return ChatConversation(
      id: '!created:api.weave.test',
      title: title,
      previewType: ChatConversationPreviewType.none,
      unreadCount: 0,
      isInvite: false,
      isDirectMessage: isDirect,
    );
  }
}

Future<void> _openChatCreation(WidgetTester tester) async {
  await tester.tap(find.byKey(const Key('chat-create-conversation-button')));
  await tester.pumpAndSettle();
}

Future<void> _chooseChatSpace(WidgetTester tester, String name) async {
  final selector = find.byKey(const Key('chat-create-space-selector'));
  await tester.ensureVisible(selector);
  await tester.tap(selector);
  await tester.pump();
  // A previous Space search may still be in flight while the menu opens.
  await tester.pump(const Duration(milliseconds: 300));
  await tester.tap(find.text(name).last);
  await tester.pumpAndSettle();
}

Future<void> _submitChatCreation(WidgetTester tester) async {
  final submit = find.byKey(
    const Key('chat-create-conversation-submit-button'),
  );
  await tester.ensureVisible(submit);
  await tester.tap(submit);
  await tester.pumpAndSettle();
}

class _ChatSpacesController extends Notifier<MemberSpaceAccessSnapshot?> {
  @override
  MemberSpaceAccessSnapshot? build() => _defaultChatSpace;

  void show(MemberSpaceAccessSnapshot? access) => state = access;
}

void main() {
  testWidgets('empty nondefault Space is named and receives new conversation', (
    tester,
  ) async {
    final repository = _ParticipantChatRepository(refuse: false);
    await tester.pumpWidget(
      _chatTestApp(
        const ChatScreen(),
        spaces: const MemberSpaceAccessSnapshot(
          visibleSpaceRefs: {'opaque-design'},
          defaultSpaceReadable: false,
          displayNames: {'opaque-design': 'Design'},
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
    await _openChatCreation(tester);
    final selector = find.byKey(const Key('chat-create-space-selector'));
    expect(
      tester.widget<DropdownButton<String>>(selector).value,
      'opaque-design',
    );
    expect(find.text('Design'), findsOneWidget);
    expect(find.textContaining('opaque-design'), findsNothing);
    expect(tester.getSize(selector).height, greaterThanOrEqualTo(48));
    await tester.enterText(
      find.byKey(const Key('chat-create-conversation-name-field')),
      'Design decisions',
    );
    final mode = find.byKey(const Key('chat-create-direct-switch'));
    await tester.ensureVisible(mode);
    await tester.tap(mode);
    await tester.pumpAndSettle();
    await _submitChatCreation(tester);
    expect(repository.createdSpace, 'opaque-design');
    expect(repository.invited, isEmpty);
    expect(repository.direct, isFalse);
    expect(find.byType(ChatRoomScreen), findsOneWidget);
  });

  testWidgets(
    'multiple named Spaces require explicit disambiguated selection',
    (tester) async {
      final repository = _ParticipantChatRepository(refuse: false);
      await tester.pumpWidget(
        _chatTestApp(
          const ChatScreen(),
          spaces: const MemberSpaceAccessSnapshot(
            visibleSpaceRefs: {'opaque-b', 'opaque-a'},
            defaultSpaceReadable: false,
            displayNames: {'opaque-a': 'Research', 'opaque-b': 'Research'},
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
      await _openChatCreation(tester);
      final selector = find.byKey(const Key('chat-create-space-selector'));
      final participants = find.byKey(
        const Key('chat-create-conversation-participant-field'),
      );
      expect(tester.widget<DropdownButton<String>>(selector).value, isNull);
      expect(tester.widget<TextField>(participants).enabled, isFalse);
      await tester.enterText(
        find.byKey(const Key('chat-create-conversation-name-field')),
        'Research discussion',
      );
      await _submitChatCreation(tester);
      final l10n = AppLocalizations.of(tester.element(selector));
      expect(find.text(l10n.chatCreateSpaceRequired), findsOneWidget);
      expect(repository.invited, isNull);
      expect(repository.searchedSpaces, isEmpty);
      await _chooseChatSpace(tester, 'Research (2)');
      expect(find.textContaining('opaque-'), findsNothing);
      await tester.enterText(participants, 'Team');
      await tester.pump(const Duration(milliseconds: 300));
      await tester.pumpAndSettle();
      final peer = find.byKey(
        const ValueKey('chat-participant-result-@member:api.weave.test'),
      );
      await tester.ensureVisible(peer);
      await tester.tap(peer);
      await tester.pumpAndSettle();
      await _submitChatCreation(tester);
      expect(repository.searchedSpaces, ['opaque-b']);
      expect(repository.createdSpace, 'opaque-b');
      expect(repository.invited!.single.displayName, 'Team member');
      expect(find.byType(ChatRoomScreen), findsOneWidget);
    },
  );

  testWidgets(
    'Space switch clears participants and ignores prior search results',
    (tester) async {
      final pending = Completer<List<ChatParticipant>>();
      final repository = _ParticipantChatRepository(
        refuse: false,
        searchHandler: (query, spaceRef) async {
          if (query == 'wait') return pending.future;
          return [
            ChatParticipant(
              userId: spaceRef == 'scope-design'
                  ? '@amy:weave.test'
                  : '@bob:weave.test',
              displayName: spaceRef == 'scope-design' ? 'Amy' : 'Bob',
            ),
          ];
        },
      );
      await tester.pumpWidget(
        _chatTestApp(
          const ChatScreen(),
          spaces: const MemberSpaceAccessSnapshot(
            visibleSpaceRefs: {'scope-design', 'scope-support'},
            defaultSpaceReadable: false,
            displayNames: {
              'scope-design': 'Design',
              'scope-support': 'Support',
            },
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
      await _openChatCreation(tester);
      await _chooseChatSpace(tester, 'Design');
      await tester.enterText(
        find.byKey(const Key('chat-create-conversation-name-field')),
        'Scoped conversation',
      );
      final participants = find.byKey(
        const Key('chat-create-conversation-participant-field'),
      );
      await tester.enterText(participants, 'Amy');
      await tester.pump(const Duration(milliseconds: 300));
      await tester.pumpAndSettle();
      final amy = find.byKey(
        const ValueKey('chat-participant-result-@amy:weave.test'),
      );
      await tester.ensureVisible(amy);
      await tester.tap(amy);
      await tester.pumpAndSettle();
      expect(find.byType(InputChip), findsOneWidget);
      await tester.enterText(participants, 'wait');
      await tester.pump(const Duration(milliseconds: 300));
      await _chooseChatSpace(tester, 'Support');
      expect(find.byType(InputChip), findsNothing);
      expect(tester.widget<TextField>(participants).controller!.text, isEmpty);
      pending.complete(const [
        ChatParticipant(userId: '@late:weave.test', displayName: 'Late member'),
      ]);
      await tester.pumpAndSettle();
      expect(find.text('Late member'), findsNothing);
      expect(find.text('Amy'), findsNothing);
      await tester.enterText(participants, 'Bob');
      await tester.pump(const Duration(milliseconds: 300));
      await tester.pumpAndSettle();
      final bob = find.byKey(
        const ValueKey('chat-participant-result-@bob:weave.test'),
      );
      await tester.ensureVisible(bob);
      await tester.tap(bob);
      await tester.pumpAndSettle();
      await _submitChatCreation(tester);
      expect(repository.searchedSpaces, [
        'scope-design',
        'scope-design',
        'scope-support',
      ]);
      expect(repository.createdSpace, 'scope-support');
      expect(repository.invited!.single.displayName, 'Bob');
    },
  );

  testWidgets(
    'open creation dialog fails closed when current Space access is lost',
    (tester) async {
      final access =
          NotifierProvider<_ChatSpacesController, MemberSpaceAccessSnapshot?>(
            _ChatSpacesController.new,
          );
      final repository = _ParticipantChatRepository(refuse: false);
      await tester.pumpWidget(
        _chatTestApp(
          const ChatScreen(),
          spaces: null,
          overrides: [
            weaveApiMemberSpacesProvider.overrideWith(
              (ref) async => ref.watch(access),
            ),
            chatRepositoryProvider.overrideWithValue(repository),
          ],
        ),
      );
      await tester.pumpAndSettle();
      final container = ProviderScope.containerOf(
        tester.element(find.byType(ChatScreen)),
      );
      await _openChatCreation(tester);
      final participants = find.byKey(
        const Key('chat-create-conversation-participant-field'),
      );
      await tester.enterText(participants, 'Team');
      await tester.pump(const Duration(milliseconds: 300));
      await tester.pumpAndSettle();
      final peer = find.byKey(
        const ValueKey('chat-participant-result-@member:api.weave.test'),
      );
      await tester.ensureVisible(peer);
      await tester.tap(peer);
      await tester.pumpAndSettle();
      container.read(access.notifier).show(null);
      await tester.pumpAndSettle();
      expect(find.byType(InputChip), findsNothing);
      expect(find.byType(CheckboxListTile), findsNothing);
      expect(tester.widget<TextField>(participants).enabled, isFalse);
      expect(
        tester
            .widget<FilledButton>(
              find.byKey(const Key('chat-create-conversation-submit-button')),
            )
            .onPressed,
        isNull,
      );
      expect(repository.invited, isNull);
    },
  );

  testWidgets(
    'lost selected Space never silently changes the creation target',
    (tester) async {
      final access =
          NotifierProvider<_ChatSpacesController, MemberSpaceAccessSnapshot?>(
            _ChatSpacesController.new,
          );
      final repository = _ParticipantChatRepository(refuse: false);
      await tester.pumpWidget(
        _chatTestApp(
          const ChatScreen(),
          spaces: null,
          overrides: [
            weaveApiMemberSpacesProvider.overrideWith(
              (ref) async => ref.watch(access),
            ),
            chatRepositoryProvider.overrideWithValue(repository),
            preferencesStoreProvider.overrideWith(
              (ref) => InMemoryPreferencesStore(),
            ),
          ],
        ),
      );
      await tester.pumpAndSettle();
      final container = ProviderScope.containerOf(
        tester.element(find.byType(ChatScreen)),
      );
      await _openChatCreation(tester);
      final selector = find.byKey(const Key('chat-create-space-selector'));
      expect(
        tester.widget<DropdownButton<String>>(selector).value,
        'workspace-default',
      );
      final title = find.byKey(
        const Key('chat-create-conversation-name-field'),
      );
      await tester.enterText(title, 'Retained draft');
      final participants = find.byKey(
        const Key('chat-create-conversation-participant-field'),
      );
      await tester.enterText(participants, 'Team');
      await tester.pump(const Duration(milliseconds: 300));
      await tester.pumpAndSettle();
      final peer = find.byKey(
        const ValueKey('chat-participant-result-@member:api.weave.test'),
      );
      await tester.ensureVisible(peer);
      await tester.tap(peer);
      await tester.pumpAndSettle();
      container
          .read(access.notifier)
          .show(
            const MemberSpaceAccessSnapshot(
              visibleSpaceRefs: {'scope-operations'},
              defaultSpaceReadable: false,
              displayNames: {'scope-operations': 'Operations'},
            ),
          );
      await tester.pumpAndSettle();
      expect(tester.widget<DropdownButton<String>>(selector).value, isNull);
      expect(
        tester.widget<TextField>(title).controller!.text,
        'Retained draft',
      );
      expect(tester.widget<TextField>(participants).enabled, isFalse);
      expect(find.byType(InputChip), findsNothing);
      await _submitChatCreation(tester);
      expect(repository.invited, isNull);
      expect(repository.createdSpace, isNull);
      await _chooseChatSpace(tester, 'Operations');
      await tester.enterText(participants, 'Team');
      await tester.pump(const Duration(milliseconds: 300));
      await tester.pumpAndSettle();
      await tester.ensureVisible(peer);
      await tester.tap(peer);
      await tester.pumpAndSettle();
      await _submitChatCreation(tester);
      expect(repository.createdSpace, 'scope-operations');
      expect(repository.searchedSpaces, [
        'workspace-default',
        'scope-operations',
      ]);
    },
  );

  testWidgets(
    'creation survives actual Chat admission loss and explicit regrant',
    (tester) async {
      final capabilityState =
          NotifierProvider<
            _ChatCapabilityController,
            AsyncValue<WorkspaceCapabilitySnapshot>
          >(_ChatCapabilityController.new);
      final repository = _ParticipantChatRepository(refuse: false);
      await tester.pumpWidget(
        _chatTestApp(
          const ChatScreen(),
          provideReadyCapabilities: false,
          overrides: [
            workspaceCapabilitySnapshotProvider.overrideWith(
              (ref) => ref.watch(capabilityState),
            ),
            chatRepositoryProvider.overrideWithValue(repository),
            preferencesStoreProvider.overrideWith(
              (ref) => InMemoryPreferencesStore(),
            ),
          ],
        ),
      );
      await tester.pumpAndSettle();
      final container = ProviderScope.containerOf(
        tester.element(find.byType(ChatScreen)),
      );
      await _openChatCreation(tester);
      final title = find.byKey(
        const Key('chat-create-conversation-name-field'),
      );
      await tester.enterText(title, 'Retained after regrant');
      final participants = find.byKey(
        const Key('chat-create-conversation-participant-field'),
      );
      await tester.enterText(participants, 'Team');
      await tester.pump(const Duration(milliseconds: 300));
      await tester.pumpAndSettle();
      final peer = find.byKey(
        const ValueKey('chat-participant-result-@member:api.weave.test'),
      );
      await tester.ensureVisible(peer);
      await tester.tap(peer);
      await tester.pumpAndSettle();

      container.read(capabilityState.notifier).show(_blockedChatSnapshot);
      await tester.pumpAndSettle();
      expect(
        find.byKey(const Key('chat-create-conversation-button')),
        findsNothing,
      );
      expect(find.byType(InputChip), findsNothing);
      expect(tester.widget<TextField>(participants).enabled, isFalse);
      expect(repository.invited, isNull);

      container.read(capabilityState.notifier).show(_readyChatSnapshot);
      await tester.pumpAndSettle();
      expect(
        find.byKey(const Key('chat-create-conversation-button')),
        findsOneWidget,
      );
      expect(
        tester
            .widget<DropdownButton<String>>(
              find.byKey(const Key('chat-create-space-selector')),
            )
            .value,
        isNull,
      );
      expect(
        tester.widget<TextField>(title).controller!.text,
        'Retained after regrant',
      );
      await _chooseChatSpace(tester, 'Product team');
      await tester.enterText(participants, 'Team');
      await tester.pump(const Duration(milliseconds: 300));
      await tester.pumpAndSettle();
      await tester.ensureVisible(peer);
      await tester.tap(peer);
      await tester.pumpAndSettle();
      await _submitChatCreation(tester);
      expect(repository.createdSpace, 'workspace-default');
      expect(find.byType(ChatRoomScreen), findsOneWidget);
      expect(tester.takeException(), isNull);
    },
  );

  testWidgets(
    'pending create after access loss asks for refresh without false failure',
    (tester) async {
      final capabilityState =
          NotifierProvider<
            _ChatCapabilityController,
            AsyncValue<WorkspaceCapabilitySnapshot>
          >(_ChatCapabilityController.new);
      final pending = Completer<ChatConversation>();
      var createCalls = 0;
      final repository = _ParticipantChatRepository(
        refuse: false,
        createHandler: (_) {
          createCalls++;
          return pending.future;
        },
      );
      await tester.pumpWidget(
        _chatTestApp(
          const ChatScreen(),
          provideReadyCapabilities: false,
          overrides: [
            workspaceCapabilitySnapshotProvider.overrideWith(
              (ref) => ref.watch(capabilityState),
            ),
            chatRepositoryProvider.overrideWithValue(repository),
          ],
        ),
      );
      await tester.pumpAndSettle();
      final container = ProviderScope.containerOf(
        tester.element(find.byType(ChatScreen)),
      );
      final l10n = AppLocalizations.of(tester.element(find.byType(ChatScreen)));
      await _openChatCreation(tester);
      await tester.enterText(
        find.byKey(const Key('chat-create-conversation-name-field')),
        'Pending conversation',
      );
      final mode = find.byKey(const Key('chat-create-direct-switch'));
      await tester.ensureVisible(mode);
      await tester.tap(mode);
      await tester.pumpAndSettle();
      final submit = find.byKey(
        const Key('chat-create-conversation-submit-button'),
      );
      await tester.ensureVisible(submit);
      await tester.tap(submit);
      await tester.pump();
      expect(createCalls, 1);

      container.read(capabilityState.notifier).show(_blockedChatSnapshot);
      await tester.pumpAndSettle();
      pending.complete(
        const ChatConversation(
          id: '!completed:weave.test',
          title: 'Pending conversation',
          previewType: ChatConversationPreviewType.none,
          unreadCount: 0,
          isInvite: false,
          isDirectMessage: false,
        ),
      );
      await tester.pumpAndSettle();
      expect(find.text(l10n.chatCreateSpaceChanged), findsOneWidget);
      expect(find.text(l10n.chatCreateConversationFailure), findsNothing);
      expect(find.byType(ChatRoomScreen), findsNothing);
      expect(createCalls, 1);
      expect(tester.takeException(), isNull);
    },
  );

  for (final locale in ['en', 'de']) {
    for (final operation in ['search', 'create']) {
      testWidgets(
        'unsupported scoped Chat remains actionable $locale $operation',
        (tester) async {
          tester.platformDispatcher.localesTestValue = [Locale(locale)];
          addTearDown(tester.platformDispatcher.clearLocalesTestValue);
          const failure = ChatFailure.unsupportedConfiguration(
            'M_WEAVE_CHAT_SPACE_SCOPE_UNSUPPORTED',
          );
          final repository = _ParticipantChatRepository(
            refuse: false,
            createFailure: operation == 'create' ? failure : null,
            searchHandler: operation == 'search'
                ? (_, _) async => throw failure
                : null,
          );
          await tester.pumpWidget(
            _chatTestApp(
              const ChatScreen(),
              overrides: [chatRepositoryProvider.overrideWithValue(repository)],
            ),
          );
          await tester.pumpAndSettle();
          await _openChatCreation(tester);
          final l10n = AppLocalizations.of(
            tester.element(find.byType(ChatScreen)),
          );
          if (operation == 'search') {
            await tester.enterText(
              find.byKey(
                const Key('chat-create-conversation-participant-field'),
              ),
              'Team',
            );
            await tester.pump(const Duration(milliseconds: 300));
            await tester.pumpAndSettle();
          } else {
            await tester.enterText(
              find.byKey(const Key('chat-create-conversation-name-field')),
              'New conversation',
            );
            final mode = find.byKey(const Key('chat-create-direct-switch'));
            await tester.ensureVisible(mode);
            await tester.tap(mode);
            await tester.pumpAndSettle();
            await _submitChatCreation(tester);
          }
          expect(find.text(l10n.chatSpaceScopeUnsupported), findsOneWidget);
          expect(find.text(l10n.chatCreateConversationFailure), findsNothing);
          expect(find.textContaining('M_WEAVE_'), findsNothing);
          expect(find.textContaining('connection is back'), findsNothing);
          expect(find.textContaining('encrypted conversation'), findsNothing);
          expect(find.byType(ChatRoomScreen), findsNothing);
        },
      );
    }
  }

  for (final type in [
    ChatFailureType.transport,
    ChatFailureType.storage,
    ChatFailureType.protocol,
    ChatFailureType.peerDevicePending,
    ChatFailureType.serviceUnavailable,
    ChatFailureType.unknown,
    ChatFailureType.accessDenied,
  ]) {
    testWidgets(
      'initial Chat failure renders safe class-specific guidance $type',
      (tester) async {
        final repository = FakeChatRepository(
          loadConversationsHandler: () async {
            throw ChatFailure(
              type: type,
              message: 'private provider body bearer',
            );
          },
        );
        await tester.pumpWidget(
          _chatTestApp(
            const ChatScreen(),
            overrides: [chatRepositoryProvider.overrideWithValue(repository)],
          ),
        );
        await tester.pumpAndSettle();
        final copy = AppLocalizations.of(
          tester.element(find.byType(ChatScreen)),
        );
        final expected = switch (type) {
          ChatFailureType.transport => copy.chatErrorTransportGuidance,
          ChatFailureType.storage => copy.chatErrorStorageGuidance,
          ChatFailureType.protocol => copy.chatErrorProtocolGuidance,
          ChatFailureType.peerDevicePending =>
            copy.chatErrorPeerDevicePendingGuidance,
          ChatFailureType.serviceUnavailable => copy.chatErrorServiceGuidance,
          ChatFailureType.accessDenied => copy.chatErrorAccessDeniedGuidance,
          _ => copy.chatErrorUnknownGuidance,
        };
        expect(find.text(expected), findsOneWidget);
        expect(find.textContaining('private provider'), findsNothing);
        if (type != ChatFailureType.transport) {
          expect(find.text(copy.chatErrorTransportGuidance), findsNothing);
        }
      },
    );
  }

  for (final locale in ['en', 'de']) {
    for (final scale in [1.0, 2.0]) {
      testWidgets(
        'phone Chat opens its first real conversation; $locale $scale',
        (tester) async {
          tester.view.physicalSize = const Size(1206, 2622);
          tester.view.devicePixelRatio = 3;
          addTearDown(tester.view.resetPhysicalSize);
          addTearDown(tester.view.resetDevicePixelRatio);
          const target = ChatConversation(
            id: '!owned-direct:weave.test',
            title: 'Owned conversation',
            previewType: ChatConversationPreviewType.none,
            unreadCount: 1,
            isInvite: false,
            isDirectMessage: true,
          );
          final repository = FakeChatRepository(
            loadConversationsHandler: () async => [target],
            loadRoomTimelineHandler: (_) async => ChatRoomTimeline(
              roomId: target.id,
              roomTitle: target.title,
              isInvite: false,
              canSendMessages: true,
              messages: [
                ChatMessage(
                  id: 'retained-owned-message',
                  senderId: 'owned-peer',
                  senderDisplayName: 'Owned peer',
                  sentAt: DateTime(2026),
                  isMine: false,
                  deliveryState: ChatMessageDeliveryState.sent,
                  contentType: ChatMessageContentType.text,
                  text: 'Retained conversation content',
                ),
              ],
            ),
          );
          final router = GoRouter(
            initialLocation: '/chat',
            routes: [
              GoRoute(
                path: '/chat',
                builder: (context, state) => Scaffold(
                  body: const ChatScreen(),
                  bottomNavigationBar: NavigationBar(
                    selectedIndex: 1,
                    destinations: [
                      NavigationDestination(
                        icon: const Icon(Icons.home_outlined),
                        label: AppLocalizations.of(context).navHome,
                      ),
                      NavigationDestination(
                        icon: const Icon(Icons.chat_bubble_outline),
                        label: AppLocalizations.of(context).navChat,
                      ),
                    ],
                  ),
                ),
                routes: [
                  GoRoute(
                    path: 'rooms/:roomId',
                    builder: (_, state) => ChatRoomScreen(
                      conversation: state.extra! as ChatConversation,
                    ),
                  ),
                ],
              ),
            ],
          );
          addTearDown(router.dispose);
          await tester.pumpWidget(
            ProviderScope(
              overrides: [
                workspaceCapabilitySnapshotProvider.overrideWithValue(
                  const AsyncData(_readyChatSnapshot),
                ),
                chatRepositoryProvider.overrideWithValue(repository),
                preferencesStoreProvider.overrideWith(
                  (ref) => InMemoryPreferencesStore(),
                ),
              ],
              child: MaterialApp.router(
                theme: AppTheme.light,
                locale: Locale(locale),
                localizationsDelegates: AppLocalizations.localizationsDelegates,
                supportedLocales: AppLocalizations.supportedLocales,
                builder: (context, child) => MediaQuery(
                  data: MediaQuery.of(
                    context,
                  ).copyWith(textScaler: TextScaler.linear(scale)),
                  child: child!,
                ),
                routerConfig: router,
              ),
            ),
          );
          await tester.pumpAndSettle();
          expect(find.text(target.title).hitTestable(), findsOneWidget);
          final copy = AppLocalizations.of(
            tester.element(find.byType(ChatScreen)),
          );
          expect(
            find.byTooltip(copy.chatCreateConversationAction).hitTestable(),
            findsOneWidget,
          );
          expect(find.text(copy.chatFavoritesSectionTitle), findsNothing);
          expect(find.text(copy.chatConversationNoPreview), findsOneWidget);
          await tester.tap(find.text(target.title));
          await tester.pumpAndSettle();
          expect(
            tester
                .widget<ChatRoomScreen>(find.byType(ChatRoomScreen))
                .conversation
                .id,
            target.id,
          );
          expect(repository.loadRoomTimelineCalls, greaterThan(0));
          expect(
            find.text('Retained conversation content').hitTestable(),
            findsOneWidget,
          );
          expect(tester.takeException(), isNull);
          await tester.pumpWidget(const SizedBox.shrink());
          await tester.pump(const Duration(seconds: 1));
        },
      );
    }
  }
  group('ChatScreen', () {
    testWidgets('hides revoked rooms and reloads after grant restoration', (
      tester,
    ) async {
      final capabilityState =
          NotifierProvider<
            _ChatCapabilityController,
            AsyncValue<WorkspaceCapabilitySnapshot>
          >(_ChatCapabilityController.new);
      final repository = FakeChatRepository(
        loadConversationsHandler: () async => const [
          ChatConversation(
            id: '!room:home.internal',
            title: 'Private project',
            previewType: ChatConversationPreviewType.text,
            unreadCount: 0,
            isInvite: false,
            isDirectMessage: false,
          ),
        ],
      );
      await tester.pumpWidget(
        createTestApp(
          const ChatScreen(),
          overrides: [
            workspaceCapabilitySnapshotProvider.overrideWith(
              (ref) => ref.watch(capabilityState),
            ),
            chatRepositoryProvider.overrideWithValue(repository),
          ],
        ),
      );
      await tester.pumpAndSettle();
      expect(find.text('Private project'), findsOneWidget);
      final initialReads = repository.loadConversationsCalls;

      final container = ProviderScope.containerOf(
        tester.element(find.byType(ChatScreen)),
      );
      container.read(capabilityState.notifier).show(_blockedChatSnapshot);
      await tester.pumpAndSettle();
      expect(find.text('Private project'), findsNothing);
      expect(repository.loadConversationsCalls, initialReads);

      container.read(capabilityState.notifier).show(_readyChatSnapshot);
      await tester.pumpAndSettle();
      expect(repository.loadConversationsCalls, greaterThan(initialReads));
      expect(find.text('Private project'), findsOneWidget);
    });

    testWidgets('does not start native Matrix without a current Space grant', (
      tester,
    ) async {
      final repository = FakeChatRepository();
      await tester.pumpWidget(
        createTestApp(
          const ChatScreen(),
          overrides: [
            workspaceCapabilitySnapshotProvider.overrideWithValue(
              AsyncData(_blockedChatSnapshot),
            ),
            chatRepositoryProvider.overrideWithValue(repository),
          ],
        ),
      );
      await tester.pumpAndSettle();

      expect(repository.loadConversationsCalls, 0);
      expect(find.text('No conversations yet'), findsOneWidget);
    });

    FakeChatSecurityRepository buildSecurityRepository() {
      return FakeChatSecurityRepository(
        loadSecurityStateHandler: ({bool refresh = false}) async {
          return const ChatSecurityState(
            isMatrixSignedIn: false,
            bootstrapState: ChatSecurityBootstrapState.signedOut,
            accountVerificationState: ChatAccountVerificationState.unavailable,
            deviceVerificationState: ChatDeviceVerificationState.unavailable,
            keyBackupState: ChatKeyBackupState.unavailable,
            roomEncryptionReadiness: ChatRoomEncryptionReadiness.unavailable,
            secretStorageReady: false,
            crossSigningReady: false,
            hasEncryptedConversations: false,
            verificationSession: ChatVerificationSession.none(),
          );
        },
      );
    }

    testWidgets('shows the loading state while conversations are loading', (
      tester,
    ) async {
      final completer = Completer<List<ChatConversation>>();
      final repository = FakeChatRepository(
        loadConversationsHandler: () => completer.future,
      );
      final securityRepository = buildSecurityRepository();

      await tester.pumpWidget(
        _chatTestApp(
          const ChatScreen(),
          overrides: [
            chatRepositoryProvider.overrideWithValue(repository),
            chatSecurityRepositoryProvider.overrideWithValue(
              securityRepository,
            ),
          ],
        ),
      );
      await tester.pump();

      expect(find.text('Loading conversations…'), findsOneWidget);
    });

    testWidgets('loads Chat without a separate member Connect action', (
      tester,
    ) async {
      final repository = FakeChatRepository();
      final securityRepository = buildSecurityRepository();
      repository.loadConversationsHandler = () async =>
          const <ChatConversation>[
            ChatConversation(
              id: '!abc:home.internal',
              title: 'Family',
              previewType: ChatConversationPreviewType.text,
              previewText: 'Dinner is ready',
              unreadCount: 2,
              isInvite: false,
              isDirectMessage: false,
            ),
          ];

      await tester.pumpWidget(
        _chatTestApp(
          const ChatScreen(),
          overrides: [
            chatRepositoryProvider.overrideWithValue(repository),
            chatSecurityRepositoryProvider.overrideWithValue(
              securityRepository,
            ),
          ],
        ),
      );
      await tester.pumpAndSettle();

      expect(find.text('Connect chat'), findsNothing);
      expect(repository.connectCalls, 0);

      expect(find.text('Family'), findsOneWidget);
      expect(find.text('Dinner is ready'), findsOneWidget);
    });

    testWidgets(
      'shows an unsupported homeserver message when Matrix OAuth metadata is unavailable',
      (tester) async {
        final repository = FakeChatRepository();
        final securityRepository = buildSecurityRepository();
        repository.loadConversationsHandler = () async {
          throw const ChatFailure.unsupportedConfiguration(
            'Weave Matrix support is unavailable to this member.',
          );
        };

        await tester.pumpWidget(
          _chatTestApp(
            const ChatScreen(),
            overrides: [
              chatRepositoryProvider.overrideWithValue(repository),
              chatSecurityRepositoryProvider.overrideWithValue(
                securityRepository,
              ),
            ],
          ),
        );
        await tester.pump();
        await tester.pump();

        expect(
          find.textContaining('Chat setup needs admin attention'),
          findsOneWidget,
        );
        expect(find.text('Connect chat'), findsNothing);
        expect(find.text('Retry'), findsOneWidget);
      },
    );

    testWidgets('offers a retry after automatic Chat sign-in is interrupted', (
      tester,
    ) async {
      final repository = FakeChatRepository();
      final securityRepository = buildSecurityRepository();
      var loadCalls = 0;
      repository.loadConversationsHandler = () async {
        loadCalls++;
        if (loadCalls == 1) {
          throw const ChatFailure.cancelled(
            'Automatic Chat sign-in was interrupted.',
          );
        }

        return const <ChatConversation>[
          ChatConversation(
            id: '@sam:home.internal',
            title: 'Sam',
            previewType: ChatConversationPreviewType.text,
            previewText: 'See you soon',
            unreadCount: 0,
            isInvite: false,
            isDirectMessage: true,
          ),
        ];
      };

      await tester.pumpWidget(
        _chatTestApp(
          const ChatScreen(),
          overrides: [
            chatRepositoryProvider.overrideWithValue(repository),
            chatSecurityRepositoryProvider.overrideWithValue(
              securityRepository,
            ),
          ],
        ),
      );
      await tester.pump();
      await tester.pump();

      expect(find.text('Retry'), findsOneWidget);
      expect(find.text('Connect chat'), findsNothing);
      expect(repository.connectCalls, 0);

      await tester.tap(find.text('Retry'));
      await tester.pumpAndSettle();

      expect(find.text('Sam'), findsOneWidget);
      expect(loadCalls, 2);
      expect(repository.connectCalls, 0);
    });

    testWidgets('rechecks Chat after a typed Matrix endpoint invalidation', (
      tester,
    ) async {
      var homeserverChanged = false;
      final repository = FakeChatRepository(
        loadConversationsHandler: () async {
          if (homeserverChanged) {
            throw const ChatFailure.sessionRequired(
              'Chat access could not be confirmed.',
            );
          }

          return const <ChatConversation>[
            ChatConversation(
              id: '!abc:home.internal',
              title: 'Family',
              previewType: ChatConversationPreviewType.text,
              previewText: 'Dinner is ready',
              unreadCount: 2,
              isInvite: false,
              isDirectMessage: false,
            ),
          ];
        },
      );
      final securityRepository = buildSecurityRepository();
      final container = ProviderContainer.test(
        overrides: [
          workspaceCapabilitySnapshotProvider.overrideWithValue(
            const AsyncData(_readyChatSnapshot),
          ),
          chatRepositoryProvider.overrideWithValue(repository),
          chatSecurityRepositoryProvider.overrideWithValue(securityRepository),
        ],
      );
      addTearDown(container.dispose);

      await tester.pumpWidget(
        UncontrolledProviderScope(
          container: container,
          child: MaterialApp(
            theme: AppTheme.light,
            localizationsDelegates: AppLocalizations.localizationsDelegates,
            supportedLocales: AppLocalizations.supportedLocales,
            home: const Scaffold(body: ChatScreen()),
          ),
        ),
      );
      await tester.pumpAndSettle();

      expect(find.text('Family'), findsOneWidget);
      expect(repository.connectCalls, 0);

      homeserverChanged = true;
      container
          .read(workspaceInvalidationProvider.notifier)
          .invalidate(
            integration: WorkspaceIntegration.chat,
            reason: IntegrationInvalidationReason.chatConfigurationChanged,
          );

      await tester.pump();
      await tester.pump();

      expect(repository.connectCalls, 0);
      expect(find.text('Connect chat'), findsNothing);
      expect(find.text('Retry'), findsOneWidget);
    });

    testWidgets('shows the empty state when there are no conversations', (
      tester,
    ) async {
      final semantics = tester.ensureSemantics();
      var loadCount = 0;
      final repository = FakeChatRepository(
        loadConversationsHandler: () async {
          loadCount++;
          if (loadCount == 1) {
            return const <ChatConversation>[];
          }

          return const <ChatConversation>[
            ChatConversation(
              id: '!project:home.internal',
              title: 'Project',
              previewType: ChatConversationPreviewType.text,
              previewText: 'Recovered room',
              unreadCount: 0,
              isInvite: false,
              isDirectMessage: false,
            ),
          ];
        },
      );
      final securityRepository = buildSecurityRepository();

      await tester.pumpWidget(
        _chatTestApp(
          const ChatScreen(),
          overrides: [
            chatRepositoryProvider.overrideWithValue(repository),
            chatSecurityRepositoryProvider.overrideWithValue(
              securityRepository,
            ),
          ],
        ),
      );
      await tester.pumpAndSettle();

      expect(find.text('No conversations yet'), findsOneWidget);
      expect(
        find.text(
          'Workspace rooms and direct messages will appear here when chat is ready.',
        ),
        findsOneWidget,
      );
      expect(find.text('Refresh rooms'), findsOneWidget);
      expect(find.bySemanticsLabel('Refresh rooms'), findsOneWidget);

      await tester.tap(find.text('Refresh rooms'));
      await tester.pumpAndSettle();

      expect(repository.loadConversationsCalls, 2);
      expect(find.text('Project'), findsOneWidget);
      expect(find.text('Recovered room'), findsOneWidget);
      expect(find.text('No conversations yet'), findsNothing);
      semantics.dispose();
    });

    for (final locale in ['en', 'de']) {
      for (final direct in [true, false]) {
        testWidgets(
          'localized explicit conversation intent reaches real room: $locale direct=$direct',
          (tester) async {
            tester.platformDispatcher.localesTestValue = [Locale(locale)];
            addTearDown(tester.platformDispatcher.clearLocalesTestValue);
            final repository = _ParticipantChatRepository(refuse: false);
            await tester.pumpWidget(
              _chatTestApp(
                const ChatScreen(),
                overrides: [
                  chatRepositoryProvider.overrideWithValue(repository),
                  preferencesStoreProvider.overrideWith(
                    (ref) => InMemoryPreferencesStore(),
                  ),
                ],
              ),
            );
            await tester.pumpAndSettle();
            await tester.tap(
              find.byKey(const Key('chat-create-conversation-button')),
            );
            await tester.pumpAndSettle();
            final mode = find.byKey(const Key('chat-create-direct-switch'));
            expect(tester.widget<SwitchListTile>(mode).value, isTrue);
            await tester.enterText(
              find.byKey(const Key('chat-create-conversation-name-field')),
              'Controlled conversation',
            );
            if (!direct) {
              await tester.tap(mode);
              await tester.pumpAndSettle();
            } else {
              final create = find.byKey(
                const Key('chat-create-conversation-submit-button'),
              );
              await tester.ensureVisible(create);
              await tester.pump();
              await tester.tap(create);
              await tester.pumpAndSettle();
              expect(repository.invited, isNull);
              final copy = AppLocalizations.of(tester.element(mode));
              expect(
                find.text(copy.chatDirectParticipantRequired),
                findsOneWidget,
              );
            }
            await tester.enterText(
              find.byKey(
                const Key('chat-create-conversation-participant-field'),
              ),
              'Team',
            );
            await tester.pump(const Duration(milliseconds: 300));
            await tester.pumpAndSettle();
            final peer = find.byKey(
              const ValueKey('chat-participant-result-@member:api.weave.test'),
            );
            await tester.ensureVisible(peer);
            await tester.pump();
            await tester.tap(peer);
            await tester.pumpAndSettle();
            expect(
              find.text(
                AppLocalizations.of(
                  tester.element(peer),
                ).chatDirectParticipantRequired,
              ),
              findsNothing,
            );
            final create = find.byKey(
              const Key('chat-create-conversation-submit-button'),
            );
            await tester.ensureVisible(create);
            await tester.pump();
            await tester.tap(create);
            await tester.pumpAndSettle();
            expect(repository.direct, direct);
            expect(repository.invited, hasLength(1));
            final actual = tester
                .widget<ChatRoomScreen>(find.byType(ChatRoomScreen))
                .conversation;
            expect(find.byType(ChatRoomScreen), findsOneWidget);
            expect(
              find.byWidgetPredicate(
                (widget) =>
                    widget is Semantics &&
                    widget.properties.identifier ==
                        'chat-active-conversation-${actual.id}',
              ),
              findsOneWidget,
            );
            expect(actual.isDirectMessage, direct);
            expect(actual.isInvite, isFalse);
            expect(
              find.byKey(const Key('chat-accept-invitation-button')),
              findsNothing,
            );
          },
        );
      }
    }

    testWidgets(
      'selects authorized directory member and submits their native ID without claiming success after refusal',
      (tester) async {
        final repository = _ParticipantChatRepository();
        await tester.pumpWidget(
          _chatTestApp(
            const Scaffold(body: ChatScreen()),
            overrides: [
              chatRepositoryProvider.overrideWith((ref) => repository),
              chatSecurityRepositoryProvider.overrideWith(
                (ref) => buildSecurityRepository(),
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
          'Shared work',
        );
        await tester.enterText(
          find.byKey(const Key('chat-create-conversation-participant-field')),
          'Team',
        );
        await tester.pump(const Duration(milliseconds: 300));
        await tester.pumpAndSettle();
        final peer = find.byKey(
          const ValueKey('chat-participant-result-@member:api.weave.test'),
        );
        await tester.ensureVisible(peer);
        await tester.pump();
        await tester.tap(peer);
        await tester.pumpAndSettle();
        await tester.ensureVisible(
          find.byKey(const Key('chat-create-conversation-submit-button')),
        );
        await tester.pump();
        await tester.tap(
          find.byKey(const Key('chat-create-conversation-submit-button')),
        );
        await tester.pumpAndSettle();
        expect(repository.invited!.single.userId, '@member:api.weave.test');
        expect(repository.direct, isTrue);
        expect(find.textContaining('could not be created'), findsOneWidget);
        expect(
          find.byKey(const Key('chat-create-conversation-participant-field')),
          findsOneWidget,
        );
      },
    );

    testWidgets(
      'creates an encrypted conversation from the empty state accessibly',
      (tester) async {
        final semantics = tester.ensureSemantics();
        final repository = FakeChatRepository(
          loadConversationsHandler: () async => const <ChatConversation>[],
          createConversationHandler: ({required title}) async =>
              ChatConversation(
                id: '!created:home.internal',
                title: title,
                previewType: ChatConversationPreviewType.encrypted,
                unreadCount: 0,
                isInvite: false,
                isDirectMessage: false,
              ),
          loadRoomTimelineHandler: (roomId) async => ChatRoomTimeline(
            roomId: roomId,
            roomTitle: 'Release planning',
            isInvite: false,
            canSendMessages: true,
            messages: const [],
          ),
        );
        final securityRepository = buildSecurityRepository();
        final router = GoRouter(
          initialLocation: '/chat',
          routes: [
            GoRoute(
              path: '/chat',
              builder: (context, state) => const Scaffold(body: ChatScreen()),
              routes: [
                GoRoute(
                  path: 'rooms/:roomId',
                  builder: (context, state) =>
                      const Scaffold(body: Text('Created encrypted room')),
                ),
              ],
            ),
          ],
        );
        addTearDown(router.dispose);

        await tester.pumpWidget(
          _chatTestRouterApp(
            router,
            overrides: [
              chatRepositoryProvider.overrideWithValue(repository),
              chatSecurityRepositoryProvider.overrideWithValue(
                securityRepository,
              ),
            ],
          ),
        );
        await tester.pumpAndSettle();

        expect(find.byTooltip('Start conversation'), findsOneWidget);
        await tester.tap(find.byTooltip('Start conversation'));
        await tester.pumpAndSettle();

        expect(find.text('Start a conversation'), findsOneWidget);
        expect(find.text('Conversation name'), findsOneWidget);

        await tester.tap(find.text('Create conversation'));
        await tester.pump();
        expect(
          find.text('Enter a name between 1 and 200 characters.'),
          findsOneWidget,
        );

        await tester.tap(find.byKey(const Key('chat-create-direct-switch')));
        await tester.pumpAndSettle();
        await tester.enterText(
          find.byKey(const Key('chat-create-conversation-name-field')),
          'Release planning',
        );
        await tester.tap(find.text('Create conversation'));
        await tester.pumpAndSettle();

        expect(repository.createConversationCalls, 1);
        expect(find.text('Start a conversation'), findsNothing);
        expect(find.text('Created encrypted room'), findsOneWidget);
        semantics.dispose();
      },
    );

    testWidgets('keeps conversation creation failures domain local', (
      tester,
    ) async {
      const rawFailure = 'M_WEAVE_E2EE_CREATE_ROOM raw-provider-payload';
      final repository = FakeChatRepository(
        loadConversationsHandler: () async => const <ChatConversation>[],
        createConversationHandler: ({required title}) async {
          throw const ChatFailure.protocol(rawFailure);
        },
      );
      final securityRepository = buildSecurityRepository();

      await tester.pumpWidget(
        _chatTestApp(
          const ChatScreen(),
          overrides: [
            chatRepositoryProvider.overrideWithValue(repository),
            chatSecurityRepositoryProvider.overrideWithValue(
              securityRepository,
            ),
          ],
        ),
      );
      await tester.pumpAndSettle();
      await tester.tap(find.byTooltip('Start conversation'));
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const Key('chat-create-direct-switch')));
      await tester.pumpAndSettle();
      await tester.enterText(
        find.byKey(const Key('chat-create-conversation-name-field')),
        'Release planning',
      );
      await tester.tap(find.text('Create conversation'));
      await tester.pumpAndSettle();

      expect(find.textContaining('could not be created'), findsOneWidget);
      expect(find.textContaining(rawFailure), findsNothing);
      expect(find.text('No conversations yet'), findsOneWidget);
    });

    testWidgets(
      'groups conversations into favorites, personal messages, and channels',
      (tester) async {
        final repository = FakeChatRepository(
          loadConversationsHandler: () async => const <ChatConversation>[
            ChatConversation(
              id: '@sam:home.internal',
              title: 'Sam',
              previewType: ChatConversationPreviewType.text,
              previewText: 'Can you review this?',
              unreadCount: 0,
              isInvite: false,
              isDirectMessage: true,
            ),
            ChatConversation(
              id: '!project:home.internal',
              title: 'Project channel',
              previewType: ChatConversationPreviewType.text,
              previewText: 'Build is green',
              unreadCount: 3,
              isInvite: false,
              isDirectMessage: false,
              isFavorite: true,
            ),
          ],
        );
        final securityRepository = buildSecurityRepository();

        await tester.pumpWidget(
          _chatTestApp(
            const ChatScreen(),
            overrides: [
              chatRepositoryProvider.overrideWithValue(repository),
              chatSecurityRepositoryProvider.overrideWithValue(
                securityRepository,
              ),
            ],
          ),
        );
        await tester.pumpAndSettle();

        expect(find.text('Context for this workspace'), findsNothing);
        expect(find.text('Channel context'), findsNothing);
        expect(find.text('Agent context packs'), findsNothing);
        expect(find.text('Active workflows'), findsNothing);
        expect(find.text('Prepare a release'), findsNothing);
        expect(
          find.text('Agent chats are governed by your workspace'),
          findsNothing,
        );
        expect(find.text('Personal assistant'), findsNothing);
        expect(find.text('Channel agent'), findsNothing);
        expect(find.text('Unavailable until enabled'), findsNothing);
        expect(find.text('Favorites'), findsOneWidget);
        expect(find.text('Personal messages'), findsOneWidget);
        expect(find.text('Channels'), findsOneWidget);
        expect(find.text('Project channel'), findsNWidgets(2));
        expect(find.text('Sam'), findsOneWidget);

        expect(find.text('AI chats'), findsNothing);
        expect(find.text('Release coach'), findsNothing);
      },
    );

    testWidgets('shows populated groups without an empty favorites section', (
      tester,
    ) async {
      final repository = FakeChatRepository(
        loadConversationsHandler: () async => const <ChatConversation>[
          ChatConversation(
            id: '@sam:home.internal',
            title: 'Sam',
            previewType: ChatConversationPreviewType.text,
            previewText: 'See you soon',
            unreadCount: 0,
            isInvite: false,
            isDirectMessage: true,
          ),
          ChatConversation(
            id: '!project:home.internal',
            title: 'Project channel',
            previewType: ChatConversationPreviewType.text,
            previewText: 'Build is green',
            unreadCount: 0,
            isInvite: false,
            isDirectMessage: false,
          ),
        ],
      );
      final securityRepository = buildSecurityRepository();

      await tester.pumpWidget(
        _chatTestApp(
          const ChatScreen(),
          overrides: [
            chatRepositoryProvider.overrideWithValue(repository),
            chatSecurityRepositoryProvider.overrideWithValue(
              securityRepository,
            ),
          ],
        ),
      );
      await tester.pumpAndSettle();

      expect(find.text('Favorites'), findsNothing);
      expect(find.text('Personal messages'), findsOneWidget);
      expect(find.text('Channels'), findsOneWidget);
      expect(find.text('Sam'), findsOneWidget);
      expect(find.text('Project channel'), findsOneWidget);
      expect(
        find.text(
          'No favorites yet. Important direct messages and channels marked as favorites stay here.',
        ),
        findsNothing,
      );

      expect(find.text('AI chats'), findsNothing);
    });

    testWidgets('keeps unread recent room metadata within the tile', (
      tester,
    ) async {
      final semantics = tester.ensureSemantics();
      final now = DateTime.now();
      final yesterdayAtNoon = DateTime(
        now.year,
        now.month,
        now.day,
      ).subtract(const Duration(days: 1)).add(const Duration(hours: 12));
      final repository = FakeChatRepository(
        loadConversationsHandler: () async => <ChatConversation>[
          ChatConversation(
            id: '!latest:home.internal',
            title: 'Newest room',
            previewType: ChatConversationPreviewType.text,
            previewText: 'Fresh update',
            lastActivityAt: now.subtract(const Duration(minutes: 10)),
            unreadCount: 0,
            isInvite: false,
            isDirectMessage: false,
          ),
          ChatConversation(
            id: '!older-unread:home.internal',
            title: 'Older unread room',
            previewType: ChatConversationPreviewType.text,
            previewText: 'Unread update',
            lastActivityAt: now.subtract(const Duration(minutes: 20)),
            unreadCount: 3,
            isInvite: false,
            isDirectMessage: false,
          ),
          ChatConversation(
            id: '!older:home.internal',
            title: 'Older room',
            previewType: ChatConversationPreviewType.text,
            previewText: 'Yesterday update',
            lastActivityAt: yesterdayAtNoon.subtract(const Duration(hours: 1)),
            unreadCount: 0,
            isInvite: false,
            isDirectMessage: false,
          ),
        ],
      );
      final securityRepository = buildSecurityRepository();

      await tester.pumpWidget(
        _chatTestApp(
          const ChatScreen(),
          overrides: [
            chatRepositoryProvider.overrideWithValue(repository),
            chatSecurityRepositoryProvider.overrideWithValue(
              securityRepository,
            ),
          ],
        ),
      );
      await tester.pumpAndSettle();

      expect(find.text('Active now'), findsWidgets);
      expect(find.text('3'), findsOneWidget);
      expect(find.text('Yesterday'), findsOneWidget);
      expect(
        tester.getTopLeft(find.text('Older unread room')).dy,
        lessThan(tester.getTopLeft(find.text('Newest room')).dy),
      );
      expect(
        tester.getTopLeft(find.text('Newest room')).dy,
        lessThan(tester.getTopLeft(find.text('Older room')).dy),
      );

      final unreadRoomSemantics = find.byWidgetPredicate(
        (widget) =>
            widget is Semantics &&
            widget.properties.button == true &&
            (widget.properties.label ?? '').contains('Older unread room'),
      );
      final unreadRoomSemanticsData = tester
          .getSemantics(unreadRoomSemantics)
          .getSemanticsData();
      expect(unreadRoomSemanticsData.label, contains('Older unread room'));
      expect(unreadRoomSemanticsData.label, contains('Unread update'));
      expect(unreadRoomSemanticsData.label, contains('Active now'));
      expect(unreadRoomSemanticsData.label, contains('3 unread messages'));
      expect(unreadRoomSemanticsData.hasAction(SemanticsAction.tap), isTrue);
      semantics.dispose();
    });

    testWidgets('keeps the last room list visible when a manual refresh fails', (
      tester,
    ) async {
      var shouldFailRefresh = false;
      final repository = FakeChatRepository(
        loadConversationsHandler: () async {
          if (shouldFailRefresh) {
            throw const ChatFailure.protocol(
              'Raw chat sync timeout should not render.',
            );
          }

          return const <ChatConversation>[
            ChatConversation(
              id: '!project:home.internal',
              title: 'Project',
              previewType: ChatConversationPreviewType.text,
              previewText: 'Latest update',
              unreadCount: 1,
              isInvite: false,
              isDirectMessage: false,
            ),
          ];
        },
      );
      final securityRepository = buildSecurityRepository();
      final container = ProviderContainer.test(
        overrides: [
          workspaceCapabilitySnapshotProvider.overrideWithValue(
            const AsyncData(_readyChatSnapshot),
          ),
          chatRepositoryProvider.overrideWithValue(repository),
          chatSecurityRepositoryProvider.overrideWithValue(securityRepository),
        ],
      );
      addTearDown(container.dispose);

      await tester.pumpWidget(
        UncontrolledProviderScope(
          container: container,
          child: MaterialApp(
            theme: AppTheme.light,
            localizationsDelegates: AppLocalizations.localizationsDelegates,
            supportedLocales: AppLocalizations.supportedLocales,
            home: const Scaffold(body: ChatScreen()),
          ),
        ),
      );
      await tester.pumpAndSettle();

      expect(find.text('Project'), findsOneWidget);
      expect(find.text('Showing last known rooms'), findsNothing);

      shouldFailRefresh = true;
      await container.read(chatProvider.notifier).retry();
      await tester.pumpAndSettle();

      expect(find.text('Project'), findsOneWidget);
      expect(find.text('Latest update'), findsOneWidget);
      expect(find.text('Showing last known rooms'), findsOneWidget);
      expect(
        find.text(
          'Chat received an unexpected response. Retry, and contact support if this continues.',
        ),
        findsOneWidget,
      );
      expect(
        find.text('Raw chat sync timeout should not render.'),
        findsNothing,
      );
      expect(find.text('Refresh rooms'), findsOneWidget);

      shouldFailRefresh = false;
      await tester.tap(find.text('Refresh rooms'));
      await tester.pumpAndSettle();

      expect(find.text('Project'), findsOneWidget);
      expect(find.text('Showing last known rooms'), findsNothing);
    });

    testWidgets('drops cached rooms when refresh loses member authority', (
      tester,
    ) async {
      var accessRevoked = false;
      final repository = FakeChatRepository(
        loadConversationsHandler: () async {
          if (accessRevoked) {
            throw const ChatFailure.sessionRequired(
              'M_WEAVE_MATRIX_ACCESS_DENIED',
            );
          }
          return const <ChatConversation>[
            ChatConversation(
              id: '!private:home.internal',
              title: 'Private project',
              previewType: ChatConversationPreviewType.text,
              unreadCount: 0,
              isInvite: false,
              isDirectMessage: false,
            ),
          ];
        },
      );
      await tester.pumpWidget(
        _chatTestApp(
          const ChatScreen(),
          overrides: [chatRepositoryProvider.overrideWithValue(repository)],
        ),
      );
      await tester.pumpAndSettle();
      expect(find.text('Private project'), findsOneWidget);

      accessRevoked = true;
      final container = ProviderScope.containerOf(
        tester.element(find.byType(ChatScreen)),
      );
      await container.read(chatProvider.notifier).retry();
      await tester.pumpAndSettle();

      expect(find.text('Private project'), findsNothing);
      expect(find.text('Showing last known rooms'), findsNothing);
      expect(find.text('Retry'), findsOneWidget);
    });

    testWidgets('meets androidTapTargetGuideline', (tester) async {
      final repository = FakeChatRepository(
        loadConversationsHandler: () async => const <ChatConversation>[
          ChatConversation(
            id: '!room:home.internal',
            title: 'Project',
            previewType: ChatConversationPreviewType.text,
            previewText: 'Latest update',
            unreadCount: 1,
            isInvite: false,
            isDirectMessage: false,
          ),
        ],
      );
      final securityRepository = buildSecurityRepository();

      await tester.pumpWidget(
        _chatTestApp(
          const ChatScreen(),
          overrides: [
            chatRepositoryProvider.overrideWithValue(repository),
            chatSecurityRepositoryProvider.overrideWithValue(
              securityRepository,
            ),
          ],
        ),
      );
      await tester.pumpAndSettle();

      await expectLater(tester, meetsGuideline(androidTapTargetGuideline));
    });

    testWidgets('meets labeledTapTargetGuideline', (tester) async {
      final repository = FakeChatRepository(
        loadConversationsHandler: () async => const <ChatConversation>[
          ChatConversation(
            id: '!room:home.internal',
            title: 'Project',
            previewType: ChatConversationPreviewType.text,
            previewText: 'Latest update',
            unreadCount: 1,
            isInvite: false,
            isDirectMessage: false,
          ),
        ],
      );
      final securityRepository = buildSecurityRepository();

      await tester.pumpWidget(
        _chatTestApp(
          const ChatScreen(),
          overrides: [
            chatRepositoryProvider.overrideWithValue(repository),
            chatSecurityRepositoryProvider.overrideWithValue(
              securityRepository,
            ),
          ],
        ),
      );
      await tester.pumpAndSettle();

      await expectLater(tester, meetsGuideline(labeledTapTargetGuideline));
    });
  });
}
