import 'dart:convert';
import 'dart:io';
import 'dart:math';

import 'package:crypto/crypto.dart';
import 'package:path_provider/path_provider.dart';
import 'package:weave/core/persistence/secure_store.dart';
import 'package:weave/features/auth/domain/entities/auth_configuration.dart';
import 'package:weave/features/auth/domain/entities/auth_session.dart';
import 'package:weave/features/auth/domain/repositories/auth_session_repository.dart';
import 'package:weave/features/chat/data/repositories/matrix_device_identity_repository.dart';
import 'package:weave/features/chat/domain/entities/chat_failure.dart';
import 'package:weave/features/server_config/domain/repositories/server_configuration_repository.dart';

import 'matrix_crypto_session_coordinator.dart';
import 'matrix_session_access.dart';
import 'rust_matrix_core_bridge.dart';

const _memberMatrixDeviceProofKeyPrefix = 'matrix_member_device_proof_v1_';

/// Opens the Weave Matrix facade with the member session owned by auth.
/// The bearer is held only by the live Rust SDK client, never copied to a
/// Matrix-specific token file or secure-store entry.
class WeaveMemberMatrixSessionCoordinator implements MatrixCryptoSessionPort {
  WeaveMemberMatrixSessionCoordinator({
    required ServerConfigurationRepository serverConfigurationRepository,
    required AuthSessionRepository authSessionRepository,
    required MatrixDeviceIdentityRepository matrixDeviceIdentityRepository,
    required MatrixSessionAccessPort matrixSessionAccess,
    required SecureStore secureStore,
    RustMatrixCoreBridge rustMatrixCoreBridge = const RustMatrixCoreBridge(),
    MatrixStoreRootLoader storeRootLoader = getApplicationSupportDirectory,
    Random? random,
  }) : _serverConfigurationRepository = serverConfigurationRepository,
       _authSessionRepository = authSessionRepository,
       _matrixDeviceIdentityRepository = matrixDeviceIdentityRepository,
       _matrixSessionAccess = matrixSessionAccess,
       _secureStore = secureStore,
       _bridge = rustMatrixCoreBridge,
       _storeRootLoader = storeRootLoader,
       _random = random ?? Random.secure();

  final ServerConfigurationRepository _serverConfigurationRepository;
  final AuthSessionRepository _authSessionRepository;
  final MatrixDeviceIdentityRepository _matrixDeviceIdentityRepository;
  final MatrixSessionAccessPort _matrixSessionAccess;
  final SecureStore _secureStore;
  final RustMatrixCoreBridge _bridge;
  final MatrixStoreRootLoader _storeRootLoader;
  final Random _random;

  Future<MatrixCryptoSession>? _opening;
  MatrixCryptoSession? _active;
  String? _activeFingerprint;

  @override
  Future<MatrixCryptoSession> open({
    bool synchronize = true,
    bool allowInteractiveSignIn = true,
  }) async {
    final pending = _opening;
    if (pending != null) {
      await pending;
      return open(synchronize: synchronize);
    }
    final opening = _open(synchronize: synchronize);
    _opening = opening;
    try {
      return await opening;
    } finally {
      if (identical(_opening, opening)) _opening = null;
    }
  }

