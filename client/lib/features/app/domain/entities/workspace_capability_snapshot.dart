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
    this.connectionStatus,
    this.recoveryRequirement = IntegrationRecoveryRequirement.none,
    this.policyState = WorkspaceCapabilityPolicyState.unavailable,
    this.profileKey,
    this.memberImpact,
    this.supportRef,
    this.grantedCapabilities = const <String>[],
  });

  final WorkspaceCapability capability;
  final WorkspaceCapabilityReadiness readiness;
  final IntegrationConnectionStatus? connectionStatus;
  final IntegrationRecoveryRequirement recoveryRequirement;
  final WorkspaceCapabilityPolicyState policyState;
  final String? profileKey;
  final String? memberImpact;
  final String? supportRef;
  final List<String> grantedCapabilities;

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
    required this.chat,
    required this.files,
    required this.calendar,
    required this.boards,
    this.meetingsCalls = const WorkspaceCapabilityState(
      capability: WorkspaceCapability.meetingsCalls,
      readiness: WorkspaceCapabilityReadiness.unavailable,
      policyState: WorkspaceCapabilityPolicyState.disabled,
    ),
    this.documentsCollaboration = const WorkspaceCapabilityState(
      capability: WorkspaceCapability.documentsCollaboration,
      readiness: WorkspaceCapabilityReadiness.unavailable,
      policyState: WorkspaceCapabilityPolicyState.disabled,
    ),
    this.decisionsEvidence = const WorkspaceCapabilityState(
      capability: WorkspaceCapability.decisionsEvidence,
      readiness: WorkspaceCapabilityReadiness.ready,
      policyState: WorkspaceCapabilityPolicyState.allowed,
    ),
    this.manualsHelp = const WorkspaceCapabilityState(
      capability: WorkspaceCapability.manualsHelp,
      readiness: WorkspaceCapabilityReadiness.ready,
      policyState: WorkspaceCapabilityPolicyState.allowed,
    ),
    this.releaseEvidence = const WorkspaceCapabilityState(
      capability: WorkspaceCapability.releaseEvidence,
      readiness: WorkspaceCapabilityReadiness.ready,
      policyState: WorkspaceCapabilityPolicyState.allowed,
    ),
    this.adminControlPlane = const WorkspaceCapabilityState(
      capability: WorkspaceCapability.adminControlPlane,
      readiness: WorkspaceCapabilityReadiness.ready,
      policyState: WorkspaceCapabilityPolicyState.allowed,
    ),
    this.agentRuntimeControl = const WorkspaceCapabilityState(
      capability: WorkspaceCapability.agentRuntimeControl,
      readiness: WorkspaceCapabilityReadiness.unavailable,
      policyState: WorkspaceCapabilityPolicyState.disabled,
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
  ];
}
