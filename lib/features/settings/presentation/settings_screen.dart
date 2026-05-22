import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:weave/core/a11y/semantic_button.dart';
import 'package:weave/core/a11y/semantic_list_tile.dart';
import 'package:weave/core/bootstrap/presentation/providers/app_bootstrap_provider.dart';
import 'package:weave/core/config/feature_flags.dart';
import 'package:weave/core/router/app_routes.dart';
import 'package:weave/core/widgets/error_state.dart';
import 'package:weave/core/widgets/loading_state.dart';
import 'package:weave/core/widgets/weave_logo.dart';
import 'package:weave/features/app/domain/entities/integration_invalidation.dart';
import 'package:weave/features/app/domain/entities/matrix_e2ee_diagnostic.dart';
import 'package:weave/features/app/domain/entities/workspace_capability_snapshot.dart';
import 'package:weave/features/app/domain/entities/workspace_connection_state.dart';
import 'package:weave/features/app/presentation/providers/workspace_connection_provider.dart';
import 'package:weave/features/auth/presentation/providers/auth_flow_controller.dart';
import 'package:weave/features/chat/presentation/widgets/chat_security_settings_section.dart';
import 'package:weave/features/connectors/presentation/providers/connector_preview_provider.dart';
import 'package:weave/features/connectors/presentation/widgets/connector_settings_preview_card.dart';
import 'package:weave/features/guests/presentation/providers/guest_preview_provider.dart';
import 'package:weave/features/guests/presentation/widgets/guest_access_preview_card.dart';
import 'package:weave/features/profile/presentation/widgets/profile_summary_card.dart';
import 'package:weave/features/server_config/presentation/providers/'
    'server_configuration_form_controller.dart';
import 'package:weave/features/server_config/presentation/widgets/server_configuration_form.dart';
import 'package:weave/features/shell/domain/entities/shell_module.dart';
import 'package:weave/features/shell/presentation/providers/shell_module_preferences_provider.dart';
import 'package:weave/integrations/weave_api/presentation/providers/weave_api_provider.dart';
import 'package:weave/l10n/generated/app_localizations.dart';

class SettingsScreen extends ConsumerWidget {
  const SettingsScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    final savedConfiguration = ref.watch(savedServerConfigurationProvider);
    final authState = ref.watch(authFlowControllerProvider);

    return CustomScrollView(
      slivers: [
        SliverAppBar.large(title: Text(l10n.settingsScreenTitle)),
        SliverPadding(
          padding: const EdgeInsets.all(24),
          sliver: SliverToBoxAdapter(
            child: savedConfiguration.when(
              loading: () => LoadingState(message: l10n.loadingLabel),
              error: (error, _) => ErrorState(
                message: l10n.errorStateLabel,
                retryLabel: l10n.retryButton,
                onRetry: () => ref.invalidate(savedServerConfigurationProvider),
              ),
              data: (configuration) => Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  const _SettingsBrandCard(),
                  const SizedBox(height: 32),
                  const ProfileSummaryCard(),
                  const SizedBox(height: 32),
                  const _WorkspaceReadinessCard(),
                  const SizedBox(height: 32),
                  const _SettingsHelpCard(),
                  const SizedBox(height: 32),
                  const _ShellModuleVisibilitySettingsSection(),
                  if (FeatureFlags.hasFeatureGatedSurfaces) ...[
                    const SizedBox(height: 32),
                    const _FeaturePreviewSurfacesSection(),
                  ],
                  const SizedBox(height: 32),
                  Text(
                    l10n.settingsServerConfigurationTitle,
                    style: Theme.of(context).textTheme.headlineSmall,
                  ),
                  const SizedBox(height: 12),
                  Text(
                    l10n.settingsServerConfigurationDescription,
                    style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                      color: Theme.of(context).colorScheme.onSurfaceVariant,
                    ),
                  ),
                  const SizedBox(height: 24),
                  ServerConfigurationForm(
                    layout: ServerConfigurationFormLayout.full,
                    initialConfiguration: configuration,
                    submitLabel: l10n.settingsSaveButton,
                    onSaved: (result) async {
                      await ref
                          .read(authFlowControllerProvider.notifier)
                          .handleConfigurationSaved(result);
                    },
                  ),
                  const SizedBox(height: 32),
                  const ChatSecuritySettingsSection(),
                  const SizedBox(height: 32),
                  Text(
                    l10n.settingsSignOutTitle,
                    style: Theme.of(context).textTheme.headlineSmall,
                  ),
                  const SizedBox(height: 12),
                  Text(
                    l10n.settingsSignOutDescription,
                    style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                      color: Theme.of(context).colorScheme.onSurfaceVariant,
                    ),
                  ),
                  const SizedBox(height: 16),
                  AccessibleButton(
                    outlined: true,
                    onPressed: authState.isBusy
                        ? null
                        : () => ref
                              .read(authFlowControllerProvider.notifier)
                              .signOut(),
                    semanticLabel: l10n.settingsSignOutButton,
                    child: Text(
                      authState.isBusy
                          ? l10n.settingsSignOutInProgress
                          : l10n.settingsSignOutButton,
                    ),
                  ),
                  if (authState.failure != null) ...[
                    const SizedBox(height: 16),
                    Text(
                      authState.failure!.message,
                      style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                        color: Theme.of(context).colorScheme.error,
                      ),
                    ),
                  ],
                ],
              ),
            ),
          ),
        ),
      ],
    );
  }
}

