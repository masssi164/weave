import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:weave/core/theme/app_theme.dart';
import 'package:weave/core/theme/app_theme_preference.dart';

void main() {
  group('AppTheme', () {
    test('light theme uses Material 3 with light brightness', () {
      expect(AppTheme.light.useMaterial3, isTrue);
      expect(AppTheme.light.brightness, Brightness.light);
      expect(
        AppTheme.light.visualDensity,
        equals(VisualDensity.adaptivePlatformDensity),
      );
    });

    test('dark theme uses Material 3 with dark brightness', () {
      expect(AppTheme.dark.useMaterial3, isTrue);
      expect(AppTheme.dark.brightness, Brightness.dark);
      expect(
        AppTheme.dark.visualDensity,
        equals(VisualDensity.adaptivePlatformDensity),
      );
    });

    test('resolves high-contrast palettes from the user selection', () {
      const selection = AppThemeSelection(
        userPreference: AppThemePreference.highContrast,
      );

      expect(selection.themeMode, ThemeMode.system);
      expect(AppTheme.lightFor(selection).brightness, Brightness.light);
      expect(AppTheme.darkFor(selection).brightness, Brightness.dark);
      expect(
        AppTheme.lightFor(selection).colorScheme.primary,
        isNot(AppTheme.light.colorScheme.primary),
      );
    });
  });
}
