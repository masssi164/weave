import 'dart:async';
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
import 'package:weave/integrations/rust_matrix_core/data/services/matrix_oauth_browser.dart';
import 'package:weave/integrations/rust_matrix_core/data/services/matrix_session_access.dart';

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
  bool failRestore = false;

  @override
  Future<void> clearLocalSession() async {}

  @override
  Future<AuthState> refreshSession(AuthConfiguration configuration) async =>
      state;

  @override
  Future<AuthState> restoreSession(AuthConfiguration configuration) async {
    if (failRestore) throw StateError('Weave API unavailable');
    return state;
  }

  @override
  Future<AuthState> signIn(AuthConfiguration configuration) async => state;

  @override
  Future<void> signOut(AuthConfiguration configuration) async {}
}

class _ControlledInitializationBridge extends FakeRustMatrixCoreBridge {
  final initializationStarted = Completer<void>();
  final allowInitialization = Completer<void>();

  @override
  Future<void> activateOAuth({
    required String loginKey,
    required String profileKey,
    required String storePath,
    required String storePassphrase,
  }) async {
    initializationStarted.complete();
    await allowInitialization.future;
    await super.activateOAuth(
      loginKey: loginKey,
      profileKey: profileKey,
      storePath: storePath,
      storePassphrase: storePassphrase,
    );
  }
}

class _FakeMatrixOAuthBrowser implements MatrixOAuthBrowser {
  final List<Uri> opened = <Uri>[];
  bool cancelled = false;

  @override
  Future<Uri> authorize(
    Uri authorizationUrl, {
    required String state,
    required bool allowInsecureAuthorization,
  }) async {
    opened.add(authorizationUrl);
    if (cancelled) {
      throw const ChatFailure.cancelled('Matrix sign-in cancelled.');
    }
    return Uri.parse('$matrixOAuthRedirectUri?code=matrix-code&state=$state');
  }
}

class _FakeMatrixSessionAccess implements MatrixSessionAccessPort {
  String organizationId = 'org-one';
  bool allowed = true;
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
      throw const ChatFailure.sessionRequired('Chat access was revoked.');
    }
    return MatrixSessionAccess(
      organizationId: organizationId,
      subject: expectedSubject,
    );
  }
}

String _idToken({
  String issuer = 'https://auth.home.internal',
  String audience = 'weave-app',
  String subject = 'person-1',
}) {
  String part(Map<String, Object> claims) =>
      base64UrlEncode(utf8.encode(jsonEncode(claims))).replaceAll('=', '');
  return '${part(<String, Object>{'alg': 'RS256'})}.${part(<String, Object>{'iss': issuer, 'aud': audience, 'sub': subject})}.signature';
}

