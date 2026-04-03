import 'dart:async';

import 'package:flutter_test/flutter_test.dart';
import 'package:weave/features/chat/domain/entities/chat_conversation.dart';
import 'package:weave/features/chat/domain/entities/chat_failure.dart';
import 'package:weave/features/chat/domain/entities/chat_security_state.dart';
import 'package:weave/features/chat/presentation/chat_screen.dart';
import 'package:weave/features/chat/presentation/providers/chat_repository_provider.dart';
import 'package:weave/features/chat/presentation/providers/chat_security_repository_provider.dart';

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
            'Connect Weave to your Matrix homeserver to load conversations.',
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

      expect(find.text('Connecting to Matrix…'), findsOneWidget);
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
              'Connect Weave to your Matrix homeserver to load conversations.',
            );
          }

          return const <ChatConversation>[];
        };
        repository.connectHandler = () async {
          throw const ChatFailure.unsupportedConfiguration(
            'The configured Matrix homeserver at https://matrix.home.internal '
            'does not advertise Matrix OAuth 2.0 metadata. '
            'Weave currently requires Matrix Native OAuth 2.0 for chat.',
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
          find.textContaining('does not advertise Matrix OAuth 2.0 metadata'),
          findsOneWidget,
        );
        expect(find.text('Connect Matrix'), findsOneWidget);
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
            'Connect Weave to your Matrix homeserver to load conversations.',
          );
        }

        if (repository.connectCalls == 1) {
          throw const ChatFailure.sessionRequired(
            'Connect Weave to your Matrix homeserver to load conversations.',
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
            'Matrix sign-in was cancelled before it completed.',
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

      expect(find.text('Connect Matrix'), findsOneWidget);

      await tester.tap(find.text('Connect Matrix'));
      await tester.pumpAndSettle();

      expect(find.text('Sam'), findsOneWidget);
      expect(repository.connectCalls, 2);
    });

    testWidgets('shows the empty state when there are no conversations', (
      tester,
    ) async {
      final repository = FakeChatRepository(
        loadConversationsHandler: () async => const <ChatConversation>[],
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
    });

    testWidgets('shows the Matrix security banner when attention is needed', (
      tester,
    ) async {
      final repository = FakeChatRepository(
        loadConversationsHandler: () async => const <ChatConversation>[
          ChatConversation(
            id: '!room:home.internal',
            title: 'Project',
            previewType: ChatConversationPreviewType.encrypted,
            unreadCount: 0,
            isInvite: false,
            isDirectMessage: false,
          ),
        ],
      );
      final securityRepository = FakeChatSecurityRepository(
        loadSecurityStateHandler: ({bool refresh = false}) async {
          return const ChatSecurityState(
            isMatrixSignedIn: true,
            bootstrapState: ChatSecurityBootstrapState.recoveryRequired,
            accountVerificationState:
                ChatAccountVerificationState.verificationRequired,
            deviceVerificationState: ChatDeviceVerificationState.unverified,
            keyBackupState: ChatKeyBackupState.recoveryRequired,
            roomEncryptionReadiness:
                ChatRoomEncryptionReadiness.encryptedRoomsNeedAttention,
            secretStorageReady: true,
            crossSigningReady: true,
            hasEncryptedConversations: true,
            verificationSession: ChatVerificationSession.none(),
          );
        },
      );

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

      expect(find.text('Matrix security needs attention'), findsOneWidget);
      expect(find.text('Open security settings'), findsOneWidget);
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
  });
}
