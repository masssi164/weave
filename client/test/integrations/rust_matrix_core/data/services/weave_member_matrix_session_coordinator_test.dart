import 'dart:async';
import 'dart:convert';
import 'dart:io';
import 'dart:math';

import 'package:flutter_test/flutter_test.dart';
import 'package:flutter/foundation.dart';
import 'package:weave/features/chat/data/repositories/native_matrix_chat_repository.dart';
import 'package:weave/integrations/rust_matrix_core/data/services/rust_matrix_core_bridge.dart';
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
  Future<void> Function(int call)? onLoad;
  int loads = 0;

  @override
  Future<ServerConfiguration?> loadConfiguration() async {
    loads++;
    await onLoad?.call(loads);
    return configuration;
  }

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
  final restoredBindings = <({Uri issuer, String clientId})>[];

  @override
  Future<AuthState> restoreSession(AuthConfiguration configuration) async {
    restoredBindings.add((
      issuer: configuration.issuer,
      clientId: configuration.clientId,
    ));
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
  Future<void> Function(int call)? onAdmission;
  int calls = 0;
  final bearers = <String>[];
  final admissionBindings = <({Uri api, Uri issuer, String subject})>[];

  @override
  Future<MatrixSessionAccess> authorize({
    required Uri userApiBaseUrl,
    required String weaveAccessToken,
    required String expectedSubject,
    required Uri expectedIssuer,
  }) async {
    calls++;
    bearers.add(weaveAccessToken);
    admissionBindings.add((
      api: userApiBaseUrl,
      issuer: expectedIssuer,
      subject: expectedSubject,
    ));
    await onAdmission?.call(calls);
    if (!allowed) {
      throw const ChatFailure.sessionRequired('M_WEAVE_MATRIX_ACCESS_DENIED');
    }
    return MatrixSessionAccess(
      organizationId: organizationId,
      subject: expectedSubject,
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

class _StageFailureBridge extends FakeRustMatrixCoreBridge {
  _StageFailureBridge(this.atSync);
  final bool atSync;
  @override
  Future<RustMatrixOAuthIdentity> activateMemberSession({
    required String profileKey,
    required String homeserverUrl,
    required String userId,
    required String deviceId,
    required String accessToken,
    required String deviceProof,
    required String storePath,
    required String storePassphrase,
  }) async {
    if (!atSync) {
      throw const RustMatrixCoreBridgeException(
        'M_WEAVE_MATRIX_TRANSPORT_CONNECTION',
      );
    }
    return super.activateMemberSession(
      profileKey: profileKey,
      homeserverUrl: homeserverUrl,
      userId: userId,
      deviceId: deviceId,
      accessToken: accessToken,
      deviceProof: deviceProof,
      storePath: storePath,
      storePassphrase: storePassphrase,
    );
  }

  @override
  Future<void> syncClient({required String profileKey}) async {
    throw const RustMatrixCoreBridgeException(
      'M_WEAVE_MATRIX_TRANSPORT_TIMEOUT',
    );
  }
}

String _idToken({
  String issuer = 'https://auth.home.internal',
  String subject = 'person-1',
}) {
  String encode(Map<String, Object> value) =>
      base64UrlEncode(utf8.encode(jsonEncode(value))).replaceAll('=', '');
  return '${encode({'alg': 'RS256'})}.${encode({'iss': issuer, 'aud': 'weave-app', 'sub': subject})}.signature';
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

  for (final phase in [
    'admission',
    'bindingStorage',
    'nativeActivation',
    'sync',
  ]) {
    test(
      'first conversation load captures real coordinator phase $phase',
      () async {
        if (phase == 'admission') {
          access.onAdmission = (_) async {
            throw const ChatFailure.accessDenied(
              'M_WEAVE_MATRIX_ACCESS_DENIED',
            );
          };
        } else if (phase == 'bindingStorage') {
          await coordinator().open(synchronize: false);
          final key = store.rawValue(matrixOAuthCurrentBindingKey)!;
          await store.write(key, 'private malformed binding');
        } else {
          bridge = _StageFailureBridge(phase == 'sync');
        }
        final original = debugPrint;
        final records = <String>[];
        debugPrint = (String? value, {int? wrapWidth}) =>
            records.add(value ?? '');
        addTearDown(() => debugPrint = original);
        final repository = NativeMatrixChatRepository(
          matrixCryptoSessionCoordinator: coordinator(),
          rustMatrixCoreBridge: bridge,
        );
        await expectLater(
          repository.loadConversations(),
          throwsA(isA<ChatFailure>()),
        );
        expect(
          records.single,
          contains('operation=conversations stage=$phase '),
        );
        expect(records.single, contains('supportSafe=true'));
        expect(records.join(), isNot(contains('private')));
        expect(records.join(), isNot(contains('access-token')));
        expect(records.join(), isNot(contains('@acct_')));
        if (phase == 'bindingStorage') {
          final key = store.rawValue(matrixOAuthCurrentBindingKey)!;
          expect(store.rawValue(key), 'private malformed binding');
        }
      },
    );
  }

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

  test(
    'member storage scope survives token refresh, coordinator restart and device replacement',
    () async {
      final first = await coordinator().open(synchronize: false);
      expect(first.memberStorageScope, matches(RegExp(r'^[0-9a-f]{64}$')));
      auth.state = AuthState.authenticated(
        buildTestAuthSession(accessToken: 'rotated-token', idToken: _idToken()),
      );
      final refreshed = await coordinator().open(synchronize: false);
      expect(refreshed.memberStorageScope, first.memberStorageScope);
      await store.write(
        matrixDeviceIdentityStorageKey,
        'WEAVE111111111111111111111111111111111111',
      );
      final newDevice = await coordinator().open(synchronize: false);
      expect(newDevice.deviceId, isNot(first.deviceId));
      expect(newDevice.memberStorageScope, first.memberStorageScope);
    },
  );

  test(
    'member storage scope distinguishes verified issuer, canonical organization and subject tuples',
    () async {
      Future<String?> scope(
        String issuer,
        String organization,
        String subject,
      ) async {
        store = InMemorySecureStore();
        configuration.configuration = buildTestConfiguration(
          issuerUrl: issuer,
          matrixHomeserverUrl: 'https://api.weave.test',
        );
        auth.state = AuthState.authenticated(
          buildTestAuthSession(
            issuer: issuer,
            idToken: _idToken(issuer: issuer, subject: subject),
          ),
        );
        access.organizationId = organization;
        return (await coordinator().open(
          synchronize: false,
        )).memberStorageScope;
      }

      final scopes = [
        await scope('https://auth.home.internal', 'org-one', 'person-1'),
        await scope('https://other.home.internal', 'org-one', 'person-1'),
        await scope('https://auth.home.internal', 'org-two', 'person-1'),
        await scope('https://auth.home.internal', 'org-one', 'person-2'),
        await scope('https://auth.home.internal', 'a|b', 'c'),
        await scope('https://auth.home.internal', 'a', 'b|c'),
      ];
      expect(scopes.toSet(), hasLength(scopes.length));
      expect(scopes, everyElement(matches(RegExp(r'^[0-9a-f]{64}$'))));
    },
  );

  test('does not include a local HTTPS port in the Matrix user ID', () async {
    configuration.configuration = buildTestConfiguration(
      matrixHomeserverUrl: 'https://api.weave.test:44443',
    );

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

  for (final reuse in [false, true]) {
    for (final offset in (reuse ? [1, 2] : [1, 2, 4])) {
      test(
        'refresh during admission preserves crypto; reuse=$reuse offset=$offset',
        () async {
          final current = coordinator();
          final first = reuse ? await current.open(synchronize: false) : null;
          final previousActivation = bridge.memberActivations.firstOrNull;
          final rotateAt = access.calls + offset;
          access.onAdmission = (call) async {
            if (call == rotateAt) {
              auth.state = AuthState.authenticated(
                buildTestAuthSession(
                  accessToken: 'refreshed-during-admission',
                  idToken: _idToken(),
                ),
              );
            }
          };

          final renewed = await current.open(synchronize: false);

          expect(
            bridge.memberActivations.last['accessToken'],
            'refreshed-during-admission',
          );
          expect(
            access.bearers
                .where((value) => value == 'refreshed-during-admission')
                .length,
            greaterThanOrEqualTo(4),
          );
          expect(
            bridge.memberActivations,
            hasLength(reuse || offset == 4 ? 2 : 1),
          );
          if (first != null) {
            expect(renewed.profileKey, first.profileKey);
            expect(renewed.deviceId, first.deviceId);
            expect(renewed.memberStorageScope, first.memberStorageScope);
          }
          final initial = previousActivation ?? bridge.memberActivations.first;
          for (final key in [
            'profileKey',
            'deviceId',
            'deviceProof',
            'storePath',
            'storePassphrase',
          ]) {
            expect(
              bridge.memberActivations.last[key],
              initial[key],
              reason: key,
            );
          }
          expect(
            store.rawValue(
              '$matrixCryptoStorePassphraseKeyPrefix${renewed.profileKey}',
            ),
            initial['storePassphrase'],
          );
          if (reuse || offset == 4) {
            expect(bridge.disposedProfiles, contains(renewed.profileKey));
          }
        },
      );
    }
  }

  test(
    'a second bearer rotation fails closed without an admission loop',
    () async {
      access.onAdmission = (call) async {
        auth.state = AuthState.authenticated(
          buildTestAuthSession(
            accessToken: 'rotation-$call',
            idToken: _idToken(),
          ),
        );
      };
      await expectLater(
        coordinator().open(synchronize: false),
        throwsA(
          isA<ChatFailure>().having(
            (failure) => failure.message,
            'bounded rotation rejection',
            'M_WEAVE_MATRIX_SESSION_CHANGED',
          ),
        ),
      );
      expect(access.calls, 2);
      expect(bridge.memberActivations, isEmpty);
      expect(store.rawValue(matrixOAuthCurrentBindingKey), isNull);
    },
  );

  for (final reuse in [false, true]) {
    for (final change in [
      'subject',
      'organization',
      'endpoint',
      'api',
      'issuer',
      'client',
      'clear',
      'logout',
      'denial',
    ]) {
      test('refresh cannot hide $change; reused client=$reuse', () async {
        final current = coordinator();
        final first = reuse ? await current.open(synchronize: false) : null;
        final binding = store.rawValue(matrixOAuthCurrentBindingKey);
        final start = access.calls;
        final expectedBinding = (
          api: configuration.configuration!.serviceEndpoints.backendApiBaseUrl,
          issuer: configuration.configuration!.oidcIssuerUrl,
          subject: 'person-1',
        );
        access.onAdmission = (call) async {
          if (call == start + 1) {
            auth.state = AuthState.authenticated(
              buildTestAuthSession(accessToken: 'rotated', idToken: _idToken()),
            );
          }
          if (call == start + 2) {
            switch (change) {
              case 'subject':
                auth.state = AuthState.authenticated(
                  buildTestAuthSession(
                    accessToken: 'other-person',
                    idToken: _idToken(subject: 'person-two'),
                  ),
                );
              case 'organization':
                access.organizationId = 'other-organization';
              case 'endpoint':
                configuration.configuration = buildTestConfiguration(
                  matrixHomeserverUrl: 'https://other.weave.test',
                );
              case 'api':
                configuration.configuration = buildTestConfiguration(
                  backendApiBaseUrl: 'https://other.weave.test/api',
                );
              case 'issuer':
                configuration.configuration = buildTestConfiguration(
                  issuerUrl: 'https://other.weave.test',
                );
              case 'client':
                configuration.configuration = buildTestConfiguration(
                  clientId: 'other-client',
                );
              case 'clear':
                configuration.configuration = null;
              case 'logout':
                auth.state = const AuthState.signedOut();
              case 'denial':
                access.allowed = false;
            }
          }
        };
        await expectLater(
          current.open(synchronize: false),
          throwsA(
            isA<ChatFailure>().having(
              (failure) => failure.message,
              'current binding or grant rejection',
              change == 'denial'
                  ? 'M_WEAVE_MATRIX_ACCESS_DENIED'
                  : 'M_WEAVE_MATRIX_SESSION_CHANGED',
            ),
          ),
        );
        expect(access.calls, start + 2);
        expect(
          access.admissionBindings.skip(start),
          everyElement(expectedBinding),
        );
        expect(bridge.memberActivations, hasLength(reuse ? 1 : 0));
        expect(store.rawValue(matrixOAuthCurrentBindingKey), binding);
        if (first != null) {
          expect(bridge.disposedProfiles, contains(first.profileKey));
        }
      });
    }
  }

  for (final change in [
    'issuer',
    'client',
    'api',
    'matrix',
    'subject',
    'clear',
  ]) {
    test(
      'retry stays bound before restore or admission when $change changes',
      () async {
        final initial = configuration.configuration!;
        access.onAdmission = (call) async {
          if (call == 1) {
            auth.state = AuthState.authenticated(
              buildTestAuthSession(accessToken: 'rotated', idToken: _idToken()),
            );
          }
        };
        configuration.onLoad = (call) async {
          if (call != 3) return;
          switch (change) {
            case 'issuer':
              configuration.configuration = buildTestConfiguration(
                issuerUrl: 'https://other.weave.test',
              );
            case 'client':
              configuration.configuration = buildTestConfiguration(
                clientId: 'other-client',
              );
            case 'api':
              configuration.configuration = buildTestConfiguration(
                backendApiBaseUrl: 'https://other.weave.test/api',
              );
            case 'matrix':
              configuration.configuration = buildTestConfiguration(
                matrixHomeserverUrl: 'https://other.weave.test',
              );
            case 'subject':
              auth.state = AuthState.authenticated(
                buildTestAuthSession(
                  accessToken: 'other-person',
                  idToken: _idToken(subject: 'other-person'),
                ),
              );
            case 'clear':
              configuration.configuration = null;
          }
        };
        await expectLater(
          coordinator().open(synchronize: false),
          throwsA(isA<ChatFailure>()),
        );
        expect(access.calls, 1);
        expect(auth.restoredBindings, hasLength(change == 'subject' ? 3 : 2));
        expect(
          auth.restoredBindings,
          everyElement((
            issuer: initial.oidcIssuerUrl,
            clientId: initial.oidcClientRegistration.clientId,
          )),
        );
        expect(bridge.memberActivations, isEmpty);
        expect(store.rawValue(matrixOAuthCurrentBindingKey), isNull);
      },
    );
  }

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

  for (final reuse in [false, true]) {
    for (final change in ['endpoint', 'clear-profile', 'logout']) {
      test(
        change == 'endpoint'
            ? 'a changed manifest endpoint drops live Matrix access (${reuse ? 'reused' : 'new'} client during admission)'
            : 'rejects ${reuse ? 'reused' : 'new'} client when $change occurs during admission',
        () async {
          final current = coordinator();
          final first = reuse ? await current.open(synchronize: false) : null;
          final held = Completer<void>();
          final release = Completer<void>();
          final holdAt = access.calls + 2;
          access.onAdmission = (call) async {
            if (call == holdAt) {
              held.complete();
              await release.future;
            }
          };
          final opening = current.open(synchronize: false);
          final rejection = expectLater(
            opening,
            throwsA(
              isA<ChatFailure>().having(
                (failure) => failure.message,
                'current binding rejection',
                'M_WEAVE_MATRIX_SESSION_CHANGED',
              ),
            ),
          );
          await held.future;
          if (change == 'endpoint') {
            configuration.configuration = buildTestConfiguration(
              matrixHomeserverUrl: 'https://other.weave.test',
            );
          } else if (change == 'clear-profile') {
            configuration.configuration = null;
          } else {
            auth.state = const AuthState.signedOut();
          }
          release.complete();
          await rejection;
          if (first != null) {
            expect(bridge.disposedProfiles, contains(first.profileKey));
          }
          expect(bridge.memberActivations, hasLength(reuse ? 1 : 0));
        },
      );
    }
  }

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

  test(
    'no-Chat logout does not restore a session, contact Matrix or erase crypto',
    () async {
      configuration.configuration = buildTestConfiguration(
        matrixHomeserverUrl: null,
        domains: const ['files'],
      );
      await store.write('retained-crypto-proof', 'local-private-state');
      await coordinator().endSession();
      expect(auth.restoredBindings, isEmpty);
      expect(logoutRequests, isEmpty);
      expect(bridge.memberActivations, isEmpty);
      expect(store.rawValue('retained-crypto-proof'), 'local-private-state');
    },
  );

  test(
    'a retained Chat binding fails closed after switching to a no-Chat server',
    () async {
      final current = coordinator();
      final session = await current.open(synchronize: false);
      final proofKey = 'matrix_member_device_proof_v1_${session.profileKey}';
      final proof = store.rawValue(proofKey);
      configuration.configuration = buildTestConfiguration(
        matrixHomeserverUrl: null,
        domains: const ['files'],
      );
      await expectLater(current.endSession(), throwsA(isA<ChatFailure>()));
      expect(logoutRequests, isEmpty);
      expect(store.rawValue(proofKey), proof);
      // The retained locator/proof are crypto recovery metadata, not an active bearer.
      expect(store.rawValue(matrixOAuthCurrentBindingKey), isNotNull);
      expect(bridge.disposedProfiles, contains(session.profileKey));
    },
  );

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
