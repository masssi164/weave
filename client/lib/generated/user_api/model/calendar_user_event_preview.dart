//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//
// @dart=2.18

// ignore_for_file: unused_element, unused_import
// ignore_for_file: always_put_required_named_parameters_first
// ignore_for_file: constant_identifier_names
// ignore_for_file: lines_longer_than_80_chars

part of weave_user_api;

class CalendarUserEventPreview {
  /// Returns a new [CalendarUserEventPreview] instance.
  CalendarUserEventPreview({
    this.allowedActions = const [],
    required this.calendarId,
    required this.content,
    required this.expiresAt,
    required this.handle,
    required this.scope,
  });

  List<String> allowedActions;

  String calendarId;

  CalendarEventWriteRequest content;

  DateTime expiresAt;

  String handle;

  CalendarUserScope scope;

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is CalendarUserEventPreview &&
          _deepEquality.equals(other.allowedActions, allowedActions) &&
          other.calendarId == calendarId &&
          other.content == content &&
          other.expiresAt == expiresAt &&
          other.handle == handle &&
          other.scope == scope;

  @override
  int get hashCode =>
      // ignore: unnecessary_parenthesis
      (allowedActions.hashCode) +
      (calendarId.hashCode) +
      (content.hashCode) +
      (expiresAt.hashCode) +
      (handle.hashCode) +
      (scope.hashCode);

  @override
  String toString() =>
      'CalendarUserEventPreview[allowedActions=$allowedActions, calendarId=$calendarId, content=$content, expiresAt=$expiresAt, handle=$handle, scope=$scope]';

  Map<String, dynamic> toJson() {
    final json = <String, dynamic>{};
    json[r'allowedActions'] = this.allowedActions;
    json[r'calendarId'] = this.calendarId;
    json[r'content'] = this.content;
    json[r'expiresAt'] = this.expiresAt.toUtc().toIso8601String();
    json[r'handle'] = this.handle;
    json[r'scope'] = this.scope;
    return json;
  }

  /// Returns a new [CalendarUserEventPreview] instance and imports its values from
  /// [value] if it's a [Map], null otherwise.
  // ignore: prefer_constructors_over_static_methods
  static CalendarUserEventPreview? fromJson(dynamic value) {
    if (value is Map) {
      final json = value.cast<String, dynamic>();

      // Ensure that the map contains the required keys.
      // Note 1: the values aren't checked for validity beyond being non-null.
      // Note 2: this code is stripped in release mode!
      assert(() {
        requiredKeys.forEach((key) {
          assert(json.containsKey(key),
              'Required key "CalendarUserEventPreview[$key]" is missing from JSON.');
          assert(json[key] != null,
              'Required key "CalendarUserEventPreview[$key]" has a null value in JSON.');
        });
        return true;
      }());

      return CalendarUserEventPreview(
        allowedActions: json[r'allowedActions'] is Iterable
            ? (json[r'allowedActions'] as Iterable)
                .cast<String>()
                .toList(growable: false)
            : const [],
        calendarId: mapValueOfType<String>(json, r'calendarId')!,
        content: CalendarEventWriteRequest.fromJson(json[r'content'])!,
        expiresAt: mapDateTime(json, r'expiresAt', r'')!,
        handle: mapValueOfType<String>(json, r'handle')!,
        scope: CalendarUserScope.fromJson(json[r'scope'])!,
      );
    }
    return null;
  }

  static List<CalendarUserEventPreview> listFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final result = <CalendarUserEventPreview>[];
    if (json is List && json.isNotEmpty) {
      for (final row in json) {
        final value = CalendarUserEventPreview.fromJson(row);
        if (value != null) {
          result.add(value);
        }
      }
    }
    return result.toList(growable: growable);
  }

  static Map<String, CalendarUserEventPreview> mapFromJson(dynamic json) {
    final map = <String, CalendarUserEventPreview>{};
    if (json is Map && json.isNotEmpty) {
      json = json.cast<String, dynamic>(); // ignore: parameter_assignments
      for (final entry in json.entries) {
        final value = CalendarUserEventPreview.fromJson(entry.value);
        if (value != null) {
          map[entry.key] = value;
        }
      }
    }
    return map;
  }

  // maps a json object with a list of CalendarUserEventPreview-objects as value to a dart map
  static Map<String, List<CalendarUserEventPreview>> mapListFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final map = <String, List<CalendarUserEventPreview>>{};
    if (json is Map && json.isNotEmpty) {
      // ignore: parameter_assignments
      json = json.cast<String, dynamic>();
      for (final entry in json.entries) {
        map[entry.key] = CalendarUserEventPreview.listFromJson(
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
    'expiresAt',
    'handle',
    'scope',
  };
}
