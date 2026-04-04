enum WorkspaceIntegration { appAuth, matrix, nextcloud }

enum IntegrationInvalidationReason {
  authConfigurationChanged,
  matrixHomeserverChanged,
  nextcloudBaseUrlChanged,
  explicitSignOut,
  restartSetup,
}

class IntegrationInvalidation {
  const IntegrationInvalidation({
    required this.integration,
    required this.reason,
    required this.sequence,
  });

  final WorkspaceIntegration integration;
  final IntegrationInvalidationReason reason;
  final int sequence;
}
