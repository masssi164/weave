import 'dart:async';
import 'dart:io';

import 'package:flutter/foundation.dart';
import 'package:flutter_vodozemac/flutter_vodozemac.dart' as vod;
import 'package:matrix/encryption/utils/crypto_setup_extension.dart';
import 'package:matrix/matrix.dart' as sdk;
import 'package:path_provider/path_provider.dart';
import 'package:sqflite/sqflite.dart' as sqflite;
import 'package:weave/features/chat/data/services/matrix_auth_browser.dart';
import 'package:weave/features/chat/data/services/matrix_client_interface.dart';
import 'package:weave/features/chat/domain/entities/chat_failure.dart';

typedef MatrixSdkClientFactory =
    sdk.Client Function({required sdk.DatabaseApi database});
typedef MatrixDatabaseOpener = Future<sqflite.Database> Function(String path);
typedef ApplicationSupportDirectoryProvider = Future<Directory> Function();

class SdkMatrixClient implements MatrixClient {
  SdkMatrixClient({
    required MatrixAuthBrowser authBrowser,
    ApplicationSupportDirectoryProvider? appSupportDirectoryProvider,
    MatrixDatabaseOpener? databaseOpener,
    MatrixSdkClientFactory? clientFactory,
  }) : _authBrowser = authBrowser,
       _appSupportDirectoryProvider =
           appSupportDirectoryProvider ?? getApplicationSupportDirectory,
       _databaseOpener = databaseOpener ?? sqflite.openDatabase,
       _clientFactory = clientFactory ?? _defaultClientFactory;

  final MatrixAuthBrowser _authBrowser;
  final ApplicationSupportDirectoryProvider _appSupportDirectoryProvider;
  final MatrixDatabaseOpener _databaseOpener;
  final MatrixSdkClientFactory _clientFactory;

  sdk.Client? _client;
  Future<sdk.Client>? _clientFuture;
  StreamSubscription<sdk.KeyVerification>? _verificationSubscription;
  final StreamController<MatrixVerificationSnapshot>
  _verificationUpdatesController =
      StreamController<MatrixVerificationSnapshot>.broadcast();
  sdk.KeyVerification? _activeVerification;

  static final Uri _redirectUri = Uri.parse(matrixOidcRedirectUri);
  static final Uri _clientUri = Uri.parse(matrixOidcClientUri);
  static Future<void>? _vodozemacInitFuture;

  static sdk.Client _defaultClientFactory({required sdk.DatabaseApi database}) {
    return sdk.Client(
      'weave_matrix_chat',
      database: database,
      verificationMethods: const <sdk.KeyVerificationMethod>{
        sdk.KeyVerificationMethod.emoji,
        sdk.KeyVerificationMethod.numbers,
      },
      nativeImplementations: sdk.NativeImplementationsIsolate(
        compute,
        vodozemacInit: _ensureVodozemacInitialized,
      ),
      supportedLoginTypes: {
        sdk.AuthenticationTypes.password,
        sdk.AuthenticationTypes.sso,
        sdk.AuthenticationTypes.oauth2,
      },
      onSoftLogout: (client) => client.refreshAccessToken(),
    );
  }

  static Future<void> _ensureVodozemacInitialized() {
    return _vodozemacInitFuture ??= vod.init();
  }

  @override
  Stream<MatrixVerificationSnapshot> get verificationUpdates =>
      _verificationUpdatesController.stream;

  @override
  Future<List<MatrixRoomSnapshot>> loadConversations({
    required Uri homeserver,
  }) async {
    final client = await _ensureClient();
    final normalizedHomeserver = _normalizeUri(homeserver);

    await _clearSessionIfHomeserverChanged(client, normalizedHomeserver);

    if (!client.isLogged()) {
      throw const ChatFailure.sessionRequired(
        'Connect Weave to your Matrix homeserver to load conversations.',
      );
    }

    try {
      await client.oneShotSync();
      return client.rooms.map(_mapRoom).toList(growable: false);
    } catch (error) {
      throw _mapError(
        error,
        fallback: 'Unable to load conversations from the Matrix homeserver.',
      );
    }
  }

