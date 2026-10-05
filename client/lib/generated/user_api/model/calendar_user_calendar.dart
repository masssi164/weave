//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//
// @dart=2.18

// ignore_for_file: unused_element, unused_import
// ignore_for_file: always_put_required_named_parameters_first
// ignore_for_file: constant_identifier_names
// ignore_for_file: lines_longer_than_80_chars

part of weave_user_api;

class CalendarUserCalendar {
  /// Returns a new [CalendarUserCalendar] instance.
  CalendarUserCalendar({
    this.allowedActions = const [],
    required this.id,
    required this.scope,
  });

  List<String> allowedActions;

  String id;

  CalendarUserScope scope;

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is CalendarUserCalendar &&
          _deepEquality.equals(other.allowedActions, allowedActions) &&
          other.id == id &&
          other.scope == scope;

  @override
  int get hashCode =>
      // ignore: unnecessary_parenthesis
      (allowedActions.hashCode) + (id.hashCode) + (scope.hashCode);

  @override
  String toString() =>
      'CalendarUserCalendar[allowedActions=$allowedActions, id=$id, scope=$scope]';

  Map<String, dynamic> toJson() {
    final json = <String, dynamic>{};
    json[r'allowedActions'] = this.allowedActions;
    json[r'id'] = this.id;
    json[r'scope'] = this.scope;
    return json;
  }

  /// Returns a new [CalendarUserCalendar] instance and imports its values from
  /// [value] if it's a [Map], null otherwise.
  // ignore: prefer_constructors_over_static_methods
  static CalendarUserCalendar? fromJson(dynamic value) {
    if (value is Map) {
      final json = value.cast<String, dynamic>();

      // Ensure that the map contains the required keys.
      // Note 1: the values aren't checked for validity beyond being non-null.
      // Note 2: this code is stripped in release mode!
      assert(() {
        requiredKeys.forEach((key) {
          assert(json.containsKey(key),
              'Required key "CalendarUserCalendar[$key]" is missing from JSON.');
          assert(json[key] != null,
              'Required key "CalendarUserCalendar[$key]" has a null value in JSON.');
        });
        return true;
      }());

      return CalendarUserCalendar(
        allowedActions: json[r'allowedActions'] is Iterable
            ? (json[r'allowedActions'] as Iterable)
                .cast<String>()
                .toList(growable: false)
            : const [],
        id: mapValueOfType<String>(json, r'id')!,
        scope: CalendarUserScope.fromJson(json[r'scope'])!,
      );
    }
    return null;
  }

  static List<CalendarUserCalendar> listFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final result = <CalendarUserCalendar>[];
    if (json is List && json.isNotEmpty) {
      for (final row in json) {
        final value = CalendarUserCalendar.fromJson(row);
        if (value != null) {
          result.add(value);
        }
      }
    }
    return result.toList(growable: growable);
  }

  static Map<String, CalendarUserCalendar> mapFromJson(dynamic json) {
    final map = <String, CalendarUserCalendar>{};
    if (json is Map && json.isNotEmpty) {
      json = json.cast<String, dynamic>(); // ignore: parameter_assignments
      for (final entry in json.entries) {
        final value = CalendarUserCalendar.fromJson(entry.value);
        if (value != null) {
          map[entry.key] = value;
        }
      }
    }
    return map;
  }

  // maps a json object with a list of CalendarUserCalendar-objects as value to a dart map
  static Map<String, List<CalendarUserCalendar>> mapListFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final map = <String, List<CalendarUserCalendar>>{};
    if (json is Map && json.isNotEmpty) {
      // ignore: parameter_assignments
      json = json.cast<String, dynamic>();
      for (final entry in json.entries) {
        map[entry.key] = CalendarUserCalendar.listFromJson(
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
    'id',
    'scope',
  };
}
