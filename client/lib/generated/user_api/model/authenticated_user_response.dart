//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//
// @dart=2.18

// ignore_for_file: unused_element, unused_import
// ignore_for_file: always_put_required_named_parameters_first
// ignore_for_file: constant_identifier_names
// ignore_for_file: lines_longer_than_80_chars

part of weave_user_api;

class AuthenticatedUserResponse {
  /// Returns a new [AuthenticatedUserResponse] instance.
  AuthenticatedUserResponse({
    this.accountId,
    this.audience = const [],
    this.contextRoles = const [],
    this.displayName,
    this.email,
    this.emailPrimaryKey,
    this.emailVerified,
    this.groups = const [],
    this.identityIssuer,
    this.issuedFor,
    this.locale,
    this.moduleSyncStatus,
    this.organizationId,
    this.primaryIdentityKey,
    this.providerRoleMappings = const [],
    this.roles = const [],
    this.subject,
    this.timezone,
    this.userId,
    this.username,
  });

  /// Support-safe account id derived from the primary identity key.
  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? accountId;

  /// Token audience values.
  List<String> audience;

  /// Context-local roles for the current organization/session.
  List<String> contextRoles;

  /// User-visible product display name.
  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? displayName;

  /// Verified email address when available; never the primary identity key.
  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? email;

  /// Always false: email is an attribute, not the primary key.
  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  bool? emailPrimaryKey;

  /// Whether the identity source reports the email address as verified.
  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  bool? emailVerified;

  /// Workspace or module groups.
  List<String> groups;

  /// Identity source issuer used to form the immutable primary identity key.
  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? identityIssuer;

  /// Authorized party/client that requested the token.
  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? issuedFor;

  /// Preferred locale.
  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? locale;

  /// Product profile sync status by module.
  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  ModuleSyncStatusResponse? moduleSyncStatus;

  /// Organization identifier in Weave policy space.
  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? organizationId;

  /// Documented immutable primary identity key: issuer plus subject.
  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? primaryIdentityKey;

  /// Support-safe role/group claim mapping evidence.
  List<String> providerRoleMappings;

  /// Canonical Weave organization roles mapped from identity-source claims.
  List<String> roles;

  /// Identity source subject used to form the immutable primary identity key.
  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? subject;

  /// Preferred timezone.
  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? timezone;

  /// Stable immutable Weave account identifier derived from issuer plus subject, never from email.
  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? userId;

  /// Stable login/workspace handle.
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
      other is AuthenticatedUserResponse &&
          other.accountId == accountId &&
          _deepEquality.equals(other.audience, audience) &&
          _deepEquality.equals(other.contextRoles, contextRoles) &&
          other.displayName == displayName &&
          other.email == email &&
          other.emailPrimaryKey == emailPrimaryKey &&
          other.emailVerified == emailVerified &&
          _deepEquality.equals(other.groups, groups) &&
          other.identityIssuer == identityIssuer &&
          other.issuedFor == issuedFor &&
          other.locale == locale &&
          other.moduleSyncStatus == moduleSyncStatus &&
          other.organizationId == organizationId &&
          other.primaryIdentityKey == primaryIdentityKey &&
          _deepEquality.equals(
              other.providerRoleMappings, providerRoleMappings) &&
          _deepEquality.equals(other.roles, roles) &&
          other.subject == subject &&
          other.timezone == timezone &&
          other.userId == userId &&
          other.username == username;

