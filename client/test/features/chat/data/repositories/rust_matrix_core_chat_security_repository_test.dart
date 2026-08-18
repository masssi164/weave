import 'package:flutter_test/flutter_test.dart';
import 'package:weave/features/chat/data/repositories/rust_matrix_core_chat_security_repository.dart';
import 'package:weave/features/chat/domain/entities/chat_failure.dart';
import 'package:weave/features/chat/domain/entities/chat_security_state.dart';
import 'package:weave/integrations/rust_matrix_core/data/services/rust_matrix_core_bridge.dart';

import '../../../../helpers/fake_matrix_crypto.dart';

class _NoOtherDeviceBridge extends FakeRustMatrixCoreBridge {
  @override
  Future<RustMatrixVerificationProjection> startVerification({
    required String profileKey,
  }) {
    throw const RustMatrixCoreBridgeException('M_WEAVE_E2EE_NO_OTHER_DEVICE');
  }
}

void main() {
  late FakeMatrixCryptoSessionPort cryptoSession;
  late FakeRustMatrixCoreBridge bridge;

  RustMatrixCoreChatSecurityRepository repository({
    FakeRustMatrixCoreBridge? rustBridge,
  }) {
    return RustMatrixCoreChatSecurityRepository(
      matrixCryptoSessionCoordinator: cryptoSession,
      rustMatrixCoreBridge: rustBridge ?? bridge,
    );
  }

  setUp(() {
    cryptoSession = FakeMatrixCryptoSessionPort();
    bridge = FakeRustMatrixCoreBridge();
  });

  test('maps SDK recovery, cross-signing, and encrypted-room state', () async {
    // MATRIX_E2EE_STATE_CONTRACT
    final security = await repository().loadSecurityState(refresh: true);

    expect(security.bootstrapState, ChatSecurityBootstrapState.ready);
    expect(
      security.accountVerificationState,
      ChatAccountVerificationState.verified,
    );
    expect(
      security.deviceVerificationState,
      ChatDeviceVerificationState.verified,
    );
    expect(security.keyBackupState, ChatKeyBackupState.ready);
    expect(security.roomEncryptionReadiness, ChatRoomEncryptionReadiness.ready);
    expect(security.readinessState, ChatReadinessState.e2eeEncryptedTimeline);
    expect(cryptoSession.synchronizeValues, <bool>[true]);
  });

  test('reports recovery-required state without claiming readiness', () async {
    bridge.security = const RustMatrixSecurityProjection(
      signedIn: true,
      recoveryState: 'incomplete',
      crossSigningReady: false,
      deviceVerified: false,
      accountVerified: false,
      encryptedRoomCount: 1,
      verification: RustMatrixVerificationProjection(
        phase: 'needsRecoveryKey',
        sasNumbers: <int>[],
        sasEmojis: <Map<String, dynamic>>[],
      ),
    );

    final security = await repository().loadSecurityState();

    expect(
      security.bootstrapState,
      ChatSecurityBootstrapState.recoveryRequired,
    );
    expect(security.keyBackupState, ChatKeyBackupState.recoveryRequired);
    expect(
      security.roomEncryptionReadiness,
      ChatRoomEncryptionReadiness.encryptedRoomsNeedAttention,
    );
    expect(
      security.verificationSession.phase,
      ChatVerificationPhase.needsRecoveryKey,
    );
    expect(security.requiresAttention, isTrue);
  });

  test('bootstrap and SAS actions delegate to the same Rust profile', () async {
    bridge.verification = const RustMatrixVerificationProjection(
      phase: 'compareSas',
      sasNumbers: <int>[1234, 5678, 9012],
      sasEmojis: <Map<String, dynamic>>[
        <String, dynamic>{'symbol': 'A', 'label': 'Alpha'},
      ],
    );

    final recoveryKey = await repository().bootstrapSecurity(
      passphrase: 'correct horse battery staple',
    );
    await repository().startVerification();
    await repository().acceptVerification();
    await repository().startSasVerification();
    await repository().confirmSas(matches: true);

    expect(recoveryKey, 'recovery-key');
    expect(cryptoSession.synchronizeValues, everyElement(isTrue));
  });

  test('explains when no second trusted device exists', () async {
    await expectLater(
      repository(rustBridge: _NoOtherDeviceBridge()).startVerification(),
      throwsA(
        isA<ChatFailure>()
            .having(
              (failure) => failure.type,
              'type',
              ChatFailureType.unsupportedConfiguration,
            )
            .having(
              (failure) => failure.message,
              'message',
              contains('recovery key'),
            ),
      ),
    );
  });
}
