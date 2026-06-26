import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:weave/features/shell/domain/entities/shell_module.dart';
import 'package:weave/features/shell/presentation/providers/shell_module_preferences_provider.dart';
import 'package:weave/features/shell/presentation/shell_recent_activity.dart';
import 'package:weave/features/shell/presentation/shell_workspace_status.dart';
import 'package:weave/l10n/generated/app_localizations.dart';

class HomeScreen extends ConsumerWidget {
  const HomeScreen({super.key});

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

    return Scaffold(
      appBar: AppBar(title: Text(l10n.chatOverviewTitle)),
      body: SafeArea(
        child: ListView(
          padding: const EdgeInsets.only(bottom: 24),
          children: [for (final module in visibleModules) _buildModule(module)],
        ),
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
