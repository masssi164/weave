//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//
// @dart=2.18

// ignore_for_file: unused_element, unused_import
// ignore_for_file: always_put_required_named_parameters_first
// ignore_for_file: constant_identifier_names
// ignore_for_file: lines_longer_than_80_chars

part of weave_user_api;

class WorkspaceCapabilityPolicyResponse {
  /// Returns a new [WorkspaceCapabilityPolicyResponse] instance.
  WorkspaceCapabilityPolicyResponse({
    required this.agentRuntimeControlPosture,
    required this.denyByDefault,
    required this.federationContract,
    this.grantedCapabilities = const [],
    this.groups = const [],
    required this.platformIdentityAuthority,
    required this.platformIdentityCategory,
    required this.principalSource,
    this.profileKeys = const [],
    this.roles = const [],
    required this.supportSafe,
  });

  String agentRuntimeControlPosture;

  bool denyByDefault;

  String federationContract;

  List<String> grantedCapabilities;

  List<String> groups;

  String platformIdentityAuthority;

  String platformIdentityCategory;

  String principalSource;

  List<String> profileKeys;

  List<String> roles;

  bool supportSafe;

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is WorkspaceCapabilityPolicyResponse &&
          other.agentRuntimeControlPosture == agentRuntimeControlPosture &&
          other.denyByDefault == denyByDefault &&
          other.federationContract == federationContract &&
          _deepEquality.equals(
              other.grantedCapabilities, grantedCapabilities) &&
          _deepEquality.equals(other.groups, groups) &&
          other.platformIdentityAuthority == platformIdentityAuthority &&
          other.platformIdentityCategory == platformIdentityCategory &&
          other.principalSource == principalSource &&
          _deepEquality.equals(other.profileKeys, profileKeys) &&
          _deepEquality.equals(other.roles, roles) &&
          other.supportSafe == supportSafe;

  @override
  int get hashCode =>
      // ignore: unnecessary_parenthesis
      (agentRuntimeControlPosture.hashCode) +
      (denyByDefault.hashCode) +
      (federationContract.hashCode) +
      (grantedCapabilities.hashCode) +
      (groups.hashCode) +
      (platformIdentityAuthority.hashCode) +
      (platformIdentityCategory.hashCode) +
      (principalSource.hashCode) +
      (profileKeys.hashCode) +
      (roles.hashCode) +
      (supportSafe.hashCode);

  @override
  String toString() =>
      'WorkspaceCapabilityPolicyResponse[agentRuntimeControlPosture=$agentRuntimeControlPosture, denyByDefault=$denyByDefault, federationContract=$federationContract, grantedCapabilities=$grantedCapabilities, groups=$groups, platformIdentityAuthority=$platformIdentityAuthority, platformIdentityCategory=$platformIdentityCategory, principalSource=$principalSource, profileKeys=$profileKeys, roles=$roles, supportSafe=$supportSafe]';

  Map<String, dynamic> toJson() {
    final json = <String, dynamic>{};
    json[r'agentRuntimeControlPosture'] = this.agentRuntimeControlPosture;
    json[r'denyByDefault'] = this.denyByDefault;
    json[r'federationContract'] = this.federationContract;
    json[r'grantedCapabilities'] = this.grantedCapabilities;
    json[r'groups'] = this.groups;
    json[r'platformIdentityAuthority'] = this.platformIdentityAuthority;
    json[r'platformIdentityCategory'] = this.platformIdentityCategory;
    json[r'principalSource'] = this.principalSource;
    json[r'profileKeys'] = this.profileKeys;
    json[r'roles'] = this.roles;
    json[r'supportSafe'] = this.supportSafe;
    return json;
  }

