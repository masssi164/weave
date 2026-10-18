import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:weave/core/bootstrap/domain/bootstrap_state.dart';
import 'package:weave/core/bootstrap/presentation/providers/app_bootstrap_provider.dart';
import 'package:weave/core/failures/app_failure.dart';
import 'package:weave/features/app/domain/entities/integration_invalidation.dart';
import 'package:weave/features/app/presentation/providers/workspace_invalidation_provider.dart';
import 'package:weave/features/auth/domain/entities/auth_configuration.dart';
import 'package:weave/features/auth/domain/entities/auth_failure.dart';
import 'package:weave/features/auth/presentation/providers/auth_session_repository_provider.dart';
import 'package:weave/features/server_config/presentation/providers/saved_server_configuration_provider.dart';

class WeaveAuthenticatedSession {
  const WeaveAuthenticatedSession({
    required this.apiBaseUrl,
    required this.accessToken,
  });

  final Uri apiBaseUrl;
  final String accessToken;
}

final weaveAuthenticatedSessionProvider =
    FutureProvider<WeaveAuthenticatedSession?>((ref) {
      // Existing screens use this cached value as a lifecycle snapshot.
      // Authenticated operations use resolveCurrentWeaveAuthenticatedSession.
      ref.watch(savedServerConfigurationProvider.future);
      ref.watch(appBootstrapProvider.future);
      return resolveCurrentWeaveAuthenticatedSession(ref);
    });

/// Resolves transport access afresh; the auth repository owns expiry and refresh.
Future<WeaveAuthenticatedSession?> resolveCurrentWeaveAuthenticatedSession(
  Ref ref,
) async {
  final invalidation = ref.read(
    integrationInvalidationProvider(WorkspaceIntegration.weaveBackend),
  );
  final configuration = await ref.read(savedServerConfigurationProvider.future);
  if (!ref.mounted || configuration == null) return null;
  final bootstrap = await ref.read(appBootstrapProvider.future);
  if (!ref.mounted || bootstrap.phase != BootstrapPhase.ready) return null;

  bool current() {
    if (!ref.mounted) return false;
    final saved = ref.read(savedServerConfigurationProvider).asData?.value;
    return saved != null &&
        saved.organizationOrigin == configuration.organizationOrigin &&
        saved.oidcIssuerUrl == configuration.oidcIssuerUrl &&
        saved.oidcClientRegistration.clientId.trim() ==
            configuration.oidcClientRegistration.clientId.trim() &&
        saved.serviceEndpoints.backendApiBaseUrl ==
            configuration.serviceEndpoints.backendApiBaseUrl &&
        ref.read(appBootstrapProvider).asData?.value.phase ==
            BootstrapPhase.ready &&
        ref.read(
              integrationInvalidationProvider(
                WorkspaceIntegration.weaveBackend,
              ),
            ) ==
            invalidation;
  }

  if (!current()) return null;
  final authConfiguration = AuthConfiguration(
    issuer: configuration.oidcIssuerUrl,
    clientId: configuration.oidcClientRegistration.clientId.trim(),
  );
  try {
    final auth = await ref
        .read(authSessionRepositoryProvider)
        .restoreSession(authConfiguration);
    if (!current()) return null;
    final session = auth.session;
    if (!auth.isAuthenticated ||
        session == null ||
        !session.matches(authConfiguration)) {
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
}
