//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//
// @dart=2.18

// ignore_for_file: unused_element, unused_import
// ignore_for_file: always_put_required_named_parameters_first
// ignore_for_file: constant_identifier_names
// ignore_for_file: lines_longer_than_80_chars

part of weave_user_api;

class GuestAccessContractResponse {
  /// Returns a new [GuestAccessContractResponse] instance.
  GuestAccessContractResponse({
    this.auditEventTypes = const [],
    this.defaultDeniedCapabilities = const [],
    this.enabled,
    this.explicitPolicyCapabilities = const [],
    this.externalIdentityLinkingAudited,
    this.identityType,
    this.invitationStates = const [],
    this.silentlyMergedWithInternalUsers,
  });

  List<String> auditEventTypes;

  List<String> defaultDeniedCapabilities;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  bool? enabled;

  List<String> explicitPolicyCapabilities;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  bool? externalIdentityLinkingAudited;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? identityType;

  List<String> invitationStates;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  bool? silentlyMergedWithInternalUsers;

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is GuestAccessContractResponse &&
          _deepEquality.equals(other.auditEventTypes, auditEventTypes) &&
          _deepEquality.equals(
              other.defaultDeniedCapabilities, defaultDeniedCapabilities) &&
          other.enabled == enabled &&
          _deepEquality.equals(
              other.explicitPolicyCapabilities, explicitPolicyCapabilities) &&
          other.externalIdentityLinkingAudited ==
              externalIdentityLinkingAudited &&
          other.identityType == identityType &&
          _deepEquality.equals(other.invitationStates, invitationStates) &&
          other.silentlyMergedWithInternalUsers ==
              silentlyMergedWithInternalUsers;

  @override
  int get hashCode =>
      // ignore: unnecessary_parenthesis
      (auditEventTypes.hashCode) +
      (defaultDeniedCapabilities.hashCode) +
      (enabled == null ? 0 : enabled!.hashCode) +
      (explicitPolicyCapabilities.hashCode) +
      (externalIdentityLinkingAudited == null
          ? 0
          : externalIdentityLinkingAudited!.hashCode) +
      (identityType == null ? 0 : identityType!.hashCode) +
      (invitationStates.hashCode) +
      (silentlyMergedWithInternalUsers == null
          ? 0
          : silentlyMergedWithInternalUsers!.hashCode);

  @override
  String toString() =>
      'GuestAccessContractResponse[auditEventTypes=$auditEventTypes, defaultDeniedCapabilities=$defaultDeniedCapabilities, enabled=$enabled, explicitPolicyCapabilities=$explicitPolicyCapabilities, externalIdentityLinkingAudited=$externalIdentityLinkingAudited, identityType=$identityType, invitationStates=$invitationStates, silentlyMergedWithInternalUsers=$silentlyMergedWithInternalUsers]';

  Map<String, dynamic> toJson() {
    final json = <String, dynamic>{};
    json[r'auditEventTypes'] = this.auditEventTypes;
    json[r'defaultDeniedCapabilities'] = this.defaultDeniedCapabilities;
    if (this.enabled != null) {
      json[r'enabled'] = this.enabled;
    } else {
      json[r'enabled'] = null;
    }
    json[r'explicitPolicyCapabilities'] = this.explicitPolicyCapabilities;
    if (this.externalIdentityLinkingAudited != null) {
      json[r'externalIdentityLinkingAudited'] =
          this.externalIdentityLinkingAudited;
    } else {
      json[r'externalIdentityLinkingAudited'] = null;
    }
    if (this.identityType != null) {
      json[r'identityType'] = this.identityType;
    } else {
      json[r'identityType'] = null;
    }
    json[r'invitationStates'] = this.invitationStates;
    if (this.silentlyMergedWithInternalUsers != null) {
      json[r'silentlyMergedWithInternalUsers'] =
          this.silentlyMergedWithInternalUsers;
    } else {
      json[r'silentlyMergedWithInternalUsers'] = null;
    }
    return json;
  }

  /// Returns a new [GuestAccessContractResponse] instance and imports its values from
  /// [value] if it's a [Map], null otherwise.
  // ignore: prefer_constructors_over_static_methods
  static GuestAccessContractResponse? fromJson(dynamic value) {
    if (value is Map) {
      final json = value.cast<String, dynamic>();

      // Ensure that the map contains the required keys.
      // Note 1: the values aren't checked for validity beyond being non-null.
      // Note 2: this code is stripped in release mode!
      assert(() {
        requiredKeys.forEach((key) {
          assert(json.containsKey(key),
              'Required key "GuestAccessContractResponse[$key]" is missing from JSON.');
          assert(json[key] != null,
              'Required key "GuestAccessContractResponse[$key]" has a null value in JSON.');
        });
        return true;
      }());

      return GuestAccessContractResponse(
        auditEventTypes: json[r'auditEventTypes'] is Iterable
            ? (json[r'auditEventTypes'] as Iterable)
                .cast<String>()
                .toList(growable: false)
            : const [],
        defaultDeniedCapabilities:
            json[r'defaultDeniedCapabilities'] is Iterable
                ? (json[r'defaultDeniedCapabilities'] as Iterable)
                    .cast<String>()
                    .toList(growable: false)
                : const [],
        enabled: mapValueOfType<bool>(json, r'enabled'),
        explicitPolicyCapabilities:
            json[r'explicitPolicyCapabilities'] is Iterable
                ? (json[r'explicitPolicyCapabilities'] as Iterable)
                    .cast<String>()
                    .toList(growable: false)
                : const [],
        externalIdentityLinkingAudited:
            mapValueOfType<bool>(json, r'externalIdentityLinkingAudited'),
        identityType: mapValueOfType<String>(json, r'identityType'),
        invitationStates: json[r'invitationStates'] is Iterable
            ? (json[r'invitationStates'] as Iterable)
                .cast<String>()
                .toList(growable: false)
            : const [],
        silentlyMergedWithInternalUsers:
            mapValueOfType<bool>(json, r'silentlyMergedWithInternalUsers'),
      );
    }
    return null;
  }

  static List<GuestAccessContractResponse> listFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final result = <GuestAccessContractResponse>[];
    if (json is List && json.isNotEmpty) {
      for (final row in json) {
        final value = GuestAccessContractResponse.fromJson(row);
        if (value != null) {
          result.add(value);
        }
      }
    }
    return result.toList(growable: growable);
  }

  static Map<String, GuestAccessContractResponse> mapFromJson(dynamic json) {
    final map = <String, GuestAccessContractResponse>{};
    if (json is Map && json.isNotEmpty) {
      json = json.cast<String, dynamic>(); // ignore: parameter_assignments
      for (final entry in json.entries) {
        final value = GuestAccessContractResponse.fromJson(entry.value);
        if (value != null) {
          map[entry.key] = value;
        }
      }
    }
    return map;
  }

  // maps a json object with a list of GuestAccessContractResponse-objects as value to a dart map
  static Map<String, List<GuestAccessContractResponse>> mapListFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final map = <String, List<GuestAccessContractResponse>>{};
    if (json is Map && json.isNotEmpty) {
      // ignore: parameter_assignments
      json = json.cast<String, dynamic>();
      for (final entry in json.entries) {
        map[entry.key] = GuestAccessContractResponse.listFromJson(
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
