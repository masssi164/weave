//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//
// @dart=2.18

// ignore_for_file: unused_element, unused_import
// ignore_for_file: always_put_required_named_parameters_first
// ignore_for_file: constant_identifier_names
// ignore_for_file: lines_longer_than_80_chars

part of weave_user_api;

class FileNativeProviderOptionResponse {
  /// Returns a new [FileNativeProviderOptionResponse] instance.
  FileNativeProviderOptionResponse({
    this.available,
    this.bridge,
    this.notes = const [],
    this.osBoundary,
    this.platform,
    this.requiredContracts = const [],
    this.setupAction,
    this.setupState,
  });

  /// Whether this native provider option is ready for end-user setup.
  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  bool? available;

  /// Flutter/native bridge role for setup, status, and revoke only.
  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? bridge;

  /// Support-safe notes for member/admin setup surfaces.
  List<String> notes;

  /// Native OS boundary to implement.
  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? osBoundary;

  /// Target platform family.
  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? platform;

  /// Native implementation contracts required before availability can be true.
  List<String> requiredContracts;

  /// Support-safe setup action or route. Does not expose raw provider URLs.
  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? setupAction;

  /// Support-safe setup state.
  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? setupState;

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is FileNativeProviderOptionResponse &&
          other.available == available &&
          other.bridge == bridge &&
          _deepEquality.equals(other.notes, notes) &&
          other.osBoundary == osBoundary &&
          other.platform == platform &&
          _deepEquality.equals(other.requiredContracts, requiredContracts) &&
          other.setupAction == setupAction &&
          other.setupState == setupState;

  @override
  int get hashCode =>
      // ignore: unnecessary_parenthesis
      (available == null ? 0 : available!.hashCode) +
      (bridge == null ? 0 : bridge!.hashCode) +
      (notes.hashCode) +
      (osBoundary == null ? 0 : osBoundary!.hashCode) +
      (platform == null ? 0 : platform!.hashCode) +
      (requiredContracts.hashCode) +
      (setupAction == null ? 0 : setupAction!.hashCode) +
      (setupState == null ? 0 : setupState!.hashCode);

  @override
  String toString() =>
      'FileNativeProviderOptionResponse[available=$available, bridge=$bridge, notes=$notes, osBoundary=$osBoundary, platform=$platform, requiredContracts=$requiredContracts, setupAction=$setupAction, setupState=$setupState]';

  Map<String, dynamic> toJson() {
    final json = <String, dynamic>{};
    if (this.available != null) {
      json[r'available'] = this.available;
    } else {
      json[r'available'] = null;
    }
    if (this.bridge != null) {
      json[r'bridge'] = this.bridge;
    } else {
      json[r'bridge'] = null;
    }
    json[r'notes'] = this.notes;
    if (this.osBoundary != null) {
      json[r'osBoundary'] = this.osBoundary;
    } else {
      json[r'osBoundary'] = null;
    }
    if (this.platform != null) {
      json[r'platform'] = this.platform;
    } else {
      json[r'platform'] = null;
    }
    json[r'requiredContracts'] = this.requiredContracts;
    if (this.setupAction != null) {
      json[r'setupAction'] = this.setupAction;
    } else {
      json[r'setupAction'] = null;
    }
    if (this.setupState != null) {
      json[r'setupState'] = this.setupState;
    } else {
      json[r'setupState'] = null;
    }
    return json;
  }

  /// Returns a new [FileNativeProviderOptionResponse] instance and imports its values from
  /// [value] if it's a [Map], null otherwise.
  // ignore: prefer_constructors_over_static_methods
  static FileNativeProviderOptionResponse? fromJson(dynamic value) {
    if (value is Map) {
      final json = value.cast<String, dynamic>();

      // Ensure that the map contains the required keys.
      // Note 1: the values aren't checked for validity beyond being non-null.
      // Note 2: this code is stripped in release mode!
      assert(() {
        requiredKeys.forEach((key) {
          assert(json.containsKey(key),
              'Required key "FileNativeProviderOptionResponse[$key]" is missing from JSON.');
          assert(json[key] != null,
              'Required key "FileNativeProviderOptionResponse[$key]" has a null value in JSON.');
        });
        return true;
      }());

      return FileNativeProviderOptionResponse(
        available: mapValueOfType<bool>(json, r'available'),
        bridge: mapValueOfType<String>(json, r'bridge'),
        notes: json[r'notes'] is Iterable
            ? (json[r'notes'] as Iterable)
                .cast<String>()
                .toList(growable: false)
            : const [],
        osBoundary: mapValueOfType<String>(json, r'osBoundary'),
        platform: mapValueOfType<String>(json, r'platform'),
        requiredContracts: json[r'requiredContracts'] is Iterable
            ? (json[r'requiredContracts'] as Iterable)
                .cast<String>()
                .toList(growable: false)
            : const [],
        setupAction: mapValueOfType<String>(json, r'setupAction'),
        setupState: mapValueOfType<String>(json, r'setupState'),
      );
    }
    return null;
  }

  static List<FileNativeProviderOptionResponse> listFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final result = <FileNativeProviderOptionResponse>[];
    if (json is List && json.isNotEmpty) {
      for (final row in json) {
        final value = FileNativeProviderOptionResponse.fromJson(row);
        if (value != null) {
          result.add(value);
        }
      }
    }
    return result.toList(growable: growable);
  }

  static Map<String, FileNativeProviderOptionResponse> mapFromJson(
      dynamic json) {
    final map = <String, FileNativeProviderOptionResponse>{};
    if (json is Map && json.isNotEmpty) {
      json = json.cast<String, dynamic>(); // ignore: parameter_assignments
      for (final entry in json.entries) {
        final value = FileNativeProviderOptionResponse.fromJson(entry.value);
        if (value != null) {
          map[entry.key] = value;
        }
      }
    }
    return map;
  }

  // maps a json object with a list of FileNativeProviderOptionResponse-objects as value to a dart map
  static Map<String, List<FileNativeProviderOptionResponse>> mapListFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final map = <String, List<FileNativeProviderOptionResponse>>{};
    if (json is Map && json.isNotEmpty) {
      // ignore: parameter_assignments
      json = json.cast<String, dynamic>();
      for (final entry in json.entries) {
        map[entry.key] = FileNativeProviderOptionResponse.listFromJson(
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
