const matrixOidcClientName = 'Weave';
const matrixOidcClientUri = 'https://github.com/masssi164/weave';
const matrixOidcRedirectScheme = 'com.massimotter.weave.matrix';
const matrixOidcRedirectUri = '$matrixOidcRedirectScheme:/oauthredirect';

enum MatrixRoomPreviewType { none, text, encrypted, unsupported }

enum MatrixSecurityBootstrapState {
  signedOut,
  notInitialized,
  partiallyInitialized,
  recoveryRequired,
  ready,
  unavailable,
}

enum MatrixAccountVerificationState { verified, verificationRequired, unavailable }

enum MatrixDeviceVerificationState {
  verified,
  unverified,
  blocked,
  unavailable,
}

enum MatrixKeyBackupState { unavailable, missing, recoveryRequired, ready }

enum MatrixRoomEncryptionReadiness {
  unavailable,
  noEncryptedRooms,
  encryptedRoomsNeedAttention,
  ready,
}

enum MatrixVerificationPhase {
  none,
  incomingRequest,
  chooseMethod,
  waitingForOtherDevice,
  compareSas,
  done,
  cancelled,
  failed,
}

class MatrixVerificationEmoji {
  const MatrixVerificationEmoji({required this.symbol, required this.label});

  final String symbol;
  final String label;
}

class MatrixVerificationSnapshot {
  const MatrixVerificationSnapshot({
    required this.phase,
    this.message,
    this.sasNumbers = const <int>[],
    this.sasEmojis = const <MatrixVerificationEmoji>[],
  });

  const MatrixVerificationSnapshot.none()
    : this(phase: MatrixVerificationPhase.none);

  final MatrixVerificationPhase phase;
  final String? message;
  final List<int> sasNumbers;
  final List<MatrixVerificationEmoji> sasEmojis;
}

class MatrixSecuritySnapshot {
  const MatrixSecuritySnapshot({
    required this.isMatrixSignedIn,
    required this.bootstrapState,
    required this.accountVerificationState,
    required this.deviceVerificationState,
    required this.keyBackupState,
    required this.roomEncryptionReadiness,
    required this.secretStorageReady,
    required this.crossSigningReady,
    required this.hasEncryptedConversations,
    this.verification = const MatrixVerificationSnapshot.none(),
  });

  final bool isMatrixSignedIn;
  final MatrixSecurityBootstrapState bootstrapState;
  final MatrixAccountVerificationState accountVerificationState;
  final MatrixDeviceVerificationState deviceVerificationState;
  final MatrixKeyBackupState keyBackupState;
  final MatrixRoomEncryptionReadiness roomEncryptionReadiness;
  final bool secretStorageReady;
  final bool crossSigningReady;
  final bool hasEncryptedConversations;
  final MatrixVerificationSnapshot verification;
}

class MatrixRoomSnapshot {
  const MatrixRoomSnapshot({
    required this.id,
    required this.title,
    required this.previewType,
    required this.unreadCount,
    required this.isInvite,
    required this.isDirectMessage,
    this.previewText,
    this.lastActivityAt,
  });

  final String id;
  final String title;
  final MatrixRoomPreviewType previewType;
  final String? previewText;
  final DateTime? lastActivityAt;
  final int unreadCount;
  final bool isInvite;
  final bool isDirectMessage;
}

abstract interface class MatrixClient {
  Stream<MatrixVerificationSnapshot> get verificationUpdates;

  Future<List<MatrixRoomSnapshot>> loadConversations({required Uri homeserver});

  Future<void> connect({required Uri homeserver});

  Future<MatrixSecuritySnapshot> loadSecurityState({
    required Uri homeserver,
    bool refresh = false,
  });

  Future<String> bootstrapSecurity({
    required Uri homeserver,
    String? passphrase,
  });

  Future<void> restoreSecurity({
    required Uri homeserver,
    required String recoveryKeyOrPassphrase,
  });

  Future<void> startVerification({required Uri homeserver});

  Future<void> acceptVerification({required Uri homeserver});

  Future<void> startSasVerification({required Uri homeserver});

  Future<void> confirmSas({
    required Uri homeserver,
    required bool matches,
  });

  Future<void> cancelVerification({required Uri homeserver});

  Future<void> dismissVerificationResult({required Uri homeserver});

  Future<void> signOut();

  Future<void> clearSession();

  Future<void> dispose();
}
