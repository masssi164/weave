//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//
// @dart=2.18

// ignore_for_file: unused_element, unused_import
// ignore_for_file: always_put_required_named_parameters_first
// ignore_for_file: constant_identifier_names
// ignore_for_file: lines_longer_than_80_chars

part of weave_user_api;

class CalendarUserAgenda {
  /// Returns a new [CalendarUserAgenda] instance.
  CalendarUserAgenda({
    required this.calendarId,
    required this.evaluationTimeZone,
    this.events = const [],
    required this.from,
    this.occurrences = const [],
    this.previewOccurrences = const [],
    this.previews = const [],
    required this.to,
  });

  String calendarId;

  String evaluationTimeZone;

  List<CalendarUserEvent> events;

  DateTime from;

  List<CalendarUserOccurrence> occurrences;

  List<CalendarUserPreviewOccurrence> previewOccurrences;

  List<CalendarUserEventPreview> previews;

  DateTime to;

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is CalendarUserAgenda &&
          other.calendarId == calendarId &&
          other.evaluationTimeZone == evaluationTimeZone &&
          _deepEquality.equals(other.events, events) &&
          other.from == from &&
          _deepEquality.equals(other.occurrences, occurrences) &&
          _deepEquality.equals(other.previewOccurrences, previewOccurrences) &&
          _deepEquality.equals(other.previews, previews) &&
          other.to == to;

  @override
  int get hashCode =>
      // ignore: unnecessary_parenthesis
      (calendarId.hashCode) +
      (evaluationTimeZone.hashCode) +
      (events.hashCode) +
      (from.hashCode) +
      (occurrences.hashCode) +
      (previewOccurrences.hashCode) +
      (previews.hashCode) +
      (to.hashCode);

  @override
  String toString() =>
      'CalendarUserAgenda[calendarId=$calendarId, evaluationTimeZone=$evaluationTimeZone, events=$events, from=$from, occurrences=$occurrences, previewOccurrences=$previewOccurrences, previews=$previews, to=$to]';

  Map<String, dynamic> toJson() {
    final json = <String, dynamic>{};
    json[r'calendarId'] = this.calendarId;
    json[r'evaluationTimeZone'] = this.evaluationTimeZone;
    json[r'events'] = this.events;
    json[r'from'] = this.from.toUtc().toIso8601String();
    json[r'occurrences'] = this.occurrences;
    json[r'previewOccurrences'] = this.previewOccurrences;
    json[r'previews'] = this.previews;
    json[r'to'] = this.to.toUtc().toIso8601String();
    return json;
  }

  /// Returns a new [CalendarUserAgenda] instance and imports its values from
  /// [value] if it's a [Map], null otherwise.
  // ignore: prefer_constructors_over_static_methods
  static CalendarUserAgenda? fromJson(dynamic value) {
    if (value is Map) {
      final json = value.cast<String, dynamic>();

      // Ensure that the map contains the required keys.
      // Note 1: the values aren't checked for validity beyond being non-null.
      // Note 2: this code is stripped in release mode!
      assert(() {
        requiredKeys.forEach((key) {
          assert(json.containsKey(key),
              'Required key "CalendarUserAgenda[$key]" is missing from JSON.');
          assert(json[key] != null,
              'Required key "CalendarUserAgenda[$key]" has a null value in JSON.');
        });
        return true;
      }());

      return CalendarUserAgenda(
        calendarId: mapValueOfType<String>(json, r'calendarId')!,
        evaluationTimeZone:
            mapValueOfType<String>(json, r'evaluationTimeZone')!,
        events: CalendarUserEvent.listFromJson(json[r'events']),
        from: mapDateTime(json, r'from', r'')!,
        occurrences: CalendarUserOccurrence.listFromJson(json[r'occurrences']),
        previewOccurrences: CalendarUserPreviewOccurrence.listFromJson(
            json[r'previewOccurrences']),
        previews: CalendarUserEventPreview.listFromJson(json[r'previews']),
        to: mapDateTime(json, r'to', r'')!,
      );
    }
    return null;
  }

  static List<CalendarUserAgenda> listFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final result = <CalendarUserAgenda>[];
    if (json is List && json.isNotEmpty) {
      for (final row in json) {
        final value = CalendarUserAgenda.fromJson(row);
        if (value != null) {
          result.add(value);
        }
      }
    }
    return result.toList(growable: growable);
  }

  static Map<String, CalendarUserAgenda> mapFromJson(dynamic json) {
    final map = <String, CalendarUserAgenda>{};
    if (json is Map && json.isNotEmpty) {
      json = json.cast<String, dynamic>(); // ignore: parameter_assignments
      for (final entry in json.entries) {
        final value = CalendarUserAgenda.fromJson(entry.value);
        if (value != null) {
          map[entry.key] = value;
        }
      }
    }
    return map;
  }

  // maps a json object with a list of CalendarUserAgenda-objects as value to a dart map
  static Map<String, List<CalendarUserAgenda>> mapListFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final map = <String, List<CalendarUserAgenda>>{};
    if (json is Map && json.isNotEmpty) {
      // ignore: parameter_assignments
      json = json.cast<String, dynamic>();
      for (final entry in json.entries) {
        map[entry.key] = CalendarUserAgenda.listFromJson(
          entry.value,
          growable: growable,
        );
      }
    }
    return map;
  }

  /// The list of required keys that must be present in a JSON.
  static const requiredKeys = <String>{
    'calendarId',
    'evaluationTimeZone',
    'events',
    'from',
    'occurrences',
    'previewOccurrences',
    'previews',
    'to',
  };
}
