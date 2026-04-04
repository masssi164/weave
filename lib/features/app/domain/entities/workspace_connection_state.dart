import 'package:weave/features/app/domain/entities/integration_invalidation.dart';

enum IntegrationConnectionStatus {
  connected,
  disconnected,
  degraded,
  misconfigured,
  requiresReauthentication,
  unavailableOnPlatform,
}

enum IntegrationRecoveryRequirement {
  none,
  completeSetup,
  connect,
  reauthenticate,
  reviewConfiguration,
  switchPlatform,
}

class IntegrationConnectionState {
  const IntegrationConnectionState({
    required this.integration,
    required this.status,
    this.recoveryRequirement = IntegrationRecoveryRequirement.none,
    this.lastInvalidation,
  });

  final WorkspaceIntegration integration;
  final IntegrationConnectionStatus status;
  final IntegrationRecoveryRequirement recoveryRequirement;
  final IntegrationInvalidation? lastInvalidation;

  bool get isUsable =>
      status == IntegrationConnectionStatus.connected ||
      status == IntegrationConnectionStatus.degraded;

  bool get requiresRecovery =>
      recoveryRequirement != IntegrationRecoveryRequirement.none;
}

class WorkspaceConnectionState {
  const WorkspaceConnectionState({
    required this.appAuth,
    required this.matrix,
    required this.nextcloud,
  });

  final IntegrationConnectionState appAuth;
  final IntegrationConnectionState matrix;
  final IntegrationConnectionState nextcloud;

  List<IntegrationConnectionState> get serviceIntegrations =>
      <IntegrationConnectionState>[matrix, nextcloud];

  bool get shellAccessReady =>
      appAuth.status == IntegrationConnectionStatus.connected;

  bool get hasServiceReadinessIssues =>
      shellAccessReady &&
      serviceIntegrations.any(
        (state) => state.status != IntegrationConnectionStatus.connected,
      );

  IntegrationConnectionStatus get status {
    if (!shellAccessReady) {
      return appAuth.status;
    }

    return hasServiceReadinessIssues
        ? IntegrationConnectionStatus.degraded
        : IntegrationConnectionStatus.connected;
  }
}
