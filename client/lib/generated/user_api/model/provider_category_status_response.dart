//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//
// @dart=2.18

// ignore_for_file: unused_element, unused_import
// ignore_for_file: always_put_required_named_parameters_first
// ignore_for_file: constant_identifier_names
// ignore_for_file: lines_longer_than_80_chars

part of weave_user_api;

class ProviderCategoryStatusResponse {
  /// Returns a new [ProviderCategoryStatusResponse] instance.
  ProviderCategoryStatusResponse({
    this.adapterEvidence = const [],
    this.bootstrapSuggestionOnly,
    this.category,
    this.choiceModel,
    this.contract,
    this.diagnostics = const {},
    this.label,
    this.lossyMappingNotes = const [],
    this.memberCapabilityState,
    this.memberImpact,
    this.modules = const [],
    this.policyState,
    this.providerCandidates = const [],
    this.providerRealityLevel,
    this.readiness,
    this.realityLevelRemediation,
    this.selectedByAdmin,
    this.selectedProviderKey,
  });

  /// Support-safe infra/backend adapter readiness evidence for Admin Console and support bundles.
  List<ProviderAdapterReadinessEvidenceResponse> adapterEvidence;

  /// True when defaults are being shown only as bootstrap/profile suggestions, not product truth.
  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  bool? bootstrapSuggestionOnly;

  /// Stable provider-neutral category key.
  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? category;

  /// Selected provider choice model: recommended_self_hosted_default, external_existing_provider, or managed_cloud_provider.
  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? choiceModel;

  /// Provider-neutral capability contract and adapter seam for this category.
  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  ProviderCategoryContractResponse? contract;

  /// Support-safe diagnostics. Values are booleans/counts/keys only; no endpoints, secrets, or raw upstream errors.
  Map<String, Object> diagnostics;

  /// Human-readable provider-neutral category label.
  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? label;

  /// Support-safe notes for known lossy mappings across provider families.
  List<String> lossyMappingNotes;

  /// Stable member capability state derived from policy, readiness, and provider reality level.
  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? memberCapabilityState;

  /// Member-safe impact label. Does not expose raw provider setup.
  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? memberImpact;

  /// Provider registry modules contributing to this category.
  List<String> modules;

  /// Effective capability policy state used for this category.
  ProviderCategoryStatusResponsePolicyStateEnum? policyState;

  /// Support-safe provider choices/candidates for admin diagnostics.
  List<String> providerCandidates;

  /// Evidence-backed provider implementation maturity for this category.
  ProviderCategoryStatusResponseProviderRealityLevelEnum? providerRealityLevel;

  /// Admin readiness state for this category.
  ProviderCategoryStatusResponseReadinessEnum? readiness;

  /// Actionable admin remediation for the current provider reality level.
  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? realityLevelRemediation;

  /// True only after an admin has applied a provider mapping for this category.
  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  bool? selectedByAdmin;

  /// Admin Console-selected provider key, or awaiting_admin_selection when not applied.
  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? selectedProviderKey;

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is ProviderCategoryStatusResponse &&
          _deepEquality.equals(other.adapterEvidence, adapterEvidence) &&
          other.bootstrapSuggestionOnly == bootstrapSuggestionOnly &&
          other.category == category &&
          other.choiceModel == choiceModel &&
          other.contract == contract &&
          _deepEquality.equals(other.diagnostics, diagnostics) &&
          other.label == label &&
          _deepEquality.equals(other.lossyMappingNotes, lossyMappingNotes) &&
          other.memberCapabilityState == memberCapabilityState &&
          other.memberImpact == memberImpact &&
          _deepEquality.equals(other.modules, modules) &&
          other.policyState == policyState &&
          _deepEquality.equals(other.providerCandidates, providerCandidates) &&
          other.providerRealityLevel == providerRealityLevel &&
          other.readiness == readiness &&
          other.realityLevelRemediation == realityLevelRemediation &&
          other.selectedByAdmin == selectedByAdmin &&
          other.selectedProviderKey == selectedProviderKey;

