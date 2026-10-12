//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//
// @dart=2.18

// ignore_for_file: unused_element, unused_import
// ignore_for_file: always_put_required_named_parameters_first
// ignore_for_file: constant_identifier_names
// ignore_for_file: lines_longer_than_80_chars

part of weave_user_api;

class ProviderStatusResponse {
  /// Returns a new [ProviderStatusResponse] instance.
  ProviderStatusResponse({
    this.candidates = const [],
    this.configured,
    this.diagnostics = const {},
    this.enabled,
    this.failClosed,
    this.module,
    this.paidFeaturesRequired,
    this.providerKey,
    this.providerRealityLevel,
    this.readOnly,
    this.readiness,
    this.redactionPolicy,
    this.state,
    this.summary,
    this.supportSafe,
    this.supportSafeErrorCodes = const [],
    this.supportedCapabilities = const {},
    this.unsupportedOperations = const {},
  });

  List<String> candidates;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  bool? configured;

  Map<String, Object> diagnostics;

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

  ProviderStatusResponseModuleEnum? module;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  bool? paidFeaturesRequired;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? providerKey;

  /// Evidence-backed implementation maturity for provider candidates. Contract-only providers are never member-available.
  ProviderStatusResponseProviderRealityLevelEnum? providerRealityLevel;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  bool? readOnly;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? readiness;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? redactionPolicy;

  ProviderStatusResponseStateEnum? state;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? summary;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  bool? supportSafe;

  List<String> supportSafeErrorCodes;

  Set<String> supportedCapabilities;

  Set<String> unsupportedOperations;

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is ProviderStatusResponse &&
          _deepEquality.equals(other.candidates, candidates) &&
          other.configured == configured &&
          _deepEquality.equals(other.diagnostics, diagnostics) &&
          other.enabled == enabled &&
          other.failClosed == failClosed &&
          other.module == module &&
          other.paidFeaturesRequired == paidFeaturesRequired &&
          other.providerKey == providerKey &&
          other.providerRealityLevel == providerRealityLevel &&
          other.readOnly == readOnly &&
          other.readiness == readiness &&
          other.redactionPolicy == redactionPolicy &&
          other.state == state &&
          other.summary == summary &&
          other.supportSafe == supportSafe &&
          _deepEquality.equals(
              other.supportSafeErrorCodes, supportSafeErrorCodes) &&
          _deepEquality.equals(
              other.supportedCapabilities, supportedCapabilities) &&
          _deepEquality.equals(
              other.unsupportedOperations, unsupportedOperations);

  @override
  int get hashCode =>
      // ignore: unnecessary_parenthesis
      (candidates.hashCode) +
      (configured == null ? 0 : configured!.hashCode) +
      (diagnostics.hashCode) +
      (enabled == null ? 0 : enabled!.hashCode) +
      (failClosed == null ? 0 : failClosed!.hashCode) +
      (module == null ? 0 : module!.hashCode) +
      (paidFeaturesRequired == null ? 0 : paidFeaturesRequired!.hashCode) +
      (providerKey == null ? 0 : providerKey!.hashCode) +
      (providerRealityLevel == null ? 0 : providerRealityLevel!.hashCode) +
      (readOnly == null ? 0 : readOnly!.hashCode) +
      (readiness == null ? 0 : readiness!.hashCode) +
      (redactionPolicy == null ? 0 : redactionPolicy!.hashCode) +
      (state == null ? 0 : state!.hashCode) +
      (summary == null ? 0 : summary!.hashCode) +
      (supportSafe == null ? 0 : supportSafe!.hashCode) +
      (supportSafeErrorCodes.hashCode) +
      (supportedCapabilities.hashCode) +
      (unsupportedOperations.hashCode);

  @override
  String toString() =>
      'ProviderStatusResponse[candidates=$candidates, configured=$configured, diagnostics=$diagnostics, enabled=$enabled, failClosed=$failClosed, module=$module, paidFeaturesRequired=$paidFeaturesRequired, providerKey=$providerKey, providerRealityLevel=$providerRealityLevel, readOnly=$readOnly, readiness=$readiness, redactionPolicy=$redactionPolicy, state=$state, summary=$summary, supportSafe=$supportSafe, supportSafeErrorCodes=$supportSafeErrorCodes, supportedCapabilities=$supportedCapabilities, unsupportedOperations=$unsupportedOperations]';

