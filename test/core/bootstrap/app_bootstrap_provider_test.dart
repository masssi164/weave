import 'package:flutter_test/flutter_test.dart';
import 'package:riverpod/riverpod.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:weave/core/bootstrap/domain/bootstrap_state.dart';
import 'package:weave/core/bootstrap/presentation/providers/app_bootstrap_provider.dart';
import 'package:weave/core/failures/app_failure.dart';
import 'package:weave/features/server_config/data/repositories/shared_preferences_server_configuration_repository.dart';
import 'package:weave/features/server_config/domain/entities/server_configuration.dart';
import 'package:weave/features/server_config/domain/repositories/server_configuration_repository.dart';

import '../../helpers/server_config_test_data.dart';

class _FakeServerConfigurationRepository
    implements ServerConfigurationRepository {
  _FakeServerConfigurationRepository({required this.loadConfigurationHandler});

  Future<ServerConfiguration?> Function() loadConfigurationHandler;

  @override
  Future<ServerConfiguration?> loadConfiguration() {
    return loadConfigurationHandler();
  }

  @override
  Future<void> saveConfiguration(ServerConfiguration configuration) async {}
}

void main() {
  group('AppBootstrap', () {
    setUp(() {
      SharedPreferences.setMockInitialValues({});
    });

    test('returns needsSetup when no saved configuration exists', () async {
      final container = ProviderContainer.test();
      addTearDown(container.dispose);

      final bootstrapState = await container.read(appBootstrapProvider.future);

      expect(bootstrapState.phase, BootstrapPhase.needsSetup);
    });

    test('returns ready when a saved configuration exists', () async {
      SharedPreferences.setMockInitialValues(buildStoredConfiguration());
      final container = ProviderContainer.test();
      addTearDown(container.dispose);

      final bootstrapState = await container.read(appBootstrapProvider.future);

      expect(bootstrapState.phase, BootstrapPhase.ready);
    });

    test('returns error when configuration loading fails', () async {
      final repository = _FakeServerConfigurationRepository(
        loadConfigurationHandler: () async {
          throw const AppFailure.storage('Broken preferences.');
        },
      );
      final container = ProviderContainer.test(
        overrides: [
          serverConfigurationRepositoryProvider.overrideWith(
            (ref) => repository,
          ),
        ],
      );
      addTearDown(container.dispose);

      final bootstrapState = await container.read(appBootstrapProvider.future);

      expect(bootstrapState.phase, BootstrapPhase.error);
      expect(bootstrapState.failure?.message, 'Broken preferences.');
    });

    test('retry re-reads storage and recovers to ready', () async {
      var shouldFail = true;
      final repository = _FakeServerConfigurationRepository(
        loadConfigurationHandler: () async {
          if (shouldFail) {
            throw const AppFailure.storage('Temporary read failure.');
          }

          return buildTestConfiguration();
        },
      );
      final container = ProviderContainer.test(
        overrides: [
          serverConfigurationRepositoryProvider.overrideWith(
            (ref) => repository,
          ),
        ],
      );
      addTearDown(container.dispose);

      final initialState = await container.read(appBootstrapProvider.future);
      expect(initialState.phase, BootstrapPhase.error);

      shouldFail = false;
      await container.read(appBootstrapProvider.notifier).retry();

      expect(
        container.read(appBootstrapProvider).requireValue.phase,
        BootstrapPhase.ready,
      );
    });
  });
}
