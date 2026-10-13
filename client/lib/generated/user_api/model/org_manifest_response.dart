//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//
// @dart=2.18

// ignore_for_file: unused_element, unused_import
// ignore_for_file: always_put_required_named_parameters_first
// ignore_for_file: constant_identifier_names
// ignore_for_file: lines_longer_than_80_chars

part of weave_user_api;

class OrgManifestResponse {
  /// Returns a new [OrgManifestResponse] instance.
  OrgManifestResponse({
    this.domains = const [],
    required this.oidc,
    required this.organizationOrigin,
    required this.protocols,
    this.recoveryActions = const [],
    required this.releasePosture,
    required this.schemaVersion,
    required this.userApiBaseUrl,
  });

  List<DomainCapability> domains;

  Oidc oidc;

  String organizationOrigin;

  Protocols protocols;

  List<RecoveryAction> recoveryActions;

  String releasePosture;

  int schemaVersion;

  String userApiBaseUrl;

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is OrgManifestResponse &&
          _deepEquality.equals(other.domains, domains) &&
          other.oidc == oidc &&
          other.organizationOrigin == organizationOrigin &&
          other.protocols == protocols &&
          _deepEquality.equals(other.recoveryActions, recoveryActions) &&
          other.releasePosture == releasePosture &&
          other.schemaVersion == schemaVersion &&
          other.userApiBaseUrl == userApiBaseUrl;

  @override
  int get hashCode =>
      // ignore: unnecessary_parenthesis
      (domains.hashCode) +
      (oidc.hashCode) +
      (organizationOrigin.hashCode) +
      (protocols.hashCode) +
      (recoveryActions.hashCode) +
      (releasePosture.hashCode) +
      (schemaVersion.hashCode) +
      (userApiBaseUrl.hashCode);

  @override
  String toString() =>
      'OrgManifestResponse[domains=$domains, oidc=$oidc, organizationOrigin=$organizationOrigin, protocols=$protocols, recoveryActions=$recoveryActions, releasePosture=$releasePosture, schemaVersion=$schemaVersion, userApiBaseUrl=$userApiBaseUrl]';

  Map<String, dynamic> toJson() {
    final json = <String, dynamic>{};
    json[r'domains'] = this.domains;
    json[r'oidc'] = this.oidc;
    json[r'organizationOrigin'] = this.organizationOrigin;
    json[r'protocols'] = this.protocols;
    json[r'recoveryActions'] = this.recoveryActions;
    json[r'releasePosture'] = this.releasePosture;
    json[r'schemaVersion'] = this.schemaVersion;
    json[r'userApiBaseUrl'] = this.userApiBaseUrl;
    return json;
  }

  /// Returns a new [OrgManifestResponse] instance and imports its values from
  /// [value] if it's a [Map], null otherwise.
  // ignore: prefer_constructors_over_static_methods
  static OrgManifestResponse? fromJson(dynamic value) {
    if (value is Map) {
      final json = value.cast<String, dynamic>();

      // Ensure that the map contains the required keys.
      // Note 1: the values aren't checked for validity beyond being non-null.
      // Note 2: this code is stripped in release mode!
      assert(() {
        requiredKeys.forEach((key) {
          assert(json.containsKey(key),
              'Required key "OrgManifestResponse[$key]" is missing from JSON.');
          assert(json[key] != null,
              'Required key "OrgManifestResponse[$key]" has a null value in JSON.');
        });
        return true;
      }());

      return OrgManifestResponse(
        domains: DomainCapability.listFromJson(json[r'domains']),
        oidc: Oidc.fromJson(json[r'oidc'])!,
        organizationOrigin:
            mapValueOfType<String>(json, r'organizationOrigin')!,
        protocols: Protocols.fromJson(json[r'protocols'])!,
        recoveryActions: RecoveryAction.listFromJson(json[r'recoveryActions']),
        releasePosture: mapValueOfType<String>(json, r'releasePosture')!,
        schemaVersion: mapValueOfType<int>(json, r'schemaVersion')!,
        userApiBaseUrl: mapValueOfType<String>(json, r'userApiBaseUrl')!,
      );
    }
    return null;
  }

  static List<OrgManifestResponse> listFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final result = <OrgManifestResponse>[];
    if (json is List && json.isNotEmpty) {
      for (final row in json) {
        final value = OrgManifestResponse.fromJson(row);
        if (value != null) {
          result.add(value);
        }
      }
    }
    return result.toList(growable: growable);
  }

  static Map<String, OrgManifestResponse> mapFromJson(dynamic json) {
    final map = <String, OrgManifestResponse>{};
    if (json is Map && json.isNotEmpty) {
      json = json.cast<String, dynamic>(); // ignore: parameter_assignments
      for (final entry in json.entries) {
        final value = OrgManifestResponse.fromJson(entry.value);
        if (value != null) {
          map[entry.key] = value;
        }
      }
    }
    return map;
  }

  // maps a json object with a list of OrgManifestResponse-objects as value to a dart map
  static Map<String, List<OrgManifestResponse>> mapListFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final map = <String, List<OrgManifestResponse>>{};
    if (json is Map && json.isNotEmpty) {
      // ignore: parameter_assignments
      json = json.cast<String, dynamic>();
      for (final entry in json.entries) {
        map[entry.key] = OrgManifestResponse.listFromJson(
          entry.value,
          growable: growable,
        );
      }
    }
    return map;
  }

  /// The list of required keys that must be present in a JSON.
  static const requiredKeys = <String>{
    'domains',
    'oidc',
    'organizationOrigin',
    'protocols',
    'releasePosture',
    'schemaVersion',
    'userApiBaseUrl',
  };
}
