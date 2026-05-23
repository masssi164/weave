import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:weave/features/shell/domain/entities/shell_module.dart';
import 'package:weave/features/shell/presentation/providers/shell_module_preferences_provider.dart';
import 'package:weave/features/shell/presentation/shell_recent_activity.dart';
import 'package:weave/features/shell/presentation/shell_workspace_status.dart';
import 'package:weave/l10n/generated/app_localizations.dart';

/// The main application shell rendered by [StatefulShellRoute].
///
/// Renders a [Scaffold] with a Material 3 [NavigationBar] at the bottom.
/// The [navigationShell] is provided by GoRouter and manages the active
/// branch's widget tree via an [IndexedStack] internally.
class AppShell extends ConsumerWidget {
  const AppShell({super.key, required this.navigationShell});

  /// The navigation shell created by [StatefulShellRoute.indexedStack].
  final StatefulNavigationShell navigationShell;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    final modulePreferences = ref.watch(shellModulePreferencesProvider);
    final modules =
        modulePreferences.asData?.value.orderedModules ?? ShellModule.values;
    final visibleModules = modules
        .where(
          (module) => modulePreferences.asData?.value.isVisible(module) ?? true,
        )
        .toList(growable: false);

    final showLandingModules =
        navigationShell.currentIndex == 0 && visibleModules.isNotEmpty;

    return Scaffold(
      body: Column(
        children: [
          if (showLandingModules)
            SizedBox(
              height: 128,
              child: SingleChildScrollView(
                child: Column(
                  children: visibleModules.map(_buildModule).toList(),
                ),
              ),
            ),
          Expanded(child: navigationShell),
        ],
      ),
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

  Widget _buildModule(ShellModule module) {
    return switch (module) {
      ShellModule.workspaceStatus => const ShellWorkspaceStatus(),
      ShellModule.recentActivity => const ShellRecentActivity(),
    };
  }
}
