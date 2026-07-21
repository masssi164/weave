import 'package:weave/core/failures/app_failure.dart';
import 'package:weave/features/app/domain/entities/workspace_capability_snapshot.dart';
import 'package:weave/generated/openapi_models.dart' as openapi;

extension WorkspaceCapabilitiesResponseMapper
    on openapi.WorkspaceCapabilitiesResponse {
  WorkspaceCapabilitySnapshot toSnapshot() {
    return WorkspaceCapabilitySnapshot(
      shellAccess: _requiredCapability(
        shellAccess,
        'shellAccess',
      ).toCapabilityState(WorkspaceCapability.shellAccess),
      chat: _requiredCapability(
        chat,
        'chat',
      ).toCapabilityState(WorkspaceCapability.chat),
      files: _requiredCapability(
        files,
        'files',
      ).toCapabilityState(WorkspaceCapability.files),
      calendar: _requiredCapability(
        calendar,
        'calendar',
      ).toCapabilityState(WorkspaceCapability.calendar),
      boards: _requiredCapability(
        boards,
        'boards',
      ).toCapabilityState(WorkspaceCapability.boards),
      meetingsCalls: _optionalCapability(
        meetingsCalls,
        enabled: false,
        readiness: 'unavailable',
        policyState: 'disabled',
      ).toCapabilityState(WorkspaceCapability.meetingsCalls),
      documentsCollaboration: _optionalCapability(
        documentsCollaboration,
        enabled: false,
        readiness: 'unavailable',
        policyState: 'disabled',
      ).toCapabilityState(WorkspaceCapability.documentsCollaboration),
      decisionsEvidence: _optionalCapability(
        decisionsEvidence,
        enabled: true,
        readiness: 'ready',
        policyState: 'allowed',
      ).toCapabilityState(WorkspaceCapability.decisionsEvidence),
      manualsHelp: _optionalCapability(
        manualsHelp,
        enabled: true,
        readiness: 'ready',
        policyState: 'allowed',
      ).toCapabilityState(WorkspaceCapability.manualsHelp),
      releaseEvidence: _optionalCapability(
        releaseEvidence,
        enabled: true,
        readiness: 'ready',
        policyState: 'allowed',
      ).toCapabilityState(WorkspaceCapability.releaseEvidence),
      adminControlPlane: _optionalCapability(
        adminControlPlane,
        enabled: true,
        readiness: 'ready',
        policyState: 'allowed',
      ).toCapabilityState(WorkspaceCapability.adminControlPlane),
      agentRuntimeControl: _optionalCapability(
        agentRuntimeControl,
        enabled: false,
        readiness: 'unavailable',
        policyState: 'disabled',
      ).toCapabilityState(WorkspaceCapability.agentRuntimeControl),
    );
  }
}

openapi.WorkspaceCapabilityStatusResponse _requiredCapability(
  openapi.WorkspaceCapabilityStatusResponse? value,
  String key,
) {
  if (value != null) return value;
  throw AppFailure.unknown('wcap_001', cause: 'wcap_field:$key');
}

openapi.WorkspaceCapabilityStatusResponse _optionalCapability(
  openapi.WorkspaceCapabilityStatusResponse? value, {
  required bool enabled,
  required String readiness,
  required String policyState,
}) {
  return value ??
      openapi.WorkspaceCapabilityStatusResponse(
        enabled: enabled,
        readiness: readiness,
        policyState: policyState,
      );
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
          ? _parseReadiness(_requiredReadiness(readiness))
          : WorkspaceCapabilityReadiness.unavailable,
      policyState: _parsePolicyState(
        policyState ?? ((enabled ?? false) ? 'allowed' : 'disabled'),
      ),
      profileKey: profileKey,
      memberImpact: memberImpact,
      supportRef: supportRef,
      grantedCapabilities: grantedCapabilities ?? const <String>[],
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
