//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//
// @dart=2.18

// ignore_for_file: unused_element, unused_import
// ignore_for_file: always_put_required_named_parameters_first
// ignore_for_file: constant_identifier_names
// ignore_for_file: lines_longer_than_80_chars

part of weave_user_api;

class WorkspaceReleaseReadinessResponse {
  /// Returns a new [WorkspaceReleaseReadinessResponse] instance.
  WorkspaceReleaseReadinessResponse({
    this.actions = const [],
    this.checks = const [],
    this.readiness,
    this.summary,
  });

  /// Outstanding operator actions extracted from the failing checks.
  List<String> actions;

  /// Readiness checks for the core product slice.
  List<WorkspaceReleaseReadinessCheckResponse> checks;

  /// Overall workspace readiness for the currently configured workspace.
  WorkspaceReleaseReadinessResponseReadinessEnum? readiness;

  /// Short summary of the current release posture.
  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? summary;

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is WorkspaceReleaseReadinessResponse &&
          _deepEquality.equals(other.actions, actions) &&
          _deepEquality.equals(other.checks, checks) &&
          other.readiness == readiness &&
          other.summary == summary;

  @override
  int get hashCode =>
      // ignore: unnecessary_parenthesis
      (actions.hashCode) +
      (checks.hashCode) +
      (readiness == null ? 0 : readiness!.hashCode) +
      (summary == null ? 0 : summary!.hashCode);

  @override
  String toString() =>
      'WorkspaceReleaseReadinessResponse[actions=$actions, checks=$checks, readiness=$readiness, summary=$summary]';

  Map<String, dynamic> toJson() {
    final json = <String, dynamic>{};
    json[r'actions'] = this.actions;
    json[r'checks'] = this.checks;
    if (this.readiness != null) {
      json[r'readiness'] = this.readiness;
    } else {
      json[r'readiness'] = null;
    }
    if (this.summary != null) {
      json[r'summary'] = this.summary;
    } else {
      json[r'summary'] = null;
    }
    return json;
  }

  /// Returns a new [WorkspaceReleaseReadinessResponse] instance and imports its values from
  /// [value] if it's a [Map], null otherwise.
  // ignore: prefer_constructors_over_static_methods
  static WorkspaceReleaseReadinessResponse? fromJson(dynamic value) {
    if (value is Map) {
      final json = value.cast<String, dynamic>();

      // Ensure that the map contains the required keys.
      // Note 1: the values aren't checked for validity beyond being non-null.
      // Note 2: this code is stripped in release mode!
      assert(() {
        requiredKeys.forEach((key) {
          assert(json.containsKey(key),
              'Required key "WorkspaceReleaseReadinessResponse[$key]" is missing from JSON.');
          assert(json[key] != null,
              'Required key "WorkspaceReleaseReadinessResponse[$key]" has a null value in JSON.');
        });
        return true;
      }());

      return WorkspaceReleaseReadinessResponse(
        actions: json[r'actions'] is Iterable
            ? (json[r'actions'] as Iterable)
                .cast<String>()
                .toList(growable: false)
            : const [],
        checks: WorkspaceReleaseReadinessCheckResponse.listFromJson(
            json[r'checks']),
        readiness: WorkspaceReleaseReadinessResponseReadinessEnum.fromJson(
            json[r'readiness']),
        summary: mapValueOfType<String>(json, r'summary'),
      );
    }
    return null;
  }

