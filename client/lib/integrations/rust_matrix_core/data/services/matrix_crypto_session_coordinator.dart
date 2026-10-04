import 'dart:convert';
import 'dart:io';
import 'dart:math';

import 'package:crypto/crypto.dart';
import 'package:path_provider/path_provider.dart';
import 'package:weave/core/persistence/secure_store.dart';
import 'package:weave/features/auth/domain/entities/auth_configuration.dart';
import 'package:weave/features/auth/domain/entities/auth_session.dart';
import 'package:weave/features/auth/domain/entities/auth_state.dart';
import 'package:weave/features/auth/domain/repositories/auth_session_repository.dart';
import 'package:weave/features/chat/data/repositories/matrix_device_identity_repository.dart';
import 'package:weave/features/chat/domain/entities/chat_failure.dart';
import 'package:weave/features/server_config/domain/repositories/server_configuration_repository.dart';

import 'matrix_oauth_browser.dart';
import 'matrix_session_access.dart';
import 'rust_matrix_core_bridge.dart';

const matrixCryptoStorePassphraseKeyPrefix =
    'matrix_crypto_store_passphrase_v1_';
const matrixOAuthBindingKeyPrefix = 'matrix_oauth_binding_v1_';
const matrixOAuthCurrentBindingKey = 'matrix_oauth_current_binding_v1';
const matrixOAuthProfileOwnerKeyPrefix = 'matrix_oauth_profile_owner_v1_';
const _matrixOAuthSessionFile = 'weave-matrix-oauth-session.v1';

typedef MatrixStoreRootLoader = Future<Directory> Function();

class MatrixCryptoSession {
  const MatrixCryptoSession({
    required this.profileKey,
    required this.userId,
    required this.deviceId,
  });

  final String profileKey;
  final String userId;
  final String deviceId;
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

class MatrixCryptoSessionCoordinator implements MatrixCryptoSessionPort {
  MatrixCryptoSessionCoordinator({
    required ServerConfigurationRepository serverConfigurationRepository,
    required AuthSessionRepository authSessionRepository,
    required MatrixDeviceIdentityRepository matrixDeviceIdentityRepository,
    required MatrixSessionAccessPort matrixSessionAccess,
    required SecureStore secureStore,
    RustMatrixCoreBridge rustMatrixCoreBridge = const RustMatrixCoreBridge(),
    MatrixOAuthBrowser? oauthBrowser,
    MatrixStoreRootLoader storeRootLoader = _defaultStoreRoot,
    Random? random,
  }) : _serverConfigurationRepository = serverConfigurationRepository,
       _authSessionRepository = authSessionRepository,
       _matrixDeviceIdentityRepository = matrixDeviceIdentityRepository,
       _matrixSessionAccess = matrixSessionAccess,
       _secureStore = secureStore,
       _rustMatrixCoreBridge = rustMatrixCoreBridge,
       _oauthBrowser = oauthBrowser ?? SystemMatrixOAuthBrowser(),
       _storeRootLoader = storeRootLoader,
       _random = random ?? Random.secure();

  final ServerConfigurationRepository _serverConfigurationRepository;
  final AuthSessionRepository _authSessionRepository;
  final MatrixDeviceIdentityRepository _matrixDeviceIdentityRepository;
  final MatrixSessionAccessPort _matrixSessionAccess;
  final SecureStore _secureStore;
  final RustMatrixCoreBridge _rustMatrixCoreBridge;
  final MatrixOAuthBrowser _oauthBrowser;
  final MatrixStoreRootLoader _storeRootLoader;
  final Random _random;

  Future<MatrixCryptoSession>? _opening;
  Future<void>? _disposing;
  String? _activeFingerprint;
  String? _activeBindingKey;
  MatrixCryptoSession? _activeSession;

  @override
  Future<MatrixCryptoSession> open({
    bool synchronize = true,
    bool allowInteractiveSignIn = true,
  }) async {
    final disposing = _disposing;
    if (disposing != null) {
      await disposing;
    }
    final pending = _opening;
    if (pending != null) {
      await pending;
      // The Weave member or selected organization may have changed while the
      // first request waited for OAuth. Recheck access before sharing it.
      return _open(
        synchronize: synchronize,
        allowInteractiveSignIn: allowInteractiveSignIn,
      );
    }

    final opening = _open(
      synchronize: synchronize,
      allowInteractiveSignIn: allowInteractiveSignIn,
    );
    _opening = opening;
    try {
      return await opening;
    } finally {
      if (identical(_opening, opening)) {
        _opening = null;
      }
    }
  }

