//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//
// @dart=2.18

// ignore_for_file: unused_element, unused_import
// ignore_for_file: always_put_required_named_parameters_first
// ignore_for_file: constant_identifier_names
// ignore_for_file: lines_longer_than_80_chars

part of weave_user_api;

class WorkspaceHomeRecentActivityResponse {
  /// Returns a new [WorkspaceHomeRecentActivityResponse] instance.
  WorkspaceHomeRecentActivityResponse({
    this.action,
    this.activityRef,
    this.actorIsCurrentUser,
    this.actorRefHash,
    this.domain,
    this.occurredAt,
    this.supportSafe,
    this.visibility,
  });

  /// Canonical completed Weave mutation action.
  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? action;

  /// Stable opaque activity reference; never a provider or resource identifier.
  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? activityRef;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  bool? actorIsCurrentUser;

  /// Tenant-scoped opaque SHA-256 actor reference.
  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? actorRefHash;

  /// Canonical Weave domain.
  WorkspaceHomeRecentActivityResponseDomainEnum? domain;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  DateTime? occurredAt;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  bool? supportSafe;

  /// Canonical visibility without a raw Context identifier.
  WorkspaceHomeRecentActivityResponseVisibilityEnum? visibility;

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is WorkspaceHomeRecentActivityResponse &&
          other.action == action &&
          other.activityRef == activityRef &&
          other.actorIsCurrentUser == actorIsCurrentUser &&
          other.actorRefHash == actorRefHash &&
          other.domain == domain &&
          other.occurredAt == occurredAt &&
          other.supportSafe == supportSafe &&
          other.visibility == visibility;

  @override
  int get hashCode =>
      // ignore: unnecessary_parenthesis
      (action == null ? 0 : action!.hashCode) +
      (activityRef == null ? 0 : activityRef!.hashCode) +
      (actorIsCurrentUser == null ? 0 : actorIsCurrentUser!.hashCode) +
      (actorRefHash == null ? 0 : actorRefHash!.hashCode) +
      (domain == null ? 0 : domain!.hashCode) +
      (occurredAt == null ? 0 : occurredAt!.hashCode) +
      (supportSafe == null ? 0 : supportSafe!.hashCode) +
      (visibility == null ? 0 : visibility!.hashCode);

  @override
  String toString() =>
      'WorkspaceHomeRecentActivityResponse[action=$action, activityRef=$activityRef, actorIsCurrentUser=$actorIsCurrentUser, actorRefHash=$actorRefHash, domain=$domain, occurredAt=$occurredAt, supportSafe=$supportSafe, visibility=$visibility]';

  Map<String, dynamic> toJson() {
    final json = <String, dynamic>{};
    if (this.action != null) {
      json[r'action'] = this.action;
    } else {
      json[r'action'] = null;
    }
    if (this.activityRef != null) {
      json[r'activityRef'] = this.activityRef;
    } else {
      json[r'activityRef'] = null;
    }
    if (this.actorIsCurrentUser != null) {
      json[r'actorIsCurrentUser'] = this.actorIsCurrentUser;
    } else {
      json[r'actorIsCurrentUser'] = null;
    }
    if (this.actorRefHash != null) {
      json[r'actorRefHash'] = this.actorRefHash;
    } else {
      json[r'actorRefHash'] = null;
    }
    if (this.domain != null) {
      json[r'domain'] = this.domain;
    } else {
      json[r'domain'] = null;
    }
    if (this.occurredAt != null) {
      json[r'occurredAt'] = this.occurredAt!.toUtc().toIso8601String();
    } else {
      json[r'occurredAt'] = null;
    }
    if (this.supportSafe != null) {
      json[r'supportSafe'] = this.supportSafe;
    } else {
      json[r'supportSafe'] = null;
    }
    if (this.visibility != null) {
      json[r'visibility'] = this.visibility;
    } else {
      json[r'visibility'] = null;
    }
    return json;
  }

