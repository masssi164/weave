//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//
// @dart=2.18

// ignore_for_file: unused_element, unused_import
// ignore_for_file: always_put_required_named_parameters_first
// ignore_for_file: constant_identifier_names
// ignore_for_file: lines_longer_than_80_chars

part of weave_user_api;

class WorkspaceReleaseReadinessCheckResponse {
  /// Returns a new [WorkspaceReleaseReadinessCheckResponse] instance.
  WorkspaceReleaseReadinessCheckResponse({
    this.action,
    this.key,
    this.label,
    this.message,
    this.readiness,
  });

  /// Recommended operator action when the check is not ready.
  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? action;

  /// Stable check identifier.
  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? key;

  /// Human-readable check label.
  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? label;

  /// Why this check is in its current state.
  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? message;

  /// Current readiness state for this check.
  WorkspaceReleaseReadinessCheckResponseReadinessEnum? readiness;

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is WorkspaceReleaseReadinessCheckResponse &&
          other.action == action &&
          other.key == key &&
          other.label == label &&
          other.message == message &&
          other.readiness == readiness;

  @override
  int get hashCode =>
      // ignore: unnecessary_parenthesis
      (action == null ? 0 : action!.hashCode) +
      (key == null ? 0 : key!.hashCode) +
      (label == null ? 0 : label!.hashCode) +
      (message == null ? 0 : message!.hashCode) +
      (readiness == null ? 0 : readiness!.hashCode);

  @override
  String toString() =>
      'WorkspaceReleaseReadinessCheckResponse[action=$action, key=$key, label=$label, message=$message, readiness=$readiness]';

  Map<String, dynamic> toJson() {
    final json = <String, dynamic>{};
    if (this.action != null) {
      json[r'action'] = this.action;
    } else {
      json[r'action'] = null;
    }
    if (this.key != null) {
      json[r'key'] = this.key;
    } else {
      json[r'key'] = null;
    }
    if (this.label != null) {
      json[r'label'] = this.label;
    } else {
      json[r'label'] = null;
    }
    if (this.message != null) {
      json[r'message'] = this.message;
    } else {
      json[r'message'] = null;
    }
    if (this.readiness != null) {
      json[r'readiness'] = this.readiness;
    } else {
      json[r'readiness'] = null;
    }
    return json;
  }

  /// Returns a new [WorkspaceReleaseReadinessCheckResponse] instance and imports its values from
  /// [value] if it's a [Map], null otherwise.
  // ignore: prefer_constructors_over_static_methods
  static WorkspaceReleaseReadinessCheckResponse? fromJson(dynamic value) {
    if (value is Map) {
      final json = value.cast<String, dynamic>();

      // Ensure that the map contains the required keys.
      // Note 1: the values aren't checked for validity beyond being non-null.
      // Note 2: this code is stripped in release mode!
      assert(() {
        requiredKeys.forEach((key) {
          assert(json.containsKey(key),
              'Required key "WorkspaceReleaseReadinessCheckResponse[$key]" is missing from JSON.');
          assert(json[key] != null,
              'Required key "WorkspaceReleaseReadinessCheckResponse[$key]" has a null value in JSON.');
        });
        return true;
      }());

      return WorkspaceReleaseReadinessCheckResponse(
        action: mapValueOfType<String>(json, r'action'),
        key: mapValueOfType<String>(json, r'key'),
        label: mapValueOfType<String>(json, r'label'),
        message: mapValueOfType<String>(json, r'message'),
        readiness: WorkspaceReleaseReadinessCheckResponseReadinessEnum.fromJson(
            json[r'readiness']),
      );
    }
    return null;
  }

