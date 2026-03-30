import 'package:riverpod_annotation/riverpod_annotation.dart';
import 'package:weave/core/bootstrap/domain/bootstrap_state.dart';
import 'package:weave/core/failures/app_failure.dart';
import 'package:weave/core/persistence/shared_preferences_store.dart';
import 'package:weave/features/server_config/data/repositories/shared_preferences_server_configuration_repository.dart';

part 'app_bootstrap_provider.g.dart';

@Riverpod(keepAlive: true)
class AppBootstrap extends _$AppBootstrap {
  @override
  Future<BootstrapState> build() => _resolve();

  Future<void> retry() async {
    state = const AsyncLoading();
    state = AsyncData(await _resolve());
  }

  void markReady() {
    state = const AsyncData(BootstrapState.ready());
  }

  Future<BootstrapState> _resolve() async {
    final repository = ref.read(serverConfigurationRepositoryProvider);
    final preferencesStore = ref.read(preferencesStoreProvider);

    try {
      final configuration = await repository.loadConfiguration();
      if (configuration != null) {
        return const BootstrapState.ready();
      }

      await preferencesStore.getBool(legacySetupCompleteKey);
      return const BootstrapState.needsSetup();
    } on AppFailure catch (failure) {
      return BootstrapState.error(failure);
    } catch (error) {
      return BootstrapState.error(
        AppFailure.bootstrap(
          'Unable to bootstrap the application.',
          cause: error,
        ),
      );
    }
  }
}

@Riverpod(keepAlive: true)
BootstrapState resolvedBootstrapState(Ref ref) {
  return ref
      .watch(appBootstrapProvider)
      .maybeWhen(
        data: (state) => state,
        orElse: () => const BootstrapState.loading(),
      );
}
