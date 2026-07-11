import 'dart:async';

import 'package:flutter/semantics.dart';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:weave/core/theme/app_theme.dart';
import 'package:weave/features/agents/domain/entities/agent_capability_policy.dart';
import 'package:weave/features/agents/presentation/providers/agent_capability_policy_provider.dart';
import 'package:weave/features/app/domain/entities/integration_invalidation.dart';
import 'package:weave/features/app/presentation/providers/workspace_invalidation_provider.dart';
import 'package:weave/features/chat/domain/entities/chat_conversation.dart';
import 'package:weave/features/chat/domain/entities/chat_failure.dart';
import 'package:weave/features/chat/domain/entities/chat_security_state.dart';
import 'package:weave/features/chat/presentation/chat_screen.dart';
import 'package:weave/features/chat/presentation/providers/chat_provider.dart';
import 'package:weave/features/chat/presentation/providers/chat_repository_provider.dart';
import 'package:weave/features/chat/presentation/providers/chat_security_repository_provider.dart';
import 'package:weave/l10n/generated/app_localizations.dart';

import '../../helpers/fake_chat_repository.dart';
import '../../helpers/fake_chat_security_repository.dart';
import '../../helpers/test_app.dart';

void main() {
  group('ChatScreen', () {
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
        createTestApp(
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

    testWidgets('auto-connects when no Matrix session is available', (
      tester,
    ) async {
      final connectCompleter = Completer<void>();
      final repository = FakeChatRepository();
      final securityRepository = buildSecurityRepository();
      repository.loadConversationsHandler = () async {
        if (repository.connectCalls == 0) {
          throw const ChatFailure.sessionRequired(
            'Connect chat to load conversations.',
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
      };
      repository.connectHandler = () => connectCompleter.future;

      await tester.pumpWidget(
        createTestApp(
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

      expect(find.text('Connecting to chat…'), findsOneWidget);
      expect(repository.connectCalls, 1);

      connectCompleter.complete();
      await tester.pumpAndSettle();

      expect(find.text('Family'), findsOneWidget);
      expect(find.text('Dinner is ready'), findsOneWidget);
    });

    testWidgets(
      'shows an unsupported homeserver message when Matrix OAuth metadata is unavailable',
      (tester) async {
        final repository = FakeChatRepository();
        final securityRepository = buildSecurityRepository();
        repository.loadConversationsHandler = () async {
          if (repository.connectCalls == 0) {
            throw const ChatFailure.sessionRequired(
              'Connect chat to load conversations.',
            );
          }

          return const <ChatConversation>[];
        };
        repository.connectHandler = () async {
          throw const ChatFailure.unsupportedConfiguration(
            'Chat provider setup is unavailable to this member.',
          );
        };

        await tester.pumpWidget(
          createTestApp(
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
        expect(find.text('Connect chat'), findsOneWidget);
      },
    );

    testWidgets('shows a connect action after a cancelled Matrix sign-in', (
      tester,
    ) async {
      final repository = FakeChatRepository();
      final securityRepository = buildSecurityRepository();
      repository.loadConversationsHandler = () async {
        if (repository.connectCalls == 0) {
          throw const ChatFailure.sessionRequired(
            'Connect chat to load conversations.',
          );
        }

        if (repository.connectCalls == 1) {
          throw const ChatFailure.sessionRequired(
            'Connect chat to load conversations.',
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
      repository.connectHandler = () async {
        if (repository.connectCalls == 1) {
          throw const ChatFailure.cancelled(
            'Chat sign-in was cancelled before it completed.',
          );
        }
      };

      await tester.pumpWidget(
        createTestApp(
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

      expect(find.text('Connect chat'), findsOneWidget);

      await tester.tap(find.text('Connect chat'));
      await tester.pumpAndSettle();

      expect(find.text('Sam'), findsOneWidget);
      expect(repository.connectCalls, 2);
    });

    testWidgets(
      'does not auto-connect again after a typed Matrix homeserver invalidation',
      (tester) async {
        var homeserverChanged = false;
        final repository = FakeChatRepository(
          loadConversationsHandler: () async {
            if (homeserverChanged) {
              throw const ChatFailure.sessionRequired(
                'Connect chat to load conversations.',
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
            chatRepositoryProvider.overrideWithValue(repository),
            chatSecurityRepositoryProvider.overrideWithValue(
              securityRepository,
            ),
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
        expect(find.text('Connect chat'), findsOneWidget);
      },
    );

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
        createTestApp(
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

    testWidgets(
      'groups conversations into favorites, personal messages, channels, and AI chats',
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
            ChatConversation(
              id: 'agent:release-coach',
              title: 'Release coach',
              previewType: ChatConversationPreviewType.text,
              previewText: 'Ready to prepare notes',
              unreadCount: 0,
              isInvite: false,
              isDirectMessage: true,
              isAiChat: true,
            ),
          ],
        );
        final securityRepository = buildSecurityRepository();

        await tester.pumpWidget(
          createTestApp(
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

        expect(find.text('Weave Home'), findsOneWidget);
        expect(find.text('Your organization workspace'), findsOneWidget);
        expect(find.text('3 unread items'), findsOneWidget);
        expect(find.text('1 channel workspace'), findsOneWidget);
        expect(find.text('1 personal message'), findsOneWidget);
        expect(find.text('1 governed AI chat'), findsOneWidget);
        expect(find.text('Open next work item'), findsOneWidget);
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

        await tester.ensureVisible(find.text('AI chats'));
        await tester.pumpAndSettle();

        expect(find.text('AI chats'), findsOneWidget);
        expect(find.text('Release coach'), findsOneWidget);
      },
    );

    testWidgets(
      'keeps favorites and AI areas visible when backend data is not ready',
      (tester) async {
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
          createTestApp(
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

        expect(find.text('No unread work'), findsOneWidget);
        expect(find.text('1 channel workspace'), findsOneWidget);
        expect(find.text('1 personal message'), findsOneWidget);
        expect(find.text('AI governed by workspace policy'), findsOneWidget);
        expect(find.text('Favorites'), findsOneWidget);
        expect(
          find.text(
            'No favorites yet. Important direct messages, channels, and AI chats marked as favorites stay here.',
          ),
          findsOneWidget,
        );

        await tester.ensureVisible(find.text('AI chats'));
        await tester.pumpAndSettle();

        expect(find.text('AI chats'), findsOneWidget);
        expect(
          find.text(
            'AI chats are not enabled for this workspace. A workspace owner or admin can enable governed assistants after policy, consent, and audit controls are ready.',
          ),
          findsOneWidget,
        );
      },
    );

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
        createTestApp(
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
          'Chat could not refresh just now. Your conversation list is preserved so you can keep your place and retry when the connection is back.',
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
        createTestApp(
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
        createTestApp(
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

    testWidgets('renders bounded Weaver Beta helper states accessibly', (
      tester,
    ) async {
      final repository = FakeChatRepository(
        loadConversationsHandler: () async => const <ChatConversation>[
          ChatConversation(
            id: '!ai:home.internal',
            title: 'Weaver notes',
            previewType: ChatConversationPreviewType.text,
            previewText: 'Structured result ready',
            unreadCount: 0,
            isInvite: false,
            isDirectMessage: false,
          ),
        ],
      );
      final securityRepository = buildSecurityRepository();
      await tester.pumpWidget(
        createTestApp(
          const ChatScreen(),
          overrides: [
            chatRepositoryProvider.overrideWithValue(repository),
            chatSecurityRepositoryProvider.overrideWithValue(
              securityRepository,
            ),
            agentCapabilityPolicyProvider.overrideWithValue(
              const AsyncData(
                AgentCapabilityPolicy(
                  canManageCapabilities: false,
                  capabilities: <AgentCapabilityState>[
                    AgentCapabilityState(
                      capability: AgentCapability.personalAssistant,
                      enablement: AgentCapabilityEnablement.enabled,
                      availability:
                          AgentCapabilityAvailability.adminSetupRequired,
                    ),
                    AgentCapabilityState(
                      capability: AgentCapability.channelAgent,
                      enablement: AgentCapabilityEnablement.disabled,
                      availability:
                          AgentCapabilityAvailability.disabledByPolicy,
                    ),
                  ],
                ),
              ),
            ),
          ],
        ),
      );
      await tester.pumpAndSettle();

      expect(find.text('Weaver Beta helper'), findsOneWidget);
      expect(find.text('Connected'), findsOneWidget);
      expect(find.text('Weaver enabled'), findsOneWidget);
      expect(find.text('Weaver disabled'), findsOneWidget);
      expect(
        find.text('Approval required for sensitive actions'),
        findsOneWidget,
      );
      expect(find.text('Denied or failed safely'), findsOneWidget);
      expect(find.textContaining('raw provider payloads'), findsOneWidget);
      expect(find.textContaining('MCP'), findsNothing);
      expect(find.textContaining('tool catalog'), findsNothing);
      expect(
        find.bySemanticsLabel(RegExp('Results are support-safe')),
        findsOneWidget,
      );
    });
  });
}
