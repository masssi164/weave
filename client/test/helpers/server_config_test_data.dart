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
  String matrixOAuthIssuer = 'https://auth.home.internal/realms/weave',
  String? matrixOAuthClientId = 'weave-matrix-app',
  String backendApiBaseUrl = 'https://api.home.internal/api',
}) {
  return ServerConfiguration(
    providerType: providerType,
    oidcIssuerUrl: Uri.parse(issuerUrl),
    oidcClientRegistration: OidcClientRegistration.manual(clientId: clientId),
    serviceEndpoints: ServiceEndpoints(
      matrixHomeserverUrl: Uri.parse(matrixHomeserverUrl),
      backendApiBaseUrl: Uri.parse(backendApiBaseUrl),
    ),
    matrixOAuthIssuer: Uri.parse(matrixOAuthIssuer),
    matrixOAuthClientId: matrixOAuthClientId,
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
