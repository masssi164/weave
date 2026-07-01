import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:weave/core/l10n/app_locale_preference.dart';
import 'package:weave/core/l10n/app_locale_preference_repository.dart';
import 'package:weave/core/l10n/shared_preferences_app_locale_preference_repository.dart';
import 'package:weave/core/persistence/shared_preferences_store.dart';

final appLocalePreferenceRepositoryProvider =
    Provider<AppLocalePreferenceRepository>((ref) {
      return SharedPreferencesAppLocalePreferenceRepository(
        store: ref.watch(preferencesStoreProvider),
      );
    });

class AppLocalePreferenceController extends AsyncNotifier<AppLocaleSelection> {
  @override
  Future<AppLocaleSelection> build() async {
    final userPreference = await ref
        .watch(appLocalePreferenceRepositoryProvider)
        .loadUserPreference();
    return AppLocaleSelection(userPreference: userPreference);
  }

  Future<void> setUserPreference(AppLocalePreference preference) async {
    final previous = state.asData?.value ?? const AppLocaleSelection();
    final next = previous.copyWith(userPreference: preference);
    state = AsyncData(next);
    try {
      await ref
          .read(appLocalePreferenceRepositoryProvider)
          .saveUserPreference(preference);
    } catch (error, stackTrace) {
      state = AsyncError(error, stackTrace);
    }
  }
}

final appLocalePreferenceProvider =
    AsyncNotifierProvider<AppLocalePreferenceController, AppLocaleSelection>(
      AppLocalePreferenceController.new,
    );
