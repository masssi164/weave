//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//
// @dart=2.18

// ignore_for_file: unused_element, unused_import
// ignore_for_file: always_put_required_named_parameters_first
// ignore_for_file: constant_identifier_names
// ignore_for_file: lines_longer_than_80_chars

part of weave_user_api;

class OrganizationManifestResponse {
  /// Returns a new [OrganizationManifestResponse] instance.
  OrganizationManifestResponse({
    this.adminConsoleResponsibilities = const [],
    this.capabilities,
    this.clientAccessDiscovery = const {},
    this.clientResponsibilities = const [],
    this.diagnosticsExposed,
    this.displayName,
    this.generatedAt,
    this.manifestVersion,
    this.memberCapabilityStates = const {},
    this.organizationAuthUrl,
    this.organizationId,
    this.providerConfigurationExposed,
    this.supportSafe,
    this.whitelistingOwner,
  });

  /// Responsibilities owned by the Organization/Admin Console.
  List<String> adminConsoleResponsibilities;

  /// Effective member capability snapshot; provider setup and diagnostics stay out of this contract.
  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  WorkspaceCapabilitiesResponse? capabilities;

  /// Support-safe domain access discovery for product APIs, standard-protocol projections, native setup, and governed MCP consumers.
  Map<String, ClientAccessDiscoveryResponse> clientAccessDiscovery;

  /// Responsibilities owned by the member Weave Client.
  List<String> clientResponsibilities;

  /// False: member clients must not receive admin/provider diagnostics.
  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  bool? diagnosticsExposed;

  /// Member-visible organization name.
  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? displayName;

  /// When this support-safe manifest was generated.
  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  DateTime? generatedAt;

  /// Contract version for the member-client manifest.
  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? manifestVersion;

  /// Stable member-visible capability states keyed by provider-neutral Weave domain. Values are available, disabled_by_policy, not_configured, degraded, unavailable, or coming_later.
  Map<String, OrganizationManifestResponseMemberCapabilityStatesEnum>
      memberCapabilityStates;

  /// The organization auth URL or invite/deep-link origin the member client may use for SSO.
  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? organizationAuthUrl;

  /// Stable organization identifier; not a provider tenant secret.
  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? organizationId;

  /// False: member clients must not receive raw provider setup or endpoint rotation configuration.
  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  bool? providerConfigurationExposed;

  /// True when the manifest excludes provider secrets, endpoint rotation data, and raw diagnostics.
  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  bool? supportSafe;

  /// Control plane that owns provider/tool/agent whitelisting.
  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? whitelistingOwner;

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is OrganizationManifestResponse &&
          _deepEquality.equals(other.adminConsoleResponsibilities,
              adminConsoleResponsibilities) &&
          other.capabilities == capabilities &&
          _deepEquality.equals(
              other.clientAccessDiscovery, clientAccessDiscovery) &&
          _deepEquality.equals(
              other.clientResponsibilities, clientResponsibilities) &&
          other.diagnosticsExposed == diagnosticsExposed &&
          other.displayName == displayName &&
          other.generatedAt == generatedAt &&
          other.manifestVersion == manifestVersion &&
          _deepEquality.equals(
              other.memberCapabilityStates, memberCapabilityStates) &&
          other.organizationAuthUrl == organizationAuthUrl &&
          other.organizationId == organizationId &&
          other.providerConfigurationExposed == providerConfigurationExposed &&
          other.supportSafe == supportSafe &&
          other.whitelistingOwner == whitelistingOwner;