  Map<String, dynamic> toJson() {
    final json = <String, dynamic>{};
    json[r'candidates'] = this.candidates;
    if (this.configured != null) {
      json[r'configured'] = this.configured;
    } else {
      json[r'configured'] = null;
    }
    json[r'diagnostics'] = this.diagnostics;
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
    if (this.module != null) {
      json[r'module'] = this.module;
    } else {
      json[r'module'] = null;
    }
    if (this.paidFeaturesRequired != null) {
      json[r'paidFeaturesRequired'] = this.paidFeaturesRequired;
    } else {
      json[r'paidFeaturesRequired'] = null;
    }
    if (this.providerKey != null) {
      json[r'providerKey'] = this.providerKey;
    } else {
      json[r'providerKey'] = null;
    }
    if (this.providerRealityLevel != null) {
      json[r'providerRealityLevel'] = this.providerRealityLevel;
    } else {
      json[r'providerRealityLevel'] = null;
    }
    if (this.readOnly != null) {
      json[r'readOnly'] = this.readOnly;
    } else {
      json[r'readOnly'] = null;
    }
    if (this.readiness != null) {
      json[r'readiness'] = this.readiness;
    } else {
      json[r'readiness'] = null;
    }
    if (this.redactionPolicy != null) {
      json[r'redactionPolicy'] = this.redactionPolicy;
    } else {
      json[r'redactionPolicy'] = null;
    }
    if (this.state != null) {
      json[r'state'] = this.state;
    } else {
      json[r'state'] = null;
    }
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
    json[r'supportSafeErrorCodes'] = this.supportSafeErrorCodes;
    json[r'supportedCapabilities'] =
        this.supportedCapabilities.toList(growable: false);
    json[r'unsupportedOperations'] =
        this.unsupportedOperations.toList(growable: false);
    return json;
  }

  /// Returns a new [ProviderStatusResponse] instance and imports its values from
  /// [value] if it's a [Map], null otherwise.
  // ignore: prefer_constructors_over_static_methods
  static ProviderStatusResponse? fromJson(dynamic value) {
    if (value is Map) {
      final json = value.cast<String, dynamic>();

      // Ensure that the map contains the required keys.
      // Note 1: the values aren't checked for validity beyond being non-null.
      // Note 2: this code is stripped in release mode!
      assert(() {
        requiredKeys.forEach((key) {
          assert(json.containsKey(key),
              'Required key "ProviderStatusResponse[$key]" is missing from JSON.');
          assert(json[key] != null,
              'Required key "ProviderStatusResponse[$key]" has a null value in JSON.');
        });
        return true;
      }());

      return ProviderStatusResponse(
        candidates: json[r'candidates'] is Iterable
            ? (json[r'candidates'] as Iterable)
                .cast<String>()
                .toList(growable: false)
            : const [],
        configured: mapValueOfType<bool>(json, r'configured'),
        diagnostics:
            mapCastOfType<String, Object>(json, r'diagnostics') ?? const {},
        enabled: mapValueOfType<bool>(json, r'enabled'),
        failClosed: mapValueOfType<bool>(json, r'failClosed'),
        module: ProviderStatusResponseModuleEnum.fromJson(json[r'module']),
        paidFeaturesRequired:
            mapValueOfType<bool>(json, r'paidFeaturesRequired'),
        providerKey: mapValueOfType<String>(json, r'providerKey'),
        providerRealityLevel:
            ProviderStatusResponseProviderRealityLevelEnum.fromJson(
                json[r'providerRealityLevel']),
        readOnly: mapValueOfType<bool>(json, r'readOnly'),
        readiness: mapValueOfType<String>(json, r'readiness'),
        redactionPolicy: mapValueOfType<String>(json, r'redactionPolicy'),
        state: ProviderStatusResponseStateEnum.fromJson(json[r'state']),
        summary: mapValueOfType<String>(json, r'summary'),
        supportSafe: mapValueOfType<bool>(json, r'supportSafe'),
        supportSafeErrorCodes: json[r'supportSafeErrorCodes'] is Iterable
            ? (json[r'supportSafeErrorCodes'] as Iterable)
                .cast<String>()
                .toList(growable: false)
            : const [],
        supportedCapabilities: json[r'supportedCapabilities'] is Iterable
            ? (json[r'supportedCapabilities'] as Iterable)
                .cast<String>()
                .toSet()
            : const {},
        unsupportedOperations: json[r'unsupportedOperations'] is Iterable
            ? (json[r'unsupportedOperations'] as Iterable)
                .cast<String>()
                .toSet()
            : const {},
      );
    }
    return null;
  }

