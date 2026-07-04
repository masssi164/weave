import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:weave/core/a11y/semantic_button.dart';
import 'package:weave/core/a11y/semantic_list_tile.dart';
import 'package:weave/core/bootstrap/presentation/providers/app_bootstrap_provider.dart';
import 'package:weave/core/config/feature_flags.dart';
import 'package:weave/core/l10n/app_locale_preference.dart';
import 'package:weave/core/l10n/app_locale_preference_provider.dart';
import 'package:weave/core/router/app_routes.dart';
import 'package:weave/core/theme/app_theme_preference.dart';
import 'package:weave/core/theme/app_theme_preference_provider.dart';
import 'package:weave/core/widgets/error_state.dart';
import 'package:weave/core/widgets/loading_state.dart';
import 'package:weave/core/widgets/weave_logo.dart';
import 'package:weave/features/app/domain/entities/integration_invalidation.dart';
import 'package:weave/features/app/domain/entities/provider_stack_snapshot.dart';
import 'package:weave/features/app/domain/entities/workspace_capability_snapshot.dart';
import 'package:weave/features/app/domain/entities/workspace_connection_state.dart';
import 'package:weave/features/app/presentation/workspace_capability_recovery_presenter.dart';
import 'package:weave/features/agents/domain/entities/agent_capability_policy.dart';
import 'package:weave/features/agents/presentation/providers/agent_capability_policy_provider.dart';
import 'package:weave/features/agents/presentation/widgets/agent_capability_policy_card.dart';
import 'package:weave/features/app/presentation/providers/workspace_connection_provider.dart';
import 'package:weave/features/auth/presentation/providers/auth_flow_controller.dart';
import 'package:weave/features/connectors/presentation/providers/connector_preview_provider.dart';
import 'package:weave/features/connectors/presentation/widgets/connector_settings_preview_card.dart';
import 'package:weave/features/guests/presentation/providers/guest_preview_provider.dart';
import 'package:weave/features/guests/presentation/widgets/guest_access_preview_card.dart';
import 'package:weave/features/profile/domain/entities/user_profile.dart';
import 'package:weave/features/profile/presentation/providers/user_profile_provider.dart';
import 'package:weave/features/server_config/domain/entities/server_configuration.dart';
import 'package:weave/features/server_config/presentation/providers/'
    'server_configuration_form_controller.dart';
import 'package:weave/features/server_config/presentation/widgets/provider_category_summary.dart';
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
    final authState = ref.watch(authFlowControllerProvider);
    final profile = ref.watch(userProfileProvider);
    final canAdministerWorkspace = profile.maybeWhen(
      data: (user) => user?.canAdministerWorkspace ?? false,
      orElse: () => false,
    );

    return CustomScrollView(
      slivers: [
        SliverAppBar.large(title: Text(l10n.settingsScreenTitle)),
        SliverPadding(
          padding: const EdgeInsets.all(24),
          sliver: SliverToBoxAdapter(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                const _SettingsBrandCard(),
                const SizedBox(height: 32),
                const _ThemePreferenceSection(),
                const SizedBox(height: 32),
                const _LanguagePreferenceSection(),
                const SizedBox(height: 32),
                const _ProfileSettingsLinkCard(),
                const SizedBox(height: 32),
                const _WeaverMemberSettingsSection(),
                const SizedBox(height: 32),
                const _SettingsHelpCard(),
                const SizedBox(height: 32),
                const _ShellModuleVisibilitySettingsSection(),
                if (canAdministerWorkspace) ...[
                  const SizedBox(height: 32),
                  const _WorkspaceHealthLinkCard(),
                ],
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
      ],
    );
  }
}

class WorkspaceHealthScreen extends ConsumerWidget {
  const WorkspaceHealthScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    final profile = ref.watch(userProfileProvider);

    return CustomScrollView(
      slivers: [
        SliverAppBar.large(title: Text(l10n.workspaceHealthTitle)),
        SliverPadding(
          padding: const EdgeInsets.all(24),
          sliver: SliverToBoxAdapter(
            child: switch (profile) {
              AsyncData(value: final user) =>
                user != null && user.canAdministerWorkspace
                    ? const _AdminOnlySettingsSections()
                    : const _AdminSetupBoundaryCard(),
              AsyncError() => const _AdminSetupBoundaryCard(showRetry: true),
              _ => LoadingState(
                message: l10n.settingsAdminPermissionLoading,
                icon: Icons.admin_panel_settings_outlined,
              ),
            },
          ),
        ),
      ],
    );
  }
}

class _WorkspaceHealthLinkCard extends StatelessWidget {
  const _WorkspaceHealthLinkCard();

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final theme = Theme.of(context);

    return Semantics(
      button: true,
      child: Card(
        elevation: 0,
        color: theme.colorScheme.surfaceContainerLow,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(8),
          side: BorderSide(color: theme.colorScheme.outlineVariant),
        ),
        child: ListTile(
          leading: Icon(
            Icons.admin_panel_settings_outlined,
            color: theme.colorScheme.primary,
          ),
          title: Text(l10n.settingsWorkspaceHealthLinkTitle),
          subtitle: Text(l10n.settingsWorkspaceHealthLinkDescription),
          trailing: const Icon(Icons.chevron_right),
          onTap: () => context.push(AppRoutes.workspaceHealth),
        ),
      ),
    );
  }
}

class _ProfileSettingsLinkCard extends ConsumerWidget {
  const _ProfileSettingsLinkCard();

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    final theme = Theme.of(context);
    final profile = ref.watch(userProfileProvider);
    final subtitle = profile.maybeWhen(
      data: (user) => user == null
          ? l10n.settingsProfileLinkSignedOutDescription
          : l10n.settingsProfileLinkDescription(user.displayName),
      orElse: () => l10n.settingsProfileLinkLoadingDescription,
    );

    return Semantics(
      button: true,
      child: Card(
        elevation: 0,
        color: theme.colorScheme.surfaceContainerLow,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(8),
          side: BorderSide(color: theme.colorScheme.outlineVariant),
        ),
        child: ListTile(
          leading: Icon(
            Icons.account_circle_outlined,
            color: theme.colorScheme.primary,
          ),
          title: Text(l10n.settingsProfileLinkTitle),
          subtitle: Text(subtitle),
          trailing: const Icon(Icons.chevron_right),
          onTap: () => context.push(AppRoutes.profile),
        ),
      ),
    );
  }
}

