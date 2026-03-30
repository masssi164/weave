import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:weave/core/bootstrap/domain/bootstrap_state.dart';
import 'package:weave/core/bootstrap/presentation/bootstrap_gate.dart';
import 'package:weave/core/bootstrap/presentation/providers/app_bootstrap_provider.dart';
import 'package:weave/core/failures/app_failure.dart';
import 'package:weave/core/router/app_router.dart';
import 'package:weave/core/theme/app_theme.dart';
import 'package:weave/l10n/generated/app_localizations.dart';

void main() => runApp(const ProviderScope(child: WeaveApp()));

/// Root widget for the Weave collaboration app.
class WeaveApp extends ConsumerWidget {
  const WeaveApp({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final bootstrap = ref.watch(appBootstrapProvider);

    if (bootstrap.isLoading) {
      return _buildFallbackApp(const BootstrapGate.loading());
    }

    if (bootstrap.hasError) {
      return _buildFallbackApp(
        BootstrapGate.error(
          failure: const AppFailure.bootstrap(
            'Unable to bootstrap the application.',
          ),
          onRetry: () => ref.read(appBootstrapProvider.notifier).retry(),
        ),
      );
    }

    final bootstrapState = bootstrap.requireValue;
    if (bootstrapState.phase == BootstrapPhase.error) {
      return _buildFallbackApp(
        BootstrapGate.error(
          failure: bootstrapState.failure!,
          onRetry: () => ref.read(appBootstrapProvider.notifier).retry(),
        ),
      );
    }

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

  MaterialApp _buildFallbackApp(Widget home) {
    return MaterialApp(
      title: 'Weave',
      debugShowCheckedModeBanner: false,
      theme: AppTheme.light,
      darkTheme: AppTheme.dark,
      localizationsDelegates: AppLocalizations.localizationsDelegates,
      supportedLocales: AppLocalizations.supportedLocales,
      home: home,
    );
  }
}