  @override
  int get hashCode =>
      // ignore: unnecessary_parenthesis
      (adminConsoleResponsibilities.hashCode) +
      (capabilities == null ? 0 : capabilities!.hashCode) +
      (clientAccessDiscovery.hashCode) +
      (clientResponsibilities.hashCode) +
      (diagnosticsExposed == null ? 0 : diagnosticsExposed!.hashCode) +
      (displayName == null ? 0 : displayName!.hashCode) +
      (generatedAt == null ? 0 : generatedAt!.hashCode) +
      (manifestVersion == null ? 0 : manifestVersion!.hashCode) +
      (memberCapabilityStates.hashCode) +
      (organizationAuthUrl == null ? 0 : organizationAuthUrl!.hashCode) +
      (organizationId == null ? 0 : organizationId!.hashCode) +
      (providerConfigurationExposed == null
          ? 0
          : providerConfigurationExposed!.hashCode) +
      (supportSafe == null ? 0 : supportSafe!.hashCode) +
      (whitelistingOwner == null ? 0 : whitelistingOwner!.hashCode);

  @override
  String toString() =>
      'OrganizationManifestResponse[adminConsoleResponsibilities=$adminConsoleResponsibilities, capabilities=$capabilities, clientAccessDiscovery=$clientAccessDiscovery, clientResponsibilities=$clientResponsibilities, diagnosticsExposed=$diagnosticsExposed, displayName=$displayName, generatedAt=$generatedAt, manifestVersion=$manifestVersion, memberCapabilityStates=$memberCapabilityStates, organizationAuthUrl=$organizationAuthUrl, organizationId=$organizationId, providerConfigurationExposed=$providerConfigurationExposed, supportSafe=$supportSafe, whitelistingOwner=$whitelistingOwner]';

  Map<String, dynamic> toJson() {
    final json = <String, dynamic>{};
    json[r'adminConsoleResponsibilities'] = this.adminConsoleResponsibilities;
    if (this.capabilities != null) {
      json[r'capabilities'] = this.capabilities;
    } else {
      json[r'capabilities'] = null;
    }
    json[r'clientAccessDiscovery'] = this.clientAccessDiscovery;
    json[r'clientResponsibilities'] = this.clientResponsibilities;
    if (this.diagnosticsExposed != null) {
      json[r'diagnosticsExposed'] = this.diagnosticsExposed;
    } else {
      json[r'diagnosticsExposed'] = null;
    }
    if (this.displayName != null) {
      json[r'displayName'] = this.displayName;
    } else {
      json[r'displayName'] = null;
    }
    if (this.generatedAt != null) {
      json[r'generatedAt'] = this.generatedAt!.toUtc().toIso8601String();
    } else {
      json[r'generatedAt'] = null;
    }
    if (this.manifestVersion != null) {
      json[r'manifestVersion'] = this.manifestVersion;
    } else {
      json[r'manifestVersion'] = null;
    }
    json[r'memberCapabilityStates'] = this.memberCapabilityStates;
    if (this.organizationAuthUrl != null) {
      json[r'organizationAuthUrl'] = this.organizationAuthUrl;
    } else {
      json[r'organizationAuthUrl'] = null;
    }
    if (this.organizationId != null) {
      json[r'organizationId'] = this.organizationId;
    } else {
      json[r'organizationId'] = null;
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
    if (this.whitelistingOwner != null) {
      json[r'whitelistingOwner'] = this.whitelistingOwner;
    } else {
      json[r'whitelistingOwner'] = null;
    }
    return json;
  }

  /// Returns a new [OrganizationManifestResponse] instance and imports its values from
  /// [value] if it's a [Map], null otherwise.
  // ignore: prefer_constructors_over_static_methods
  static OrganizationManifestResponse? fromJson(dynamic value) {
    if (value is Map) {
      final json = value.cast<String, dynamic>();

      // Ensure that the map contains the required keys.
      // Note 1: the values aren't checked for validity beyond being non-null.
      // Note 2: this code is stripped in release mode!
      assert(() {
        requiredKeys.forEach((key) {
          assert(json.containsKey(key),
              'Required key "OrganizationManifestResponse[$key]" is missing from JSON.');
          assert(json[key] != null,
              'Required key "OrganizationManifestResponse[$key]" has a null value in JSON.');
        });
        return true;
      }());

      return OrganizationManifestResponse(
        adminConsoleResponsibilities:
            json[r'adminConsoleResponsibilities'] is Iterable
                ? (json[r'adminConsoleResponsibilities'] as Iterable)
                    .cast<String>()
                    .toList(growable: false)
                : const [],
        capabilities:
            WorkspaceCapabilitiesResponse.fromJson(json[r'capabilities']),
        clientAccessDiscovery: ClientAccessDiscoveryResponse.mapFromJson(
            json[r'clientAccessDiscovery']),
        clientResponsibilities: json[r'clientResponsibilities'] is Iterable
            ? (json[r'clientResponsibilities'] as Iterable)
                .cast<String>()
                .toList(growable: false)
            : const [],
        diagnosticsExposed: mapValueOfType<bool>(json, r'diagnosticsExposed'),
        displayName: mapValueOfType<String>(json, r'displayName'),
        generatedAt: mapDateTime(json, r'generatedAt', r''),
        manifestVersion: mapValueOfType<String>(json, r'manifestVersion'),
        memberCapabilityStates: (mapCastOfType<String, String>(
                    json, r'memberCapabilityStates') ??
                const <String, String>{})
            .map((key, value) => MapEntry(
                key,
                OrganizationManifestResponseMemberCapabilityStatesEnum.fromJson(
                        value) ??
                    (throw FormatException(
                        'Unknown member capability state: $value')))),
        organizationAuthUrl:
            mapValueOfType<String>(json, r'organizationAuthUrl'),
        organizationId: mapValueOfType<String>(json, r'organizationId'),
        providerConfigurationExposed:
            mapValueOfType<bool>(json, r'providerConfigurationExposed'),
        supportSafe: mapValueOfType<bool>(json, r'supportSafe'),
        whitelistingOwner: mapValueOfType<String>(json, r'whitelistingOwner'),
      );
    }
    return null;
  }

  static List<OrganizationManifestResponse> listFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final result = <OrganizationManifestResponse>[];
    if (json is List && json.isNotEmpty) {
      for (final row in json) {
        final value = OrganizationManifestResponse.fromJson(row);
        if (value != null) {
          result.add(value);
        }
      }
    }
    return result.toList(growable: growable);
  }

