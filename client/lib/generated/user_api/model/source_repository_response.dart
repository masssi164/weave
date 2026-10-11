//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//
// @dart=2.18

// ignore_for_file: unused_element, unused_import
// ignore_for_file: always_put_required_named_parameters_first
// ignore_for_file: constant_identifier_names
// ignore_for_file: lines_longer_than_80_chars

part of weave_user_api;

class SourceRepositoryResponse {
  /// Returns a new [SourceRepositoryResponse] instance.
  SourceRepositoryResponse({
    this.archived,
    this.defaultBranch,
    this.displayName,
    this.id,
    this.projectId,
    this.providerKey,
  });

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  bool? archived;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? defaultBranch;

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
  String? projectId;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? providerKey;

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is SourceRepositoryResponse &&
          other.archived == archived &&
          other.defaultBranch == defaultBranch &&
          other.displayName == displayName &&
          other.id == id &&
          other.projectId == projectId &&
          other.providerKey == providerKey;

  @override
  int get hashCode =>
      // ignore: unnecessary_parenthesis
      (archived == null ? 0 : archived!.hashCode) +
      (defaultBranch == null ? 0 : defaultBranch!.hashCode) +
      (displayName == null ? 0 : displayName!.hashCode) +
      (id == null ? 0 : id!.hashCode) +
      (projectId == null ? 0 : projectId!.hashCode) +
      (providerKey == null ? 0 : providerKey!.hashCode);

  @override
  String toString() =>
      'SourceRepositoryResponse[archived=$archived, defaultBranch=$defaultBranch, displayName=$displayName, id=$id, projectId=$projectId, providerKey=$providerKey]';

  Map<String, dynamic> toJson() {
    final json = <String, dynamic>{};
    if (this.archived != null) {
      json[r'archived'] = this.archived;
    } else {
      json[r'archived'] = null;
    }
    if (this.defaultBranch != null) {
      json[r'defaultBranch'] = this.defaultBranch;
    } else {
      json[r'defaultBranch'] = null;
    }
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
    if (this.projectId != null) {
      json[r'projectId'] = this.projectId;
    } else {
      json[r'projectId'] = null;
    }
    if (this.providerKey != null) {
      json[r'providerKey'] = this.providerKey;
    } else {
      json[r'providerKey'] = null;
    }
    return json;
  }

  /// Returns a new [SourceRepositoryResponse] instance and imports its values from
  /// [value] if it's a [Map], null otherwise.
  // ignore: prefer_constructors_over_static_methods
  static SourceRepositoryResponse? fromJson(dynamic value) {
    if (value is Map) {
      final json = value.cast<String, dynamic>();

      // Ensure that the map contains the required keys.
      // Note 1: the values aren't checked for validity beyond being non-null.
      // Note 2: this code is stripped in release mode!
      assert(() {
        requiredKeys.forEach((key) {
          assert(json.containsKey(key),
              'Required key "SourceRepositoryResponse[$key]" is missing from JSON.');
          assert(json[key] != null,
              'Required key "SourceRepositoryResponse[$key]" has a null value in JSON.');
        });
        return true;
      }());

      return SourceRepositoryResponse(
        archived: mapValueOfType<bool>(json, r'archived'),
        defaultBranch: mapValueOfType<String>(json, r'defaultBranch'),
        displayName: mapValueOfType<String>(json, r'displayName'),
        id: mapValueOfType<String>(json, r'id'),
        projectId: mapValueOfType<String>(json, r'projectId'),
        providerKey: mapValueOfType<String>(json, r'providerKey'),
      );
    }
    return null;
  }

  static List<SourceRepositoryResponse> listFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final result = <SourceRepositoryResponse>[];
    if (json is List && json.isNotEmpty) {
      for (final row in json) {
        final value = SourceRepositoryResponse.fromJson(row);
        if (value != null) {
          result.add(value);
        }
      }
    }
    return result.toList(growable: growable);
  }

  static Map<String, SourceRepositoryResponse> mapFromJson(dynamic json) {
    final map = <String, SourceRepositoryResponse>{};
    if (json is Map && json.isNotEmpty) {
      json = json.cast<String, dynamic>(); // ignore: parameter_assignments
      for (final entry in json.entries) {
        final value = SourceRepositoryResponse.fromJson(entry.value);
        if (value != null) {
          map[entry.key] = value;
        }
      }
    }
    return map;
  }

  // maps a json object with a list of SourceRepositoryResponse-objects as value to a dart map
  static Map<String, List<SourceRepositoryResponse>> mapListFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final map = <String, List<SourceRepositoryResponse>>{};
    if (json is Map && json.isNotEmpty) {
      // ignore: parameter_assignments
      json = json.cast<String, dynamic>();
      for (final entry in json.entries) {
        map[entry.key] = SourceRepositoryResponse.listFromJson(
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
