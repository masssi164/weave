import 'package:flutter/foundation.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:weave/core/bootstrap/domain/bootstrap_state.dart';
import 'package:weave/core/bootstrap/presentation/providers/app_bootstrap_provider.dart';
import 'package:weave/features/app/domain/entities/integration_invalidation.dart';
import 'package:weave/features/app/domain/entities/workspace_capability_snapshot.dart';
import 'package:weave/features/app/domain/entities/workspace_connection_state.dart';
import 'package:weave/features/app/presentation/providers/workspace_invalidation_provider.dart';
import 'package:weave/features/chat/domain/entities/chat_failure.dart';
import 'package:weave/features/chat/domain/entities/chat_security_state.dart';
import 'package:weave/features/chat/presentation/providers/chat_security_repository_provider.dart';
import 'package:weave/features/files/domain/entities/files_connection_state.dart';
import 'package:weave/features/files/domain/entities/files_failure.dart';
import 'package:weave/features/files/presentation/providers/files_repository_provider.dart';
import 'package:weave/features/server_config/presentation/providers/server_configuration_form_controller.dart';

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

final matrixIntegrationConnectionProvider =
    FutureProvider<IntegrationConnectionState>((ref) async {
      final invalidation = ref.watch(
        integrationInvalidationProvider(WorkspaceIntegration.matrix),
      );
      final configuration = await ref.watch(
        savedServerConfigurationProvider.future,
      );
      if (configuration == null) {
        return IntegrationConnectionState(
          integration: WorkspaceIntegration.matrix,
          status: IntegrationConnectionStatus.misconfigured,
          recoveryRequirement: IntegrationRecoveryRequirement.completeSetup,
          lastInvalidation: invalidation,
        );
      }

      if (kIsWeb) {
        return IntegrationConnectionState(
          integration: WorkspaceIntegration.matrix,
          status: IntegrationConnectionStatus.unavailableOnPlatform,
          recoveryRequirement: IntegrationRecoveryRequirement.switchPlatform,
          lastInvalidation: invalidation,
        );
      }

      try {
        final security = await ref
            .watch(chatSecurityRepositoryProvider)
            .loadSecurityState(refresh: false);
        return _mapMatrixConnectionState(security, invalidation);
      } on ChatFailure catch (failure) {
        return _mapMatrixFailure(failure, invalidation);
      }
    });

final nextcloudIntegrationConnectionProvider =
    FutureProvider<IntegrationConnectionState>((ref) async {
      final invalidation = ref.watch(
        integrationInvalidationProvider(WorkspaceIntegration.nextcloud),
      );
      final configuration = await ref.watch(
        savedServerConfigurationProvider.future,
      );
      if (configuration == null) {
        return IntegrationConnectionState(
          integration: WorkspaceIntegration.nextcloud,
          status: IntegrationConnectionStatus.misconfigured,
          recoveryRequirement: IntegrationRecoveryRequirement.completeSetup,
          lastInvalidation: invalidation,
        );
      }

      try {
        final connectionState = await ref
            .watch(filesRepositoryProvider)
            .restoreConnection();
        return _mapNextcloudConnectionState(connectionState, invalidation);
      } on FilesFailure catch (failure) {
        return _mapNextcloudFailure(failure, invalidation);
      }
    });

final workspaceConnectionStateProvider =
    Provider<AsyncValue<WorkspaceConnectionState>>((ref) {
      final appAuth = ref.watch(appAuthIntegrationConnectionProvider);
      final matrix = ref.watch(matrixIntegrationConnectionProvider);
      final nextcloud = ref.watch(nextcloudIntegrationConnectionProvider);

      if (appAuth.hasError) {
        return AsyncError(appAuth.error!, appAuth.stackTrace!);
      }
      if (matrix.hasError) {
        return AsyncError(matrix.error!, matrix.stackTrace!);
      }
      if (nextcloud.hasError) {
        return AsyncError(nextcloud.error!, nextcloud.stackTrace!);
      }
      if (appAuth.isLoading || matrix.isLoading || nextcloud.isLoading) {
        return const AsyncLoading();
      }

      return AsyncData(
        WorkspaceConnectionState(
          appAuth: appAuth.requireValue,
          matrix: matrix.requireValue,
          nextcloud: nextcloud.requireValue,
        ),
      );
    });

