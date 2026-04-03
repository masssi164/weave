import 'package:weave/features/chat/data/services/matrix_client.dart';
import 'package:weave/features/chat/domain/entities/chat_failure.dart';
import 'package:weave/features/chat/domain/entities/chat_security_state.dart';
import 'package:weave/features/chat/domain/repositories/chat_security_repository.dart';
import 'package:weave/features/server_config/domain/repositories/'
    'server_configuration_repository.dart';

class MatrixChatSecurityRepository implements ChatSecurityRepository {
  const MatrixChatSecurityRepository({
    required MatrixClient client,
    required ServerConfigurationRepository serverConfigurationRepository,
  }) : _client = client,
       _serverConfigurationRepository = serverConfigurationRepository;

  final MatrixClient _client;
  final ServerConfigurationRepository _serverConfigurationRepository;

  @override
  Stream<ChatVerificationSession> watchVerificationUpdates() {
    return _client.verificationUpdates.map(_mapVerificationSnapshot);
  }

  @override
  Future<ChatSecurityState> loadSecurityState({bool refresh = false}) async {
    final homeserver = await _loadHomeserver();
    final snapshot = await _client.loadSecurityState(
      homeserver: homeserver,
      refresh: refresh,
    );
    return _mapSecurityState(snapshot);
  }

  @override
  Future<String> bootstrapSecurity({String? passphrase}) async {
    final homeserver = await _loadHomeserver();
    return _client.bootstrapSecurity(
      homeserver: homeserver,
      passphrase: passphrase,
    );
  }

  @override
  Future<void> restoreSecurity({required String recoveryKeyOrPassphrase}) async {
    final homeserver = await _loadHomeserver();
    await _client.restoreSecurity(
      homeserver: homeserver,
      recoveryKeyOrPassphrase: recoveryKeyOrPassphrase,
    );
  }

  @override
  Future<void> startVerification() async {
    final homeserver = await _loadHomeserver();
    await _client.startVerification(homeserver: homeserver);
  }

  @override
  Future<void> acceptVerification() async {
    final homeserver = await _loadHomeserver();
    await _client.acceptVerification(homeserver: homeserver);
  }

  @override
  Future<void> startSasVerification() async {
    final homeserver = await _loadHomeserver();
    await _client.startSasVerification(homeserver: homeserver);
  }

  @override
  Future<void> confirmSas({required bool matches}) async {
    final homeserver = await _loadHomeserver();
    await _client.confirmSas(homeserver: homeserver, matches: matches);
  }

  @override
  Future<void> cancelVerification() async {
    final homeserver = await _loadHomeserver();
    await _client.cancelVerification(homeserver: homeserver);
  }

  @override
  Future<void> dismissVerificationResult() async {
    final homeserver = await _loadHomeserver();
    await _client.dismissVerificationResult(homeserver: homeserver);
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
        MatrixVerificationPhase.compareSas => ChatVerificationPhase.compareSas,
        MatrixVerificationPhase.done => ChatVerificationPhase.done,
        MatrixVerificationPhase.cancelled => ChatVerificationPhase.cancelled,
        MatrixVerificationPhase.failed => ChatVerificationPhase.failed,
      },
      message: snapshot.message,
      sasNumbers: snapshot.sasNumbers,
      sasEmojis: snapshot.sasEmojis
          .map(
            (emoji) => ChatVerificationEmoji(
              symbol: emoji.symbol,
              label: emoji.label,
            ),
          )
          .toList(growable: false),
    );
  }
}