  @override
  Future<void> disposePreservingCryptoState() async {
    final pending = _disposing;
    if (pending != null) {
      return pending;
    }
    final disposing = _disposePreservingCryptoState();
    _disposing = disposing;
    try {
      await disposing;
    } finally {
      if (identical(_disposing, disposing)) {
        _disposing = null;
      }
    }
  }

  Future<void> _disposePreservingCryptoState() async {
    try {
      await _opening;
    } on Object {
      // A failed open did not acquire a native sync owner.
    }
    final session = _activeSession;
    _activeSession = null;
    _activeFingerprint = null;
    _activeBindingKey = null;
    if (session != null) {
      await _rustMatrixCoreBridge.disposeClient(profileKey: session.profileKey);
    }
  }

  @override
  Future<void> removeForExplicitAccountRemoval() async {
    final bindingKey =
        _activeBindingKey ??
        await _secureStore.read(matrixOAuthCurrentBindingKey);
    final binding = bindingKey == null ? null : await _loadBinding(bindingKey);
    Object? signOutFailure;
    StackTrace? signOutStack;
    try {
      await endSession();
    } on Object catch (error, stack) {
      signOutFailure = error;
      signOutStack = stack;
    }
    if (binding == null) {
      await _matrixDeviceIdentityRepository.removeForExplicitAccountRemoval();
      if (signOutFailure != null) {
        Error.throwWithStackTrace(signOutFailure, signOutStack!);
      }
      return;
    }
    await _secureStore.delete(
      '$matrixCryptoStorePassphraseKeyPrefix${binding.profileKey}',
    );
    final ownerKey = '$matrixOAuthProfileOwnerKeyPrefix${binding.profileKey}';
    if (await _secureStore.read(ownerKey) == bindingKey) {
      await _secureStore.delete(ownerKey);
    }
    final root = await _storeRootLoader();
    final store = Directory(
      '${root.path}${Platform.pathSeparator}matrix-e2ee${Platform.pathSeparator}${binding.profileKey}',
    );
    if (await store.exists()) {
      await store.delete(recursive: true);
    }
    await _matrixDeviceIdentityRepository.removeForExplicitAccountRemoval();
    if (signOutFailure != null) {
      Error.throwWithStackTrace(signOutFailure, signOutStack!);
    }
  }

  @override
  Future<void> endSession() async {
    final bindingKey =
        _activeBindingKey ??
        await _secureStore.read(matrixOAuthCurrentBindingKey);
    if (bindingKey == null) {
      await disposePreservingCryptoState();
      return;
    }
    final binding = await _loadBinding(bindingKey);
    if (binding == null) {
      await _secureStore.delete(matrixOAuthCurrentBindingKey);
      await disposePreservingCryptoState();
      throw const ChatFailure.storage(
        'Saved Matrix account binding is missing; local Matrix access could not be cleared.',
      );
    }
    final storePath = (await _storeDirectory(binding.profileKey)).path;
    Object? nativeFailure;
    if (_activeSession == null) {
      final passphrase = await _secureStore.read(
        '$matrixCryptoStorePassphraseKeyPrefix${binding.profileKey}',
      );
      if (passphrase != null && passphrase.length >= 32) {
        try {
          await _rustMatrixCoreBridge.restoreOAuth(
            profileKey: binding.profileKey,
            homeserverUrl: binding.homeserverUrl,
            userId: binding.userId,
            deviceId: binding.deviceId,
            storePath: storePath,
            storePassphrase: passphrase,
          );
        } on Object {
          // A revoked or damaged remote session must still be cleared locally.
        }
      }
    }
    var remoteRevocationConfirmed = false;
    try {
      remoteRevocationConfirmed = await _rustMatrixCoreBridge.endOAuth(
        profileKey: binding.profileKey,
        storePath: storePath,
      );
    } on Object catch (error) {
      nativeFailure = error;
      try {
        await _rustMatrixCoreBridge.disposeClient(
          profileKey: binding.profileKey,
        );
      } on Object {
        // The encrypted local token file is removed below even if the bridge
        // cannot dispose an in-memory SDK client after a native failure.
      }
    }
    final tokenFile = File(
      '$storePath${Platform.pathSeparator}$_matrixOAuthSessionFile',
    );
    try {
      if (await tokenFile.exists()) await tokenFile.delete();
    } on FileSystemException catch (error) {
      throw ChatFailure.storage(
        'The saved Matrix session could not be cleared.',
        cause: error,
      );
    }
    await _secureStore.delete(bindingKey);
    await _secureStore.delete(matrixOAuthCurrentBindingKey);
    _activeSession = null;
    _activeFingerprint = null;
    _activeBindingKey = null;
    if (nativeFailure != null || !remoteRevocationConfirmed) {
      throw ChatFailure.protocol(
        'Matrix sign-out cleared local access, but remote revocation could not be confirmed.',
        cause: nativeFailure,
      );
    }
  }

