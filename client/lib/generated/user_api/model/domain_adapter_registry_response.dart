//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//
// @dart=2.18

// ignore_for_file: unused_element, unused_import
// ignore_for_file: always_put_required_named_parameters_first
// ignore_for_file: constant_identifier_names
// ignore_for_file: lines_longer_than_80_chars

part of weave_user_api;

class DomainAdapterRegistryResponse {
  /// Returns a new [DomainAdapterRegistryResponse] instance.
  DomainAdapterRegistryResponse({
    this.domains = const [],
    this.generatedAt,
    this.memberProviderConfigurationAllowed,
    this.releaseStatus,
    this.singleActiveAdapterEnforced,
    this.supportSafe,
  });

  List<DomainAdapterStatusResponse> domains;

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
  bool? memberProviderConfigurationAllowed;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? releaseStatus;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  bool? singleActiveAdapterEnforced;

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
      other is DomainAdapterRegistryResponse &&
          _deepEquality.equals(other.domains, domains) &&
          other.generatedAt == generatedAt &&
          other.memberProviderConfigurationAllowed ==
              memberProviderConfigurationAllowed &&
          other.releaseStatus == releaseStatus &&
          other.singleActiveAdapterEnforced == singleActiveAdapterEnforced &&
          other.supportSafe == supportSafe;

  @override
  int get hashCode =>
      // ignore: unnecessary_parenthesis
      (domains.hashCode) +
      (generatedAt == null ? 0 : generatedAt!.hashCode) +
      (memberProviderConfigurationAllowed == null
          ? 0
          : memberProviderConfigurationAllowed!.hashCode) +
      (releaseStatus == null ? 0 : releaseStatus!.hashCode) +
      (singleActiveAdapterEnforced == null
          ? 0
          : singleActiveAdapterEnforced!.hashCode) +
      (supportSafe == null ? 0 : supportSafe!.hashCode);

  @override
  String toString() =>
      'DomainAdapterRegistryResponse[domains=$domains, generatedAt=$generatedAt, memberProviderConfigurationAllowed=$memberProviderConfigurationAllowed, releaseStatus=$releaseStatus, singleActiveAdapterEnforced=$singleActiveAdapterEnforced, supportSafe=$supportSafe]';

  Map<String, dynamic> toJson() {
    final json = <String, dynamic>{};
    json[r'domains'] = this.domains;
    if (this.generatedAt != null) {
      json[r'generatedAt'] = this.generatedAt!.toUtc().toIso8601String();
    } else {
      json[r'generatedAt'] = null;
    }
    if (this.memberProviderConfigurationAllowed != null) {
      json[r'memberProviderConfigurationAllowed'] =
          this.memberProviderConfigurationAllowed;
    } else {
      json[r'memberProviderConfigurationAllowed'] = null;
    }
    if (this.releaseStatus != null) {
      json[r'releaseStatus'] = this.releaseStatus;
    } else {
      json[r'releaseStatus'] = null;
    }
    if (this.singleActiveAdapterEnforced != null) {
      json[r'singleActiveAdapterEnforced'] = this.singleActiveAdapterEnforced;
    } else {
      json[r'singleActiveAdapterEnforced'] = null;
    }
    if (this.supportSafe != null) {
      json[r'supportSafe'] = this.supportSafe;
    } else {
      json[r'supportSafe'] = null;
    }
    return json;
  }

  /// Returns a new [DomainAdapterRegistryResponse] instance and imports its values from
  /// [value] if it's a [Map], null otherwise.
  // ignore: prefer_constructors_over_static_methods
  static DomainAdapterRegistryResponse? fromJson(dynamic value) {
    if (value is Map) {
      final json = value.cast<String, dynamic>();

      // Ensure that the map contains the required keys.
      // Note 1: the values aren't checked for validity beyond being non-null.
      // Note 2: this code is stripped in release mode!
      assert(() {
        requiredKeys.forEach((key) {
          assert(json.containsKey(key),
              'Required key "DomainAdapterRegistryResponse[$key]" is missing from JSON.');
          assert(json[key] != null,
              'Required key "DomainAdapterRegistryResponse[$key]" has a null value in JSON.');
        });
        return true;
      }());

      return DomainAdapterRegistryResponse(
        domains: DomainAdapterStatusResponse.listFromJson(json[r'domains']),
        generatedAt: mapDateTime(json, r'generatedAt', r''),
        memberProviderConfigurationAllowed:
            mapValueOfType<bool>(json, r'memberProviderConfigurationAllowed'),
        releaseStatus: mapValueOfType<String>(json, r'releaseStatus'),
        singleActiveAdapterEnforced:
            mapValueOfType<bool>(json, r'singleActiveAdapterEnforced'),
        supportSafe: mapValueOfType<bool>(json, r'supportSafe'),
      );
    }
    return null;
  }

  static List<DomainAdapterRegistryResponse> listFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final result = <DomainAdapterRegistryResponse>[];
    if (json is List && json.isNotEmpty) {
      for (final row in json) {
        final value = DomainAdapterRegistryResponse.fromJson(row);
        if (value != null) {
          result.add(value);
        }
      }
    }
    return result.toList(growable: growable);
  }

  static Map<String, DomainAdapterRegistryResponse> mapFromJson(dynamic json) {
    final map = <String, DomainAdapterRegistryResponse>{};
    if (json is Map && json.isNotEmpty) {
      json = json.cast<String, dynamic>(); // ignore: parameter_assignments
      for (final entry in json.entries) {
        final value = DomainAdapterRegistryResponse.fromJson(entry.value);
        if (value != null) {
          map[entry.key] = value;
        }
      }
    }
    return map;
  }

  // maps a json object with a list of DomainAdapterRegistryResponse-objects as value to a dart map
  static Map<String, List<DomainAdapterRegistryResponse>> mapListFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final map = <String, List<DomainAdapterRegistryResponse>>{};
    if (json is Map && json.isNotEmpty) {
      // ignore: parameter_assignments
      json = json.cast<String, dynamic>();
      for (final entry in json.entries) {
        map[entry.key] = DomainAdapterRegistryResponse.listFromJson(
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
