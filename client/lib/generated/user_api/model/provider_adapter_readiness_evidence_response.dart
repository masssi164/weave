//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//
// @dart=2.18

// ignore_for_file: unused_element, unused_import
// ignore_for_file: always_put_required_named_parameters_first
// ignore_for_file: constant_identifier_names
// ignore_for_file: lines_longer_than_80_chars

part of weave_user_api;

class ProviderAdapterReadinessEvidenceResponse {
  /// Returns a new [ProviderAdapterReadinessEvidenceResponse] instance.
  ProviderAdapterReadinessEvidenceResponse({
    this.adapterKey,
    this.configured,
    this.domain,
    this.evidenceTimestamp,
    this.failClosed,
    this.health,
    this.providerRealityLevel,
    this.reachable,
    this.supportSafeDiagnostics = const {},
  });

  /// Support-safe adapter key; never an endpoint URL or credential.
  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? adapterKey;

  /// True when backend/operator configuration is present for the selected adapter.
  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  bool? configured;

  /// Provider-neutral Weave domain/category key.
  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? domain;

  /// UTC timestamp for the evidence snapshot.
  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  DateTime? evidenceTimestamp;

  /// True when unavailable provider access remains fail-closed behind Weave facades.
  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  bool? failClosed;

  /// Support-safe readiness/health state for this adapter.
  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? health;

  /// Evidence-backed implementation maturity for this adapter candidate.
  ProviderAdapterReadinessEvidenceResponseProviderRealityLevelEnum?
      providerRealityLevel;

  /// True when the backend can infer a reachable adapter without exposing raw diagnostics.
  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  bool? reachable;

  /// Booleans/counts/stable codes only; no endpoints, secrets, tokens, or raw upstream errors.
  Map<String, Object> supportSafeDiagnostics;

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is ProviderAdapterReadinessEvidenceResponse &&
          other.adapterKey == adapterKey &&
          other.configured == configured &&
          other.domain == domain &&
          other.evidenceTimestamp == evidenceTimestamp &&
          other.failClosed == failClosed &&
          other.health == health &&
          other.providerRealityLevel == providerRealityLevel &&
          other.reachable == reachable &&
          _deepEquality.equals(
              other.supportSafeDiagnostics, supportSafeDiagnostics);

  @override
  int get hashCode =>
      // ignore: unnecessary_parenthesis
      (adapterKey == null ? 0 : adapterKey!.hashCode) +
      (configured == null ? 0 : configured!.hashCode) +
      (domain == null ? 0 : domain!.hashCode) +
      (evidenceTimestamp == null ? 0 : evidenceTimestamp!.hashCode) +
      (failClosed == null ? 0 : failClosed!.hashCode) +
      (health == null ? 0 : health!.hashCode) +
      (providerRealityLevel == null ? 0 : providerRealityLevel!.hashCode) +
      (reachable == null ? 0 : reachable!.hashCode) +
      (supportSafeDiagnostics.hashCode);

  @override
  String toString() =>
      'ProviderAdapterReadinessEvidenceResponse[adapterKey=$adapterKey, configured=$configured, domain=$domain, evidenceTimestamp=$evidenceTimestamp, failClosed=$failClosed, health=$health, providerRealityLevel=$providerRealityLevel, reachable=$reachable, supportSafeDiagnostics=$supportSafeDiagnostics]';

  Map<String, dynamic> toJson() {
    final json = <String, dynamic>{};
    if (this.adapterKey != null) {
      json[r'adapterKey'] = this.adapterKey;
    } else {
      json[r'adapterKey'] = null;
    }
    if (this.configured != null) {
      json[r'configured'] = this.configured;
    } else {
      json[r'configured'] = null;
    }
    if (this.domain != null) {
      json[r'domain'] = this.domain;
    } else {
      json[r'domain'] = null;
    }
    if (this.evidenceTimestamp != null) {
      json[r'evidenceTimestamp'] =
          this.evidenceTimestamp!.toUtc().toIso8601String();
    } else {
      json[r'evidenceTimestamp'] = null;
    }
    if (this.failClosed != null) {
      json[r'failClosed'] = this.failClosed;
    } else {
      json[r'failClosed'] = null;
    }
    if (this.health != null) {
      json[r'health'] = this.health;
    } else {
      json[r'health'] = null;
    }
    if (this.providerRealityLevel != null) {
      json[r'providerRealityLevel'] = this.providerRealityLevel;
    } else {
      json[r'providerRealityLevel'] = null;
    }
    if (this.reachable != null) {
      json[r'reachable'] = this.reachable;
    } else {
      json[r'reachable'] = null;
    }
    json[r'supportSafeDiagnostics'] = this.supportSafeDiagnostics;
    return json;
  }

