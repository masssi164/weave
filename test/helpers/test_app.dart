import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:weave/core/theme/app_theme.dart';
import 'package:weave/l10n/generated/app_localizations.dart';

Widget createTestApp(
  Widget child, {
  List<dynamic> overrides = const <dynamic>[],
}) {
  return ProviderScope(
    overrides: [...overrides],
    child: MaterialApp(
      theme: AppTheme.light,
      localizationsDelegates: AppLocalizations.localizationsDelegates,
      supportedLocales: AppLocalizations.supportedLocales,
      home: Scaffold(body: child),
    ),
  );
}

Widget createTestRouterApp(
  GoRouter router, {
  List<dynamic> overrides = const <dynamic>[],
}) {
  return ProviderScope(
    overrides: [...overrides],
    child: MaterialApp.router(
      theme: AppTheme.light,
      localizationsDelegates: AppLocalizations.localizationsDelegates,
      supportedLocales: AppLocalizations.supportedLocales,
      routerConfig: router,
    ),
  );
}
