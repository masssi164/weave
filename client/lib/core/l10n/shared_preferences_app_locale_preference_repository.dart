import 'package:weave/core/l10n/app_locale_preference.dart';
import 'package:weave/core/l10n/app_locale_preference_repository.dart';
import 'package:weave/core/persistence/preferences_store.dart';

const appLocalePreferenceStorageKey = 'locale.userPreference.v1';

class SharedPreferencesAppLocalePreferenceRepository
    implements AppLocalePreferenceRepository {
  const SharedPreferencesAppLocalePreferenceRepository({
    required PreferencesStore store,
  }) : _store = store;

  final PreferencesStore _store;

  @override
  Future<AppLocalePreference?> loadUserPreference() async {
    final raw = await _store.getString(appLocalePreferenceStorageKey);
    if (raw == null || raw.isEmpty) {
      return null;
    }
    return AppLocalePreference.fromStorageKey(raw);
  }

  @override
  Future<void> saveUserPreference(AppLocalePreference preference) {
    return _store.setString(
      appLocalePreferenceStorageKey,
      preference.storageKey,
    );
  }
}
