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
