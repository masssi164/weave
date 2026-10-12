//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//
// @dart=2.18

// ignore_for_file: unused_element, unused_import
// ignore_for_file: always_put_required_named_parameters_first
// ignore_for_file: constant_identifier_names
// ignore_for_file: lines_longer_than_80_chars

part of weave_user_api;

class GuestInvitationResponse {
  /// Returns a new [GuestInvitationResponse] instance.
  GuestInvitationResponse({
    this.explicitPolicyRequired,
    this.grantedCapabilities = const [],
    this.guestId,
    this.identityType,
    this.internalUserMerged,
    this.state,
  });

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  bool? explicitPolicyRequired;

  List<String> grantedCapabilities;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? guestId;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? identityType;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  bool? internalUserMerged;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? state;

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is GuestInvitationResponse &&
          other.explicitPolicyRequired == explicitPolicyRequired &&
          _deepEquality.equals(
              other.grantedCapabilities, grantedCapabilities) &&
          other.guestId == guestId &&
          other.identityType == identityType &&
          other.internalUserMerged == internalUserMerged &&
          other.state == state;

  @override
  int get hashCode =>
      // ignore: unnecessary_parenthesis
      (explicitPolicyRequired == null ? 0 : explicitPolicyRequired!.hashCode) +
      (grantedCapabilities.hashCode) +
      (guestId == null ? 0 : guestId!.hashCode) +
      (identityType == null ? 0 : identityType!.hashCode) +
      (internalUserMerged == null ? 0 : internalUserMerged!.hashCode) +
      (state == null ? 0 : state!.hashCode);

  @override
  String toString() =>
      'GuestInvitationResponse[explicitPolicyRequired=$explicitPolicyRequired, grantedCapabilities=$grantedCapabilities, guestId=$guestId, identityType=$identityType, internalUserMerged=$internalUserMerged, state=$state]';

  Map<String, dynamic> toJson() {
    final json = <String, dynamic>{};
    if (this.explicitPolicyRequired != null) {
      json[r'explicitPolicyRequired'] = this.explicitPolicyRequired;
    } else {
      json[r'explicitPolicyRequired'] = null;
    }
    json[r'grantedCapabilities'] = this.grantedCapabilities;
    if (this.guestId != null) {
      json[r'guestId'] = this.guestId;
    } else {
      json[r'guestId'] = null;
    }
    if (this.identityType != null) {
      json[r'identityType'] = this.identityType;
    } else {
      json[r'identityType'] = null;
    }
    if (this.internalUserMerged != null) {
      json[r'internalUserMerged'] = this.internalUserMerged;
    } else {
      json[r'internalUserMerged'] = null;
    }
    if (this.state != null) {
      json[r'state'] = this.state;
    } else {
      json[r'state'] = null;
    }
    return json;
  }

  /// Returns a new [GuestInvitationResponse] instance and imports its values from
  /// [value] if it's a [Map], null otherwise.
  // ignore: prefer_constructors_over_static_methods
  static GuestInvitationResponse? fromJson(dynamic value) {
    if (value is Map) {
      final json = value.cast<String, dynamic>();

      // Ensure that the map contains the required keys.
      // Note 1: the values aren't checked for validity beyond being non-null.
      // Note 2: this code is stripped in release mode!
      assert(() {
        requiredKeys.forEach((key) {
          assert(json.containsKey(key),
              'Required key "GuestInvitationResponse[$key]" is missing from JSON.');
          assert(json[key] != null,
              'Required key "GuestInvitationResponse[$key]" has a null value in JSON.');
        });
        return true;
      }());

      return GuestInvitationResponse(
        explicitPolicyRequired:
            mapValueOfType<bool>(json, r'explicitPolicyRequired'),
        grantedCapabilities: json[r'grantedCapabilities'] is Iterable
            ? (json[r'grantedCapabilities'] as Iterable)
                .cast<String>()
                .toList(growable: false)
            : const [],
        guestId: mapValueOfType<String>(json, r'guestId'),
        identityType: mapValueOfType<String>(json, r'identityType'),
        internalUserMerged: mapValueOfType<bool>(json, r'internalUserMerged'),
        state: mapValueOfType<String>(json, r'state'),
      );
    }
    return null;
  }

  static List<GuestInvitationResponse> listFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final result = <GuestInvitationResponse>[];
    if (json is List && json.isNotEmpty) {
      for (final row in json) {
        final value = GuestInvitationResponse.fromJson(row);
        if (value != null) {
          result.add(value);
        }
      }
    }
    return result.toList(growable: growable);
  }

  static Map<String, GuestInvitationResponse> mapFromJson(dynamic json) {
    final map = <String, GuestInvitationResponse>{};
    if (json is Map && json.isNotEmpty) {
      json = json.cast<String, dynamic>(); // ignore: parameter_assignments
      for (final entry in json.entries) {
        final value = GuestInvitationResponse.fromJson(entry.value);
        if (value != null) {
          map[entry.key] = value;
        }
      }
    }
    return map;
  }

  // maps a json object with a list of GuestInvitationResponse-objects as value to a dart map
  static Map<String, List<GuestInvitationResponse>> mapListFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final map = <String, List<GuestInvitationResponse>>{};
    if (json is Map && json.isNotEmpty) {
      // ignore: parameter_assignments
      json = json.cast<String, dynamic>();
      for (final entry in json.entries) {
        map[entry.key] = GuestInvitationResponse.listFromJson(
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
