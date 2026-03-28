import 'package:flutter/material.dart';

/// Centralised Material 3 theme definitions for the Weave app.
///
/// A seed colour is used so that the entire palette is auto-generated
/// by the M3 colour-system, keeping light & dark modes consistent.
abstract final class AppTheme {
  static const _seed = Color(0xFF6750A4);

  static final light = ThemeData(
    useMaterial3: true,
    colorSchemeSeed: _seed,
    brightness: Brightness.light,
  );

  static final dark = ThemeData(
    useMaterial3: true,
    colorSchemeSeed: _seed,
    brightness: Brightness.dark,
  );
}
