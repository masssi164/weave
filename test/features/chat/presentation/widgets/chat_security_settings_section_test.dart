import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:weave/features/chat/domain/entities/chat_security_state.dart';
import 'package:weave/features/chat/presentation/providers/chat_security_repository_provider.dart';
import 'package:weave/features/chat/presentation/widgets/chat_security_settings_section.dart';

import '../../../../helpers/fake_chat_security_repository.dart';
import '../../../../helpers/test_app.dart';

void main() {
  group('ChatSecuritySettingsSection', () {
    testWidgets('documents backend metadata and agent E2EE boundaries', (
      tester,
    ) async {
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

      await tester.pumpWidget(
        createTestApp(
          const SingleChildScrollView(child: ChatSecuritySettingsSection()),
          overrides: [
            chatSecurityRepositoryProvider.overrideWithValue(repository),
          ],
        ),
      );
      await tester.pumpAndSettle();

      expect(find.text('Backend and agent boundary'), findsOneWidget);
      expect(find.text('Blocked until consent/audit'), findsOneWidget);
      expect(
        find.textContaining(
          'Encrypted message contents stay on Matrix devices',
        ),
        findsOneWidget,
      );
      expect(
        find.textContaining('Bots and connectors stay blocked'),
        findsOneWidget,
      );
      expect(
        find.bySemanticsLabel(
          RegExp(
            'Backend and agent boundary.*Blocked until consent/audit.*not decrypted message bodies',
            dotAll: true,
          ),
        ),
        findsOneWidget,
      );
      expect(find.text('Device recovery checklist'), findsOneWidget);
      expect(find.text('Ready for device changes'), findsOneWidget);
      expect(
        find.textContaining('cannot recover encrypted message contents'),
        findsOneWidget,
      );
    });

    testWidgets('shows actionable device recovery guidance', (tester) async {
      final repository = FakeChatSecurityRepository(
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
            secretStorageReady: false,
            crossSigningReady: false,
            hasEncryptedConversations: true,
            verificationSession: ChatVerificationSession.none(),
          );
        },
      );

      await tester.pumpWidget(
        createTestApp(
          const SingleChildScrollView(child: ChatSecuritySettingsSection()),
          overrides: [
            chatSecurityRepositoryProvider.overrideWithValue(repository),
          ],
        ),
      );
      await tester.pumpAndSettle();

      expect(find.text('Device recovery checklist'), findsOneWidget);
      expect(find.text('Action required'), findsOneWidget);
      expect(find.textContaining('new or reinstalled device'), findsOneWidget);
      expect(find.textContaining('device is lost'), findsOneWidget);
      expect(
        find.bySemanticsLabel(
          RegExp(
            'Device recovery checklist.*Action required.*Save the recovery key.*new or reinstalled device.*cannot recover encrypted message contents',
            dotAll: true,
          ),
        ),
        findsOneWidget,
      );
    });

    testWidgets('continues verification with recovery material', (
      tester,
    ) async {
      String? submittedRecoveryMaterial;
      final repository = FakeChatSecurityRepository(
        loadSecurityStateHandler: ({bool refresh = false}) async {
          return const ChatSecurityState(
            isMatrixSignedIn: true,
            bootstrapState: ChatSecurityBootstrapState.ready,
            accountVerificationState: ChatAccountVerificationState.verified,
            deviceVerificationState: ChatDeviceVerificationState.unverified,
            keyBackupState: ChatKeyBackupState.ready,
            roomEncryptionReadiness: ChatRoomEncryptionReadiness.ready,
            secretStorageReady: true,
            crossSigningReady: true,
            hasEncryptedConversations: true,
            verificationSession: ChatVerificationSession(
              phase: ChatVerificationPhase.needsRecoveryKey,
            ),
          );
        },
        unlockVerificationHandler:
            ({required String recoveryKeyOrPassphrase}) async {
              submittedRecoveryMaterial = recoveryKeyOrPassphrase;
            },
      );

      await tester.pumpWidget(
        createTestApp(
          const SingleChildScrollView(child: ChatSecuritySettingsSection()),
          overrides: [
            chatSecurityRepositoryProvider.overrideWithValue(repository),
          ],
        ),
      );
      await tester.pumpAndSettle();

      expect(
        find.text('Continue verification with recovery key'),
        findsOneWidget,
      );
      expect(
        find.textContaining('needs your Matrix recovery key'),
        findsOneWidget,
      );

      await tester.ensureVisible(
        find.widgetWithText(
          FilledButton,
          'Continue verification with recovery key',
        ),
      );
      await tester.pumpAndSettle();

      await tester.tap(
        find.widgetWithText(
          FilledButton,
          'Continue verification with recovery key',
        ),
      );
      await tester.pumpAndSettle();

      expect(find.text('Continue verification'), findsOneWidget);
      await tester.enterText(
        find.byWidgetPredicate(
          (widget) =>
              widget is TextField &&
              widget.decoration?.labelText == 'Recovery key or passphrase',
        ),
        'RECOVERY-KEY',
      );

      await tester.tap(
        find
            .widgetWithText(
              FilledButton,
              'Continue verification with recovery key',
            )
            .last,
      );
      await tester.pumpAndSettle();

      expect(submittedRecoveryMaterial, 'RECOVERY-KEY');
      expect(find.text('Continue verification'), findsNothing);
    });
  });
}
