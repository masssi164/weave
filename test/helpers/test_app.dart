import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:weave/core/theme/app_theme.dart';
import 'package:weave/l10n/generated/app_localizations.dart';

/// Wraps a widget in all the necessary ancestors for screen-level tests:
/// [ProviderScope], [MaterialApp] with localizations, and theming.
///
/// Use [overrides] to inject mock providers.
Widget createTestApp(Widget child, {List<Override> overrides = const []}) {
  return ProviderScope(
    overrides: overrides,
    child: MaterialApp(
      theme: AppTheme.light,
      localizationsDelegates: AppLocalizations.localizationsDelegates,
      supportedLocales: AppLocalizations.supportedLocales,
      home: Scaffold(body: child),
    ),
  );
}
