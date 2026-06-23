import 'package:flutter_test/flutter_test.dart';
import 'package:weave/features/chat/domain/entities/chat_security_state.dart';

void main() {
  // E2EE_RESULT: offline diagnostic evidence for the Matrix security seam.
  group('ChatSecurityState readinessState', () {
    test('distinguishes Matrix signed-out from signed-in readiness', () {
      expect(
        _state(isMatrixSignedIn: false).readinessState,
        ChatReadinessState.matrixNotSignedIn,
      );
      expect(_state().readinessState, ChatReadinessState.matrixSignedIn);
    });

    test('keeps unsupported-device snapshots reachable before signed-out', () {
      expect(
        _state(
          isMatrixSignedIn: false,
          bootstrapState: ChatSecurityBootstrapState.unavailable,
          deviceVerificationState: ChatDeviceVerificationState.unavailable,
          roomEncryptionReadiness: ChatRoomEncryptionReadiness.unavailable,
        ).readinessState,
        ChatReadinessState.unsupportedDevice,
      );
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

    test('keeps provider and policy readiness buckets explicit', () {
      expect(
        ChatReadinessState.values,
        containsAll(<ChatReadinessState>[
          ChatReadinessState.unsupportedDevice,
          ChatReadinessState.providerUnavailable,
          ChatReadinessState.policyDisabled,
        ]),
      );
    });
  });
}

ChatSecurityState _state({
  bool isMatrixSignedIn = true,
  ChatSecurityBootstrapState bootstrapState = ChatSecurityBootstrapState.ready,
  ChatDeviceVerificationState deviceVerificationState =
      ChatDeviceVerificationState.verified,
  ChatRoomEncryptionReadiness roomEncryptionReadiness =
      ChatRoomEncryptionReadiness.ready,
  bool hasEncryptedConversations = false,
}) {
  return ChatSecurityState(
    isMatrixSignedIn: isMatrixSignedIn,
    bootstrapState: bootstrapState,
    accountVerificationState: ChatAccountVerificationState.verified,
    deviceVerificationState: deviceVerificationState,
    keyBackupState: ChatKeyBackupState.ready,
    roomEncryptionReadiness: roomEncryptionReadiness,
    secretStorageReady: true,
    crossSigningReady: true,
    hasEncryptedConversations: hasEncryptedConversations,
    verificationSession: const ChatVerificationSession.none(),
  );
}
