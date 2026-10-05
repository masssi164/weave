//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//
// @dart=2.18

// ignore_for_file: unused_element, unused_import
// ignore_for_file: always_put_required_named_parameters_first
// ignore_for_file: constant_identifier_names
// ignore_for_file: lines_longer_than_80_chars

part of weave_user_api;

class ProviderRegistryResponse {
  /// Returns a new [ProviderRegistryResponse] instance.
  ProviderRegistryResponse({
    this.adminSelectedMappingsRequired,
    this.backendOwnedFacades,
    this.bootstrapDefaultsAreSuggestionsOnly,
    this.canonicalDomainRegistry,
    this.categories = const [],
    this.domainAdapterRegistry,
    this.flutterDirectProviderCallsAllowed,
    this.generatedAt,
    this.providerConfigSource,
    this.providers = const [],
    this.releaseStatus,
    this.selectedProviderMappings = const [],
    this.supportSafe,
  });

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  bool? adminSelectedMappingsRequired;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  bool? backendOwnedFacades;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  bool? bootstrapDefaultsAreSuggestionsOnly;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  CanonicalDomainRegistryResponse? canonicalDomainRegistry;

  List<ProviderCategoryStatusResponse> categories;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  DomainAdapterRegistryResponse? domainAdapterRegistry;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  bool? flutterDirectProviderCallsAllowed;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  DateTime? generatedAt;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? providerConfigSource;

  List<ProviderStatusResponse> providers;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? releaseStatus;

  List<ProviderSelection> selectedProviderMappings;

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
      other is ProviderRegistryResponse &&
          other.adminSelectedMappingsRequired ==
              adminSelectedMappingsRequired &&
          other.backendOwnedFacades == backendOwnedFacades &&
          other.bootstrapDefaultsAreSuggestionsOnly ==
              bootstrapDefaultsAreSuggestionsOnly &&
          other.canonicalDomainRegistry == canonicalDomainRegistry &&
          _deepEquality.equals(other.categories, categories) &&
          other.domainAdapterRegistry == domainAdapterRegistry &&
          other.flutterDirectProviderCallsAllowed ==
              flutterDirectProviderCallsAllowed &&
          other.generatedAt == generatedAt &&
          other.providerConfigSource == providerConfigSource &&
          _deepEquality.equals(other.providers, providers) &&
          other.releaseStatus == releaseStatus &&
          _deepEquality.equals(
              other.selectedProviderMappings, selectedProviderMappings) &&
          other.supportSafe == supportSafe;

  @override
  int get hashCode =>
      // ignore: unnecessary_parenthesis
      (adminSelectedMappingsRequired == null
          ? 0
          : adminSelectedMappingsRequired!.hashCode) +
      (backendOwnedFacades == null ? 0 : backendOwnedFacades!.hashCode) +
      (bootstrapDefaultsAreSuggestionsOnly == null
          ? 0
          : bootstrapDefaultsAreSuggestionsOnly!.hashCode) +
      (canonicalDomainRegistry == null
          ? 0
          : canonicalDomainRegistry!.hashCode) +
      (categories.hashCode) +
      (domainAdapterRegistry == null ? 0 : domainAdapterRegistry!.hashCode) +
      (flutterDirectProviderCallsAllowed == null
          ? 0
          : flutterDirectProviderCallsAllowed!.hashCode) +
      (generatedAt == null ? 0 : generatedAt!.hashCode) +
      (providerConfigSource == null ? 0 : providerConfigSource!.hashCode) +
      (providers.hashCode) +
      (releaseStatus == null ? 0 : releaseStatus!.hashCode) +
      (selectedProviderMappings.hashCode) +
      (supportSafe == null ? 0 : supportSafe!.hashCode);

  @override
  String toString() =>
      'ProviderRegistryResponse[adminSelectedMappingsRequired=$adminSelectedMappingsRequired, backendOwnedFacades=$backendOwnedFacades, bootstrapDefaultsAreSuggestionsOnly=$bootstrapDefaultsAreSuggestionsOnly, canonicalDomainRegistry=$canonicalDomainRegistry, categories=$categories, domainAdapterRegistry=$domainAdapterRegistry, flutterDirectProviderCallsAllowed=$flutterDirectProviderCallsAllowed, generatedAt=$generatedAt, providerConfigSource=$providerConfigSource, providers=$providers, releaseStatus=$releaseStatus, selectedProviderMappings=$selectedProviderMappings, supportSafe=$supportSafe]';

