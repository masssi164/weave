//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//
// @dart=2.18

// ignore_for_file: unused_element, unused_import
// ignore_for_file: always_put_required_named_parameters_first
// ignore_for_file: constant_identifier_names
// ignore_for_file: lines_longer_than_80_chars

part of weave_user_api;

class OfficeCapabilitiesResponse {
  /// Returns a new [OfficeCapabilitiesResponse] instance.
  OfficeCapabilitiesResponse({
    this.candidates = const [],
    this.capabilities,
    this.configured,
    this.defaultProvider,
    this.enabled,
    this.launchMode,
    this.lockSessionReadiness,
    this.permissions,
    this.providerReadiness = const [],
    this.releaseStatus,
    this.supportSafe,
    this.supportedFileTypes = const [],
  });

  List<OfficeProviderCandidateResponse> candidates;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  OfficeCapabilityFlagsResponse? capabilities;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  bool? configured;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? defaultProvider;

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
  String? launchMode;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  OfficeLockSessionReadinessResponse? lockSessionReadiness;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  OfficePermissionModelResponse? permissions;

  List<ProviderStatusResponse> providerReadiness;

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
  bool? supportSafe;

  List<String> supportedFileTypes;

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is OfficeCapabilitiesResponse &&
          _deepEquality.equals(other.candidates, candidates) &&
          other.capabilities == capabilities &&
          other.configured == configured &&
          other.defaultProvider == defaultProvider &&
          other.enabled == enabled &&
          other.launchMode == launchMode &&
          other.lockSessionReadiness == lockSessionReadiness &&
          other.permissions == permissions &&
          _deepEquality.equals(other.providerReadiness, providerReadiness) &&
          other.releaseStatus == releaseStatus &&
          other.supportSafe == supportSafe &&
          _deepEquality.equals(other.supportedFileTypes, supportedFileTypes);

  @override
  int get hashCode =>
      // ignore: unnecessary_parenthesis
      (candidates.hashCode) +
      (capabilities == null ? 0 : capabilities!.hashCode) +
      (configured == null ? 0 : configured!.hashCode) +
      (defaultProvider == null ? 0 : defaultProvider!.hashCode) +
      (enabled == null ? 0 : enabled!.hashCode) +
      (launchMode == null ? 0 : launchMode!.hashCode) +
      (lockSessionReadiness == null ? 0 : lockSessionReadiness!.hashCode) +
      (permissions == null ? 0 : permissions!.hashCode) +
      (providerReadiness.hashCode) +
      (releaseStatus == null ? 0 : releaseStatus!.hashCode) +
      (supportSafe == null ? 0 : supportSafe!.hashCode) +
      (supportedFileTypes.hashCode);

  @override
  String toString() =>
      'OfficeCapabilitiesResponse[candidates=$candidates, capabilities=$capabilities, configured=$configured, defaultProvider=$defaultProvider, enabled=$enabled, launchMode=$launchMode, lockSessionReadiness=$lockSessionReadiness, permissions=$permissions, providerReadiness=$providerReadiness, releaseStatus=$releaseStatus, supportSafe=$supportSafe, supportedFileTypes=$supportedFileTypes]';

  Map<String, dynamic> toJson() {
    final json = <String, dynamic>{};
    json[r'candidates'] = this.candidates;
    if (this.capabilities != null) {
      json[r'capabilities'] = this.capabilities;
    } else {
      json[r'capabilities'] = null;
    }
    if (this.configured != null) {
      json[r'configured'] = this.configured;
    } else {
      json[r'configured'] = null;
    }
    if (this.defaultProvider != null) {
      json[r'defaultProvider'] = this.defaultProvider;
    } else {
      json[r'defaultProvider'] = null;
    }
    if (this.enabled != null) {
      json[r'enabled'] = this.enabled;
    } else {
      json[r'enabled'] = null;
    }
    if (this.launchMode != null) {
      json[r'launchMode'] = this.launchMode;
    } else {
      json[r'launchMode'] = null;
    }
    if (this.lockSessionReadiness != null) {
      json[r'lockSessionReadiness'] = this.lockSessionReadiness;
    } else {
      json[r'lockSessionReadiness'] = null;
    }
    if (this.permissions != null) {
      json[r'permissions'] = this.permissions;
    } else {
      json[r'permissions'] = null;
    }
    json[r'providerReadiness'] = this.providerReadiness;
    if (this.releaseStatus != null) {
      json[r'releaseStatus'] = this.releaseStatus;
    } else {
      json[r'releaseStatus'] = null;
    }
    if (this.supportSafe != null) {
      json[r'supportSafe'] = this.supportSafe;
    } else {
      json[r'supportSafe'] = null;
    }
    json[r'supportedFileTypes'] = this.supportedFileTypes;
    return json;
  }

