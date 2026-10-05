//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//
// @dart=2.18

// ignore_for_file: unused_element, unused_import
// ignore_for_file: always_put_required_named_parameters_first
// ignore_for_file: constant_identifier_names
// ignore_for_file: lines_longer_than_80_chars

part of weave_user_api;

class DomainAdapterCandidateResponse {
  /// Returns a new [DomainAdapterCandidateResponse] instance.
  DomainAdapterCandidateResponse({
    this.active,
    this.adapterKey,
    this.choiceModel,
    this.configured,
    this.diagnostics = const {},
    this.migrationSupport = const [],
    this.providerRealityLevel,
    this.readiness,
    this.realityLevelRemediation,
    this.riskNotes = const [],
    this.supportSafe,
  });

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  bool? active;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? adapterKey;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? choiceModel;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  bool? configured;

  Map<String, Object> diagnostics;

  List<String> migrationSupport;

  /// Evidence-backed implementation maturity for provider candidates. Contract-only providers are never member-available.
  DomainAdapterCandidateResponseProviderRealityLevelEnum? providerRealityLevel;

  /// Admin Workspace Health readiness state for one provider-neutral category.
  DomainAdapterCandidateResponseReadinessEnum? readiness;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? realityLevelRemediation;

  List<String> riskNotes;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  bool? supportSafe;

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is DomainAdapterCandidateResponse &&
          other.active == active &&
          other.adapterKey == adapterKey &&
          other.choiceModel == choiceModel &&
          other.configured == configured &&
          _deepEquality.equals(other.diagnostics, diagnostics) &&
          _deepEquality.equals(other.migrationSupport, migrationSupport) &&
          other.providerRealityLevel == providerRealityLevel &&
          other.readiness == readiness &&
          other.realityLevelRemediation == realityLevelRemediation &&
          _deepEquality.equals(other.riskNotes, riskNotes) &&
          other.supportSafe == supportSafe;

  @override
  int get hashCode =>
      // ignore: unnecessary_parenthesis
      (active == null ? 0 : active!.hashCode) +
      (adapterKey == null ? 0 : adapterKey!.hashCode) +
      (choiceModel == null ? 0 : choiceModel!.hashCode) +
      (configured == null ? 0 : configured!.hashCode) +
      (diagnostics.hashCode) +
      (migrationSupport.hashCode) +
      (providerRealityLevel == null ? 0 : providerRealityLevel!.hashCode) +
      (readiness == null ? 0 : readiness!.hashCode) +
      (realityLevelRemediation == null
          ? 0
          : realityLevelRemediation!.hashCode) +
      (riskNotes.hashCode) +
      (supportSafe == null ? 0 : supportSafe!.hashCode);

  @override
  String toString() =>
      'DomainAdapterCandidateResponse[active=$active, adapterKey=$adapterKey, choiceModel=$choiceModel, configured=$configured, diagnostics=$diagnostics, migrationSupport=$migrationSupport, providerRealityLevel=$providerRealityLevel, readiness=$readiness, realityLevelRemediation=$realityLevelRemediation, riskNotes=$riskNotes, supportSafe=$supportSafe]';

  Map<String, dynamic> toJson() {
    final json = <String, dynamic>{};
    if (this.active != null) {
      json[r'active'] = this.active;
    } else {
      json[r'active'] = null;
    }
    if (this.adapterKey != null) {
      json[r'adapterKey'] = this.adapterKey;
    } else {
      json[r'adapterKey'] = null;
    }
    if (this.choiceModel != null) {
      json[r'choiceModel'] = this.choiceModel;
    } else {
      json[r'choiceModel'] = null;
    }
    if (this.configured != null) {
      json[r'configured'] = this.configured;
    } else {
      json[r'configured'] = null;
    }
    json[r'diagnostics'] = this.diagnostics;
    json[r'migrationSupport'] = this.migrationSupport;
    if (this.providerRealityLevel != null) {
      json[r'providerRealityLevel'] = this.providerRealityLevel;
    } else {
      json[r'providerRealityLevel'] = null;
    }
    if (this.readiness != null) {
      json[r'readiness'] = this.readiness;
    } else {
      json[r'readiness'] = null;
    }
    if (this.realityLevelRemediation != null) {
      json[r'realityLevelRemediation'] = this.realityLevelRemediation;
    } else {
      json[r'realityLevelRemediation'] = null;
    }
    json[r'riskNotes'] = this.riskNotes;
    if (this.supportSafe != null) {
      json[r'supportSafe'] = this.supportSafe;
    } else {
      json[r'supportSafe'] = null;
    }
    return json;
  }

