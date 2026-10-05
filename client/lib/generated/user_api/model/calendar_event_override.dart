//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//
// @dart=2.18

// ignore_for_file: unused_element, unused_import
// ignore_for_file: always_put_required_named_parameters_first
// ignore_for_file: constant_identifier_names
// ignore_for_file: lines_longer_than_80_chars

part of weave_user_api;

class CalendarEventOverride {
  /// Returns a new [CalendarEventOverride] instance.
  CalendarEventOverride({
    this.cancelled,
    this.description,
    this.end,
    this.location,
    required this.recurrenceId,
    this.start,
    this.title,
  });

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  bool? cancelled;

  String? description;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  CalendarTimeValue? end;

  String? location;

  CalendarTimeValue recurrenceId;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  CalendarTimeValue? start;

  String? title;

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is CalendarEventOverride &&
          other.cancelled == cancelled &&
          other.description == description &&
          other.end == end &&
          other.location == location &&
          other.recurrenceId == recurrenceId &&
          other.start == start &&
          other.title == title;

  @override
  int get hashCode =>
      // ignore: unnecessary_parenthesis
      (cancelled == null ? 0 : cancelled!.hashCode) +
      (description == null ? 0 : description!.hashCode) +
      (end == null ? 0 : end!.hashCode) +
      (location == null ? 0 : location!.hashCode) +
      (recurrenceId.hashCode) +
      (start == null ? 0 : start!.hashCode) +
      (title == null ? 0 : title!.hashCode);

  @override
  String toString() =>
      'CalendarEventOverride[cancelled=$cancelled, description=$description, end=$end, location=$location, recurrenceId=$recurrenceId, start=$start, title=$title]';

  Map<String, dynamic> toJson() {
    final json = <String, dynamic>{};
    if (this.cancelled != null) {
      json[r'cancelled'] = this.cancelled;
    } else {
      json[r'cancelled'] = null;
    }
    if (this.description != null) {
      json[r'description'] = this.description;
    } else {
      json[r'description'] = null;
    }
    if (this.end != null) {
      json[r'end'] = this.end;
    } else {
      json[r'end'] = null;
    }
    if (this.location != null) {
      json[r'location'] = this.location;
    } else {
      json[r'location'] = null;
    }
    json[r'recurrenceId'] = this.recurrenceId;
    if (this.start != null) {
      json[r'start'] = this.start;
    } else {
      json[r'start'] = null;
    }
    if (this.title != null) {
      json[r'title'] = this.title;
    } else {
      json[r'title'] = null;
    }
    return json;
  }

  /// Returns a new [CalendarEventOverride] instance and imports its values from
  /// [value] if it's a [Map], null otherwise.
  // ignore: prefer_constructors_over_static_methods
  static CalendarEventOverride? fromJson(dynamic value) {
    if (value is Map) {
      final json = value.cast<String, dynamic>();

      // Ensure that the map contains the required keys.
      // Note 1: the values aren't checked for validity beyond being non-null.
      // Note 2: this code is stripped in release mode!
      assert(() {
        requiredKeys.forEach((key) {
          assert(json.containsKey(key),
              'Required key "CalendarEventOverride[$key]" is missing from JSON.');
          assert(json[key] != null,
              'Required key "CalendarEventOverride[$key]" has a null value in JSON.');
        });
        return true;
      }());

      return CalendarEventOverride(
        cancelled: mapValueOfType<bool>(json, r'cancelled'),
        description: mapValueOfType<String>(json, r'description'),
        end: CalendarTimeValue.fromJson(json[r'end']),
        location: mapValueOfType<String>(json, r'location'),
        recurrenceId: CalendarTimeValue.fromJson(json[r'recurrenceId'])!,
        start: CalendarTimeValue.fromJson(json[r'start']),
        title: mapValueOfType<String>(json, r'title'),
      );
    }
    return null;
  }

  static List<CalendarEventOverride> listFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final result = <CalendarEventOverride>[];
    if (json is List && json.isNotEmpty) {
      for (final row in json) {
        final value = CalendarEventOverride.fromJson(row);
        if (value != null) {
          result.add(value);
        }
      }
    }
    return result.toList(growable: growable);
  }

  static Map<String, CalendarEventOverride> mapFromJson(dynamic json) {
    final map = <String, CalendarEventOverride>{};
    if (json is Map && json.isNotEmpty) {
      json = json.cast<String, dynamic>(); // ignore: parameter_assignments
      for (final entry in json.entries) {
        final value = CalendarEventOverride.fromJson(entry.value);
        if (value != null) {
          map[entry.key] = value;
        }
      }
    }
    return map;
  }

  // maps a json object with a list of CalendarEventOverride-objects as value to a dart map
  static Map<String, List<CalendarEventOverride>> mapListFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final map = <String, List<CalendarEventOverride>>{};
    if (json is Map && json.isNotEmpty) {
      // ignore: parameter_assignments
      json = json.cast<String, dynamic>();
      for (final entry in json.entries) {
        map[entry.key] = CalendarEventOverride.listFromJson(
          entry.value,
          growable: growable,
        );
      }
    }
    return map;
  }

  /// The list of required keys that must be present in a JSON.
  static const requiredKeys = <String>{
    'recurrenceId',
  };
}
