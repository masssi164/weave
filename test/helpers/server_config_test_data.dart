import 'dart:convert';

import 'package:weave/features/server_config/domain/entities/oidc_client_registration.dart';
import 'package:weave/features/server_config/data/repositories/shared_preferences_server_configuration_repository.dart';
import 'package:weave/features/server_config/domain/entities/oidc_provider_type.dart';
import 'package:weave/features/server_config/domain/entities/server_configuration.dart';
import 'package:weave/features/server_config/domain/entities/service_endpoints.dart';

ServerConfiguration buildTestConfiguration({
  OidcProviderType providerType = OidcProviderType.authentik,
  String issuerUrl = 'https://auth.home.internal',
  String clientId = 'weave-app',
  String matrixHomeserverUrl = 'https://matrix.home.internal',
  String nextcloudBaseUrl = 'https://files.home.internal',
}) {
  return ServerConfiguration(
    providerType: providerType,
    oidcIssuerUrl: Uri.parse(issuerUrl),
    oidcClientRegistration: OidcClientRegistration.manual(clientId: clientId),
    serviceEndpoints: ServiceEndpoints(
      matrixHomeserverUrl: Uri.parse(matrixHomeserverUrl),
      nextcloudBaseUrl: Uri.parse(nextcloudBaseUrl),
    ),
  );
}

String encodeTestConfiguration({
  OidcProviderType providerType = OidcProviderType.authentik,
  String issuerUrl = 'https://auth.home.internal',
  String clientId = 'weave-app',
  String matrixHomeserverUrl = 'https://matrix.home.internal',
  String nextcloudBaseUrl = 'https://files.home.internal',
}) {
  return jsonEncode({
    'providerType': providerType.name,
    'oidcIssuerUrl': issuerUrl,
    'oidcClientRegistrationMode': 'manual',
    'oidcClientId': clientId,
    'matrixHomeserverUrl': matrixHomeserverUrl,
    'nextcloudBaseUrl': nextcloudBaseUrl,
  });
}

Map<String, Object> buildStoredConfiguration({
  OidcProviderType providerType = OidcProviderType.authentik,
  String issuerUrl = 'https://auth.home.internal',
  String clientId = 'weave-app',
  String matrixHomeserverUrl = 'https://matrix.home.internal',
  String nextcloudBaseUrl = 'https://files.home.internal',
}) {
  return {
    serverConfigurationStorageKey: encodeTestConfiguration(
      providerType: providerType,
      issuerUrl: issuerUrl,
      clientId: clientId,
      matrixHomeserverUrl: matrixHomeserverUrl,
      nextcloudBaseUrl: nextcloudBaseUrl,
    ),
  };
}
