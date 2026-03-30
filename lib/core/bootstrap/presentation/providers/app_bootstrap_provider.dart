import 'package:riverpod_annotation/riverpod_annotation.dart';
import 'package:weave/core/bootstrap/domain/bootstrap_state.dart';
import 'package:weave/core/failures/app_failure.dart';
import 'package:weave/features/auth/data/repositories/oidc_auth_session_repository.dart';
import 'package:weave/features/auth/domain/entities/auth_configuration.dart';
import 'package:weave/features/auth/domain/entities/auth_failure.dart';
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

  Future<BootstrapState> _resolve() async {
    final repository = ref.read(serverConfigurationRepositoryProvider);
    final authRepository = ref.read(authSessionRepositoryProvider);

    try {
      final configuration = await repository.loadConfiguration();
      if (configuration == null ||
          !configuration.hasCompleteAuthConfiguration) {
        return const BootstrapState.needsSetup();
      }

      final authState = await authRepository.restoreSession(
        AuthConfiguration(
          issuer: configuration.oidcIssuerUrl,
          clientId: configuration.oidcClientRegistration.clientId.trim(),
        ),
      );
      if (authState.isAuthenticated) {
        return const BootstrapState.ready();
      }

      return const BootstrapState.needsSignIn();
    } on AuthFailure catch (failure) {
      return BootstrapState.error(
        AppFailure.storage(failure.message, cause: failure.cause),
      );
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
