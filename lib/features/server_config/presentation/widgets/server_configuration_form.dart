import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:weave/core/a11y/semantic_button.dart';
import 'package:weave/features/auth/domain/entities/oidc_constants.dart';
import 'package:weave/features/server_config/domain/entities/oidc_provider_type.dart';
import 'package:weave/features/server_config/domain/entities/server_configuration.dart';
import 'package:weave/features/server_config/presentation/providers/server_configuration_form_controller.dart';
import 'package:weave/l10n/generated/app_localizations.dart';

enum ServerConfigurationFormLayout {
  providerAndIssuerOnly,
  serviceEndpointsOnly,
  full,
}

class ServerConfigurationForm extends ConsumerStatefulWidget {
  const ServerConfigurationForm({
    super.key,
    required this.layout,
    this.initialConfiguration,
    this.submitLabel,
    this.onSaved,
  });

  final ServerConfigurationFormLayout layout;
  final ServerConfiguration? initialConfiguration;
  final String? submitLabel;
  final Future<void> Function(ServerConfigurationSaveResult result)? onSaved;

  @override
  ConsumerState<ServerConfigurationForm> createState() =>
      _ServerConfigurationFormState();
}

class _ServerConfigurationFormState
    extends ConsumerState<ServerConfigurationForm> {
  late final TextEditingController _issuerController;
  late final TextEditingController _clientIdController;
  late final TextEditingController _matrixController;
  late final TextEditingController _nextcloudController;

  @override
  void initState() {
    super.initState();
    _issuerController = TextEditingController();
    _clientIdController = TextEditingController();
    _matrixController = TextEditingController();
    _nextcloudController = TextEditingController();
  }

  @override
  void dispose() {
    _issuerController.dispose();
    _clientIdController.dispose();
    _matrixController.dispose();
    _nextcloudController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final formState = ref.watch(serverConfigurationFormControllerProvider);

    if (!formState.initialized) {
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (mounted) {
          ref
              .read(serverConfigurationFormControllerProvider.notifier)
              .initialize(widget.initialConfiguration);
        }
      });
    }

    _syncController(_issuerController, formState.issuerUrl);
    _syncController(_clientIdController, formState.clientId);
    _syncController(_matrixController, formState.matrixHomeserverUrl);
    _syncController(_nextcloudController, formState.nextcloudBaseUrl);

    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        if (widget.layout != ServerConfigurationFormLayout.serviceEndpointsOnly)
          _buildProviderAndIssuerSection(context, l10n, formState),
        if (widget.layout == ServerConfigurationFormLayout.full)
          const SizedBox(height: 24),
        if (widget.layout !=
            ServerConfigurationFormLayout.providerAndIssuerOnly)
          _buildServiceEndpointsSection(context, l10n, formState),
        if (widget.submitLabel != null) ...[
          const SizedBox(height: 24),
          AccessibleButton(
            onPressed: formState.isSaving
                ? null
                : () async {
                    final result = await ref
                        .read(
                          serverConfigurationFormControllerProvider.notifier,
                        )
                        .save();
                    if (result != null && mounted && widget.onSaved != null) {
                      await widget.onSaved!.call(result);
                    }
                  },
            semanticLabel: widget.submitLabel!,
            child: Text(
              formState.isSaving
                  ? l10n.settingsSaveInProgress
                  : widget.submitLabel!,
            ),
          ),
        ],
      ],
    );
  }

  Widget _buildProviderAndIssuerSection(
    BuildContext context,
    AppLocalizations l10n,
    ServerConfigurationFormState formState,
  ) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Text(
          l10n.serverConfigurationProviderLabel,
          style: Theme.of(context).textTheme.titleMedium,
        ),
        const SizedBox(height: 12),
        DropdownButtonFormField<OidcProviderType>(
          initialValue: formState.providerType,
          decoration: InputDecoration(
            labelText: l10n.serverConfigurationProviderFieldLabel,
          ),
          items: [
            DropdownMenuItem(
              value: OidcProviderType.authentik,
              child: Text(l10n.oidcProviderAuthentik),
            ),
            DropdownMenuItem(
              value: OidcProviderType.keycloak,
              child: Text(l10n.oidcProviderKeycloak),
            ),
          ],
          onChanged: (value) {
            if (value != null) {
              ref
                  .read(serverConfigurationFormControllerProvider.notifier)
                  .updateProviderType(value);
            }
          },
        ),
        const SizedBox(height: 12),
        TextField(
          controller: _issuerController,
          keyboardType: TextInputType.url,
          textInputAction: TextInputAction.next,
          decoration: InputDecoration(
            labelText: l10n.serverConfigurationIssuerLabel,
            hintText: 'https://auth.home.internal',
            helperText: l10n.serverConfigurationIssuerHelper,
            errorText: formState.issuerError,
          ),
          onChanged: ref
              .read(serverConfigurationFormControllerProvider.notifier)
              .updateIssuerUrl,
        ),
        const SizedBox(height: 16),
        TextField(
          controller: _clientIdController,
          textInputAction:
              widget.layout ==
                  ServerConfigurationFormLayout.providerAndIssuerOnly
              ? TextInputAction.done
              : TextInputAction.next,
          decoration: InputDecoration(
            labelText: l10n.serverConfigurationClientIdLabel,
            hintText: 'weave-mobile',
            helperText: l10n.serverConfigurationClientIdHelper,
            errorText: formState.clientIdError,
          ),
          onChanged: ref
              .read(serverConfigurationFormControllerProvider.notifier)
              .updateClientId,
        ),
        const SizedBox(height: 24),
        _OidcRegistrationHelpCard(providerType: formState.providerType),
      ],
    );
  }

  Widget _buildServiceEndpointsSection(
    BuildContext context,
    AppLocalizations l10n,
    ServerConfigurationFormState formState,
  ) {
    final theme = Theme.of(context);

    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Text(
          l10n.serverConfigurationServicesLabel,
          style: theme.textTheme.titleMedium,
        ),
        const SizedBox(height: 8),
        Text(
          l10n.serverConfigurationServicesHelper,
          style: theme.textTheme.bodyMedium?.copyWith(
            color: theme.colorScheme.onSurfaceVariant,
          ),
        ),
        const SizedBox(height: 16),
        TextField(
          controller: _matrixController,
          keyboardType: TextInputType.url,
          textInputAction: TextInputAction.next,
          decoration: InputDecoration(
            labelText: l10n.serverConfigurationMatrixLabel,
            hintText: 'https://matrix.home.internal',
            helperText: formState.derivedMatrixHomeserverUrl.isEmpty
                ? null
                : l10n.serverConfigurationDerivedHint(
                    formState.derivedMatrixHomeserverUrl,
                  ),
            errorText: formState.matrixError,
          ),
          onChanged: ref
              .read(serverConfigurationFormControllerProvider.notifier)
              .updateMatrixHomeserverUrl,
        ),
        const SizedBox(height: 16),
        TextField(
          controller: _nextcloudController,
          keyboardType: TextInputType.url,
          textInputAction: TextInputAction.done,
          decoration: InputDecoration(
            labelText: l10n.serverConfigurationNextcloudLabel,
            hintText: 'https://nextcloud.home.internal',
            helperText: formState.derivedNextcloudBaseUrl.isEmpty
                ? null
                : l10n.serverConfigurationDerivedHint(
                    formState.derivedNextcloudBaseUrl,
                  ),
            errorText: formState.nextcloudError,
          ),
          onChanged: ref
              .read(serverConfigurationFormControllerProvider.notifier)
              .updateNextcloudBaseUrl,
        ),
        if (formState.saveFailure != null) ...[
          const SizedBox(height: 16),
          Text(
            formState.saveFailure!.message,
            style: theme.textTheme.bodyMedium?.copyWith(
              color: theme.colorScheme.error,
            ),
          ),
        ],
      ],
    );
  }

  void _syncController(TextEditingController controller, String nextValue) {
    if (controller.text == nextValue) {
      return;
    }

    controller.value = TextEditingValue(
      text: nextValue,
      selection: TextSelection.collapsed(offset: nextValue.length),
    );
  }
}

