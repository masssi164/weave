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
      final dto = ServerConfigurationDto.decode(raw);
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
      final endpoints = configuration.serviceEndpoints;
      final normalized = configuration.copyWith(
        serviceEndpoints: endpoints.copyWith(
          matrixHomeserverUrl: _deriver.parseMatrixHomeserverUrl(
            endpoints.matrixHomeserverUrl.toString(),
          ),
        ),
      );
      final dto = ServerConfigurationDto.fromConfiguration(normalized);
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
