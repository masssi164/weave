import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:weave/l10n/generated/app_localizations.dart';

/// The main application shell rendered by [StatefulShellRoute].
///
/// Renders a [Scaffold] with a Material 3 [NavigationBar] at the bottom.
/// The [navigationShell] is provided by GoRouter and manages the active
/// branch's widget tree via an [IndexedStack] internally.
class AppShell extends StatelessWidget {
  const AppShell({super.key, required this.navigationShell});

  /// The navigation shell created by [StatefulShellRoute.indexedStack].
  final StatefulNavigationShell navigationShell;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);

    return Scaffold(
      body: navigationShell,
      bottomNavigationBar: NavigationBar(
        selectedIndex: navigationShell.currentIndex,
        onDestinationSelected: (index) => navigationShell.goBranch(
          index,
          initialLocation: index == navigationShell.currentIndex,
        ),
        destinations: [
          NavigationDestination(
            icon: Icon(
              Icons.chat_bubble_outline,
              semanticLabel: l10n.semanticChatIcon,
            ),
            selectedIcon: Icon(
              Icons.chat_bubble,
              semanticLabel: l10n.semanticChatIcon,
            ),
            label: l10n.navChat,
          ),
          NavigationDestination(
            icon: Icon(
              Icons.folder_outlined,
              semanticLabel: l10n.semanticFilesIcon,
            ),
            selectedIcon: Icon(
              Icons.folder,
              semanticLabel: l10n.semanticFilesIcon,
            ),
            label: l10n.navFiles,
          ),
          NavigationDestination(
            icon: Icon(
              Icons.calendar_today_outlined,
              semanticLabel: l10n.semanticCalendarIcon,
            ),
            selectedIcon: Icon(
              Icons.calendar_month,
              semanticLabel: l10n.semanticCalendarIcon,
            ),
            label: l10n.navCalendar,
          ),
          NavigationDestination(
            icon: Icon(
              Icons.dashboard_outlined,
              semanticLabel: l10n.semanticDeckIcon,
            ),
            selectedIcon: Icon(
              Icons.dashboard,
              semanticLabel: l10n.semanticDeckIcon,
            ),
            label: l10n.navDeck,
          ),
          NavigationDestination(
            icon: Icon(
              Icons.settings_outlined,
              semanticLabel: l10n.semanticSettingsIcon,
            ),
            selectedIcon: Icon(
              Icons.settings,
              semanticLabel: l10n.semanticSettingsIcon,
            ),
            label: l10n.navSettings,
          ),
        ],
      ),
    );
  }
}