  @override
  Future<void> connect({required Uri homeserver}) async {
    final client = await _ensureClient();
    final normalizedHomeserver = _normalizeUri(homeserver);

    await _clearSessionIfHomeserverChanged(client, normalizedHomeserver);

    if (client.isLogged()) {
      return;
    }

    final authMetadata = await _discoverAuthMetadata(
      client,
      normalizedHomeserver,
    );
    if (authMetadata == null) {
      throw ChatFailure.unsupportedConfiguration(
        'The configured Matrix homeserver at ${normalizedHomeserver.toString()} '
        'does not advertise Matrix OAuth 2.0 metadata. '
        'Weave currently requires Matrix Native OAuth 2.0 for chat.',
      );
    }

    try {
      final oidcClient = await client.registerOidcClient(
        redirectUris: [_redirectUri],
        applicationType: sdk.OidcApplicationType.native,
        clientInformation: sdk.OidcClientInformation(
          clientName: matrixOidcClientName,
          clientUri: _clientUri,
          logoUri: null,
          tosUri: null,
          policyUri: null,
        ),
      );
      final session = await client.initOidcLoginSession(
        oidcClientData: oidcClient,
        redirectUri: _redirectUri,
      );
      final callbackUri = await _authBrowser.authenticate(
        authorizationUri: session.authenticationUri,
        redirectUri: _redirectUri,
      );
      final callbackParameters = _extractCallbackParameters(callbackUri);
      final code = callbackParameters['code']?.trim();
      final state = callbackParameters['state']?.trim();

      if (code == null || code.isEmpty || state == null || state.isEmpty) {
        throw const ChatFailure.protocol(
          'The Matrix homeserver sign-in callback did not include the '
          'expected authorization response.',
        );
      }

      await client.oidcLogin(session: session, code: code, state: state);
    } on ChatFailure {
      rethrow;
    } catch (error) {
      throw _mapError(
        error,
        fallback: 'Unable to connect Weave to the Matrix homeserver.',
      );
    }
  }

  @override
  Future<MatrixSecuritySnapshot> loadSecurityState({
    required Uri homeserver,
    bool refresh = false,
  }) async {
    final client = await _ensureClient();
    final normalizedHomeserver = _normalizeUri(homeserver);

    await _clearSessionIfHomeserverChanged(client, normalizedHomeserver);

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
      throw _mapError(
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
    final client = await _loggedInClient(homeserver);

    try {
      final recoveryKey = await client.initCryptoIdentity(passphrase: passphrase);
      await client.oneShotSync();
      return recoveryKey;
    } catch (error) {
      throw _mapError(
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
    final client = await _loggedInClient(homeserver);

    try {
      await client.restoreCryptoIdentity(recoveryKeyOrPassphrase);
      await client.oneShotSync();
    } catch (error) {
      throw _mapError(
        error,
        fallback: 'Unable to reconnect encrypted chat with that recovery key.',
      );
    }
  }

  @override
  Future<void> startVerification({required Uri homeserver}) async {
    final client = await _loggedInClient(homeserver);

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
      throw _mapError(
        error,
        fallback: 'Unable to start device verification right now.',
      );
    }
  }

  @override
  Future<void> acceptVerification({required Uri homeserver}) async {
    final client = await _loggedInClient(homeserver);
    final verification = _requireVerification();

    try {
      await client.oneShotSync();
      await verification.acceptVerification();
      _emitVerificationUpdate();
    } catch (error) {
      throw _mapError(
        error,
        fallback: 'Unable to accept the verification request right now.',
      );
    }
  }

  @override
  Future<void> startSasVerification({required Uri homeserver}) async {
    final client = await _loggedInClient(homeserver);
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
      throw _mapError(
        error,
        fallback: 'Unable to continue verification with security numbers.',
      );
    }
  }

  @override
  Future<void> confirmSas({
    required Uri homeserver,
    required bool matches,
  }) async {
    final client = await _loggedInClient(homeserver);
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
      throw _mapError(
        error,
        fallback: 'Unable to finish comparing the security numbers.',
      );
    }
  }

  @override
  Future<void> cancelVerification({required Uri homeserver}) async {
    final client = await _loggedInClient(homeserver);
    final verification = _requireVerification();

    try {
      await client.oneShotSync();
      await verification.cancel('m.user');
      _emitVerificationUpdate();
    } catch (error) {
      throw _mapError(
        error,
        fallback: 'Unable to cancel the verification request right now.',
      );
    }
  }

  @override
  Future<void> dismissVerificationResult({required Uri homeserver}) async {
    await _loggedInClient(homeserver);
    if (_activeVerification == null) {
      return;
    }
    _clearActiveVerification();
  }

