import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:weave/core/a11y/semantic_button.dart';
import 'package:weave/core/widgets/error_state.dart';
import 'package:weave/core/widgets/loading_state.dart';
import 'package:weave/features/auth/presentation/providers/auth_flow_controller.dart';
import 'package:weave/features/chat/presentation/widgets/chat_security_settings_section.dart';
import 'package:weave/features/server_config/presentation/providers/'
    'server_configuration_form_controller.dart';
import 'package:weave/features/server_config/presentation/widgets/server_configuration_form.dart';
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
