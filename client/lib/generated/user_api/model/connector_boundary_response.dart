//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//
// @dart=2.18

// ignore_for_file: unused_element, unused_import
// ignore_for_file: always_put_required_named_parameters_first
// ignore_for_file: constant_identifier_names
// ignore_for_file: lines_longer_than_80_chars

part of weave_user_api;

class ConnectorBoundaryResponse {
  /// Returns a new [ConnectorBoundaryResponse] instance.
  ConnectorBoundaryResponse({
    this.deferredUntil = const [],
    this.internalRuntimeBoundaries = const [],
    this.publicSdkEnabled,
    this.status,
  });

  List<String> deferredUntil;

  List<String> internalRuntimeBoundaries;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  bool? publicSdkEnabled;

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
      other is ConnectorBoundaryResponse &&
          _deepEquality.equals(other.deferredUntil, deferredUntil) &&
          _deepEquality.equals(
              other.internalRuntimeBoundaries, internalRuntimeBoundaries) &&
          other.publicSdkEnabled == publicSdkEnabled &&
          other.status == status;

  @override
  int get hashCode =>
      // ignore: unnecessary_parenthesis
      (deferredUntil.hashCode) +
      (internalRuntimeBoundaries.hashCode) +
      (publicSdkEnabled == null ? 0 : publicSdkEnabled!.hashCode) +
      (status == null ? 0 : status!.hashCode);

  @override
  String toString() =>
      'ConnectorBoundaryResponse[deferredUntil=$deferredUntil, internalRuntimeBoundaries=$internalRuntimeBoundaries, publicSdkEnabled=$publicSdkEnabled, status=$status]';

  Map<String, dynamic> toJson() {
    final json = <String, dynamic>{};
    json[r'deferredUntil'] = this.deferredUntil;
    json[r'internalRuntimeBoundaries'] = this.internalRuntimeBoundaries;
    if (this.publicSdkEnabled != null) {
      json[r'publicSdkEnabled'] = this.publicSdkEnabled;
    } else {
      json[r'publicSdkEnabled'] = null;
    }
    if (this.status != null) {
      json[r'status'] = this.status;
    } else {
      json[r'status'] = null;
    }
    return json;
  }

  /// Returns a new [ConnectorBoundaryResponse] instance and imports its values from
  /// [value] if it's a [Map], null otherwise.
  // ignore: prefer_constructors_over_static_methods
  static ConnectorBoundaryResponse? fromJson(dynamic value) {
    if (value is Map) {
      final json = value.cast<String, dynamic>();

      // Ensure that the map contains the required keys.
      // Note 1: the values aren't checked for validity beyond being non-null.
      // Note 2: this code is stripped in release mode!
      assert(() {
        requiredKeys.forEach((key) {
          assert(json.containsKey(key),
              'Required key "ConnectorBoundaryResponse[$key]" is missing from JSON.');
          assert(json[key] != null,
              'Required key "ConnectorBoundaryResponse[$key]" has a null value in JSON.');
        });
        return true;
      }());

      return ConnectorBoundaryResponse(
        deferredUntil: json[r'deferredUntil'] is Iterable
            ? (json[r'deferredUntil'] as Iterable)
                .cast<String>()
                .toList(growable: false)
            : const [],
        internalRuntimeBoundaries:
            json[r'internalRuntimeBoundaries'] is Iterable
                ? (json[r'internalRuntimeBoundaries'] as Iterable)
                    .cast<String>()
                    .toList(growable: false)
                : const [],
        publicSdkEnabled: mapValueOfType<bool>(json, r'publicSdkEnabled'),
        status: mapValueOfType<String>(json, r'status'),
      );
    }
    return null;
  }

  static List<ConnectorBoundaryResponse> listFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final result = <ConnectorBoundaryResponse>[];
    if (json is List && json.isNotEmpty) {
      for (final row in json) {
        final value = ConnectorBoundaryResponse.fromJson(row);
        if (value != null) {
          result.add(value);
        }
      }
    }
    return result.toList(growable: growable);
  }

  static Map<String, ConnectorBoundaryResponse> mapFromJson(dynamic json) {
    final map = <String, ConnectorBoundaryResponse>{};
    if (json is Map && json.isNotEmpty) {
      json = json.cast<String, dynamic>(); // ignore: parameter_assignments
      for (final entry in json.entries) {
        final value = ConnectorBoundaryResponse.fromJson(entry.value);
        if (value != null) {
          map[entry.key] = value;
        }
      }
    }
    return map;
  }

  // maps a json object with a list of ConnectorBoundaryResponse-objects as value to a dart map
  static Map<String, List<ConnectorBoundaryResponse>> mapListFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final map = <String, List<ConnectorBoundaryResponse>>{};
    if (json is Map && json.isNotEmpty) {
      // ignore: parameter_assignments
      json = json.cast<String, dynamic>();
      for (final entry in json.entries) {
        map[entry.key] = ConnectorBoundaryResponse.listFromJson(
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