class _AdminOnlySettingsSections extends ConsumerWidget {
  const _AdminOnlySettingsSections();

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    final savedConfiguration = ref.watch(savedServerConfigurationProvider);

    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        const _WorkspaceReadinessCard(),
        const SizedBox(height: 32),
        const _AgentCapabilityPolicySection(),
        if (FeatureFlags.hasFeatureGatedSurfaces) ...[
          const SizedBox(height: 32),
          const _FeaturePreviewSurfacesSection(),
        ],
        const SizedBox(height: 32),
        savedConfiguration.when(
          loading: () => LoadingState(message: l10n.loadingLabel),
          error: (error, _) => ErrorState(
            message: l10n.errorStateLabel,
            retryLabel: l10n.retryButton,
            onRetry: () => ref.invalidate(savedServerConfigurationProvider),
          ),
          data: (configuration) =>
              _AdminSetupSection(configuration: configuration),
        ),
      ],
    );
  }
}

class _AdminSetupSection extends ConsumerWidget {
  const _AdminSetupSection({required this.configuration});

  final ServerConfiguration? configuration;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final profile = ref.watch(userProfileProvider);

    return switch (profile) {
      AsyncData(value: final user) =>
        user != null && user.canAdministerWorkspace
            ? _AdminSetupConfigurationCard(
                configuration: configuration,
                profile: user,
              )
            : const _AdminSetupBoundaryCard(),
      AsyncError() => const _AdminSetupBoundaryCard(showRetry: true),
      _ => const _AdminSetupLoadingCard(),
    };
  }
}

class _AdminSetupConfigurationCard extends ConsumerWidget {
  const _AdminSetupConfigurationCard({
    required this.configuration,
    required this.profile,
  });

  final ServerConfiguration? configuration;
  final UserProfile profile;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    final theme = Theme.of(context);

    return Card(
      elevation: 0,
      color: theme.colorScheme.surfaceContainerLow,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(24),
        side: BorderSide(color: theme.colorScheme.primary),
      ),
      child: Padding(
        padding: const EdgeInsets.all(20),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Semantics(
              header: true,
              child: Text(
                l10n.settingsAdminSetupTitle,
                style: theme.textTheme.headlineSmall,
              ),
            ),
            const SizedBox(height: 8),
            Text(
              l10n.settingsAdminSetupDescription,
              style: theme.textTheme.bodyMedium?.copyWith(
                color: theme.colorScheme.onSurfaceVariant,
              ),
            ),
            const SizedBox(height: 12),
            _AdminPermissionSummary(profile: profile),
            const SizedBox(height: 16),
            const ProviderCategorySummary(compact: true),
            const SizedBox(height: 16),
            _AdminManualEmbedCard(
              title: l10n.settingsAdminManualTitle,
              description: l10n.settingsAdminManualDescription,
              pathLabel: l10n.helpEmbeddedManualPathLabel,
              path: 'docs/admin-operator-handbook.md',
              permissionLabel: l10n.helpEmbeddedManualPermissionLabel,
              fallbackLabel: l10n.helpEmbeddedManualUnavailableLabel,
            ),
            const SizedBox(height: 24),
            Text(
              l10n.settingsServerConfigurationTitle,
              style: theme.textTheme.titleLarge,
            ),
            const SizedBox(height: 8),
            Text(
              l10n.settingsServerConfigurationDescription,
              style: theme.textTheme.bodyMedium?.copyWith(
                color: theme.colorScheme.onSurfaceVariant,
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
          ],
        ),
      ),
    );
  }
}

class _AdminManualEmbedCard extends StatelessWidget {
  const _AdminManualEmbedCard({
    required this.title,
    required this.description,
    required this.pathLabel,
    required this.path,
    required this.permissionLabel,
    required this.fallbackLabel,
  });

