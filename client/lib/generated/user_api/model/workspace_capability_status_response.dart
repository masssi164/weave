//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//
// @dart=2.18

// ignore_for_file: unused_element, unused_import
// ignore_for_file: always_put_required_named_parameters_first
// ignore_for_file: constant_identifier_names
// ignore_for_file: lines_longer_than_80_chars

part of weave_user_api;

class WorkspaceCapabilityStatusResponse {
  /// Returns a new [WorkspaceCapabilityStatusResponse] instance.
  WorkspaceCapabilityStatusResponse({
    this.enabled,
    this.grantedCapabilities = const [],
    this.memberImpact,
    this.policyState,
    this.profileKey,
    this.readiness,
    this.supportRef,
  });

  /// Whether the capability is enabled for this workspace.
  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  bool? enabled;

  /// Category-level capability identifiers granted to the authenticated principal.
  List<String> grantedCapabilities;

  /// Member-safe impact or fallback copy for this capability.
  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? memberImpact;

  /// Support-safe effective policy state for the authenticated principal.
  WorkspaceCapabilityStatusResponsePolicyStateEnum? policyState;

  /// Policy profile key that decided this capability. Does not expose provider internals.
  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? profileKey;

  /// Current readiness state for this capability.
  WorkspaceCapabilityStatusResponseReadinessEnum? readiness;

  /// Support-safe reference for this capability state. Does not expose provider internals.
  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? supportRef;

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is WorkspaceCapabilityStatusResponse &&
          other.enabled == enabled &&
          _deepEquality.equals(
              other.grantedCapabilities, grantedCapabilities) &&
          other.memberImpact == memberImpact &&
          other.policyState == policyState &&
          other.profileKey == profileKey &&
          other.readiness == readiness &&
          other.supportRef == supportRef;

  @override
  int get hashCode =>
      // ignore: unnecessary_parenthesis
      (enabled == null ? 0 : enabled!.hashCode) +
      (grantedCapabilities.hashCode) +
      (memberImpact == null ? 0 : memberImpact!.hashCode) +
      (policyState == null ? 0 : policyState!.hashCode) +
      (profileKey == null ? 0 : profileKey!.hashCode) +
      (readiness == null ? 0 : readiness!.hashCode) +
      (supportRef == null ? 0 : supportRef!.hashCode);

  @override
  String toString() =>
      'WorkspaceCapabilityStatusResponse[enabled=$enabled, grantedCapabilities=$grantedCapabilities, memberImpact=$memberImpact, policyState=$policyState, profileKey=$profileKey, readiness=$readiness, supportRef=$supportRef]';

  Map<String, dynamic> toJson() {
    final json = <String, dynamic>{};
    if (this.enabled != null) {
      json[r'enabled'] = this.enabled;
    } else {
      json[r'enabled'] = null;
    }
    json[r'grantedCapabilities'] = this.grantedCapabilities;
    if (this.memberImpact != null) {
      json[r'memberImpact'] = this.memberImpact;
    } else {
      json[r'memberImpact'] = null;
    }
    if (this.policyState != null) {
      json[r'policyState'] = this.policyState;
    } else {
      json[r'policyState'] = null;
    }
    if (this.profileKey != null) {
      json[r'profileKey'] = this.profileKey;
    } else {
      json[r'profileKey'] = null;
    }
    if (this.readiness != null) {
      json[r'readiness'] = this.readiness;
    } else {
      json[r'readiness'] = null;
    }
    if (this.supportRef != null) {
      json[r'supportRef'] = this.supportRef;
    } else {
      json[r'supportRef'] = null;
    }
    return json;
  }

  /// Returns a new [WorkspaceCapabilityStatusResponse] instance and imports its values from
  /// [value] if it's a [Map], null otherwise.
  // ignore: prefer_constructors_over_static_methods
  static WorkspaceCapabilityStatusResponse? fromJson(dynamic value) {
    if (value is Map) {
      final json = value.cast<String, dynamic>();

      // Ensure that the map contains the required keys.
      // Note 1: the values aren't checked for validity beyond being non-null.
      // Note 2: this code is stripped in release mode!
      assert(() {
        requiredKeys.forEach((key) {
          assert(json.containsKey(key),
              'Required key "WorkspaceCapabilityStatusResponse[$key]" is missing from JSON.');
          assert(json[key] != null,
              'Required key "WorkspaceCapabilityStatusResponse[$key]" has a null value in JSON.');
        });
        return true;
      }());

      return WorkspaceCapabilityStatusResponse(
        enabled: mapValueOfType<bool>(json, r'enabled'),
        grantedCapabilities: json[r'grantedCapabilities'] is Iterable
            ? (json[r'grantedCapabilities'] as Iterable)
                .cast<String>()
                .toList(growable: false)
            : const [],
        memberImpact: mapValueOfType<String>(json, r'memberImpact'),
        policyState: WorkspaceCapabilityStatusResponsePolicyStateEnum.fromJson(
            json[r'policyState']),
        profileKey: mapValueOfType<String>(json, r'profileKey'),
        readiness: WorkspaceCapabilityStatusResponseReadinessEnum.fromJson(
            json[r'readiness']),
        supportRef: mapValueOfType<String>(json, r'supportRef'),
      );
    }
    return null;
  }

