import 'dart:convert';
import 'dart:io';
import 'dart:math';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart' as http_testing;
import 'package:weave/features/auth/domain/entities/auth_configuration.dart';
import 'package:weave/features/auth/domain/entities/auth_state.dart';
import 'package:weave/features/auth/domain/repositories/auth_session_repository.dart';
import 'package:weave/features/chat/data/repositories/matrix_device_identity_repository.dart';
import 'package:weave/features/chat/domain/entities/chat_failure.dart';
import 'package:weave/features/server_config/domain/entities/server_configuration.dart';
import 'package:weave/features/server_config/domain/repositories/server_configuration_repository.dart';
import 'package:weave/integrations/rust_matrix_core/data/services/matrix_crypto_session_coordinator.dart';
import 'package:weave/integrations/rust_matrix_core/data/services/matrix_session_access.dart';
import 'package:weave/integrations/rust_matrix_core/data/services/weave_member_matrix_session_coordinator.dart';

import '../../../../helpers/auth_test_data.dart';
import '../../../../helpers/fake_matrix_crypto.dart';
import '../../../../helpers/in_memory_stores.dart';
import '../../../../helpers/server_config_test_data.dart';

class _ConfigurationRepository implements ServerConfigurationRepository {
  _ConfigurationRepository(this.configuration);
  ServerConfiguration? configuration;

  @override
  Future<ServerConfiguration?> loadConfiguration() async => configuration;
  @override
  Future<void> saveConfiguration(ServerConfiguration value) async =>
      configuration = value;
  @override
  Future<void> clearConfiguration() async => configuration = null;
}

class _AuthRepository implements AuthSessionRepository {
  _AuthRepository(this.state);
  AuthState state;
  bool failRestore = false;

  @override
  Future<AuthState> restoreSession(AuthConfiguration configuration) async {
    if (failRestore) throw StateError('Member session restore failed');
    return state;
  }

  @override
  Future<AuthState> refreshSession(AuthConfiguration configuration) async =>
      state;
  @override
  Future<AuthState> signIn(AuthConfiguration configuration) async => state;
  @override
  Future<void> signOut(AuthConfiguration configuration) async {}
  @override
  Future<void> clearLocalSession() async {}
}

class _Access implements MatrixSessionAccessPort {
  String organizationId = 'org-one';
  bool allowed = true;
  Uri advertisedMatrixUrl = Uri.parse('https://api.weave.test');
  int calls = 0;

  @override
  Future<MatrixSessionAccess> authorize({
    required Uri userApiBaseUrl,
    required String weaveAccessToken,
    required String expectedSubject,
    required Uri expectedIssuer,
  }) async {
    calls++;
    if (!allowed) {
      throw const ChatFailure.sessionRequired('M_WEAVE_MATRIX_ACCESS_DENIED');
    }
    return MatrixSessionAccess(
      organizationId: organizationId,
      subject: expectedSubject,
      matrixClientServerBaseUrl: advertisedMatrixUrl,
    );
  }
}

class _FailingBindingStore extends InMemorySecureStore {
  @override
  Future<void> write(String key, String value) async {
    if (key == matrixOAuthCurrentBindingKey) {
      throw StateError('Secure binding write failed');
    }
    await super.write(key, value);
  }
}

String _idToken() {
  String encode(Map<String, Object> value) =>
      base64UrlEncode(utf8.encode(jsonEncode(value))).replaceAll('=', '');
  return '${encode({'alg': 'RS256'})}.${encode({'iss': 'https://auth.home.internal', 'aud': 'weave-app', 'sub': 'person-1'})}.signature';
}

