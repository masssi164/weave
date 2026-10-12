import 'dart:convert';

import 'package:weave/core/failures/app_failure.dart';
import 'package:weave/features/onboarding/domain/entities/member_handoff.dart';
import 'package:weave/features/onboarding/domain/use_cases/discover_organization_access.dart';
import 'package:weave/features/server_config/domain/entities/oidc_client_registration.dart';
import 'package:weave/features/server_config/domain/entities/oidc_provider_type.dart';
import 'package:weave/features/server_config/domain/entities/server_configuration.dart';
import 'package:weave/features/server_config/domain/entities/service_endpoints.dart';

/// Persists one canonical manifest; transport values are derived on load.
class ServerConfigurationDto {
  const ServerConfigurationDto._(this.configuration);

  factory ServerConfigurationDto.fromConfiguration(
    ServerConfiguration configuration,
  ) {
    final origin = configuration.organizationOrigin;
    final manifest = configuration.validatedManifest;
    final validatedAt = configuration.manifestValidatedAt;
    if (origin == null || manifest == null || validatedAt == null) {
      throw const AppFailure.validation(
        'A validated organization manifest is required before saving a profile.',
      );
    }
    final access = const OrganizationAccessParser().parse(origin);
    final canonical = AppStartDiscoveryClient.parseManifest(manifest, access);
    return ServerConfigurationDto._(
      canonical.toServerConfiguration(origin, validatedAt: validatedAt),
    );
  }

  factory ServerConfigurationDto.fromJson(Map<String, dynamic> json) {
    final origin = Uri.parse(json['organizationOrigin'] as String);
    final access = const OrganizationAccessParser().parse(origin);
    final manifest = json['validatedManifest'] as Map<String, dynamic>?;
    if (manifest != null) {
      final canonical = AppStartDiscoveryClient.parseManifest(manifest, access);
      return ServerConfigurationDto._(
        canonical.toServerConfiguration(
          origin,
          validatedAt: DateTime.parse(json['manifestValidatedAt'] as String),
        ),
      );
    }
    if (json['schemaVersion'] == 3) {
      throw const AppFailure.validation(
        'The saved organization manifest is missing.',
      );
    }
    // One-way migration input only. Refresh resolves the manifest before auth,
    // preserving the old identity binding while discarding provider overrides.
    return ServerConfigurationDto._(
      ServerConfiguration(
        organizationOrigin: origin,
        providerType: OidcProviderType.oidc,
        oidcIssuerUrl: Uri.parse(json['oidcIssuerUrl'] as String),
        oidcClientRegistration: OidcClientRegistration.manual(
          clientId: json['oidcClientId'] as String,
        ),
        serviceEndpoints: ServiceEndpoints(
          matrixHomeserverUrl: Uri.parse(json['matrixHomeserverUrl'] as String),
          backendApiBaseUrl: Uri.parse(json['backendApiBaseUrl'] as String),
        ),
      ),
    );
  }

  final ServerConfiguration configuration;

  ServerConfiguration toConfiguration() => configuration;

  String encode() => jsonEncode({
    'schemaVersion': 3,
    'organizationOrigin': configuration.organizationOrigin.toString(),
    'validatedManifest': configuration.validatedManifest,
    'manifestValidatedAt': configuration.manifestValidatedAt!
        .toUtc()
        .toIso8601String(),
  });
}
