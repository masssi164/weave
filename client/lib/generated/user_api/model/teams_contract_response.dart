//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//
// @dart=2.18

// ignore_for_file: unused_element, unused_import
// ignore_for_file: always_put_required_named_parameters_first
// ignore_for_file: constant_identifier_names
// ignore_for_file: lines_longer_than_80_chars

part of weave_user_api;

class TeamsContractResponse {
  /// Returns a new [TeamsContractResponse] instance.
  TeamsContractResponse({
    this.degradedStates = const [],
    this.enabled,
    this.gatedBehindSlackHardening,
    this.requiredBeforeImplementation = const [],
    this.status,
  });

  List<String> degradedStates;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  bool? enabled;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  bool? gatedBehindSlackHardening;

  List<String> requiredBeforeImplementation;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? status;

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is TeamsContractResponse &&
          _deepEquality.equals(other.degradedStates, degradedStates) &&
          other.enabled == enabled &&
          other.gatedBehindSlackHardening == gatedBehindSlackHardening &&
          _deepEquality.equals(other.requiredBeforeImplementation,
              requiredBeforeImplementation) &&
          other.status == status;

  @override
  int get hashCode =>
      // ignore: unnecessary_parenthesis
      (degradedStates.hashCode) +
      (enabled == null ? 0 : enabled!.hashCode) +
      (gatedBehindSlackHardening == null
          ? 0
          : gatedBehindSlackHardening!.hashCode) +
      (requiredBeforeImplementation.hashCode) +
      (status == null ? 0 : status!.hashCode);

  @override
  String toString() =>
      'TeamsContractResponse[degradedStates=$degradedStates, enabled=$enabled, gatedBehindSlackHardening=$gatedBehindSlackHardening, requiredBeforeImplementation=$requiredBeforeImplementation, status=$status]';

  Map<String, dynamic> toJson() {
    final json = <String, dynamic>{};
    json[r'degradedStates'] = this.degradedStates;
    if (this.enabled != null) {
      json[r'enabled'] = this.enabled;
    } else {
      json[r'enabled'] = null;
    }
    if (this.gatedBehindSlackHardening != null) {
      json[r'gatedBehindSlackHardening'] = this.gatedBehindSlackHardening;
    } else {
      json[r'gatedBehindSlackHardening'] = null;
    }
    json[r'requiredBeforeImplementation'] = this.requiredBeforeImplementation;
    if (this.status != null) {
      json[r'status'] = this.status;
    } else {
      json[r'status'] = null;
    }
    return json;
  }

  /// Returns a new [TeamsContractResponse] instance and imports its values from
  /// [value] if it's a [Map], null otherwise.
  // ignore: prefer_constructors_over_static_methods
  static TeamsContractResponse? fromJson(dynamic value) {
    if (value is Map) {
      final json = value.cast<String, dynamic>();

      // Ensure that the map contains the required keys.
      // Note 1: the values aren't checked for validity beyond being non-null.
      // Note 2: this code is stripped in release mode!
      assert(() {
        requiredKeys.forEach((key) {
          assert(json.containsKey(key),
              'Required key "TeamsContractResponse[$key]" is missing from JSON.');
          assert(json[key] != null,
              'Required key "TeamsContractResponse[$key]" has a null value in JSON.');
        });
        return true;
      }());

      return TeamsContractResponse(
        degradedStates: json[r'degradedStates'] is Iterable
            ? (json[r'degradedStates'] as Iterable)
                .cast<String>()
                .toList(growable: false)
            : const [],
        enabled: mapValueOfType<bool>(json, r'enabled'),
        gatedBehindSlackHardening:
            mapValueOfType<bool>(json, r'gatedBehindSlackHardening'),
        requiredBeforeImplementation:
            json[r'requiredBeforeImplementation'] is Iterable
                ? (json[r'requiredBeforeImplementation'] as Iterable)
                    .cast<String>()
                    .toList(growable: false)
                : const [],
        status: mapValueOfType<String>(json, r'status'),
      );
    }
    return null;
  }

  static List<TeamsContractResponse> listFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final result = <TeamsContractResponse>[];
    if (json is List && json.isNotEmpty) {
      for (final row in json) {
        final value = TeamsContractResponse.fromJson(row);
        if (value != null) {
          result.add(value);
        }
      }
    }
    return result.toList(growable: growable);
  }

  static Map<String, TeamsContractResponse> mapFromJson(dynamic json) {
    final map = <String, TeamsContractResponse>{};
    if (json is Map && json.isNotEmpty) {
      json = json.cast<String, dynamic>(); // ignore: parameter_assignments
      for (final entry in json.entries) {
        final value = TeamsContractResponse.fromJson(entry.value);
        if (value != null) {
          map[entry.key] = value;
        }
      }
    }
    return map;
  }

  // maps a json object with a list of TeamsContractResponse-objects as value to a dart map
  static Map<String, List<TeamsContractResponse>> mapListFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final map = <String, List<TeamsContractResponse>>{};
    if (json is Map && json.isNotEmpty) {
      // ignore: parameter_assignments
      json = json.cast<String, dynamic>();
      for (final entry in json.entries) {
        map[entry.key] = TeamsContractResponse.listFromJson(
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
