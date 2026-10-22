import 'dart:convert';

import 'package:crypto/crypto.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:weave/features/app/domain/entities/integration_invalidation.dart';
import 'package:weave/features/app/presentation/providers/workspace_invalidation_provider.dart';
import 'package:weave/features/server_config/presentation/providers/saved_server_configuration_provider.dart';
import 'package:weave/generated/user_api/api.dart' as api;
import 'package:weave/integrations/weave_api/data/services/weave_user_api_client.dart';
import 'package:weave/integrations/weave_api/presentation/providers/weave_api_client_provider.dart';
import 'package:weave/integrations/weave_api/presentation/providers/weave_authenticated_session_provider.dart';

class SupportIdentityReferences {
  const SupportIdentityReferences({
    required this.account,
    required this.organization,
  });
  final String account;
  final String organization;
}

/// References come only from the authenticated generated identity response.
/// Raw identity values remain private to this request and never enter the UI.
final supportIdentityReferenceProvider =
    FutureProvider<SupportIdentityReferences?>((ref) async {
      final configuration = await ref.watch(
        savedServerConfigurationProvider.future,
      );
      await ref.watch(weaveAuthenticatedSessionProvider.future);
      final invalidation = ref.watch(
        integrationInvalidationProvider(WorkspaceIntegration.weaveBackend),
      );
      final session = await resolveCurrentWeaveAuthenticatedSession(ref);
      if (configuration == null ||
          session == null ||
          session.apiBaseUrl !=
              configuration.serviceEndpoints.backendApiBaseUrl) {
        return null;
      }
      bool current() {
        if (!ref.mounted) return false;
        final saved = ref.read(savedServerConfigurationProvider);
        return !saved.isLoading &&
            !saved.hasError &&
            ref.read(
                  integrationInvalidationProvider(
                    WorkspaceIntegration.weaveBackend,
                  ),
                ) ==
                invalidation &&
            saved.asData?.value?.organizationOrigin ==
                configuration.organizationOrigin &&
            saved.asData?.value?.oidcIssuerUrl == configuration.oidcIssuerUrl &&
            saved.asData?.value?.oidcClientRegistration.clientId ==
                configuration.oidcClientRegistration.clientId &&
            saved.asData?.value?.serviceEndpoints.backendApiBaseUrl ==
                session.apiBaseUrl;
      }

      try {
        if (!current()) return null;
        final identity = await api.IdentityApi(
          weaveUserApiClient(
            apiBaseUrl: session.apiBaseUrl,
            accessToken: session.accessToken,
            httpClient: ref.watch(weaveApiHttpClientProvider),
          ),
        ).me().timeout(const Duration(seconds: 8));
        final after = await resolveCurrentWeaveAuthenticatedSession(ref);
        if (!current() ||
            after == null ||
            after.apiBaseUrl != session.apiBaseUrl ||
            after.accessToken != session.accessToken ||
            identity == null ||
            identity.accountId?.trim().isNotEmpty != true ||
            identity.organizationId?.trim().isNotEmpty != true ||
            identity.identityIssuer != configuration.oidcIssuerUrl.toString()) {
          return null;
        }
        String reference(String domain, String value) =>
            'sha256:${sha256.convert(utf8.encode(jsonEncode([domain, identity.identityIssuer, value])))}';
        return SupportIdentityReferences(
          account: reference('weave-account', identity.accountId!),
          organization: reference(
            'weave-organization',
            identity.organizationId!,
          ),
        );
      } catch (_) {
        return null;
      }
    });
