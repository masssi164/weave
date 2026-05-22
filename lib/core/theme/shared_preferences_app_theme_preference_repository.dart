import 'package:weave/core/persistence/preferences_store.dart';
import 'package:weave/core/theme/app_theme_preference.dart';
import 'package:weave/core/theme/app_theme_preference_repository.dart';

const appThemePreferenceStorageKey = 'theme.userPreference.v1';

class SharedPreferencesAppThemePreferenceRepository
    implements AppThemePreferenceRepository {
  const SharedPreferencesAppThemePreferenceRepository({
    required PreferencesStore store,
  }) : _store = store;

  final PreferencesStore _store;

  @override
  Future<AppThemePreference?> loadUserPreference() async {
    final raw = await _store.getString(appThemePreferenceStorageKey);
    if (raw == null || raw.isEmpty) {
      return null;
    }
    return AppThemePreference.fromStorageKey(raw);
  }

  @override
  Future<void> saveUserPreference(AppThemePreference preference) {
    return _store.setString(
      appThemePreferenceStorageKey,
      preference.storageKey,
    );
  }
}
