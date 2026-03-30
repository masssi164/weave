import 'package:weave/features/server_config/domain/entities/oidc_provider_type.dart';
import 'package:weave/features/server_config/domain/entities/service_endpoints.dart';

class ServerConfiguration {
  const ServerConfiguration({
    required this.providerType,
    required this.oidcIssuerUrl,
    required this.serviceEndpoints,
  });

  final OidcProviderType providerType;
  final Uri oidcIssuerUrl;
  final ServiceEndpoints serviceEndpoints;

  ServerConfiguration copyWith({
    OidcProviderType? providerType,
    Uri? oidcIssuerUrl,
    ServiceEndpoints? serviceEndpoints,
  }) {
    return ServerConfiguration(
      providerType: providerType ?? this.providerType,
      oidcIssuerUrl: oidcIssuerUrl ?? this.oidcIssuerUrl,
      serviceEndpoints: serviceEndpoints ?? this.serviceEndpoints,
    );
  }
}