  @override
  int get hashCode =>
      // ignore: unnecessary_parenthesis
      (adapterEvidence.hashCode) +
      (bootstrapSuggestionOnly == null
          ? 0
          : bootstrapSuggestionOnly!.hashCode) +
      (category == null ? 0 : category!.hashCode) +
      (choiceModel == null ? 0 : choiceModel!.hashCode) +
      (contract == null ? 0 : contract!.hashCode) +
      (diagnostics.hashCode) +
      (label == null ? 0 : label!.hashCode) +
      (lossyMappingNotes.hashCode) +
      (memberCapabilityState == null ? 0 : memberCapabilityState!.hashCode) +
      (memberImpact == null ? 0 : memberImpact!.hashCode) +
      (modules.hashCode) +
      (policyState == null ? 0 : policyState!.hashCode) +
      (providerCandidates.hashCode) +
      (providerRealityLevel == null ? 0 : providerRealityLevel!.hashCode) +
      (readiness == null ? 0 : readiness!.hashCode) +
      (realityLevelRemediation == null
          ? 0
          : realityLevelRemediation!.hashCode) +
      (selectedByAdmin == null ? 0 : selectedByAdmin!.hashCode) +
      (selectedProviderKey == null ? 0 : selectedProviderKey!.hashCode);

  @override
  String toString() =>
      'ProviderCategoryStatusResponse[adapterEvidence=$adapterEvidence, bootstrapSuggestionOnly=$bootstrapSuggestionOnly, category=$category, choiceModel=$choiceModel, contract=$contract, diagnostics=$diagnostics, label=$label, lossyMappingNotes=$lossyMappingNotes, memberCapabilityState=$memberCapabilityState, memberImpact=$memberImpact, modules=$modules, policyState=$policyState, providerCandidates=$providerCandidates, providerRealityLevel=$providerRealityLevel, readiness=$readiness, realityLevelRemediation=$realityLevelRemediation, selectedByAdmin=$selectedByAdmin, selectedProviderKey=$selectedProviderKey]';

  Map<String, dynamic> toJson() {
    final json = <String, dynamic>{};
    json[r'adapterEvidence'] = this.adapterEvidence;
    if (this.bootstrapSuggestionOnly != null) {
      json[r'bootstrapSuggestionOnly'] = this.bootstrapSuggestionOnly;
    } else {
      json[r'bootstrapSuggestionOnly'] = null;
    }
    if (this.category != null) {
      json[r'category'] = this.category;
    } else {
      json[r'category'] = null;
    }
    if (this.choiceModel != null) {
      json[r'choiceModel'] = this.choiceModel;
    } else {
      json[r'choiceModel'] = null;
    }
    if (this.contract != null) {
      json[r'contract'] = this.contract;
    } else {
      json[r'contract'] = null;
    }
    json[r'diagnostics'] = this.diagnostics;
    if (this.label != null) {
      json[r'label'] = this.label;
    } else {
      json[r'label'] = null;
    }
    json[r'lossyMappingNotes'] = this.lossyMappingNotes;
    if (this.memberCapabilityState != null) {
      json[r'memberCapabilityState'] = this.memberCapabilityState;
    } else {
      json[r'memberCapabilityState'] = null;
    }
    if (this.memberImpact != null) {
      json[r'memberImpact'] = this.memberImpact;
    } else {
      json[r'memberImpact'] = null;
    }
    json[r'modules'] = this.modules;
    if (this.policyState != null) {
      json[r'policyState'] = this.policyState;
    } else {
      json[r'policyState'] = null;
    }
    json[r'providerCandidates'] = this.providerCandidates;
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
    if (this.selectedByAdmin != null) {
      json[r'selectedByAdmin'] = this.selectedByAdmin;
    } else {
      json[r'selectedByAdmin'] = null;
    }
    if (this.selectedProviderKey != null) {
      json[r'selectedProviderKey'] = this.selectedProviderKey;
    } else {
      json[r'selectedProviderKey'] = null;
    }
    return json;
  }

