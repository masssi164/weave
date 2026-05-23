import 'package:matrix/encryption/utils/crypto_setup_extension.dart';
import 'package:matrix/matrix.dart' as sdk;
import 'package:riverpod/riverpod.dart';
import 'package:weave/features/chat/data/services/matrix_client_factory.dart';
import 'package:weave/features/chat/data/services/matrix_error_mapper.dart';
import 'package:weave/features/chat/data/services/matrix_service_types.dart';
import 'package:weave/features/chat/domain/entities/chat_failure.dart';

/// Handles crypto bootstrap, key-backup recovery, and security state
/// snapshot construction.
abstract interface class MatrixSecurityService {
  /// Returns the current security state for the given [homeserver].
  ///
  /// Performs a one-shot sync first when [refresh] is `true`.
  /// Returns an all-[MatrixSecurityBootstrapState.unavailable] snapshot on
  /// unsupported platforms instead of throwing.
  Future<MatrixSecuritySnapshot> loadSecurityState({
    required Uri homeserver,
    bool refresh = false,
  });

  /// Bootstraps the crypto identity (SSSS, cross-signing, key backup).
  ///
  /// Returns the generated recovery key.
  Future<String> bootstrapSecurity({
    required Uri homeserver,
    String? passphrase,
  });

  /// Reconnects the crypto identity using the given recovery key or passphrase.
  Future<void> restoreSecurity({
    required Uri homeserver,
    required String recoveryKeyOrPassphrase,
  });
}

class SdkMatrixSecurityService implements MatrixSecurityService {
  const SdkMatrixSecurityService({required MatrixClientFactory factory})
    : _factory = factory;

  final MatrixClientFactory _factory;

  static const _unavailableSnapshot = MatrixSecuritySnapshot(
    isMatrixSignedIn: false,
    bootstrapState: MatrixSecurityBootstrapState.unavailable,
    accountVerificationState: MatrixAccountVerificationState.unavailable,
    deviceVerificationState: MatrixDeviceVerificationState.unavailable,
    keyBackupState: MatrixKeyBackupState.unavailable,
    roomEncryptionReadiness: MatrixRoomEncryptionReadiness.unavailable,
    secretStorageReady: false,
    crossSigningReady: false,
    hasEncryptedConversations: false,
  );

  @override
  Future<MatrixSecuritySnapshot> loadSecurityState({
    required Uri homeserver,
    bool refresh = false,
  }) async {
    final sdk.Client client;
    try {
      client = await _factory.getClientForHomeserver(homeserver);
    } on ChatFailure catch (e) {
      if (e.type == ChatFailureType.unsupportedPlatform) {
        return _unavailableSnapshot;
      }
      rethrow;
    }

    if (!client.isLogged()) {
      return const MatrixSecuritySnapshot(
        isMatrixSignedIn: false,
        bootstrapState: MatrixSecurityBootstrapState.signedOut,
        accountVerificationState: MatrixAccountVerificationState.unavailable,
        deviceVerificationState: MatrixDeviceVerificationState.unavailable,
        keyBackupState: MatrixKeyBackupState.unavailable,
        roomEncryptionReadiness: MatrixRoomEncryptionReadiness.unavailable,
        secretStorageReady: false,
        crossSigningReady: false,
        hasEncryptedConversations: false,
      );
    }

    try {
      if (refresh) {
        await client.oneShotSync();
      }
      return await _buildSecuritySnapshot(client);
    } catch (error) {
      throw mapMatrixServiceError(
        error,
        fallback: 'Unable to load the Matrix security state right now.',
      );
    }
  }

  @override
  Future<String> bootstrapSecurity({
    required Uri homeserver,
    String? passphrase,
  }) async {
    final client = await _requireLoggedInClient(homeserver);

    try {
      final recoveryKey = await client.initCryptoIdentity(
        passphrase: passphrase,
      );
      await client.oneShotSync();
      return recoveryKey;
    } catch (error) {
      throw mapMatrixServiceError(
        error,
        fallback: 'Unable to finish the first encrypted-chat setup.',
      );
    }
  }

  @override
  Future<void> restoreSecurity({
    required Uri homeserver,
    required String recoveryKeyOrPassphrase,
  }) async {
    final client = await _requireLoggedInClient(homeserver);

    try {
      await client.restoreCryptoIdentity(recoveryKeyOrPassphrase);
      await client.oneShotSync();
    } catch (error) {
      throw mapMatrixServiceError(
        error,
        fallback: 'Unable to reconnect encrypted chat with that recovery key.',
      );
    }
  }

  Future<sdk.Client> _requireLoggedInClient(Uri homeserver) async {
    final client = await _factory.getClientForHomeserver(homeserver);
    if (!client.isLogged()) {
      throw const ChatFailure.sessionRequired(
        'Connect Weave to your Matrix homeserver before managing Matrix security.',
      );
    }
    return client;
  }

