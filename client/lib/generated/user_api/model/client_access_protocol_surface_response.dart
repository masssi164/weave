//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//
// @dart=2.18

// ignore_for_file: unused_element, unused_import
// ignore_for_file: always_put_required_named_parameters_first
// ignore_for_file: constant_identifier_names
// ignore_for_file: lines_longer_than_80_chars

part of weave_user_api;

class ClientAccessProtocolSurfaceResponse {
  /// Returns a new [ClientAccessProtocolSurfaceResponse] instance.
  ClientAccessProtocolSurfaceResponse({
    this.kind,
    this.name,
    this.notes = const [],
    this.readiness,
    this.setupPath,
  });

  /// Surface kind, such as openapi, standard-protocol, native-os, mcp, or provider-adapter.
  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? kind;

  /// Surface name in product-neutral terms.
  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? name;

  /// Support-safe notes for generated clients and admins.
  List<String> notes;

  /// Stable readiness posture for this surface.
  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? readiness;

  /// Weave-owned API, setup, status, or lifecycle path when one exists. Null means the surface is documented but not exposed yet.
  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? setupPath;

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is ClientAccessProtocolSurfaceResponse &&
          other.kind == kind &&
          other.name == name &&
          _deepEquality.equals(other.notes, notes) &&
          other.readiness == readiness &&
          other.setupPath == setupPath;

  @override
  int get hashCode =>
      // ignore: unnecessary_parenthesis
      (kind == null ? 0 : kind!.hashCode) +
      (name == null ? 0 : name!.hashCode) +
      (notes.hashCode) +
      (readiness == null ? 0 : readiness!.hashCode) +
      (setupPath == null ? 0 : setupPath!.hashCode);

  @override
  String toString() =>
      'ClientAccessProtocolSurfaceResponse[kind=$kind, name=$name, notes=$notes, readiness=$readiness, setupPath=$setupPath]';

  Map<String, dynamic> toJson() {
    final json = <String, dynamic>{};
    if (this.kind != null) {
      json[r'kind'] = this.kind;
    } else {
      json[r'kind'] = null;
    }
    if (this.name != null) {
      json[r'name'] = this.name;
    } else {
      json[r'name'] = null;
    }
    json[r'notes'] = this.notes;
    if (this.readiness != null) {
      json[r'readiness'] = this.readiness;
    } else {
      json[r'readiness'] = null;
    }
    if (this.setupPath != null) {
      json[r'setupPath'] = this.setupPath;
    } else {
      json[r'setupPath'] = null;
    }
    return json;
  }

  /// Returns a new [ClientAccessProtocolSurfaceResponse] instance and imports its values from
  /// [value] if it's a [Map], null otherwise.
  // ignore: prefer_constructors_over_static_methods
  static ClientAccessProtocolSurfaceResponse? fromJson(dynamic value) {
    if (value is Map) {
      final json = value.cast<String, dynamic>();

      // Ensure that the map contains the required keys.
      // Note 1: the values aren't checked for validity beyond being non-null.
      // Note 2: this code is stripped in release mode!
      assert(() {
        requiredKeys.forEach((key) {
          assert(json.containsKey(key),
              'Required key "ClientAccessProtocolSurfaceResponse[$key]" is missing from JSON.');
          assert(json[key] != null,
              'Required key "ClientAccessProtocolSurfaceResponse[$key]" has a null value in JSON.');
        });
        return true;
      }());

      return ClientAccessProtocolSurfaceResponse(
        kind: mapValueOfType<String>(json, r'kind'),
        name: mapValueOfType<String>(json, r'name'),
        notes: json[r'notes'] is Iterable
            ? (json[r'notes'] as Iterable)
                .cast<String>()
                .toList(growable: false)
            : const [],
        readiness: mapValueOfType<String>(json, r'readiness'),
        setupPath: mapValueOfType<String>(json, r'setupPath'),
      );
    }
    return null;
  }

  static List<ClientAccessProtocolSurfaceResponse> listFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final result = <ClientAccessProtocolSurfaceResponse>[];
    if (json is List && json.isNotEmpty) {
      for (final row in json) {
        final value = ClientAccessProtocolSurfaceResponse.fromJson(row);
        if (value != null) {
          result.add(value);
        }
      }
    }
    return result.toList(growable: growable);
  }

  static Map<String, ClientAccessProtocolSurfaceResponse> mapFromJson(
      dynamic json) {
    final map = <String, ClientAccessProtocolSurfaceResponse>{};
    if (json is Map && json.isNotEmpty) {
      json = json.cast<String, dynamic>(); // ignore: parameter_assignments
      for (final entry in json.entries) {
        final value = ClientAccessProtocolSurfaceResponse.fromJson(entry.value);
        if (value != null) {
          map[entry.key] = value;
        }
      }
    }
    return map;
  }

  // maps a json object with a list of ClientAccessProtocolSurfaceResponse-objects as value to a dart map
  static Map<String, List<ClientAccessProtocolSurfaceResponse>> mapListFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final map = <String, List<ClientAccessProtocolSurfaceResponse>>{};
    if (json is Map && json.isNotEmpty) {
      // ignore: parameter_assignments
      json = json.cast<String, dynamic>();
      for (final entry in json.entries) {
        map[entry.key] = ClientAccessProtocolSurfaceResponse.listFromJson(
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
