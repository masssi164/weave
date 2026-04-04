import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:riverpod/riverpod.dart';
import 'package:weave/core/persistence/shared_preferences_store.dart';
import 'package:weave/features/server_config/data/repositories/shared_preferences_server_configuration_repository.dart';
import 'package:weave/features/server_config/presentation/providers/server_configuration_repository_provider.dart';

import '../../../../helpers/in_memory_stores.dart';
import '../../../../helpers/server_config_test_data.dart';

void main() {
  group('SharedPreferencesServerConfigurationRepository', () {
    test('saves and reloads the same configuration', () async {
      final store = InMemoryPreferencesStore();
      final container = ProviderContainer.test(
        overrides: [preferencesStoreProvider.overrideWith((ref) => store)],
      );
      addTearDown(container.dispose);
      final repository = container.read(serverConfigurationRepositoryProvider);
      final configuration = buildTestConfiguration();

      await repository.saveConfiguration(configuration);
      final loaded = await repository.loadConfiguration();

      expect(loaded?.providerType, configuration.providerType);
      expect(
        loaded?.oidcIssuerUrl.toString(),
        configuration.oidcIssuerUrl.toString(),
      );
      expect(
        loaded?.oidcClientRegistration.clientId,
        configuration.oidcClientRegistration.clientId,
      );
      expect(
        loaded?.serviceEndpoints.matrixHomeserverUrl.toString(),
        configuration.serviceEndpoints.matrixHomeserverUrl.toString(),
      );
      expect(
        loaded?.serviceEndpoints.nextcloudBaseUrl.toString(),
        configuration.serviceEndpoints.nextcloudBaseUrl.toString(),
      );
    });

    test('removes the legacy setup key when saving', () async {
      final store = InMemoryPreferencesStore({legacySetupCompleteKey: true});
      final container = ProviderContainer.test(
        overrides: [preferencesStoreProvider.overrideWith((ref) => store)],
      );
      addTearDown(container.dispose);
      final repository = container.read(serverConfigurationRepositoryProvider);

      await repository.saveConfiguration(buildTestConfiguration());

      expect(await store.getBool(legacySetupCompleteKey), isNull);
    });

    test('clears the stored configuration', () async {
      final store = InMemoryPreferencesStore(buildStoredConfiguration());
      final container = ProviderContainer.test(
        overrides: [preferencesStoreProvider.overrideWith((ref) => store)],
      );
      addTearDown(container.dispose);
      final repository = container.read(serverConfigurationRepositoryProvider);

      await repository.clearConfiguration();

      expect(await store.getString(serverConfigurationStorageKey), isNull);
    });

    test('normalizes a blank stored client ID to weave-app', () async {
      final store = InMemoryPreferencesStore(
        buildStoredConfiguration(clientId: ''),
      );
      final container = ProviderContainer.test(
        overrides: [preferencesStoreProvider.overrideWith((ref) => store)],
      );
      addTearDown(container.dispose);
      final repository = container.read(serverConfigurationRepositoryProvider);

      final loaded = await repository.loadConfiguration();

      expect(loaded?.oidcClientRegistration.clientId, 'weave-app');
    });

    test('normalizes a missing stored client ID to weave-app', () async {
      final raw = jsonEncode({
        'providerType': 'authentik',
        'oidcIssuerUrl': 'https://auth.home.internal',
        'oidcClientRegistrationMode': 'manual',
        'matrixHomeserverUrl': 'https://matrix.home.internal',
        'nextcloudBaseUrl': 'https://files.home.internal',
      });
      final store = InMemoryPreferencesStore({
        serverConfigurationStorageKey: raw,
      });
      final container = ProviderContainer.test(
        overrides: [preferencesStoreProvider.overrideWith((ref) => store)],
      );
      addTearDown(container.dispose);
      final repository = container.read(serverConfigurationRepositoryProvider);

      final loaded = await repository.loadConfiguration();

      expect(loaded?.oidcClientRegistration.clientId, 'weave-app');
    });
  });
}
