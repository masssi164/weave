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
      chat: chat.toCapabilityState(WorkspaceCapability.chat),
      files: files.toCapabilityState(WorkspaceCapability.files),
      calendar: calendar.toCapabilityState(WorkspaceCapability.calendar),
      boards: boards.toCapabilityState(WorkspaceCapability.boards),
      meetingsCalls: meetingsCalls.toCapabilityState(
        WorkspaceCapability.meetingsCalls,
      ),
      documentsCollaboration: documentsCollaboration.toCapabilityState(
        WorkspaceCapability.documentsCollaboration,
      ),
      decisionsEvidence: decisionsEvidence.toCapabilityState(
        WorkspaceCapability.decisionsEvidence,
      ),
      manualsHelp: manualsHelp.toCapabilityState(
        WorkspaceCapability.manualsHelp,
      ),
      releaseEvidence: releaseEvidence.toCapabilityState(
        WorkspaceCapability.releaseEvidence,
      ),
      adminControlPlane: adminControlPlane.toCapabilityState(
        WorkspaceCapability.adminControlPlane,
      ),
      agentRuntimeControl: agentRuntimeControl.toCapabilityState(
        WorkspaceCapability.agentRuntimeControl,
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
      readiness: (enabled ?? false)
          ? _parseReadiness(_requiredReadiness(readiness?.value))
          : WorkspaceCapabilityReadiness.unavailable,
      policyState: _parsePolicyState(
        policyState?.value ?? ((enabled ?? false) ? 'allowed' : 'disabled'),
      ),
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
