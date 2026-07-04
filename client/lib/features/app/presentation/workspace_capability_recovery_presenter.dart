import 'package:weave/features/app/domain/entities/workspace_capability_snapshot.dart';
import 'package:weave/l10n/generated/app_localizations.dart';

enum WorkspaceMemberRecoveryState {
  available,
  disabledByPolicy,
  notConfigured,
  degraded,
  unavailable,
  comingLater,
}

class WorkspaceCapabilityRecoveryPresentation {
  const WorkspaceCapabilityRecoveryPresentation({
    required this.state,
    required this.stateLabel,
    required this.recovery,
    required this.supportReference,
  });

  final WorkspaceMemberRecoveryState state;
  final String stateLabel;
  final String recovery;
  final String supportReference;

  String semanticLabel(AppLocalizations l10n, String capabilityLabel) {
    return l10n.settingsWorkspaceRecoverySemanticLabel(
      capabilityLabel,
      stateLabel,
      recovery,
      supportReference,
    );
  }
}

WorkspaceCapabilityRecoveryPresentation workspaceCapabilityRecoveryPresentation(
  AppLocalizations l10n,
  WorkspaceCapabilityState capability,
) {
  final state = workspaceMemberRecoveryState(capability);
  return WorkspaceCapabilityRecoveryPresentation(
    state: state,
    stateLabel: _stateLabel(l10n, state),
    recovery: _recovery(l10n, state),
    supportReference: capability.supportRef?.trim().isNotEmpty == true
        ? capability.supportRef!.trim()
        : l10n.settingsWorkspaceRecoverySupportRefUnavailable,
  );
}

WorkspaceMemberRecoveryState workspaceMemberRecoveryState(
  WorkspaceCapabilityState capability,
) {
  return switch ((capability.readiness, capability.policyState)) {
    (_, WorkspaceCapabilityPolicyState.policyBlocked) ||
    (
      _,
      WorkspaceCapabilityPolicyState.disabled,
    ) => WorkspaceMemberRecoveryState.disabledByPolicy,
    (WorkspaceCapabilityReadiness.ready, _) =>
      WorkspaceMemberRecoveryState.available,
    (WorkspaceCapabilityReadiness.degraded, _) =>
      WorkspaceMemberRecoveryState.degraded,
    (WorkspaceCapabilityReadiness.blocked, _) =>
      WorkspaceMemberRecoveryState.notConfigured,
    (WorkspaceCapabilityReadiness.unavailable, _) => _unavailableRecoveryState(
      capability.capability,
    ),
  };
}

WorkspaceMemberRecoveryState _unavailableRecoveryState(
  WorkspaceCapability capability,
) {
  return switch (capability) {
    WorkspaceCapability.calendar ||
    WorkspaceCapability.boards ||
    WorkspaceCapability.meetingsCalls ||
    WorkspaceCapability.documentsCollaboration ||
    WorkspaceCapability.decisionsEvidence ||
    WorkspaceCapability.manualsHelp ||
    WorkspaceCapability.releaseEvidence ||
    WorkspaceCapability.adminControlPlane =>
      WorkspaceMemberRecoveryState.comingLater,
    WorkspaceCapability.shellAccess ||
    WorkspaceCapability.chat ||
    WorkspaceCapability.files ||
    WorkspaceCapability.weaver => WorkspaceMemberRecoveryState.unavailable,
  };
}

String _stateLabel(AppLocalizations l10n, WorkspaceMemberRecoveryState state) {
  return switch (state) {
    WorkspaceMemberRecoveryState.available =>
      l10n.settingsWorkspaceRecoveryAvailable,
    WorkspaceMemberRecoveryState.disabledByPolicy =>
      l10n.settingsWorkspaceRecoveryDisabledByPolicy,
    WorkspaceMemberRecoveryState.notConfigured =>
      l10n.settingsWorkspaceRecoveryNotConfigured,
    WorkspaceMemberRecoveryState.degraded =>
      l10n.settingsWorkspaceRecoveryDegraded,
    WorkspaceMemberRecoveryState.unavailable =>
      l10n.settingsWorkspaceRecoveryUnavailable,
    WorkspaceMemberRecoveryState.comingLater =>
      l10n.settingsWorkspaceRecoveryComingLater,
  };
}

String _recovery(AppLocalizations l10n, WorkspaceMemberRecoveryState state) {
  return switch (state) {
    WorkspaceMemberRecoveryState.available =>
      l10n.settingsWorkspaceRecoveryAvailableAction,
    WorkspaceMemberRecoveryState.disabledByPolicy =>
      l10n.settingsWorkspaceRecoveryDisabledByPolicyAction,
    WorkspaceMemberRecoveryState.notConfigured =>
      l10n.settingsWorkspaceRecoveryNotConfiguredAction,
    WorkspaceMemberRecoveryState.degraded =>
      l10n.settingsWorkspaceRecoveryDegradedAction,
    WorkspaceMemberRecoveryState.unavailable =>
      l10n.settingsWorkspaceRecoveryUnavailableAction,
    WorkspaceMemberRecoveryState.comingLater =>
      l10n.settingsWorkspaceRecoveryComingLaterAction,
  };
}
