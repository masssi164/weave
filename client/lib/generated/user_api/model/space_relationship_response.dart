//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//
// @dart=2.18

// ignore_for_file: unused_element, unused_import
// ignore_for_file: always_put_required_named_parameters_first
// ignore_for_file: constant_identifier_names
// ignore_for_file: lines_longer_than_80_chars

part of weave_user_api;

class SpaceRelationshipResponse {
  /// Returns a new [SpaceRelationshipResponse] instance.
  SpaceRelationshipResponse({
    required this.relationKind,
    required this.relationRef,
    required this.targetKind,
    required this.targetRef,
  });

  SpaceRelationshipResponseRelationKindEnum relationKind;

  String relationRef;

  SpaceRelationshipResponseTargetKindEnum targetKind;

  String targetRef;

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is SpaceRelationshipResponse &&
          other.relationKind == relationKind &&
          other.relationRef == relationRef &&
          other.targetKind == targetKind &&
          other.targetRef == targetRef;

  @override
  int get hashCode =>
      // ignore: unnecessary_parenthesis
      (relationKind.hashCode) +
      (relationRef.hashCode) +
      (targetKind.hashCode) +
      (targetRef.hashCode);

  @override
  String toString() =>
      'SpaceRelationshipResponse[relationKind=$relationKind, relationRef=$relationRef, targetKind=$targetKind, targetRef=$targetRef]';

  Map<String, dynamic> toJson() {
    final json = <String, dynamic>{};
    json[r'relationKind'] = this.relationKind;
    json[r'relationRef'] = this.relationRef;
    json[r'targetKind'] = this.targetKind;
    json[r'targetRef'] = this.targetRef;
    return json;
  }

  /// Returns a new [SpaceRelationshipResponse] instance and imports its values from
  /// [value] if it's a [Map], null otherwise.
  // ignore: prefer_constructors_over_static_methods
  static SpaceRelationshipResponse? fromJson(dynamic value) {
    if (value is Map) {
      final json = value.cast<String, dynamic>();

      // Ensure that the map contains the required keys.
      // Note 1: the values aren't checked for validity beyond being non-null.
      // Note 2: this code is stripped in release mode!
      assert(() {
        requiredKeys.forEach((key) {
          assert(json.containsKey(key),
              'Required key "SpaceRelationshipResponse[$key]" is missing from JSON.');
          assert(json[key] != null,
              'Required key "SpaceRelationshipResponse[$key]" has a null value in JSON.');
        });
        return true;
      }());

      return SpaceRelationshipResponse(
        relationKind: SpaceRelationshipResponseRelationKindEnum.fromJson(
            json[r'relationKind'])!,
        relationRef: mapValueOfType<String>(json, r'relationRef')!,
        targetKind: SpaceRelationshipResponseTargetKindEnum.fromJson(
            json[r'targetKind'])!,
        targetRef: mapValueOfType<String>(json, r'targetRef')!,
      );
    }
    return null;
  }

  static List<SpaceRelationshipResponse> listFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final result = <SpaceRelationshipResponse>[];
    if (json is List && json.isNotEmpty) {
      for (final row in json) {
        final value = SpaceRelationshipResponse.fromJson(row);
        if (value != null) {
          result.add(value);
        }
      }
    }
    return result.toList(growable: growable);
  }

  static Map<String, SpaceRelationshipResponse> mapFromJson(dynamic json) {
    final map = <String, SpaceRelationshipResponse>{};
    if (json is Map && json.isNotEmpty) {
      json = json.cast<String, dynamic>(); // ignore: parameter_assignments
      for (final entry in json.entries) {
        final value = SpaceRelationshipResponse.fromJson(entry.value);
        if (value != null) {
          map[entry.key] = value;
        }
      }
    }
    return map;
  }

  // maps a json object with a list of SpaceRelationshipResponse-objects as value to a dart map
  static Map<String, List<SpaceRelationshipResponse>> mapListFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final map = <String, List<SpaceRelationshipResponse>>{};
    if (json is Map && json.isNotEmpty) {
      // ignore: parameter_assignments
      json = json.cast<String, dynamic>();
      for (final entry in json.entries) {
        map[entry.key] = SpaceRelationshipResponse.listFromJson(
          entry.value,
          growable: growable,
        );
      }
    }
    return map;
  }

  /// The list of required keys that must be present in a JSON.
  static const requiredKeys = <String>{
    'relationKind',
    'relationRef',
    'targetKind',
    'targetRef',
  };
}

class SpaceRelationshipResponseRelationKindEnum {
  /// Instantiate a new enum with the provided [value].
  const SpaceRelationshipResponseRelationKindEnum._(this.value);

  /// The underlying value of this enum member.
  final String value;

  @override
  String toString() => value;

  String toJson() => value;

  static const CONTAINS =
      SpaceRelationshipResponseRelationKindEnum._(r'CONTAINS');

  /// List of all possible values in this [enum][SpaceRelationshipResponseRelationKindEnum].
  static const values = <SpaceRelationshipResponseRelationKindEnum>[
    CONTAINS,
  ];

  static SpaceRelationshipResponseRelationKindEnum? fromJson(dynamic value) =>
      SpaceRelationshipResponseRelationKindEnumTypeTransformer().decode(value);

