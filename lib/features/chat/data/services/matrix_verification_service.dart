import 'dart:async';

import 'package:matrix/encryption.dart' as crypto;
import 'package:matrix/matrix.dart' as sdk;
import 'package:riverpod/riverpod.dart';
import 'package:weave/features/chat/data/services/matrix_client_factory.dart';
import 'package:weave/features/chat/data/services/matrix_client_factory_io.dart'
    if (dart.library.js_interop)
    'package:weave/features/chat/data/services/matrix_client_factory_web.dart';
import 'package:weave/features/chat/data/services/matrix_service_types.dart';
import 'package:weave/features/chat/domain/entities/chat_failure.dart';

/// Manages the device-verification flow and exposes incoming verification
/// requests as a stream.
///
/// The service binds to [sdk.Client.onKeyVerificationRequest] as soon as the
/// SDK client is created (via [MatrixClientFactory.clientCreated]) and resets
/// its in-memory state whenever the session is cleared (via
/// [MatrixClientFactory.sessionCleared]).
abstract interface class MatrixVerificationService {
  /// Emits a snapshot of the current (or just-completed) verification session
  /// whenever the state changes. Always returns a non-null stream – on
  /// unsupported platforms it simply never emits.
  Stream<MatrixVerificationSnapshot> get verificationUpdates;

  Future<void> startVerification({required Uri homeserver});

  Future<void> acceptVerification({required Uri homeserver});

  Future<void> startSasVerification({required Uri homeserver});

  Future<void> unlockVerification({
    required Uri homeserver,
    required String recoveryKeyOrPassphrase,
  });

  Future<void> confirmSas({required Uri homeserver, required bool matches});

  Future<void> cancelVerification({required Uri homeserver});

  Future<void> dismissVerificationResult({required Uri homeserver});

  Future<void> dispose();
}

class SdkMatrixVerificationService implements MatrixVerificationService {
  SdkMatrixVerificationService({required MatrixClientFactory factory})
    : _factory = factory {
    // Bind eagerly to any client that already exists.
    final existing = factory.currentClient;
    if (existing != null) {
      _bindVerificationListener(existing);
    }

    // Also bind whenever a new client is created in the future.
    _clientCreatedSubscription = factory.clientCreated.listen(
      _bindVerificationListener,
    );

    // Reset active verification whenever the session is cleared.
    _sessionClearedSubscription = factory.sessionCleared.listen(
      (_) => _clearActiveVerification(),
    );
  }

  final MatrixClientFactory _factory;

  StreamSubscription<sdk.Client>? _clientCreatedSubscription;
  StreamSubscription<void>? _sessionClearedSubscription;
  StreamSubscription<crypto.KeyVerification>? _verificationSubscription;

  final StreamController<MatrixVerificationSnapshot>
  _verificationUpdatesController =
      StreamController<MatrixVerificationSnapshot>.broadcast();

  crypto.KeyVerification? _activeVerification;

  @override
  Stream<MatrixVerificationSnapshot> get verificationUpdates =>
      _verificationUpdatesController.stream;

  @override
  Future<void> startVerification({required Uri homeserver}) async {
    final client = await _requireLoggedInClient(homeserver);

    try {
      await client.oneShotSync();
      final userId = client.userID;
      final deviceId = client.deviceID;
      if (userId == null || deviceId == null) {
        throw const ChatFailure.protocol(
          'Matrix did not expose the current device identity yet.',
        );
      }
      final ownKeys = client.userDeviceKeys[userId];
      if (ownKeys == null) {
        throw const ChatFailure.protocol(
          'Matrix has not finished loading your device keys yet.',
        );
      }
      final otherDevices = ownKeys.deviceKeys.values
          .where((device) => device.deviceId != deviceId)
          .toList(growable: false);
      if (otherDevices.isEmpty) {
        throw const ChatFailure.protocol(
          'Sign in on another Matrix device before starting verification here.',
        );
      }

      _setActiveVerification(await ownKeys.startVerification());
    } on ChatFailure {
      rethrow;
    } catch (error) {
      throw mapMatrixServiceError(
        error,
        fallback: 'Unable to start device verification right now.',
      );
    }
  }

  @override
  Future<void> acceptVerification({required Uri homeserver}) async {
    final client = await _requireLoggedInClient(homeserver);
    final verification = _requireVerification();

    try {
      await client.oneShotSync();
      await verification.acceptVerification();
      _emitVerificationUpdate();
    } catch (error) {
      throw mapMatrixServiceError(
        error,
        fallback: 'Unable to accept the verification request right now.',
      );
    }
  }

  @override
  Future<void> startSasVerification({required Uri homeserver}) async {
    final client = await _requireLoggedInClient(homeserver);
    final verification = _requireVerification();

    try {
      await client.oneShotSync();
      if (!verification.possibleMethods.contains(sdk.EventTypes.Sas)) {
        throw const ChatFailure.protocol(
          'This verification request does not support comparing security numbers.',
        );
      }
      await verification.continueVerification(sdk.EventTypes.Sas);
      _emitVerificationUpdate();
    } catch (error) {
      throw mapMatrixServiceError(
        error,
        fallback: 'Unable to continue verification with security numbers.',
      );
    }
  }

  @override
  Future<void> unlockVerification({
    required Uri homeserver,
    required String recoveryKeyOrPassphrase,
  }) async {
    final client = await _requireLoggedInClient(homeserver);
    final verification = _requireVerification();

    try {
      await client.oneShotSync();
      await verification.openSSSS(keyOrPassphrase: recoveryKeyOrPassphrase);
      _emitVerificationUpdate();
    } catch (error) {
      throw mapMatrixServiceError(
        error,
        fallback: 'Unable to continue verification with that recovery key.',
      );
    }
  }

