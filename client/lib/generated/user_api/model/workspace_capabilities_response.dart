//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//
// @dart=2.18

// ignore_for_file: unused_element, unused_import
// ignore_for_file: always_put_required_named_parameters_first
// ignore_for_file: constant_identifier_names
// ignore_for_file: lines_longer_than_80_chars

part of weave_user_api;

class WorkspaceCapabilitiesResponse {
  /// Returns a new [WorkspaceCapabilitiesResponse] instance.
  WorkspaceCapabilitiesResponse({
    required this.adminControlPlane,
    this.domains = const {},
    required this.shellAccess,
  });

  WorkspaceCapabilityStatusResponse adminControlPlane;

  Map<String, WorkspaceCapabilityStatusResponse> domains;

  WorkspaceCapabilityStatusResponse shellAccess;

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is WorkspaceCapabilitiesResponse &&
          other.adminControlPlane == adminControlPlane &&
          _deepEquality.equals(other.domains, domains) &&
          other.shellAccess == shellAccess;

  @override
  int get hashCode =>
      // ignore: unnecessary_parenthesis
      (adminControlPlane.hashCode) +
      (domains.hashCode) +
      (shellAccess.hashCode);

  @override
  String toString() =>
      'WorkspaceCapabilitiesResponse[adminControlPlane=$adminControlPlane, domains=$domains, shellAccess=$shellAccess]';

  Map<String, dynamic> toJson() {
    final json = <String, dynamic>{};
    json[r'adminControlPlane'] = this.adminControlPlane;
    json[r'domains'] = this.domains;
    json[r'shellAccess'] = this.shellAccess;
    return json;
  }

  /// Returns a new [WorkspaceCapabilitiesResponse] instance and imports its values from
  /// [value] if it's a [Map], null otherwise.
  // ignore: prefer_constructors_over_static_methods
  static WorkspaceCapabilitiesResponse? fromJson(dynamic value) {
    if (value is Map) {
      final json = value.cast<String, dynamic>();
      final domains = json['domains'];
      if (domains is! Map ||
          domains.entries.any((entry) =>
              entry.key is! String ||
              !RegExp(r'^[a-z][a-z0-9-]*$').hasMatch(entry.key as String) ||
              entry.value is! Map)) {
        throw const FormatException('Invalid workspace installed-domain map');
      }

      // Ensure that the map contains the required keys.
      // Note 1: the values aren't checked for validity beyond being non-null.
      // Note 2: this code is stripped in release mode!
      assert(() {
        requiredKeys.forEach((key) {
          assert(json.containsKey(key),
              'Required key "WorkspaceCapabilitiesResponse[$key]" is missing from JSON.');
          assert(json[key] != null,
              'Required key "WorkspaceCapabilitiesResponse[$key]" has a null value in JSON.');
        });
        return true;
      }());

      return WorkspaceCapabilitiesResponse(
        adminControlPlane: WorkspaceCapabilityStatusResponse.fromJson(
            json[r'adminControlPlane'])!,
        domains:
            WorkspaceCapabilityStatusResponse.mapFromJson(json[r'domains']),
        shellAccess:
            WorkspaceCapabilityStatusResponse.fromJson(json[r'shellAccess'])!,
      );
    }
    return null;
  }

  static List<WorkspaceCapabilitiesResponse> listFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final result = <WorkspaceCapabilitiesResponse>[];
    if (json is List && json.isNotEmpty) {
      for (final row in json) {
        final value = WorkspaceCapabilitiesResponse.fromJson(row);
        if (value != null) {
          result.add(value);
        }
      }
    }
    return result.toList(growable: growable);
  }

  static Map<String, WorkspaceCapabilitiesResponse> mapFromJson(dynamic json) {
    final map = <String, WorkspaceCapabilitiesResponse>{};
    if (json is Map && json.isNotEmpty) {
      json = json.cast<String, dynamic>(); // ignore: parameter_assignments
      for (final entry in json.entries) {
        final value = WorkspaceCapabilitiesResponse.fromJson(entry.value);
        if (value != null) {
          map[entry.key] = value;
        }
      }
    }
    return map;
  }

  // maps a json object with a list of WorkspaceCapabilitiesResponse-objects as value to a dart map
  static Map<String, List<WorkspaceCapabilitiesResponse>> mapListFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final map = <String, List<WorkspaceCapabilitiesResponse>>{};
    if (json is Map && json.isNotEmpty) {
      // ignore: parameter_assignments
      json = json.cast<String, dynamic>();
      for (final entry in json.entries) {
        map[entry.key] = WorkspaceCapabilitiesResponse.listFromJson(
          entry.value,
          growable: growable,
        );
      }
    }
    return map;
  }

  /// The list of required keys that must be present in a JSON.
  static const requiredKeys = <String>{
    'adminControlPlane',
    'domains',
    'shellAccess',
  };
}
