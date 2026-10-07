//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//
// @dart=2.18

// ignore_for_file: unused_element, unused_import
// ignore_for_file: always_put_required_named_parameters_first
// ignore_for_file: constant_identifier_names
// ignore_for_file: lines_longer_than_80_chars

part of weave_user_api;

class PlatformConfigResponse {
  /// Returns a new [PlatformConfigResponse] instance.
  PlatformConfigResponse({
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
      other is PlatformConfigResponse &&
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
      'PlatformConfigResponse[domains=$domains, oidc=$oidc, organizationOrigin=$organizationOrigin, protocols=$protocols, recoveryActions=$recoveryActions, releasePosture=$releasePosture, schemaVersion=$schemaVersion, userApiBaseUrl=$userApiBaseUrl]';

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

  /// Returns a new [PlatformConfigResponse] instance and imports its values from
  /// [value] if it's a [Map], null otherwise.
  // ignore: prefer_constructors_over_static_methods
  static PlatformConfigResponse? fromJson(dynamic value) {
    if (value is Map) {
      final json = value.cast<String, dynamic>();
      _validateOrgManifestShape(value);

      // Ensure that the map contains the required keys.
      // Note 1: the values aren't checked for validity beyond being non-null.
      // Note 2: this code is stripped in release mode!
      assert(() {
        requiredKeys.forEach((key) {
          assert(json.containsKey(key),
              'Required key "PlatformConfigResponse[$key]" is missing from JSON.');
          assert(json[key] != null,
              'Required key "PlatformConfigResponse[$key]" has a null value in JSON.');
        });
        return true;
      }());

      return PlatformConfigResponse(
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

  static List<PlatformConfigResponse> listFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final result = <PlatformConfigResponse>[];
    if (json is List && json.isNotEmpty) {
      for (final row in json) {
        final value = PlatformConfigResponse.fromJson(row);
        if (value != null) {
          result.add(value);
        }
      }
    }
    return result.toList(growable: growable);
  }

  static Map<String, PlatformConfigResponse> mapFromJson(dynamic json) {
    final map = <String, PlatformConfigResponse>{};
    if (json is Map && json.isNotEmpty) {
      json = json.cast<String, dynamic>(); // ignore: parameter_assignments
      for (final entry in json.entries) {
        final value = PlatformConfigResponse.fromJson(entry.value);
        if (value != null) {
          map[entry.key] = value;
        }
      }
    }
    return map;
  }

  // maps a json object with a list of PlatformConfigResponse-objects as value to a dart map
  static Map<String, List<PlatformConfigResponse>> mapListFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final map = <String, List<PlatformConfigResponse>>{};
    if (json is Map && json.isNotEmpty) {
      // ignore: parameter_assignments
      json = json.cast<String, dynamic>();
      for (final entry in json.entries) {
        map[entry.key] = PlatformConfigResponse.listFromJson(
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

// The manifest is a closed public bootstrap document. Validate its wire shape
// before the generated transport decoder can discard an unknown property.
class OrgManifestShapeException implements Exception {
  const OrgManifestShapeException(this.message);
  final String message;
}

class _OrgManifestShape {
  const _OrgManifestShape(this.allowed, this.required);
  final Set<String> allowed;
  final Set<String> required;
}

const _orgManifestShapes = <String, _OrgManifestShape>{
  'PlatformConfigResponse': _OrgManifestShape(<String>{
    'domains',
    'oidc',
    'organizationOrigin',
    'protocols',
    'recoveryActions',
    'releasePosture',
    'schemaVersion',
    'userApiBaseUrl'
  }, <String>{
    'domains',
    'oidc',
    'organizationOrigin',
    'protocols',
    'releasePosture',
    'schemaVersion',
    'userApiBaseUrl'
  }),
  'Oidc': _OrgManifestShape(
      <String>{'clientId', 'issuer'}, <String>{'clientId', 'issuer'}),
  'Protocols': _OrgManifestShape(<String>{'matrixClientServerBaseUrl'},
      <String>{'matrixClientServerBaseUrl'}),
  'DomainCapability': _OrgManifestShape(
      <String>{'capabilities', 'domain', 'state', 'supportReference'},
      <String>{'capabilities', 'domain', 'state'}),
  'RecoveryAction': _OrgManifestShape(
      <String>{'code', 'label', 'supportReference'}, <String>{}),
};

Map<String, dynamic> _orgManifestObject(dynamic value, String name) {
  if (value is! Map<String, dynamic>) {
    throw OrgManifestShapeException('$name must be an object');
  }
  final shape = _orgManifestShapes[name]!;
  if (value.keys.any((key) => !shape.allowed.contains(key))) {
    throw const OrgManifestShapeException(
        'The organization manifest contains unsupported fields');
  }
  for (final key in shape.required) {
    if (!value.containsKey(key) || value[key] == null) {
      final prefix = name == 'PlatformConfigResponse'
          ? ''
          : '${name[0].toLowerCase()}${name.substring(1)}.';
      throw OrgManifestShapeException('$prefix$key is required');
    }
  }
  return value;
}

void _validateOrgManifestShape(dynamic value) {
  final manifest = _orgManifestObject(value, 'PlatformConfigResponse');
  _orgManifestObject(manifest['oidc'], 'Oidc');
  _orgManifestObject(manifest['protocols'], 'Protocols');
  final domains = manifest['domains'];
  if (domains is! List || domains.isEmpty) {
    throw const OrgManifestShapeException('domains must be a nonempty array');
  }
  for (final domain in domains) {
    final entry = _orgManifestObject(domain, 'DomainCapability');
    if (entry['capabilities'] is! List ||
        (entry['capabilities'] as List)
            .any((capability) => capability is! String) ||
        (entry.containsKey('supportReference') &&
            entry['supportReference'] is! String)) {
      throw const OrgManifestShapeException('domains entries are incomplete');
    }
  }
  if (manifest.containsKey('recoveryActions')) {
    final actions = manifest['recoveryActions'];
    if (actions is! List) {
      throw const OrgManifestShapeException('recoveryActions must be an array');
    }
    for (final action in actions) {
      final entry = _orgManifestObject(action, 'RecoveryAction');
      if (entry['code'] is! String ||
          entry['label'] is! String ||
          (entry.containsKey('supportReference') &&
              entry['supportReference'] is! String)) {
        throw const OrgManifestShapeException(
            'recoveryActions entries are incomplete');
      }
    }
  }
}
