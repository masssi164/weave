import 'dart:convert';

import 'package:weave/features/server_config/domain/entities/oidc_provider_type.dart';
import 'package:weave/features/server_config/domain/entities/server_configuration.dart';
import 'package:weave/features/server_config/domain/entities/service_endpoints.dart';

class ServerConfigurationDto {
  const ServerConfigurationDto({
    required this.providerType,
    required this.oidcIssuerUrl,
    required this.matrixHomeserverUrl,
    required this.nextcloudBaseUrl,
  });

  factory ServerConfigurationDto.fromConfiguration(
    ServerConfiguration configuration,
  ) {
    return ServerConfigurationDto(
      providerType: configuration.providerType.name,
      oidcIssuerUrl: configuration.oidcIssuerUrl.toString(),
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
      matrixHomeserverUrl: json['matrixHomeserverUrl'] as String,
      nextcloudBaseUrl: json['nextcloudBaseUrl'] as String,
    );
  }

  final String providerType;
  final String oidcIssuerUrl;
  final String matrixHomeserverUrl;
  final String nextcloudBaseUrl;

  ServerConfiguration toConfiguration() {
    return ServerConfiguration(
      providerType: OidcProviderType.values.byName(providerType),
      oidcIssuerUrl: Uri.parse(oidcIssuerUrl),
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
