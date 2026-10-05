//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//
// @dart=2.18

// ignore_for_file: unused_element, unused_import
// ignore_for_file: always_put_required_named_parameters_first
// ignore_for_file: constant_identifier_names
// ignore_for_file: lines_longer_than_80_chars

part of weave_user_api;

class FileSetupCredentialRequest {
  /// Returns a new [FileSetupCredentialRequest] instance.
  FileSetupCredentialRequest({
    this.clientType,
    this.label,
  });

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? clientType;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? label;

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is FileSetupCredentialRequest &&
          other.clientType == clientType &&
          other.label == label;

  @override
  int get hashCode =>
      // ignore: unnecessary_parenthesis
      (clientType == null ? 0 : clientType!.hashCode) +
      (label == null ? 0 : label!.hashCode);

  @override
  String toString() =>
      'FileSetupCredentialRequest[clientType=$clientType, label=$label]';

  Map<String, dynamic> toJson() {
    final json = <String, dynamic>{};
    if (this.clientType != null) {
      json[r'clientType'] = this.clientType;
    } else {
      json[r'clientType'] = null;
    }
    if (this.label != null) {
      json[r'label'] = this.label;
    } else {
      json[r'label'] = null;
    }
    return json;
  }

  /// Returns a new [FileSetupCredentialRequest] instance and imports its values from
  /// [value] if it's a [Map], null otherwise.
  // ignore: prefer_constructors_over_static_methods
  static FileSetupCredentialRequest? fromJson(dynamic value) {
    if (value is Map) {
      final json = value.cast<String, dynamic>();

      // Ensure that the map contains the required keys.
      // Note 1: the values aren't checked for validity beyond being non-null.
      // Note 2: this code is stripped in release mode!
      assert(() {
        requiredKeys.forEach((key) {
          assert(json.containsKey(key),
              'Required key "FileSetupCredentialRequest[$key]" is missing from JSON.');
          assert(json[key] != null,
              'Required key "FileSetupCredentialRequest[$key]" has a null value in JSON.');
        });
        return true;
      }());

      return FileSetupCredentialRequest(
        clientType: mapValueOfType<String>(json, r'clientType'),
        label: mapValueOfType<String>(json, r'label'),
      );
    }
    return null;
  }

  static List<FileSetupCredentialRequest> listFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final result = <FileSetupCredentialRequest>[];
    if (json is List && json.isNotEmpty) {
      for (final row in json) {
        final value = FileSetupCredentialRequest.fromJson(row);
        if (value != null) {
          result.add(value);
        }
      }
    }
    return result.toList(growable: growable);
  }

  static Map<String, FileSetupCredentialRequest> mapFromJson(dynamic json) {
    final map = <String, FileSetupCredentialRequest>{};
    if (json is Map && json.isNotEmpty) {
      json = json.cast<String, dynamic>(); // ignore: parameter_assignments
      for (final entry in json.entries) {
        final value = FileSetupCredentialRequest.fromJson(entry.value);
        if (value != null) {
          map[entry.key] = value;
        }
      }
    }
    return map;
  }

  // maps a json object with a list of FileSetupCredentialRequest-objects as value to a dart map
  static Map<String, List<FileSetupCredentialRequest>> mapListFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final map = <String, List<FileSetupCredentialRequest>>{};
    if (json is Map && json.isNotEmpty) {
      // ignore: parameter_assignments
      json = json.cast<String, dynamic>();
      for (final entry in json.entries) {
        map[entry.key] = FileSetupCredentialRequest.listFromJson(
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
