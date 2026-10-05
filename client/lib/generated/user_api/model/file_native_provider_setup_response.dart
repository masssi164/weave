//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//
// @dart=2.18

// ignore_for_file: unused_element, unused_import
// ignore_for_file: always_put_required_named_parameters_first
// ignore_for_file: constant_identifier_names
// ignore_for_file: lines_longer_than_80_chars

part of weave_user_api;

class FileNativeProviderSetupResponse {
  /// Returns a new [FileNativeProviderSetupResponse] instance.
  FileNativeProviderSetupResponse({
    this.blockedUntil = const [],
    this.credentialLifecyclePath,
    this.credentialsExposed,
    this.downloadPathTemplate,
    this.facadeBasePath,
    this.listPathTemplate,
    this.options = const [],
    this.proofHooks = const [],
    this.providerConfigurationExposed,
    this.readiness,
    this.supportSafe,
    this.uploadPath,
  });

  /// Support-safe blockers before native provider availability can be true.
  List<String> blockedUntil;

  /// Weave-owned credential lifecycle path for native and generic WebDAV clients.
  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? credentialLifecyclePath;

  /// False: this contract never returns provider credentials or bearer tokens.
  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  bool? credentialsExposed;

  /// Weave-owned WebDAV download path template for native providers.
  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? downloadPathTemplate;

  /// Weave-owned WebDAV facade base path for native providers.
  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? facadeBasePath;

  /// Weave-owned WebDAV list path template for native providers.
  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? listPathTemplate;

  /// OS-specific provider setup options.
  List<FileNativeProviderOptionResponse> options;

  /// Executable proof hooks that can be exercised before full OS extension availability.
  List<String> proofHooks;

  /// False: member/native setup must not receive raw storage-provider configuration.
  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  bool? providerConfigurationExposed;

  /// Files capability readiness as seen by the authenticated member.
  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  WorkspaceCapabilityStatusResponse? readiness;

  /// True when this setup contract excludes raw provider endpoints, credentials, and diagnostics.
  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  bool? supportSafe;

  /// Weave-owned WebDAV upload path for native providers.
  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? uploadPath;

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is FileNativeProviderSetupResponse &&
          _deepEquality.equals(other.blockedUntil, blockedUntil) &&
          other.credentialLifecyclePath == credentialLifecyclePath &&
          other.credentialsExposed == credentialsExposed &&
          other.downloadPathTemplate == downloadPathTemplate &&
          other.facadeBasePath == facadeBasePath &&
          other.listPathTemplate == listPathTemplate &&
          _deepEquality.equals(other.options, options) &&
          _deepEquality.equals(other.proofHooks, proofHooks) &&
          other.providerConfigurationExposed == providerConfigurationExposed &&
          other.readiness == readiness &&
          other.supportSafe == supportSafe &&
          other.uploadPath == uploadPath;

  @override
  int get hashCode =>
      // ignore: unnecessary_parenthesis
      (blockedUntil.hashCode) +
      (credentialLifecyclePath == null
          ? 0
          : credentialLifecyclePath!.hashCode) +
      (credentialsExposed == null ? 0 : credentialsExposed!.hashCode) +
      (downloadPathTemplate == null ? 0 : downloadPathTemplate!.hashCode) +
      (facadeBasePath == null ? 0 : facadeBasePath!.hashCode) +
      (listPathTemplate == null ? 0 : listPathTemplate!.hashCode) +
      (options.hashCode) +
      (proofHooks.hashCode) +
      (providerConfigurationExposed == null
          ? 0
          : providerConfigurationExposed!.hashCode) +
      (readiness == null ? 0 : readiness!.hashCode) +
      (supportSafe == null ? 0 : supportSafe!.hashCode) +
      (uploadPath == null ? 0 : uploadPath!.hashCode);

  @override
  String toString() =>
      'FileNativeProviderSetupResponse[blockedUntil=$blockedUntil, credentialLifecyclePath=$credentialLifecyclePath, credentialsExposed=$credentialsExposed, downloadPathTemplate=$downloadPathTemplate, facadeBasePath=$facadeBasePath, listPathTemplate=$listPathTemplate, options=$options, proofHooks=$proofHooks, providerConfigurationExposed=$providerConfigurationExposed, readiness=$readiness, supportSafe=$supportSafe, uploadPath=$uploadPath]';

