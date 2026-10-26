import 'dart:async';

import 'package:app_links/app_links.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:weave/core/bootstrap/domain/bootstrap_state.dart';
import 'package:weave/core/bootstrap/presentation/bootstrap_gate.dart';
import 'package:weave/core/bootstrap/presentation/providers/app_bootstrap_provider.dart';
import 'package:weave/core/failures/app_failure.dart';
import 'package:weave/core/l10n/app_locale_preference.dart';
import 'package:weave/core/l10n/app_locale_preference_provider.dart';
import 'package:weave/core/router/app_routes.dart';
import 'package:weave/core/router/app_router.dart';
import 'package:weave/core/theme/app_theme.dart';
import 'package:weave/core/theme/app_theme_preference.dart';
import 'package:weave/core/theme/app_theme_preference_provider.dart';
import 'package:weave/features/profile/domain/entities/user_profile.dart';
import 'package:weave/features/profile/presentation/providers/user_profile_provider.dart';
import 'package:weave/l10n/generated/app_localizations.dart';

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

class _WeaveAppState extends ConsumerState<WeaveApp> {
  final AppLinks _appLinks = AppLinks();
  StreamSubscription<Uri>? _linkSubscription;
  Uri? _firstNativeLink;
  bool _hasResolvedBootstrap = false;

  @override
  void initState() {
    super.initState();
    _linkSubscription = _appLinks.uriLinkStream.listen(
      (uri) {
        if (_firstNativeLink == uri) {
          _firstNativeLink = null;
          return;
        }
        unawaited(_openAppLink(uri));
      },
      onError: (_) {},
    );
    unawaited(_openInitialAppLink());
  }

  Future<void> _openInitialAppLink() async {
    try {
      final uri = await _appLinks.getInitialLink();
      if (!mounted || uri == null) return;
      _firstNativeLink = uri;
      await _openAppLink(uri);
    } catch (_) {
      // A platform-link failure must not block ordinary in-app organization entry.
    }
  }

  @override
  void dispose() {
    _linkSubscription?.cancel();
    super.dispose();
  }

  Future<void> _openAppLink(Uri uri) async {
    final location = initialLocationForDefaultRoute(uri.toString());
    if (location == AppRoutes.organizationAccess || !mounted) {
      return;
    }
    if (!_hasResolvedBootstrap) {
      setStartupInitialLocation(location);
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
    if (bootstrap.value?.isResolved == true) _hasResolvedBootstrap = true;

    Widget? gate;
    if (bootstrap.isLoading ||
        bootstrap.value?.phase == BootstrapPhase.loading) {
      gate = const BootstrapGate.loading();
    } else if (bootstrap.hasError) {
      gate = BootstrapGate.error(
        failure: const AppFailure.bootstrap(
          'Unable to bootstrap the application.',
        ),
        onRetry: () => ref.read(appBootstrapProvider.notifier).retry(),
      );
    } else if (bootstrap.value?.phase == BootstrapPhase.error) {
      gate = BootstrapGate.error(
        failure: bootstrap.requireValue.failure!,
        onRetry: () => ref.read(appBootstrapProvider.notifier).retry(),
      );
    }
    if (!_hasResolvedBootstrap) {
      return _buildFallbackApp(gate ?? const BootstrapGate.loading());
    }

    final router = ref.watch(appRouterProvider);
    final themeSelection = ref
        .watch(appThemePreferenceProvider)
        .maybeWhen(
          data: (selection) => selection,
          orElse: () => const AppThemeSelection(),
        );
    final localeSelection = ref
        .watch(appLocalePreferenceProvider)
        .maybeWhen(
          data: (selection) => selection,
          orElse: () => const AppLocaleSelection(),
        );
    final profile = ref
        .watch(userProfileProvider)
        .maybeWhen(data: (profile) => profile, orElse: () => null);
    final profileLocale = _profileLocale(profile?.locale);
    final appLocale = localeSelection.userPreference == null
        ? profileLocale
        : localeSelection.locale;
    return MaterialApp.router(
      title: 'Weave',
      debugShowCheckedModeBanner: false,
      theme: AppTheme.lightFor(
        themeSelection,
        reducedMotion: profile?.prefersReducedMotion ?? false,
      ),
      darkTheme: AppTheme.darkFor(
        themeSelection,
        reducedMotion: profile?.prefersReducedMotion ?? false,
      ),
      themeMode: themeSelection.themeMode,
      locale: appLocale,
      routerConfig: router,
      builder: (context, child) {
        if (gate == null) return child!;
        // Keep async route owners mounted while blocking stale content and
        // all pointer, keyboard-focus and accessibility interaction.
        return Stack(
          fit: StackFit.expand,
          children: [
            TickerMode(
              enabled: false,
              child: ExcludeFocus(
                child: Offstage(offstage: true, child: child),
              ),
            ),
            gate,
          ],
        );
      },
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

  Locale? _profileLocale(String? locale) {
    final normalizedLocale = locale?.trim();
    if (normalizedLocale == null || normalizedLocale.isEmpty) {
      return null;
    }
    final languageCode = normalizedLocale
        .split(RegExp('[-_]'))
        .first
        .toLowerCase();
    if (languageCode == 'en' || languageCode == 'de') {
      return Locale(languageCode);
    }
    return null;
  }
}
