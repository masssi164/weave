import 'package:test/test.dart';
import 'package:weave/features/chat/domain/entities/chat_security_state.dart';

void main() {
  group('ChatSecurityState readinessState', () {
    test('distinguishes Matrix signed-out from signed-in readiness', () {
      expect(
        _state(isMatrixSignedIn: false).readinessState,
        ChatReadinessState.matrixNotSignedIn,
      );
      expect(_state().readinessState, ChatReadinessState.matrixSignedIn);
    });

    test('distinguishes E2EE unavailable and encrypted timeline states', () {
      expect(
        _state(
          bootstrapState: ChatSecurityBootstrapState.unavailable,
          roomEncryptionReadiness: ChatRoomEncryptionReadiness.unavailable,
        ).readinessState,
        ChatReadinessState.e2eeUnavailable,
      );
      expect(
        _state(hasEncryptedConversations: true).readinessState,
        ChatReadinessState.e2eeEncryptedTimeline,
      );
    });
  });
}

ChatSecurityState _state({
  bool isMatrixSignedIn = true,
  ChatSecurityBootstrapState bootstrapState = ChatSecurityBootstrapState.ready,
  ChatRoomEncryptionReadiness roomEncryptionReadiness =
      ChatRoomEncryptionReadiness.ready,
  bool hasEncryptedConversations = false,
}) {
  return ChatSecurityState(
    isMatrixSignedIn: isMatrixSignedIn,
    bootstrapState: bootstrapState,
    accountVerificationState: ChatAccountVerificationState.verified,
    deviceVerificationState: ChatDeviceVerificationState.verified,
    keyBackupState: ChatKeyBackupState.ready,
    roomEncryptionReadiness: roomEncryptionReadiness,
    secretStorageReady: true,
    crossSigningReady: true,
    hasEncryptedConversations: hasEncryptedConversations,
    verificationSession: const ChatVerificationSession.none(),
  );
}
