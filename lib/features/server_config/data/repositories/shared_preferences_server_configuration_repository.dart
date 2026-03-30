import 'package:riverpod_annotation/riverpod_annotation.dart';
import 'package:weave/core/failures/app_failure.dart';
import 'package:weave/core/persistence/preferences_store.dart';
import 'package:weave/core/persistence/shared_preferences_store.dart';
import 'package:weave/features/server_config/data/dtos/server_configuration_dto.dart';
import 'package:weave/features/server_config/data/services/service_endpoint_deriver.dart';
import 'package:weave/features/server_config/domain/entities/server_configuration.dart';
import 'package:weave/features/server_config/domain/repositories/server_configuration_repository.dart';

part 'shared_preferences_server_configuration_repository.g.dart';

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

      final configuration = ServerConfigurationDto.decode(
        raw,
      ).toConfiguration();

      // Re-validate persisted values on load so presentation never receives
      // malformed configuration from storage.
      final issuerUrl = _deriver.parseIssuerUrl(
        configuration.oidcIssuerUrl.toString(),
      );
      final matrixUrl = _deriver.parseServiceUrl(
        configuration.serviceEndpoints.matrixHomeserverUrl.toString(),
        fieldName: 'the Matrix homeserver URL',
      );
      final nextcloudUrl = _deriver.parseServiceUrl(
        configuration.serviceEndpoints.nextcloudBaseUrl.toString(),
        fieldName: 'the Nextcloud URL',
      );

      return configuration.copyWith(
        oidcIssuerUrl: issuerUrl,
        serviceEndpoints: configuration.serviceEndpoints.copyWith(
          matrixHomeserverUrl: matrixUrl,
          nextcloudBaseUrl: nextcloudUrl,
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
}

@Riverpod(keepAlive: true)
ServerConfigurationRepository serverConfigurationRepository(Ref ref) {
  final store = ref.watch(preferencesStoreProvider);
  final deriver = ref.watch(serviceEndpointDeriverProvider);
  return SharedPreferencesServerConfigurationRepository(
    store: store,
    deriver: deriver,
  );
}