  @override
  Future<void> signOut() async {
    if (!_isInteractivePlatform) {
      return;
    }

    _disposeActiveVerification();
    final client = await _ensureClient();
    if (!client.isLogged()) {
      await clearSession();
      return;
    }

    try {
      await client.logout();
    } catch (error) {
      try {
        await client.clear();
      } catch (clearError) {
        throw _mapError(
          clearError,
          fallback: 'Unable to clear the saved Matrix session.',
        );
      }
    }
  }

  @override
  Future<void> clearSession() async {
    if (!_isInteractivePlatform) {
      return;
    }

    _disposeActiveVerification();
    final client = await _ensureClient();

    try {
      await client.clear();
    } catch (error) {
      throw _mapError(
        error,
        fallback: 'Unable to clear the saved Matrix session.',
      );
    }
  }

  Future<sdk.Client> _ensureClient() async {
    final existingClient = _client;
    if (existingClient != null) {
      return existingClient;
    }

    final pendingClient = _clientFuture;
    if (pendingClient != null) {
      return pendingClient;
    }

    final future = _createClient();
    _clientFuture = future;

    try {
      return await future;
    } catch (error) {
      _clientFuture = null;
      _client = null;
      rethrow;
    }
  }

  Future<sdk.Client> _createClient() async {
    if (!_isInteractivePlatform) {
      throw const ChatFailure.unsupportedPlatform(
        'Matrix chat is currently supported on Android, iOS, and macOS.',
      );
    }

    try {
      await _ensureVodozemacInitialized();
      final supportDirectory = await _appSupportDirectoryProvider();
      final databasePath = supportDirectory.uri
          .resolve('weave_matrix_chat.sqlite')
          .toFilePath();
      final rawDatabase = await _databaseOpener(databasePath);
      final database = await sdk.MatrixSdkDatabase.init(
        'weave_matrix_chat',
        database: rawDatabase,
        fileStorageLocation: supportDirectory.uri.resolve(
          'weave_matrix_media/',
        ),
      );
      final client = _clientFactory(database: database);

      await client.init();
      _bindVerificationListener(client);
      _client = client;
      return client;
    } catch (error) {
      throw _mapError(
        error,
        fallback: 'Unable to initialize the Matrix chat client.',
      );
    }
  }

  Future<sdk.Client> _loggedInClient(Uri homeserver) async {
    final client = await _ensureClient();
    final normalizedHomeserver = _normalizeUri(homeserver);

    await _clearSessionIfHomeserverChanged(client, normalizedHomeserver);
    if (!client.isLogged()) {
      throw const ChatFailure.sessionRequired(
        'Connect Weave to your Matrix homeserver before managing Matrix security.',
      );
    }

    return client;
  }

  void _bindVerificationListener(sdk.Client client) {
    _verificationSubscription ??= client.onKeyVerificationRequest.stream.listen((
      request,
    ) {
      _setActiveVerification(request);
    });
  }

  void _setActiveVerification(sdk.KeyVerification request) {
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
    sdk.KeyVerification? verification, {
    bool dispose = true,
  }) {
    if (verification == null) {
      return;
    }

    verification.onUpdate = null;
    if (dispose) {
      verification.dispose();
    }
  }

  void _emitVerificationUpdate() {
    final activeVerification = _activeVerification;
    if (_verificationUpdatesController.isClosed) {
      return;
    }

    _verificationUpdatesController.add(_verificationSnapshot(activeVerification));
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
    final secretStorageReady = encryption.ssss.defaultKeyId != null;
    final crossSigningReady = encryption.crossSigning.enabled;
    final keyBackupReady = encryption.keyManager.enabled;
    final crossSigningCached = crossSigningReady
        ? await encryption.crossSigning.isCached()
        : false;
    final keyBackupCached = keyBackupReady
        ? await encryption.keyManager.isCached()
        : false;

    return MatrixSecuritySnapshot(
      isMatrixSignedIn: true,
      bootstrapState: _bootstrapStateForClient(
        client,
        secretStorageReady: secretStorageReady,
        crossSigningReady: crossSigningReady,
        keyBackupReady: keyBackupReady,
        crossSigningCached: crossSigningCached,
        keyBackupCached: keyBackupCached,
      ),
      accountVerificationState: _accountVerificationStateForKeys(ownKeys),
      deviceVerificationState: _deviceVerificationStateForDevice(ownDevice),
      keyBackupState: _keyBackupStateForClient(
        keyBackupReady: keyBackupReady,
        keyBackupCached: keyBackupCached,
      ),
      roomEncryptionReadiness: _roomEncryptionReadinessForClient(
        client,
        secretStorageReady: secretStorageReady,
        crossSigningReady: crossSigningReady,
        keyBackupReady: keyBackupReady,
        crossSigningCached: crossSigningCached,
        keyBackupCached: keyBackupCached,
      ),
      secretStorageReady: secretStorageReady,
      crossSigningReady: crossSigningReady,
      hasEncryptedConversations: client.rooms.any((room) => room.encrypted),
      verification: _verificationSnapshot(_activeVerification),
    );
  }