  static List<WorkspaceCapabilityStatusResponse> listFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final result = <WorkspaceCapabilityStatusResponse>[];
    if (json is List && json.isNotEmpty) {
      for (final row in json) {
        final value = WorkspaceCapabilityStatusResponse.fromJson(row);
        if (value != null) {
          result.add(value);
        }
      }
    }
    return result.toList(growable: growable);
  }

  static Map<String, WorkspaceCapabilityStatusResponse> mapFromJson(
      dynamic json) {
    final map = <String, WorkspaceCapabilityStatusResponse>{};
    if (json is Map && json.isNotEmpty) {
      json = json.cast<String, dynamic>(); // ignore: parameter_assignments
      for (final entry in json.entries) {
        final value = WorkspaceCapabilityStatusResponse.fromJson(entry.value);
        if (value != null) {
          map[entry.key] = value;
        }
      }
    }
    return map;
  }

  // maps a json object with a list of WorkspaceCapabilityStatusResponse-objects as value to a dart map
  static Map<String, List<WorkspaceCapabilityStatusResponse>> mapListFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final map = <String, List<WorkspaceCapabilityStatusResponse>>{};
    if (json is Map && json.isNotEmpty) {
      // ignore: parameter_assignments
      json = json.cast<String, dynamic>();
      for (final entry in json.entries) {
        map[entry.key] = WorkspaceCapabilityStatusResponse.listFromJson(
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

/// Support-safe effective policy state for the authenticated principal.
class WorkspaceCapabilityStatusResponsePolicyStateEnum {
  /// Instantiate a new enum with the provided [value].
  const WorkspaceCapabilityStatusResponsePolicyStateEnum._(this.value);

  /// The underlying value of this enum member.
  final String value;

  @override
  String toString() => value;

  String toJson() => value;

  static const allowed =
      WorkspaceCapabilityStatusResponsePolicyStateEnum._(r'allowed');
  static const policyBlocked =
      WorkspaceCapabilityStatusResponsePolicyStateEnum._(r'policy_blocked');
  static const disabled =
      WorkspaceCapabilityStatusResponsePolicyStateEnum._(r'disabled');
  static const unavailable =
      WorkspaceCapabilityStatusResponsePolicyStateEnum._(r'unavailable');

  /// List of all possible values in this [enum][WorkspaceCapabilityStatusResponsePolicyStateEnum].
  static const values = <WorkspaceCapabilityStatusResponsePolicyStateEnum>[
    allowed,
    policyBlocked,
    disabled,
    unavailable,
  ];

  static WorkspaceCapabilityStatusResponsePolicyStateEnum? fromJson(
          dynamic value) =>
      WorkspaceCapabilityStatusResponsePolicyStateEnumTypeTransformer()
          .decode(value);

  static List<WorkspaceCapabilityStatusResponsePolicyStateEnum> listFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final result = <WorkspaceCapabilityStatusResponsePolicyStateEnum>[];
    if (json is List && json.isNotEmpty) {
      for (final row in json) {
        final value =
            WorkspaceCapabilityStatusResponsePolicyStateEnum.fromJson(row);
        if (value != null) {
          result.add(value);
        }
      }
    }
    return result.toList(growable: growable);
  }
}

/// Transformation class that can [encode] an instance of [WorkspaceCapabilityStatusResponsePolicyStateEnum] to String,
/// and [decode] dynamic data back to [WorkspaceCapabilityStatusResponsePolicyStateEnum].
class WorkspaceCapabilityStatusResponsePolicyStateEnumTypeTransformer {
  factory WorkspaceCapabilityStatusResponsePolicyStateEnumTypeTransformer() =>
      _instance ??=
          const WorkspaceCapabilityStatusResponsePolicyStateEnumTypeTransformer
              ._();

  const WorkspaceCapabilityStatusResponsePolicyStateEnumTypeTransformer._();

  String encode(WorkspaceCapabilityStatusResponsePolicyStateEnum data) =>
      data.value;

  /// Decodes a [dynamic value][data] to a WorkspaceCapabilityStatusResponsePolicyStateEnum.
  ///
  /// If [allowNull] is true and the [dynamic value][data] cannot be decoded successfully,
  /// then null is returned. However, if [allowNull] is false and the [dynamic value][data]
  /// cannot be decoded successfully, then an [UnimplementedError] is thrown.
  ///
  /// The [allowNull] is very handy when an API changes and a new enum value is added or removed,
  /// and users are still using an old app with the old code.
  WorkspaceCapabilityStatusResponsePolicyStateEnum? decode(dynamic data,
      {bool allowNull = true}) {
    if (data != null) {
      switch (data) {
        case r'allowed':
          return WorkspaceCapabilityStatusResponsePolicyStateEnum.allowed;
        case r'policy_blocked':
          return WorkspaceCapabilityStatusResponsePolicyStateEnum.policyBlocked;
        case r'disabled':
          return WorkspaceCapabilityStatusResponsePolicyStateEnum.disabled;
        case r'unavailable':
          return WorkspaceCapabilityStatusResponsePolicyStateEnum.unavailable;
        default:
          if (!allowNull) {
            throw ArgumentError('Unknown enum value to decode: $data');
          }
      }
    }
    return null;
  }

  /// Singleton [WorkspaceCapabilityStatusResponsePolicyStateEnumTypeTransformer] instance.
  static WorkspaceCapabilityStatusResponsePolicyStateEnumTypeTransformer?
      _instance;
}

/// Current readiness state for this capability.
class WorkspaceCapabilityStatusResponseReadinessEnum {
  /// Instantiate a new enum with the provided [value].
  const WorkspaceCapabilityStatusResponseReadinessEnum._(this.value);

  /// The underlying value of this enum member.
  final String value;

  @override
  String toString() => value;

  String toJson() => value;

  static const ready =
      WorkspaceCapabilityStatusResponseReadinessEnum._(r'ready');
  static const degraded =
      WorkspaceCapabilityStatusResponseReadinessEnum._(r'degraded');
  static const blocked =
      WorkspaceCapabilityStatusResponseReadinessEnum._(r'blocked');
  static const unavailable =
      WorkspaceCapabilityStatusResponseReadinessEnum._(r'unavailable');

  /// List of all possible values in this [enum][WorkspaceCapabilityStatusResponseReadinessEnum].
  static const values = <WorkspaceCapabilityStatusResponseReadinessEnum>[
    ready,
    degraded,
    blocked,
    unavailable,
  ];

  static WorkspaceCapabilityStatusResponseReadinessEnum? fromJson(
          dynamic value) =>
      WorkspaceCapabilityStatusResponseReadinessEnumTypeTransformer()
          .decode(value);

  static List<WorkspaceCapabilityStatusResponseReadinessEnum> listFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final result = <WorkspaceCapabilityStatusResponseReadinessEnum>[];
    if (json is List && json.isNotEmpty) {
      for (final row in json) {
        final value =
            WorkspaceCapabilityStatusResponseReadinessEnum.fromJson(row);
        if (value != null) {
          result.add(value);
        }
      }
    }
    return result.toList(growable: growable);
  }
}

/// Transformation class that can [encode] an instance of [WorkspaceCapabilityStatusResponseReadinessEnum] to String,
/// and [decode] dynamic data back to [WorkspaceCapabilityStatusResponseReadinessEnum].
class WorkspaceCapabilityStatusResponseReadinessEnumTypeTransformer {
  factory WorkspaceCapabilityStatusResponseReadinessEnumTypeTransformer() =>
      _instance ??=
          const WorkspaceCapabilityStatusResponseReadinessEnumTypeTransformer
              ._();

  const WorkspaceCapabilityStatusResponseReadinessEnumTypeTransformer._();

  String encode(WorkspaceCapabilityStatusResponseReadinessEnum data) =>
      data.value;

  /// Decodes a [dynamic value][data] to a WorkspaceCapabilityStatusResponseReadinessEnum.
  ///
  /// If [allowNull] is true and the [dynamic value][data] cannot be decoded successfully,
  /// then null is returned. However, if [allowNull] is false and the [dynamic value][data]
  /// cannot be decoded successfully, then an [UnimplementedError] is thrown.
  ///
  /// The [allowNull] is very handy when an API changes and a new enum value is added or removed,
  /// and users are still using an old app with the old code.
  WorkspaceCapabilityStatusResponseReadinessEnum? decode(dynamic data,
      {bool allowNull = true}) {
    if (data != null) {
      switch (data) {
        case r'ready':
          return WorkspaceCapabilityStatusResponseReadinessEnum.ready;
        case r'degraded':
          return WorkspaceCapabilityStatusResponseReadinessEnum.degraded;
        case r'blocked':
          return WorkspaceCapabilityStatusResponseReadinessEnum.blocked;
        case r'unavailable':
          return WorkspaceCapabilityStatusResponseReadinessEnum.unavailable;
        default:
          if (!allowNull) {
            throw ArgumentError('Unknown enum value to decode: $data');
          }
      }
    }
    return null;
  }

  /// Singleton [WorkspaceCapabilityStatusResponseReadinessEnumTypeTransformer] instance.
  static WorkspaceCapabilityStatusResponseReadinessEnumTypeTransformer?
      _instance;
}
