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
        memberImpact: 'Meetings and calls are disabled in this workspace.',
      ).toCapabilityState(WorkspaceCapability.meetingsCalls),
      documentsCollaboration: _optionalCapability(
        documentsCollaboration,
        enabled: false,
        readiness: 'unavailable',
        policyState: 'disabled',
        memberImpact:
            'Documents and collaboration are disabled in this workspace.',
      ).toCapabilityState(WorkspaceCapability.documentsCollaboration),
      decisionsEvidence: _optionalCapability(
        decisionsEvidence,
        enabled: true,
        readiness: 'ready',
        policyState: 'allowed',
        memberImpact:
            'Decisions and evidence are represented by the Weave domain model.',
      ).toCapabilityState(WorkspaceCapability.decisionsEvidence),
      manualsHelp: _optionalCapability(
        manualsHelp,
        enabled: true,
        readiness: 'ready',
        policyState: 'allowed',
        memberImpact: 'Manuals and help are available through Weave.',
      ).toCapabilityState(WorkspaceCapability.manualsHelp),
      releaseEvidence: _optionalCapability(
        releaseEvidence,
        enabled: true,
        readiness: 'ready',
        policyState: 'allowed',
        memberImpact: 'Release evidence is available through Weave.',
      ).toCapabilityState(WorkspaceCapability.releaseEvidence),
      adminControlPlane: _optionalCapability(
        adminControlPlane,
        enabled: true,
        readiness: 'ready',
        policyState: 'allowed',
        memberImpact:
            'Workspace Health exposes support-safe admin readiness only.',
      ).toCapabilityState(WorkspaceCapability.adminControlPlane),
      weaver: _optionalCapability(
        weaver,
        enabled: false,
        readiness: 'unavailable',
        policyState: 'disabled',
        memberImpact:
            'Weaver is disabled by workspace policy until an admin enables a governed runtime profile.',
      ).toCapabilityState(WorkspaceCapability.weaver),
    );
  }
}

openapi.WorkspaceCapabilityStatusResponse _requiredCapability(
  openapi.WorkspaceCapabilityStatusResponse? value,
  String key,
) {
  if (value != null) return value;
  throw AppFailure.unknown(
    'The backend returned an invalid workspace capabilities response.',
    cause: 'Expected an object for "$key".',
  );
}

openapi.WorkspaceCapabilityStatusResponse _optionalCapability(
  openapi.WorkspaceCapabilityStatusResponse? value, {
  required bool enabled,
  required String readiness,
  required String policyState,
  required String memberImpact,
}) {
  return value ??
      openapi.WorkspaceCapabilityStatusResponse(
        enabled: enabled,
        readiness: readiness,
        policyState: policyState,
        memberImpact: memberImpact,
      );
}

String _requiredReadiness(String? value) {
  if (value != null) return value;
  throw const AppFailure.unknown(
    'The backend returned an invalid workspace capability item.',
  );
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
      grantedCapabilities: grantedCapabilities ?? const <String>[],
    );
  }

  WorkspaceCapabilityPolicyState _parsePolicyState(String rawValue) {
    return switch (rawValue.trim()) {
      'allowed' => WorkspaceCapabilityPolicyState.allowed,
      'policy_blocked' => WorkspaceCapabilityPolicyState.policyBlocked,
      'disabled' => WorkspaceCapabilityPolicyState.disabled,
      'unavailable' => WorkspaceCapabilityPolicyState.unavailable,
      _ => throw AppFailure.unknown(
        'The backend returned an unknown workspace capability policy state.',
        cause: rawValue,
      ),
    };
  }

  WorkspaceCapabilityReadiness _parseReadiness(String rawValue) {
    return switch (rawValue.trim()) {
      'ready' => WorkspaceCapabilityReadiness.ready,
      'degraded' => WorkspaceCapabilityReadiness.degraded,
      'blocked' => WorkspaceCapabilityReadiness.blocked,
      'unavailable' => WorkspaceCapabilityReadiness.unavailable,
      _ => throw AppFailure.unknown(
        'The backend returned an unknown workspace capability readiness.',
        cause: rawValue,
      ),
    };
  }
}