class _OidcRegistrationHelpCard extends StatelessWidget {
  const _OidcRegistrationHelpCard({required this.providerType});

  final OidcProviderType providerType;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final theme = Theme.of(context);
    final providerSteps = switch (providerType) {
      OidcProviderType.authentik => l10n.oidcRegistrationHelpAuthentikSteps,
      OidcProviderType.keycloak => l10n.oidcRegistrationHelpKeycloakSteps,
    };

    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              l10n.oidcRegistrationHelpTitle,
              style: theme.textTheme.titleMedium,
            ),
            const SizedBox(height: 8),
            Text(l10n.oidcRegistrationHelpDescription),
            const SizedBox(height: 8),
            Text(
              l10n.oidcRegistrationHelpNoSecret,
              style: theme.textTheme.bodyMedium?.copyWith(
                color: theme.colorScheme.onSurfaceVariant,
              ),
            ),
            const SizedBox(height: 12),
            Text(providerSteps),
            const SizedBox(height: 12),
            Text(
              l10n.oidcRegistrationHelpRedirectsTitle,
              style: theme.textTheme.titleSmall,
            ),
            const SizedBox(height: 8),
            Text(l10n.oidcRegistrationHelpRedirectValue(oidcRedirectUri)),
            const SizedBox(height: 4),
            Text(
              l10n.oidcRegistrationHelpPostLogoutRedirectValue(
                oidcPostLogoutRedirectUri,
              ),
            ),
          ],
        ),
      ),
    );
  }
}
