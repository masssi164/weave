//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//
// @dart=2.18

// ignore_for_file: unused_element, unused_import
// ignore_for_file: always_put_required_named_parameters_first
// ignore_for_file: constant_identifier_names
// ignore_for_file: lines_longer_than_80_chars

part of weave_user_api;

class FilesUserCreateFolderRequest {
  /// Returns a new [FilesUserCreateFolderRequest] instance.
  FilesUserCreateFolderRequest({
    required this.name,
    required this.parentFileId,
    this.spaceRef,
  });

  String name;

  String parentFileId;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? spaceRef;

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is FilesUserCreateFolderRequest &&
          other.name == name &&
          other.parentFileId == parentFileId &&
          other.spaceRef == spaceRef;

  @override
  int get hashCode =>
      // ignore: unnecessary_parenthesis
      (name.hashCode) +
      (parentFileId.hashCode) +
      (spaceRef == null ? 0 : spaceRef!.hashCode);

  @override
  String toString() =>
      'FilesUserCreateFolderRequest[name=$name, parentFileId=$parentFileId, spaceRef=$spaceRef]';

  Map<String, dynamic> toJson() {
    final json = <String, dynamic>{};
    json[r'name'] = this.name;
    json[r'parentFileId'] = this.parentFileId;
    if (this.spaceRef != null) {
      json[r'spaceRef'] = this.spaceRef;
    } else {
      json[r'spaceRef'] = null;
    }
    return json;
  }

  /// Returns a new [FilesUserCreateFolderRequest] instance and imports its values from
  /// [value] if it's a [Map], null otherwise.
  // ignore: prefer_constructors_over_static_methods
  static FilesUserCreateFolderRequest? fromJson(dynamic value) {
    if (value is Map) {
      final json = value.cast<String, dynamic>();

      // Ensure that the map contains the required keys.
      // Note 1: the values aren't checked for validity beyond being non-null.
      // Note 2: this code is stripped in release mode!
      assert(() {
        requiredKeys.forEach((key) {
          assert(json.containsKey(key),
              'Required key "FilesUserCreateFolderRequest[$key]" is missing from JSON.');
          assert(json[key] != null,
              'Required key "FilesUserCreateFolderRequest[$key]" has a null value in JSON.');
        });
        return true;
      }());

      return FilesUserCreateFolderRequest(
        name: mapValueOfType<String>(json, r'name')!,
        parentFileId: mapValueOfType<String>(json, r'parentFileId')!,
        spaceRef: mapValueOfType<String>(json, r'spaceRef'),
      );
    }
    return null;
  }

  static List<FilesUserCreateFolderRequest> listFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final result = <FilesUserCreateFolderRequest>[];
    if (json is List && json.isNotEmpty) {
      for (final row in json) {
        final value = FilesUserCreateFolderRequest.fromJson(row);
        if (value != null) {
          result.add(value);
        }
      }
    }
    return result.toList(growable: growable);
  }

  static Map<String, FilesUserCreateFolderRequest> mapFromJson(dynamic json) {
    final map = <String, FilesUserCreateFolderRequest>{};
    if (json is Map && json.isNotEmpty) {
      json = json.cast<String, dynamic>(); // ignore: parameter_assignments
      for (final entry in json.entries) {
        final value = FilesUserCreateFolderRequest.fromJson(entry.value);
        if (value != null) {
          map[entry.key] = value;
        }
      }
    }
    return map;
  }

  // maps a json object with a list of FilesUserCreateFolderRequest-objects as value to a dart map
  static Map<String, List<FilesUserCreateFolderRequest>> mapListFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final map = <String, List<FilesUserCreateFolderRequest>>{};
    if (json is Map && json.isNotEmpty) {
      // ignore: parameter_assignments
      json = json.cast<String, dynamic>();
      for (final entry in json.entries) {
        map[entry.key] = FilesUserCreateFolderRequest.listFromJson(
          entry.value,
          growable: growable,
        );
      }
    }
    return map;
  }

  /// The list of required keys that must be present in a JSON.
  static const requiredKeys = <String>{
    'name',
    'parentFileId',
  };
}
