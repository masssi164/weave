import 'package:weave/features/chat/domain/entities/chat_failure.dart';
import 'package:weave/features/chat/domain/entities/chat_security_state.dart';
import 'package:weave/features/chat/domain/repositories/chat_security_repository.dart';
import 'package:weave/integrations/rust_matrix_core/data/services/matrix_crypto_session_coordinator.dart';
import 'package:weave/integrations/rust_matrix_core/data/services/rust_matrix_core_bridge.dart';

class RustMatrixCoreChatSecurityRepository implements ChatSecurityRepository {
  const RustMatrixCoreChatSecurityRepository({
    required MatrixCryptoSessionPort matrixCryptoSessionCoordinator,
    RustMatrixCoreBridge rustMatrixCoreBridge = const RustMatrixCoreBridge(),
  }) : _matrixCryptoSessionCoordinator = matrixCryptoSessionCoordinator,
       _rustMatrixCoreBridge = rustMatrixCoreBridge;

  final MatrixCryptoSessionPort _matrixCryptoSessionCoordinator;
  final RustMatrixCoreBridge _rustMatrixCoreBridge;

  @override
  Stream<ChatVerificationSession> watchVerificationUpdates() async* {
    String? previous;
    while (true) {
      try {
        final security = await loadSecurityState(refresh: true);
        final verification = security.verificationSession;
        final signature = _verificationSignature(verification);
        if (signature != previous) {
          previous = signature;
          yield verification;
        }
      } on ChatFailure {
        // The settings controller owns member-visible failure rendering.
      }
      await Future<void>.delayed(const Duration(seconds: 3));
    }
  }

  @override
  Future<ChatSecurityState> loadSecurityState({bool refresh = false}) async {
    try {
      final session = await _matrixCryptoSessionCoordinator.open(
        synchronize: refresh,
      );
      return _securityState(
        await _rustMatrixCoreBridge.loadSecurityState(
          profileKey: session.profileKey,
        ),
      );
    } on RustMatrixCoreBridgeException catch (error) {
      throw _failure(error, 'Matrix security state is unavailable.');
    }
  }

  @override
  Future<String> bootstrapSecurity({String? passphrase}) async {
    try {
      final session = await _matrixCryptoSessionCoordinator.open();
      return await _rustMatrixCoreBridge.bootstrapRecovery(
        profileKey: session.profileKey,
        passphrase: passphrase ?? '',
      );
    } on RustMatrixCoreBridgeException catch (error) {
      throw _failure(error, 'Matrix recovery could not be initialized.');
    }
  }

  @override
  Future<void> restoreSecurity({
    required String recoveryKeyOrPassphrase,
  }) async {
    try {
      final session = await _matrixCryptoSessionCoordinator.open();
      await _rustMatrixCoreBridge.recover(
        profileKey: session.profileKey,
        recoveryKeyOrPassphrase: recoveryKeyOrPassphrase,
      );
    } on RustMatrixCoreBridgeException catch (error) {
      throw _failure(error, 'Matrix recovery could not be restored.');
    }
  }

  @override
  Future<void> startVerification() => _verificationAction(
    (profileKey) =>
        _rustMatrixCoreBridge.startVerification(profileKey: profileKey),
  );

  @override
  Future<void> acceptVerification() => _verificationAction(
    (profileKey) =>
        _rustMatrixCoreBridge.acceptVerification(profileKey: profileKey),
  );

  @override
  Future<void> startSasVerification() => _verificationAction(
    (profileKey) => _rustMatrixCoreBridge.startSas(profileKey: profileKey),
  );

  @override
  Future<void> unlockVerification({required String recoveryKeyOrPassphrase}) =>
      restoreSecurity(recoveryKeyOrPassphrase: recoveryKeyOrPassphrase);

  @override
  Future<void> confirmSas({required bool matches}) => _verificationAction(
    (profileKey) => _rustMatrixCoreBridge.confirmSas(
      profileKey: profileKey,
      matches: matches,
    ),
  );

  @override
  Future<void> cancelVerification() => _verificationAction(
    (profileKey) =>
        _rustMatrixCoreBridge.cancelVerification(profileKey: profileKey),
  );

  @override
  Future<void> dismissVerificationResult() async {
    try {
      final session = await _matrixCryptoSessionCoordinator.open(
        synchronize: false,
      );
      await _rustMatrixCoreBridge.dismissVerification(
        profileKey: session.profileKey,
      );
    } on RustMatrixCoreBridgeException catch (error) {
      throw _failure(error, 'Matrix verification could not be dismissed.');
    }
  }