  final String title;
  final String description;
  final String pathLabel;
  final String path;
  final String permissionLabel;
  final String fallbackLabel;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return Semantics(
      container: true,
      label: '$title. $permissionLabel',
      child: DecoratedBox(
        decoration: BoxDecoration(
          color: theme.colorScheme.secondaryContainer.withValues(alpha: 0.28),
          borderRadius: BorderRadius.circular(16),
          border: Border.all(
            color: theme.colorScheme.secondary.withValues(alpha: 0.45),
          ),
        ),
        child: Padding(
          padding: const EdgeInsets.all(14),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Icon(
                    Icons.menu_book_outlined,
                    color: theme.colorScheme.secondary,
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    child: Semantics(
                      header: true,
                      child: Text(title, style: theme.textTheme.titleMedium),
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 8),
              Text(description, style: theme.textTheme.bodyMedium),
              const SizedBox(height: 10),
              Wrap(
                spacing: 8,
                runSpacing: 8,
                children: [
                  Chip(label: Text('$pathLabel $path')),
                  Chip(label: Text(permissionLabel)),
                ],
              ),
              const SizedBox(height: 8),
              Text(
                fallbackLabel,
                style: theme.textTheme.bodySmall?.copyWith(
                  color: theme.colorScheme.onSurfaceVariant,
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _AdminPermissionSummary extends StatelessWidget {
  const _AdminPermissionSummary({required this.profile});

  final UserProfile profile;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final theme = Theme.of(context);
    final roles = profile.roles.isEmpty ? '—' : profile.roles.join(', ');

    return Semantics(
      container: true,
      label: l10n.settingsAdminPermissionSemantic(roles),
      child: ExcludeSemantics(
        child: DecoratedBox(
          decoration: BoxDecoration(
            color: theme.colorScheme.primaryContainer.withValues(alpha: 0.28),
            borderRadius: BorderRadius.circular(16),
            border: Border.all(
              color: theme.colorScheme.primary.withValues(alpha: 0.45),
            ),
          ),
          child: Padding(
            padding: const EdgeInsets.all(14),
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
                      Text(
                        l10n.settingsAdminPermissionTitle,
                        style: theme.textTheme.titleMedium,
                      ),
                      const SizedBox(height: 4),
                      Text(
                        l10n.settingsAdminPermissionDescription(roles),
                        style: theme.textTheme.bodyMedium,
                      ),
                    ],
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

class _AdminSetupBoundaryCard extends ConsumerWidget {
  const _AdminSetupBoundaryCard({this.showRetry = false});

  final bool showRetry;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
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
        child: Semantics(
          container: true,
          explicitChildNodes: true,
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Semantics(
                header: true,
                child: Text(
                  l10n.settingsAdminBoundaryTitle,
                  style: theme.textTheme.titleLarge,
                ),
              ),
              const SizedBox(height: 8),
              Text(
                l10n.settingsAdminBoundaryDescription,
                style: theme.textTheme.bodyMedium?.copyWith(
                  color: theme.colorScheme.onSurfaceVariant,
                ),
              ),
              if (showRetry) ...[
                const SizedBox(height: 12),
                TextButton.icon(
                  onPressed: () => ref.invalidate(userProfileProvider),
                  icon: const Icon(Icons.refresh),
                  label: Text(l10n.retryButton),
                ),
              ],
            ],
          ),
        ),
      ),
    );
  }
}

class _AdminSetupLoadingCard extends StatelessWidget {
  const _AdminSetupLoadingCard();

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);

    return Card(
      child: Padding(
        padding: const EdgeInsets.all(20),
        child: LoadingState(
          message: l10n.settingsAdminPermissionLoading,
          icon: Icons.admin_panel_settings_outlined,
        ),
      ),
    );
  }
}

class _AgentCapabilityPolicySection extends ConsumerWidget {
  const _AgentCapabilityPolicySection();

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    final policy = ref.watch(agentCapabilityPolicyProvider);

    return switch (policy) {
      AsyncData(value: final value) => AgentCapabilityPolicyCard(policy: value),
      AsyncError() => ErrorState(
        message: l10n.agentCapabilityPolicyErrorTitle,
        retryLabel: l10n.retryButton,
        onRetry: () => ref.invalidate(agentCapabilityPolicyProvider),
      ),
      _ => LoadingState(
        message: l10n.agentCapabilityPolicyLoading,
        icon: Icons.admin_panel_settings_outlined,
      ),
    };
  }
}

class _WeaverMemberSettingsSection extends ConsumerWidget {
  const _WeaverMemberSettingsSection();

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    final policy = ref.watch(agentCapabilityPolicyProvider);

    return switch (policy) {
      AsyncData(value: final value) => _WeaverMemberSettingsCard(
        state: value.weaverMemberUx,
      ),
      AsyncError() => _WeaverMemberSettingsCard(
        state: WeaverMemberUxState.blockedState,
        statusOverride: l10n.weaverMemberStatusUnavailable,
      ),
      _ => LoadingState(
        message: l10n.weaverMemberLoading,
        icon: Icons.auto_awesome_outlined,
      ),
    };
  }
}

class _WeaverMemberSettingsCard extends StatelessWidget {
  const _WeaverMemberSettingsCard({required this.state, this.statusOverride});

  final WeaverMemberUxState state;
  final String? statusOverride;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final theme = Theme.of(context);
    final available = state.available;
    final title = available
        ? l10n.weaverMemberTitle
        : l10n.weaverMemberUnavailableTitle;
    final description = available
        ? l10n.weaverMemberDescription
        : l10n.weaverMemberUnavailableDescription;

    return Card(
      elevation: 0,
      color: theme.colorScheme.surfaceContainerLow,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(24),
        side: BorderSide(color: theme.colorScheme.outlineVariant),
      ),
      child: Padding(
        padding: const EdgeInsets.all(20),
        child: Semantics(
          container: true,
          explicitChildNodes: true,
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Icon(
                    available
                        ? Icons.auto_awesome_outlined
                        : Icons.lock_outline,
                    color: theme.colorScheme.primary,
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Semantics(
                          header: true,
                          child: Text(title, style: theme.textTheme.titleLarge),
                        ),
                        const SizedBox(height: 8),
                        Text(
                          description,
                          style: theme.textTheme.bodyMedium?.copyWith(
                            color: theme.colorScheme.onSurfaceVariant,
                          ),
                        ),
                      ],
                    ),
                  ),
                  const SizedBox(width: 8),
                  Chip(
                    avatar: Icon(
                      available ? Icons.verified_user_outlined : Icons.policy,
                      size: 18,
                    ),
                    label: Text(
                      statusOverride ??
                          (available
                              ? l10n.weaverMemberStatusAvailable
                              : state.isBlocked
                              ? l10n.weaverMemberStatusUnavailable
                              : l10n.weaverMemberStatusDisabled),
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 16),
              if (available) ...[
                _AdminApprovedModelAliasPicker(aliases: state.modelAliases),
                const SizedBox(height: 16),
                _WeaverPersonalSettingsList(state: state),
                const SizedBox(height: 16),
                _WeaverAllowedItemsWrap(
                  title: l10n.weaverMemberAllowedSkillsTitle,
                  emptyLabel: l10n.weaverMemberNoAllowedSkills,
                  items: state.allowedSkills,
                ),
                const SizedBox(height: 12),
                _WeaverAllowedItemsWrap(
                  title: l10n.weaverMemberAllowedConnectionsTitle,
                  emptyLabel: l10n.weaverMemberNoAllowedConnections,
                  items: state.allowedPersonalConnections,
                ),
                const SizedBox(height: 16),
                _WeaverBoundaryNotice(text: l10n.weaverMemberBoundaryNotice),
              ] else
                _WeaverBoundaryNotice(
                  text: l10n.weaverMemberDisabledBoundaryNotice,
                ),
            ],
          ),
        ),
      ),
    );
  }
}

class _AdminApprovedModelAliasPicker extends StatefulWidget {
  const _AdminApprovedModelAliasPicker({required this.aliases});

  final List<String> aliases;

  @override
  State<_AdminApprovedModelAliasPicker> createState() =>
      _AdminApprovedModelAliasPickerState();
}

class _AdminApprovedModelAliasPickerState
    extends State<_AdminApprovedModelAliasPicker> {
  String? _selectedAlias;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final theme = Theme.of(context);
    final aliases = widget.aliases.isEmpty
        ? <String>[l10n.weaverMemberWorkspaceDefaultAlias]
        : widget.aliases;
    _selectedAlias ??= aliases.first;

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          l10n.weaverMemberModelAliasTitle,
          style: theme.textTheme.titleMedium,
        ),
        const SizedBox(height: 4),
        Text(
          l10n.weaverMemberModelAliasDescription,
          style: theme.textTheme.bodySmall?.copyWith(
            color: theme.colorScheme.onSurfaceVariant,
          ),
        ),
        const SizedBox(height: 8),
        RadioGroup<String>(
          groupValue: _selectedAlias,
          onChanged: (value) => setState(() => _selectedAlias = value),
          child: Column(
            children: [
              for (final alias in aliases)
                RadioListTile<String>.adaptive(
                  contentPadding: EdgeInsets.zero,
                  value: alias,
                  title: Text(alias),
                  subtitle: Text(l10n.weaverMemberApprovedByAdmin),
                  controlAffinity: ListTileControlAffinity.leading,
                ),
            ],
          ),
        ),
      ],
    );
  }
}

class _WeaverPersonalSettingsList extends StatelessWidget {
  const _WeaverPersonalSettingsList({required this.state});

