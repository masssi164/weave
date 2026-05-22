import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:weave/core/persistence/shared_preferences_store.dart';
import 'package:weave/core/theme/app_theme_preference.dart';
import 'package:weave/core/theme/app_theme_preference_repository.dart';
import 'package:weave/core/theme/shared_preferences_app_theme_preference_repository.dart';

final appThemePreferenceRepositoryProvider =
    Provider<AppThemePreferenceRepository>((ref) {
      return SharedPreferencesAppThemePreferenceRepository(
        store: ref.watch(preferencesStoreProvider),
      );
    });

class AppThemePreferenceController extends AsyncNotifier<AppThemeSelection> {
  @override
  Future<AppThemeSelection> build() async {
    final userPreference = await ref
        .watch(appThemePreferenceRepositoryProvider)
        .loadUserPreference();
    return AppThemeSelection(userPreference: userPreference);
  }

  Future<void> setUserPreference(AppThemePreference preference) async {
    final previous = state.asData?.value ?? const AppThemeSelection();
    final next = previous.copyWith(userPreference: preference);
    state = AsyncData(next);
    try {
      await ref
          .read(appThemePreferenceRepositoryProvider)
          .saveUserPreference(preference);
    } catch (error, stackTrace) {
      state = AsyncError(error, stackTrace);
    }
  }
}

final appThemePreferenceProvider =
    AsyncNotifierProvider<AppThemePreferenceController, AppThemeSelection>(
      AppThemePreferenceController.new,
    );
