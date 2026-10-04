//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//
// @dart=2.18

// ignore_for_file: unused_element, unused_import
// ignore_for_file: always_put_required_named_parameters_first
// ignore_for_file: constant_identifier_names
// ignore_for_file: lines_longer_than_80_chars

part of weave_user_api;

class MatrixStatus {
  /// Returns a new [MatrixStatus] instance.
  MatrixStatus({
    this.action,
    this.backendBoundary,
    this.e2ee,
    this.e2eeEnabled,
    this.federationEnabled,
    this.message,
    this.readiness,
    this.status,
  });

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? action;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  MatrixBackendBoundary? backendBoundary;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  E2eeStatus? e2ee;

  /// True only after all active Matrix E2EE implementation and accessibility gates are validated.
  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  bool? e2eeEnabled;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  bool? federationEnabled;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? message;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? readiness;

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
      other is MatrixStatus &&
          other.action == action &&
          other.backendBoundary == backendBoundary &&
          other.e2ee == e2ee &&
          other.e2eeEnabled == e2eeEnabled &&
          other.federationEnabled == federationEnabled &&
          other.message == message &&
          other.readiness == readiness &&
          other.status == status;

  @override
  int get hashCode =>
      // ignore: unnecessary_parenthesis
      (action == null ? 0 : action!.hashCode) +
      (backendBoundary == null ? 0 : backendBoundary!.hashCode) +
      (e2ee == null ? 0 : e2ee!.hashCode) +
      (e2eeEnabled == null ? 0 : e2eeEnabled!.hashCode) +
      (federationEnabled == null ? 0 : federationEnabled!.hashCode) +
      (message == null ? 0 : message!.hashCode) +
      (readiness == null ? 0 : readiness!.hashCode) +
      (status == null ? 0 : status!.hashCode);

  @override
  String toString() =>
      'MatrixStatus[action=$action, backendBoundary=$backendBoundary, e2ee=$e2ee, e2eeEnabled=$e2eeEnabled, federationEnabled=$federationEnabled, message=$message, readiness=$readiness, status=$status]';

  Map<String, dynamic> toJson() {
    final json = <String, dynamic>{};
    if (this.action != null) {
      json[r'action'] = this.action;
    } else {
      json[r'action'] = null;
    }
    if (this.backendBoundary != null) {
      json[r'backendBoundary'] = this.backendBoundary;
    } else {
      json[r'backendBoundary'] = null;
    }
    if (this.e2ee != null) {
      json[r'e2ee'] = this.e2ee;
    } else {
      json[r'e2ee'] = null;
    }
    if (this.e2eeEnabled != null) {
      json[r'e2eeEnabled'] = this.e2eeEnabled;
    } else {
      json[r'e2eeEnabled'] = null;
    }
    if (this.federationEnabled != null) {
      json[r'federationEnabled'] = this.federationEnabled;
    } else {
      json[r'federationEnabled'] = null;
    }
    if (this.message != null) {
      json[r'message'] = this.message;
    } else {
      json[r'message'] = null;
    }
    if (this.readiness != null) {
      json[r'readiness'] = this.readiness;
    } else {
      json[r'readiness'] = null;
    }
    if (this.status != null) {
      json[r'status'] = this.status;
    } else {
      json[r'status'] = null;
    }
    return json;
  }

  /// Returns a new [MatrixStatus] instance and imports its values from
  /// [value] if it's a [Map], null otherwise.
  // ignore: prefer_constructors_over_static_methods
  static MatrixStatus? fromJson(dynamic value) {
    if (value is Map) {
      final json = value.cast<String, dynamic>();

      // Ensure that the map contains the required keys.
      // Note 1: the values aren't checked for validity beyond being non-null.
      // Note 2: this code is stripped in release mode!
      assert(() {
        requiredKeys.forEach((key) {
          assert(json.containsKey(key),
              'Required key "MatrixStatus[$key]" is missing from JSON.');
          assert(json[key] != null,
              'Required key "MatrixStatus[$key]" has a null value in JSON.');
        });
        return true;
      }());

      return MatrixStatus(
        action: mapValueOfType<String>(json, r'action'),
        backendBoundary:
            MatrixBackendBoundary.fromJson(json[r'backendBoundary']),
        e2ee: E2eeStatus.fromJson(json[r'e2ee']),
        e2eeEnabled: mapValueOfType<bool>(json, r'e2eeEnabled'),
        federationEnabled: mapValueOfType<bool>(json, r'federationEnabled'),
        message: mapValueOfType<String>(json, r'message'),
        readiness: mapValueOfType<String>(json, r'readiness'),
        status: mapValueOfType<String>(json, r'status'),
      );
    }
    return null;
  }

  static List<MatrixStatus> listFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final result = <MatrixStatus>[];
    if (json is List && json.isNotEmpty) {
      for (final row in json) {
        final value = MatrixStatus.fromJson(row);
        if (value != null) {
          result.add(value);
        }
      }
    }
    return result.toList(growable: growable);
  }

  static Map<String, MatrixStatus> mapFromJson(dynamic json) {
    final map = <String, MatrixStatus>{};
    if (json is Map && json.isNotEmpty) {
      json = json.cast<String, dynamic>(); // ignore: parameter_assignments
      for (final entry in json.entries) {
        final value = MatrixStatus.fromJson(entry.value);
        if (value != null) {
          map[entry.key] = value;
        }
      }
    }
    return map;
  }

  // maps a json object with a list of MatrixStatus-objects as value to a dart map
  static Map<String, List<MatrixStatus>> mapListFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final map = <String, List<MatrixStatus>>{};
    if (json is Map && json.isNotEmpty) {
      // ignore: parameter_assignments
      json = json.cast<String, dynamic>();
      for (final entry in json.entries) {
        map[entry.key] = MatrixStatus.listFromJson(
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
