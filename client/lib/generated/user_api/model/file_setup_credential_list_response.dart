//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//
// @dart=2.18

// ignore_for_file: unused_element, unused_import
// ignore_for_file: always_put_required_named_parameters_first
// ignore_for_file: constant_identifier_names
// ignore_for_file: lines_longer_than_80_chars

part of weave_user_api;

class FileSetupCredentialListResponse {
  /// Returns a new [FileSetupCredentialListResponse] instance.
  FileSetupCredentialListResponse({
    this.credentials = const [],
  });

  List<FileSetupCredentialResponse> credentials;

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is FileSetupCredentialListResponse &&
          _deepEquality.equals(other.credentials, credentials);

  @override
  int get hashCode =>
      // ignore: unnecessary_parenthesis
      (credentials.hashCode);

  @override
  String toString() =>
      'FileSetupCredentialListResponse[credentials=$credentials]';

  Map<String, dynamic> toJson() {
    final json = <String, dynamic>{};
    json[r'credentials'] = this.credentials;
    return json;
  }

  /// Returns a new [FileSetupCredentialListResponse] instance and imports its values from
  /// [value] if it's a [Map], null otherwise.
  // ignore: prefer_constructors_over_static_methods
  static FileSetupCredentialListResponse? fromJson(dynamic value) {
    if (value is Map) {
      final json = value.cast<String, dynamic>();

      // Ensure that the map contains the required keys.
      // Note 1: the values aren't checked for validity beyond being non-null.
      // Note 2: this code is stripped in release mode!
      assert(() {
        requiredKeys.forEach((key) {
          assert(json.containsKey(key),
              'Required key "FileSetupCredentialListResponse[$key]" is missing from JSON.');
          assert(json[key] != null,
              'Required key "FileSetupCredentialListResponse[$key]" has a null value in JSON.');
        });
        return true;
      }());

      return FileSetupCredentialListResponse(
        credentials:
            FileSetupCredentialResponse.listFromJson(json[r'credentials']),
      );
    }
    return null;
  }

  static List<FileSetupCredentialListResponse> listFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final result = <FileSetupCredentialListResponse>[];
    if (json is List && json.isNotEmpty) {
      for (final row in json) {
        final value = FileSetupCredentialListResponse.fromJson(row);
        if (value != null) {
          result.add(value);
        }
      }
    }
    return result.toList(growable: growable);
  }

  static Map<String, FileSetupCredentialListResponse> mapFromJson(
      dynamic json) {
    final map = <String, FileSetupCredentialListResponse>{};
    if (json is Map && json.isNotEmpty) {
      json = json.cast<String, dynamic>(); // ignore: parameter_assignments
      for (final entry in json.entries) {
        final value = FileSetupCredentialListResponse.fromJson(entry.value);
        if (value != null) {
          map[entry.key] = value;
        }
      }
    }
    return map;
  }

  // maps a json object with a list of FileSetupCredentialListResponse-objects as value to a dart map
  static Map<String, List<FileSetupCredentialListResponse>> mapListFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final map = <String, List<FileSetupCredentialListResponse>>{};
    if (json is Map && json.isNotEmpty) {
      // ignore: parameter_assignments
      json = json.cast<String, dynamic>();
      for (final entry in json.entries) {
        map[entry.key] = FileSetupCredentialListResponse.listFromJson(
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
