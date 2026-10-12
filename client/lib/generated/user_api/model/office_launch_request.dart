//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//
// @dart=2.18

// ignore_for_file: unused_element, unused_import
// ignore_for_file: always_put_required_named_parameters_first
// ignore_for_file: constant_identifier_names
// ignore_for_file: lines_longer_than_80_chars

part of weave_user_api;

class OfficeLaunchRequest {
  /// Returns a new [OfficeLaunchRequest] instance.
  OfficeLaunchRequest({
    required this.fileId,
    required this.requestedMode,
  });

  String fileId;

  String requestedMode;

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is OfficeLaunchRequest &&
          other.fileId == fileId &&
          other.requestedMode == requestedMode;

  @override
  int get hashCode =>
      // ignore: unnecessary_parenthesis
      (fileId.hashCode) + (requestedMode.hashCode);

  @override
  String toString() =>
      'OfficeLaunchRequest[fileId=$fileId, requestedMode=$requestedMode]';

  Map<String, dynamic> toJson() {
    final json = <String, dynamic>{};
    json[r'fileId'] = this.fileId;
    json[r'requestedMode'] = this.requestedMode;
    return json;
  }

  /// Returns a new [OfficeLaunchRequest] instance and imports its values from
  /// [value] if it's a [Map], null otherwise.
  // ignore: prefer_constructors_over_static_methods
  static OfficeLaunchRequest? fromJson(dynamic value) {
    if (value is Map) {
      final json = value.cast<String, dynamic>();

      // Ensure that the map contains the required keys.
      // Note 1: the values aren't checked for validity beyond being non-null.
      // Note 2: this code is stripped in release mode!
      assert(() {
        requiredKeys.forEach((key) {
          assert(json.containsKey(key),
              'Required key "OfficeLaunchRequest[$key]" is missing from JSON.');
          assert(json[key] != null,
              'Required key "OfficeLaunchRequest[$key]" has a null value in JSON.');
        });
        return true;
      }());

      return OfficeLaunchRequest(
        fileId: mapValueOfType<String>(json, r'fileId')!,
        requestedMode: mapValueOfType<String>(json, r'requestedMode')!,
      );
    }
    return null;
  }

  static List<OfficeLaunchRequest> listFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final result = <OfficeLaunchRequest>[];
    if (json is List && json.isNotEmpty) {
      for (final row in json) {
        final value = OfficeLaunchRequest.fromJson(row);
        if (value != null) {
          result.add(value);
        }
      }
    }
    return result.toList(growable: growable);
  }

  static Map<String, OfficeLaunchRequest> mapFromJson(dynamic json) {
    final map = <String, OfficeLaunchRequest>{};
    if (json is Map && json.isNotEmpty) {
      json = json.cast<String, dynamic>(); // ignore: parameter_assignments
      for (final entry in json.entries) {
        final value = OfficeLaunchRequest.fromJson(entry.value);
        if (value != null) {
          map[entry.key] = value;
        }
      }
    }
    return map;
  }

  // maps a json object with a list of OfficeLaunchRequest-objects as value to a dart map
  static Map<String, List<OfficeLaunchRequest>> mapListFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final map = <String, List<OfficeLaunchRequest>>{};
    if (json is Map && json.isNotEmpty) {
      // ignore: parameter_assignments
      json = json.cast<String, dynamic>();
      for (final entry in json.entries) {
        map[entry.key] = OfficeLaunchRequest.listFromJson(
          entry.value,
          growable: growable,
        );
      }
    }
    return map;
  }

  /// The list of required keys that must be present in a JSON.
  static const requiredKeys = <String>{
    'fileId',
    'requestedMode',
  };
}
