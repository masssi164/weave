//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//
// @dart=2.18

// ignore_for_file: unused_element, unused_import
// ignore_for_file: always_put_required_named_parameters_first
// ignore_for_file: constant_identifier_names
// ignore_for_file: lines_longer_than_80_chars

part of weave_user_api;

class WorkspaceHomeResponse {
  /// Returns a new [WorkspaceHomeResponse] instance.
  WorkspaceHomeResponse({
    this.actions = const [],
    this.readiness,
    this.recentActivity = const [],
    this.sections = const [],
    this.summary,
    this.supportSafe,
    this.version,
  });

  /// Actionable follow-ups in priority order.
  List<WorkspaceHomeActionResponse> actions;

  /// Overall readiness of the daily work loop.
  WorkspaceHomeResponseReadinessEnum? readiness;

  /// Newest completed support-safe activities the current member may view.
  List<WorkspaceHomeRecentActivityResponse> recentActivity;

  /// Daily work sections shown by Weave Home.
  List<WorkspaceHomeSectionResponse> sections;

  /// Support-safe summary of the workspace day.
  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? summary;

  /// True when the payload is intentionally free of raw provider URLs, IDs, filenames, usernames, and secrets.
  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  bool? supportSafe;

  /// Stable schema version for client compatibility.
  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  int? version;

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is WorkspaceHomeResponse &&
          _deepEquality.equals(other.actions, actions) &&
          other.readiness == readiness &&
          _deepEquality.equals(other.recentActivity, recentActivity) &&
          _deepEquality.equals(other.sections, sections) &&
          other.summary == summary &&
          other.supportSafe == supportSafe &&
          other.version == version;

  @override
  int get hashCode =>
      // ignore: unnecessary_parenthesis
      (actions.hashCode) +
      (readiness == null ? 0 : readiness!.hashCode) +
      (recentActivity.hashCode) +
      (sections.hashCode) +
      (summary == null ? 0 : summary!.hashCode) +
      (supportSafe == null ? 0 : supportSafe!.hashCode) +
      (version == null ? 0 : version!.hashCode);

  @override
  String toString() =>
      'WorkspaceHomeResponse[actions=$actions, readiness=$readiness, recentActivity=$recentActivity, sections=$sections, summary=$summary, supportSafe=$supportSafe, version=$version]';

  Map<String, dynamic> toJson() {
    final json = <String, dynamic>{};
    json[r'actions'] = this.actions;
    if (this.readiness != null) {
      json[r'readiness'] = this.readiness;
    } else {
      json[r'readiness'] = null;
    }
    json[r'recentActivity'] = this.recentActivity;
    json[r'sections'] = this.sections;
    if (this.summary != null) {
      json[r'summary'] = this.summary;
    } else {
      json[r'summary'] = null;
    }
    if (this.supportSafe != null) {
      json[r'supportSafe'] = this.supportSafe;
    } else {
      json[r'supportSafe'] = null;
    }
    if (this.version != null) {
      json[r'version'] = this.version;
    } else {
      json[r'version'] = null;
    }
    return json;
  }

  /// Returns a new [WorkspaceHomeResponse] instance and imports its values from
  /// [value] if it's a [Map], null otherwise.
  // ignore: prefer_constructors_over_static_methods
  static WorkspaceHomeResponse? fromJson(dynamic value) {
    if (value is Map) {
      final json = value.cast<String, dynamic>();

      // Ensure that the map contains the required keys.
      // Note 1: the values aren't checked for validity beyond being non-null.
      // Note 2: this code is stripped in release mode!
      assert(() {
        requiredKeys.forEach((key) {
          assert(json.containsKey(key),
              'Required key "WorkspaceHomeResponse[$key]" is missing from JSON.');
          assert(json[key] != null,
              'Required key "WorkspaceHomeResponse[$key]" has a null value in JSON.');
        });
        return true;
      }());

      return WorkspaceHomeResponse(
        actions: WorkspaceHomeActionResponse.listFromJson(json[r'actions']),
        readiness:
            WorkspaceHomeResponseReadinessEnum.fromJson(json[r'readiness']),
        recentActivity: WorkspaceHomeRecentActivityResponse.listFromJson(
            json[r'recentActivity']),
        sections: WorkspaceHomeSectionResponse.listFromJson(json[r'sections']),
        summary: mapValueOfType<String>(json, r'summary'),
        supportSafe: mapValueOfType<bool>(json, r'supportSafe'),
        version: mapValueOfType<int>(json, r'version'),
      );
    }
    return null;
  }