void main() {
  late Directory storeRoot;
  late InMemorySecureStore secureStore;
  late _ConfigurationRepository configurationRepository;
  late _AuthRepository authRepository;
  late FakeRustMatrixCoreBridge bridge;
  late _FakeMatrixOAuthBrowser browser;
  late _FakeMatrixSessionAccess access;

  MatrixCryptoSessionCoordinator buildCoordinator({required int randomSeed}) {
    return MatrixCryptoSessionCoordinator(
      serverConfigurationRepository: configurationRepository,
      authSessionRepository: authRepository,
      matrixDeviceIdentityRepository: MatrixDeviceIdentityRepository(
        secureStore: secureStore,
        random: Random(randomSeed),
      ),
      matrixSessionAccess: access,
      secureStore: secureStore,
      rustMatrixCoreBridge: bridge,
      oauthBrowser: browser,
      storeRootLoader: () async => storeRoot,
      random: Random(randomSeed),
    );
  }

  setUp(() async {
    storeRoot = await Directory.systemTemp.createTemp('weave-matrix-e2ee-');
    secureStore = InMemorySecureStore();
    configurationRepository = _ConfigurationRepository(
      buildTestConfiguration(matrixHomeserverUrl: 'https://api.weave.test'),
    );
    authRepository = _AuthRepository();
    authRepository.state = AuthState.authenticated(
      buildTestAuthSession(idToken: _idToken()),
    );
    bridge = FakeRustMatrixCoreBridge();
    bridge.oauthUserId = '@person-1:api.weave.test';
    browser = _FakeMatrixOAuthBrowser();
    access = _FakeMatrixSessionAccess();
  });

  tearDown(() async {
    if (await storeRoot.exists()) {
      await storeRoot.delete(recursive: true);
    }
  });

  test(
    'ordinary Chat access establishes Matrix through the Weave SSO context',
    () async {
      final coordinator = buildCoordinator(randomSeed: 1);

      final session = await coordinator.open();

      expect(session.userId, '@person-1:api.weave.test');
      expect(bridge.oauthStarts, hasLength(1));
      expect(browser.opened, hasLength(1));
      expect(access.calls, 1);
    },
  );

  test(
    'app relaunch restores the same profile, device, and crypto store',
    () async {
      // MATRIX_E2EE_IPHONE_RELAUNCH
      final first = buildCoordinator(randomSeed: 1);

      final firstSession = await first.open(allowInteractiveSignIn: true);
      final firstInitialization = bridge.oauthActivations.single;
      await first.disposePreservingCryptoState();
      final second = buildCoordinator(randomSeed: 999);
      final secondSession = await second.open();
      final secondInitialization = bridge.oauthRestores.single;

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
      expect(bridge.oauthStarts, hasLength(1));
      expect(browser.opened, hasLength(1));
    },
  );

  test(
    'Weave OIDC token refresh never rebinds Matrix with a Weave bearer',
    () async {
      final coordinator = buildCoordinator(randomSeed: 1);

      final first = await coordinator.open(
        synchronize: false,
        allowInteractiveSignIn: true,
      );
      authRepository.state = AuthState.authenticated(
        buildTestAuthSession(
          accessToken: 'refreshed-access-token',
          idToken: _idToken(),
        ),
      );
      final second = await coordinator.open(synchronize: false);

      expect(second.profileKey, first.profileKey);
      expect(bridge.oauthActivations, hasLength(1));
      expect(bridge.oauthRestores, isEmpty);
    },
  );

  test(
    'expired Matrix grant recovers automatically for the same account',
    () async {
      final coordinator = buildCoordinator(randomSeed: 1);
      final first = await coordinator.open(allowInteractiveSignIn: true);
      final bindingKey = await secureStore.read(matrixOAuthCurrentBindingKey);
      final passphrase = await secureStore.read(
        '$matrixCryptoStorePassphraseKeyPrefix${first.profileKey}',
      );
      final storePath = bridge.oauthActivations.single['storePath']!;
      final tokenFile = File('$storePath/weave-matrix-oauth-session.v1');
      await tokenFile.writeAsString('encrypted Matrix session test fixture');

      bridge.expiredSyncsRemaining = 2;
      final reconnected = await coordinator.open();
      expect(browser.opened, hasLength(2));
      expect(await tokenFile.readAsString(), isNotEmpty);

      expect(reconnected.profileKey, first.profileKey);
      expect(bridge.oauthStarts, hasLength(2));
      expect(browser.opened, hasLength(2));
      expect(bridge.oauthActivations, hasLength(2));
      expect(bridge.oauthRestores, hasLength(1));
      expect(bridge.disposedProfiles, contains(first.profileKey));
      expect(bridge.oauthActivations.last['storePassphrase'], passphrase);
      expect(await secureStore.read(matrixOAuthCurrentBindingKey), bindingKey);
      expect(await tokenFile.readAsString(), isNotEmpty);
    },
  );

  test(
    'reconnect rejects a different Matrix account without replacing keys',
    () async {
      final coordinator = buildCoordinator(randomSeed: 1);
      final first = await coordinator.open(allowInteractiveSignIn: true);
      final bindingKey = await secureStore.read(matrixOAuthCurrentBindingKey);
      final passphrase = await secureStore.read(
        '$matrixCryptoStorePassphraseKeyPrefix${first.profileKey}',
      );
      bridge.expiredSyncsRemaining = 2;
      bridge.oauthUserId = '@different:matrix.test';

      await expectLater(
        coordinator.open(allowInteractiveSignIn: true),
        throwsA(
          isA<ChatFailure>().having(
            (failure) => failure.type,
            'type',
            ChatFailureType.protocol,
          ),
        ),
      );

      expect(bridge.oauthActivations, hasLength(1));
      expect(bridge.oauthAborts, hasLength(1));
      expect(await secureStore.read(matrixOAuthCurrentBindingKey), bindingKey);
      expect(
        await secureStore.read(
          '$matrixCryptoStorePassphraseKeyPrefix${first.profileKey}',
        ),
        passphrase,
      );
      expect(
        await Directory(bridge.oauthActivations.single['storePath']!).exists(),
        isTrue,
      );
    },
  );

  test(
    'Weave API outage and rejected refresh leave Matrix OAuth and E2EE intact',
    () async {
      final first = buildCoordinator(randomSeed: 1);
      final session = await first.open(
        synchronize: false,
        allowInteractiveSignIn: true,
      );
      final bindingKey = await secureStore.read(matrixOAuthCurrentBindingKey);
      final passphraseKey =
          '$matrixCryptoStorePassphraseKeyPrefix${session.profileKey}';
      final passphrase = await secureStore.read(passphraseKey);
      final storePath = bridge.oauthActivations.single['storePath']!;
      final tokenFile = File('$storePath/weave-matrix-oauth-session.v1');
      await tokenFile.writeAsString('encrypted Matrix session test fixture');
      await first.disposePreservingCryptoState();

      authRepository.failRestore = true;
      await expectLater(
        buildCoordinator(randomSeed: 2).open(synchronize: false),
        throwsStateError,
      );
      authRepository.failRestore = false;
      authRepository.state = const AuthState.signedOut();
      await expectLater(
        buildCoordinator(randomSeed: 3).open(synchronize: false),
        throwsA(
          isA<ChatFailure>().having(
            (failure) => failure.type,
            'type',
            ChatFailureType.sessionRequired,
          ),
        ),
      );

      expect(await secureStore.read(matrixOAuthCurrentBindingKey), bindingKey);
      expect(await secureStore.read(passphraseKey), passphrase);
      expect(await Directory(storePath).exists(), isTrue);
      expect(
        await tokenFile.readAsString(),
        'encrypted Matrix session test fixture',
      );
      expect(bridge.oauthEnds, isEmpty);
      expect(bridge.oauthRestores, isEmpty);

      authRepository.state = AuthState.authenticated(
        buildTestAuthSession(idToken: _idToken()),
      );
      final restored = await buildCoordinator(
        randomSeed: 4,
      ).open(synchronize: false);
      expect(restored.profileKey, session.profileKey);
      expect(bridge.oauthRestores, hasLength(1));
      expect(browser.opened, hasLength(1));
    },
  );

  test(
    'revoked Chat access drops the active client but preserves E2EE',
    () async {
      final coordinator = buildCoordinator(randomSeed: 1);
      final session = await coordinator.open(synchronize: false);
      final bindingKey = await secureStore.read(matrixOAuthCurrentBindingKey);
      final storePath = bridge.oauthActivations.single['storePath']!;

      access.allowed = false;
      await expectLater(
        coordinator.open(synchronize: false),
        throwsA(
          isA<ChatFailure>().having(
            (failure) => failure.type,
            'type',
            ChatFailureType.sessionRequired,
          ),
        ),
      );

      expect(bridge.disposedProfiles, contains(session.profileKey));
      expect(await secureStore.read(matrixOAuthCurrentBindingKey), bindingKey);
      expect(await Directory(storePath).exists(), isTrue);
      expect(browser.opened, hasLength(1));
    },
  );

  test('unauthorized Chat never starts Matrix OAuth', () async {
    access.allowed = false;

    await expectLater(
      buildCoordinator(randomSeed: 1).open(),
      throwsA(isA<ChatFailure>()),
    );

    expect(bridge.oauthStarts, isEmpty);
    expect(browser.opened, isEmpty);
  });

  test(
    'a different organization cannot reuse the saved Matrix session',
    () async {
      final coordinator = buildCoordinator(randomSeed: 1);
      final session = await coordinator.open(synchronize: false);
      final storePath = bridge.oauthActivations.single['storePath']!;

      access.organizationId = 'org-two';
      await expectLater(
        coordinator.open(synchronize: false),
        throwsA(isA<ChatFailure>()),
      );

      expect(bridge.disposedProfiles, contains(session.profileKey));
      expect(await Directory(storePath).exists(), isTrue);
      expect(browser.opened, hasLength(1));
    },
  );

  test(
    'unscoped legacy Matrix binding fails closed without deleting E2EE',
    () async {
      final first = buildCoordinator(randomSeed: 1);
      final session = await first.open(synchronize: false);
      final storePath = bridge.oauthActivations.single['storePath']!;
      final bindingKey = (await secureStore.read(
        matrixOAuthCurrentBindingKey,
      ))!;
      final passphraseKey =
          '$matrixCryptoStorePassphraseKeyPrefix${session.profileKey}';
      final passphrase = await secureStore.read(passphraseKey);
      final raw =
          jsonDecode((await secureStore.read(bindingKey))!)
              as Map<String, dynamic>;
      raw.remove('organizationId');
      await secureStore.write(bindingKey, jsonEncode(raw));
      await first.disposePreservingCryptoState();

      await expectLater(
        buildCoordinator(randomSeed: 2).open(synchronize: false),
        throwsA(
          isA<ChatFailure>().having(
            (failure) => failure.type,
            'type',
            ChatFailureType.storage,
          ),
        ),
      );

      expect(await secureStore.read(bindingKey), jsonEncode(raw));
      expect(await secureStore.read(passphraseKey), passphrase);
      expect(await Directory(storePath).exists(), isTrue);
      expect(bridge.oauthRestores, isEmpty);
      expect(bridge.oauthActivations, hasLength(1));
    },
  );

  test(
    'Matrix user projection follows Rust ASCII-only normalization',
    () async {
      authRepository.state = AuthState.authenticated(
        buildTestAuthSession(idToken: _idToken(subject: 'İX')),
      );
      bridge.oauthUserId = '@x:api.weave.test';

      final session = await buildCoordinator(
        randomSeed: 1,
      ).open(synchronize: false);

      expect(session.userId, '@x:api.weave.test');
      expect(bridge.oauthActivations, hasLength(1));
    },
  );

  test(
    'workspace sign-out revokes Matrix OAuth but retains E2EE material',
    () async {
      final coordinator = buildCoordinator(randomSeed: 1);
      final session = await coordinator.open(
        synchronize: false,
        allowInteractiveSignIn: true,
      );
      final storePath = bridge.oauthActivations.single['storePath']!;
      final passphraseKey =
          '$matrixCryptoStorePassphraseKeyPrefix${session.profileKey}';
      final tokenFile = File('$storePath/weave-matrix-oauth-session.v1');
      await tokenFile.writeAsString('encrypted Matrix session test fixture');

      await coordinator.endSession();

      expect(bridge.oauthEnds.single['profileKey'], session.profileKey);
      expect(await secureStore.read(matrixOAuthCurrentBindingKey), isNull);
      expect(await secureStore.read(passphraseKey), isNotNull);
      expect(await tokenFile.exists(), isFalse);
      expect(await Directory(storePath).exists(), isTrue);
      expect(await secureStore.read(matrixDeviceIdentityStorageKey), isNotNull);
    },
  );

  test(
    'sign-out cannot rebind a preserved Matrix store to another organization',
    () async {
      final coordinator = buildCoordinator(randomSeed: 1);
      final session = await coordinator.open(synchronize: false);
      final storePath = bridge.oauthActivations.single['storePath']!;
      final passphraseKey =
          '$matrixCryptoStorePassphraseKeyPrefix${session.profileKey}';
      final passphrase = await secureStore.read(passphraseKey);

      await coordinator.endSession();
      access.organizationId = 'org-two';

      await expectLater(
        coordinator.open(synchronize: false),
        throwsA(
          isA<ChatFailure>().having(
            (failure) => failure.type,
            'type',
            ChatFailureType.storage,
          ),
        ),
      );
      expect(
        await secureStore.read(
          '$matrixOAuthProfileOrganizationKeyPrefix${session.profileKey}',
        ),
        'org-one',
      );
      expect(await secureStore.read(passphraseKey), passphrase);
      expect(await Directory(storePath).exists(), isTrue);
      expect(bridge.oauthActivations, hasLength(1));
      expect(browser.opened, hasLength(1));
    },
  );

  test(
    'unconfirmed remote revocation is reported after local Matrix clear',
    () async {
      final coordinator = buildCoordinator(randomSeed: 1);
      final session = await coordinator.open(
        synchronize: false,
        allowInteractiveSignIn: true,
      );
      final storePath = bridge.oauthActivations.single['storePath']!;
      final tokenFile = File('$storePath/weave-matrix-oauth-session.v1');
      await tokenFile.writeAsString('encrypted Matrix session test fixture');
      bridge.remoteRevocationConfirmed = false;

      await expectLater(
        coordinator.endSession(),
        throwsA(
          isA<ChatFailure>().having(
            (failure) => failure.type,
            'type',
            ChatFailureType.protocol,
          ),
        ),
      );

      expect(await tokenFile.exists(), isFalse);
      expect(await secureStore.read(matrixOAuthCurrentBindingKey), isNull);
      expect(
        await secureStore.read(
          '$matrixCryptoStorePassphraseKeyPrefix${session.profileKey}',
        ),
        isNotNull,
      );
    },
  );

  test(
    'native Matrix logout error still removes only local OAuth access',
    () async {
      final coordinator = buildCoordinator(randomSeed: 1);
      final session = await coordinator.open(
        synchronize: false,
        allowInteractiveSignIn: true,
      );
      final storePath = bridge.oauthActivations.single['storePath']!;
      final tokenFile = File('$storePath/weave-matrix-oauth-session.v1');
      await tokenFile.writeAsString('encrypted Matrix session test fixture');
      bridge.failOAuthEnd = true;

      await expectLater(coordinator.endSession(), throwsA(isA<ChatFailure>()));

      expect(bridge.disposedProfiles, contains(session.profileKey));
      expect(await tokenFile.exists(), isFalse);
      expect(await secureStore.read(matrixOAuthCurrentBindingKey), isNull);
      expect(await Directory(storePath).exists(), isTrue);
    },
  );

  test('a saved Matrix binding for another account fails closed', () async {
    final first = buildCoordinator(randomSeed: 1);
    await first.open(synchronize: false, allowInteractiveSignIn: true);
    await first.disposePreservingCryptoState();
    final bindingKey = await secureStore.read(matrixOAuthCurrentBindingKey);
    final saved =
        jsonDecode((await secureStore.read(bindingKey!))!)
            as Map<String, dynamic>;
    await secureStore.write(
      bindingKey,
      jsonEncode(<String, dynamic>{...saved, 'userId': '@other:matrix.test'}),
    );

    await expectLater(
      buildCoordinator(
        randomSeed: 2,
      ).open(synchronize: false, allowInteractiveSignIn: true),
      throwsA(
        isA<ChatFailure>().having(
          (failure) => failure.type,
          'type',
          ChatFailureType.storage,
        ),
      ),
    );
    expect(bridge.oauthRestores, isEmpty);
    expect(browser.opened, hasLength(1));
  });

  test(
    'a second Weave account cannot reuse the first Matrix account store',
    () async {
      final coordinator = buildCoordinator(randomSeed: 1);
      final first = await coordinator.open(
        synchronize: false,
        allowInteractiveSignIn: true,
      );
      final firstStorePath = bridge.oauthActivations.single['storePath']!;
      final firstOwnerKey =
          '$matrixOAuthProfileOwnerKeyPrefix${first.profileKey}';
      final firstOwner = await secureStore.read(firstOwnerKey);
      authRepository.state = AuthState.authenticated(
        buildTestAuthSession(idToken: _idToken(subject: 'person-2')),
      );

      await expectLater(
        coordinator.open(synchronize: false, allowInteractiveSignIn: true),
        throwsA(
          isA<ChatFailure>().having(
            (failure) => failure.type,
            'type',
            ChatFailureType.protocol,
          ),
        ),
      );

      expect(bridge.disposedProfiles, contains(first.profileKey));
      expect(bridge.oauthActivations, hasLength(1));
      expect(bridge.oauthAborts, hasLength(1));
      expect(await secureStore.read(firstOwnerKey), firstOwner);
      expect(await Directory(firstStorePath).exists(), isTrue);
    },
  );

  test(
    'a projected ID collision disposes the previous native client',
    () async {
      final coordinator = buildCoordinator(randomSeed: 1);
      final first = await coordinator.open(synchronize: false);
      authRepository.state = AuthState.authenticated(
        buildTestAuthSession(idToken: _idToken(subject: 'PERSON-1')),
      );

      await expectLater(
        coordinator.open(synchronize: false),
        throwsA(
          isA<ChatFailure>().having(
            (failure) => failure.type,
            'type',
            ChatFailureType.storage,
          ),
        ),
      );

      expect(bridge.disposedProfiles, contains(first.profileKey));
      expect(browser.opened, hasLength(1));
    },
  );

  test(
    'dispose waits for an in-flight owner startup before shutdown',
    () async {
      final controlledBridge = _ControlledInitializationBridge();
      controlledBridge.oauthUserId = '@person-1:api.weave.test';
      bridge = controlledBridge;
      final coordinator = buildCoordinator(randomSeed: 1);

      final opening = coordinator.open(
        synchronize: false,
        allowInteractiveSignIn: true,
      );
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
      final coordinator = buildCoordinator(randomSeed: 1);
      final session = await coordinator.open(
        synchronize: false,
        allowInteractiveSignIn: true,
      );
      final storePath = bridge.oauthActivations.single['storePath']!;
      final passphraseKey =
          '$matrixCryptoStorePassphraseKeyPrefix${session.profileKey}';

      await coordinator.removeForExplicitAccountRemoval();

      expect(await secureStore.read(matrixDeviceIdentityStorageKey), isNull);
      expect(await secureStore.read(passphraseKey), isNull);
      expect(
        await secureStore.read(
          '$matrixOAuthProfileOwnerKeyPrefix${session.profileKey}',
        ),
        isNull,
      );
      expect(await Directory(storePath).exists(), isFalse);
    },
  );

  test('fails closed when Matrix OAuth returns a different device', () async {
    // MATRIX_E2EE_CLIENT_FAILS_CLOSED
    bridge.oauthDeviceIdOverride = 'WEAVEOTHERDEVICE000000000000000000000000';
    final coordinator = buildCoordinator(randomSeed: 1);

    await expectLater(
      coordinator.open(allowInteractiveSignIn: true),
      throwsA(
        isA<ChatFailure>().having(
          (failure) => failure.type,
          'type',
          ChatFailureType.protocol,
        ),
      ),
    );
    expect(bridge.oauthActivations, isEmpty);
    expect(bridge.oauthAborts, hasLength(1));
  });

  test(
    'cancelled Matrix browser sign-in never creates a Matrix session',
    () async {
      browser.cancelled = true;
      final coordinator = buildCoordinator(randomSeed: 1);

      await expectLater(
        coordinator.open(allowInteractiveSignIn: true),
        throwsA(
          isA<ChatFailure>().having(
            (failure) => failure.type,
            'type',
            ChatFailureType.cancelled,
          ),
        ),
      );
      expect(bridge.oauthActivations, isEmpty);
      expect(bridge.oauthAborts, hasLength(1));
    },
  );

  test('wrong OIDC issuer or audience never starts Matrix sign-in', () async {
    for (final idToken in <String>[
      _idToken(issuer: 'https://other.example'),
      _idToken(audience: 'other-client'),
    ]) {
      authRepository.state = AuthState.authenticated(
        buildTestAuthSession(idToken: idToken),
      );
      final coordinator = buildCoordinator(randomSeed: 1);
      await expectLater(
        coordinator.open(allowInteractiveSignIn: true),
        throwsA(
          isA<ChatFailure>().having(
            (failure) => failure.type,
            'type',
            ChatFailureType.sessionRequired,
          ),
        ),
      );
    }
    expect(bridge.oauthStarts, isEmpty);
  });

  test('a mismatched Weave session never opens the Matrix browser', () async {
    for (final session in [
      buildTestAuthSession(
        issuer: 'https://other.example',
        idToken: _idToken(),
      ),
      buildTestAuthSession(clientId: 'other-client', idToken: _idToken()),
    ]) {
      authRepository.state = AuthState.authenticated(session);
      await expectLater(
        buildCoordinator(randomSeed: 1).open(allowInteractiveSignIn: true),
        throwsA(isA<ChatFailure>()),
      );
    }
    expect(bridge.oauthStarts, isEmpty);
    expect(browser.opened, isEmpty);
  });

  test('Matrix OAuth callback requires its own redirect and matching state', () {
    final valid = Uri.parse(
      '$matrixOAuthRedirectUri?code=matrix-code&state=expected',
    );
    expect(validateMatrixOAuthRedirect(valid, state: 'expected'), valid);
    for (final invalid in <Uri>[
      Uri.parse('$matrixOAuthRedirectUri?code=matrix-code&state=wrong'),
      Uri.parse(
        '$matrixOAuthRedirectUri?code=matrix-code&state=expected&state=expected',
      ),
      Uri.parse(
        'com.massimotter.weave:/oauthredirect?code=matrix-code&state=expected',
      ),
      Uri.parse(
        'com.massimotter.weave.matrix:/other?code=matrix-code&state=expected',
      ),
      Uri.parse(
        '$matrixOAuthRedirectUri?code=matrix-code&state=expected#fragment',
      ),
    ]) {
      expect(
        () => validateMatrixOAuthRedirect(invalid, state: 'expected'),
        throwsA(isA<ChatFailure>()),
      );
    }
  });

  test('Matrix authorization URL permits HTTP only for an HTTP homeserver', () {
    final secure = Uri.parse(
      'https://mas.matrix.test/authorize?state=expected',
    );
    final local = Uri.parse('http://mas.matrix.test/authorize?state=expected');
    expect(
      validateMatrixOAuthAuthorizationUrl(
        secure,
        state: 'expected',
        allowInsecureAuthorization: false,
      ),
      secure,
    );
    expect(
      () => validateMatrixOAuthAuthorizationUrl(
        local,
        state: 'expected',
        allowInsecureAuthorization: false,
      ),
      throwsA(isA<ChatFailure>()),
    );
    expect(
      validateMatrixOAuthAuthorizationUrl(
        local,
        state: 'expected',
        allowInsecureAuthorization: true,
      ),
      local,
    );
    for (final invalid in [
      Uri.parse('https://user@mas.matrix.test/authorize?state=expected'),
      Uri.parse(
        'https://mas.matrix.test/authorize?state=expected&state=expected',
      ),
      Uri.parse('https://mas.matrix.test/authorize?state=wrong'),
      Uri.parse('https://mas.matrix.test/authorize?state=expected#fragment'),
    ]) {
      expect(
        () => validateMatrixOAuthAuthorizationUrl(
          invalid,
          state: 'expected',
          allowInsecureAuthorization: false,
        ),
        throwsA(isA<ChatFailure>()),
      );
    }
  });
}
