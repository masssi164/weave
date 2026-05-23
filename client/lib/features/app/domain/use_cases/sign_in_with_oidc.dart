import 'package:weave/features/app/domain/ports/app_auth_port.dart';
import 'package:weave/features/app/domain/ports/server_configuration_port.dart';
import 'package:weave/features/auth/domain/entities/auth_configuration.dart';
import 'package:weave/features/auth/domain/entities/auth_failure.dart';
import 'package:weave/features/server_config/domain/entities/server_configuration.dart';

class SignInWithOidc {
  const SignInWithOidc({
    required AppAuthPort authPort,
    required ServerConfigurationPort serverConfigurationPort,
  }) : _authPort = authPort,
       _serverConfigurationPort = serverConfigurationPort;

  final AppAuthPort _authPort;
  final ServerConfigurationPort _serverConfigurationPort;

  Future<void> call({required bool isInteractiveSignInSupported}) async {
    final configuration = await _serverConfigurationPort.loadConfiguration();
    if (configuration == null || !configuration.hasCompleteAuthConfiguration) {
      throw const AuthFailure.configuration(
        'Finish server setup before signing in.',
      );
    }

    if (!isInteractiveSignInSupported) {
      throw const AuthFailure.unsupportedPlatform(
        'Interactive sign-in is currently supported on Android, iOS, and macOS.',
      );
    }

    await _authPort.signIn(_toAuthConfiguration(configuration));
  }

  AuthConfiguration _toAuthConfiguration(ServerConfiguration configuration) {
    return AuthConfiguration(
      issuer: configuration.oidcIssuerUrl,
      clientId: configuration.oidcClientRegistration.clientId.trim(),
    );
  }
}
