//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//
// @dart=2.18

// ignore_for_file: unused_element, unused_import
// ignore_for_file: always_put_required_named_parameters_first
// ignore_for_file: constant_identifier_names
// ignore_for_file: lines_longer_than_80_chars

part of weave_user_api;

class DevopsMergeRequestSummaryResponse {
  /// Returns a new [DevopsMergeRequestSummaryResponse] instance.
  DevopsMergeRequestSummaryResponse({
    this.id,
    this.providerKey,
    this.repositoryId,
    this.sourceBranch,
    this.state,
    this.targetBranch,
    this.title,
    this.updatedAt,
  });

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

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? repositoryId;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? sourceBranch;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? state;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? targetBranch;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? title;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  DateTime? updatedAt;

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is DevopsMergeRequestSummaryResponse &&
          other.id == id &&
          other.providerKey == providerKey &&
          other.repositoryId == repositoryId &&
          other.sourceBranch == sourceBranch &&
          other.state == state &&
          other.targetBranch == targetBranch &&
          other.title == title &&
          other.updatedAt == updatedAt;

  @override
  int get hashCode =>
      // ignore: unnecessary_parenthesis
      (id == null ? 0 : id!.hashCode) +
      (providerKey == null ? 0 : providerKey!.hashCode) +
      (repositoryId == null ? 0 : repositoryId!.hashCode) +
      (sourceBranch == null ? 0 : sourceBranch!.hashCode) +
      (state == null ? 0 : state!.hashCode) +
      (targetBranch == null ? 0 : targetBranch!.hashCode) +
      (title == null ? 0 : title!.hashCode) +
      (updatedAt == null ? 0 : updatedAt!.hashCode);

  @override
  String toString() =>
      'DevopsMergeRequestSummaryResponse[id=$id, providerKey=$providerKey, repositoryId=$repositoryId, sourceBranch=$sourceBranch, state=$state, targetBranch=$targetBranch, title=$title, updatedAt=$updatedAt]';

  Map<String, dynamic> toJson() {
    final json = <String, dynamic>{};
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
    if (this.repositoryId != null) {
      json[r'repositoryId'] = this.repositoryId;
    } else {
      json[r'repositoryId'] = null;
    }
    if (this.sourceBranch != null) {
      json[r'sourceBranch'] = this.sourceBranch;
    } else {
      json[r'sourceBranch'] = null;
    }
    if (this.state != null) {
      json[r'state'] = this.state;
    } else {
      json[r'state'] = null;
    }
    if (this.targetBranch != null) {
      json[r'targetBranch'] = this.targetBranch;
    } else {
      json[r'targetBranch'] = null;
    }
    if (this.title != null) {
      json[r'title'] = this.title;
    } else {
      json[r'title'] = null;
    }
    if (this.updatedAt != null) {
      json[r'updatedAt'] = this.updatedAt!.toUtc().toIso8601String();
    } else {
      json[r'updatedAt'] = null;
    }
    return json;
  }

  /// Returns a new [DevopsMergeRequestSummaryResponse] instance and imports its values from
  /// [value] if it's a [Map], null otherwise.
  // ignore: prefer_constructors_over_static_methods
  static DevopsMergeRequestSummaryResponse? fromJson(dynamic value) {
    if (value is Map) {
      final json = value.cast<String, dynamic>();

      // Ensure that the map contains the required keys.
      // Note 1: the values aren't checked for validity beyond being non-null.
      // Note 2: this code is stripped in release mode!
      assert(() {
        requiredKeys.forEach((key) {
          assert(json.containsKey(key),
              'Required key "DevopsMergeRequestSummaryResponse[$key]" is missing from JSON.');
          assert(json[key] != null,
              'Required key "DevopsMergeRequestSummaryResponse[$key]" has a null value in JSON.');
        });
        return true;
      }());

      return DevopsMergeRequestSummaryResponse(
        id: mapValueOfType<String>(json, r'id'),
        providerKey: mapValueOfType<String>(json, r'providerKey'),
        repositoryId: mapValueOfType<String>(json, r'repositoryId'),
        sourceBranch: mapValueOfType<String>(json, r'sourceBranch'),
        state: mapValueOfType<String>(json, r'state'),
        targetBranch: mapValueOfType<String>(json, r'targetBranch'),
        title: mapValueOfType<String>(json, r'title'),
        updatedAt: mapDateTime(json, r'updatedAt', r''),
      );
    }
    return null;
  }

  static List<DevopsMergeRequestSummaryResponse> listFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final result = <DevopsMergeRequestSummaryResponse>[];
    if (json is List && json.isNotEmpty) {
      for (final row in json) {
        final value = DevopsMergeRequestSummaryResponse.fromJson(row);
        if (value != null) {
          result.add(value);
        }
      }
    }
    return result.toList(growable: growable);
  }

  static Map<String, DevopsMergeRequestSummaryResponse> mapFromJson(
      dynamic json) {
    final map = <String, DevopsMergeRequestSummaryResponse>{};
    if (json is Map && json.isNotEmpty) {
      json = json.cast<String, dynamic>(); // ignore: parameter_assignments
      for (final entry in json.entries) {
        final value = DevopsMergeRequestSummaryResponse.fromJson(entry.value);
        if (value != null) {
          map[entry.key] = value;
        }
      }
    }
    return map;
  }

  // maps a json object with a list of DevopsMergeRequestSummaryResponse-objects as value to a dart map
  static Map<String, List<DevopsMergeRequestSummaryResponse>> mapListFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final map = <String, List<DevopsMergeRequestSummaryResponse>>{};
    if (json is Map && json.isNotEmpty) {
      // ignore: parameter_assignments
      json = json.cast<String, dynamic>();
      for (final entry in json.entries) {
        map[entry.key] = DevopsMergeRequestSummaryResponse.listFromJson(
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