  static List<ProviderStatusResponse> listFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final result = <ProviderStatusResponse>[];
    if (json is List && json.isNotEmpty) {
      for (final row in json) {
        final value = ProviderStatusResponse.fromJson(row);
        if (value != null) {
          result.add(value);
        }
      }
    }
    return result.toList(growable: growable);
  }

  static Map<String, ProviderStatusResponse> mapFromJson(dynamic json) {
    final map = <String, ProviderStatusResponse>{};
    if (json is Map && json.isNotEmpty) {
      json = json.cast<String, dynamic>(); // ignore: parameter_assignments
      for (final entry in json.entries) {
        final value = ProviderStatusResponse.fromJson(entry.value);
        if (value != null) {
          map[entry.key] = value;
        }
      }
    }
    return map;
  }

  // maps a json object with a list of ProviderStatusResponse-objects as value to a dart map
  static Map<String, List<ProviderStatusResponse>> mapListFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final map = <String, List<ProviderStatusResponse>>{};
    if (json is Map && json.isNotEmpty) {
      // ignore: parameter_assignments
      json = json.cast<String, dynamic>();
      for (final entry in json.entries) {
        map[entry.key] = ProviderStatusResponse.listFromJson(
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

class ProviderStatusResponseModuleEnum {
  /// Instantiate a new enum with the provided [value].
  const ProviderStatusResponseModuleEnum._(this.value);

  /// The underlying value of this enum member.
  final String value;

  @override
  String toString() => value;

  String toJson() => value;

  static const matrix = ProviderStatusResponseModuleEnum._(r'matrix');
  static const files = ProviderStatusResponseModuleEnum._(r'files');
  static const office = ProviderStatusResponseModuleEnum._(r'office');
  static const calendar = ProviderStatusResponseModuleEnum._(r'calendar');
  static const contacts = ProviderStatusResponseModuleEnum._(r'contacts');
  static const forms = ProviderStatusResponseModuleEnum._(r'forms');
  static const boards = ProviderStatusResponseModuleEnum._(r'boards');
  static const meetings = ProviderStatusResponseModuleEnum._(r'meetings');
  static const sourceControl =
      ProviderStatusResponseModuleEnum._(r'source-control');
  static const ci = ProviderStatusResponseModuleEnum._(r'ci');
  static const issueTracker =
      ProviderStatusResponseModuleEnum._(r'issue-tracker');
  static const release = ProviderStatusResponseModuleEnum._(r'release');

  /// List of all possible values in this [enum][ProviderStatusResponseModuleEnum].
  static const values = <ProviderStatusResponseModuleEnum>[
    matrix,
    files,
    office,
    calendar,
    contacts,
    forms,
    boards,
    meetings,
    sourceControl,
    ci,
    issueTracker,
    release,
  ];

  static ProviderStatusResponseModuleEnum? fromJson(dynamic value) =>
      ProviderStatusResponseModuleEnumTypeTransformer().decode(value);

  static List<ProviderStatusResponseModuleEnum> listFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final result = <ProviderStatusResponseModuleEnum>[];
    if (json is List && json.isNotEmpty) {
      for (final row in json) {
        final value = ProviderStatusResponseModuleEnum.fromJson(row);
        if (value != null) {
          result.add(value);
        }
      }
    }
    return result.toList(growable: growable);
  }
}

/// Transformation class that can [encode] an instance of [ProviderStatusResponseModuleEnum] to String,
/// and [decode] dynamic data back to [ProviderStatusResponseModuleEnum].
class ProviderStatusResponseModuleEnumTypeTransformer {
  factory ProviderStatusResponseModuleEnumTypeTransformer() =>
      _instance ??= const ProviderStatusResponseModuleEnumTypeTransformer._();

  const ProviderStatusResponseModuleEnumTypeTransformer._();

  String encode(ProviderStatusResponseModuleEnum data) => data.value;

  /// Decodes a [dynamic value][data] to a ProviderStatusResponseModuleEnum.
  ///
  /// If [allowNull] is true and the [dynamic value][data] cannot be decoded successfully,
  /// then null is returned. However, if [allowNull] is false and the [dynamic value][data]
  /// cannot be decoded successfully, then an [UnimplementedError] is thrown.
  ///
  /// The [allowNull] is very handy when an API changes and a new enum value is added or removed,
  /// and users are still using an old app with the old code.
  ProviderStatusResponseModuleEnum? decode(dynamic data,
      {bool allowNull = true}) {
    if (data != null) {
      switch (data) {
        case r'matrix':
          return ProviderStatusResponseModuleEnum.matrix;
        case r'files':
          return ProviderStatusResponseModuleEnum.files;
        case r'office':
          return ProviderStatusResponseModuleEnum.office;
        case r'calendar':
          return ProviderStatusResponseModuleEnum.calendar;
        case r'contacts':
          return ProviderStatusResponseModuleEnum.contacts;
        case r'forms':
          return ProviderStatusResponseModuleEnum.forms;
        case r'boards':
          return ProviderStatusResponseModuleEnum.boards;
        case r'meetings':
          return ProviderStatusResponseModuleEnum.meetings;
        case r'source-control':
          return ProviderStatusResponseModuleEnum.sourceControl;
        case r'ci':
          return ProviderStatusResponseModuleEnum.ci;
        case r'issue-tracker':
          return ProviderStatusResponseModuleEnum.issueTracker;
        case r'release':
          return ProviderStatusResponseModuleEnum.release;
        default:
          if (!allowNull) {
            throw ArgumentError('Unknown enum value to decode: $data');
          }
      }
    }
    return null;
  }

  /// Singleton [ProviderStatusResponseModuleEnumTypeTransformer] instance.
  static ProviderStatusResponseModuleEnumTypeTransformer? _instance;
}

/// Evidence-backed implementation maturity for provider candidates. Contract-only providers are never member-available.
class ProviderStatusResponseProviderRealityLevelEnum {
  /// Instantiate a new enum with the provided [value].
  const ProviderStatusResponseProviderRealityLevelEnum._(this.value);

  /// The underlying value of this enum member.
  final String value;

  @override
  String toString() => value;

  String toJson() => value;

  static const contractOnly =
      ProviderStatusResponseProviderRealityLevelEnum._(r'contract_only');
  static const configured =
      ProviderStatusResponseProviderRealityLevelEnum._(r'configured');
  static const liveRead =
      ProviderStatusResponseProviderRealityLevelEnum._(r'live_read');
  static const liveWrite =
      ProviderStatusResponseProviderRealityLevelEnum._(r'live_write');
  static const migrationDryRun =
      ProviderStatusResponseProviderRealityLevelEnum._(r'migration_dry_run');
  static const migrationApplyReady =
      ProviderStatusResponseProviderRealityLevelEnum._(
          r'migration_apply_ready');
  static const rollbackReady =
      ProviderStatusResponseProviderRealityLevelEnum._(r'rollback_ready');
  static const releaseReady =
      ProviderStatusResponseProviderRealityLevelEnum._(r'release_ready');

  /// List of all possible values in this [enum][ProviderStatusResponseProviderRealityLevelEnum].
  static const values = <ProviderStatusResponseProviderRealityLevelEnum>[
    contractOnly,
    configured,
    liveRead,
    liveWrite,
    migrationDryRun,
    migrationApplyReady,
    rollbackReady,
    releaseReady,
  ];

  static ProviderStatusResponseProviderRealityLevelEnum? fromJson(
          dynamic value) =>
      ProviderStatusResponseProviderRealityLevelEnumTypeTransformer()
          .decode(value);

  static List<ProviderStatusResponseProviderRealityLevelEnum> listFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final result = <ProviderStatusResponseProviderRealityLevelEnum>[];
    if (json is List && json.isNotEmpty) {
      for (final row in json) {
        final value =
            ProviderStatusResponseProviderRealityLevelEnum.fromJson(row);
        if (value != null) {
          result.add(value);
        }
      }
    }
    return result.toList(growable: growable);
  }
}

/// Transformation class that can [encode] an instance of [ProviderStatusResponseProviderRealityLevelEnum] to String,
/// and [decode] dynamic data back to [ProviderStatusResponseProviderRealityLevelEnum].
class ProviderStatusResponseProviderRealityLevelEnumTypeTransformer {
  factory ProviderStatusResponseProviderRealityLevelEnumTypeTransformer() =>
      _instance ??=
          const ProviderStatusResponseProviderRealityLevelEnumTypeTransformer
              ._();

  const ProviderStatusResponseProviderRealityLevelEnumTypeTransformer._();

  String encode(ProviderStatusResponseProviderRealityLevelEnum data) =>
      data.value;

  /// Decodes a [dynamic value][data] to a ProviderStatusResponseProviderRealityLevelEnum.
  ///
  /// If [allowNull] is true and the [dynamic value][data] cannot be decoded successfully,
  /// then null is returned. However, if [allowNull] is false and the [dynamic value][data]
  /// cannot be decoded successfully, then an [UnimplementedError] is thrown.
  ///
  /// The [allowNull] is very handy when an API changes and a new enum value is added or removed,
  /// and users are still using an old app with the old code.
  ProviderStatusResponseProviderRealityLevelEnum? decode(dynamic data,
      {bool allowNull = true}) {
    if (data != null) {
      switch (data) {
        case r'contract_only':
          return ProviderStatusResponseProviderRealityLevelEnum.contractOnly;
        case r'configured':
          return ProviderStatusResponseProviderRealityLevelEnum.configured;
        case r'live_read':
          return ProviderStatusResponseProviderRealityLevelEnum.liveRead;
        case r'live_write':
          return ProviderStatusResponseProviderRealityLevelEnum.liveWrite;
        case r'migration_dry_run':
          return ProviderStatusResponseProviderRealityLevelEnum.migrationDryRun;
        case r'migration_apply_ready':
          return ProviderStatusResponseProviderRealityLevelEnum
              .migrationApplyReady;
        case r'rollback_ready':
          return ProviderStatusResponseProviderRealityLevelEnum.rollbackReady;
        case r'release_ready':
          return ProviderStatusResponseProviderRealityLevelEnum.releaseReady;
        default:
          if (!allowNull) {
            throw ArgumentError('Unknown enum value to decode: $data');
          }
      }
    }
    return null;
  }

  /// Singleton [ProviderStatusResponseProviderRealityLevelEnumTypeTransformer] instance.
  static ProviderStatusResponseProviderRealityLevelEnumTypeTransformer?
      _instance;
}

class ProviderStatusResponseStateEnum {
  /// Instantiate a new enum with the provided [value].
  const ProviderStatusResponseStateEnum._(this.value);

  /// The underlying value of this enum member.
  final String value;

  @override
  String toString() => value;

  String toJson() => value;

  static const disabled = ProviderStatusResponseStateEnum._(r'disabled');
  static const notConfigured =
      ProviderStatusResponseStateEnum._(r'not_configured');
  static const configured = ProviderStatusResponseStateEnum._(r'configured');
  static const ready = ProviderStatusResponseStateEnum._(r'ready');
  static const degraded = ProviderStatusResponseStateEnum._(r'degraded');
  static const unsupported = ProviderStatusResponseStateEnum._(r'unsupported');

  /// List of all possible values in this [enum][ProviderStatusResponseStateEnum].
  static const values = <ProviderStatusResponseStateEnum>[
    disabled,
    notConfigured,
    configured,
    ready,
    degraded,
    unsupported,
  ];

  static ProviderStatusResponseStateEnum? fromJson(dynamic value) =>
      ProviderStatusResponseStateEnumTypeTransformer().decode(value);

  static List<ProviderStatusResponseStateEnum> listFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final result = <ProviderStatusResponseStateEnum>[];
    if (json is List && json.isNotEmpty) {
      for (final row in json) {
        final value = ProviderStatusResponseStateEnum.fromJson(row);
        if (value != null) {
          result.add(value);
        }
      }
    }
    return result.toList(growable: growable);
  }
}

