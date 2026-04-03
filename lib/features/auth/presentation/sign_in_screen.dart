import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:weave/core/a11y/semantic_button.dart';
import 'package:weave/core/router/app_routes.dart';
import 'package:weave/core/widgets/error_state.dart';
import 'package:weave/core/widgets/loading_state.dart';
import 'package:weave/core/widgets/weave_logo.dart';
import 'package:weave/features/auth/presentation/providers/auth_flow_controller.dart';
import 'package:weave/features/server_config/domain/entities/oidc_provider_type.dart';
import 'package:weave/features/server_config/presentation/providers/server_configuration_form_controller.dart';
import 'package:weave/l10n/generated/app_localizations.dart';

class SignInScreen extends ConsumerWidget {
  const SignInScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    final configuration = ref.watch(savedServerConfigurationProvider);
    final authState = ref.watch(authFlowControllerProvider);

    return Scaffold(
      appBar: AppBar(title: Text(l10n.signInScreenTitle)),
      body: SafeArea(
        child: Center(
          child: Padding(
            padding: const EdgeInsets.all(24),
            child: ConstrainedBox(
              constraints: const BoxConstraints(maxWidth: 520),
              child: configuration.when(
                loading: () => LoadingState(message: l10n.loadingLabel),
                error: (error, stackTrace) => ErrorState(
                  message: l10n.errorStateLabel,
                  retryLabel: l10n.retryButton,
                  onRetry: () =>
                      ref.invalidate(savedServerConfigurationProvider),
                ),
                data: (configuration) {
                  if (configuration == null ||
                      !configuration.hasCompleteAuthConfiguration) {
                    return SingleChildScrollView(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.stretch,
                        children: [
                          Center(
                            child: WeaveLogo(
                              semanticLabel: l10n.semanticWeaveLogo,
                              width: 144,
                            ),
                          ),
                          const SizedBox(height: 24),
                          _MissingConfigurationCard(
                            isBusy: authState.isBusy,
                            onReturnToSetup: () async {
                              await ref
                                  .read(authFlowControllerProvider.notifier)
                                  .restartSetup();
                              if (context.mounted) {
                                context.go(AppRoutes.setup);
                              }
                            },
                          ),
                        ],
                      ),
                    );
                  }

                  return SingleChildScrollView(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.stretch,
                      children: [
                        Center(
                          child: WeaveLogo(
                            semanticLabel: l10n.semanticWeaveLogo,
                            width: 144,
                          ),
                        ),
                        const SizedBox(height: 24),
                        Semantics(
                          header: true,
                          child: Text(
                            l10n.signInTitle,
                            style: Theme.of(context).textTheme.headlineMedium,
                            textAlign: TextAlign.center,
                          ),
                        ),
                        const SizedBox(height: 16),
                        Text(
                          l10n.signInDescription,
                          style: Theme.of(context).textTheme.bodyLarge,
                          textAlign: TextAlign.center,
                        ),
                        const SizedBox(height: 32),
                        Card(
                          child: Padding(
                            padding: const EdgeInsets.all(20),
                            child: Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                Text(
                                  l10n.signInConfigurationTitle,
                                  style: Theme.of(
                                    context,
                                  ).textTheme.titleMedium,
                                ),
                                const SizedBox(height: 12),
                                Text(
                                  l10n.signInConfigurationProvider(
                                    _providerLabel(
                                      configuration.providerType,
                                      l10n,
                                    ),
                                  ),
                                ),
                                const SizedBox(height: 8),
                                Text(
                                  l10n.signInConfigurationIssuer(
                                    configuration.oidcIssuerUrl.toString(),
                                  ),
                                ),
                                const SizedBox(height: 8),
                                Text(
                                  l10n.signInConfigurationClientId(
                                    configuration
                                        .oidcClientRegistration
                                        .clientId,
                                  ),
                                ),
                              ],
                            ),
                          ),
                        ),
                        if (authState.failure != null) ...[
                          const SizedBox(height: 16),
                          Text(
                            authState.failure!.message,
                            style: Theme.of(context).textTheme.bodyMedium
                                ?.copyWith(
                                  color: Theme.of(context).colorScheme.error,
                                ),
                            textAlign: TextAlign.center,
                          ),
                        ],
                        const SizedBox(height: 24),
                        AccessibleButton(
                          onPressed: authState.isBusy
                              ? null
                              : () => ref
                                    .read(authFlowControllerProvider.notifier)
                                    .signIn(),
                          semanticLabel: l10n.signInButton,
                          child: Text(
                            authState.isBusy
                                ? l10n.signInInProgress
                                : l10n.signInButton,
                          ),
                        ),
                        const SizedBox(height: 12),
                        AccessibleButton(
                          outlined: true,
                          onPressed: authState.isBusy
                              ? null
                              : () async {
                                  await ref
                                      .read(authFlowControllerProvider.notifier)
                                      .restartSetup();
                                  if (context.mounted) {
                                    context.go(AppRoutes.setup);
                                  }
                                },
                          semanticLabel: l10n.signInBackToSetupButton,
                          child: Text(l10n.signInBackToSetupButton),
                        ),
                      ],
                    ),
                  );
                },
              ),
            ),
          ),
        ),
      ),
    );
  }

  String _providerLabel(OidcProviderType providerType, AppLocalizations l10n) {
    return switch (providerType) {
      OidcProviderType.authentik => l10n.oidcProviderAuthentik,
      OidcProviderType.keycloak => l10n.oidcProviderKeycloak,
    };
  }
}

class _MissingConfigurationCard extends StatelessWidget {
  const _MissingConfigurationCard({
    required this.isBusy,
    required this.onReturnToSetup,
  });

  final bool isBusy;
  final Future<void> Function() onReturnToSetup;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);

    return Card(
      child: Padding(
        padding: const EdgeInsets.all(24),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Semantics(
              header: true,
              child: Text(
                l10n.signInMissingConfigurationTitle,
                style: Theme.of(context).textTheme.headlineSmall,
                textAlign: TextAlign.center,
              ),
            ),
            const SizedBox(height: 12),
            Text(
              l10n.signInMissingConfigurationDescription,
              textAlign: TextAlign.center,
            ),
            const SizedBox(height: 24),
            AccessibleButton(
              onPressed: isBusy
                  ? null
                  : () async {
                      await onReturnToSetup();
                    },
              semanticLabel: l10n.signInBackToSetupButton,
              child: Text(l10n.signInBackToSetupButton),
            ),
          ],
        ),
      ),
    );
  }
}