  /// Returns a new [ProviderCategoryStatusResponse] instance and imports its values from
  /// [value] if it's a [Map], null otherwise.
  // ignore: prefer_constructors_over_static_methods
  static ProviderCategoryStatusResponse? fromJson(dynamic value) {
    if (value is Map) {
      final json = value.cast<String, dynamic>();

      // Ensure that the map contains the required keys.
      // Note 1: the values aren't checked for validity beyond being non-null.
      // Note 2: this code is stripped in release mode!
      assert(() {
        requiredKeys.forEach((key) {
          assert(json.containsKey(key),
              'Required key "ProviderCategoryStatusResponse[$key]" is missing from JSON.');
          assert(json[key] != null,
              'Required key "ProviderCategoryStatusResponse[$key]" has a null value in JSON.');
        });
        return true;
      }());

      return ProviderCategoryStatusResponse(
        adapterEvidence: ProviderAdapterReadinessEvidenceResponse.listFromJson(
            json[r'adapterEvidence']),
        bootstrapSuggestionOnly:
            mapValueOfType<bool>(json, r'bootstrapSuggestionOnly'),
        category: mapValueOfType<String>(json, r'category'),
        choiceModel: mapValueOfType<String>(json, r'choiceModel'),
        contract: ProviderCategoryContractResponse.fromJson(json[r'contract']),
        diagnostics:
            mapCastOfType<String, Object>(json, r'diagnostics') ?? const {},
        label: mapValueOfType<String>(json, r'label'),
        lossyMappingNotes: json[r'lossyMappingNotes'] is Iterable
            ? (json[r'lossyMappingNotes'] as Iterable)
                .cast<String>()
                .toList(growable: false)
            : const [],
        memberCapabilityState:
            mapValueOfType<String>(json, r'memberCapabilityState'),
        memberImpact: mapValueOfType<String>(json, r'memberImpact'),
        modules: json[r'modules'] is Iterable
            ? (json[r'modules'] as Iterable)
                .cast<String>()
                .toList(growable: false)
            : const [],
        policyState: ProviderCategoryStatusResponsePolicyStateEnum.fromJson(
            json[r'policyState']),
        providerCandidates: json[r'providerCandidates'] is Iterable
            ? (json[r'providerCandidates'] as Iterable)
                .cast<String>()
                .toList(growable: false)
            : const [],
        providerRealityLevel:
            ProviderCategoryStatusResponseProviderRealityLevelEnum.fromJson(
                json[r'providerRealityLevel']),
        readiness: ProviderCategoryStatusResponseReadinessEnum.fromJson(
            json[r'readiness']),
        realityLevelRemediation:
            mapValueOfType<String>(json, r'realityLevelRemediation'),
        selectedByAdmin: mapValueOfType<bool>(json, r'selectedByAdmin'),
        selectedProviderKey:
            mapValueOfType<String>(json, r'selectedProviderKey'),
      );
    }
    return null;
  }

