import 'package:weave/features/server_config/domain/entities/oidc_client_registration.dart';
import 'package:weave/features/server_config/domain/entities/oidc_provider_type.dart';
import 'package:weave/features/server_config/domain/entities/service_endpoints.dart';

class ServerConfiguration {
  const ServerConfiguration({
    required this.providerType,
    required this.oidcIssuerUrl,
    required this.oidcClientRegistration,
    required this.serviceEndpoints,
  });

  final OidcProviderType providerType;
  final Uri oidcIssuerUrl;
  final OidcClientRegistration oidcClientRegistration;
  final ServiceEndpoints serviceEndpoints;

  bool get hasCompleteAuthConfiguration => oidcClientRegistration.isComplete;

  ServerConfiguration copyWith({
    OidcProviderType? providerType,
    Uri? oidcIssuerUrl,
    OidcClientRegistration? oidcClientRegistration,
    ServiceEndpoints? serviceEndpoints,
  }) {
    return ServerConfiguration(
      providerType: providerType ?? this.providerType,
      oidcIssuerUrl: oidcIssuerUrl ?? this.oidcIssuerUrl,
      oidcClientRegistration:
          oidcClientRegistration ?? this.oidcClientRegistration,
      serviceEndpoints: serviceEndpoints ?? this.serviceEndpoints,
    );
  }
}
