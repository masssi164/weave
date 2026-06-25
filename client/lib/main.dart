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
  runApp(const ProviderScope(child: WeaveApp()));
}

/// Root widget for the Weave collaboration app.
class WeaveApp extends ConsumerStatefulWidget {
  const WeaveApp({super.key});

  @override
  ConsumerState<WeaveApp> createState() => _WeaveAppState();
}

class _WeaveAppState extends ConsumerState<WeaveApp>
    with WidgetsBindingObserver {
  StreamSubscription<Uri>? _linkSubscription;
  final List<Timer> _pendingDeepLinkTimers = [];

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _linkSubscription = AppLinks().uriLinkStream.listen(
      _openAppLink,
      onError: (_) {},
    );
    _schedulePendingDeepLinkPoll();
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    _linkSubscription?.cancel();
    for (final timer in _pendingDeepLinkTimers) {
      timer.cancel();
    }
    _pendingDeepLinkTimers.clear();
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) {
      _schedulePendingDeepLinkPoll();
    }
  }

  void _schedulePendingDeepLinkPoll() {
    _pendingDeepLinkTimers.add(
      Timer(const Duration(milliseconds: 250), () {
        if (mounted) {
          _openPendingNativeDeepLink();
        }
      }),
    );
    _pendingDeepLinkTimers.add(
      Timer(const Duration(seconds: 2), () {
        if (mounted) {
          _openPendingNativeDeepLink();
        }
      }),
    );
  }

  Future<void> _openPendingNativeDeepLink() async {
    final SharedPreferencesAsync preferences;
    try {
      preferences = SharedPreferencesAsync();
    } catch (_) {
      return;
    }
    final String? pendingDeepLink;
    try {
      pendingDeepLink = await preferences.getString(_pendingDeepLinkKey);
    } catch (_) {
      return;
    }
    if (pendingDeepLink == null || pendingDeepLink.isEmpty) {
      return;
    }
    try {
      await preferences.remove(_pendingDeepLinkKey);
    } catch (_) {}
    final uri = Uri.tryParse(pendingDeepLink);
    if (uri != null) {
      final location = initialLocationForDefaultRoute(uri.toString());
      if (location != AppRoutes.welcome) {
        setStartupInitialLocation(location);
      }
      _openAppLink(uri);
    }
  }

  void _openAppLink(Uri uri) {
    final location = initialLocationForDefaultRoute(uri.toString());
    if (location == AppRoutes.welcome || !mounted) {
      return;
    }
    try {
      ref.read(appRouterProvider).go(location);
    } catch (_) {
      setStartupInitialLocation(location);
    }
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
