import 'package:flutter/material.dart';
import 'package:weave/features/connectors/domain/entities/connector_preview.dart';

class ConnectorSettingsPreviewCard extends StatelessWidget {
  const ConnectorSettingsPreviewCard({
    super.key,
    required this.connectors,
    this.title = 'Governed connectors preview',
    this.description =
        'Connector status is safe metadata from backend contracts or fixtures. OAuth, webhook, access-token, and refresh-token secrets never belong in this client.',
  });

  final List<ConnectorPreviewCapability> connectors;
  final String title;
  final String description;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Semantics(
      container: true,
      label:
          '$title. Hidden by default for Release 1. Provider secrets are never entered, shown, stored, or logged by the Flutter client.',
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
                    semanticLabel: 'Connectors preview icon',
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
    final statusLabel = _statusLabel(connector.status);
    final actionAllowed = connector.providerActionsEnabled;

    return Semantics(
      container: true,
      label:
          '${connector.name}. Status $statusLabel. ${connector.summary} ${connector.auditSummary} Provider actions ${actionAllowed ? 'are preview-only until backend runtime enables them' : 'are unavailable'}; no provider secret is handled by the app.',
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
          Text(connector.summary),
          const SizedBox(height: 8),
          Text(
            connector.auditSummary,
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
                  ? 'Backend-configured action preview only'
                  : 'Provider action unavailable',
            ),
          ),
        ],
      ),
    );
  }

  static String _statusLabel(ConnectorPreviewStatus status) {
    return switch (status) {
      ConnectorPreviewStatus.disabled => 'Disabled',
      ConnectorPreviewStatus.unavailable => 'Unavailable',
      ConnectorPreviewStatus.degraded => 'Degraded',
      ConnectorPreviewStatus.actionRequired => 'Action required',
      ConnectorPreviewStatus.configured => 'Configured reference',
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
