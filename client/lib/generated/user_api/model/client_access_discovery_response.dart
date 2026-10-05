//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//
// @dart=2.18

// ignore_for_file: unused_element, unused_import
// ignore_for_file: always_put_required_named_parameters_first
// ignore_for_file: constant_identifier_names
// ignore_for_file: lines_longer_than_80_chars

part of weave_user_api;

class ClientAccessDiscoveryResponse {
  /// Returns a new [ClientAccessDiscoveryResponse] instance.
  ClientAccessDiscoveryResponse({
    this.credentialLifecycle,
    this.domain,
    this.openApiTag,
    this.productApiBasePath,
    this.providerConfigurationExposed,
    this.supportSafe,
    this.surfaces = const [],
  });

  /// Credential and grant lifecycle posture for this domain.
  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  ClientAccessCredentialLifecycleResponse? credentialLifecycle;

  /// Provider-neutral Weave domain key.
  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? domain;

  /// OpenAPI tag or contract group for generated clients and MCP allowlists.
  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? openApiTag;

  /// Weave-owned product API base path.
  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? productApiBasePath;

  /// False: discovery must not expose raw provider setup or endpoint configuration.
  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  bool? providerConfigurationExposed;

  /// True when discovery excludes raw provider URLs, credentials, endpoint rotation data, and diagnostics.
  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  bool? supportSafe;

  /// Open-standard or native projection surfaces exposed over Weave domain truth.
  List<ClientAccessProtocolSurfaceResponse> surfaces;

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is ClientAccessDiscoveryResponse &&
          other.credentialLifecycle == credentialLifecycle &&
          other.domain == domain &&
          other.openApiTag == openApiTag &&
          other.productApiBasePath == productApiBasePath &&
          other.providerConfigurationExposed == providerConfigurationExposed &&
          other.supportSafe == supportSafe &&
          _deepEquality.equals(other.surfaces, surfaces);

  @override
  int get hashCode =>
      // ignore: unnecessary_parenthesis
      (credentialLifecycle == null ? 0 : credentialLifecycle!.hashCode) +
      (domain == null ? 0 : domain!.hashCode) +
      (openApiTag == null ? 0 : openApiTag!.hashCode) +
      (productApiBasePath == null ? 0 : productApiBasePath!.hashCode) +
      (providerConfigurationExposed == null
          ? 0
          : providerConfigurationExposed!.hashCode) +
      (supportSafe == null ? 0 : supportSafe!.hashCode) +
      (surfaces.hashCode);

  @override
  String toString() =>
      'ClientAccessDiscoveryResponse[credentialLifecycle=$credentialLifecycle, domain=$domain, openApiTag=$openApiTag, productApiBasePath=$productApiBasePath, providerConfigurationExposed=$providerConfigurationExposed, supportSafe=$supportSafe, surfaces=$surfaces]';

  Map<String, dynamic> toJson() {
    final json = <String, dynamic>{};
    if (this.credentialLifecycle != null) {
      json[r'credentialLifecycle'] = this.credentialLifecycle;
    } else {
      json[r'credentialLifecycle'] = null;
    }
    if (this.domain != null) {
      json[r'domain'] = this.domain;
    } else {
      json[r'domain'] = null;
    }
    if (this.openApiTag != null) {
      json[r'openApiTag'] = this.openApiTag;
    } else {
      json[r'openApiTag'] = null;
    }
    if (this.productApiBasePath != null) {
      json[r'productApiBasePath'] = this.productApiBasePath;
    } else {
      json[r'productApiBasePath'] = null;
    }
    if (this.providerConfigurationExposed != null) {
      json[r'providerConfigurationExposed'] = this.providerConfigurationExposed;
    } else {
      json[r'providerConfigurationExposed'] = null;
    }
    if (this.supportSafe != null) {
      json[r'supportSafe'] = this.supportSafe;
    } else {
      json[r'supportSafe'] = null;
    }
    json[r'surfaces'] = this.surfaces;
    return json;
  }

  /// Returns a new [ClientAccessDiscoveryResponse] instance and imports its values from
  /// [value] if it's a [Map], null otherwise.
  // ignore: prefer_constructors_over_static_methods
  static ClientAccessDiscoveryResponse? fromJson(dynamic value) {
    if (value is Map) {
      final json = value.cast<String, dynamic>();

      // Ensure that the map contains the required keys.
      // Note 1: the values aren't checked for validity beyond being non-null.
      // Note 2: this code is stripped in release mode!
      assert(() {
        requiredKeys.forEach((key) {
          assert(json.containsKey(key),
              'Required key "ClientAccessDiscoveryResponse[$key]" is missing from JSON.');
          assert(json[key] != null,
              'Required key "ClientAccessDiscoveryResponse[$key]" has a null value in JSON.');
        });
        return true;
      }());

      return ClientAccessDiscoveryResponse(
        credentialLifecycle: ClientAccessCredentialLifecycleResponse.fromJson(
            json[r'credentialLifecycle']),
        domain: mapValueOfType<String>(json, r'domain'),
        openApiTag: mapValueOfType<String>(json, r'openApiTag'),
        productApiBasePath: mapValueOfType<String>(json, r'productApiBasePath'),
        providerConfigurationExposed:
            mapValueOfType<bool>(json, r'providerConfigurationExposed'),
        supportSafe: mapValueOfType<bool>(json, r'supportSafe'),
        surfaces:
            ClientAccessProtocolSurfaceResponse.listFromJson(json[r'surfaces']),
      );
    }
    return null;
  }

  static List<ClientAccessDiscoveryResponse> listFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final result = <ClientAccessDiscoveryResponse>[];
    if (json is List && json.isNotEmpty) {
      for (final row in json) {
        final value = ClientAccessDiscoveryResponse.fromJson(row);
        if (value != null) {
          result.add(value);
        }
      }
    }
    return result.toList(growable: growable);
  }

  static Map<String, ClientAccessDiscoveryResponse> mapFromJson(dynamic json) {
    final map = <String, ClientAccessDiscoveryResponse>{};
    if (json is Map && json.isNotEmpty) {
      json = json.cast<String, dynamic>(); // ignore: parameter_assignments
      for (final entry in json.entries) {
        final value = ClientAccessDiscoveryResponse.fromJson(entry.value);
        if (value != null) {
          map[entry.key] = value;
        }
      }
    }
    return map;
  }

  // maps a json object with a list of ClientAccessDiscoveryResponse-objects as value to a dart map
  static Map<String, List<ClientAccessDiscoveryResponse>> mapListFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final map = <String, List<ClientAccessDiscoveryResponse>>{};
    if (json is Map && json.isNotEmpty) {
      // ignore: parameter_assignments
      json = json.cast<String, dynamic>();
      for (final entry in json.entries) {
        map[entry.key] = ClientAccessDiscoveryResponse.listFromJson(
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
