//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//
// @dart=2.18

// ignore_for_file: unused_element, unused_import
// ignore_for_file: always_put_required_named_parameters_first
// ignore_for_file: constant_identifier_names
// ignore_for_file: lines_longer_than_80_chars

part of weave_user_api;

class CalendarClientSetupResponse {
  /// Returns a new [CalendarClientSetupResponse] instance.
  CalendarClientSetupResponse({
    this.accessModel,
    this.credentialPolicy,
    this.credentialReadiness,
    this.endpoints,
    this.options = const [],
    this.scope,
    this.username,
  });

  /// Explicit product/private/external-client access model.
  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  CalendarAccessModelResponse? accessModel;

  /// Explicit credential policy for external clients.
  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? credentialPolicy;

  /// Safety readiness for profile generation and tokenized subscription paths.
  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  CalendarCredentialReadinessResponse? credentialReadiness;

  /// CalDAV/WebDAV discovery URLs that may be shown to the user without credentials.
  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  CalendarExternalEndpointsResponse? endpoints;

  /// Platform-specific setup options. Options never contain passwords or bearer tokens.
  List<CalendarClientSetupOptionResponse> options;

  /// Calendar ownership scope exposed by the Weave calendar facade.
  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  CalendarScopeResponse? scope;

  /// Authenticated user's external calendar account identifier.
  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? username;

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is CalendarClientSetupResponse &&
          other.accessModel == accessModel &&
          other.credentialPolicy == credentialPolicy &&
          other.credentialReadiness == credentialReadiness &&
          other.endpoints == endpoints &&
          _deepEquality.equals(other.options, options) &&
          other.scope == scope &&
          other.username == username;

  @override
  int get hashCode =>
      // ignore: unnecessary_parenthesis
      (accessModel == null ? 0 : accessModel!.hashCode) +
      (credentialPolicy == null ? 0 : credentialPolicy!.hashCode) +
      (credentialReadiness == null ? 0 : credentialReadiness!.hashCode) +
      (endpoints == null ? 0 : endpoints!.hashCode) +
      (options.hashCode) +
      (scope == null ? 0 : scope!.hashCode) +
      (username == null ? 0 : username!.hashCode);

  @override
  String toString() =>
      'CalendarClientSetupResponse[accessModel=$accessModel, credentialPolicy=$credentialPolicy, credentialReadiness=$credentialReadiness, endpoints=$endpoints, options=$options, scope=$scope, username=$username]';

  Map<String, dynamic> toJson() {
    final json = <String, dynamic>{};
    if (this.accessModel != null) {
      json[r'accessModel'] = this.accessModel;
    } else {
      json[r'accessModel'] = null;
    }
    if (this.credentialPolicy != null) {
      json[r'credentialPolicy'] = this.credentialPolicy;
    } else {
      json[r'credentialPolicy'] = null;
    }
    if (this.credentialReadiness != null) {
      json[r'credentialReadiness'] = this.credentialReadiness;
    } else {
      json[r'credentialReadiness'] = null;
    }
    if (this.endpoints != null) {
      json[r'endpoints'] = this.endpoints;
    } else {
      json[r'endpoints'] = null;
    }
    json[r'options'] = this.options;
    if (this.scope != null) {
      json[r'scope'] = this.scope;
    } else {
      json[r'scope'] = null;
    }
    if (this.username != null) {
      json[r'username'] = this.username;
    } else {
      json[r'username'] = null;
    }
    return json;
  }

  /// Returns a new [CalendarClientSetupResponse] instance and imports its values from
  /// [value] if it's a [Map], null otherwise.
  // ignore: prefer_constructors_over_static_methods
  static CalendarClientSetupResponse? fromJson(dynamic value) {
    if (value is Map) {
      final json = value.cast<String, dynamic>();

      // Ensure that the map contains the required keys.
      // Note 1: the values aren't checked for validity beyond being non-null.
      // Note 2: this code is stripped in release mode!
      assert(() {
        requiredKeys.forEach((key) {
          assert(json.containsKey(key),
              'Required key "CalendarClientSetupResponse[$key]" is missing from JSON.');
          assert(json[key] != null,
              'Required key "CalendarClientSetupResponse[$key]" has a null value in JSON.');
        });
        return true;
      }());

      return CalendarClientSetupResponse(
        accessModel: CalendarAccessModelResponse.fromJson(json[r'accessModel']),
        credentialPolicy: mapValueOfType<String>(json, r'credentialPolicy'),
        credentialReadiness: CalendarCredentialReadinessResponse.fromJson(
            json[r'credentialReadiness']),
        endpoints:
            CalendarExternalEndpointsResponse.fromJson(json[r'endpoints']),
        options:
            CalendarClientSetupOptionResponse.listFromJson(json[r'options']),
        scope: CalendarScopeResponse.fromJson(json[r'scope']),
        username: mapValueOfType<String>(json, r'username'),
      );
    }
    return null;
  }

  static List<CalendarClientSetupResponse> listFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final result = <CalendarClientSetupResponse>[];
    if (json is List && json.isNotEmpty) {
      for (final row in json) {
        final value = CalendarClientSetupResponse.fromJson(row);
        if (value != null) {
          result.add(value);
        }
      }
    }
    return result.toList(growable: growable);
  }

  static Map<String, CalendarClientSetupResponse> mapFromJson(dynamic json) {
    final map = <String, CalendarClientSetupResponse>{};
    if (json is Map && json.isNotEmpty) {
      json = json.cast<String, dynamic>(); // ignore: parameter_assignments
      for (final entry in json.entries) {
        final value = CalendarClientSetupResponse.fromJson(entry.value);
        if (value != null) {
          map[entry.key] = value;
        }
      }
    }
    return map;
  }

  // maps a json object with a list of CalendarClientSetupResponse-objects as value to a dart map
  static Map<String, List<CalendarClientSetupResponse>> mapListFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final map = <String, List<CalendarClientSetupResponse>>{};
    if (json is Map && json.isNotEmpty) {
      // ignore: parameter_assignments
      json = json.cast<String, dynamic>();
      for (final entry in json.entries) {
        map[entry.key] = CalendarClientSetupResponse.listFromJson(
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