  final WeaverMemberUxState state;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final theme = Theme.of(context);

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          l10n.weaverMemberPersonalSettingsTitle,
          style: theme.textTheme.titleMedium,
        ),
        const SizedBox(height: 8),
        _WeaverSettingRow(
          label: l10n.weaverMemberStyleSetting,
          enabled: state.canConfigureStyle,
        ),
        _WeaverSettingRow(
          label: l10n.weaverMemberMemorySetting,
          enabled: state.canConfigureMemory,
        ),
        _WeaverSettingRow(
          label: l10n.weaverMemberWorkspaceSetting,
          enabled: state.canConfigureWorkspace,
        ),
      ],
    );
  }
}

class _WeaverSettingRow extends StatelessWidget {
  const _WeaverSettingRow({required this.label, required this.enabled});

  final String label;
  final bool enabled;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    return CheckboxListTile.adaptive(
      contentPadding: EdgeInsets.zero,
      value: enabled,
      onChanged: null,
      title: Text(label),
      subtitle: Text(
        enabled
            ? l10n.weaverMemberSettingAllowed
            : l10n.weaverMemberSettingDisabled,
      ),
      controlAffinity: ListTileControlAffinity.leading,
    );
  }
}

class _WeaverAllowedItemsWrap extends StatelessWidget {
  const _WeaverAllowedItemsWrap({
    required this.title,
    required this.emptyLabel,
    required this.items,
  });

  final String title;
  final String emptyLabel;
  final List<String> items;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(title, style: theme.textTheme.titleMedium),
        const SizedBox(height: 8),
        if (items.isEmpty)
          Text(
            emptyLabel,
            style: theme.textTheme.bodyMedium?.copyWith(
              color: theme.colorScheme.onSurfaceVariant,
            ),
          )
        else
          Wrap(
            spacing: 8,
            runSpacing: 8,
            children: [
              for (final item in items)
                Chip(
                  avatar: const Icon(Icons.check_circle_outline, size: 18),
                  label: Text(item),
                ),
            ],
          ),
      ],
    );
  }
}

class _WeaverBoundaryNotice extends StatelessWidget {
  const _WeaverBoundaryNotice({required this.text});

  final String text;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return MergeSemantics(
      child: DecoratedBox(
        decoration: BoxDecoration(
          color: theme.colorScheme.secondaryContainer.withValues(alpha: 0.22),
          borderRadius: BorderRadius.circular(16),
          border: Border.all(color: theme.colorScheme.outlineVariant),
        ),
        child: Padding(
          padding: const EdgeInsets.all(12),
          child: Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Icon(
                Icons.privacy_tip_outlined,
                color: theme.colorScheme.primary,
              ),
              const SizedBox(width: 10),
              Expanded(child: Text(text, style: theme.textTheme.bodyMedium)),
            ],
          ),
        ),
      ),
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

