import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:riverpod_annotation/riverpod_annotation.dart';
import 'package:weave/core/bootstrap/domain/bootstrap_state.dart';
import 'package:weave/core/bootstrap/presentation/providers/app_bootstrap_provider.dart';
import 'package:weave/core/router/app_routes.dart';
import 'package:weave/features/auth/presentation/sign_in_screen.dart';
import 'package:weave/features/chat/presentation/chat_screen.dart';
import 'package:weave/features/files/presentation/files_screen.dart';
import 'package:weave/features/onboarding/presentation/setup_flow.dart';
import 'package:weave/features/onboarding/presentation/welcome_screen.dart';
import 'package:weave/features/settings/presentation/settings_screen.dart';
import 'package:weave/features/shell/presentation/app_shell.dart';

part 'app_router.g.dart';

/// Global navigator key for the root [GoRouter].
final rootNavigatorKey = GlobalKey<NavigatorState>(debugLabel: 'root');

/// Top-level [GoRouter] exposed as a Riverpod provider so that
/// the router can read the resolved bootstrap state for redirects.
@riverpod
GoRouter appRouter(Ref ref) {
  final bootstrapState = ref.watch(appBootstrapProvider).requireValue;

  return GoRouter(
    navigatorKey: rootNavigatorKey,
    initialLocation: AppRoutes.welcome,
    redirect: (context, state) {
      final onOnboarding =
          state.matchedLocation == AppRoutes.welcome ||
          state.matchedLocation == AppRoutes.setup;
      final onSignIn = state.matchedLocation == AppRoutes.signIn;
      final onHiddenReleaseOneRoute =
          state.matchedLocation == AppRoutes.calendar ||
          state.matchedLocation == AppRoutes.deck;

      if (onHiddenReleaseOneRoute) {
        return AppRoutes.chat;
      }

      switch (bootstrapState.phase) {
        case BootstrapPhase.loading:
        case BootstrapPhase.error:
          return null;
        case BootstrapPhase.needsSetup:
          return onOnboarding ? null : AppRoutes.welcome;
        case BootstrapPhase.needsSignIn:
          return onSignIn ? null : AppRoutes.signIn;
        case BootstrapPhase.ready:
          return onOnboarding || onSignIn ? AppRoutes.chat : null;
      }
    },
    routes: [
      GoRoute(
        path: AppRoutes.welcome,
        builder: (context, state) => const WelcomeScreen(),
      ),
      GoRoute(
        path: AppRoutes.setup,
        builder: (context, state) => const SetupFlow(),
      ),
      GoRoute(
        path: AppRoutes.signIn,
        builder: (context, state) => const SignInScreen(),
      ),
      StatefulShellRoute.indexedStack(
        builder: (context, state, navigationShell) =>
            AppShell(navigationShell: navigationShell),
        branches: [
          StatefulShellBranch(
            routes: [
              GoRoute(
                path: AppRoutes.chat,
                builder: (context, state) => const ChatScreen(),
              ),
            ],
          ),
          StatefulShellBranch(
            routes: [
              GoRoute(
                path: AppRoutes.files,
                builder: (context, state) => const FilesScreen(),
              ),
            ],
          ),
          StatefulShellBranch(
            routes: [
              GoRoute(
                path: AppRoutes.settings,
                builder: (context, state) => const SettingsScreen(),
              ),
            ],
          ),
        ],
      ),
    ],
  );
}