  static List<ProviderCategoryStatusResponse> listFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final result = <ProviderCategoryStatusResponse>[];
    if (json is List && json.isNotEmpty) {
      for (final row in json) {
        final value = ProviderCategoryStatusResponse.fromJson(row);
        if (value != null) {
          result.add(value);
        }
      }
    }
    return result.toList(growable: growable);
  }

  static Map<String, ProviderCategoryStatusResponse> mapFromJson(dynamic json) {
    final map = <String, ProviderCategoryStatusResponse>{};
    if (json is Map && json.isNotEmpty) {
      json = json.cast<String, dynamic>(); // ignore: parameter_assignments
      for (final entry in json.entries) {
        final value = ProviderCategoryStatusResponse.fromJson(entry.value);
        if (value != null) {
          map[entry.key] = value;
        }
      }
    }
    return map;
  }

  // maps a json object with a list of ProviderCategoryStatusResponse-objects as value to a dart map
  static Map<String, List<ProviderCategoryStatusResponse>> mapListFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final map = <String, List<ProviderCategoryStatusResponse>>{};
    if (json is Map && json.isNotEmpty) {
      // ignore: parameter_assignments
      json = json.cast<String, dynamic>();
      for (final entry in json.entries) {
        map[entry.key] = ProviderCategoryStatusResponse.listFromJson(
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

/// Effective capability policy state used for this category.
class ProviderCategoryStatusResponsePolicyStateEnum {
  /// Instantiate a new enum with the provided [value].
  const ProviderCategoryStatusResponsePolicyStateEnum._(this.value);

  /// The underlying value of this enum member.
  final String value;

  @override
  String toString() => value;

  String toJson() => value;

  static const allowed =
      ProviderCategoryStatusResponsePolicyStateEnum._(r'allowed');
  static const policyBlocked =
      ProviderCategoryStatusResponsePolicyStateEnum._(r'policy_blocked');
  static const disabled =
      ProviderCategoryStatusResponsePolicyStateEnum._(r'disabled');
  static const unavailable =
      ProviderCategoryStatusResponsePolicyStateEnum._(r'unavailable');

  /// List of all possible values in this [enum][ProviderCategoryStatusResponsePolicyStateEnum].
  static const values = <ProviderCategoryStatusResponsePolicyStateEnum>[
    allowed,
    policyBlocked,
    disabled,
    unavailable,
  ];

  static ProviderCategoryStatusResponsePolicyStateEnum? fromJson(
          dynamic value) =>
      ProviderCategoryStatusResponsePolicyStateEnumTypeTransformer()
          .decode(value);

  static List<ProviderCategoryStatusResponsePolicyStateEnum> listFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final result = <ProviderCategoryStatusResponsePolicyStateEnum>[];
    if (json is List && json.isNotEmpty) {
      for (final row in json) {
        final value =
            ProviderCategoryStatusResponsePolicyStateEnum.fromJson(row);
        if (value != null) {
          result.add(value);
        }
      }
    }
    return result.toList(growable: growable);
  }
}

/// Transformation class that can [encode] an instance of [ProviderCategoryStatusResponsePolicyStateEnum] to String,
/// and [decode] dynamic data back to [ProviderCategoryStatusResponsePolicyStateEnum].
class ProviderCategoryStatusResponsePolicyStateEnumTypeTransformer {
  factory ProviderCategoryStatusResponsePolicyStateEnumTypeTransformer() =>
      _instance ??=
          const ProviderCategoryStatusResponsePolicyStateEnumTypeTransformer
              ._();

  const ProviderCategoryStatusResponsePolicyStateEnumTypeTransformer._();

  String encode(ProviderCategoryStatusResponsePolicyStateEnum data) =>
      data.value;

  /// Decodes a [dynamic value][data] to a ProviderCategoryStatusResponsePolicyStateEnum.
  ///
  /// If [allowNull] is true and the [dynamic value][data] cannot be decoded successfully,
  /// then null is returned. However, if [allowNull] is false and the [dynamic value][data]
  /// cannot be decoded successfully, then an [UnimplementedError] is thrown.
  ///
  /// The [allowNull] is very handy when an API changes and a new enum value is added or removed,
  /// and users are still using an old app with the old code.
  ProviderCategoryStatusResponsePolicyStateEnum? decode(dynamic data,
      {bool allowNull = true}) {
    if (data != null) {
      switch (data) {
        case r'allowed':
          return ProviderCategoryStatusResponsePolicyStateEnum.allowed;
        case r'policy_blocked':
          return ProviderCategoryStatusResponsePolicyStateEnum.policyBlocked;
        case r'disabled':
          return ProviderCategoryStatusResponsePolicyStateEnum.disabled;
        case r'unavailable':
          return ProviderCategoryStatusResponsePolicyStateEnum.unavailable;
        default:
          if (!allowNull) {
            throw ArgumentError('Unknown enum value to decode: $data');
          }
      }
    }
    return null;
  }

  /// Singleton [ProviderCategoryStatusResponsePolicyStateEnumTypeTransformer] instance.
  static ProviderCategoryStatusResponsePolicyStateEnumTypeTransformer?
      _instance;
}

/// Evidence-backed provider implementation maturity for this category.
class ProviderCategoryStatusResponseProviderRealityLevelEnum {
  /// Instantiate a new enum with the provided [value].
  const ProviderCategoryStatusResponseProviderRealityLevelEnum._(this.value);

  /// The underlying value of this enum member.
  final String value;

  @override
  String toString() => value;

  String toJson() => value;

  static const contractOnly =
      ProviderCategoryStatusResponseProviderRealityLevelEnum._(
          r'contract_only');
  static const configured =
      ProviderCategoryStatusResponseProviderRealityLevelEnum._(r'configured');
  static const liveRead =
      ProviderCategoryStatusResponseProviderRealityLevelEnum._(r'live_read');
  static const liveWrite =
      ProviderCategoryStatusResponseProviderRealityLevelEnum._(r'live_write');
  static const migrationDryRun =
      ProviderCategoryStatusResponseProviderRealityLevelEnum._(
          r'migration_dry_run');
  static const migrationApplyReady =
      ProviderCategoryStatusResponseProviderRealityLevelEnum._(
          r'migration_apply_ready');
  static const rollbackReady =
      ProviderCategoryStatusResponseProviderRealityLevelEnum._(
          r'rollback_ready');
  static const releaseReady =
      ProviderCategoryStatusResponseProviderRealityLevelEnum._(
          r'release_ready');

  /// List of all possible values in this [enum][ProviderCategoryStatusResponseProviderRealityLevelEnum].
  static const values =
      <ProviderCategoryStatusResponseProviderRealityLevelEnum>[
    contractOnly,
    configured,
    liveRead,
    liveWrite,
    migrationDryRun,
    migrationApplyReady,
    rollbackReady,
    releaseReady,
  ];

  static ProviderCategoryStatusResponseProviderRealityLevelEnum? fromJson(
          dynamic value) =>
      ProviderCategoryStatusResponseProviderRealityLevelEnumTypeTransformer()
          .decode(value);

  static List<ProviderCategoryStatusResponseProviderRealityLevelEnum>
      listFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final result = <ProviderCategoryStatusResponseProviderRealityLevelEnum>[];
    if (json is List && json.isNotEmpty) {
      for (final row in json) {
        final value =
            ProviderCategoryStatusResponseProviderRealityLevelEnum.fromJson(
                row);
        if (value != null) {
          result.add(value);
        }
      }
    }
    return result.toList(growable: growable);
  }
}

/// Transformation class that can [encode] an instance of [ProviderCategoryStatusResponseProviderRealityLevelEnum] to String,
/// and [decode] dynamic data back to [ProviderCategoryStatusResponseProviderRealityLevelEnum].
class ProviderCategoryStatusResponseProviderRealityLevelEnumTypeTransformer {
  factory ProviderCategoryStatusResponseProviderRealityLevelEnumTypeTransformer() =>
      _instance ??=
          const ProviderCategoryStatusResponseProviderRealityLevelEnumTypeTransformer
              ._();

  const ProviderCategoryStatusResponseProviderRealityLevelEnumTypeTransformer._();

  String encode(ProviderCategoryStatusResponseProviderRealityLevelEnum data) =>
      data.value;

  /// Decodes a [dynamic value][data] to a ProviderCategoryStatusResponseProviderRealityLevelEnum.
  ///
  /// If [allowNull] is true and the [dynamic value][data] cannot be decoded successfully,
  /// then null is returned. However, if [allowNull] is false and the [dynamic value][data]
  /// cannot be decoded successfully, then an [UnimplementedError] is thrown.
  ///
  /// The [allowNull] is very handy when an API changes and a new enum value is added or removed,
  /// and users are still using an old app with the old code.
  ProviderCategoryStatusResponseProviderRealityLevelEnum? decode(dynamic data,
      {bool allowNull = true}) {
    if (data != null) {
      switch (data) {
        case r'contract_only':
          return ProviderCategoryStatusResponseProviderRealityLevelEnum
              .contractOnly;
        case r'configured':
          return ProviderCategoryStatusResponseProviderRealityLevelEnum
              .configured;
        case r'live_read':
          return ProviderCategoryStatusResponseProviderRealityLevelEnum
              .liveRead;
        case r'live_write':
          return ProviderCategoryStatusResponseProviderRealityLevelEnum
              .liveWrite;
        case r'migration_dry_run':
          return ProviderCategoryStatusResponseProviderRealityLevelEnum
              .migrationDryRun;
        case r'migration_apply_ready':
          return ProviderCategoryStatusResponseProviderRealityLevelEnum
              .migrationApplyReady;
        case r'rollback_ready':
          return ProviderCategoryStatusResponseProviderRealityLevelEnum
              .rollbackReady;
        case r'release_ready':
          return ProviderCategoryStatusResponseProviderRealityLevelEnum
              .releaseReady;
        default:
          if (!allowNull) {
            throw ArgumentError('Unknown enum value to decode: $data');
          }
      }
    }
    return null;
  }

  /// Singleton [ProviderCategoryStatusResponseProviderRealityLevelEnumTypeTransformer] instance.
  static ProviderCategoryStatusResponseProviderRealityLevelEnumTypeTransformer?
      _instance;
}

/// Admin readiness state for this category.
class ProviderCategoryStatusResponseReadinessEnum {
  /// Instantiate a new enum with the provided [value].
  const ProviderCategoryStatusResponseReadinessEnum._(this.value);

  /// The underlying value of this enum member.
  final String value;

  @override
  String toString() => value;

  String toJson() => value;

  static const ready = ProviderCategoryStatusResponseReadinessEnum._(r'ready');
  static const disabled =
      ProviderCategoryStatusResponseReadinessEnum._(r'disabled');
  static const degraded =
      ProviderCategoryStatusResponseReadinessEnum._(r'degraded');
  static const policyBlocked =
      ProviderCategoryStatusResponseReadinessEnum._(r'policy_blocked');
  static const misconfigured =
      ProviderCategoryStatusResponseReadinessEnum._(r'misconfigured');

  /// List of all possible values in this [enum][ProviderCategoryStatusResponseReadinessEnum].
  static const values = <ProviderCategoryStatusResponseReadinessEnum>[
    ready,
    disabled,
    degraded,
    policyBlocked,
    misconfigured,
  ];

  static ProviderCategoryStatusResponseReadinessEnum? fromJson(dynamic value) =>
      ProviderCategoryStatusResponseReadinessEnumTypeTransformer()
          .decode(value);

  static List<ProviderCategoryStatusResponseReadinessEnum> listFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final result = <ProviderCategoryStatusResponseReadinessEnum>[];
    if (json is List && json.isNotEmpty) {
      for (final row in json) {
        final value = ProviderCategoryStatusResponseReadinessEnum.fromJson(row);
        if (value != null) {
          result.add(value);
        }
      }
    }
    return result.toList(growable: growable);
  }
}

