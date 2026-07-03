import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:weave/core/bootstrap/domain/bootstrap_state.dart';
import 'package:weave/core/bootstrap/presentation/providers/app_bootstrap_provider.dart';
import 'package:weave/features/app/domain/entities/integration_invalidation.dart';
import 'package:weave/features/app/domain/entities/workspace_capability_snapshot.dart';
import 'package:weave/features/app/domain/entities/workspace_connection_state.dart';
import 'package:weave/features/app/presentation/providers/workspace_invalidation_provider.dart';
import 'package:weave/integrations/weave_api/presentation/providers/weave_api_provider.dart';

final appAuthIntegrationConnectionProvider =
    Provider<AsyncValue<IntegrationConnectionState>>((ref) {
      final invalidation = ref.watch(
        integrationInvalidationProvider(WorkspaceIntegration.appAuth),
      );
      final bootstrap = ref.watch(appBootstrapProvider);
      return bootstrap.whenData(
        (state) => _mapAppAuthConnectionState(state, invalidation),
      );
    });

final workspaceConnectionStateProvider =
    Provider<AsyncValue<WorkspaceConnectionState>>((ref) {
      final appAuth = ref.watch(appAuthIntegrationConnectionProvider);
      final backendCapabilities = ref.watch(
        weaveApiWorkspaceCapabilitySnapshotProvider,
      );

      if (backendCapabilities case AsyncData(value: final backendSnapshot?)) {
        if (appAuth.hasError) {
          return AsyncError(appAuth.error!, appAuth.stackTrace!);
        }
        if (appAuth.isLoading) {
          return const AsyncLoading();
        }

        return AsyncData(
          _mapBackendFacadeConnectionState(
            appAuth: appAuth.requireValue,
            capabilities: backendSnapshot,
            chatInvalidation: ref.watch(
              integrationInvalidationProvider(WorkspaceIntegration.chat),
            ),
            filesInvalidation: ref.watch(
              integrationInvalidationProvider(WorkspaceIntegration.files),
            ),
          ),
        );
      }

      if (appAuth.hasError) {
        return AsyncError(appAuth.error!, appAuth.stackTrace!);
      }
      if (backendCapabilities.hasError) {
        return AsyncError(
          backendCapabilities.error!,
          backendCapabilities.stackTrace!,
        );
      }
      if (appAuth.isLoading || backendCapabilities.isLoading) {
        return const AsyncLoading();
      }

      return AsyncData(
        _mapBackendFacadeConnectionState(
          appAuth: appAuth.requireValue,
          capabilities: _mapBackendFacadeLocalSnapshot(appAuth.requireValue),
          chatInvalidation: ref.watch(
            integrationInvalidationProvider(WorkspaceIntegration.chat),
          ),
          filesInvalidation: ref.watch(
            integrationInvalidationProvider(WorkspaceIntegration.files),
          ),
        ),
      );
    });

WorkspaceConnectionState _mapBackendFacadeConnectionState({
  required IntegrationConnectionState appAuth,
  required WorkspaceCapabilitySnapshot capabilities,
  IntegrationInvalidation? chatInvalidation,
  IntegrationInvalidation? filesInvalidation,
}) {
  return WorkspaceConnectionState(
    appAuth: appAuth,
    chat: _mapBackendCapabilityConnection(
      integration: WorkspaceIntegration.chat,
      capability: capabilities.chat,
      invalidation: chatInvalidation,
    ),
    files: _mapBackendCapabilityConnection(
      integration: WorkspaceIntegration.files,
      capability: capabilities.files,
      invalidation: filesInvalidation,
    ),
  );
}

IntegrationConnectionState _mapBackendCapabilityConnection({
  required WorkspaceIntegration integration,
  required WorkspaceCapabilityState capability,
  IntegrationInvalidation? invalidation,
}) {
  if (invalidation != null) {
    return IntegrationConnectionState(
      integration: integration,
      status: IntegrationConnectionStatus.disconnected,
      recoveryRequirement: IntegrationRecoveryRequirement.reviewConfiguration,
      lastInvalidation: invalidation,
    );
  }

  return switch (capability.readiness) {
    WorkspaceCapabilityReadiness.ready ||
    WorkspaceCapabilityReadiness.degraded ||
    WorkspaceCapabilityReadiness.unavailable ||
    WorkspaceCapabilityReadiness.blocked => IntegrationConnectionState(
      integration: integration,
      status: IntegrationConnectionStatus.connected,
    ),
  };
}

