//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//
// @dart=2.18

// ignore_for_file: unused_element, unused_import
// ignore_for_file: always_put_required_named_parameters_first
// ignore_for_file: constant_identifier_names
// ignore_for_file: lines_longer_than_80_chars

part of weave_user_api;

class CalendarAccessPolicyResponse {
  /// Returns a new [CalendarAccessPolicyResponse] instance.
  CalendarAccessPolicyResponse({
    this.accessModel,
    this.allowedScopes = const [],
    this.backendActorMayReadPrivateUserCalendars,
    this.deniedScopes = const [],
    this.requiredBeforePrivateCalendars = const [],
  });

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  CalendarAccessModelResponse? accessModel;

  List<String> allowedScopes;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  bool? backendActorMayReadPrivateUserCalendars;

  List<String> deniedScopes;

  List<String> requiredBeforePrivateCalendars;

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is CalendarAccessPolicyResponse &&
          other.accessModel == accessModel &&
          _deepEquality.equals(other.allowedScopes, allowedScopes) &&
          other.backendActorMayReadPrivateUserCalendars ==
              backendActorMayReadPrivateUserCalendars &&
          _deepEquality.equals(other.deniedScopes, deniedScopes) &&
          _deepEquality.equals(other.requiredBeforePrivateCalendars,
              requiredBeforePrivateCalendars);

  @override
  int get hashCode =>
      // ignore: unnecessary_parenthesis
      (accessModel == null ? 0 : accessModel!.hashCode) +
      (allowedScopes.hashCode) +
      (backendActorMayReadPrivateUserCalendars == null
          ? 0
          : backendActorMayReadPrivateUserCalendars!.hashCode) +
      (deniedScopes.hashCode) +
      (requiredBeforePrivateCalendars.hashCode);

  @override
  String toString() =>
      'CalendarAccessPolicyResponse[accessModel=$accessModel, allowedScopes=$allowedScopes, backendActorMayReadPrivateUserCalendars=$backendActorMayReadPrivateUserCalendars, deniedScopes=$deniedScopes, requiredBeforePrivateCalendars=$requiredBeforePrivateCalendars]';

  Map<String, dynamic> toJson() {
    final json = <String, dynamic>{};
    if (this.accessModel != null) {
      json[r'accessModel'] = this.accessModel;
    } else {
      json[r'accessModel'] = null;
    }
    json[r'allowedScopes'] = this.allowedScopes;
    if (this.backendActorMayReadPrivateUserCalendars != null) {
      json[r'backendActorMayReadPrivateUserCalendars'] =
          this.backendActorMayReadPrivateUserCalendars;
    } else {
      json[r'backendActorMayReadPrivateUserCalendars'] = null;
    }
    json[r'deniedScopes'] = this.deniedScopes;
    json[r'requiredBeforePrivateCalendars'] =
        this.requiredBeforePrivateCalendars;
    return json;
  }

  /// Returns a new [CalendarAccessPolicyResponse] instance and imports its values from
  /// [value] if it's a [Map], null otherwise.
  // ignore: prefer_constructors_over_static_methods
  static CalendarAccessPolicyResponse? fromJson(dynamic value) {
    if (value is Map) {
      final json = value.cast<String, dynamic>();

      // Ensure that the map contains the required keys.
      // Note 1: the values aren't checked for validity beyond being non-null.
      // Note 2: this code is stripped in release mode!
      assert(() {
        requiredKeys.forEach((key) {
          assert(json.containsKey(key),
              'Required key "CalendarAccessPolicyResponse[$key]" is missing from JSON.');
          assert(json[key] != null,
              'Required key "CalendarAccessPolicyResponse[$key]" has a null value in JSON.');
        });
        return true;
      }());

      return CalendarAccessPolicyResponse(
        accessModel: CalendarAccessModelResponse.fromJson(json[r'accessModel']),
        allowedScopes: json[r'allowedScopes'] is Iterable
            ? (json[r'allowedScopes'] as Iterable)
                .cast<String>()
                .toList(growable: false)
            : const [],
        backendActorMayReadPrivateUserCalendars: mapValueOfType<bool>(
            json, r'backendActorMayReadPrivateUserCalendars'),
        deniedScopes: json[r'deniedScopes'] is Iterable
            ? (json[r'deniedScopes'] as Iterable)
                .cast<String>()
                .toList(growable: false)
            : const [],
        requiredBeforePrivateCalendars:
            json[r'requiredBeforePrivateCalendars'] is Iterable
                ? (json[r'requiredBeforePrivateCalendars'] as Iterable)
                    .cast<String>()
                    .toList(growable: false)
                : const [],
      );
    }
    return null;
  }

  static List<CalendarAccessPolicyResponse> listFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final result = <CalendarAccessPolicyResponse>[];
    if (json is List && json.isNotEmpty) {
      for (final row in json) {
        final value = CalendarAccessPolicyResponse.fromJson(row);
        if (value != null) {
          result.add(value);
        }
      }
    }
    return result.toList(growable: growable);
  }

  static Map<String, CalendarAccessPolicyResponse> mapFromJson(dynamic json) {
    final map = <String, CalendarAccessPolicyResponse>{};
    if (json is Map && json.isNotEmpty) {
      json = json.cast<String, dynamic>(); // ignore: parameter_assignments
      for (final entry in json.entries) {
        final value = CalendarAccessPolicyResponse.fromJson(entry.value);
        if (value != null) {
          map[entry.key] = value;
        }
      }
    }
    return map;
  }

  // maps a json object with a list of CalendarAccessPolicyResponse-objects as value to a dart map
  static Map<String, List<CalendarAccessPolicyResponse>> mapListFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final map = <String, List<CalendarAccessPolicyResponse>>{};
    if (json is Map && json.isNotEmpty) {
      // ignore: parameter_assignments
      json = json.cast<String, dynamic>();
      for (final entry in json.entries) {
        map[entry.key] = CalendarAccessPolicyResponse.listFromJson(
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
