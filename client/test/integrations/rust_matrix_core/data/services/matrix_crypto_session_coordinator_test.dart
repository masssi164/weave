import 'dart:async';
import 'dart:convert';
import 'dart:io';
import 'dart:math';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:weave/features/auth/domain/entities/auth_configuration.dart';
import 'package:weave/features/auth/domain/entities/auth_state.dart';
import 'package:weave/features/auth/domain/repositories/auth_session_repository.dart';
import 'package:weave/features/chat/data/repositories/matrix_device_identity_repository.dart';
import 'package:weave/features/chat/domain/entities/chat_failure.dart';
import 'package:weave/features/server_config/domain/entities/server_configuration.dart';
import 'package:weave/features/server_config/domain/repositories/server_configuration_repository.dart';
import 'package:weave/integrations/rust_matrix_core/data/services/matrix_crypto_session_coordinator.dart';

import '../../../../helpers/auth_test_data.dart';
import '../../../../helpers/fake_matrix_crypto.dart';
import '../../../../helpers/in_memory_stores.dart';
import '../../../../helpers/server_config_test_data.dart';

class _ConfigurationRepository implements ServerConfigurationRepository {
  _ConfigurationRepository(this.configuration);

  ServerConfiguration? configuration;

  @override
  Future<void> clearConfiguration() async => configuration = null;

  @override
  Future<ServerConfiguration?> loadConfiguration() async => configuration;

  @override
  Future<void> saveConfiguration(ServerConfiguration configuration) async {
    this.configuration = configuration;
  }
}

class _AuthRepository implements AuthSessionRepository {
  AuthState state = AuthState.authenticated(buildTestAuthSession());

  @override
  Future<void> clearLocalSession() async {}

  @override
  Future<AuthState> refreshSession(AuthConfiguration configuration) async =>
      state;

  @override
  Future<AuthState> restoreSession(AuthConfiguration configuration) async =>
      state;

  @override
  Future<AuthState> signIn(AuthConfiguration configuration) async => state;

  @override
  Future<void> signOut(AuthConfiguration configuration) async {}
}

class _ControlledInitializationBridge extends FakeRustMatrixCoreBridge {
  final initializationStarted = Completer<void>();
  final allowInitialization = Completer<void>();

  @override
  Future<void> initializeClient({
    required String profileKey,
    required String homeserverUrl,
    required String userId,
    required String deviceId,
    required String accessToken,
    required String storePath,
    required String storePassphrase,
    String? extraRootCertificatePem,
  }) async {
    initializationStarted.complete();
    await allowInitialization.future;
    await super.initializeClient(
      profileKey: profileKey,
      homeserverUrl: homeserverUrl,
      userId: userId,
      deviceId: deviceId,
      accessToken: accessToken,
      storePath: storePath,
      storePassphrase: storePassphrase,
      extraRootCertificatePem: extraRootCertificatePem,
    );
  }
}