  /// Returns a new [ProviderAdapterReadinessEvidenceResponse] instance and imports its values from
  /// [value] if it's a [Map], null otherwise.
  // ignore: prefer_constructors_over_static_methods
  static ProviderAdapterReadinessEvidenceResponse? fromJson(dynamic value) {
    if (value is Map) {
      final json = value.cast<String, dynamic>();

      // Ensure that the map contains the required keys.
      // Note 1: the values aren't checked for validity beyond being non-null.
      // Note 2: this code is stripped in release mode!
      assert(() {
        requiredKeys.forEach((key) {
          assert(json.containsKey(key),
              'Required key "ProviderAdapterReadinessEvidenceResponse[$key]" is missing from JSON.');
          assert(json[key] != null,
              'Required key "ProviderAdapterReadinessEvidenceResponse[$key]" has a null value in JSON.');
        });
        return true;
      }());

      return ProviderAdapterReadinessEvidenceResponse(
        adapterKey: mapValueOfType<String>(json, r'adapterKey'),
        configured: mapValueOfType<bool>(json, r'configured'),
        domain: mapValueOfType<String>(json, r'domain'),
        evidenceTimestamp: mapDateTime(json, r'evidenceTimestamp', r''),
        failClosed: mapValueOfType<bool>(json, r'failClosed'),
        health: mapValueOfType<String>(json, r'health'),
        providerRealityLevel:
            ProviderAdapterReadinessEvidenceResponseProviderRealityLevelEnum
                .fromJson(json[r'providerRealityLevel']),
        reachable: mapValueOfType<bool>(json, r'reachable'),
        supportSafeDiagnostics:
            mapCastOfType<String, Object>(json, r'supportSafeDiagnostics') ??
                const {},
      );
    }
    return null;
  }

