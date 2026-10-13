import 'package:riverpod_annotation/riverpod_annotation.dart';
import 'package:weave/core/bootstrap/domain/bootstrap_state.dart';
import 'package:weave/features/app/presentation/providers/app_application_providers.dart';

part 'app_bootstrap_provider.g.dart';

@Riverpod(keepAlive: true)
class AppBootstrap extends _$AppBootstrap {
  @override
  Future<BootstrapState> build() => _resolve();

  Future<void> retry() async {
    state = const AsyncLoading<BootstrapState>();
    try {
      state = AsyncData(await _resolve());
    } catch (error, stackTrace) {
      state = AsyncError(error, stackTrace);
      rethrow;
    }
  }

  Future<BootstrapState> _resolve() =>
      ref.read(resolveAppBootstrapProvider).call();
}

@Riverpod(keepAlive: true)
BootstrapState resolvedBootstrapState(Ref ref) {
  final bootstrap = ref.watch(appBootstrapProvider);
  if (bootstrap.isLoading) return const BootstrapState.loading();
  return bootstrap.maybeWhen(
    data: (state) => state,
    orElse: () => const BootstrapState.loading(),
  );
}