  Future<MatrixCryptoSession> _open({
    required bool synchronize,
    required bool allowInteractiveSignIn,
  }) async {
    final configuration = await _serverConfigurationRepository
        .loadConfiguration();
    if (configuration == null || !configuration.hasCompleteAuthConfiguration) {
      throw const ChatFailure.configuration(
        'Finish setup before opening Weave Chat.',
      );
    }
    final authConfiguration = AuthConfiguration(
      issuer: configuration.oidcIssuerUrl,
      clientId: configuration.oidcClientRegistration.clientId,
    );
    final AuthState authState;
    try {
      authState = await _authSessionRepository.restoreSession(
        authConfiguration,
      );
    } on Object {
      await _dropActiveClient();
      rethrow;
    }
    final authSession = authState.session;
    if (!authState.isAuthenticated || authSession == null) {
      await _dropActiveClient();
      throw const ChatFailure.sessionRequired(
        'Sign in before opening Weave Chat.',
      );
    }
    final String subject;
    try {
      subject = _validatedSubject(authSession, authConfiguration);
    } on Object {
      await _dropActiveClient();
      rethrow;
    }
    final MatrixSessionAccess access;
    try {
      access = await _matrixSessionAccess.authorize(
        userApiBaseUrl: configuration.serviceEndpoints.backendApiBaseUrl,
        weaveAccessToken: authSession.accessToken,
        expectedSubject: subject,
        expectedIssuer: authConfiguration.issuer,
      );
    } on Object {
      // Revoked or uncertain current access cannot keep an active Matrix
      // client. Disposing it leaves the encrypted device store untouched.
      await _dropActiveClient();
      rethrow;
    }
    final deviceId = await _matrixDeviceIdentityRepository.loadOrCreate();
    final homeserver = configuration.serviceEndpoints.matrixHomeserverUrl;
    final bindingKey =
        '$matrixOAuthBindingKeyPrefix${_digest('${authConfiguration.issuer}|$subject|${_matrixStoreHomeserverIdentity(homeserver)}|$deviceId')}';
    final binding = await _loadBinding(bindingKey);
    if (binding?.organizationId != null &&
        binding!.organizationId != access.organizationId) {
      await _dropActiveClient();
      throw const ChatFailure.sessionRequired(
        'This Matrix session belongs to another Weave organization.',
      );
    }
    final fingerprint = '$bindingKey|${binding?.profileKey ?? ''}';
    final active = _activeSession;
    if (_activeFingerprint == fingerprint && active != null) {
      if (synchronize) {
        try {
          await _rustMatrixCoreBridge.syncClient(profileKey: active.profileKey);
        } on RustMatrixCoreBridgeException catch (error) {
          if (!isMatrixSessionExpiredCode(error.code)) rethrow;
          if (!allowInteractiveSignIn) {
            throw ChatFailure.sessionRequired(
              'Chat authorization expired. Retry with your Weave sign-in.',
              cause: error,
            );
          }
          await _dropActiveClient();
        }
      }
      if (_activeSession != null) return active;
    }
    if (active != null && _activeSession != null) {
      // A new Weave account cannot keep using the previous account's active
      // native Matrix client while its own OAuth authorization is pending.
      await _dropActiveClient();
    }
    late MatrixCryptoSession opened;
    var synchronized = false;
    if (binding != null) {
      if (binding.homeserverUrl != homeserver.toString() ||
          binding.deviceId != deviceId ||
          binding.userId != _expectedMatrixUserId(subject, homeserver) ||
          binding.profileKey !=
              _profileKey(homeserver, binding.userId, deviceId)) {
        throw const ChatFailure.storage(
          'Saved Matrix account binding does not match this device.',
        );
      }
      final storePassphrase = await _secureStore.read(
        '$matrixCryptoStorePassphraseKeyPrefix${binding.profileKey}',
      );
      if (storePassphrase == null || storePassphrase.length < 32) {
        throw const ChatFailure.storage(
          'The Matrix encryption store cannot be unlocked.',
        );
      }
      await _assertProfileOwner(binding.profileKey, bindingKey);
      try {
        await _rustMatrixCoreBridge.restoreOAuth(
          profileKey: binding.profileKey,
          homeserverUrl: homeserver.toString(),
          userId: binding.userId,
          deviceId: deviceId,
          storePath: (await _storeDirectory(binding.profileKey)).path,
          storePassphrase: storePassphrase,
        );
        if (synchronize) {
          await _rustMatrixCoreBridge.syncClient(
            profileKey: binding.profileKey,
          );
          synchronized = true;
        }
        opened = MatrixCryptoSession(
          profileKey: binding.profileKey,
          userId: binding.userId,
          deviceId: deviceId,
        );
      } on RustMatrixCoreBridgeException catch (error) {
        if (!isMatrixSessionExpiredCode(error.code)) rethrow;
        if (!allowInteractiveSignIn) {
          throw ChatFailure.sessionRequired(
            'Chat authorization expired. Retry with your Weave sign-in.',
            cause: error,
          );
        }
        // The old refresh grant is unusable. Keep its E2EE store and bind the
        // replacement grant only if MAS returns this exact Matrix account and
        // device. Disposal drops the stale SDK token without deleting keys.
        await _rustMatrixCoreBridge.disposeClient(
          profileKey: binding.profileKey,
        );
        opened = await _authorizeMatrixAccount(
          homeserver: homeserver,
          bindingKey: bindingKey,
          deviceId: deviceId,
          subject: subject,
          organizationId: access.organizationId,
          expectedBinding: binding,
        );
      }
      await _secureStore.write(
        '$matrixOAuthProfileOwnerKeyPrefix${binding.profileKey}',
        bindingKey,
      );
      if (binding.organizationId == null) {
        await _secureStore.write(
          bindingKey,
          jsonEncode(
            binding.toJson()..['organizationId'] = access.organizationId,
          ),
        );
      }
    } else {
      if (!allowInteractiveSignIn) {
        throw const ChatFailure.sessionRequired(
          'Chat authorization requires your Weave sign-in.',
        );
      }
      opened = await _authorizeMatrixAccount(
        homeserver: homeserver,
        bindingKey: bindingKey,
        deviceId: deviceId,
        subject: subject,
        organizationId: access.organizationId,
      );
    }
    if (synchronize && !synchronized) {
      await _rustMatrixCoreBridge.syncClient(profileKey: opened.profileKey);
    }
    _activeFingerprint = '$bindingKey|${opened.profileKey}';
    _activeBindingKey = bindingKey;
    _activeSession = opened;
    await _secureStore.write(matrixOAuthCurrentBindingKey, bindingKey);
    return opened;
  }

