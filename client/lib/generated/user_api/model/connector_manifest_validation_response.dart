//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//
// @dart=2.18

// ignore_for_file: unused_element, unused_import
// ignore_for_file: always_put_required_named_parameters_first
// ignore_for_file: constant_identifier_names
// ignore_for_file: lines_longer_than_80_chars

part of weave_user_api;

class ConnectorManifestValidationResponse {
  /// Returns a new [ConnectorManifestValidationResponse] instance.
  ConnectorManifestValidationResponse({
    this.errors = const [],
    this.publicSdkAccepted,
    this.secretValuesAccepted,
    this.valid,
    this.warnings = const [],
  });

  List<String> errors;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  bool? publicSdkAccepted;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  bool? secretValuesAccepted;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  bool? valid;

  List<String> warnings;

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is ConnectorManifestValidationResponse &&
          _deepEquality.equals(other.errors, errors) &&
          other.publicSdkAccepted == publicSdkAccepted &&
          other.secretValuesAccepted == secretValuesAccepted &&
          other.valid == valid &&
          _deepEquality.equals(other.warnings, warnings);

  @override
  int get hashCode =>
      // ignore: unnecessary_parenthesis
      (errors.hashCode) +
      (publicSdkAccepted == null ? 0 : publicSdkAccepted!.hashCode) +
      (secretValuesAccepted == null ? 0 : secretValuesAccepted!.hashCode) +
      (valid == null ? 0 : valid!.hashCode) +
      (warnings.hashCode);

  @override
  String toString() =>
      'ConnectorManifestValidationResponse[errors=$errors, publicSdkAccepted=$publicSdkAccepted, secretValuesAccepted=$secretValuesAccepted, valid=$valid, warnings=$warnings]';

  Map<String, dynamic> toJson() {
    final json = <String, dynamic>{};
    json[r'errors'] = this.errors;
    if (this.publicSdkAccepted != null) {
      json[r'publicSdkAccepted'] = this.publicSdkAccepted;
    } else {
      json[r'publicSdkAccepted'] = null;
    }
    if (this.secretValuesAccepted != null) {
      json[r'secretValuesAccepted'] = this.secretValuesAccepted;
    } else {
      json[r'secretValuesAccepted'] = null;
    }
    if (this.valid != null) {
      json[r'valid'] = this.valid;
    } else {
      json[r'valid'] = null;
    }
    json[r'warnings'] = this.warnings;
    return json;
  }

  /// Returns a new [ConnectorManifestValidationResponse] instance and imports its values from
  /// [value] if it's a [Map], null otherwise.
  // ignore: prefer_constructors_over_static_methods
  static ConnectorManifestValidationResponse? fromJson(dynamic value) {
    if (value is Map) {
      final json = value.cast<String, dynamic>();

      // Ensure that the map contains the required keys.
      // Note 1: the values aren't checked for validity beyond being non-null.
      // Note 2: this code is stripped in release mode!
      assert(() {
        requiredKeys.forEach((key) {
          assert(json.containsKey(key),
              'Required key "ConnectorManifestValidationResponse[$key]" is missing from JSON.');
          assert(json[key] != null,
              'Required key "ConnectorManifestValidationResponse[$key]" has a null value in JSON.');
        });
        return true;
      }());

      return ConnectorManifestValidationResponse(
        errors: json[r'errors'] is Iterable
            ? (json[r'errors'] as Iterable)
                .cast<String>()
                .toList(growable: false)
            : const [],
        publicSdkAccepted: mapValueOfType<bool>(json, r'publicSdkAccepted'),
        secretValuesAccepted:
            mapValueOfType<bool>(json, r'secretValuesAccepted'),
        valid: mapValueOfType<bool>(json, r'valid'),
        warnings: json[r'warnings'] is Iterable
            ? (json[r'warnings'] as Iterable)
                .cast<String>()
                .toList(growable: false)
            : const [],
      );
    }
    return null;
  }

  static List<ConnectorManifestValidationResponse> listFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final result = <ConnectorManifestValidationResponse>[];
    if (json is List && json.isNotEmpty) {
      for (final row in json) {
        final value = ConnectorManifestValidationResponse.fromJson(row);
        if (value != null) {
          result.add(value);
        }
      }
    }
    return result.toList(growable: growable);
  }

  static Map<String, ConnectorManifestValidationResponse> mapFromJson(
      dynamic json) {
    final map = <String, ConnectorManifestValidationResponse>{};
    if (json is Map && json.isNotEmpty) {
      json = json.cast<String, dynamic>(); // ignore: parameter_assignments
      for (final entry in json.entries) {
        final value = ConnectorManifestValidationResponse.fromJson(entry.value);
        if (value != null) {
          map[entry.key] = value;
        }
      }
    }
    return map;
  }

  // maps a json object with a list of ConnectorManifestValidationResponse-objects as value to a dart map
  static Map<String, List<ConnectorManifestValidationResponse>> mapListFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final map = <String, List<ConnectorManifestValidationResponse>>{};
    if (json is Map && json.isNotEmpty) {
      // ignore: parameter_assignments
      json = json.cast<String, dynamic>();
      for (final entry in json.entries) {
        map[entry.key] = ConnectorManifestValidationResponse.listFromJson(
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
