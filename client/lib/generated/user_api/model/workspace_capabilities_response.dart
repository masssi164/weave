//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//
// @dart=2.18

// ignore_for_file: unused_element, unused_import
// ignore_for_file: always_put_required_named_parameters_first
// ignore_for_file: constant_identifier_names
// ignore_for_file: lines_longer_than_80_chars

part of weave_user_api;

class WorkspaceCapabilitiesResponse {
  /// Returns a new [WorkspaceCapabilitiesResponse] instance.
  WorkspaceCapabilitiesResponse({
    required this.adminControlPlane,
    required this.agentRuntimeControl,
    required this.boards,
    required this.calendar,
    required this.chat,
    required this.decisionsEvidence,
    required this.documentsCollaboration,
    required this.files,
    required this.manualsHelp,
    required this.meetingsCalls,
    required this.releaseEvidence,
    required this.shellAccess,
  });

  WorkspaceCapabilityStatusResponse adminControlPlane;

  WorkspaceCapabilityStatusResponse agentRuntimeControl;

  WorkspaceCapabilityStatusResponse boards;

  WorkspaceCapabilityStatusResponse calendar;

  WorkspaceCapabilityStatusResponse chat;

  WorkspaceCapabilityStatusResponse decisionsEvidence;

  WorkspaceCapabilityStatusResponse documentsCollaboration;

  WorkspaceCapabilityStatusResponse files;

  WorkspaceCapabilityStatusResponse manualsHelp;

  WorkspaceCapabilityStatusResponse meetingsCalls;

  WorkspaceCapabilityStatusResponse releaseEvidence;

  WorkspaceCapabilityStatusResponse shellAccess;

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is WorkspaceCapabilitiesResponse &&
          other.adminControlPlane == adminControlPlane &&
          other.agentRuntimeControl == agentRuntimeControl &&
          other.boards == boards &&
          other.calendar == calendar &&
          other.chat == chat &&
          other.decisionsEvidence == decisionsEvidence &&
          other.documentsCollaboration == documentsCollaboration &&
          other.files == files &&
          other.manualsHelp == manualsHelp &&
          other.meetingsCalls == meetingsCalls &&
          other.releaseEvidence == releaseEvidence &&
          other.shellAccess == shellAccess;

  @override
  int get hashCode =>
      // ignore: unnecessary_parenthesis
      (adminControlPlane.hashCode) +
      (agentRuntimeControl.hashCode) +
      (boards.hashCode) +
      (calendar.hashCode) +
      (chat.hashCode) +
      (decisionsEvidence.hashCode) +
      (documentsCollaboration.hashCode) +
      (files.hashCode) +
      (manualsHelp.hashCode) +
      (meetingsCalls.hashCode) +
      (releaseEvidence.hashCode) +
      (shellAccess.hashCode);

  @override
  String toString() =>
      'WorkspaceCapabilitiesResponse[adminControlPlane=$adminControlPlane, agentRuntimeControl=$agentRuntimeControl, boards=$boards, calendar=$calendar, chat=$chat, decisionsEvidence=$decisionsEvidence, documentsCollaboration=$documentsCollaboration, files=$files, manualsHelp=$manualsHelp, meetingsCalls=$meetingsCalls, releaseEvidence=$releaseEvidence, shellAccess=$shellAccess]';

  Map<String, dynamic> toJson() {
    final json = <String, dynamic>{};
    json[r'adminControlPlane'] = this.adminControlPlane;
    json[r'agentRuntimeControl'] = this.agentRuntimeControl;
    json[r'boards'] = this.boards;
    json[r'calendar'] = this.calendar;
    json[r'chat'] = this.chat;
    json[r'decisionsEvidence'] = this.decisionsEvidence;
    json[r'documentsCollaboration'] = this.documentsCollaboration;
    json[r'files'] = this.files;
    json[r'manualsHelp'] = this.manualsHelp;
    json[r'meetingsCalls'] = this.meetingsCalls;
    json[r'releaseEvidence'] = this.releaseEvidence;
    json[r'shellAccess'] = this.shellAccess;
    return json;
  }

