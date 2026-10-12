//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//
// @dart=2.18

// ignore_for_file: unused_element, unused_import
// ignore_for_file: always_put_required_named_parameters_first
// ignore_for_file: constant_identifier_names
// ignore_for_file: lines_longer_than_80_chars

part of weave_user_api;

class DevopsPipelineSummaryResponse {
  /// Returns a new [DevopsPipelineSummaryResponse] instance.
  DevopsPipelineSummaryResponse({
    this.id,
    this.jobs = const [],
    this.providerKey,
    this.ref,
    this.repositoryId,
    this.state,
    this.updatedAt,
  });

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? id;

  List<DevopsJobSummaryResponse> jobs;

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
  String? ref;

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
  String? state;

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
      other is DevopsPipelineSummaryResponse &&
          other.id == id &&
          _deepEquality.equals(other.jobs, jobs) &&
          other.providerKey == providerKey &&
          other.ref == ref &&
          other.repositoryId == repositoryId &&
          other.state == state &&
          other.updatedAt == updatedAt;

  @override
  int get hashCode =>
      // ignore: unnecessary_parenthesis
      (id == null ? 0 : id!.hashCode) +
      (jobs.hashCode) +
      (providerKey == null ? 0 : providerKey!.hashCode) +
      (ref == null ? 0 : ref!.hashCode) +
      (repositoryId == null ? 0 : repositoryId!.hashCode) +
      (state == null ? 0 : state!.hashCode) +
      (updatedAt == null ? 0 : updatedAt!.hashCode);

  @override
  String toString() =>
      'DevopsPipelineSummaryResponse[id=$id, jobs=$jobs, providerKey=$providerKey, ref=$ref, repositoryId=$repositoryId, state=$state, updatedAt=$updatedAt]';

  Map<String, dynamic> toJson() {
    final json = <String, dynamic>{};
    if (this.id != null) {
      json[r'id'] = this.id;
    } else {
      json[r'id'] = null;
    }
    json[r'jobs'] = this.jobs;
    if (this.providerKey != null) {
      json[r'providerKey'] = this.providerKey;
    } else {
      json[r'providerKey'] = null;
    }
    if (this.ref != null) {
      json[r'ref'] = this.ref;
    } else {
      json[r'ref'] = null;
    }
    if (this.repositoryId != null) {
      json[r'repositoryId'] = this.repositoryId;
    } else {
      json[r'repositoryId'] = null;
    }
    if (this.state != null) {
      json[r'state'] = this.state;
    } else {
      json[r'state'] = null;
    }
    if (this.updatedAt != null) {
      json[r'updatedAt'] = this.updatedAt!.toUtc().toIso8601String();
    } else {
      json[r'updatedAt'] = null;
    }
    return json;
  }

  /// Returns a new [DevopsPipelineSummaryResponse] instance and imports its values from
  /// [value] if it's a [Map], null otherwise.
  // ignore: prefer_constructors_over_static_methods
  static DevopsPipelineSummaryResponse? fromJson(dynamic value) {
    if (value is Map) {
      final json = value.cast<String, dynamic>();

      // Ensure that the map contains the required keys.
      // Note 1: the values aren't checked for validity beyond being non-null.
      // Note 2: this code is stripped in release mode!
      assert(() {
        requiredKeys.forEach((key) {
          assert(json.containsKey(key),
              'Required key "DevopsPipelineSummaryResponse[$key]" is missing from JSON.');
          assert(json[key] != null,
              'Required key "DevopsPipelineSummaryResponse[$key]" has a null value in JSON.');
        });
        return true;
      }());

      return DevopsPipelineSummaryResponse(
        id: mapValueOfType<String>(json, r'id'),
        jobs: DevopsJobSummaryResponse.listFromJson(json[r'jobs']),
        providerKey: mapValueOfType<String>(json, r'providerKey'),
        ref: mapValueOfType<String>(json, r'ref'),
        repositoryId: mapValueOfType<String>(json, r'repositoryId'),
        state: mapValueOfType<String>(json, r'state'),
        updatedAt: mapDateTime(json, r'updatedAt', r''),
      );
    }
    return null;
  }

  static List<DevopsPipelineSummaryResponse> listFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final result = <DevopsPipelineSummaryResponse>[];
    if (json is List && json.isNotEmpty) {
      for (final row in json) {
        final value = DevopsPipelineSummaryResponse.fromJson(row);
        if (value != null) {
          result.add(value);
        }
      }
    }
    return result.toList(growable: growable);
  }

  static Map<String, DevopsPipelineSummaryResponse> mapFromJson(dynamic json) {
    final map = <String, DevopsPipelineSummaryResponse>{};
    if (json is Map && json.isNotEmpty) {
      json = json.cast<String, dynamic>(); // ignore: parameter_assignments
      for (final entry in json.entries) {
        final value = DevopsPipelineSummaryResponse.fromJson(entry.value);
        if (value != null) {
          map[entry.key] = value;
        }
      }
    }
    return map;
  }

  // maps a json object with a list of DevopsPipelineSummaryResponse-objects as value to a dart map
  static Map<String, List<DevopsPipelineSummaryResponse>> mapListFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final map = <String, List<DevopsPipelineSummaryResponse>>{};
    if (json is Map && json.isNotEmpty) {
      // ignore: parameter_assignments
      json = json.cast<String, dynamic>();
      for (final entry in json.entries) {
        map[entry.key] = DevopsPipelineSummaryResponse.listFromJson(
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
