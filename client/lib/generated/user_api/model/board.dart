//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//
// @dart=2.18

// ignore_for_file: unused_element, unused_import
// ignore_for_file: always_put_required_named_parameters_first
// ignore_for_file: constant_identifier_names
// ignore_for_file: lines_longer_than_80_chars

part of weave_user_api;

class Board {
  /// Returns a new [Board] instance.
  Board({
    this.archived,
    this.columns = const [],
    this.description,
    this.id,
    this.name,
    this.projectId,
    this.providerRefs = const [],
  });

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  bool? archived;

  List<BoardColumn> columns;

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
  String? id;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? name;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? projectId;

  List<ProviderRef> providerRefs;

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is Board &&
          other.archived == archived &&
          _deepEquality.equals(other.columns, columns) &&
          other.description == description &&
          other.id == id &&
          other.name == name &&
          other.projectId == projectId &&
          _deepEquality.equals(other.providerRefs, providerRefs);

  @override
  int get hashCode =>
      // ignore: unnecessary_parenthesis
      (archived == null ? 0 : archived!.hashCode) +
      (columns.hashCode) +
      (description == null ? 0 : description!.hashCode) +
      (id == null ? 0 : id!.hashCode) +
      (name == null ? 0 : name!.hashCode) +
      (projectId == null ? 0 : projectId!.hashCode) +
      (providerRefs.hashCode);

  @override
  String toString() =>
      'Board[archived=$archived, columns=$columns, description=$description, id=$id, name=$name, projectId=$projectId, providerRefs=$providerRefs]';

  Map<String, dynamic> toJson() {
    final json = <String, dynamic>{};
    if (this.archived != null) {
      json[r'archived'] = this.archived;
    } else {
      json[r'archived'] = null;
    }
    json[r'columns'] = this.columns;
    if (this.description != null) {
      json[r'description'] = this.description;
    } else {
      json[r'description'] = null;
    }
    if (this.id != null) {
      json[r'id'] = this.id;
    } else {
      json[r'id'] = null;
    }
    if (this.name != null) {
      json[r'name'] = this.name;
    } else {
      json[r'name'] = null;
    }
    if (this.projectId != null) {
      json[r'projectId'] = this.projectId;
    } else {
      json[r'projectId'] = null;
    }
    json[r'providerRefs'] = this.providerRefs;
    return json;
  }

  /// Returns a new [Board] instance and imports its values from
  /// [value] if it's a [Map], null otherwise.
  // ignore: prefer_constructors_over_static_methods
  static Board? fromJson(dynamic value) {
    if (value is Map) {
      final json = value.cast<String, dynamic>();

      // Ensure that the map contains the required keys.
      // Note 1: the values aren't checked for validity beyond being non-null.
      // Note 2: this code is stripped in release mode!
      assert(() {
        requiredKeys.forEach((key) {
          assert(json.containsKey(key),
              'Required key "Board[$key]" is missing from JSON.');
          assert(json[key] != null,
              'Required key "Board[$key]" has a null value in JSON.');
        });
        return true;
      }());

      return Board(
        archived: mapValueOfType<bool>(json, r'archived'),
        columns: BoardColumn.listFromJson(json[r'columns']),
        description: mapValueOfType<String>(json, r'description'),
        id: mapValueOfType<String>(json, r'id'),
        name: mapValueOfType<String>(json, r'name'),
        projectId: mapValueOfType<String>(json, r'projectId'),
        providerRefs: ProviderRef.listFromJson(json[r'providerRefs']),
      );
    }
    return null;
  }

  static List<Board> listFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final result = <Board>[];
    if (json is List && json.isNotEmpty) {
      for (final row in json) {
        final value = Board.fromJson(row);
        if (value != null) {
          result.add(value);
        }
      }
    }
    return result.toList(growable: growable);
  }

  static Map<String, Board> mapFromJson(dynamic json) {
    final map = <String, Board>{};
    if (json is Map && json.isNotEmpty) {
      json = json.cast<String, dynamic>(); // ignore: parameter_assignments
      for (final entry in json.entries) {
        final value = Board.fromJson(entry.value);
        if (value != null) {
          map[entry.key] = value;
        }
      }
    }
    return map;
  }

  // maps a json object with a list of Board-objects as value to a dart map
  static Map<String, List<Board>> mapListFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final map = <String, List<Board>>{};
    if (json is Map && json.isNotEmpty) {
      // ignore: parameter_assignments
      json = json.cast<String, dynamic>();
      for (final entry in json.entries) {
        map[entry.key] = Board.listFromJson(
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
