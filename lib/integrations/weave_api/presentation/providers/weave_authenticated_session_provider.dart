import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:weave/core/bootstrap/domain/bootstrap_state.dart';
import 'package:weave/core/bootstrap/presentation/providers/app_bootstrap_provider.dart';
import 'package:weave/core/failures/app_failure.dart';
import 'package:weave/features/auth/domain/entities/auth_configuration.dart';
import 'package:weave/features/auth/domain/entities/auth_failure.dart';
import 'package:weave/features/auth/presentation/providers/auth_session_repository_provider.dart';
import 'package:weave/features/server_config/presentation/providers/server_configuration_form_controller.dart';

class WeaveAuthenticatedSession {
  const WeaveAuthenticatedSession({
    required this.apiBaseUrl,
    required this.accessToken,
  });

  final Uri apiBaseUrl;
  final String accessToken;
}

final weaveAuthenticatedSessionProvider =
    FutureProvider<WeaveAuthenticatedSession?>((ref) async {
      final configuration = await ref.watch(
        savedServerConfigurationProvider.future,
      );
      if (configuration == null) {
        return null;
      }

      final bootstrapState = await ref.watch(appBootstrapProvider.future);
      if (bootstrapState.phase != BootstrapPhase.ready) {
        return null;
      }

      try {
        final authState = await ref
            .read(authSessionRepositoryProvider)
            .restoreSession(
              AuthConfiguration(
                issuer: configuration.oidcIssuerUrl,
                clientId: configuration.oidcClientRegistration.clientId.trim(),
              ),
            );
        final session = authState.session;
        if (!authState.isAuthenticated || session == null) {
          return null;
        }

        return WeaveAuthenticatedSession(
          apiBaseUrl: configuration.serviceEndpoints.backendApiBaseUrl,
          accessToken: session.accessToken,
        );
      } on AuthFailure {
        throw const AppFailure.unknown(
          'The Weave backend rejected the current session.',
        );
      } on AppFailure {
        rethrow;
      } catch (error) {
        throw AppFailure.unknown(
          'Unable to restore the Weave backend session.',
          cause: error,
        );
      }
    });
