import 'package:weave/core/failures/app_failure.dart';
import 'package:weave/features/app/domain/entities/workspace_capability_snapshot.dart';
import 'package:weave/generated/user_api/api.dart' as openapi;

extension WorkspaceCapabilitiesResponseMapper
    on openapi.WorkspaceCapabilitiesResponse {
  WorkspaceCapabilitySnapshot toSnapshot() {
    return WorkspaceCapabilitySnapshot(
      shellAccess: shellAccess.toCapabilityState(
        WorkspaceCapability.shellAccess,
      ),
      chat:
          domains['chat']?.toCapabilityState(WorkspaceCapability.chat) ??
          const WorkspaceCapabilityState.notInstalled(WorkspaceCapability.chat),
      files:
          domains['files']?.toCapabilityState(WorkspaceCapability.files) ??
          const WorkspaceCapabilityState.notInstalled(
            WorkspaceCapability.files,
          ),
      calendar:
          domains['calendar']?.toCapabilityState(
            WorkspaceCapability.calendar,
          ) ??
          const WorkspaceCapabilityState.notInstalled(
            WorkspaceCapability.calendar,
          ),
      boards:
          domains['boards']?.toCapabilityState(WorkspaceCapability.boards) ??
          const WorkspaceCapabilityState.notInstalled(
            WorkspaceCapability.boards,
          ),
      meetingsCalls:
          domains['meetings']?.toCapabilityState(
            WorkspaceCapability.meetingsCalls,
          ) ??
          const WorkspaceCapabilityState.notInstalled(
            WorkspaceCapability.meetingsCalls,
          ),
      documentsCollaboration:
          domains['office']?.toCapabilityState(
            WorkspaceCapability.documentsCollaboration,
          ) ??
          const WorkspaceCapabilityState.notInstalled(
            WorkspaceCapability.documentsCollaboration,
          ),
      decisionsEvidence:
          domains['decisions']?.toCapabilityState(
            WorkspaceCapability.decisionsEvidence,
          ) ??
          const WorkspaceCapabilityState.notInstalled(
            WorkspaceCapability.decisionsEvidence,
          ),
      manualsHelp:
          domains['manuals']?.toCapabilityState(
            WorkspaceCapability.manualsHelp,
          ) ??
          const WorkspaceCapabilityState.notInstalled(
            WorkspaceCapability.manualsHelp,
          ),
      releaseEvidence:
          domains['devops']?.toCapabilityState(
            WorkspaceCapability.releaseEvidence,
          ) ??
          const WorkspaceCapabilityState.notInstalled(
            WorkspaceCapability.releaseEvidence,
          ),
      agentRuntimeControl:
          domains['agent-runtime']?.toCapabilityState(
            WorkspaceCapability.agentRuntimeControl,
          ) ??
          const WorkspaceCapabilityState.notInstalled(
            WorkspaceCapability.agentRuntimeControl,
          ),
      adminControlPlane: adminControlPlane.toCapabilityState(
        WorkspaceCapability.adminControlPlane,
      ),
    );
  }
}

String _requiredReadiness(String? value) {
  if (value != null) return value;
  throw const AppFailure.unknown('wcap_002');
}

extension WorkspaceCapabilityStatusResponseMapper
    on openapi.WorkspaceCapabilityStatusResponse {
  WorkspaceCapabilityState toCapabilityState(WorkspaceCapability capability) {
    return WorkspaceCapabilityState(
      capability: capability,
      readiness: enabled
          ? _parseReadiness(_requiredReadiness(readiness.value))
          : WorkspaceCapabilityReadiness.unavailable,
      policyState: _parsePolicyState(policyState.value),
      profileKey: profileKey,
      memberImpact: memberImpact,
      supportRef: supportRef,
      grantedCapabilities: grantedCapabilities,
    );
  }

  WorkspaceCapabilityPolicyState _parsePolicyState(String rawValue) {
    return switch (rawValue.trim()) {
      'allowed' => WorkspaceCapabilityPolicyState.allowed,
      'policy_blocked' => WorkspaceCapabilityPolicyState.policyBlocked,
      'disabled' => WorkspaceCapabilityPolicyState.disabled,
      'unavailable' => WorkspaceCapabilityPolicyState.unavailable,
      _ => throw AppFailure.unknown('wcap_003', cause: rawValue),
    };
  }

  WorkspaceCapabilityReadiness _parseReadiness(String rawValue) {
    return switch (rawValue.trim()) {
      'ready' => WorkspaceCapabilityReadiness.ready,
      'degraded' => WorkspaceCapabilityReadiness.degraded,
      'blocked' => WorkspaceCapabilityReadiness.blocked,
      'unavailable' => WorkspaceCapabilityReadiness.unavailable,
      _ => throw AppFailure.unknown('wcap_004', cause: rawValue),
    };
  }
}
