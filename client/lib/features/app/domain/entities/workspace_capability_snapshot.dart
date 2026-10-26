import 'package:weave/features/app/domain/entities/workspace_connection_state.dart';

enum WorkspaceCapability {
  shellAccess,
  chat,
  files,
  calendar,
  boards,
  meetingsCalls,
  documentsCollaboration,
  decisionsEvidence,
  manualsHelp,
  releaseEvidence,
  adminControlPlane,
  agentRuntimeControl,
}

enum WorkspaceCapabilityReadiness { ready, degraded, blocked, unavailable }

enum WorkspaceCapabilityPolicyState {
  allowed,
  policyBlocked,
  disabled,
  unavailable,
}

class WorkspaceCapabilityState {
  const WorkspaceCapabilityState({
    required this.capability,
    required this.readiness,
    this.installed = true,
    this.connectionStatus,
    this.recoveryRequirement = IntegrationRecoveryRequirement.none,
    this.policyState = WorkspaceCapabilityPolicyState.unavailable,
    this.profileKey,
    this.memberImpact,
    this.supportRef,
    this.grantedCapabilities = const <String>[],
    this.memberSpaceAccessUnavailable = false,
  });

  const WorkspaceCapabilityState.notInstalled(this.capability)
    : installed = false,
      readiness = WorkspaceCapabilityReadiness.unavailable,
      connectionStatus = null,
      recoveryRequirement = IntegrationRecoveryRequirement.none,
      policyState = WorkspaceCapabilityPolicyState.unavailable,
      profileKey = null,
      memberImpact = null,
      supportRef = null,
      grantedCapabilities = const [],
      memberSpaceAccessUnavailable = false;

  final bool installed;
  final WorkspaceCapability capability;
  final WorkspaceCapabilityReadiness readiness;
  final IntegrationConnectionStatus? connectionStatus;
  final IntegrationRecoveryRequirement recoveryRequirement;
  final WorkspaceCapabilityPolicyState policyState;
  final String? profileKey;
  final String? memberImpact;
  final String? supportRef;
  final List<String> grantedCapabilities;
  final bool memberSpaceAccessUnavailable;

  bool get isReady => readiness == WorkspaceCapabilityReadiness.ready;
  bool get isPolicyBlocked =>
      policyState == WorkspaceCapabilityPolicyState.policyBlocked;

  bool grants(String capabilityKey) {
    return grantedCapabilities.contains(capabilityKey);
  }
}

class WorkspaceCapabilitySnapshot {
  const WorkspaceCapabilitySnapshot({
    required this.shellAccess,
    this.chat = const WorkspaceCapabilityState.notInstalled(
      WorkspaceCapability.chat,
    ),
    this.files = const WorkspaceCapabilityState.notInstalled(
      WorkspaceCapability.files,
    ),
    this.calendar = const WorkspaceCapabilityState.notInstalled(
      WorkspaceCapability.calendar,
    ),
    this.boards = const WorkspaceCapabilityState.notInstalled(
      WorkspaceCapability.boards,
    ),
    this.meetingsCalls = const WorkspaceCapabilityState.notInstalled(
      WorkspaceCapability.meetingsCalls,
    ),
    this.documentsCollaboration = const WorkspaceCapabilityState.notInstalled(
      WorkspaceCapability.documentsCollaboration,
    ),
    this.decisionsEvidence = const WorkspaceCapabilityState.notInstalled(
      WorkspaceCapability.decisionsEvidence,
    ),
    this.manualsHelp = const WorkspaceCapabilityState.notInstalled(
      WorkspaceCapability.manualsHelp,
    ),
    this.releaseEvidence = const WorkspaceCapabilityState.notInstalled(
      WorkspaceCapability.releaseEvidence,
    ),
    this.agentRuntimeControl = const WorkspaceCapabilityState.notInstalled(
      WorkspaceCapability.agentRuntimeControl,
    ),
    this.adminControlPlane = const WorkspaceCapabilityState(
      capability: WorkspaceCapability.adminControlPlane,
      readiness: WorkspaceCapabilityReadiness.unavailable,
    ),
  });

  final WorkspaceCapabilityState shellAccess;
  final WorkspaceCapabilityState chat;
  final WorkspaceCapabilityState files;
  final WorkspaceCapabilityState calendar;
  final WorkspaceCapabilityState boards;
  final WorkspaceCapabilityState meetingsCalls;
  final WorkspaceCapabilityState documentsCollaboration;
  final WorkspaceCapabilityState decisionsEvidence;
  final WorkspaceCapabilityState manualsHelp;
  final WorkspaceCapabilityState releaseEvidence;
  final WorkspaceCapabilityState adminControlPlane;
  final WorkspaceCapabilityState agentRuntimeControl;

  bool isInstalled(WorkspaceCapability capability) =>
      all.any((state) => state.capability == capability);

  List<WorkspaceCapabilityState> get all => <WorkspaceCapabilityState>[
    shellAccess,
    chat,
    files,
    calendar,
    boards,
    meetingsCalls,
    documentsCollaboration,
    decisionsEvidence,
    manualsHelp,
    releaseEvidence,
    adminControlPlane,
    agentRuntimeControl,
  ].where((state) => state.installed).toList(growable: false);
}
