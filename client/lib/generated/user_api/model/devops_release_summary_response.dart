//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//
// @dart=2.18

// ignore_for_file: unused_element, unused_import
// ignore_for_file: always_put_required_named_parameters_first
// ignore_for_file: constant_identifier_names
// ignore_for_file: lines_longer_than_80_chars

part of weave_user_api;

class DevopsReleaseSummaryResponse {
  /// Returns a new [DevopsReleaseSummaryResponse] instance.
  DevopsReleaseSummaryResponse({
    this.id,
    this.name,
    this.providerKey,
    this.releasedAt,
    this.repositoryId,
    this.tagName,
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
  String? name;

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
  DateTime? releasedAt;

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
  String? tagName;

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is DevopsReleaseSummaryResponse &&
          other.id == id &&
          other.name == name &&
          other.providerKey == providerKey &&
          other.releasedAt == releasedAt &&
          other.repositoryId == repositoryId &&
          other.tagName == tagName;

  @override
  int get hashCode =>
      // ignore: unnecessary_parenthesis
      (id == null ? 0 : id!.hashCode) +
      (name == null ? 0 : name!.hashCode) +
      (providerKey == null ? 0 : providerKey!.hashCode) +
      (releasedAt == null ? 0 : releasedAt!.hashCode) +
      (repositoryId == null ? 0 : repositoryId!.hashCode) +
      (tagName == null ? 0 : tagName!.hashCode);

  @override
  String toString() =>
      'DevopsReleaseSummaryResponse[id=$id, name=$name, providerKey=$providerKey, releasedAt=$releasedAt, repositoryId=$repositoryId, tagName=$tagName]';

  Map<String, dynamic> toJson() {
    final json = <String, dynamic>{};
    if (this.id != null) {
      json[r'id'] = this.id;
    } else {
      json[r'id'] = null;
    }
    if (this.name != null) {
      json[r'name'] = this.name;
    } else {
      json[r'name'] = null;
    }
    if (this.providerKey != null) {
      json[r'providerKey'] = this.providerKey;
    } else {
      json[r'providerKey'] = null;
    }
    if (this.releasedAt != null) {
      json[r'releasedAt'] = this.releasedAt!.toUtc().toIso8601String();
    } else {
      json[r'releasedAt'] = null;
    }
    if (this.repositoryId != null) {
      json[r'repositoryId'] = this.repositoryId;
    } else {
      json[r'repositoryId'] = null;
    }
    if (this.tagName != null) {
      json[r'tagName'] = this.tagName;
    } else {
      json[r'tagName'] = null;
    }
    return json;
  }

  /// Returns a new [DevopsReleaseSummaryResponse] instance and imports its values from
  /// [value] if it's a [Map], null otherwise.
  // ignore: prefer_constructors_over_static_methods
  static DevopsReleaseSummaryResponse? fromJson(dynamic value) {
    if (value is Map) {
      final json = value.cast<String, dynamic>();

      // Ensure that the map contains the required keys.
      // Note 1: the values aren't checked for validity beyond being non-null.
      // Note 2: this code is stripped in release mode!
      assert(() {
        requiredKeys.forEach((key) {
          assert(json.containsKey(key),
              'Required key "DevopsReleaseSummaryResponse[$key]" is missing from JSON.');
          assert(json[key] != null,
              'Required key "DevopsReleaseSummaryResponse[$key]" has a null value in JSON.');
        });
        return true;
      }());

      return DevopsReleaseSummaryResponse(
        id: mapValueOfType<String>(json, r'id'),
        name: mapValueOfType<String>(json, r'name'),
        providerKey: mapValueOfType<String>(json, r'providerKey'),
        releasedAt: mapDateTime(json, r'releasedAt', r''),
        repositoryId: mapValueOfType<String>(json, r'repositoryId'),
        tagName: mapValueOfType<String>(json, r'tagName'),
      );
    }
    return null;
  }

  static List<DevopsReleaseSummaryResponse> listFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final result = <DevopsReleaseSummaryResponse>[];
    if (json is List && json.isNotEmpty) {
      for (final row in json) {
        final value = DevopsReleaseSummaryResponse.fromJson(row);
        if (value != null) {
          result.add(value);
        }
      }
    }
    return result.toList(growable: growable);
  }

  static Map<String, DevopsReleaseSummaryResponse> mapFromJson(dynamic json) {
    final map = <String, DevopsReleaseSummaryResponse>{};
    if (json is Map && json.isNotEmpty) {
      json = json.cast<String, dynamic>(); // ignore: parameter_assignments
      for (final entry in json.entries) {
        final value = DevopsReleaseSummaryResponse.fromJson(entry.value);
        if (value != null) {
          map[entry.key] = value;
        }
      }
    }
    return map;
  }

  // maps a json object with a list of DevopsReleaseSummaryResponse-objects as value to a dart map
  static Map<String, List<DevopsReleaseSummaryResponse>> mapListFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final map = <String, List<DevopsReleaseSummaryResponse>>{};
    if (json is Map && json.isNotEmpty) {
      // ignore: parameter_assignments
      json = json.cast<String, dynamic>();
      for (final entry in json.entries) {
        map[entry.key] = DevopsReleaseSummaryResponse.listFromJson(
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
