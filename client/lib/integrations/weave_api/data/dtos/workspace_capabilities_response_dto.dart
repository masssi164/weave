import 'package:weave/core/failures/app_failure.dart';
import 'package:weave/features/app/domain/entities/workspace_capability_snapshot.dart';

class WorkspaceCapabilitiesResponseDto {
  const WorkspaceCapabilitiesResponseDto({
    required this.shellAccess,
    required this.chat,
    required this.files,
    required this.calendar,
    required this.boards,
    required this.meetingsCalls,
    required this.documentsCollaboration,
    required this.decisionsEvidence,
    required this.manualsHelp,
    required this.releaseEvidence,
    required this.adminControlPlane,
    required this.weaver,
  });

  factory WorkspaceCapabilitiesResponseDto.fromJson(Map<String, dynamic> json) {
    return WorkspaceCapabilitiesResponseDto(
      shellAccess: WorkspaceCapabilityStatusDto.fromJson(
        _readNestedJson(json, 'shellAccess'),
      ),
      chat: WorkspaceCapabilityStatusDto.fromJson(
        _readNestedJson(json, 'chat'),
      ),
      files: WorkspaceCapabilityStatusDto.fromJson(
        _readNestedJson(json, 'files'),
      ),
      calendar: WorkspaceCapabilityStatusDto.fromJson(
        _readNestedJson(json, 'calendar'),
      ),
      boards: WorkspaceCapabilityStatusDto.fromJson(
        _readNestedJson(json, 'boards'),
      ),
      meetingsCalls: WorkspaceCapabilityStatusDto.fromJson(
        _readOptionalNestedJson(
          json,
          'meetingsCalls',
          enabled: false,
          readiness: 'unavailable',
          policyState: 'disabled',
          memberImpact: 'Meetings and calls are disabled in this workspace.',
        ),
      ),
      documentsCollaboration: WorkspaceCapabilityStatusDto.fromJson(
        _readOptionalNestedJson(
          json,
          'documentsCollaboration',
          enabled: false,
          readiness: 'unavailable',
          policyState: 'disabled',
          memberImpact:
              'Documents and collaboration are disabled in this workspace.',
        ),
      ),
      decisionsEvidence: WorkspaceCapabilityStatusDto.fromJson(
        _readOptionalNestedJson(
          json,
          'decisionsEvidence',
          enabled: true,
          readiness: 'ready',
          policyState: 'allowed',
          memberImpact:
              'Decisions and evidence are represented by the Weave domain model.',
        ),
      ),
      manualsHelp: WorkspaceCapabilityStatusDto.fromJson(
        _readOptionalNestedJson(
          json,
          'manualsHelp',
          enabled: true,
          readiness: 'ready',
          policyState: 'allowed',
          memberImpact: 'Manuals and help are available through Weave.',
        ),
      ),
      releaseEvidence: WorkspaceCapabilityStatusDto.fromJson(
        _readOptionalNestedJson(
          json,
          'releaseEvidence',
          enabled: true,
          readiness: 'ready',
          policyState: 'allowed',
          memberImpact: 'Release evidence is available through Weave.',
        ),
      ),
      adminControlPlane: WorkspaceCapabilityStatusDto.fromJson(
        _readOptionalNestedJson(
          json,
          'adminControlPlane',
          enabled: true,
          readiness: 'ready',
          policyState: 'allowed',
          memberImpact:
              'Workspace Health exposes support-safe admin readiness only.',
        ),
      ),
      weaver: WorkspaceCapabilityStatusDto.fromJson(
        json['weaver'] is Map<String, dynamic>
            ? _readNestedJson(json, 'weaver')
            : const <String, dynamic>{
                'enabled': false,
                'readiness': 'unavailable',
                'policyState': 'disabled',
                'memberImpact':
                    'Weaver is disabled by workspace policy until an admin enables a governed runtime profile.',
              },
      ),
    );
  }

  final WorkspaceCapabilityStatusDto shellAccess;
  final WorkspaceCapabilityStatusDto chat;
  final WorkspaceCapabilityStatusDto files;
  final WorkspaceCapabilityStatusDto calendar;
  final WorkspaceCapabilityStatusDto boards;
  final WorkspaceCapabilityStatusDto meetingsCalls;
  final WorkspaceCapabilityStatusDto documentsCollaboration;
  final WorkspaceCapabilityStatusDto decisionsEvidence;
  final WorkspaceCapabilityStatusDto manualsHelp;
  final WorkspaceCapabilityStatusDto releaseEvidence;
  final WorkspaceCapabilityStatusDto adminControlPlane;
  final WorkspaceCapabilityStatusDto weaver;

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
      weaver: weaver.toCapabilityState(WorkspaceCapability.weaver),
    );
  }

  static Map<String, dynamic> _readOptionalNestedJson(
    Map<String, dynamic> json,
    String key, {
    required bool enabled,
    required String readiness,
    required String policyState,
    required String memberImpact,
  }) {
    if (json[key] is Map<String, dynamic>) {
      return _readNestedJson(json, key);
    }
    return <String, dynamic>{
      'enabled': enabled,
      'readiness': readiness,
      'policyState': policyState,
      'memberImpact': memberImpact,
    };
  }

  static Map<String, dynamic> _readNestedJson(
    Map<String, dynamic> json,
    String key,
  ) {
    final value = json[key];
    if (value is Map<String, dynamic>) {
      return value;
    }

    throw AppFailure.unknown(
      'The backend returned an invalid workspace capabilities response.',
      cause: 'Expected an object for "$key".',
    );
  }
}

class WorkspaceCapabilityStatusDto {
  const WorkspaceCapabilityStatusDto({
    required this.enabled,
    required this.readiness,
    this.policyState = 'unavailable',
    this.grantedCapabilities = const <String>[],
    this.profileKey,
    this.memberImpact,
  });

  factory WorkspaceCapabilityStatusDto.fromJson(Map<String, dynamic> json) {
    final enabled = json['enabled'];
    final readiness = json['readiness'];

    if (enabled is! bool || readiness is! String) {
      throw const AppFailure.unknown(
        'The backend returned an invalid workspace capability item.',
      );
    }

    return WorkspaceCapabilityStatusDto(
      enabled: enabled,
      readiness: readiness,
      policyState: json['policyState'] is String
          ? json['policyState'] as String
          : (enabled ? 'allowed' : 'disabled'),
      profileKey: json['profileKey'] is String
          ? json['profileKey'] as String
          : null,
      memberImpact: json['memberImpact'] is String
          ? json['memberImpact'] as String
          : null,
      grantedCapabilities: _readStringList(json['grantedCapabilities']),
    );
  }

  final bool enabled;
  final String readiness;
  final String policyState;
  final String? profileKey;
  final String? memberImpact;
  final List<String> grantedCapabilities;

  WorkspaceCapabilityState toCapabilityState(WorkspaceCapability capability) {
    return WorkspaceCapabilityState(
      capability: capability,
      readiness: enabled
          ? _parseReadiness(readiness)
          : WorkspaceCapabilityReadiness.unavailable,
      policyState: _parsePolicyState(policyState),
      profileKey: profileKey,
      memberImpact: memberImpact,
      grantedCapabilities: grantedCapabilities,
    );
  }

  static List<String> _readStringList(Object? value) {
    if (value is! List<dynamic>) {
      return const <String>[];
    }
    return value.whereType<String>().toList(growable: false);
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