  Map<String, dynamic> toJson() {
    final json = <String, dynamic>{};
    json[r'blockedUntil'] = this.blockedUntil;
    if (this.credentialLifecyclePath != null) {
      json[r'credentialLifecyclePath'] = this.credentialLifecyclePath;
    } else {
      json[r'credentialLifecyclePath'] = null;
    }
    if (this.credentialsExposed != null) {
      json[r'credentialsExposed'] = this.credentialsExposed;
    } else {
      json[r'credentialsExposed'] = null;
    }
    if (this.downloadPathTemplate != null) {
      json[r'downloadPathTemplate'] = this.downloadPathTemplate;
    } else {
      json[r'downloadPathTemplate'] = null;
    }
    if (this.facadeBasePath != null) {
      json[r'facadeBasePath'] = this.facadeBasePath;
    } else {
      json[r'facadeBasePath'] = null;
    }
    if (this.listPathTemplate != null) {
      json[r'listPathTemplate'] = this.listPathTemplate;
    } else {
      json[r'listPathTemplate'] = null;
    }
    json[r'options'] = this.options;
    json[r'proofHooks'] = this.proofHooks;
    if (this.providerConfigurationExposed != null) {
      json[r'providerConfigurationExposed'] = this.providerConfigurationExposed;
    } else {
      json[r'providerConfigurationExposed'] = null;
    }
    if (this.readiness != null) {
      json[r'readiness'] = this.readiness;
    } else {
      json[r'readiness'] = null;
    }
    if (this.supportSafe != null) {
      json[r'supportSafe'] = this.supportSafe;
    } else {
      json[r'supportSafe'] = null;
    }
    if (this.uploadPath != null) {
      json[r'uploadPath'] = this.uploadPath;
    } else {
      json[r'uploadPath'] = null;
    }
    return json;
  }

  /// Returns a new [FileNativeProviderSetupResponse] instance and imports its values from
  /// [value] if it's a [Map], null otherwise.
  // ignore: prefer_constructors_over_static_methods
  static FileNativeProviderSetupResponse? fromJson(dynamic value) {
    if (value is Map) {
      final json = value.cast<String, dynamic>();

      // Ensure that the map contains the required keys.
      // Note 1: the values aren't checked for validity beyond being non-null.
      // Note 2: this code is stripped in release mode!
      assert(() {
        requiredKeys.forEach((key) {
          assert(json.containsKey(key),
              'Required key "FileNativeProviderSetupResponse[$key]" is missing from JSON.');
          assert(json[key] != null,
              'Required key "FileNativeProviderSetupResponse[$key]" has a null value in JSON.');
        });
        return true;
      }());

      return FileNativeProviderSetupResponse(
        blockedUntil: json[r'blockedUntil'] is Iterable
            ? (json[r'blockedUntil'] as Iterable)
                .cast<String>()
                .toList(growable: false)
            : const [],
        credentialLifecyclePath:
            mapValueOfType<String>(json, r'credentialLifecyclePath'),
        credentialsExposed: mapValueOfType<bool>(json, r'credentialsExposed'),
        downloadPathTemplate:
            mapValueOfType<String>(json, r'downloadPathTemplate'),
        facadeBasePath: mapValueOfType<String>(json, r'facadeBasePath'),
        listPathTemplate: mapValueOfType<String>(json, r'listPathTemplate'),
        options:
            FileNativeProviderOptionResponse.listFromJson(json[r'options']),
        proofHooks: json[r'proofHooks'] is Iterable
            ? (json[r'proofHooks'] as Iterable)
                .cast<String>()
                .toList(growable: false)
            : const [],
        providerConfigurationExposed:
            mapValueOfType<bool>(json, r'providerConfigurationExposed'),
        readiness:
            WorkspaceCapabilityStatusResponse.fromJson(json[r'readiness']),
        supportSafe: mapValueOfType<bool>(json, r'supportSafe'),
        uploadPath: mapValueOfType<String>(json, r'uploadPath'),
      );
    }
    return null;
  }

  static List<FileNativeProviderSetupResponse> listFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final result = <FileNativeProviderSetupResponse>[];
    if (json is List && json.isNotEmpty) {
      for (final row in json) {
        final value = FileNativeProviderSetupResponse.fromJson(row);
        if (value != null) {
          result.add(value);
        }
      }
    }
    return result.toList(growable: growable);
  }

  static Map<String, FileNativeProviderSetupResponse> mapFromJson(
      dynamic json) {
    final map = <String, FileNativeProviderSetupResponse>{};
    if (json is Map && json.isNotEmpty) {
      json = json.cast<String, dynamic>(); // ignore: parameter_assignments
      for (final entry in json.entries) {
        final value = FileNativeProviderSetupResponse.fromJson(entry.value);
        if (value != null) {
          map[entry.key] = value;
        }
      }
    }
    return map;
  }

  // maps a json object with a list of FileNativeProviderSetupResponse-objects as value to a dart map
  static Map<String, List<FileNativeProviderSetupResponse>> mapListFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final map = <String, List<FileNativeProviderSetupResponse>>{};
    if (json is Map && json.isNotEmpty) {
      // ignore: parameter_assignments
      json = json.cast<String, dynamic>();
      for (final entry in json.entries) {
        map[entry.key] = FileNativeProviderSetupResponse.listFromJson(
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
