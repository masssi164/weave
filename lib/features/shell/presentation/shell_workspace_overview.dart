import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:weave/core/router/app_routes.dart';
import 'package:weave/l10n/generated/app_localizations.dart';

class ShellWorkspaceOverview extends StatelessWidget {
  const ShellWorkspaceOverview({super.key});

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final theme = Theme.of(context);

    return Semantics(
      container: true,
      label: l10n.shellWorkspaceOverviewSemanticLabel,
      child: Card(
        margin: const EdgeInsets.fromLTRB(16, 12, 16, 0),
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
          child: Row(
            children: [
              Expanded(
                child: Text(
                  l10n.shellWorkspaceOverviewTitle,
                  style: theme.textTheme.titleMedium,
                ),
              ),
              Wrap(
                spacing: 4,
                children: [
                  TextButton(
                    onPressed: () => context.go(AppRoutes.chat),
                    child: Text(l10n.shellWorkspaceOverviewOpenChat),
                  ),
                  TextButton(
                    onPressed: () => context.go(AppRoutes.files),
                    child: Text(l10n.shellWorkspaceOverviewOpenFiles),
                  ),
                  TextButton(
                    onPressed: () => context.go(AppRoutes.settings),
                    child: Text(l10n.shellWorkspaceOverviewOpenSettings),
                  ),
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }
}
