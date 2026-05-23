import 'package:weave/features/app/domain/entities/workspace_connection_state.dart';

enum WorkspaceCapability { shellAccess, chat, files, calendar, boards }

enum WorkspaceCapabilityReadiness { ready, degraded, blocked, unavailable }

class WorkspaceCapabilityState {
  const WorkspaceCapabilityState({
    required this.capability,
    required this.readiness,
    this.connectionStatus,
    this.recoveryRequirement = IntegrationRecoveryRequirement.none,
  });

  final WorkspaceCapability capability;
  final WorkspaceCapabilityReadiness readiness;
  final IntegrationConnectionStatus? connectionStatus;
  final IntegrationRecoveryRequirement recoveryRequirement;

  bool get isReady => readiness == WorkspaceCapabilityReadiness.ready;
}

class WorkspaceCapabilitySnapshot {
  const WorkspaceCapabilitySnapshot({
    required this.shellAccess,
    required this.chat,
    required this.files,
    required this.calendar,
    required this.boards,
  });

  final WorkspaceCapabilityState shellAccess;
  final WorkspaceCapabilityState chat;
  final WorkspaceCapabilityState files;
  final WorkspaceCapabilityState calendar;
  final WorkspaceCapabilityState boards;

  List<WorkspaceCapabilityState> get all => <WorkspaceCapabilityState>[
    shellAccess,
    chat,
    files,
    calendar,
    boards,
  ];
}