  Future<MatrixCryptoSession> _open({required bool synchronize}) async {
    final configuration = await _serverConfigurationRepository
        .loadConfiguration();
    if (configuration == null || !configuration.hasCompleteAuthConfiguration) {
      await _dropActive();
      throw const ChatFailure.configuration('M_WEAVE_CHAT_SETUP_REQUIRED');
    }
    final authConfiguration = AuthConfiguration(
      issuer: configuration.oidcIssuerUrl,
      clientId: configuration.oidcClientRegistration.clientId,
    );
    final authState = await _authSessionRepository.restoreSession(
      authConfiguration,
    );
    final session = authState.session;
    if (!authState.isAuthenticated || session == null) {
      await _dropActive();
      throw const ChatFailure.sessionRequired(
        'M_WEAVE_MATRIX_MEMBER_SESSION_REQUIRED',
      );
    }
    final subject = _verifiedSubject(session, authConfiguration);
    final access = await _authorize(
      configuration.serviceEndpoints.backendApiBaseUrl,
      session.accessToken,
      subject,
      authConfiguration.issuer,
    );
    final homeserver = configuration.serviceEndpoints.matrixHomeserverUrl;
    final deviceId = await _matrixDeviceIdentityRepository.loadOrCreate();
    final userId = _matrixUserId(subject, homeserver);
    final bindingKey =
        '$matrixOAuthBindingKeyPrefix${_digest('${authConfiguration.issuer}|$subject|${_homeserverIdentity(homeserver)}|$deviceId')}';
    final profileKey = _digest(
      '${_homeserverIdentity(homeserver)}|$userId|$deviceId',
    );
    final fingerprint =
        '$bindingKey|${access.organizationId}|${_digest(session.accessToken)}';

    Future<void> revalidate() async {
      final currentConfig = await _serverConfigurationRepository
          .loadConfiguration();
      if (currentConfig == null ||
          currentConfig.oidcIssuerUrl != authConfiguration.issuer ||
          currentConfig.oidcClientRegistration.clientId !=
              authConfiguration.clientId ||
          currentConfig.serviceEndpoints.backendApiBaseUrl !=
              configuration.serviceEndpoints.backendApiBaseUrl ||
          currentConfig.serviceEndpoints.matrixHomeserverUrl != homeserver) {
        throw const ChatFailure.sessionRequired(
          'M_WEAVE_MATRIX_SESSION_CHANGED',
        );
      }
      final current = await _authSessionRepository.restoreSession(
        authConfiguration,
      );
      if (!current.isAuthenticated ||
          current.session?.accessToken != session.accessToken ||
          _verifiedSubject(current.session!, authConfiguration) != subject) {
        throw const ChatFailure.sessionRequired(
          'M_WEAVE_MATRIX_SESSION_CHANGED',
        );
      }
      final currentAccess = await _authorize(
        configuration.serviceEndpoints.backendApiBaseUrl,
        session.accessToken,
        subject,
        authConfiguration.issuer,
      );
      if (currentAccess.organizationId != access.organizationId) {
        throw const ChatFailure.sessionRequired(
          'M_WEAVE_MATRIX_SESSION_CHANGED',
        );
      }
    }

    try {
      await revalidate();
    } on Object {
      await _dropActive();
      rethrow;
    }
    final active = _active;
    if (active != null && _activeFingerprint == fingerprint) {
      if (synchronize) {
        try {
          await _bridge.syncClient(profileKey: profileKey);
        } on Object {
          await _dropActive();
          rethrow;
        }
      }
      return active;
    }
    await _dropActive();

    final rawBinding = await _secureStore.read(bindingKey);
    if (rawBinding != null) {
      final Map<String, dynamic> saved;
      try {
        saved = jsonDecode(rawBinding) as Map<String, dynamic>;
      } on Object {
        throw const ChatFailure.storage('M_WEAVE_MATRIX_BINDING_INVALID');
      }
      if (saved['homeserverUrl'] != homeserver.toString() ||
          saved['userId'] != userId ||
          saved['deviceId'] != deviceId ||
          saved['profileKey'] != profileKey ||
          saved['organizationId'] != access.organizationId) {
        throw const ChatFailure.storage('M_WEAVE_MATRIX_BINDING_MISMATCH');
      }
    }
    final ownerKey = '$matrixOAuthProfileOwnerKeyPrefix$profileKey';
    final organizationKey =
        '$matrixOAuthProfileOrganizationKeyPrefix$profileKey';
    final passphraseKey = '$matrixCryptoStorePassphraseKeyPrefix$profileKey';
    final deviceProofKey = '$_memberMatrixDeviceProofKeyPrefix$profileKey';
    final owner = await _secureStore.read(ownerKey);
    final storedOrganization = await _secureStore.read(organizationKey);
    var passphrase = await _secureStore.read(passphraseKey);
    if ((owner != null && owner != bindingKey) ||
        (storedOrganization != null &&
            storedOrganization != access.organizationId) ||
        (passphrase != null &&
            rawBinding == null &&
            (owner != bindingKey ||
                storedOrganization != access.organizationId))) {
      throw const ChatFailure.storage('M_WEAVE_MATRIX_STORE_OWNER_MISMATCH');
    }
    await _secureStore.write(ownerKey, bindingKey);
    await _secureStore.write(organizationKey, access.organizationId);
    if (passphrase == null) {
      passphrase = base64UrlEncode(
        List<int>.generate(48, (_) => _random.nextInt(256)),
      ).replaceAll('=', '');
      await _secureStore.write(passphraseKey, passphrase);
    }
    if (passphrase.length < 32) {
      throw const ChatFailure.storage('M_WEAVE_MATRIX_STORE_LOCKED');
    }
    var deviceProof = await _secureStore.read(deviceProofKey);
    if (deviceProof == null) {
      deviceProof = base64UrlEncode(
        List<int>.generate(48, (_) => _random.nextInt(256)),
      ).replaceAll('=', '');
      await _secureStore.write(deviceProofKey, deviceProof);
    }
    try {
      final proofBytes = base64Url.decode(base64Url.normalize(deviceProof));
      if (proofBytes.length < 32 || proofBytes.length > 64) {
        throw const FormatException();
      }
    } on FormatException {
      throw const ChatFailure.storage('M_WEAVE_MATRIX_DEVICE_PROOF_INVALID');
    }
    final store = Directory.fromUri(
      (await _storeRootLoader()).uri
          .resolve('matrix-e2ee/')
          .resolve(profileKey),
    );
    await store.create(recursive: true);
    await revalidate();
    try {
      await _bridge.activateMemberSession(
        profileKey: profileKey,
        homeserverUrl: homeserver.toString(),
        userId: userId,
        deviceId: deviceId,
        accessToken: session.accessToken,
        deviceProof: deviceProof,
        storePath: store.path,
        storePassphrase: passphrase,
      );
      await revalidate();
      if (synchronize) await _bridge.syncClient(profileKey: profileKey);
    } on Object {
      await _bridge.disposeClient(profileKey: profileKey);
      rethrow;
    }
    await _secureStore.write(
      bindingKey,
      jsonEncode(<String, String>{
        'homeserverUrl': homeserver.toString(),
        'userId': userId,
        'deviceId': deviceId,
        'profileKey': profileKey,
        'organizationId': access.organizationId,
      }),
    );
    await _secureStore.write(matrixOAuthCurrentBindingKey, bindingKey);
    final oldGrant = File.fromUri(
      store.uri.resolve('weave-matrix-oauth-session.v1'),
    );
    try {
      if (await oldGrant.exists()) await oldGrant.delete();
    } on FileSystemException catch (error) {
      await _bridge.disposeClient(profileKey: profileKey);
      throw ChatFailure.storage(
        'M_WEAVE_MATRIX_RETIRED_GRANT_CLEAR_FAILED',
        cause: error,
      );
    }
    final opened = MatrixCryptoSession(
      profileKey: profileKey,
      userId: userId,
      deviceId: deviceId,
    );
    _active = opened;
    _activeFingerprint = fingerprint;
    return opened;
  }