  Future<void> _verificationAction(
    Future<RustMatrixVerificationProjection> Function(String profileKey) action,
  ) async {
    try {
      final session = await _matrixCryptoSessionCoordinator.open();
      await action(session.profileKey);
    } on RustMatrixCoreBridgeException catch (error) {
      throw _failure(error, 'Matrix device verification could not continue.');
    }
  }

  ChatSecurityState _securityState(RustMatrixSecurityProjection projection) {
    final recoveryEnabled = projection.recoveryState == 'enabled';
    final bootstrapState = switch (projection.recoveryState) {
      'enabled' when projection.crossSigningReady =>
        ChatSecurityBootstrapState.ready,
      'disabled' => ChatSecurityBootstrapState.notInitialized,
      'incomplete' => ChatSecurityBootstrapState.recoveryRequired,
      _ => ChatSecurityBootstrapState.partiallyInitialized,
    };
    final deviceState = projection.deviceVerified
        ? ChatDeviceVerificationState.verified
        : ChatDeviceVerificationState.unverified;
    final roomsReady =
        projection.encryptedRoomCount > 0 &&
        recoveryEnabled &&
        projection.crossSigningReady;
    return ChatSecurityState(
      isMatrixSignedIn: projection.signedIn,
      bootstrapState: bootstrapState,
      accountVerificationState: projection.accountVerified
          ? ChatAccountVerificationState.verified
          : ChatAccountVerificationState.verificationRequired,
      deviceVerificationState: deviceState,
      keyBackupState: switch (projection.recoveryState) {
        'enabled' => ChatKeyBackupState.ready,
        'incomplete' => ChatKeyBackupState.recoveryRequired,
        _ => ChatKeyBackupState.missing,
      },
      roomEncryptionReadiness: projection.encryptedRoomCount == 0
          ? ChatRoomEncryptionReadiness.noEncryptedRooms
          : roomsReady
          ? ChatRoomEncryptionReadiness.ready
          : ChatRoomEncryptionReadiness.encryptedRoomsNeedAttention,
      secretStorageReady: recoveryEnabled,
      crossSigningReady: projection.crossSigningReady,
      hasEncryptedConversations: projection.encryptedRoomCount > 0,
      verificationSession: _verificationSession(projection.verification),
    );
  }

  ChatVerificationSession _verificationSession(
    RustMatrixVerificationProjection projection,
  ) {
    final phase = switch (projection.phase) {
      'incomingRequest' => ChatVerificationPhase.incomingRequest,
      'chooseMethod' => ChatVerificationPhase.chooseMethod,
      'waitingForOtherDevice' => ChatVerificationPhase.waitingForOtherDevice,
      'needsRecoveryKey' => ChatVerificationPhase.needsRecoveryKey,
      'compareSas' => ChatVerificationPhase.compareSas,
      'done' => ChatVerificationPhase.done,
      'cancelled' => ChatVerificationPhase.cancelled,
      'failed' => ChatVerificationPhase.failed,
      _ => ChatVerificationPhase.none,
    };
    return ChatVerificationSession(
      phase: phase,
      sasNumbers: projection.sasNumbers,
      sasEmojis: projection.sasEmojis
          .map(
            (emoji) => ChatVerificationEmoji(
              symbol: emoji['symbol'] as String? ?? '',
              label: emoji['label'] as String? ?? '',
            ),
          )
          .where((emoji) => emoji.symbol.isNotEmpty && emoji.label.isNotEmpty)
          .toList(growable: false),
    );
  }

  ChatFailure _failure(RustMatrixCoreBridgeException error, String message) {
    if (error.code == 'M_WEAVE_E2EE_NO_OTHER_DEVICE') {
      return const ChatFailure.unsupportedConfiguration(
        'Sign in on another trusted device or restore with the recovery key.',
      );
    }
    if (error.code == 'M_WEAVE_E2EE_NOT_INITIALIZED' ||
        error.code == 'M_WEAVE_E2EE_SESSION') {
      return ChatFailure.sessionRequired(message);
    }
    if (error.code == 'M_WEAVE_E2EE_STORE') {
      return ChatFailure.storage(message);
    }
    return ChatFailure.protocol(message);
  }

  String _verificationSignature(ChatVerificationSession verification) {
    return '${verification.phase.name}|${verification.sasNumbers.join(',')}|'
        '${verification.sasEmojis.map((emoji) => emoji.symbol).join()}';
  }
}
