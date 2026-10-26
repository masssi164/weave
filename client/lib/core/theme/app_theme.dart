import 'package:flutter/material.dart';
import 'package:weave/core/theme/app_theme_preference.dart';
import 'package:weave/core/theme/weave_motion_theme.dart';

/// Centralised Material 3 theme definitions for the Weave app.
///
/// Seed colour: Deep Purple 600 (#6750A4) — the default M3 primary,
/// chosen for strong contrast ratios in both light and dark modes.
abstract final class AppTheme {
  static const _seed = Color(0xFF6750A4);

  static final light = _buildTheme(
    ColorScheme.fromSeed(seedColor: _seed, brightness: Brightness.light),
  );

  static final dark = _buildTheme(
    ColorScheme.fromSeed(seedColor: _seed, brightness: Brightness.dark),
  );

  static final highContrastLight = _buildTheme(
    ColorScheme.fromSeed(
      seedColor: _seed,
      brightness: Brightness.light,
      contrastLevel: 1,
    ),
  );

  static final highContrastDark = _buildTheme(
    ColorScheme.fromSeed(
      seedColor: _seed,
      brightness: Brightness.dark,
      contrastLevel: 1,
    ),
  );

  static final _reducedLight =
      _buildTheme(light.colorScheme, reducedMotion: true);
  static final _reducedDark =
      _buildTheme(dark.colorScheme, reducedMotion: true);
  static final _reducedHighContrastLight =
      _buildTheme(highContrastLight.colorScheme, reducedMotion: true);
  static final _reducedHighContrastDark =
      _buildTheme(highContrastDark.colorScheme, reducedMotion: true);

  static ThemeData lightFor(
    AppThemeSelection selection, {
    bool reducedMotion = false,
  }) => selection.usesHighContrastPalette
      ? (reducedMotion ? _reducedHighContrastLight : highContrastLight)
      : (reducedMotion ? _reducedLight : light);

  static ThemeData darkFor(
    AppThemeSelection selection, {
    bool reducedMotion = false,
  }) => selection.usesHighContrastPalette
      ? (reducedMotion ? _reducedHighContrastDark : highContrastDark)
      : (reducedMotion ? _reducedDark : dark);

  static ThemeData _buildTheme(
    ColorScheme colorScheme, {
    bool reducedMotion = false,
  }) {
    return ThemeData(
      useMaterial3: true,
      colorScheme: colorScheme,
      visualDensity: VisualDensity.adaptivePlatformDensity,
      textTheme: _textTheme,
      extensions: <ThemeExtension<dynamic>>[
        WeaveMotionTheme(profileReducedMotion: reducedMotion),
      ],
    );
  }

  /// Custom text theme — uses the default M3 type scale but ensures
  /// that no font sizes are hard-fixed so [TextScaler] works correctly.
  static const _textTheme = TextTheme(
    // All sizes come from the default M3 type scale.
    // We declare the const object so it can be shared between themes.
  );
}