/// Transformation class that can [encode] an instance of [ProviderCategoryStatusResponseReadinessEnum] to String,
/// and [decode] dynamic data back to [ProviderCategoryStatusResponseReadinessEnum].
class ProviderCategoryStatusResponseReadinessEnumTypeTransformer {
  factory ProviderCategoryStatusResponseReadinessEnumTypeTransformer() =>
      _instance ??=
          const ProviderCategoryStatusResponseReadinessEnumTypeTransformer._();

  const ProviderCategoryStatusResponseReadinessEnumTypeTransformer._();

  String encode(ProviderCategoryStatusResponseReadinessEnum data) => data.value;

  /// Decodes a [dynamic value][data] to a ProviderCategoryStatusResponseReadinessEnum.
  ///
  /// If [allowNull] is true and the [dynamic value][data] cannot be decoded successfully,
  /// then null is returned. However, if [allowNull] is false and the [dynamic value][data]
  /// cannot be decoded successfully, then an [UnimplementedError] is thrown.
  ///
  /// The [allowNull] is very handy when an API changes and a new enum value is added or removed,
  /// and users are still using an old app with the old code.
  ProviderCategoryStatusResponseReadinessEnum? decode(dynamic data,
      {bool allowNull = true}) {
    if (data != null) {
      switch (data) {
        case r'ready':
          return ProviderCategoryStatusResponseReadinessEnum.ready;
        case r'disabled':
          return ProviderCategoryStatusResponseReadinessEnum.disabled;
        case r'degraded':
          return ProviderCategoryStatusResponseReadinessEnum.degraded;
        case r'policy_blocked':
          return ProviderCategoryStatusResponseReadinessEnum.policyBlocked;
        case r'misconfigured':
          return ProviderCategoryStatusResponseReadinessEnum.misconfigured;
        default:
          if (!allowNull) {
            throw ArgumentError('Unknown enum value to decode: $data');
          }
      }
    }
    return null;
  }

  /// Singleton [ProviderCategoryStatusResponseReadinessEnumTypeTransformer] instance.
  static ProviderCategoryStatusResponseReadinessEnumTypeTransformer? _instance;
}
