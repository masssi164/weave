import 'package:weave/features/chat/domain/entities/chat_security_state.dart';
import 'package:weave/features/chat/domain/repositories/chat_security_repository.dart';

class FakeChatSecurityRepository implements ChatSecurityRepository {
  FakeChatSecurityRepository({
    this.loadSecurityStateHandler,
    this.bootstrapSecurityHandler,
    this.restoreSecurityHandler,
    this.startVerificationHandler,
    this.acceptVerificationHandler,
    this.startSasVerificationHandler,
    this.confirmSasHandler,
    this.cancelVerificationHandler,
    this.dismissVerificationResultHandler,
  });

  Future<ChatSecurityState> Function({bool refresh})? loadSecurityStateHandler;
  Future<String> Function({String? passphrase})? bootstrapSecurityHandler;
  Future<void> Function({required String recoveryKeyOrPassphrase})?
  restoreSecurityHandler;
  Future<void> Function()? startVerificationHandler;
  Future<void> Function()? acceptVerificationHandler;
  Future<void> Function()? startSasVerificationHandler;
  Future<void> Function({required bool matches})? confirmSasHandler;
  Future<void> Function()? cancelVerificationHandler;
  Future<void> Function()? dismissVerificationResultHandler;

  @override
  Future<void> acceptVerification() async {
    await acceptVerificationHandler?.call();
  }

  @override
  Future<String> bootstrapSecurity({String? passphrase}) async {
    return bootstrapSecurityHandler?.call(passphrase: passphrase) ?? 'recovery';
  }

  @override
  Future<void> cancelVerification() async {
    await cancelVerificationHandler?.call();
  }

  @override
  Future<void> confirmSas({required bool matches}) async {
    await confirmSasHandler?.call(matches: matches);
  }

  @override
  Future<void> dismissVerificationResult() async {
    await dismissVerificationResultHandler?.call();
  }

  @override
  Future<ChatSecurityState> loadSecurityState({bool refresh = false}) async {
    return loadSecurityStateHandler?.call(refresh: refresh) ??
        const ChatSecurityState(
          isMatrixSignedIn: false,
          bootstrapState: ChatSecurityBootstrapState.signedOut,
          accountVerificationState: ChatAccountVerificationState.unavailable,
          deviceVerificationState: ChatDeviceVerificationState.unavailable,
          keyBackupState: ChatKeyBackupState.unavailable,
          roomEncryptionReadiness: ChatRoomEncryptionReadiness.unavailable,
          secretStorageReady: false,
          crossSigningReady: false,
          hasEncryptedConversations: false,
          verificationSession: ChatVerificationSession.none(),
        );
  }

  @override
  Future<void> restoreSecurity({required String recoveryKeyOrPassphrase}) async {
    await restoreSecurityHandler?.call(
      recoveryKeyOrPassphrase: recoveryKeyOrPassphrase,
    );
  }

  @override
  Future<void> startSasVerification() async {
    await startSasVerificationHandler?.call();
  }

  @override
  Future<void> startVerification() async {
    await startVerificationHandler?.call();
  }
}
