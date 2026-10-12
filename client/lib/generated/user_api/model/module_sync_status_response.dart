//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//
// @dart=2.18

// ignore_for_file: unused_element, unused_import
// ignore_for_file: always_put_required_named_parameters_first
// ignore_for_file: constant_identifier_names
// ignore_for_file: lines_longer_than_80_chars

part of weave_user_api;

class ModuleSyncStatusResponse {
  /// Returns a new [ModuleSyncStatusResponse] instance.
  ModuleSyncStatusResponse({
    this.matrix,
    this.nextcloud,
  });

  /// Matrix profile sync status.
  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? matrix;

  /// Nextcloud profile sync status.
  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? nextcloud;

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is ModuleSyncStatusResponse &&
          other.matrix == matrix &&
          other.nextcloud == nextcloud;

  @override
  int get hashCode =>
      // ignore: unnecessary_parenthesis
      (matrix == null ? 0 : matrix!.hashCode) +
      (nextcloud == null ? 0 : nextcloud!.hashCode);

  @override
  String toString() =>
      'ModuleSyncStatusResponse[matrix=$matrix, nextcloud=$nextcloud]';

  Map<String, dynamic> toJson() {
    final json = <String, dynamic>{};
    if (this.matrix != null) {
      json[r'matrix'] = this.matrix;
    } else {
      json[r'matrix'] = null;
    }
    if (this.nextcloud != null) {
      json[r'nextcloud'] = this.nextcloud;
    } else {
      json[r'nextcloud'] = null;
    }
    return json;
  }

  /// Returns a new [ModuleSyncStatusResponse] instance and imports its values from
  /// [value] if it's a [Map], null otherwise.
  // ignore: prefer_constructors_over_static_methods
  static ModuleSyncStatusResponse? fromJson(dynamic value) {
    if (value is Map) {
      final json = value.cast<String, dynamic>();

      // Ensure that the map contains the required keys.
      // Note 1: the values aren't checked for validity beyond being non-null.
      // Note 2: this code is stripped in release mode!
      assert(() {
        requiredKeys.forEach((key) {
          assert(json.containsKey(key),
              'Required key "ModuleSyncStatusResponse[$key]" is missing from JSON.');
          assert(json[key] != null,
              'Required key "ModuleSyncStatusResponse[$key]" has a null value in JSON.');
        });
        return true;
      }());

      return ModuleSyncStatusResponse(
        matrix: mapValueOfType<String>(json, r'matrix'),
        nextcloud: mapValueOfType<String>(json, r'nextcloud'),
      );
    }
    return null;
  }

  static List<ModuleSyncStatusResponse> listFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final result = <ModuleSyncStatusResponse>[];
    if (json is List && json.isNotEmpty) {
      for (final row in json) {
        final value = ModuleSyncStatusResponse.fromJson(row);
        if (value != null) {
          result.add(value);
        }
      }
    }
    return result.toList(growable: growable);
  }

  static Map<String, ModuleSyncStatusResponse> mapFromJson(dynamic json) {
    final map = <String, ModuleSyncStatusResponse>{};
    if (json is Map && json.isNotEmpty) {
      json = json.cast<String, dynamic>(); // ignore: parameter_assignments
      for (final entry in json.entries) {
        final value = ModuleSyncStatusResponse.fromJson(entry.value);
        if (value != null) {
          map[entry.key] = value;
        }
      }
    }
    return map;
  }

  // maps a json object with a list of ModuleSyncStatusResponse-objects as value to a dart map
  static Map<String, List<ModuleSyncStatusResponse>> mapListFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final map = <String, List<ModuleSyncStatusResponse>>{};
    if (json is Map && json.isNotEmpty) {
      // ignore: parameter_assignments
      json = json.cast<String, dynamic>();
      for (final entry in json.entries) {
        map[entry.key] = ModuleSyncStatusResponse.listFromJson(
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
