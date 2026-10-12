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
import 'package:weave/features/app/domain/entities/workspace_capability_snapshot.dart';
import 'package:weave/features/app/domain/entities/workspace_connection_state.dart';
import 'package:weave/features/app/presentation/workspace_capability_recovery_presenter.dart';
import 'package:weave/features/app/presentation/providers/workspace_connection_provider.dart';
import 'package:weave/features/auth/presentation/providers/auth_flow_controller.dart';
import 'package:weave/features/connectors/presentation/providers/connector_preview_provider.dart';
import 'package:weave/features/connectors/presentation/widgets/connector_settings_preview_card.dart';
import 'package:weave/features/guests/presentation/providers/guest_preview_provider.dart';
import 'package:weave/features/guests/presentation/widgets/guest_access_preview_card.dart';
import 'package:weave/features/profile/domain/entities/user_profile.dart';
import 'package:weave/features/profile/presentation/providers/user_profile_provider.dart';
import 'package:weave/features/shell/domain/entities/shell_module.dart';
import 'package:weave/features/shell/presentation/providers/shell_module_preferences_provider.dart';
import 'package:weave/features/settings/presentation/widgets/support_diagnostics_card.dart';
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
                const SupportDiagnosticsCard(),
                const SizedBox(height: 32),
                const _ThemePreferenceSection(),
                const SizedBox(height: 32),
                const _LanguagePreferenceSection(),
                const SizedBox(height: 32),
                const _ProfileSettingsLinkCard(),
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
    return const Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        _WorkspaceReadinessCard(),
        if (FeatureFlags.hasFeatureGatedSurfaces) ...[
          SizedBox(height: 32),
          _FeaturePreviewSurfacesSection(),
        ],
      ],
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
        label: Text(
          AppLocalizations.of(context).settingsWorkspaceRecoveryUnavailable,
        ),
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
                  _workspaceSummary(l10n, workspaceState, capabilitySnapshot),
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
