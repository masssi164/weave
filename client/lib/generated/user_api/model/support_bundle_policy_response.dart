//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//
// @dart=2.18

// ignore_for_file: unused_element, unused_import
// ignore_for_file: always_put_required_named_parameters_first
// ignore_for_file: constant_identifier_names
// ignore_for_file: lines_longer_than_80_chars

part of weave_user_api;

class SupportBundlePolicyResponse {
  /// Returns a new [SupportBundlePolicyResponse] instance.
  SupportBundlePolicyResponse({
    this.providerSecretsRedacted,
    this.redactedFields = const [],
    this.redactionMode,
  });

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  bool? providerSecretsRedacted;

  List<String> redactedFields;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? redactionMode;

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is SupportBundlePolicyResponse &&
          other.providerSecretsRedacted == providerSecretsRedacted &&
          _deepEquality.equals(other.redactedFields, redactedFields) &&
          other.redactionMode == redactionMode;

  @override
  int get hashCode =>
      // ignore: unnecessary_parenthesis
      (providerSecretsRedacted == null
          ? 0
          : providerSecretsRedacted!.hashCode) +
      (redactedFields.hashCode) +
      (redactionMode == null ? 0 : redactionMode!.hashCode);

  @override
  String toString() =>
      'SupportBundlePolicyResponse[providerSecretsRedacted=$providerSecretsRedacted, redactedFields=$redactedFields, redactionMode=$redactionMode]';

  Map<String, dynamic> toJson() {
    final json = <String, dynamic>{};
    if (this.providerSecretsRedacted != null) {
      json[r'providerSecretsRedacted'] = this.providerSecretsRedacted;
    } else {
      json[r'providerSecretsRedacted'] = null;
    }
    json[r'redactedFields'] = this.redactedFields;
    if (this.redactionMode != null) {
      json[r'redactionMode'] = this.redactionMode;
    } else {
      json[r'redactionMode'] = null;
    }
    return json;
  }

  /// Returns a new [SupportBundlePolicyResponse] instance and imports its values from
  /// [value] if it's a [Map], null otherwise.
  // ignore: prefer_constructors_over_static_methods
  static SupportBundlePolicyResponse? fromJson(dynamic value) {
    if (value is Map) {
      final json = value.cast<String, dynamic>();

      // Ensure that the map contains the required keys.
      // Note 1: the values aren't checked for validity beyond being non-null.
      // Note 2: this code is stripped in release mode!
      assert(() {
        requiredKeys.forEach((key) {
          assert(json.containsKey(key),
              'Required key "SupportBundlePolicyResponse[$key]" is missing from JSON.');
          assert(json[key] != null,
              'Required key "SupportBundlePolicyResponse[$key]" has a null value in JSON.');
        });
        return true;
      }());

      return SupportBundlePolicyResponse(
        providerSecretsRedacted:
            mapValueOfType<bool>(json, r'providerSecretsRedacted'),
        redactedFields: json[r'redactedFields'] is Iterable
            ? (json[r'redactedFields'] as Iterable)
                .cast<String>()
                .toList(growable: false)
            : const [],
        redactionMode: mapValueOfType<String>(json, r'redactionMode'),
      );
    }
    return null;
  }

  static List<SupportBundlePolicyResponse> listFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final result = <SupportBundlePolicyResponse>[];
    if (json is List && json.isNotEmpty) {
      for (final row in json) {
        final value = SupportBundlePolicyResponse.fromJson(row);
        if (value != null) {
          result.add(value);
        }
      }
    }
    return result.toList(growable: growable);
  }

  static Map<String, SupportBundlePolicyResponse> mapFromJson(dynamic json) {
    final map = <String, SupportBundlePolicyResponse>{};
    if (json is Map && json.isNotEmpty) {
      json = json.cast<String, dynamic>(); // ignore: parameter_assignments
      for (final entry in json.entries) {
        final value = SupportBundlePolicyResponse.fromJson(entry.value);
        if (value != null) {
          map[entry.key] = value;
        }
      }
    }
    return map;
  }

  // maps a json object with a list of SupportBundlePolicyResponse-objects as value to a dart map
  static Map<String, List<SupportBundlePolicyResponse>> mapListFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final map = <String, List<SupportBundlePolicyResponse>>{};
    if (json is Map && json.isNotEmpty) {
      // ignore: parameter_assignments
      json = json.cast<String, dynamic>();
      for (final entry in json.entries) {
        map[entry.key] = SupportBundlePolicyResponse.listFromJson(
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
