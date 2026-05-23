import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:weave/core/persistence/shared_preferences_store.dart';
import 'package:weave/features/shell/data/repositories/shared_preferences_shell_module_preferences_repository.dart';
import 'package:weave/features/shell/domain/entities/shell_module.dart';
import 'package:weave/features/shell/domain/entities/shell_module_preferences.dart';
import 'package:weave/features/shell/domain/repositories/shell_module_preferences_repository.dart';

final shellModulePreferencesRepositoryProvider =
    Provider<ShellModulePreferencesRepository>((ref) {
      return SharedPreferencesShellModulePreferencesRepository(
        store: ref.watch(preferencesStoreProvider),
      );
    });

class ShellModulePreferencesController
    extends AsyncNotifier<ShellModulePreferences> {
  @override
  Future<ShellModulePreferences> build() {
    return ref
        .watch(shellModulePreferencesRepositoryProvider)
        .loadPreferences();
  }

  Future<void> setModuleVisibility({
    required ShellModule module,
    required bool isVisible,
  }) async {
    final previous = state.asData?.value ?? const ShellModulePreferences();
    final next = previous.setVisibility(module: module, isVisible: isVisible);
    await _save(next);
  }

  Future<void> moveModule({
    required ShellModule module,
    required int delta,
  }) async {
    final previous = state.asData?.value ?? const ShellModulePreferences();
    final next = previous.moveModule(module: module, delta: delta);
    await _save(next);
  }

  Future<void> _save(ShellModulePreferences preferences) async {
    state = AsyncData(preferences);
    try {
      await ref
          .read(shellModulePreferencesRepositoryProvider)
          .savePreferences(preferences);
    } catch (error, stackTrace) {
      state = AsyncError(error, stackTrace);
    }
  }
}

final shellModulePreferencesProvider =
    AsyncNotifierProvider<
      ShellModulePreferencesController,
      ShellModulePreferences
    >(ShellModulePreferencesController.new);
