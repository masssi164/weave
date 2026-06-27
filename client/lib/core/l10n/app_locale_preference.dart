import 'package:flutter/widgets.dart';

/// User-owned language choices stored on this device/profile.
enum AppLocalePreference {
  system('system'),
  english('en'),
  german('de');

  const AppLocalePreference(this.storageKey);

  final String storageKey;

  Locale? get locale {
    return switch (this) {
      AppLocalePreference.system => null,
      AppLocalePreference.english => const Locale('en'),
      AppLocalePreference.german => const Locale('de'),
    };
  }

  static AppLocalePreference? fromStorageKey(String value) {
    for (final preference in values) {
      if (preference.storageKey == value) {
        return preference;
      }
    }
    return null;
  }
}

/// Separates personal language selection from future workspace defaults.
class AppLocaleSelection {
  const AppLocaleSelection({
    this.userPreference,
    this.workspaceDefault = AppLocalePreference.system,
  });

  final AppLocalePreference? userPreference;
  final AppLocalePreference workspaceDefault;

  AppLocalePreference get effectivePreference =>
      userPreference ?? workspaceDefault;

  Locale? get locale => effectivePreference.locale;

  AppLocaleSelection copyWith({
    AppLocalePreference? userPreference,
    bool clearUserPreference = false,
    AppLocalePreference? workspaceDefault,
  }) {
    return AppLocaleSelection(
      userPreference: clearUserPreference
          ? null
          : userPreference ?? this.userPreference,
      workspaceDefault: workspaceDefault ?? this.workspaceDefault,
    );
  }
}
