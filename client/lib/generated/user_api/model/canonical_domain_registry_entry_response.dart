//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//
// @dart=2.18

// ignore_for_file: unused_element, unused_import
// ignore_for_file: always_put_required_named_parameters_first
// ignore_for_file: constant_identifier_names
// ignore_for_file: lines_longer_than_80_chars

part of weave_user_api;

class CanonicalDomainRegistryEntryResponse {
  /// Returns a new [CanonicalDomainRegistryEntryResponse] instance.
  CanonicalDomainRegistryEntryResponse({
    this.adapterManifestRequirements = const [],
    this.adminStates = const [],
    this.canonicalObjects = const [],
    this.capabilityKeys = const [],
    this.compatibilityAliases = const [],
    this.displayName,
    this.key,
    this.memberStates = const [],
    this.portabilityRequirements = const [],
    this.providerRealityLevelByCandidate = const {},
    this.purpose,
    this.sourceOfTruthModes = const [],
    this.version,
  });

  List<String> adapterManifestRequirements;

  List<String> adminStates;

  List<String> canonicalObjects;

  List<String> capabilityKeys;

  List<String> compatibilityAliases;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? displayName;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? key;

  List<String> memberStates;

  List<String> portabilityRequirements;

  Map<String, String> providerRealityLevelByCandidate;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? purpose;

  List<String> sourceOfTruthModes;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  int? version;

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is CanonicalDomainRegistryEntryResponse &&
          _deepEquality.equals(
              other.adapterManifestRequirements, adapterManifestRequirements) &&
          _deepEquality.equals(other.adminStates, adminStates) &&
          _deepEquality.equals(other.canonicalObjects, canonicalObjects) &&
          _deepEquality.equals(other.capabilityKeys, capabilityKeys) &&
          _deepEquality.equals(
              other.compatibilityAliases, compatibilityAliases) &&
          other.displayName == displayName &&
          other.key == key &&
          _deepEquality.equals(other.memberStates, memberStates) &&
          _deepEquality.equals(
              other.portabilityRequirements, portabilityRequirements) &&
          _deepEquality.equals(other.providerRealityLevelByCandidate,
              providerRealityLevelByCandidate) &&
          other.purpose == purpose &&
          _deepEquality.equals(other.sourceOfTruthModes, sourceOfTruthModes) &&
          other.version == version;

  @override
  int get hashCode =>
      // ignore: unnecessary_parenthesis
      (adapterManifestRequirements.hashCode) +
      (adminStates.hashCode) +
      (canonicalObjects.hashCode) +
      (capabilityKeys.hashCode) +
      (compatibilityAliases.hashCode) +
      (displayName == null ? 0 : displayName!.hashCode) +
      (key == null ? 0 : key!.hashCode) +
      (memberStates.hashCode) +
      (portabilityRequirements.hashCode) +
      (providerRealityLevelByCandidate.hashCode) +
      (purpose == null ? 0 : purpose!.hashCode) +
      (sourceOfTruthModes.hashCode) +
      (version == null ? 0 : version!.hashCode);

  @override
  String toString() =>
      'CanonicalDomainRegistryEntryResponse[adapterManifestRequirements=$adapterManifestRequirements, adminStates=$adminStates, canonicalObjects=$canonicalObjects, capabilityKeys=$capabilityKeys, compatibilityAliases=$compatibilityAliases, displayName=$displayName, key=$key, memberStates=$memberStates, portabilityRequirements=$portabilityRequirements, providerRealityLevelByCandidate=$providerRealityLevelByCandidate, purpose=$purpose, sourceOfTruthModes=$sourceOfTruthModes, version=$version]';

  Map<String, dynamic> toJson() {
    final json = <String, dynamic>{};
    json[r'adapterManifestRequirements'] = this.adapterManifestRequirements;
    json[r'adminStates'] = this.adminStates;
    json[r'canonicalObjects'] = this.canonicalObjects;
    json[r'capabilityKeys'] = this.capabilityKeys;
    json[r'compatibilityAliases'] = this.compatibilityAliases;
    if (this.displayName != null) {
      json[r'displayName'] = this.displayName;
    } else {
      json[r'displayName'] = null;
    }
    if (this.key != null) {
      json[r'key'] = this.key;
    } else {
      json[r'key'] = null;
    }
    json[r'memberStates'] = this.memberStates;
    json[r'portabilityRequirements'] = this.portabilityRequirements;
    json[r'providerRealityLevelByCandidate'] =
        this.providerRealityLevelByCandidate;
    if (this.purpose != null) {
      json[r'purpose'] = this.purpose;
    } else {
      json[r'purpose'] = null;
    }
    json[r'sourceOfTruthModes'] = this.sourceOfTruthModes;
    if (this.version != null) {
      json[r'version'] = this.version;
    } else {
      json[r'version'] = null;
    }
    return json;
  }

