import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:weave/features/shell/domain/entities/shell_module.dart';
import 'package:weave/features/shell/domain/entities/shell_module_preferences.dart';
import 'package:weave/features/shell/presentation/providers/shell_module_preferences_provider.dart';
import 'package:weave/features/shell/presentation/shell_recent_activity.dart';
import 'package:weave/features/shell/presentation/shell_workspace_overview.dart';
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
    final visibleModules =
        modulePreferences.asData?.value.visibleModules ??
        const ShellModulePreferences().visibleModules;

    return Scaffold(
      body: Stack(
        children: [
          Positioned.fill(child: navigationShell),
          if (visibleModules.isNotEmpty)
            Align(
              alignment: Alignment.topCenter,
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  for (final module in visibleModules)
                    _ShellModuleHost(module: module),
                ],
              ),
            ),
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
}

class _ShellModuleHost extends StatelessWidget {
  const _ShellModuleHost({required this.module});

  final ShellModule module;

  @override
  Widget build(BuildContext context) {
    return switch (module) {
      ShellModule.workspaceOverview => const ShellWorkspaceOverview(),
      ShellModule.recentActivity => const ShellRecentActivity(),
    };
  }
}
