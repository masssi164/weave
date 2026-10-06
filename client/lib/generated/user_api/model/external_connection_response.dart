//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//
// @dart=2.18

// ignore_for_file: unused_element, unused_import
// ignore_for_file: always_put_required_named_parameters_first
// ignore_for_file: constant_identifier_names
// ignore_for_file: lines_longer_than_80_chars

part of weave_user_api;

class ExternalConnectionResponse {
  /// Returns a new [ExternalConnectionResponse] instance.
  ExternalConnectionResponse({
    this.actions = const [],
    this.capabilities = const [],
    this.credentialState,
    this.mappingState,
    this.provider,
    this.readiness,
    this.status,
  });

  List<String> actions;

  List<String> capabilities;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? credentialState;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? mappingState;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? provider;

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
      other is ExternalConnectionResponse &&
          _deepEquality.equals(other.actions, actions) &&
          _deepEquality.equals(other.capabilities, capabilities) &&
          other.credentialState == credentialState &&
          other.mappingState == mappingState &&
          other.provider == provider &&
          other.readiness == readiness &&
          other.status == status;

  @override
  int get hashCode =>
      // ignore: unnecessary_parenthesis
      (actions.hashCode) +
      (capabilities.hashCode) +
      (credentialState == null ? 0 : credentialState!.hashCode) +
      (mappingState == null ? 0 : mappingState!.hashCode) +
      (provider == null ? 0 : provider!.hashCode) +
      (readiness == null ? 0 : readiness!.hashCode) +
      (status == null ? 0 : status!.hashCode);

  @override
  String toString() =>
      'ExternalConnectionResponse[actions=$actions, capabilities=$capabilities, credentialState=$credentialState, mappingState=$mappingState, provider=$provider, readiness=$readiness, status=$status]';

  Map<String, dynamic> toJson() {
    final json = <String, dynamic>{};
    json[r'actions'] = this.actions;
    json[r'capabilities'] = this.capabilities;
    if (this.credentialState != null) {
      json[r'credentialState'] = this.credentialState;
    } else {
      json[r'credentialState'] = null;
    }
    if (this.mappingState != null) {
      json[r'mappingState'] = this.mappingState;
    } else {
      json[r'mappingState'] = null;
    }
    if (this.provider != null) {
      json[r'provider'] = this.provider;
    } else {
      json[r'provider'] = null;
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

  /// Returns a new [ExternalConnectionResponse] instance and imports its values from
  /// [value] if it's a [Map], null otherwise.
  // ignore: prefer_constructors_over_static_methods
  static ExternalConnectionResponse? fromJson(dynamic value) {
    if (value is Map) {
      final json = value.cast<String, dynamic>();

      // Ensure that the map contains the required keys.
      // Note 1: the values aren't checked for validity beyond being non-null.
      // Note 2: this code is stripped in release mode!
      assert(() {
        requiredKeys.forEach((key) {
          assert(json.containsKey(key),
              'Required key "ExternalConnectionResponse[$key]" is missing from JSON.');
          assert(json[key] != null,
              'Required key "ExternalConnectionResponse[$key]" has a null value in JSON.');
        });
        return true;
      }());

      return ExternalConnectionResponse(
        actions: json[r'actions'] is Iterable
            ? (json[r'actions'] as Iterable)
                .cast<String>()
                .toList(growable: false)
            : const [],
        capabilities: json[r'capabilities'] is Iterable
            ? (json[r'capabilities'] as Iterable)
                .cast<String>()
                .toList(growable: false)
            : const [],
        credentialState: mapValueOfType<String>(json, r'credentialState'),
        mappingState: mapValueOfType<String>(json, r'mappingState'),
        provider: mapValueOfType<String>(json, r'provider'),
        readiness: mapValueOfType<String>(json, r'readiness'),
        status: mapValueOfType<String>(json, r'status'),
      );
    }
    return null;
  }

  static List<ExternalConnectionResponse> listFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final result = <ExternalConnectionResponse>[];
    if (json is List && json.isNotEmpty) {
      for (final row in json) {
        final value = ExternalConnectionResponse.fromJson(row);
        if (value != null) {
          result.add(value);
        }
      }
    }
    return result.toList(growable: growable);
  }

  static Map<String, ExternalConnectionResponse> mapFromJson(dynamic json) {
    final map = <String, ExternalConnectionResponse>{};
    if (json is Map && json.isNotEmpty) {
      json = json.cast<String, dynamic>(); // ignore: parameter_assignments
      for (final entry in json.entries) {
        final value = ExternalConnectionResponse.fromJson(entry.value);
        if (value != null) {
          map[entry.key] = value;
        }
      }
    }
    return map;
  }

  // maps a json object with a list of ExternalConnectionResponse-objects as value to a dart map
  static Map<String, List<ExternalConnectionResponse>> mapListFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final map = <String, List<ExternalConnectionResponse>>{};
    if (json is Map && json.isNotEmpty) {
      // ignore: parameter_assignments
      json = json.cast<String, dynamic>();
      for (final entry in json.entries) {
        map[entry.key] = ExternalConnectionResponse.listFromJson(
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