  /// Returns a new [WorkspaceCapabilityPolicyResponse] instance and imports its values from
  /// [value] if it's a [Map], null otherwise.
  // ignore: prefer_constructors_over_static_methods
  static WorkspaceCapabilityPolicyResponse? fromJson(dynamic value) {
    if (value is Map) {
      final json = value.cast<String, dynamic>();

      // Ensure that the map contains the required keys.
      // Note 1: the values aren't checked for validity beyond being non-null.
      // Note 2: this code is stripped in release mode!
      assert(() {
        requiredKeys.forEach((key) {
          assert(json.containsKey(key),
              'Required key "WorkspaceCapabilityPolicyResponse[$key]" is missing from JSON.');
          assert(json[key] != null,
              'Required key "WorkspaceCapabilityPolicyResponse[$key]" has a null value in JSON.');
        });
        return true;
      }());

      return WorkspaceCapabilityPolicyResponse(
        agentRuntimeControlPosture:
            mapValueOfType<String>(json, r'agentRuntimeControlPosture')!,
        denyByDefault: mapValueOfType<bool>(json, r'denyByDefault')!,
        federationContract:
            mapValueOfType<String>(json, r'federationContract')!,
        grantedCapabilities: json[r'grantedCapabilities'] is Iterable
            ? (json[r'grantedCapabilities'] as Iterable)
                .cast<String>()
                .toList(growable: false)
            : const [],
        groups: json[r'groups'] is Iterable
            ? (json[r'groups'] as Iterable)
                .cast<String>()
                .toList(growable: false)
            : const [],
        platformIdentityAuthority:
            mapValueOfType<String>(json, r'platformIdentityAuthority')!,
        platformIdentityCategory:
            mapValueOfType<String>(json, r'platformIdentityCategory')!,
        principalSource: mapValueOfType<String>(json, r'principalSource')!,
        profileKeys: json[r'profileKeys'] is Iterable
            ? (json[r'profileKeys'] as Iterable)
                .cast<String>()
                .toList(growable: false)
            : const [],
        roles: json[r'roles'] is Iterable
            ? (json[r'roles'] as Iterable)
                .cast<String>()
                .toList(growable: false)
            : const [],
        supportSafe: mapValueOfType<bool>(json, r'supportSafe')!,
      );
    }
    return null;
  }

  static List<WorkspaceCapabilityPolicyResponse> listFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final result = <WorkspaceCapabilityPolicyResponse>[];
    if (json is List && json.isNotEmpty) {
      for (final row in json) {
        final value = WorkspaceCapabilityPolicyResponse.fromJson(row);
        if (value != null) {
          result.add(value);
        }
      }
    }
    return result.toList(growable: growable);
  }

  static Map<String, WorkspaceCapabilityPolicyResponse> mapFromJson(
      dynamic json) {
    final map = <String, WorkspaceCapabilityPolicyResponse>{};
    if (json is Map && json.isNotEmpty) {
      json = json.cast<String, dynamic>(); // ignore: parameter_assignments
      for (final entry in json.entries) {
        final value = WorkspaceCapabilityPolicyResponse.fromJson(entry.value);
        if (value != null) {
          map[entry.key] = value;
        }
      }
    }
    return map;
  }

  // maps a json object with a list of WorkspaceCapabilityPolicyResponse-objects as value to a dart map
  static Map<String, List<WorkspaceCapabilityPolicyResponse>> mapListFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final map = <String, List<WorkspaceCapabilityPolicyResponse>>{};
    if (json is Map && json.isNotEmpty) {
      // ignore: parameter_assignments
      json = json.cast<String, dynamic>();
      for (final entry in json.entries) {
        map[entry.key] = WorkspaceCapabilityPolicyResponse.listFromJson(
          entry.value,
          growable: growable,
        );
      }
    }
    return map;
  }

  /// The list of required keys that must be present in a JSON.
  static const requiredKeys = <String>{
    'agentRuntimeControlPosture',
    'denyByDefault',
    'federationContract',
    'grantedCapabilities',
    'groups',
    'platformIdentityAuthority',
    'platformIdentityCategory',
    'principalSource',
    'profileKeys',
    'roles',
    'supportSafe',
  };
}
