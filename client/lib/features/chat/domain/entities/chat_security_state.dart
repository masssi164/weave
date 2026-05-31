enum ChatSecurityBootstrapState {
  signedOut,
  notInitialized,
  partiallyInitialized,
  recoveryRequired,
  ready,
  unavailable,
}

enum ChatAccountVerificationState {
  verified,
  verificationRequired,
  unavailable,
}

enum ChatDeviceVerificationState { verified, unverified, blocked, unavailable }

enum ChatKeyBackupState { unavailable, missing, recoveryRequired, ready }

enum ChatRoomEncryptionReadiness {
  unavailable,
  noEncryptedRooms,
  encryptedRoomsNeedAttention,
  ready,
}

/// Member-safe readiness buckets for the Matrix-backed Chat path.
///
/// This intentionally stays provider-owned but support-safe: widgets/tests can
/// distinguish signed-out, unsupported, E2EE-unavailable, encrypted-timeline,
/// provider-unavailable, and policy-disabled cases without consuming raw Matrix
/// SDK diagnostics.
enum ChatReadinessState {
  matrixSignedIn,
  matrixNotSignedIn,
  unsupportedDevice,
  e2eeUnavailable,
  e2eeEncryptedTimeline,
  providerUnavailable,
  policyDisabled,
}

enum ChatVerificationPhase {
  none,
  incomingRequest,
  chooseMethod,
  waitingForOtherDevice,
  needsRecoveryKey,
  compareSas,
  done,
  cancelled,
  failed,
}

enum ChatSecurityActionNotice {
  setupComplete,
  recoveryRestored,
  verificationRequestSent,
  verificationCancelled,
}

class ChatVerificationEmoji {
  const ChatVerificationEmoji({required this.symbol, required this.label});

  final String symbol;
  final String label;
}

class ChatVerificationSession {
  const ChatVerificationSession({
    required this.phase,
    this.message,
    this.sasNumbers = const <int>[],
    this.sasEmojis = const <ChatVerificationEmoji>[],
  });

  const ChatVerificationSession.none()
    : this(phase: ChatVerificationPhase.none);

  final ChatVerificationPhase phase;
  final String? message;
  final List<int> sasNumbers;
  final List<ChatVerificationEmoji> sasEmojis;

  bool get isActionable =>
      phase == ChatVerificationPhase.incomingRequest ||
      phase == ChatVerificationPhase.chooseMethod ||
      phase == ChatVerificationPhase.needsRecoveryKey ||
      phase == ChatVerificationPhase.compareSas ||
      phase == ChatVerificationPhase.done ||
      phase == ChatVerificationPhase.cancelled ||
      phase == ChatVerificationPhase.failed;

  bool get isOngoing =>
      phase == ChatVerificationPhase.incomingRequest ||
      phase == ChatVerificationPhase.chooseMethod ||
      phase == ChatVerificationPhase.waitingForOtherDevice ||
      phase == ChatVerificationPhase.needsRecoveryKey ||
      phase == ChatVerificationPhase.compareSas;
}

class ChatSecurityState {
  const ChatSecurityState({
    required this.isMatrixSignedIn,
    required this.bootstrapState,
    required this.accountVerificationState,
    required this.deviceVerificationState,
    required this.keyBackupState,
    required this.roomEncryptionReadiness,
    required this.secretStorageReady,
    required this.crossSigningReady,
    required this.hasEncryptedConversations,
    required this.verificationSession,
  });

  final bool isMatrixSignedIn;
  final ChatSecurityBootstrapState bootstrapState;
  final ChatAccountVerificationState accountVerificationState;
  final ChatDeviceVerificationState deviceVerificationState;
  final ChatKeyBackupState keyBackupState;
  final ChatRoomEncryptionReadiness roomEncryptionReadiness;
  final bool secretStorageReady;
  final bool crossSigningReady;
  final bool hasEncryptedConversations;
  final ChatVerificationSession verificationSession;

  ChatReadinessState get readinessState {
    if (!isMatrixSignedIn &&
        bootstrapState == ChatSecurityBootstrapState.unavailable &&
        deviceVerificationState == ChatDeviceVerificationState.unavailable &&
        roomEncryptionReadiness == ChatRoomEncryptionReadiness.unavailable) {
      return ChatReadinessState.unsupportedDevice;
    }
    if (!isMatrixSignedIn) {
      return ChatReadinessState.matrixNotSignedIn;
    }
    if (bootstrapState == ChatSecurityBootstrapState.unavailable &&
        roomEncryptionReadiness == ChatRoomEncryptionReadiness.unavailable) {
      return ChatReadinessState.e2eeUnavailable;
    }
    if (hasEncryptedConversations) {
      return ChatReadinessState.e2eeEncryptedTimeline;
    }
    return ChatReadinessState.matrixSignedIn;
  }

  bool get requiresAttention =>
      !isMatrixSignedIn ||
      bootstrapState == ChatSecurityBootstrapState.notInitialized ||
      bootstrapState == ChatSecurityBootstrapState.partiallyInitialized ||
      bootstrapState == ChatSecurityBootstrapState.recoveryRequired ||
      accountVerificationState ==
          ChatAccountVerificationState.verificationRequired ||
      deviceVerificationState != ChatDeviceVerificationState.verified ||
      keyBackupState == ChatKeyBackupState.missing ||
      keyBackupState == ChatKeyBackupState.recoveryRequired ||
      verificationSession.isActionable;
}
