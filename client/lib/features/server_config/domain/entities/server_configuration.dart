import 'package:weave/features/server_config/domain/entities/oidc_client_registration.dart';
import 'package:weave/features/server_config/domain/entities/oidc_provider_type.dart';
import 'package:weave/features/server_config/domain/entities/service_endpoints.dart';

class ServerConfiguration {
  const ServerConfiguration({
    required this.providerType,
    required this.oidcIssuerUrl,
    required this.oidcClientRegistration,
    required this.serviceEndpoints,
    this.organizationOrigin,
    this.validatedManifest,
    this.manifestValidatedAt,
    this.matrixOAuthIssuer,
    this.matrixOAuthClientId,
  });

  final Uri? organizationOrigin;
  final Map<String, dynamic>? validatedManifest;
  final DateTime? manifestValidatedAt;

  final OidcProviderType providerType;
  final Uri oidcIssuerUrl;
  final OidcClientRegistration oidcClientRegistration;
  final ServiceEndpoints serviceEndpoints;
  final Uri? matrixOAuthIssuer;
  final String? matrixOAuthClientId;

  bool get hasCompleteAuthConfiguration => oidcClientRegistration.isComplete;
  bool get hasMatrixOAuthConfiguration =>
      matrixOAuthIssuer != null && matrixOAuthClientId != null;

  ServerConfiguration copyWith({
    Uri? organizationOrigin,
    Map<String, dynamic>? validatedManifest,
    DateTime? manifestValidatedAt,
    OidcProviderType? providerType,
    Uri? oidcIssuerUrl,
    OidcClientRegistration? oidcClientRegistration,
    ServiceEndpoints? serviceEndpoints,
    Uri? matrixOAuthIssuer,
    String? matrixOAuthClientId,
  }) {
    return ServerConfiguration(
      organizationOrigin: organizationOrigin ?? this.organizationOrigin,
      validatedManifest: validatedManifest ?? this.validatedManifest,
      manifestValidatedAt: manifestValidatedAt ?? this.manifestValidatedAt,
      providerType: providerType ?? this.providerType,
      oidcIssuerUrl: oidcIssuerUrl ?? this.oidcIssuerUrl,
      oidcClientRegistration:
          oidcClientRegistration ?? this.oidcClientRegistration,
      serviceEndpoints: serviceEndpoints ?? this.serviceEndpoints,
      matrixOAuthIssuer: matrixOAuthIssuer ?? this.matrixOAuthIssuer,
      matrixOAuthClientId: matrixOAuthClientId ?? this.matrixOAuthClientId,
    );
  }
}
