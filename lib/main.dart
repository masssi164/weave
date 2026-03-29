import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:weave/core/router/app_router.dart';
import 'package:weave/core/theme/app_theme.dart';
import 'package:weave/l10n/generated/app_localizations.dart';

void main() => runApp(const ProviderScope(child: WeaveApp()));

/// Root widget for the Weave collaboration app.
class WeaveApp extends ConsumerWidget {
  const WeaveApp({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final router = ref.watch(appRouterProvider);

    return MaterialApp.router(
      title: 'Weave',
      debugShowCheckedModeBanner: false,
      theme: AppTheme.light,
      darkTheme: AppTheme.dark,
      routerConfig: router,
      localizationsDelegates: AppLocalizations.localizationsDelegates,
      supportedLocales: AppLocalizations.supportedLocales,
    );
  }
}
