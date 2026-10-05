//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//
// @dart=2.18

// ignore_for_file: unused_element, unused_import
// ignore_for_file: always_put_required_named_parameters_first
// ignore_for_file: constant_identifier_names
// ignore_for_file: lines_longer_than_80_chars

part of weave_user_api;

class ClientAccessCredentialLifecycleResponse {
  /// Returns a new [ClientAccessCredentialLifecycleResponse] instance.
  ClientAccessCredentialLifecycleResponse({
    this.blockedUntil = const [],
    this.lifecyclePaths = const [],
    this.secretMaterialReturned,
    this.status,
  });

  /// Support-safe blockers before this access surface can be called ready.
  List<String> blockedUntil;

  /// Weave-owned lifecycle paths or references only; never provider URLs or raw secret refs.
  List<String> lifecyclePaths;

  /// Whether credentials or bearer tokens are returned in this manifest. Must remain false.
  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  bool? secretMaterialReturned;

  /// Stable lifecycle posture.
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
      other is ClientAccessCredentialLifecycleResponse &&
          _deepEquality.equals(other.blockedUntil, blockedUntil) &&
          _deepEquality.equals(other.lifecyclePaths, lifecyclePaths) &&
          other.secretMaterialReturned == secretMaterialReturned &&
          other.status == status;

  @override
  int get hashCode =>
      // ignore: unnecessary_parenthesis
      (blockedUntil.hashCode) +
      (lifecyclePaths.hashCode) +
      (secretMaterialReturned == null ? 0 : secretMaterialReturned!.hashCode) +
      (status == null ? 0 : status!.hashCode);

  @override
  String toString() =>
      'ClientAccessCredentialLifecycleResponse[blockedUntil=$blockedUntil, lifecyclePaths=$lifecyclePaths, secretMaterialReturned=$secretMaterialReturned, status=$status]';

  Map<String, dynamic> toJson() {
    final json = <String, dynamic>{};
    json[r'blockedUntil'] = this.blockedUntil;
    json[r'lifecyclePaths'] = this.lifecyclePaths;
    if (this.secretMaterialReturned != null) {
      json[r'secretMaterialReturned'] = this.secretMaterialReturned;
    } else {
      json[r'secretMaterialReturned'] = null;
    }
    if (this.status != null) {
      json[r'status'] = this.status;
    } else {
      json[r'status'] = null;
    }
    return json;
  }

  /// Returns a new [ClientAccessCredentialLifecycleResponse] instance and imports its values from
  /// [value] if it's a [Map], null otherwise.
  // ignore: prefer_constructors_over_static_methods
  static ClientAccessCredentialLifecycleResponse? fromJson(dynamic value) {
    if (value is Map) {
      final json = value.cast<String, dynamic>();

      // Ensure that the map contains the required keys.
      // Note 1: the values aren't checked for validity beyond being non-null.
      // Note 2: this code is stripped in release mode!
      assert(() {
        requiredKeys.forEach((key) {
          assert(json.containsKey(key),
              'Required key "ClientAccessCredentialLifecycleResponse[$key]" is missing from JSON.');
          assert(json[key] != null,
              'Required key "ClientAccessCredentialLifecycleResponse[$key]" has a null value in JSON.');
        });
        return true;
      }());

      return ClientAccessCredentialLifecycleResponse(
        blockedUntil: json[r'blockedUntil'] is Iterable
            ? (json[r'blockedUntil'] as Iterable)
                .cast<String>()
                .toList(growable: false)
            : const [],
        lifecyclePaths: json[r'lifecyclePaths'] is Iterable
            ? (json[r'lifecyclePaths'] as Iterable)
                .cast<String>()
                .toList(growable: false)
            : const [],
        secretMaterialReturned:
            mapValueOfType<bool>(json, r'secretMaterialReturned'),
        status: mapValueOfType<String>(json, r'status'),
      );
    }
    return null;
  }

  static List<ClientAccessCredentialLifecycleResponse> listFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final result = <ClientAccessCredentialLifecycleResponse>[];
    if (json is List && json.isNotEmpty) {
      for (final row in json) {
        final value = ClientAccessCredentialLifecycleResponse.fromJson(row);
        if (value != null) {
          result.add(value);
        }
      }
    }
    return result.toList(growable: growable);
  }

  static Map<String, ClientAccessCredentialLifecycleResponse> mapFromJson(
      dynamic json) {
    final map = <String, ClientAccessCredentialLifecycleResponse>{};
    if (json is Map && json.isNotEmpty) {
      json = json.cast<String, dynamic>(); // ignore: parameter_assignments
      for (final entry in json.entries) {
        final value =
            ClientAccessCredentialLifecycleResponse.fromJson(entry.value);
        if (value != null) {
          map[entry.key] = value;
        }
      }
    }
    return map;
  }

  // maps a json object with a list of ClientAccessCredentialLifecycleResponse-objects as value to a dart map
  static Map<String, List<ClientAccessCredentialLifecycleResponse>>
      mapListFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final map = <String, List<ClientAccessCredentialLifecycleResponse>>{};
    if (json is Map && json.isNotEmpty) {
      // ignore: parameter_assignments
      json = json.cast<String, dynamic>();
      for (final entry in json.entries) {
        map[entry.key] = ClientAccessCredentialLifecycleResponse.listFromJson(
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
