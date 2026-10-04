//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//
// @dart=2.18

// ignore_for_file: unused_element, unused_import
// ignore_for_file: always_put_required_named_parameters_first
// ignore_for_file: constant_identifier_names
// ignore_for_file: lines_longer_than_80_chars

part of weave_user_api;

class DomainAdapterStatusResponse {
  /// Returns a new [DomainAdapterStatusResponse] instance.
  DomainAdapterStatusResponse({
    this.activeAdapter,
    this.candidates = const [],
    this.domain,
    this.enabled,
    this.failClosed,
    this.label,
    this.memberImpact,
    this.readiness,
    this.supportSafe,
    this.violations = const [],
  });

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? activeAdapter;

  List<DomainAdapterCandidateResponse> candidates;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? domain;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  bool? enabled;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  bool? failClosed;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? label;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? memberImpact;

  /// Admin Workspace Health readiness state for one provider-neutral category.
  DomainAdapterStatusResponseReadinessEnum? readiness;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  bool? supportSafe;

  List<String> violations;

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is DomainAdapterStatusResponse &&
          other.activeAdapter == activeAdapter &&
          _deepEquality.equals(other.candidates, candidates) &&
          other.domain == domain &&
          other.enabled == enabled &&
          other.failClosed == failClosed &&
          other.label == label &&
          other.memberImpact == memberImpact &&
          other.readiness == readiness &&
          other.supportSafe == supportSafe &&
          _deepEquality.equals(other.violations, violations);

  @override
  int get hashCode =>
      // ignore: unnecessary_parenthesis
      (activeAdapter == null ? 0 : activeAdapter!.hashCode) +
      (candidates.hashCode) +
      (domain == null ? 0 : domain!.hashCode) +
      (enabled == null ? 0 : enabled!.hashCode) +
      (failClosed == null ? 0 : failClosed!.hashCode) +
      (label == null ? 0 : label!.hashCode) +
      (memberImpact == null ? 0 : memberImpact!.hashCode) +
      (readiness == null ? 0 : readiness!.hashCode) +
      (supportSafe == null ? 0 : supportSafe!.hashCode) +
      (violations.hashCode);

  @override
  String toString() =>
      'DomainAdapterStatusResponse[activeAdapter=$activeAdapter, candidates=$candidates, domain=$domain, enabled=$enabled, failClosed=$failClosed, label=$label, memberImpact=$memberImpact, readiness=$readiness, supportSafe=$supportSafe, violations=$violations]';

  Map<String, dynamic> toJson() {
    final json = <String, dynamic>{};
    if (this.activeAdapter != null) {
      json[r'activeAdapter'] = this.activeAdapter;
    } else {
      json[r'activeAdapter'] = null;
    }
    json[r'candidates'] = this.candidates;
    if (this.domain != null) {
      json[r'domain'] = this.domain;
    } else {
      json[r'domain'] = null;
    }
    if (this.enabled != null) {
      json[r'enabled'] = this.enabled;
    } else {
      json[r'enabled'] = null;
    }
    if (this.failClosed != null) {
      json[r'failClosed'] = this.failClosed;
    } else {
      json[r'failClosed'] = null;
    }
    if (this.label != null) {
      json[r'label'] = this.label;
    } else {
      json[r'label'] = null;
    }
    if (this.memberImpact != null) {
      json[r'memberImpact'] = this.memberImpact;
    } else {
      json[r'memberImpact'] = null;
    }
    if (this.readiness != null) {
      json[r'readiness'] = this.readiness;
    } else {
      json[r'readiness'] = null;
    }
    if (this.supportSafe != null) {
      json[r'supportSafe'] = this.supportSafe;
    } else {
      json[r'supportSafe'] = null;
    }
    json[r'violations'] = this.violations;
    return json;
  }

  /// Returns a new [DomainAdapterStatusResponse] instance and imports its values from
  /// [value] if it's a [Map], null otherwise.
  // ignore: prefer_constructors_over_static_methods
  static DomainAdapterStatusResponse? fromJson(dynamic value) {
    if (value is Map) {
      final json = value.cast<String, dynamic>();

      // Ensure that the map contains the required keys.
      // Note 1: the values aren't checked for validity beyond being non-null.
      // Note 2: this code is stripped in release mode!
      assert(() {
        requiredKeys.forEach((key) {
          assert(json.containsKey(key),
              'Required key "DomainAdapterStatusResponse[$key]" is missing from JSON.');
          assert(json[key] != null,
              'Required key "DomainAdapterStatusResponse[$key]" has a null value in JSON.');
        });
        return true;
      }());

      return DomainAdapterStatusResponse(
        activeAdapter: mapValueOfType<String>(json, r'activeAdapter'),
        candidates:
            DomainAdapterCandidateResponse.listFromJson(json[r'candidates']),
        domain: mapValueOfType<String>(json, r'domain'),
        enabled: mapValueOfType<bool>(json, r'enabled'),
        failClosed: mapValueOfType<bool>(json, r'failClosed'),
        label: mapValueOfType<String>(json, r'label'),
        memberImpact: mapValueOfType<String>(json, r'memberImpact'),
        readiness: DomainAdapterStatusResponseReadinessEnum.fromJson(
            json[r'readiness']),
        supportSafe: mapValueOfType<bool>(json, r'supportSafe'),
        violations: json[r'violations'] is Iterable
            ? (json[r'violations'] as Iterable)
                .cast<String>()
                .toList(growable: false)
            : const [],
      );
    }
    return null;
  }

