import 'package:weave/features/chat/data/services/matrix_auth_browser.dart';
import 'package:weave/features/chat/data/services/matrix_client_interface.dart';
import 'package:weave/features/chat/domain/entities/chat_failure.dart';

class _WebMatrixClientStub implements MatrixClient {
  const _WebMatrixClientStub();

  @override
  Stream<MatrixVerificationSnapshot> get verificationUpdates =>
      const Stream<MatrixVerificationSnapshot>.empty();

  @override
  Future<List<MatrixRoomSnapshot>> loadConversations({
    required Uri homeserver,
  }) async {
    throw const ChatFailure.unsupportedPlatform(
      'Matrix chat is not supported on the web.',
    );
  }

  @override
  Future<void> connect({required Uri homeserver}) async {
    throw const ChatFailure.unsupportedPlatform(
      'Matrix chat is not supported on the web.',
    );
  }

  @override
  Future<MatrixSecuritySnapshot> loadSecurityState({
    required Uri homeserver,
    bool refresh = false,
  }) async {
    return const MatrixSecuritySnapshot(
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
  }

  @override
  Future<String> bootstrapSecurity({
    required Uri homeserver,
    String? passphrase,
  }) async {
    throw const ChatFailure.unsupportedPlatform(
      'Matrix chat is not supported on the web.',
    );
  }

  @override
  Future<void> restoreSecurity({
    required Uri homeserver,
    required String recoveryKeyOrPassphrase,
  }) async {
    throw const ChatFailure.unsupportedPlatform(
      'Matrix chat is not supported on the web.',
    );
  }

  @override
  Future<void> startVerification({required Uri homeserver}) async {
    throw const ChatFailure.unsupportedPlatform(
      'Matrix chat is not supported on the web.',
    );
  }

  @override
  Future<void> acceptVerification({required Uri homeserver}) async {
    throw const ChatFailure.unsupportedPlatform(
      'Matrix chat is not supported on the web.',
    );
  }

  @override
  Future<void> startSasVerification({required Uri homeserver}) async {
    throw const ChatFailure.unsupportedPlatform(
      'Matrix chat is not supported on the web.',
    );
  }

  @override
  Future<void> confirmSas({
    required Uri homeserver,
    required bool matches,
  }) async {
    throw const ChatFailure.unsupportedPlatform(
      'Matrix chat is not supported on the web.',
    );
  }

  @override
  Future<void> cancelVerification({required Uri homeserver}) async {
    throw const ChatFailure.unsupportedPlatform(
      'Matrix chat is not supported on the web.',
    );
  }

  @override
  Future<void> dismissVerificationResult({required Uri homeserver}) async {}

  @override
  Future<void> signOut() async {}

  @override
  Future<void> clearSession() async {}
}

MatrixClient createMatrixClient({required MatrixAuthBrowser authBrowser}) {
  return const _WebMatrixClientStub();
}