final workspaceCapabilitySnapshotProvider =
    Provider<AsyncValue<WorkspaceCapabilitySnapshot>>((ref) {
      final backendCapabilities = ref.watch(
        weaveApiWorkspaceCapabilitySnapshotProvider,
      );
      final appAuth = ref.watch(appAuthIntegrationConnectionProvider);

      if (backendCapabilities case AsyncData(value: final backendSnapshot?)) {
        if (appAuth.hasError) {
          return AsyncError(appAuth.error!, appAuth.stackTrace!);
        }
        if (appAuth.isLoading) {
          return const AsyncLoading();
        }

        return AsyncData(
          _mergeWorkspaceCapabilitySnapshots(
            localSnapshot: _mapBackendFacadeLocalSnapshot(appAuth.requireValue),
            backendSnapshot: backendSnapshot,
          ),
        );
      }

      final workspace = ref.watch(workspaceConnectionStateProvider);
      if (workspace.hasError) {
        return AsyncError(workspace.error!, workspace.stackTrace!);
      }
      if (workspace.isLoading) {
        return const AsyncLoading();
      }

      return switch (backendCapabilities) {
        AsyncData(value: final snapshot) => AsyncData(
          snapshot == null
              ? _mapBackendFacadeLocalSnapshot(workspace.requireValue.appAuth)
              : _mergeWorkspaceCapabilitySnapshots(
                  localSnapshot: _mapBackendFacadeLocalSnapshot(
                    workspace.requireValue.appAuth,
                  ),
                  backendSnapshot: snapshot,
                ),
        ),
        AsyncLoading() => const AsyncLoading(),
        AsyncError(:final error, :final stackTrace) => AsyncError(
          error,
          stackTrace,
        ),
      };
    });

IntegrationConnectionState _mapAppAuthConnectionState(
  BootstrapState state,
  IntegrationInvalidation? invalidation,
) {
  return switch (state.phase) {
    BootstrapPhase.loading => IntegrationConnectionState(
      integration: WorkspaceIntegration.appAuth,
      status: IntegrationConnectionStatus.disconnected,
      recoveryRequirement: IntegrationRecoveryRequirement.reauthenticate,
      lastInvalidation: invalidation,
    ),
    BootstrapPhase.needsSetup => IntegrationConnectionState(
      integration: WorkspaceIntegration.appAuth,
      status: IntegrationConnectionStatus.misconfigured,
      recoveryRequirement: IntegrationRecoveryRequirement.completeSetup,
      lastInvalidation: invalidation,
    ),
    BootstrapPhase.needsSignIn => IntegrationConnectionState(
      integration: WorkspaceIntegration.appAuth,
      status: IntegrationConnectionStatus.requiresReauthentication,
      recoveryRequirement: IntegrationRecoveryRequirement.reauthenticate,
      lastInvalidation: invalidation,
    ),
    BootstrapPhase.ready => IntegrationConnectionState(
      integration: WorkspaceIntegration.appAuth,
      status: IntegrationConnectionStatus.connected,
      lastInvalidation: invalidation,
    ),
    BootstrapPhase.error => IntegrationConnectionState(
      integration: WorkspaceIntegration.appAuth,
      status: IntegrationConnectionStatus.degraded,
      recoveryRequirement: IntegrationRecoveryRequirement.reauthenticate,
      lastInvalidation: invalidation,
    ),
  };
}

WorkspaceCapabilitySnapshot _mapBackendFacadeLocalSnapshot(
  IntegrationConnectionState appAuth,
) {
  final shellAccess = _mapShellAccessCapability(appAuth);

  return WorkspaceCapabilitySnapshot(
    shellAccess: shellAccess,
    chat: _mapBackendOwnedCapability(
      capability: WorkspaceCapability.chat,
      shellAccess: appAuth,
    ),
    files: _mapBackendOwnedCapability(
      capability: WorkspaceCapability.files,
      shellAccess: appAuth,
    ),
    calendar: _mapBackendOwnedCapability(
      capability: WorkspaceCapability.calendar,
      shellAccess: appAuth,
    ),
    boards: _mapBackendOwnedCapability(
      capability: WorkspaceCapability.boards,
      shellAccess: appAuth,
    ),
    weaver: _mapDisabledPolicyCapability(WorkspaceCapability.weaver),
  );
}