  static List<SpaceRelationshipResponseRelationKindEnum> listFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final result = <SpaceRelationshipResponseRelationKindEnum>[];
    if (json is List && json.isNotEmpty) {
      for (final row in json) {
        final value = SpaceRelationshipResponseRelationKindEnum.fromJson(row);
        if (value != null) {
          result.add(value);
        }
      }
    }
    return result.toList(growable: growable);
  }
}

/// Transformation class that can [encode] an instance of [SpaceRelationshipResponseRelationKindEnum] to String,
/// and [decode] dynamic data back to [SpaceRelationshipResponseRelationKindEnum].
class SpaceRelationshipResponseRelationKindEnumTypeTransformer {
  factory SpaceRelationshipResponseRelationKindEnumTypeTransformer() =>
      _instance ??=
          const SpaceRelationshipResponseRelationKindEnumTypeTransformer._();

  const SpaceRelationshipResponseRelationKindEnumTypeTransformer._();

  String encode(SpaceRelationshipResponseRelationKindEnum data) => data.value;

  /// Decodes a [dynamic value][data] to a SpaceRelationshipResponseRelationKindEnum.
  ///
  /// If [allowNull] is true and the [dynamic value][data] cannot be decoded successfully,
  /// then null is returned. However, if [allowNull] is false and the [dynamic value][data]
  /// cannot be decoded successfully, then an [UnimplementedError] is thrown.
  ///
  /// The [allowNull] is very handy when an API changes and a new enum value is added or removed,
  /// and users are still using an old app with the old code.
  SpaceRelationshipResponseRelationKindEnum? decode(dynamic data,
      {bool allowNull = true}) {
    if (data != null) {
      switch (data) {
        case r'CONTAINS':
          return SpaceRelationshipResponseRelationKindEnum.CONTAINS;
        default:
          if (!allowNull) {
            throw ArgumentError('Unknown enum value to decode: $data');
          }
      }
    }
    return null;
  }

  /// Singleton [SpaceRelationshipResponseRelationKindEnumTypeTransformer] instance.
  static SpaceRelationshipResponseRelationKindEnumTypeTransformer? _instance;
}

class SpaceRelationshipResponseTargetKindEnum {
  /// Instantiate a new enum with the provided [value].
  const SpaceRelationshipResponseTargetKindEnum._(this.value);

  /// The underlying value of this enum member.
  final String value;

  @override
  String toString() => value;

  String toJson() => value;

  static const FILE = SpaceRelationshipResponseTargetKindEnum._(r'FILE');
  static const EVENT = SpaceRelationshipResponseTargetKindEnum._(r'EVENT');
  static const ROOM = SpaceRelationshipResponseTargetKindEnum._(r'ROOM');

  /// List of all possible values in this [enum][SpaceRelationshipResponseTargetKindEnum].
  static const values = <SpaceRelationshipResponseTargetKindEnum>[
    FILE,
    EVENT,
    ROOM,
  ];

  static SpaceRelationshipResponseTargetKindEnum? fromJson(dynamic value) =>
      SpaceRelationshipResponseTargetKindEnumTypeTransformer().decode(value);

  static List<SpaceRelationshipResponseTargetKindEnum> listFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final result = <SpaceRelationshipResponseTargetKindEnum>[];
    if (json is List && json.isNotEmpty) {
      for (final row in json) {
        final value = SpaceRelationshipResponseTargetKindEnum.fromJson(row);
        if (value != null) {
          result.add(value);
        }
      }
    }
    return result.toList(growable: growable);
  }
}

/// Transformation class that can [encode] an instance of [SpaceRelationshipResponseTargetKindEnum] to String,
/// and [decode] dynamic data back to [SpaceRelationshipResponseTargetKindEnum].
class SpaceRelationshipResponseTargetKindEnumTypeTransformer {
  factory SpaceRelationshipResponseTargetKindEnumTypeTransformer() =>
      _instance ??=
          const SpaceRelationshipResponseTargetKindEnumTypeTransformer._();

  const SpaceRelationshipResponseTargetKindEnumTypeTransformer._();

  String encode(SpaceRelationshipResponseTargetKindEnum data) => data.value;

  /// Decodes a [dynamic value][data] to a SpaceRelationshipResponseTargetKindEnum.
  ///
  /// If [allowNull] is true and the [dynamic value][data] cannot be decoded successfully,
  /// then null is returned. However, if [allowNull] is false and the [dynamic value][data]
  /// cannot be decoded successfully, then an [UnimplementedError] is thrown.
  ///
  /// The [allowNull] is very handy when an API changes and a new enum value is added or removed,
  /// and users are still using an old app with the old code.
  SpaceRelationshipResponseTargetKindEnum? decode(dynamic data,
      {bool allowNull = true}) {
    if (data != null) {
      switch (data) {
        case r'FILE':
          return SpaceRelationshipResponseTargetKindEnum.FILE;
        case r'EVENT':
          return SpaceRelationshipResponseTargetKindEnum.EVENT;
        case r'ROOM':
          return SpaceRelationshipResponseTargetKindEnum.ROOM;
        default:
          if (!allowNull) {
            throw ArgumentError('Unknown enum value to decode: $data');
          }
      }
    }
    return null;
  }

  /// Singleton [SpaceRelationshipResponseTargetKindEnumTypeTransformer] instance.
  static SpaceRelationshipResponseTargetKindEnumTypeTransformer? _instance;
}