  @override
  int get hashCode =>
      // ignore: unnecessary_parenthesis
      (accountId == null ? 0 : accountId!.hashCode) +
      (audience.hashCode) +
      (contextRoles.hashCode) +
      (displayName == null ? 0 : displayName!.hashCode) +
      (email == null ? 0 : email!.hashCode) +
      (emailPrimaryKey == null ? 0 : emailPrimaryKey!.hashCode) +
      (emailVerified == null ? 0 : emailVerified!.hashCode) +
      (groups.hashCode) +
      (identityIssuer == null ? 0 : identityIssuer!.hashCode) +
      (issuedFor == null ? 0 : issuedFor!.hashCode) +
      (locale == null ? 0 : locale!.hashCode) +
      (moduleSyncStatus == null ? 0 : moduleSyncStatus!.hashCode) +
      (organizationId == null ? 0 : organizationId!.hashCode) +
      (primaryIdentityKey == null ? 0 : primaryIdentityKey!.hashCode) +
      (providerRoleMappings.hashCode) +
      (roles.hashCode) +
      (subject == null ? 0 : subject!.hashCode) +
      (timezone == null ? 0 : timezone!.hashCode) +
      (userId == null ? 0 : userId!.hashCode) +
      (username == null ? 0 : username!.hashCode);

  @override
  String toString() =>
      'AuthenticatedUserResponse[accountId=$accountId, audience=$audience, contextRoles=$contextRoles, displayName=$displayName, email=$email, emailPrimaryKey=$emailPrimaryKey, emailVerified=$emailVerified, groups=$groups, identityIssuer=$identityIssuer, issuedFor=$issuedFor, locale=$locale, moduleSyncStatus=$moduleSyncStatus, organizationId=$organizationId, primaryIdentityKey=$primaryIdentityKey, providerRoleMappings=$providerRoleMappings, roles=$roles, subject=$subject, timezone=$timezone, userId=$userId, username=$username]';

  Map<String, dynamic> toJson() {
    final json = <String, dynamic>{};
    if (this.accountId != null) {
      json[r'accountId'] = this.accountId;
    } else {
      json[r'accountId'] = null;
    }
    json[r'audience'] = this.audience;
    json[r'contextRoles'] = this.contextRoles;
    if (this.displayName != null) {
      json[r'displayName'] = this.displayName;
    } else {
      json[r'displayName'] = null;
    }
    if (this.email != null) {
      json[r'email'] = this.email;
    } else {
      json[r'email'] = null;
    }
    if (this.emailPrimaryKey != null) {
      json[r'emailPrimaryKey'] = this.emailPrimaryKey;
    } else {
      json[r'emailPrimaryKey'] = null;
    }
    if (this.emailVerified != null) {
      json[r'emailVerified'] = this.emailVerified;
    } else {
      json[r'emailVerified'] = null;
    }
    json[r'groups'] = this.groups;
    if (this.identityIssuer != null) {
      json[r'identityIssuer'] = this.identityIssuer;
    } else {
      json[r'identityIssuer'] = null;
    }
    if (this.issuedFor != null) {
      json[r'issuedFor'] = this.issuedFor;
    } else {
      json[r'issuedFor'] = null;
    }
    if (this.locale != null) {
      json[r'locale'] = this.locale;
    } else {
      json[r'locale'] = null;
    }
    if (this.moduleSyncStatus != null) {
      json[r'moduleSyncStatus'] = this.moduleSyncStatus;
    } else {
      json[r'moduleSyncStatus'] = null;
    }
    if (this.organizationId != null) {
      json[r'organizationId'] = this.organizationId;
    } else {
      json[r'organizationId'] = null;
    }
    if (this.primaryIdentityKey != null) {
      json[r'primaryIdentityKey'] = this.primaryIdentityKey;
    } else {
      json[r'primaryIdentityKey'] = null;
    }
    json[r'providerRoleMappings'] = this.providerRoleMappings;
    json[r'roles'] = this.roles;
    if (this.subject != null) {
      json[r'subject'] = this.subject;
    } else {
      json[r'subject'] = null;
    }
    if (this.timezone != null) {
      json[r'timezone'] = this.timezone;
    } else {
      json[r'timezone'] = null;
    }
    if (this.userId != null) {
      json[r'userId'] = this.userId;
    } else {
      json[r'userId'] = null;
    }
    if (this.username != null) {
      json[r'username'] = this.username;
    } else {
      json[r'username'] = null;
    }
    return json;
  }

