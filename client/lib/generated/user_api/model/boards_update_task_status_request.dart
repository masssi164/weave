//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//
// @dart=2.18

// ignore_for_file: unused_element, unused_import
// ignore_for_file: always_put_required_named_parameters_first
// ignore_for_file: constant_identifier_names
// ignore_for_file: lines_longer_than_80_chars

part of weave_user_api;

class BoardsUpdateTaskStatusRequest {
  /// Returns a new [BoardsUpdateTaskStatusRequest] instance.
  BoardsUpdateTaskStatusRequest({
    this.status,
    this.targetColumnId,
  });

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? status;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? targetColumnId;

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is BoardsUpdateTaskStatusRequest &&
          other.status == status &&
          other.targetColumnId == targetColumnId;

  @override
  int get hashCode =>
      // ignore: unnecessary_parenthesis
      (status == null ? 0 : status!.hashCode) +
      (targetColumnId == null ? 0 : targetColumnId!.hashCode);

  @override
  String toString() =>
      'BoardsUpdateTaskStatusRequest[status=$status, targetColumnId=$targetColumnId]';

  Map<String, dynamic> toJson() {
    final json = <String, dynamic>{};
    if (this.status != null) {
      json[r'status'] = this.status;
    } else {
      json[r'status'] = null;
    }
    if (this.targetColumnId != null) {
      json[r'targetColumnId'] = this.targetColumnId;
    } else {
      json[r'targetColumnId'] = null;
    }
    return json;
  }

  /// Returns a new [BoardsUpdateTaskStatusRequest] instance and imports its values from
  /// [value] if it's a [Map], null otherwise.
  // ignore: prefer_constructors_over_static_methods
  static BoardsUpdateTaskStatusRequest? fromJson(dynamic value) {
    if (value is Map) {
      final json = value.cast<String, dynamic>();

      // Ensure that the map contains the required keys.
      // Note 1: the values aren't checked for validity beyond being non-null.
      // Note 2: this code is stripped in release mode!
      assert(() {
        requiredKeys.forEach((key) {
          assert(json.containsKey(key),
              'Required key "BoardsUpdateTaskStatusRequest[$key]" is missing from JSON.');
          assert(json[key] != null,
              'Required key "BoardsUpdateTaskStatusRequest[$key]" has a null value in JSON.');
        });
        return true;
      }());

      return BoardsUpdateTaskStatusRequest(
        status: mapValueOfType<String>(json, r'status'),
        targetColumnId: mapValueOfType<String>(json, r'targetColumnId'),
      );
    }
    return null;
  }

  static List<BoardsUpdateTaskStatusRequest> listFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final result = <BoardsUpdateTaskStatusRequest>[];
    if (json is List && json.isNotEmpty) {
      for (final row in json) {
        final value = BoardsUpdateTaskStatusRequest.fromJson(row);
        if (value != null) {
          result.add(value);
        }
      }
    }
    return result.toList(growable: growable);
  }

  static Map<String, BoardsUpdateTaskStatusRequest> mapFromJson(dynamic json) {
    final map = <String, BoardsUpdateTaskStatusRequest>{};
    if (json is Map && json.isNotEmpty) {
      json = json.cast<String, dynamic>(); // ignore: parameter_assignments
      for (final entry in json.entries) {
        final value = BoardsUpdateTaskStatusRequest.fromJson(entry.value);
        if (value != null) {
          map[entry.key] = value;
        }
      }
    }
    return map;
  }

  // maps a json object with a list of BoardsUpdateTaskStatusRequest-objects as value to a dart map
  static Map<String, List<BoardsUpdateTaskStatusRequest>> mapListFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final map = <String, List<BoardsUpdateTaskStatusRequest>>{};
    if (json is Map && json.isNotEmpty) {
      // ignore: parameter_assignments
      json = json.cast<String, dynamic>();
      for (final entry in json.entries) {
        map[entry.key] = BoardsUpdateTaskStatusRequest.listFromJson(
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