  @override
  Future<void> confirmSas({
    required Uri homeserver,
    required bool matches,
  }) async {
    final client = await _requireLoggedInClient(homeserver);
    final verification = _requireVerification();

    try {
      await client.oneShotSync();
      if (matches) {
        await verification.acceptSas();
      } else {
        await verification.rejectSas();
      }
      _emitVerificationUpdate();
    } catch (error) {
      throw mapMatrixServiceError(
        error,
        fallback: 'Unable to finish comparing the security numbers.',
      );
    }
  }

  @override
  Future<void> cancelVerification({required Uri homeserver}) async {
    final client = await _requireLoggedInClient(homeserver);
    final verification = _requireVerification();

    try {
      await client.oneShotSync();
      await verification.cancel('m.user');
      _emitVerificationUpdate();
    } catch (error) {
      throw mapMatrixServiceError(
        error,
        fallback: 'Unable to cancel the verification request right now.',
      );
    }
  }

  @override
  Future<void> dismissVerificationResult({required Uri homeserver}) async {
    await _requireLoggedInClient(homeserver);
    if (_activeVerification == null) {
      return;
    }
    _clearActiveVerification();
  }

  @override
  Future<void> dispose() async {
    await _clientCreatedSubscription?.cancel();
    _clientCreatedSubscription = null;
    await _sessionClearedSubscription?.cancel();
    _sessionClearedSubscription = null;
    await _verificationSubscription?.cancel();
    _verificationSubscription = null;
    _clearActiveVerification();
    if (!_verificationUpdatesController.isClosed) {
      await _verificationUpdatesController.close();
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

  void _bindVerificationListener(sdk.Client client) {
    _verificationSubscription ??= client.onKeyVerificationRequest.stream
        .listen(_setActiveVerification);
  }

  void _setActiveVerification(crypto.KeyVerification request) {
    if (identical(_activeVerification, request)) {
      _emitVerificationUpdate();
      return;
    }

    _detachVerification(_activeVerification);
    _activeVerification = request;
    request.onUpdate = _emitVerificationUpdate;
    _emitVerificationUpdate();
  }

  void _clearActiveVerification() {
    _detachVerification(_activeVerification, dispose: true);
    _activeVerification = null;
    _emitVerificationUpdate();
  }

  void _detachVerification(
    crypto.KeyVerification? verification, {
    bool dispose = true,
  }) {
    if (verification == null) return;
    verification.onUpdate = null;
    if (dispose) {
      verification.dispose();
    }
  }

  void _emitVerificationUpdate() {
    if (_verificationUpdatesController.isClosed) return;
    _verificationUpdatesController.add(
      _verificationSnapshot(_activeVerification),
    );
  }

  MatrixVerificationSnapshot _verificationSnapshot(
    crypto.KeyVerification? verification,
  ) {
    if (verification == null) {
      return const MatrixVerificationSnapshot.none();
    }

    if (verification.canceled) {
      return MatrixVerificationSnapshot(
        phase: MatrixVerificationPhase.cancelled,
        message:
            verification.canceledReason ??
            'Verification was cancelled before it finished.',
      );
    }

    return switch (verification.state) {
      crypto.KeyVerificationState.askAccept => const MatrixVerificationSnapshot(
        phase: MatrixVerificationPhase.incomingRequest,
        message: 'Another device wants to verify this session.',
      ),
      crypto.KeyVerificationState.askChoice => const MatrixVerificationSnapshot(
        phase: MatrixVerificationPhase.chooseMethod,
        message: 'Choose a verification method to compare both devices.',
      ),
      crypto.KeyVerificationState.waitingAccept ||
      crypto.KeyVerificationState.waitingSas =>
        const MatrixVerificationSnapshot(
          phase: MatrixVerificationPhase.waitingForOtherDevice,
          message: 'Waiting for the other device to continue verification.',
        ),
      crypto.KeyVerificationState.askSSSS => const MatrixVerificationSnapshot(
        phase: MatrixVerificationPhase.needsRecoveryKey,
        message:
            'Enter your Matrix recovery key or passphrase to continue verification.',
      ),
      crypto.KeyVerificationState.askSas => MatrixVerificationSnapshot(
        phase: MatrixVerificationPhase.compareSas,
        message: 'Compare the security emoji or numbers on both devices.',
        sasNumbers: verification.sasNumbers,
        sasEmojis: verification.sasEmojis
            .map(
              (emoji) => MatrixVerificationEmoji(
                symbol: emoji.emoji,
                label: emoji.name,
              ),
            )
            .toList(growable: false),
      ),
      crypto.KeyVerificationState.done => const MatrixVerificationSnapshot(
        phase: MatrixVerificationPhase.done,
        message: 'This device is now verified.',
      ),
      crypto.KeyVerificationState.error => const MatrixVerificationSnapshot(
        phase: MatrixVerificationPhase.failed,
        message: 'Verification could not be completed.',
      ),
      _ => const MatrixVerificationSnapshot(
        phase: MatrixVerificationPhase.waitingForOtherDevice,
        message: 'Waiting for the other device to continue verification.',
      ),
    };
  }

  crypto.KeyVerification _requireVerification() {
    final verification = _activeVerification;
    if (verification == null) {
      throw const ChatFailure.protocol(
        'There is no active Matrix verification request right now.',
      );
    }
    return verification;
  }
}

final matrixVerificationServiceProvider =
    Provider<MatrixVerificationService>((ref) {
      final service = SdkMatrixVerificationService(
        factory: ref.watch(matrixClientFactoryProvider),
      );
      ref.onDispose(service.dispose);
      return service;
    });
