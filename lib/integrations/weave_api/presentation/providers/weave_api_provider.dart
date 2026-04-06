import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:http/http.dart' as http;
import 'package:weave/core/bootstrap/domain/bootstrap_state.dart';
import 'package:weave/core/bootstrap/presentation/providers/app_bootstrap_provider.dart';
import 'package:weave/core/failures/app_failure.dart';
import 'package:weave/features/app/domain/entities/integration_invalidation.dart';
import 'package:weave/features/app/domain/entities/workspace_capability_snapshot.dart';
import 'package:weave/features/app/presentation/providers/workspace_invalidation_provider.dart';
import 'package:weave/features/auth/domain/entities/auth_configuration.dart';
import 'package:weave/features/auth/domain/entities/auth_failure.dart';
import 'package:weave/features/auth/presentation/providers/auth_session_repository_provider.dart';
import 'package:weave/features/server_config/presentation/providers/server_configuration_form_controller.dart';
import 'package:weave/integrations/weave_api/data/services/weave_api_client.dart';

/// Connection state for the Weave backend integration.
///
/// Distinct states allow the UI to surface actionable failure messages
/// rather than silently degrading to local-only capabilities.
enum WeaveBackendConnectionState {
  /// The backend URL is not configured; no fetch is attempted.
  unconfigured,

  /// The app is not yet in a ready bootstrap phase; no fetch is attempted.
  notReady,

  /// A fetch is in progress.
  loading,

  /// Capabilities were successfully fetched from the backend.
  connected,

  /// The backend could not be reached (network error or DNS failure).
  unreachable,

  /// The backend rejected the current session token (HTTP 401 or 403).
  unauthorized,

  /// The backend returned an unexpected error response.
  serverError,
}

final weaveApiHttpClientProvider = Provider<http.Client>((ref) {
  final client = http.Client();
  ref.onDispose(client.close);
  return client;
});

final weaveApiClientProvider = Provider<WeaveApiClient>((ref) {
  return HttpWeaveApiClient(httpClient: ref.watch(weaveApiHttpClientProvider));
});

final weaveApiWorkspaceCapabilitySnapshotProvider =
    FutureProvider<WorkspaceCapabilitySnapshot?>((ref) async {
      // Watch the invalidation signal so explicit invalidations (sign-out,
      // restart-setup, URL change) trigger a re-fetch beyond just config changes.
      ref.watch(
        integrationInvalidationProvider(WorkspaceIntegration.weaveBackend),
      );

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

        return ref
            .read(weaveApiClientProvider)
            .fetchWorkspaceCapabilities(
              baseUrl: configuration.serviceEndpoints.backendApiBaseUrl,
              accessToken: session.accessToken,
            );
      } on AuthFailure {
        return null;
      } on AppFailure {
        return null;
      } catch (_) {
        return null;
      }
    });

/// Maps the backend capability provider state to a distinct [WeaveBackendConnectionState].
///
/// This provider is the testable surface for "is the backend reachable, authorized,
/// or unconfigured?" — separate from the merged [workspaceCapabilitySnapshotProvider].
final weaveBackendConnectionStateProvider =
    Provider<WeaveBackendConnectionState>((ref) {
      // Watch the invalidation signal to ensure this provider also re-evaluates
      // on explicit invalidations.
      ref.watch(
        integrationInvalidationProvider(WorkspaceIntegration.weaveBackend),
      );

      final backendAsync = ref.watch(
        weaveApiWorkspaceCapabilitySnapshotProvider,
      );

      return backendAsync.when(
        loading: () => WeaveBackendConnectionState.loading,
        error: (error, _) {
          if (error is AppFailure) {
            final msg = error.message;
            if (msg.contains('rejected the current session')) {
              return WeaveBackendConnectionState.unauthorized;
            }
            if (msg.contains('reach the Weave backend')) {
              return WeaveBackendConnectionState.unreachable;
            }
          }
          return WeaveBackendConnectionState.serverError;
        },
        data: (snapshot) => snapshot == null
            ? WeaveBackendConnectionState.unconfigured
            : WeaveBackendConnectionState.connected,
      );
    });
