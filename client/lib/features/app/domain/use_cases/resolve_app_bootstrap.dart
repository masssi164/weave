import 'dart:async';

import 'package:weave/core/bootstrap/domain/bootstrap_state.dart';
import 'package:weave/core/failures/app_failure.dart';
import 'package:weave/features/app/domain/ports/app_auth_port.dart';
import 'package:weave/features/app/domain/ports/chat_session_port.dart';
import 'package:weave/features/app/domain/ports/identity_session_port.dart';
import 'package:weave/features/app/domain/ports/server_configuration_port.dart';
import 'package:weave/features/app/domain/use_cases/reconcile_identity_session.dart';
import 'package:weave/features/auth/domain/entities/auth_configuration.dart';
import 'package:weave/features/auth/domain/entities/auth_failure.dart';
import 'package:weave/features/server_config/domain/entities/server_configuration.dart';

class ResolveAppBootstrap {
  const ResolveAppBootstrap({
    required AppAuthPort authPort,
    required ChatSessionPort chatSessionPort,
    required ReconcileIdentitySession reconcileIdentitySession,
    required ServerConfigurationPort serverConfigurationPort,
  }) : _authPort = authPort,
       _chatSessionPort = chatSessionPort,
       _reconcileIdentitySession = reconcileIdentitySession,
       _serverConfigurationPort = serverConfigurationPort;

  final AppAuthPort _authPort;
  final ChatSessionPort _chatSessionPort;
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
        final reconciliation = await _reconcileIdentitySession(
          backendApiBaseUrl: configuration.serviceEndpoints.backendApiBaseUrl,
          authenticated: authState,
        );
        if (reconciliation ==
            IdentitySessionReconciliation.reauthorizationRequired) {
          await _authPort.clearLocalSession();
          return const BootstrapState.needsSignIn();
        }
        // Prepare the separate Matrix session automatically. The coordinator
        // shares an in-flight open with Chat; independent capabilities do not
        // wait for its network or browser recovery.
        unawaited(_prepareChat());
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

  Future<void> _prepareChat() async {
    try {
      await _chatSessionPort.ensureSession();
    } on Object {
      // Chat owns its error/retry state. A late capability failure must not
      // escape this background preparation or invalidate the Weave login.
    }
  }

  AuthConfiguration _toAuthConfiguration(ServerConfiguration configuration) {
    return AuthConfiguration(
      issuer: configuration.oidcIssuerUrl,
      clientId: configuration.oidcClientRegistration.clientId.trim(),
    );
  }
}
