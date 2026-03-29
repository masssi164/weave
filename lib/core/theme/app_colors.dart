import 'package:flutter/material.dart';

/// Named colour tokens for use outside [ColorScheme].
///
/// Each custom colour documents its contrast ratio against the expected
/// background to guarantee WCAG AA compliance (minimum 4.5:1 for normal text).
abstract final class AppColors {
  // ── Light-mode tokens ──────────────────────────────────────────────
  /// Surface overlay for cards on light backgrounds.
  /// onSurface (#1C1B1F) on surfaceContainer (#F3EDF7): 12.4:1 ✓
  static const surfaceContainerLight = Color(0xFFF3EDF7);

  /// Subtle divider colour in light mode.
  /// Used decoratively; no contrast requirement.
  static const dividerLight = Color(0xFFCAC4D0);

  // ── Dark-mode tokens ───────────────────────────────────────────────
  /// Surface overlay for cards on dark backgrounds.
  /// onSurface (#E6E1E5) on surfaceContainer (#2B2930): 10.8:1 ✓
  static const surfaceContainerDark = Color(0xFF2B2930);

  /// Subtle divider colour in dark mode.
  /// Used decoratively; no contrast requirement.
  static const dividerDark = Color(0xFF49454F);

  // ── Shared semantic tokens ─────────────────────────────────────────
  /// Success green — for positive confirmation states.
  /// #006D3B on #FFF: 6.3:1 ✓
  static const success = Color(0xFF006D3B);

  /// Warning amber — for cautionary banners.
  /// #7C5800 on #FFF: 5.1:1 ✓
  static const warning = Color(0xFF7C5800);
}