class _ThemePreferenceSection extends ConsumerWidget {
  const _ThemePreferenceSection();

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    final theme = Theme.of(context);
    final selection = ref.watch(appThemePreferenceProvider);

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
                l10n.settingsThemeTitle,
                style: theme.textTheme.titleLarge,
              ),
            ),
            const SizedBox(height: 8),
            Text(
              l10n.settingsThemeDescription,
              style: theme.textTheme.bodyMedium?.copyWith(
                color: theme.colorScheme.onSurfaceVariant,
              ),
            ),
            const SizedBox(height: 12),
            switch (selection) {
              AsyncData(value: final value) => RadioGroup<AppThemePreference>(
                groupValue: value.effectivePreference,
                onChanged: (preference) {
                  if (preference == null) {
                    return;
                  }
                  unawaited(
                    ref
                        .read(appThemePreferenceProvider.notifier)
                        .setUserPreference(preference),
                  );
                },
                child: Column(
                  children: [
                    for (final preference in AppThemePreference.values)
                      _ThemePreferenceTile(
                        preference: preference,
                        selected: value.effectivePreference == preference,
                      ),
                  ],
                ),
              ),
              AsyncError() => ErrorState(
                message: l10n.settingsThemeError,
                retryLabel: l10n.retryButton,
                onRetry: () => ref.invalidate(appThemePreferenceProvider),
              ),
              _ => Padding(
                padding: const EdgeInsets.symmetric(vertical: 16),
                child: Text(
                  l10n.settingsThemeLoading,
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

class _ThemePreferenceTile extends StatelessWidget {
  const _ThemePreferenceTile({
    required this.preference,
    required this.selected,
  });

  final AppThemePreference preference;
  final bool selected;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);

    return RadioListTile<AppThemePreference>.adaptive(
      contentPadding: EdgeInsets.zero,
      value: preference,
      selected: selected,
      title: Text(_title(l10n)),
      subtitle: Text(_description(l10n)),
      controlAffinity: ListTileControlAffinity.leading,
      secondary: ExcludeSemantics(child: Icon(_icon)),
    );
  }

  IconData get _icon {
    return switch (preference) {
      AppThemePreference.system => Icons.brightness_auto_outlined,
      AppThemePreference.light => Icons.light_mode_outlined,
      AppThemePreference.dark => Icons.dark_mode_outlined,
      AppThemePreference.highContrast => Icons.contrast_outlined,
    };
  }

  String _title(AppLocalizations l10n) {
    return switch (preference) {
      AppThemePreference.system => l10n.settingsThemeSystemTitle,
      AppThemePreference.light => l10n.settingsThemeLightTitle,
      AppThemePreference.dark => l10n.settingsThemeDarkTitle,
      AppThemePreference.highContrast => l10n.settingsThemeHighContrastTitle,
    };
  }

  String _description(AppLocalizations l10n) {
    return switch (preference) {
      AppThemePreference.system => l10n.settingsThemeSystemDescription,
      AppThemePreference.light => l10n.settingsThemeLightDescription,
      AppThemePreference.dark => l10n.settingsThemeDarkDescription,
      AppThemePreference.highContrast =>
        l10n.settingsThemeHighContrastDescription,
    };
  }
}

class _LanguagePreferenceSection extends ConsumerWidget {
  const _LanguagePreferenceSection();

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    final theme = Theme.of(context);
    final selection = ref.watch(appLocalePreferenceProvider);

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
                l10n.settingsLanguageTitle,
                style: theme.textTheme.titleLarge,
              ),
            ),
            const SizedBox(height: 8),
            Text(
              l10n.settingsLanguageDescription,
              style: theme.textTheme.bodyMedium?.copyWith(
                color: theme.colorScheme.onSurfaceVariant,
              ),
            ),
            const SizedBox(height: 12),
            switch (selection) {
              AsyncData(value: final value) => RadioGroup<AppLocalePreference>(
                groupValue: value.effectivePreference,
                onChanged: (preference) {
                  if (preference == null) {
                    return;
                  }
                  unawaited(
                    ref
                        .read(appLocalePreferenceProvider.notifier)
                        .setUserPreference(preference),
                  );
                },
                child: Column(
                  children: [
                    for (final preference in AppLocalePreference.values)
                      _LanguagePreferenceTile(
                        preference: preference,
                        selected: value.effectivePreference == preference,
                      ),
                  ],
                ),
              ),
              AsyncError() => ErrorState(
                message: l10n.settingsLanguageError,
                retryLabel: l10n.retryButton,
                onRetry: () => ref.invalidate(appLocalePreferenceProvider),
              ),
              _ => Padding(
                padding: const EdgeInsets.symmetric(vertical: 16),
                child: Text(
                  l10n.settingsLanguageLoading,
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

class _LanguagePreferenceTile extends StatelessWidget {
  const _LanguagePreferenceTile({
    required this.preference,
    required this.selected,
  });

  final AppLocalePreference preference;
  final bool selected;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);

    return RadioListTile<AppLocalePreference>.adaptive(
      contentPadding: EdgeInsets.zero,
      value: preference,
      selected: selected,
      title: Text(_title(l10n)),
      subtitle: Text(_description(l10n)),
      controlAffinity: ListTileControlAffinity.leading,
      secondary: ExcludeSemantics(child: Icon(_icon)),
    );
  }

  IconData get _icon {
    return switch (preference) {
      AppLocalePreference.system => Icons.language_outlined,
      AppLocalePreference.english => Icons.translate_outlined,
      AppLocalePreference.german => Icons.translate_outlined,
    };
  }

  String _title(AppLocalizations l10n) {
    return switch (preference) {
      AppLocalePreference.system => l10n.settingsLanguageSystemTitle,
      AppLocalePreference.english => l10n.settingsLanguageEnglishTitle,
      AppLocalePreference.german => l10n.settingsLanguageGermanTitle,
    };
  }

  String _description(AppLocalizations l10n) {
    return switch (preference) {
      AppLocalePreference.system => l10n.settingsLanguageSystemDescription,
      AppLocalePreference.english => l10n.settingsLanguageEnglishDescription,
      AppLocalePreference.german => l10n.settingsLanguageGermanDescription,
    };
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
    final canViewWorkspaceHealth = ref
        .watch(userProfileProvider)
        .maybeWhen(
          data: (profile) => profile?.canAdministerWorkspace ?? false,
          orElse: () => false,
        );
    final providerStackSnapshot = canViewWorkspaceHealth
        ? ref.watch(weaveApiProviderStackSnapshotProvider).asData?.value
        : null;
    final officeCapabilitiesSnapshot = canViewWorkspaceHealth
        ? ref.watch(weaveApiOfficeCapabilitiesSnapshotProvider).asData?.value
        : null;

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
                      ref.invalidate(weaveApiProviderStackSnapshotProvider);
                      ref.invalidate(
                        weaveApiOfficeCapabilitiesSnapshotProvider,
                      );
                    },
                  ),
                ],
                const SizedBox(height: 16),
                Text(
                  _workspaceSummary(l10n, workspaceState, capabilitySnapshot),
                  style: theme.textTheme.bodyMedium,
                ),
                if (providerStackSnapshot case final stack?) ...[
                  const SizedBox(height: 20),
                  _ProviderStackReadinessSummary(
                    stack: stack,
                    officeCapabilities: officeCapabilitiesSnapshot,
                  ),
                ],
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
                  connection: workspaceState.chat,
                ),
                const Divider(height: 32),
                _WorkspaceReadinessRow(
                  label: l10n.settingsWorkspaceFilesLabel,
                  capability: capabilitySnapshot.files,
                  connection: workspaceState.files,
                ),
                const Divider(height: 32),
                _WorkspaceReadinessRow(
                  label: l10n.settingsWorkspaceCalendarLabel,
                  capability: capabilitySnapshot.calendar,
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
              ref.invalidate(weaveApiWorkspaceCapabilitySnapshotProvider);
              ref.invalidate(weaveApiProviderStackSnapshotProvider);
              ref.invalidate(weaveApiOfficeCapabilitiesSnapshotProvider);
            },
          ),
          _ => LoadingState(message: l10n.loadingLabel),
        },
      ),
    );
  }

  bool _releaseServicesNeedAttention(WorkspaceCapabilitySnapshot capabilities) {
    return <WorkspaceCapabilityState>[
      capabilities.chat,
      capabilities.files,
    ].any(
      (capability) =>
          capability.readiness == WorkspaceCapabilityReadiness.blocked ||
          capability.readiness == WorkspaceCapabilityReadiness.degraded,
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
    WorkspaceCapabilitySnapshot capabilities,
  ) {
    if (workspace.shellAccessReady &&
        _releaseServicesNeedAttention(capabilities)) {
      return l10n.settingsWorkspaceSummaryDegraded;
    }

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

class _ProviderStackReadinessSummary extends StatelessWidget {
  const _ProviderStackReadinessSummary({
    required this.stack,
    this.officeCapabilities,
  });

  final ProviderStackSnapshot stack;
  final OfficeCapabilitiesSnapshot? officeCapabilities;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final theme = Theme.of(context);
    final categories = stack.categories;
    final providers = stack.providers;
    final posture = _overallPostureLabel(l10n, stack, categories, providers);
    final nextActions = _nextActions(l10n, stack, categories, providers);
    final flutterCalls = stack.flutterDirectProviderCallsAllowed
        ? l10n.settingsProviderStackFlutterCallsAllowed
        : l10n.settingsProviderStackFlutterCallsBlocked;

    return Semantics(
      container: true,
      explicitChildNodes: true,
      label: l10n.settingsProviderStackSemanticLabel(
        stack.backendOwnedFacades
            ? l10n.settingsProviderStackYes
            : l10n.settingsProviderStackNo,
        flutterCalls,
      ),
      child: DecoratedBox(
        decoration: BoxDecoration(
          color: theme.colorScheme.secondaryContainer.withValues(alpha: 0.22),
          borderRadius: BorderRadius.circular(16),
          border: Border.all(color: theme.colorScheme.outlineVariant),
        ),
        child: Padding(
          padding: const EdgeInsets.all(14),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                l10n.settingsProviderStackTitle,
                style: theme.textTheme.titleMedium,
              ),
              const SizedBox(height: 6),
              Text(
                stack.failClosed
                    ? l10n.settingsProviderStackFailClosedDescription
                    : l10n.settingsProviderStackNeedsReviewDescription,
                style: theme.textTheme.bodyMedium,
              ),
              const SizedBox(height: 12),
              Semantics(
                header: true,
                child: Text(
                  l10n.settingsAdminReadinessCockpitTitle,
                  style: theme.textTheme.titleSmall,
                ),
              ),
              const SizedBox(height: 6),
              Text(
                l10n.settingsAdminReadinessCockpitDescription,
                style: theme.textTheme.bodySmall?.copyWith(
                  color: theme.colorScheme.onSurfaceVariant,
                ),
              ),
              const SizedBox(height: 10),
              Wrap(
                spacing: 8,
                runSpacing: 8,
                children: [
                  _StatusPill(
                    label: l10n.settingsAdminReadinessOverallPostureLabel,
                    value: posture,
                  ),
                  _StatusPill(
                    label: l10n.settingsAdminReadinessCategoryHealthLabel,
                    value: l10n.settingsAdminReadinessCategoryHealthValue(
                      _readyCategoryCount(categories),
                      categories.length,
                      _attentionCategoryCount(categories),
                    ),
                  ),
                  _StatusPill(
                    label: l10n.settingsAdminReadinessEvidenceLabel,
                    value: stack.supportSafe
                        ? l10n.settingsAdminReadinessEvidenceRedacted
                        : l10n.settingsProviderStackNeedsReview,
                  ),
                  _StatusPill(
                    label: l10n.settingsAdminReadinessMemberBoundaryLabel,
                    value: l10n.settingsAdminReadinessMemberBoundaryHidden,
                  ),
                ],
              ),
              const SizedBox(height: 12),
              Text(
                l10n.settingsAdminReadinessNextActionsTitle,
                style: theme.textTheme.titleSmall,
              ),
              const SizedBox(height: 6),
              for (final action in nextActions)
                Padding(
                  padding: const EdgeInsets.only(top: 4),
                  child: Text('• $action', style: theme.textTheme.bodySmall),
                ),
              const SizedBox(height: 12),
              Wrap(
                spacing: 8,
                runSpacing: 8,
                children: [
                  _StatusPill(
                    label: l10n.settingsProviderStackBackendFacadesLabel,
                    value: stack.backendOwnedFacades
                        ? l10n.settingsProviderStackOwned
                        : l10n.settingsProviderStackMissing,
                  ),
                  _StatusPill(
                    label: l10n.settingsProviderStackFlutterCallsLabel,
                    value: stack.flutterDirectProviderCallsAllowed
                        ? l10n.settingsProviderStackNeedsReview
                        : l10n.settingsProviderStackBlocked,
                  ),
                  _StatusPill(
                    label: l10n.settingsProviderStackSupportSafetyLabel,
                    value: stack.supportSafe
                        ? l10n.settingsProviderStackRedacted
                        : l10n.settingsProviderStackNeedsReview,
                  ),
                ],
              ),
              if (officeCapabilities case final office?) ...[
                const SizedBox(height: 12),
                _OfficeProviderReadinessSummary(office: office),
              ],
              if (categories.isNotEmpty) ...[
                const SizedBox(height: 12),
                Semantics(
                  header: true,
                  child: Text(
                    l10n.settingsProviderCategoryHealthTitle,
                    style: theme.textTheme.titleSmall,
                  ),
                ),
                for (final category in categories)
                  Padding(
                    padding: const EdgeInsets.only(top: 8),
                    child: DecoratedBox(
                      decoration: BoxDecoration(
                        color: theme.colorScheme.surface,
                        borderRadius: BorderRadius.circular(14),
                        border: Border.all(
                          color: theme.colorScheme.outlineVariant,
                        ),
                      ),
                      child: Padding(
                        padding: const EdgeInsets.all(12),
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Wrap(
                              spacing: 8,
                              runSpacing: 8,
                              crossAxisAlignment: WrapCrossAlignment.center,
                              children: [
                                Text(
                                  '${category.label}: ${_providerCategoryReadinessLabel(l10n, category.readiness)}',
                                  style: theme.textTheme.bodySmall?.copyWith(
                                    fontWeight: FontWeight.w700,
                                  ),
                                ),
                                if (category.readiness ==
                                    ProviderCategoryReadiness.policyBlocked)
                                  _CompactStatusBadge(
                                    label:
                                        l10n.settingsWorkspaceCapabilityBlocked,
                                  ),
                                if (category.readiness ==
                                    ProviderCategoryReadiness.disabled)
                                  _CompactStatusBadge(
                                    label: l10n.settingsProviderStateDisabled,
                                  ),
                                if (category.readiness ==
                                    ProviderCategoryReadiness.misconfigured)
                                  _CompactStatusBadge(
                                    label:
                                        l10n.settingsProviderStateNotConfigured,
                                  ),
                                if (category.supportSafe)
                                  _CompactStatusBadge(
                                    label: l10n.settingsProviderStackRedacted,
                                  ),
                              ],
                            ),
                            const SizedBox(height: 6),
                            Text(
                              '${l10n.settingsWorkspaceImpactLabel}: ${category.memberImpact}',
                              style: theme.textTheme.bodySmall,
                            ),
                            const SizedBox(height: 4),
                            Text(
                              '${l10n.settingsWorkspacePolicyLabel}: ${category.policyState}',
                              style: theme.textTheme.bodySmall,
                            ),
                            const SizedBox(height: 4),
                            Text(
                              '${l10n.settingsAdminReadinessNextActionLabel}: ${_categoryNextAction(l10n, category)}',
                              style: theme.textTheme.bodySmall?.copyWith(
                                fontWeight: FontWeight.w600,
                              ),
                            ),
                            if (category.supportSafe) ...[
                              const SizedBox(height: 4),
                              Text(
                                l10n.settingsAdminReadinessCategoryEvidence,
                                style: theme.textTheme.bodySmall?.copyWith(
                                  color: theme.colorScheme.onSurfaceVariant,
                                ),
                              ),
                            ],
                            if (category.adapterEvidence.isNotEmpty) ...[
                              const SizedBox(height: 8),
                              Wrap(
                                spacing: 8,
                                runSpacing: 8,
                                crossAxisAlignment: WrapCrossAlignment.center,
                                children: [
                                  for (final evidence
                                      in category.adapterEvidence) ...[
                                    _CompactStatusBadge(
                                      label:
                                          '${evidence.adapterKey}: ${evidence.configured ? l10n.settingsProviderStateConfigured : l10n.settingsProviderStateNotConfigured}',
                                    ),
                                    _CompactStatusBadge(
                                      label: evidence.reachable
                                          ? l10n.settingsProviderStateReady
                                          : l10n.settingsWorkspaceCapabilityUnavailable,
                                    ),
                                    if (evidence.failClosed)
                                      _CompactStatusBadge(
                                        label: l10n
                                            .settingsProviderStackFailClosedBadge,
                                      ),
                                  ],
                                ],
                              ),
                            ],
                          ],
                        ),
                      ),
                    ),
                  ),
              ],
              if (providers.isNotEmpty) ...[
                const SizedBox(height: 12),
                Semantics(
                  header: true,
                  child: Text(
                    l10n.settingsProviderAdapterHealthTitle,
                    style: theme.textTheme.titleSmall,
                  ),
                ),
                for (final provider in providers)
                  Padding(
                    padding: const EdgeInsets.only(top: 6),
                    child: Wrap(
                      spacing: 8,
                      runSpacing: 8,
                      crossAxisAlignment: WrapCrossAlignment.center,
                      children: [
                        Text(
                          '${_providerModuleLabel(l10n, provider.module)}: ${_providerStateLabel(l10n, provider.state)}',
                          style: theme.textTheme.bodySmall?.copyWith(
                            fontWeight: FontWeight.w700,
                          ),
                        ),
                        if (provider.disabled)
                          _CompactStatusBadge(
                            label: l10n.settingsProviderStateDisabled,
                          ),
                        if (provider.unconfigured)
                          _CompactStatusBadge(
                            label: l10n.settingsProviderStateNotConfigured,
                          ),
                        if (provider.failClosed)
                          _CompactStatusBadge(
                            label: l10n.settingsProviderStackFailClosedBadge,
                          ),
                        if (provider.readOnly)
                          _CompactStatusBadge(
                            label: l10n.settingsProviderStackReadOnlyBadge,
                          ),
                        if (provider.paidFeaturesRequired)
                          _CompactStatusBadge(
                            label: l10n
                                .settingsProviderStackPaidFeaturesRequiredBadge,
                          ),
                        _CompactStatusBadge(
                          label:
                              '${l10n.settingsAdminReadinessNextActionLabel}: ${_providerNextAction(l10n, provider)}',
                        ),
                      ],
                    ),
                  ),
              ],
            ],
          ),
        ),
      ),
    );
  }

  int _readyCategoryCount(List<ProviderCategoryStatusSnapshot> categories) {
    return categories
        .where(
          (category) => category.readiness == ProviderCategoryReadiness.ready,
        )
        .length;
  }

  int _attentionCategoryCount(List<ProviderCategoryStatusSnapshot> categories) {
    return categories
        .where(
          (category) => category.readiness != ProviderCategoryReadiness.ready,
        )
        .length;
  }

  String _overallPostureLabel(
    AppLocalizations l10n,
    ProviderStackSnapshot stack,
    List<ProviderCategoryStatusSnapshot> categories,
    List<ProviderStatusSnapshot> providers,
  ) {
    if (!stack.backendOwnedFacades || stack.flutterDirectProviderCallsAllowed) {
      return l10n.settingsAdminReadinessPostureNeedsReview;
    }

    if (!stack.supportSafe) {
      return l10n.settingsAdminReadinessPostureEvidenceUnsafe;
    }

    if (categories.any(
          (category) =>
              category.readiness == ProviderCategoryReadiness.misconfigured ||
              category.readiness == ProviderCategoryReadiness.degraded,
        ) ||
        providers.any(
          (provider) => provider.unconfigured || provider.failClosed,
        )) {
      return l10n.settingsAdminReadinessPostureAdminAction;
    }

    if (categories.any(
      (category) =>
          category.readiness == ProviderCategoryReadiness.disabled ||
          category.readiness == ProviderCategoryReadiness.policyBlocked,
    )) {
      return l10n.settingsAdminReadinessPosturePolicyBoundary;
    }

    return l10n.settingsAdminReadinessPostureReady;
  }

  List<String> _nextActions(
    AppLocalizations l10n,
    ProviderStackSnapshot stack,
    List<ProviderCategoryStatusSnapshot> categories,
    List<ProviderStatusSnapshot> providers,
  ) {
    final actions = <String>[];
    if (!stack.backendOwnedFacades) {
      actions.add(l10n.settingsAdminReadinessActionRestoreBackendFacades);
    }
    if (stack.flutterDirectProviderCallsAllowed) {
      actions.add(l10n.settingsAdminReadinessActionBlockDirectProviderCalls);
    }
    if (!stack.supportSafe) {
      actions.add(l10n.settingsAdminReadinessActionRedactEvidence);
    }

    for (final category in categories) {
      final action = _categoryNextAction(l10n, category);
      if (action != l10n.settingsAdminReadinessActionNoOperatorAction &&
          !actions.contains(action)) {
        actions.add(action);
      }
    }

    for (final provider in providers) {
      final action = _providerNextAction(l10n, provider);
      if (action != l10n.settingsAdminReadinessActionNoOperatorAction &&
          !actions.contains(action)) {
        actions.add(action);
      }
    }

    if (actions.isEmpty) {
      actions.add(l10n.settingsAdminReadinessActionNoOperatorAction);
    }

    return actions.take(4).toList(growable: false);
  }

  String _categoryNextAction(
    AppLocalizations l10n,
    ProviderCategoryStatusSnapshot category,
  ) {
    return switch (category.readiness) {
      ProviderCategoryReadiness.ready =>
        l10n.settingsAdminReadinessActionNoOperatorAction,
      ProviderCategoryReadiness.disabled =>
        l10n.settingsAdminReadinessActionEnableOnlyWithPolicy,
      ProviderCategoryReadiness.degraded =>
        l10n.settingsAdminReadinessActionRunReadinessSmoke,
      ProviderCategoryReadiness.policyBlocked =>
        l10n.settingsAdminReadinessActionReviewWhitelistPolicy,
      ProviderCategoryReadiness.misconfigured =>
        l10n.settingsAdminReadinessActionConfigureSecretRefs,
      ProviderCategoryReadiness.unknown =>
        l10n.settingsAdminReadinessActionConfirmBackendEvidence,
    };
  }

  String _providerNextAction(
    AppLocalizations l10n,
    ProviderStatusSnapshot provider,
  ) {
    if (provider.unconfigured) {
      return l10n.settingsAdminReadinessActionConfigureSecretRefs;
    }
    if (provider.disabled) {
      return l10n.settingsAdminReadinessActionEnableOnlyWithPolicy;
    }
    if (provider.failClosed) {
      return l10n.settingsAdminReadinessActionRunReadinessSmoke;
    }
    return l10n.settingsAdminReadinessActionNoOperatorAction;
  }

  String _providerCategoryReadinessLabel(
    AppLocalizations l10n,
    ProviderCategoryReadiness readiness,
  ) {
    return switch (readiness) {
      ProviderCategoryReadiness.ready => l10n.settingsProviderStateReady,
      ProviderCategoryReadiness.disabled => l10n.settingsProviderStateDisabled,
      ProviderCategoryReadiness.degraded => l10n.settingsProviderStateDegraded,
      ProviderCategoryReadiness.policyBlocked =>
        l10n.settingsWorkspaceCapabilityBlocked,
      ProviderCategoryReadiness.misconfigured =>
        l10n.settingsProviderStateNotConfigured,
      ProviderCategoryReadiness.unknown => l10n.settingsProviderStateUnknown,
    };
  }

  String _providerModuleLabel(AppLocalizations l10n, String module) {
    return switch (module) {
      'identity-realm' => l10n.settingsProviderModuleIdentityRealm,
      'source-control' => l10n.settingsProviderModuleSourceControl,
      'issue-tracker' => l10n.settingsProviderModuleIssueTracker,
      'ci' => l10n.settingsProviderModuleCi,
      'release' => l10n.settingsProviderModuleRelease,
      'office' => l10n.settingsProviderModuleOffice,
      'files' => l10n.settingsProviderModuleFiles,
      'calendar' => l10n.settingsProviderModuleCalendar,
      'contacts' => l10n.settingsProviderModuleContacts,
      'forms' => l10n.settingsProviderModuleForms,
      'matrix' => l10n.settingsProviderModuleMatrix,
      'matrix-auth' => l10n.settingsProviderModuleMatrixAuth,
      'meetings' => l10n.settingsProviderModuleMeetings,
      'boards' => l10n.settingsProviderModuleBoards,
      _ => l10n.settingsProviderModuleProvider,
    };
  }

  String _providerStateLabel(AppLocalizations l10n, ProviderState state) {
    return switch (state) {
      ProviderState.disabled => l10n.settingsProviderStateDisabled,
      ProviderState.notConfigured => l10n.settingsProviderStateNotConfigured,
      ProviderState.configured => l10n.settingsProviderStateConfigured,
      ProviderState.ready => l10n.settingsProviderStateReady,
      ProviderState.degraded => l10n.settingsProviderStateDegraded,
      ProviderState.unsupported => l10n.settingsProviderStateUnsupported,
      ProviderState.unknown => l10n.settingsProviderStateUnknown,
    };
  }
}

