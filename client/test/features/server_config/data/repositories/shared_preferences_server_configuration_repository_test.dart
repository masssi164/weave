import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:riverpod/riverpod.dart';
import 'package:weave/core/failures/app_failure.dart';
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
        'https://api.home.internal/dav/files',
      );
      expect(
        loaded?.serviceEndpoints.backendApiBaseUrl.toString(),
        configuration.serviceEndpoints.backendApiBaseUrl.toString(),
      );
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

    test(
      'rejects a saved profile without the canonical backend API URL',
      () async {
        final store = InMemoryPreferencesStore({
          serverConfigurationStorageKey: encodeTestConfiguration(
            backendApiBaseUrl: null,
          ),
        });
        final container = ProviderContainer.test(
          overrides: [preferencesStoreProvider.overrideWith((ref) => store)],
        );
        addTearDown(container.dispose);
        final repository = container.read(
          serverConfigurationRepositoryProvider,
        );

        await expectLater(
          repository.loadConfiguration,
          throwsA(isA<AppFailure>()),
        );
      },
    );

    test('rejects a blank stored client ID', () async {
      final store = InMemoryPreferencesStore(
        buildStoredConfiguration(clientId: ''),
      );
      final container = ProviderContainer.test(
        overrides: [preferencesStoreProvider.overrideWith((ref) => store)],
      );
      addTearDown(container.dispose);
      final repository = container.read(serverConfigurationRepositoryProvider);

      await expectLater(
        repository.loadConfiguration,
        throwsA(isA<AppFailure>()),
      );
    });

    test('rejects a missing stored client ID', () async {
      final raw = jsonEncode({
        'providerType': 'oidc',
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

      await expectLater(
        repository.loadConfiguration,
        throwsA(isA<AppFailure>()),
      );
    });

    test('rejects stale provider-shaped Matrix state', () async {
      final store = InMemoryPreferencesStore(
        buildStoredConfiguration(
          matrixHomeserverUrl: 'https://matrix.home.internal',
          backendApiBaseUrl: 'https://api.home.internal/api',
        ),
      );
      final container = ProviderContainer.test(
        overrides: [preferencesStoreProvider.overrideWith((ref) => store)],
      );
      addTearDown(container.dispose);
      final repository = container.read(serverConfigurationRepositoryProvider);

      await expectLater(
        repository.loadConfiguration,
        throwsA(isA<AppFailure>()),
      );
    });
  });
}
