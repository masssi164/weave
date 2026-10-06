//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//
// @dart=2.18

// ignore_for_file: unused_element, unused_import
// ignore_for_file: always_put_required_named_parameters_first
// ignore_for_file: constant_identifier_names
// ignore_for_file: lines_longer_than_80_chars

part of weave_user_api;

class CalendarAccessModelResponse {
  /// Returns a new [CalendarAccessModelResponse] instance.
  CalendarAccessModelResponse({
    this.externalClientCredentialModel,
    this.notes = const [],
    this.privateUserCalendarsAvailable,
    this.privateUserCalendarsReason,
    this.productScope,
    this.type,
  });

  /// Credential model expected for external CalDAV clients.
  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? externalClientCredentialModel;

  /// Human-readable notes that keep the user/admin boundary honest.
  List<String> notes;

  /// Whether private per-user calendars are available through the product facade.
  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  bool? privateUserCalendarsAvailable;

  /// Support-safe reason private per-user calendars are unavailable or constrained.
  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? privateUserCalendarsReason;

  /// Product calendar scope served by the Weave CalDAV facade.
  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? productScope;

  /// Stable access model identifier.
  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? type;

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is CalendarAccessModelResponse &&
          other.externalClientCredentialModel ==
              externalClientCredentialModel &&
          _deepEquality.equals(other.notes, notes) &&
          other.privateUserCalendarsAvailable ==
              privateUserCalendarsAvailable &&
          other.privateUserCalendarsReason == privateUserCalendarsReason &&
          other.productScope == productScope &&
          other.type == type;

  @override
  int get hashCode =>
      // ignore: unnecessary_parenthesis
      (externalClientCredentialModel == null
          ? 0
          : externalClientCredentialModel!.hashCode) +
      (notes.hashCode) +
      (privateUserCalendarsAvailable == null
          ? 0
          : privateUserCalendarsAvailable!.hashCode) +
      (privateUserCalendarsReason == null
          ? 0
          : privateUserCalendarsReason!.hashCode) +
      (productScope == null ? 0 : productScope!.hashCode) +
      (type == null ? 0 : type!.hashCode);

  @override
  String toString() =>
      'CalendarAccessModelResponse[externalClientCredentialModel=$externalClientCredentialModel, notes=$notes, privateUserCalendarsAvailable=$privateUserCalendarsAvailable, privateUserCalendarsReason=$privateUserCalendarsReason, productScope=$productScope, type=$type]';

  Map<String, dynamic> toJson() {
    final json = <String, dynamic>{};
    if (this.externalClientCredentialModel != null) {
      json[r'externalClientCredentialModel'] =
          this.externalClientCredentialModel;
    } else {
      json[r'externalClientCredentialModel'] = null;
    }
    json[r'notes'] = this.notes;
    if (this.privateUserCalendarsAvailable != null) {
      json[r'privateUserCalendarsAvailable'] =
          this.privateUserCalendarsAvailable;
    } else {
      json[r'privateUserCalendarsAvailable'] = null;
    }
    if (this.privateUserCalendarsReason != null) {
      json[r'privateUserCalendarsReason'] = this.privateUserCalendarsReason;
    } else {
      json[r'privateUserCalendarsReason'] = null;
    }
    if (this.productScope != null) {
      json[r'productScope'] = this.productScope;
    } else {
      json[r'productScope'] = null;
    }
    if (this.type != null) {
      json[r'type'] = this.type;
    } else {
      json[r'type'] = null;
    }
    return json;
  }

  /// Returns a new [CalendarAccessModelResponse] instance and imports its values from
  /// [value] if it's a [Map], null otherwise.
  // ignore: prefer_constructors_over_static_methods
  static CalendarAccessModelResponse? fromJson(dynamic value) {
    if (value is Map) {
      final json = value.cast<String, dynamic>();

      // Ensure that the map contains the required keys.
      // Note 1: the values aren't checked for validity beyond being non-null.
      // Note 2: this code is stripped in release mode!
      assert(() {
        requiredKeys.forEach((key) {
          assert(json.containsKey(key),
              'Required key "CalendarAccessModelResponse[$key]" is missing from JSON.');
          assert(json[key] != null,
              'Required key "CalendarAccessModelResponse[$key]" has a null value in JSON.');
        });
        return true;
      }());

      return CalendarAccessModelResponse(
        externalClientCredentialModel:
            mapValueOfType<String>(json, r'externalClientCredentialModel'),
        notes: json[r'notes'] is Iterable
            ? (json[r'notes'] as Iterable)
                .cast<String>()
                .toList(growable: false)
            : const [],
        privateUserCalendarsAvailable:
            mapValueOfType<bool>(json, r'privateUserCalendarsAvailable'),
        privateUserCalendarsReason:
            mapValueOfType<String>(json, r'privateUserCalendarsReason'),
        productScope: mapValueOfType<String>(json, r'productScope'),
        type: mapValueOfType<String>(json, r'type'),
      );
    }
    return null;
  }

  static List<CalendarAccessModelResponse> listFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final result = <CalendarAccessModelResponse>[];
    if (json is List && json.isNotEmpty) {
      for (final row in json) {
        final value = CalendarAccessModelResponse.fromJson(row);
        if (value != null) {
          result.add(value);
        }
      }
    }
    return result.toList(growable: growable);
  }

  static Map<String, CalendarAccessModelResponse> mapFromJson(dynamic json) {
    final map = <String, CalendarAccessModelResponse>{};
    if (json is Map && json.isNotEmpty) {
      json = json.cast<String, dynamic>(); // ignore: parameter_assignments
      for (final entry in json.entries) {
        final value = CalendarAccessModelResponse.fromJson(entry.value);
        if (value != null) {
          map[entry.key] = value;
        }
      }
    }
    return map;
  }

  // maps a json object with a list of CalendarAccessModelResponse-objects as value to a dart map
  static Map<String, List<CalendarAccessModelResponse>> mapListFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final map = <String, List<CalendarAccessModelResponse>>{};
    if (json is Map && json.isNotEmpty) {
      // ignore: parameter_assignments
      json = json.cast<String, dynamic>();
      for (final entry in json.entries) {
        map[entry.key] = CalendarAccessModelResponse.listFromJson(
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