void main() {
  late Directory storeRoot;
  late InMemorySecureStore secureStore;
  late _ConfigurationRepository configurationRepository;
  late _AuthRepository authRepository;
  late FakeRustMatrixCoreBridge bridge;

  MatrixCryptoSessionCoordinator buildCoordinator({
    required http.Client httpClient,
    required int randomSeed,
  }) {
    return MatrixCryptoSessionCoordinator(
      serverConfigurationRepository: configurationRepository,
      authSessionRepository: authRepository,
      matrixDeviceIdentityRepository: MatrixDeviceIdentityRepository(
        secureStore: secureStore,
        random: Random(randomSeed),
      ),
      secureStore: secureStore,
      httpClient: httpClient,
      rustMatrixCoreBridge: bridge,
      storeRootLoader: () async => storeRoot,
      random: Random(randomSeed),
    );
  }

  http.Client whoamiClient() {
    return MockClient((request) async {
      expect(request.url.path, '/_matrix/client/v3/account/whoami');
      expect(request.headers['authorization'], startsWith('Bearer '));
      final deviceId = request.headers['x-weave-matrix-device-id'];
      return http.Response(
        jsonEncode(<String, String>{
          'user_id': '@user:api.weave.test',
          'device_id': deviceId!,
        }),
        200,
      );
    });
  }

  setUp(() async {
    storeRoot = await Directory.systemTemp.createTemp('weave-matrix-e2ee-');
    secureStore = InMemorySecureStore();
    configurationRepository = _ConfigurationRepository(
      buildTestConfiguration(matrixHomeserverUrl: 'https://api.weave.test'),
    );
    authRepository = _AuthRepository();
    bridge = FakeRustMatrixCoreBridge();
  });

  tearDown(() async {
    if (await storeRoot.exists()) {
      await storeRoot.delete(recursive: true);
    }
  });

  test(
    'app relaunch restores the same profile, device, and crypto store',
    () async {
      // MATRIX_E2EE_IPHONE_RELAUNCH
      final httpClient = whoamiClient();
      final first = buildCoordinator(httpClient: httpClient, randomSeed: 1);

      final firstSession = await first.open();
      final firstInitialization = bridge.initializations.single;
      await first.disposePreservingCryptoState();
      final second = buildCoordinator(httpClient: httpClient, randomSeed: 999);
      final secondSession = await second.open();
      final secondInitialization = bridge.initializations.last;

      expect(secondSession.profileKey, firstSession.profileKey);
      expect(secondSession.deviceId, firstSession.deviceId);
      expect(
        secondInitialization['storePath'],
        firstInitialization['storePath'],
      );
      expect(
        secondInitialization['storePassphrase'],
        firstInitialization['storePassphrase'],
      );
      expect(
        await Directory(firstInitialization['storePath']!).exists(),
        isTrue,
      );
      expect(bridge.disposedProfiles, <String>[firstSession.profileKey]);
    },
  );

  test(
    'OIDC token refresh rebinds Rust without changing crypto identity',
    () async {
      final coordinator = buildCoordinator(
        httpClient: whoamiClient(),
        randomSeed: 1,
      );

      final first = await coordinator.open(synchronize: false);
      authRepository.state = AuthState.authenticated(
        buildTestAuthSession(accessToken: 'refreshed-access-token'),
      );
      final second = await coordinator.open(synchronize: false);

      expect(second.profileKey, first.profileKey);
      expect(bridge.initializations, hasLength(2));
      expect(
        bridge.initializations.map((value) => value['accessToken']),
        <String?>['access-token', 'refreshed-access-token'],
      );
      expect(
        bridge.initializations.map((value) => value['storePassphrase']).toSet(),
        hasLength(1),
      );
    },
  );

  test(
    'dispose waits for an in-flight owner startup before shutdown',
    () async {
      final controlledBridge = _ControlledInitializationBridge();
      bridge = controlledBridge;
      final coordinator = buildCoordinator(
        httpClient: whoamiClient(),
        randomSeed: 1,
      );

      final opening = coordinator.open(synchronize: false);
      await controlledBridge.initializationStarted.future;
      var disposed = false;
      final disposing = coordinator.disposePreservingCryptoState().then((_) {
        disposed = true;
      });
      await Future<void>.delayed(Duration.zero);

      expect(disposed, isFalse);
      expect(controlledBridge.disposedProfiles, isEmpty);

      controlledBridge.allowInitialization.complete();
      final session = await opening;
      await disposing;

      expect(controlledBridge.disposedProfiles, <String>[session.profileKey]);
    },
  );

  test(
    'only explicit account removal deletes device and crypto material',
    () async {
      final coordinator = buildCoordinator(
        httpClient: whoamiClient(),
        randomSeed: 1,
      );
      final session = await coordinator.open(synchronize: false);
      final storePath = bridge.initializations.single['storePath']!;
      final passphraseKey =
          '$matrixCryptoStorePassphraseKeyPrefix${session.profileKey}';

      await coordinator.removeForExplicitAccountRemoval();

      expect(await secureStore.read(matrixDeviceIdentityStorageKey), isNull);
      expect(await secureStore.read(passphraseKey), isNull);
      expect(await Directory(storePath).exists(), isFalse);
    },
  );

  test('fails closed when the OIDC device identity does not match', () async {
    // MATRIX_E2EE_CLIENT_FAILS_CLOSED
    final coordinator = buildCoordinator(
      httpClient: MockClient(
        (_) async => http.Response(
          jsonEncode(<String, String>{
            'user_id': '@user:api.weave.test',
            'device_id': 'WEAVEOTHERDEVICE000000000000000000000000',
          }),
          200,
        ),
      ),
      randomSeed: 1,
    );

    await expectLater(
      coordinator.open(),
      throwsA(
        isA<ChatFailure>().having(
          (failure) => failure.type,
          'type',
          ChatFailureType.protocol,
        ),
      ),
    );
    expect(bridge.initializations, isEmpty);
  });
}
