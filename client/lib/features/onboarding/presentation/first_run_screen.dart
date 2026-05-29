import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:weave/core/a11y/semantic_button.dart';
import 'package:weave/core/router/app_routes.dart';
import 'package:weave/core/widgets/error_state.dart';
import 'package:weave/core/widgets/loading_state.dart';
import 'package:weave/features/onboarding/domain/entities/first_run_status.dart';
import 'package:weave/features/onboarding/presentation/providers/first_run_status_provider.dart';
import 'package:weave/l10n/generated/app_localizations.dart';

class FirstRunScreen extends ConsumerWidget {
  const FirstRunScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    final status = ref.watch(firstRunStatusProvider);

    return Scaffold(
      appBar: AppBar(title: Text(l10n.firstRunAppBarTitle)),
      body: SafeArea(
        child: status.when(
          loading: () => LoadingState(
            message: l10n.firstRunLoadingLabel,
            hint: l10n.firstRunLoadingHint,
            icon: Icons.hub_outlined,
          ),
          error: (error, _) => ErrorState(
            message: l10n.firstRunLoadFailure,
            guidance: l10n.firstRunLoadFailureGuidance,
            retryLabel: l10n.retryButton,
            onRetry: () => ref.invalidate(firstRunStatusProvider),
          ),
          data: (status) => status == null
              ? ErrorState(
                  message: l10n.firstRunSignedOutMessage,
                  guidance: l10n.firstRunSignedOutGuidance,
                  retryLabel: l10n.firstRunSignInAction,
                  onRetry: () => context.go(AppRoutes.signIn),
                  semanticLabel: l10n.firstRunSignedOutSemanticLabel,
                )
              : _FirstRunStatusView(status: status),
        ),
      ),
    );
  }
}

class _FirstRunStatusView extends ConsumerWidget {
  const _FirstRunStatusView({required this.status});

  final FirstRunStatus status;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    final theme = Theme.of(context);