  /// Returns a new [CanonicalDomainRegistryEntryResponse] instance and imports its values from
  /// [value] if it's a [Map], null otherwise.
  // ignore: prefer_constructors_over_static_methods
  static CanonicalDomainRegistryEntryResponse? fromJson(dynamic value) {
    if (value is Map) {
      final json = value.cast<String, dynamic>();

      // Ensure that the map contains the required keys.
      // Note 1: the values aren't checked for validity beyond being non-null.
      // Note 2: this code is stripped in release mode!
      assert(() {
        requiredKeys.forEach((key) {
          assert(json.containsKey(key),
              'Required key "CanonicalDomainRegistryEntryResponse[$key]" is missing from JSON.');
          assert(json[key] != null,
              'Required key "CanonicalDomainRegistryEntryResponse[$key]" has a null value in JSON.');
        });
        return true;
      }());

      return CanonicalDomainRegistryEntryResponse(
        adapterManifestRequirements:
            json[r'adapterManifestRequirements'] is Iterable
                ? (json[r'adapterManifestRequirements'] as Iterable)
                    .cast<String>()
                    .toList(growable: false)
                : const [],
        adminStates: json[r'adminStates'] is Iterable
            ? (json[r'adminStates'] as Iterable)
                .cast<String>()
                .toList(growable: false)
            : const [],
        canonicalObjects: json[r'canonicalObjects'] is Iterable
            ? (json[r'canonicalObjects'] as Iterable)
                .cast<String>()
                .toList(growable: false)
            : const [],
        capabilityKeys: json[r'capabilityKeys'] is Iterable
            ? (json[r'capabilityKeys'] as Iterable)
                .cast<String>()
                .toList(growable: false)
            : const [],
        compatibilityAliases: json[r'compatibilityAliases'] is Iterable
            ? (json[r'compatibilityAliases'] as Iterable)
                .cast<String>()
                .toList(growable: false)
            : const [],
        displayName: mapValueOfType<String>(json, r'displayName'),
        key: mapValueOfType<String>(json, r'key'),
        memberStates: json[r'memberStates'] is Iterable
            ? (json[r'memberStates'] as Iterable)
                .cast<String>()
                .toList(growable: false)
            : const [],
        portabilityRequirements: json[r'portabilityRequirements'] is Iterable
            ? (json[r'portabilityRequirements'] as Iterable)
                .cast<String>()
                .toList(growable: false)
            : const [],
        providerRealityLevelByCandidate: mapCastOfType<String, String>(
                json, r'providerRealityLevelByCandidate') ??
            const {},
        purpose: mapValueOfType<String>(json, r'purpose'),
        sourceOfTruthModes: json[r'sourceOfTruthModes'] is Iterable
            ? (json[r'sourceOfTruthModes'] as Iterable)
                .cast<String>()
                .toList(growable: false)
            : const [],
        version: mapValueOfType<int>(json, r'version'),
      );
    }
    return null;
  }

  static List<CanonicalDomainRegistryEntryResponse> listFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final result = <CanonicalDomainRegistryEntryResponse>[];
    if (json is List && json.isNotEmpty) {
      for (final row in json) {
        final value = CanonicalDomainRegistryEntryResponse.fromJson(row);
        if (value != null) {
          result.add(value);
        }
      }
    }
    return result.toList(growable: growable);
  }

  static Map<String, CanonicalDomainRegistryEntryResponse> mapFromJson(
      dynamic json) {
    final map = <String, CanonicalDomainRegistryEntryResponse>{};
    if (json is Map && json.isNotEmpty) {
      json = json.cast<String, dynamic>(); // ignore: parameter_assignments
      for (final entry in json.entries) {
        final value =
            CanonicalDomainRegistryEntryResponse.fromJson(entry.value);
        if (value != null) {
          map[entry.key] = value;
        }
      }
    }
    return map;
  }

  // maps a json object with a list of CanonicalDomainRegistryEntryResponse-objects as value to a dart map
  static Map<String, List<CanonicalDomainRegistryEntryResponse>>
      mapListFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final map = <String, List<CanonicalDomainRegistryEntryResponse>>{};
    if (json is Map && json.isNotEmpty) {
      // ignore: parameter_assignments
      json = json.cast<String, dynamic>();
      for (final entry in json.entries) {
        map[entry.key] = CanonicalDomainRegistryEntryResponse.listFromJson(
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
