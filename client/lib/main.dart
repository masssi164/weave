import 'dart:async';

import 'package:app_links/app_links.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:weave/core/bootstrap/domain/bootstrap_state.dart';
import 'package:weave/core/bootstrap/presentation/bootstrap_gate.dart';
import 'package:weave/core/bootstrap/presentation/providers/app_bootstrap_provider.dart';
import 'package:weave/core/failures/app_failure.dart';
import 'package:weave/core/router/app_routes.dart';
import 'package:weave/core/router/app_router.dart';
import 'package:weave/core/theme/app_theme.dart';
import 'package:weave/core/theme/app_theme_preference.dart';
import 'package:weave/core/theme/app_theme_preference_provider.dart';
import 'package:weave/l10n/generated/app_localizations.dart';

const _pendingDeepLinkKey = 'pending_deep_link_url';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  setStartupInitialLocation(
    await _initialAppLinkLocation() ?? await _consumePendingDeepLinkLocation(),
  );
  runApp(const ProviderScope(child: WeaveApp()));
}

Future<String?> _initialAppLinkLocation() async {
  try {
    final initialLink = await AppLinks().getInitialLink();
    if (initialLink == null) {
      return null;
    }
    return initialLocationForDefaultRoute(initialLink.toString());
  } catch (_) {
    return null;
  }
}

Future<String?> _consumePendingDeepLinkLocation() async {
  final preferences = SharedPreferencesAsync();
  final pendingDeepLink = await preferences.getString(_pendingDeepLinkKey);
  if (pendingDeepLink == null || pendingDeepLink.isEmpty) {
    return null;
  }
  await preferences.remove(_pendingDeepLinkKey);
  return initialLocationForDefaultRoute(pendingDeepLink);
}

/// Root widget for the Weave collaboration app.
class WeaveApp extends ConsumerStatefulWidget {
  const WeaveApp({super.key});

  @override
  ConsumerState<WeaveApp> createState() => _WeaveAppState();
}

class _WeaveAppState extends ConsumerState<WeaveApp> {
  StreamSubscription<Uri>? _linkSubscription;

  @override
  void initState() {
    super.initState();
    _linkSubscription = AppLinks().uriLinkStream.listen(
      _openAppLink,
      onError: (_) {},
    );
  }

  @override
  void dispose() {
    _linkSubscription?.cancel();
    super.dispose();
  }

  void _openAppLink(Uri uri) {
    final location = initialLocationForDefaultRoute(uri.toString());
    if (location == AppRoutes.welcome || !mounted) {
      return;
    }
    ref.read(appRouterProvider).go(location);
  }

  @override
  Widget build(BuildContext context) {
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
    final themeSelection = ref
        .watch(appThemePreferenceProvider)
        .maybeWhen(
          data: (selection) => selection,
          orElse: () => const AppThemeSelection(),
        );
    return MaterialApp.router(
      title: 'Weave',
      debugShowCheckedModeBanner: false,
      theme: AppTheme.lightFor(themeSelection),
      darkTheme: AppTheme.darkFor(themeSelection),
      themeMode: themeSelection.themeMode,
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
      themeMode: ThemeMode.system,
      localizationsDelegates: AppLocalizations.localizationsDelegates,
      supportedLocales: AppLocalizations.supportedLocales,
      home: home,
    );
  }
}