    return SingleChildScrollView(
      padding: const EdgeInsets.fromLTRB(24, 24, 24, 32),
      child: Center(
        child: ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: 760),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Semantics(
                header: true,
                child: Text(
                  status.firstRunComplete
                      ? l10n.firstRunReadyTitle
                      : l10n.firstRunNeedsAttentionTitle,
                  style: theme.textTheme.headlineMedium,
                ),
              ),
              const SizedBox(height: 12),
              Text(
                l10n.firstRunDescription,
                style: theme.textTheme.bodyLarge?.copyWith(
                  color: theme.colorScheme.onSurfaceVariant,
                ),
              ),
              const SizedBox(height: 24),
              _IdentityCard(status: status),
              if (status.access.canAdministerWorkspace) ...[
                const SizedBox(height: 24),
                const _FirstRunAdminSetupCard(),
              ],
              const SizedBox(height: 24),
              Semantics(
                header: true,
                child: Text(
                  l10n.firstRunModuleSectionTitle,
                  style: theme.textTheme.titleLarge,
                ),
              ),
              const SizedBox(height: 12),
              LayoutBuilder(
                builder: (context, constraints) {
                  final useTwoColumns = constraints.maxWidth >= 680;
                  return Wrap(
                    spacing: 12,
                    runSpacing: 12,
                    children: [
                      _ModuleStatusCard(
                        width: useTwoColumns
                            ? (constraints.maxWidth - 12) / 2
                            : constraints.maxWidth,
                        icon: Icons.badge_outlined,
                        title: l10n.firstRunProfileModuleTitle,
                        stateLabel: status.profile.isReady
                            ? l10n.firstRunStateReady
                            : l10n.firstRunStatePending,
                        state: status.profile.isReady
                            ? FirstRunProvisioningState.ready
                            : FirstRunProvisioningState.pending,
                        message: status.profile.message,
                        action: status.profile.action,
                      ),
                      _ModuleStatusCard(
                        width: useTwoColumns
                            ? (constraints.maxWidth - 12) / 2
                            : constraints.maxWidth,
                        icon: Icons.chat_bubble_outline,
                        title: l10n.firstRunChatModuleTitle,
                        stateLabel: _stateLabel(
                          l10n,
                          status.moduleProvisioning.matrix.state,
                        ),
                        state: status.moduleProvisioning.matrix.state,
                        message: status.moduleProvisioning.matrix.message,
                        action: status.moduleProvisioning.matrix.action,
                      ),
                      _ModuleStatusCard(
                        width: useTwoColumns
                            ? (constraints.maxWidth - 12) / 2
                            : constraints.maxWidth,
                        icon: Icons.folder_outlined,
                        title: l10n.firstRunFilesModuleTitle,
                        stateLabel: _stateLabel(
                          l10n,
                          status.moduleProvisioning.nextcloud.state,
                        ),
                        state: status.moduleProvisioning.nextcloud.state,
                        message: status.moduleProvisioning.nextcloud.message,
                        action: status.moduleProvisioning.nextcloud.action,
                      ),
                      _ModuleStatusCard(
                        width: useTwoColumns
                            ? (constraints.maxWidth - 12) / 2
                            : constraints.maxWidth,
                        icon: Icons.calendar_today_outlined,
                        title: l10n.firstRunCalendarModuleTitle,
                        stateLabel: _stateLabel(
                          l10n,
                          status.moduleProvisioning.nextcloud.state,
                        ),
                        state: status.moduleProvisioning.nextcloud.state,
                        message: status.moduleProvisioning.nextcloud.message,
                        action: status.moduleProvisioning.nextcloud.action,
                      ),
                    ],
                  );
                },
              ),
              if (status.actions.isNotEmpty) ...[
                const SizedBox(height: 24),
                _NextStepsCard(actions: status.actions),
              ],
              const SizedBox(height: 24),
              Wrap(
                spacing: 12,
                runSpacing: 12,
                alignment: WrapAlignment.end,
                children: [
                  AccessibleButton(
                    outlined: true,
                    onPressed: () => ref.invalidate(firstRunStatusProvider),
                    semanticLabel: l10n.firstRunRefreshButton,
                    child: Text(l10n.firstRunRefreshButton),
                  ),
                  if (status.firstRunComplete)
                    AccessibleButton(
                      onPressed: () => context.go(AppRoutes.chat),
                      semanticLabel: l10n.firstRunContinueButton,
                      child: Text(l10n.firstRunContinueButton),
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

class _IdentityCard extends StatelessWidget {
  const _IdentityCard({required this.status});

  final FirstRunStatus status;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final theme = Theme.of(context);
    final identity = status.identity;

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
            Semantics(
              header: true,
              child: Text(
                l10n.firstRunIdentitySectionTitle,
                style: theme.textTheme.titleLarge,
              ),
            ),
            const SizedBox(height: 8),
            Text(
              l10n.firstRunIdentitySectionDescription,
              style: theme.textTheme.bodyMedium?.copyWith(
                color: theme.colorScheme.onSurfaceVariant,
              ),
            ),
            const SizedBox(height: 16),
            MergeSemantics(
              child: Column(
                children: [
                  _DetailRow(
                    label: l10n.firstRunDisplayNameLabel,
                    value: identity.displayName,
                  ),
                  _DetailRow(
                    label: l10n.firstRunUsernameLabel,
                    value: identity.username,
                  ),
                  _DetailRow(
                    label: l10n.firstRunEmailLabel,
                    value: identity.email ?? '—',
                  ),
                  _DetailRow(
                    label: l10n.firstRunRoleLabel,
                    value: status.access.primaryRole,
                  ),
                  _DetailRow(
                    label: l10n.firstRunInviteStatusLabel,
                    value: status.invite.status,
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _FirstRunAdminSetupCard extends StatelessWidget {
  const _FirstRunAdminSetupCard();

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final theme = Theme.of(context);

    return Card(
      elevation: 0,
      color: theme.colorScheme.primaryContainer.withValues(alpha: 0.24),
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(24),
        side: BorderSide(
          color: theme.colorScheme.primary.withValues(alpha: 0.42),
        ),
      ),
      child: Padding(
        padding: const EdgeInsets.all(20),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Icon(
              Icons.admin_panel_settings_outlined,
              color: theme.colorScheme.primary,
            ),
            const SizedBox(width: 12),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Semantics(
                    header: true,
                    child: Text(
                      l10n.firstRunAdminSetupTitle,
                      style: theme.textTheme.titleLarge,
                    ),
                  ),
                  const SizedBox(height: 8),
                  Text(
                    l10n.firstRunAdminSetupDescription,
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
    );
  }
}

class _ModuleStatusCard extends StatelessWidget {
  const _ModuleStatusCard({
    required this.width,
    required this.icon,
    required this.title,
    required this.stateLabel,
    required this.state,
    required this.message,
    this.action,
  });

  final double width;
  final IconData icon;
  final String title;
  final String stateLabel;
  final FirstRunProvisioningState state;
  final String message;
  final String? action;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final colors = _colorsForState(theme.colorScheme, state);

    return SizedBox(
      width: width,
      child: Semantics(
        container: true,
        label:
            '$title. $stateLabel. $message${action == null ? '' : '. $action'}',
        child: ExcludeSemantics(
          child: Card(
            elevation: 0,
            color: colors.background,
            shape: RoundedRectangleBorder(
              borderRadius: BorderRadius.circular(20),
              side: BorderSide(color: colors.border),
            ),
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Icon(icon, color: colors.foreground),
                      const SizedBox(width: 12),
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(title, style: theme.textTheme.titleMedium),
                            const SizedBox(height: 4),
                            Text(
                              stateLabel,
                              style: theme.textTheme.labelLarge?.copyWith(
                                color: colors.foreground,
                              ),
                            ),
                          ],
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 12),
                  Text(message, style: theme.textTheme.bodyMedium),
                  if (action != null) ...[
                    const SizedBox(height: 8),
                    Text(
                      action!,
                      style: theme.textTheme.bodyMedium?.copyWith(
                        color: theme.colorScheme.onSurfaceVariant,
                      ),
                    ),
                  ],
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}

class _NextStepsCard extends StatelessWidget {
  const _NextStepsCard({required this.actions});

  final List<String> actions;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final theme = Theme.of(context);

    return Card(
      child: Padding(
        padding: const EdgeInsets.all(20),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Semantics(
              header: true,
              child: Text(
                l10n.firstRunNextStepsTitle,
                style: theme.textTheme.titleMedium,
              ),
            ),
            const SizedBox(height: 12),
            ...actions.map(
              (action) => Padding(
                padding: const EdgeInsets.symmetric(vertical: 4),
                child: Row(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    const Text('•'),
                    const SizedBox(width: 8),
                    Expanded(child: Text(action)),
                  ],
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _DetailRow extends StatelessWidget {
  const _DetailRow({required this.label, required this.value});

  final String label;
  final String value;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 6),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          SizedBox(
            width: 132,
            child: Text(label, style: theme.textTheme.labelLarge),
          ),
          const SizedBox(width: 12),
          Expanded(child: Text(value, style: theme.textTheme.bodyMedium)),
        ],
      ),
    );
  }
}

String _stateLabel(AppLocalizations l10n, FirstRunProvisioningState state) {
  return switch (state) {
    FirstRunProvisioningState.notConfigured => l10n.firstRunStateUnavailable,
    FirstRunProvisioningState.pending => l10n.firstRunStatePending,
    FirstRunProvisioningState.ready => l10n.firstRunStateReady,
    FirstRunProvisioningState.degraded => l10n.firstRunStateDegraded,
    FirstRunProvisioningState.failed => l10n.firstRunStateActionNeeded,
  };
}

_FirstRunStatusColors _colorsForState(
  ColorScheme colorScheme,
  FirstRunProvisioningState state,
) {
  return switch (state) {
    FirstRunProvisioningState.ready => _FirstRunStatusColors(
      foreground: colorScheme.primary,
      background: colorScheme.primaryContainer.withValues(alpha: 0.28),
      border: colorScheme.primary.withValues(alpha: 0.45),
    ),
    FirstRunProvisioningState.pending => _FirstRunStatusColors(
      foreground: colorScheme.tertiary,
      background: colorScheme.tertiaryContainer.withValues(alpha: 0.32),
      border: colorScheme.tertiary.withValues(alpha: 0.45),
    ),
    FirstRunProvisioningState.degraded => _FirstRunStatusColors(
      foreground: colorScheme.secondary,
      background: colorScheme.secondaryContainer.withValues(alpha: 0.32),
      border: colorScheme.secondary.withValues(alpha: 0.45),
    ),
    FirstRunProvisioningState.notConfigured ||
    FirstRunProvisioningState.failed => _FirstRunStatusColors(
      foreground: colorScheme.error,
      background: colorScheme.errorContainer.withValues(alpha: 0.32),
      border: colorScheme.error.withValues(alpha: 0.45),
    ),
  };
}

class _FirstRunStatusColors {
  const _FirstRunStatusColors({
    required this.foreground,
    required this.background,
    required this.border,
  });

  final Color foreground;
  final Color background;
  final Color border;
}
