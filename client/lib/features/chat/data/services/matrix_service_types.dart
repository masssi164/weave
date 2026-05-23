// Internal Matrix types – never exported outside features/chat/.
// These constants, enums and value objects are shared across the internal
// service layer.

const matrixOidcClientName = 'Weave';
const matrixOidcClientUri = 'https://github.com/masssi164/weave';
const matrixOidcContact = 'support@weave.local';
const matrixOidcLoopbackRedirectHost = '127.0.0.1';
const matrixOidcRedirectPath = '/oauthredirect';

enum MatrixRoomPreviewType { none, text, encrypted, unsupported }

enum MatrixMessageDeliveryState { sending, sent, failed }

enum MatrixMessageContentType { text, encrypted, unsupported }

enum MatrixSecurityBootstrapState {
  signedOut,
  notInitialized,
  partiallyInitialized,
  recoveryRequired,
  ready,
  unavailable,
}

enum MatrixAccountVerificationState {
  verified,
  verificationRequired,
  unavailable,
}

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
  needsRecoveryKey,
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

class MatrixTimelineMessageSnapshot {
  const MatrixTimelineMessageSnapshot({
    required this.id,
    required this.senderId,
    required this.senderDisplayName,
    required this.sentAt,
    required this.isMine,
    required this.deliveryState,
    required this.contentType,
    this.text,
  });

  final String id;
  final String senderId;
  final String senderDisplayName;
  final DateTime sentAt;
  final bool isMine;
  final MatrixMessageDeliveryState deliveryState;
  final MatrixMessageContentType contentType;
  final String? text;
}

class MatrixRoomTimelineSnapshot {
  const MatrixRoomTimelineSnapshot({
    required this.roomId,
    required this.roomTitle,
    required this.isInvite,
    required this.canSendMessages,
    required this.messages,
  });

  final String roomId;
  final String roomTitle;
  final bool isInvite;
  final bool canSendMessages;
  final List<MatrixTimelineMessageSnapshot> messages;
}
