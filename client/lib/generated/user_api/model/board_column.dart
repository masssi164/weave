//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//
// @dart=2.18

// ignore_for_file: unused_element, unused_import
// ignore_for_file: always_put_required_named_parameters_first
// ignore_for_file: constant_identifier_names
// ignore_for_file: lines_longer_than_80_chars

part of weave_user_api;

class BoardColumn {
  /// Returns a new [BoardColumn] instance.
  BoardColumn({
    this.boardId,
    this.id,
    this.name,
    this.position,
    this.providerRefs = const [],
    this.semanticStatus,
    this.wipLimit,
  });

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? boardId;

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
  int? position;

  List<ProviderRef> providerRefs;

  BoardColumnSemanticStatusEnum? semanticStatus;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  int? wipLimit;

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is BoardColumn &&
          other.boardId == boardId &&
          other.id == id &&
          other.name == name &&
          other.position == position &&
          _deepEquality.equals(other.providerRefs, providerRefs) &&
          other.semanticStatus == semanticStatus &&
          other.wipLimit == wipLimit;

  @override
  int get hashCode =>
      // ignore: unnecessary_parenthesis
      (boardId == null ? 0 : boardId!.hashCode) +
      (id == null ? 0 : id!.hashCode) +
      (name == null ? 0 : name!.hashCode) +
      (position == null ? 0 : position!.hashCode) +
      (providerRefs.hashCode) +
      (semanticStatus == null ? 0 : semanticStatus!.hashCode) +
      (wipLimit == null ? 0 : wipLimit!.hashCode);

  @override
  String toString() =>
      'BoardColumn[boardId=$boardId, id=$id, name=$name, position=$position, providerRefs=$providerRefs, semanticStatus=$semanticStatus, wipLimit=$wipLimit]';

  Map<String, dynamic> toJson() {
    final json = <String, dynamic>{};
    if (this.boardId != null) {
      json[r'boardId'] = this.boardId;
    } else {
      json[r'boardId'] = null;
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
    if (this.position != null) {
      json[r'position'] = this.position;
    } else {
      json[r'position'] = null;
    }
    json[r'providerRefs'] = this.providerRefs;
    if (this.semanticStatus != null) {
      json[r'semanticStatus'] = this.semanticStatus;
    } else {
      json[r'semanticStatus'] = null;
    }
    if (this.wipLimit != null) {
      json[r'wipLimit'] = this.wipLimit;
    } else {
      json[r'wipLimit'] = null;
    }
    return json;
  }

  /// Returns a new [BoardColumn] instance and imports its values from
  /// [value] if it's a [Map], null otherwise.
  // ignore: prefer_constructors_over_static_methods
  static BoardColumn? fromJson(dynamic value) {
    if (value is Map) {
      final json = value.cast<String, dynamic>();

      // Ensure that the map contains the required keys.
      // Note 1: the values aren't checked for validity beyond being non-null.
      // Note 2: this code is stripped in release mode!
      assert(() {
        requiredKeys.forEach((key) {
          assert(json.containsKey(key),
              'Required key "BoardColumn[$key]" is missing from JSON.');
          assert(json[key] != null,
              'Required key "BoardColumn[$key]" has a null value in JSON.');
        });
        return true;
      }());

      return BoardColumn(
        boardId: mapValueOfType<String>(json, r'boardId'),
        id: mapValueOfType<String>(json, r'id'),
        name: mapValueOfType<String>(json, r'name'),
        position: mapValueOfType<int>(json, r'position'),
        providerRefs: ProviderRef.listFromJson(json[r'providerRefs']),
        semanticStatus:
            BoardColumnSemanticStatusEnum.fromJson(json[r'semanticStatus']),
        wipLimit: mapValueOfType<int>(json, r'wipLimit'),
      );
    }
    return null;
  }

  static List<BoardColumn> listFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final result = <BoardColumn>[];
    if (json is List && json.isNotEmpty) {
      for (final row in json) {
        final value = BoardColumn.fromJson(row);
        if (value != null) {
          result.add(value);
        }
      }
    }
    return result.toList(growable: growable);
  }