class _ShellModuleVisibilitySettingsSection extends ConsumerWidget {
  const _ShellModuleVisibilitySettingsSection();

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    final theme = Theme.of(context);
    final preferences = ref.watch(shellModulePreferencesProvider);

    return Card(
      elevation: 0,
      color: theme.colorScheme.surfaceContainerLow,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(24),
        side: BorderSide(color: theme.colorScheme.outlineVariant),
      ),
      child: Padding(
        padding: const EdgeInsets.all(20),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              l10n.settingsShellModulesTitle,
              style: theme.textTheme.titleLarge,
            ),
            const SizedBox(height: 8),
            Text(
              l10n.settingsShellModulesDescription,
              style: theme.textTheme.bodyMedium?.copyWith(
                color: theme.colorScheme.onSurfaceVariant,
              ),
            ),
            const SizedBox(height: 12),
            switch (preferences) {
              AsyncData(value: final value) => Column(
                children: [
                  for (final module in value.orderedModules)
                    _ShellModulePreferenceTile(
                      module: module,
                      isVisible: value.isVisible(module),
                      isFirst: value.orderedModules.first == module,
                      isLast: value.orderedModules.last == module,
                    ),
                ],
              ),
              AsyncError() => ErrorState(
                message: l10n.settingsShellModulesError,
                retryLabel: l10n.retryButton,
                onRetry: () => ref.invalidate(shellModulePreferencesProvider),
              ),
              _ => Padding(
                padding: const EdgeInsets.symmetric(vertical: 16),
                child: Text(
                  l10n.settingsShellModulesLoading,
                  style: theme.textTheme.bodyMedium,
                ),
              ),
            },
          ],
        ),
      ),
    );
  }
}

class _ShellModulePreferenceTile extends ConsumerWidget {
  const _ShellModulePreferenceTile({
    required this.module,
    required this.isVisible,
    required this.isFirst,
    required this.isLast,
  });

  final ShellModule module;
  final bool isVisible;
  final bool isFirst;
  final bool isLast;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    final title = _title(l10n);
    final description = _description(l10n);

