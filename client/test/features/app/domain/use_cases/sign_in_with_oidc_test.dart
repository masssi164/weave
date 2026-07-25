import 'package:flutter_test/flutter_test.dart';
import 'package:weave/features/app/domain/ports/app_auth_port.dart';
import 'package:weave/features/app/domain/ports/identity_session_port.dart';
import 'package:weave/features/app/domain/ports/server_configuration_port.dart';
import 'package:weave/features/app/domain/use_cases/reconcile_identity_session.dart';
import 'package:weave/features/app/domain/use_cases/sign_in_with_oidc.dart';
import 'package:weave/features/auth/domain/entities/auth_configuration.dart';
import 'package:weave/features/auth/domain/entities/auth_failure.dart';
import 'package:weave/features/auth/domain/entities/auth_state.dart';
import 'package:weave/features/server_config/domain/entities/server_configuration.dart';

import '../../../../helpers/auth_test_data.dart';
import '../../../../helpers/server_config_test_data.dart';

class _FakeAppAuthPort implements AppAuthPort {
  int signInCalls = 0;
  int refreshCalls = 0;
  int clearCalls = 0;
  AuthConfiguration? lastSignInConfiguration;
  AuthState signInResult = AuthState.authenticated(buildTestAuthSession());
  AuthState refreshResult = AuthState.authenticated(
    buildTestAuthSession(accessToken: 'refreshed-access-token'),
  );

  @override
  Future<void> clearLocalSession() async {
    clearCalls++;
  }

  @override
  Future<AuthState> restoreSession(AuthConfiguration configuration) async {
    return const AuthState.signedOut();
  }

  @override
  Future<AuthState> refreshSession(AuthConfiguration configuration) async {
    refreshCalls++;
    return refreshResult;
  }

  @override
  Future<AuthState> signIn(AuthConfiguration configuration) async {
    signInCalls++;
    lastSignInConfiguration = configuration;
    return signInResult;
  }

  @override
  Future<void> signOut(AuthConfiguration configuration) async {}
}

class _FakeIdentitySessionPort implements IdentitySessionPort {
  IdentitySessionReconciliation result =
      IdentitySessionReconciliation.unchanged;
  int calls = 0;
  Uri? lastBaseUrl;
  String? lastAccessToken;

  @override
  Future<IdentitySessionReconciliation> reconcile({
    required Uri baseUrl,
    required String accessToken,
  }) async {
    calls++;
    lastBaseUrl = baseUrl;
    lastAccessToken = accessToken;
    return result;
  }
}

class _FakeServerConfigurationPort implements ServerConfigurationPort {
  _FakeServerConfigurationPort({this.configuration});

  final ServerConfiguration? configuration;

  @override
  Future<void> clearConfiguration() async {}

  @override
  Future<ServerConfiguration?> loadConfiguration() async => configuration;
}

SignInWithOidc _buildUseCase({
  required _FakeAppAuthPort authPort,
  required _FakeIdentitySessionPort identitySessionPort,
  ServerConfiguration? configuration,
}) {
  return SignInWithOidc(
    authPort: authPort,
    reconcileIdentitySession: ReconcileIdentitySession(
      authPort: authPort,
      identitySessionPort: identitySessionPort,
    ),
    serverConfigurationPort: _FakeServerConfigurationPort(
      configuration: configuration,
    ),
  );
}

void main() {
  group('SignInWithOidc', () {
    test('uses the saved auth configuration to start sign in', () async {
      final authPort = _FakeAppAuthPort();
      final identitySessionPort = _FakeIdentitySessionPort();
      final useCase = _buildUseCase(
        authPort: authPort,
        identitySessionPort: identitySessionPort,
        configuration: buildTestConfiguration(clientId: ' weave-mobile '),
      );

      await useCase.call(isInteractiveSignInSupported: true);

      expect(authPort.signInCalls, 1);
      expect(authPort.lastSignInConfiguration?.clientId, 'weave-mobile');
      expect(
        authPort.lastSignInConfiguration?.issuer,
        Uri.parse('https://auth.home.internal'),
      );
      expect(identitySessionPort.calls, 1);
      expect(
        identitySessionPort.lastBaseUrl,
        Uri.parse('https://api.home.internal/api'),
      );
      expect(identitySessionPort.lastAccessToken, 'access-token');
      expect(authPort.refreshCalls, 0);
    });

    test('refreshes exactly once when reconciliation updates access', () async {
      final authPort = _FakeAppAuthPort();
      final identitySessionPort = _FakeIdentitySessionPort()
        ..result = IdentitySessionReconciliation.accessUpdated;
      final useCase = _buildUseCase(
        authPort: authPort,
        identitySessionPort: identitySessionPort,
        configuration: buildTestConfiguration(),
      );

      await useCase.call(isInteractiveSignInSupported: true);

      expect(identitySessionPort.calls, 1);
      expect(authPort.refreshCalls, 1);
      expect(authPort.clearCalls, 0);
    });

    test(
      'clears a stale session when reconciliation needs an unavailable refresh',
      () async {
        final authPort = _FakeAppAuthPort()
          ..signInResult = AuthState.authenticated(
            buildTestAuthSession(refreshToken: null),
          );
        final identitySessionPort = _FakeIdentitySessionPort()
          ..result = IdentitySessionReconciliation.accessUpdated;
        final useCase = _buildUseCase(
          authPort: authPort,
          identitySessionPort: identitySessionPort,
          configuration: buildTestConfiguration(),
        );

        await expectLater(
          () => useCase.call(isInteractiveSignInSupported: true),
          throwsA(
            isA<AuthFailure>().having(
              (failure) => failure.invalidatesSavedSession,
              'invalidatesSavedSession',
              isTrue,
            ),
          ),
        );

        expect(authPort.clearCalls, 1);
        expect(authPort.refreshCalls, 0);
      },
    );

    test('fails when setup is incomplete', () async {
      final authPort = _FakeAppAuthPort();
      final useCase = _buildUseCase(
        authPort: authPort,
        identitySessionPort: _FakeIdentitySessionPort(),
      );

      expect(
        () => useCase.call(isInteractiveSignInSupported: true),
        throwsA(
          isA<AuthFailure>().having(
            (failure) => failure.type,
            'type',
            AuthFailureType.configuration,
          ),
        ),
      );
      expect(authPort.signInCalls, 0);
    });

    test('fails on unsupported platforms before starting sign in', () async {
      final authPort = _FakeAppAuthPort();
      final useCase = _buildUseCase(
        authPort: authPort,
        identitySessionPort: _FakeIdentitySessionPort(),
        configuration: buildTestConfiguration(),
      );

      expect(
        () => useCase.call(isInteractiveSignInSupported: false),
        throwsA(
          isA<AuthFailure>().having(
            (failure) => failure.type,
            'type',
            AuthFailureType.unsupportedPlatform,
          ),
        ),
      );
      expect(authPort.signInCalls, 0);
    });
  });
}