  /// Returns a new [OfficeCapabilitiesResponse] instance and imports its values from
  /// [value] if it's a [Map], null otherwise.
  // ignore: prefer_constructors_over_static_methods
  static OfficeCapabilitiesResponse? fromJson(dynamic value) {
    if (value is Map) {
      final json = value.cast<String, dynamic>();

      // Ensure that the map contains the required keys.
      // Note 1: the values aren't checked for validity beyond being non-null.
      // Note 2: this code is stripped in release mode!
      assert(() {
        requiredKeys.forEach((key) {
          assert(json.containsKey(key),
              'Required key "OfficeCapabilitiesResponse[$key]" is missing from JSON.');
          assert(json[key] != null,
              'Required key "OfficeCapabilitiesResponse[$key]" has a null value in JSON.');
        });
        return true;
      }());

      return OfficeCapabilitiesResponse(
        candidates:
            OfficeProviderCandidateResponse.listFromJson(json[r'candidates']),
        capabilities:
            OfficeCapabilityFlagsResponse.fromJson(json[r'capabilities']),
        configured: mapValueOfType<bool>(json, r'configured'),
        defaultProvider: mapValueOfType<String>(json, r'defaultProvider'),
        enabled: mapValueOfType<bool>(json, r'enabled'),
        launchMode: mapValueOfType<String>(json, r'launchMode'),
        lockSessionReadiness: OfficeLockSessionReadinessResponse.fromJson(
            json[r'lockSessionReadiness']),
        permissions:
            OfficePermissionModelResponse.fromJson(json[r'permissions']),
        providerReadiness:
            ProviderStatusResponse.listFromJson(json[r'providerReadiness']),
        releaseStatus: mapValueOfType<String>(json, r'releaseStatus'),
        supportSafe: mapValueOfType<bool>(json, r'supportSafe'),
        supportedFileTypes: json[r'supportedFileTypes'] is Iterable
            ? (json[r'supportedFileTypes'] as Iterable)
                .cast<String>()
                .toList(growable: false)
            : const [],
      );
    }
    return null;
  }

  static List<OfficeCapabilitiesResponse> listFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final result = <OfficeCapabilitiesResponse>[];
    if (json is List && json.isNotEmpty) {
      for (final row in json) {
        final value = OfficeCapabilitiesResponse.fromJson(row);
        if (value != null) {
          result.add(value);
        }
      }
    }
    return result.toList(growable: growable);
  }

  static Map<String, OfficeCapabilitiesResponse> mapFromJson(dynamic json) {
    final map = <String, OfficeCapabilitiesResponse>{};
    if (json is Map && json.isNotEmpty) {
      json = json.cast<String, dynamic>(); // ignore: parameter_assignments
      for (final entry in json.entries) {
        final value = OfficeCapabilitiesResponse.fromJson(entry.value);
        if (value != null) {
          map[entry.key] = value;
        }
      }
    }
    return map;
  }

  // maps a json object with a list of OfficeCapabilitiesResponse-objects as value to a dart map
  static Map<String, List<OfficeCapabilitiesResponse>> mapListFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final map = <String, List<OfficeCapabilitiesResponse>>{};
    if (json is Map && json.isNotEmpty) {
      // ignore: parameter_assignments
      json = json.cast<String, dynamic>();
      for (final entry in json.entries) {
        map[entry.key] = OfficeCapabilitiesResponse.listFromJson(
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