    return MergeSemantics(
      child: Padding(
        padding: const EdgeInsets.symmetric(vertical: 4),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.center,
          children: [
            Expanded(
              child: SwitchListTile.adaptive(
                contentPadding: EdgeInsets.zero,
                title: Text(title),
                subtitle: Text(description),
                value: isVisible,
                onChanged: (visible) => ref
                    .read(shellModulePreferencesProvider.notifier)
                    .setModuleVisibility(module: module, isVisible: visible),
              ),
            ),
            IconButton(
              tooltip: l10n.settingsShellMoveModuleUp(title),
              onPressed: isFirst
                  ? null
                  : () => ref
                        .read(shellModulePreferencesProvider.notifier)
                        .moveModule(module: module, delta: -1),
              icon: const Icon(Icons.arrow_upward),
            ),
            IconButton(
              tooltip: l10n.settingsShellMoveModuleDown(title),
              onPressed: isLast
                  ? null
                  : () => ref
                        .read(shellModulePreferencesProvider.notifier)
                        .moveModule(module: module, delta: 1),
              icon: const Icon(Icons.arrow_downward),
            ),
          ],
        ),
      ),
    );
  }

  String _title(AppLocalizations l10n) {
    return switch (module) {
      ShellModule.workspaceStatus =>
        l10n.settingsShellWorkspaceStatusToggleTitle,
      ShellModule.recentActivity => l10n.settingsShellRecentActivityToggleTitle,
    };
  }

  String _description(AppLocalizations l10n) {
    return switch (module) {
      ShellModule.workspaceStatus =>
        l10n.settingsShellWorkspaceStatusToggleDescription,
      ShellModule.recentActivity =>
        l10n.settingsShellRecentActivityToggleDescription,
    };
  }
}

class _FeaturePreviewSurfacesSection extends ConsumerWidget {
  const _FeaturePreviewSurfacesSection();

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final theme = Theme.of(context);
    final l10n = AppLocalizations.of(context);
    final guestPreview = ref.watch(guestPreviewProvider);
    final connectorPreview = ref.watch(connectorPreviewProvider);
    return Card(
      elevation: 0,
      color: theme.colorScheme.surfaceContainerLow,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(24),
        side: BorderSide(color: theme.colorScheme.outlineVariant),
      ),
      child: Padding(
        padding: const EdgeInsets.all(20),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              l10n.settingsPreviewSurfacesTitle,
              style: theme.textTheme.titleLarge,
            ),
            const SizedBox(height: 8),
            Text(
              l10n.settingsPreviewSurfacesDescription,
              style: theme.textTheme.bodyMedium?.copyWith(
                color: theme.colorScheme.onSurfaceVariant,
              ),
            ),
            const SizedBox(height: 12),
            if (FeatureFlags.guestPortal) ...[
              GuestAccessPreviewCard(
                guests: guestPreview,
                title: l10n.settingsGuestPortalPreviewTitle,
                description: l10n.settingsGuestPortalPreviewDescription,
              ),
              const SizedBox(height: 12),
            ],
            if (FeatureFlags.interopAdmin) ...[
              ConnectorSettingsPreviewCard(
                connectors: connectorPreview,
                title: l10n.settingsInteropAdminPreviewTitle,
                description: l10n.settingsInteropAdminPreviewDescription,
              ),
              const SizedBox(height: 12),
            ],
            if (FeatureFlags.migrationDryRun)
              _PreviewSurfaceTile(
                icon: Icons.fact_check_outlined,
                title: l10n.settingsMigrationDryRunPreviewTitle,
                description: l10n.settingsMigrationDryRunPreviewDescription,
              ),
          ],
        ),
      ),
    );
  }
}

class _PreviewSurfaceTile extends StatelessWidget {
  const _PreviewSurfaceTile({
    required this.icon,
    required this.title,
    required this.description,
  });

  final IconData icon;
  final String title;
  final String description;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return ListTile(
      contentPadding: EdgeInsets.zero,
      leading: ExcludeSemantics(child: Icon(icon)),
      title: Text(title),
      subtitle: Text(description),
      trailing: Chip(
        label: Text(AppLocalizations.of(context).firstRunStateUnavailable),
        side: BorderSide(color: theme.colorScheme.outlineVariant),
      ),
    );
  }
}

class _SettingsBrandCard extends StatelessWidget {
  const _SettingsBrandCard();

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final theme = Theme.of(context);