  static Map<String, OrganizationManifestResponse> mapFromJson(dynamic json) {
    final map = <String, OrganizationManifestResponse>{};
    if (json is Map && json.isNotEmpty) {
      json = json.cast<String, dynamic>(); // ignore: parameter_assignments
      for (final entry in json.entries) {
        final value = OrganizationManifestResponse.fromJson(entry.value);
        if (value != null) {
          map[entry.key] = value;
        }
      }
    }
    return map;
  }

  // maps a json object with a list of OrganizationManifestResponse-objects as value to a dart map
  static Map<String, List<OrganizationManifestResponse>> mapListFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final map = <String, List<OrganizationManifestResponse>>{};
    if (json is Map && json.isNotEmpty) {
      // ignore: parameter_assignments
      json = json.cast<String, dynamic>();
      for (final entry in json.entries) {
        map[entry.key] = OrganizationManifestResponse.listFromJson(
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

/// Provider-neutral member capability states exposed by organization capability manifests.
class OrganizationManifestResponseMemberCapabilityStatesEnum {
  /// Instantiate a new enum with the provided [value].
  const OrganizationManifestResponseMemberCapabilityStatesEnum._(this.value);

  /// The underlying value of this enum member.
  final String value;

  @override
  String toString() => value;

  String toJson() => value;

  static const available =
      OrganizationManifestResponseMemberCapabilityStatesEnum._(r'available');
  static const disabledByPolicy =
      OrganizationManifestResponseMemberCapabilityStatesEnum._(
          r'disabled_by_policy');
  static const notConfigured =
      OrganizationManifestResponseMemberCapabilityStatesEnum._(
          r'not_configured');
  static const degraded =
      OrganizationManifestResponseMemberCapabilityStatesEnum._(r'degraded');
  static const unavailable =
      OrganizationManifestResponseMemberCapabilityStatesEnum._(r'unavailable');
  static const comingLater =
      OrganizationManifestResponseMemberCapabilityStatesEnum._(r'coming_later');

  /// List of all possible values in this [enum][OrganizationManifestResponseMemberCapabilityStatesEnum].
  static const values =
      <OrganizationManifestResponseMemberCapabilityStatesEnum>[
    available,
    disabledByPolicy,
    notConfigured,
    degraded,
    unavailable,
    comingLater,
  ];

  static OrganizationManifestResponseMemberCapabilityStatesEnum? fromJson(
          dynamic value) =>
      OrganizationManifestResponseMemberCapabilityStatesEnumTypeTransformer()
          .decode(value);

  static List<OrganizationManifestResponseMemberCapabilityStatesEnum>
      listFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final result = <OrganizationManifestResponseMemberCapabilityStatesEnum>[];
    if (json is List && json.isNotEmpty) {
      for (final row in json) {
        final value =
            OrganizationManifestResponseMemberCapabilityStatesEnum.fromJson(
                row);
        if (value != null) {
          result.add(value);
        }
      }
    }
    return result.toList(growable: growable);
  }
}

/// Transformation class that can [encode] an instance of [OrganizationManifestResponseMemberCapabilityStatesEnum] to String,
/// and [decode] dynamic data back to [OrganizationManifestResponseMemberCapabilityStatesEnum].
class OrganizationManifestResponseMemberCapabilityStatesEnumTypeTransformer {
  factory OrganizationManifestResponseMemberCapabilityStatesEnumTypeTransformer() =>
      _instance ??=
          const OrganizationManifestResponseMemberCapabilityStatesEnumTypeTransformer
              ._();

  const OrganizationManifestResponseMemberCapabilityStatesEnumTypeTransformer._();

  String encode(OrganizationManifestResponseMemberCapabilityStatesEnum data) =>
      data.value;

  /// Decodes a [dynamic value][data] to a OrganizationManifestResponseMemberCapabilityStatesEnum.
  ///
  /// If [allowNull] is true and the [dynamic value][data] cannot be decoded successfully,
  /// then null is returned. However, if [allowNull] is false and the [dynamic value][data]
  /// cannot be decoded successfully, then an [UnimplementedError] is thrown.
  ///
  /// The [allowNull] is very handy when an API changes and a new enum value is added or removed,
  /// and users are still using an old app with the old code.
  OrganizationManifestResponseMemberCapabilityStatesEnum? decode(dynamic data,
      {bool allowNull = true}) {
    if (data != null) {
      switch (data) {
        case r'available':
          return OrganizationManifestResponseMemberCapabilityStatesEnum
              .available;
        case r'disabled_by_policy':
          return OrganizationManifestResponseMemberCapabilityStatesEnum
              .disabledByPolicy;
        case r'not_configured':
          return OrganizationManifestResponseMemberCapabilityStatesEnum
              .notConfigured;
        case r'degraded':
          return OrganizationManifestResponseMemberCapabilityStatesEnum
              .degraded;
        case r'unavailable':
          return OrganizationManifestResponseMemberCapabilityStatesEnum
              .unavailable;
        case r'coming_later':
          return OrganizationManifestResponseMemberCapabilityStatesEnum
              .comingLater;
        default:
          if (!allowNull) {
            throw ArgumentError('Unknown enum value to decode: $data');
          }
      }
    }
    return null;
  }

  /// Singleton [OrganizationManifestResponseMemberCapabilityStatesEnumTypeTransformer] instance.
  static OrganizationManifestResponseMemberCapabilityStatesEnumTypeTransformer?
      _instance;
}