  Future<MatrixCryptoSession> _authorizeMatrixAccount({
    required Uri homeserver,
    required String bindingKey,
    required String deviceId,
    required String subject,
    required String organizationId,
    _MatrixOAuthBinding? expectedBinding,
  }) async {
    final loginKey = _digest('$bindingKey|${homeserver.toString()}');
    try {
      final authorization = await _rustMatrixCoreBridge.startOAuth(
        loginKey: loginKey,
        homeserverUrl: homeserver.toString(),
        deviceId: deviceId,
        redirectUri: matrixOAuthRedirectUri,
      );
      final callback = await _oauthBrowser.authorize(
        authorization.authorizationUrl,
        state: authorization.state,
        allowInsecureAuthorization: homeserver.scheme == 'http',
      );
      final identity = await _rustMatrixCoreBridge.finishOAuth(
        loginKey: loginKey,
        callbackUrl: callback,
      );
      if (identity.deviceId != deviceId ||
          identity.userId != _expectedMatrixUserId(subject, homeserver) ||
          (expectedBinding != null &&
              identity.userId != expectedBinding.userId)) {
        throw const ChatFailure.protocol(
          'Matrix signed in with an unexpected account or device identity.',
        );
      }
      final profileKey = _profileKey(homeserver, identity.userId, deviceId);
      if (expectedBinding != null && profileKey != expectedBinding.profileKey) {
        throw const ChatFailure.storage(
          'Saved Matrix account binding does not match this device.',
        );
      }
      await _assertProfileOwner(profileKey, bindingKey);
      final passphrase = expectedBinding == null
          ? await _loadOrCreateStorePassphrase(profileKey)
          : (await _secureStore.read(
              '$matrixCryptoStorePassphraseKeyPrefix$profileKey',
            ))!;
      await _rustMatrixCoreBridge.activateOAuth(
        loginKey: loginKey,
        profileKey: profileKey,
        storePath: (await _storeDirectory(profileKey)).path,
        storePassphrase: passphrase,
      );
      await _secureStore.write(
        '$matrixOAuthProfileOwnerKeyPrefix$profileKey',
        bindingKey,
      );
      await _secureStore.write(
        bindingKey,
        jsonEncode(<String, String>{
          'homeserverUrl': homeserver.toString(),
          'userId': identity.userId,
          'deviceId': deviceId,
          'profileKey': profileKey,
          'organizationId': organizationId,
        }),
      );
      return MatrixCryptoSession(
        profileKey: profileKey,
        userId: identity.userId,
        deviceId: deviceId,
      );
    } on Object {
      try {
        await _rustMatrixCoreBridge.abortOAuth(loginKey: loginKey);
      } on Object {
        // Preserve the original sign-in failure; pending native state is ephemeral.
      }
      rethrow;
    }
  }

