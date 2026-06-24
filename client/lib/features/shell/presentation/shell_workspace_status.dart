import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:weave/core/router/app_routes.dart';
import 'package:weave/core/widgets/error_state.dart';
import 'package:weave/core/widgets/loading_state.dart';
import 'package:weave/features/app/domain/entities/workspace_capability_snapshot.dart';
import 'package:weave/features/app/domain/entities/workspace_connection_state.dart';
import 'package:weave/features/app/domain/entities/workspace_home_snapshot.dart';
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
    final home = ref.watch(weaveApiWorkspaceHomeProvider);

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
            (
              AsyncData(value: final workspaceState),
              AsyncData(value: final capabilitySnapshot),
            ) =>
              _WorkspaceSummary(
                workspace: workspaceState,
                capabilities: capabilitySnapshot,
                home: home.asData?.value,
              ),
            (AsyncError(), _) || (_, AsyncError()) => ErrorState(
              message: l10n.settingsWorkspaceBackendUnreachable,
              retryLabel: l10n.retryButton,
              onRetry: () {
                ref.invalidate(appAuthIntegrationConnectionProvider);
                ref.invalidate(weaveApiWorkspaceCapabilitySnapshotProvider);
                ref.invalidate(weaveApiWorkspaceHomeProvider);
              },
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

class _WorkspaceSummary extends StatelessWidget {
  const _WorkspaceSummary({
    required this.workspace,
    required this.capabilities,
    required this.home,
  });

  final WorkspaceConnectionState workspace;
  final WorkspaceCapabilitySnapshot capabilities;
  final WorkspaceHomeSnapshot? home;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final l10n = AppLocalizations.of(context);
    final isReady = workspace.status == IntegrationConnectionStatus.connected;
    final summary = isReady
        ? l10n.settingsWorkspaceSummaryConnected
        : workspace.shellAccessReady
        ? l10n.settingsWorkspaceSummaryDegraded
        : l10n.settingsWorkspaceSummaryNeedsSignIn;

    return Semantics(
      container: true,
      label: '${l10n.settingsWorkspaceReadinessTitle}. $summary',
      child: ExcludeSemantics(
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Icon(
                  isReady ? Icons.check_circle_outline : Icons.info_outline,
                  color: isReady
                      ? theme.colorScheme.primary
                      : theme.colorScheme.tertiary,
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        l10n.settingsWorkspaceReadinessTitle,
                        style: theme.textTheme.titleMedium,
                      ),
                      const SizedBox(height: 4),
                      Text(summary, style: theme.textTheme.bodyMedium),
                    ],
                  ),
                ),
                TextButton(
                  onPressed: () => context.go(AppRoutes.settings),
                  child: Text(l10n.navSettings),
                ),
              ],
            ),
            const SizedBox(height: 12),
            Wrap(
              spacing: 8,
              runSpacing: 8,
              children: [
                _CapabilityChip(
                  label: l10n.settingsWorkspaceChatLabel,
                  capability: capabilities.chat,
                ),
                _CapabilityChip(
                  label: l10n.settingsWorkspaceFilesLabel,
                  capability: capabilities.files,
                ),
                _CapabilityChip(
                  label: l10n.settingsWorkspaceCalendarLabel,
                  capability: capabilities.calendar,
                ),
                _CapabilityChip(
                  label: l10n.settingsWorkspaceBoardsLabel,
                  capability: capabilities.boards,
                ),
              ],
            ),
            if (home case final homeSnapshot?) ...[
              const SizedBox(height: 12),
              _HomeDailyLoopSummary(home: homeSnapshot),
            ],
          ],
        ),
      ),
    );
  }
}

class _HomeDailyLoopSummary extends StatelessWidget {
  const _HomeDailyLoopSummary({required this.home});

  final WorkspaceHomeSnapshot home;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final visibleSections = home.sections.take(5).toList(growable: false);
    final visibleActions = home.actions.take(2).toList(growable: false);
    final semanticSections = visibleSections
        .map((section) => '${section.title}: ${section.summary}')
        .join('. ');
    final homeTitle = AppLocalizations.of(context).chatOverviewTitle;

    return Semantics(
      container: true,
      label: '$homeTitle. ${home.summary}. $semanticSections',
      child: ExcludeSemantics(
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(homeTitle, style: theme.textTheme.titleSmall),
            const SizedBox(height: 4),
            Text(home.summary, style: theme.textTheme.bodyMedium),
            const SizedBox(height: 8),
            Wrap(
              spacing: 8,
              runSpacing: 8,
              children: visibleSections
                  .map(
                    (section) => _CapabilityChip(
                      label: '${section.title} (${section.itemCount})',
                      capability: WorkspaceCapabilityState(
                        capability: WorkspaceCapability.shellAccess,
                        readiness: section.readiness,
                      ),
                    ),
                  )
                  .toList(growable: false),
            ),
            if (visibleActions.isNotEmpty) ...[
              const SizedBox(height: 8),
              for (final action in visibleActions)
                Text('• ${action.label}: ${action.reason}'),
            ],
          ],
        ),
      ),
    );
  }
}

class _CapabilityChip extends StatelessWidget {
  const _CapabilityChip({required this.label, required this.capability});

  final String label;
  final WorkspaceCapabilityState capability;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final l10n = AppLocalizations.of(context);
    final recovery = workspaceCapabilityRecoveryPresentation(l10n, capability);
    final color = switch (capability.readiness) {
      WorkspaceCapabilityReadiness.ready => theme.colorScheme.primary,
      WorkspaceCapabilityReadiness.degraded => theme.colorScheme.tertiary,
      WorkspaceCapabilityReadiness.blocked => theme.colorScheme.error,
      WorkspaceCapabilityReadiness.unavailable =>
        theme.colorScheme.onSurfaceVariant,
    };

    return Semantics(
      label: recovery.semanticLabel(l10n, label),
      child: ExcludeSemantics(
        child: Chip(
          side: BorderSide(color: color),
          label: Text('$label: ${recovery.stateLabel}'),
          visualDensity: VisualDensity.compact,
        ),
      ),
    );
  }
}
