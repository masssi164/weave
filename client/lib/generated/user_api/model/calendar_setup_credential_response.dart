//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//
// @dart=2.18

// ignore_for_file: unused_element, unused_import
// ignore_for_file: always_put_required_named_parameters_first
// ignore_for_file: constant_identifier_names
// ignore_for_file: lines_longer_than_80_chars

part of weave_user_api;

class CalendarSetupCredentialResponse {
  /// Returns a new [CalendarSetupCredentialResponse] instance.
  CalendarSetupCredentialResponse({
    this.clientType,
    this.credentialId,
    this.expiresAt,
    this.issuedAt,
    this.label,
    this.principalRef,
    this.profilePasswordEligible,
    this.revocationActions = const [],
    this.revokedAt,
    this.secret,
    this.secretMaterialReturned,
    this.state,
    this.username,
  });

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? clientType;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? credentialId;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  DateTime? expiresAt;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  DateTime? issuedAt;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? label;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? principalRef;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  bool? profilePasswordEligible;

  List<String> revocationActions;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  DateTime? revokedAt;

  /// One-time CalDAV secret. Present only in the create response.
  String? secret;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  bool? secretMaterialReturned;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? state;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? username;

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is CalendarSetupCredentialResponse &&
          other.clientType == clientType &&
          other.credentialId == credentialId &&
          other.expiresAt == expiresAt &&
          other.issuedAt == issuedAt &&
          other.label == label &&
          other.principalRef == principalRef &&
          other.profilePasswordEligible == profilePasswordEligible &&
          _deepEquality.equals(other.revocationActions, revocationActions) &&
          other.revokedAt == revokedAt &&
          other.secret == secret &&
          other.secretMaterialReturned == secretMaterialReturned &&
          other.state == state &&
          other.username == username;

  @override
  int get hashCode =>
      // ignore: unnecessary_parenthesis
      (clientType == null ? 0 : clientType!.hashCode) +
      (credentialId == null ? 0 : credentialId!.hashCode) +
      (expiresAt == null ? 0 : expiresAt!.hashCode) +
      (issuedAt == null ? 0 : issuedAt!.hashCode) +
      (label == null ? 0 : label!.hashCode) +
      (principalRef == null ? 0 : principalRef!.hashCode) +
      (profilePasswordEligible == null
          ? 0
          : profilePasswordEligible!.hashCode) +
      (revocationActions.hashCode) +
      (revokedAt == null ? 0 : revokedAt!.hashCode) +
      (secret == null ? 0 : secret!.hashCode) +
      (secretMaterialReturned == null ? 0 : secretMaterialReturned!.hashCode) +
      (state == null ? 0 : state!.hashCode) +
      (username == null ? 0 : username!.hashCode);

  @override
  String toString() =>
      'CalendarSetupCredentialResponse[clientType=$clientType, credentialId=$credentialId, expiresAt=$expiresAt, issuedAt=$issuedAt, label=$label, principalRef=$principalRef, profilePasswordEligible=$profilePasswordEligible, revocationActions=$revocationActions, revokedAt=$revokedAt, secret=$secret, secretMaterialReturned=$secretMaterialReturned, state=$state, username=$username]';

  Map<String, dynamic> toJson() {
    final json = <String, dynamic>{};
    if (this.clientType != null) {
      json[r'clientType'] = this.clientType;
    } else {
      json[r'clientType'] = null;
    }
    if (this.credentialId != null) {
      json[r'credentialId'] = this.credentialId;
    } else {
      json[r'credentialId'] = null;
    }
    if (this.expiresAt != null) {
      json[r'expiresAt'] = this.expiresAt!.toUtc().toIso8601String();
    } else {
      json[r'expiresAt'] = null;
    }
    if (this.issuedAt != null) {
      json[r'issuedAt'] = this.issuedAt!.toUtc().toIso8601String();
    } else {
      json[r'issuedAt'] = null;
    }
    if (this.label != null) {
      json[r'label'] = this.label;
    } else {
      json[r'label'] = null;
    }
    if (this.principalRef != null) {
      json[r'principalRef'] = this.principalRef;
    } else {
      json[r'principalRef'] = null;
    }
    if (this.profilePasswordEligible != null) {
      json[r'profilePasswordEligible'] = this.profilePasswordEligible;
    } else {
      json[r'profilePasswordEligible'] = null;
    }
    json[r'revocationActions'] = this.revocationActions;
    if (this.revokedAt != null) {
      json[r'revokedAt'] = this.revokedAt!.toUtc().toIso8601String();
    } else {
      json[r'revokedAt'] = null;
    }
    if (this.secret != null) {
      json[r'secret'] = this.secret;
    } else {
      json[r'secret'] = null;
    }
    if (this.secretMaterialReturned != null) {
      json[r'secretMaterialReturned'] = this.secretMaterialReturned;
    } else {
      json[r'secretMaterialReturned'] = null;
    }
    if (this.state != null) {
      json[r'state'] = this.state;
    } else {
      json[r'state'] = null;
    }
    if (this.username != null) {
      json[r'username'] = this.username;
    } else {
      json[r'username'] = null;
    }
    return json;
  }

