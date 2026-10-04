//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//
// @dart=2.18

// ignore_for_file: unused_element, unused_import
// ignore_for_file: always_put_required_named_parameters_first
// ignore_for_file: constant_identifier_names
// ignore_for_file: lines_longer_than_80_chars

part of weave_user_api;

class ProviderCategoryContractResponse {
  /// Returns a new [ProviderCategoryContractResponse] instance.
  ProviderCategoryContractResponse({
    this.adapterModules = const [],
    this.adminSelectable,
    this.canonicalObjects = const [],
    this.category,
    this.choiceModels = const [],
    this.defaultAdapters = const [],
    this.exportDeleteExpectation,
    this.externalAdapters = const [],
    this.featureCapabilities = const [],
    this.lossyMappingRisks = const [],
    this.normalMembersConfigureProviders,
    this.replacementRequirement,
    this.sourceOfTruth,
    this.stableMemberImpactStates = const [],
  });

  /// Operational provider modules that can currently report readiness for this category.
  List<String> adapterModules;

  /// Whether admins/operators may select or change the adapter for this category.
  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  bool? adminSelectable;

  /// Canonical Weave domain objects that adapters must map into without leaking provider schemas.
  List<String> canonicalObjects;

  /// Stable provider-neutral category key.
  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? category;

  /// Risk-aware admin choice models for recommended self-hosted defaults, existing external providers, managed cloud providers, and hybrid composites.
  List<ProviderChoiceModelResponse> choiceModels;

  /// Current self-hosted dogfood/default adapter keys for this category.
  List<String> defaultAdapters;

  /// Export/delete/deprovision expectation used to avoid Weave becoming a silo.
  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? exportDeleteExpectation;

  /// External or future adapter keys proving the category is not coupled to the dogfood default.
  List<String> externalAdapters;

  /// Category-level feature capability keys enforced by Weave policy/facades.
  List<String> featureCapabilities;

  /// Adapter fields or semantics that require explicit lossy mapping notes.
  List<String> lossyMappingRisks;

  /// Always false: normal members never configure raw providers.
  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  bool? normalMembersConfigureProviders;

  /// Provider replacement or migration dry-run expectation.
  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? replacementRequirement;

  /// Admin-visible source-of-truth rule for this category.
  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? sourceOfTruth;

  /// Member-visible impact states that must remain stable even when an admin swaps adapters.
  List<String> stableMemberImpactStates;

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is ProviderCategoryContractResponse &&
          _deepEquality.equals(other.adapterModules, adapterModules) &&
          other.adminSelectable == adminSelectable &&
          _deepEquality.equals(other.canonicalObjects, canonicalObjects) &&
          other.category == category &&
          _deepEquality.equals(other.choiceModels, choiceModels) &&
          _deepEquality.equals(other.defaultAdapters, defaultAdapters) &&
          other.exportDeleteExpectation == exportDeleteExpectation &&
          _deepEquality.equals(other.externalAdapters, externalAdapters) &&
          _deepEquality.equals(
              other.featureCapabilities, featureCapabilities) &&
          _deepEquality.equals(other.lossyMappingRisks, lossyMappingRisks) &&
          other.normalMembersConfigureProviders ==
              normalMembersConfigureProviders &&
          other.replacementRequirement == replacementRequirement &&
          other.sourceOfTruth == sourceOfTruth &&
          _deepEquality.equals(
              other.stableMemberImpactStates, stableMemberImpactStates);

  @override
  int get hashCode =>
      // ignore: unnecessary_parenthesis
      (adapterModules.hashCode) +
      (adminSelectable == null ? 0 : adminSelectable!.hashCode) +
      (canonicalObjects.hashCode) +
      (category == null ? 0 : category!.hashCode) +
      (choiceModels.hashCode) +
      (defaultAdapters.hashCode) +
      (exportDeleteExpectation == null
          ? 0
          : exportDeleteExpectation!.hashCode) +
      (externalAdapters.hashCode) +
      (featureCapabilities.hashCode) +
      (lossyMappingRisks.hashCode) +
      (normalMembersConfigureProviders == null
          ? 0
          : normalMembersConfigureProviders!.hashCode) +
      (replacementRequirement == null ? 0 : replacementRequirement!.hashCode) +
      (sourceOfTruth == null ? 0 : sourceOfTruth!.hashCode) +
      (stableMemberImpactStates.hashCode);

  @override
  String toString() =>
      'ProviderCategoryContractResponse[adapterModules=$adapterModules, adminSelectable=$adminSelectable, canonicalObjects=$canonicalObjects, category=$category, choiceModels=$choiceModels, defaultAdapters=$defaultAdapters, exportDeleteExpectation=$exportDeleteExpectation, externalAdapters=$externalAdapters, featureCapabilities=$featureCapabilities, lossyMappingRisks=$lossyMappingRisks, normalMembersConfigureProviders=$normalMembersConfigureProviders, replacementRequirement=$replacementRequirement, sourceOfTruth=$sourceOfTruth, stableMemberImpactStates=$stableMemberImpactStates]';