  static Map<String, BoardColumn> mapFromJson(dynamic json) {
    final map = <String, BoardColumn>{};
    if (json is Map && json.isNotEmpty) {
      json = json.cast<String, dynamic>(); // ignore: parameter_assignments
      for (final entry in json.entries) {
        final value = BoardColumn.fromJson(entry.value);
        if (value != null) {
          map[entry.key] = value;
        }
      }
    }
    return map;
  }

  // maps a json object with a list of BoardColumn-objects as value to a dart map
  static Map<String, List<BoardColumn>> mapListFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final map = <String, List<BoardColumn>>{};
    if (json is Map && json.isNotEmpty) {
      // ignore: parameter_assignments
      json = json.cast<String, dynamic>();
      for (final entry in json.entries) {
        map[entry.key] = BoardColumn.listFromJson(
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

class BoardColumnSemanticStatusEnum {
  /// Instantiate a new enum with the provided [value].
  const BoardColumnSemanticStatusEnum._(this.value);

  /// The underlying value of this enum member.
  final String value;

  @override
  String toString() => value;

  String toJson() => value;

  static const notStarted = BoardColumnSemanticStatusEnum._(r'not_started');
  static const inProgress = BoardColumnSemanticStatusEnum._(r'in_progress');
  static const blocked = BoardColumnSemanticStatusEnum._(r'blocked');
  static const done = BoardColumnSemanticStatusEnum._(r'done');
  static const archived = BoardColumnSemanticStatusEnum._(r'archived');

  /// List of all possible values in this [enum][BoardColumnSemanticStatusEnum].
  static const values = <BoardColumnSemanticStatusEnum>[
    notStarted,
    inProgress,
    blocked,
    done,
    archived,
  ];

  static BoardColumnSemanticStatusEnum? fromJson(dynamic value) =>
      BoardColumnSemanticStatusEnumTypeTransformer().decode(value);

  static List<BoardColumnSemanticStatusEnum> listFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final result = <BoardColumnSemanticStatusEnum>[];
    if (json is List && json.isNotEmpty) {
      for (final row in json) {
        final value = BoardColumnSemanticStatusEnum.fromJson(row);
        if (value != null) {
          result.add(value);
        }
      }
    }
    return result.toList(growable: growable);
  }
}

/// Transformation class that can [encode] an instance of [BoardColumnSemanticStatusEnum] to String,
/// and [decode] dynamic data back to [BoardColumnSemanticStatusEnum].
class BoardColumnSemanticStatusEnumTypeTransformer {
  factory BoardColumnSemanticStatusEnumTypeTransformer() =>
      _instance ??= const BoardColumnSemanticStatusEnumTypeTransformer._();

  const BoardColumnSemanticStatusEnumTypeTransformer._();

  String encode(BoardColumnSemanticStatusEnum data) => data.value;

  /// Decodes a [dynamic value][data] to a BoardColumnSemanticStatusEnum.
  ///
  /// If [allowNull] is true and the [dynamic value][data] cannot be decoded successfully,
  /// then null is returned. However, if [allowNull] is false and the [dynamic value][data]
  /// cannot be decoded successfully, then an [UnimplementedError] is thrown.
  ///
  /// The [allowNull] is very handy when an API changes and a new enum value is added or removed,
  /// and users are still using an old app with the old code.
  BoardColumnSemanticStatusEnum? decode(dynamic data, {bool allowNull = true}) {
    if (data != null) {
      switch (data) {
        case r'not_started':
          return BoardColumnSemanticStatusEnum.notStarted;
        case r'in_progress':
          return BoardColumnSemanticStatusEnum.inProgress;
        case r'blocked':
          return BoardColumnSemanticStatusEnum.blocked;
        case r'done':
          return BoardColumnSemanticStatusEnum.done;
        case r'archived':
          return BoardColumnSemanticStatusEnum.archived;
        default:
          if (!allowNull) {
            throw ArgumentError('Unknown enum value to decode: $data');
          }
      }
    }
    return null;
  }

  /// Singleton [BoardColumnSemanticStatusEnumTypeTransformer] instance.
  static BoardColumnSemanticStatusEnumTypeTransformer? _instance;
}
