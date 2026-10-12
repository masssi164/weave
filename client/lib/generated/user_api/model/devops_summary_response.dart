//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//
// @dart=2.18

// ignore_for_file: unused_element, unused_import
// ignore_for_file: always_put_required_named_parameters_first
// ignore_for_file: constant_identifier_names
// ignore_for_file: lines_longer_than_80_chars

part of weave_user_api;

class DevopsSummaryResponse {
  /// Returns a new [DevopsSummaryResponse] instance.
  DevopsSummaryResponse({
    this.channelId,
    this.linkedProjects = const [],
    this.mergeRequests = const [],
    this.openIssues = const [],
    this.paidFeaturesRequired,
    this.pipelines = const [],
    this.providerReadiness = const [],
    this.readOnly,
    this.releaseStatus,
    this.releases = const [],
    this.repositories = const [],
    this.supportSafe,
    this.workspaceId,
  });

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? channelId;

  List<LinkedSourceProjectResponse> linkedProjects;

  List<DevopsMergeRequestSummaryResponse> mergeRequests;

  List<DevopsIssueSummaryResponse> openIssues;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  bool? paidFeaturesRequired;

  List<DevopsPipelineSummaryResponse> pipelines;

  List<ProviderStatusResponse> providerReadiness;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  bool? readOnly;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? releaseStatus;

  List<DevopsReleaseSummaryResponse> releases;

  List<SourceRepositoryResponse> repositories;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  bool? supportSafe;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? workspaceId;

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is DevopsSummaryResponse &&
          other.channelId == channelId &&
          _deepEquality.equals(other.linkedProjects, linkedProjects) &&
          _deepEquality.equals(other.mergeRequests, mergeRequests) &&
          _deepEquality.equals(other.openIssues, openIssues) &&
          other.paidFeaturesRequired == paidFeaturesRequired &&
          _deepEquality.equals(other.pipelines, pipelines) &&
          _deepEquality.equals(other.providerReadiness, providerReadiness) &&
          other.readOnly == readOnly &&
          other.releaseStatus == releaseStatus &&
          _deepEquality.equals(other.releases, releases) &&
          _deepEquality.equals(other.repositories, repositories) &&
          other.supportSafe == supportSafe &&
          other.workspaceId == workspaceId;

  @override
  int get hashCode =>
      // ignore: unnecessary_parenthesis
      (channelId == null ? 0 : channelId!.hashCode) +
      (linkedProjects.hashCode) +
      (mergeRequests.hashCode) +
      (openIssues.hashCode) +
      (paidFeaturesRequired == null ? 0 : paidFeaturesRequired!.hashCode) +
      (pipelines.hashCode) +
      (providerReadiness.hashCode) +
      (readOnly == null ? 0 : readOnly!.hashCode) +
      (releaseStatus == null ? 0 : releaseStatus!.hashCode) +
      (releases.hashCode) +
      (repositories.hashCode) +
      (supportSafe == null ? 0 : supportSafe!.hashCode) +
      (workspaceId == null ? 0 : workspaceId!.hashCode);

  @override
  String toString() =>
      'DevopsSummaryResponse[channelId=$channelId, linkedProjects=$linkedProjects, mergeRequests=$mergeRequests, openIssues=$openIssues, paidFeaturesRequired=$paidFeaturesRequired, pipelines=$pipelines, providerReadiness=$providerReadiness, readOnly=$readOnly, releaseStatus=$releaseStatus, releases=$releases, repositories=$repositories, supportSafe=$supportSafe, workspaceId=$workspaceId]';

