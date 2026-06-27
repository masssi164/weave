import 'dart:async';

import 'package:app_links/app_links.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
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
import 'package:weave/features/auth/data/repositories/oidc_auth_session_repository.dart';
import 'package:weave/features/onboarding/domain/entities/member_auth_onboarding_state.dart';
import 'package:weave/features/onboarding/domain/use_cases/consume_member_handoff.dart';
import 'package:weave/features/onboarding/presentation/member_handoff_screen.dart';
import 'package:weave/features/profile/presentation/providers/user_profile_provider.dart';
import 'package:weave/features/server_config/data/repositories/shared_preferences_server_configuration_repository.dart';
import 'package:weave/integrations/nextcloud/data/repositories/secure_nextcloud_session_repository.dart';
import 'package:weave/l10n/generated/app_localizations.dart';

const _pendingDeepLinkKey = 'pending_deep_link_url';
const _dogfoodResetQueryKey = 'dogfood_reset';
const _dogfoodResetAppStateValue = 'app_state';
const _dogfoodLocalProfile = 'local-lan-dogfood';

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
      (uri) => unawaited(_openAppLink(uri)),
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
          unawaited(_openPendingNativeDeepLink());
        }
      }),
    );
    _pendingDeepLinkTimers.add(
      Timer(const Duration(seconds: 2), () {
        if (mounted) {
          unawaited(_openPendingNativeDeepLink());
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
      await _openAppLink(uri);
    }
  }

  Future<void> _openAppLink(Uri uri) async {
    final location = initialLocationForDefaultRoute(uri.toString());
    if (location == AppRoutes.welcome || !mounted) {
      return;
    }
    if (await _resetDogfoodAppStateIfRequested(uri)) {
      return;
    }
    try {
      ref.read(appRouterProvider).go(location);
    } catch (_) {
      setStartupInitialLocation(location);
    }
  }

  Future<bool> _resetDogfoodAppStateIfRequested(Uri uri) async {
    if (uri.queryParameters[_dogfoodResetQueryKey] !=
            _dogfoodResetAppStateValue ||
        uri.queryParameters['profile'] != _dogfoodLocalProfile) {
      return false;
    }
    final preferences = SharedPreferencesAsync();
    for (final key in const [
      serverConfigurationStorageKey,
      legacySetupCompleteKey,
      lastHandoffConsumedStorageKey,
      dogfoodAuthStateStorageKey,
      dogfoodVisibleStateStorageKey,
    ]) {
      try {
        await preferences.remove(key);
      } catch (_) {}
    }
    const secureStorage = FlutterSecureStorage(
      iOptions: IOSOptions(
        accessibility: KeychainAccessibility.first_unlock_this_device,
      ),
      mOptions: MacOsOptions(
        accessibility: KeychainAccessibility.first_unlock_this_device,
      ),
    );
    for (final key in const [
      authSessionStorageKey,
      nextcloudSessionStorageKey,
    ]) {
      try {
        await secureStorage.delete(key: key);
      } catch (_) {}
    }
    final cleanQuery = Map<String, String>.from(uri.queryParameters)
      ..remove(_dogfoodResetQueryKey);
    final cleanUri = uri.replace(queryParameters: cleanQuery);
    try {
      await preferences.setString(_pendingDeepLinkKey, cleanUri.toString());
    } catch (_) {}
    _schedulePendingDeepLinkPoll();
    return true;
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
    final profileLocale = _profileLocale(
      ref
          .watch(userProfileProvider)
          .maybeWhen(data: (profile) => profile?.locale, orElse: () => null),
    );
    return MaterialApp.router(
      title: 'Weave',
      debugShowCheckedModeBanner: false,
      theme: AppTheme.lightFor(themeSelection),
      darkTheme: AppTheme.darkFor(themeSelection),
      themeMode: themeSelection.themeMode,
      locale: profileLocale,
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
