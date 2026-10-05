//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//
// @dart=2.18

// ignore_for_file: unused_element, unused_import
// ignore_for_file: always_put_required_named_parameters_first
// ignore_for_file: constant_identifier_names
// ignore_for_file: lines_longer_than_80_chars

part of weave_user_api;

class BoardsWorkspaceResponse {
  /// Returns a new [BoardsWorkspaceResponse] instance.
  BoardsWorkspaceResponse({
    this.boards = const [],
    this.capabilities,
    this.projects = const [],
    this.releaseStatus,
    this.source_,
    this.syncMetadata,
    this.tasks = const [],
    this.workspace,
  });

  List<Board> boards;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  BoardProviderCapabilities? capabilities;

  List<WeaveProject> projects;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? releaseStatus;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? source_;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  BoardsSyncMetadataResponse? syncMetadata;

  List<TaskItem> tasks;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  bool? workspace;

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is BoardsWorkspaceResponse &&
          _deepEquality.equals(other.boards, boards) &&
          other.capabilities == capabilities &&
          _deepEquality.equals(other.projects, projects) &&
          other.releaseStatus == releaseStatus &&
          other.source_ == source_ &&
          other.syncMetadata == syncMetadata &&
          _deepEquality.equals(other.tasks, tasks) &&
          other.workspace == workspace;

  @override
  int get hashCode =>
      // ignore: unnecessary_parenthesis
      (boards.hashCode) +
      (capabilities == null ? 0 : capabilities!.hashCode) +
      (projects.hashCode) +
      (releaseStatus == null ? 0 : releaseStatus!.hashCode) +
      (source_ == null ? 0 : source_!.hashCode) +
      (syncMetadata == null ? 0 : syncMetadata!.hashCode) +
      (tasks.hashCode) +
      (workspace == null ? 0 : workspace!.hashCode);

  @override
  String toString() =>
      'BoardsWorkspaceResponse[boards=$boards, capabilities=$capabilities, projects=$projects, releaseStatus=$releaseStatus, source_=$source_, syncMetadata=$syncMetadata, tasks=$tasks, workspace=$workspace]';

  Map<String, dynamic> toJson() {
    final json = <String, dynamic>{};
    json[r'boards'] = this.boards;
    if (this.capabilities != null) {
      json[r'capabilities'] = this.capabilities;
    } else {
      json[r'capabilities'] = null;
    }
    json[r'projects'] = this.projects;
    if (this.releaseStatus != null) {
      json[r'releaseStatus'] = this.releaseStatus;
    } else {
      json[r'releaseStatus'] = null;
    }
    if (this.source_ != null) {
      json[r'source'] = this.source_;
    } else {
      json[r'source'] = null;
    }
    if (this.syncMetadata != null) {
      json[r'syncMetadata'] = this.syncMetadata;
    } else {
      json[r'syncMetadata'] = null;
    }
    json[r'tasks'] = this.tasks;
    if (this.workspace != null) {
      json[r'workspace'] = this.workspace;
    } else {
      json[r'workspace'] = null;
    }
    return json;
  }

  /// Returns a new [BoardsWorkspaceResponse] instance and imports its values from
  /// [value] if it's a [Map], null otherwise.
  // ignore: prefer_constructors_over_static_methods
  static BoardsWorkspaceResponse? fromJson(dynamic value) {
    if (value is Map) {
      final json = value.cast<String, dynamic>();

      // Ensure that the map contains the required keys.
      // Note 1: the values aren't checked for validity beyond being non-null.
      // Note 2: this code is stripped in release mode!
      assert(() {
        requiredKeys.forEach((key) {
          assert(json.containsKey(key),
              'Required key "BoardsWorkspaceResponse[$key]" is missing from JSON.');
          assert(json[key] != null,
              'Required key "BoardsWorkspaceResponse[$key]" has a null value in JSON.');
        });
        return true;
      }());

      return BoardsWorkspaceResponse(
        boards: Board.listFromJson(json[r'boards']),
        capabilities: BoardProviderCapabilities.fromJson(json[r'capabilities']),
        projects: WeaveProject.listFromJson(json[r'projects']),
        releaseStatus: mapValueOfType<String>(json, r'releaseStatus'),
        source_: mapValueOfType<String>(json, r'source'),
        syncMetadata:
            BoardsSyncMetadataResponse.fromJson(json[r'syncMetadata']),
        tasks: TaskItem.listFromJson(json[r'tasks']),
        workspace: mapValueOfType<bool>(json, r'workspace'),
      );
    }
    return null;
  }

  static List<BoardsWorkspaceResponse> listFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final result = <BoardsWorkspaceResponse>[];
    if (json is List && json.isNotEmpty) {
      for (final row in json) {
        final value = BoardsWorkspaceResponse.fromJson(row);
        if (value != null) {
          result.add(value);
        }
      }
    }
    return result.toList(growable: growable);
  }

  static Map<String, BoardsWorkspaceResponse> mapFromJson(dynamic json) {
    final map = <String, BoardsWorkspaceResponse>{};
    if (json is Map && json.isNotEmpty) {
      json = json.cast<String, dynamic>(); // ignore: parameter_assignments
      for (final entry in json.entries) {
        final value = BoardsWorkspaceResponse.fromJson(entry.value);
        if (value != null) {
          map[entry.key] = value;
        }
      }
    }
    return map;
  }

  // maps a json object with a list of BoardsWorkspaceResponse-objects as value to a dart map
  static Map<String, List<BoardsWorkspaceResponse>> mapListFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final map = <String, List<BoardsWorkspaceResponse>>{};
    if (json is Map && json.isNotEmpty) {
      // ignore: parameter_assignments
      json = json.cast<String, dynamic>();
      for (final entry in json.entries) {
        map[entry.key] = BoardsWorkspaceResponse.listFromJson(
          entry.value,
          growable: growable,
        );
      }
    }
    return map;
  }

  /// The list of required keys that must be present in a JSON.
  static const requiredKeys = <String>{};
}