  /// Returns a new [WorkspaceCapabilitiesResponse] instance and imports its values from
  /// [value] if it's a [Map], null otherwise.
  // ignore: prefer_constructors_over_static_methods
  static WorkspaceCapabilitiesResponse? fromJson(dynamic value) {
    if (value is Map) {
      final json = value.cast<String, dynamic>();

      // Ensure that the map contains the required keys.
      // Note 1: the values aren't checked for validity beyond being non-null.
      // Note 2: this code is stripped in release mode!
      assert(() {
        requiredKeys.forEach((key) {
          assert(json.containsKey(key),
              'Required key "WorkspaceCapabilitiesResponse[$key]" is missing from JSON.');
          assert(json[key] != null,
              'Required key "WorkspaceCapabilitiesResponse[$key]" has a null value in JSON.');
        });
        return true;
      }());

      return WorkspaceCapabilitiesResponse(
        adminControlPlane: WorkspaceCapabilityStatusResponse.fromJson(
            json[r'adminControlPlane'])!,
        agentRuntimeControl: WorkspaceCapabilityStatusResponse.fromJson(
            json[r'agentRuntimeControl'])!,
        boards: WorkspaceCapabilityStatusResponse.fromJson(json[r'boards'])!,
        calendar:
            WorkspaceCapabilityStatusResponse.fromJson(json[r'calendar'])!,
        chat: WorkspaceCapabilityStatusResponse.fromJson(json[r'chat'])!,
        decisionsEvidence: WorkspaceCapabilityStatusResponse.fromJson(
            json[r'decisionsEvidence'])!,
        documentsCollaboration: WorkspaceCapabilityStatusResponse.fromJson(
            json[r'documentsCollaboration'])!,
        files: WorkspaceCapabilityStatusResponse.fromJson(json[r'files'])!,
        manualsHelp:
            WorkspaceCapabilityStatusResponse.fromJson(json[r'manualsHelp'])!,
        meetingsCalls:
            WorkspaceCapabilityStatusResponse.fromJson(json[r'meetingsCalls'])!,
        releaseEvidence: WorkspaceCapabilityStatusResponse.fromJson(
            json[r'releaseEvidence'])!,
        shellAccess:
            WorkspaceCapabilityStatusResponse.fromJson(json[r'shellAccess'])!,
      );
    }
    return null;
  }

  static List<WorkspaceCapabilitiesResponse> listFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final result = <WorkspaceCapabilitiesResponse>[];
    if (json is List && json.isNotEmpty) {
      for (final row in json) {
        final value = WorkspaceCapabilitiesResponse.fromJson(row);
        if (value != null) {
          result.add(value);
        }
      }
    }
    return result.toList(growable: growable);
  }

  static Map<String, WorkspaceCapabilitiesResponse> mapFromJson(dynamic json) {
    final map = <String, WorkspaceCapabilitiesResponse>{};
    if (json is Map && json.isNotEmpty) {
      json = json.cast<String, dynamic>(); // ignore: parameter_assignments
      for (final entry in json.entries) {
        final value = WorkspaceCapabilitiesResponse.fromJson(entry.value);
        if (value != null) {
          map[entry.key] = value;
        }
      }
    }
    return map;
  }

  // maps a json object with a list of WorkspaceCapabilitiesResponse-objects as value to a dart map
  static Map<String, List<WorkspaceCapabilitiesResponse>> mapListFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final map = <String, List<WorkspaceCapabilitiesResponse>>{};
    if (json is Map && json.isNotEmpty) {
      // ignore: parameter_assignments
      json = json.cast<String, dynamic>();
      for (final entry in json.entries) {
        map[entry.key] = WorkspaceCapabilitiesResponse.listFromJson(
          entry.value,
          growable: growable,
        );
      }
    }
    return map;
  }

  /// The list of required keys that must be present in a JSON.
  static const requiredKeys = <String>{
    'adminControlPlane',
    'agentRuntimeControl',
    'boards',
    'calendar',
    'chat',
    'decisionsEvidence',
    'documentsCollaboration',
    'files',
    'manualsHelp',
    'meetingsCalls',
    'releaseEvidence',
    'shellAccess',
  };
}
