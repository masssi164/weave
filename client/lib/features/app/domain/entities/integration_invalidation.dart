enum WorkspaceIntegration { appAuth, chat, files, weaveBackend }

enum IntegrationInvalidationReason {
  authConfigurationChanged,
  chatConfigurationChanged,
  filesConfigurationChanged,
  backendApiBaseUrlChanged,
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