  static List<WorkspaceReleaseReadinessResponse> listFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final result = <WorkspaceReleaseReadinessResponse>[];
    if (json is List && json.isNotEmpty) {
      for (final row in json) {
        final value = WorkspaceReleaseReadinessResponse.fromJson(row);
        if (value != null) {
          result.add(value);
        }
      }
    }
    return result.toList(growable: growable);
  }

  static Map<String, WorkspaceReleaseReadinessResponse> mapFromJson(
      dynamic json) {
    final map = <String, WorkspaceReleaseReadinessResponse>{};
    if (json is Map && json.isNotEmpty) {
      json = json.cast<String, dynamic>(); // ignore: parameter_assignments
      for (final entry in json.entries) {
        final value = WorkspaceReleaseReadinessResponse.fromJson(entry.value);
        if (value != null) {
          map[entry.key] = value;
        }
      }
    }
    return map;
  }

  // maps a json object with a list of WorkspaceReleaseReadinessResponse-objects as value to a dart map
  static Map<String, List<WorkspaceReleaseReadinessResponse>> mapListFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final map = <String, List<WorkspaceReleaseReadinessResponse>>{};
    if (json is Map && json.isNotEmpty) {
      // ignore: parameter_assignments
      json = json.cast<String, dynamic>();
      for (final entry in json.entries) {
        map[entry.key] = WorkspaceReleaseReadinessResponse.listFromJson(
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

/// Overall workspace readiness for the currently configured workspace.
class WorkspaceReleaseReadinessResponseReadinessEnum {
  /// Instantiate a new enum with the provided [value].
  const WorkspaceReleaseReadinessResponseReadinessEnum._(this.value);

  /// The underlying value of this enum member.
  final String value;

  @override
  String toString() => value;

  String toJson() => value;

  static const ready =
      WorkspaceReleaseReadinessResponseReadinessEnum._(r'ready');
  static const degraded =
      WorkspaceReleaseReadinessResponseReadinessEnum._(r'degraded');
  static const blocked =
      WorkspaceReleaseReadinessResponseReadinessEnum._(r'blocked');
  static const unavailable =
      WorkspaceReleaseReadinessResponseReadinessEnum._(r'unavailable');

  /// List of all possible values in this [enum][WorkspaceReleaseReadinessResponseReadinessEnum].
  static const values = <WorkspaceReleaseReadinessResponseReadinessEnum>[
    ready,
    degraded,
    blocked,
    unavailable,
  ];

  static WorkspaceReleaseReadinessResponseReadinessEnum? fromJson(
          dynamic value) =>
      WorkspaceReleaseReadinessResponseReadinessEnumTypeTransformer()
          .decode(value);

  static List<WorkspaceReleaseReadinessResponseReadinessEnum> listFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final result = <WorkspaceReleaseReadinessResponseReadinessEnum>[];
    if (json is List && json.isNotEmpty) {
      for (final row in json) {
        final value =
            WorkspaceReleaseReadinessResponseReadinessEnum.fromJson(row);
        if (value != null) {
          result.add(value);
        }
      }
    }
    return result.toList(growable: growable);
  }
}

/// Transformation class that can [encode] an instance of [WorkspaceReleaseReadinessResponseReadinessEnum] to String,
/// and [decode] dynamic data back to [WorkspaceReleaseReadinessResponseReadinessEnum].
class WorkspaceReleaseReadinessResponseReadinessEnumTypeTransformer {
  factory WorkspaceReleaseReadinessResponseReadinessEnumTypeTransformer() =>
      _instance ??=
          const WorkspaceReleaseReadinessResponseReadinessEnumTypeTransformer
              ._();

  const WorkspaceReleaseReadinessResponseReadinessEnumTypeTransformer._();

  String encode(WorkspaceReleaseReadinessResponseReadinessEnum data) =>
      data.value;

  /// Decodes a [dynamic value][data] to a WorkspaceReleaseReadinessResponseReadinessEnum.
  ///
  /// If [allowNull] is true and the [dynamic value][data] cannot be decoded successfully,
  /// then null is returned. However, if [allowNull] is false and the [dynamic value][data]
  /// cannot be decoded successfully, then an [UnimplementedError] is thrown.
  ///
  /// The [allowNull] is very handy when an API changes and a new enum value is added or removed,
  /// and users are still using an old app with the old code.
  WorkspaceReleaseReadinessResponseReadinessEnum? decode(dynamic data,
      {bool allowNull = true}) {
    if (data != null) {
      switch (data) {
        case r'ready':
          return WorkspaceReleaseReadinessResponseReadinessEnum.ready;
        case r'degraded':
          return WorkspaceReleaseReadinessResponseReadinessEnum.degraded;
        case r'blocked':
          return WorkspaceReleaseReadinessResponseReadinessEnum.blocked;
        case r'unavailable':
          return WorkspaceReleaseReadinessResponseReadinessEnum.unavailable;
        default:
          if (!allowNull) {
            throw ArgumentError('Unknown enum value to decode: $data');
          }
      }
    }
    return null;
  }

  /// Singleton [WorkspaceReleaseReadinessResponseReadinessEnumTypeTransformer] instance.
  static WorkspaceReleaseReadinessResponseReadinessEnumTypeTransformer?
      _instance;
}