  Map<String, dynamic> toJson() {
    final json = <String, dynamic>{};
    json[r'adapterModules'] = this.adapterModules;
    if (this.adminSelectable != null) {
      json[r'adminSelectable'] = this.adminSelectable;
    } else {
      json[r'adminSelectable'] = null;
    }
    json[r'canonicalObjects'] = this.canonicalObjects;
    if (this.category != null) {
      json[r'category'] = this.category;
    } else {
      json[r'category'] = null;
    }
    json[r'choiceModels'] = this.choiceModels;
    json[r'defaultAdapters'] = this.defaultAdapters;
    if (this.exportDeleteExpectation != null) {
      json[r'exportDeleteExpectation'] = this.exportDeleteExpectation;
    } else {
      json[r'exportDeleteExpectation'] = null;
    }
    json[r'externalAdapters'] = this.externalAdapters;
    json[r'featureCapabilities'] = this.featureCapabilities;
    json[r'lossyMappingRisks'] = this.lossyMappingRisks;
    if (this.normalMembersConfigureProviders != null) {
      json[r'normalMembersConfigureProviders'] =
          this.normalMembersConfigureProviders;
    } else {
      json[r'normalMembersConfigureProviders'] = null;
    }
    if (this.replacementRequirement != null) {
      json[r'replacementRequirement'] = this.replacementRequirement;
    } else {
      json[r'replacementRequirement'] = null;
    }
    if (this.sourceOfTruth != null) {
      json[r'sourceOfTruth'] = this.sourceOfTruth;
    } else {
      json[r'sourceOfTruth'] = null;
    }
    json[r'stableMemberImpactStates'] = this.stableMemberImpactStates;
    return json;
  }

  /// Returns a new [ProviderCategoryContractResponse] instance and imports its values from
  /// [value] if it's a [Map], null otherwise.
  // ignore: prefer_constructors_over_static_methods
  static ProviderCategoryContractResponse? fromJson(dynamic value) {
    if (value is Map) {
      final json = value.cast<String, dynamic>();

      // Ensure that the map contains the required keys.
      // Note 1: the values aren't checked for validity beyond being non-null.
      // Note 2: this code is stripped in release mode!
      assert(() {
        requiredKeys.forEach((key) {
          assert(json.containsKey(key),
              'Required key "ProviderCategoryContractResponse[$key]" is missing from JSON.');
          assert(json[key] != null,
              'Required key "ProviderCategoryContractResponse[$key]" has a null value in JSON.');
        });
        return true;
      }());

      return ProviderCategoryContractResponse(
        adapterModules: json[r'adapterModules'] is Iterable
            ? (json[r'adapterModules'] as Iterable)
                .cast<String>()
                .toList(growable: false)
            : const [],
        adminSelectable: mapValueOfType<bool>(json, r'adminSelectable'),
        canonicalObjects: json[r'canonicalObjects'] is Iterable
            ? (json[r'canonicalObjects'] as Iterable)
                .cast<String>()
                .toList(growable: false)
            : const [],
        category: mapValueOfType<String>(json, r'category'),
        choiceModels:
            ProviderChoiceModelResponse.listFromJson(json[r'choiceModels']),
        defaultAdapters: json[r'defaultAdapters'] is Iterable
            ? (json[r'defaultAdapters'] as Iterable)
                .cast<String>()
                .toList(growable: false)
            : const [],
        exportDeleteExpectation:
            mapValueOfType<String>(json, r'exportDeleteExpectation'),
        externalAdapters: json[r'externalAdapters'] is Iterable
            ? (json[r'externalAdapters'] as Iterable)
                .cast<String>()
                .toList(growable: false)
            : const [],
        featureCapabilities: json[r'featureCapabilities'] is Iterable
            ? (json[r'featureCapabilities'] as Iterable)
                .cast<String>()
                .toList(growable: false)
            : const [],
        lossyMappingRisks: json[r'lossyMappingRisks'] is Iterable
            ? (json[r'lossyMappingRisks'] as Iterable)
                .cast<String>()
                .toList(growable: false)
            : const [],
        normalMembersConfigureProviders:
            mapValueOfType<bool>(json, r'normalMembersConfigureProviders'),
        replacementRequirement:
            mapValueOfType<String>(json, r'replacementRequirement'),
        sourceOfTruth: mapValueOfType<String>(json, r'sourceOfTruth'),
        stableMemberImpactStates: json[r'stableMemberImpactStates'] is Iterable
            ? (json[r'stableMemberImpactStates'] as Iterable)
                .cast<String>()
                .toList(growable: false)
            : const [],
      );
    }
    return null;
  }

  static List<ProviderCategoryContractResponse> listFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final result = <ProviderCategoryContractResponse>[];
    if (json is List && json.isNotEmpty) {
      for (final row in json) {
        final value = ProviderCategoryContractResponse.fromJson(row);
        if (value != null) {
          result.add(value);
        }
      }
    }
    return result.toList(growable: growable);
  }

  static Map<String, ProviderCategoryContractResponse> mapFromJson(
      dynamic json) {
    final map = <String, ProviderCategoryContractResponse>{};
    if (json is Map && json.isNotEmpty) {
      json = json.cast<String, dynamic>(); // ignore: parameter_assignments
      for (final entry in json.entries) {
        final value = ProviderCategoryContractResponse.fromJson(entry.value);
        if (value != null) {
          map[entry.key] = value;
        }
      }
    }
    return map;
  }

  // maps a json object with a list of ProviderCategoryContractResponse-objects as value to a dart map
  static Map<String, List<ProviderCategoryContractResponse>> mapListFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final map = <String, List<ProviderCategoryContractResponse>>{};
    if (json is Map && json.isNotEmpty) {
      // ignore: parameter_assignments
      json = json.cast<String, dynamic>();
      for (final entry in json.entries) {
        map[entry.key] = ProviderCategoryContractResponse.listFromJson(
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