final workspaceCapabilitySnapshotProvider =
    Provider<AsyncValue<WorkspaceCapabilitySnapshot>>((ref) {
      final workspace = ref.watch(workspaceConnectionStateProvider);
      return workspace.whenData(_mapWorkspaceCapabilitySnapshot);
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

IntegrationConnectionState _mapMatrixConnectionState(
  ChatSecurityState security,
  IntegrationInvalidation? invalidation,
) {
  if (!security.isMatrixSignedIn) {
    return IntegrationConnectionState(
      integration: WorkspaceIntegration.matrix,
      status: IntegrationConnectionStatus.disconnected,
      recoveryRequirement: IntegrationRecoveryRequirement.connect,
      lastInvalidation: invalidation,
    );
  }

  final requiresSecurityRecovery =
      security.bootstrapState == ChatSecurityBootstrapState.notInitialized ||
      security.bootstrapState ==
          ChatSecurityBootstrapState.partiallyInitialized ||
      security.bootstrapState == ChatSecurityBootstrapState.recoveryRequired ||
      security.bootstrapState == ChatSecurityBootstrapState.unavailable ||
      security.accountVerificationState ==
          ChatAccountVerificationState.verificationRequired ||
      security.deviceVerificationState !=
          ChatDeviceVerificationState.verified ||
      security.keyBackupState == ChatKeyBackupState.missing ||
      security.keyBackupState == ChatKeyBackupState.recoveryRequired ||
      security.roomEncryptionReadiness ==
          ChatRoomEncryptionReadiness.encryptedRoomsNeedAttention ||
      security.verificationSession.isActionable;

  return IntegrationConnectionState(
    integration: WorkspaceIntegration.matrix,
    status: requiresSecurityRecovery
        ? IntegrationConnectionStatus.degraded
        : IntegrationConnectionStatus.connected,
    recoveryRequirement: requiresSecurityRecovery
        ? IntegrationRecoveryRequirement.completeSetup
        : IntegrationRecoveryRequirement.none,
    lastInvalidation: invalidation,
  );
}

IntegrationConnectionState _mapMatrixFailure(
  ChatFailure failure,
  IntegrationInvalidation? invalidation,
) {
  return switch (failure.type) {
    ChatFailureType.configuration ||
    ChatFailureType.unsupportedConfiguration => IntegrationConnectionState(
      integration: WorkspaceIntegration.matrix,
      status: IntegrationConnectionStatus.misconfigured,
      recoveryRequirement: IntegrationRecoveryRequirement.reviewConfiguration,
      lastInvalidation: invalidation,
    ),
    ChatFailureType.sessionRequired ||
    ChatFailureType.cancelled => IntegrationConnectionState(
      integration: WorkspaceIntegration.matrix,
      status: IntegrationConnectionStatus.disconnected,
      recoveryRequirement: IntegrationRecoveryRequirement.connect,
      lastInvalidation: invalidation,
    ),
    ChatFailureType.unsupportedPlatform => IntegrationConnectionState(
      integration: WorkspaceIntegration.matrix,
      status: IntegrationConnectionStatus.unavailableOnPlatform,
      recoveryRequirement: IntegrationRecoveryRequirement.switchPlatform,
      lastInvalidation: invalidation,
    ),
    ChatFailureType.protocol ||
    ChatFailureType.storage ||
    ChatFailureType.unknown => IntegrationConnectionState(
      integration: WorkspaceIntegration.matrix,
      status: IntegrationConnectionStatus.degraded,
      recoveryRequirement: IntegrationRecoveryRequirement.connect,
      lastInvalidation: invalidation,
    ),
  };
}

IntegrationConnectionState _mapNextcloudConnectionState(
  FilesConnectionState connectionState,
  IntegrationInvalidation? invalidation,
) {
  return switch (connectionState.status) {
    FilesConnectionStatus.misconfigured => IntegrationConnectionState(
      integration: WorkspaceIntegration.nextcloud,
      status: IntegrationConnectionStatus.misconfigured,
      recoveryRequirement: IntegrationRecoveryRequirement.reviewConfiguration,
      lastInvalidation: invalidation,
    ),
    FilesConnectionStatus.disconnected => IntegrationConnectionState(
      integration: WorkspaceIntegration.nextcloud,
      status: IntegrationConnectionStatus.disconnected,
      recoveryRequirement: IntegrationRecoveryRequirement.connect,
      lastInvalidation: invalidation,
    ),
    FilesConnectionStatus.connected => IntegrationConnectionState(
      integration: WorkspaceIntegration.nextcloud,
      status: IntegrationConnectionStatus.connected,
      lastInvalidation: invalidation,
    ),
    FilesConnectionStatus.invalid => IntegrationConnectionState(
      integration: WorkspaceIntegration.nextcloud,
      status: IntegrationConnectionStatus.requiresReauthentication,
      recoveryRequirement: IntegrationRecoveryRequirement.reauthenticate,
      lastInvalidation: invalidation,
    ),
  };
}

IntegrationConnectionState _mapNextcloudFailure(
  FilesFailure failure,
  IntegrationInvalidation? invalidation,
) {
  return switch (failure.type) {
    FilesFailureType.configuration => IntegrationConnectionState(
      integration: WorkspaceIntegration.nextcloud,
      status: IntegrationConnectionStatus.misconfigured,
      recoveryRequirement: IntegrationRecoveryRequirement.reviewConfiguration,
      lastInvalidation: invalidation,
    ),
    FilesFailureType.sessionRequired ||
    FilesFailureType.cancelled => IntegrationConnectionState(
      integration: WorkspaceIntegration.nextcloud,
      status: IntegrationConnectionStatus.disconnected,
      recoveryRequirement: IntegrationRecoveryRequirement.connect,
      lastInvalidation: invalidation,
    ),
    FilesFailureType.invalidCredentials => IntegrationConnectionState(
      integration: WorkspaceIntegration.nextcloud,
      status: IntegrationConnectionStatus.requiresReauthentication,
      recoveryRequirement: IntegrationRecoveryRequirement.reauthenticate,
      lastInvalidation: invalidation,
    ),
    FilesFailureType.unsupportedPlatform => IntegrationConnectionState(
      integration: WorkspaceIntegration.nextcloud,
      status: IntegrationConnectionStatus.unavailableOnPlatform,
      recoveryRequirement: IntegrationRecoveryRequirement.switchPlatform,
      lastInvalidation: invalidation,
    ),
    FilesFailureType.protocol ||
    FilesFailureType.storage ||
    FilesFailureType.unknown => IntegrationConnectionState(
      integration: WorkspaceIntegration.nextcloud,
      status: IntegrationConnectionStatus.degraded,
      recoveryRequirement: IntegrationRecoveryRequirement.connect,
      lastInvalidation: invalidation,
    ),
  };
}

WorkspaceCapabilitySnapshot _mapWorkspaceCapabilitySnapshot(
  WorkspaceConnectionState connection,
) {
  final shellAccess = _mapShellAccessCapability(connection.appAuth);

  return WorkspaceCapabilitySnapshot(
    shellAccess: shellAccess,
    chat: _mapServiceCapability(
      capability: WorkspaceCapability.chat,
      shellAccess: connection.appAuth,
      integration: connection.matrix,
    ),
    files: _mapServiceCapability(
      capability: WorkspaceCapability.files,
      shellAccess: connection.appAuth,
      integration: connection.nextcloud,
    ),
    calendar: _mapFutureCapability(
      capability: WorkspaceCapability.calendar,
      shellAccess: connection.appAuth,
    ),
    boards: _mapFutureCapability(
      capability: WorkspaceCapability.boards,
      shellAccess: connection.appAuth,
    ),
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

WorkspaceCapabilityState _mapServiceCapability({
  required WorkspaceCapability capability,
  required IntegrationConnectionState shellAccess,
  required IntegrationConnectionState integration,
}) {
  if (shellAccess.status != IntegrationConnectionStatus.connected) {
    return WorkspaceCapabilityState(
      capability: capability,
      readiness: WorkspaceCapabilityReadiness.blocked,
      connectionStatus: integration.status,
      recoveryRequirement: shellAccess.recoveryRequirement,
    );
  }

  return switch (integration.status) {
    IntegrationConnectionStatus.connected => WorkspaceCapabilityState(
      capability: capability,
      readiness: WorkspaceCapabilityReadiness.ready,
      connectionStatus: integration.status,
      recoveryRequirement: integration.recoveryRequirement,
    ),
    IntegrationConnectionStatus.degraded => WorkspaceCapabilityState(
      capability: capability,
      readiness: WorkspaceCapabilityReadiness.degraded,
      connectionStatus: integration.status,
      recoveryRequirement: integration.recoveryRequirement,
    ),
    IntegrationConnectionStatus.unavailableOnPlatform =>
      WorkspaceCapabilityState(
        capability: capability,
        readiness: WorkspaceCapabilityReadiness.unavailable,
        connectionStatus: integration.status,
        recoveryRequirement: integration.recoveryRequirement,
      ),
    IntegrationConnectionStatus.disconnected ||
    IntegrationConnectionStatus.misconfigured ||
    IntegrationConnectionStatus.requiresReauthentication =>
      WorkspaceCapabilityState(
        capability: capability,
        readiness: WorkspaceCapabilityReadiness.blocked,
        connectionStatus: integration.status,
        recoveryRequirement: integration.recoveryRequirement,
      ),
  };
}

WorkspaceCapabilityState _mapFutureCapability({
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
    readiness: WorkspaceCapabilityReadiness.unavailable,
  );
}
