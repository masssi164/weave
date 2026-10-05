//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//
// @dart=2.18

// ignore_for_file: unused_element, unused_import
// ignore_for_file: always_put_required_named_parameters_first
// ignore_for_file: constant_identifier_names
// ignore_for_file: lines_longer_than_80_chars

part of weave_user_api;

class CalendarCredentialReadinessResponse {
  /// Returns a new [CalendarCredentialReadinessResponse] instance.
  CalendarCredentialReadinessResponse({
    this.appleProfilePasswordIncluded,
    this.appleProfileSigned,
    this.backendActorCredentialsExposed,
    this.blockers = const [],
    this.readOnlySubscriptionTokensAvailable,
    this.revocableCredentialsAvailable,
    this.status,
  });

  /// Whether generated Apple profiles include a password or token.
  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  bool? appleProfilePasswordIncluded;

  /// Whether a signed Apple .mobileconfig download is available.
  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  bool? appleProfileSigned;

  /// Whether backend actor credentials can appear in client-facing setup artifacts. Must remain false.
  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  bool? backendActorCredentialsExposed;

  /// Support-safe blockers before setup artifacts may become downloadable.
  List<String> blockers;

  /// Whether read-only ICS/webcal feed tokens are available.
  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  bool? readOnlySubscriptionTokensAvailable;

  /// Whether Weave can issue/revoke per-client CalDAV credentials.
  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  bool? revocableCredentialsAvailable;

  /// Stable machine-readable readiness status.
  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? status;

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is CalendarCredentialReadinessResponse &&
          other.appleProfilePasswordIncluded == appleProfilePasswordIncluded &&
          other.appleProfileSigned == appleProfileSigned &&
          other.backendActorCredentialsExposed ==
              backendActorCredentialsExposed &&
          _deepEquality.equals(other.blockers, blockers) &&
          other.readOnlySubscriptionTokensAvailable ==
              readOnlySubscriptionTokensAvailable &&
          other.revocableCredentialsAvailable ==
              revocableCredentialsAvailable &&
          other.status == status;

  @override
  int get hashCode =>
      // ignore: unnecessary_parenthesis
      (appleProfilePasswordIncluded == null
          ? 0
          : appleProfilePasswordIncluded!.hashCode) +
      (appleProfileSigned == null ? 0 : appleProfileSigned!.hashCode) +
      (backendActorCredentialsExposed == null
          ? 0
          : backendActorCredentialsExposed!.hashCode) +
      (blockers.hashCode) +
      (readOnlySubscriptionTokensAvailable == null
          ? 0
          : readOnlySubscriptionTokensAvailable!.hashCode) +
      (revocableCredentialsAvailable == null
          ? 0
          : revocableCredentialsAvailable!.hashCode) +
      (status == null ? 0 : status!.hashCode);

  @override
  String toString() =>
      'CalendarCredentialReadinessResponse[appleProfilePasswordIncluded=$appleProfilePasswordIncluded, appleProfileSigned=$appleProfileSigned, backendActorCredentialsExposed=$backendActorCredentialsExposed, blockers=$blockers, readOnlySubscriptionTokensAvailable=$readOnlySubscriptionTokensAvailable, revocableCredentialsAvailable=$revocableCredentialsAvailable, status=$status]';

