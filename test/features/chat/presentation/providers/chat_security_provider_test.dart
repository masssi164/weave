import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:weave/features/chat/domain/entities/chat_failure.dart';
import 'package:weave/features/chat/domain/entities/chat_security_state.dart';
import 'package:weave/features/chat/presentation/providers/chat_security_provider.dart';
import 'package:weave/features/chat/presentation/providers/chat_security_repository_provider.dart';

import '../../../../helpers/fake_chat_security_repository.dart';

void main() {
  group('ChatSecurityController', () {
    test('loads the current security state on first refresh', () async {
      final repository = FakeChatSecurityRepository(
        loadSecurityStateHandler: ({bool refresh = false}) async {
          return const ChatSecurityState(
            isMatrixSignedIn: true,
            bootstrapState: ChatSecurityBootstrapState.ready,
            accountVerificationState: ChatAccountVerificationState.verified,
            deviceVerificationState: ChatDeviceVerificationState.verified,
            keyBackupState: ChatKeyBackupState.ready,
            roomEncryptionReadiness: ChatRoomEncryptionReadiness.ready,
            secretStorageReady: true,
            crossSigningReady: true,
            hasEncryptedConversations: true,
            verificationSession: ChatVerificationSession.none(),
          );
        },
      );
      final container = ProviderContainer(
        overrides: [
          chatSecurityRepositoryProvider.overrideWithValue(repository),
        ],
      );
      addTearDown(container.dispose);

      await container.read(chatSecurityProvider.notifier).refresh();
      final state = container.read(chatSecurityProvider);

      expect(state.security?.bootstrapState, ChatSecurityBootstrapState.ready);
      expect(state.failure, isNull);
    });

    test('stores the generated recovery key after bootstrap', () async {
      final repository = FakeChatSecurityRepository(
        loadSecurityStateHandler: ({bool refresh = false}) async {
          return const ChatSecurityState(
            isMatrixSignedIn: true,
            bootstrapState: ChatSecurityBootstrapState.ready,
            accountVerificationState: ChatAccountVerificationState.verified,
            deviceVerificationState: ChatDeviceVerificationState.verified,
            keyBackupState: ChatKeyBackupState.ready,
            roomEncryptionReadiness: ChatRoomEncryptionReadiness.ready,
            secretStorageReady: true,
            crossSigningReady: true,
            hasEncryptedConversations: true,
            verificationSession: ChatVerificationSession.none(),
          );
        },
        bootstrapSecurityHandler: ({String? passphrase}) async => 'ABC-123',
      );
      final container = ProviderContainer(
        overrides: [
          chatSecurityRepositoryProvider.overrideWithValue(repository),
        ],
      );
      addTearDown(container.dispose);
      await container.read(chatSecurityProvider.notifier).refresh();

      await container.read(chatSecurityProvider.notifier).bootstrap();
      final state = container.read(chatSecurityProvider);

      expect(state.generatedRecoveryKey, 'ABC-123');
      expect(state.lastActionNotice, ChatSecurityActionNotice.setupComplete);
    });

    test('surfaces repository failures', () async {
      final repository = FakeChatSecurityRepository(
        loadSecurityStateHandler: ({bool refresh = false}) async {
          throw const ChatFailure.storage('Unable to read Matrix crypto state.');
        },
      );
      final container = ProviderContainer(
        overrides: [
          chatSecurityRepositoryProvider.overrideWithValue(repository),
        ],
      );
      addTearDown(container.dispose);

      await container.read(chatSecurityProvider.notifier).refresh();
      final state = container.read(chatSecurityProvider);

      expect(state.failure?.type, ChatFailureType.storage);
    });

    test('refreshes when a verification event arrives', () async {
      var loadCount = 0;
      final repository = FakeChatSecurityRepository(
        loadSecurityStateHandler: ({bool refresh = false}) async {
          loadCount++;
          return ChatSecurityState(
            isMatrixSignedIn: true,
            bootstrapState: ChatSecurityBootstrapState.ready,
            accountVerificationState: ChatAccountVerificationState.verified,
            deviceVerificationState: ChatDeviceVerificationState.verified,
            keyBackupState: ChatKeyBackupState.ready,
            roomEncryptionReadiness: ChatRoomEncryptionReadiness.ready,
            secretStorageReady: true,
            crossSigningReady: true,
            hasEncryptedConversations: true,
            verificationSession: loadCount > 1
                ? const ChatVerificationSession(
                    phase: ChatVerificationPhase.incomingRequest,
                  )
                : const ChatVerificationSession.none(),
          );
        },
      );
      final container = ProviderContainer(
        overrides: [
          chatSecurityRepositoryProvider.overrideWithValue(repository),
        ],
      );
      addTearDown(container.dispose);

      await container.read(chatSecurityProvider.notifier).refresh();
      repository.emitVerificationUpdate(
        const ChatVerificationSession(
          phase: ChatVerificationPhase.incomingRequest,
        ),
      );
      await Future<void>.delayed(Duration.zero);
      await Future<void>.delayed(Duration.zero);

      expect(
        container.read(chatSecurityProvider).security?.verificationSession.phase,
        ChatVerificationPhase.incomingRequest,
      );
      expect(loadCount, greaterThanOrEqualTo(2));
    });
  });
}