  /// Returns a new [DomainAdapterCandidateResponse] instance and imports its values from
  /// [value] if it's a [Map], null otherwise.
  // ignore: prefer_constructors_over_static_methods
  static DomainAdapterCandidateResponse? fromJson(dynamic value) {
    if (value is Map) {
      final json = value.cast<String, dynamic>();

      // Ensure that the map contains the required keys.
      // Note 1: the values aren't checked for validity beyond being non-null.
      // Note 2: this code is stripped in release mode!
      assert(() {
        requiredKeys.forEach((key) {
          assert(json.containsKey(key),
              'Required key "DomainAdapterCandidateResponse[$key]" is missing from JSON.');
          assert(json[key] != null,
              'Required key "DomainAdapterCandidateResponse[$key]" has a null value in JSON.');
        });
        return true;
      }());

      return DomainAdapterCandidateResponse(
        active: mapValueOfType<bool>(json, r'active'),
        adapterKey: mapValueOfType<String>(json, r'adapterKey'),
        choiceModel: mapValueOfType<String>(json, r'choiceModel'),
        configured: mapValueOfType<bool>(json, r'configured'),
        diagnostics:
            mapCastOfType<String, Object>(json, r'diagnostics') ?? const {},
        migrationSupport: json[r'migrationSupport'] is Iterable
            ? (json[r'migrationSupport'] as Iterable)
                .cast<String>()
                .toList(growable: false)
            : const [],
        providerRealityLevel:
            DomainAdapterCandidateResponseProviderRealityLevelEnum.fromJson(
                json[r'providerRealityLevel']),
        readiness: DomainAdapterCandidateResponseReadinessEnum.fromJson(
            json[r'readiness']),
        realityLevelRemediation:
            mapValueOfType<String>(json, r'realityLevelRemediation'),
        riskNotes: json[r'riskNotes'] is Iterable
            ? (json[r'riskNotes'] as Iterable)
                .cast<String>()
                .toList(growable: false)
            : const [],
        supportSafe: mapValueOfType<bool>(json, r'supportSafe'),
      );
    }
    return null;
  }

