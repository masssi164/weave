//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//
// @dart=2.18

// ignore_for_file: unused_element, unused_import
// ignore_for_file: always_put_required_named_parameters_first
// ignore_for_file: constant_identifier_names
// ignore_for_file: lines_longer_than_80_chars

part of weave_user_api;

class BoardsCreateTaskRequest {
  /// Returns a new [BoardsCreateTaskRequest] instance.
  BoardsCreateTaskRequest({
    this.assigneeRefs = const [],
    required this.columnId,
    this.description,
    this.dueAt,
    this.labelRefs = const [],
    required this.title,
  });

  List<String> assigneeRefs;

  String columnId;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? description;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  DateTime? dueAt;

  List<String> labelRefs;

  String title;

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is BoardsCreateTaskRequest &&
          _deepEquality.equals(other.assigneeRefs, assigneeRefs) &&
          other.columnId == columnId &&
          other.description == description &&
          other.dueAt == dueAt &&
          _deepEquality.equals(other.labelRefs, labelRefs) &&
          other.title == title;

  @override
  int get hashCode =>
      // ignore: unnecessary_parenthesis
      (assigneeRefs.hashCode) +
      (columnId.hashCode) +
      (description == null ? 0 : description!.hashCode) +
      (dueAt == null ? 0 : dueAt!.hashCode) +
      (labelRefs.hashCode) +
      (title.hashCode);

  @override
  String toString() =>
      'BoardsCreateTaskRequest[assigneeRefs=$assigneeRefs, columnId=$columnId, description=$description, dueAt=$dueAt, labelRefs=$labelRefs, title=$title]';

  Map<String, dynamic> toJson() {
    final json = <String, dynamic>{};
    json[r'assigneeRefs'] = this.assigneeRefs;
    json[r'columnId'] = this.columnId;
    if (this.description != null) {
      json[r'description'] = this.description;
    } else {
      json[r'description'] = null;
    }
    if (this.dueAt != null) {
      json[r'dueAt'] = this.dueAt!.toUtc().toIso8601String();
    } else {
      json[r'dueAt'] = null;
    }
    json[r'labelRefs'] = this.labelRefs;
    json[r'title'] = this.title;
    return json;
  }

  /// Returns a new [BoardsCreateTaskRequest] instance and imports its values from
  /// [value] if it's a [Map], null otherwise.
  // ignore: prefer_constructors_over_static_methods
  static BoardsCreateTaskRequest? fromJson(dynamic value) {
    if (value is Map) {
      final json = value.cast<String, dynamic>();

      // Ensure that the map contains the required keys.
      // Note 1: the values aren't checked for validity beyond being non-null.
      // Note 2: this code is stripped in release mode!
      assert(() {
        requiredKeys.forEach((key) {
          assert(json.containsKey(key),
              'Required key "BoardsCreateTaskRequest[$key]" is missing from JSON.');
          assert(json[key] != null,
              'Required key "BoardsCreateTaskRequest[$key]" has a null value in JSON.');
        });
        return true;
      }());

      return BoardsCreateTaskRequest(
        assigneeRefs: json[r'assigneeRefs'] is Iterable
            ? (json[r'assigneeRefs'] as Iterable)
                .cast<String>()
                .toList(growable: false)
            : const [],
        columnId: mapValueOfType<String>(json, r'columnId')!,
        description: mapValueOfType<String>(json, r'description'),
        dueAt: mapDateTime(json, r'dueAt', r''),
        labelRefs: json[r'labelRefs'] is Iterable
            ? (json[r'labelRefs'] as Iterable)
                .cast<String>()
                .toList(growable: false)
            : const [],
        title: mapValueOfType<String>(json, r'title')!,
      );
    }
    return null;
  }

  static List<BoardsCreateTaskRequest> listFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final result = <BoardsCreateTaskRequest>[];
    if (json is List && json.isNotEmpty) {
      for (final row in json) {
        final value = BoardsCreateTaskRequest.fromJson(row);
        if (value != null) {
          result.add(value);
        }
      }
    }
    return result.toList(growable: growable);
  }

  static Map<String, BoardsCreateTaskRequest> mapFromJson(dynamic json) {
    final map = <String, BoardsCreateTaskRequest>{};
    if (json is Map && json.isNotEmpty) {
      json = json.cast<String, dynamic>(); // ignore: parameter_assignments
      for (final entry in json.entries) {
        final value = BoardsCreateTaskRequest.fromJson(entry.value);
        if (value != null) {
          map[entry.key] = value;
        }
      }
    }
    return map;
  }

  // maps a json object with a list of BoardsCreateTaskRequest-objects as value to a dart map
  static Map<String, List<BoardsCreateTaskRequest>> mapListFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final map = <String, List<BoardsCreateTaskRequest>>{};
    if (json is Map && json.isNotEmpty) {
      // ignore: parameter_assignments
      json = json.cast<String, dynamic>();
      for (final entry in json.entries) {
        map[entry.key] = BoardsCreateTaskRequest.listFromJson(
          entry.value,
          growable: growable,
        );
      }
    }
    return map;
  }

  /// The list of required keys that must be present in a JSON.
  static const requiredKeys = <String>{
    'columnId',
    'title',
  };
}
