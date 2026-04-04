import 'package:weave/features/chat/data/services/matrix_security_service.dart';
import 'package:weave/features/chat/data/services/matrix_service_types.dart';
import 'package:weave/features/chat/data/services/matrix_verification_service.dart';
import 'package:weave/features/chat/domain/entities/chat_failure.dart';
import 'package:weave/features/chat/domain/entities/chat_security_state.dart';
import 'package:weave/features/chat/domain/repositories/chat_security_repository.dart';
import 'package:weave/features/server_config/domain/repositories/'
    'server_configuration_repository.dart';

class MatrixChatSecurityRepository implements ChatSecurityRepository {
  const MatrixChatSecurityRepository({
    required MatrixSecurityService securityService,
    required MatrixVerificationService verificationService,
    required ServerConfigurationRepository serverConfigurationRepository,
  }) : _securityService = securityService,
       _verificationService = verificationService,
       _serverConfigurationRepository = serverConfigurationRepository;

  final MatrixSecurityService _securityService;
  final MatrixVerificationService _verificationService;
  final ServerConfigurationRepository _serverConfigurationRepository;

  @override
  Stream<ChatVerificationSession> watchVerificationUpdates() {
    return _verificationService.verificationUpdates.map(
      _mapVerificationSnapshot,
    );
  }

  @override
  Future<ChatSecurityState> loadSecurityState({bool refresh = false}) async {
    final homeserver = await _loadHomeserver();
    final snapshot = await _securityService.loadSecurityState(
      homeserver: homeserver,
      refresh: refresh,
    );
    return _mapSecurityState(snapshot);
  }

  @override
  Future<String> bootstrapSecurity({String? passphrase}) async {
    final homeserver = await _loadHomeserver();
    return _securityService.bootstrapSecurity(
      homeserver: homeserver,
      passphrase: passphrase,
    );
  }

  @override
  Future<void> restoreSecurity({
    required String recoveryKeyOrPassphrase,
  }) async {
    final homeserver = await _loadHomeserver();
    await _securityService.restoreSecurity(
      homeserver: homeserver,
      recoveryKeyOrPassphrase: recoveryKeyOrPassphrase,
    );
  }

  @override
  Future<void> startVerification() async {
    final homeserver = await _loadHomeserver();
    await _verificationService.startVerification(homeserver: homeserver);
  }

  @override
  Future<void> acceptVerification() async {
    final homeserver = await _loadHomeserver();
    await _verificationService.acceptVerification(homeserver: homeserver);
  }

  @override
  Future<void> startSasVerification() async {
    final homeserver = await _loadHomeserver();
    await _verificationService.startSasVerification(homeserver: homeserver);
  }

  @override
  Future<void> unlockVerification({
    required String recoveryKeyOrPassphrase,
  }) async {
    final homeserver = await _loadHomeserver();
    await _verificationService.unlockVerification(
      homeserver: homeserver,
      recoveryKeyOrPassphrase: recoveryKeyOrPassphrase,
    );
  }

  @override
  Future<void> confirmSas({required bool matches}) async {
    final homeserver = await _loadHomeserver();
    await _verificationService.confirmSas(
      homeserver: homeserver,
      matches: matches,
    );
  }

  @override
  Future<void> cancelVerification() async {
    final homeserver = await _loadHomeserver();
    await _verificationService.cancelVerification(homeserver: homeserver);
  }

  @override
  Future<void> dismissVerificationResult() async {
    final homeserver = await _loadHomeserver();
    await _verificationService.dismissVerificationResult(
      homeserver: homeserver,
    );
  }

  Future<Uri> _loadHomeserver() async {
    final configuration = await _serverConfigurationRepository
        .loadConfiguration();
    if (configuration == null) {
      throw const ChatFailure.configuration(
        'Finish setup before managing Matrix security.',
      );
    }

    return configuration.serviceEndpoints.matrixHomeserverUrl;
  }