void main() {
  late Directory root;
  late InMemorySecureStore store;
  late _ConfigurationRepository configuration;
  late _AuthRepository auth;
  late _Access access;
  late FakeRustMatrixCoreBridge bridge;
  late http.Client matrixHttpClient;
  late List<http.Request> logoutRequests;

  WeaveMemberMatrixSessionCoordinator coordinator() =>
      WeaveMemberMatrixSessionCoordinator(
        serverConfigurationRepository: configuration,
        authSessionRepository: auth,
        matrixDeviceIdentityRepository: MatrixDeviceIdentityRepository(
          secureStore: store,
          random: Random(1),
        ),
        matrixSessionAccess: access,
        matrixHttpClient: matrixHttpClient,
        secureStore: store,
        rustMatrixCoreBridge: bridge,
        storeRootLoader: () async => root,
        random: Random(2),
      );

  setUp(() async {
    root = await Directory.systemTemp.createTemp('weave-member-matrix-');
    store = InMemorySecureStore();
    configuration = _ConfigurationRepository(
      buildTestConfiguration(matrixHomeserverUrl: 'https://api.weave.test'),
    );
    auth = _AuthRepository(
      AuthState.authenticated(buildTestAuthSession(idToken: _idToken())),
    );
    access = _Access();
    bridge = FakeRustMatrixCoreBridge();
    logoutRequests = <http.Request>[];
    matrixHttpClient = http_testing.MockClient((request) async {
      logoutRequests.add(request);
      return http.Response('{}', 200);
    });
  });

  tearDown(() async {
    if (await root.exists()) await root.delete(recursive: true);
  });

  test(
    'opens the native Matrix client with the current Weave member token',
    () async {
      final session = await coordinator().open(
        synchronize: false,
        allowInteractiveSignIn: false,
      );

      expect(
        session.userId,
        '@acct_e476de73525691fc887829c1c645e993:api.weave.test',
      );
      expect(bridge.memberActivations, hasLength(1));
      expect(bridge.memberActivations.single['accessToken'], 'access-token');
      final proof = bridge.memberActivations.single['deviceProof']!;
      expect(base64Url.decode(base64Url.normalize(proof)), hasLength(48));
      expect(bridge.oauthStarts, isEmpty);
      expect(bridge.oauthActivations, isEmpty);
      final bindingKey = store.rawValue(matrixOAuthCurrentBindingKey)!;
      expect(store.rawValue(bindingKey), contains('org-one'));
      expect(store.rawValue(bindingKey), isNot(contains('access-token')));
      expect(store.rawValue(bindingKey), isNot(contains(proof)));
      expect(
        await File(
          '${bridge.memberActivations.single['storePath']}/weave-matrix-oauth-session.v1',
        ).exists(),
        isFalse,
      );
    },
  );

  test('does not include a local HTTPS port in the Matrix user ID', () async {
    configuration.configuration = buildTestConfiguration(
      matrixHomeserverUrl: 'https://api.weave.test:44443',
    );
    access.advertisedMatrixUrl = Uri.parse('https://api.weave.test:44443');

    final session = await coordinator().open(
      synchronize: false,
      allowInteractiveSignIn: false,
    );

    expect(
      session.userId,
      '@acct_e476de73525691fc887829c1c645e993:api.weave.test',
    );
    expect(
      bridge.memberActivations.single['homeserverUrl'],
      'https://api.weave.test:44443',
    );
  });

  test('rejects an insecure Matrix URL before member admission', () async {
    configuration.configuration = buildTestConfiguration(
      matrixHomeserverUrl: 'http://api.weave.test',
    );

    await expectLater(
      coordinator().open(synchronize: false),
      throwsA(
        isA<ChatFailure>()
            .having(
              (failure) => failure.type,
              'failure category',
              ChatFailureType.configuration,
            )
            .having(
              (failure) => failure.message,
              'support-safe diagnostic code',
              'M_WEAVE_MATRIX_ENDPOINT_UNCONFIRMED',
            ),
      ),
    );
    expect(access.calls, 0);
    expect(bridge.memberActivations, isEmpty);
  });

  test('rejects a Matrix URL that differs from the current manifest', () async {
    access.advertisedMatrixUrl = Uri.parse('https://other.weave.test');

    await expectLater(
      coordinator().open(synchronize: false),
      throwsA(
        isA<ChatFailure>()
            .having(
              (failure) => failure.type,
              'failure category',
              ChatFailureType.configuration,
            )
            .having(
              (failure) => failure.message,
              'support-safe diagnostic code',
              'M_WEAVE_MATRIX_ENDPOINT_MISMATCH',
            ),
      ),
    );
    expect(bridge.memberActivations, isEmpty);
  });

  test(
    'rotating the member bearer keeps the device and encrypted store',
    () async {
      final current = coordinator();
      final first = await current.open(synchronize: false);
      final passphrase = store.rawValue(
        '$matrixCryptoStorePassphraseKeyPrefix${first.profileKey}',
      );
      auth.state = AuthState.authenticated(
        buildTestAuthSession(
          accessToken: 'fresh-member-token',
          idToken: _idToken(),
        ),
      );

      final renewed = await current.open(
        synchronize: false,
        allowInteractiveSignIn: false,
      );

      expect(renewed.profileKey, first.profileKey);
      expect(renewed.deviceId, first.deviceId);
      expect(bridge.disposedProfiles, contains(first.profileKey));
      expect(bridge.memberActivations, hasLength(2));
      expect(
        bridge.memberActivations.last['deviceProof'],
        bridge.memberActivations.first['deviceProof'],
      );
      expect(
        bridge.memberActivations.last['accessToken'],
        'fresh-member-token',
      );
      expect(
        store.rawValue(
          '$matrixCryptoStorePassphraseKeyPrefix${first.profileKey}',
        ),
        passphrase,
      );
    },
  );

  test(
    'loss of current organization access drops the live Matrix client',
    () async {
      final current = coordinator();
      final first = await current.open(synchronize: false);
      access.allowed = false;

      await expectLater(
        current.open(synchronize: false),
        throwsA(isA<ChatFailure>()),
      );
      expect(bridge.disposedProfiles, contains(first.profileKey));
      expect(bridge.memberActivations, hasLength(1));
    },
  );

  test(
    'failed binding persistence disposes the native member client',
    () async {
      store = _FailingBindingStore();
      final current = coordinator();

      await expectLater(
        current.open(synchronize: false),
        throwsA(isA<StateError>()),
      );

      expect(bridge.memberActivations, hasLength(1));
      expect(
        bridge.disposedProfiles,
        contains(bridge.memberActivations.single['profileKey']),
      );
      expect(store.rawValue(matrixOAuthCurrentBindingKey), isNull);
    },
  );

  test('a changed manifest endpoint drops live Matrix access', () async {
    final current = coordinator();
    final first = await current.open(synchronize: false);
    access.advertisedMatrixUrl = Uri.parse('https://other.weave.test');

    await expectLater(
      current.open(synchronize: false),
      throwsA(
        isA<ChatFailure>()
            .having(
              (failure) => failure.type,
              'failure category',
              ChatFailureType.configuration,
            )
            .having(
              (failure) => failure.message,
              'support-safe diagnostic code',
              'M_WEAVE_MATRIX_ENDPOINT_MISMATCH',
            ),
      ),
    );
    expect(bridge.disposedProfiles, contains(first.profileKey));
    expect(bridge.memberActivations, hasLength(1));
  });

  test('failed member session restore drops the prior native bearer', () async {
    final current = coordinator();
    final first = await current.open(synchronize: false);
    final passphraseKey =
        '$matrixCryptoStorePassphraseKeyPrefix${first.profileKey}';
    final passphrase = store.rawValue(passphraseKey);
    auth.failRestore = true;

    await expectLater(
      current.open(synchronize: false),
      throwsA(isA<StateError>()),
    );

    expect(bridge.disposedProfiles, contains(first.profileKey));
    expect(store.rawValue(passphraseKey), passphrase);
  });

  test('invalid member subject drops the prior native bearer', () async {
    final current = coordinator();
    final first = await current.open(synchronize: false);
    final passphraseKey =
        '$matrixCryptoStorePassphraseKeyPrefix${first.profileKey}';
    final passphrase = store.rawValue(passphraseKey);
    auth.state = AuthState.authenticated(
      buildTestAuthSession(idToken: 'invalid-id-token'),
    );

    await expectLater(
      current.open(synchronize: false),
      throwsA(isA<ChatFailure>()),
    );

    expect(bridge.disposedProfiles, contains(first.profileKey));
    expect(store.rawValue(passphraseKey), passphrase);
  });

  test(
    'a changed organization cannot reuse the previous crypto store',
    () async {
      final current = coordinator();
      final first = await current.open(synchronize: false);
      access.organizationId = 'org-two';

      await expectLater(
        current.open(synchronize: false),
        throwsA(isA<ChatFailure>()),
      );
      expect(bridge.disposedProfiles, contains(first.profileKey));
      expect(bridge.memberActivations, hasLength(1));
    },
  );

  test('account removal clears only the bound device store', () async {
    final current = coordinator();
    final session = await current.open(synchronize: false);
    final passphraseKey =
        '$matrixCryptoStorePassphraseKeyPrefix${session.profileKey}';
    final path = bridge.memberActivations.single['storePath']!;
    await current.endSession();
    expect(logoutRequests, hasLength(1));
    expect(
      logoutRequests.single.url.toString(),
      'https://api.weave.test/_matrix/client/v3/logout',
    );
    expect(logoutRequests.single.method, 'POST');
    expect(
      logoutRequests.single.headers['authorization'],
      'Bearer access-token',
    );
    expect(
      logoutRequests.single.headers['x-weave-matrix-device-id'],
      session.deviceId,
    );
    expect(
      logoutRequests.single.headers['x-weave-matrix-device-proof'],
      isNotEmpty,
    );
    expect(store.rawValue(passphraseKey), isNotNull);
    expect(await Directory(path).exists(), isTrue);

    await current.removeForExplicitAccountRemoval();

    expect(store.rawValue(passphraseKey), isNull);
    expect(
      store.rawValue('matrix_member_device_proof_v1_${session.profileKey}'),
      isNull,
    );
    expect(store.rawValue(matrixOAuthCurrentBindingKey), isNull);
    expect(await Directory(path).exists(), isFalse);
  });

  test('logout before Chat opens revokes the current member session', () async {
    await coordinator().endSession();

    expect(store.rawValue(matrixOAuthCurrentBindingKey), isNull);
    expect(bridge.memberActivations, isEmpty);
    expect(logoutRequests, hasLength(1));
    final request = logoutRequests.single;
    expect(request.method, 'POST');
    expect(
      request.url.toString(),
      'https://api.weave.test/_matrix/client/v3/logout',
    );
    expect(request.headers['authorization'], 'Bearer access-token');
    expect(request.headers.containsKey('x-weave-matrix-device-id'), isFalse);
    expect(request.headers.containsKey('x-weave-matrix-device-proof'), isFalse);
  });

  test('logout without a member session makes no Matrix request', () async {
    auth.state = const AuthState.signedOut();

    await coordinator().endSession();

    expect(logoutRequests, isEmpty);
    expect(bridge.memberActivations, isEmpty);
  });

  test('unbound logout refuses an insecure configured Matrix origin', () async {
    configuration.configuration = buildTestConfiguration(
      matrixHomeserverUrl: 'http://api.weave.test',
    );

    await expectLater(
      coordinator().endSession(),
      throwsA(
        isA<ChatFailure>().having(
          (failure) => failure.type,
          'type',
          ChatFailureType.configuration,
        ),
      ),
    );
    expect(logoutRequests, isEmpty);
  });

  test('unbound logout reports remote revocation failure', () async {
    matrixHttpClient = http_testing.MockClient((request) async {
      logoutRequests.add(request);
      return http.Response('{}', 503);
    });

    await expectLater(
      coordinator().endSession(),
      throwsA(
        isA<ChatFailure>()
            .having((failure) => failure.type, 'type', ChatFailureType.protocol)
            .having((failure) => failure.cause, 'status', 503),
      ),
    );
    expect(logoutRequests, hasLength(1));
  });

  test(
    'failed remote logout clears the local bearer and reports failure',
    () async {
      matrixHttpClient = http_testing.MockClient(
        (request) async => http.Response('{}', 503),
      );
      final current = coordinator();
      final session = await current.open(synchronize: false);

      await expectLater(
        current.endSession(),
        throwsA(
          isA<ChatFailure>().having(
            (failure) => failure.type,
            'type',
            ChatFailureType.protocol,
          ),
        ),
      );
      expect(bridge.disposedProfiles, contains(session.profileKey));
    },
  );
}
