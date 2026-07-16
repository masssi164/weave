import 'package:weave/core/failures/app_failure.dart';
import 'package:weave/core/persistence/preferences_store.dart';
import 'package:weave/features/auth/domain/entities/oidc_constants.dart';
import 'package:weave/features/server_config/data/dtos/server_configuration_dto.dart';
import 'package:weave/features/server_config/data/services/service_endpoint_deriver.dart';
import 'package:weave/features/server_config/domain/entities/server_configuration.dart';
import 'package:weave/features/server_config/domain/repositories/server_configuration_repository.dart';

const serverConfigurationStorageKey = 'server_configuration_v1';
const legacySetupCompleteKey = 'setup_complete';

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
      final defaults = _deriver.derive(issuerUrl);
      final configuration = dto.toConfiguration(
        fallbackBackendApiBaseUrl: defaults.backendApiBaseUrl,
      );
      final normalizedClientId = _normalizedClientId(
        configuration.oidcClientRegistration.clientId,
      );
      final backendApiUrl = _deriver.parseServiceUrl(
        configuration.serviceEndpoints.backendApiBaseUrl.toString(),
        fieldName: 'the backend API URL',
      );
      // Matrix is a Weave API-origin projection. Ignore stale provider-shaped
      // values from older on-device configuration without clearing the profile.
      final matrixUrl = _deriver.matrixFacadeFromBackendApi(backendApiUrl);
      final filesUrl = _deriver.filesFacadeFromBackendApi(backendApiUrl);

      // Older clients wrote this completion flag before entering their global
      // setup/readiness flow. It is not an application-entry contract. Remove
      // it opportunistically after the durable organization configuration has
      // been validated, without allowing cleanup failure to block sign-in.
      await _removeObsoleteSetupFlag();

      return configuration.copyWith(
        oidcIssuerUrl: issuerUrl,
        oidcClientRegistration: configuration.oidcClientRegistration.copyWith(
          clientId: normalizedClientId,
        ),
        serviceEndpoints: configuration.serviceEndpoints.copyWith(
          matrixHomeserverUrl: matrixUrl,
          nextcloudBaseUrl: filesUrl,
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
          matrixHomeserverUrl: _deriver.matrixFacadeFromBackendApi(
            endpoints.backendApiBaseUrl,
          ),
          nextcloudBaseUrl: _deriver.filesFacadeFromBackendApi(
            endpoints.backendApiBaseUrl,
          ),
        ),
      );
      final dto = ServerConfigurationDto.fromConfiguration(normalized);
      await _store.setString(serverConfigurationStorageKey, dto.encode());
      await _store.remove(legacySetupCompleteKey);
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
      await _store.remove(legacySetupCompleteKey);
    } catch (error) {
      throw AppFailure.storage(
        'Failed to clear the saved server configuration.',
        cause: error,
      );
    }
  }

  String _normalizedClientId(String clientId) {
    final trimmed = clientId.trim();
    return trimmed.isEmpty ? oidcDefaultClientId : trimmed;
  }

  Future<void> _removeObsoleteSetupFlag() async {
    try {
      await _store.remove(legacySetupCompleteKey);
    } catch (_) {
      // Obsolete preference cleanup is best effort. The validated current
      // configuration remains authoritative for bootstrap.
    }
  }
}
