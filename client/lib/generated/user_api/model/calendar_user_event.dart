//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//
// @dart=2.18

// ignore_for_file: unused_element, unused_import
// ignore_for_file: always_put_required_named_parameters_first
// ignore_for_file: constant_identifier_names
// ignore_for_file: lines_longer_than_80_chars

part of weave_user_api;

class CalendarUserEvent {
  /// Returns a new [CalendarUserEvent] instance.
  CalendarUserEvent({
    this.allowedActions = const [],
    required this.calendarId,
    required this.content,
    required this.id,
    required this.meetingThreadRef,
    required this.scope,
    required this.version,
  });

  List<String> allowedActions;

  String calendarId;

  CalendarEventWriteRequest content;

  String id;

  String meetingThreadRef;

  CalendarUserScope scope;

  String version;

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is CalendarUserEvent &&
          _deepEquality.equals(other.allowedActions, allowedActions) &&
          other.calendarId == calendarId &&
          other.content == content &&
          other.id == id &&
          other.meetingThreadRef == meetingThreadRef &&
          other.scope == scope &&
          other.version == version;

  @override
  int get hashCode =>
      // ignore: unnecessary_parenthesis
      (allowedActions.hashCode) +
      (calendarId.hashCode) +
      (content.hashCode) +
      (id.hashCode) +
      (meetingThreadRef.hashCode) +
      (scope.hashCode) +
      (version.hashCode);

  @override
  String toString() =>
      'CalendarUserEvent[allowedActions=$allowedActions, calendarId=$calendarId, content=$content, id=$id, meetingThreadRef=$meetingThreadRef, scope=$scope, version=$version]';

  Map<String, dynamic> toJson() {
    final json = <String, dynamic>{};
    json[r'allowedActions'] = this.allowedActions;
    json[r'calendarId'] = this.calendarId;
    json[r'content'] = this.content;
    json[r'id'] = this.id;
    json[r'meetingThreadRef'] = this.meetingThreadRef;
    json[r'scope'] = this.scope;
    json[r'version'] = this.version;
    return json;
  }

  /// Returns a new [CalendarUserEvent] instance and imports its values from
  /// [value] if it's a [Map], null otherwise.
  // ignore: prefer_constructors_over_static_methods
  static CalendarUserEvent? fromJson(dynamic value) {
    if (value is Map) {
      final json = value.cast<String, dynamic>();

      // Ensure that the map contains the required keys.
      // Note 1: the values aren't checked for validity beyond being non-null.
      // Note 2: this code is stripped in release mode!
      assert(() {
        requiredKeys.forEach((key) {
          assert(json.containsKey(key),
              'Required key "CalendarUserEvent[$key]" is missing from JSON.');
          assert(json[key] != null,
              'Required key "CalendarUserEvent[$key]" has a null value in JSON.');
        });
        return true;
      }());

      return CalendarUserEvent(
        allowedActions: json[r'allowedActions'] is Iterable
            ? (json[r'allowedActions'] as Iterable)
                .cast<String>()
                .toList(growable: false)
            : const [],
        calendarId: mapValueOfType<String>(json, r'calendarId')!,
        content: CalendarEventWriteRequest.fromJson(json[r'content'])!,
        id: mapValueOfType<String>(json, r'id')!,
        meetingThreadRef: mapValueOfType<String>(json, r'meetingThreadRef')!,
        scope: CalendarUserScope.fromJson(json[r'scope'])!,
        version: mapValueOfType<String>(json, r'version')!,
      );
    }
    return null;
  }

  static List<CalendarUserEvent> listFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final result = <CalendarUserEvent>[];
    if (json is List && json.isNotEmpty) {
      for (final row in json) {
        final value = CalendarUserEvent.fromJson(row);
        if (value != null) {
          result.add(value);
        }
      }
    }
    return result.toList(growable: growable);
  }

  static Map<String, CalendarUserEvent> mapFromJson(dynamic json) {
    final map = <String, CalendarUserEvent>{};
    if (json is Map && json.isNotEmpty) {
      json = json.cast<String, dynamic>(); // ignore: parameter_assignments
      for (final entry in json.entries) {
        final value = CalendarUserEvent.fromJson(entry.value);
        if (value != null) {
          map[entry.key] = value;
        }
      }
    }
    return map;
  }

  // maps a json object with a list of CalendarUserEvent-objects as value to a dart map
  static Map<String, List<CalendarUserEvent>> mapListFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final map = <String, List<CalendarUserEvent>>{};
    if (json is Map && json.isNotEmpty) {
      // ignore: parameter_assignments
      json = json.cast<String, dynamic>();
      for (final entry in json.entries) {
        map[entry.key] = CalendarUserEvent.listFromJson(
          entry.value,
          growable: growable,
        );
      }
    }
    return map;
  }

  /// The list of required keys that must be present in a JSON.
  static const requiredKeys = <String>{
    'allowedActions',
    'calendarId',
    'content',
    'id',
    'meetingThreadRef',
    'scope',
    'version',
  };
}
