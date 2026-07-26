import 'package:weave/core/bootstrap/domain/bootstrap_state.dart';
import 'package:weave/core/failures/app_failure.dart';
import 'package:weave/features/app/domain/ports/app_auth_port.dart';
import 'package:weave/features/app/domain/ports/server_configuration_port.dart';
import 'package:weave/features/app/domain/use_cases/reconcile_identity_session.dart';
import 'package:weave/features/auth/domain/entities/auth_configuration.dart';
import 'package:weave/features/auth/domain/entities/auth_failure.dart';
import 'package:weave/features/server_config/domain/entities/server_configuration.dart';

class ResolveAppBootstrap {
  const ResolveAppBootstrap({
    required AppAuthPort authPort,
    required ReconcileIdentitySession reconcileIdentitySession,
    required ServerConfigurationPort serverConfigurationPort,
  }) : _authPort = authPort,
       _reconcileIdentitySession = reconcileIdentitySession,
       _serverConfigurationPort = serverConfigurationPort;

  final AppAuthPort _authPort;
  final ReconcileIdentitySession _reconcileIdentitySession;
  final ServerConfigurationPort _serverConfigurationPort;

  Future<BootstrapState> call() async {
    try {
      final configuration = await _serverConfigurationPort.loadConfiguration();
      if (configuration == null ||
          !configuration.hasCompleteAuthConfiguration) {
        return const BootstrapState.needsSetup();
      }

      final authConfiguration = _toAuthConfiguration(configuration);
      final authState = await _authPort.restoreSession(authConfiguration);
      if (authState.isAuthenticated) {
        await _reconcileIdentitySession(
          authConfiguration: authConfiguration,
          backendApiBaseUrl: configuration.serviceEndpoints.backendApiBaseUrl,
          authenticated: authState,
        );
        return const BootstrapState.ready();
      }

      return const BootstrapState.needsSignIn();
    } on AuthFailure catch (failure) {
      return BootstrapState.error(
        failure.type == AuthFailureType.storage
            ? AppFailure.storage(failure.message, cause: failure.cause)
            : AppFailure.bootstrap(failure.message, cause: failure.cause),
      );
    } on AppFailure catch (failure) {
      return BootstrapState.error(failure);
    } catch (error) {
      return BootstrapState.error(
        AppFailure.bootstrap(
          'Unable to bootstrap the application.',
          cause: error,
        ),
      );
    }
  }

  AuthConfiguration _toAuthConfiguration(ServerConfiguration configuration) {
    return AuthConfiguration(
      issuer: configuration.oidcIssuerUrl,
      clientId: configuration.oidcClientRegistration.clientId.trim(),
    );
  }
}