  /// Returns a new [WorkspaceHomeRecentActivityResponse] instance and imports its values from
  /// [value] if it's a [Map], null otherwise.
  // ignore: prefer_constructors_over_static_methods
  static WorkspaceHomeRecentActivityResponse? fromJson(dynamic value) {
    if (value is Map) {
      final json = value.cast<String, dynamic>();

      // Ensure that the map contains the required keys.
      // Note 1: the values aren't checked for validity beyond being non-null.
      // Note 2: this code is stripped in release mode!
      assert(() {
        requiredKeys.forEach((key) {
          assert(json.containsKey(key),
              'Required key "WorkspaceHomeRecentActivityResponse[$key]" is missing from JSON.');
          assert(json[key] != null,
              'Required key "WorkspaceHomeRecentActivityResponse[$key]" has a null value in JSON.');
        });
        return true;
      }());

      return WorkspaceHomeRecentActivityResponse(
        action: mapValueOfType<String>(json, r'action'),
        activityRef: mapValueOfType<String>(json, r'activityRef'),
        actorIsCurrentUser: mapValueOfType<bool>(json, r'actorIsCurrentUser'),
        actorRefHash: mapValueOfType<String>(json, r'actorRefHash'),
        domain: WorkspaceHomeRecentActivityResponseDomainEnum.fromJson(
            json[r'domain']),
        occurredAt: mapDateTime(json, r'occurredAt', r''),
        supportSafe: mapValueOfType<bool>(json, r'supportSafe'),
        visibility: WorkspaceHomeRecentActivityResponseVisibilityEnum.fromJson(
            json[r'visibility']),
      );
    }
    return null;
  }

