import 'dart:io';

// Shared member-session interfaces and existing device-store keys.
// Legacy key names preserve client-owned crypto continuity; they do not
// establish an independent Matrix OAuth authority.
const matrixCryptoStorePassphraseKeyPrefix =
    'matrix_crypto_store_passphrase_v1_';
const matrixOAuthBindingKeyPrefix = 'matrix_oauth_binding_v1_';
const matrixOAuthCurrentBindingKey = 'matrix_oauth_current_binding_v1';
const matrixOAuthProfileOwnerKeyPrefix = 'matrix_oauth_profile_owner_v1_';
const matrixOAuthProfileOrganizationKeyPrefix =
    'matrix_oauth_profile_organization_v1_';

typedef MatrixStoreRootLoader = Future<Directory> Function();

class MatrixCryptoSession {
  const MatrixCryptoSession({
    required this.profileKey,
    required this.userId,
    required this.deviceId,
    this.memberStorageScope,
  });

  final String profileKey;
  final String userId;
  final String deviceId;

  /// Opaque local storage selector from verified issuer, organization and member.
  /// It grants no room or session access and does not depend on token or device.
  final String? memberStorageScope;
}

abstract interface class MatrixCryptoSessionPort {
  Future<MatrixCryptoSession> open({
    bool synchronize = true,
    bool allowInteractiveSignIn = true,
  });

  Future<void> disposePreservingCryptoState();

  Future<void> endSession();

  Future<void> removeForExplicitAccountRemoval();
}

bool isMatrixSessionExpiredCode(String code) =>
    code == 'M_UNKNOWN_TOKEN' ||
    code == 'M_MISSING_TOKEN' ||
    code == 'M_WEAVE_MATRIX_SESSION_EXPIRED';
