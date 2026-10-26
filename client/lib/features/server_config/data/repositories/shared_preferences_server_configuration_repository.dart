import 'dart:convert';

import 'package:weave/core/failures/app_failure.dart';
import 'package:weave/features/onboarding/domain/entities/member_handoff.dart';
import 'package:weave/core/persistence/preferences_store.dart';
import 'package:weave/features/server_config/data/dtos/server_configuration_dto.dart';
import 'package:weave/features/server_config/data/services/service_endpoint_deriver.dart';
import 'package:weave/features/server_config/domain/entities/server_configuration.dart';
import 'package:weave/features/server_config/domain/repositories/server_configuration_repository.dart';

const serverConfigurationStorageKey = 'server_configuration_v1';

class SharedPreferencesServerConfigurationRepository
    implements ServerConfigurationRepository {
  const SharedPreferencesServerConfigurationRepository({
    required PreferencesStore store,
    required ServiceEndpointDeriver deriver,
  }) : _store = store,
       _deriver = deriver;

  final PreferencesStore _store;
  final ServiceEndpointDeriver _deriver;

  @override
  Future<ServerConfiguration?> loadConfiguration() async {
    try {
      final raw = await _store.getString(serverConfigurationStorageKey);
      if (raw == null || raw.isEmpty) {
        return null;
      }

      // Re-validate persisted values on load so presentation never receives
      // malformed configuration from storage.
      final decoded = jsonDecode(raw);
      if (decoded is! Map<String, dynamic>) {
        throw const FormatException('Invalid saved configuration');
      }
      if (![1, 2, 3].contains(decoded['schemaVersion']) ||
          decoded['organizationOrigin'] == null) {
        // A v1 Matrix URL could name a concrete provider. Keep the saved
        // value intact for support, but require a new OrgManifest handoff.
        return null;
      }
      final dto = ServerConfigurationDto.fromJson(decoded);
      final issuerUrl = _deriver.parseIssuerUrl(
        dto.configuration.oidcIssuerUrl.toString(),
      );
      final configuration = dto.toConfiguration();
      if (configuration.organizationOrigin != null) {
        const OrganizationAccessParser().parse(
          configuration.organizationOrigin!,
        );
      }
      final clientId = _requiredClientId(
        configuration.oidcClientRegistration.clientId,
      );
      if (configuration.organizationOrigin != null &&
          configuration.validatedManifest == null) {
        // Origin-bearing legacy profiles are only migration input. Bootstrap
        // resolves OrgManifest before authentication or any endpoint is used.
        return configuration.copyWith(
          oidcIssuerUrl: issuerUrl,
          oidcClientRegistration: configuration.oidcClientRegistration.copyWith(
            clientId: clientId,
          ),
        );
      }
      final backendApiUrl = _deriver.parseServiceUrl(
        configuration.serviceEndpoints.backendApiBaseUrl.toString(),
        fieldName: 'the backend API URL',
      );
      final matrixOrigin = configuration.serviceEndpoints.matrixHomeserverUrl;
      final matrixUrl = matrixOrigin == null
          ? null
          : _deriver.parseMatrixHomeserverUrl(matrixOrigin.toString());
      return configuration.copyWith(
        oidcIssuerUrl: issuerUrl,
        oidcClientRegistration: configuration.oidcClientRegistration.copyWith(
          clientId: clientId,
        ),
        serviceEndpoints: configuration.serviceEndpoints.copyWith(
          matrixHomeserverUrl: matrixUrl,
          backendApiBaseUrl: backendApiUrl,
        ),
      );
    } on AppFailure {
      rethrow;
    } catch (error) {
      throw AppFailure.storage(
        'Failed to read the saved server configuration.',
        cause: error,
      );
    }
  }

  @override
  Future<void> saveConfiguration(ServerConfiguration configuration) async {
    try {
      final dto = ServerConfigurationDto.fromConfiguration(configuration);
      await _store.setString(serverConfigurationStorageKey, dto.encode());
    } on AppFailure {
      rethrow;
    } catch (error) {
      throw AppFailure.storage(
        'Failed to save the server configuration.',
        cause: error,
      );
    }
  }

  @override
  Future<void> clearConfiguration() async {
    try {
      await _store.remove(serverConfigurationStorageKey);
    } catch (error) {
      throw AppFailure.storage(
        'Failed to clear the saved server configuration.',
        cause: error,
      );
    }
  }

  String _requiredClientId(String clientId) {
    final trimmed = clientId.trim();
    if (trimmed.isEmpty) {
      throw const AppFailure.validation(
        'The saved organization profile is missing its OIDC client ID.',
      );
    }
    return trimmed;
  }
}
