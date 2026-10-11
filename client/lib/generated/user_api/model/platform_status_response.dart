//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//
// @dart=2.18

// ignore_for_file: unused_element, unused_import
// ignore_for_file: always_put_required_named_parameters_first
// ignore_for_file: constant_identifier_names
// ignore_for_file: lines_longer_than_80_chars

part of weave_user_api;

class PlatformStatusResponse {
  /// Returns a new [PlatformStatusResponse] instance.
  PlatformStatusResponse({
    this.actions = const [],
    this.auth,
    this.backend,
    this.calendar,
    this.checks = const [],
    this.files,
    this.matrix,
    this.nextcloud,
    this.requestId,
  });

  /// Distinct operator actions for checks that are not ready.
  List<String> actions;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  DiagnosticStatus? auth;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  DiagnosticStatus? backend;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  DiagnosticStatus? calendar;

  /// Normalized non-secret checks that admin UI, setup scripts, and smoke tests can render.
  List<DiagnosticCheck> checks;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  DiagnosticStatus? files;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  MatrixStatus? matrix;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  DiagnosticStatus? nextcloud;

  /// Correlation identifier also returned in the X-Request-Id response header.
  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? requestId;

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is PlatformStatusResponse &&
          _deepEquality.equals(other.actions, actions) &&
          other.auth == auth &&
          other.backend == backend &&
          other.calendar == calendar &&
          _deepEquality.equals(other.checks, checks) &&
          other.files == files &&
          other.matrix == matrix &&
          other.nextcloud == nextcloud &&
          other.requestId == requestId;

  @override
  int get hashCode =>
      // ignore: unnecessary_parenthesis
      (actions.hashCode) +
      (auth == null ? 0 : auth!.hashCode) +
      (backend == null ? 0 : backend!.hashCode) +
      (calendar == null ? 0 : calendar!.hashCode) +
      (checks.hashCode) +
      (files == null ? 0 : files!.hashCode) +
      (matrix == null ? 0 : matrix!.hashCode) +
      (nextcloud == null ? 0 : nextcloud!.hashCode) +
      (requestId == null ? 0 : requestId!.hashCode);

  @override
  String toString() =>
      'PlatformStatusResponse[actions=$actions, auth=$auth, backend=$backend, calendar=$calendar, checks=$checks, files=$files, matrix=$matrix, nextcloud=$nextcloud, requestId=$requestId]';

  Map<String, dynamic> toJson() {
    final json = <String, dynamic>{};
    json[r'actions'] = this.actions;
    if (this.auth != null) {
      json[r'auth'] = this.auth;
    } else {
      json[r'auth'] = null;
    }
    if (this.backend != null) {
      json[r'backend'] = this.backend;
    } else {
      json[r'backend'] = null;
    }
    if (this.calendar != null) {
      json[r'calendar'] = this.calendar;
    } else {
      json[r'calendar'] = null;
    }
    json[r'checks'] = this.checks;
    if (this.files != null) {
      json[r'files'] = this.files;
    } else {
      json[r'files'] = null;
    }
    if (this.matrix != null) {
      json[r'matrix'] = this.matrix;
    } else {
      json[r'matrix'] = null;
    }
    if (this.nextcloud != null) {
      json[r'nextcloud'] = this.nextcloud;
    } else {
      json[r'nextcloud'] = null;
    }
    if (this.requestId != null) {
      json[r'requestId'] = this.requestId;
    } else {
      json[r'requestId'] = null;
    }
    return json;
  }

  /// Returns a new [PlatformStatusResponse] instance and imports its values from
  /// [value] if it's a [Map], null otherwise.
  // ignore: prefer_constructors_over_static_methods
  static PlatformStatusResponse? fromJson(dynamic value) {
    if (value is Map) {
      final json = value.cast<String, dynamic>();

      // Ensure that the map contains the required keys.
      // Note 1: the values aren't checked for validity beyond being non-null.
      // Note 2: this code is stripped in release mode!
      assert(() {
        requiredKeys.forEach((key) {
          assert(json.containsKey(key),
              'Required key "PlatformStatusResponse[$key]" is missing from JSON.');
          assert(json[key] != null,
              'Required key "PlatformStatusResponse[$key]" has a null value in JSON.');
        });
        return true;
      }());

      return PlatformStatusResponse(
        actions: json[r'actions'] is Iterable
            ? (json[r'actions'] as Iterable)
                .cast<String>()
                .toList(growable: false)
            : const [],
        auth: DiagnosticStatus.fromJson(json[r'auth']),
        backend: DiagnosticStatus.fromJson(json[r'backend']),
        calendar: DiagnosticStatus.fromJson(json[r'calendar']),
        checks: DiagnosticCheck.listFromJson(json[r'checks']),
        files: DiagnosticStatus.fromJson(json[r'files']),
        matrix: MatrixStatus.fromJson(json[r'matrix']),
        nextcloud: DiagnosticStatus.fromJson(json[r'nextcloud']),
        requestId: mapValueOfType<String>(json, r'requestId'),
      );
    }
    return null;
  }

  static List<PlatformStatusResponse> listFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final result = <PlatformStatusResponse>[];
    if (json is List && json.isNotEmpty) {
      for (final row in json) {
        final value = PlatformStatusResponse.fromJson(row);
        if (value != null) {
          result.add(value);
        }
      }
    }
    return result.toList(growable: growable);
  }

  static Map<String, PlatformStatusResponse> mapFromJson(dynamic json) {
    final map = <String, PlatformStatusResponse>{};
    if (json is Map && json.isNotEmpty) {
      json = json.cast<String, dynamic>(); // ignore: parameter_assignments
      for (final entry in json.entries) {
        final value = PlatformStatusResponse.fromJson(entry.value);
        if (value != null) {
          map[entry.key] = value;
        }
      }
    }
    return map;
  }

  // maps a json object with a list of PlatformStatusResponse-objects as value to a dart map
  static Map<String, List<PlatformStatusResponse>> mapListFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final map = <String, List<PlatformStatusResponse>>{};
    if (json is Map && json.isNotEmpty) {
      // ignore: parameter_assignments
      json = json.cast<String, dynamic>();
      for (final entry in json.entries) {
        map[entry.key] = PlatformStatusResponse.listFromJson(
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
