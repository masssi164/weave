import 'package:flutter_test/flutter_test.dart';
import 'package:weave/features/auth/data/dtos/auth_session_dto.dart';
import 'package:weave/features/auth/data/repositories/oidc_auth_session_repository.dart';
import 'package:weave/features/auth/data/services/oidc_client.dart';
import 'package:weave/features/auth/domain/entities/auth_configuration.dart';
import 'package:weave/features/auth/domain/entities/auth_failure.dart';
import 'package:weave/features/auth/domain/entities/auth_state.dart';

import '../../../../helpers/auth_test_data.dart';
import '../../../../helpers/in_memory_stores.dart';

class _FakeOidcClient implements OidcClient {
  Future<OidcTokenBundle> Function(AuthConfiguration configuration)?
  authorizeHandler;
  Future<OidcTokenBundle> Function(
    AuthConfiguration configuration,
    String refreshToken,
  )?
  refreshHandler;
  Future<void> Function(AuthConfiguration configuration, String idTokenHint)?
  endSessionHandler;

  @override
  Future<OidcTokenBundle> authorizeAndExchangeCode(
    AuthConfiguration configuration,
  ) {
    final handler = authorizeHandler;
    if (handler == null) {
      throw StateError('authorizeHandler was not configured.');
    }

    return handler(configuration);
  }

  @override
  Future<OidcTokenBundle> refresh(
    AuthConfiguration configuration, {
    required String refreshToken,
  }) {
    final handler = refreshHandler;
    if (handler == null) {
      throw StateError('refreshHandler was not configured.');
    }

    return handler(configuration, refreshToken);
  }

  @override
  Future<void> endSession(
    AuthConfiguration configuration, {
    required String idTokenHint,
  }) {
    final handler = endSessionHandler;
    if (handler == null) {
      return Future.value();
    }

    return handler(configuration, idTokenHint);
  }
}

void main() {
  group('OidcAuthSessionRepository', () {
    late InMemorySecureStore secureStore;
    late _FakeOidcClient oidcClient;
    late OidcAuthSessionRepository repository;
    final configuration = AuthConfiguration(
      issuer: Uri.parse('https://auth.home.internal'),
      clientId: 'weave-app',
    );

    setUp(() {
      secureStore = InMemorySecureStore();
      oidcClient = _FakeOidcClient();
      repository = OidcAuthSessionRepository(
        secureStore: secureStore,
        oidcClient: oidcClient,
      );
    });

    test('signIn persists the session in secure storage', () async {
      oidcClient.authorizeHandler = (configuration) async {
        return const OidcTokenBundle(
          accessToken: 'access-1',
          refreshToken: 'refresh-1',
          idToken: 'id-1',
          expiresAt: null,
          tokenType: 'Bearer',
          scopes: ['openid', 'offline_access'],
        );
      };

      final state = await repository.signIn(configuration);

      expect(state.status, AuthStatus.authenticated);
      expect(secureStore.rawValue(authSessionStorageKey), isNotNull);
      expect(secureStore.rawValue(authSessionStorageKey), contains('access-1'));
    });

    test('restoreSession clears mismatched issuer or client ids', () async {
      final mismatchedSession = buildTestAuthSession(clientId: 'other-client');
      await secureStore.write(
        authSessionStorageKey,
        AuthSessionDto.fromSession(mismatchedSession).encode(),
      );

      final state = await repository.restoreSession(configuration);

      expect(state.status, AuthStatus.signedOut);
      expect(await secureStore.read(authSessionStorageKey), isNull);
    });

    test('restoreSession refreshes expired sessions when possible', () async {
      final expiredSession = buildTestAuthSession(
        expiresAt: DateTime.now().toUtc().subtract(const Duration(minutes: 5)),
      );
      await secureStore.write(
        authSessionStorageKey,
        AuthSessionDto.fromSession(expiredSession).encode(),
      );
      oidcClient.refreshHandler = (configuration, refreshToken) async {
        expect(refreshToken, 'refresh-token');
        return OidcTokenBundle(
          accessToken: 'access-2',
          refreshToken: refreshToken,
          idToken: 'id-2',
          expiresAt: DateTime.now().toUtc().add(const Duration(hours: 1)),
          tokenType: 'Bearer',
          scopes: const ['openid', 'offline_access'],
        );
      };

      final state = await repository.restoreSession(configuration);

      expect(state.status, AuthStatus.authenticated);
      expect(state.session?.accessToken, 'access-2');
      expect(
        await secureStore.read(authSessionStorageKey),
        contains('access-2'),
      );
    });

    test('signOut clears local tokens even when end-session fails', () async {
      await secureStore.write(
        authSessionStorageKey,
        AuthSessionDto.fromSession(buildTestAuthSession()).encode(),
      );
      oidcClient.endSessionHandler = (configuration, idTokenHint) async {
        throw const AuthFailure.protocol('End-session endpoint unavailable.');
      };

      await repository.signOut(configuration);

      expect(await secureStore.read(authSessionStorageKey), isNull);
    });
  });
}
