//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//
// @dart=2.18

// ignore_for_file: unused_element, unused_import
// ignore_for_file: always_put_required_named_parameters_first
// ignore_for_file: constant_identifier_names
// ignore_for_file: lines_longer_than_80_chars

part of weave_user_api;

class CalendarUserOccurrence {
  /// Returns a new [CalendarUserOccurrence] instance.
  CalendarUserOccurrence({
    required this.endsAt,
    required this.eventId,
    required this.startsAt,
  });

  DateTime endsAt;

  String eventId;

  DateTime startsAt;

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is CalendarUserOccurrence &&
          other.endsAt == endsAt &&
          other.eventId == eventId &&
          other.startsAt == startsAt;

  @override
  int get hashCode =>
      // ignore: unnecessary_parenthesis
      (endsAt.hashCode) + (eventId.hashCode) + (startsAt.hashCode);

  @override
  String toString() =>
      'CalendarUserOccurrence[endsAt=$endsAt, eventId=$eventId, startsAt=$startsAt]';

  Map<String, dynamic> toJson() {
    final json = <String, dynamic>{};
    json[r'endsAt'] = this.endsAt.toUtc().toIso8601String();
    json[r'eventId'] = this.eventId;
    json[r'startsAt'] = this.startsAt.toUtc().toIso8601String();
    return json;
  }

  /// Returns a new [CalendarUserOccurrence] instance and imports its values from
  /// [value] if it's a [Map], null otherwise.
  // ignore: prefer_constructors_over_static_methods
  static CalendarUserOccurrence? fromJson(dynamic value) {
    if (value is Map) {
      final json = value.cast<String, dynamic>();

      // Ensure that the map contains the required keys.
      // Note 1: the values aren't checked for validity beyond being non-null.
      // Note 2: this code is stripped in release mode!
      assert(() {
        requiredKeys.forEach((key) {
          assert(json.containsKey(key),
              'Required key "CalendarUserOccurrence[$key]" is missing from JSON.');
          assert(json[key] != null,
              'Required key "CalendarUserOccurrence[$key]" has a null value in JSON.');
        });
        return true;
      }());

      return CalendarUserOccurrence(
        endsAt: mapDateTime(json, r'endsAt', r'')!,
        eventId: mapValueOfType<String>(json, r'eventId')!,
        startsAt: mapDateTime(json, r'startsAt', r'')!,
      );
    }
    return null;
  }

  static List<CalendarUserOccurrence> listFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final result = <CalendarUserOccurrence>[];
    if (json is List && json.isNotEmpty) {
      for (final row in json) {
        final value = CalendarUserOccurrence.fromJson(row);
        if (value != null) {
          result.add(value);
        }
      }
    }
    return result.toList(growable: growable);
  }

  static Map<String, CalendarUserOccurrence> mapFromJson(dynamic json) {
    final map = <String, CalendarUserOccurrence>{};
    if (json is Map && json.isNotEmpty) {
      json = json.cast<String, dynamic>(); // ignore: parameter_assignments
      for (final entry in json.entries) {
        final value = CalendarUserOccurrence.fromJson(entry.value);
        if (value != null) {
          map[entry.key] = value;
        }
      }
    }
    return map;
  }

  // maps a json object with a list of CalendarUserOccurrence-objects as value to a dart map
  static Map<String, List<CalendarUserOccurrence>> mapListFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final map = <String, List<CalendarUserOccurrence>>{};
    if (json is Map && json.isNotEmpty) {
      // ignore: parameter_assignments
      json = json.cast<String, dynamic>();
      for (final entry in json.entries) {
        map[entry.key] = CalendarUserOccurrence.listFromJson(
          entry.value,
          growable: growable,
        );
      }
    }
    return map;
  }

  /// The list of required keys that must be present in a JSON.
  static const requiredKeys = <String>{
    'endsAt',
    'eventId',
    'startsAt',
  };
}