  static List<DomainAdapterStatusResponse> listFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final result = <DomainAdapterStatusResponse>[];
    if (json is List && json.isNotEmpty) {
      for (final row in json) {
        final value = DomainAdapterStatusResponse.fromJson(row);
        if (value != null) {
          result.add(value);
        }
      }
    }
    return result.toList(growable: growable);
  }

  static Map<String, DomainAdapterStatusResponse> mapFromJson(dynamic json) {
    final map = <String, DomainAdapterStatusResponse>{};
    if (json is Map && json.isNotEmpty) {
      json = json.cast<String, dynamic>(); // ignore: parameter_assignments
      for (final entry in json.entries) {
        final value = DomainAdapterStatusResponse.fromJson(entry.value);
        if (value != null) {
          map[entry.key] = value;
        }
      }
    }
    return map;
  }

  // maps a json object with a list of DomainAdapterStatusResponse-objects as value to a dart map
  static Map<String, List<DomainAdapterStatusResponse>> mapListFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final map = <String, List<DomainAdapterStatusResponse>>{};
    if (json is Map && json.isNotEmpty) {
      // ignore: parameter_assignments
      json = json.cast<String, dynamic>();
      for (final entry in json.entries) {
        map[entry.key] = DomainAdapterStatusResponse.listFromJson(
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

/// Admin Workspace Health readiness state for one provider-neutral category.
class DomainAdapterStatusResponseReadinessEnum {
  /// Instantiate a new enum with the provided [value].
  const DomainAdapterStatusResponseReadinessEnum._(this.value);

  /// The underlying value of this enum member.
  final String value;

  @override
  String toString() => value;

  String toJson() => value;

  static const ready = DomainAdapterStatusResponseReadinessEnum._(r'ready');
  static const disabled =
      DomainAdapterStatusResponseReadinessEnum._(r'disabled');
  static const degraded =
      DomainAdapterStatusResponseReadinessEnum._(r'degraded');
  static const policyBlocked =
      DomainAdapterStatusResponseReadinessEnum._(r'policy_blocked');
  static const misconfigured =
      DomainAdapterStatusResponseReadinessEnum._(r'misconfigured');

  /// List of all possible values in this [enum][DomainAdapterStatusResponseReadinessEnum].
  static const values = <DomainAdapterStatusResponseReadinessEnum>[
    ready,
    disabled,
    degraded,
    policyBlocked,
    misconfigured,
  ];

  static DomainAdapterStatusResponseReadinessEnum? fromJson(dynamic value) =>
      DomainAdapterStatusResponseReadinessEnumTypeTransformer().decode(value);

  static List<DomainAdapterStatusResponseReadinessEnum> listFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final result = <DomainAdapterStatusResponseReadinessEnum>[];
    if (json is List && json.isNotEmpty) {
      for (final row in json) {
        final value = DomainAdapterStatusResponseReadinessEnum.fromJson(row);
        if (value != null) {
          result.add(value);
        }
      }
    }
    return result.toList(growable: growable);
  }
}

/// Transformation class that can [encode] an instance of [DomainAdapterStatusResponseReadinessEnum] to String,
/// and [decode] dynamic data back to [DomainAdapterStatusResponseReadinessEnum].
class DomainAdapterStatusResponseReadinessEnumTypeTransformer {
  factory DomainAdapterStatusResponseReadinessEnumTypeTransformer() =>
      _instance ??=
          const DomainAdapterStatusResponseReadinessEnumTypeTransformer._();

  const DomainAdapterStatusResponseReadinessEnumTypeTransformer._();

  String encode(DomainAdapterStatusResponseReadinessEnum data) => data.value;

  /// Decodes a [dynamic value][data] to a DomainAdapterStatusResponseReadinessEnum.
  ///
  /// If [allowNull] is true and the [dynamic value][data] cannot be decoded successfully,
  /// then null is returned. However, if [allowNull] is false and the [dynamic value][data]
  /// cannot be decoded successfully, then an [UnimplementedError] is thrown.
  ///
  /// The [allowNull] is very handy when an API changes and a new enum value is added or removed,
  /// and users are still using an old app with the old code.
  DomainAdapterStatusResponseReadinessEnum? decode(dynamic data,
      {bool allowNull = true}) {
    if (data != null) {
      switch (data) {
        case r'ready':
          return DomainAdapterStatusResponseReadinessEnum.ready;
        case r'disabled':
          return DomainAdapterStatusResponseReadinessEnum.disabled;
        case r'degraded':
          return DomainAdapterStatusResponseReadinessEnum.degraded;
        case r'policy_blocked':
          return DomainAdapterStatusResponseReadinessEnum.policyBlocked;
        case r'misconfigured':
          return DomainAdapterStatusResponseReadinessEnum.misconfigured;
        default:
          if (!allowNull) {
            throw ArgumentError('Unknown enum value to decode: $data');
          }
      }
    }
    return null;
  }

  /// Singleton [DomainAdapterStatusResponseReadinessEnumTypeTransformer] instance.
  static DomainAdapterStatusResponseReadinessEnumTypeTransformer? _instance;
}