  Future<MatrixSessionAccess> _authorize(
    Uri apiBaseUrl,
    String token,
    String subject,
    Uri issuer,
  ) async {
    try {
      return await _matrixSessionAccess.authorize(
        userApiBaseUrl: apiBaseUrl,
        weaveAccessToken: token,
        expectedSubject: subject,
        expectedIssuer: issuer,
      );
    } on Object {
      await _dropActive();
      rethrow;
    }
  }

  Future<void> _dropActive() async {
    final active = _active;
    _active = null;
    _activeFingerprint = null;
    if (active != null) {
      await _bridge.disposeClient(profileKey: active.profileKey);
    }
  }

  @override
  Future<void> disposePreservingCryptoState() async {
    try {
      await _opening;
    } on Object {
      // Failed opens never publish a session.
    }
    await _dropActive();
  }

  @override
  Future<void> endSession() => disposePreservingCryptoState();

  @override
  Future<void> removeForExplicitAccountRemoval() async {
    await disposePreservingCryptoState();
    final bindingKey = await _secureStore.read(matrixOAuthCurrentBindingKey);
    final raw = bindingKey == null ? null : await _secureStore.read(bindingKey);
    if (raw != null) {
      final Map<String, dynamic> binding;
      try {
        binding = jsonDecode(raw) as Map<String, dynamic>;
      } on Object {
        throw const ChatFailure.storage('M_WEAVE_MATRIX_BINDING_INVALID');
      }
      final profileKey = binding['profileKey'];
      if (profileKey is! String ||
          !RegExp(r'^[0-9a-f]{64}$').hasMatch(profileKey)) {
        throw const ChatFailure.storage('M_WEAVE_MATRIX_BINDING_INVALID');
      }
      await _secureStore.delete(
        '$matrixCryptoStorePassphraseKeyPrefix$profileKey',
      );
      await _secureStore.delete(
        '$_memberMatrixDeviceProofKeyPrefix$profileKey',
      );
      await _secureStore.delete('$matrixOAuthProfileOwnerKeyPrefix$profileKey');
      await _secureStore.delete(
        '$matrixOAuthProfileOrganizationKeyPrefix$profileKey',
      );
      final store = Directory.fromUri(
        (await _storeRootLoader()).uri
            .resolve('matrix-e2ee/')
            .resolve(profileKey),
      );
      if (await store.exists()) await store.delete(recursive: true);
      await _secureStore.delete(bindingKey!);
    }
    await _secureStore.delete(matrixOAuthCurrentBindingKey);
    await _matrixDeviceIdentityRepository.removeForExplicitAccountRemoval();
  }
}

