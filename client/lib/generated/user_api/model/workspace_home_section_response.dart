//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//
// @dart=2.18

// ignore_for_file: unused_element, unused_import
// ignore_for_file: always_put_required_named_parameters_first
// ignore_for_file: constant_identifier_names
// ignore_for_file: lines_longer_than_80_chars

part of weave_user_api;

class WorkspaceHomeSectionResponse {
  /// Returns a new [WorkspaceHomeSectionResponse] instance.
  WorkspaceHomeSectionResponse({
    this.accessible,
    this.itemCount,
    this.key,
    this.productRoute,
    this.readiness,
    this.summary,
    this.title,
  });

  /// Whether the section has a keyboard and screen-reader path.
  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  bool? accessible;

  /// Measured number of currently authorized actionable items. Null means no count was measured; capability availability is not an item count.
  ///
  /// Minimum value: 0
  int? itemCount;

  /// Stable product section key.
  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? key;

  /// Backend-owned product route/surface, not a raw provider URL.
  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? productRoute;

  /// Current product readiness for this section.
  WorkspaceHomeSectionResponseReadinessEnum? readiness;

  /// Support-safe summary without provider identifiers or raw errors.
  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? summary;

  /// Human-readable product label.
  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? title;

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is WorkspaceHomeSectionResponse &&
          other.accessible == accessible &&
          other.itemCount == itemCount &&
          other.key == key &&
          other.productRoute == productRoute &&
          other.readiness == readiness &&
          other.summary == summary &&
          other.title == title;

  @override
  int get hashCode =>
      // ignore: unnecessary_parenthesis
      (accessible == null ? 0 : accessible!.hashCode) +
      (itemCount == null ? 0 : itemCount!.hashCode) +
      (key == null ? 0 : key!.hashCode) +
      (productRoute == null ? 0 : productRoute!.hashCode) +
      (readiness == null ? 0 : readiness!.hashCode) +
      (summary == null ? 0 : summary!.hashCode) +
      (title == null ? 0 : title!.hashCode);

  @override
  String toString() =>
      'WorkspaceHomeSectionResponse[accessible=$accessible, itemCount=$itemCount, key=$key, productRoute=$productRoute, readiness=$readiness, summary=$summary, title=$title]';

  Map<String, dynamic> toJson() {
    final json = <String, dynamic>{};
    if (this.accessible != null) {
      json[r'accessible'] = this.accessible;
    } else {
      json[r'accessible'] = null;
    }
    if (this.itemCount != null) {
      json[r'itemCount'] = this.itemCount;
    } else {
      json[r'itemCount'] = null;
    }
    if (this.key != null) {
      json[r'key'] = this.key;
    } else {
      json[r'key'] = null;
    }
    if (this.productRoute != null) {
      json[r'productRoute'] = this.productRoute;
    } else {
      json[r'productRoute'] = null;
    }
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
    if (this.title != null) {
      json[r'title'] = this.title;
    } else {
      json[r'title'] = null;
    }
    return json;
  }

  /// Returns a new [WorkspaceHomeSectionResponse] instance and imports its values from
  /// [value] if it's a [Map], null otherwise.
  // ignore: prefer_constructors_over_static_methods
  static WorkspaceHomeSectionResponse? fromJson(dynamic value) {
    if (value is Map) {
      final json = value.cast<String, dynamic>();

      // Ensure that the map contains the required keys.
      // Note 1: the values aren't checked for validity beyond being non-null.
      // Note 2: this code is stripped in release mode!
      assert(() {
        requiredKeys.forEach((key) {
          assert(json.containsKey(key),
              'Required key "WorkspaceHomeSectionResponse[$key]" is missing from JSON.');
          assert(json[key] != null,
              'Required key "WorkspaceHomeSectionResponse[$key]" has a null value in JSON.');
        });
        return true;
      }());

      return WorkspaceHomeSectionResponse(
        accessible: mapValueOfType<bool>(json, r'accessible'),
        itemCount: mapValueOfType<int>(json, r'itemCount'),
        key: mapValueOfType<String>(json, r'key'),
        productRoute: mapValueOfType<String>(json, r'productRoute'),
        readiness: WorkspaceHomeSectionResponseReadinessEnum.fromJson(
            json[r'readiness']),
        summary: mapValueOfType<String>(json, r'summary'),
        title: mapValueOfType<String>(json, r'title'),
      );
    }
    return null;
  }