  /// Returns a new [CalendarSetupCredentialResponse] instance and imports its values from
  /// [value] if it's a [Map], null otherwise.
  // ignore: prefer_constructors_over_static_methods
  static CalendarSetupCredentialResponse? fromJson(dynamic value) {
    if (value is Map) {
      final json = value.cast<String, dynamic>();

      // Ensure that the map contains the required keys.
      // Note 1: the values aren't checked for validity beyond being non-null.
      // Note 2: this code is stripped in release mode!
      assert(() {
        requiredKeys.forEach((key) {
          assert(json.containsKey(key),
              'Required key "CalendarSetupCredentialResponse[$key]" is missing from JSON.');
          assert(json[key] != null,
              'Required key "CalendarSetupCredentialResponse[$key]" has a null value in JSON.');
        });
        return true;
      }());

      return CalendarSetupCredentialResponse(
        clientType: mapValueOfType<String>(json, r'clientType'),
        credentialId: mapValueOfType<String>(json, r'credentialId'),
        expiresAt: mapDateTime(json, r'expiresAt', r''),
        issuedAt: mapDateTime(json, r'issuedAt', r''),
        label: mapValueOfType<String>(json, r'label'),
        principalRef: mapValueOfType<String>(json, r'principalRef'),
        profilePasswordEligible:
            mapValueOfType<bool>(json, r'profilePasswordEligible'),
        revocationActions: json[r'revocationActions'] is Iterable
            ? (json[r'revocationActions'] as Iterable)
                .cast<String>()
                .toList(growable: false)
            : const [],
        revokedAt: mapDateTime(json, r'revokedAt', r''),
        secret: mapValueOfType<String>(json, r'secret'),
        secretMaterialReturned:
            mapValueOfType<bool>(json, r'secretMaterialReturned'),
        state: mapValueOfType<String>(json, r'state'),
        username: mapValueOfType<String>(json, r'username'),
      );
    }
    return null;
  }

  static List<CalendarSetupCredentialResponse> listFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final result = <CalendarSetupCredentialResponse>[];
    if (json is List && json.isNotEmpty) {
      for (final row in json) {
        final value = CalendarSetupCredentialResponse.fromJson(row);
        if (value != null) {
          result.add(value);
        }
      }
    }
    return result.toList(growable: growable);
  }

  static Map<String, CalendarSetupCredentialResponse> mapFromJson(
      dynamic json) {
    final map = <String, CalendarSetupCredentialResponse>{};
    if (json is Map && json.isNotEmpty) {
      json = json.cast<String, dynamic>(); // ignore: parameter_assignments
      for (final entry in json.entries) {
        final value = CalendarSetupCredentialResponse.fromJson(entry.value);
        if (value != null) {
          map[entry.key] = value;
        }
      }
    }
    return map;
  }

  // maps a json object with a list of CalendarSetupCredentialResponse-objects as value to a dart map
  static Map<String, List<CalendarSetupCredentialResponse>> mapListFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final map = <String, List<CalendarSetupCredentialResponse>>{};
    if (json is Map && json.isNotEmpty) {
      // ignore: parameter_assignments
      json = json.cast<String, dynamic>();
      for (final entry in json.entries) {
        map[entry.key] = CalendarSetupCredentialResponse.listFromJson(
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
