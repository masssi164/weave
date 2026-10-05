//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//
// @dart=2.18

// ignore_for_file: unused_element, unused_import
// ignore_for_file: always_put_required_named_parameters_first
// ignore_for_file: constant_identifier_names
// ignore_for_file: lines_longer_than_80_chars

part of weave_user_api;

class CalendarNativeSyncSetupResponse {
  /// Returns a new [CalendarNativeSyncSetupResponse] instance.
  CalendarNativeSyncSetupResponse({
    this.appleProfilePath,
    this.blockedUntil = const [],
    this.credentialLifecyclePath,
    this.credentialsExposed,
    this.eventSyncPathTemplate,
    this.facadeBasePath,
    this.options = const [],
    this.proofHooks = const [],
    this.providerConfigurationExposed,
    this.readiness,
    this.supportSafe,
  });

  /// Weave-owned Apple profile download path. The profile itself remains unavailable until signing and scoped credentials exist.
  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? appleProfilePath;

  /// Support-safe blockers before native calendar availability can be true.
  List<String> blockedUntil;

  /// Weave-owned setup credential lifecycle path.
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

  /// Weave-owned event sync facade path template.
  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? eventSyncPathTemplate;

  /// Weave-owned calendar facade base path for CalDAV sync.
  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? facadeBasePath;

  /// OS-specific sync setup options.
  List<CalendarNativeSyncOptionResponse> options;

  /// Executable proof hooks that can be exercised before full native sync availability.
  List<String> proofHooks;

  /// False: member/native setup must not receive raw calendar-provider configuration.
  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  bool? providerConfigurationExposed;

  /// Calendar capability readiness as seen by the authenticated member.
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

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is CalendarNativeSyncSetupResponse &&
          other.appleProfilePath == appleProfilePath &&
          _deepEquality.equals(other.blockedUntil, blockedUntil) &&
          other.credentialLifecyclePath == credentialLifecyclePath &&
          other.credentialsExposed == credentialsExposed &&
          other.eventSyncPathTemplate == eventSyncPathTemplate &&
          other.facadeBasePath == facadeBasePath &&
          _deepEquality.equals(other.options, options) &&
          _deepEquality.equals(other.proofHooks, proofHooks) &&
          other.providerConfigurationExposed == providerConfigurationExposed &&
          other.readiness == readiness &&
          other.supportSafe == supportSafe;

  @override
  int get hashCode =>
      // ignore: unnecessary_parenthesis
      (appleProfilePath == null ? 0 : appleProfilePath!.hashCode) +
      (blockedUntil.hashCode) +
      (credentialLifecyclePath == null
          ? 0
          : credentialLifecyclePath!.hashCode) +
      (credentialsExposed == null ? 0 : credentialsExposed!.hashCode) +
      (eventSyncPathTemplate == null ? 0 : eventSyncPathTemplate!.hashCode) +
      (facadeBasePath == null ? 0 : facadeBasePath!.hashCode) +
      (options.hashCode) +
      (proofHooks.hashCode) +
      (providerConfigurationExposed == null
          ? 0
          : providerConfigurationExposed!.hashCode) +
      (readiness == null ? 0 : readiness!.hashCode) +
      (supportSafe == null ? 0 : supportSafe!.hashCode);

  @override
  String toString() =>
      'CalendarNativeSyncSetupResponse[appleProfilePath=$appleProfilePath, blockedUntil=$blockedUntil, credentialLifecyclePath=$credentialLifecyclePath, credentialsExposed=$credentialsExposed, eventSyncPathTemplate=$eventSyncPathTemplate, facadeBasePath=$facadeBasePath, options=$options, proofHooks=$proofHooks, providerConfigurationExposed=$providerConfigurationExposed, readiness=$readiness, supportSafe=$supportSafe]';

  Map<String, dynamic> toJson() {
    final json = <String, dynamic>{};
    if (this.appleProfilePath != null) {
      json[r'appleProfilePath'] = this.appleProfilePath;
    } else {
      json[r'appleProfilePath'] = null;
    }
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
    if (this.eventSyncPathTemplate != null) {
      json[r'eventSyncPathTemplate'] = this.eventSyncPathTemplate;
    } else {
      json[r'eventSyncPathTemplate'] = null;
    }
    if (this.facadeBasePath != null) {
      json[r'facadeBasePath'] = this.facadeBasePath;
    } else {
      json[r'facadeBasePath'] = null;
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
    return json;
  }

  /// Returns a new [CalendarNativeSyncSetupResponse] instance and imports its values from
  /// [value] if it's a [Map], null otherwise.
  // ignore: prefer_constructors_over_static_methods
  static CalendarNativeSyncSetupResponse? fromJson(dynamic value) {
    if (value is Map) {
      final json = value.cast<String, dynamic>();

      // Ensure that the map contains the required keys.
      // Note 1: the values aren't checked for validity beyond being non-null.
      // Note 2: this code is stripped in release mode!
      assert(() {
        requiredKeys.forEach((key) {
          assert(json.containsKey(key),
              'Required key "CalendarNativeSyncSetupResponse[$key]" is missing from JSON.');
          assert(json[key] != null,
              'Required key "CalendarNativeSyncSetupResponse[$key]" has a null value in JSON.');
        });
        return true;
      }());

      return CalendarNativeSyncSetupResponse(
        appleProfilePath: mapValueOfType<String>(json, r'appleProfilePath'),
        blockedUntil: json[r'blockedUntil'] is Iterable
            ? (json[r'blockedUntil'] as Iterable)
                .cast<String>()
                .toList(growable: false)
            : const [],
        credentialLifecyclePath:
            mapValueOfType<String>(json, r'credentialLifecyclePath'),
        credentialsExposed: mapValueOfType<bool>(json, r'credentialsExposed'),
        eventSyncPathTemplate:
            mapValueOfType<String>(json, r'eventSyncPathTemplate'),
        facadeBasePath: mapValueOfType<String>(json, r'facadeBasePath'),
        options:
            CalendarNativeSyncOptionResponse.listFromJson(json[r'options']),
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
      );
    }
    return null;
  }

  static List<CalendarNativeSyncSetupResponse> listFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final result = <CalendarNativeSyncSetupResponse>[];
    if (json is List && json.isNotEmpty) {
      for (final row in json) {
        final value = CalendarNativeSyncSetupResponse.fromJson(row);
        if (value != null) {
          result.add(value);
        }
      }
    }
    return result.toList(growable: growable);
  }

  static Map<String, CalendarNativeSyncSetupResponse> mapFromJson(
      dynamic json) {
    final map = <String, CalendarNativeSyncSetupResponse>{};
    if (json is Map && json.isNotEmpty) {
      json = json.cast<String, dynamic>(); // ignore: parameter_assignments
      for (final entry in json.entries) {
        final value = CalendarNativeSyncSetupResponse.fromJson(entry.value);
        if (value != null) {
          map[entry.key] = value;
        }
      }
    }
    return map;
  }

  // maps a json object with a list of CalendarNativeSyncSetupResponse-objects as value to a dart map
  static Map<String, List<CalendarNativeSyncSetupResponse>> mapListFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final map = <String, List<CalendarNativeSyncSetupResponse>>{};
    if (json is Map && json.isNotEmpty) {
      // ignore: parameter_assignments
      json = json.cast<String, dynamic>();
      for (final entry in json.entries) {
        map[entry.key] = CalendarNativeSyncSetupResponse.listFromJson(
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
