//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//
// @dart=2.18

// ignore_for_file: unused_element, unused_import
// ignore_for_file: always_put_required_named_parameters_first
// ignore_for_file: constant_identifier_names
// ignore_for_file: lines_longer_than_80_chars

part of weave_user_api;

class InteropStatusResponse {
  /// Returns a new [InteropStatusResponse] instance.
  InteropStatusResponse({
    this.auditEventTypes = const [],
    this.connections = const [],
    this.connectorBoundary,
    this.consentRegistry,
    this.degradedStates = const [],
    this.enabled,
    this.rateLimitStates = const [],
    this.readiness,
    this.supportBundle,
  });

  List<String> auditEventTypes;

  List<ExternalConnectionResponse> connections;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  ConnectorBoundarySummaryResponse? connectorBoundary;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  ConsentCapabilityRegistryResponse? consentRegistry;

  List<String> degradedStates;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  bool? enabled;

  List<String> rateLimitStates;

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
  SupportBundlePolicyResponse? supportBundle;

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is InteropStatusResponse &&
          _deepEquality.equals(other.auditEventTypes, auditEventTypes) &&
          _deepEquality.equals(other.connections, connections) &&
          other.connectorBoundary == connectorBoundary &&
          other.consentRegistry == consentRegistry &&
          _deepEquality.equals(other.degradedStates, degradedStates) &&
          other.enabled == enabled &&
          _deepEquality.equals(other.rateLimitStates, rateLimitStates) &&
          other.readiness == readiness &&
          other.supportBundle == supportBundle;

  @override
  int get hashCode =>
      // ignore: unnecessary_parenthesis
      (auditEventTypes.hashCode) +
      (connections.hashCode) +
      (connectorBoundary == null ? 0 : connectorBoundary!.hashCode) +
      (consentRegistry == null ? 0 : consentRegistry!.hashCode) +
      (degradedStates.hashCode) +
      (enabled == null ? 0 : enabled!.hashCode) +
      (rateLimitStates.hashCode) +
      (readiness == null ? 0 : readiness!.hashCode) +
      (supportBundle == null ? 0 : supportBundle!.hashCode);

  @override
  String toString() =>
      'InteropStatusResponse[auditEventTypes=$auditEventTypes, connections=$connections, connectorBoundary=$connectorBoundary, consentRegistry=$consentRegistry, degradedStates=$degradedStates, enabled=$enabled, rateLimitStates=$rateLimitStates, readiness=$readiness, supportBundle=$supportBundle]';

  Map<String, dynamic> toJson() {
    final json = <String, dynamic>{};
    json[r'auditEventTypes'] = this.auditEventTypes;
    json[r'connections'] = this.connections;
    if (this.connectorBoundary != null) {
      json[r'connectorBoundary'] = this.connectorBoundary;
    } else {
      json[r'connectorBoundary'] = null;
    }
    if (this.consentRegistry != null) {
      json[r'consentRegistry'] = this.consentRegistry;
    } else {
      json[r'consentRegistry'] = null;
    }
    json[r'degradedStates'] = this.degradedStates;
    if (this.enabled != null) {
      json[r'enabled'] = this.enabled;
    } else {
      json[r'enabled'] = null;
    }
    json[r'rateLimitStates'] = this.rateLimitStates;
    if (this.readiness != null) {
      json[r'readiness'] = this.readiness;
    } else {
      json[r'readiness'] = null;
    }
    if (this.supportBundle != null) {
      json[r'supportBundle'] = this.supportBundle;
    } else {
      json[r'supportBundle'] = null;
    }
    return json;
  }

  /// Returns a new [InteropStatusResponse] instance and imports its values from
  /// [value] if it's a [Map], null otherwise.
  // ignore: prefer_constructors_over_static_methods
  static InteropStatusResponse? fromJson(dynamic value) {
    if (value is Map) {
      final json = value.cast<String, dynamic>();

      // Ensure that the map contains the required keys.
      // Note 1: the values aren't checked for validity beyond being non-null.
      // Note 2: this code is stripped in release mode!
      assert(() {
        requiredKeys.forEach((key) {
          assert(json.containsKey(key),
              'Required key "InteropStatusResponse[$key]" is missing from JSON.');
          assert(json[key] != null,
              'Required key "InteropStatusResponse[$key]" has a null value in JSON.');
        });
        return true;
      }());

      return InteropStatusResponse(
        auditEventTypes: json[r'auditEventTypes'] is Iterable
            ? (json[r'auditEventTypes'] as Iterable)
                .cast<String>()
                .toList(growable: false)
            : const [],
        connections:
            ExternalConnectionResponse.listFromJson(json[r'connections']),
        connectorBoundary: ConnectorBoundarySummaryResponse.fromJson(
            json[r'connectorBoundary']),
        consentRegistry: ConsentCapabilityRegistryResponse.fromJson(
            json[r'consentRegistry']),
        degradedStates: json[r'degradedStates'] is Iterable
            ? (json[r'degradedStates'] as Iterable)
                .cast<String>()
                .toList(growable: false)
            : const [],
        enabled: mapValueOfType<bool>(json, r'enabled'),
        rateLimitStates: json[r'rateLimitStates'] is Iterable
            ? (json[r'rateLimitStates'] as Iterable)
                .cast<String>()
                .toList(growable: false)
            : const [],
        readiness: mapValueOfType<String>(json, r'readiness'),
        supportBundle:
            SupportBundlePolicyResponse.fromJson(json[r'supportBundle']),
      );
    }
    return null;
  }

  static List<InteropStatusResponse> listFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final result = <InteropStatusResponse>[];
    if (json is List && json.isNotEmpty) {
      for (final row in json) {
        final value = InteropStatusResponse.fromJson(row);
        if (value != null) {
          result.add(value);
        }
      }
    }
    return result.toList(growable: growable);
  }

  static Map<String, InteropStatusResponse> mapFromJson(dynamic json) {
    final map = <String, InteropStatusResponse>{};
    if (json is Map && json.isNotEmpty) {
      json = json.cast<String, dynamic>(); // ignore: parameter_assignments
      for (final entry in json.entries) {
        final value = InteropStatusResponse.fromJson(entry.value);
        if (value != null) {
          map[entry.key] = value;
        }
      }
    }
    return map;
  }

  // maps a json object with a list of InteropStatusResponse-objects as value to a dart map
  static Map<String, List<InteropStatusResponse>> mapListFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final map = <String, List<InteropStatusResponse>>{};
    if (json is Map && json.isNotEmpty) {
      // ignore: parameter_assignments
      json = json.cast<String, dynamic>();
      for (final entry in json.entries) {
        map[entry.key] = InteropStatusResponse.listFromJson(
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