  Future<MatrixSecuritySnapshot> _buildSecuritySnapshot(
    sdk.Client client,
  ) async {
    final encryption = client.encryption;
    if (encryption == null || !client.encryptionEnabled) {
      return const MatrixSecuritySnapshot(
        isMatrixSignedIn: true,
        bootstrapState: MatrixSecurityBootstrapState.unavailable,
        accountVerificationState: MatrixAccountVerificationState.unavailable,
        deviceVerificationState: MatrixDeviceVerificationState.unavailable,
        keyBackupState: MatrixKeyBackupState.unavailable,
        roomEncryptionReadiness: MatrixRoomEncryptionReadiness.unavailable,
        secretStorageReady: false,
        crossSigningReady: false,
        hasEncryptedConversations: false,
      );
    }

    final userId = client.userID;
    final deviceId = client.deviceID;
    final ownKeys = userId == null ? null : client.userDeviceKeys[userId];
    final ownDevice = deviceId == null ? null : ownKeys?.deviceKeys[deviceId];
    final cryptoIdentityState = await client.getCryptoIdentityState();
    final secretStorageReady = encryption.ssss.defaultKeyId != null;
    final crossSigningReady = encryption.crossSigning.enabled;
    final keyBackupReady = encryption.keyManager.enabled;
    final keyBackupCached = keyBackupReady
        ? await encryption.keyManager.isCached()
        : false;

    return MatrixSecuritySnapshot(
      isMatrixSignedIn: true,
      bootstrapState: _bootstrapState(
        client,
        secretStorageReady: secretStorageReady,
        crossSigningReady: crossSigningReady,
        keyBackupReady: keyBackupReady,
        cryptoIdentityInitialized: cryptoIdentityState.initialized,
        cryptoIdentityConnected: cryptoIdentityState.connected,
      ),
      accountVerificationState: _accountVerificationState(ownKeys),
      deviceVerificationState: _deviceVerificationState(ownDevice),
      keyBackupState: _keyBackupState(
        keyBackupReady: keyBackupReady,
        keyBackupCached: keyBackupCached,
      ),
      roomEncryptionReadiness: _roomEncryptionReadiness(
        client,
        secretStorageReady: secretStorageReady,
        keyBackupReady: keyBackupReady,
        cryptoIdentityInitialized: cryptoIdentityState.initialized,
        cryptoIdentityConnected: cryptoIdentityState.connected,
      ),
      secretStorageReady: secretStorageReady,
      crossSigningReady: crossSigningReady,
      hasEncryptedConversations: client.rooms.any((room) => room.encrypted),
    );
  }

  MatrixSecurityBootstrapState _bootstrapState(
    sdk.Client client, {
    required bool secretStorageReady,
    required bool crossSigningReady,
    required bool keyBackupReady,
    required bool cryptoIdentityInitialized,
    required bool cryptoIdentityConnected,
  }) {
    if (!client.isLogged()) {
      return MatrixSecurityBootstrapState.signedOut;
    }
    if (!secretStorageReady && !crossSigningReady && !keyBackupReady) {
      return MatrixSecurityBootstrapState.notInitialized;
    }
    if (!secretStorageReady || !cryptoIdentityInitialized) {
      return MatrixSecurityBootstrapState.partiallyInitialized;
    }
    if (!cryptoIdentityConnected) {
      return MatrixSecurityBootstrapState.recoveryRequired;
    }
    return MatrixSecurityBootstrapState.ready;
  }

  MatrixAccountVerificationState _accountVerificationState(
    sdk.DeviceKeysList? ownKeys,
  ) {
    if (ownKeys == null) {
      return MatrixAccountVerificationState.unavailable;
    }
    return switch (ownKeys.verified) {
      sdk.UserVerifiedStatus.verified =>
        MatrixAccountVerificationState.verified,
      _ => MatrixAccountVerificationState.verificationRequired,
    };
  }

  MatrixDeviceVerificationState _deviceVerificationState(
    sdk.DeviceKeys? device,
  ) {
    if (device == null) {
      return MatrixDeviceVerificationState.unavailable;
    }
    if (device.blocked) {
      return MatrixDeviceVerificationState.blocked;
    }
    if (device.verified) {
      return MatrixDeviceVerificationState.verified;
    }
    return MatrixDeviceVerificationState.unverified;
  }

  MatrixKeyBackupState _keyBackupState({
    required bool keyBackupReady,
    required bool keyBackupCached,
  }) {
    if (!keyBackupReady) {
      return MatrixKeyBackupState.missing;
    }
    return keyBackupCached
        ? MatrixKeyBackupState.ready
        : MatrixKeyBackupState.recoveryRequired;
  }

  MatrixRoomEncryptionReadiness _roomEncryptionReadiness(
    sdk.Client client, {
    required bool secretStorageReady,
    required bool keyBackupReady,
    required bool cryptoIdentityInitialized,
    required bool cryptoIdentityConnected,
  }) {
    final hasEncryptedConversations = client.rooms.any(
      (room) => room.encrypted,
    );
    if (!hasEncryptedConversations) {
      return MatrixRoomEncryptionReadiness.noEncryptedRooms;
    }
    if (!secretStorageReady ||
        !keyBackupReady ||
        !cryptoIdentityInitialized ||
        !cryptoIdentityConnected) {
      return MatrixRoomEncryptionReadiness.encryptedRoomsNeedAttention;
    }
    return MatrixRoomEncryptionReadiness.ready;
  }
}

final matrixSecurityServiceProvider = Provider<MatrixSecurityService>((ref) {
  return SdkMatrixSecurityService(
    factory: ref.watch(matrixClientFactoryProvider),
  );
});
