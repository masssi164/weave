import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:weave/core/router/app_shell.dart';
import 'package:weave/features/calendar/views/calendar_view.dart';
import 'package:weave/features/chat/views/chat_view.dart';
import 'package:weave/features/files/views/files_view.dart';

/// Global navigation key used by the outer [GoRouter].
final _rootNavigatorKey = GlobalKey<NavigatorState>(debugLabel: 'root');

/// Navigation key scoped to the [ShellRoute] so that tab
/// switches animate inside the shell instead of replacing it.
final _shellNavigatorKey = GlobalKey<NavigatorState>(debugLabel: 'shell');

/// Top-level router configuration for the Weave app.
///
/// Uses a [ShellRoute] to persist the bottom navigation bar
/// across the three main tabs (Chat · Files · Calendar).
final appRouter = GoRouter(
  navigatorKey: _rootNavigatorKey,
  initialLocation: '/chat',
  routes: [
    ShellRoute(
      navigatorKey: _shellNavigatorKey,
      builder: (context, state, child) => AppShell(child: child),
      routes: [
        GoRoute(
          path: '/chat',
          pageBuilder: (context, state) => const NoTransitionPage(
            child: ChatView(),
          ),
        ),
        GoRoute(
          path: '/files',
          pageBuilder: (context, state) => const NoTransitionPage(
            child: FilesView(),
          ),
        ),
        GoRoute(
          path: '/calendar',
          pageBuilder: (context, state) => const NoTransitionPage(
            child: CalendarView(),
          ),
        ),
      ],
    ),
  ],
);