  Map<String, dynamic> toJson() {
    final json = <String, dynamic>{};
    if (this.appleProfilePasswordIncluded != null) {
      json[r'appleProfilePasswordIncluded'] = this.appleProfilePasswordIncluded;
    } else {
      json[r'appleProfilePasswordIncluded'] = null;
    }
    if (this.appleProfileSigned != null) {
      json[r'appleProfileSigned'] = this.appleProfileSigned;
    } else {
      json[r'appleProfileSigned'] = null;
    }
    if (this.backendActorCredentialsExposed != null) {
      json[r'backendActorCredentialsExposed'] =
          this.backendActorCredentialsExposed;
    } else {
      json[r'backendActorCredentialsExposed'] = null;
    }
    json[r'blockers'] = this.blockers;
    if (this.readOnlySubscriptionTokensAvailable != null) {
      json[r'readOnlySubscriptionTokensAvailable'] =
          this.readOnlySubscriptionTokensAvailable;
    } else {
      json[r'readOnlySubscriptionTokensAvailable'] = null;
    }
    if (this.revocableCredentialsAvailable != null) {
      json[r'revocableCredentialsAvailable'] =
          this.revocableCredentialsAvailable;
    } else {
      json[r'revocableCredentialsAvailable'] = null;
    }
    if (this.status != null) {
      json[r'status'] = this.status;
    } else {
      json[r'status'] = null;
    }
    return json;
  }

  /// Returns a new [CalendarCredentialReadinessResponse] instance and imports its values from
  /// [value] if it's a [Map], null otherwise.
  // ignore: prefer_constructors_over_static_methods
  static CalendarCredentialReadinessResponse? fromJson(dynamic value) {
    if (value is Map) {
      final json = value.cast<String, dynamic>();

      // Ensure that the map contains the required keys.
      // Note 1: the values aren't checked for validity beyond being non-null.
      // Note 2: this code is stripped in release mode!
      assert(() {
        requiredKeys.forEach((key) {
          assert(json.containsKey(key),
              'Required key "CalendarCredentialReadinessResponse[$key]" is missing from JSON.');
          assert(json[key] != null,
              'Required key "CalendarCredentialReadinessResponse[$key]" has a null value in JSON.');
        });
        return true;
      }());

      return CalendarCredentialReadinessResponse(
        appleProfilePasswordIncluded:
            mapValueOfType<bool>(json, r'appleProfilePasswordIncluded'),
        appleProfileSigned: mapValueOfType<bool>(json, r'appleProfileSigned'),
        backendActorCredentialsExposed:
            mapValueOfType<bool>(json, r'backendActorCredentialsExposed'),
        blockers: json[r'blockers'] is Iterable
            ? (json[r'blockers'] as Iterable)
                .cast<String>()
                .toList(growable: false)
            : const [],
        readOnlySubscriptionTokensAvailable:
            mapValueOfType<bool>(json, r'readOnlySubscriptionTokensAvailable'),
        revocableCredentialsAvailable:
            mapValueOfType<bool>(json, r'revocableCredentialsAvailable'),
        status: mapValueOfType<String>(json, r'status'),
      );
    }
    return null;
  }

  static List<CalendarCredentialReadinessResponse> listFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final result = <CalendarCredentialReadinessResponse>[];
    if (json is List && json.isNotEmpty) {
      for (final row in json) {
        final value = CalendarCredentialReadinessResponse.fromJson(row);
        if (value != null) {
          result.add(value);
        }
      }
    }
    return result.toList(growable: growable);
  }

  static Map<String, CalendarCredentialReadinessResponse> mapFromJson(
      dynamic json) {
    final map = <String, CalendarCredentialReadinessResponse>{};
    if (json is Map && json.isNotEmpty) {
      json = json.cast<String, dynamic>(); // ignore: parameter_assignments
      for (final entry in json.entries) {
        final value = CalendarCredentialReadinessResponse.fromJson(entry.value);
        if (value != null) {
          map[entry.key] = value;
        }
      }
    }
    return map;
  }

  // maps a json object with a list of CalendarCredentialReadinessResponse-objects as value to a dart map
  static Map<String, List<CalendarCredentialReadinessResponse>> mapListFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final map = <String, List<CalendarCredentialReadinessResponse>>{};
    if (json is Map && json.isNotEmpty) {
      // ignore: parameter_assignments
      json = json.cast<String, dynamic>();
      for (final entry in json.entries) {
        map[entry.key] = CalendarCredentialReadinessResponse.listFromJson(
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