  static List<WorkspaceReleaseReadinessCheckResponse> listFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final result = <WorkspaceReleaseReadinessCheckResponse>[];
    if (json is List && json.isNotEmpty) {
      for (final row in json) {
        final value = WorkspaceReleaseReadinessCheckResponse.fromJson(row);
        if (value != null) {
          result.add(value);
        }
      }
    }
    return result.toList(growable: growable);
  }

  static Map<String, WorkspaceReleaseReadinessCheckResponse> mapFromJson(
      dynamic json) {
    final map = <String, WorkspaceReleaseReadinessCheckResponse>{};
    if (json is Map && json.isNotEmpty) {
      json = json.cast<String, dynamic>(); // ignore: parameter_assignments
      for (final entry in json.entries) {
        final value =
            WorkspaceReleaseReadinessCheckResponse.fromJson(entry.value);
        if (value != null) {
          map[entry.key] = value;
        }
      }
    }
    return map;
  }

  // maps a json object with a list of WorkspaceReleaseReadinessCheckResponse-objects as value to a dart map
  static Map<String, List<WorkspaceReleaseReadinessCheckResponse>>
      mapListFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final map = <String, List<WorkspaceReleaseReadinessCheckResponse>>{};
    if (json is Map && json.isNotEmpty) {
      // ignore: parameter_assignments
      json = json.cast<String, dynamic>();
      for (final entry in json.entries) {
        map[entry.key] = WorkspaceReleaseReadinessCheckResponse.listFromJson(
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

/// Current readiness state for this check.
class WorkspaceReleaseReadinessCheckResponseReadinessEnum {
  /// Instantiate a new enum with the provided [value].
  const WorkspaceReleaseReadinessCheckResponseReadinessEnum._(this.value);

  /// The underlying value of this enum member.
  final String value;

  @override
  String toString() => value;

  String toJson() => value;

  static const ready =
      WorkspaceReleaseReadinessCheckResponseReadinessEnum._(r'ready');
  static const degraded =
      WorkspaceReleaseReadinessCheckResponseReadinessEnum._(r'degraded');
  static const blocked =
      WorkspaceReleaseReadinessCheckResponseReadinessEnum._(r'blocked');
  static const unavailable =
      WorkspaceReleaseReadinessCheckResponseReadinessEnum._(r'unavailable');

  /// List of all possible values in this [enum][WorkspaceReleaseReadinessCheckResponseReadinessEnum].
  static const values = <WorkspaceReleaseReadinessCheckResponseReadinessEnum>[
    ready,
    degraded,
    blocked,
    unavailable,
  ];

  static WorkspaceReleaseReadinessCheckResponseReadinessEnum? fromJson(
          dynamic value) =>
      WorkspaceReleaseReadinessCheckResponseReadinessEnumTypeTransformer()
          .decode(value);

  static List<WorkspaceReleaseReadinessCheckResponseReadinessEnum> listFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final result = <WorkspaceReleaseReadinessCheckResponseReadinessEnum>[];
    if (json is List && json.isNotEmpty) {
      for (final row in json) {
        final value =
            WorkspaceReleaseReadinessCheckResponseReadinessEnum.fromJson(row);
        if (value != null) {
          result.add(value);
        }
      }
    }
    return result.toList(growable: growable);
  }
}

/// Transformation class that can [encode] an instance of [WorkspaceReleaseReadinessCheckResponseReadinessEnum] to String,
/// and [decode] dynamic data back to [WorkspaceReleaseReadinessCheckResponseReadinessEnum].
class WorkspaceReleaseReadinessCheckResponseReadinessEnumTypeTransformer {
  factory WorkspaceReleaseReadinessCheckResponseReadinessEnumTypeTransformer() =>
      _instance ??=
          const WorkspaceReleaseReadinessCheckResponseReadinessEnumTypeTransformer
              ._();

  const WorkspaceReleaseReadinessCheckResponseReadinessEnumTypeTransformer._();

  String encode(WorkspaceReleaseReadinessCheckResponseReadinessEnum data) =>
      data.value;

  /// Decodes a [dynamic value][data] to a WorkspaceReleaseReadinessCheckResponseReadinessEnum.
  ///
  /// If [allowNull] is true and the [dynamic value][data] cannot be decoded successfully,
  /// then null is returned. However, if [allowNull] is false and the [dynamic value][data]
  /// cannot be decoded successfully, then an [UnimplementedError] is thrown.
  ///
  /// The [allowNull] is very handy when an API changes and a new enum value is added or removed,
  /// and users are still using an old app with the old code.
  WorkspaceReleaseReadinessCheckResponseReadinessEnum? decode(dynamic data,
      {bool allowNull = true}) {
    if (data != null) {
      switch (data) {
        case r'ready':
          return WorkspaceReleaseReadinessCheckResponseReadinessEnum.ready;
        case r'degraded':
          return WorkspaceReleaseReadinessCheckResponseReadinessEnum.degraded;
        case r'blocked':
          return WorkspaceReleaseReadinessCheckResponseReadinessEnum.blocked;
        case r'unavailable':
          return WorkspaceReleaseReadinessCheckResponseReadinessEnum
              .unavailable;
        default:
          if (!allowNull) {
            throw ArgumentError('Unknown enum value to decode: $data');
          }
      }
    }
    return null;
  }

  /// Singleton [WorkspaceReleaseReadinessCheckResponseReadinessEnumTypeTransformer] instance.
  static WorkspaceReleaseReadinessCheckResponseReadinessEnumTypeTransformer?
      _instance;
}