WorkspaceCapabilitySnapshot _mergeWorkspaceCapabilitySnapshots({
  required WorkspaceCapabilitySnapshot localSnapshot,
  required WorkspaceCapabilitySnapshot backendSnapshot,
}) {
  return WorkspaceCapabilitySnapshot(
    shellAccess: _mergeWorkspaceCapabilityState(
      local: localSnapshot.shellAccess,
      backend: backendSnapshot.shellAccess,
    ),
    chat: _mergeWorkspaceCapabilityState(
      local: localSnapshot.chat,
      backend: backendSnapshot.chat,
    ),
    files: _mergeWorkspaceCapabilityState(
      local: localSnapshot.files,
      backend: backendSnapshot.files,
    ),
    calendar: _mergeWorkspaceCapabilityState(
      local: localSnapshot.calendar,
      backend: backendSnapshot.calendar,
    ),
    boards: _mergeWorkspaceCapabilityState(
      local: localSnapshot.boards,
      backend: backendSnapshot.boards,
    ),
    weaver: _mergeWorkspaceCapabilityState(
      local: localSnapshot.weaver,
      backend: backendSnapshot.weaver,
    ),
  );
}

WorkspaceCapabilityState _mergeWorkspaceCapabilityState({
  required WorkspaceCapabilityState local,
  required WorkspaceCapabilityState backend,
}) {
  return WorkspaceCapabilityState(
    capability: local.capability,
    readiness: backend.readiness,
    connectionStatus: local.connectionStatus,
    recoveryRequirement: local.recoveryRequirement,
    policyState: backend.policyState,
    profileKey: backend.profileKey,
    memberImpact: backend.memberImpact,
    supportRef: backend.supportRef,
    grantedCapabilities: backend.grantedCapabilities,
  );
}

WorkspaceCapabilityState _mapShellAccessCapability(
  IntegrationConnectionState appAuth,
) {
  return switch (appAuth.status) {
    IntegrationConnectionStatus.connected => WorkspaceCapabilityState(
      capability: WorkspaceCapability.shellAccess,
      readiness: WorkspaceCapabilityReadiness.ready,
      connectionStatus: appAuth.status,
      recoveryRequirement: appAuth.recoveryRequirement,
    ),
    IntegrationConnectionStatus.degraded => WorkspaceCapabilityState(
      capability: WorkspaceCapability.shellAccess,
      readiness: WorkspaceCapabilityReadiness.degraded,
      connectionStatus: appAuth.status,
      recoveryRequirement: appAuth.recoveryRequirement,
    ),
    IntegrationConnectionStatus.unavailableOnPlatform =>
      WorkspaceCapabilityState(
        capability: WorkspaceCapability.shellAccess,
        readiness: WorkspaceCapabilityReadiness.unavailable,
        connectionStatus: appAuth.status,
        recoveryRequirement: appAuth.recoveryRequirement,
      ),
    IntegrationConnectionStatus.disconnected ||
    IntegrationConnectionStatus.misconfigured ||
    IntegrationConnectionStatus.requiresReauthentication =>
      WorkspaceCapabilityState(
        capability: WorkspaceCapability.shellAccess,
        readiness: WorkspaceCapabilityReadiness.blocked,
        connectionStatus: appAuth.status,
        recoveryRequirement: appAuth.recoveryRequirement,
      ),
  };
}

WorkspaceCapabilityState _mapBackendOwnedCapability({
  required WorkspaceCapability capability,
  required IntegrationConnectionState shellAccess,
}) {
  if (shellAccess.status != IntegrationConnectionStatus.connected) {
    return WorkspaceCapabilityState(
      capability: capability,
      readiness: WorkspaceCapabilityReadiness.blocked,
      recoveryRequirement: shellAccess.recoveryRequirement,
    );
  }

  return WorkspaceCapabilityState(
    capability: capability,
    readiness: WorkspaceCapabilityReadiness.ready,
    connectionStatus: IntegrationConnectionStatus.connected,
  );
}

WorkspaceCapabilityState _mapDisabledPolicyCapability(
  WorkspaceCapability capability,
) {
  return WorkspaceCapabilityState(
    capability: capability,
    readiness: WorkspaceCapabilityReadiness.unavailable,
    policyState: WorkspaceCapabilityPolicyState.disabled,
  );
}
