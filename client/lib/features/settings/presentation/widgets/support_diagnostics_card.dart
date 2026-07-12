import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:weave/core/application_identity/domain/client_build_identity.dart';
import 'package:weave/core/application_identity/presentation/providers/client_build_identity_provider.dart';
import 'package:weave/features/server_config/domain/entities/server_configuration.dart';
import 'package:weave/features/server_config/presentation/providers/server_configuration_form_controller.dart';
import 'package:weave/l10n/generated/app_localizations.dart';

class SupportDiagnosticsCard extends ConsumerWidget {
  const SupportDiagnosticsCard({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    final theme = Theme.of(context);
    final buildIdentity = ref.watch(clientBuildIdentityProvider);
    final configuration = ref.watch(savedServerConfigurationProvider);
    final identity = buildIdentity.asData?.value;

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
                  l10n.settingsSupportDiagnosticsTitle,
                  style: theme.textTheme.titleLarge,
                ),
              ),
              const SizedBox(height: 8),
              Text(
                l10n.settingsSupportDiagnosticsDescription,
                style: theme.textTheme.bodyMedium?.copyWith(
                  color: theme.colorScheme.onSurfaceVariant,
                ),
              ),
              const SizedBox(height: 16),
              _DiagnosticValue(
                label: l10n.settingsSupportServerLabel,
                value: _serverValue(configuration, l10n),
              ),
              _DiagnosticValue(
                label: l10n.settingsSupportCandidateCommitLabel,
                value: _buildValue(
                  buildIdentity,
                  identity?.candidateCommit,
                  l10n,
                ),
              ),
              _DiagnosticValue(
                label: l10n.settingsSupportVersionLabel,
                value: identity == null
                    ? _buildValue(buildIdentity, null, l10n)
                    : l10n.settingsSupportVersionValue(
                        _displayValue(identity.version, l10n),
                        _displayValue(identity.buildNumber, l10n),
                      ),
              ),
              _DiagnosticValue(
                label: l10n.settingsSupportBundleIdentifierLabel,
                value: _buildValue(
                  buildIdentity,
                  identity?.bundleIdentifier,
                  l10n,
                ),
              ),
              _DiagnosticValue(
                label: l10n.settingsSupportEvidenceReferenceLabel,
                value: _buildValue(
                  buildIdentity,
                  identity?.evidenceReference,
                  l10n,
                ),
              ),
              const SizedBox(height: 8),
              Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  ExcludeSemantics(
                    child: Icon(
                      identity?.isCandidateTraceable == true
                          ? Icons.verified_outlined
                          : Icons.info_outline,
                      color: identity?.isCandidateTraceable == true
                          ? theme.colorScheme.primary
                          : theme.colorScheme.error,
                    ),
                  ),
                  const SizedBox(width: 8),
                  Expanded(
                    child: Text(
                      identity?.isCandidateTraceable == true
                          ? l10n.settingsSupportIdentityComplete
                          : l10n.settingsSupportIdentityIncomplete,
                    ),
                  ),
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }

  String _serverValue(
    AsyncValue<ServerConfiguration?> configuration,
    AppLocalizations l10n,
  ) {
    return switch (configuration) {
      AsyncData(value: final value?) =>
        value.serviceEndpoints.backendApiBaseUrl.origin,
      AsyncData() || AsyncError() => l10n.settingsSupportValueUnavailable,
      _ => l10n.settingsSupportValueLoading,
    };
  }

  String _buildValue(
    AsyncValue<ClientBuildIdentity> buildIdentity,
    String? value,
    AppLocalizations l10n,
  ) {
    if (buildIdentity.isLoading) {
      return l10n.settingsSupportValueLoading;
    }
    return _displayValue(value, l10n);
  }

  String _displayValue(String? value, AppLocalizations l10n) {
    return value == null || value == ClientBuildIdentity.unavailableValue
        ? l10n.settingsSupportValueUnavailable
        : value;
  }
}

class _DiagnosticValue extends StatelessWidget {
  const _DiagnosticValue({required this.label, required this.value});

  final String label;
  final String value;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Padding(
      padding: const EdgeInsets.only(bottom: 12),
      child: Semantics(
        container: true,
        label: '$label: $value',
        child: ExcludeSemantics(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                label,
                style: theme.textTheme.labelLarge?.copyWith(
                  color: theme.colorScheme.onSurfaceVariant,
                ),
              ),
              const SizedBox(height: 2),
              SelectableText(value, style: theme.textTheme.bodyMedium),
            ],
          ),
        ),
      ),
    );
  }
}