  static List<DomainAdapterCandidateResponse> listFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final result = <DomainAdapterCandidateResponse>[];
    if (json is List && json.isNotEmpty) {
      for (final row in json) {
        final value = DomainAdapterCandidateResponse.fromJson(row);
        if (value != null) {
          result.add(value);
        }
      }
    }
    return result.toList(growable: growable);
  }

  static Map<String, DomainAdapterCandidateResponse> mapFromJson(dynamic json) {
    final map = <String, DomainAdapterCandidateResponse>{};
    if (json is Map && json.isNotEmpty) {
      json = json.cast<String, dynamic>(); // ignore: parameter_assignments
      for (final entry in json.entries) {
        final value = DomainAdapterCandidateResponse.fromJson(entry.value);
        if (value != null) {
          map[entry.key] = value;
        }
      }
    }
    return map;
  }

  // maps a json object with a list of DomainAdapterCandidateResponse-objects as value to a dart map
  static Map<String, List<DomainAdapterCandidateResponse>> mapListFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final map = <String, List<DomainAdapterCandidateResponse>>{};
    if (json is Map && json.isNotEmpty) {
      // ignore: parameter_assignments
      json = json.cast<String, dynamic>();
      for (final entry in json.entries) {
        map[entry.key] = DomainAdapterCandidateResponse.listFromJson(
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

/// Evidence-backed implementation maturity for provider candidates. Contract-only providers are never member-available.
class DomainAdapterCandidateResponseProviderRealityLevelEnum {
  /// Instantiate a new enum with the provided [value].
  const DomainAdapterCandidateResponseProviderRealityLevelEnum._(this.value);

  /// The underlying value of this enum member.
  final String value;

  @override
  String toString() => value;

  String toJson() => value;

  static const contractOnly =
      DomainAdapterCandidateResponseProviderRealityLevelEnum._(
          r'contract_only');
  static const configured =
      DomainAdapterCandidateResponseProviderRealityLevelEnum._(r'configured');
  static const liveRead =
      DomainAdapterCandidateResponseProviderRealityLevelEnum._(r'live_read');
  static const liveWrite =
      DomainAdapterCandidateResponseProviderRealityLevelEnum._(r'live_write');
  static const migrationDryRun =
      DomainAdapterCandidateResponseProviderRealityLevelEnum._(
          r'migration_dry_run');
  static const migrationApplyReady =
      DomainAdapterCandidateResponseProviderRealityLevelEnum._(
          r'migration_apply_ready');
  static const rollbackReady =
      DomainAdapterCandidateResponseProviderRealityLevelEnum._(
          r'rollback_ready');
  static const releaseReady =
      DomainAdapterCandidateResponseProviderRealityLevelEnum._(
          r'release_ready');

  /// List of all possible values in this [enum][DomainAdapterCandidateResponseProviderRealityLevelEnum].
  static const values =
      <DomainAdapterCandidateResponseProviderRealityLevelEnum>[
    contractOnly,
    configured,
    liveRead,
    liveWrite,
    migrationDryRun,
    migrationApplyReady,
    rollbackReady,
    releaseReady,
  ];

  static DomainAdapterCandidateResponseProviderRealityLevelEnum? fromJson(
          dynamic value) =>
      DomainAdapterCandidateResponseProviderRealityLevelEnumTypeTransformer()
          .decode(value);

  static List<DomainAdapterCandidateResponseProviderRealityLevelEnum>
      listFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final result = <DomainAdapterCandidateResponseProviderRealityLevelEnum>[];
    if (json is List && json.isNotEmpty) {
      for (final row in json) {
        final value =
            DomainAdapterCandidateResponseProviderRealityLevelEnum.fromJson(
                row);
        if (value != null) {
          result.add(value);
        }
      }
    }
    return result.toList(growable: growable);
  }
}

/// Transformation class that can [encode] an instance of [DomainAdapterCandidateResponseProviderRealityLevelEnum] to String,
/// and [decode] dynamic data back to [DomainAdapterCandidateResponseProviderRealityLevelEnum].
class DomainAdapterCandidateResponseProviderRealityLevelEnumTypeTransformer {
  factory DomainAdapterCandidateResponseProviderRealityLevelEnumTypeTransformer() =>
      _instance ??=
          const DomainAdapterCandidateResponseProviderRealityLevelEnumTypeTransformer
              ._();

  const DomainAdapterCandidateResponseProviderRealityLevelEnumTypeTransformer._();

  String encode(DomainAdapterCandidateResponseProviderRealityLevelEnum data) =>
      data.value;

  /// Decodes a [dynamic value][data] to a DomainAdapterCandidateResponseProviderRealityLevelEnum.
  ///
  /// If [allowNull] is true and the [dynamic value][data] cannot be decoded successfully,
  /// then null is returned. However, if [allowNull] is false and the [dynamic value][data]
  /// cannot be decoded successfully, then an [UnimplementedError] is thrown.
  ///
  /// The [allowNull] is very handy when an API changes and a new enum value is added or removed,
  /// and users are still using an old app with the old code.
  DomainAdapterCandidateResponseProviderRealityLevelEnum? decode(dynamic data,
      {bool allowNull = true}) {
    if (data != null) {
      switch (data) {
        case r'contract_only':
          return DomainAdapterCandidateResponseProviderRealityLevelEnum
              .contractOnly;
        case r'configured':
          return DomainAdapterCandidateResponseProviderRealityLevelEnum
              .configured;
        case r'live_read':
          return DomainAdapterCandidateResponseProviderRealityLevelEnum
              .liveRead;
        case r'live_write':
          return DomainAdapterCandidateResponseProviderRealityLevelEnum
              .liveWrite;
        case r'migration_dry_run':
          return DomainAdapterCandidateResponseProviderRealityLevelEnum
              .migrationDryRun;
        case r'migration_apply_ready':
          return DomainAdapterCandidateResponseProviderRealityLevelEnum
              .migrationApplyReady;
        case r'rollback_ready':
          return DomainAdapterCandidateResponseProviderRealityLevelEnum
              .rollbackReady;
        case r'release_ready':
          return DomainAdapterCandidateResponseProviderRealityLevelEnum
              .releaseReady;
        default:
          if (!allowNull) {
            throw ArgumentError('Unknown enum value to decode: $data');
          }
      }
    }
    return null;
  }

  /// Singleton [DomainAdapterCandidateResponseProviderRealityLevelEnumTypeTransformer] instance.
  static DomainAdapterCandidateResponseProviderRealityLevelEnumTypeTransformer?
      _instance;
}

/// Admin Workspace Health readiness state for one provider-neutral category.
class DomainAdapterCandidateResponseReadinessEnum {
  /// Instantiate a new enum with the provided [value].
  const DomainAdapterCandidateResponseReadinessEnum._(this.value);

  /// The underlying value of this enum member.
  final String value;

  @override
  String toString() => value;

  String toJson() => value;

  static const ready = DomainAdapterCandidateResponseReadinessEnum._(r'ready');
  static const disabled =
      DomainAdapterCandidateResponseReadinessEnum._(r'disabled');
  static const degraded =
      DomainAdapterCandidateResponseReadinessEnum._(r'degraded');
  static const policyBlocked =
      DomainAdapterCandidateResponseReadinessEnum._(r'policy_blocked');
  static const misconfigured =
      DomainAdapterCandidateResponseReadinessEnum._(r'misconfigured');

  /// List of all possible values in this [enum][DomainAdapterCandidateResponseReadinessEnum].
  static const values = <DomainAdapterCandidateResponseReadinessEnum>[
    ready,
    disabled,
    degraded,
    policyBlocked,
    misconfigured,
  ];

  static DomainAdapterCandidateResponseReadinessEnum? fromJson(dynamic value) =>
      DomainAdapterCandidateResponseReadinessEnumTypeTransformer()
          .decode(value);

  static List<DomainAdapterCandidateResponseReadinessEnum> listFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final result = <DomainAdapterCandidateResponseReadinessEnum>[];
    if (json is List && json.isNotEmpty) {
      for (final row in json) {
        final value = DomainAdapterCandidateResponseReadinessEnum.fromJson(row);
        if (value != null) {
          result.add(value);
        }
      }
    }
    return result.toList(growable: growable);
  }
}

/// Transformation class that can [encode] an instance of [DomainAdapterCandidateResponseReadinessEnum] to String,
/// and [decode] dynamic data back to [DomainAdapterCandidateResponseReadinessEnum].
class DomainAdapterCandidateResponseReadinessEnumTypeTransformer {
  factory DomainAdapterCandidateResponseReadinessEnumTypeTransformer() =>
      _instance ??=
          const DomainAdapterCandidateResponseReadinessEnumTypeTransformer._();

  const DomainAdapterCandidateResponseReadinessEnumTypeTransformer._();

  String encode(DomainAdapterCandidateResponseReadinessEnum data) => data.value;

  /// Decodes a [dynamic value][data] to a DomainAdapterCandidateResponseReadinessEnum.
  ///
  /// If [allowNull] is true and the [dynamic value][data] cannot be decoded successfully,
  /// then null is returned. However, if [allowNull] is false and the [dynamic value][data]
  /// cannot be decoded successfully, then an [UnimplementedError] is thrown.
  ///
  /// The [allowNull] is very handy when an API changes and a new enum value is added or removed,
  /// and users are still using an old app with the old code.
  DomainAdapterCandidateResponseReadinessEnum? decode(dynamic data,
      {bool allowNull = true}) {
    if (data != null) {
      switch (data) {
        case r'ready':
          return DomainAdapterCandidateResponseReadinessEnum.ready;
        case r'disabled':
          return DomainAdapterCandidateResponseReadinessEnum.disabled;
        case r'degraded':
          return DomainAdapterCandidateResponseReadinessEnum.degraded;
        case r'policy_blocked':
          return DomainAdapterCandidateResponseReadinessEnum.policyBlocked;
        case r'misconfigured':
          return DomainAdapterCandidateResponseReadinessEnum.misconfigured;
        default:
          if (!allowNull) {
            throw ArgumentError('Unknown enum value to decode: $data');
          }
      }
    }
    return null;
  }

  /// Singleton [DomainAdapterCandidateResponseReadinessEnumTypeTransformer] instance.
  static DomainAdapterCandidateResponseReadinessEnumTypeTransformer? _instance;
}
