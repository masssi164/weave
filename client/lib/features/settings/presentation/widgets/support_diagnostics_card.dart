import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:weave/core/application_identity/domain/client_build_identity.dart';
import 'package:weave/core/application_identity/presentation/providers/client_build_identity_provider.dart';
import 'package:weave/features/settings/presentation/providers/support_identity_reference_provider.dart';
import 'package:weave/l10n/generated/app_localizations.dart';

/// Member support: product identity and installed version only.
///
/// Candidate commits, evidence receipts, raw endpoints and deployment posture
/// are owned by DevOps qualification, not the ordinary app settings experience.
class SupportDiagnosticsCard extends ConsumerWidget {
  const SupportDiagnosticsCard({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    final theme = Theme.of(context);
    final identity = ref.watch(clientBuildIdentityProvider).asData?.value;
    final references = ref.watch(supportIdentityReferenceProvider);
    final currentReferences = !references.isLoading && !references.hasError
        ? references.asData?.value
        : null;
    final referenceUnavailable = references.isLoading
        ? l10n.settingsSupportValueLoading
        : l10n.settingsSupportValueUnavailable;

    final version = identity?.version;
    final build = identity?.buildNumber;
    final versionLabel =
        version == null ||
        version == ClientBuildIdentity.unavailableValue ||
        build == null ||
        build == ClientBuildIdentity.unavailableValue
        ? l10n.settingsSupportValueUnavailable
        : l10n.settingsSupportVersionValue(version, build);

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
                identifier: 'settings-current-account-reference',
                label: l10n.settingsSupportAccountReference,
                value: currentReferences?.account ?? referenceUnavailable,
              ),
              _DiagnosticValue(
                identifier: 'settings-current-organization-reference',
                label: l10n.settingsSupportOrganizationReference,
                value: currentReferences?.organization ?? referenceUnavailable,
              ),
              TextButton.icon(
                onPressed: currentReferences == null
                    ? null
                    : () => Clipboard.setData(
                        ClipboardData(
                          text:
                              '${l10n.settingsSupportAccountReference}: ${currentReferences.account}\n${l10n.settingsSupportOrganizationReference}: ${currentReferences.organization}',
                        ),
                      ),
                icon: const Icon(Icons.copy),
                label: Text(l10n.settingsSupportCopyIdentityReferences),
              ),
              _DiagnosticValue(
                label: l10n.settingsSupportVersionLabel,
                value: versionLabel,
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _DiagnosticValue extends StatelessWidget {
  const _DiagnosticValue({
    required this.label,
    required this.value,
    this.identifier,
  });

  final String? identifier;
  final String label;
  final String value;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Padding(
      padding: const EdgeInsets.only(bottom: 12),
      child: Semantics(
        container: true,
        identifier: identifier,
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
