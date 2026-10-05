//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//
// @dart=2.18

// ignore_for_file: unused_element, unused_import
// ignore_for_file: always_put_required_named_parameters_first
// ignore_for_file: constant_identifier_names
// ignore_for_file: lines_longer_than_80_chars

part of weave_user_api;

class OfficeProviderCandidateResponse {
  /// Returns a new [OfficeProviderCandidateResponse] instance.
  OfficeProviderCandidateResponse({
    this.defaultCandidate,
    this.displayName,
    this.integrationPath,
    this.licensingPosture,
    this.notes = const [],
    this.providerKey,
    this.runtimeFit,
  });

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  bool? defaultCandidate;

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
  String? integrationPath;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? licensingPosture;

  List<String> notes;

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
  String? runtimeFit;

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is OfficeProviderCandidateResponse &&
          other.defaultCandidate == defaultCandidate &&
          other.displayName == displayName &&
          other.integrationPath == integrationPath &&
          other.licensingPosture == licensingPosture &&
          _deepEquality.equals(other.notes, notes) &&
          other.providerKey == providerKey &&
          other.runtimeFit == runtimeFit;

  @override
  int get hashCode =>
      // ignore: unnecessary_parenthesis
      (defaultCandidate == null ? 0 : defaultCandidate!.hashCode) +
      (displayName == null ? 0 : displayName!.hashCode) +
      (integrationPath == null ? 0 : integrationPath!.hashCode) +
      (licensingPosture == null ? 0 : licensingPosture!.hashCode) +
      (notes.hashCode) +
      (providerKey == null ? 0 : providerKey!.hashCode) +
      (runtimeFit == null ? 0 : runtimeFit!.hashCode);

  @override
  String toString() =>
      'OfficeProviderCandidateResponse[defaultCandidate=$defaultCandidate, displayName=$displayName, integrationPath=$integrationPath, licensingPosture=$licensingPosture, notes=$notes, providerKey=$providerKey, runtimeFit=$runtimeFit]';

  Map<String, dynamic> toJson() {
    final json = <String, dynamic>{};
    if (this.defaultCandidate != null) {
      json[r'defaultCandidate'] = this.defaultCandidate;
    } else {
      json[r'defaultCandidate'] = null;
    }
    if (this.displayName != null) {
      json[r'displayName'] = this.displayName;
    } else {
      json[r'displayName'] = null;
    }
    if (this.integrationPath != null) {
      json[r'integrationPath'] = this.integrationPath;
    } else {
      json[r'integrationPath'] = null;
    }
    if (this.licensingPosture != null) {
      json[r'licensingPosture'] = this.licensingPosture;
    } else {
      json[r'licensingPosture'] = null;
    }
    json[r'notes'] = this.notes;
    if (this.providerKey != null) {
      json[r'providerKey'] = this.providerKey;
    } else {
      json[r'providerKey'] = null;
    }
    if (this.runtimeFit != null) {
      json[r'runtimeFit'] = this.runtimeFit;
    } else {
      json[r'runtimeFit'] = null;
    }
    return json;
  }

  /// Returns a new [OfficeProviderCandidateResponse] instance and imports its values from
  /// [value] if it's a [Map], null otherwise.
  // ignore: prefer_constructors_over_static_methods
  static OfficeProviderCandidateResponse? fromJson(dynamic value) {
    if (value is Map) {
      final json = value.cast<String, dynamic>();

      // Ensure that the map contains the required keys.
      // Note 1: the values aren't checked for validity beyond being non-null.
      // Note 2: this code is stripped in release mode!
      assert(() {
        requiredKeys.forEach((key) {
          assert(json.containsKey(key),
              'Required key "OfficeProviderCandidateResponse[$key]" is missing from JSON.');
          assert(json[key] != null,
              'Required key "OfficeProviderCandidateResponse[$key]" has a null value in JSON.');
        });
        return true;
      }());

      return OfficeProviderCandidateResponse(
        defaultCandidate: mapValueOfType<bool>(json, r'defaultCandidate'),
        displayName: mapValueOfType<String>(json, r'displayName'),
        integrationPath: mapValueOfType<String>(json, r'integrationPath'),
        licensingPosture: mapValueOfType<String>(json, r'licensingPosture'),
        notes: json[r'notes'] is Iterable
            ? (json[r'notes'] as Iterable)
                .cast<String>()
                .toList(growable: false)
            : const [],
        providerKey: mapValueOfType<String>(json, r'providerKey'),
        runtimeFit: mapValueOfType<String>(json, r'runtimeFit'),
      );
    }
    return null;
  }

  static List<OfficeProviderCandidateResponse> listFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final result = <OfficeProviderCandidateResponse>[];
    if (json is List && json.isNotEmpty) {
      for (final row in json) {
        final value = OfficeProviderCandidateResponse.fromJson(row);
        if (value != null) {
          result.add(value);
        }
      }
    }
    return result.toList(growable: growable);
  }

  static Map<String, OfficeProviderCandidateResponse> mapFromJson(
      dynamic json) {
    final map = <String, OfficeProviderCandidateResponse>{};
    if (json is Map && json.isNotEmpty) {
      json = json.cast<String, dynamic>(); // ignore: parameter_assignments
      for (final entry in json.entries) {
        final value = OfficeProviderCandidateResponse.fromJson(entry.value);
        if (value != null) {
          map[entry.key] = value;
        }
      }
    }
    return map;
  }

  // maps a json object with a list of OfficeProviderCandidateResponse-objects as value to a dart map
  static Map<String, List<OfficeProviderCandidateResponse>> mapListFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final map = <String, List<OfficeProviderCandidateResponse>>{};
    if (json is Map && json.isNotEmpty) {
      // ignore: parameter_assignments
      json = json.cast<String, dynamic>();
      for (final entry in json.entries) {
        map[entry.key] = OfficeProviderCandidateResponse.listFromJson(
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
