import 'package:weave/features/app/domain/entities/workspace_connection_state.dart';

enum WorkspaceCapability { shellAccess, chat, files, calendar, boards, weaver }

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
    this.grantedCapabilities = const <String>[],
  });

  final WorkspaceCapability capability;
  final WorkspaceCapabilityReadiness readiness;
  final IntegrationConnectionStatus? connectionStatus;
  final IntegrationRecoveryRequirement recoveryRequirement;
  final WorkspaceCapabilityPolicyState policyState;
  final String? profileKey;
  final String? memberImpact;
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
    this.weaver = const WorkspaceCapabilityState(
      capability: WorkspaceCapability.weaver,
      readiness: WorkspaceCapabilityReadiness.unavailable,
      policyState: WorkspaceCapabilityPolicyState.disabled,
      memberImpact:
          'Weaver is disabled by workspace policy until an admin enables a governed runtime profile.',
    ),
  });

  final WorkspaceCapabilityState shellAccess;
  final WorkspaceCapabilityState chat;
  final WorkspaceCapabilityState files;
  final WorkspaceCapabilityState calendar;
  final WorkspaceCapabilityState boards;
  final WorkspaceCapabilityState weaver;

  List<WorkspaceCapabilityState> get all => <WorkspaceCapabilityState>[
    shellAccess,
    chat,
    files,
    calendar,
    boards,
    weaver,
  ];
}
