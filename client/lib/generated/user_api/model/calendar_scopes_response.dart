//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//
// @dart=2.18

// ignore_for_file: unused_element, unused_import
// ignore_for_file: always_put_required_named_parameters_first
// ignore_for_file: constant_identifier_names
// ignore_for_file: lines_longer_than_80_chars

part of weave_user_api;

class CalendarScopesResponse {
  /// Returns a new [CalendarScopesResponse] instance.
  CalendarScopesResponse({
    this.scopes = const [],
  });

  /// Visible workspace, team, and channel calendar scopes.
  List<CalendarScopeResponse> scopes;

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is CalendarScopesResponse &&
          _deepEquality.equals(other.scopes, scopes);

  @override
  int get hashCode =>
      // ignore: unnecessary_parenthesis
      (scopes.hashCode);

  @override
  String toString() => 'CalendarScopesResponse[scopes=$scopes]';

  Map<String, dynamic> toJson() {
    final json = <String, dynamic>{};
    json[r'scopes'] = this.scopes;
    return json;
  }

  /// Returns a new [CalendarScopesResponse] instance and imports its values from
  /// [value] if it's a [Map], null otherwise.
  // ignore: prefer_constructors_over_static_methods
  static CalendarScopesResponse? fromJson(dynamic value) {
    if (value is Map) {
      final json = value.cast<String, dynamic>();

      // Ensure that the map contains the required keys.
      // Note 1: the values aren't checked for validity beyond being non-null.
      // Note 2: this code is stripped in release mode!
      assert(() {
        requiredKeys.forEach((key) {
          assert(json.containsKey(key),
              'Required key "CalendarScopesResponse[$key]" is missing from JSON.');
          assert(json[key] != null,
              'Required key "CalendarScopesResponse[$key]" has a null value in JSON.');
        });
        return true;
      }());

      return CalendarScopesResponse(
        scopes: CalendarScopeResponse.listFromJson(json[r'scopes']),
      );
    }
    return null;
  }

  static List<CalendarScopesResponse> listFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final result = <CalendarScopesResponse>[];
    if (json is List && json.isNotEmpty) {
      for (final row in json) {
        final value = CalendarScopesResponse.fromJson(row);
        if (value != null) {
          result.add(value);
        }
      }
    }
    return result.toList(growable: growable);
  }

  static Map<String, CalendarScopesResponse> mapFromJson(dynamic json) {
    final map = <String, CalendarScopesResponse>{};
    if (json is Map && json.isNotEmpty) {
      json = json.cast<String, dynamic>(); // ignore: parameter_assignments
      for (final entry in json.entries) {
        final value = CalendarScopesResponse.fromJson(entry.value);
        if (value != null) {
          map[entry.key] = value;
        }
      }
    }
    return map;
  }

  // maps a json object with a list of CalendarScopesResponse-objects as value to a dart map
  static Map<String, List<CalendarScopesResponse>> mapListFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final map = <String, List<CalendarScopesResponse>>{};
    if (json is Map && json.isNotEmpty) {
      // ignore: parameter_assignments
      json = json.cast<String, dynamic>();
      for (final entry in json.entries) {
        map[entry.key] = CalendarScopesResponse.listFromJson(
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
