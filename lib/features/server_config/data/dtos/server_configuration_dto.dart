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
    required this.nextcloudBaseUrl,
  });

  factory ServerConfigurationDto.fromConfiguration(
    ServerConfiguration configuration,
  ) {
    return ServerConfigurationDto(
      providerType: configuration.providerType.name,
      oidcIssuerUrl: configuration.oidcIssuerUrl.toString(),
      oidcClientRegistrationMode:
          configuration.oidcClientRegistration.mode.name,
      oidcClientId: configuration.oidcClientRegistration.clientId,
      matrixHomeserverUrl: configuration.serviceEndpoints.matrixHomeserverUrl
          .toString(),
      nextcloudBaseUrl: configuration.serviceEndpoints.nextcloudBaseUrl
          .toString(),
    );
  }

  factory ServerConfigurationDto.fromJson(Map<String, dynamic> json) {
    return ServerConfigurationDto(
      providerType: json['providerType'] as String,
      oidcIssuerUrl: json['oidcIssuerUrl'] as String,
      oidcClientRegistrationMode:
          (json['oidcClientRegistrationMode'] as String?) ?? 'manual',
      oidcClientId: (json['oidcClientId'] as String?) ?? '',
      matrixHomeserverUrl: json['matrixHomeserverUrl'] as String,
      nextcloudBaseUrl: json['nextcloudBaseUrl'] as String,
    );
  }

  final String providerType;
  final String oidcIssuerUrl;
  final String oidcClientRegistrationMode;
  final String oidcClientId;
  final String matrixHomeserverUrl;
  final String nextcloudBaseUrl;

  ServerConfiguration toConfiguration() {
    return ServerConfiguration(
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
        nextcloudBaseUrl: Uri.parse(nextcloudBaseUrl),
      ),
    );
  }

  String encode() => jsonEncode(toJson());

  Map<String, dynamic> toJson() {
    return {
      'providerType': providerType,
      'oidcIssuerUrl': oidcIssuerUrl,
      'oidcClientRegistrationMode': oidcClientRegistrationMode,
      'oidcClientId': oidcClientId,
      'matrixHomeserverUrl': matrixHomeserverUrl,
      'nextcloudBaseUrl': nextcloudBaseUrl,
    };
  }

  static ServerConfigurationDto decode(String raw) {
    return ServerConfigurationDto.fromJson(
      jsonDecode(raw) as Map<String, dynamic>,
    );
  }
}
