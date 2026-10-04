//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//
// @dart=2.18

// ignore_for_file: unused_element, unused_import
// ignore_for_file: always_put_required_named_parameters_first
// ignore_for_file: constant_identifier_names
// ignore_for_file: lines_longer_than_80_chars

part of weave_user_api;

class HealthResponse {
  /// Returns a new [HealthResponse] instance.
  HealthResponse({
    this.actions = const [],
    this.checks = const [],
    this.requestId,
    this.status,
  });

  List<String> actions;

  List<DiagnosticCheck> checks;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? requestId;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? status;

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is HealthResponse &&
          _deepEquality.equals(other.actions, actions) &&
          _deepEquality.equals(other.checks, checks) &&
          other.requestId == requestId &&
          other.status == status;

  @override
  int get hashCode =>
      // ignore: unnecessary_parenthesis
      (actions.hashCode) +
      (checks.hashCode) +
      (requestId == null ? 0 : requestId!.hashCode) +
      (status == null ? 0 : status!.hashCode);

  @override
  String toString() =>
      'HealthResponse[actions=$actions, checks=$checks, requestId=$requestId, status=$status]';

  Map<String, dynamic> toJson() {
    final json = <String, dynamic>{};
    json[r'actions'] = this.actions;
    json[r'checks'] = this.checks;
    if (this.requestId != null) {
      json[r'requestId'] = this.requestId;
    } else {
      json[r'requestId'] = null;
    }
    if (this.status != null) {
      json[r'status'] = this.status;
    } else {
      json[r'status'] = null;
    }
    return json;
  }

  /// Returns a new [HealthResponse] instance and imports its values from
  /// [value] if it's a [Map], null otherwise.
  // ignore: prefer_constructors_over_static_methods
  static HealthResponse? fromJson(dynamic value) {
    if (value is Map) {
      final json = value.cast<String, dynamic>();

      // Ensure that the map contains the required keys.
      // Note 1: the values aren't checked for validity beyond being non-null.
      // Note 2: this code is stripped in release mode!
      assert(() {
        requiredKeys.forEach((key) {
          assert(json.containsKey(key),
              'Required key "HealthResponse[$key]" is missing from JSON.');
          assert(json[key] != null,
              'Required key "HealthResponse[$key]" has a null value in JSON.');
        });
        return true;
      }());

      return HealthResponse(
        actions: json[r'actions'] is Iterable
            ? (json[r'actions'] as Iterable)
                .cast<String>()
                .toList(growable: false)
            : const [],
        checks: DiagnosticCheck.listFromJson(json[r'checks']),
        requestId: mapValueOfType<String>(json, r'requestId'),
        status: mapValueOfType<String>(json, r'status'),
      );
    }
    return null;
  }

  static List<HealthResponse> listFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final result = <HealthResponse>[];
    if (json is List && json.isNotEmpty) {
      for (final row in json) {
        final value = HealthResponse.fromJson(row);
        if (value != null) {
          result.add(value);
        }
      }
    }
    return result.toList(growable: growable);
  }

  static Map<String, HealthResponse> mapFromJson(dynamic json) {
    final map = <String, HealthResponse>{};
    if (json is Map && json.isNotEmpty) {
      json = json.cast<String, dynamic>(); // ignore: parameter_assignments
      for (final entry in json.entries) {
        final value = HealthResponse.fromJson(entry.value);
        if (value != null) {
          map[entry.key] = value;
        }
      }
    }
    return map;
  }

  // maps a json object with a list of HealthResponse-objects as value to a dart map
  static Map<String, List<HealthResponse>> mapListFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final map = <String, List<HealthResponse>>{};
    if (json is Map && json.isNotEmpty) {
      // ignore: parameter_assignments
      json = json.cast<String, dynamic>();
      for (final entry in json.entries) {
        map[entry.key] = HealthResponse.listFromJson(
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
