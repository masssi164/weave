import 'package:weave/features/app/domain/entities/workspace_capability_snapshot.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:riverpod_annotation/riverpod_annotation.dart';
import 'package:weave/core/bootstrap/domain/bootstrap_state.dart';
import 'package:weave/core/bootstrap/presentation/providers/app_bootstrap_provider.dart';
import 'package:weave/core/router/app_routes.dart';
import 'package:weave/features/auth/presentation/sign_in_screen.dart';
import 'package:weave/features/calendar/presentation/calendar_screen.dart';
import 'package:weave/features/chat/domain/entities/chat_conversation.dart';
import 'package:weave/features/chat/presentation/chat_admission_gate.dart';
import 'package:weave/features/chat/presentation/chat_room_screen.dart';
import 'package:weave/features/chat/presentation/chat_screen.dart';
import 'package:weave/core/router/files_routes.dart';
import 'package:weave/features/help/presentation/help_screen.dart';
import 'package:weave/features/home/presentation/home_screen.dart';
import 'package:weave/features/onboarding/presentation/member_handoff_screen.dart';
import 'package:weave/features/onboarding/presentation/organization_access_screen.dart';
import 'package:weave/features/profile/presentation/profile_screen.dart';
import 'package:weave/features/settings/presentation/settings_screen.dart';
import 'package:weave/features/shell/presentation/app_shell.dart';
import 'package:weave/features/shell/domain/entities/shell_destination.dart';

import 'package:weave/integrations/weave_api/presentation/providers/weave_api_provider.dart';
import 'package:weave/features/server_config/presentation/providers/saved_server_configuration_provider.dart';

part 'app_router.g.dart';

/// Global navigator key for the root [GoRouter].
final rootNavigatorKey = GlobalKey<NavigatorState>(debugLabel: 'root');

String? _startupInitialLocation;

void setStartupInitialLocation(String? location) {
  if (location == null) {
    _startupInitialLocation = null;
    return;
  }

  final uri = Uri.tryParse(location);
  _startupInitialLocation = uri == null
      ? location
      : normalizedJoinRouteLocation(uri) ?? location;
}

/// Installation stays separate from changing member policy and operation readiness.
/// The validated manifest preserves installation while a member snapshot refreshes.
final installedWorkspaceDomainIdsProvider = Provider<Set<String>>((ref) {
  final snapshot = ref
      .watch(weaveApiWorkspaceCapabilitySnapshotProvider)
      .asData
      ?.value;
  if (snapshot != null) {
    const identities = {
      WorkspaceCapability.chat: 'chat',
      WorkspaceCapability.files: 'files',
      WorkspaceCapability.calendar: 'calendar',
    };
    return {
      for (final entry in identities.entries)
        if (snapshot.isInstalled(entry.key)) entry.value,
    };
  }
  final manifest = ref
      .watch(savedServerConfigurationProvider)
      .asData
      ?.value
      ?.validatedManifest;
  final declarations = manifest?['domains'];
  return {
    if (declarations is List)
      for (final declaration in declarations)
        if (declaration is Map && declaration['domain'] is String)
          declaration['domain'] as String,
  };
});

