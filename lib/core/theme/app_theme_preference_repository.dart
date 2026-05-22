import 'package:weave/core/theme/app_theme_preference.dart';

abstract interface class AppThemePreferenceRepository {
  Future<AppThemePreference?> loadUserPreference();

  Future<void> saveUserPreference(AppThemePreference preference);
}
