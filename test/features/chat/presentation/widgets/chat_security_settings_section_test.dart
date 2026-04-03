import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:weave/features/chat/domain/entities/chat_security_state.dart';
import 'package:weave/features/chat/presentation/providers/chat_security_repository_provider.dart';
import 'package:weave/features/chat/presentation/widgets/chat_security_settings_section.dart';

import '../../../../helpers/fake_chat_security_repository.dart';
import '../../../../helpers/test_app.dart';

void main() {
  group('ChatSecuritySettingsSection', () {
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