  static List<WorkspaceHomeResponse> listFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final result = <WorkspaceHomeResponse>[];
    if (json is List && json.isNotEmpty) {
      for (final row in json) {
        final value = WorkspaceHomeResponse.fromJson(row);
        if (value != null) {
          result.add(value);
        }
      }
    }
    return result.toList(growable: growable);
  }

  static Map<String, WorkspaceHomeResponse> mapFromJson(dynamic json) {
    final map = <String, WorkspaceHomeResponse>{};
    if (json is Map && json.isNotEmpty) {
      json = json.cast<String, dynamic>(); // ignore: parameter_assignments
      for (final entry in json.entries) {
        final value = WorkspaceHomeResponse.fromJson(entry.value);
        if (value != null) {
          map[entry.key] = value;
        }
      }
    }
    return map;
  }

  // maps a json object with a list of WorkspaceHomeResponse-objects as value to a dart map
  static Map<String, List<WorkspaceHomeResponse>> mapListFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final map = <String, List<WorkspaceHomeResponse>>{};
    if (json is Map && json.isNotEmpty) {
      // ignore: parameter_assignments
      json = json.cast<String, dynamic>();
      for (final entry in json.entries) {
        map[entry.key] = WorkspaceHomeResponse.listFromJson(
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

/// Overall readiness of the daily work loop.
class WorkspaceHomeResponseReadinessEnum {
  /// Instantiate a new enum with the provided [value].
  const WorkspaceHomeResponseReadinessEnum._(this.value);

  /// The underlying value of this enum member.
  final String value;

  @override
  String toString() => value;

  String toJson() => value;

  static const ready = WorkspaceHomeResponseReadinessEnum._(r'ready');
  static const degraded = WorkspaceHomeResponseReadinessEnum._(r'degraded');
  static const blocked = WorkspaceHomeResponseReadinessEnum._(r'blocked');
  static const unavailable =
      WorkspaceHomeResponseReadinessEnum._(r'unavailable');

  /// List of all possible values in this [enum][WorkspaceHomeResponseReadinessEnum].
  static const values = <WorkspaceHomeResponseReadinessEnum>[
    ready,
    degraded,
    blocked,
    unavailable,
  ];

  static WorkspaceHomeResponseReadinessEnum? fromJson(dynamic value) =>
      WorkspaceHomeResponseReadinessEnumTypeTransformer().decode(value);

  static List<WorkspaceHomeResponseReadinessEnum> listFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final result = <WorkspaceHomeResponseReadinessEnum>[];
    if (json is List && json.isNotEmpty) {
      for (final row in json) {
        final value = WorkspaceHomeResponseReadinessEnum.fromJson(row);
        if (value != null) {
          result.add(value);
        }
      }
    }
    return result.toList(growable: growable);
  }
}

/// Transformation class that can [encode] an instance of [WorkspaceHomeResponseReadinessEnum] to String,
/// and [decode] dynamic data back to [WorkspaceHomeResponseReadinessEnum].
class WorkspaceHomeResponseReadinessEnumTypeTransformer {
  factory WorkspaceHomeResponseReadinessEnumTypeTransformer() =>
      _instance ??= const WorkspaceHomeResponseReadinessEnumTypeTransformer._();

  const WorkspaceHomeResponseReadinessEnumTypeTransformer._();

  String encode(WorkspaceHomeResponseReadinessEnum data) => data.value;

  /// Decodes a [dynamic value][data] to a WorkspaceHomeResponseReadinessEnum.
  ///
  /// If [allowNull] is true and the [dynamic value][data] cannot be decoded successfully,
  /// then null is returned. However, if [allowNull] is false and the [dynamic value][data]
  /// cannot be decoded successfully, then an [UnimplementedError] is thrown.
  ///
  /// The [allowNull] is very handy when an API changes and a new enum value is added or removed,
  /// and users are still using an old app with the old code.
  WorkspaceHomeResponseReadinessEnum? decode(dynamic data,
      {bool allowNull = true}) {
    if (data != null) {
      switch (data) {
        case r'ready':
          return WorkspaceHomeResponseReadinessEnum.ready;
        case r'degraded':
          return WorkspaceHomeResponseReadinessEnum.degraded;
        case r'blocked':
          return WorkspaceHomeResponseReadinessEnum.blocked;
        case r'unavailable':
          return WorkspaceHomeResponseReadinessEnum.unavailable;
        default:
          if (!allowNull) {
            throw ArgumentError('Unknown enum value to decode: $data');
          }
      }
    }
    return null;
  }

  /// Singleton [WorkspaceHomeResponseReadinessEnumTypeTransformer] instance.
  static WorkspaceHomeResponseReadinessEnumTypeTransformer? _instance;
}
