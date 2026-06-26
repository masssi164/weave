import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:riverpod_annotation/riverpod_annotation.dart';
import 'package:weave/core/bootstrap/domain/bootstrap_state.dart';
import 'package:weave/core/bootstrap/presentation/providers/app_bootstrap_provider.dart';
import 'package:weave/core/router/app_routes.dart';
import 'package:weave/features/auth/presentation/sign_in_screen.dart';
import 'package:weave/features/chat/domain/entities/chat_conversation.dart';
import 'package:weave/features/chat/presentation/chat_room_screen.dart';
import 'package:weave/features/chat/presentation/chat_screen.dart';
import 'package:weave/features/files/presentation/files_screen.dart';
import 'package:weave/features/help/presentation/help_screen.dart';
import 'package:weave/features/onboarding/domain/entities/first_run_status.dart';
import 'package:weave/features/onboarding/presentation/first_run_screen.dart';
import 'package:weave/features/onboarding/presentation/member_handoff_screen.dart';
import 'package:weave/features/onboarding/presentation/providers/first_run_status_provider.dart';
import 'package:weave/features/onboarding/presentation/setup_flow.dart';
import 'package:weave/features/onboarding/presentation/welcome_screen.dart';
import 'package:weave/features/settings/presentation/settings_screen.dart';
import 'package:weave/features/shell/presentation/app_shell.dart';

part 'app_router.g.dart';

/// Global navigator key for the root [GoRouter].
final rootNavigatorKey = GlobalKey<NavigatorState>(debugLabel: 'root');

String? _startupInitialLocation;

void setStartupInitialLocation(String? location) {
  _startupInitialLocation = location;
}

/// Top-level [GoRouter] exposed as a Riverpod provider so that
/// the router can read the resolved bootstrap state for redirects.
@riverpod
GoRouter appRouter(Ref ref) {
  final bootstrapState = ref.watch(appBootstrapProvider).requireValue;

  return GoRouter(
    navigatorKey: rootNavigatorKey,
    initialLocation:
        _startupInitialLocation ??
        initialLocationForDefaultRoute(
          WidgetsBinding.instance.platformDispatcher.defaultRouteName,
        ),
    redirect: (context, state) async {
      final onOnboarding =
          state.matchedLocation == AppRoutes.welcome ||
          state.matchedLocation == AppRoutes.setup;
      final onSignIn = state.matchedLocation == AppRoutes.signIn;
      final onJoin = state.matchedLocation == AppRoutes.join;
      final onFirstRun = state.matchedLocation == AppRoutes.firstRun;
      switch (bootstrapState.phase) {
        case BootstrapPhase.loading:
        case BootstrapPhase.error:
          return null;
        case BootstrapPhase.needsSetup:
          return (onOnboarding || onJoin) ? null : AppRoutes.welcome;
        case BootstrapPhase.needsSignIn:
          return (onSignIn || onJoin) ? null : AppRoutes.signIn;
        case BootstrapPhase.ready:
          try {
            final result = await ref.read(firstRunStatusProvider.future);
            return switch (result) {
              FirstRunAuthenticated(:final status) =>
                !status.firstRunComplete
                    ? (onFirstRun ? null : AppRoutes.firstRun)
                    : (onOnboarding || onSignIn || onFirstRun
                          ? AppRoutes.chat
                          : null),
              FirstRunSignedOut() ||
              FirstRunUnauthorized() => onSignIn ? null : AppRoutes.signIn,
              FirstRunBackendUnavailable() || FirstRunInvalidPayload() =>
                onFirstRun ? null : AppRoutes.firstRun,
            };
          } catch (_) {
            return onFirstRun ? null : AppRoutes.firstRun;
          }
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
      GoRoute(
        path: AppRoutes.join,
        builder: (context, state) => MemberHandoffScreen(uri: state.uri),
      ),
      GoRoute(
        path: AppRoutes.firstRun,
        builder: (context, state) => const FirstRunScreen(),
      ),
      GoRoute(
        path: AppRoutes.help,
        builder: (context, state) => const HelpScreen(),
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
                routes: [
                  GoRoute(
                    path: AppRoutes.chatRoomRelative,
                    builder: (context, state) {
                      final conversation = state.extra;
                      if (conversation is ChatConversation) {
                        return ChatRoomScreen(conversation: conversation);
                      }

                      return const ChatScreen();
                    },
                  ),
                ],
              ),
            ],
          ),
          StatefulShellBranch(
            routes: [
              GoRoute(
                path: AppRoutes.files,
                builder: (context, state) => FilesScreen(
                  initialPath: state.uri.queryParameters['path'] ?? '/',
                ),
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

String initialLocationForDefaultRoute(String defaultRouteName) {
  if (defaultRouteName.isEmpty || defaultRouteName == '/') {
    return AppRoutes.welcome;
  }

  final uri = Uri.tryParse(defaultRouteName);
  if (uri == null) {
    return AppRoutes.welcome;
  }

  if (uri.scheme == 'weave' && (uri.host == 'join' || uri.path == '/join')) {
    final query = uri.hasQuery ? '?${uri.query}' : '';
    return '${AppRoutes.join}$query';
  }

  if (uri.scheme.isEmpty && uri.path == AppRoutes.join) {
    return uri.toString();
  }

  return AppRoutes.welcome;
}
