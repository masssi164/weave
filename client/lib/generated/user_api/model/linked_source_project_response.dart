//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//
// @dart=2.18

// ignore_for_file: unused_element, unused_import
// ignore_for_file: always_put_required_named_parameters_first
// ignore_for_file: constant_identifier_names
// ignore_for_file: lines_longer_than_80_chars

part of weave_user_api;

class LinkedSourceProjectResponse {
  /// Returns a new [LinkedSourceProjectResponse] instance.
  LinkedSourceProjectResponse({
    this.displayName,
    this.id,
    this.providerKey,
    this.repositoryIds = const [],
    this.visibility,
  });

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? displayName;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? id;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? providerKey;

  List<String> repositoryIds;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? visibility;

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is LinkedSourceProjectResponse &&
          other.displayName == displayName &&
          other.id == id &&
          other.providerKey == providerKey &&
          _deepEquality.equals(other.repositoryIds, repositoryIds) &&
          other.visibility == visibility;

  @override
  int get hashCode =>
      // ignore: unnecessary_parenthesis
      (displayName == null ? 0 : displayName!.hashCode) +
      (id == null ? 0 : id!.hashCode) +
      (providerKey == null ? 0 : providerKey!.hashCode) +
      (repositoryIds.hashCode) +
      (visibility == null ? 0 : visibility!.hashCode);

  @override
  String toString() =>
      'LinkedSourceProjectResponse[displayName=$displayName, id=$id, providerKey=$providerKey, repositoryIds=$repositoryIds, visibility=$visibility]';

  Map<String, dynamic> toJson() {
    final json = <String, dynamic>{};
    if (this.displayName != null) {
      json[r'displayName'] = this.displayName;
    } else {
      json[r'displayName'] = null;
    }
    if (this.id != null) {
      json[r'id'] = this.id;
    } else {
      json[r'id'] = null;
    }
    if (this.providerKey != null) {
      json[r'providerKey'] = this.providerKey;
    } else {
      json[r'providerKey'] = null;
    }
    json[r'repositoryIds'] = this.repositoryIds;
    if (this.visibility != null) {
      json[r'visibility'] = this.visibility;
    } else {
      json[r'visibility'] = null;
    }
    return json;
  }

  /// Returns a new [LinkedSourceProjectResponse] instance and imports its values from
  /// [value] if it's a [Map], null otherwise.
  // ignore: prefer_constructors_over_static_methods
  static LinkedSourceProjectResponse? fromJson(dynamic value) {
    if (value is Map) {
      final json = value.cast<String, dynamic>();

      // Ensure that the map contains the required keys.
      // Note 1: the values aren't checked for validity beyond being non-null.
      // Note 2: this code is stripped in release mode!
      assert(() {
        requiredKeys.forEach((key) {
          assert(json.containsKey(key),
              'Required key "LinkedSourceProjectResponse[$key]" is missing from JSON.');
          assert(json[key] != null,
              'Required key "LinkedSourceProjectResponse[$key]" has a null value in JSON.');
        });
        return true;
      }());

      return LinkedSourceProjectResponse(
        displayName: mapValueOfType<String>(json, r'displayName'),
        id: mapValueOfType<String>(json, r'id'),
        providerKey: mapValueOfType<String>(json, r'providerKey'),
        repositoryIds: json[r'repositoryIds'] is Iterable
            ? (json[r'repositoryIds'] as Iterable)
                .cast<String>()
                .toList(growable: false)
            : const [],
        visibility: mapValueOfType<String>(json, r'visibility'),
      );
    }
    return null;
  }

  static List<LinkedSourceProjectResponse> listFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final result = <LinkedSourceProjectResponse>[];
    if (json is List && json.isNotEmpty) {
      for (final row in json) {
        final value = LinkedSourceProjectResponse.fromJson(row);
        if (value != null) {
          result.add(value);
        }
      }
    }
    return result.toList(growable: growable);
  }

  static Map<String, LinkedSourceProjectResponse> mapFromJson(dynamic json) {
    final map = <String, LinkedSourceProjectResponse>{};
    if (json is Map && json.isNotEmpty) {
      json = json.cast<String, dynamic>(); // ignore: parameter_assignments
      for (final entry in json.entries) {
        final value = LinkedSourceProjectResponse.fromJson(entry.value);
        if (value != null) {
          map[entry.key] = value;
        }
      }
    }
    return map;
  }

  // maps a json object with a list of LinkedSourceProjectResponse-objects as value to a dart map
  static Map<String, List<LinkedSourceProjectResponse>> mapListFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final map = <String, List<LinkedSourceProjectResponse>>{};
    if (json is Map && json.isNotEmpty) {
      // ignore: parameter_assignments
      json = json.cast<String, dynamic>();
      for (final entry in json.entries) {
        map[entry.key] = LinkedSourceProjectResponse.listFromJson(
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
