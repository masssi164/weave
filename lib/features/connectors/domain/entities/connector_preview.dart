enum ConnectorPreviewStatus {
  disabled,
  unavailable,
  degraded,
  actionRequired,
  configured,
}

class ConnectorPreviewCapability {
  const ConnectorPreviewCapability({
    required this.name,
    required this.status,
    required this.summary,
    required this.providerActionsEnabled,
    required this.auditSummary,
  });

  final String name;
  final ConnectorPreviewStatus status;
  final String summary;
  final bool providerActionsEnabled;
  final String auditSummary;
}
