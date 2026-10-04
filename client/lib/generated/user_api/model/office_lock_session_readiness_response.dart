//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//
// @dart=2.18

// ignore_for_file: unused_element, unused_import
// ignore_for_file: always_put_required_named_parameters_first
// ignore_for_file: constant_identifier_names
// ignore_for_file: lines_longer_than_80_chars

part of weave_user_api;

class OfficeLockSessionReadinessResponse {
  /// Returns a new [OfficeLockSessionReadinessResponse] instance.
  OfficeLockSessionReadinessResponse({
    this.callbackVerification,
    this.documentLocks,
    this.sessionTokens,
    this.supportSafe,
  });

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? callbackVerification;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? documentLocks;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? sessionTokens;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  bool? supportSafe;

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is OfficeLockSessionReadinessResponse &&
          other.callbackVerification == callbackVerification &&
          other.documentLocks == documentLocks &&
          other.sessionTokens == sessionTokens &&
          other.supportSafe == supportSafe;

  @override
  int get hashCode =>
      // ignore: unnecessary_parenthesis
      (callbackVerification == null ? 0 : callbackVerification!.hashCode) +
      (documentLocks == null ? 0 : documentLocks!.hashCode) +
      (sessionTokens == null ? 0 : sessionTokens!.hashCode) +
      (supportSafe == null ? 0 : supportSafe!.hashCode);

  @override
  String toString() =>
      'OfficeLockSessionReadinessResponse[callbackVerification=$callbackVerification, documentLocks=$documentLocks, sessionTokens=$sessionTokens, supportSafe=$supportSafe]';

  Map<String, dynamic> toJson() {
    final json = <String, dynamic>{};
    if (this.callbackVerification != null) {
      json[r'callbackVerification'] = this.callbackVerification;
    } else {
      json[r'callbackVerification'] = null;
    }
    if (this.documentLocks != null) {
      json[r'documentLocks'] = this.documentLocks;
    } else {
      json[r'documentLocks'] = null;
    }
    if (this.sessionTokens != null) {
      json[r'sessionTokens'] = this.sessionTokens;
    } else {
      json[r'sessionTokens'] = null;
    }
    if (this.supportSafe != null) {
      json[r'supportSafe'] = this.supportSafe;
    } else {
      json[r'supportSafe'] = null;
    }
    return json;
  }

  /// Returns a new [OfficeLockSessionReadinessResponse] instance and imports its values from
  /// [value] if it's a [Map], null otherwise.
  // ignore: prefer_constructors_over_static_methods
  static OfficeLockSessionReadinessResponse? fromJson(dynamic value) {
    if (value is Map) {
      final json = value.cast<String, dynamic>();

      // Ensure that the map contains the required keys.
      // Note 1: the values aren't checked for validity beyond being non-null.
      // Note 2: this code is stripped in release mode!
      assert(() {
        requiredKeys.forEach((key) {
          assert(json.containsKey(key),
              'Required key "OfficeLockSessionReadinessResponse[$key]" is missing from JSON.');
          assert(json[key] != null,
              'Required key "OfficeLockSessionReadinessResponse[$key]" has a null value in JSON.');
        });
        return true;
      }());

      return OfficeLockSessionReadinessResponse(
        callbackVerification:
            mapValueOfType<String>(json, r'callbackVerification'),
        documentLocks: mapValueOfType<String>(json, r'documentLocks'),
        sessionTokens: mapValueOfType<String>(json, r'sessionTokens'),
        supportSafe: mapValueOfType<bool>(json, r'supportSafe'),
      );
    }
    return null;
  }

  static List<OfficeLockSessionReadinessResponse> listFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final result = <OfficeLockSessionReadinessResponse>[];
    if (json is List && json.isNotEmpty) {
      for (final row in json) {
        final value = OfficeLockSessionReadinessResponse.fromJson(row);
        if (value != null) {
          result.add(value);
        }
      }
    }
    return result.toList(growable: growable);
  }

  static Map<String, OfficeLockSessionReadinessResponse> mapFromJson(
      dynamic json) {
    final map = <String, OfficeLockSessionReadinessResponse>{};
    if (json is Map && json.isNotEmpty) {
      json = json.cast<String, dynamic>(); // ignore: parameter_assignments
      for (final entry in json.entries) {
        final value = OfficeLockSessionReadinessResponse.fromJson(entry.value);
        if (value != null) {
          map[entry.key] = value;
        }
      }
    }
    return map;
  }

  // maps a json object with a list of OfficeLockSessionReadinessResponse-objects as value to a dart map
  static Map<String, List<OfficeLockSessionReadinessResponse>> mapListFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final map = <String, List<OfficeLockSessionReadinessResponse>>{};
    if (json is Map && json.isNotEmpty) {
      // ignore: parameter_assignments
      json = json.cast<String, dynamic>();
      for (final entry in json.entries) {
        map[entry.key] = OfficeLockSessionReadinessResponse.listFromJson(
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
