import 'package:flutter_test/flutter_test.dart';
import 'package:riverpod/riverpod.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:weave/features/server_config/data/repositories/shared_preferences_server_configuration_repository.dart';

import '../../../../helpers/server_config_test_data.dart';

void main() {
  group('SharedPreferencesServerConfigurationRepository', () {
    setUp(() {
      SharedPreferences.setMockInitialValues({});
    });

    test('saves and reloads the same configuration', () async {
      final container = ProviderContainer.test();
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
        loaded?.serviceEndpoints.matrixHomeserverUrl.toString(),
        configuration.serviceEndpoints.matrixHomeserverUrl.toString(),
      );
      expect(
        loaded?.serviceEndpoints.nextcloudBaseUrl.toString(),
        configuration.serviceEndpoints.nextcloudBaseUrl.toString(),
      );
    });

    test('removes the legacy setup key when saving', () async {
      SharedPreferences.setMockInitialValues({legacySetupCompleteKey: true});
      final container = ProviderContainer.test();
      addTearDown(container.dispose);
      final repository = container.read(serverConfigurationRepositoryProvider);

      await repository.saveConfiguration(buildTestConfiguration());

      final prefs = await SharedPreferences.getInstance();
      expect(prefs.getBool(legacySetupCompleteKey), isNull);
    });
  });
}