/// Top-level [GoRouter] exposed as a Riverpod provider so that
/// the router can read the resolved bootstrap state for redirects.
@riverpod
GoRouter appRouter(Ref ref) {
  var destinations = shellDestinationsForDomains(
    ref.read(installedWorkspaceDomainIdsProvider),
  );
  final refresh = ValueNotifier(0);
  ref.listen(appBootstrapProvider, (_, next) => refresh.value++);
  final initialLocation =
      _startupInitialLocation ??
      initialLocationForDefaultRoute(
        WidgetsBinding.instance.platformDispatcher.defaultRouteName,
      );
  final resumeSignIn =
      initialLocation == AppRoutes.organizationAccess &&
      ref.read(appBootstrapProvider).asData?.value.phase ==
          BootstrapPhase.needsSignIn;
  RoutingConfig configuration() => RoutingConfig(
    redirect: (context, state) async {
      final bootstrap = ref.read(appBootstrapProvider);
      // Route ownership survives refresh; authorization uses only resolved state.
      if (bootstrap.isLoading || bootstrap.hasError || !bootstrap.hasValue) {
        return null;
      }
      final bootstrapState = bootstrap.requireValue;
      if (bootstrapState.phase == BootstrapPhase.ready) {
        for (final destination in ShellDestination.values) {
          final path = switch (destination) {
            ShellDestination.chat => AppRoutes.chat,
            ShellDestination.files => AppRoutes.files,
            ShellDestination.calendar => AppRoutes.calendar,
            _ => null,
          };
          if (path != null &&
              !destinations.contains(destination) &&
              (state.uri.path == path || state.uri.path.startsWith('$path/'))) {
            return AppRoutes.home;
          }
        }
      }
      final onOrganizationAccess =
          state.matchedLocation == AppRoutes.organizationAccess;
      final onSignIn = state.matchedLocation == AppRoutes.signIn;
      final onJoin = state.matchedLocation == AppRoutes.join;
      switch (bootstrapState.phase) {
        case BootstrapPhase.loading:
        case BootstrapPhase.error:
          return null;
        case BootstrapPhase.needsSetup:
          return (onOrganizationAccess || onJoin)
              ? null
              : AppRoutes.organizationAccess;
        case BootstrapPhase.needsSignIn:
          return (onOrganizationAccess || onSignIn || onJoin)
              ? null
              : AppRoutes.signIn;
        case BootstrapPhase.ready:
          return (onOrganizationAccess || onSignIn) ? AppRoutes.home : null;
      }
    },
    routes: [
      GoRoute(
        path: AppRoutes.organizationAccess,
        builder: (context, state) => const OrganizationAccessScreen(),
      ),
      GoRoute(
        path: AppRoutes.signIn,
        builder: (context, state) => const SignInScreen(),
      ),
      GoRoute(
        path: AppRoutes.join,
        // This transient handoff may immediately return to the existing shell.
        // Avoid overlapping its outgoing and re-entered navigator keys.
        pageBuilder: (context, state) => NoTransitionPage(
          key: state.pageKey,
          child: MemberHandoffScreen(uri: state.uri),
        ),
      ),
      GoRoute(
        path: AppRoutes.help,
        builder: (context, state) => const HelpScreen(),
      ),
      StatefulShellRoute.indexedStack(
        builder: (context, state, navigationShell) => AppShell(
          navigationShell: navigationShell,
          destinations: destinations,
        ),
        branches: [
          for (final destination in destinations) _shellBranch(destination),
        ],
      ),
    ],
  );
  final routingConfig = ValueNotifier(configuration());
  ref.listen(
    installedWorkspaceDomainIdsProvider.select(
      (ids) => (ids.toList()..sort()).join(','),
    ),
    (_, next) {
      destinations = shellDestinationsForDomains(next.split(',').toSet());
      routingConfig.value = configuration();
    },
  );
  final router = GoRouter.routingConfig(
    routingConfig: routingConfig,
    refreshListenable: refresh,
    navigatorKey: rootNavigatorKey,
    initialLocation: resumeSignIn ? AppRoutes.signIn : initialLocation,
    onException: (context, state, router) {
      if ([AppRoutes.chat, AppRoutes.files, AppRoutes.calendar].any(
        (path) => state.uri.path == path || state.uri.path.startsWith('$path/'),
      )) {
        router.go(AppRoutes.home);
        return;
      }
      final normalizedLocation = initialLocationForDefaultRoute(
        state.uri.toString(),
      );
      if (normalizedLocation != AppRoutes.organizationAccess) {
        router.go(normalizedLocation);
      }
    },
  );

  ref.onDispose(() {
    router.dispose();
    refresh.dispose();
    routingConfig.dispose();
  });
  return router;
}

String initialLocationForDefaultRoute(String defaultRouteName) {
  if (defaultRouteName.isEmpty || defaultRouteName == '/') {
    return AppRoutes.organizationAccess;
  }

  final uri = Uri.tryParse(defaultRouteName);
  if (uri == null) {
    return AppRoutes.organizationAccess;
  }

  final normalizedJoinLocation = normalizedJoinRouteLocation(uri);
  if (normalizedJoinLocation != null) {
    return normalizedJoinLocation;
  }

  return AppRoutes.organizationAccess;
}

StatefulShellBranch _shellBranch(ShellDestination destination) =>
    switch (destination) {
      ShellDestination.home => StatefulShellBranch(
        routes: [
          GoRoute(
            path: AppRoutes.home,
            builder: (context, state) => const HomeScreen(),
          ),
        ],
      ),
      ShellDestination.chat => StatefulShellBranch(
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
                    return ChatAdmissionGate(
                      child: ChatRoomScreen(conversation: conversation),
                    );
                  }

                  return const ChatScreen();
                },
              ),
            ],
          ),
        ],
      ),
      ShellDestination.files => StatefulShellBranch(routes: [filesRoute()]),
      ShellDestination.calendar => StatefulShellBranch(
        routes: [
          GoRoute(
            path: AppRoutes.calendar,
            builder: (context, state) => const CalendarScreen(),
          ),
        ],
      ),
      ShellDestination.settings => StatefulShellBranch(
        routes: [
          GoRoute(
            path: AppRoutes.settings,
            builder: (context, state) => const SettingsScreen(),
          ),
          GoRoute(
            path: AppRoutes.profile,
            builder: (context, state) => const ProfileScreen(),
          ),
          GoRoute(
            path: AppRoutes.settingsPreferences,
            builder: (context, state) => SettingsPreferencesScreen(
              section: state.uri.queryParameters['section'],
            ),
          ),
          GoRoute(
            path: AppRoutes.settingsSupport,
            builder: (context, state) => const SettingsSupportScreen(),
          ),
          GoRoute(
            path: AppRoutes.workspaceHealth,
            builder: (context, state) => const WorkspaceHealthScreen(),
          ),
        ],
      ),
    };
