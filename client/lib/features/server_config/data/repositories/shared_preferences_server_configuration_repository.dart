import 'dart:convert';

import 'package:weave/core/failures/app_failure.dart';
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
      if (decoded['schemaVersion'] != 2) {
        // A v1 Matrix URL could name a concrete provider. Keep the saved
        // value intact for support, but require a new OrgManifest handoff.
        return null;
      }
      final dto = ServerConfigurationDto.fromJson(decoded);
      final issuerUrl = _deriver.parseIssuerUrl(dto.oidcIssuerUrl);
      final configuration = dto.toConfiguration();
      final clientId = _requiredClientId(
        configuration.oidcClientRegistration.clientId,
      );
      final backendApiUrl = _deriver.parseServiceUrl(
        configuration.serviceEndpoints.backendApiBaseUrl.toString(),
        fieldName: 'the backend API URL',
      );
      final matrixUrl = _deriver.parseMatrixHomeserverUrl(
        configuration.serviceEndpoints.matrixHomeserverUrl.toString(),
      );
      return _validateMatrixOAuth(
        configuration.copyWith(
          oidcIssuerUrl: issuerUrl,
          oidcClientRegistration: configuration.oidcClientRegistration.copyWith(
            clientId: clientId,
          ),
          serviceEndpoints: configuration.serviceEndpoints.copyWith(
            matrixHomeserverUrl: matrixUrl,
            backendApiBaseUrl: backendApiUrl,
          ),
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
      final endpoints = configuration.serviceEndpoints;
      final normalized = configuration.copyWith(
        serviceEndpoints: endpoints.copyWith(
          matrixHomeserverUrl: _deriver.parseMatrixHomeserverUrl(
            endpoints.matrixHomeserverUrl.toString(),
          ),
        ),
      );
      final dto = ServerConfigurationDto.fromConfiguration(
        _validateMatrixOAuth(normalized),
      );
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

  ServerConfiguration _validateMatrixOAuth(ServerConfiguration configuration) {
    final issuer = configuration.matrixOAuthIssuer;
    final clientId = configuration.matrixOAuthClientId;
    if ((issuer == null) != (clientId == null)) {
      throw const AppFailure.validation(
        'The Matrix OAuth issuer and client ID must be configured together.',
      );
    }
    if (issuer == null) return configuration;
    if (issuer.scheme != 'https' ||
        issuer.host.isEmpty ||
        issuer.userInfo.isNotEmpty ||
        issuer.hasQuery ||
        issuer.hasFragment) {
      throw const AppFailure.validation(
        'The Matrix OAuth issuer must be a credential-free HTTPS URL.',
      );
    }
    final normalizedClientId = clientId!.trim();
    if (normalizedClientId.isEmpty ||
        normalizedClientId ==
            configuration.oidcClientRegistration.clientId.trim()) {
      throw const AppFailure.validation(
        'Matrix OAuth requires a separate registered client ID.',
      );
    }
    return configuration.copyWith(matrixOAuthClientId: normalizedClientId);
  }
}