  MatrixSecurityBootstrapState _bootstrapStateForClient(
    sdk.Client client, {
    required bool secretStorageReady,
    required bool crossSigningReady,
    required bool keyBackupReady,
    required bool crossSigningCached,
    required bool keyBackupCached,
  }) {
    if (!client.isLogged()) {
      return MatrixSecurityBootstrapState.signedOut;
    }
    if (!secretStorageReady && !crossSigningReady && !keyBackupReady) {
      return MatrixSecurityBootstrapState.notInitialized;
    }
    if (!secretStorageReady || !crossSigningReady || !keyBackupReady) {
      return MatrixSecurityBootstrapState.partiallyInitialized;
    }
    if (!crossSigningCached || !keyBackupCached) {
      return MatrixSecurityBootstrapState.recoveryRequired;
    }
    return MatrixSecurityBootstrapState.ready;
  }

  MatrixAccountVerificationState _accountVerificationStateForKeys(
    sdk.DeviceKeysList? ownKeys,
  ) {
    if (ownKeys == null) {
      return MatrixAccountVerificationState.unavailable;
    }
    return switch (ownKeys.verified) {
      sdk.UserVerifiedStatus.verified => MatrixAccountVerificationState.verified,
      sdk.UserVerifiedStatus.unknownDevice =>
        MatrixAccountVerificationState.verificationRequired,
      _ => MatrixAccountVerificationState.verificationRequired,
    };
  }