/// Transformation class that can [encode] an instance of [ProviderStatusResponseStateEnum] to String,
/// and [decode] dynamic data back to [ProviderStatusResponseStateEnum].
class ProviderStatusResponseStateEnumTypeTransformer {
  factory ProviderStatusResponseStateEnumTypeTransformer() =>
      _instance ??= const ProviderStatusResponseStateEnumTypeTransformer._();

  const ProviderStatusResponseStateEnumTypeTransformer._();

  String encode(ProviderStatusResponseStateEnum data) => data.value;

  /// Decodes a [dynamic value][data] to a ProviderStatusResponseStateEnum.
  ///
  /// If [allowNull] is true and the [dynamic value][data] cannot be decoded successfully,
  /// then null is returned. However, if [allowNull] is false and the [dynamic value][data]
  /// cannot be decoded successfully, then an [UnimplementedError] is thrown.
  ///
  /// The [allowNull] is very handy when an API changes and a new enum value is added or removed,
  /// and users are still using an old app with the old code.
  ProviderStatusResponseStateEnum? decode(dynamic data,
      {bool allowNull = true}) {
    if (data != null) {
      switch (data) {
        case r'disabled':
          return ProviderStatusResponseStateEnum.disabled;
        case r'not_configured':
          return ProviderStatusResponseStateEnum.notConfigured;
        case r'configured':
          return ProviderStatusResponseStateEnum.configured;
        case r'ready':
          return ProviderStatusResponseStateEnum.ready;
        case r'degraded':
          return ProviderStatusResponseStateEnum.degraded;
        case r'unsupported':
          return ProviderStatusResponseStateEnum.unsupported;
        default:
          if (!allowNull) {
            throw ArgumentError('Unknown enum value to decode: $data');
          }
      }
    }
    return null;
  }

  /// Singleton [ProviderStatusResponseStateEnumTypeTransformer] instance.
  static ProviderStatusResponseStateEnumTypeTransformer? _instance;
}