  Future<_MatrixOAuthBinding?> _loadBinding(String key) async {
    final raw = await _secureStore.read(key);
    if (raw == null) return null;
    try {
      final decoded = jsonDecode(raw);
      if (decoded is! Map<String, dynamic>) throw const FormatException();
      return _MatrixOAuthBinding.fromJson(decoded);
    } on Object {
      throw const ChatFailure.storage(
        'Saved Matrix account binding is invalid.',
      );
    }
  }

  Future<void> _dropActiveClient() async {
    final active = _activeSession;
    _activeSession = null;
    _activeFingerprint = null;
    _activeBindingKey = null;
    if (active != null) {
      await _rustMatrixCoreBridge.disposeClient(profileKey: active.profileKey);
    }
  }

  Future<void> _assertProfileOwner(String profileKey, String bindingKey) async {
    final owner = await _secureStore.read(
      '$matrixOAuthProfileOwnerKeyPrefix$profileKey',
    );
    if (owner != null && owner != bindingKey) {
      throw const ChatFailure.storage(
        'This Matrix account is already bound to another Weave account on this device.',
      );
    }
  }

  Future<String> _loadOrCreateStorePassphrase(String profileKey) async {
    final key = '$matrixCryptoStorePassphraseKeyPrefix$profileKey';
    var passphrase = await _secureStore.read(key);
    if (passphrase == null || passphrase.length < 32) {
      passphrase = base64UrlEncode(
        List<int>.generate(48, (_) => _random.nextInt(256)),
      ).replaceAll('=', '');
      await _secureStore.write(key, passphrase);
    }
    return passphrase;
  }

  Future<Directory> _storeDirectory(String profileKey) async {
    final root = await _storeRootLoader();
    final store = Directory(
      '${root.path}${Platform.pathSeparator}matrix-e2ee${Platform.pathSeparator}$profileKey',
    );
    await store.create(recursive: true);
    return store;
  }
}

class _MatrixOAuthBinding {
  const _MatrixOAuthBinding({
    required this.homeserverUrl,
    required this.userId,
    required this.deviceId,
    required this.profileKey,
    this.organizationId,
  });