  Map<String, dynamic> toJson() {
    final json = <String, dynamic>{};
    if (this.adminSelectedMappingsRequired != null) {
      json[r'adminSelectedMappingsRequired'] =
          this.adminSelectedMappingsRequired;
    } else {
      json[r'adminSelectedMappingsRequired'] = null;
    }
    if (this.backendOwnedFacades != null) {
      json[r'backendOwnedFacades'] = this.backendOwnedFacades;
    } else {
      json[r'backendOwnedFacades'] = null;
    }
    if (this.bootstrapDefaultsAreSuggestionsOnly != null) {
      json[r'bootstrapDefaultsAreSuggestionsOnly'] =
          this.bootstrapDefaultsAreSuggestionsOnly;
    } else {
      json[r'bootstrapDefaultsAreSuggestionsOnly'] = null;
    }
    if (this.canonicalDomainRegistry != null) {
      json[r'canonicalDomainRegistry'] = this.canonicalDomainRegistry;
    } else {
      json[r'canonicalDomainRegistry'] = null;
    }
    json[r'categories'] = this.categories;
    if (this.domainAdapterRegistry != null) {
      json[r'domainAdapterRegistry'] = this.domainAdapterRegistry;
    } else {
      json[r'domainAdapterRegistry'] = null;
    }
    if (this.flutterDirectProviderCallsAllowed != null) {
      json[r'flutterDirectProviderCallsAllowed'] =
          this.flutterDirectProviderCallsAllowed;
    } else {
      json[r'flutterDirectProviderCallsAllowed'] = null;
    }
    if (this.generatedAt != null) {
      json[r'generatedAt'] = this.generatedAt!.toUtc().toIso8601String();
    } else {
      json[r'generatedAt'] = null;
    }
    if (this.providerConfigSource != null) {
      json[r'providerConfigSource'] = this.providerConfigSource;
    } else {
      json[r'providerConfigSource'] = null;
    }
    json[r'providers'] = this.providers;
    if (this.releaseStatus != null) {
      json[r'releaseStatus'] = this.releaseStatus;
    } else {
      json[r'releaseStatus'] = null;
    }
    json[r'selectedProviderMappings'] = this.selectedProviderMappings;
    if (this.supportSafe != null) {
      json[r'supportSafe'] = this.supportSafe;
    } else {
      json[r'supportSafe'] = null;
    }
    return json;
  }

  /// Returns a new [ProviderRegistryResponse] instance and imports its values from
  /// [value] if it's a [Map], null otherwise.
  // ignore: prefer_constructors_over_static_methods
  static ProviderRegistryResponse? fromJson(dynamic value) {
    if (value is Map) {
      final json = value.cast<String, dynamic>();

      // Ensure that the map contains the required keys.
      // Note 1: the values aren't checked for validity beyond being non-null.
      // Note 2: this code is stripped in release mode!
      assert(() {
        requiredKeys.forEach((key) {
          assert(json.containsKey(key),
              'Required key "ProviderRegistryResponse[$key]" is missing from JSON.');
          assert(json[key] != null,
              'Required key "ProviderRegistryResponse[$key]" has a null value in JSON.');
        });
        return true;
      }());

      return ProviderRegistryResponse(
        adminSelectedMappingsRequired:
            mapValueOfType<bool>(json, r'adminSelectedMappingsRequired'),
        backendOwnedFacades: mapValueOfType<bool>(json, r'backendOwnedFacades'),
        bootstrapDefaultsAreSuggestionsOnly:
            mapValueOfType<bool>(json, r'bootstrapDefaultsAreSuggestionsOnly'),
        canonicalDomainRegistry: CanonicalDomainRegistryResponse.fromJson(
            json[r'canonicalDomainRegistry']),
        categories:
            ProviderCategoryStatusResponse.listFromJson(json[r'categories']),
        domainAdapterRegistry: DomainAdapterRegistryResponse.fromJson(
            json[r'domainAdapterRegistry']),
        flutterDirectProviderCallsAllowed:
            mapValueOfType<bool>(json, r'flutterDirectProviderCallsAllowed'),
        generatedAt: mapDateTime(json, r'generatedAt', r''),
        providerConfigSource:
            mapValueOfType<String>(json, r'providerConfigSource'),
        providers: ProviderStatusResponse.listFromJson(json[r'providers']),
        releaseStatus: mapValueOfType<String>(json, r'releaseStatus'),
        selectedProviderMappings:
            ProviderSelection.listFromJson(json[r'selectedProviderMappings']),
        supportSafe: mapValueOfType<bool>(json, r'supportSafe'),
      );
    }
    return null;
  }

  static List<ProviderRegistryResponse> listFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final result = <ProviderRegistryResponse>[];
    if (json is List && json.isNotEmpty) {
      for (final row in json) {
        final value = ProviderRegistryResponse.fromJson(row);
        if (value != null) {
          result.add(value);
        }
      }
    }
    return result.toList(growable: growable);
  }

  static Map<String, ProviderRegistryResponse> mapFromJson(dynamic json) {
    final map = <String, ProviderRegistryResponse>{};
    if (json is Map && json.isNotEmpty) {
      json = json.cast<String, dynamic>(); // ignore: parameter_assignments
      for (final entry in json.entries) {
        final value = ProviderRegistryResponse.fromJson(entry.value);
        if (value != null) {
          map[entry.key] = value;
        }
      }
    }
    return map;
  }

  // maps a json object with a list of ProviderRegistryResponse-objects as value to a dart map
  static Map<String, List<ProviderRegistryResponse>> mapListFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final map = <String, List<ProviderRegistryResponse>>{};
    if (json is Map && json.isNotEmpty) {
      // ignore: parameter_assignments
      json = json.cast<String, dynamic>();
      for (final entry in json.entries) {
        map[entry.key] = ProviderRegistryResponse.listFromJson(
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
