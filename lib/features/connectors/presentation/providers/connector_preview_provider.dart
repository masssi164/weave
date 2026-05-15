import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:weave/features/connectors/domain/entities/connector_preview.dart';

final connectorPreviewProvider = Provider<List<ConnectorPreviewCapability>>((
  ref,
) {
  return const <ConnectorPreviewCapability>[
    ConnectorPreviewCapability(
      name: 'Slack one-channel bridge',
      status: ConnectorPreviewStatus.disabled,
      summary:
          'Disabled by default. No OAuth, webhook, access token, or refresh token is collected in the app.',
      providerActionsEnabled: false,
      auditSummary:
          'Install, scope, mapping, disconnect, and failure events will be audited by the backend.',
    ),
    ConnectorPreviewCapability(
      name: 'Teams bridge',
      status: ConnectorPreviewStatus.unavailable,
      summary:
          'Unavailable until Slack hardening proves the connector boundary.',
      providerActionsEnabled: false,
      auditSummary:
          'Future consent and throttling states will be reported without exposing provider secrets.',
    ),
    ConnectorPreviewCapability(
      name: 'Migration dry run',
      status: ConnectorPreviewStatus.degraded,
      summary:
          'Preview status can describe rate limits and unmapped content before any import starts.',
      providerActionsEnabled: false,
      auditSummary: 'Dry-run inventory remains review-only and replay-safe.',
    ),
    ConnectorPreviewCapability(
      name: 'Connector manifest',
      status: ConnectorPreviewStatus.actionRequired,
      summary:
          'Requires reviewed scoped capabilities and secret references before runtime enablement.',
      providerActionsEnabled: false,
      auditSummary:
          'Manifest validation rejects provider secrets in client-visible metadata.',
    ),
    ConnectorPreviewCapability(
      name: 'Configured reference example',
      status: ConnectorPreviewStatus.configured,
      summary:
          'Configured-reference means the backend knows a safe reference; the Flutter client still never handles raw secrets.',
      providerActionsEnabled: true,
      auditSummary:
          'Provider actions become available only when backend metadata marks the connector enabled and configured.',
    ),
  ];
});
