import 'dart:convert';

import 'package:weave/features/server_config/domain/entities/oidc_client_registration.dart';
import 'package:weave/features/server_config/domain/entities/oidc_provider_type.dart';
import 'package:weave/features/server_config/domain/entities/server_configuration.dart';
import 'package:weave/features/server_config/domain/entities/service_endpoints.dart';

class ServerConfigurationDto {
  const ServerConfigurationDto({
    required this.providerType,
    required this.oidcIssuerUrl,
    required this.oidcClientRegistrationMode,
    required this.oidcClientId,
    required this.matrixHomeserverUrl,
    required this.backendApiBaseUrl,
    this.organizationOrigin,
    this.validatedManifest,
    this.manifestValidatedAt,
    this.matrixOAuthIssuer,
    this.matrixOAuthClientId,
  });

  factory ServerConfigurationDto.fromConfiguration(
    ServerConfiguration configuration,
  ) {
    return ServerConfigurationDto(
      organizationOrigin: configuration.organizationOrigin?.toString(),
      validatedManifest: configuration.validatedManifest,
      manifestValidatedAt: configuration.manifestValidatedAt
          ?.toUtc()
          .toIso8601String(),
      providerType: configuration.providerType.name,
      oidcIssuerUrl: configuration.oidcIssuerUrl.toString(),
      oidcClientRegistrationMode:
          configuration.oidcClientRegistration.mode.name,
      oidcClientId: configuration.oidcClientRegistration.clientId,
      matrixHomeserverUrl: configuration.serviceEndpoints.matrixHomeserverUrl
          .toString(),
      backendApiBaseUrl: configuration.serviceEndpoints.backendApiBaseUrl
          .toString(),
      matrixOAuthIssuer: configuration.matrixOAuthIssuer?.toString(),
      matrixOAuthClientId: configuration.matrixOAuthClientId,
    );
  }

  factory ServerConfigurationDto.fromJson(Map<String, dynamic> json) {
    return ServerConfigurationDto(
      organizationOrigin: json['organizationOrigin'] as String?,
      validatedManifest: json['validatedManifest'] as Map<String, dynamic>?,
      manifestValidatedAt: json['manifestValidatedAt'] as String?,
      providerType: json['providerType'] as String,
      oidcIssuerUrl: json['oidcIssuerUrl'] as String,
      oidcClientRegistrationMode: json['oidcClientRegistrationMode'] as String,
      oidcClientId: json['oidcClientId'] as String,
      matrixHomeserverUrl: json['matrixHomeserverUrl'] as String,
      backendApiBaseUrl: json['backendApiBaseUrl'] as String,
      matrixOAuthIssuer: json['matrixOAuthIssuer'] as String?,
      matrixOAuthClientId: json['matrixOAuthClientId'] as String?,
    );
  }

  final String? organizationOrigin;
  final Map<String, dynamic>? validatedManifest;
  final String? manifestValidatedAt;
  final String providerType;
  final String oidcIssuerUrl;
  final String oidcClientRegistrationMode;
  final String oidcClientId;
  final String matrixHomeserverUrl;
  final String backendApiBaseUrl;
  final String? matrixOAuthIssuer;
  final String? matrixOAuthClientId;

  ServerConfiguration toConfiguration() {
    return ServerConfiguration(
      organizationOrigin: organizationOrigin == null
          ? null
          : Uri.parse(organizationOrigin!),
      validatedManifest: validatedManifest,
      manifestValidatedAt: manifestValidatedAt == null
          ? null
          : DateTime.parse(manifestValidatedAt!),
      providerType: OidcProviderType.values.byName(providerType),
      oidcIssuerUrl: Uri.parse(oidcIssuerUrl),
      oidcClientRegistration: OidcClientRegistration(
        mode: OidcClientRegistrationMode.values.byName(
          oidcClientRegistrationMode,
        ),
        clientId: oidcClientId,
      ),
      serviceEndpoints: ServiceEndpoints(
        matrixHomeserverUrl: Uri.parse(matrixHomeserverUrl),
        backendApiBaseUrl: Uri.parse(backendApiBaseUrl),
      ),
      matrixOAuthIssuer: matrixOAuthIssuer == null
          ? null
          : Uri.parse(matrixOAuthIssuer!),
      matrixOAuthClientId: matrixOAuthClientId,
    );
  }

  String encode() => jsonEncode(toJson());

  Map<String, dynamic> toJson() {
    return {
      'schemaVersion': 2,
      if (organizationOrigin != null) 'organizationOrigin': organizationOrigin,
      if (validatedManifest != null) 'validatedManifest': validatedManifest,
      if (manifestValidatedAt != null)
        'manifestValidatedAt': manifestValidatedAt,
      'providerType': providerType,
      'oidcIssuerUrl': oidcIssuerUrl,
      'oidcClientRegistrationMode': oidcClientRegistrationMode,
      'oidcClientId': oidcClientId,
      'matrixHomeserverUrl': matrixHomeserverUrl,
      'backendApiBaseUrl': backendApiBaseUrl,
      if (matrixOAuthIssuer != null) 'matrixOAuthIssuer': matrixOAuthIssuer,
      if (matrixOAuthClientId != null)
        'matrixOAuthClientId': matrixOAuthClientId,
    };
  }

  static ServerConfigurationDto decode(String raw) {
    return ServerConfigurationDto.fromJson(
      jsonDecode(raw) as Map<String, dynamic>,
    );
  }
}