  static List<WorkspaceHomeRecentActivityResponse> listFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final result = <WorkspaceHomeRecentActivityResponse>[];
    if (json is List && json.isNotEmpty) {
      for (final row in json) {
        final value = WorkspaceHomeRecentActivityResponse.fromJson(row);
        if (value != null) {
          result.add(value);
        }
      }
    }
    return result.toList(growable: growable);
  }

  static Map<String, WorkspaceHomeRecentActivityResponse> mapFromJson(
      dynamic json) {
    final map = <String, WorkspaceHomeRecentActivityResponse>{};
    if (json is Map && json.isNotEmpty) {
      json = json.cast<String, dynamic>(); // ignore: parameter_assignments
      for (final entry in json.entries) {
        final value = WorkspaceHomeRecentActivityResponse.fromJson(entry.value);
        if (value != null) {
          map[entry.key] = value;
        }
      }
    }
    return map;
  }

  // maps a json object with a list of WorkspaceHomeRecentActivityResponse-objects as value to a dart map
  static Map<String, List<WorkspaceHomeRecentActivityResponse>> mapListFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final map = <String, List<WorkspaceHomeRecentActivityResponse>>{};
    if (json is Map && json.isNotEmpty) {
      // ignore: parameter_assignments
      json = json.cast<String, dynamic>();
      for (final entry in json.entries) {
        map[entry.key] = WorkspaceHomeRecentActivityResponse.listFromJson(
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

/// Canonical Weave domain.
class WorkspaceHomeRecentActivityResponseDomainEnum {
  /// Instantiate a new enum with the provided [value].
  const WorkspaceHomeRecentActivityResponseDomainEnum._(this.value);

  /// The underlying value of this enum member.
  final String value;

  @override
  String toString() => value;

  String toJson() => value;

  static const files =
      WorkspaceHomeRecentActivityResponseDomainEnum._(r'files');

  /// List of all possible values in this [enum][WorkspaceHomeRecentActivityResponseDomainEnum].
  static const values = <WorkspaceHomeRecentActivityResponseDomainEnum>[
    files,
  ];

  static WorkspaceHomeRecentActivityResponseDomainEnum? fromJson(
          dynamic value) =>
      WorkspaceHomeRecentActivityResponseDomainEnumTypeTransformer()
          .decode(value);

  static List<WorkspaceHomeRecentActivityResponseDomainEnum> listFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final result = <WorkspaceHomeRecentActivityResponseDomainEnum>[];
    if (json is List && json.isNotEmpty) {
      for (final row in json) {
        final value =
            WorkspaceHomeRecentActivityResponseDomainEnum.fromJson(row);
        if (value != null) {
          result.add(value);
        }
      }
    }
    return result.toList(growable: growable);
  }
}

/// Transformation class that can [encode] an instance of [WorkspaceHomeRecentActivityResponseDomainEnum] to String,
/// and [decode] dynamic data back to [WorkspaceHomeRecentActivityResponseDomainEnum].
class WorkspaceHomeRecentActivityResponseDomainEnumTypeTransformer {
  factory WorkspaceHomeRecentActivityResponseDomainEnumTypeTransformer() =>
      _instance ??=
          const WorkspaceHomeRecentActivityResponseDomainEnumTypeTransformer
              ._();

  const WorkspaceHomeRecentActivityResponseDomainEnumTypeTransformer._();

  String encode(WorkspaceHomeRecentActivityResponseDomainEnum data) =>
      data.value;

  /// Decodes a [dynamic value][data] to a WorkspaceHomeRecentActivityResponseDomainEnum.
  ///
  /// If [allowNull] is true and the [dynamic value][data] cannot be decoded successfully,
  /// then null is returned. However, if [allowNull] is false and the [dynamic value][data]
  /// cannot be decoded successfully, then an [UnimplementedError] is thrown.
  ///
  /// The [allowNull] is very handy when an API changes and a new enum value is added or removed,
  /// and users are still using an old app with the old code.
  WorkspaceHomeRecentActivityResponseDomainEnum? decode(dynamic data,
      {bool allowNull = true}) {
    if (data != null) {
      switch (data) {
        case r'files':
          return WorkspaceHomeRecentActivityResponseDomainEnum.files;
        default:
          if (!allowNull) {
            throw ArgumentError('Unknown enum value to decode: $data');
          }
      }
    }
    return null;
  }

  /// Singleton [WorkspaceHomeRecentActivityResponseDomainEnumTypeTransformer] instance.
  static WorkspaceHomeRecentActivityResponseDomainEnumTypeTransformer?
      _instance;
}

/// Canonical visibility without a raw Context identifier.
class WorkspaceHomeRecentActivityResponseVisibilityEnum {
  /// Instantiate a new enum with the provided [value].
  const WorkspaceHomeRecentActivityResponseVisibilityEnum._(this.value);

  /// The underlying value of this enum member.
  final String value;

  @override
  String toString() => value;

  String toJson() => value;

  static const workspace =
      WorkspaceHomeRecentActivityResponseVisibilityEnum._(r'workspace');

  /// List of all possible values in this [enum][WorkspaceHomeRecentActivityResponseVisibilityEnum].
  static const values = <WorkspaceHomeRecentActivityResponseVisibilityEnum>[
    workspace,
  ];

  static WorkspaceHomeRecentActivityResponseVisibilityEnum? fromJson(
          dynamic value) =>
      WorkspaceHomeRecentActivityResponseVisibilityEnumTypeTransformer()
          .decode(value);

  static List<WorkspaceHomeRecentActivityResponseVisibilityEnum> listFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final result = <WorkspaceHomeRecentActivityResponseVisibilityEnum>[];
    if (json is List && json.isNotEmpty) {
      for (final row in json) {
        final value =
            WorkspaceHomeRecentActivityResponseVisibilityEnum.fromJson(row);
        if (value != null) {
          result.add(value);
        }
      }
    }
    return result.toList(growable: growable);
  }
}

/// Transformation class that can [encode] an instance of [WorkspaceHomeRecentActivityResponseVisibilityEnum] to String,
/// and [decode] dynamic data back to [WorkspaceHomeRecentActivityResponseVisibilityEnum].
class WorkspaceHomeRecentActivityResponseVisibilityEnumTypeTransformer {
  factory WorkspaceHomeRecentActivityResponseVisibilityEnumTypeTransformer() =>
      _instance ??=
          const WorkspaceHomeRecentActivityResponseVisibilityEnumTypeTransformer
              ._();

  const WorkspaceHomeRecentActivityResponseVisibilityEnumTypeTransformer._();

  String encode(WorkspaceHomeRecentActivityResponseVisibilityEnum data) =>
      data.value;

  /// Decodes a [dynamic value][data] to a WorkspaceHomeRecentActivityResponseVisibilityEnum.
  ///
  /// If [allowNull] is true and the [dynamic value][data] cannot be decoded successfully,
  /// then null is returned. However, if [allowNull] is false and the [dynamic value][data]
  /// cannot be decoded successfully, then an [UnimplementedError] is thrown.
  ///
  /// The [allowNull] is very handy when an API changes and a new enum value is added or removed,
  /// and users are still using an old app with the old code.
  WorkspaceHomeRecentActivityResponseVisibilityEnum? decode(dynamic data,
      {bool allowNull = true}) {
    if (data != null) {
      switch (data) {
        case r'workspace':
          return WorkspaceHomeRecentActivityResponseVisibilityEnum.workspace;
        default:
          if (!allowNull) {
            throw ArgumentError('Unknown enum value to decode: $data');
          }
      }
    }
    return null;
  }

  /// Singleton [WorkspaceHomeRecentActivityResponseVisibilityEnumTypeTransformer] instance.
  static WorkspaceHomeRecentActivityResponseVisibilityEnumTypeTransformer?
      _instance;
}
