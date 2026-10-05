//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//
// @dart=2.18

// ignore_for_file: unused_element, unused_import
// ignore_for_file: always_put_required_named_parameters_first
// ignore_for_file: constant_identifier_names
// ignore_for_file: lines_longer_than_80_chars

part of weave_user_api;

class CanonicalDomainRegistryResponse {
  /// Returns a new [CanonicalDomainRegistryResponse] instance.
  CanonicalDomainRegistryResponse({
    this.adminStates = const [],
    this.compatibilityAliases = const {},
    this.domains = const [],
    this.lossClasses = const [],
    this.memberStates = const [],
    this.providerNamesInMemberContractsAllowed,
    this.providerRealityLevels = const [],
    this.registryVersion,
    this.supportSafe,
  });

  List<String> adminStates;

  Map<String, String> compatibilityAliases;

  List<CanonicalDomainRegistryEntryResponse> domains;

  List<String> lossClasses;

  List<String> memberStates;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  bool? providerNamesInMemberContractsAllowed;

  List<String> providerRealityLevels;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? registryVersion;

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
      other is CanonicalDomainRegistryResponse &&
          _deepEquality.equals(other.adminStates, adminStates) &&
          _deepEquality.equals(
              other.compatibilityAliases, compatibilityAliases) &&
          _deepEquality.equals(other.domains, domains) &&
          _deepEquality.equals(other.lossClasses, lossClasses) &&
          _deepEquality.equals(other.memberStates, memberStates) &&
          other.providerNamesInMemberContractsAllowed ==
              providerNamesInMemberContractsAllowed &&
          _deepEquality.equals(
              other.providerRealityLevels, providerRealityLevels) &&
          other.registryVersion == registryVersion &&
          other.supportSafe == supportSafe;

  @override
  int get hashCode =>
      // ignore: unnecessary_parenthesis
      (adminStates.hashCode) +
      (compatibilityAliases.hashCode) +
      (domains.hashCode) +
      (lossClasses.hashCode) +
      (memberStates.hashCode) +
      (providerNamesInMemberContractsAllowed == null
          ? 0
          : providerNamesInMemberContractsAllowed!.hashCode) +
      (providerRealityLevels.hashCode) +
      (registryVersion == null ? 0 : registryVersion!.hashCode) +
      (supportSafe == null ? 0 : supportSafe!.hashCode);

  @override
  String toString() =>
      'CanonicalDomainRegistryResponse[adminStates=$adminStates, compatibilityAliases=$compatibilityAliases, domains=$domains, lossClasses=$lossClasses, memberStates=$memberStates, providerNamesInMemberContractsAllowed=$providerNamesInMemberContractsAllowed, providerRealityLevels=$providerRealityLevels, registryVersion=$registryVersion, supportSafe=$supportSafe]';

  Map<String, dynamic> toJson() {
    final json = <String, dynamic>{};
    json[r'adminStates'] = this.adminStates;
    json[r'compatibilityAliases'] = this.compatibilityAliases;
    json[r'domains'] = this.domains;
    json[r'lossClasses'] = this.lossClasses;
    json[r'memberStates'] = this.memberStates;
    if (this.providerNamesInMemberContractsAllowed != null) {
      json[r'providerNamesInMemberContractsAllowed'] =
          this.providerNamesInMemberContractsAllowed;
    } else {
      json[r'providerNamesInMemberContractsAllowed'] = null;
    }
    json[r'providerRealityLevels'] = this.providerRealityLevels;
    if (this.registryVersion != null) {
      json[r'registryVersion'] = this.registryVersion;
    } else {
      json[r'registryVersion'] = null;
    }
    if (this.supportSafe != null) {
      json[r'supportSafe'] = this.supportSafe;
    } else {
      json[r'supportSafe'] = null;
    }
    return json;
  }

  /// Returns a new [CanonicalDomainRegistryResponse] instance and imports its values from
  /// [value] if it's a [Map], null otherwise.
  // ignore: prefer_constructors_over_static_methods
  static CanonicalDomainRegistryResponse? fromJson(dynamic value) {
    if (value is Map) {
      final json = value.cast<String, dynamic>();

      // Ensure that the map contains the required keys.
      // Note 1: the values aren't checked for validity beyond being non-null.
      // Note 2: this code is stripped in release mode!
      assert(() {
        requiredKeys.forEach((key) {
          assert(json.containsKey(key),
              'Required key "CanonicalDomainRegistryResponse[$key]" is missing from JSON.');
          assert(json[key] != null,
              'Required key "CanonicalDomainRegistryResponse[$key]" has a null value in JSON.');
        });
        return true;
      }());

      return CanonicalDomainRegistryResponse(
        adminStates: json[r'adminStates'] is Iterable
            ? (json[r'adminStates'] as Iterable)
                .cast<String>()
                .toList(growable: false)
            : const [],
        compatibilityAliases:
            mapCastOfType<String, String>(json, r'compatibilityAliases') ??
                const {},
        domains:
            CanonicalDomainRegistryEntryResponse.listFromJson(json[r'domains']),
        lossClasses: json[r'lossClasses'] is Iterable
            ? (json[r'lossClasses'] as Iterable)
                .cast<String>()
                .toList(growable: false)
            : const [],
        memberStates: json[r'memberStates'] is Iterable
            ? (json[r'memberStates'] as Iterable)
                .cast<String>()
                .toList(growable: false)
            : const [],
        providerNamesInMemberContractsAllowed: mapValueOfType<bool>(
            json, r'providerNamesInMemberContractsAllowed'),
        providerRealityLevels: json[r'providerRealityLevels'] is Iterable
            ? (json[r'providerRealityLevels'] as Iterable)
                .cast<String>()
                .toList(growable: false)
            : const [],
        registryVersion: mapValueOfType<String>(json, r'registryVersion'),
        supportSafe: mapValueOfType<bool>(json, r'supportSafe'),
      );
    }
    return null;
  }

  static List<CanonicalDomainRegistryResponse> listFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final result = <CanonicalDomainRegistryResponse>[];
    if (json is List && json.isNotEmpty) {
      for (final row in json) {
        final value = CanonicalDomainRegistryResponse.fromJson(row);
        if (value != null) {
          result.add(value);
        }
      }
    }
    return result.toList(growable: growable);
  }

  static Map<String, CanonicalDomainRegistryResponse> mapFromJson(
      dynamic json) {
    final map = <String, CanonicalDomainRegistryResponse>{};
    if (json is Map && json.isNotEmpty) {
      json = json.cast<String, dynamic>(); // ignore: parameter_assignments
      for (final entry in json.entries) {
        final value = CanonicalDomainRegistryResponse.fromJson(entry.value);
        if (value != null) {
          map[entry.key] = value;
        }
      }
    }
    return map;
  }

  // maps a json object with a list of CanonicalDomainRegistryResponse-objects as value to a dart map
  static Map<String, List<CanonicalDomainRegistryResponse>> mapListFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final map = <String, List<CanonicalDomainRegistryResponse>>{};
    if (json is Map && json.isNotEmpty) {
      // ignore: parameter_assignments
      json = json.cast<String, dynamic>();
      for (final entry in json.entries) {
        map[entry.key] = CanonicalDomainRegistryResponse.listFromJson(
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
