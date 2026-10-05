//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//
// @dart=2.18

// ignore_for_file: unused_element, unused_import
// ignore_for_file: always_put_required_named_parameters_first
// ignore_for_file: constant_identifier_names
// ignore_for_file: lines_longer_than_80_chars

part of weave_user_api;

class CalendarEventAttendee {
  /// Returns a new [CalendarEventAttendee] instance.
  CalendarEventAttendee({
    required this.address,
    this.displayName,
    this.response,
    this.role,
  });

  String address;

  String? displayName;

  String? response;

  String? role;

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is CalendarEventAttendee &&
          other.address == address &&
          other.displayName == displayName &&
          other.response == response &&
          other.role == role;

  @override
  int get hashCode =>
      // ignore: unnecessary_parenthesis
      (address.hashCode) +
      (displayName == null ? 0 : displayName!.hashCode) +
      (response == null ? 0 : response!.hashCode) +
      (role == null ? 0 : role!.hashCode);

  @override
  String toString() =>
      'CalendarEventAttendee[address=$address, displayName=$displayName, response=$response, role=$role]';

  Map<String, dynamic> toJson() {
    final json = <String, dynamic>{};
    json[r'address'] = this.address;
    if (this.displayName != null) {
      json[r'displayName'] = this.displayName;
    } else {
      json[r'displayName'] = null;
    }
    if (this.response != null) {
      json[r'response'] = this.response;
    } else {
      json[r'response'] = null;
    }
    if (this.role != null) {
      json[r'role'] = this.role;
    } else {
      json[r'role'] = null;
    }
    return json;
  }

  /// Returns a new [CalendarEventAttendee] instance and imports its values from
  /// [value] if it's a [Map], null otherwise.
  // ignore: prefer_constructors_over_static_methods
  static CalendarEventAttendee? fromJson(dynamic value) {
    if (value is Map) {
      final json = value.cast<String, dynamic>();

      // Ensure that the map contains the required keys.
      // Note 1: the values aren't checked for validity beyond being non-null.
      // Note 2: this code is stripped in release mode!
      assert(() {
        requiredKeys.forEach((key) {
          assert(json.containsKey(key),
              'Required key "CalendarEventAttendee[$key]" is missing from JSON.');
          assert(json[key] != null,
              'Required key "CalendarEventAttendee[$key]" has a null value in JSON.');
        });
        return true;
      }());

      return CalendarEventAttendee(
        address: mapValueOfType<String>(json, r'address')!,
        displayName: mapValueOfType<String>(json, r'displayName'),
        response: mapValueOfType<String>(json, r'response'),
        role: mapValueOfType<String>(json, r'role'),
      );
    }
    return null;
  }

  static List<CalendarEventAttendee> listFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final result = <CalendarEventAttendee>[];
    if (json is List && json.isNotEmpty) {
      for (final row in json) {
        final value = CalendarEventAttendee.fromJson(row);
        if (value != null) {
          result.add(value);
        }
      }
    }
    return result.toList(growable: growable);
  }

  static Map<String, CalendarEventAttendee> mapFromJson(dynamic json) {
    final map = <String, CalendarEventAttendee>{};
    if (json is Map && json.isNotEmpty) {
      json = json.cast<String, dynamic>(); // ignore: parameter_assignments
      for (final entry in json.entries) {
        final value = CalendarEventAttendee.fromJson(entry.value);
        if (value != null) {
          map[entry.key] = value;
        }
      }
    }
    return map;
  }

  // maps a json object with a list of CalendarEventAttendee-objects as value to a dart map
  static Map<String, List<CalendarEventAttendee>> mapListFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final map = <String, List<CalendarEventAttendee>>{};
    if (json is Map && json.isNotEmpty) {
      // ignore: parameter_assignments
      json = json.cast<String, dynamic>();
      for (final entry in json.entries) {
        map[entry.key] = CalendarEventAttendee.listFromJson(
          entry.value,
          growable: growable,
        );
      }
    }
    return map;
  }

  /// The list of required keys that must be present in a JSON.
  static const requiredKeys = <String>{
    'address',
  };
}
