import 'package:weave/core/bootstrap/domain/bootstrap_state.dart';
import 'package:weave/core/failures/app_failure.dart';
import 'package:weave/features/app/domain/ports/app_auth_port.dart';
import 'package:weave/features/app/domain/ports/client_upgrade_port.dart';
import 'package:weave/features/app/domain/ports/server_configuration_port.dart';
import 'package:weave/features/auth/domain/entities/auth_configuration.dart';
import 'package:weave/features/auth/domain/entities/auth_failure.dart';
import 'package:weave/features/server_config/domain/entities/server_configuration.dart';

class ResolveAppBootstrap {
  const ResolveAppBootstrap({
    required AppAuthPort authPort,
    required ServerConfigurationPort serverConfigurationPort,
    ClientUpgradePort? clientUpgradePort,
  }) : _authPort = authPort,
       _serverConfigurationPort = serverConfigurationPort,
       _clientUpgradePort = clientUpgradePort;

  final AppAuthPort _authPort;
  final ServerConfigurationPort _serverConfigurationPort;
  final ClientUpgradePort? _clientUpgradePort;

  Future<BootstrapState> call() async {
    try {
      final configuration = await _serverConfigurationPort.loadConfiguration();
      if (configuration == null ||
          !configuration.hasCompleteAuthConfiguration) {
        return const BootstrapState.needsSetup();
      }

      final authState = await _authPort.restoreSession(
        _toAuthConfiguration(configuration),
      );
      if (authState.isAuthenticated) {
        await _removeObsoleteAuthenticatedState();
        return const BootstrapState.ready();
      }

      return const BootstrapState.needsSignIn();
    } on AuthFailure catch (failure) {
      return BootstrapState.error(
        AppFailure.storage(failure.message, cause: failure.cause),
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

  Future<void> _removeObsoleteAuthenticatedState() async {
    try {
      await _clientUpgradePort?.removeObsoleteAuthenticatedState();
    } catch (_) {
      // Upgrade cleanup cannot turn a valid authenticated launch into a global
      // bootstrap failure. Current configuration and OIDC state stay primary.
    }
  }

  AuthConfiguration _toAuthConfiguration(ServerConfiguration configuration) {
    return AuthConfiguration(
      issuer: configuration.oidcIssuerUrl,
      clientId: configuration.oidcClientRegistration.clientId.trim(),
    );
  }
}
