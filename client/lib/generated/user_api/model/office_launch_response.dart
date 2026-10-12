//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//
// @dart=2.18

// ignore_for_file: unused_element, unused_import
// ignore_for_file: always_put_required_named_parameters_first
// ignore_for_file: constant_identifier_names
// ignore_for_file: lines_longer_than_80_chars

part of weave_user_api;

class OfficeLaunchResponse {
  /// Returns a new [OfficeLaunchResponse] instance.
  OfficeLaunchResponse({
    this.expiresAt,
    this.grantedPermissions = const [],
    this.launchMode,
    this.providerKey,
    this.sessionId,
  });

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  DateTime? expiresAt;

  List<String> grantedPermissions;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? launchMode;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? providerKey;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? sessionId;

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is OfficeLaunchResponse &&
          other.expiresAt == expiresAt &&
          _deepEquality.equals(other.grantedPermissions, grantedPermissions) &&
          other.launchMode == launchMode &&
          other.providerKey == providerKey &&
          other.sessionId == sessionId;

  @override
  int get hashCode =>
      // ignore: unnecessary_parenthesis
      (expiresAt == null ? 0 : expiresAt!.hashCode) +
      (grantedPermissions.hashCode) +
      (launchMode == null ? 0 : launchMode!.hashCode) +
      (providerKey == null ? 0 : providerKey!.hashCode) +
      (sessionId == null ? 0 : sessionId!.hashCode);

  @override
  String toString() =>
      'OfficeLaunchResponse[expiresAt=$expiresAt, grantedPermissions=$grantedPermissions, launchMode=$launchMode, providerKey=$providerKey, sessionId=$sessionId]';

  Map<String, dynamic> toJson() {
    final json = <String, dynamic>{};
    if (this.expiresAt != null) {
      json[r'expiresAt'] = this.expiresAt!.toUtc().toIso8601String();
    } else {
      json[r'expiresAt'] = null;
    }
    json[r'grantedPermissions'] = this.grantedPermissions;
    if (this.launchMode != null) {
      json[r'launchMode'] = this.launchMode;
    } else {
      json[r'launchMode'] = null;
    }
    if (this.providerKey != null) {
      json[r'providerKey'] = this.providerKey;
    } else {
      json[r'providerKey'] = null;
    }
    if (this.sessionId != null) {
      json[r'sessionId'] = this.sessionId;
    } else {
      json[r'sessionId'] = null;
    }
    return json;
  }

  /// Returns a new [OfficeLaunchResponse] instance and imports its values from
  /// [value] if it's a [Map], null otherwise.
  // ignore: prefer_constructors_over_static_methods
  static OfficeLaunchResponse? fromJson(dynamic value) {
    if (value is Map) {
      final json = value.cast<String, dynamic>();

      // Ensure that the map contains the required keys.
      // Note 1: the values aren't checked for validity beyond being non-null.
      // Note 2: this code is stripped in release mode!
      assert(() {
        requiredKeys.forEach((key) {
          assert(json.containsKey(key),
              'Required key "OfficeLaunchResponse[$key]" is missing from JSON.');
          assert(json[key] != null,
              'Required key "OfficeLaunchResponse[$key]" has a null value in JSON.');
        });
        return true;
      }());

      return OfficeLaunchResponse(
        expiresAt: mapDateTime(json, r'expiresAt', r''),
        grantedPermissions: json[r'grantedPermissions'] is Iterable
            ? (json[r'grantedPermissions'] as Iterable)
                .cast<String>()
                .toList(growable: false)
            : const [],
        launchMode: mapValueOfType<String>(json, r'launchMode'),
        providerKey: mapValueOfType<String>(json, r'providerKey'),
        sessionId: mapValueOfType<String>(json, r'sessionId'),
      );
    }
    return null;
  }

  static List<OfficeLaunchResponse> listFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final result = <OfficeLaunchResponse>[];
    if (json is List && json.isNotEmpty) {
      for (final row in json) {
        final value = OfficeLaunchResponse.fromJson(row);
        if (value != null) {
          result.add(value);
        }
      }
    }
    return result.toList(growable: growable);
  }

  static Map<String, OfficeLaunchResponse> mapFromJson(dynamic json) {
    final map = <String, OfficeLaunchResponse>{};
    if (json is Map && json.isNotEmpty) {
      json = json.cast<String, dynamic>(); // ignore: parameter_assignments
      for (final entry in json.entries) {
        final value = OfficeLaunchResponse.fromJson(entry.value);
        if (value != null) {
          map[entry.key] = value;
        }
      }
    }
    return map;
  }

  // maps a json object with a list of OfficeLaunchResponse-objects as value to a dart map
  static Map<String, List<OfficeLaunchResponse>> mapListFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final map = <String, List<OfficeLaunchResponse>>{};
    if (json is Map && json.isNotEmpty) {
      // ignore: parameter_assignments
      json = json.cast<String, dynamic>();
      for (final entry in json.entries) {
        map[entry.key] = OfficeLaunchResponse.listFromJson(
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