class _OfficeProviderReadinessSummary extends StatelessWidget {
  const _OfficeProviderReadinessSummary({required this.office});

  final OfficeCapabilitiesSnapshot office;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final theme = Theme.of(context);
    final enabledModes = office.capabilities.enabledModes;

    return Semantics(
      container: true,
      label: l10n.settingsOfficeReadinessSemanticLabel(
        office.launchFailClosed
            ? l10n.settingsProviderStackFailClosedBadge
            : l10n.settingsOfficeReadinessAvailable,
        office.configured
            ? l10n.settingsProviderStateConfigured
            : l10n.settingsProviderStateNotConfigured,
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            l10n.settingsOfficeReadinessTitle,
            style: theme.textTheme.titleSmall,
          ),
          const SizedBox(height: 6),
          Text(
            office.launchFailClosed
                ? l10n.settingsOfficeReadinessFailClosedDescription
                : l10n.settingsOfficeReadinessAvailableDescription,
            style: theme.textTheme.bodySmall,
          ),
          const SizedBox(height: 8),
          Wrap(
            spacing: 8,
            runSpacing: 8,
            children: [
              _CompactStatusBadge(
                label: office.enabled
                    ? l10n.settingsOfficeReadinessEnabled
                    : l10n.settingsProviderStateDisabled,
              ),
              _CompactStatusBadge(
                label: office.configured
                    ? l10n.settingsProviderStateConfigured
                    : l10n.settingsProviderStateNotConfigured,
              ),
              if (office.launchFailClosed)
                _CompactStatusBadge(
                  label: l10n.settingsProviderStackFailClosedBadge,
                ),
              _CompactStatusBadge(
                label: enabledModes.isEmpty
                    ? l10n.settingsOfficeReadinessNoLaunchModes
                    : l10n.settingsOfficeReadinessModes(
                        enabledModes.join(', '),
                      ),
              ),
            ],
          ),
        ],
      ),
    );
  }
}