  factory _MatrixOAuthBinding.fromJson(Map<String, dynamic> json) {
    final homeserverUrl = json['homeserverUrl'];
    final userId = json['userId'];
    final deviceId = json['deviceId'];
    final profileKey = json['profileKey'];
    final organizationId = json['organizationId'];
    if (homeserverUrl is! String ||
        userId is! String ||
        deviceId is! String ||
        profileKey is! String ||
        homeserverUrl.isEmpty ||
        userId.isEmpty ||
        deviceId.isEmpty ||
        profileKey.isEmpty) {
      throw const FormatException('Invalid Matrix account binding');
    }
    if (organizationId != null &&
        (organizationId is! String || organizationId.trim().isEmpty)) {
      throw const FormatException('Invalid Matrix organization binding');
    }
    return _MatrixOAuthBinding(
      homeserverUrl: homeserverUrl,
      userId: userId,
      deviceId: deviceId,
      profileKey: profileKey,
      organizationId: organizationId,
    );
  }

  final String homeserverUrl;
  final String userId;
  final String deviceId;
  final String profileKey;
  final String? organizationId;

  Map<String, String> toJson() => {
    'homeserverUrl': homeserverUrl,
    'userId': userId,
    'deviceId': deviceId,
    'profileKey': profileKey,
    if (organizationId != null) 'organizationId': organizationId!,
  };
}

String _digest(String value) => sha256.convert(utf8.encode(value)).toString();

bool isMatrixSessionExpiredCode(String code) =>
    code == 'M_UNKNOWN_TOKEN' ||
    code == 'M_MISSING_TOKEN' ||
    code == 'M_WEAVE_MATRIX_SESSION_EXPIRED';

String _profileKey(Uri homeserver, String userId, String deviceId) =>
    _digest('${_matrixStoreHomeserverIdentity(homeserver)}|$userId|$deviceId');

String _matrixStoreHomeserverIdentity(Uri homeserver) =>
    homeserver.path.isEmpty || homeserver.path == '/'
    ? homeserver.origin
    : homeserver.toString();

String _expectedMatrixUserId(String subject, Uri homeserver) {
  // Match the accepted Weave Matrix northbound identity projection. The
  // southbound provider's account naming is never used as a member identity.
  final source = subject.split(':').last.replaceFirst(RegExp(r'^@'), '');
  final localpart = source
      .trim()
      .runes
      .map((rune) {
        final character = String.fromCharCode(rune).toLowerCase();
        return RegExp(r'[a-z0-9._=/\-]').hasMatch(character) ? character : '_';
      })
      .join()
      .replaceAll(RegExp(r'^_+|_+$'), '');
  if (localpart.isEmpty) {
    throw const ChatFailure.sessionRequired('Matrix identity is invalid.');
  }
  return '@$localpart:${homeserver.authority}';
}

String _validatedSubject(AuthSession session, AuthConfiguration configuration) {
  // These claims key local account isolation only. Matrix access is authorized
  // independently by MAS and the Matrix SDK, never by the Weave ID token.
  final idToken = session.idToken;
  if (idToken == null) {
    throw const ChatFailure.sessionRequired(
      'Sign in again before connecting a Matrix account.',
    );
  }
  try {
    if (!session.matches(configuration)) {
      throw const FormatException('Unexpected Weave session issuer or client');
    }
    final parts = idToken.split('.');
    if (parts.length != 3) throw const FormatException('Invalid ID token');
    final claims = jsonDecode(
      utf8.decode(base64Url.decode(base64Url.normalize(parts[1]))),
    );
    if (claims is! Map<String, dynamic>) throw const FormatException();
    final subject = claims['sub'];
    final audiences = claims['aud'];
    final audienceMatches =
        audiences == configuration.clientId ||
        (audiences is List && audiences.contains(configuration.clientId));
    if (claims['iss'] != configuration.issuer.toString() ||
        !audienceMatches ||
        subject is! String ||
        subject.trim().isEmpty) {
      throw const FormatException('Unexpected ID token identity');
    }
    return subject;
  } on Object {
    throw const ChatFailure.sessionRequired(
      'Sign in again before connecting a Matrix account.',
    );
  }
}

Future<Directory> _defaultStoreRoot() => getApplicationSupportDirectory();
