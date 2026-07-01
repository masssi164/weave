import 'package:weave/core/l10n/app_locale_preference.dart';

abstract interface class AppLocalePreferenceRepository {
  Future<AppLocalePreference?> loadUserPreference();

  Future<void> saveUserPreference(AppLocalePreference preference);
}
