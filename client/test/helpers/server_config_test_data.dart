import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:weave/features/onboarding/domain/use_cases/discover_organization_access.dart';
import 'package:weave/features/server_config/domain/repositories/server_configuration_repository.dart';
import 'dart:convert';

import 'package:weave/features/server_config/domain/entities/oidc_client_registration.dart';
import 'package:weave/features/server_config/data/repositories/shared_preferences_server_configuration_repository.dart';
import 'package:weave/features/server_config/domain/entities/oidc_provider_type.dart';
import 'package:weave/features/server_config/domain/entities/server_configuration.dart';
import 'package:weave/features/server_config/domain/entities/service_endpoints.dart';

ServerConfiguration buildTestConfiguration({
  OidcProviderType providerType = OidcProviderType.oidc,
  String issuerUrl = 'https://auth.home.internal',
  String clientId = 'weave-app',
  String matrixHomeserverUrl = 'https://matrix.home.internal',
  String backendApiBaseUrl = 'https://api.home.internal/api',
  bool withManifest = true,
}) {
  return ServerConfiguration(
    organizationOrigin: withManifest
        ? Uri.parse('https://weave.home.internal')
        : null,
    validatedManifest: withManifest
        ? {
            'schemaVersion': 2,
            'organizationOrigin': 'https://weave.home.internal',
            'userApiBaseUrl': backendApiBaseUrl,
            'oidc': {'issuer': issuerUrl, 'clientId': clientId.trim()},
            'protocols': {'matrixClientServerBaseUrl': matrixHomeserverUrl},
            'releasePosture': 'development',
            'domains': [
              for (final domain in ['identity', 'chat', 'files', 'calendar'])
                {
                  'domain': domain,
                  'state': 'available',
                  'capabilities': <String>[],
                },
            ],
            'recoveryActions': <Object>[],
          }
        : null,
    manifestValidatedAt: withManifest ? DateTime.now().toUtc() : null,
    providerType: providerType,
    oidcIssuerUrl: Uri.parse(issuerUrl),
    oidcClientRegistration: OidcClientRegistration.manual(clientId: clientId),
    serviceEndpoints: ServiceEndpoints(
      matrixHomeserverUrl: Uri.parse(matrixHomeserverUrl),
      backendApiBaseUrl: Uri.parse(backendApiBaseUrl),
    ),
  );
}

String encodeTestConfiguration({
  int schemaVersion = 2,
  OidcProviderType providerType = OidcProviderType.oidc,
  String issuerUrl = 'https://auth.home.internal',
  String clientId = 'weave-app',
  String matrixHomeserverUrl = 'https://matrix.home.internal',
  String? nextcloudBaseUrl,
  String? backendApiBaseUrl = 'https://api.home.internal/api',
}) {
  final json = <String, Object?>{
    'schemaVersion': schemaVersion,
    'providerType': providerType.name,
    'oidcIssuerUrl': issuerUrl,
    'oidcClientRegistrationMode': 'manual',
    'oidcClientId': clientId,
    'matrixHomeserverUrl': matrixHomeserverUrl,
    if (nextcloudBaseUrl != null) 'nextcloudBaseUrl': nextcloudBaseUrl,
  };
  if (backendApiBaseUrl != null) {
    json['backendApiBaseUrl'] = backendApiBaseUrl;
  }
  return jsonEncode(json);
}

Map<String, Object> buildStoredConfiguration({
  int schemaVersion = 2,
  OidcProviderType providerType = OidcProviderType.oidc,
  String issuerUrl = 'https://auth.home.internal',
  String clientId = 'weave-app',
  String matrixHomeserverUrl = 'https://matrix.home.internal',
  String? nextcloudBaseUrl,
  String? backendApiBaseUrl = 'https://api.home.internal/api',
}) {
  return {
    serverConfigurationStorageKey: encodeTestConfiguration(
      providerType: providerType,
      schemaVersion: schemaVersion,
      issuerUrl: issuerUrl,
      clientId: clientId,
      matrixHomeserverUrl: matrixHomeserverUrl,
      nextcloudBaseUrl: nextcloudBaseUrl,
      backendApiBaseUrl: backendApiBaseUrl,
    ),
  };
}

AppStartDiscoveryClient buildTestDiscoveryClient(
  ServerConfigurationRepository repository,
) => AppStartDiscoveryClient(
  httpClient: MockClient((request) async {
    final configuration = await repository.loadConfiguration();
    return http.Response(jsonEncode(configuration!.validatedManifest), 200);
  }),
);
