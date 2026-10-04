//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//
// @dart=2.18

// ignore_for_file: unused_element, unused_import
// ignore_for_file: always_put_required_named_parameters_first
// ignore_for_file: constant_identifier_names
// ignore_for_file: lines_longer_than_80_chars

part of weave_user_api;

class CalendarClientSetupOptionResponse {
  /// Returns a new [CalendarClientSetupOptionResponse] instance.
  CalendarClientSetupOptionResponse({
    this.actionUrl,
    this.available,
    this.method,
    this.notes = const [],
    this.platform,
    this.unavailableReason,
  });

  /// Optional action URL for supported external clients. Contains no credential.
  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? actionUrl;

  /// Whether this setup method is currently safe for feature-gated use.
  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  bool? available;

  /// Setup mechanism.
  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? method;

  /// Human-readable setup notes for the authenticated user.
  List<String> notes;

  /// Stable platform identifier.
  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? platform;

  /// Support-safe reason when the option is not available yet.
  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? unavailableReason;

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is CalendarClientSetupOptionResponse &&
          other.actionUrl == actionUrl &&
          other.available == available &&
          other.method == method &&
          _deepEquality.equals(other.notes, notes) &&
          other.platform == platform &&
          other.unavailableReason == unavailableReason;

  @override
  int get hashCode =>
      // ignore: unnecessary_parenthesis
      (actionUrl == null ? 0 : actionUrl!.hashCode) +
      (available == null ? 0 : available!.hashCode) +
      (method == null ? 0 : method!.hashCode) +
      (notes.hashCode) +
      (platform == null ? 0 : platform!.hashCode) +
      (unavailableReason == null ? 0 : unavailableReason!.hashCode);

  @override
  String toString() =>
      'CalendarClientSetupOptionResponse[actionUrl=$actionUrl, available=$available, method=$method, notes=$notes, platform=$platform, unavailableReason=$unavailableReason]';

  Map<String, dynamic> toJson() {
    final json = <String, dynamic>{};
    if (this.actionUrl != null) {
      json[r'actionUrl'] = this.actionUrl;
    } else {
      json[r'actionUrl'] = null;
    }
    if (this.available != null) {
      json[r'available'] = this.available;
    } else {
      json[r'available'] = null;
    }
    if (this.method != null) {
      json[r'method'] = this.method;
    } else {
      json[r'method'] = null;
    }
    json[r'notes'] = this.notes;
    if (this.platform != null) {
      json[r'platform'] = this.platform;
    } else {
      json[r'platform'] = null;
    }
    if (this.unavailableReason != null) {
      json[r'unavailableReason'] = this.unavailableReason;
    } else {
      json[r'unavailableReason'] = null;
    }
    return json;
  }

  /// Returns a new [CalendarClientSetupOptionResponse] instance and imports its values from
  /// [value] if it's a [Map], null otherwise.
  // ignore: prefer_constructors_over_static_methods
  static CalendarClientSetupOptionResponse? fromJson(dynamic value) {
    if (value is Map) {
      final json = value.cast<String, dynamic>();

      // Ensure that the map contains the required keys.
      // Note 1: the values aren't checked for validity beyond being non-null.
      // Note 2: this code is stripped in release mode!
      assert(() {
        requiredKeys.forEach((key) {
          assert(json.containsKey(key),
              'Required key "CalendarClientSetupOptionResponse[$key]" is missing from JSON.');
          assert(json[key] != null,
              'Required key "CalendarClientSetupOptionResponse[$key]" has a null value in JSON.');
        });
        return true;
      }());

      return CalendarClientSetupOptionResponse(
        actionUrl: mapValueOfType<String>(json, r'actionUrl'),
        available: mapValueOfType<bool>(json, r'available'),
        method: mapValueOfType<String>(json, r'method'),
        notes: json[r'notes'] is Iterable
            ? (json[r'notes'] as Iterable)
                .cast<String>()
                .toList(growable: false)
            : const [],
        platform: mapValueOfType<String>(json, r'platform'),
        unavailableReason: mapValueOfType<String>(json, r'unavailableReason'),
      );
    }
    return null;
  }

  static List<CalendarClientSetupOptionResponse> listFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final result = <CalendarClientSetupOptionResponse>[];
    if (json is List && json.isNotEmpty) {
      for (final row in json) {
        final value = CalendarClientSetupOptionResponse.fromJson(row);
        if (value != null) {
          result.add(value);
        }
      }
    }
    return result.toList(growable: growable);
  }

  static Map<String, CalendarClientSetupOptionResponse> mapFromJson(
      dynamic json) {
    final map = <String, CalendarClientSetupOptionResponse>{};
    if (json is Map && json.isNotEmpty) {
      json = json.cast<String, dynamic>(); // ignore: parameter_assignments
      for (final entry in json.entries) {
        final value = CalendarClientSetupOptionResponse.fromJson(entry.value);
        if (value != null) {
          map[entry.key] = value;
        }
      }
    }
    return map;
  }

  // maps a json object with a list of CalendarClientSetupOptionResponse-objects as value to a dart map
  static Map<String, List<CalendarClientSetupOptionResponse>> mapListFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final map = <String, List<CalendarClientSetupOptionResponse>>{};
    if (json is Map && json.isNotEmpty) {
      // ignore: parameter_assignments
      json = json.cast<String, dynamic>();
      for (final entry in json.entries) {
        map[entry.key] = CalendarClientSetupOptionResponse.listFromJson(
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