String _digest(String value) => sha256.convert(utf8.encode(value)).toString();

String _homeserverIdentity(Uri homeserver) =>
    homeserver.path.isEmpty || homeserver.path == '/'
    ? homeserver.origin
    : homeserver.toString();

String _matrixUserId(String subject, Uri homeserver) {
  final source = subject.split(':').last.replaceFirst(RegExp(r'^@+'), '');
  final localpart = source
      .trim()
      .runes
      .map((rune) {
        final lower = rune >= 65 && rune <= 90 ? rune + 32 : rune;
        final allowed =
            (lower >= 97 && lower <= 122) ||
            (lower >= 48 && lower <= 57) ||
            const {46, 95, 45, 61, 47}.contains(lower);
        return allowed ? String.fromCharCode(lower) : '_';
      })
      .join()
      .replaceAll(RegExp(r'^_+|_+$'), '');
  if (localpart.isEmpty) {
    throw const ChatFailure.sessionRequired('M_WEAVE_MATRIX_IDENTITY_INVALID');
  }
  return '@$localpart:${homeserver.authority}';
}

String _verifiedSubject(AuthSession session, AuthConfiguration configuration) {
  final idToken = session.idToken;
  if (idToken == null || !session.matches(configuration)) {
    throw const ChatFailure.sessionRequired(
      'M_WEAVE_MATRIX_MEMBER_SESSION_REQUIRED',
    );
  }
  try {
    final parts = idToken.split('.');
    if (parts.length != 3) throw const FormatException();
    final claims = jsonDecode(
      utf8.decode(base64Url.decode(base64Url.normalize(parts[1]))),
    );
    if (claims is! Map<String, dynamic>) throw const FormatException();
    final audience = claims['aud'];
    if (claims['iss'] != configuration.issuer.toString() ||
        !(audience == configuration.clientId ||
            (audience is List && audience.contains(configuration.clientId))) ||
        claims['sub'] is! String ||
        (claims['sub'] as String).trim().isEmpty) {
      throw const FormatException();
    }
    return claims['sub'] as String;
  } on Object {
    throw const ChatFailure.sessionRequired(
      'M_WEAVE_MATRIX_MEMBER_SESSION_REQUIRED',
    );
  }
}
