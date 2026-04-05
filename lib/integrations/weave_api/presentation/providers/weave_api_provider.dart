import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:http/http.dart' as http;
import 'package:weave/core/bootstrap/domain/bootstrap_state.dart';
import 'package:weave/core/bootstrap/presentation/providers/app_bootstrap_provider.dart';
import 'package:weave/core/failures/app_failure.dart';
import 'package:weave/features/app/domain/entities/workspace_capability_snapshot.dart';
import 'package:weave/features/auth/domain/entities/auth_configuration.dart';
import 'package:weave/features/auth/domain/entities/auth_failure.dart';
import 'package:weave/features/auth/presentation/providers/auth_session_repository_provider.dart';
import 'package:weave/features/server_config/presentation/providers/server_configuration_form_controller.dart';
import 'package:weave/integrations/weave_api/data/services/weave_api_client.dart';

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
