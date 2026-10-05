//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//
// @dart=2.18

// ignore_for_file: unused_element, unused_import
// ignore_for_file: always_put_required_named_parameters_first
// ignore_for_file: constant_identifier_names
// ignore_for_file: lines_longer_than_80_chars

part of weave_user_api;

class CalendarSetupCredentialListResponse {
  /// Returns a new [CalendarSetupCredentialListResponse] instance.
  CalendarSetupCredentialListResponse({
    this.credentials = const [],
  });

  List<CalendarSetupCredentialResponse> credentials;

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is CalendarSetupCredentialListResponse &&
          _deepEquality.equals(other.credentials, credentials);

  @override
  int get hashCode =>
      // ignore: unnecessary_parenthesis
      (credentials.hashCode);

  @override
  String toString() =>
      'CalendarSetupCredentialListResponse[credentials=$credentials]';

  Map<String, dynamic> toJson() {
    final json = <String, dynamic>{};
    json[r'credentials'] = this.credentials;
    return json;
  }

  /// Returns a new [CalendarSetupCredentialListResponse] instance and imports its values from
  /// [value] if it's a [Map], null otherwise.
  // ignore: prefer_constructors_over_static_methods
  static CalendarSetupCredentialListResponse? fromJson(dynamic value) {
    if (value is Map) {
      final json = value.cast<String, dynamic>();

      // Ensure that the map contains the required keys.
      // Note 1: the values aren't checked for validity beyond being non-null.
      // Note 2: this code is stripped in release mode!
      assert(() {
        requiredKeys.forEach((key) {
          assert(json.containsKey(key),
              'Required key "CalendarSetupCredentialListResponse[$key]" is missing from JSON.');
          assert(json[key] != null,
              'Required key "CalendarSetupCredentialListResponse[$key]" has a null value in JSON.');
        });
        return true;
      }());

      return CalendarSetupCredentialListResponse(
        credentials:
            CalendarSetupCredentialResponse.listFromJson(json[r'credentials']),
      );
    }
    return null;
  }

  static List<CalendarSetupCredentialListResponse> listFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final result = <CalendarSetupCredentialListResponse>[];
    if (json is List && json.isNotEmpty) {
      for (final row in json) {
        final value = CalendarSetupCredentialListResponse.fromJson(row);
        if (value != null) {
          result.add(value);
        }
      }
    }
    return result.toList(growable: growable);
  }

  static Map<String, CalendarSetupCredentialListResponse> mapFromJson(
      dynamic json) {
    final map = <String, CalendarSetupCredentialListResponse>{};
    if (json is Map && json.isNotEmpty) {
      json = json.cast<String, dynamic>(); // ignore: parameter_assignments
      for (final entry in json.entries) {
        final value = CalendarSetupCredentialListResponse.fromJson(entry.value);
        if (value != null) {
          map[entry.key] = value;
        }
      }
    }
    return map;
  }

  // maps a json object with a list of CalendarSetupCredentialListResponse-objects as value to a dart map
  static Map<String, List<CalendarSetupCredentialListResponse>> mapListFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final map = <String, List<CalendarSetupCredentialListResponse>>{};
    if (json is Map && json.isNotEmpty) {
      // ignore: parameter_assignments
      json = json.cast<String, dynamic>();
      for (final entry in json.entries) {
        map[entry.key] = CalendarSetupCredentialListResponse.listFromJson(
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
