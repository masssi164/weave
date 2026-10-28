import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:weave/integrations/weave_api/presentation/providers/weave_api_provider.dart';
import 'package:weave/l10n/generated/app_localizations.dart';
import 'package:weave/features/shell/domain/entities/shell_destination.dart';

/// The main application shell rendered by [StatefulShellRoute].
///
/// Renders a [Scaffold] with a Material 3 [NavigationBar] at the bottom.
/// The [navigationShell] is provided by GoRouter and manages the active
/// branch's widget tree via an [IndexedStack] internally.
class AppShell extends ConsumerWidget {
  const AppShell({
    super.key,
    required this.navigationShell,
    this.destinations = ShellDestination.values,
  });

  /// The navigation shell created by [StatefulShellRoute.indexedStack].
  final StatefulNavigationShell navigationShell;
  final List<ShellDestination> destinations;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);

    return Scaffold(
      body: navigationShell,
      bottomNavigationBar: _ResponsiveShellNavigation(
        selectedIndex: navigationShell.currentIndex,
        onDestinationSelected: (index) {
          if (destinations[index].requiresMemberSpaces) {
            // Revalidate durable grants before reopening retained branch views.
            ref.invalidate(weaveApiMemberSpacesProvider);
          }
          navigationShell.goBranch(
            index,
            initialLocation: index == navigationShell.currentIndex,
          );
        },
        destinations: [
          for (final destination in destinations)
            (
              destination: destination,
              view: _navigationDestination(l10n, destination),
            ),
        ],
      ),
    );
  }
}

/// Keeps full destination names readable without capping accessibility text size.
class _ResponsiveShellNavigation extends StatelessWidget {
  const _ResponsiveShellNavigation({
    required this.selectedIndex,
    required this.onDestinationSelected,
    required this.destinations,
  });
  final int selectedIndex;
  final ValueChanged<int> onDestinationSelected;
  final List<_ShellDestinationView> destinations;

  @override
  Widget build(BuildContext context) => LayoutBuilder(
    builder: (context, constraints) {
      final style = Theme.of(context).textTheme.labelMedium!;
      final scaler = MediaQuery.textScalerOf(context);
      final widths = [
        for (final destination in destinations)
          (TextPainter(
                text: TextSpan(text: destination.view.label, style: style),
                textDirection: Directionality.of(context),
                textScaler: scaler,
              )..layout()).width +
              26,
      ];
      final needsWrap =
          widths.any(
            (width) => width > constraints.maxWidth / destinations.length,
          ) ||
          scaler.scale(1) > 1;
      if (!needsWrap) {
        return NavigationBar(
          selectedIndex: selectedIndex,
          onDestinationSelected: onDestinationSelected,
          destinations: [
            for (var i = 0; i < destinations.length; i++)
              Semantics(
                identifier: 'nav-${destinations[i].destination.id}',
                child: destinations[i].view,
              ),
          ],
        );
      }
      return Material(
        color: Theme.of(context).colorScheme.surfaceContainer,
        child: SafeArea(
          top: false,
          child: Padding(
            padding: const EdgeInsets.all(4),
            child: Wrap(
              alignment: WrapAlignment.center,
              spacing: 4,
              runSpacing: 4,
              children: [
                for (var index = 0; index < destinations.length; index++)
                  SizedBox(
                    width: widths[index]
                        .clamp(72, constraints.maxWidth - 8)
                        .toDouble(),
                    child: Semantics(
                      identifier: 'nav-${destinations[index].destination.id}',
                      button: true,
                      selected: selectedIndex == index,
                      label: destinations[index].view.label,
                      excludeSemantics: true,
                      child: InkWell(
                        onTap: () => onDestinationSelected(index),
                        borderRadius: BorderRadius.circular(16),
                        child: ConstrainedBox(
                          constraints: const BoxConstraints(
                            minHeight: 64,
                            minWidth: 48,
                          ),
                          child: Padding(
                            padding: const EdgeInsets.symmetric(
                              horizontal: 8,
                              vertical: 8,
                            ),
                            child: Column(
                              mainAxisSize: MainAxisSize.min,
                              children: [
                                selectedIndex == index
                                    ? destinations[index].view.selectedIcon ??
                                          destinations[index].view.icon
                                    : destinations[index].view.icon,
                                const SizedBox(height: 4),
                                Text(
                                  destinations[index].view.label,
                                  style: style,
                                  textAlign: TextAlign.center,
                                ),
                              ],
                            ),
                          ),
                        ),
                      ),
                    ),
                  ),
              ],
            ),
          ),
        ),
      );
    },
  );
}

typedef _ShellDestinationView = ({
  ShellDestination destination,
  NavigationDestination view,
});

NavigationDestination _navigationDestination(
  AppLocalizations l10n,
  ShellDestination destination,
) => switch (destination) {
  ShellDestination.home => NavigationDestination(
    icon: Icon(Icons.home_outlined, semanticLabel: l10n.semanticHomeIcon),
    selectedIcon: Icon(Icons.home, semanticLabel: l10n.semanticHomeIcon),
    label: l10n.navHome,
  ),
  ShellDestination.chat => NavigationDestination(
    icon: Icon(Icons.chat_bubble_outline, semanticLabel: l10n.semanticChatIcon),
    selectedIcon: Icon(Icons.chat_bubble, semanticLabel: l10n.semanticChatIcon),
    label: l10n.navChat,
  ),
  ShellDestination.files => NavigationDestination(
    icon: Icon(Icons.folder_outlined, semanticLabel: l10n.semanticFilesIcon),
    selectedIcon: Icon(Icons.folder, semanticLabel: l10n.semanticFilesIcon),
    label: l10n.navFiles,
  ),
  ShellDestination.calendar => NavigationDestination(
    icon: Icon(
      Icons.calendar_month_outlined,
      semanticLabel: l10n.semanticCalendarIcon,
    ),
    selectedIcon: Icon(
      Icons.calendar_month,
      semanticLabel: l10n.semanticCalendarIcon,
    ),
    label: l10n.navCalendar,
  ),
  ShellDestination.settings => NavigationDestination(
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
};