  static List<ProviderAdapterReadinessEvidenceResponse> listFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final result = <ProviderAdapterReadinessEvidenceResponse>[];
    if (json is List && json.isNotEmpty) {
      for (final row in json) {
        final value = ProviderAdapterReadinessEvidenceResponse.fromJson(row);
        if (value != null) {
          result.add(value);
        }
      }
    }
    return result.toList(growable: growable);
  }

  static Map<String, ProviderAdapterReadinessEvidenceResponse> mapFromJson(
      dynamic json) {
    final map = <String, ProviderAdapterReadinessEvidenceResponse>{};
    if (json is Map && json.isNotEmpty) {
      json = json.cast<String, dynamic>(); // ignore: parameter_assignments
      for (final entry in json.entries) {
        final value =
            ProviderAdapterReadinessEvidenceResponse.fromJson(entry.value);
        if (value != null) {
          map[entry.key] = value;
        }
      }
    }
    return map;
  }

  // maps a json object with a list of ProviderAdapterReadinessEvidenceResponse-objects as value to a dart map
  static Map<String, List<ProviderAdapterReadinessEvidenceResponse>>
      mapListFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final map = <String, List<ProviderAdapterReadinessEvidenceResponse>>{};
    if (json is Map && json.isNotEmpty) {
      // ignore: parameter_assignments
      json = json.cast<String, dynamic>();
      for (final entry in json.entries) {
        map[entry.key] = ProviderAdapterReadinessEvidenceResponse.listFromJson(
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

/// Evidence-backed implementation maturity for this adapter candidate.
class ProviderAdapterReadinessEvidenceResponseProviderRealityLevelEnum {
  /// Instantiate a new enum with the provided [value].
  const ProviderAdapterReadinessEvidenceResponseProviderRealityLevelEnum._(
      this.value);

  /// The underlying value of this enum member.
  final String value;

  @override
  String toString() => value;

  String toJson() => value;

  static const contractOnly =
      ProviderAdapterReadinessEvidenceResponseProviderRealityLevelEnum._(
          r'contract_only');
  static const configured =
      ProviderAdapterReadinessEvidenceResponseProviderRealityLevelEnum._(
          r'configured');
  static const liveRead =
      ProviderAdapterReadinessEvidenceResponseProviderRealityLevelEnum._(
          r'live_read');
  static const liveWrite =
      ProviderAdapterReadinessEvidenceResponseProviderRealityLevelEnum._(
          r'live_write');
  static const migrationDryRun =
      ProviderAdapterReadinessEvidenceResponseProviderRealityLevelEnum._(
          r'migration_dry_run');
  static const migrationApplyReady =
      ProviderAdapterReadinessEvidenceResponseProviderRealityLevelEnum._(
          r'migration_apply_ready');
  static const rollbackReady =
      ProviderAdapterReadinessEvidenceResponseProviderRealityLevelEnum._(
          r'rollback_ready');
  static const releaseReady =
      ProviderAdapterReadinessEvidenceResponseProviderRealityLevelEnum._(
          r'release_ready');

  /// List of all possible values in this [enum][ProviderAdapterReadinessEvidenceResponseProviderRealityLevelEnum].
  static const values =
      <ProviderAdapterReadinessEvidenceResponseProviderRealityLevelEnum>[
    contractOnly,
    configured,
    liveRead,
    liveWrite,
    migrationDryRun,
    migrationApplyReady,
    rollbackReady,
    releaseReady,
  ];

  static ProviderAdapterReadinessEvidenceResponseProviderRealityLevelEnum? fromJson(
          dynamic value) =>
      ProviderAdapterReadinessEvidenceResponseProviderRealityLevelEnumTypeTransformer()
          .decode(value);

  static List<ProviderAdapterReadinessEvidenceResponseProviderRealityLevelEnum>
      listFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final result =
        <ProviderAdapterReadinessEvidenceResponseProviderRealityLevelEnum>[];
    if (json is List && json.isNotEmpty) {
      for (final row in json) {
        final value =
            ProviderAdapterReadinessEvidenceResponseProviderRealityLevelEnum
                .fromJson(row);
        if (value != null) {
          result.add(value);
        }
      }
    }
    return result.toList(growable: growable);
  }
}

/// Transformation class that can [encode] an instance of [ProviderAdapterReadinessEvidenceResponseProviderRealityLevelEnum] to String,
/// and [decode] dynamic data back to [ProviderAdapterReadinessEvidenceResponseProviderRealityLevelEnum].
class ProviderAdapterReadinessEvidenceResponseProviderRealityLevelEnumTypeTransformer {
  factory ProviderAdapterReadinessEvidenceResponseProviderRealityLevelEnumTypeTransformer() =>
      _instance ??=
          const ProviderAdapterReadinessEvidenceResponseProviderRealityLevelEnumTypeTransformer
              ._();

  const ProviderAdapterReadinessEvidenceResponseProviderRealityLevelEnumTypeTransformer._();

  String encode(
          ProviderAdapterReadinessEvidenceResponseProviderRealityLevelEnum
              data) =>
      data.value;

  /// Decodes a [dynamic value][data] to a ProviderAdapterReadinessEvidenceResponseProviderRealityLevelEnum.
  ///
  /// If [allowNull] is true and the [dynamic value][data] cannot be decoded successfully,
  /// then null is returned. However, if [allowNull] is false and the [dynamic value][data]
  /// cannot be decoded successfully, then an [UnimplementedError] is thrown.
  ///
  /// The [allowNull] is very handy when an API changes and a new enum value is added or removed,
  /// and users are still using an old app with the old code.
  ProviderAdapterReadinessEvidenceResponseProviderRealityLevelEnum? decode(
      dynamic data,
      {bool allowNull = true}) {
    if (data != null) {
      switch (data) {
        case r'contract_only':
          return ProviderAdapterReadinessEvidenceResponseProviderRealityLevelEnum
              .contractOnly;
        case r'configured':
          return ProviderAdapterReadinessEvidenceResponseProviderRealityLevelEnum
              .configured;
        case r'live_read':
          return ProviderAdapterReadinessEvidenceResponseProviderRealityLevelEnum
              .liveRead;
        case r'live_write':
          return ProviderAdapterReadinessEvidenceResponseProviderRealityLevelEnum
              .liveWrite;
        case r'migration_dry_run':
          return ProviderAdapterReadinessEvidenceResponseProviderRealityLevelEnum
              .migrationDryRun;
        case r'migration_apply_ready':
          return ProviderAdapterReadinessEvidenceResponseProviderRealityLevelEnum
              .migrationApplyReady;
        case r'rollback_ready':
          return ProviderAdapterReadinessEvidenceResponseProviderRealityLevelEnum
              .rollbackReady;
        case r'release_ready':
          return ProviderAdapterReadinessEvidenceResponseProviderRealityLevelEnum
              .releaseReady;
        default:
          if (!allowNull) {
            throw ArgumentError('Unknown enum value to decode: $data');
          }
      }
    }
    return null;
  }

  /// Singleton [ProviderAdapterReadinessEvidenceResponseProviderRealityLevelEnumTypeTransformer] instance.
  static ProviderAdapterReadinessEvidenceResponseProviderRealityLevelEnumTypeTransformer?
      _instance;
}