  MatrixDeviceVerificationState _deviceVerificationStateForDevice(
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

  MatrixKeyBackupState _keyBackupStateForClient({
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

  MatrixRoomEncryptionReadiness _roomEncryptionReadinessForClient(
    sdk.Client client, {
    required bool secretStorageReady,
    required bool crossSigningReady,
    required bool keyBackupReady,
    required bool crossSigningCached,
    required bool keyBackupCached,
  }) {
    final hasEncryptedConversations = client.rooms.any((room) => room.encrypted);
    if (!hasEncryptedConversations) {
      return MatrixRoomEncryptionReadiness.noEncryptedRooms;
    }
    if (!secretStorageReady ||
        !crossSigningReady ||
        !keyBackupReady ||
        !crossSigningCached ||
        !keyBackupCached) {
      return MatrixRoomEncryptionReadiness.encryptedRoomsNeedAttention;
    }
    return MatrixRoomEncryptionReadiness.ready;
  }

  MatrixVerificationSnapshot _verificationSnapshot(
    sdk.KeyVerification? verification,
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
      sdk.KeyVerificationState.askAccept => const MatrixVerificationSnapshot(
        phase: MatrixVerificationPhase.incomingRequest,
        message: 'Another device wants to verify this session.',
      ),
      sdk.KeyVerificationState.askChoice => const MatrixVerificationSnapshot(
        phase: MatrixVerificationPhase.chooseMethod,
        message: 'Choose a verification method to compare both devices.',
      ),
      sdk.KeyVerificationState.askSas => MatrixVerificationSnapshot(
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
      sdk.KeyVerificationState.done => const MatrixVerificationSnapshot(
        phase: MatrixVerificationPhase.done,
        message: 'This device is now verified.',
      ),
      sdk.KeyVerificationState.error => const MatrixVerificationSnapshot(
        phase: MatrixVerificationPhase.failed,
        message: 'Verification could not be completed.',
      ),
      _ => const MatrixVerificationSnapshot(
        phase: MatrixVerificationPhase.waitingForOtherDevice,
        message: 'Waiting for the other device to continue verification.',
      ),
    };
  }

  sdk.KeyVerification _requireVerification() {
    final verification = _activeVerification;
    if (verification == null) {
      throw const ChatFailure.protocol(
        'There is no active Matrix verification request right now.',
      );
    }
    return verification;
  }

  void _disposeActiveVerification() {
    _clearActiveVerification();
  }

  Future<void> _clearSessionIfHomeserverChanged(
    sdk.Client client,
    Uri homeserver,
  ) async {
    final activeHomeserver = client.homeserver;
    if (activeHomeserver == null || !client.isLogged()) {
      return;
    }

    if (_normalizeUri(activeHomeserver).toString() == homeserver.toString()) {
      return;
    }

    _disposeActiveVerification();
    await client.clear();
  }

  Future<sdk.GetAuthMetadataResponse?> _discoverAuthMetadata(
    sdk.Client client,
    Uri homeserver,
  ) async {
    try {
      final (_, _, _, authMetadata) = await client.checkHomeserver(
        homeserver,
        fetchAuthMetadata: true,
      );
      return authMetadata;
    } on sdk.BadServerLoginTypesException {
      client.homeserver = homeserver;
      try {
        return await client.getAuthMetadata();
      } on sdk.MatrixException catch (error) {
        if (error.error == sdk.MatrixError.M_UNRECOGNIZED) {
          return null;
        }
        throw _mapError(
          error,
          fallback:
              'Unable to determine whether the Matrix homeserver supports OAuth 2.0.',
        );
      }
    } on sdk.MatrixException catch (error) {
      if (error.error == sdk.MatrixError.M_UNRECOGNIZED) {
        return null;
      }
      throw _mapError(
        error,
        fallback:
            'Unable to determine whether the Matrix homeserver supports OAuth 2.0.',
      );
    } catch (error) {
      throw _mapError(
        error,
        fallback:
            'Unable to determine whether the Matrix homeserver supports OAuth 2.0.',
      );
    }
  }

  MatrixRoomSnapshot _mapRoom(sdk.Room room) {
    final lastEvent = room.lastEvent;
    final previewType = _previewTypeForRoom(room, lastEvent);
    final previewText = switch (previewType) {
      MatrixRoomPreviewType.text => lastEvent?.plaintextBody.trim(),
      _ => null,
    };

    return MatrixRoomSnapshot(
      id: room.id,
      title: room.getLocalizedDisplayname(),
      previewType: previewType,
      previewText: previewText,
      lastActivityAt: lastEvent?.originServerTs.toLocal(),
      unreadCount: room.notificationCount,
      isInvite: room.membership == sdk.Membership.invite,
      isDirectMessage: room.isDirectChat,
    );
  }

  MatrixRoomPreviewType _previewTypeForRoom(sdk.Room room, sdk.Event? event) {
    if (event == null) {
      return MatrixRoomPreviewType.none;
    }

    if (room.encrypted || event.type == sdk.EventTypes.Encrypted) {
      return MatrixRoomPreviewType.encrypted;
    }

    final preview = event.plaintextBody.trim();
    if (preview.isEmpty ||
        preview.startsWith('Unknown message format of type')) {
      return MatrixRoomPreviewType.unsupported;
    }

    return MatrixRoomPreviewType.text;
  }

  Map<String, String> _extractCallbackParameters(Uri callbackUri) {
    if (callbackUri.queryParameters.isNotEmpty) {
      return callbackUri.queryParameters;
    }

    if (callbackUri.fragment.isEmpty) {
      return const <String, String>{};
    }

    return Uri.splitQueryString(callbackUri.fragment);
  }

  Uri _normalizeUri(Uri uri) {
    final normalized = uri.toString().trim();
    if (!normalized.endsWith('/')) {
      return uri;
    }

    return Uri.parse(normalized.substring(0, normalized.length - 1));
  }

  ChatFailure _mapError(Object error, {required String fallback}) {
    if (error is ChatFailure) {
      return error;
    }

    if (error is sdk.MatrixException) {
      final message = error.errorMessage.trim();
      return ChatFailure.protocol(
        message.isEmpty ? fallback : message,
        cause: error,
      );
    }

    if (error is IOException || error is sqflite.DatabaseException) {
      return ChatFailure.storage(fallback, cause: error);
    }

    return ChatFailure.unknown(fallback, cause: error);
  }

  bool get _isInteractivePlatform {
    if (kIsWeb) {
      return false;
    }

    return defaultTargetPlatform == TargetPlatform.android ||
        defaultTargetPlatform == TargetPlatform.iOS ||
        defaultTargetPlatform == TargetPlatform.macOS;
  }
}

MatrixClient createMatrixClient({required MatrixAuthBrowser authBrowser}) {
  return SdkMatrixClient(authBrowser: authBrowser);
}