  ChatSecurityState _mapSecurityState(MatrixSecuritySnapshot snapshot) {
    return ChatSecurityState(
      isMatrixSignedIn: snapshot.isMatrixSignedIn,
      bootstrapState: switch (snapshot.bootstrapState) {
        MatrixSecurityBootstrapState.signedOut =>
          ChatSecurityBootstrapState.signedOut,
        MatrixSecurityBootstrapState.notInitialized =>
          ChatSecurityBootstrapState.notInitialized,
        MatrixSecurityBootstrapState.partiallyInitialized =>
          ChatSecurityBootstrapState.partiallyInitialized,
        MatrixSecurityBootstrapState.recoveryRequired =>
          ChatSecurityBootstrapState.recoveryRequired,
        MatrixSecurityBootstrapState.ready => ChatSecurityBootstrapState.ready,
        MatrixSecurityBootstrapState.unavailable =>
          ChatSecurityBootstrapState.unavailable,
      },
      accountVerificationState: switch (snapshot.accountVerificationState) {
        MatrixAccountVerificationState.verified =>
          ChatAccountVerificationState.verified,
        MatrixAccountVerificationState.verificationRequired =>
          ChatAccountVerificationState.verificationRequired,
        MatrixAccountVerificationState.unavailable =>
          ChatAccountVerificationState.unavailable,
      },
      deviceVerificationState: switch (snapshot.deviceVerificationState) {
        MatrixDeviceVerificationState.verified =>
          ChatDeviceVerificationState.verified,
        MatrixDeviceVerificationState.unverified =>
          ChatDeviceVerificationState.unverified,
        MatrixDeviceVerificationState.blocked =>
          ChatDeviceVerificationState.blocked,
        MatrixDeviceVerificationState.unavailable =>
          ChatDeviceVerificationState.unavailable,
      },
      keyBackupState: switch (snapshot.keyBackupState) {
        MatrixKeyBackupState.unavailable => ChatKeyBackupState.unavailable,
        MatrixKeyBackupState.missing => ChatKeyBackupState.missing,
        MatrixKeyBackupState.recoveryRequired =>
          ChatKeyBackupState.recoveryRequired,
        MatrixKeyBackupState.ready => ChatKeyBackupState.ready,
      },
      roomEncryptionReadiness: switch (snapshot.roomEncryptionReadiness) {
        MatrixRoomEncryptionReadiness.unavailable =>
          ChatRoomEncryptionReadiness.unavailable,
        MatrixRoomEncryptionReadiness.noEncryptedRooms =>
          ChatRoomEncryptionReadiness.noEncryptedRooms,
        MatrixRoomEncryptionReadiness.encryptedRoomsNeedAttention =>
          ChatRoomEncryptionReadiness.encryptedRoomsNeedAttention,
        MatrixRoomEncryptionReadiness.ready =>
          ChatRoomEncryptionReadiness.ready,
      },
      secretStorageReady: snapshot.secretStorageReady,
      crossSigningReady: snapshot.crossSigningReady,
      hasEncryptedConversations: snapshot.hasEncryptedConversations,
      verificationSession: _mapVerificationSnapshot(snapshot.verification),
    );
  }

  ChatVerificationSession _mapVerificationSnapshot(
    MatrixVerificationSnapshot snapshot,
  ) {
    return ChatVerificationSession(
      phase: switch (snapshot.phase) {
        MatrixVerificationPhase.none => ChatVerificationPhase.none,
        MatrixVerificationPhase.incomingRequest =>
          ChatVerificationPhase.incomingRequest,
        MatrixVerificationPhase.chooseMethod =>
          ChatVerificationPhase.chooseMethod,
        MatrixVerificationPhase.waitingForOtherDevice =>
          ChatVerificationPhase.waitingForOtherDevice,
        MatrixVerificationPhase.needsRecoveryKey =>
          ChatVerificationPhase.needsRecoveryKey,
        MatrixVerificationPhase.compareSas => ChatVerificationPhase.compareSas,
        MatrixVerificationPhase.done => ChatVerificationPhase.done,
        MatrixVerificationPhase.cancelled => ChatVerificationPhase.cancelled,
        MatrixVerificationPhase.failed => ChatVerificationPhase.failed,
      },
      message: snapshot.message,
      sasNumbers: snapshot.sasNumbers,
      sasEmojis: snapshot.sasEmojis
          .map(
            (emoji) =>
                ChatVerificationEmoji(symbol: emoji.symbol, label: emoji.label),
          )
          .toList(growable: false),
    );
  }
}
