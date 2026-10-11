//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//
// @dart=2.18

// ignore_for_file: unused_element, unused_import
// ignore_for_file: always_put_required_named_parameters_first
// ignore_for_file: constant_identifier_names
// ignore_for_file: lines_longer_than_80_chars

part of weave_user_api;

class CalendarEventWriteRequest {
  /// Returns a new [CalendarEventWriteRequest] instance.
  CalendarEventWriteRequest({
    this.attendees = const [],
    this.description,
    required this.end,
    this.location,
    this.overrides = const [],
    this.recurrence,
    required this.start,
    required this.title,
  });

  List<CalendarEventAttendee> attendees;

  String? description;

  CalendarTimeValue end;

  String? location;

  List<CalendarEventOverride> overrides;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  CalendarEventRecurrence? recurrence;

  CalendarTimeValue start;

  String title;

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is CalendarEventWriteRequest &&
          _deepEquality.equals(other.attendees, attendees) &&
          other.description == description &&
          other.end == end &&
          other.location == location &&
          _deepEquality.equals(other.overrides, overrides) &&
          other.recurrence == recurrence &&
          other.start == start &&
          other.title == title;

  @override
  int get hashCode =>
      // ignore: unnecessary_parenthesis
      (attendees.hashCode) +
      (description == null ? 0 : description!.hashCode) +
      (end.hashCode) +
      (location == null ? 0 : location!.hashCode) +
      (overrides.hashCode) +
      (recurrence == null ? 0 : recurrence!.hashCode) +
      (start.hashCode) +
      (title.hashCode);

  @override
  String toString() =>
      'CalendarEventWriteRequest[attendees=$attendees, description=$description, end=$end, location=$location, overrides=$overrides, recurrence=$recurrence, start=$start, title=$title]';

  Map<String, dynamic> toJson() {
    final json = <String, dynamic>{};
    json[r'attendees'] = this.attendees;
    if (this.description != null) {
      json[r'description'] = this.description;
    } else {
      json[r'description'] = null;
    }
    json[r'end'] = this.end;
    if (this.location != null) {
      json[r'location'] = this.location;
    } else {
      json[r'location'] = null;
    }
    json[r'overrides'] = this.overrides;
    if (this.recurrence != null) {
      json[r'recurrence'] = this.recurrence;
    } else {
      json[r'recurrence'] = null;
    }
    json[r'start'] = this.start;
    json[r'title'] = this.title;
    return json;
  }

  /// Returns a new [CalendarEventWriteRequest] instance and imports its values from
  /// [value] if it's a [Map], null otherwise.
  // ignore: prefer_constructors_over_static_methods
  static CalendarEventWriteRequest? fromJson(dynamic value) {
    if (value is Map) {
      final json = value.cast<String, dynamic>();

      // Ensure that the map contains the required keys.
      // Note 1: the values aren't checked for validity beyond being non-null.
      // Note 2: this code is stripped in release mode!
      assert(() {
        requiredKeys.forEach((key) {
          assert(json.containsKey(key),
              'Required key "CalendarEventWriteRequest[$key]" is missing from JSON.');
          assert(json[key] != null,
              'Required key "CalendarEventWriteRequest[$key]" has a null value in JSON.');
        });
        return true;
      }());

      return CalendarEventWriteRequest(
        attendees: CalendarEventAttendee.listFromJson(json[r'attendees']),
        description: mapValueOfType<String>(json, r'description'),
        end: CalendarTimeValue.fromJson(json[r'end'])!,
        location: mapValueOfType<String>(json, r'location'),
        overrides: CalendarEventOverride.listFromJson(json[r'overrides']),
        recurrence: CalendarEventRecurrence.fromJson(json[r'recurrence']),
        start: CalendarTimeValue.fromJson(json[r'start'])!,
        title: mapValueOfType<String>(json, r'title')!,
      );
    }
    return null;
  }

  static List<CalendarEventWriteRequest> listFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final result = <CalendarEventWriteRequest>[];
    if (json is List && json.isNotEmpty) {
      for (final row in json) {
        final value = CalendarEventWriteRequest.fromJson(row);
        if (value != null) {
          result.add(value);
        }
      }
    }
    return result.toList(growable: growable);
  }

  static Map<String, CalendarEventWriteRequest> mapFromJson(dynamic json) {
    final map = <String, CalendarEventWriteRequest>{};
    if (json is Map && json.isNotEmpty) {
      json = json.cast<String, dynamic>(); // ignore: parameter_assignments
      for (final entry in json.entries) {
        final value = CalendarEventWriteRequest.fromJson(entry.value);
        if (value != null) {
          map[entry.key] = value;
        }
      }
    }
    return map;
  }

  // maps a json object with a list of CalendarEventWriteRequest-objects as value to a dart map
  static Map<String, List<CalendarEventWriteRequest>> mapListFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final map = <String, List<CalendarEventWriteRequest>>{};
    if (json is Map && json.isNotEmpty) {
      // ignore: parameter_assignments
      json = json.cast<String, dynamic>();
      for (final entry in json.entries) {
        map[entry.key] = CalendarEventWriteRequest.listFromJson(
          entry.value,
          growable: growable,
        );
      }
    }
    return map;
  }

  /// The list of required keys that must be present in a JSON.
  static const requiredKeys = <String>{
    'attendees',
    'end',
    'overrides',
    'start',
    'title',
  };
}