  static List<WorkspaceHomeSectionResponse> listFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final result = <WorkspaceHomeSectionResponse>[];
    if (json is List && json.isNotEmpty) {
      for (final row in json) {
        final value = WorkspaceHomeSectionResponse.fromJson(row);
        if (value != null) {
          result.add(value);
        }
      }
    }
    return result.toList(growable: growable);
  }

  static Map<String, WorkspaceHomeSectionResponse> mapFromJson(dynamic json) {
    final map = <String, WorkspaceHomeSectionResponse>{};
    if (json is Map && json.isNotEmpty) {
      json = json.cast<String, dynamic>(); // ignore: parameter_assignments
      for (final entry in json.entries) {
        final value = WorkspaceHomeSectionResponse.fromJson(entry.value);
        if (value != null) {
          map[entry.key] = value;
        }
      }
    }
    return map;
  }

  // maps a json object with a list of WorkspaceHomeSectionResponse-objects as value to a dart map
  static Map<String, List<WorkspaceHomeSectionResponse>> mapListFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final map = <String, List<WorkspaceHomeSectionResponse>>{};
    if (json is Map && json.isNotEmpty) {
      // ignore: parameter_assignments
      json = json.cast<String, dynamic>();
      for (final entry in json.entries) {
        map[entry.key] = WorkspaceHomeSectionResponse.listFromJson(
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

/// Current product readiness for this section.
class WorkspaceHomeSectionResponseReadinessEnum {
  /// Instantiate a new enum with the provided [value].
  const WorkspaceHomeSectionResponseReadinessEnum._(this.value);

  /// The underlying value of this enum member.
  final String value;

  @override
  String toString() => value;

  String toJson() => value;

  static const ready = WorkspaceHomeSectionResponseReadinessEnum._(r'ready');
  static const degraded =
      WorkspaceHomeSectionResponseReadinessEnum._(r'degraded');
  static const blocked =
      WorkspaceHomeSectionResponseReadinessEnum._(r'blocked');
  static const unavailable =
      WorkspaceHomeSectionResponseReadinessEnum._(r'unavailable');

  /// List of all possible values in this [enum][WorkspaceHomeSectionResponseReadinessEnum].
  static const values = <WorkspaceHomeSectionResponseReadinessEnum>[
    ready,
    degraded,
    blocked,
    unavailable,
  ];

  static WorkspaceHomeSectionResponseReadinessEnum? fromJson(dynamic value) =>
      WorkspaceHomeSectionResponseReadinessEnumTypeTransformer().decode(value);

  static List<WorkspaceHomeSectionResponseReadinessEnum> listFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final result = <WorkspaceHomeSectionResponseReadinessEnum>[];
    if (json is List && json.isNotEmpty) {
      for (final row in json) {
        final value = WorkspaceHomeSectionResponseReadinessEnum.fromJson(row);
        if (value != null) {
          result.add(value);
        }
      }
    }
    return result.toList(growable: growable);
  }
}

/// Transformation class that can [encode] an instance of [WorkspaceHomeSectionResponseReadinessEnum] to String,
/// and [decode] dynamic data back to [WorkspaceHomeSectionResponseReadinessEnum].
class WorkspaceHomeSectionResponseReadinessEnumTypeTransformer {
  factory WorkspaceHomeSectionResponseReadinessEnumTypeTransformer() =>
      _instance ??=
          const WorkspaceHomeSectionResponseReadinessEnumTypeTransformer._();

  const WorkspaceHomeSectionResponseReadinessEnumTypeTransformer._();

  String encode(WorkspaceHomeSectionResponseReadinessEnum data) => data.value;

  /// Decodes a [dynamic value][data] to a WorkspaceHomeSectionResponseReadinessEnum.
  ///
  /// If [allowNull] is true and the [dynamic value][data] cannot be decoded successfully,
  /// then null is returned. However, if [allowNull] is false and the [dynamic value][data]
  /// cannot be decoded successfully, then an [UnimplementedError] is thrown.
  ///
  /// The [allowNull] is very handy when an API changes and a new enum value is added or removed,
  /// and users are still using an old app with the old code.
  WorkspaceHomeSectionResponseReadinessEnum? decode(dynamic data,
      {bool allowNull = true}) {
    if (data != null) {
      switch (data) {
        case r'ready':
          return WorkspaceHomeSectionResponseReadinessEnum.ready;
        case r'degraded':
          return WorkspaceHomeSectionResponseReadinessEnum.degraded;
        case r'blocked':
          return WorkspaceHomeSectionResponseReadinessEnum.blocked;
        case r'unavailable':
          return WorkspaceHomeSectionResponseReadinessEnum.unavailable;
        default:
          if (!allowNull) {
            throw ArgumentError('Unknown enum value to decode: $data');
          }
      }
    }
    return null;
  }

  /// Singleton [WorkspaceHomeSectionResponseReadinessEnumTypeTransformer] instance.
  static WorkspaceHomeSectionResponseReadinessEnumTypeTransformer? _instance;
}