class _CompactStatusBadge extends StatelessWidget {
  const _CompactStatusBadge({required this.label});

  final String label;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return DecoratedBox(
      decoration: BoxDecoration(
        color: theme.colorScheme.surface,
        borderRadius: BorderRadius.circular(999),
        border: Border.all(color: theme.colorScheme.outlineVariant),
      ),
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
        child: Text(label, style: theme.textTheme.labelSmall),
      ),
    );
  }
}

class _WorkspaceReadinessRow extends StatelessWidget {
  const _WorkspaceReadinessRow({
    required this.label,
    required this.capability,
    this.connection,
  });

  final String label;
  final WorkspaceCapabilityState capability;
  final IntegrationConnectionState? connection;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final l10n = AppLocalizations.of(context);
    final recovery = workspaceCapabilityRecoveryPresentation(l10n, capability);

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
              if (connection case final connection?)
                _StatusPill(
                  label: l10n.settingsWorkspaceConnectionLabel,
                  value: _connectionLabel(l10n, connection.status),
                ),
              _StatusPill(
                label: l10n.settingsWorkspacePolicyLabel,
                value: _policyLabel(l10n, capability.policyState),
              ),
              Semantics(
                label: recovery.semanticLabel(l10n, label),
                child: _StatusPill(
                  label: l10n.settingsWorkspaceRecoveryLabel,
                  value: recovery.recovery,
                ),
              ),
              if (connection?.lastInvalidation case final invalidation?)
                _StatusPill(
                  label: l10n.settingsWorkspaceLastChangeLabel,
                  value: _invalidationLabel(l10n, invalidation.reason),
                ),
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

  String _policyLabel(
    AppLocalizations l10n,
    WorkspaceCapabilityPolicyState state,
  ) {
    return switch (state) {
      WorkspaceCapabilityPolicyState.allowed =>
        l10n.settingsWorkspacePolicyAllowed,
      WorkspaceCapabilityPolicyState.policyBlocked =>
        l10n.settingsWorkspacePolicyBlocked,
      WorkspaceCapabilityPolicyState.disabled =>
        l10n.settingsWorkspacePolicyDisabled,
      WorkspaceCapabilityPolicyState.unavailable =>
        l10n.settingsWorkspacePolicyUnavailable,
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
      IntegrationInvalidationReason.chatConfigurationChanged =>
        l10n.settingsWorkspaceInvalidationChatConfigurationChanged,
      IntegrationInvalidationReason.filesConfigurationChanged =>
        l10n.settingsWorkspaceInvalidationFilesConfigurationChanged,
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
