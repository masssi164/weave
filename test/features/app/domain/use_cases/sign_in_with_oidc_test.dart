import 'package:flutter_test/flutter_test.dart';
import 'package:weave/features/app/domain/ports/app_auth_port.dart';
import 'package:weave/features/app/domain/ports/server_configuration_port.dart';
import 'package:weave/features/app/domain/use_cases/sign_in_with_oidc.dart';
import 'package:weave/features/auth/domain/entities/auth_configuration.dart';
import 'package:weave/features/auth/domain/entities/auth_failure.dart';
import 'package:weave/features/auth/domain/entities/auth_state.dart';
import 'package:weave/features/server_config/domain/entities/server_configuration.dart';

import '../../../../helpers/server_config_test_data.dart';

class _FakeAppAuthPort implements AppAuthPort {
  int signInCalls = 0;
  AuthConfiguration? lastSignInConfiguration;

  @override
  Future<void> clearLocalSession() async {}

  @override
  Future<AuthState> restoreSession(AuthConfiguration configuration) async {
    return const AuthState.signedOut();
  }

  @override
  Future<AuthState> signIn(AuthConfiguration configuration) async {
    signInCalls++;
    lastSignInConfiguration = configuration;
    return const AuthState.signedOut();
  }

  @override
  Future<void> signOut(AuthConfiguration configuration) async {}
}

class _FakeServerConfigurationPort implements ServerConfigurationPort {
  _FakeServerConfigurationPort({this.configuration});

  final ServerConfiguration? configuration;

  @override
  Future<void> clearConfiguration() async {}

  @override
  Future<ServerConfiguration?> loadConfiguration() async => configuration;
}

void main() {
  group('SignInWithOidc', () {
    test('uses the saved auth configuration to start sign in', () async {
      final authPort = _FakeAppAuthPort();
      final useCase = SignInWithOidc(
        authPort: authPort,
        serverConfigurationPort: _FakeServerConfigurationPort(
          configuration: buildTestConfiguration(clientId: ' weave-mobile '),
        ),
      );

      await useCase.call(isInteractiveSignInSupported: true);

      expect(authPort.signInCalls, 1);
      expect(authPort.lastSignInConfiguration?.clientId, 'weave-mobile');
      expect(
        authPort.lastSignInConfiguration?.issuer,
        Uri.parse('https://auth.home.internal'),
      );
    });

    test('fails when setup is incomplete', () async {
      final authPort = _FakeAppAuthPort();
      final useCase = SignInWithOidc(
        authPort: authPort,
        serverConfigurationPort: _FakeServerConfigurationPort(),
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
      final useCase = SignInWithOidc(
        authPort: authPort,
        serverConfigurationPort: _FakeServerConfigurationPort(
          configuration: buildTestConfiguration(),
        ),
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