  Map<String, dynamic> toJson() {
    final json = <String, dynamic>{};
    if (this.channelId != null) {
      json[r'channelId'] = this.channelId;
    } else {
      json[r'channelId'] = null;
    }
    json[r'linkedProjects'] = this.linkedProjects;
    json[r'mergeRequests'] = this.mergeRequests;
    json[r'openIssues'] = this.openIssues;
    if (this.paidFeaturesRequired != null) {
      json[r'paidFeaturesRequired'] = this.paidFeaturesRequired;
    } else {
      json[r'paidFeaturesRequired'] = null;
    }
    json[r'pipelines'] = this.pipelines;
    json[r'providerReadiness'] = this.providerReadiness;
    if (this.readOnly != null) {
      json[r'readOnly'] = this.readOnly;
    } else {
      json[r'readOnly'] = null;
    }
    if (this.releaseStatus != null) {
      json[r'releaseStatus'] = this.releaseStatus;
    } else {
      json[r'releaseStatus'] = null;
    }
    json[r'releases'] = this.releases;
    json[r'repositories'] = this.repositories;
    if (this.supportSafe != null) {
      json[r'supportSafe'] = this.supportSafe;
    } else {
      json[r'supportSafe'] = null;
    }
    if (this.workspaceId != null) {
      json[r'workspaceId'] = this.workspaceId;
    } else {
      json[r'workspaceId'] = null;
    }
    return json;
  }

  /// Returns a new [DevopsSummaryResponse] instance and imports its values from
  /// [value] if it's a [Map], null otherwise.
  // ignore: prefer_constructors_over_static_methods
  static DevopsSummaryResponse? fromJson(dynamic value) {
    if (value is Map) {
      final json = value.cast<String, dynamic>();

      // Ensure that the map contains the required keys.
      // Note 1: the values aren't checked for validity beyond being non-null.
      // Note 2: this code is stripped in release mode!
      assert(() {
        requiredKeys.forEach((key) {
          assert(json.containsKey(key),
              'Required key "DevopsSummaryResponse[$key]" is missing from JSON.');
          assert(json[key] != null,
              'Required key "DevopsSummaryResponse[$key]" has a null value in JSON.');
        });
        return true;
      }());

      return DevopsSummaryResponse(
        channelId: mapValueOfType<String>(json, r'channelId'),
        linkedProjects:
            LinkedSourceProjectResponse.listFromJson(json[r'linkedProjects']),
        mergeRequests: DevopsMergeRequestSummaryResponse.listFromJson(
            json[r'mergeRequests']),
        openIssues:
            DevopsIssueSummaryResponse.listFromJson(json[r'openIssues']),
        paidFeaturesRequired:
            mapValueOfType<bool>(json, r'paidFeaturesRequired'),
        pipelines:
            DevopsPipelineSummaryResponse.listFromJson(json[r'pipelines']),
        providerReadiness:
            ProviderStatusResponse.listFromJson(json[r'providerReadiness']),
        readOnly: mapValueOfType<bool>(json, r'readOnly'),
        releaseStatus: mapValueOfType<String>(json, r'releaseStatus'),
        releases: DevopsReleaseSummaryResponse.listFromJson(json[r'releases']),
        repositories:
            SourceRepositoryResponse.listFromJson(json[r'repositories']),
        supportSafe: mapValueOfType<bool>(json, r'supportSafe'),
        workspaceId: mapValueOfType<String>(json, r'workspaceId'),
      );
    }
    return null;
  }

  static List<DevopsSummaryResponse> listFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final result = <DevopsSummaryResponse>[];
    if (json is List && json.isNotEmpty) {
      for (final row in json) {
        final value = DevopsSummaryResponse.fromJson(row);
        if (value != null) {
          result.add(value);
        }
      }
    }
    return result.toList(growable: growable);
  }

  static Map<String, DevopsSummaryResponse> mapFromJson(dynamic json) {
    final map = <String, DevopsSummaryResponse>{};
    if (json is Map && json.isNotEmpty) {
      json = json.cast<String, dynamic>(); // ignore: parameter_assignments
      for (final entry in json.entries) {
        final value = DevopsSummaryResponse.fromJson(entry.value);
        if (value != null) {
          map[entry.key] = value;
        }
      }
    }
    return map;
  }

  // maps a json object with a list of DevopsSummaryResponse-objects as value to a dart map
  static Map<String, List<DevopsSummaryResponse>> mapListFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final map = <String, List<DevopsSummaryResponse>>{};
    if (json is Map && json.isNotEmpty) {
      // ignore: parameter_assignments
      json = json.cast<String, dynamic>();
      for (final entry in json.entries) {
        map[entry.key] = DevopsSummaryResponse.listFromJson(
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