    return Card(
      elevation: 0,
      color: theme.colorScheme.surfaceContainerLow,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(24),
        side: BorderSide(color: theme.colorScheme.outlineVariant),
      ),
      child: Padding(
        padding: const EdgeInsets.all(20),
        child: MergeSemantics(
          child: Row(
            children: [
              WeaveLogo(
                semanticLabel: l10n.semanticWeaveLogo,
                width: 72,
                framed: false,
              ),
              const SizedBox(width: 16),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(l10n.appTitle, style: theme.textTheme.titleMedium),
                    const SizedBox(height: 8),
                    Text(
                      l10n.settingsBrandSectionDescription,
                      style: theme.textTheme.bodyMedium?.copyWith(
                        color: theme.colorScheme.onSurfaceVariant,
                      ),
                    ),
                  ],
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _SettingsHelpCard extends StatelessWidget {
  const _SettingsHelpCard();

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final theme = Theme.of(context);

    return Card(
      elevation: 0,
      color: theme.colorScheme.surfaceContainerLow,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(24),
        side: BorderSide(color: theme.colorScheme.outlineVariant),
      ),
      child: AccessibleListTile(
        leading: const Icon(Icons.help_outline),
        title: Text(l10n.settingsHelpTitle),
        subtitle: Text(l10n.settingsHelpDescription),
        trailing: const ExcludeSemantics(child: Icon(Icons.chevron_right)),
        onTap: () => context.push(AppRoutes.help),
      ),
    );
  }
}

class _WorkspaceReadinessCard extends ConsumerWidget {
  const _WorkspaceReadinessCard();

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    final theme = Theme.of(context);
    final workspace = ref.watch(workspaceConnectionStateProvider);
    final capabilities = ref.watch(workspaceCapabilitySnapshotProvider);
    final backendState = ref.watch(weaveBackendConnectionStateProvider);
    final matrixDiagnostic = ref.watch(weaveApiMatrixE2eeDiagnosticProvider);

    return Card(
      elevation: 0,
      color: theme.colorScheme.surfaceContainerLow,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(24),
        side: BorderSide(color: theme.colorScheme.outlineVariant),
      ),
      child: Padding(
        padding: const EdgeInsets.all(20),
        child: switch ((workspace, capabilities)) {
          (
            AsyncData(value: final workspaceState),
            AsyncData(value: final capabilitySnapshot),
          ) =>
            Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  l10n.settingsWorkspaceReadinessTitle,
                  style: theme.textTheme.titleLarge,
                ),
                const SizedBox(height: 8),
                Text(
                  l10n.settingsWorkspaceReadinessDescription,
                  style: theme.textTheme.bodyMedium?.copyWith(
                    color: theme.colorScheme.onSurfaceVariant,
                  ),
                ),
                if (_backendFailureMessage(l10n, backendState)
                    case final message?) ...[
                  const SizedBox(height: 16),
                  ErrorState(
                    message: message,
                    retryLabel: l10n.retryButton,
                    onRetry: () {
                      ref.invalidate(
                        weaveApiWorkspaceCapabilitySnapshotProvider,
                      );
                    },
                  ),
                ],
                const SizedBox(height: 16),
                Text(
                  _workspaceSummary(l10n, workspaceState),
                  style: theme.textTheme.bodyMedium,
                ),
                const SizedBox(height: 20),
                _WorkspaceReadinessRow(
                  label: l10n.settingsWorkspaceShellAccessLabel,
                  capability: capabilitySnapshot.shellAccess,
                  connection: workspaceState.appAuth,
                ),
                const Divider(height: 32),
                _WorkspaceReadinessRow(
                  label: l10n.settingsWorkspaceChatLabel,
                  capability: capabilitySnapshot.chat,
                  connection: workspaceState.matrix,
                  matrixDiagnostic: matrixDiagnostic.asData?.value,
                ),
                const Divider(height: 32),
                _WorkspaceReadinessRow(
                  label: l10n.settingsWorkspaceFilesLabel,
                  capability: capabilitySnapshot.files,
                  connection: workspaceState.nextcloud,
                ),
                const Divider(height: 32),
                _WorkspaceReadinessRow(
                  label: l10n.settingsWorkspaceCalendarLabel,
                  capability: capabilitySnapshot.calendar,
                  connection: workspaceState.nextcloud,
                ),
              ],
            ),
          (AsyncError(), _) || (_, AsyncError()) => ErrorState(
            message: l10n.errorStateLabel,
            retryLabel: l10n.retryButton,
            onRetry: () {
              if (ref.read(appAuthIntegrationConnectionProvider).hasError) {
                unawaited(ref.read(appBootstrapProvider.notifier).retry());
              }
              ref.invalidate(appAuthIntegrationConnectionProvider);
              ref.invalidate(matrixIntegrationConnectionProvider);
              ref.invalidate(nextcloudIntegrationConnectionProvider);
            },
          ),
          _ => LoadingState(message: l10n.loadingLabel),
        },
      ),
    );
  }

  String? _backendFailureMessage(
    AppLocalizations l10n,
    WeaveBackendConnectionState backendState,
  ) {
    return switch (backendState) {
      WeaveBackendConnectionState.unreachable =>
        l10n.settingsWorkspaceBackendUnreachable,
      WeaveBackendConnectionState.unauthorized =>
        l10n.settingsWorkspaceBackendUnauthorized,
      WeaveBackendConnectionState.serverError =>
        l10n.settingsWorkspaceBackendServerError,
      WeaveBackendConnectionState.unconfigured ||
      WeaveBackendConnectionState.loading ||
      WeaveBackendConnectionState.connected => null,
    };
  }

  String _workspaceSummary(
    AppLocalizations l10n,
    WorkspaceConnectionState workspace,
  ) {
    if (workspace.status == IntegrationConnectionStatus.connected) {
      return l10n.settingsWorkspaceSummaryConnected;
    }

    if (workspace.shellAccessReady) {
      return l10n.settingsWorkspaceSummaryDegraded;
    }

    return switch (workspace.appAuth.status) {
      IntegrationConnectionStatus.misconfigured =>
        l10n.settingsWorkspaceSummaryNeedsSetup,
      IntegrationConnectionStatus.requiresReauthentication ||
      IntegrationConnectionStatus.disconnected ||
      IntegrationConnectionStatus.degraded ||
      IntegrationConnectionStatus.unavailableOnPlatform =>
        l10n.settingsWorkspaceSummaryNeedsSignIn,
      IntegrationConnectionStatus.connected =>
        l10n.settingsWorkspaceSummaryConnected,
    };
  }
}

