import 'package:flutter/material.dart';

/// User-owned visual theme choices.
///
/// These values describe the personal preference stored on this device/profile.
/// Workspace branding and future admin defaults should provide a separate
/// default [AppThemePreference] rather than overwriting the user's selection.
enum AppThemePreference {
  system('system'),
  light('light'),
  dark('dark'),
  highContrast('highContrast');

  const AppThemePreference(this.storageKey);

  final String storageKey;

  ThemeMode get themeMode {
    return switch (this) {
      AppThemePreference.system => ThemeMode.system,
      AppThemePreference.light => ThemeMode.light,
      AppThemePreference.dark => ThemeMode.dark,
      AppThemePreference.highContrast => ThemeMode.system,
    };
  }

  bool get usesHighContrastPalette => this == AppThemePreference.highContrast;

  static AppThemePreference? fromStorageKey(String value) {
    for (final preference in values) {
      if (preference.storageKey == value) {
        return preference;
      }
    }
    return null;
  }
}

/// Separates personal theme selection from workspace defaults/branding.
class AppThemeSelection {
  const AppThemeSelection({
    this.userPreference,
    this.workspaceDefault = AppThemePreference.system,
    this.workspaceBrandThemeId,
  });

  /// Explicit user choice. When absent, Weave falls back to workspace defaults.
  final AppThemePreference? userPreference;

  /// Future workspace/admin default. It must not override [userPreference].
  final AppThemePreference workspaceDefault;

  /// Reserved extension point for polished workspace brand palettes.
  final String? workspaceBrandThemeId;

  AppThemePreference get effectivePreference =>
      userPreference ?? workspaceDefault;

  ThemeMode get themeMode => effectivePreference.themeMode;

  bool get usesHighContrastPalette =>
      effectivePreference.usesHighContrastPalette;

  AppThemeSelection copyWith({
    AppThemePreference? userPreference,
    bool clearUserPreference = false,
    AppThemePreference? workspaceDefault,
    String? workspaceBrandThemeId,
    bool clearWorkspaceBrandThemeId = false,
  }) {
    return AppThemeSelection(
      userPreference: clearUserPreference
          ? null
          : userPreference ?? this.userPreference,
      workspaceDefault: workspaceDefault ?? this.workspaceDefault,
      workspaceBrandThemeId: clearWorkspaceBrandThemeId
          ? null
          : workspaceBrandThemeId ?? this.workspaceBrandThemeId,
    );
  }
}
