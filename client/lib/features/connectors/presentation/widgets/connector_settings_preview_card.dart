import 'package:flutter/material.dart';
import 'package:weave/features/connectors/domain/entities/connector_preview.dart';
import 'package:weave/l10n/generated/app_localizations.dart';

class ConnectorSettingsPreviewCard extends StatelessWidget {
  const ConnectorSettingsPreviewCard({
    super.key,
    required this.connectors,
    this.title,
    this.description,
  });

  final List<ConnectorPreviewCapability> connectors;
  final String? title;
  final String? description;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final l10n = AppLocalizations.of(context);
    final title = this.title ?? l10n.connectorSettingsPreviewTitle;
    final description =
        this.description ?? l10n.connectorSettingsPreviewDescription;
    return Semantics(
      container: true,
      label: l10n.connectorSettingsPreviewCardSemanticLabel(title),
      child: Card(
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
              Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Icon(
                    Icons.sync_alt_outlined,
                    color: theme.colorScheme.primary,
                    semanticLabel: l10n.connectorPreviewIconSemantic,
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(title, style: theme.textTheme.titleLarge),
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
                ],
              ),
              const SizedBox(height: 16),
              for (final connector in connectors) ...[
                _ConnectorPreviewTile(connector: connector),
                if (connector != connectors.last) const Divider(height: 24),
              ],
            ],
          ),
        ),
      ),
    );
  }
}

class _ConnectorPreviewTile extends StatelessWidget {
  const _ConnectorPreviewTile({required this.connector});

  final ConnectorPreviewCapability connector;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final l10n = AppLocalizations.of(context);
    final statusLabel = _statusLabel(l10n, connector.status);
    final actionAllowed = connector.providerActionsEnabled;
    final summaryText = l10n.connectorPreviewDemoSummary(connector.summary);
    final auditSummaryText = l10n.connectorPreviewDemoAuditSummary(
      connector.auditSummary,
    );
    final actionState = actionAllowed
        ? l10n.connectorProviderActionsPreviewOnlySemantic
        : l10n.connectorProviderActionsUnavailableSemantic;

    return Semantics(
      container: true,
      label: l10n.connectorPreviewTileSemanticLabel(
        connector.name,
        statusLabel,
        summaryText,
        auditSummaryText,
        actionState,
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Wrap(
            crossAxisAlignment: WrapCrossAlignment.center,
            spacing: 8,
            runSpacing: 8,
            children: [
              Text(
                connector.name,
                style: theme.textTheme.titleMedium?.copyWith(
                  fontWeight: FontWeight.w700,
                ),
              ),
              Chip(
                label: Text(statusLabel),
                avatar: Icon(_statusIcon(connector.status), size: 18),
                side: BorderSide(color: theme.colorScheme.outlineVariant),
              ),
            ],
          ),
          const SizedBox(height: 8),
          Text(summaryText),
          const SizedBox(height: 8),
          Text(
            auditSummaryText,
            style: theme.textTheme.bodyMedium?.copyWith(
              color: theme.colorScheme.onSurfaceVariant,
            ),
          ),
          const SizedBox(height: 10),
          OutlinedButton.icon(
            onPressed: null,
            icon: const Icon(Icons.lock),
            label: Text(
              actionAllowed
                  ? l10n.connectorProviderActionsPreviewOnly
                  : l10n.connectorProviderActionUnavailable,
            ),
          ),
        ],
      ),
    );
  }

  static String _statusLabel(
    AppLocalizations l10n,
    ConnectorPreviewStatus status,
  ) {
    return switch (status) {
      ConnectorPreviewStatus.disabled => l10n.connectorPreviewStatusDisabled,
      ConnectorPreviewStatus.unavailable =>
        l10n.connectorPreviewStatusUnavailable,
      ConnectorPreviewStatus.degraded => l10n.connectorPreviewStatusDegraded,
      ConnectorPreviewStatus.actionRequired =>
        l10n.connectorPreviewStatusActionRequired,
      ConnectorPreviewStatus.configured =>
        l10n.connectorPreviewStatusConfigured,
    };
  }

  static IconData _statusIcon(ConnectorPreviewStatus status) {
    return switch (status) {
      ConnectorPreviewStatus.disabled => Icons.toggle_off_outlined,
      ConnectorPreviewStatus.unavailable => Icons.cloud_off_outlined,
      ConnectorPreviewStatus.degraded => Icons.warning_amber_outlined,
      ConnectorPreviewStatus.actionRequired => Icons.assignment_late_outlined,
      ConnectorPreviewStatus.configured => Icons.verified_outlined,
    };
  }
}
