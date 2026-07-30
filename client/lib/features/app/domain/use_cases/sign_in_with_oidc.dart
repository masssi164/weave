import 'package:weave/features/app/domain/ports/app_auth_port.dart';
import 'package:weave/features/app/domain/ports/identity_session_port.dart';
import 'package:weave/features/app/domain/ports/server_configuration_port.dart';
import 'package:weave/features/app/domain/use_cases/reconcile_identity_session.dart';
import 'package:weave/features/auth/domain/entities/auth_configuration.dart';
import 'package:weave/features/auth/domain/entities/auth_failure.dart';
import 'package:weave/features/server_config/domain/entities/server_configuration.dart';

class SignInWithOidc {
  const SignInWithOidc({
    required AppAuthPort authPort,
    required ReconcileIdentitySession reconcileIdentitySession,
    required ServerConfigurationPort serverConfigurationPort,
  }) : _authPort = authPort,
       _reconcileIdentitySession = reconcileIdentitySession,
       _serverConfigurationPort = serverConfigurationPort;

  final AppAuthPort _authPort;
  final ReconcileIdentitySession _reconcileIdentitySession;
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

    final authConfiguration = _toAuthConfiguration(configuration);
    final authenticated = await _authPort.signIn(authConfiguration);
    final reconciliation = await _reconcileIdentitySession(
      backendApiBaseUrl: configuration.serviceEndpoints.backendApiBaseUrl,
      authenticated: authenticated,
    );
    if (reconciliation ==
        IdentitySessionReconciliation.reauthorizationRequired) {
      final reauthorized = await _authPort.signIn(authConfiguration);
      if (!reauthorized.isAuthenticated || reauthorized.session == null) {
        await _authPort.clearLocalSession();
        throw const AuthFailure.sessionRejected(
          'Organization access changed, but OIDC reauthorization was rejected.',
        );
      }
    }
  }

  AuthConfiguration _toAuthConfiguration(ServerConfiguration configuration) {
    return AuthConfiguration(
      issuer: configuration.oidcIssuerUrl,
      clientId: configuration.oidcClientRegistration.clientId.trim(),
    );
  }
}