class _WorkspaceReadinessRow extends StatelessWidget {
  const _WorkspaceReadinessRow({
    required this.label,
    required this.capability,
    required this.connection,
    this.matrixDiagnostic,
  });

  final String label;
  final WorkspaceCapabilityState capability;
  final IntegrationConnectionState connection;
  final MatrixE2eeDiagnostic? matrixDiagnostic;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final l10n = AppLocalizations.of(context);

    return MergeSemantics(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(label, style: theme.textTheme.titleMedium),
          const SizedBox(height: 12),
          Wrap(
            spacing: 12,
            runSpacing: 12,
            children: [
              _StatusPill(
                label: l10n.settingsWorkspaceCapabilityLabel,
                value: _capabilityLabel(l10n, capability.readiness),
              ),
              _StatusPill(
                label: l10n.settingsWorkspaceConnectionLabel,
                value: _connectionLabel(l10n, connection.status),
              ),
              if (connection.lastInvalidation != null)
                _StatusPill(
                  label: l10n.settingsWorkspaceLastChangeLabel,
                  value: _invalidationLabel(
                    l10n,
                    connection.lastInvalidation!.reason,
                  ),
                ),
              if (matrixDiagnostic case final diagnostic?) ...[
                _StatusPill(
                  label: l10n.settingsWorkspaceMatrixE2eeGateLabel,
                  value: diagnostic.isValidated
                      ? l10n.settingsWorkspaceMatrixE2eeValidated
                      : l10n.settingsWorkspaceMatrixE2eeNotValidated,
                ),
                _StatusPill(
                  label: l10n.settingsWorkspaceMatrixServerBodiesLabel,
                  value: diagnostic.keepsMessageBodiesOpaque
                      ? l10n.settingsWorkspaceMatrixServerBodiesOpaque
                      : l10n.settingsWorkspaceMatrixServerBodiesReadable,
                ),
                _StatusPill(
                  label: l10n.settingsWorkspaceMatrixAgentWritesLabel,
                  value: diagnostic.keepsAgentsAndConnectorsFailClosed
                      ? l10n.settingsWorkspaceMatrixAgentWritesBlocked
                      : l10n.settingsWorkspaceMatrixAgentWritesReview,
                ),
              ],
            ],
          ),
        ],
      ),
    );
  }

  String _capabilityLabel(
    AppLocalizations l10n,
    WorkspaceCapabilityReadiness readiness,
  ) {
    return switch (readiness) {
      WorkspaceCapabilityReadiness.ready =>
        l10n.settingsWorkspaceCapabilityReady,
      WorkspaceCapabilityReadiness.degraded =>
        l10n.settingsWorkspaceCapabilityDegraded,
      WorkspaceCapabilityReadiness.blocked =>
        l10n.settingsWorkspaceCapabilityBlocked,
      WorkspaceCapabilityReadiness.unavailable =>
        l10n.settingsWorkspaceCapabilityUnavailable,
    };
  }

  String _connectionLabel(
    AppLocalizations l10n,
    IntegrationConnectionStatus status,
  ) {
    return switch (status) {
      IntegrationConnectionStatus.connected =>
        l10n.settingsWorkspaceConnectionConnected,
      IntegrationConnectionStatus.disconnected =>
        l10n.settingsWorkspaceConnectionDisconnected,
      IntegrationConnectionStatus.degraded =>
        l10n.settingsWorkspaceConnectionDegraded,
      IntegrationConnectionStatus.misconfigured =>
        l10n.settingsWorkspaceConnectionMisconfigured,
      IntegrationConnectionStatus.requiresReauthentication =>
        l10n.settingsWorkspaceConnectionRequiresReauthentication,
      IntegrationConnectionStatus.unavailableOnPlatform =>
        l10n.settingsWorkspaceConnectionUnavailableOnPlatform,
    };
  }

  String _invalidationLabel(
    AppLocalizations l10n,
    IntegrationInvalidationReason reason,
  ) {
    return switch (reason) {
      IntegrationInvalidationReason.authConfigurationChanged =>
        l10n.settingsWorkspaceInvalidationAuthConfigurationChanged,
      IntegrationInvalidationReason.matrixHomeserverChanged =>
        l10n.settingsWorkspaceInvalidationMatrixHomeserverChanged,
      IntegrationInvalidationReason.nextcloudBaseUrlChanged =>
        l10n.settingsWorkspaceInvalidationNextcloudBaseUrlChanged,
      IntegrationInvalidationReason.backendApiBaseUrlChanged =>
        l10n.settingsWorkspaceInvalidationBackendApiBaseUrlChanged,
      IntegrationInvalidationReason.explicitSignOut =>
        l10n.settingsWorkspaceInvalidationExplicitSignOut,
      IntegrationInvalidationReason.restartSetup =>
        l10n.settingsWorkspaceInvalidationRestartSetup,
    };
  }
}

class _StatusPill extends StatelessWidget {
  const _StatusPill({required this.label, required this.value});

  final String label;
  final String value;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
      decoration: BoxDecoration(
        color: theme.colorScheme.surface,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: theme.colorScheme.outlineVariant),
      ),
      child: RichText(
        text: TextSpan(
          style: theme.textTheme.bodyMedium?.copyWith(
            color: theme.colorScheme.onSurface,
          ),
          children: [
            TextSpan(
              text: '$label: ',
              style: theme.textTheme.bodyMedium?.copyWith(
                color: theme.colorScheme.onSurfaceVariant,
              ),
            ),
            TextSpan(text: value),
          ],
        ),
      ),
    );
  }
}
