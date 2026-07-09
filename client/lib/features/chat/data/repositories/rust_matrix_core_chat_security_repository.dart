import 'package:weave/features/chat/domain/entities/chat_failure.dart';
import 'package:weave/features/chat/domain/entities/chat_security_state.dart';
import 'package:weave/features/chat/domain/repositories/chat_security_repository.dart';
import 'package:weave/features/server_config/domain/repositories/server_configuration_repository.dart';
import 'package:weave/integrations/rust_matrix_core/data/services/rust_matrix_core_bridge.dart';

class RustMatrixCoreChatSecurityRepository implements ChatSecurityRepository {
  const RustMatrixCoreChatSecurityRepository({
    required ServerConfigurationRepository serverConfigurationRepository,
    RustMatrixCoreBridge rustMatrixCoreBridge = const RustMatrixCoreBridge(),
  }) : _serverConfigurationRepository = serverConfigurationRepository,
       _rustMatrixCoreBridge = rustMatrixCoreBridge;

  final ServerConfigurationRepository _serverConfigurationRepository;
  final RustMatrixCoreBridge _rustMatrixCoreBridge;

  static const _bridgePendingMessage =
      'Matrix E2EE management is blocked until the Rust Matrix core Flutter bridge implements device verification and recovery.';

  @override
  Stream<ChatVerificationSession> watchVerificationUpdates() {
    return const Stream<ChatVerificationSession>.empty();
  }

  @override
  Future<ChatSecurityState> loadSecurityState({bool refresh = false}) async {
    final configuration = await _serverConfigurationRepository
        .loadConfiguration();
    if (configuration == null) {
      throw const ChatFailure.configuration(
        'Finish setup before managing Matrix security.',
      );
    }
    final descriptor = await _rustMatrixCoreBridge.descriptor(
      serverName: configuration.serviceEndpoints.matrixHomeserverUrl.host,
    );
    if (!descriptor.isWeaveFacade) {
      throw const ChatFailure.configuration(
        'The Rust Matrix core bridge is not configured for the Weave facade.',
      );
    }
    return const ChatSecurityState(
      isMatrixSignedIn: false,
      bootstrapState: ChatSecurityBootstrapState.unavailable,
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
  Future<String> bootstrapSecurity({String? passphrase}) async {
    throw const ChatFailure.unsupportedConfiguration(_bridgePendingMessage);
  }

  @override
  Future<void> restoreSecurity({
    required String recoveryKeyOrPassphrase,
  }) async {
    throw const ChatFailure.unsupportedConfiguration(_bridgePendingMessage);
  }

  @override
  Future<void> startVerification() async {
    throw const ChatFailure.unsupportedConfiguration(_bridgePendingMessage);
  }

  @override
  Future<void> acceptVerification() async {
    throw const ChatFailure.unsupportedConfiguration(_bridgePendingMessage);
  }

  @override
  Future<void> startSasVerification() async {
    throw const ChatFailure.unsupportedConfiguration(_bridgePendingMessage);
  }

  @override
  Future<void> unlockVerification({
    required String recoveryKeyOrPassphrase,
  }) async {
    throw const ChatFailure.unsupportedConfiguration(_bridgePendingMessage);
  }

  @override
  Future<void> confirmSas({required bool matches}) async {
    throw const ChatFailure.unsupportedConfiguration(_bridgePendingMessage);
  }

  @override
  Future<void> cancelVerification() async {
    throw const ChatFailure.unsupportedConfiguration(_bridgePendingMessage);
  }

  @override
  Future<void> dismissVerificationResult() async {
    throw const ChatFailure.unsupportedConfiguration(_bridgePendingMessage);
  }
}