  /// Returns a new [AuthenticatedUserResponse] instance and imports its values from
  /// [value] if it's a [Map], null otherwise.
  // ignore: prefer_constructors_over_static_methods
  static AuthenticatedUserResponse? fromJson(dynamic value) {
    if (value is Map) {
      final json = value.cast<String, dynamic>();

      // Ensure that the map contains the required keys.
      // Note 1: the values aren't checked for validity beyond being non-null.
      // Note 2: this code is stripped in release mode!
      assert(() {
        requiredKeys.forEach((key) {
          assert(json.containsKey(key),
              'Required key "AuthenticatedUserResponse[$key]" is missing from JSON.');
          assert(json[key] != null,
              'Required key "AuthenticatedUserResponse[$key]" has a null value in JSON.');
        });
        return true;
      }());

      return AuthenticatedUserResponse(
        accountId: mapValueOfType<String>(json, r'accountId'),
        audience: json[r'audience'] is Iterable
            ? (json[r'audience'] as Iterable)
                .cast<String>()
                .toList(growable: false)
            : const [],
        contextRoles: json[r'contextRoles'] is Iterable
            ? (json[r'contextRoles'] as Iterable)
                .cast<String>()
                .toList(growable: false)
            : const [],
        displayName: mapValueOfType<String>(json, r'displayName'),
        email: mapValueOfType<String>(json, r'email'),
        emailPrimaryKey: mapValueOfType<bool>(json, r'emailPrimaryKey'),
        emailVerified: mapValueOfType<bool>(json, r'emailVerified'),
        groups: json[r'groups'] is Iterable
            ? (json[r'groups'] as Iterable)
                .cast<String>()
                .toList(growable: false)
            : const [],
        identityIssuer: mapValueOfType<String>(json, r'identityIssuer'),
        issuedFor: mapValueOfType<String>(json, r'issuedFor'),
        locale: mapValueOfType<String>(json, r'locale'),
        moduleSyncStatus:
            ModuleSyncStatusResponse.fromJson(json[r'moduleSyncStatus']),
        organizationId: mapValueOfType<String>(json, r'organizationId'),
        primaryIdentityKey: mapValueOfType<String>(json, r'primaryIdentityKey'),
        providerRoleMappings: json[r'providerRoleMappings'] is Iterable
            ? (json[r'providerRoleMappings'] as Iterable)
                .cast<String>()
                .toList(growable: false)
            : const [],
        roles: json[r'roles'] is Iterable
            ? (json[r'roles'] as Iterable)
                .cast<String>()
                .toList(growable: false)
            : const [],
        subject: mapValueOfType<String>(json, r'subject'),
        timezone: mapValueOfType<String>(json, r'timezone'),
        userId: mapValueOfType<String>(json, r'userId'),
        username: mapValueOfType<String>(json, r'username'),
      );
    }
    return null;
  }

  static List<AuthenticatedUserResponse> listFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final result = <AuthenticatedUserResponse>[];
    if (json is List && json.isNotEmpty) {
      for (final row in json) {
        final value = AuthenticatedUserResponse.fromJson(row);
        if (value != null) {
          result.add(value);
        }
      }
    }
    return result.toList(growable: growable);
  }

  static Map<String, AuthenticatedUserResponse> mapFromJson(dynamic json) {
    final map = <String, AuthenticatedUserResponse>{};
    if (json is Map && json.isNotEmpty) {
      json = json.cast<String, dynamic>(); // ignore: parameter_assignments
      for (final entry in json.entries) {
        final value = AuthenticatedUserResponse.fromJson(entry.value);
        if (value != null) {
          map[entry.key] = value;
        }
      }
    }
    return map;
  }

  // maps a json object with a list of AuthenticatedUserResponse-objects as value to a dart map
  static Map<String, List<AuthenticatedUserResponse>> mapListFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final map = <String, List<AuthenticatedUserResponse>>{};
    if (json is Map && json.isNotEmpty) {
      // ignore: parameter_assignments
      json = json.cast<String, dynamic>();
      for (final entry in json.entries) {
        map[entry.key] = AuthenticatedUserResponse.listFromJson(
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
