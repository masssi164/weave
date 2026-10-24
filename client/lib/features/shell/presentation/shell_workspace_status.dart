import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:weave/core/router/app_routes.dart';
import 'package:weave/core/widgets/error_state.dart';
import 'package:weave/core/widgets/loading_state.dart';
import 'package:weave/features/app/domain/entities/workspace_capability_snapshot.dart';
import 'package:weave/features/app/presentation/workspace_capability_recovery_presenter.dart';
import 'package:weave/features/app/presentation/providers/workspace_connection_provider.dart';
import 'package:weave/integrations/weave_api/presentation/providers/weave_api_provider.dart';
import 'package:weave/l10n/generated/app_localizations.dart';

class ShellWorkspaceStatus extends ConsumerWidget {
  const ShellWorkspaceStatus({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final theme = Theme.of(context);
    final l10n = AppLocalizations.of(context);
    final workspace = ref.watch(workspaceConnectionStateProvider);
    final capabilities = ref.watch(workspaceCapabilitySnapshotProvider);

    void refreshWorkspace() {
      ref.invalidate(appAuthIntegrationConnectionProvider);
      ref.invalidate(weaveApiMemberSpacesProvider);
      ref.invalidate(weaveApiWorkspaceCapabilitySnapshotProvider);
      ref.invalidate(weaveApiWorkspaceHomeProvider);
    }

    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 12, 16, 8),
      child: Card(
        elevation: 0,
        color: theme.colorScheme.surfaceContainerLow,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(20),
          side: BorderSide(color: theme.colorScheme.outlineVariant),
        ),
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: switch ((workspace, capabilities)) {
            (AsyncData(), AsyncData(value: final capabilitySnapshot)) =>
              _WorkspaceActions(
                capabilities: capabilitySnapshot,
                onRefresh: refreshWorkspace,
              ),
            (AsyncError(), _) || (_, AsyncError()) => ErrorState(
              message: l10n.homeWorkspaceAccessUnavailable,
              retryLabel: l10n.retryButton,
              onRetry: refreshWorkspace,
            ),
            _ => LoadingState(
              message: l10n.bootstrapLoadingLabel,
              hint: l10n.bootstrapLoadingHint,
              icon: Icons.hub_outlined,
            ),
          },
        ),
      ),
    );
  }
}

class _WorkspaceActions extends StatelessWidget {
  const _WorkspaceActions({
    required this.capabilities,
    required this.onRefresh,
  });

  final WorkspaceCapabilitySnapshot capabilities;
  final VoidCallback onRefresh;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Semantics(
          header: true,
          child: Text(
            l10n.homeWorkspaceActionsTitle,
            style: Theme.of(context).textTheme.titleMedium,
          ),
        ),
        const SizedBox(height: 8),
        _WorkspaceAction(
          label: l10n.homeOpenChat,
          icon: Icons.chat_bubble_outline,
          route: AppRoutes.chat,
          capability: capabilities.chat,
        ),
        _WorkspaceAction(
          label: l10n.homeOpenFiles,
          icon: Icons.folder_outlined,
          route: AppRoutes.files,
          capability: capabilities.files,
        ),
        _WorkspaceAction(
          label: l10n.homeOpenCalendar,
          icon: Icons.calendar_month_outlined,
          route: AppRoutes.calendar,
          capability: capabilities.calendar,
        ),
        Wrap(
          spacing: 8,
          children: [
            TextButton.icon(
              style: TextButton.styleFrom(minimumSize: const Size(48, 48)),
              onPressed: onRefresh,
              icon: const Icon(Icons.refresh),
              label: Text(l10n.homeRefreshWorkspace),
            ),
            TextButton.icon(
              style: TextButton.styleFrom(minimumSize: const Size(48, 48)),
              onPressed: () => context.go(AppRoutes.settings),
              icon: const Icon(Icons.settings_outlined),
              label: Text(l10n.navSettings),
            ),
          ],
        ),
      ],
    );
  }
}

class _WorkspaceAction extends ConsumerWidget {
  const _WorkspaceAction({
    required this.label,
    required this.icon,
    required this.route,
    required this.capability,
  });

  final String label;
  final IconData icon;
  final String route;
  final WorkspaceCapabilityState capability;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final recovery = workspaceCapabilityRecoveryPresentation(
      AppLocalizations.of(context),
      capability,
    );

    // Admission permits opening a feature; its screen owns runtime success.
    final needsRecovery =
        recovery.state != WorkspaceMemberRecoveryState.available;
    return ListTile(
      contentPadding: EdgeInsets.zero,
      minTileHeight: 56,
      leading: Icon(icon),
      title: Text(label),
      subtitle: needsRecovery
          ? Text('${recovery.stateLabel}. ${recovery.recovery}')
          : null,
      trailing: const Icon(Icons.chevron_right),
      onTap: () {
        ref.invalidate(weaveApiMemberSpacesProvider);
        context.go(route);
      },
    );
  }
}
