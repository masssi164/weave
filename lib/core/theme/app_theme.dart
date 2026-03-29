import 'package:flutter/material.dart';

/// Centralised Material 3 theme definitions for the Weave app.
///
/// Seed colour: Deep Purple 600 (#6750A4) — the default M3 primary,
/// chosen for strong contrast ratios in both light and dark modes.
abstract final class AppTheme {
  static const _seed = Color(0xFF6750A4);

  static final light = ThemeData(
    useMaterial3: true,
    colorSchemeSeed: _seed,
    brightness: Brightness.light,
    visualDensity: VisualDensity.adaptivePlatformDensity,
    textTheme: _textTheme,
  );

  static final dark = ThemeData(
    useMaterial3: true,
    colorSchemeSeed: _seed,
    brightness: Brightness.dark,
    visualDensity: VisualDensity.adaptivePlatformDensity,
    textTheme: _textTheme,
  );

  /// Custom text theme — uses the default M3 type scale but ensures
  /// that no font sizes are hard-fixed so [TextScaler] works correctly.
  static const _textTheme = TextTheme(
    // All sizes come from the default M3 type scale.
    // We declare the const object so it can be shared between themes.
  );
}
