//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//
// @dart=2.18

// ignore_for_file: unused_element, unused_import
// ignore_for_file: always_put_required_named_parameters_first
// ignore_for_file: constant_identifier_names
// ignore_for_file: lines_longer_than_80_chars

part of weave_user_api;

class DevopsIssueSummaryResponse {
  /// Returns a new [DevopsIssueSummaryResponse] instance.
  DevopsIssueSummaryResponse({
    this.id,
    this.labels = const [],
    this.projectId,
    this.providerKey,
    this.state,
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

  List<String> labels;

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
      other is DevopsIssueSummaryResponse &&
          other.id == id &&
          _deepEquality.equals(other.labels, labels) &&
          other.projectId == projectId &&
          other.providerKey == providerKey &&
          other.state == state &&
          other.title == title &&
          other.updatedAt == updatedAt;

  @override
  int get hashCode =>
      // ignore: unnecessary_parenthesis
      (id == null ? 0 : id!.hashCode) +
      (labels.hashCode) +
      (projectId == null ? 0 : projectId!.hashCode) +
      (providerKey == null ? 0 : providerKey!.hashCode) +
      (state == null ? 0 : state!.hashCode) +
      (title == null ? 0 : title!.hashCode) +
      (updatedAt == null ? 0 : updatedAt!.hashCode);

  @override
  String toString() =>
      'DevopsIssueSummaryResponse[id=$id, labels=$labels, projectId=$projectId, providerKey=$providerKey, state=$state, title=$title, updatedAt=$updatedAt]';

  Map<String, dynamic> toJson() {
    final json = <String, dynamic>{};
    if (this.id != null) {
      json[r'id'] = this.id;
    } else {
      json[r'id'] = null;
    }
    json[r'labels'] = this.labels;
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
    if (this.state != null) {
      json[r'state'] = this.state;
    } else {
      json[r'state'] = null;
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

  /// Returns a new [DevopsIssueSummaryResponse] instance and imports its values from
  /// [value] if it's a [Map], null otherwise.
  // ignore: prefer_constructors_over_static_methods
  static DevopsIssueSummaryResponse? fromJson(dynamic value) {
    if (value is Map) {
      final json = value.cast<String, dynamic>();

      // Ensure that the map contains the required keys.
      // Note 1: the values aren't checked for validity beyond being non-null.
      // Note 2: this code is stripped in release mode!
      assert(() {
        requiredKeys.forEach((key) {
          assert(json.containsKey(key),
              'Required key "DevopsIssueSummaryResponse[$key]" is missing from JSON.');
          assert(json[key] != null,
              'Required key "DevopsIssueSummaryResponse[$key]" has a null value in JSON.');
        });
        return true;
      }());

      return DevopsIssueSummaryResponse(
        id: mapValueOfType<String>(json, r'id'),
        labels: json[r'labels'] is Iterable
            ? (json[r'labels'] as Iterable)
                .cast<String>()
                .toList(growable: false)
            : const [],
        projectId: mapValueOfType<String>(json, r'projectId'),
        providerKey: mapValueOfType<String>(json, r'providerKey'),
        state: mapValueOfType<String>(json, r'state'),
        title: mapValueOfType<String>(json, r'title'),
        updatedAt: mapDateTime(json, r'updatedAt', r''),
      );
    }
    return null;
  }

  static List<DevopsIssueSummaryResponse> listFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final result = <DevopsIssueSummaryResponse>[];
    if (json is List && json.isNotEmpty) {
      for (final row in json) {
        final value = DevopsIssueSummaryResponse.fromJson(row);
        if (value != null) {
          result.add(value);
        }
      }
    }
    return result.toList(growable: growable);
  }

  static Map<String, DevopsIssueSummaryResponse> mapFromJson(dynamic json) {
    final map = <String, DevopsIssueSummaryResponse>{};
    if (json is Map && json.isNotEmpty) {
      json = json.cast<String, dynamic>(); // ignore: parameter_assignments
      for (final entry in json.entries) {
        final value = DevopsIssueSummaryResponse.fromJson(entry.value);
        if (value != null) {
          map[entry.key] = value;
        }
      }
    }
    return map;
  }

  // maps a json object with a list of DevopsIssueSummaryResponse-objects as value to a dart map
  static Map<String, List<DevopsIssueSummaryResponse>> mapListFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final map = <String, List<DevopsIssueSummaryResponse>>{};
    if (json is Map && json.isNotEmpty) {
      // ignore: parameter_assignments
      json = json.cast<String, dynamic>();
      for (final entry in json.entries) {
        map[entry.key] = DevopsIssueSummaryResponse.listFromJson(
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
