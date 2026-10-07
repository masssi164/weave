import 'dart:convert';
import 'dart:io';
import 'dart:math';

import 'package:flutter_test/flutter_test.dart';
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

  @override
  Future<AuthState> restoreSession(AuthConfiguration configuration) async =>
      state;
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

  WeaveMemberMatrixSessionCoordinator coordinator() =>
      WeaveMemberMatrixSessionCoordinator(
        serverConfigurationRepository: configuration,
        authSessionRepository: auth,
        matrixDeviceIdentityRepository: MatrixDeviceIdentityRepository(
          secureStore: store,
          random: Random(1),
        ),
        matrixSessionAccess: access,
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

      expect(session.userId, '@person-1:api.weave.test');
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
}
