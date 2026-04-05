import 'package:weave/core/failures/app_failure.dart';
import 'package:weave/features/app/domain/entities/workspace_capability_snapshot.dart';

class WorkspaceCapabilitiesResponseDto {
  const WorkspaceCapabilitiesResponseDto({
    required this.shellAccess,
    required this.chat,
    required this.files,
    required this.calendar,
    required this.boards,
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
    );
  }

  final WorkspaceCapabilityStatusDto shellAccess;
  final WorkspaceCapabilityStatusDto chat;
  final WorkspaceCapabilityStatusDto files;
  final WorkspaceCapabilityStatusDto calendar;
  final WorkspaceCapabilityStatusDto boards;

  WorkspaceCapabilitySnapshot toSnapshot() {
    return WorkspaceCapabilitySnapshot(
      shellAccess: shellAccess.toCapabilityState(
        WorkspaceCapability.shellAccess,
      ),
      chat: chat.toCapabilityState(WorkspaceCapability.chat),
      files: files.toCapabilityState(WorkspaceCapability.files),
      calendar: calendar.toCapabilityState(WorkspaceCapability.calendar),
      boards: boards.toCapabilityState(WorkspaceCapability.boards),
    );
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
  });

  factory WorkspaceCapabilityStatusDto.fromJson(Map<String, dynamic> json) {
    final enabled = json['enabled'];
    final readiness = json['readiness'];

    if (enabled is! bool || readiness is! String) {
      throw AppFailure.unknown(
        'The backend returned an invalid workspace capability item.',
      );
    }

    return WorkspaceCapabilityStatusDto(enabled: enabled, readiness: readiness);
  }

  final bool enabled;
  final String readiness;

  WorkspaceCapabilityState toCapabilityState(WorkspaceCapability capability) {
    return WorkspaceCapabilityState(
      capability: capability,
      readiness: enabled
          ? _parseReadiness(readiness)
          : WorkspaceCapabilityReadiness.unavailable,
    );
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
