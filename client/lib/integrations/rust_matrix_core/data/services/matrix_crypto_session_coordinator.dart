import 'dart:convert';
import 'dart:io';
import 'dart:math';

import 'package:crypto/crypto.dart';
import 'package:http/http.dart' as http;
import 'package:path_provider/path_provider.dart';
import 'package:weave/core/persistence/secure_store.dart';
import 'package:weave/features/auth/domain/entities/auth_configuration.dart';
import 'package:weave/features/auth/domain/repositories/auth_session_repository.dart';
import 'package:weave/features/chat/data/repositories/matrix_device_identity_repository.dart';
import 'package:weave/features/chat/domain/entities/chat_failure.dart';
import 'package:weave/features/server_config/domain/repositories/server_configuration_repository.dart';

import 'rust_matrix_core_bridge.dart';

const matrixCryptoStorePassphraseKeyPrefix =
    'matrix_crypto_store_passphrase_v1_';

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
  Future<MatrixCryptoSession> open({bool synchronize = true});

  Future<void> disposePreservingCryptoState();

  Future<void> removeForExplicitAccountRemoval();
}

class MatrixCryptoSessionCoordinator implements MatrixCryptoSessionPort {
  MatrixCryptoSessionCoordinator({
    required ServerConfigurationRepository serverConfigurationRepository,
    required AuthSessionRepository authSessionRepository,
    required MatrixDeviceIdentityRepository matrixDeviceIdentityRepository,
    required SecureStore secureStore,
    required http.Client httpClient,
    RustMatrixCoreBridge rustMatrixCoreBridge = const RustMatrixCoreBridge(),
    MatrixStoreRootLoader storeRootLoader = _defaultStoreRoot,
    Random? random,
  }) : _serverConfigurationRepository = serverConfigurationRepository,
       _authSessionRepository = authSessionRepository,
       _matrixDeviceIdentityRepository = matrixDeviceIdentityRepository,
       _secureStore = secureStore,
       _httpClient = httpClient,
       _rustMatrixCoreBridge = rustMatrixCoreBridge,
       _storeRootLoader = storeRootLoader,
       _random = random ?? Random.secure();

  final ServerConfigurationRepository _serverConfigurationRepository;
  final AuthSessionRepository _authSessionRepository;
  final MatrixDeviceIdentityRepository _matrixDeviceIdentityRepository;
  final SecureStore _secureStore;
  final http.Client _httpClient;
  final RustMatrixCoreBridge _rustMatrixCoreBridge;
  final MatrixStoreRootLoader _storeRootLoader;
  final Random _random;

  Future<MatrixCryptoSession>? _opening;
  String? _activeFingerprint;
  MatrixCryptoSession? _activeSession;

  @override
  Future<MatrixCryptoSession> open({bool synchronize = true}) async {
    final pending = _opening;
    if (pending != null) {
      final session = await pending;
      if (synchronize) {
        await _rustMatrixCoreBridge.syncClient(profileKey: session.profileKey);
      }
      return session;
    }

    final opening = _open(synchronize: synchronize);
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
    final session = _activeSession;
    _activeSession = null;
    _activeFingerprint = null;
    if (session != null) {
      await _rustMatrixCoreBridge.disposeClient(profileKey: session.profileKey);
    }
  }

  @override
  Future<void> removeForExplicitAccountRemoval() async {
    final session = _activeSession ?? await open(synchronize: false);
    await disposePreservingCryptoState();
    await _secureStore.delete(
      '$matrixCryptoStorePassphraseKeyPrefix${session.profileKey}',
    );
    final root = await _storeRootLoader();
    final store = Directory(
      '${root.path}${Platform.pathSeparator}matrix-e2ee${Platform.pathSeparator}${session.profileKey}',
    );
    if (await store.exists()) {
      await store.delete(recursive: true);
    }
    await _matrixDeviceIdentityRepository.removeForExplicitAccountRemoval();
  }

  Future<MatrixCryptoSession> _open({required bool synchronize}) async {
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
    final authState = await _authSessionRepository.restoreSession(
      authConfiguration,
    );
    final authSession = authState.session;
    if (!authState.isAuthenticated || authSession == null) {
      throw const ChatFailure.sessionRequired(
        'Sign in before opening Weave Chat.',
      );
    }

    final deviceId = await _matrixDeviceIdentityRepository.loadOrCreate();
    final userId = await _loadMatrixIdentity(
      configuration.serviceEndpoints.matrixHomeserverUrl,
      accessToken: authSession.accessToken,
      deviceId: deviceId,
    );
    final profileKey = sha256
        .convert(
          utf8.encode(
            '${configuration.serviceEndpoints.matrixHomeserverUrl.origin}|$userId|$deviceId',
          ),
        )
        .toString();
    final fingerprint = sha256
        .convert(utf8.encode('${authSession.accessToken}|$profileKey'))
        .toString();
    final active = _activeSession;
    if (_activeFingerprint == fingerprint && active != null) {
      if (synchronize) {
        await _rustMatrixCoreBridge.syncClient(profileKey: active.profileKey);
      }
      return active;
    }

    final passphraseKey = '$matrixCryptoStorePassphraseKeyPrefix$profileKey';
    var storePassphrase = await _secureStore.read(passphraseKey);
    if (storePassphrase == null || storePassphrase.length < 32) {
      storePassphrase = base64UrlEncode(
        List<int>.generate(48, (_) => _random.nextInt(256)),
      ).replaceAll('=', '');
      await _secureStore.write(passphraseKey, storePassphrase);
    }
    final root = await _storeRootLoader();
    final store = Directory(
      '${root.path}${Platform.pathSeparator}matrix-e2ee${Platform.pathSeparator}$profileKey',
    );
    await store.create(recursive: true);

    await _rustMatrixCoreBridge.initializeClient(
      profileKey: profileKey,
      homeserverUrl: configuration.serviceEndpoints.matrixHomeserverUrl
          .toString(),
      userId: userId,
      deviceId: deviceId,
      accessToken: authSession.accessToken,
      storePath: store.path,
      storePassphrase: storePassphrase,
    );
    if (synchronize) {
      await _rustMatrixCoreBridge.syncClient(profileKey: profileKey);
    }
    final opened = MatrixCryptoSession(
      profileKey: profileKey,
      userId: userId,
      deviceId: deviceId,
    );
    _activeFingerprint = fingerprint;
    _activeSession = opened;
    return opened;
  }

  Future<String> _loadMatrixIdentity(
    Uri homeserverUrl, {
    required String accessToken,
    required String deviceId,
  }) async {
    final request =
        http.Request(
            'GET',
            homeserverUrl.resolve('/_matrix/client/v3/account/whoami'),
          )
          ..headers['Authorization'] = 'Bearer $accessToken'
          ..headers['X-Weave-Matrix-Device-Id'] = deviceId
          ..headers['Accept'] = 'application/json';
    final response = await http.Response.fromStream(
      await _httpClient.send(request),
    );
    if (response.statusCode < 200 || response.statusCode >= 300) {
      throw const ChatFailure.sessionRequired(
        'The Weave session is no longer authorized for Chat.',
      );
    }
    final decoded = jsonDecode(response.body);
    if (decoded is! Map ||
        decoded['user_id'] is! String ||
        decoded['device_id'] != deviceId) {
      throw const ChatFailure.protocol(
        'Weave Chat returned an invalid device identity.',
      );
    }
    return decoded['user_id'] as String;
  }
}

Future<Directory> _defaultStoreRoot() => getApplicationSupportDirectory();
